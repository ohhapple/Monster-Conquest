package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.ArenaCareer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自走棋大厅的单人快照。房间列表是所有玩家共享的数据，
 * 当前房间与生涯数据则按收包玩家单独生成。
 */
public record ArenaLobbySnapshotPayload(
        List<RoomSummary> rooms,
        @Nullable RoomView currentRoom,
        ArenaCareer career) implements CustomPacketPayload {

    public static final int MAX_SEATS = 8;

    public static final Type<ArenaLobbySnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_lobby_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaLobbySnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArenaLobbySnapshotPayload decode(RegistryFriendlyByteBuf buf) {
                    int roomCount = buf.readVarInt();
                    List<RoomSummary> rooms = new ArrayList<>(roomCount);
                    for (int i = 0; i < roomCount; i++) {
                        rooms.add(decodeRoomSummary(buf));
                    }

                    RoomView currentRoom = buf.readBoolean() ? decodeRoom(buf) : null;
                    ArenaCareer career = decodeCareer(buf);
                    return new ArenaLobbySnapshotPayload(rooms, currentRoom, career);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ArenaLobbySnapshotPayload payload) {
                    buf.writeVarInt(payload.rooms().size());
                    for (RoomSummary room : payload.rooms()) {
                        encodeRoomSummary(buf, room);
                    }

                    buf.writeBoolean(payload.currentRoom() != null);
                    if (payload.currentRoom() != null) {
                        encodeRoom(buf, payload.currentRoom());
                    }
                    encodeCareer(buf, payload.career());
                }
            };

    public ArenaLobbySnapshotPayload {
        rooms = List.copyOf(rooms);
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record RoomSummary(
            int roomId,
            String hostName,
            int players,
            boolean autoFillAi,
            boolean started) {
    }

    public record RoomView(
            int roomId,
            UUID hostId,
            boolean autoFillAi,
            boolean started,
            List<SeatView> seats) {

        public RoomView {
            seats = List.copyOf(seats);
        }
    }

    public record SeatView(
            int index,
            @Nullable UUID playerId,
            String playerName,
            boolean ready) {
    }

    private static void encodeRoomSummary(RegistryFriendlyByteBuf buf, RoomSummary room) {
        buf.writeVarInt(room.roomId());
        buf.writeUtf(room.hostName(), 64);
        buf.writeVarInt(room.players());
        buf.writeBoolean(room.autoFillAi());
        buf.writeBoolean(room.started());
    }

    private static RoomSummary decodeRoomSummary(RegistryFriendlyByteBuf buf) {
        return new RoomSummary(
                buf.readVarInt(),
                buf.readUtf(64),
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean());
    }

    private static void encodeRoom(RegistryFriendlyByteBuf buf, RoomView room) {
        buf.writeVarInt(room.roomId());
        buf.writeUUID(room.hostId());
        buf.writeBoolean(room.autoFillAi());
        buf.writeBoolean(room.started());
        buf.writeVarInt(room.seats().size());
        for (SeatView seat : room.seats()) {
            buf.writeVarInt(seat.index());
            buf.writeBoolean(seat.playerId() != null);
            if (seat.playerId() != null) buf.writeUUID(seat.playerId());
            buf.writeUtf(seat.playerName(), 64);
            buf.writeBoolean(seat.ready());
        }
    }

    private static RoomView decodeRoom(RegistryFriendlyByteBuf buf) {
        int roomId = buf.readVarInt();
        UUID hostId = buf.readUUID();
        boolean autoFillAi = buf.readBoolean();
        boolean started = buf.readBoolean();
        int seatCount = buf.readVarInt();
        List<SeatView> seats = new ArrayList<>(seatCount);
        for (int i = 0; i < seatCount; i++) {
            int index = buf.readVarInt();
            UUID playerId = buf.readBoolean() ? buf.readUUID() : null;
            String playerName = buf.readUtf(64);
            boolean ready = buf.readBoolean();
            seats.add(new SeatView(index, playerId, playerName, ready));
        }
        return new RoomView(roomId, hostId, autoFillAi, started, seats);
    }

    private static void encodeCareer(RegistryFriendlyByteBuf buf, ArenaCareer career) {
        buf.writeVarInt(career.matches());
        buf.writeVarInt(career.wins());
        buf.writeVarInt(career.topHalf());
        buf.writeVarInt(career.currentWinStreak());
        buf.writeVarInt(career.bestWinStreak());
        buf.writeVarInt(career.recentMatches().size());
        for (ArenaCareer.Match match : career.recentMatches()) {
            buf.writeVarInt(match.roomId());
            buf.writeVarInt(match.placement());
            buf.writeVarInt(match.participants());
            buf.writeLong(match.finishedAtEpochMillis());
        }
    }

    private static ArenaCareer decodeCareer(RegistryFriendlyByteBuf buf) {
        int matches = buf.readVarInt();
        int wins = buf.readVarInt();
        int topHalf = buf.readVarInt();
        int currentWinStreak = buf.readVarInt();
        int bestWinStreak = buf.readVarInt();
        int matchCount = buf.readVarInt();
        List<ArenaCareer.Match> recentMatches = new ArrayList<>(matchCount);
        for (int i = 0; i < matchCount; i++) {
            recentMatches.add(new ArenaCareer.Match(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readLong()));
        }
        return new ArenaCareer(
                matches,
                wins,
                topHalf,
                currentWinStreak,
                bestWinStreak,
                recentMatches);
    }
}
