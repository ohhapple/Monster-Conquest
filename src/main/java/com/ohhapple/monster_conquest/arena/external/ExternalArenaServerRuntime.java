package com.ohhapple.monster_conquest.arena.external;

import com.mojang.authlib.GameProfile;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.game.ArenaDimensions;
import com.ohhapple.monster_conquest.arena.game.ArenaMatchManager;
import com.ohhapple.monster_conquest.arena.game.ArenaParticipant;
import com.ohhapple.monster_conquest.arena.game.ArenaPlotLayout;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaReconnectToMainPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaReturnToWorldPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteListEntry;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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

/**
 * 独立竞技子服的状态所有者。它只接受主服桥接请求中列出的玩家，
 * 等同一房间的全部玩家到齐后才创建该房间的 {@link ArenaMatchManager} 对局。
 */
public final class ExternalArenaServerRuntime {

    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int PARENT_CHECK_INTERVAL_TICKS = 100;

    private static final Map<UUID, PendingRoom> ROOMS = new HashMap<>();
    private static final Map<UUID, PendingRoom> BY_PLAYER = new HashMap<>();
    private static final Map<Integer, PendingRoom> BY_ROOM_ID = new HashMap<>();

    @Nullable private static MinecraftServer server;
    private static int scanCooldown;
    private static int parentCheckCooldown;
    private static int idleTicks;
    private static boolean stopping;

    private ExternalArenaServerRuntime() {
    }

    public static void setServer(@Nullable MinecraftServer newServer) {
        if (newServer == null) {
            deleteReadyMarker();
        }
        server = newServer;
        ROOMS.clear();
        BY_PLAYER.clear();
        BY_ROOM_ID.clear();
        scanCooldown = 0;
        parentCheckCooldown = 0;
        idleTicks = 0;
        stopping = false;
        if (newServer != null) {
            FilesOperations.createDirectories(
                    ArenaRuntime.bridgeDirectory().resolve("results"));
            FilesOperations.createDirectories(ArenaRuntime.bridgeDirectory()
                    .resolve(ArenaRuntime.ACCEPTED_DIRECTORY));
            FilesOperations.createDirectories(
                    ArenaTransferAuth.receiptsDirectory(
                            ArenaRuntime.bridgeDirectory()));
            configureArenaRules(newServer);
            newServer.getPlayerList().setUsingWhiteList(true);
            scanRequests();
            writeReadyMarker(newServer);
        }
    }

    public static void tick(MinecraftServer runningServer) {
        if (server == null) server = runningServer;
        if (++scanCooldown >= SCAN_INTERVAL_TICKS) {
            scanCooldown = 0;
            scanRequests();
        }
        if (++parentCheckCooldown >= PARENT_CHECK_INTERVAL_TICKS) {
            parentCheckCooldown = 0;
            if (parentMissing()) {
                requestStop("main server exited");
                return;
            }
        }
        if (runningServer.getTickCount() % 100 == 0) {
            configureArenaTime(runningServer);
        }
        for (PendingRoom room : new ArrayList<>(ROOMS.values())) {
            if (room.started) continue;
            if (System.currentTimeMillis() >= room.request.expiresAtMillis()) {
                failPendingRoom(room, "players did not arrive before expiry");
                continue;
            }
            if (room.allConnected()) tryStartRoom(room);
        }

        boolean busy = runningServer.getPlayerCount() > 0
                || !ROOMS.isEmpty()
                || ArenaMatchManager.hasActiveMatches();
        if (busy) {
            idleTicks = 0;
        } else if (++idleTicks >= ArenaConfig.idleShutdownTicks) {
            requestStop("idle");
        }
    }

