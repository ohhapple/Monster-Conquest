package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 仅发送给本人界面的弈子回合表现统计。 */
public record ArenaCombatStatsPayload(
        boolean active,
        int roomId,
        int round,
        List<UnitStats> units) implements CustomPacketPayload {

    public static final Type<ArenaCombatStatsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_combat_stats"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ArenaCombatStatsPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArenaCombatStatsPayload decode(
                        RegistryFriendlyByteBuf buf) {
                    boolean active = buf.readBoolean();
                    int roomId = buf.readVarInt();
                    int round = buf.readVarInt();
                    int count = buf.readVarInt();
                    List<UnitStats> units =
                            new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        units.add(new UnitStats(
                                buf.readUUID(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble(),
                                buf.readDouble()));
                    }
                    return new ArenaCombatStatsPayload(
                            active, roomId, round, units);
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buf,
                        ArenaCombatStatsPayload payload) {
                    buf.writeBoolean(payload.active());
                    buf.writeVarInt(payload.roomId());
                    buf.writeVarInt(payload.round());
                    buf.writeVarInt(payload.units().size());
                    for (UnitStats unit : payload.units()) {
                        buf.writeUUID(unit.pieceId());
                        buf.writeDouble(unit.physicalDealt());
                        buf.writeDouble(unit.magicDealt());
                        buf.writeDouble(unit.trueDealt());
                        buf.writeDouble(unit.healing());
                        buf.writeDouble(unit.shieldHealing());
                        buf.writeDouble(unit.physicalTaken());
                        buf.writeDouble(unit.magicTaken());
                        buf.writeDouble(unit.trueTaken());
                    }
                }
            };

    public ArenaCombatStatsPayload {
        units = List.copyOf(units);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record UnitStats(
            UUID pieceId,
            double physicalDealt,
            double magicDealt,
            double trueDealt,
            double healing,
            double shieldHealing,
            double physicalTaken,
            double magicTaken,
            double trueTaken) {

        public double totalDealt() {
            return physicalDealt + magicDealt + trueDealt;
        }

        public double totalTaken() {
            return physicalTaken + magicTaken + trueTaken;
        }

        public double totalHealing() {
            return healing + shieldHealing;
        }
    }
}
