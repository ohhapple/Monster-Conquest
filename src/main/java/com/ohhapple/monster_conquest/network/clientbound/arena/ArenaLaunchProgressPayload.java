package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/** 主服启动本地竞技子服时，向房间玩家同步当前阶段和预计剩余时间。 */
public record ArenaLaunchProgressPayload(
        int roomId,
        int stage,
        int elapsedMillis,
        int estimatedRemainingMillis) implements CustomPacketPayload {

    public static final int QUEUED = 0;
    public static final int STARTING_PROCESS = 1;
    public static final int LOADING_WORLD = 2;
    public static final int TRANSFERRING_PLAYERS = 3;
    public static final int ENTERING_ARENA = 4;

    public static final Type<ArenaLaunchProgressPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_launch_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf,
            ArenaLaunchProgressPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.roomId());
                buf.writeVarInt(payload.stage());
                buf.writeVarInt(payload.elapsedMillis());
                buf.writeVarInt(payload.estimatedRemainingMillis());
            },
            buf -> new ArenaLaunchProgressPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public float progress() {
        if (stage >= ENTERING_ARENA) return 1.0f;
        if (stage >= TRANSFERRING_PLAYERS) return 0.92f;
        float elapsedFraction = Math.min(1.0f, elapsedMillis / 30_000.0f);
        return Math.min(stage == LOADING_WORLD ? 0.82f : 0.72f,
                0.08f + elapsedFraction * 0.74f);
    }
}