    public static boolean onPlayerLogin(ServerPlayer player) {
        PendingRoom room = BY_PLAYER.get(player.getUUID());
        if (room == null) {
            // A request can arrive after the child became ready but before
            // its next one-second bridge scan.
            scanRequests();
            room = BY_PLAYER.get(player.getUUID());
        }
        if (room == null) {
            player.connection.disconnect(Component.literal(
                    "This server only accepts Monster Conquest arena transfers."));
            return false;
        }
        if (room.started && !ArenaMatchManager.isPlayerInArenaMatch(player.getUUID())) {
            if (room.spectators.contains(player.getUUID())) {
                resetArenaPlayer(player);
                if (ArenaMatchManager.enterSpectator(
                        player, room.request.roomId())) {
                    writeSpectatorTransferReceipt(
                            room, player.getUUID());
                    Monster_conquest.LOGGER.info(
                            "[ARENA-SERVER] Spectator {} connected room={}",
                            player.getGameProfile().getName(),
                            room.request.roomId());
                    return true;
                }
            }
            returnToMain(player, room.request);
            return false;
        }

        resetArenaPlayer(player);
        if (!room.started) {
            if (!ensureArenaPrepared(room)) {
                player.connection.disconnect(Component.literal(
                        "Could not prepare the Monster Conquest arena."));
                return false;
            }
            room.connected.add(player.getUUID());
            if (!room.allConnected()) {
                teleportToWaitingLobby(player, room);
            }
            tryStartRoom(room);
        }
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Player {} connected room={} session={}",
                player.getGameProfile().getName(), room.request.roomId(),
                room.request.sessionId());
        return true;
    }

    private static void writeTransferReceipt(
            PendingRoom room, UUID ticketPlayerId,
            UUID actualPlayerId) {
        if (!ArenaConfig.embeddedGatewayRouting) return;
        try {
            ArenaTransferAuth.writeReceipt(
                    ArenaRuntime.bridgeDirectory(),
                    room.request, ticketPlayerId,
                    actualPlayerId);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-GATEWAY] Failed to write transfer receipt "
                            + "room={} player={}",
                    room.request.roomId(), ticketPlayerId,
                    exception);
        }
    }

    private static void writeSpectatorTransferReceipt(
            PendingRoom room, UUID playerId) {
        if (!ArenaConfig.embeddedGatewayRouting) return;
        try {
            ArenaTransferAuth.writeReceipt(
                    ArenaRuntime.bridgeDirectory(),
                    room.request.sessionId(),
                    room.request.transferSecret(),
                    playerId,
                    room.spectatorNonces.getOrDefault(
                            playerId, ""),
                    playerId);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-GATEWAY] Failed to write spectator "
                            + "transfer receipt room={} player={}",
                    room.request.roomId(), playerId,
                    exception);
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        PendingRoom room = BY_PLAYER.get(player.getUUID());
        if (room == null || room.started) return;
        room.connected.remove(player.getUUID());
    }

    public static boolean exitPlayer(ServerPlayer player) {
        PendingRoom room = BY_PLAYER.remove(player.getUUID());
        if (room == null || !room.started) return false;
        ArenaParticipant participant = room.request.participants().stream()
                .filter(candidate -> candidate.id().equals(player.getUUID()))
                .findFirst().orElse(null);
        if (participant == null) {
            if (!room.spectators.remove(player.getUUID())) return false;
            returnToMain(player, room.request);
            revokePlayerIfUnused(player.getUUID(),
                    player.getGameProfile().getName());
            Monster_conquest.LOGGER.info(
                    "[ARENA-SERVER] Spectator {} left room={}",
                    player.getGameProfile().getName(), room.request.roomId());
            return true;
        }
        reportParticipantDeparted(room.request.roomId(), player.getUUID());
        returnToMain(player, room.request);
        revokePlayerIfUnused(participant.id(), participant.name());
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Player {} exited active room={} as a spectator",
                player.getGameProfile().getName(), room.request.roomId());
        return true;
    }

    /** 将单个投降/离场玩家同步给主服，不影响同一房间里仍在对局的玩家。 */
    public static void reportParticipantDeparted(int roomId, UUID playerId) {
        PendingRoom room = BY_ROOM_ID.get(roomId);
        if (room == null) return;
        Path departures = ArenaRuntime.bridgeDirectory()
                .resolve("departures");
        Path path = departures.resolve(
                room.request.sessionId() + "-" + playerId + ".properties");
        Properties properties = new Properties();
        properties.setProperty("sessionId",
                room.request.sessionId().toString());
        properties.setProperty("roomId", Integer.toString(roomId));
        properties.setProperty("playerId", playerId.toString());
        try {
            FilesOperations.createDirectories(departures);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "Monster Conquest arena departure");
            }
            try {
                Files.move(temporary, path,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to report departure room={} player={}",
                    roomId, playerId, exception);
        }
    }

    public static void finishMatch(
            int roomId, List<ArenaBridgeResult.PlayerPlacement> placements) {
        PendingRoom room = BY_ROOM_ID.get(roomId);
        if (room == null) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Finished unknown room={}", roomId);
            return;
        }
        writeResult(room, placements);
        for (ArenaParticipant participant : room.request.participants()) {
            ServerPlayer player = player(participant.id());
            if (player != null) returnToMain(player, room.request);
        }
        for (Map.Entry<UUID, String> spectator
                : room.spectatorNames.entrySet()) {
            ServerPlayer player = player(spectator.getKey());
            if (player != null) returnToMain(player, room.request);
        }
        ROOMS.remove(room.request.sessionId());
        BY_ROOM_ID.remove(roomId);
        for (ArenaParticipant participant : room.request.participants()) {
            BY_PLAYER.remove(participant.id(), room);
            revokePlayerIfUnused(participant.id(), participant.name());
        }
        for (Map.Entry<UUID, String> spectator
                : room.spectatorNames.entrySet()) {
            BY_PLAYER.remove(spectator.getKey(), room);
            revokePlayerIfUnused(spectator.getKey(), spectator.getValue());
        }
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Returned room={} session={} to {}:{}",
                roomId, room.request.sessionId(), room.request.returnHost(),
                room.request.returnPort());
    }

    public static void stop() {
        deleteReadyMarker();
        ROOMS.clear();
        BY_PLAYER.clear();
        BY_ROOM_ID.clear();
        idleTicks = 0;
        stopping = false;
        server = null;
    }

    /**
     * NeoForge development runs can leave non-daemon launcher threads alive
     * after the server has saved and stopped. The child is a single-purpose
     * process, so it terminates itself once the stopped event has completed.
     */
    public static void scheduleProcessExit() {
        Thread exit = new Thread(() -> {
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(0);
        }, "monster-conquest-arena-exit");
        exit.setDaemon(false);
        exit.start();
    }

    private static void scanRequests() {
        if (server == null) return;
        Path requests = ArenaRuntime.bridgeDirectory().resolve("requests");
        if (!Files.isDirectory(requests)) return;
        try (var files = Files.list(requests)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .endsWith(".properties"))
                    .toList()) {
                ArenaBridgeRequest request;
                try {
                    request = ArenaBridgeRequest.read(path);
                } catch (Exception exception) {
                    Monster_conquest.LOGGER.error(
                            "[ARENA-SERVER] Invalid bridge request {}", path,
                            exception);
                    continue;
                }
                if (ROOMS.containsKey(request.sessionId())) continue;
                if (System.currentTimeMillis() >= request.expiresAtMillis()) {
                    writeEmptyResult(request);
                    Files.deleteIfExists(path);
                    continue;
                }
                PendingRoom room = new PendingRoom(path, request);
                ROOMS.put(request.sessionId(), room);
                BY_ROOM_ID.put(request.roomId(), room);
                for (ArenaParticipant participant : request.participants()) {
                    BY_PLAYER.put(participant.id(), room);
                    allowPlayer(participant);
                }
                writeAcceptanceMarker(request);
                Monster_conquest.LOGGER.info(
                        "[ARENA-SERVER] Accepted bridge request room={} session={} players={}",
                        request.roomId(), request.sessionId(),
                        request.participants().size());
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to scan bridge requests", exception);
        }
        scanSpectatorRequests();
        if (!stopping) writeReadyMarker(server);
    }

    private static void allowPlayer(ArenaParticipant participant) {
        allowPlayer(participant.id(), participant.name());
    }

    private static void allowPlayer(UUID playerId, String playerName) {
        if (server == null) return;
        GameProfile profile = new GameProfile(playerId, playerName);
        if (!server.getPlayerList().isWhiteListed(profile)) {
            server.getPlayerList().getWhiteList()
                    .add(new UserWhiteListEntry(profile));
        }
    }

    private static void scanSpectatorRequests() {
        Path directory = ArenaRuntime.bridgeDirectory().resolve("spectators");
        if (!Files.isDirectory(directory)) return;
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile)
                    .filter(file -> file.getFileName().toString()
                            .endsWith(".properties"))
                    .toList()) {
                ArenaSpectatorRequest request;
                try {
                    request = ArenaSpectatorRequest.read(path);
                } catch (Exception exception) {
                    Monster_conquest.LOGGER.warn(
                            "[ARENA-SERVER] Invalid spectator request {}", path,
                            exception);
                    Files.deleteIfExists(path);
                    continue;
                }
                if (System.currentTimeMillis() >= request.expiresAtMillis()) {
                    Files.deleteIfExists(path);
                    continue;
                }
                PendingRoom room = ROOMS.get(request.sessionId());
                if (room == null || !room.started) continue;
                room.spectators.add(request.playerId());
                room.spectatorNames.put(
                        request.playerId(), request.playerName());
                room.spectatorNonces.put(
                        request.playerId(),
                        request.transferNonce());
                BY_PLAYER.put(request.playerId(), room);
                allowPlayer(request.playerId(), request.playerName());
                ArenaSpectatorRequest.writeAcceptedMarker(path);
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to scan spectator requests",
                    exception);
        }
    }

    private static void tryStartRoom(PendingRoom room) {
        if (room.started || server == null || !room.allConnected()) return;
        List<ArenaParticipant> participants = new ArrayList<>();
        for (ArenaParticipant participant : room.request.participants()) {
            participants.add(participant);
        }
        boolean started = ArenaMatchManager.startRoom(
                server, room.request.roomId(), participants,
                room.request.autoFillAi());
        if (!started) {
            failPendingRoom(room, "match manager rejected the room");
            return;
        }

        room.started = true;
        for (ArenaParticipant participant
                : room.request.participants()) {
            if (room.connected.contains(participant.id())) {
                writeTransferReceipt(
                        room, participant.id(),
                        participant.id());
            }
        }
        try {
            Files.deleteIfExists(room.requestPath);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to consume request {}",
                    room.requestPath, exception);
        }
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Started room={} session={}",
                room.request.roomId(), room.request.sessionId());
    }

    private static void failPendingRoom(PendingRoom room, String reason) {
        Monster_conquest.LOGGER.error(
                "[ARENA-SERVER] Request failed room={} session={}: {}",
                room.request.roomId(), room.request.sessionId(), reason);
        writeEmptyResult(room.request);
        try {
            Files.deleteIfExists(room.requestPath);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to delete request {}",
                    room.requestPath, exception);
        }
        for (ArenaParticipant participant : room.request.participants()) {
            ServerPlayer player = player(participant.id());
            if (player != null) returnToMain(player, room.request);
            BY_PLAYER.remove(participant.id(), room);
            revokePlayerIfUnused(participant.id(), participant.name());
        }
        ROOMS.remove(room.request.sessionId(), room);
        BY_ROOM_ID.remove(room.request.roomId(), room);
    }

    private static void writeEmptyResult(ArenaBridgeRequest request) {
        try {
            ArenaBridgeResult.write(resultPath(request), new ArenaBridgeResult(
                    request.sessionId(), request.roomId(), List.of()));
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to write empty result session={}",
                    request.sessionId(), exception);
        }
    }

    private static void revokePlayerIfUnused(
            UUID playerId, String playerName) {
        if (server == null) return;
        boolean stillNeeded = BY_PLAYER.values().stream()
                .anyMatch(room -> room.containsPlayer(playerId));
        if (!stillNeeded) {
            server.getPlayerList().getWhiteList().remove(
                    new GameProfile(playerId, playerName));
        }
    }

    private static void writeResult(
            PendingRoom room,
            List<ArenaBridgeResult.PlayerPlacement> placements) {
        try {
            ArenaBridgeResult.write(resultPath(room.request), new ArenaBridgeResult(
                    room.request.sessionId(), room.request.roomId(), placements));
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to write result session={}",
                    room.request.sessionId(), exception);
        }
    }

    private static Path resultPath(ArenaBridgeRequest request) {
        return ArenaRuntime.bridgeDirectory().resolve("results")
                .resolve(request.sessionId() + ".properties");
    }

    private static void writeAcceptanceMarker(ArenaBridgeRequest request) {
        Path marker = ArenaRuntime.acceptedRequestMarker(
                ArenaRuntime.bridgeDirectory(), request.sessionId());
        try {
            FilesOperations.createDirectories(marker.getParent());
            Files.writeString(marker, Long.toString(System.currentTimeMillis()),
                    StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to acknowledge request {}",
                    request.sessionId(), exception);
        }
    }

    private static void resetArenaPlayer(ServerPlayer player) {
        player.setGameMode(GameType.SPECTATOR);
        player.getInventory().clearContent();
        player.removeAllEffects();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().setFoodLevel(20);
        player.getFoodData().setSaturation(5.0f);
        player.setExperienceLevels(0);
        player.setExperiencePoints(0);
        player.setRemainingFireTicks(0);
        player.clearFire();
    }

    private static boolean ensureArenaPrepared(PendingRoom room) {
        if (room.arenaPrepared) return true;
        if (server == null) return false;
        ServerLevel level = ArenaDimensions.get(server);
        if (level == null || !ArenaPlotLayout.prepare(
                level, room.request.roomId())) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to prepare room={} before arrival",
                    room.request.roomId());
            return false;
        }
        room.arenaPrepared = true;
        return true;
    }

    private static void teleportToWaitingLobby(ServerPlayer player,
                                               PendingRoom room) {
        ServerLevel level = ArenaDimensions.get(server);
        if (level == null) return;
        Vec3 center = Vec3.atCenterOf(
                ArenaPlotLayout.lobbyCenter(room.request.roomId()));
        int index = room.connected.size();
        double angle = Math.PI * 2.0 * index
                / Math.max(1, room.request.participants().size());
        player.setGameMode(GameType.SPECTATOR);
        player.teleportTo(level,
                center.x + Math.cos(angle) * 6.0,
                center.y,
                center.z + Math.sin(angle) * 6.0,
                0.0f, 0.0f);
    }

    private static void returnToMain(ServerPlayer player,
                                     ArenaBridgeRequest request) {
        if (!request.integratedMain()) {
            if (ArenaConfig.embeddedGatewayRouting) {
                player.connection.send(new ClientboundTransferPacket(
                        request.returnHost(), request.returnPort()));
                return;
            }
            player.connection.send(new ClientboundTransferPacket(
                    request.returnHost(), request.returnPort()));
            return;
        }
        if (request.integratedLocalPlayers().contains(player.getUUID())) {
            player.connection.send(new ArenaReturnToWorldPayload(
                    request.returnWorldName()));
            return;
        }
        player.connection.send(new ArenaReconnectToMainPayload(
                request.returnHost(), request.returnPort()));
    }

    @Nullable
    private static ServerPlayer player(UUID playerId) {
        return server == null ? null
                : server.getPlayerList().getPlayer(playerId);
    }

    private static boolean parentMissing() {
        long parentPid = ArenaRuntime.parentPid();
        return parentPid > 0 && ProcessHandle.of(parentPid)
                .map(handle -> !handle.isAlive())
                .orElse(true);
    }

    private static void requestStop(String reason) {
        if (stopping || server == null) return;
        stopping = true;
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Stopping child server: {}", reason);
        deleteReadyMarker();
        MinecraftServer stoppingServer = server;
        Thread shutdown = new Thread(() -> {
            stoppingServer.halt(false);
            try {
                stoppingServer.getRunningThread().join(30_000L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(0);
        }, "monster-conquest-arena-shutdown");
        shutdown.setDaemon(false);
        shutdown.start();
    }

    private static void writeReadyMarker(MinecraftServer runningServer) {
        Path ready = ArenaRuntime.bridgeDirectory()
                .resolve(ArenaRuntime.READY_FILE_NAME);
        try {
            FilesOperations.createDirectories(ready.getParent());
            Files.writeString(ready, Integer.toString(runningServer.getPort()),
                    StandardCharsets.US_ASCII);
        } catch (IOException exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-SERVER] Failed to write ready marker", exception);
        }
    }

    private static void configureArenaRules(MinecraftServer runningServer) {
        GameRules rules = runningServer.getGameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, runningServer);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, runningServer);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, runningServer);
        rules.getRule(GameRules.RULE_DO_PATROL_SPAWNING).set(false, runningServer);
        rules.getRule(GameRules.RULE_DO_TRADER_SPAWNING).set(false, runningServer);
        rules.getRule(GameRules.RULE_DO_WARDEN_SPAWNING).set(false, runningServer);
        rules.getRule(GameRules.RULE_DOINSOMNIA).set(false, runningServer);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, runningServer);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, runningServer);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION)
                .set(false, runningServer);
        rules.getRule(GameRules.RULE_SHOWDEATHMESSAGES)
                .set(false, runningServer);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)
                .set(false, runningServer);
        rules.getRule(GameRules.RULE_DISABLE_RAIDS).set(true, runningServer);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, runningServer);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN)
                .set(true, runningServer);
        rules.getRule(GameRules.RULE_RANDOMTICKING).set(0, runningServer);
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(false, runningServer);
        rules.getRule(GameRules.RULE_DOENTITYDROPS).set(false, runningServer);
        rules.getRule(GameRules.RULE_DOBLOCKDROPS).set(false, runningServer);
        rules.getRule(GameRules.RULE_COMMANDBLOCKOUTPUT)
                .set(false, runningServer);
        configureArenaTime(runningServer);
        Monster_conquest.LOGGER.info(
                "[ARENA-SERVER] Applied isolated arena rules and fixed daytime");
    }

    private static void configureArenaTime(MinecraftServer runningServer) {
        for (var level : runningServer.getAllLevels()) {
            level.setDayTime(6_000L);
            level.setWeatherParameters(6_000, 0, false, false);
        }
    }

    private static void deleteReadyMarker() {
        try {
            Files.deleteIfExists(ArenaRuntime.bridgeDirectory()
                    .resolve(ArenaRuntime.READY_FILE_NAME));
        } catch (IOException exception) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-SERVER] Failed to delete ready marker", exception);
        }
    }

    private static final class PendingRoom {
        private final Path requestPath;
        private final ArenaBridgeRequest request;
        private final Set<UUID> connected = new HashSet<>();
        private final Set<UUID> spectators = new HashSet<>();
        private final Map<UUID, String> spectatorNames = new HashMap<>();
        private final Map<UUID, String> spectatorNonces = new HashMap<>();
        private boolean started;
        private boolean arenaPrepared;

        private PendingRoom(Path requestPath, ArenaBridgeRequest request) {
            this.requestPath = requestPath;
            this.request = request;
        }

        private boolean allConnected() {
            for (ArenaParticipant participant : request.participants()) {
                if (!connected.contains(participant.id())) return false;
            }
            return true;
        }

        private boolean containsPlayer(UUID playerId) {
            return request.contains(playerId)
                    || spectators.contains(playerId);
        }
    }

    private static final class FilesOperations {
        private static void createDirectories(Path path) {
            try {
                Files.createDirectories(path);
            } catch (IOException exception) {
                Monster_conquest.LOGGER.error(
                        "[ARENA-SERVER] Failed to create directory {}", path,
                        exception);
            }
        }
    }
}
