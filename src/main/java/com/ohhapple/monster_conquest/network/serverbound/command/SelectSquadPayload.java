package com.ohhapple.monster_conquest.network.serverbound.command;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncSquadPayload;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 客户端 → 服务端：切换当前选中/指挥的小分队。
 * squad = 0 表示"全选所有小分队"。
 *
 * 服务端处理完会回发 SyncSquadPayload，让客户端轮盘显示与服务器状态一致。
 */
public record SelectSquadPayload(int squad) implements CustomPacketPayload {

    public static final Type<SelectSquadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "select_squad"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectSquadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, SelectSquadPayload::squad,
                    SelectSquadPayload::new);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(SelectSquadPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());
            state.setSelectedSquad(payload.squad());

            // 回发 SyncSquadPayload，让客户端轮盘显示与服务器状态一致
            PacketDistributor.sendToPlayer(player, new SyncSquadPayload(payload.squad()));

            Component msg = payload.squad() == 0
                    ? Component.translatable("msg.monster_conquest.squad.all")
                    : Component.translatable("msg.monster_conquest.squad.selected",
                    payload.squad());
            player.displayClientMessage(msg, true);
        });
    }
}