package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaConnectToChildPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaReconnectToMainPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaReturnToWorldPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 集成主世界与本地竞技子服之间的客户端切换。 */
public final class ArenaIntegratedReturnHandler {

    private static final int MAIN_RECONNECT_DELAY_SECONDS = 5;
    private static final int LAN_RESTORE_ATTEMPTS = 80;
    private static final long LAN_RESTORE_POLL_MILLIS = 250L;
    private static final int WORLD_UNLOCK_ATTEMPTS = 120;
    private static final long WORLD_UNLOCK_POLL_MILLIS = 250L;

    @Nullable private static LanRestoreState lanRestoreState;

    private ArenaIntegratedReturnHandler() {
    }

    public static void connectToChild(ArenaConnectToChildPayload payload,
                                      IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            IntegratedServer integratedServer = minecraft.getSingleplayerServer();
            lanRestoreState = captureLanState(minecraft, integratedServer);
            Monster_conquest.LOGGER.info(
                    "[ARENA-CLIENT] Integrated LAN restore state: {}",
                    lanRestoreState == null
                            ? "not published"
                            : "port " + lanRestoreState.port());
            Thread connector = new Thread(() -> {
                if (integratedServer != null) {
                    integratedServer.halt(false);
                    Thread serverThread = integratedServer.getRunningThread();
                    while (serverThread.isAlive()) {
                        try {
                            Thread.sleep(100L);
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                }
                minecraft.execute(() -> {
                    String address = payload.host() + ":" + payload.port();
                    Monster_conquest.LOGGER.info(
                            "[ARENA-CLIENT] Connecting to integrated child at {}",
                            address);
                    if (minecraft.getSingleplayerServer() != null) {
                        minecraft.disconnect(new TitleScreen(), false);
                    }
                    ConnectScreen.startConnecting(
                            new TitleScreen(),
                            minecraft,
                            new ServerAddress(payload.host(), payload.port()),
                            new ServerData(
                                    "Monster Conquest Arena",
                                    address,
                                    ServerData.Type.OTHER),
                            false,
                            null);
                });
            }, "monster-conquest-arena-client-connect");
            connector.setDaemon(true);
            connector.start();
        });
    }

