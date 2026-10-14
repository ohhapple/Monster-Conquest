package com.ohhapple.monster_conquest.network.clientbound.recruit;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 服务端 → 客户端：打开募兵站 GUI（带方块坐标，招募请求要带上它做距离校验）。
 *
 * 本类不引用任何 net.minecraft.client.* 类。
 *   实际客户端逻辑由 ClientPayloadDispatcher 处理。
 */
public record OpenRecruitGuiPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<OpenRecruitGuiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "open_recruit_gui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenRecruitGuiPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenRecruitGuiPayload::pos,
                    OpenRecruitGuiPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** 占位：实际处理见 ClientboundPayloadRouter → ClientPayloadDispatcher。 */
    public static void handle(OpenRecruitGuiPayload payload, IPayloadContext ctx) {
        // 空实现，仅供签名占位。
    }
}
