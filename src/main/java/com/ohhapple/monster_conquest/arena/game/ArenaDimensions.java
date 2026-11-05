package com.ohhapple.monster_conquest.arena.game;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public final class ArenaDimensions {

    /**
     * 自走棋子服只使用自己的原版主世界。主服和子服拥有独立存档，
     * 固定保留坐标再负责隔离同一子服内的多个房间。
     */
    public static final ResourceKey<Level> ARENA_LEVEL = Level.OVERWORLD;

    private ArenaDimensions() {
    }

    @Nullable
    public static ServerLevel get(MinecraftServer server) {
        return server.getLevel(ARENA_LEVEL);
    }

    public static boolean isArena(Level level, BlockPos pos) {
        return level.dimension().equals(ARENA_LEVEL)
                && ArenaPlotLayout.isReservedArenaSpace(pos);
    }

    public static boolean isArena(ServerPlayer player) {
        return isArena(player.level(), player.blockPosition());
    }
}
