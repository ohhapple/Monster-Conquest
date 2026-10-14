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

import java.util.UUID;

/**
 * RTS 双击全选同类士兵。
 *
 *   mode 0 = 同种：同一实体种类（例如所有僵尸士兵）
 *   mode 1 = 同兵种：同一兵种类别（近战 / 远程 / 双形态 / 投掷 / 枪手；
 *                    枪手再按枪械种类细分：步枪 / 霰弹 / 狙击 / 火箭）
 */
public record RtsSelectSimilarPayload(UUID soldier, int mode) implements CustomPacketPayload {

    public static final Type<RtsSelectSimilarPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "rts_select_similar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsSelectSimilarPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RtsSelectSimilarPayload decode(RegistryFriendlyByteBuf buf) {
                    return new RtsSelectSimilarPayload(buf.readUUID(), buf.readVarInt());
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RtsSelectSimilarPayload p) {
                    buf.writeUUID(p.soldier());
                    buf.writeVarInt(p.mode());
                }
            };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RtsSelectSimilarPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());
            state.applyRtsSelectSimilar(player, payload.soldier(), payload.mode());
            PacketDistributor.sendToPlayer(player, new SyncRtsSelectionPayload(
                    state.collectRtsSelectedSoldierUuids(player),
                    state.getRtsMoveMarker()));
        });
    }
}
