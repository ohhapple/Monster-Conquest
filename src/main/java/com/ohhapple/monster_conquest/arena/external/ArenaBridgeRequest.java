package com.ohhapple.monster_conquest.arena.external;

import com.ohhapple.monster_conquest.arena.game.ArenaParticipant;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/** 主服写入共享目录、竞技子服读取的一次性房间转服请求。 */
public record ArenaBridgeRequest(
        UUID sessionId,
        int roomId,
        boolean autoFillAi,
        boolean integratedMain,
        String returnHost,
        int returnPort,
        String returnWorldName,
        long expiresAtMillis,
        String transferSecret,
        Map<UUID, String> transferNonces,
        List<ArenaParticipant> participants,
        List<UUID> integratedLocalPlayers) {

    public ArenaBridgeRequest {
        participants = List.copyOf(participants);
        transferNonces = Collections.unmodifiableMap(
                new LinkedHashMap<>(transferNonces));
        integratedLocalPlayers = List.copyOf(integratedLocalPlayers);
    }

    public static void write(Path path, ArenaBridgeRequest request) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sessionId", request.sessionId.toString());
        properties.setProperty("roomId", Integer.toString(request.roomId));
        properties.setProperty("autoFillAi", Boolean.toString(request.autoFillAi));
        properties.setProperty("integratedMain",
                Boolean.toString(request.integratedMain));
        properties.setProperty("returnHost", request.returnHost);
        properties.setProperty("returnPort", Integer.toString(request.returnPort));
        properties.setProperty("returnWorldName", request.returnWorldName);
        properties.setProperty("expiresAt", Long.toString(request.expiresAtMillis));
        properties.setProperty("transferSecret", request.transferSecret);
        properties.setProperty("participantCount",
                Integer.toString(request.participants.size()));
        for (int i = 0; i < request.participants.size(); i++) {
            ArenaParticipant participant = request.participants.get(i);
            properties.setProperty("participant." + i + ".id",
                    participant.id().toString());
            properties.setProperty("participant." + i + ".name",
                    participant.name());
            properties.setProperty("participant." + i + ".seat",
                    Integer.toString(participant.seatIndex()));
            properties.setProperty("participant." + i + ".nonce",
                    request.transferNonce(participant.id()));
        }
        properties.setProperty("integratedLocalCount",
                Integer.toString(request.integratedLocalPlayers.size()));
        for (int i = 0; i < request.integratedLocalPlayers.size(); i++) {
            properties.setProperty("integratedLocal." + i,
                    request.integratedLocalPlayers.get(i).toString());
        }

        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Monster Conquest arena request");
        }
        try {
            Files.move(temporary, path,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static ArenaBridgeRequest read(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        int count = Integer.parseInt(properties.getProperty("participantCount", "0"));
        List<ArenaParticipant> participants = new ArrayList<>(count);
        Map<UUID, String> transferNonces = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            UUID playerId = UUID.fromString(properties.getProperty(
                    "participant." + i + ".id"));
            participants.add(new ArenaParticipant(
                    playerId,
                    properties.getProperty("participant." + i + ".name", ""),
                    Integer.parseInt(properties.getProperty(
                            "participant." + i + ".seat",
                            Integer.toString(Math.min(7, i))))));
            transferNonces.put(playerId,
                    properties.getProperty(
                            "participant." + i + ".nonce", ""));
        }
        int localCount = Integer.parseInt(
                properties.getProperty("integratedLocalCount", "0"));
        List<UUID> integratedLocalPlayers = new ArrayList<>(localCount);
        for (int i = 0; i < localCount; i++) {
            integratedLocalPlayers.add(UUID.fromString(
                    properties.getProperty("integratedLocal." + i)));
        }
        return new ArenaBridgeRequest(
                UUID.fromString(properties.getProperty("sessionId")),
                Integer.parseInt(properties.getProperty("roomId")),
                Boolean.parseBoolean(properties.getProperty("autoFillAi", "false")),
                Boolean.parseBoolean(properties.getProperty(
                        "integratedMain", "false")),
                properties.getProperty("returnHost", "127.0.0.1"),
                Integer.parseInt(properties.getProperty("returnPort", "25565")),
                properties.getProperty("returnWorldName", ""),
                Long.parseLong(properties.getProperty("expiresAt", "0")),
                properties.getProperty("transferSecret", ""),
                transferNonces,
                participants,
                integratedLocalPlayers);
    }

    public String transferNonce(UUID playerId) {
        return transferNonces.getOrDefault(playerId, "");
    }

    public boolean contains(UUID playerId) {
        return participants.stream().anyMatch(participant ->
                participant.id().equals(playerId));
    }
}
