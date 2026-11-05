package com.ohhapple.monster_conquest.arena.external;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** 竞技子服写回主服的单局结算，主服据此恢复房间并更新战绩。 */
public record ArenaBridgeResult(
        UUID sessionId,
        int roomId,
        List<PlayerPlacement> placements) {

    public ArenaBridgeResult {
        placements = List.copyOf(placements);
    }

    public static void write(Path path, ArenaBridgeResult result) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("sessionId", result.sessionId.toString());
        properties.setProperty("roomId", Integer.toString(result.roomId));
        properties.setProperty("placementCount",
                Integer.toString(result.placements.size()));
        for (int i = 0; i < result.placements.size(); i++) {
            PlayerPlacement placement = result.placements.get(i);
            properties.setProperty("placement." + i + ".id",
                    placement.playerId().toString());
            properties.setProperty("placement." + i + ".rank",
                    Integer.toString(placement.placement()));
            properties.setProperty("placement." + i + ".participants",
                    Integer.toString(placement.participants()));
        }

        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (OutputStream output = Files.newOutputStream(temporary)) {
            properties.store(output, "Monster Conquest arena result");
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

    public static ArenaBridgeResult read(Path path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(path)) {
            properties.load(input);
        }
        int count = Integer.parseInt(
                properties.getProperty("placementCount", "0"));
        List<PlayerPlacement> placements = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            placements.add(new PlayerPlacement(
                    UUID.fromString(properties.getProperty(
                            "placement." + i + ".id")),
                    Integer.parseInt(properties.getProperty(
                            "placement." + i + ".rank", "0")),
                    Integer.parseInt(properties.getProperty(
                            "placement." + i + ".participants", "0"))));
        }
        return new ArenaBridgeResult(
                UUID.fromString(properties.getProperty("sessionId")),
                Integer.parseInt(properties.getProperty("roomId")),
                placements);
    }

    public record PlayerPlacement(
            UUID playerId,
            int placement,
            int participants) {
    }
}
