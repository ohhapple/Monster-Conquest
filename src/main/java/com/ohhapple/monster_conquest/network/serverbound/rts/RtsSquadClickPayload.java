package com.ohhapple.monster_conquest.network.serverbound.rts;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 客户端 → 服务端：RTS 单击士兵。
 *
 *   · squadId > 0 → 选中整队；
 *   · squadId == 0（无小队士兵 / 骑兵战马解析出的骑手）→ 只选中 soldierUuid 这一名士兵。
 */
public record RtsSquadClickPayload(int squadId, boolean additive, @Nullable UUID soldierUuid)
        implements CustomPacketPayload {

    public static final Type<RtsSquadClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "rts_squad_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsSquadClickPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RtsSquadClickPayload decode(RegistryFriendlyByteBuf buf) {
                    int squadId = ByteBufCodecs.VAR_INT.decode(buf);
                    boolean additive = buf.readBoolean();
                    boolean hasSoldier = buf.readBoolean();
                    UUID soldier = hasSoldier ? buf.readUUID() : null;
                    return new RtsSquadClickPayload(squadId, additive, soldier);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, RtsSquadClickPayload p) {
                    ByteBufCodecs.VAR_INT.encode(buf, p.squadId());
                    buf.writeBoolean(p.additive());
                    boolean hasSoldier = p.soldierUuid() != null;
                    buf.writeBoolean(hasSoldier);
                    if (hasSoldier) buf.writeUUID(p.soldierUuid());
                }
            };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RtsSquadClickPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());
            state.applyRtsSquadClick(player, payload.squadId(), payload.additive(),
                    payload.soldierUuid());
            PacketDistributor.sendToPlayer(player, new SyncRtsSelectionPayload(
                    state.collectRtsSelectedSoldierUuids(player),
                    state.getRtsMoveMarker()));
        });
    }
}
