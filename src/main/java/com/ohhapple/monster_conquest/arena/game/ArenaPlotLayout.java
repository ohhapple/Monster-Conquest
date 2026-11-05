package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.entity.ArenaLingeringPotion;
import com.ohhapple.monster_conquest.arena.entity.ArenaLootDrop;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * 单个房间的自走棋地块布局。房号直接对应固定地块，因此房号回收后也会复用同一空间。
 */
public final class ArenaPlotLayout {

    public static final int BOARD_WIDTH = 42;
    public static final int BOARD_HEIGHT = 13;
    public static final int BOARD_DEPTH = 31;
    public static final int PLOT_SIZE = 128;
    public static final int ROOM_GAP = 64;
    public static final int FLOOR_Y = 0;
    private static final double COMBAT_ZONE_MIN_X_OFFSET = 6.0;
    private static final double COMBAT_ZONE_MAX_X_OFFSET = 36.0;
    private static final double COMBAT_ZONE_MIN_Z_OFFSET = 2.0;
    private static final double COMBAT_ZONE_MAX_Z_OFFSET = 29.0;
    private static final int ROOM_STRIDE = PLOT_SIZE + ROOM_GAP;
    private static final int GRID_COLUMNS = 16;
    private static final int GRID_ROWS = 128;
    private static final int ORIGIN_X = 100_000;
    private static final int ORIGIN_Z = 100_000;
    private static final int LOBBY_OFFSET_X = 44;
    private static final int LOBBY_OFFSET_Z = 80;
    private static final int LOBBY_AREA_SIZE = 32;
    private static final int LOBBY_CLEAR_HEIGHT = 16;
    private static final Direction SIDE_ZERO_FORWARD = Direction.EAST;
    private static final Direction SIDE_ONE_FORWARD = Direction.WEST;

    private static final int[][] BOARD_OFFSETS = {
            {2, 1},
            {76, 1},
            {2, 33},
            {76, 33},
            {2, 65},
            {76, 65},
            {2, 97},
            {76, 97}
    };

    private static final ResourceLocation BOARD_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena/chessboard_1");
    private static final ResourceLocation LOBBY_TEMPLATE =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena/lobby_1");

    private ArenaPlotLayout() {
    }

    public static BlockPos plotOrigin(int roomId) {
        int index = Math.max(0, roomId - 1);
        int column = index % GRID_COLUMNS;
        int row = index / GRID_COLUMNS;
        return new BlockPos(ORIGIN_X + column * ROOM_STRIDE, FLOOR_Y,
                ORIGIN_Z + row * ROOM_STRIDE);
    }

    public static boolean isReservedArenaSpace(BlockPos pos) {
        int x = pos.getX();
        int z = pos.getZ();
        return x >= ORIGIN_X
                && x < ORIGIN_X + GRID_COLUMNS * ROOM_STRIDE
                && z >= ORIGIN_Z
                && z < ORIGIN_Z + GRID_ROWS * ROOM_STRIDE;
    }

    public static BlockPos boardOrigin(int roomId, int boardIndex) {
        BlockPos plot = plotOrigin(roomId);
        int[] offset = BOARD_OFFSETS[Math.floorMod(boardIndex, BOARD_OFFSETS.length)];
        return plot.offset(offset[0], 0, offset[1]);
    }

    public static BlockPos lobbyCenter(int roomId) {
        return plotOrigin(roomId).offset(
                LOBBY_OFFSET_X + LOBBY_AREA_SIZE / 2,
                1,
                LOBBY_OFFSET_Z + LOBBY_AREA_SIZE / 2);
    }

    public static boolean prepare(ServerLevel level, int roomId) {
        forcePlotChunks(level, roomId, true);
        BlockPos plot = plotOrigin(roomId);
        clearArenaEntities(level, plot);
        if (!prepareLobby(level, plot)) {
            release(level, roomId);
            return false;
        }

        Optional<StructureTemplate> template = level.getStructureManager().get(BOARD_TEMPLATE);
        if (template.isEmpty()) {
            Monster_conquest.LOGGER.error(
                    "Missing arena board structure {}", BOARD_TEMPLATE);
            release(level, roomId);
            return false;
        }

        for (int i = 0; i < BOARD_OFFSETS.length; i++) {
            BlockPos origin = boardOrigin(roomId, i);
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .setIgnoreEntities(true);
            template.get().placeInWorld(
                    level, origin, origin, settings, RandomSource.create(roomId * 31L + i), 2);
        }
        return true;
    }

