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

/** 观战者可见的公开对局状态，不包含任何玩家私有经济或仓库信息。 */
public record ArenaSpectatorSnapshotPayload(
        boolean active,
        boolean openGui,
        int roomId,
        int round,
        int phase,
        int phaseTicksLeft,
        int phaseTicksTotal,
        UUID targetId,
        String targetName,
        int targetHealth,
        int targetLevel,
        boolean targetAlive,
        int viewBoardIndex,
        String opponentName,
        UUID opponentId,
        int lastDamage,
        String championName,
        List<SpectatorPiece> boardPieces,
        List<SpectatorPlayerStatus> players)
        implements CustomPacketPayload {

    public static final Type<ArenaSpectatorSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_spectator_snapshot"));

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ArenaSpectatorSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ArenaSpectatorSnapshotPayload decode(
                RegistryFriendlyByteBuf buf) {
            boolean active = buf.readBoolean();
            boolean openGui = buf.readBoolean();
            int roomId = buf.readVarInt();
            int round = buf.readVarInt();
            int phase = buf.readVarInt();
            int phaseLeft = buf.readVarInt();
            int phaseTotal = buf.readVarInt();
            UUID targetId = buf.readUUID();
            String targetName = buf.readUtf(64);
            int targetHealth = buf.readVarInt();
            int targetLevel = buf.readVarInt();
            boolean targetAlive = buf.readBoolean();
            int viewBoardIndex = buf.readVarInt();
            String opponentName = buf.readUtf(64);
            UUID opponentId = buf.readUUID();
            int lastDamage = buf.readVarInt();
            String championName = buf.readUtf(64);

            int pieceCount = buf.readVarInt();
            List<SpectatorPiece> pieces =
                    new ArrayList<>(pieceCount);
            for (int i = 0; i < pieceCount; i++) {
                UUID id = buf.readUUID();
                int unitIndex = buf.readVarInt();
                int star = buf.readVarInt();
                int boardSlot = buf.readVarInt();
                int equipmentCount = buf.readVarInt();
                List<Integer> equipment =
                        new ArrayList<>(equipmentCount);
                for (int j = 0; j < equipmentCount; j++) {
                    equipment.add(buf.readVarInt());
                }
                pieces.add(new SpectatorPiece(
                        id, unitIndex, star, boardSlot, equipment));
            }

            int playerCount = buf.readVarInt();
            List<SpectatorPlayerStatus> players =
                    new ArrayList<>(playerCount);
            for (int i = 0; i < playerCount; i++) {
                players.add(new SpectatorPlayerStatus(
                        buf.readUUID(),
                        buf.readUtf(64),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readBoolean(),
                        buf.readBoolean(),
                        buf.readVarInt()));
            }
            return new ArenaSpectatorSnapshotPayload(
                    active, openGui, roomId, round, phase,
                    phaseLeft, phaseTotal, targetId, targetName,
                    targetHealth, targetLevel, targetAlive,
                    viewBoardIndex,
                    opponentName, opponentId, lastDamage,
                    championName,
                    pieces, players);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buf,
                           ArenaSpectatorSnapshotPayload payload) {
            buf.writeBoolean(payload.active());
            buf.writeBoolean(payload.openGui());
            buf.writeVarInt(payload.roomId());
            buf.writeVarInt(payload.round());
            buf.writeVarInt(payload.phase());
            buf.writeVarInt(payload.phaseTicksLeft());
            buf.writeVarInt(payload.phaseTicksTotal());
            buf.writeUUID(payload.targetId());
            buf.writeUtf(payload.targetName(), 64);
            buf.writeVarInt(payload.targetHealth());
            buf.writeVarInt(payload.targetLevel());
            buf.writeBoolean(payload.targetAlive());
            buf.writeVarInt(payload.viewBoardIndex());
            buf.writeUtf(payload.opponentName(), 64);
            buf.writeUUID(payload.opponentId());
            buf.writeVarInt(payload.lastDamage());
            buf.writeUtf(payload.championName(), 64);

            buf.writeVarInt(payload.boardPieces().size());
            for (SpectatorPiece piece : payload.boardPieces()) {
                buf.writeUUID(piece.id());
                buf.writeVarInt(piece.unitIndex());
                buf.writeVarInt(piece.star());
                buf.writeVarInt(piece.boardSlot());
                buf.writeVarInt(piece.equipment().size());
                for (int equipment : piece.equipment()) {
                    buf.writeVarInt(equipment);
                }
            }

            buf.writeVarInt(payload.players().size());
            for (SpectatorPlayerStatus status : payload.players()) {
                buf.writeUUID(status.id());
                buf.writeUtf(status.name(), 64);
                buf.writeVarInt(status.boardIndex());
                buf.writeVarInt(status.health());
                buf.writeVarInt(status.level());
                buf.writeBoolean(status.alive());
                buf.writeBoolean(status.ai());
                buf.writeVarInt(status.battleResult());
            }
        }
    };

    public ArenaSpectatorSnapshotPayload {
        boardPieces = List.copyOf(boardPieces);
        players = List.copyOf(players);
        if (championName == null) championName = "";
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record SpectatorPiece(
            UUID id,
            int unitIndex,
            int star,
            int boardSlot,
            List<Integer> equipment) {

        public SpectatorPiece {
            equipment = List.copyOf(equipment);
        }
    }

    public record SpectatorPlayerStatus(
            UUID id,
            String name,
            int boardIndex,
            int health,
            int level,
            boolean alive,
            boolean ai,
            int battleResult) {
    }
}
