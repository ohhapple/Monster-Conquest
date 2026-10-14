package com.ohhapple.monster_conquest.network.serverbound.rts;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：RTS 按键指令（0/1/2 = 上马/下马/默认骑乘，10~14 = 线列/盾墙/楔形/散兵/自由站位）。 */
public record RtsOrderPayload(int action) implements CustomPacketPayload {

    public static final Type<RtsOrderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "rts_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsOrderPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RtsOrderPayload::action,
                    RtsOrderPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RtsOrderPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());
            state.applyRtsOrder(player, payload.action());
        });
    }
}
