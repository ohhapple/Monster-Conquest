package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 方块级 A* 绕路规划：目标只要求到达目标附近，搜索失败时按多个"目标偏好系数"
 * 取最接近目标且已走出足够距离的部分路线。邻居为 8 方向 × 台阶高度
 * （上 1 格、下最多 3 格，不切角），木门 / 栅栏门可通行、危险方块不可通行，
 * 支持半砖 / 雪层 / 地毯等部分方块的站立面；受节点数与时间预算限制。
 *
 * 支持传入"上一次的路线"作为代价偏好：靠近旧路线的节点更便宜，
 * 重规划时会优先沿原路线走，避免在两条绕路之间来回切换。
 */
public final class RoutePlanner {

    /** 规划结果：路径点（含起点）与是否真正到达目标附近。 */
    public record Result(List<BlockPos> points, boolean reachedGoal) {}

    /**
     * 通行净空：宽度取移动实体（骑乘时为坐骑），高度再加上骑手。
     *   {@code centerOffset} 是"路径节点 → 实际站位中心"的换算偏移：
     *   原版对宽实体取 (int)(宽度 + 1) / 2（宽 1.4 的马是 1 格），
     *   净空必须按这个真实站位中心校验，否则宽坐骑的转向点会落在贴着墙的位置。
     */
    private record Clearance(double halfWidth, double height, double centerOffset) {}

    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[] STEP_LEVELS = {1, 0, -1, -2, -3};

    private static final int MAX_NODES = 12000;
    private static final long TIME_BUDGET_NANOS = 4_000_000L;
    private static final double MIN_PARTIAL_DISTANCE = 5.0;
    /** 最优努力系数：先取路径较"合理"的解，不行再取更贴近目标的解。 */
    private static final double[] BEST_COEFFICIENTS = {1.5, 4.0};
    /** 旧路线节点的代价系数（越小越优先走旧路线）。 */
    private static final double FAVOR_EXACT = 0.75;
    private static final double FAVOR_NEAR = 0.9;
    private static final int FAVOR_LIMIT = 2048;

    private RoutePlanner() {}

    @Nullable
    public static Result plan(Level level, LivingEntity entity, Vec3 goal,
                              double goalRadius, double maxRadius, int maxNodes,
                              double extraHeight, @Nullable List<BlockPos> favored) {
        return plan(level, entity, goal, goalRadius, maxRadius, maxNodes, extraHeight,
                favored, true, TIME_BUDGET_NANOS);
    }

    /**
     * @param smooth true = 直线平滑（战斗绕路用，路径点少）；false = 返回原始逐格节点，
     *               用于一格宽迷宫里的贴格跟随（平滑后的长直线会让单位漂着走、擦墙）。
     */
    @Nullable
    public static Result plan(Level level, LivingEntity entity, Vec3 goal,
                              double goalRadius, double maxRadius, int maxNodes,
                              double extraHeight, @Nullable List<BlockPos> favored,
                              boolean smooth) {
        return plan(level, entity, goal, goalRadius, maxRadius, maxNodes, extraHeight,
                favored, smooth, TIME_BUDGET_NANOS);
    }

