package com.ohhapple.monster_conquest.network.serverbound.rts;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record RtsCommandPayload(double x, double y, double z,
                                @Nullable UUID targetUuid)
        implements CustomPacketPayload {

    public static final Type<RtsCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "rts_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RtsCommandPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public RtsCommandPayload decode(RegistryFriendlyByteBuf buf) {
                    double x = buf.readDouble();
                    double y = buf.readDouble();
                    double z = buf.readDouble();
                    boolean has = buf.readBoolean();
                    UUID uuid = has ? buf.readUUID() : null;
                    return new RtsCommandPayload(x, y, z, uuid);
                }
                @Override
                public void encode(RegistryFriendlyByteBuf buf, RtsCommandPayload p) {
                    buf.writeDouble(p.x());
                    buf.writeDouble(p.y());
                    buf.writeDouble(p.z());
                    boolean has = p.targetUuid() != null;
                    buf.writeBoolean(has);
                    if (has) buf.writeUUID(p.targetUuid());
                }
            };

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RtsCommandPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            var state = PlayerArmyManager.get(player.getUUID());

            LivingEntity target = null;
            if (payload.targetUuid() != null
                    && player.level() instanceof ServerLevel sl) {
                Entity e = sl.getEntities().get(payload.targetUuid());
                if (e instanceof LivingEntity le && le.isAlive()) target = le;
            }

            Vec3 dest = new Vec3(payload.x(), payload.y(), payload.z());
            state.applyRtsCommand(player, target, dest);

            // 回发最新选中 + marker
            PacketDistributor.sendToPlayer(player, new SyncRtsSelectionPayload(
                    state.collectRtsSelectedSoldierUuids(player),
                    state.getRtsMoveMarker()));
        });
    }
}