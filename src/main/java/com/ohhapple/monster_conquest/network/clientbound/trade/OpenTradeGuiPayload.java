package com.ohhapple.monster_conquest.network.clientbound.trade;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 → 客户端：打开交易站 GUI。
 *
 * 本类不引用任何 net.minecraft.client.* 类。
 *   实际客户端逻辑由 ClientPayloadDispatcher 处理。
 */
public record OpenTradeGuiPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<OpenTradeGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_trade_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenTradeGuiPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenTradeGuiPayload::pos,
                    OpenTradeGuiPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 占位：实际处理见 ClientboundPayloadRouter → ClientPayloadDispatcher。 */
    public static void handle(OpenTradeGuiPayload payload, IPayloadContext ctx) {
        // 空实现，仅供签名占位。
    }
}