    /**
     * @param timeBudgetNanos 本次规划的时间预算；玩家指定点的行军目标可以给大一些
     *                        （迷宫节点多，默认预算会算不完 → 只能给半路路线）。
     */
    @Nullable
    public static Result plan(Level level, LivingEntity entity, Vec3 goal,
                              double goalRadius, double maxRadius, int maxNodes,
                              double extraHeight, @Nullable List<BlockPos> favored,
                              boolean smooth, long timeBudgetNanos) {

        // 全局每 tick 预算：所有调用点（行军绕路 / 战斗追击 / 索敌可达性探测）共用，
        //   单位多时把 A* 摊到多个 tick，避免同一 tick 跑一堆大范围搜索造成掉刻。
        //   本 tick 轮不到时直接返回 null，调用方通过 wasLastCallDeferred() 区分
        //   "预算被占满、稍后重试"和"真的规划不出路"。
        long nowTick = level.getGameTime();
        if (nowTick != budgetTick) {
            long elapsedTicks = Math.max(1L, (long) nowTick - budgetTick);
            nanosUsed = Math.max(0L, nanosUsed - elapsedTicks * NANOS_PER_TICK);
            budgetTick = nowTick;
        }
        if (nanosUsed >= NANOS_PER_TICK) {
            lastCallDeferred = true;
            return null;
        }
        lastCallDeferred = false;
        long budgetStartNanos = System.nanoTime();
        // 单次规划期间缓存"格子能不能走"：同一条走廊相邻节点会重复查询，
        //   迷宫搜索能因此快好几倍（服务端单线程，静态缓存安全）。
        WALK_CACHE.clear();
        CACHE_ACTIVE = true;
        try {
            return planInternal(level, entity, goal, goalRadius, maxRadius, maxNodes,
                    extraHeight, favored, smooth, timeBudgetNanos);
        } finally {
            CACHE_ACTIVE = false;
            WALK_CACHE.clear();
            nanosUsed += System.nanoTime() - budgetStartNanos;
        }
    }

    private static boolean CACHE_ACTIVE = false;
    private static final Map<Long, Boolean> WALK_CACHE = new HashMap<>();

    /** 全局每 tick 规划预算（纳秒）：所有调用点合计，防止战斗中大量单位同时跑 A* 造成掉刻。 */
    private static final long NANOS_PER_TICK = 5_000_000L;
    private static long budgetTick = Long.MIN_VALUE;
    private static long nanosUsed = 0L;
    private static boolean lastCallDeferred = false;

    /** 上一次 plan() 返回 null 是否只是"本 tick 预算被占满"（不是真的没有路）。 */
    public static boolean wasLastCallDeferred() {
        return lastCallDeferred;
    }

    /** 移动实体 + 额外高度（骑乘时传骑手高度）对应的通行净空。 */
    private static Clearance clearanceOf(LivingEntity entity, double extraHeight) {
        return new Clearance(
                entity.getBbWidth() * 0.5 + 0.05,
                entity.getBbHeight() + extraHeight + 0.05,
                ((int) (entity.getBbWidth() + 1.0F)) * 0.5);
    }

    /**
     * 供"可达性搜索"复用：指定移动实体（骑乘时传坐骑 + 骑手高度）能不能站 / 通过这一格。
     *   与 {@link #plan} 内部用的是同一套判定，保证"可达性"和"能规划出路线"不打架。
     */
    public static boolean walkableFor(Level level, LivingEntity mover, double extraHeight,
                                      BlockPos pos) {
        return walkableUncached(level, mover, clearanceOf(mover, extraHeight), pos);
    }

