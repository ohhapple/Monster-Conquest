package com.ohhapple.monster_conquest.arena;

import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbyNoticePayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload.RoomSummary;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload.RoomView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload.SeatView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCareerPayload;
import com.ohhapple.monster_conquest.network.serverbound.arena.ArenaActionPayload;
import com.ohhapple.monster_conquest.arena.external.ExternalArenaMainCoordinator;
import com.ohhapple.monster_conquest.arena.game.ArenaRequestLimiter;
import com.ohhapple.monster_conquest.arena.game.ArenaParticipant;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/**
 * 自走棋大厅的服务端状态所有者。房间是单次服务器运行期内的临时状态，
 * 玩家席位、准备、权限和开始条件都在这里校验，客户端只消费快照。
 */
public final class ArenaLobbyManager {

    private static final int MAX_ROOMS = 2048;
    private static final int MAX_ROOM_ID = 2048;
    private static final long SEAT_CHANGE_COOLDOWN_MS = 1_000L;
    private static final ArenaRequestLimiter LOBBY_REQUEST_LIMITER =
            new ArenaRequestLimiter(10.0, 15.0);

    private static final Map<Integer, Room> ROOMS = new LinkedHashMap<>();
    private static final Map<UUID, Integer> MEMBERSHIPS = new HashMap<>();
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static final Map<UUID, Long> SEAT_CHANGE_COOLDOWN =
            new HashMap<>();
    private static final Queue<Integer> RECYCLED_ROOM_IDS = new PriorityQueue<>();

    @Nullable private static MinecraftServer server;
    private static int nextRoomId = 1;

    private ArenaLobbyManager() {
    }

    public static void setServer(@Nullable MinecraftServer newServer) {
        setServer(newServer, false);
    }

    public static void setServer(@Nullable MinecraftServer newServer,
                                 boolean preserveRooms) {
        server = newServer;
        if (preserveRooms) return;
        LOBBY_REQUEST_LIMITER.clear();
        ROOMS.clear();
        MEMBERSHIPS.clear();
        VIEWERS.clear();
        SEAT_CHANGE_COOLDOWN.clear();
        RECYCLED_ROOM_IDS.clear();
        nextRoomId = 1;
    }

    public static void handleAction(ServerPlayer player, int action, int roomId,
                                    UUID targetId, int targetIndex) {
        if (server == null) return;
        if (action == ArenaActionPayload.CLOSE) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        if (!LOBBY_REQUEST_LIMITER.allow(player.getUUID())) return;
        switch (action) {
            case ArenaActionPayload.OPEN -> {
                VIEWERS.add(player.getUUID());
                if (!ArenaConfig.arenaServerEnabled) {
                    notice(player,
                            "gui.monster_conquest.arena.notice.arena_disabled");
                }
                sendSnapshot(player);
            }
            case ArenaActionPayload.CREATE -> createRoom(player);
            case ArenaActionPayload.JOIN -> joinRoom(player, roomId);
            case ArenaActionPayload.LEAVE -> leaveRoom(player, true);
            case ArenaActionPayload.SET_READY -> setReady(player, roomId);
            case ArenaActionPayload.TOGGLE_AUTO_FILL -> toggleAutoFill(player, roomId);
            case ArenaActionPayload.KICK -> kick(player, roomId, targetId);
            case ArenaActionPayload.START -> start(player, roomId);
            case ArenaActionPayload.CHANGE_SEAT ->
                    changeSeat(player, roomId, targetIndex);
            case ArenaActionPayload.VIEW_CAREER ->
                    viewCareer(player, targetId);
            case ArenaActionPayload.SPECTATE ->
                    spectateRoom(player, roomId);
            default -> {
            }
        }
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        LOBBY_REQUEST_LIMITER.remove(player.getUUID());
        VIEWERS.remove(player.getUUID());
        SEAT_CHANGE_COOLDOWN.remove(player.getUUID());
        if (ExternalArenaMainCoordinator.isTransferPending(player.getUUID())) {
            return;
        }
        if (MEMBERSHIPS.containsKey(player.getUUID())) {
            removeFromRoom(player);
            broadcastSnapshots();
        }
    }

