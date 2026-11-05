package com.ohhapple.monster_conquest.arena.external;

import java.nio.file.Path;
import java.util.UUID;

/** 主服与本地竞技子服共享的角色和桥接路径。 */
public final class ArenaRuntime {

    public static final String READY_FILE_NAME = "arena-server-ready.txt";
    public static final String ARENA_SERVER_PROPERTY =
            "monster_conquest.arenaServer";
    public static final String BRIDGE_DIR_PROPERTY =
            "monster_conquest.arenaBridgeDir";
    public static final String PARENT_PID_PROPERTY =
            "monster_conquest.parentPid";
    public static final String ACCEPTED_DIRECTORY = "accepted";
    public static final String TRANSFER_RECEIPTS_DIRECTORY =
            "transfer-receipts";

    private ArenaRuntime() {
    }

    public static boolean isArenaServer() {
        return Boolean.getBoolean(ARENA_SERVER_PROPERTY);
    }

    public static Path bridgeDirectory() {
        String configured = System.getProperty(BRIDGE_DIR_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured).toAbsolutePath();
        }
        return Path.of("monster_conquest", "arena_bridge").toAbsolutePath();
    }

    public static long parentPid() {
        return Long.getLong(PARENT_PID_PROPERTY, -1L);
    }

    public static Path acceptedRequestMarker(Path bridgeDirectory, UUID sessionId) {
        return bridgeDirectory.resolve(ACCEPTED_DIRECTORY)
                .resolve(sessionId + ".properties");
    }
}
