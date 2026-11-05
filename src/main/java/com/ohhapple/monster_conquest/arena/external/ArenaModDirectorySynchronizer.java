package com.ohhapple.monster_conquest.arena.external;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps the child mod directory equal to the main server set. */
final class ArenaModDirectorySynchronizer {

    private ArenaModDirectorySynchronizer() {
    }

    static List<Path> synchronize(
            Path mainDirectory, Path childDirectory,
            Path currentModJar) throws IOException {
        Path sourceMods = mainDirectory.resolve("mods");
        Path targetMods = childDirectory.resolve("mods");
        Files.createDirectories(targetMods);

        Map<String, Path> expectedMods = new HashMap<>();
        if (Files.isDirectory(sourceMods)) {
            try (var files = Files.list(sourceMods)) {
                for (Path source : files.filter(Files::isRegularFile)
                        .filter(ArenaModDirectorySynchronizer::isJar).toList()) {
                    expectedMods.put(modFileKey(source),
                            source.toAbsolutePath().normalize());
                }
            }
        }
        if (currentModJar != null) {
            expectedMods.put(modFileKey(currentModJar), currentModJar);
        }

        List<Path> removed = new ArrayList<>();
        try (var files = Files.list(targetMods)) {
            for (Path target : files.filter(Files::isRegularFile)
                    .filter(ArenaModDirectorySynchronizer::isJar).toList()) {
                if (expectedMods.containsKey(modFileKey(target))) continue;
                Files.delete(target);
                removed.add(target.getFileName());
            }
        }

        for (Path source : expectedMods.values()) {
            Path target = targetMods.resolve(source.getFileName());
            if (Files.isRegularFile(target)
                    && Files.size(target) == Files.size(source)
                    && Files.getLastModifiedTime(target)
                    .equals(Files.getLastModifiedTime(source))) {
                continue;
            }
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.COPY_ATTRIBUTES);
        }
        return List.copyOf(removed);
    }

    private static boolean isJar(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(".jar");
    }

    private static String modFileKey(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT);
    }
}
