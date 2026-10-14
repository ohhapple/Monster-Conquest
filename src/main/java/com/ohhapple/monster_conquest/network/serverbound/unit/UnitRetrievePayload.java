package com.ohhapple.monster_conquest.network.serverbound.unit;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.util.UnitAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：单位界面里的"收纳该单位"按钮。
 *   与收纳工具完全同一条打包逻辑（背包放不下则收纳失败，单位留在原地）；
 *   生存玩家只能收纳自己玩家阵营的单位（创造模式不限），服务端会重新校验。
 */
public record UnitRetrievePayload(int entityId) implements CustomPacketPayload {

    public static final Type<UnitRetrievePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "unit_retrieve"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UnitRetrievePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeVarInt(payload.entityId()),
                    buf -> new UnitRetrievePayload(buf.readVarInt()));

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(UnitRetrievePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            Entity entity = player.serverLevel().getEntity(payload.entityId());
            if (!(entity instanceof ManagedUnit unit)) return;

            // 生存玩家只能收纳自己的单位（与收纳工具的归属校验一致，OP 也不放宽）
            if (!player.isCreative()) {
                java.util.UUID owner = UnitAccess.ownerOf(unit);
                if (owner == null || !owner.equals(player.getUUID())) {
                    player.displayClientMessage(
                            Component.translatable("item.monster_conquest.retrieval_tool.not_owner"),
                            true);
                    return;
                }
            }

            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();
            RetrievalToolItem.RetrieveResult result =
                    RetrievalToolItem.retrieveUnit(player, entity);
            RetrievalToolItem.notifyRetrieveResult(player, result, x, y, z);
            if (result == RetrievalToolItem.RetrieveResult.OK) {
                player.closeContainer();      // 单位已收走，界面跟着关掉
            }
        });
    }
}
