package com.ohhapple.monster_conquest.network.serverbound.rts;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record RtsSelectPayload(List<UUID> soldierUuids, boolean additive)
        implements CustomPacketPayload {

    public static final Type<RtsSelectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "rts_select"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsSelectPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RtsSelectPayload decode(RegistryFriendlyByteBuf buf) {
                    int n = buf.readVarInt();
                    List<UUID> list = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) list.add(buf.readUUID());
                    boolean add = buf.readBoolean();
                    return new RtsSelectPayload(list, add);
                }
                @Override
                public void encode(RegistryFriendlyByteBuf buf, RtsSelectPayload p) {
                    List<UUID> list = p.soldierUuids();
                    buf.writeVarInt(list.size());
                    for (UUID u : list) buf.writeUUID(u);
                    buf.writeBoolean(p.additive());
                }
            };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RtsSelectPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());
            state.applyRtsBoxSelection(player, payload.soldierUuids(), payload.additive());

            // 空选中 + 非加选 = 玩家点击了空白 → 同时清空黄色移动标记
            if (payload.soldierUuids().isEmpty() && !payload.additive()) {
                state.clearRtsMoveMarker();
            }

            PacketDistributor.sendToPlayer(player, new SyncRtsSelectionPayload(
                    state.collectRtsSelectedSoldierUuids(player),
                    state.getRtsMoveMarker()));
        });
    }
}