package com.ohhapple.monster_conquest.network.clientbound.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 → 客户端：装填开始 / 结束。
 * 客户端收到开始包后本地倒数，用于绘制装填进度条。
 */
public record GunStatusPayload(boolean reloading, int totalTicks, int ticksLeft)
        implements CustomPacketPayload {

    public static final Type<GunStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "gun_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunStatusPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, GunStatusPayload::reloading,
                    ByteBufCodecs.VAR_INT, GunStatusPayload::totalTicks,
                    ByteBufCodecs.VAR_INT, GunStatusPayload::ticksLeft,
                    GunStatusPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GunStatusPayload payload, IPayloadContext ctx) {
        // 占位：实际处理见 ClientboundPayloadRouter → ClientPayloadDispatcher → GunClientState
    }
}
