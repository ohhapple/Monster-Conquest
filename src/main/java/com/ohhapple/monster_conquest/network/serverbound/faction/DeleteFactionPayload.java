package com.ohhapple.monster_conquest.network.serverbound.faction;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.network.FactionGuiNetwork;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * 客户端 → 服务端：删除已注册的玩家阵营。
 * 权限：OP + 创造模式。
 */
public record DeleteFactionPayload(String factionId) implements CustomPacketPayload {

    public static final Type<DeleteFactionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "delete_faction"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DeleteFactionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, DeleteFactionPayload::factionId,
                    DeleteFactionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(DeleteFactionPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            if (!player.hasPermissions(2) || !player.isCreative()) {
                player.displayClientMessage(
                        Component.translatable("gui.monster_conquest.faction.delete_no_permission"),
                        true);
                return;
            }

            ResourceLocation id = ResourceLocation.tryParse(payload.factionId());
            if (id == null) return;
            if (!FactionIds.isPlayer(id)) {
                // 只允许删除玩家阵营
                player.displayClientMessage(
                        Component.translatable("gui.monster_conquest.faction.delete_only_player"),
                        true);
                return;
            }

            UUID owner = FactionIds.parsePlayer(id);
            if (owner == null) return;

            // 1. 移除阵营注册
            FactionRegistry.unregister(id);
            // 2. 移除敌对关系
            FactionHostility.removeFaction(id);
            // 3. 移除结盟关系
            FactionAlly.removeFaction(id);
            // 4. 移除军队关系（若有）
            ArmyRelations.clear(owner);
            // 5. 广播给玩家
            player.displayClientMessage(
                    Component.translatable("gui.monster_conquest.faction.deleted"), true);

            // 6. 重新发送最新快照
            FactionGuiNetwork.sendSnapshot(player);
        });
    }
}
