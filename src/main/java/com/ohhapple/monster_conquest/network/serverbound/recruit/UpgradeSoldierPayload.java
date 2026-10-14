package com.ohhapple.monster_conquest.network.serverbound.recruit;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.block.RecruitmentService;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/** 客户端 → 服务端：募兵站"升级招募"（支付低一档刷怪蛋 + 目标档半价，召唤升级后的士兵）。 */
public record UpgradeSoldierPayload(BlockPos pos, int soldierIndex, int tierIndex)
        implements CustomPacketPayload {

    public static final Type<UpgradeSoldierPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "upgrade_soldier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UpgradeSoldierPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, UpgradeSoldierPayload::pos,
                    ByteBufCodecs.VAR_INT, UpgradeSoldierPayload::soldierIndex,
                    ByteBufCodecs.VAR_INT, UpgradeSoldierPayload::tierIndex,
                    UpgradeSoldierPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UpgradeSoldierPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            RecruitmentService.upgrade(player, payload.pos(), payload.soldierIndex(), payload.tierIndex());
        });
    }
}
