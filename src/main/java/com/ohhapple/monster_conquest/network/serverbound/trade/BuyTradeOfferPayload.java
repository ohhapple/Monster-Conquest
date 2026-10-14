package com.ohhapple.monster_conquest.network.serverbound.trade;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.block.TradeStationService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：交易站购买（出售项下标 + 数量 1/8/64）。 */
public record BuyTradeOfferPayload(BlockPos pos, int offerIndex, int quantity)
        implements CustomPacketPayload {

    public static final Type<BuyTradeOfferPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "buy_trade_offer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BuyTradeOfferPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, BuyTradeOfferPayload::pos,
                    ByteBufCodecs.VAR_INT, BuyTradeOfferPayload::offerIndex,
                    ByteBufCodecs.VAR_INT, BuyTradeOfferPayload::quantity,
                    BuyTradeOfferPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(BuyTradeOfferPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            TradeStationService.buy(player, payload.pos(), payload.offerIndex(), payload.quantity());
        });
    }
}
