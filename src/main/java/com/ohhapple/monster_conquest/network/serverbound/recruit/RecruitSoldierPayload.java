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

/** 客户端 → 服务端：募兵站招募一名士兵（兵种下标 + 等级下标）。 */
public record RecruitSoldierPayload(BlockPos pos, int soldierIndex, int tierIndex)
        implements CustomPacketPayload {

    public static final Type<RecruitSoldierPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "recruit_soldier"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RecruitSoldierPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RecruitSoldierPayload::pos,
                    ByteBufCodecs.VAR_INT, RecruitSoldierPayload::soldierIndex,
                    ByteBufCodecs.VAR_INT, RecruitSoldierPayload::tierIndex,
                    RecruitSoldierPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RecruitSoldierPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            RecruitmentService.recruit(player, payload.pos(), payload.soldierIndex(), payload.tierIndex());
        });
    }
}