    @Nullable
    private static Result planInternal(Level level, LivingEntity entity, Vec3 goal,
                                       double goalRadius, double maxRadius, int maxNodes,
                                       double extraHeight, @Nullable List<BlockPos> favored,
                                       boolean smooth, long timeBudgetNanos) {
        Clearance clearance = clearanceOf(entity, extraHeight);

        BlockPos start = entity.blockPosition();
        if (!walkable(level, entity, clearance, start)) {
            start = findStartNear(level, entity, clearance, start);
            if (start == null) return null;
        }

        Map<BlockPos, Double> favor = buildFavor(favored);
        double goalRadiusSq = goalRadius * goalRadius;
        int nodeBudget = Math.max(256, Math.min(maxNodes, MAX_NODES));
        long deadline = System.nanoTime() + timeBudgetNanos;
        double maxRadiusSq = maxRadius * maxRadius;

        Map<BlockPos, Node> nodes = new HashMap<>();
        Set<BlockPos> closed = new HashSet<>();
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f));

        Node startNode = new Node(start, 0.0, center(start).distanceTo(goal), null);
        nodes.put(start, startNode);
        open.add(startNode);

        Node reached = null;
        Node[] bests = new Node[BEST_COEFFICIENTS.length];
        double[] bestScores = new double[BEST_COEFFICIENTS.length];
        Arrays.fill(bestScores, Double.MAX_VALUE);
        int visited = 0;

        while (!open.isEmpty() && visited < nodeBudget) {
            if ((visited & 63) == 0 && System.nanoTime() > deadline) break;

            Node current = open.poll();
            Node known = nodes.get(current.pos);
            if (known == null || current.g > known.g + 1.0E-6) continue;
            if (!closed.add(current.pos)) continue;
            visited++;

            if (center(current.pos).distanceToSqr(goal) <= goalRadiusSq) {
                reached = current;
                break;
            }

            double distFromStart = center(current.pos).distanceTo(center(start));
            if (distFromStart >= MIN_PARTIAL_DISTANCE) {
                for (int i = 0; i < BEST_COEFFICIENTS.length; i++) {
                    double score = current.h + current.g / BEST_COEFFICIENTS[i];
                    if (score < bestScores[i]) {
                        bestScores[i] = score;
                        bests[i] = current;
                    }
                }
            }

            expand(level, entity, clearance, current, goal, start, maxRadiusSq,
                    favor, nodes, closed, open);
        }

        Node end = reached;
        if (end == null) {
            for (Node best : bests) {
                if (best != null) { end = best; break; }
            }
        }
        if (end == null) {
            return null;
        }

        List<BlockPos> raw = reconstruct(end);
        if (raw.size() < 2) return null;
        return new Result(smooth ? smooth(level, entity, clearance, raw) : raw,
                reached != null);
    }

    /** 旧路线节点及相邻节点的代价偏好。 */
    private static Map<BlockPos, Double> buildFavor(@Nullable List<BlockPos> favored) {
        if (favored == null || favored.isEmpty()) return Map.of();
        Map<BlockPos, Double> favor = new HashMap<>();
        for (BlockPos pos : favored) {
            favor.putIfAbsent(pos, FAVOR_EXACT);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                favor.putIfAbsent(pos.relative(dir), FAVOR_NEAR);
            }
            if (favor.size() >= FAVOR_LIMIT) break;
        }
        return favor;
    }

    private static void expand(Level level, LivingEntity entity, Clearance clearance,
                               Node current, Vec3 goal, BlockPos start, double maxRadiusSq,
                               Map<BlockPos, Double> favor,
                               Map<BlockPos, Node> nodes, Set<BlockPos> closed,
                               PriorityQueue<Node> open) {
        // 梯子 / 脚手架 / 藤蔓允许"垂直向上"：原版寻路不会走梯子，
        //   这里必须自己支持，否则"只能通过梯子上去的点"永远规划不出路线。
        //   但只允许"目标确实在上一层"时向上爬：否则地面上的目标（例如墙后）也会被规划出
        //   "顺着墙上的梯子翻过墙顶、再下到墙后"的路线 —— 单位不会跟着爬（爬梯辅助只在
        //   目标明显在上方时才启动），只会顶着梯子 / 墙角原地不动（就是"卡在梯子/墙角"）。
        BlockState curFeet = level.getBlockState(current.pos);
        boolean onClimbable = curFeet.is(BlockTags.CLIMBABLE)
                || level.getBlockState(current.pos.above()).is(BlockTags.CLIMBABLE);
        if (onClimbable && goal.y > current.pos.getY() + 1.0) {
            for (int dy = 1; dy <= 2; dy++) {
                BlockPos up = current.pos.above(dy);
                if (!walkable(level, entity, clearance, up)) break;
                double cost = dy * 0.8;
                Double fav = favor.get(up);
                if (fav != null) cost *= fav;
                double g = current.g + cost;
                Node known = nodes.get(up);
                if (known == null || g < known.g - 1.0E-6) {
                    Node node = new Node(up, g, center(up).distanceTo(goal), current);
                    nodes.put(up, node);
                    open.add(node);
                }
            }
        }

        for (int[] dir : DIRECTIONS) {
            int nx = current.pos.getX() + dir[0];
            int nz = current.pos.getZ() + dir[1];
            boolean diagonal = dir[0] != 0 && dir[1] != 0;

            if (diagonal
                    && (!walkable(level, entity, clearance,
                    new BlockPos(current.pos.getX() + dir[0], current.pos.getY(), current.pos.getZ()))
                    || !walkable(level, entity, clearance,
                    new BlockPos(current.pos.getX(), current.pos.getY(), current.pos.getZ() + dir[1])))) {
                continue;
            }

            for (int dy : STEP_LEVELS) {
                BlockPos next = new BlockPos(nx, current.pos.getY() + dy, nz);
                if (!walkable(level, entity, clearance, next)) continue;
                if (closed.contains(next)) continue;

                double dx = next.getX() - start.getX();
                double dz = next.getZ() - start.getZ();
                if (dx * dx + dz * dz > maxRadiusSq) break;

                double cost = diagonal ? 1.414 : 1.0;
                cost += dy > 0 ? dy * 0.8 : -dy * 0.25;
                if (isWater(level, next)) cost += 0.8;
                Double fav = favor.get(next);
                if (fav != null) cost *= fav;

                double g = current.g + cost;
                Node known = nodes.get(next);
                if (known != null && g >= known.g - 1.0E-6) break;

                Node node = new Node(next, g, center(next).distanceTo(goal), current);
                nodes.put(next, node);
                open.add(node);
                break;
            }
        }
    }

    private static List<BlockPos> reconstruct(Node end) {
        List<BlockPos> out = new ArrayList<>();
        for (Node node = end; node != null; node = node.parent) out.add(node.pos);
        Collections.reverse(out);
        return out;
    }

    /** 直线平滑：能直走到的两个路径点之间不再逐个拐（逐段限制落差）。 */
    private static List<BlockPos> smooth(Level level, LivingEntity entity,
                                         Clearance clearance, List<BlockPos> raw) {
        List<BlockPos> out = new ArrayList<>();
        out.add(raw.get(0));
        int i = 0;
        while (i < raw.size() - 1) {
            int j = raw.size() - 1;
            while (j > i + 1 && !straightWalkable(level, entity, clearance, raw.get(i), raw.get(j))) {
                j--;
            }
            out.add(raw.get(j));
            i = j;
        }
        return out;
    }

    private static boolean straightWalkable(Level level, LivingEntity entity,
                                            Clearance clearance, BlockPos from, BlockPos to) {
        Vec3 a = center(from);
        Vec3 b = center(to);
        int steps = Math.max(1, (int) Math.ceil(a.distanceTo(b) / 0.5));
        int standY = from.getY();
        for (int s = 1; s <= steps; s++) {
            Vec3 p = a.lerp(b, (double) s / steps);
            BlockPos stand = pickStand(level, entity, clearance,
                    BlockPos.containing(p.x, p.y, p.z), standY);
            if (stand == null) return false;
            standY = stand.getY();
        }
        return true;
    }

    /**
     * 采样点附近取一个可行站立格：只允许相对采样线上下 1 格以内。
     *   向下探 3 格时会把"顺着地板下方穿过去"的墙当成可走，
     *   平滑后的直线路径会穿墙，单位一路推墙（一格宽迷宫尤其明显）。
     */
    @Nullable
    private static BlockPos pickStand(Level level, LivingEntity entity, Clearance clearance,
                                      BlockPos pos, int previousY) {
        for (int dy = 1; dy >= -1; dy--) {
            BlockPos candidate = pos.offset(0, dy, 0);
            if (candidate.getY() - previousY > 1) continue;
            if (previousY - candidate.getY() > 1) continue;
            if (walkable(level, entity, clearance, candidate)) return candidate;
        }
        return null;
    }

    /**
     * 站立 / 通行判定：脚下有支撑（实心块 / 水 / 半砖雪层等地毯类部分方块），
     * 按净空无方块碰撞，不踩危险方块；木门与栅栏门可通行。
     */
    private static boolean walkable(Level level, LivingEntity entity, Clearance clearance, BlockPos pos) {
        if (CACHE_ACTIVE) {
            Long key = pos.asLong();
            Boolean cached = WALK_CACHE.get(key);
            if (cached != null) return cached;
            boolean result = walkableUncached(level, entity, clearance, pos);
            WALK_CACHE.put(key, result);
            return result;
        }
        return walkableUncached(level, entity, clearance, pos);
    }

    private static boolean walkableUncached(Level level, LivingEntity entity,
                                            Clearance clearance, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (AiNavUtil.isDangerousBlock(feet) || AiNavUtil.isDangerousBlock(head)) return false;
        if (AiNavUtil.isDangerousBlock(level.getBlockState(pos.below()))) return false;

        boolean door = openable(feet) || openable(head);
        double stand = door ? 0.0 : partialTop(level, pos, feet);
        boolean water = isWater(level, pos);

        BlockPos belowPos = pos.below();
        BlockState belowState = level.getBlockState(belowPos);
        // 梯子 / 藤蔓 / 脚手架：允许作为路径节点（可以顺着爬上去），不要求脚下有支撑
        boolean climbable = feet.is(BlockTags.CLIMBABLE) || head.is(BlockTags.CLIMBABLE);
        if (!climbable && !door && stand <= 0.01 && !water
                && !usableSupport(level, belowPos, belowState)) {
            return false;
        }

        if (door) {
            // 门 / 栅栏门 / 活板门按"打开后"的真实碰撞箱判定：
            //   有的门打开后仍然会挡住通道（转动方向不对、上格活板门打开后变成顶部板等），
            //   这种"打开也过不去"的格子不能当可通行路径。
            //   打开后的门板只有 3/16 厚、贴在门框一侧，单位可以侧身挤过去，
            //     所以这里用"缩窄的净空盒"判断中心通道是否被挡住，
            //     否则 1 格宽的门洞会被误判成不可通行（单位就不会识别有门的路线）。
            double halfW = Math.min(clearance.halfWidth(), 0.25);
            double centerX = pos.getX() + clearance.centerOffset();
            double centerZ = pos.getZ() + clearance.centerOffset();
            double baseY = pos.getY();
            AABB box = new AABB(
                    centerX - halfW, baseY,
                    centerZ - halfW,
                    centerX + halfW,
                    baseY + clearance.height(),
                    centerZ + halfW);
            if (collidesOpened(level, pos, feet, box)) return false;
            if (collidesOpened(level, pos.above(), head, box)) return false;
        } else {
            double baseY = pos.getY() + stand;
            // 便宜够用的净空判定：只查盒子覆盖到的方块是不是实心（可开的门除外）。
            //   逐块做碰撞箱运算时，长绕路 / 大范围搜索 12ms 只能展开两三百个节点，
            //   只能给出"半路路线"（单位停在墙前）。
            double centerX = pos.getX() + clearance.centerOffset();
            double centerZ = pos.getZ() + clearance.centerOffset();
            double minX = centerX - clearance.halfWidth();
            double maxX = centerX + clearance.halfWidth();
            double minZ = centerZ - clearance.halfWidth();
            double maxZ = centerZ + clearance.halfWidth();
            int x0 = Mth.floor(minX);
            int x1 = Mth.floor(maxX - 1.0E-4);
            int z0 = Mth.floor(minZ);
            int z1 = Mth.floor(maxZ - 1.0E-4);
            int y0 = Mth.floor(baseY);
            int y1 = Mth.floor(baseY + clearance.height() - 1.0E-4);
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        BlockState s = level.getBlockState(new BlockPos(x, y, z));
                        if (s.isSolid() && !openable(s)) return false;
                    }
                }
            }
        }

        if (climbable || usableSupport(level, belowPos, belowState) || stand > 0.01 || water) return true;
        return door;
    }

    /** 门 / 栅栏门 / 活板门"打开后"的状态；不是可开方块返回 null。 */
    @Nullable
    private static BlockState openedStateOf(BlockState state) {
        if (!openable(state)) return null;
        if (state.hasProperty(DoorBlock.OPEN)) return state.setValue(DoorBlock.OPEN, true);
        if (state.hasProperty(FenceGateBlock.OPEN)) return state.setValue(FenceGateBlock.OPEN, true);
        if (state.hasProperty(TrapDoorBlock.OPEN)) return state.setValue(TrapDoorBlock.OPEN, true);
        return state;
    }

    /** 打开后的门类方块是否与给定碰撞盒相交。 */
    private static boolean collidesOpened(Level level, BlockPos pos, BlockState state, AABB box) {
        BlockState opened = openedStateOf(state);
        if (opened == null || opened.isAir()) return false;
        net.minecraft.world.phys.shapes.VoxelShape shape = opened.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        return net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(
                shape.move(pos.getX(), pos.getY(), pos.getZ()),
                net.minecraft.world.phys.shapes.Shapes.create(box),
                net.minecraft.world.phys.shapes.BooleanOp.AND);
    }

    /**
     * 可用的站立支撑面：实心方块，且碰撞顶面不高于脚下一格。
     *   栅栏 / 墙 / 栏杆的碰撞盒高 1.5 格（isSolid() 也为 true），
     *   它们只能挡路，不能当站立面，否则会被规划成"翻栅栏 / 站到栅栏顶上"。
     */
    private static boolean usableSupport(Level level, BlockPos pos, BlockState state) {
        if (!state.isSolid()) return false;
        return state.getCollisionShape(level, pos).max(Direction.Axis.Y) <= 1.05;
    }

    /** 半砖 / 雪层 / 地毯 / 土径等部分方块的可站立表面高度（0 = 无）。 */
    private static double partialTop(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) return 0.0;
        double top = state.getCollisionShape(level, pos).max(Direction.Axis.Y);
        return top > 0.01 && top < 0.99 ? top : 0.0;
    }

    private static boolean isWater(Level level, BlockPos pos) {
        return level.getFluidState(pos).is(FluidTags.WATER);
    }

    private static boolean openable(BlockState state) {
        // 与"单位能用手打开"的判定保持一致：木门 / 铜门 / 模组可手动开的门、栅栏门、
        //   除铁活板门以外的活板门；铁门 / 铁活板门不算可通行。
        return AiNavUtil.isHandOpenable(state);
    }

    @Nullable
    private static BlockPos findStartNear(Level level, LivingEntity entity,
                                          Clearance clearance, BlockPos origin) {
        // 骑乘单位贴着墙 / 台阶边时，脚下这一格往往"站不下"（宽体净空更严），
        //   只沿竖直方向找会直接判定"没有路"。这里按平面距离从近到远扫一圈邻格，
        //   取最近、高度差最小的一处可站点作为规划起点。
        for (int radius = 0; radius <= 4; radius++) {
            BlockPos best = null;
            int bestDy = Integer.MAX_VALUE;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    for (int dy : new int[]{0, -1, 1, -2, 2, -3}) {
                        BlockPos p = origin.offset(dx, dy, dz);
                        if (!walkable(level, entity, clearance, p)) continue;
                        if (Math.abs(dy) < bestDy) {
                            bestDy = Math.abs(dy);
                            best = p;
                        }
                        break;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    private static Vec3 center(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static final class Node {
        final BlockPos pos;
        final double g;
        final double h;
        @Nullable final Node parent;

        Node(BlockPos pos, double g, double h, @Nullable Node parent) {
            this.pos = pos;
            this.g = g;
            this.h = h;
            this.parent = parent;
        }

        double f() { return g + h; }
    }
}
