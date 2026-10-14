package com.ohhapple.monster_conquest.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/** 世界生成辅助：在指定中心附近找一个可以站人的地面生成点。 */
public final class ArmySpawnUtil {

    private ArmySpawnUtil() {}

    /** 危险方块：脚边出现这些就不生成（岩浆 / 火 / 仙人掌 / 细雪 / 岩浆块等）。 */
    private static boolean isHazard(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE);
    }

    /**
     * 安全生成点：在 center 附近找一个"能安全站人"的位置。
     *   · 与 center 的高度差不超过 maxYDelta（避免生成到房顶 / 沟底）；
     *   · 脚下是实心方块、脚部与头部两格为空、无液体；
     *   · 附近 3×3×3 没有岩浆 / 火 / 仙人掌 / 细雪等危险方块；
     *   · center 到候选点的直线不被墙挡住（避免生成到墙里 / 房子另一侧）。
     *
     * @return 安全生成位置；找不到时返回 null（调用方自行兜底）
     */
    @Nullable
    public static Vec3 findSafeSpawnNear(ServerLevel level, Vec3 center,
                                         double minRadius, double maxRadius,
                                         int maxYDelta, RandomSource random) {
        return findSafeSpawnNear(level, center, minRadius, maxRadius, maxYDelta, random, true);
    }

    /** 安全生成点（可选是否要求从参考点能直视落点）。 */
    @Nullable
    public static Vec3 findSafeSpawnNear(ServerLevel level, Vec3 center,
                                         double minRadius, double maxRadius,
                                         int maxYDelta, RandomSource random,
                                         boolean requireLineOfSight) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = minRadius + random.nextDouble() * Math.max(0.5, maxRadius - minRadius);
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;

            // 优先同高度，其次上下 1~maxYDelta 格
            for (int dy = 0; dy <= maxYDelta; dy++) {
                Vec3 spot = trySpot(level, center, x, center.y + dy, z, requireLineOfSight);
                if (spot != null) return spot;
                if (dy > 0) {
                    spot = trySpot(level, center, x, center.y - dy, z, requireLineOfSight);
                    if (spot != null) return spot;
                }
            }
        }
        return null;
    }

    /**
     * 村庄专用生成点：在中心附近找"安全且没有被方块封住"的位置。
     *   · 与中心高度差不超过 maxYDelta（避免房顶 / 沟底）；
     *   · 脚下实心、两格净空、无液体、附近无危险方块；
     *   · 头顶至少有 openSky 格连续净空 —— 排除"被围起来的封闭空间"（盒子里 / 房间里）；
     *   · 水平方向至少有 2 面是通的（排除 1×1 坑 / 井状空间）；
     *   · requireLineOfSight = true 时要求从中心能直视落点；
     *     false 时忽略墙体遮挡（村民 / 工作方块被方块围起来时的兜底搜索）。
     */
    @Nullable
    public static Vec3 findOpenSpawnNear(ServerLevel level, Vec3 center,
                                         double minRadius, double maxRadius,
                                         int maxYDelta, int openSky,
                                         boolean requireLineOfSight,
                                         RandomSource random) {
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = minRadius + random.nextDouble() * Math.max(0.5, maxRadius - minRadius);
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;

            for (int dy = 0; dy <= maxYDelta; dy++) {
                Vec3 spot = tryOpenSpot(level, center, x, center.y + dy, z,
                        openSky, requireLineOfSight);
                if (spot != null) return spot;
                if (dy > 0) {
                    spot = tryOpenSpot(level, center, x, center.y - dy, z,
                            openSky, requireLineOfSight);
                    if (spot != null) return spot;
                }
            }
        }
        return null;
    }

    /**
     * 村庄巡逻队专用生成点：在中心附近找"有实地支撑的开阔站位"。
     *
     *   · 从中心上方 scanUp 格开始向下扫描，取第一个"下方有连续实心支撑"的合法站位；
     *   · 判定不看方块种类（兼容木板 / 羊毛 / 台阶等地板）：要求落脚点下方连续 3 格实心——
     *       屋顶 / 二楼地板（下方是房屋内部空气）、树冠（下方是空气或树干空隙）都会因此被排除；
     *       而地面上铺的木板 / 羊毛 / 台阶（下面就是泥土 / 石头）可以正常通过；
     *   · 兴趣点（床 / 工作站）本身在二楼高处也不影响：扫描会一路下探到真实地面；
     *   · 头顶至少 openSky 格净空（不会被封在盒子里 / 房间里），水平至少两面通（不是 1×1 坑）；
     *   · requireLineOfSight = true 时还要求从中心能直视落点。
     */
    @Nullable
    public static Vec3 findSupportedSpawn(ServerLevel level, Vec3 center,
                                          double minRadius, double maxRadius,
                                          int scanUp, int scanDown, int openSky,
                                          boolean requireLineOfSight,
                                          RandomSource random) {
        int top = (int) Math.floor(center.y) + scanUp;
        int bottom = (int) Math.floor(center.y) - scanDown;
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = minRadius + random.nextDouble() * Math.max(0.5, maxRadius - minRadius);
            double x = center.x + Math.cos(angle) * dist;
            double z = center.z + Math.sin(angle) * dist;

            for (int y = top; y >= bottom; y--) {
                Vec3 spot = trySupportedSpot(level, center, x, y, z, openSky, requireLineOfSight);
                if (spot != null) return spot;
            }
        }
        return null;
    }

    @Nullable
    private static Vec3 trySupportedSpot(ServerLevel level, Vec3 from, double x, double y, double z,
                                         int openSky, boolean requireLineOfSight) {
        BlockPos feet = BlockPos.containing(x, y, z);
        if (!level.isLoaded(feet)) return null;
        if (!level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())) return null;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return null;

        // 不看方块种类：只要下方连续 3 格实心即可（屋顶 / 二楼 / 树冠下方是空气，会被排除）
        if (!hasSolidSupport(level, feet)) return null;
        if (isHazard(level.getBlockState(feet)) || isHazard(level.getBlockState(feet.above()))) {
            return null;
        }
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            if (isHazard(level.getBlockState(p))) return null;
        }

        // 头顶连续净空：不会落在封闭空间 / 树冠下
        for (int i = 1; i <= openSky; i++) {
            if (!level.isEmptyBlock(feet.above(i))) return null;
        }

        // 水平至少两面通：排除 1×1 坑 / 井状空间
        int openSides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (level.isEmptyBlock(side) && level.isEmptyBlock(side.above())) openSides++;
        }
        if (openSides < 2) return null;

        if (requireLineOfSight) {
            Vec3 eye = new Vec3(from.x, from.y + 1.0, from.z);
            Vec3 target = new Vec3(x, y + 1.0, z);
            BlockHitResult hit = level.clip(new ClipContext(eye, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) return null;
        }

        return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
    }

    /**
     * 实地支撑判定（不看方块种类）：
     *   落脚点下方连续 3 格必须是实心方块。
     *   · 屋顶 / 二楼地板：下方是房屋内部空气 → 不合格；
     *   · 树冠：下方是空气 / 树干空隙 → 不合格；
     *   · 地面上铺的木板 / 羊毛 / 台阶 / 土径：下面就是泥土 / 石头 → 合格。
     */
    private static boolean hasSolidSupport(ServerLevel level, BlockPos feet) {
        for (int i = 1; i <= 3; i++) {
            if (!level.getBlockState(feet.below(i)).isSolid()) return false;
        }
        return true;
    }

    @Nullable
    private static Vec3 tryOpenSpot(ServerLevel level, Vec3 from, double x, double y, double z,
                                    int openSky, boolean requireLineOfSight) {
        Vec3 spot = trySpot(level, from, x, y, z, requireLineOfSight);
        if (spot == null) return null;

        BlockPos feet = BlockPos.containing(spot.x, spot.y, spot.z);

        // 头顶连续净空：被封在盒子里 / 房间里的位置直接排除
        for (int i = 1; i <= openSky; i++) {
            if (!level.isEmptyBlock(feet.above(i))) return null;
        }

        // 水平至少 2 面通：排除 1×1 坑 / 井状空间
        int openSides = 0;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            if (level.isEmptyBlock(side) && level.isEmptyBlock(side.above())) openSides++;
        }
        if (openSides < 2) return null;

        return spot;
    }

    @Nullable
    private static Vec3 trySpot(ServerLevel level, Vec3 from, double x, double y, double z,
                                boolean requireLineOfSight) {
        BlockPos feet = BlockPos.containing(x, y, z);
        if (!level.isLoaded(feet)) return null;
        if (!level.isEmptyBlock(feet) || !level.isEmptyBlock(feet.above())) return null;
        if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.above()).isEmpty()) return null;

        BlockState below = level.getBlockState(feet.below());
        if (!below.isSolid()) return null;
        if (isHazard(below)
                || isHazard(level.getBlockState(feet))
                || isHazard(level.getBlockState(feet.above()))) {
            return null;
        }

        // 附近不能有危险方块
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 1, 1))) {
            if (isHazard(level.getBlockState(p))) return null;
        }

        if (requireLineOfSight) {
            // 从参考点（胸口高度）到候选点不能被墙挡住
            Vec3 eye = new Vec3(from.x, from.y + 1.0, from.z);
            Vec3 target = new Vec3(x, y + 1.0, z);
            BlockHitResult hit = level.clip(new ClipContext(eye, target,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (hit.getType() != HitResult.Type.MISS) return null;
        }

        return new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
    }

    /**
     * 在中心点附近的环形区域内找一个地面上方、无液体、下方为实心方块的生成点。
     *
     * @param minRadius 最小距离（格）
     * @param maxRadius 最大距离（格）
     * @return 生成位置；找不到时返回 null
     */
    @Nullable
    public static Vec3 findSurfaceSpawn(ServerLevel level, BlockPos center,
                                        RandomSource random, double minRadius, double maxRadius) {
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = minRadius + random.nextDouble() * Math.max(1.0, maxRadius - minRadius);
            int x = center.getX() + (int) Math.round(Math.cos(angle) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(angle) * dist);

            BlockPos surface = level.getHeightmapPos(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    new BlockPos(x, center.getY(), z));
            if (!level.isLoaded(surface)) continue;
            if (!level.isEmptyBlock(surface)) continue;
            if (!level.getFluidState(surface).isEmpty()) continue;
            if (!level.getBlockState(surface.below()).isSolid()) continue;

            return Vec3.atBottomCenterOf(surface);
        }
        return null;
    }
}
