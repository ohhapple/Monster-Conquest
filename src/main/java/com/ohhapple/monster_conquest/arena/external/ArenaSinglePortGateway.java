package com.ohhapple.monster_conquest.arena.external;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.game.ArenaParticipant;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.ReferenceCountUtil;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 基于 Netty 的单端口 Minecraft TCP 路由器。
 *
 * <p>网关只消费主服登记的短租约，并把登录流量转发到本机主服或竞技服。
 * 租约在竞技子服完成真实登录并由主服验证回执后才会被删除。</p>
 */
public final class ArenaSinglePortGateway {

    private static final int LOGIN_INTENT = 2;
    private static final int TRANSFER_INTENT = 3;
    private static final long ARENA_TICKET_MILLIS = 60_000L;
    private static final long CLIENT_ROUTE_MILLIS = 86_400_000L;
    private static final int MAX_CLIENT_ROUTES = 8192;
    private static final int MAX_PACKET_LENGTH = 2_097_152;

    private static final Map<String, ArenaLease> ARENA_LEASES_BY_NAME =
            new ConcurrentHashMap<>();
    private static final Map<UUID, ArenaLease> ARENA_LEASES_BY_ID =
            new ConcurrentHashMap<>();
    private static final Map<String, StoredClientRoute>
            CLIENT_ROUTES_BY_NAME = new ConcurrentHashMap<>();
    private static final Map<UUID, StoredClientRoute>
            CLIENT_ROUTES_BY_ID = new ConcurrentHashMap<>();
    private static final Collection<Channel> ACTIVE_CLIENTS =
            ConcurrentHashMap.newKeySet();
    private static final AtomicInteger ACTIVE_CLIENT_COUNT =
            new AtomicInteger();
    private static final AtomicInteger WORKER_INDEX =
            new AtomicInteger();

    private static volatile boolean running;
    private static volatile int mainPort;
    private static volatile int connectionLimit = 256;
    @Nullable private static volatile Channel listener;
    @Nullable private static EventLoopGroup bossGroup;
    @Nullable private static EventLoopGroup workerGroup;

    private ArenaSinglePortGateway() {
    }

