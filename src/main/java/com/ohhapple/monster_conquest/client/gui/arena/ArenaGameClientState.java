package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.PieceView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.PlayerStatus;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSpectatorSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSoundPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCombatStatsPayload;
import com.ohhapple.monster_conquest.arena.game.ArenaUnitCatalog;
import com.ohhapple.monster_conquest.client.rts.RtsClientState;
import com.ohhapple.monster_conquest.client.rts.RtsClientTicker;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArenaGameClientState {

    private static final long NOTICE_MS = 3_000L;

    @Nullable private static ArenaPlayerSnapshotPayload snapshot;
    @Nullable private static Component notice;
    private static long noticeUntil;
    private static boolean arenaSessionContext;
    private static final Map<UUID,
            ArenaCombatStatsPayload.UnitStats> combatStats =
            new HashMap<>();
    private static int combatStatsRoom = -1;
    private static int combatStatsRound = -1;

    private ArenaGameClientState() {
    }

    public static void setSnapshot(ArenaPlayerSnapshotPayload payload) {
        ArenaPlayerSnapshotPayload previous = snapshot;
        snapshot = payload;
        // 准备阶段继续展示上一场统计，清零由下一战斗阶段的服务端快照负责。
        if (!payload.active()
                || payload.spectator()
                || previous == null
                || previous.spectator() != payload.spectator()
                || payload.roomId() != combatStatsRoom) {
            clearCombatStats();
        }
        playUpgradeFeedback(previous, payload);
        arenaSessionContext = payload.active();
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.active()) {
            ArenaLobbyClientState.clearLaunchProgress();
            minecraft.getToasts().clear();
        }
        if (!payload.active()) {
            if (minecraft.screen instanceof ArenaPlayerScreen
                    || minecraft.screen instanceof ArenaLobbyScreen) {
                minecraft.setScreen(null);
            }
            return;
        }
        if (RtsClientState.isRtsMode()) {
            RtsClientTicker.exitRts(minecraft);
        }
        if (payload.openGui()
                && !(minecraft.screen instanceof ArenaPlayerScreen)) {
            minecraft.setScreen(new ArenaPlayerScreen());
        } else if (minecraft.screen instanceof ArenaPlayerScreen screen) {
            screen.onSnapshotUpdated();
        }
    }

    public static void setSpectatorSnapshot(
            ArenaSpectatorSnapshotPayload payload) {
        List<PieceView> boardPieces = payload.boardPieces().stream()
                .map(piece -> new PieceView(
                        piece.id(),
                        piece.unitIndex(),
                        piece.star(),
                        piece.boardSlot(),
                        -1,
                        piece.equipment(),
                        null))
                .toList();
        int boardCount = (int) boardPieces.stream()
                .filter(piece -> !ArenaUnitCatalog.get(
                        piece.unitIndex()).special())
                .count();
        setSnapshot(new ArenaPlayerSnapshotPayload(
                payload.active(),
                payload.openGui(),
                payload.targetAlive(),
                true,
                payload.targetId(),
                payload.viewBoardIndex(),
                payload.roomId(),
                payload.round(),
                payload.phase(),
                payload.phaseTicksLeft(),
                payload.phaseTicksTotal(),
                payload.targetHealth(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                ArenaPlayerSnapshotPayload.BATTLE_NONE,
                payload.targetLevel(),
                0,
                0,
                boardCount,
                payload.opponentName(),
                payload.opponentId(),
                payload.lastDamage(),
                payload.championName(),
                List.of(),
                List.of(),
                List.of(),
                boardPieces,
                List.of(),
                payload.players().stream()
                        .map(status -> new PlayerStatus(
                                status.id(),
                                status.name(),
                                status.boardIndex(),
                                status.health(),
                                status.level(),
                                status.alive(),
                                status.ai(),
                                status.battleResult()))
                        .toList()));
    }

    @Nullable
    public static ArenaPlayerSnapshotPayload snapshot() {
        return snapshot;
    }

    public static boolean isArenaSessionContext() {
        return arenaSessionContext;
    }

    public static void setCombatStats(
            ArenaCombatStatsPayload payload) {
        if (!payload.active()) {
            clearCombatStats();
            return;
        }
        if (snapshot != null && snapshot.active()
                && (snapshot.roomId() != payload.roomId()
                || snapshot.round() != payload.round())) {
            return;
        }
        combatStatsRoom = payload.roomId();
        combatStatsRound = payload.round();
        combatStats.clear();
        for (ArenaCombatStatsPayload.UnitStats unit
                : payload.units()) {
            combatStats.put(unit.pieceId(), unit);
        }
    }

    @Nullable
    public static ArenaCombatStatsPayload.UnitStats
    combatStats(UUID pieceId) {
        return combatStats.get(pieceId);
    }

    public static void clearCombatStats() {
        combatStats.clear();
        combatStatsRoom = -1;
        combatStatsRound = -1;
    }

    public static void clearArenaSessionContext() {
        arenaSessionContext = false;
    }

    public static void showNotice(String translationKey) {
        notice = Component.translatable(translationKey);
        noticeUntil = Util.getMillis() + NOTICE_MS;
    }

    @Nullable
    public static Component activeNotice() {
        if (notice == null || Util.getMillis() >= noticeUntil) return null;
        return notice;
    }

    public static void clear() {
        snapshot = null;
        arenaSessionContext = false;
        notice = null;
        noticeUntil = 0L;
        clearCombatStats();
    }

    private static void playUpgradeFeedback(
            @Nullable ArenaPlayerSnapshotPayload previous,
            ArenaPlayerSnapshotPayload current) {
        if (previous == null || !previous.active()
                || !current.active()
                || previous.spectator()
                || current.spectator()
                || previous.roomId() != current.roomId()) {
            return;
        }
        boolean playerLevelUp = current.level() > previous.level();
        Map<UUID, Integer> previousStars = new HashMap<>();
        for (ArenaPlayerSnapshotPayload.PieceView piece
                : previous.pieces()) {
            previousStars.put(piece.id(), piece.star());
        }
        boolean unitStarUp = current.pieces().stream().anyMatch(piece -> {
            Integer oldStar = previousStars.get(piece.id());
            return oldStar != null && piece.star() > oldStar;
        });
        if (playerLevelUp || unitStarUp) {
            ArenaUiSounds.playServerCue(
                    ArenaSoundPayload.UPGRADE_SUCCESS);
        }
    }
}