    private static boolean prepareLobby(ServerLevel level, BlockPos plot) {
        BlockPos area = plot.offset(
                LOBBY_OFFSET_X, 0, LOBBY_OFFSET_Z);
        clearBlocks(level, area, LOBBY_AREA_SIZE,
                LOBBY_CLEAR_HEIGHT, LOBBY_AREA_SIZE);

        Optional<StructureTemplate> template =
                level.getStructureManager().get(LOBBY_TEMPLATE);
        if (template.isEmpty()) {
            Monster_conquest.LOGGER.error(
                    "Missing arena lobby structure {}", LOBBY_TEMPLATE);
            return false;
        }
        var size = template.get().getSize();
        if (size.getX() > LOBBY_AREA_SIZE
                || size.getY() > LOBBY_CLEAR_HEIGHT
                || size.getZ() > LOBBY_AREA_SIZE) {
            Monster_conquest.LOGGER.warn(
                    "Arena lobby structure {} is too large: {}x{}x{}; "
                            + "maximum is {}x{}x{}",
                    LOBBY_TEMPLATE,
                    size.getX(), size.getY(), size.getZ(),
                    LOBBY_AREA_SIZE, LOBBY_CLEAR_HEIGHT,
                    LOBBY_AREA_SIZE);
            return false;
        }
        BlockPos origin = area.offset(
                (LOBBY_AREA_SIZE - size.getX()) / 2,
                0,
                (LOBBY_AREA_SIZE - size.getZ()) / 2);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setIgnoreEntities(true);
        template.get().placeInWorld(
                level, origin, origin, settings,
                RandomSource.create(31L), 2);
        return true;
    }