    public static synchronized void start(MinecraftServer server) {
        if (running || !ArenaConfig.arenaServerEnabled
                || !ArenaConfig.embeddedGatewayRouting
                || !server.isDedicatedServer()) {
            return;
        }
        try {
            connectionLimit = Math.max(
                    8, ArenaConfig.gatewayMaxConnections);
            mainPort = server.getPort();
            bossGroup = new NioEventLoopGroup(1,
                    (java.util.concurrent.ThreadFactory) runnable -> daemonThread(
                            runnable, "monster-conquest-gateway-accept"));
            int workers = Math.max(2, Math.min(
                    8, Runtime.getRuntime().availableProcessors()));
            workerGroup = new NioEventLoopGroup(workers,
                    (java.util.concurrent.ThreadFactory) runnable -> daemonThread(
                            runnable,
                            "monster-conquest-gateway-io-"
                                    + WORKER_INDEX.incrementAndGet()));
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_REUSEADDR, true)
                    .childOption(ChannelOption.TCP_NODELAY, true)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel channel) {
                            int active =
                                    ACTIVE_CLIENT_COUNT.incrementAndGet();
                            if (active > connectionLimit) {
                                ACTIVE_CLIENT_COUNT.decrementAndGet();
                                channel.close();
                                return;
                            }
                            ACTIVE_CLIENTS.add(channel);
                            channel.closeFuture().addListener(
                                    ignored -> {
                                        ACTIVE_CLIENTS.remove(channel);
                                        ACTIVE_CLIENT_COUNT.updateAndGet(
                                                value -> Math.max(
                                                        0, value - 1));
                                    });
                            channel.pipeline().addLast(
                                    "gatewayReadTimeout",
                                    new ReadTimeoutHandler(15));
                            channel.pipeline().addLast(
                                    new LoginRouter());
                        }
                    });
            ChannelFuture bind = bootstrap.bind(
                    new InetSocketAddress(
                            InetAddress.getByName(
                                    ArenaConfig.gatewayListenHost),
                            ArenaConfig.gatewayPort))
                    .awaitUninterruptibly();
            if (!bind.isSuccess()) {
                throw new IOException(
                        "Gateway bind failed", bind.cause());
            }
            listener = bind.channel();
            running = true;
            if (!server.usesAuthentication()) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-GATEWAY] online-mode is disabled; "
                                + "final player authorization relies on "
                                + "the arena white list and transfer receipt");
            }
            int arenaSuggested = ArenaConfig.arenaMaxPlayers
                    + Math.max(16,
                    ArenaConfig.arenaMaxPlayers / 4);
            int allTrafficSuggested =
                    server.getPlayerList().getMaxPlayers()
                            + ArenaConfig.arenaMaxPlayers
                            + Math.max(24,
                            (server.getPlayerList().getMaxPlayers()
                                    + ArenaConfig.arenaMaxPlayers) / 5);
            if (connectionLimit < arenaSuggested) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-GATEWAY] gatewayMaxConnections={} is "
                                + "below the arena-only recommendation {}",
                        connectionLimit, arenaSuggested);
            }
            if (connectionLimit < allTrafficSuggested) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-GATEWAY] gatewayMaxConnections={} is "
                                + "below the all-traffic recommendation {}; "
                                + "raise it or keep main-server access direct",
                        connectionLimit, allTrafficSuggested);
            }
            Monster_conquest.LOGGER.info(
                    "[ARENA-GATEWAY] Listening on {}:{}; main={} arena={} "
                            + "maxConnections={} workers={}",
                    ArenaConfig.gatewayListenHost,
                    ArenaConfig.gatewayPort, mainPort,
                    ArenaConfig.arenaServerPort,
                    connectionLimit, workers);
        } catch (Exception exception) {
            running = false;
            closeListener();
            shutdownEventLoops();
            Monster_conquest.LOGGER.error(
                    "[ARENA-GATEWAY] Failed to bind {}:{}",
                    ArenaConfig.gatewayListenHost,
                    ArenaConfig.gatewayPort, exception);
        }
    }

    public static synchronized void stop() {
        running = false;
        closeListener();
        for (Channel channel : List.copyOf(ACTIVE_CLIENTS)) {
            channel.close();
        }
        ACTIVE_CLIENTS.clear();
        ACTIVE_CLIENT_COUNT.set(0);
        ARENA_LEASES_BY_NAME.clear();
        ARENA_LEASES_BY_ID.clear();
        CLIENT_ROUTES_BY_NAME.clear();
        CLIENT_ROUTES_BY_ID.clear();
        shutdownEventLoops();
    }

    public static boolean isRunning() {
        return running;
    }

    public static synchronized boolean allowArenaPlayers(
            UUID sessionId,
            List<ArenaParticipant> participants,
            String transferSecret,
            Map<UUID, String> transferNonces,
            Map<UUID, GatewayClientRoute> clientRoutes) {
        if (sessionId == null || transferSecret == null
                || transferSecret.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        purgeExpiredLeases(now);
        long expiresAt = now + ARENA_TICKET_MILLIS;
        List<ArenaLease> leases = new ArrayList<>(participants.size());
        try {
            for (ArenaParticipant participant : participants) {
                String nonce = transferNonces.getOrDefault(
                        participant.id(), "");
                if (nonce.isBlank()) return false;
                ArenaTransferAuth.deriveTicketKey(
                        transferSecret, sessionId,
                        participant.id(), nonce);
                leases.add(new ArenaLease(
                        sessionId, participant.id(),
                        participant.name().toLowerCase(Locale.ROOT),
                        routeSourceKey(
                                clientRoutes.get(participant.id())),
                        nonce, expiresAt));
            }
        } catch (RuntimeException exception) {
            return false;
        }
        if (ArenaConfig.gatewayStrictSourceBinding
                && leases.stream().anyMatch(
                lease -> lease.sourceKey().isBlank())) {
            return false;
        }
        for (ArenaLease lease : leases) {
            registerLease(lease);
        }
        return true;
    }

    public static synchronized void clearArenaPlayers(
            List<ArenaParticipant> participants) {
        for (ArenaParticipant participant : participants) {
            removeLeaseByPlayerId(participant.id());
            String nameKey =
                    participant.name().toLowerCase(Locale.ROOT);
            ArenaLease byName = ARENA_LEASES_BY_NAME.remove(nameKey);
            if (byName != null) {
                ARENA_LEASES_BY_ID.remove(
                        byName.playerId(), byName);
            }
        }
    }

    public static synchronized void clearArenaPlayersById(
            Collection<UUID> playerIds) {
        for (UUID playerId : List.copyOf(playerIds)) {
            removeLeaseByPlayerId(playerId);
        }
    }

    public static synchronized boolean confirmArenaPlayer(
            UUID sessionId, UUID playerId, String nonce) {
        ArenaLease lease = ARENA_LEASES_BY_ID.get(playerId);
        if (lease == null
                || !lease.sessionId().equals(sessionId)
                || !lease.nonce().equals(nonce)) {
            return false;
        }
        lease.markAccepted();
        return true;
    }

    @Nullable
    public static synchronized GatewayClientRoute routeFor(
            UUID playerId, String playerName) {
        long now = System.currentTimeMillis();
        purgeExpiredClientRoutes(now);
        StoredClientRoute stored =
                CLIENT_ROUTES_BY_ID.get(playerId);
        if (stored == null) {
            stored = CLIENT_ROUTES_BY_NAME.get(
                    playerName.toLowerCase(Locale.ROOT));
        }
        return stored == null ? null : stored.route();
    }

    private static void registerLease(ArenaLease lease) {
        ArenaLease previousByName = ARENA_LEASES_BY_NAME.put(
                lease.nameKey(), lease);
        if (previousByName != null
                && !previousByName.playerId().equals(
                lease.playerId())) {
            ARENA_LEASES_BY_ID.remove(
                    previousByName.playerId(), previousByName);
        }
        ArenaLease previousById = ARENA_LEASES_BY_ID.put(
                lease.playerId(), lease);
        if (previousById != null
                && !previousById.nameKey().equals(
                lease.nameKey())) {
            ARENA_LEASES_BY_NAME.remove(
                    previousById.nameKey(), previousById);
        }
    }

    private static void removeLeaseByPlayerId(UUID playerId) {
        ArenaLease lease = ARENA_LEASES_BY_ID.remove(playerId);
        if (lease == null) return;
        ARENA_LEASES_BY_NAME.remove(
                lease.nameKey(), lease);
    }

    private static String routeSourceKey(
            @Nullable GatewayClientRoute route) {
        return route == null ? "" : route.sourceKey();
    }

    private static boolean usesLoginProtocol(int intent) {
        return intent == LOGIN_INTENT
                || intent == TRANSFER_INTENT;
    }

    private static synchronized void rememberClientRoute(
            LoginStart login, String sourceKey,
            String serverAddress) {
        if (sourceKey.isBlank()) return;
        long now = System.currentTimeMillis();
        purgeExpiredClientRoutes(now);
        String nameKey = login.name().toLowerCase(Locale.ROOT);
        StoredClientRoute stored = new StoredClientRoute(
                new GatewayClientRoute(
                        serverAddress == null
                                ? "" : serverAddress.trim(),
                        sourceKey),
                login.playerId(), nameKey, now);
        StoredClientRoute previousByName =
                CLIENT_ROUTES_BY_NAME.put(nameKey, stored);
        if (previousByName != null
                && previousByName.playerId() != null
                && !previousByName.playerId().equals(
                login.playerId())) {
            CLIENT_ROUTES_BY_ID.remove(
                    previousByName.playerId(), previousByName);
        }
        if (login.playerId() != null) {
            StoredClientRoute previousById =
                    CLIENT_ROUTES_BY_ID.put(
                            login.playerId(), stored);
            if (previousById != null
                    && !previousById.nameKey().equals(nameKey)) {
                CLIENT_ROUTES_BY_NAME.remove(
                        previousById.nameKey(), previousById);
            }
        }
        evictOldClientRoutes();
    }

    private static synchronized void purgeExpiredClientRoutes(long now) {
        long cutoff = now - CLIENT_ROUTE_MILLIS;
        for (StoredClientRoute route
                : List.copyOf(CLIENT_ROUTES_BY_NAME.values())) {
            if (route.observedAtMillis() >= cutoff) continue;
            CLIENT_ROUTES_BY_NAME.remove(route.nameKey(), route);
            if (route.playerId() != null) {
                CLIENT_ROUTES_BY_ID.remove(
                        route.playerId(), route);
            }
        }
    }

    private static synchronized void evictOldClientRoutes() {
        while (CLIENT_ROUTES_BY_NAME.size() > MAX_CLIENT_ROUTES) {
            StoredClientRoute oldest = CLIENT_ROUTES_BY_NAME.values()
                    .stream()
                    .min(java.util.Comparator.comparingLong(
                            StoredClientRoute::observedAtMillis))
                    .orElse(null);
            if (oldest == null) return;
            CLIENT_ROUTES_BY_NAME.remove(
                    oldest.nameKey(), oldest);
            if (oldest.playerId() != null) {
                CLIENT_ROUTES_BY_ID.remove(
                        oldest.playerId(), oldest);
            }
        }
    }

    private static synchronized ArenaRoute arenaRoute(
            LoginStart login, String sourceKey, int intent) {
        long now = System.currentTimeMillis();
        String nameKey = login.name().toLowerCase(Locale.ROOT);
        ArenaLease lease = ARENA_LEASES_BY_NAME.get(nameKey);
        if (lease == null && login.playerId() != null) {
            ArenaLease byId = ARENA_LEASES_BY_ID.get(
                    login.playerId());
            if (byId != null && byId.nameKey().equals(nameKey)) {
                lease = byId;
            }
        }
        if (lease == null) {
            purgeExpiredLeases(now);
            return ArenaRoute.MAIN;
        }
        if (!lease.accepted()
                && lease.expiresAtMillis() < now) {
            removeLease(lease);
            return ArenaRoute.REJECT;
        }
        boolean sourceMatches =
                !ArenaConfig.gatewayStrictSourceBinding
                        || !sourceKey.isBlank()
                        && lease.sourceKey().equals(sourceKey);
        if (!lease.nameKey().equals(nameKey)
                || !sourceMatches) {
            return ArenaRoute.REJECT;
        }
        if (lease.accepted()) {
            // 正常重连是 LOGIN；返回主服由原版转服触发 TRANSFER。
            return intent == LOGIN_INTENT
                    ? ArenaRoute.ARENA : ArenaRoute.MAIN;
        }
        return intent == TRANSFER_INTENT
                ? ArenaRoute.ARENA : ArenaRoute.MAIN;
    }

    private static void removeLease(ArenaLease lease) {
        ARENA_LEASES_BY_NAME.remove(lease.nameKey(), lease);
        ARENA_LEASES_BY_ID.remove(lease.playerId(), lease);
    }

    private static synchronized void purgeExpiredLeases(long now) {
        for (ArenaLease lease
                : List.copyOf(ARENA_LEASES_BY_NAME.values())) {
            if (lease.accepted()
                    || lease.expiresAtMillis() >= now) {
                continue;
            }
            removeLease(lease);
        }
    }

    private static void routeLogin(
            ChannelHandlerContext context,
            HandshakeInfo handshake,
            @Nullable LoginStart login,
            ByteBuf handshakeFrame,
            @Nullable ByteBuf loginFrame,
            @Nullable ByteBuf trailing) {
        Channel client = context.channel();
        String sourceKey = sourceKey(client.remoteAddress());
        boolean arena = false;
        if (login != null) {
            ArenaRoute route = arenaRoute(
                    login, sourceKey, handshake.nextState());
            if (route == ArenaRoute.REJECT) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-GATEWAY] Rejected arena login for {} "
                                + "from {}",
                        login.name(), sourceKey);
                ReferenceCountUtil.release(handshakeFrame);
                if (loginFrame != null) {
                    ReferenceCountUtil.release(loginFrame);
                }
                if (trailing != null) {
                    ReferenceCountUtil.release(trailing);
                }
                client.close();
                return;
            }
            arena = route == ArenaRoute.ARENA;
            if (arena) {
                Monster_conquest.LOGGER.info(
                        "[ARENA-GATEWAY] Routing {} to arena",
                        login.name());
            }
            rememberClientRoute(
                    login, sourceKey,
                    handshake.serverAddress());
        }
        List<ByteBuf> frames = new ArrayList<>(2);
        frames.add(handshakeFrame);
        if (loginFrame != null) frames.add(loginFrame);
        if (trailing != null) frames.add(trailing);
        connectBackend(client, frames,
                arena ? ArenaConfig.arenaServerPort : mainPort,
                login == null ? null : login.name());
    }

    private static void connectBackend(
            Channel client, List<ByteBuf> initialFrames,
            int backendPort, @Nullable String playerName) {
        ReadTimeoutHandler timeout = client.pipeline()
                .get(ReadTimeoutHandler.class);
        if (timeout != null) {
            client.pipeline().remove(timeout);
        }
        client.config().setAutoRead(false);
        Bootstrap bootstrap = new Bootstrap()
                .group(client.eventLoop())
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(
                                new RelayHandler(client));
                    }
                });
        ChannelFuture connect = bootstrap.connect(
                new InetSocketAddress("127.0.0.1", backendPort));
        connect.addListener(future -> {
            if (!future.isSuccess() || !client.isActive()) {
                releaseAll(initialFrames);
                Channel backend = connect.channel();
                if (backend != null) backend.close();
                client.close();
                Monster_conquest.LOGGER.warn(
                        "[ARENA-GATEWAY] Failed to connect {} to "
                                + "127.0.0.1:{}",
                        playerName == null ? "client" : playerName,
                        backendPort,
                        future.cause());
                return;
            }
            Channel backend = connect.channel();
            for (ByteBuf frame : initialFrames) {
                backend.write(frame);
            }
            backend.flush();
            client.pipeline().addLast(
                    new RelayHandler(backend));
            backend.read();
            client.read();
        });
    }

    private static void releaseAll(List<ByteBuf> buffers) {
        for (ByteBuf buffer : buffers) {
            ReferenceCountUtil.release(buffer);
        }
    }

    static String sourceKey(@Nullable SocketAddress address) {
        if (!(address instanceof InetSocketAddress socketAddress)) {
            return "";
        }
        InetAddress inetAddress = socketAddress.getAddress();
        if (inetAddress == null) return "";
        byte[] bytes = inetAddress.getAddress();
        if (bytes.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped
                    && (bytes[10] & 0xFF) == 0xFF
                    && (bytes[11] & 0xFF) == 0xFF) {
                return (bytes[12] & 0xFF) + "."
                        + (bytes[13] & 0xFF) + "."
                        + (bytes[14] & 0xFF) + "."
                        + (bytes[15] & 0xFF);
            }
        }
        return inetAddress.getHostAddress();
    }

    private static void closeListener() {
        Channel current = listener;
        listener = null;
        if (current != null) {
            current.close().awaitUninterruptibly();
        }
    }

    private static void shutdownEventLoops() {
        EventLoopGroup workers = workerGroup;
        EventLoopGroup bosses = bossGroup;
        workerGroup = null;
        bossGroup = null;
        if (workers != null) {
            workers.shutdownGracefully(
                    0L, 2L, TimeUnit.SECONDS);
        }
        if (bosses != null) {
            bosses.shutdownGracefully(
                    0L, 2L, TimeUnit.SECONDS);
        }
    }

    private static Thread daemonThread(
            Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static FrameToken readFrame(ByteBuf input)
            throws IOException {
        int start = input.readerIndex();
        int index = start;
        int value = 0;
        int lengthBytes = 0;
        while (true) {
            if (index >= input.writerIndex()) return null;
            int current = input.getByte(index++) & 0xFF;
            value |= (current & 0x7F) << (7 * lengthBytes);
            lengthBytes++;
            if ((current & 0x80) == 0) break;
            if (lengthBytes >= 5) {
                throw new IOException("VarInt too long");
            }
        }
        if (value <= 0 || value > MAX_PACKET_LENGTH) {
            throw new IOException(
                    "Invalid Minecraft packet length " + value);
        }
        int totalLength = lengthBytes + value;
        if (input.readableBytes() < totalLength) return null;
        ByteBuf raw = input.readRetainedSlice(totalLength);
        ByteBuf payload = raw.slice(
                raw.readerIndex() + lengthBytes, value);
        return new FrameToken(raw, payload);
    }

    private static HandshakeInfo parseHandshake(ByteBuf payload)
            throws IOException {
        int packetId = readVarInt(payload);
        if (packetId != 0) {
            throw new IOException("Unexpected handshake packet");
        }
        readVarInt(payload);
        String serverAddress = readUtf(payload, 255);
        int forwardingData = serverAddress.indexOf('\0');
        if (forwardingData >= 0) {
            serverAddress = serverAddress.substring(
                    0, forwardingData);
        }
        if (!payload.isReadable(2)) {
            throw new IOException("Missing handshake port");
        }
        payload.readUnsignedShort();
        return new HandshakeInfo(
                serverAddress, readVarInt(payload));
    }

    private static LoginStart parseLoginStart(ByteBuf payload)
            throws IOException {
        int packetId = readVarInt(payload);
        if (packetId != 0) {
            throw new IOException("Unexpected Login Start");
        }
        String name = readUtf(payload, 16);
        UUID playerId = payload.readableBytes() >= 16
                ? new UUID(payload.readLong(), payload.readLong())
                : null;
        return new LoginStart(name, playerId);
    }

    private static int readVarInt(ByteBuf input)
            throws IOException {
        int value = 0;
        for (int position = 0; position < 35; position += 7) {
            if (!input.isReadable()) throw new IOException();
            byte current = input.readByte();
            value |= (current & 0x7F) << position;
            if ((current & 0x80) == 0) return value;
        }
        throw new IOException("VarInt too long");
    }

    private static String readUtf(
            ByteBuf input, int maxChars) throws IOException {
        int length = readVarInt(input);
        if (length < 0 || length > maxChars * 4
                || !input.isReadable(length)) {
            throw new IOException("Invalid UTF length");
        }
        String value = input.toString(
                input.readerIndex(), length,
                StandardCharsets.UTF_8);
        input.skipBytes(length);
        return value;
    }

    private record HandshakeInfo(
            String serverAddress, int nextState) {
    }

    private record LoginStart(
            String name, @Nullable UUID playerId) {
    }

    public record GatewayClientRoute(
            String transferHost, String sourceKey) {
    }

    private record StoredClientRoute(
            GatewayClientRoute route,
            @Nullable UUID playerId,
            String nameKey,
            long observedAtMillis) {
    }

    private static final class ArenaLease {
        private final UUID sessionId;
        private final UUID playerId;
        private final String nameKey;
        private final String sourceKey;
        private final String nonce;
        private final long expiresAtMillis;
        private boolean accepted;

        private ArenaLease(
                UUID sessionId, UUID playerId,
                String nameKey, String sourceKey,
                String nonce, long expiresAtMillis) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.nameKey = nameKey;
            this.sourceKey = sourceKey;
            this.nonce = nonce;
            this.expiresAtMillis = expiresAtMillis;
        }

        private UUID sessionId() {
            return sessionId;
        }

        private UUID playerId() {
            return playerId;
        }

        private String nameKey() {
            return nameKey;
        }

        private String sourceKey() {
            return sourceKey;
        }

        private String nonce() {
            return nonce;
        }

        private long expiresAtMillis() {
            return expiresAtMillis;
        }

        private boolean accepted() {
            return accepted;
        }

        private void markAccepted() {
            accepted = true;
        }

    }

    private record FrameToken(
            ByteBuf raw, ByteBuf payload) {
    }

    private enum ArenaRoute {
        MAIN,
        ARENA,
        REJECT
    }

    private static final class LoginRouter
            extends ChannelInboundHandlerAdapter {
        private ByteBuf pending = Unpooled.buffer();
        @Nullable private ByteBuf handshakeFrame;
        @Nullable private HandshakeInfo handshake;
        private boolean routed;

        @Override
        public void channelRead(
                ChannelHandlerContext context, Object message)
                throws Exception {
            if (!(message instanceof ByteBuf input)) {
                context.fireChannelRead(message);
                return;
            }
            pending.writeBytes(input);
            input.release();
            tryRead(context);
        }

        private void tryRead(ChannelHandlerContext context)
                throws Exception {
            if (handshake == null) {
                FrameToken frame = readFrame(pending);
                if (frame == null) return;
                try {
                    handshake = parseHandshake(frame.payload());
                } catch (Exception exception) {
                    frame.raw().release();
                    throw exception;
                }
                handshakeFrame = frame.raw();
                if (!usesLoginProtocol(handshake.nextState())) {
                    route(context, null);
                    return;
                }
            }
            FrameToken loginFrame = readFrame(pending);
            if (loginFrame == null) return;
            LoginStart login;
            try {
                login = parseLoginStart(
                        loginFrame.payload());
            } catch (Exception exception) {
                loginFrame.raw().release();
                throw exception;
            }
            route(context, login, loginFrame.raw());
        }

        private void route(
                ChannelHandlerContext context,
                @Nullable LoginStart login) {
            route(context, login, null);
        }

        private void route(
                ChannelHandlerContext context,
                @Nullable LoginStart login,
                @Nullable ByteBuf loginFrame) {
            if (routed || handshake == null
                    || handshakeFrame == null) {
                return;
            }
            routed = true;
            ByteBuf trailing = pending.isReadable()
                    ? pending.readRetainedSlice(
                    pending.readableBytes()) : null;
            ByteBuf handshakeToTransfer = handshakeFrame;
            handshakeFrame = null;
            pending.release();
            pending = Unpooled.EMPTY_BUFFER;
            context.pipeline().remove(this);
            routeLogin(
                    context, handshake, login,
                    handshakeToTransfer, loginFrame, trailing);
        }

        @Override
        public void channelInactive(
                ChannelHandlerContext context) throws Exception {
            if (handshakeFrame != null) {
                handshakeFrame.release();
                handshakeFrame = null;
            }
            pending.release();
            super.channelInactive(context);
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext context, Throwable cause) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-GATEWAY] Rejected malformed login from {}",
                    context.channel().remoteAddress(), cause);
            if (handshakeFrame != null) {
                handshakeFrame.release();
                handshakeFrame = null;
            }
            pending.release();
            pending = Unpooled.EMPTY_BUFFER;
            context.close();
        }
    }

    private static final class RelayHandler
            extends ChannelInboundHandlerAdapter {
        private final Channel peer;

        private RelayHandler(Channel peer) {
            this.peer = peer;
        }

        @Override
        public void channelInactive(
                ChannelHandlerContext context) {
            if (peer.isOpen()) peer.close();
        }

        @Override
        public void channelRead(
                ChannelHandlerContext context, Object message) {
            if (!peer.isActive()) {
                ReferenceCountUtil.release(message);
                context.close();
                return;
            }
            peer.write(message).addListener(
                    ChannelFutureListener.CLOSE_ON_FAILURE);
        }

        @Override
        public void channelReadComplete(
                ChannelHandlerContext context) {
            peer.flush();
            if (peer.isWritable()) {
                context.read();
            }
        }

        @Override
        public void channelWritabilityChanged(
                ChannelHandlerContext context) {
            if (peer.isWritable()) {
                peer.read();
            }
            context.fireChannelWritabilityChanged();
        }

        @Override
        public void exceptionCaught(
                ChannelHandlerContext context, Throwable cause) {
            context.close();
        }
    }
}
