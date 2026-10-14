package com.ohhapple.monster_conquest.network.serverbound.faction;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.FactionGuiNetwork;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：请求打开阵营 GUI。
 * 服务端收到后：
 *  1. 发送最新快照
 *  2. 下发 OpenFactionGuiPayload
 */
public record RequestOpenFactionGuiPayload() implements CustomPacketPayload {

    public static final Type<RequestOpenFactionGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "request_open_faction_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestOpenFactionGuiPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestOpenFactionGuiPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RequestOpenFactionGuiPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            FactionGuiNetwork.sendSnapshot(player);
            PacketDistributor.sendToPlayer(player, new OpenFactionGuiPayload());
        });
    }
}
