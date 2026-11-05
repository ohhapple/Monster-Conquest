package com.ohhapple.monster_conquest.arena.external;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.ArenaLobbyManager;
import com.ohhapple.monster_conquest.arena.ArenaStatsData;
import com.ohhapple.monster_conquest.arena.game.ArenaParticipant;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaConnectToChildPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbyNoticePayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLaunchProgressPayload;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * 主服侧的外部竞技服协调器。主服只准备桥接请求、转移玩家和接收结果，
 * 对局实体、计分板及临时棋盘全部属于独立子服。
 */
public final class ExternalArenaMainCoordinator {

    private static final int START_TIMEOUT_SECONDS = 120;
    private static final int RESULT_POLL_TICKS = 10;
    private static final int RETURN_GRACE_TICKS = 1200;
    private static final int MAX_ARENA_RESULT_PARTICIPANTS = 8;
    private static final long SPECTATOR_TICKET_MILLIS = 60_000L;
    private static final long SPECTATOR_ACCEPT_TIMEOUT_MILLIS = 15_000L;
    private static final ExecutorService PREPARER =
            Executors.newSingleThreadExecutor(new ThreadFactory() {
                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable,
                            "monster-conquest-arena-preparer");
                    thread.setDaemon(true);
                    return thread;
                }
            });

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, Session> BY_ROOM = new HashMap<>();

    @Nullable private static MinecraftServer server;
    private static int pollTicks;
    private static boolean disabledStateHandled;

    private ExternalArenaMainCoordinator() {
    }

    /**
     * @return true when this server start is the reopen of an integrated world
     * whose players are returning from the arena child
     */
    public static boolean setServer(@Nullable MinecraftServer newServer) {
        if (server != null && server != newServer) stop();
        boolean resumeIntegrated =
                newServer != null
                        && server == null
                        && !newServer.isDedicatedServer()
                        && hasIntegratedSessionFor(newServer);
        server = newServer;
        pollTicks = 0;
        if (newServer != null && newServer.isDedicatedServer()
                && ArenaConfig.arenaServerEnabled) {
            ArenaSinglePortGateway.start(newServer);
        }
        if (!resumeIntegrated) {
            SESSIONS.clear();
            BY_PLAYER.clear();
            BY_ROOM.clear();
            if (newServer != null) cleanupStaleBridge(newServer);
        }
        if (resumeIntegrated) {
            Monster_conquest.LOGGER.info(
                    "[ARENA-SERVER] Resuming {} integrated room session(s) after world reopen",
                    SESSIONS.size());
        }
        return resumeIntegrated;
    }

    public static boolean start(MinecraftServer runningServer, int roomId,
                                List<ArenaParticipant> participants,
                                boolean autoFillAi) {
        if (!ArenaConfig.arenaServerEnabled
                || participants.isEmpty()
                || participants.size() > MAX_ARENA_RESULT_PARTICIPANTS
                || BY_ROOM.containsKey(roomId)) {
            return false;
        }
        boolean integratedHost = !runningServer.isDedicatedServer();
        if (ArenaConfig.publicHost.isBlank()) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] publicHost must not be blank");
            return false;
        }
        if (!integratedHost
                && ArenaConfig.arenaServerPort == runningServer.getPort()) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] arenaServerPort must differ from the main port {}",
                    runningServer.getPort());
            return false;
        }
        if (!integratedHost && ArenaConfig.embeddedGatewayRouting) {
            if (!ArenaSinglePortGateway.isRunning()) {
                Monster_conquest.LOGGER.error(
                        "[ARENA-SERVER] Embedded gateway is enabled but not listening");
                return false;
            }
            if (ArenaConfig.gatewayPort == runningServer.getPort()
                    || ArenaConfig.gatewayPort == ArenaConfig.arenaServerPort) {
                Monster_conquest.LOGGER.error(
                        "[ARENA-SERVER] gatewayPort must differ from the main "
                                + "port {} and arenaServerPort {}",
                        runningServer.getPort(), ArenaConfig.arenaServerPort);
                return false;
            }
        }

        Set<UUID> participantIds = new HashSet<>();
        for (ArenaParticipant participant : participants) {
            if (!participantIds.add(participant.id())) {
                return false;
            }
        }
        if (integratedHost) {
            for (ServerPlayer online : runningServer.getPlayerList().getPlayers()) {
                if (!participantIds.contains(online.getUUID())) {
                    Monster_conquest.LOGGER.error(
                            "[ARENA-SERVER] Integrated room {} must include every "
                                    + "connected player; {} is not seated",
                            roomId, online.getGameProfile().getName());
                    return false;
                }
            }
        }

        Set<UUID> integratedLocalPlayers = new HashSet<>();
        Map<UUID, GameType> returnGameModes = new HashMap<>();
        for (ArenaParticipant participant : participants) {
            if (runningServer.getPlayerList().getPlayer(participant.id()) == null) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-SERVER] Cannot start room {}: player {} is offline",
                        roomId, participant.name());
                return false;
            }
            if (integratedHost) {
                ServerPlayer player = runningServer.getPlayerList()
                        .getPlayer(participant.id());
                if (player != null && isLocalConnection(player)) {
                    integratedLocalPlayers.add(participant.id());
                }
            }
            ServerPlayer player = runningServer.getPlayerList()
                    .getPlayer(participant.id());
            if (player != null) {
                returnGameModes.put(participant.id(),
                        player.gameMode.getGameModeForPlayer());
            }
        }

        UUID sessionId = UUID.randomUUID();
        Path bridge = bridgeDirectory(runningServer);
        Path requestPath = bridge.resolve("requests")
                .resolve(sessionId + ".properties");
        long expiresAt = System.currentTimeMillis() + 120_000L;
        String transferSecret = ArenaTransferAuth.newSecret();
        Map<UUID, String> transferNonces = new HashMap<>();
        for (ArenaParticipant participant : participants) {
            transferNonces.put(participant.id(),
                    ArenaTransferAuth.newNonce());
        }
        ArenaBridgeRequest request = new ArenaBridgeRequest(
                sessionId,
                roomId,
                autoFillAi,
                integratedHost,
                resolveReachableHost(runningServer, ArenaConfig.publicHost),
                ArenaConfig.embeddedGatewayRouting
                        ? ArenaConfig.gatewayPort
                        : Math.max(0, runningServer.getPort()),
                integratedHost ? runningServer.getWorldData().getLevelName() : "",
                expiresAt,
                transferSecret,
                transferNonces,
                participants,
                integratedLocalPlayers.stream().toList());
        try {
            ArenaBridgeRequest.write(requestPath, request);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to write bridge request for room {}",
                    roomId, exception);
            return false;
        }

        if (!LocalArenaServerProcess.isRunning()) {
            deleteReadyMarker(bridge);
        }
        Session session = new Session(
                sessionId, roomId, participants, requestPath,
                integratedHost
                        ? runningServer.getWorldData().getLevelName()
                        : null,
                returnGameModes,
                transferSecret,
                transferNonces,
                autoFillAi ? MAX_ARENA_RESULT_PARTICIPANTS
                        : Math.max(2, participants.size()));
        SESSIONS.put(sessionId, session);
        BY_ROOM.put(roomId, session);
        for (ArenaParticipant participant : participants) {
            BY_PLAYER.put(participant.id(), session);
        }

        session.prepare = CompletableFuture.supplyAsync(
                () -> ensureArenaServerReady(
                        runningServer, bridge, sessionId), PREPARER);
        sendLaunchProgress(session);
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Queued room={} session={} players={} autoFill={}",
                roomId, sessionId, participants.size(), autoFillAi);
        return true;
    }

    public static boolean requestSpectator(ServerPlayer player, int roomId) {
        if (!ArenaConfig.arenaServerEnabled) return false;
        Session session = BY_ROOM.get(roomId);
        if (server == null || session == null
                || session.state != SessionState.TRANSFERRED
                || BY_PLAYER.containsKey(player.getUUID())
                || session.spectators.contains(player.getUUID())
                || session.participants.stream().anyMatch(participant ->
                participant.id().equals(player.getUUID()))) {
            return false;
        }

        UUID playerId = player.getUUID();
        String playerName = player.getGameProfile().getName();
        Path spectatorRequest = bridgeDirectory(server)
                .resolve("spectators")
                .resolve(session.sessionId + "-" + playerId + ".properties");
        ArenaSpectatorRequest request = new ArenaSpectatorRequest(
                session.sessionId, session.roomId, playerId, playerName,
                ArenaTransferAuth.newNonce(),
                System.currentTimeMillis() + SPECTATOR_TICKET_MILLIS);
        session.spectatorNonces.put(
                playerId, request.transferNonce());
        try {
            ArenaSpectatorRequest.write(spectatorRequest, request);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to create spectator request room={} player={}",
                    roomId, playerName, exception);
            return false;
        }

        session.spectators.add(playerId);
        session.returnGameModes.put(playerId,
                player.gameMode.getGameModeForPlayer());
        session.pendingSpectators.put(playerId,
                new PendingSpectator(spectatorRequest,
                        request.transferNonce(),
                        System.currentTimeMillis()));
        BY_PLAYER.put(playerId, session);
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Queued spectator {} for room={}",
                playerName, roomId);
        return true;
    }

    public static void tick(MinecraftServer runningServer) {
        server = runningServer;
        if (!ArenaConfig.arenaServerEnabled) {
            if (!disabledStateHandled) {
                disabledStateHandled = true;
                ArenaSinglePortGateway.stop();
                forceStopChildServer();
            }
            return;
        }
        if (disabledStateHandled) {
            disabledStateHandled = false;
            if (runningServer.isDedicatedServer()) {
                ArenaSinglePortGateway.start(runningServer);
            }
        }
        if (++pollTicks % RESULT_POLL_TICKS == 0) {
            consumeResults(runningServer);
            consumeDepartures(runningServer);
            consumeTransferReceipts(runningServer);
        }
        tickSpectatorRequests(runningServer);

        for (Session session : new ArrayList<>(SESSIONS.values())) {
            switch (session.state) {
                case PREPARING -> tickPreparing(session);
                case TRANSFERRED -> tickTransferred(session);
                case RETURNED -> {
                    if ((session.resultApplied && allPlayersReturned(session))
                            || ++session.finishedTicks > RETURN_GRACE_TICKS) {
                        removeSession(session);
                    }
                }
            }
        }
    }

    private static void tickSpectatorRequests(
            MinecraftServer runningServer) {
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            for (Map.Entry<UUID, PendingSpectator> entry
                    : new ArrayList<>(session.pendingSpectators.entrySet())) {
                ServerPlayer player = runningServer.getPlayerList()
                        .getPlayer(entry.getKey());
                if (player == null || session.state != SessionState.TRANSFERRED) {
                    cancelPendingSpectator(session, entry.getKey(), false);
                    continue;
                }
                PendingSpectator pending = entry.getValue();
                Path accepted = ArenaSpectatorRequest.acceptedMarker(
                        pending.requestPath());
                if (Files.isRegularFile(accepted)) {
                    if (!transferAcceptedSpectator(player, session)) {
                        cancelPendingSpectator(
                                session, entry.getKey(), true);
                        continue;
                    }
                    session.pendingSpectators.remove(entry.getKey());
                    try {
                        Files.deleteIfExists(pending.requestPath());
                        Files.deleteIfExists(accepted);
                    } catch (IOException exception) {
                        Monster_conquest.LOGGER.warn(
                                "[ARENA-SERVER] Failed to clean spectator ticket for {}",
                                player.getGameProfile().getName(), exception);
                    }
                } else if (System.currentTimeMillis()
                        - pending.requestedAtMillis()
                        > SPECTATOR_ACCEPT_TIMEOUT_MILLIS) {
                    cancelPendingSpectator(session, entry.getKey(), true);
                }
            }
        }
    }

    private static boolean transferAcceptedSpectator(
            ServerPlayer player, Session session) {
        String host = resolveReachableHost(server, ArenaConfig.publicHost);
        String playerName = player.getGameProfile().getName();
        if (server.isDedicatedServer()
                && ArenaConfig.embeddedGatewayRouting) {
            ArenaSinglePortGateway.GatewayClientRoute route =
                    gatewayClientRouteFor(player, server);
            if (!ArenaSinglePortGateway.allowArenaPlayers(
                    session.sessionId,
                    List.of(new ArenaParticipant(
                            player.getUUID(), playerName, 0)),
                    session.transferSecret,
                    Map.of(player.getUUID(),
                            pendingSpectatorNonce(session, player.getUUID())),
                    Map.of(player.getUUID(), route))) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-SERVER] Could not bind spectator transfer "
                                + "to its gateway route for {}",
                        playerName);
                return false;
            }
            player.connection.send(new ClientboundTransferPacket(
                    route.transferHost(), ArenaConfig.gatewayPort));
        } else if (server.isDedicatedServer()) {
            player.connection.send(new ClientboundTransferPacket(
                    host, ArenaConfig.arenaServerPort));
        } else {
            player.connection.send(new ArenaConnectToChildPayload(
                    host, ArenaConfig.arenaServerPort));
        }
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Transferring spectator {} to room={}",
                playerName, session.roomId);
        return true;
    }

    private static String pendingSpectatorNonce(
            Session session, UUID playerId) {
        return session.spectatorNonces.getOrDefault(
                playerId, "");
    }

    private static void cancelPendingSpectator(
            Session session, UUID playerId, boolean notify) {
        PendingSpectator pending =
                session.pendingSpectators.remove(playerId);
        if (pending != null) {
            try {
                Files.deleteIfExists(pending.requestPath());
                Files.deleteIfExists(ArenaSpectatorRequest.acceptedMarker(
                        pending.requestPath()));
            } catch (IOException exception) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-SERVER] Failed to clean canceled spectator request",
                        exception);
            }
        }
        session.spectators.remove(playerId);
        session.spectatorNonces.remove(playerId);
        session.returnGameModes.remove(playerId);
        BY_PLAYER.remove(playerId, session);
        if (notify && server != null) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                player.connection.send(new ArenaLobbyNoticePayload(
                        "gui.monster_conquest.arena.notice.spectate_unavailable"));
            }
        }
    }





    public static boolean onPlayerLogin(ServerPlayer player) {
        Session session = BY_PLAYER.get(player.getUUID());
        if (session == null) return false;
        if (session.state == SessionState.PREPARING) return false;
        GameType originalMode = session.returnGameModes.get(player.getUUID());
        if (originalMode != null
                && player.gameMode.getGameModeForPlayer() != originalMode) {
            player.setGameMode(originalMode);
        }
        if (session.returned.add(player.getUUID())) {
            Monster_conquest.LOGGER.info(
                    "[ARENA-SERVER] Player {} returned to main, room={}",
                    player.getGameProfile().getName(), session.roomId);
        }
        ArenaLobbyManager.onMatchParticipantReturned(
                session.roomId, player.getUUID());
        if (session.resultApplied && allPlayersReturned(session)) {
            removeSession(session);
        }
        return true;
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        Session session = BY_PLAYER.get(player.getUUID());
        if (session != null
                && session.pendingSpectators.containsKey(player.getUUID())) {
            cancelPendingSpectator(session, player.getUUID(), false);
        }
    }

    public static boolean isTransferPending(UUID playerId) {
        Session session = BY_PLAYER.get(playerId);
        return session != null && !session.returned.contains(playerId);
    }

    public static void onMainServerStopping(MinecraftServer stoppingServer) {
        ArenaSinglePortGateway.stop();
        if (!stoppingServer.isDedicatedServer() && !SESSIONS.isEmpty()) {
            // The integrated client is moving to the child server. Its JVM
            // remains alive, so this is not a reason to stop the match.
            return;
        }
        LocalArenaServerProcess.requestStop();
    }

    public static void onMainServerStopped() {
        if (server != null && !server.isDedicatedServer()
                && !SESSIONS.isEmpty()) {
            server = null;
            return;
        }
        setServer(null);
    }

    public static boolean forceStopChildServer() {
        boolean running = LocalArenaServerProcess.isRunning();
        for (Session session : new ArrayList<>(SESSIONS.values())) {
            ArenaLobbyManager.onMatchFinished(session.roomId);
            try {
                Files.deleteIfExists(session.requestPath);
                deleteSpectatorRequests(session);
                Files.deleteIfExists(ArenaRuntime.acceptedRequestMarker(
                        session.requestPath.getParent().getParent(),
                        session.sessionId));
            } catch (IOException exception) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-SERVER] Failed to remove forced-stop request {}",
                        session.requestPath, exception);
            }
            clearSessionTickets(session);
        }
        SESSIONS.clear();
        BY_PLAYER.clear();
        BY_ROOM.clear();
        LocalArenaServerProcess.requestStop();
        return running;
    }

    public static boolean hasIntegratedSessions() {
        return SESSIONS.values().stream()
                .anyMatch(session -> session.integratedWorldName != null);
    }

    public static void stop() {
        ArenaSinglePortGateway.stop();
        if (server != null) {
            for (Session session : SESSIONS.values()) {
                try {
                    Files.deleteIfExists(session.requestPath);
                deleteSpectatorRequests(session);
                    Files.deleteIfExists(ArenaRuntime.acceptedRequestMarker(
                            session.requestPath.getParent().getParent(),
                            session.sessionId));
                } catch (IOException exception) {
                    Monster_conquest.LOGGER.warn(
                            "[ARENA-SERVER] Failed to remove request {}",
                            session.requestPath, exception);
                }
                clearSessionTickets(session);
            }
        }
        SESSIONS.clear();
        BY_PLAYER.clear();
        BY_ROOM.clear();
        LocalArenaServerProcess.stop();
        server = null;
    }

    private static void tickPreparing(Session session) {
        CompletableFuture<Boolean> prepare = session.prepare;
        updateLaunchProgress(session, prepare);
        if (prepare == null || !prepare.isDone()) return;
        if (prepare.isCompletedExceptionally() || !prepare.getNow(false)) {
            failSession(session, "arena child did not become ready");
            return;
        }

        if (server == null) {
            failSession(session, "main server is unavailable");
            return;
        }
        List<ServerPlayer> online = new ArrayList<>(session.participants.size());
        for (ArenaParticipant participant : session.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.id());
            if (player == null) {
                failSession(session,
                        "player left while the arena server was starting");
                return;
            }
            online.add(player);
        }

        sendLaunchProgress(session, ArenaLaunchProgressPayload.ENTERING_ARENA);
        int port = ArenaConfig.arenaServerPort;
        Map<UUID, ArenaSinglePortGateway.GatewayClientRoute>
                gatewayRoutes = new HashMap<>();
        if (ArenaConfig.embeddedGatewayRouting
                && server.isDedicatedServer()) {
            for (ServerPlayer player : online) {
                gatewayRoutes.put(
                        player.getUUID(),
                        gatewayClientRouteFor(player, server));
            }
            if (!ArenaSinglePortGateway.allowArenaPlayers(
                    session.sessionId,
                    session.participants,
                    session.transferSecret,
                    session.transferNonces,
                    gatewayRoutes)) {
                failSession(session,
                        "transfer gateway route is unavailable");
                return;
            }
        }
        for (ServerPlayer player : online) {
            if (ArenaConfig.embeddedGatewayRouting
                    && server.isDedicatedServer()) {
                ArenaSinglePortGateway.GatewayClientRoute route =
                        gatewayRoutes.get(player.getUUID());
                player.connection.send(new ClientboundTransferPacket(
                        route.transferHost(),
                        ArenaConfig.gatewayPort));
            } else if (server.isDedicatedServer()) {
                String host = childHostFor(player, server);
                player.connection.send(
                        new ClientboundTransferPacket(host, port));
            } else {
                String host = childHostFor(player, server);
                player.connection.send(new ArenaConnectToChildPayload(host, port));
            }
        }
        session.state = SessionState.TRANSFERRED;
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Transfer room={} session={} to {}",
                session.roomId, session.sessionId,
                ArenaConfig.embeddedGatewayRouting && server.isDedicatedServer()
                        ? "embedded gateway routes on port "
                        + ArenaConfig.gatewayPort
                        : "child port " + port);
    }

    private static void updateLaunchProgress(Session session,
                                             @Nullable CompletableFuture<Boolean> prepare) {
        if (--session.progressCooldown > 0) return;
        session.progressCooldown = 10;
        int stage;
        boolean ready = prepare != null
                && prepare.isDone()
                && !prepare.isCompletedExceptionally()
                && prepare.getNow(false);
        if (ready) {
            stage = ArenaLaunchProgressPayload.TRANSFERRING_PLAYERS;
        } else if (LocalArenaServerProcess.isRunning()) {
            stage = ArenaLaunchProgressPayload.LOADING_WORLD;
        } else {
            stage = ArenaLaunchProgressPayload.STARTING_PROCESS;
        }
        sendLaunchProgress(session, stage);
    }

    private static void sendLaunchProgress(Session session) {
        sendLaunchProgress(session, ArenaLaunchProgressPayload.QUEUED);
    }

    private static void sendLaunchProgress(Session session, int stage) {
        if (server == null) return;
        int elapsed = (int) Math.min(Integer.MAX_VALUE,
                System.currentTimeMillis() - session.startedAtMillis);
        int remaining = stage >= ArenaLaunchProgressPayload.TRANSFERRING_PLAYERS
                ? 1_000
                : Math.max(1_000, 30_000 - elapsed);
        ArenaLaunchProgressPayload payload = new ArenaLaunchProgressPayload(
                session.roomId, stage, elapsed, remaining);
        for (ArenaParticipant participant : session.participants) {
            ServerPlayer player = server.getPlayerList().getPlayer(participant.id());
            if (player != null) player.connection.send(payload);
        }
    }

    private static void tickTransferred(Session session) {
        if (!LocalArenaServerProcess.isRunning()) {
            ArenaLobbyManager.onMatchFinished(session.roomId);
            session.state = SessionState.RETURNED;
            session.finishedTicks = 0;
        }
    }

    private static void consumeResults(MinecraftServer runningServer) {
        Path results = bridgeDirectory(runningServer).resolve("results");
        if (!Files.isDirectory(results)) return;
        try (var files = Files.list(results)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .endsWith(".properties"))
                    .toList()) {
                ArenaBridgeResult result;
                try {
                    result = ArenaBridgeResult.read(path);
                } catch (Exception exception) {
                    Monster_conquest.LOGGER.error(
                            "[ARENA-SERVER] Failed to read result {}", path,
                            exception);
                    moveBrokenResult(path);
                    continue;
                }
                ResultApplyOutcome outcome =
                        applyResult(runningServer, result);
                if (outcome == ResultApplyOutcome.REJECTED) {
                    moveBrokenResult(path);
                } else {
                    Files.deleteIfExists(path);
                }
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to poll arena results", exception);
        }
    }

    private static void consumeTransferReceipts(
            MinecraftServer runningServer) {
        Path receipts = ArenaTransferAuth.receiptsDirectory(
                bridgeDirectory(runningServer));
        if (!Files.isDirectory(receipts)) return;
        try (var files = Files.list(receipts)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .endsWith(".properties"))
                    .toList()) {
                ArenaTransferAuth.Receipt receipt;
                try {
                    receipt = ArenaTransferAuth.readReceipt(path);
                } catch (Exception exception) {
                    moveBrokenTransferReceipt(path, "invalid");
                    continue;
                }
                Session session = SESSIONS.get(receipt.sessionId());
                if (session == null) {
                    moveBrokenTransferReceipt(path, "unknown-session");
                    continue;
                }
                String expectedNonce = session.transferNonces
                        .getOrDefault(receipt.playerId(),
                                session.spectatorNonces.getOrDefault(
                                        receipt.playerId(), ""));
                boolean accepted = !expectedNonce.isBlank()
                        && expectedNonce.equals(receipt.nonce())
                        && receipt.actualPlayerId().equals(
                        receipt.playerId())
                        && ArenaTransferAuth.verifyAcceptance(
                        session.transferSecret, receipt);
                if (!accepted) {
                    Monster_conquest.LOGGER.warn(
                            "[ARENA-GATEWAY] Rejected transfer receipt "
                                    + "room={} player={} actual={}",
                            session.roomId, receipt.playerId(),
                            receipt.actualPlayerId());
                    moveBrokenTransferReceipt(path, "invalid");
                    continue;
                }
                if (ArenaConfig.embeddedGatewayRouting
                        && !ArenaSinglePortGateway.confirmArenaPlayer(
                        receipt.sessionId(), receipt.playerId(),
                        receipt.nonce())) {
                    moveBrokenTransferReceipt(path, "unknown-ticket");
                    continue;
                }
                session.spectatorNonces.remove(receipt.playerId());
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-GATEWAY] Failed to poll transfer receipts",
                    exception);
        }
    }

    private static void moveBrokenTransferReceipt(
            Path path, String reason) {
        try {
            Files.move(path, path.resolveSibling(
                            path.getFileName() + "." + reason),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-GATEWAY] Failed to quarantine receipt {}",
                    path, exception);
        }
    }

    private static void consumeDepartures(MinecraftServer runningServer) {
        Path departures = bridgeDirectory(runningServer).resolve("departures");
        if (!Files.isDirectory(departures)) return;
        try (var files = Files.list(departures)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .endsWith(".properties"))
                    .toList()) {
                Properties properties = new Properties();
                try (var input = Files.newInputStream(path)) {
                    properties.load(input);
                    int roomId = Integer.parseInt(
                            properties.getProperty("roomId"));
                    UUID playerId = UUID.fromString(
                            properties.getProperty("playerId"));
                    Session session = BY_ROOM.get(roomId);
                    if (session != null
                            && session.state != SessionState.PREPARING
                            && session.participants.stream().anyMatch(
                            participant -> participant.id().equals(playerId))) {
                        ArenaLobbyManager.onMatchParticipantReturned(
                                roomId, playerId);
                        ArenaSinglePortGateway
                                .clearArenaPlayersById(
                                        Set.of(playerId));
                        Monster_conquest.LOGGER.info(
                                "[ARENA-SERVER] Released departed room seat room={} player={}",
                                roomId, playerId);
                    }
                } catch (Exception exception) {
                    Monster_conquest.LOGGER.warn(
                            "[ARENA-SERVER] Failed to read departure {}",
                            path, exception);
                }
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to poll arena departures", exception);
        }
    }

    private static ResultApplyOutcome applyResult(
            MinecraftServer runningServer, ArenaBridgeResult result) {
        Session session = SESSIONS.get(result.sessionId());
        if (session == null) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Ignoring result for unknown session={}",
                    result.sessionId());
            return ResultApplyOutcome.REJECTED;
        }
        if (session.roomId != result.roomId()) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Rejecting result room mismatch "
                            + "session={} expected={} actual={}",
                    result.sessionId(), session.roomId, result.roomId());
            return ResultApplyOutcome.REJECTED;
        }
        if (session.resultApplied) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Ignoring duplicate result session={}",
                    result.sessionId());
            return ResultApplyOutcome.DUPLICATE;
        }
        if (!isValidResult(session, result)) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Rejecting malformed result "
                            + "room={} session={}",
                    result.roomId(), result.sessionId());
            return ResultApplyOutcome.REJECTED;
        }

        ArenaStatsData stats = ArenaStatsData.get(runningServer);
        long finishedAt = System.currentTimeMillis();
        for (ArenaBridgeResult.PlayerPlacement placement : result.placements()) {
            stats.recordMatch(placement.playerId(), result.roomId(),
                    placement.placement(), placement.participants(),
                    finishedAt);
        }

        ArenaLobbyManager.onMatchFinished(session.roomId);
        if (session.state != SessionState.RETURNED) {
            session.state = SessionState.RETURNED;
            session.finishedTicks = 0;
        }
        session.resultApplied = true;
        clearSessionTickets(session);
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Result room={} session={} placements={}",
                result.roomId(), result.sessionId(),
                result.placements().size());
        if (allPlayersReturned(session)) removeSession(session);
        return ResultApplyOutcome.APPLIED;
    }

    private static boolean isValidResult(
            Session session, ArenaBridgeResult result) {
        if (result.placements().size()
                != session.participants.size()) {
            return false;
        }
        Set<UUID> expected = new HashSet<>();
        for (ArenaParticipant participant : session.participants) {
            expected.add(participant.id());
        }
        Set<UUID> seenPlayers = new HashSet<>();
        int participants = -1;
        for (ArenaBridgeResult.PlayerPlacement placement
                : result.placements()) {
            if (placement == null
                    || !expected.contains(placement.playerId())
                    || !seenPlayers.add(placement.playerId())
                    || placement.participants()
                    != session.expectedParticipants
                    || placement.placement() <= 0
                    || placement.placement()
                    > placement.participants()) {
                return false;
            }
            if (participants < 0) {
                participants = placement.participants();
            } else if (participants != placement.participants()) {
                return false;
            }
        }
        return seenPlayers.equals(expected);
    }

    private static boolean ensureArenaServerReady(MinecraftServer runningServer,
                                                  Path bridge,
                                                  UUID sessionId) {
        if (!ArenaConfig.arenaServerEnabled) return false;
        if (!LocalArenaServerProcess.ensureStarted(runningServer)) return false;
        Path ready = bridge.resolve(ArenaRuntime.READY_FILE_NAME);
        Path accepted = ArenaRuntime.acceptedRequestMarker(bridge, sessionId);
        long deadline = System.nanoTime()
                + START_TIMEOUT_SECONDS * 1_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (!LocalArenaServerProcess.isRunning()) return false;
            if (isReady(ready) && Files.isRegularFile(accepted)) return true;
            try {
                Thread.sleep(200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private static boolean isReady(Path ready) {
        try {
            if (!Files.isRegularFile(ready)) return false;
            String value = Files.readString(ready, StandardCharsets.US_ASCII).trim();
            if (!Integer.toString(ArenaConfig.arenaServerPort).equals(value)) {
                return false;
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1",
                        ArenaConfig.arenaServerPort), 250);
                return true;
            }
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isLocalConnection(ServerPlayer player) {
        return player.connection.getConnection().isMemoryConnection();
    }

    private static ArenaSinglePortGateway.GatewayClientRoute
    gatewayClientRouteFor(ServerPlayer player,
                          MinecraftServer runningServer) {
        String playerName = player.getGameProfile().getName();
        ArenaSinglePortGateway.GatewayClientRoute recorded =
                ArenaSinglePortGateway.routeFor(
                        player.getUUID(), playerName);
        SocketAddress remote = player.connection.getRemoteAddress();
        String sourceKey = recorded != null
                && !recorded.sourceKey().isBlank()
                ? recorded.sourceKey()
                : ArenaSinglePortGateway.sourceKey(remote);
        String configuredHost = ArenaConfig.publicHost.trim();
        String transferHost = !configuredHost.isBlank()
                && !isLoopbackOrWildcard(configuredHost)
                ? configuredHost
                : recorded != null
                && !recorded.transferHost().isBlank()
                ? recorded.transferHost()
                : fallbackGatewayHost(remote, runningServer);
        return new ArenaSinglePortGateway.GatewayClientRoute(
                transferHost, sourceKey);
    }

    private static String fallbackGatewayHost(
            SocketAddress remote, MinecraftServer runningServer) {
        if (remote instanceof InetSocketAddress address
                && address.getAddress() != null
                && address.getAddress().isLoopbackAddress()) {
            String configured = ArenaConfig.publicHost.trim();
            try {
                InetAddress configuredAddress =
                        InetAddress.getByName(configured);
                if (configuredAddress.isLoopbackAddress()
                        && !configured.isBlank()) {
                    return configured;
                }
            } catch (IOException ignored) {
            }
            return "127.0.0.1";
        }
        return resolveReachableHost(
                runningServer, ArenaConfig.publicHost);
    }

    private static String childHostFor(ServerPlayer player,
                                       MinecraftServer runningServer) {
        if (isLocalConnection(player)) {
            return ArenaConfig.publicHost;
        }
        return resolveReachableHost(
                runningServer, ArenaConfig.publicHost);
    }

    /**
     * The loopback defaults are correct for the integrated host but cannot be
     * reused by LAN clients. An explicitly configured non-loopback address wins;
     * otherwise use the address on which the integrated server is published.
     */
    private static String resolveReachableHost(MinecraftServer runningServer,
                                               String configuredHost) {
        if (!isLoopbackOrWildcard(configuredHost)) return configuredHost;

        String localIp = runningServer.getLocalIp();
        if (isUsableAddress(localIp)) return localIp;

        String detected = detectSiteLocalAddress();
        if (detected != null) return detected;

        Monster_conquest.LOGGER.warn(
                "[ARENA-SERVER] No LAN address was detected; keeping '{}'. "
                        + "Set publicHost explicitly for "
                        + "cross-machine play",
                configuredHost);
        return configuredHost;
    }

    private static boolean isLoopbackOrWildcard(String host) {
        if (host == null || host.isBlank()) return true;
        String value = host.trim().toLowerCase();
        if ("localhost".equals(value)) return true;
        try {
            InetAddress address = InetAddress.getByName(value);
            return address.isAnyLocalAddress() || address.isLoopbackAddress();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static boolean isUsableAddress(String host) {
        if (host == null || host.isBlank()) return false;
        try {
            InetAddress address = InetAddress.getByName(host);
            return !address.isAnyLocalAddress() && !address.isLoopbackAddress();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String detectSiteLocalAddress() {
        try {
            String best = null;
            int bestScore = Integer.MAX_VALUE;
            var interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) return null;
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback()) continue;
                String name = network.getName().toLowerCase();
                int score = network.isVirtual() ? 20 : 0;
                if (name.contains("docker") || name.contains("veth")
                        || name.contains("vmware") || name.contains("virtualbox")
                        || name.contains("vethernet") || name.contains("wsl")
                        || name.contains("tailscale")
                        || name.contains("zerotier")) {
                    score += 10;
                }
                for (InetAddress address : java.util.Collections.list(
                        network.getInetAddresses())) {
                    if (address instanceof Inet4Address
                            && address.isSiteLocalAddress()
                            && score < bestScore) {
                        best = address.getHostAddress();
                        bestScore = score;
                    }
                }
            }
            return best;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static void failSession(Session session, String reason) {
        Monster_conquest.LOGGER.error(
                "[ARENA-SERVER] Start failed room={} session={}: {}",
                session.roomId, session.sessionId, reason);
        try {
            Files.deleteIfExists(session.requestPath);
            deleteSpectatorRequests(session);
            Files.deleteIfExists(ArenaRuntime.acceptedRequestMarker(
                    session.requestPath.getParent().getParent(),
                    session.sessionId));
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to delete request {}",
                    session.requestPath, exception);
        }
        ArenaLobbyManager.onMatchFinished(session.roomId);
        removeSession(session);
    }

    private static void removeSession(Session session) {
        clearSessionTickets(session);
        try {
            Files.deleteIfExists(ArenaRuntime.acceptedRequestMarker(
                    session.requestPath.getParent().getParent(),
                    session.sessionId));
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to delete accepted marker {}",
                    session.sessionId, exception);
        }
        SESSIONS.remove(session.sessionId, session);
        BY_ROOM.remove(session.roomId, session);
        for (ArenaParticipant participant : session.participants) {
            BY_PLAYER.remove(participant.id(), session);
        }
        for (UUID spectatorId : session.spectators) {
            BY_PLAYER.remove(spectatorId, session);
        }
        deleteSpectatorRequests(session);
    }

    private static void clearSessionTickets(Session session) {
        ArenaSinglePortGateway.clearArenaPlayers(
                session.participants);
        ArenaSinglePortGateway.clearArenaPlayersById(
                session.spectators);
    }

    private static void moveBrokenResult(Path path) {
        try {
            Files.move(path, path.resolveSibling(
                    path.getFileName() + ".broken"),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to quarantine result {}", path,
                    exception);
        }
    }

    private static void deleteReadyMarker(Path bridge) {
        try {
            Files.deleteIfExists(bridge.resolve(ArenaRuntime.READY_FILE_NAME));
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to clear stale ready marker",
                    exception);
        }
    }

    private static boolean allPlayersReturned(Session session) {
        for (ArenaParticipant participant : session.participants) {
            if (!session.returned.contains(participant.id())) return false;
        }
        for (UUID spectatorId : session.spectators) {
            if (!session.returned.contains(spectatorId)) return false;
        }
        return true;
    }

    private static void deleteSpectatorRequests(Session session) {
        Path directory = session.requestPath.getParent().getParent()
                .resolve("spectators");
        String prefix = session.sessionId + "-";
        deleteSessionFiles(directory, prefix);
        deleteSessionFiles(ArenaTransferAuth.receiptsDirectory(
                session.requestPath.getParent().getParent()), prefix);
    }

    private static void deleteSessionFiles(
            Path directory, String prefix) {
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .startsWith(prefix)).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to clean session files in {}",
                    directory, exception);
        }
    }

    private static void cleanupStaleBridge(MinecraftServer runningServer) {
        Path bridge = bridgeDirectory(runningServer);
        if (!LocalArenaServerProcess.isRunning()) deleteReadyMarker(bridge);
        for (String directory : List.of(
                "requests", "spectators",
                ArenaRuntime.ACCEPTED_DIRECTORY,
                ArenaRuntime.TRANSFER_RECEIPTS_DIRECTORY)) {
            Path path = bridge.resolve(directory);
            if (!Files.isDirectory(path)) continue;
            try (var files = Files.list(path)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Files.deleteIfExists(file);
                }
            } catch (IOException exception) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-SERVER] Failed to clean stale bridge {}",
                        path, exception);
            }
        }
    }

    private static boolean hasIntegratedSessionFor(MinecraftServer runningServer) {
        if (runningServer.isDedicatedServer()) return false;
        String levelName = runningServer.getWorldData().getLevelName();
        return SESSIONS.values().stream().anyMatch(session ->
                levelName.equals(session.integratedWorldName));
    }

    private static Path bridgeDirectory(MinecraftServer runningServer) {
        return runningServer.getServerDirectory().toAbsolutePath().normalize()
                .resolve("monster_conquest/arena_bridge");
    }

    private enum SessionState {
        PREPARING,
        TRANSFERRED,
        RETURNED
    }

    private enum ResultApplyOutcome {
        APPLIED,
        DUPLICATE,
        REJECTED
    }

    private static final class Session {
        private final UUID sessionId;
        private final int roomId;
        private final List<ArenaParticipant> participants;
        private final Path requestPath;
        @Nullable private final String integratedWorldName;
        private final Map<UUID, GameType> returnGameModes;
        private final String transferSecret;
        private final Map<UUID, String> transferNonces;
        private final int expectedParticipants;
        private final Set<UUID> spectators = new HashSet<>();
        private final Map<UUID, String> spectatorNonces = new HashMap<>();
        private final Map<UUID, PendingSpectator> pendingSpectators =
                new HashMap<>();
        private final long startedAtMillis = System.currentTimeMillis();
        private final Set<UUID> returned = new HashSet<>();
        private CompletableFuture<Boolean> prepare;
        private SessionState state = SessionState.PREPARING;
        private boolean resultApplied;
        private int progressCooldown;
        private int finishedTicks;

        private Session(UUID sessionId, int roomId,
                        List<ArenaParticipant> participants,
                        Path requestPath,
                        @Nullable String integratedWorldName,
                        Map<UUID, GameType> returnGameModes,
                        String transferSecret,
                        Map<UUID, String> transferNonces,
                        int expectedParticipants) {
            this.sessionId = sessionId;
            this.roomId = roomId;
            this.participants = List.copyOf(participants);
            this.requestPath = requestPath;
            this.integratedWorldName = integratedWorldName;
            this.returnGameModes = new HashMap<>(returnGameModes);
            this.transferSecret = transferSecret;
            this.transferNonces = new HashMap<>(transferNonces);
            this.expectedParticipants = expectedParticipants;
        }
    }

    private record PendingSpectator(
            Path requestPath, String transferNonce,
            long requestedAtMillis) {
    }
}
