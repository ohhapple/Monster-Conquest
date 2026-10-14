package com.ohhapple.monster_conquest.network.serverbound.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.gun.GunCombat;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：左键开火请求。
 * 带上客户端准星方向，保证子弹严格沿准星飞行（不受服务器视角同步延迟影响）。
 */
public record GunFirePayload(float dx, float dy, float dz) implements CustomPacketPayload {

    public static final Type<GunFirePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "gun_fire"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GunFirePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, GunFirePayload::dx,
                    ByteBufCodecs.FLOAT, GunFirePayload::dy,
                    ByteBufCodecs.FLOAT, GunFirePayload::dz,
                    GunFirePayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(GunFirePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            GunCombat.tryFire(player, player.getMainHandItem(),
                    new Vec3(payload.dx(), payload.dy(), payload.dz()));
        });
    }
}
