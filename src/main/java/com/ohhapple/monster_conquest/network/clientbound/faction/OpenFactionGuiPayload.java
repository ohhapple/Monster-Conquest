package com.ohhapple.monster_conquest.network.clientbound.faction;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 服务端 → 客户端：打开阵营 GUI。
 *
 * 本类不引用任何 net.minecraft.client.* 类。
 *   实际客户端逻辑由 ClientPayloadDispatcher → FactionGuiClientEvents.handleOpen 处理。
 */
public record OpenFactionGuiPayload() implements CustomPacketPayload {

    public static final Type<OpenFactionGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_faction_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenFactionGuiPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenFactionGuiPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /**
     * 占位方法。实际处理见：
     *   ClientboundPayloadRouter.dispatch → ClientPayloadDispatcher.dispatch
     *   → FactionGuiClientEvents.handleOpen
     */
    public static void handle(OpenFactionGuiPayload payload, IPayloadContext ctx) {
        // 空实现，仅供签名占位。
    }
}
