package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCareerPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLaunchProgressPayload;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/** 客户端大厅的最近一次权威快照与短时提示。 */
public final class ArenaLobbyClientState {

    private static final long NOTICE_DURATION_MS = 3_000L;

    @Nullable private static ArenaLobbySnapshotPayload snapshot;
    @Nullable private static ArenaCareerPayload viewedCareer;
    @Nullable private static ArenaLaunchProgressPayload launchProgress;
    @Nullable private static Component notice;
    private static long noticeUntil;

    private ArenaLobbyClientState() {
    }

    public static void setSnapshot(ArenaLobbySnapshotPayload payload) {
        ArenaGameClientState.clearArenaSessionContext();
        snapshot = payload;
        if (viewedCareer != null && payload.currentRoom() != null
                && payload.currentRoom().seats().stream().noneMatch(
                seat -> viewedCareer.playerId().equals(seat.playerId()))) {
            viewedCareer = null;
        }
        if (payload.currentRoom() == null || !payload.currentRoom().started()) {
            launchProgress = null;
        }
        if (Minecraft.getInstance().screen instanceof ArenaLobbyScreen screen) {
            screen.onSnapshotUpdated();
        }
    }

    public static void setViewedCareer(ArenaCareerPayload payload) {
        viewedCareer = payload;
        if (Minecraft.getInstance().screen instanceof ArenaLobbyScreen screen) {
            screen.onSnapshotUpdated();
        }
    }

    public static void setLaunchProgress(ArenaLaunchProgressPayload payload) {
        launchProgress = payload;
        if (Minecraft.getInstance().screen instanceof ArenaLobbyScreen screen) {
            screen.onSnapshotUpdated();
        }
    }

    public static void clearLaunchProgress() {
        launchProgress = null;
        if (Minecraft.getInstance().screen instanceof ArenaLobbyScreen screen) {
            screen.onSnapshotUpdated();
        }
    }

    @Nullable
    public static ArenaLobbySnapshotPayload snapshot() {
        return snapshot;
    }

    @Nullable
    public static ArenaCareerPayload viewedCareer() {
        return viewedCareer;
    }

    @Nullable
    public static ArenaLaunchProgressPayload launchProgress() {
        return launchProgress;
    }

    public static void showNotice(Component message) {
        notice = message;
        noticeUntil = Util.getMillis() + NOTICE_DURATION_MS;
    }

    public static void showTranslatedNotice(String translationKey) {
        showNotice(Component.translatable(translationKey));
    }

    @Nullable
    public static Component activeNotice() {
        if (notice == null || Util.getMillis() >= noticeUntil) return null;
        return notice;
    }

    public static void clear() {
        snapshot = null;
        viewedCareer = null;
        launchProgress = null;
        notice = null;
        noticeUntil = 0L;
    }
}
