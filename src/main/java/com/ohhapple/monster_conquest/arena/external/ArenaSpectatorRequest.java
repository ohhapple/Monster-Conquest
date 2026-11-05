package com.ohhapple.monster_conquest.arena.external;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

/** 主服发给竞技子服的一次性只读观战票据。 */
public record ArenaSpectatorRequest(
        UUID sessionId, int roomId, UUID playerId,
        String playerName, String transferNonce,
        long expiresAtMillis) {

    public static void write(Path path, ArenaSpectatorRequest request)
            throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sessionId", request.sessionId().toString());
        properties.setProperty("roomId", Integer.toString(request.roomId()));
        properties.setProperty("playerId", request.playerId().toString());
        properties.setProperty("playerName", request.playerName());
        properties.setProperty("transferNonce", request.transferNonce());
        properties.setProperty("expiresAt",
                Long.toString(request.expiresAtMillis()));
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Monster Conquest arena spectator");
        }
        try {
            Files.move(temporary, path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static ArenaSpectatorRequest read(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        return new ArenaSpectatorRequest(
                UUID.fromString(required(properties, "sessionId")),
                Integer.parseInt(required(properties, "roomId")),
                UUID.fromString(required(properties, "playerId")),
                required(properties, "playerName"),
                required(properties, "transferNonce"),
                Long.parseLong(required(properties, "expiresAt")));
    }

    public static Path acceptedMarker(Path requestPath) {
        return requestPath.resolveSibling(
                requestPath.getFileName() + ".accepted");
    }

    public static void writeAcceptedMarker(Path requestPath)
            throws IOException {
        Path marker = acceptedMarker(requestPath);
        Files.writeString(marker,
                Long.toString(System.currentTimeMillis()),
                StandardCharsets.US_ASCII);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Missing arena spectator property " + key);
        }
        return value;
    }
}