    private static void createRoom(ServerPlayer player) {
        if (MEMBERSHIPS.containsKey(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.already_in_room");
            return;
        }
        if (ROOMS.size() >= MAX_ROOMS) {
            notice(player, "gui.monster_conquest.arena.notice.rooms_full");
            return;
        }

        int roomId = allocateRoomId();
        if (roomId <= 0) {
            notice(player, "gui.monster_conquest.arena.notice.rooms_full");
            return;
        }

        Room room = new Room(roomId, player.getUUID(), playerName(player));
        room.seats[0] = new Participant(player.getUUID(), playerName(player));
        ROOMS.put(roomId, room);
        MEMBERSHIPS.put(player.getUUID(), roomId);
        broadcastSnapshots();
    }

    private static void joinRoom(ServerPlayer player, int roomId) {
        if (MEMBERSHIPS.containsKey(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.already_in_room");
            return;
        }
        Room room = ROOMS.get(roomId);
        if (room == null) {
            notice(player, "gui.monster_conquest.arena.notice.room_not_found");
            return;
        }
        if (room.started) {
            notice(player, "gui.monster_conquest.arena.notice.already_started");
            return;
        }

        int seat = room.firstEmptySeat();
        if (seat < 0) {
            notice(player, "gui.monster_conquest.arena.notice.room_full");
            return;
        }

        room.seats[seat] = new Participant(player.getUUID(), playerName(player));
        MEMBERSHIPS.put(player.getUUID(), roomId);
        broadcastSnapshots();
    }

    private static void leaveRoom(ServerPlayer player, boolean notify) {
        if (!MEMBERSHIPS.containsKey(player.getUUID())) {
            if (notify) notice(player, "gui.monster_conquest.arena.notice.not_in_room");
            return;
        }
        Integer roomId = MEMBERSHIPS.get(player.getUUID());
        Room room = roomId == null ? null : ROOMS.get(roomId);
        if (room != null && room.started) {
            notice(player,
                    "gui.monster_conquest.arena.notice.match_started");
            return;
        }
        removeFromRoom(player);
        SEAT_CHANGE_COOLDOWN.remove(player.getUUID());
        broadcastSnapshots();
    }

    private static void removeFromRoom(ServerPlayer player) {
        Integer roomId = MEMBERSHIPS.remove(player.getUUID());
        if (roomId == null) return;
        Room room = ROOMS.get(roomId);
        if (room == null) return;

        room.remove(player.getUUID());
        if (room.playerCount() == 0) {
            ROOMS.remove(roomId);
            RECYCLED_ROOM_IDS.add(roomId);
            return;
        }
        if (room.hostId.equals(player.getUUID())) {
            Participant nextHost = room.firstParticipant();
            if (nextHost != null) room.hostId = nextHost.playerId;
        }
    }

    private static void setReady(ServerPlayer player, int roomId) {
        Room room = currentRoomFor(player, roomId);
        if (room == null) return;
        if (room.started) {
            notice(player, "gui.monster_conquest.arena.notice.already_started");
            return;
        }
        Participant participant = room.find(player.getUUID());
        if (participant == null) {
            notice(player, "gui.monster_conquest.arena.notice.not_in_room");
            return;
        }
        if (room.hostId.equals(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.host_does_not_ready");
            return;
        }
        participant.ready = !participant.ready;
        broadcastSnapshots();
    }

    private static void viewCareer(ServerPlayer viewer, UUID targetId) {
        Room room = currentRoom(viewer.getUUID());
        if (room == null) {
            notice(viewer, "gui.monster_conquest.arena.notice.not_in_room");
            return;
        }
        Participant target = room.find(targetId);
        if (target == null) {
            notice(viewer,
                    "gui.monster_conquest.arena.notice.target_not_in_room");
            return;
        }
        PacketDistributor.sendToPlayer(viewer, new ArenaCareerPayload(
                target.playerId,
                target.playerName,
                ArenaStatsData.get(server).career(target.playerId)));
    }

    private static void spectateRoom(ServerPlayer player, int roomId) {
        if (MEMBERSHIPS.containsKey(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.already_in_room");
            return;
        }
        Room room = ROOMS.get(roomId);
        if (room == null) {
            notice(player, "gui.monster_conquest.arena.notice.room_not_found");
            return;
        }
        if (!room.started) {
            notice(player, "gui.monster_conquest.arena.notice.room_not_started");
            return;
        }
        if (!ExternalArenaMainCoordinator.requestSpectator(player, roomId)) {
            notice(player,
                    "gui.monster_conquest.arena.notice.spectate_unavailable");
        }
    }

    private static void changeSeat(ServerPlayer player, int roomId,
                                   int targetSeat) {
        Room room = currentRoomFor(player, roomId);
        if (room == null) return;
        if (room.started) {
            notice(player, "gui.monster_conquest.arena.notice.already_started");
            return;
        }
        if (targetSeat < 0
                || targetSeat >= ArenaLobbySnapshotPayload.MAX_SEATS) {
            notice(player, "gui.monster_conquest.arena.notice.seat_invalid");
            return;
        }
        Participant participant = room.find(player.getUUID());
        int currentSeat = room.seatIndexOf(player.getUUID());
        if (participant == null || currentSeat < 0) {
            notice(player, "gui.monster_conquest.arena.notice.not_in_room");
            return;
        }

        long now = System.currentTimeMillis();
        long availableAt = SEAT_CHANGE_COOLDOWN.getOrDefault(
                player.getUUID(), 0L);
        if (now < availableAt) {
            notice(player, "gui.monster_conquest.arena.notice.seat_cooldown");
            return;
        }
        SEAT_CHANGE_COOLDOWN.put(
                player.getUUID(), now + SEAT_CHANGE_COOLDOWN_MS);

        if (targetSeat == currentSeat) {
            notice(player, "gui.monster_conquest.arena.notice.seat_unchanged");
            return;
        }
        if (room.seats[targetSeat] != null) {
            notice(player, "gui.monster_conquest.arena.notice.seat_occupied");
            return;
        }

        room.seats[currentSeat] = null;
        room.seats[targetSeat] = participant;
        broadcastSnapshots();
    }

    private static void toggleAutoFill(ServerPlayer player, int roomId) {
        Room room = currentRoomFor(player, roomId);
        if (room == null) return;
        if (!room.hostId.equals(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.host_only");
            return;
        }
        if (room.started) {
            notice(player, "gui.monster_conquest.arena.notice.already_started");
            return;
        }
        room.autoFillAi = !room.autoFillAi;
        broadcastSnapshots();
    }

    private static void kick(ServerPlayer player, int roomId, UUID targetId) {
        Room room = currentRoomFor(player, roomId);
        if (room == null) return;
        if (!room.hostId.equals(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.host_only");
            return;
        }
        if (room.started) {
            notice(player, "gui.monster_conquest.arena.notice.already_started");
            return;
        }
        if (targetId.equals(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.cannot_kick_self");
            return;
        }
        if (targetId.equals(room.hostId)) {
            notice(player, "gui.monster_conquest.arena.notice.cannot_kick_host");
            return;
        }

        Participant target = room.remove(targetId);
        if (target == null) {
            notice(player, "gui.monster_conquest.arena.notice.target_not_in_room");
            return;
        }
        MEMBERSHIPS.remove(targetId);
        SEAT_CHANGE_COOLDOWN.remove(targetId);

        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetId);
        if (targetPlayer != null) {
            notice(targetPlayer, "gui.monster_conquest.arena.notice.you_were_kicked");
        }
        broadcastSnapshots();
    }

    private static void start(ServerPlayer player, int roomId) {
        Room room = currentRoomFor(player, roomId);
        if (room == null) return;
        if (!room.hostId.equals(player.getUUID())) {
            notice(player, "gui.monster_conquest.arena.notice.host_only");
            return;
        }
        if (!ArenaConfig.arenaServerEnabled) {
            notice(player,
                    "gui.monster_conquest.arena.notice.arena_disabled");
            return;
        }
        if (room.started) {
            notice(player, "gui.monster_conquest.arena.notice.already_started");
            return;
        }
        if (!room.allOtherPlayersReady()) {
            notice(player, "gui.monster_conquest.arena.notice.not_all_ready");
            return;
        }

        List<ArenaParticipant> participants = new ArrayList<>();
        for (int seat = 0; seat < room.seats.length; seat++) {
            Participant participant = room.seats[seat];
            if (participant == null) continue;
            participants.add(new ArenaParticipant(
                    participant.playerId, participant.playerName, seat));
        }
        if (!ExternalArenaMainCoordinator.start(
                server, room.roomId, participants, room.autoFillAi)) {
            notice(player, "gui.monster_conquest.arena.notice.start_failed");
            return;
        }

        room.started = true;
        for (Participant participant : room.participants()) {
            ServerPlayer member = server.getPlayerList().getPlayer(participant.playerId);
            if (member != null) {
                notice(member, "gui.monster_conquest.arena.notice.room_started");
            }
        }
        broadcastSnapshots();
    }

    public static void onMatchFinished(int roomId) {
        Room room = ROOMS.remove(roomId);
        if (room == null) return;
        for (Participant participant : room.seats) {
            if (participant == null) continue;
            MEMBERSHIPS.remove(participant.playerId);
            SEAT_CHANGE_COOLDOWN.remove(participant.playerId);
        }
        RECYCLED_ROOM_IDS.add(roomId);
        broadcastSnapshots();
    }

    /** 玩家从竞技子服返回主服后释放其房间席位。 */
    public static void onMatchParticipantReturned(int roomId, UUID playerId) {
        Room room = ROOMS.get(roomId);
        if (room == null) return;
        if (room.remove(playerId) == null) return;
        MEMBERSHIPS.remove(playerId);
        SEAT_CHANGE_COOLDOWN.remove(playerId);
        if (!room.started) {
            if (room.playerCount() == 0) {
                ROOMS.remove(roomId);
                RECYCLED_ROOM_IDS.add(roomId);
            } else if (room.hostId.equals(playerId)) {
                Participant nextHost = room.firstParticipant();
                if (nextHost != null) room.hostId = nextHost.playerId;
            }
        }
        broadcastSnapshots();
    }

    @Nullable
    private static Room currentRoomFor(ServerPlayer player, int requestedRoomId) {
        Integer roomId = MEMBERSHIPS.get(player.getUUID());
        if (roomId == null) {
            notice(player, "gui.monster_conquest.arena.notice.not_in_room");
            return null;
        }
        if (roomId != requestedRoomId) {
            notice(player, "gui.monster_conquest.arena.notice.room_changed");
            return null;
        }
        Room room = ROOMS.get(roomId);
        if (room == null) {
            MEMBERSHIPS.remove(player.getUUID());
            notice(player, "gui.monster_conquest.arena.notice.room_not_found");
        }
        return room;
    }

    private static int allocateRoomId() {
        Integer recycled = RECYCLED_ROOM_IDS.poll();
        if (recycled != null) return recycled;
        if (nextRoomId > MAX_ROOM_ID) return -1;
        return nextRoomId++;
    }

    public static void sendSnapshot(ServerPlayer player) {
        if (server == null) return;

        List<RoomSummary> summaries = new ArrayList<>(ROOMS.size());
        for (Room room : ROOMS.values()) {
            summaries.add(new RoomSummary(
                    room.roomId,
                    room.hostName(server),
                    room.playerCount(),
                    room.autoFillAi,
                    room.started));
        }
        summaries.sort(Comparator.comparingInt(RoomSummary::roomId).reversed());

        Room current = currentRoom(player.getUUID());
        RoomView currentView = current == null ? null : toView(current);
        ArenaCareer career = ArenaStatsData.get(server).career(player.getUUID());
        PacketDistributor.sendToPlayer(player,
                new ArenaLobbySnapshotPayload(summaries, currentView, career));
    }

    private static void broadcastSnapshots() {
        if (server == null) return;
        for (UUID viewerId : new ArrayList<>(VIEWERS)) {
            ServerPlayer viewer = server.getPlayerList().getPlayer(viewerId);
            if (viewer == null) {
                VIEWERS.remove(viewerId);
            } else {
                sendSnapshot(viewer);
            }
        }
    }

    private static RoomView toView(Room room) {
        List<SeatView> seats = new ArrayList<>(ArenaLobbySnapshotPayload.MAX_SEATS);
        for (int i = 0; i < room.seats.length; i++) {
            Participant participant = room.seats[i];
            seats.add(participant == null
                    ? new SeatView(i, null, "", false)
                    : new SeatView(
                            i,
                            participant.playerId,
                            participant.playerName,
                            participant.ready));
        }
        return new RoomView(room.roomId, room.hostId, room.autoFillAi, room.started, seats);
    }

    @Nullable
    private static Room currentRoom(UUID playerId) {
        Integer roomId = MEMBERSHIPS.get(playerId);
        return roomId == null ? null : ROOMS.get(roomId);
    }

    private static void notice(ServerPlayer player, String translationKey) {
        PacketDistributor.sendToPlayer(player, new ArenaLobbyNoticePayload(translationKey));
    }

    private static String playerName(ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    private static final class Room {
        private final int roomId;
        private UUID hostId;
        private boolean autoFillAi;
        private boolean started;
        private String hostName;
        private final Participant[] seats =
                new Participant[ArenaLobbySnapshotPayload.MAX_SEATS];

        private Room(int roomId, UUID hostId, String hostName) {
            this.roomId = roomId;
            this.hostId = hostId;
            this.hostName = hostName;
        }

        private int playerCount() {
            int count = 0;
            for (Participant seat : seats) {
                if (seat != null) count++;
            }
            return count;
        }

        private int firstEmptySeat() {
            for (int i = 0; i < seats.length; i++) {
                if (seats[i] == null) return i;
            }
            return -1;
        }

        @Nullable
        private Participant find(UUID playerId) {
            for (Participant seat : seats) {
                if (seat != null && seat.playerId.equals(playerId)) return seat;
            }
            return null;
        }

        private int seatIndexOf(UUID playerId) {
            for (int i = 0; i < seats.length; i++) {
                Participant seat = seats[i];
                if (seat != null && seat.playerId.equals(playerId)) return i;
            }
            return -1;
        }

        @Nullable
        private Participant remove(UUID playerId) {
            for (int i = 0; i < seats.length; i++) {
                Participant seat = seats[i];
                if (seat != null && seat.playerId.equals(playerId)) {
                    seats[i] = null;
                    return seat;
                }
            }
            return null;
        }

        @Nullable
        private Participant firstParticipant() {
            for (Participant seat : seats) {
                if (seat != null) return seat;
            }
            return null;
        }

        private boolean allOtherPlayersReady() {
            for (Participant seat : seats) {
                if (seat != null && !seat.playerId.equals(hostId) && !seat.ready) {
                    return false;
                }
            }
            return true;
        }

        private List<Participant> participants() {
            List<Participant> result = new ArrayList<>(ArenaLobbySnapshotPayload.MAX_SEATS);
            for (Participant seat : seats) {
                if (seat != null) result.add(seat);
            }
            return result;
        }

        private String hostName(MinecraftServer server) {
            Participant host = find(hostId);
            if (host != null) {
                hostName = host.playerName;
                return hostName;
            }
            ServerPlayer currentHost = server.getPlayerList().getPlayer(hostId);
            if (currentHost != null) {
                hostName = playerName(currentHost);
            }
            return hostName == null ? "" : hostName;
        }
    }

    private static final class Participant {
        private final UUID playerId;
        private final String playerName;
        private boolean ready;

        private Participant(UUID playerId, String playerName) {
            this.playerId = playerId;
            this.playerName = playerName;
            this.ready = true;
        }
    }
}