    public static void reconnectToMain(ArenaReconnectToMainPayload payload,
                                       IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            // A bare disconnect leaves no active Screen while LocalPlayer is
            // already null; vanilla key processing can then run for one tick.
            minecraft.disconnect(new TitleScreen(), false);
            CompletableFuture.delayedExecutor(
                            MAIN_RECONNECT_DELAY_SECONDS,
                            TimeUnit.SECONDS)
                    .execute(() -> minecraft.execute(() -> connect(
                            minecraft,
                            payload.host(),
                            payload.port(),
                            "Monster Conquest Main")));
        });
    }

    public static void handle(ArenaReturnToWorldPayload payload,
                              IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.disconnect(new TitleScreen(), false);
            CompletableFuture.delayedExecutor(1500L, TimeUnit.MILLISECONDS)
                    .execute(() -> minecraft.execute(
                            () -> openLatestWorld(minecraft, payload.levelName())));
        });
    }

    private static void openLatestWorld(Minecraft minecraft, String levelName) {
        try {
            LevelStorageSource source = minecraft.getLevelSource();
            source.loadLevelSummaries(source.findLevelCandidates())
                    .thenAccept(summaries -> minecraft.execute(() -> {
                        LevelSummary target = summaries.stream()
                                .filter(summary -> summary.getLevelName()
                                        .equals(levelName))
                                .max(Comparator.comparingLong(
                                        LevelSummary::getLastPlayed))
                                .orElse(null);
                        if (target == null) {
                            Monster_conquest.LOGGER.warn(
                                    "[ARENA-CLIENT] Could not find integrated world '{}'",
                                    levelName);
                            minecraft.setScreen(new TitleScreen());
                            return;
                        }
                        openWorldWhenUnlocked(minecraft, target, 0);
                    }));
        } catch (Exception exception) {
            Monster_conquest.LOGGER.error(
                    "[ARENA-CLIENT] Failed to reopen integrated world '{}'",
                    levelName, exception);
            minecraft.setScreen(new TitleScreen());
        }
    }

    private static void openWorldWhenUnlocked(
            Minecraft minecraft, LevelSummary target, int attempt) {
        LevelStorageSource source = minecraft.getLevelSource();
        boolean unlocked = minecraft.getSingleplayerServer() == null
                && sessionLockAvailable(
                source.getLevelPath(target.getLevelId()));
        if (unlocked) {
            minecraft.createWorldOpenFlows().openWorld(
                    target.getLevelId(),
                    () -> minecraft.setScreen(new TitleScreen()));
            Monster_conquest.LOGGER.info(
                    "[ARENA-CLIENT] Reopened integrated world '{}'; "
                            + "checking LAN restore state",
                    target.getLevelName());
            restoreLanWhenReady(minecraft, 0);
            return;
        }
        if (attempt >= WORLD_UNLOCK_ATTEMPTS) {
            Monster_conquest.LOGGER.warn(
                    "[ARENA-CLIENT] Timed out waiting for world '{}' lock",
                    target.getLevelName());
            minecraft.setScreen(new TitleScreen());
            return;
        }
        CompletableFuture.delayedExecutor(
                        WORLD_UNLOCK_POLL_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> minecraft.execute(
                        () -> openWorldWhenUnlocked(
                                minecraft, target, attempt + 1)));
    }

    private static boolean sessionLockAvailable(
            java.nio.file.Path levelPath) {
        java.nio.file.Path lockPath = levelPath.resolve("session.lock");
        try (FileChannel channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.tryLock()) {
            return ignored != null;
        } catch (OverlappingFileLockException | IOException exception) {
            return false;
        }
    }

    private static void connect(Minecraft minecraft, String host, int port,
                                String displayName) {
        String address = host + ":" + port;
        Monster_conquest.LOGGER.info(
                "[ARENA-CLIENT] Connecting to {} at {}", displayName, address);
        ConnectScreen.startConnecting(
                new TitleScreen(),
                minecraft,
                new ServerAddress(host, port),
                new ServerData(displayName, address, ServerData.Type.OTHER),
                false,
                null);
    }

    @Nullable
    private static LanRestoreState captureLanState(
            Minecraft minecraft, @Nullable IntegratedServer integratedServer) {
        if (integratedServer == null || !integratedServer.isPublished()) {
            return null;
        }
        GameType gameType = minecraft.gameMode == null
                ? integratedServer.getWorldData().getGameType()
                : minecraft.gameMode.getPlayerMode();
        return new LanRestoreState(
                integratedServer.getPort(),
                gameType,
                integratedServer.getWorldData().isAllowCommands(),
                integratedServer.getWorldData().getLevelName());
    }

    private static void restoreLanWhenReady(Minecraft minecraft, int attempt) {
        LanRestoreState state = lanRestoreState;
        if (state == null) return;
        CompletableFuture.delayedExecutor(
                        LAN_RESTORE_POLL_MILLIS, TimeUnit.MILLISECONDS)
                .execute(() -> minecraft.execute(() -> {
                    IntegratedServer server = minecraft.getSingleplayerServer();
                    if (server == null) {
                        if (attempt < LAN_RESTORE_ATTEMPTS) {
                            restoreLanWhenReady(minecraft, attempt + 1);
                        } else {
                            Monster_conquest.LOGGER.warn(
                                    "[ARENA-CLIENT] Timed out restoring LAN "
                                            + "port {} for world '{}'",
                                    state.port(), state.levelName());
                            lanRestoreState = null;
                        }
                        return;
                    }
                    if (!state.levelName().equals(
                            server.getWorldData().getLevelName())) {
                        lanRestoreState = null;
                        return;
                    }
                    if (minecraft.player == null
                            || server.getPlayerList().getPlayer(
                            minecraft.player.getUUID()) == null) {
                        if (attempt < LAN_RESTORE_ATTEMPTS) {
                            restoreLanWhenReady(minecraft, attempt + 1);
                        } else {
                            Monster_conquest.LOGGER.warn(
                                    "[ARENA-CLIENT] Timed out waiting for the "
                                            + "local player before restoring "
                                            + "LAN port {}",
                                    state.port());
                            lanRestoreState = null;
                        }
                        return;
                    }
                    if (server.isPublished()) {
                        lanRestoreState = null;
                        return;
                    }
                    boolean published;
                    try {
                        published = server.publishServer(
                                state.gameType(),
                                state.allowCommands(),
                                state.port());
                    } catch (RuntimeException exception) {
                        Monster_conquest.LOGGER.warn(
                                "[ARENA-CLIENT] Failed to publish integrated "
                                        + "LAN port {} on attempt {}",
                                state.port(), attempt + 1, exception);
                        published = false;
                    }
                    if (published) {
                        Monster_conquest.LOGGER.info(
                                "[ARENA-CLIENT] Restored integrated LAN port {}",
                                state.port());
                        lanRestoreState = null;
                    } else if (attempt < LAN_RESTORE_ATTEMPTS) {
                        restoreLanWhenReady(minecraft, attempt + 1);
                    } else {
                        Monster_conquest.LOGGER.warn(
                                "[ARENA-CLIENT] Failed to restore integrated "
                                        + "LAN port {}",
                                state.port());
                        lanRestoreState = null;
                    }
                }));
    }

    private record LanRestoreState(
            int port,
            GameType gameType,
            boolean allowCommands,
            String levelName) {
    }
}