    private static void clearBlocks(ServerLevel level, BlockPos origin,
                                    int width, int height, int depth) {
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < depth; z++) {
                    level.setBlock(origin.offset(x, y, z),
                            Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void clearArenaEntities(ServerLevel level, BlockPos plot) {
        AABB bounds = new AABB(
                plot.getX(), FLOOR_Y - 8, plot.getZ(),
                plot.getX() + PLOT_SIZE, FLOOR_Y + 96,
                plot.getZ() + PLOT_SIZE);
        for (Entity entity : level.getEntitiesOfClass(
                Entity.class, bounds)) {
            if (entity instanceof AbstractArmySoldier
                    || entity instanceof WarHorseEntity
                    || entity instanceof ArenaLootDrop
                    || entity instanceof ArenaLingeringPotion
                    || entity instanceof Projectile
                    || entity instanceof EvokerFangs) {
                entity.discard();
            }
        }
    }

    /** 房间复用前和终局后无条件清理该房间内的竞技残留。 */
    public static void clearRoomEntities(
            ServerLevel level, int roomId) {
        clearArenaEntities(level, plotOrigin(roomId));
    }

    /** 战斗结束只清理投射物、尖牙和地面效果，保留结算展示与奖励物。 */
    public static void clearCombatResiduals(
            ServerLevel level, int roomId) {
        BlockPos plot = plotOrigin(roomId);
        AABB bounds = new AABB(
                plot.getX(), FLOOR_Y - 8, plot.getZ(),
                plot.getX() + PLOT_SIZE, FLOOR_Y + 96,
                plot.getZ() + PLOT_SIZE);
        for (Entity entity : level.getEntitiesOfClass(
                Entity.class, bounds)) {
            if (entity instanceof Projectile
                    || entity instanceof EvokerFangs
                    || entity instanceof ArenaLingeringPotion) {
                entity.discard();
            }
        }
    }

    public static void release(ServerLevel level, int roomId) {
        forcePlotChunks(level, roomId, false);
    }

    public static Vec3 playerViewPoint(int roomId, int boardIndex, int side) {
        BlockPos board = boardOrigin(roomId, boardIndex);
        double x = board.getX() + (side == 0 ? 2.5 : 39.5);
        double z = board.getZ() + 15.5;
        return new Vec3(x, FLOOR_Y + 8.0, z);
    }

    public static Vec3 unitPoint(int roomId, int boardIndex, int side,
                                 int column, int row) {
        // X 是双方前后方向（5 行），Z 是每方左右方向（9 列）。
        // GUI 的 row 与 column 只映射到这两个轴，不得整体旋转棋盘。
        BlockPos board = boardOrigin(roomId, boardIndex);
        int depth = ArenaPlayerState.BOARD_ROWS - 1 - row;
        int x = board.getX() + (side == 0
                ? 7 + depth * 3
                : 34 - depth * 3);
        int z = board.getZ() + (side == 0
                ? 3 + column * 3
                : 27 - column * 3);
        return new Vec3(x + 0.5, FLOOR_Y + 1.0, z + 0.5);
    }

    public static Vec3 benchPoint(int roomId, int boardIndex, int side,
                                  int slot) {
        BlockPos board = boardOrigin(roomId, boardIndex);
        int x = board.getX() + (side == 0 ? 3 : 38);
        int z = board.getZ() + (side == 0
                ? 3 + slot * 3
                : 27 - slot * 3);
        return new Vec3(x + 0.5, FLOOR_Y + 1.0, z + 0.5);
    }

    public static AABB combatSearchBounds(int roomId) {
        BlockPos plot = plotOrigin(roomId);
        return new AABB(
                plot.getX() - 32.0,
                FLOOR_Y - 16.0,
                plot.getZ() - 32.0,
                plot.getX() + PLOT_SIZE + 32.0,
                FLOOR_Y + 96.0,
                plot.getZ() + PLOT_SIZE + 32.0);
    }

    /**
     * 战斗实体一旦被击退、冲刺或传送出 9×5 个 3×3 地块，立即回到最近
     * 的合法位置，避免继续在棋盘结构外移动或卡在地形中。
     */
    public static boolean confineToCombatZone(
            AbstractArmySoldier soldier, int roomId, int boardIndex) {
        BlockPos board = boardOrigin(roomId, boardIndex);
        double minX = board.getX() + COMBAT_ZONE_MIN_X_OFFSET;
        double maxX = board.getX() + COMBAT_ZONE_MAX_X_OFFSET;
        double minZ = board.getZ() + COMBAT_ZONE_MIN_Z_OFFSET;
        double maxZ = board.getZ() + COMBAT_ZONE_MAX_Z_OFFSET;
        double targetX = Math.max(minX, Math.min(maxX, soldier.getX()));
        double targetZ = Math.max(minZ, Math.min(maxZ, soldier.getZ()));
        if (Math.abs(targetX - soldier.getX()) < 1.0E-4
                && Math.abs(targetZ - soldier.getZ()) < 1.0E-4) {
            return false;
        }
        soldier.setPos(targetX, soldier.getY(), targetZ);
        soldier.setDeltaMovement(Vec3.ZERO);
        soldier.getNavigation().stop();
        return true;
    }

    public static float viewYaw(int side) {
        return (side == 0 ? SIDE_ZERO_FORWARD : SIDE_ONE_FORWARD).toYRot();
    }

    public static float viewPitch() {
        return 30.0f;
    }

    private static void forcePlotChunks(ServerLevel level, int roomId, boolean forced) {
        BlockPos origin = plotOrigin(roomId);
        int minChunkX = origin.getX() >> 4;
        int minChunkZ = origin.getZ() >> 4;
        for (int x = 0; x < PLOT_SIZE / 16; x++) {
            for (int z = 0; z < PLOT_SIZE / 16; z++) {
                level.setChunkForced(minChunkX + x, minChunkZ + z, forced);
                if (forced) level.getChunk(minChunkX + x, minChunkZ + z);
            }
        }
    }

}
