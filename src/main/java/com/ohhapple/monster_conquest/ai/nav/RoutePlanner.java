package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * 方块级 A* 绕路规划：目标只要求到达目标附近；目标够不到时取"离目标最近的可达节点"，
 * 让单位尽量走到能到的最近位置（而不是守在起点附近的墙边）。邻居为 8 方向 × 台阶高度
 * （上 1 格、下最多 3 格，不切角），木门 / 栅栏门可通行、危险方块不可通行，
 * 支持半砖 / 雪层 / 地毯等部分方块的站立面；受节点数与时间预算限制。
 *
 * 支持传入"上一次的路线"作为代价偏好：靠近旧路线的节点更便宜，
 * 重规划时会优先沿原路线走，避免在两条绕路之间来回切换。
 * 完整路线还会建立全局节点索引，后续单位可从路线中段接入，并用短连接路径补到真实位置。
 */
public final class RoutePlanner {

    /** 规划的结束原因，供上层决定是继续推进、换目标还是延长退避。 */
    public enum Outcome {
        REACHED,
        PARTIAL_NODE_LIMIT,
        PARTIAL_TIME_LIMIT,
        PARTIAL_FRONTIER_EMPTY,
        PARTIAL_RADIUS_LIMIT,
        NO_START,
        NO_ROUTE
    }

    /** 规划结果：路径点（含起点）、是否真正到达目标附近，以及结束原因。 */
    public record Result(List<BlockPos> points, boolean reachedGoal,
                         Outcome outcome, int visitedNodes) {}

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
    private static final int[] STEP_LEVELS = {0, 1, -1, -2, -3};
    /** 一次最多顺着梯子滑多少格（再长就逐节走，避免路径节点跨度过大）。 */
    private static final int SLIDE_LIMIT = 24;

    private static final int MAX_NODES = 12000;
    private static final long TIME_BUDGET_NANOS = 4_000_000L;
    /** 跨 tick 搜索的单 tick 切片；预算仍受全局规划额度限制。 */
    private static final long SEARCH_SLICE_NANOS = 5_000_000L;
    /** 复杂迷宫的单次持久搜索上限。 */
    private static final int SEARCH_NODE_LIMIT = 24000;
    /** 略微加权，减少在迷宫死路和外墙附近展开的节点数。 */
    private static final double SEARCH_HEURISTIC_WEIGHT = 1.15;
    /** 同队单位共享路线的最长复用时间；超过后重新验证 / 规划。 */
    private static final int PATH_CACHE_TICKS = 80;
    private static final int PATH_CACHE_MAX_ENTRIES = 256;
    /** 已完整到达路线的中段接入索引上限，避免长路线把缓存撑大。 */
    private static final int ROUTE_REUSE_MAX_ANCHORS = 32_768;
    private static final int ROUTE_REUSE_ANCHORS_PER_PATH = 1_024;
    private static final int ROUTE_REUSE_SCAN_RADIUS = 5;
    private static final int ROUTE_REUSE_SCAN_DY = 2;
    private static final int ROUTE_REUSE_CONNECTOR_NODES = 512;
    private static final long ROUTE_REUSE_CONNECTOR_BUDGET_NANOS = 2_000_000L;
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
        Result cached = findCachedPath(level, entity, goal, goalRadius, maxRadius,
                maxNodes, extraHeight, favored, smooth);
        if (cached != null) {
            lastCallDeferred = false;
            return cached;
        }
        if (nanosUsed >= NANOS_PER_TICK) {
            lastCallDeferred = true;
            return null;
        }
        lastCallDeferred = false;
        long budgetStartNanos = System.nanoTime();
        // 单次规划期间缓存"格子能不能走"：同一条走廊相邻节点会重复查询，
        //   迷宫搜索能因此快好几倍（服务端单线程，静态缓存安全）。
        Map<Long, Boolean> localWalkCache = new HashMap<>();
        Map<Long, Boolean> previousWalkCache = activeWalkCache;
        activeWalkCache = localWalkCache;
        try {
            Result result = planInternal(level, entity, goal, goalRadius, maxRadius, maxNodes,
                    extraHeight, favored, smooth, timeBudgetNanos);
            storeCachedPath(level, entity, goal, goalRadius, maxRadius, maxNodes,
                    extraHeight, favored, smooth, result);
            return result;
        } finally {
            activeWalkCache = previousWalkCache;
            nanosUsed += System.nanoTime() - budgetStartNanos;
        }
    }

    /** 当前规划自己的通行缓存；跨 tick 的搜索会保留整份缓存。 */
    @Nullable private static Map<Long, Boolean> activeWalkCache = null;
    private static final Map<CacheKey, CachedPath> PATH_CACHE = new HashMap<>();
    private static final RouteReuseIndex ROUTE_REUSE_INDEX = new RouteReuseIndex();
    private static final Map<ResourceLocation, Long> WORLD_VERSIONS = new HashMap<>();
    private static long pathCacheTick = Long.MIN_VALUE;

    /** 全局每 tick 规划预算（纳秒）：所有调用点合计，防止战斗中大量单位同时跑 A* 造成掉刻。 */
    private static final long NANOS_PER_TICK = 5_000_000L;
    private static long budgetTick = Long.MIN_VALUE;
    private static long nanosUsed = 0L;
    private static boolean lastCallDeferred = false;

    /**
     * 共享路线缓存键：只复用几何条件完全相同的路线。
     *   起点是精确格；目标、净空、平滑方式和方块变化版本参与比较。
     *   搜索半径 / 节点上限只影响单次查询，不阻止同几何路线复用。
     *   旧路线偏好只影响本次选路，不阻止其他单位复用同一条几何路线。
     */
    private record CacheKey(ResourceLocation dimension, long worldVersion, long start, long goal,
                            long widthBits, long heightBits, long centerBits,
                            double goalRadius, double maxRadius, int maxNodes,
                            boolean smooth) {}

    private record CachedPath(CacheKey key, Result result, long createdTick) {}

    private record RouteAnchor(CachedPath path, int nodeIndex) {}

    private record ReuseChoice(CachedPath path, int nodeIndex, double distanceSq) {}

    /**
     * 完整路线的中段接入索引。
     *
     * <p>路线可能被多个单位从不同位置接入。索引只保存少量采样节点，
     * 命中后再用短连接路径接到真实单位位置；未到达目标的半路结果不会进入索引。</p>
     */
    private static final class RouteReuseIndex {
        private final Map<Long, List<RouteAnchor>> anchorsByNode = new HashMap<>();
        private final Map<CachedPath, List<Long>> keysByPath = new IdentityHashMap<>();
        private int anchorCount;

        private void clear() {
            anchorsByNode.clear();
            keysByPath.clear();
            anchorCount = 0;
        }

        private void add(CachedPath path) {
            if (path == null || !path.result().reachedGoal()) return;

            List<BlockPos> points = path.result().points();
            if (points.size() < 2 || keysByPath.containsKey(path)) return;

            int stride = Math.max(1,
                    (points.size() + ROUTE_REUSE_ANCHORS_PER_PATH - 1)
                            / ROUTE_REUSE_ANCHORS_PER_PATH);
            List<Long> indexedKeys = new ArrayList<>();
            for (int i = 0; i < points.size() && anchorCount < ROUTE_REUSE_MAX_ANCHORS; i += stride) {
                long key = points.get(i).asLong();
                anchorsByNode.computeIfAbsent(key, ignored -> new ArrayList<>(2))
                        .add(new RouteAnchor(path, i));
                indexedKeys.add(key);
                anchorCount++;
            }
            if (!indexedKeys.isEmpty()) {
                keysByPath.put(path, indexedKeys);
            }
        }

        private void remove(CachedPath path) {
            List<Long> indexedKeys = keysByPath.remove(path);
            if (indexedKeys == null) return;

            for (long key : indexedKeys) {
                List<RouteAnchor> anchors = anchorsByNode.get(key);
                if (anchors == null) continue;
                int before = anchors.size();
                anchors.removeIf(anchor -> anchor.path() == path);
                anchorCount -= before - anchors.size();
                if (anchors.isEmpty()) anchorsByNode.remove(key);
            }
        }

        @Nullable
        private ReuseChoice find(CacheKey lookup, BlockPos start) {
            BlockPos target = BlockPos.of(lookup.goal());
            double startToTarget = start.distSqr(target);
            ReuseChoice best = null;

            int baseX = start.getX();
            int baseY = start.getY();
            int baseZ = start.getZ();
            for (int dx = -ROUTE_REUSE_SCAN_RADIUS; dx <= ROUTE_REUSE_SCAN_RADIUS; dx++) {
                for (int dz = -ROUTE_REUSE_SCAN_RADIUS; dz <= ROUTE_REUSE_SCAN_RADIUS; dz++) {
                    for (int dy = -ROUTE_REUSE_SCAN_DY; dy <= ROUTE_REUSE_SCAN_DY; dy++) {
                        long key = BlockPos.asLong(baseX + dx, baseY + dy, baseZ + dz);
                        List<RouteAnchor> anchors = anchorsByNode.get(key);
                        if (anchors == null || anchors.isEmpty()) continue;

                        for (RouteAnchor anchor : anchors) {
                            CachedPath path = anchor.path();
                            if (!sameRoute(path.key(), lookup)) continue;
                            List<BlockPos> points = path.result().points();
                            if (anchor.nodeIndex() < 0 || anchor.nodeIndex() >= points.size()) continue;

                            BlockPos node = points.get(anchor.nodeIndex());
                            // 只允许向前接入；接到更远离目标的节点会让单位无谓折返。
                            if (node.distSqr(target) > startToTarget + 1.0D) continue;

                            double distanceSq = node.distSqr(start);
                            if (best == null
                                    || distanceSq < best.distanceSq()
                                    || (distanceSq == best.distanceSq()
                                            && anchor.nodeIndex() > best.nodeIndex())) {
                                best = new ReuseChoice(path, anchor.nodeIndex(), distanceSq);
                            }
                        }
                    }
                }
            }
            return best;
        }
    }

    /** 上一次 plan() 返回 null 是否只是"本 tick 预算被占满"（不是真的没有路）。 */
    public static boolean wasLastCallDeferred() {
        return lastCallDeferred;
    }

    /**
     * 可跨 tick 继续的 A* 搜索。MarchRouteFollower 用它处理"时间片用完但目标
     * 仍可能可达"的复杂迷宫，避免把外墙边的一小段半路路线交给单位执行。
     */
    public static final class SearchState {
        private final BlockPos start;
        private final Vec3 goal;
        private final double goalRadiusSq;
        private final double maxRadiusSq;
        private final int nodeBudget;
        private final Clearance clearance;
        private final Map<BlockPos, Double> favor;
        private final boolean smooth;
        @Nullable private final CacheKey cacheKey;
        private final Map<BlockPos, Node> nodes = new HashMap<>();
        private final Set<BlockPos> closed = new HashSet<>();
        private final PriorityQueue<Node> open = new PriorityQueue<>(
                Comparator.comparingDouble(n -> n.f(SEARCH_HEURISTIC_WEIGHT)));
        private final Map<Long, Boolean> walkCache = new HashMap<>();

        @Nullable private Node reached;
        @Nullable private Node closest;
        private double closestH = Double.MAX_VALUE;
        private final Node[] bests = new Node[BEST_COEFFICIENTS.length];
        private final double[] bestScores = new double[BEST_COEFFICIENTS.length];
        private int visited;
        private boolean finished;
        @Nullable private Result result;

        private SearchState(BlockPos start, Vec3 goal, double goalRadius, double maxRadius,
                            int nodeBudget, Clearance clearance,
                            Map<BlockPos, Double> favor, boolean smooth,
                            @Nullable CacheKey cacheKey) {
            this.start = start;
            this.goal = goal;
            this.goalRadiusSq = goalRadius * goalRadius;
            this.maxRadiusSq = maxRadius * maxRadius;
            this.nodeBudget = Math.max(256, Math.min(nodeBudget, SEARCH_NODE_LIMIT));
            this.clearance = clearance;
            this.favor = favor;
            this.smooth = smooth;
            this.cacheKey = cacheKey;
            Arrays.fill(this.bestScores, Double.MAX_VALUE);
            Node startNode = new Node(start, 0.0, center(start).distanceTo(goal), null);
            nodes.put(start, startNode);
            open.add(startNode);
        }

        public boolean isFinished() { return finished; }
        @Nullable public Result result() { return result; }
    }

    /**
     * 新建持久搜索；若共享缓存里已有同起点路线则直接返回完成状态。
     * 返回 null 表示起点附近找不到可通行格。
     */
    @Nullable
    public static SearchState beginSearch(Level level, LivingEntity entity, Vec3 goal,
                                          double goalRadius, double maxRadius, int maxNodes,
                                          double extraHeight, @Nullable List<BlockPos> favored,
                                          boolean smooth) {
        Result cached = findCachedPath(level, entity, goal, goalRadius, maxRadius,
                maxNodes, extraHeight, favored, smooth);
        Clearance clearance = clearanceOf(entity, extraHeight);
        BlockPos start = entity.blockPosition();
        if (!walkableUncached(level, entity, clearance, start)) {
            start = findStartNear(level, entity, clearance, start);
            if (start == null) return null;
        }
        CacheKey key = cacheKey(level, entity, goal, goalRadius, maxRadius, maxNodes,
                extraHeight, favored, smooth);
        SearchState state = new SearchState(start, goal, goalRadius, maxRadius,
                Math.min(maxNodes, SEARCH_NODE_LIMIT), clearance,
                buildFavor(favored), smooth, key);
        if (cached != null) {
            state.finished = true;
            state.result = cached;
        }
        return state;
    }

    /**
     * 推进一个持久搜索的时间片。
     *
     * @return true = 已结束（结果可能为 null）；false = 本 tick 预算用完，仍可继续
     */
    public static boolean advanceSearch(SearchState state, Level level, LivingEntity entity) {
        if (state.finished) return true;
        if (state.cacheKey == null
                || state.cacheKey.worldVersion()
                        != WORLD_VERSIONS.getOrDefault(level.dimension().location(), 0L)) {
            state.finished = true;
            state.result = null;
            return true;
        }

        long nowTick = level.getGameTime();
        if (nowTick != budgetTick) {
            long elapsedTicks = Math.max(1L, nowTick - budgetTick);
            nanosUsed = Math.max(0L, nanosUsed - elapsedTicks * NANOS_PER_TICK);
            budgetTick = nowTick;
        }
        long available = NANOS_PER_TICK - nanosUsed;
        if (available <= 0L) return false;

        long budgetStartNanos = System.nanoTime();
        long deadline = budgetStartNanos + Math.min(SEARCH_SLICE_NANOS, available);
        Map<Long, Boolean> previousWalkCache = activeWalkCache;
        activeWalkCache = state.walkCache;
        try {
            while (!state.open.isEmpty() && state.visited < state.nodeBudget) {
                if ((state.visited & 31) == 0 && System.nanoTime() > deadline) {
                    return false;
                }

                Node current = state.open.poll();
                Node known = state.nodes.get(current.pos);
                if (known == null || current.g > known.g + 1.0E-6) continue;
                if (!state.closed.add(current.pos)) continue;
                state.visited++;

                if (center(current.pos).distanceToSqr(state.goal) <= state.goalRadiusSq) {
                    state.reached = current;
                    break;
                }

                double distFromStart = center(current.pos).distanceTo(center(state.start));
                if (distFromStart >= MIN_PARTIAL_DISTANCE) {
                    if (current.h < state.closestH) {
                        state.closestH = current.h;
                        state.closest = current;
                    }
                    for (int i = 0; i < BEST_COEFFICIENTS.length; i++) {
                        double score = current.h + current.g / BEST_COEFFICIENTS[i];
                        if (score < state.bestScores[i]) {
                            state.bestScores[i] = score;
                            state.bests[i] = current;
                        }
                    }
                }

                expand(level, entity, state.clearance, current, state.goal, state.start,
                        state.maxRadiusSq, state.favor, state.nodes, state.closed, state.open);
            }

            Node end = state.reached;
            Outcome outcome;
            if (end != null) {
                outcome = Outcome.REACHED;
            } else {
                end = state.closest;
                if (end == null) {
                    for (Node best : state.bests) {
                        if (best != null) {
                            end = best;
                            break;
                        }
                    }
                }
                outcome = state.open.isEmpty()
                        ? Outcome.PARTIAL_FRONTIER_EMPTY : Outcome.PARTIAL_NODE_LIMIT;
            }
            state.finished = true;
            if (end == null) {
                state.result = null;
                return true;
            }
            List<BlockPos> raw = reconstruct(end);
            if (raw.size() < 2) {
                state.result = null;
                return true;
            }
            state.result = new Result(state.smooth
                    ? smooth(level, entity, state.clearance, raw) : raw,
                    outcome == Outcome.REACHED, outcome, state.visited);
            cacheResult(level, state.cacheKey, state.result);
            return true;
        } finally {
            activeWalkCache = previousWalkCache;
            nanosUsed += System.nanoTime() - budgetStartNanos;
        }
    }

    /** 方块发生变化后让该维度的旧路线缓存失配。 */
    public static void noteWorldChanged(ResourceLocation dimension) {
        if (dimension == null) return;
        WORLD_VERSIONS.merge(dimension, 1L, Long::sum);
    }

    public static long worldVersion(Level level) {
        return WORLD_VERSIONS.getOrDefault(level.dimension().location(), 0L);
    }

    /** 服务端停止时释放跨存档缓存。 */
    public static void clearCaches() {
        PATH_CACHE.clear();
        ROUTE_REUSE_INDEX.clear();
        activeWalkCache = null;
        WORLD_VERSIONS.clear();
        pathCacheTick = Long.MIN_VALUE;
        budgetTick = Long.MIN_VALUE;
        nanosUsed = 0L;
        lastCallDeferred = false;
    }

    /** 移动实体 + 额外高度（骑乘时传骑手高度）对应的通行净空。 */
    private static Clearance clearanceOf(LivingEntity entity, double extraHeight) {
        return new Clearance(
                entity.getBbWidth() * 0.5 + 0.05,
                entity.getBbHeight() + extraHeight + 0.05,
                ((int) (entity.getBbWidth() + 1.0F)) * 0.5);
    }

    private static CacheKey cacheKey(Level level, LivingEntity entity, Vec3 goal,
                                     double goalRadius, double maxRadius, int maxNodes,
                                     double extraHeight, @Nullable List<BlockPos> favored,
                                     boolean smooth) {
        Clearance clearance = clearanceOf(entity, extraHeight);
        BlockPos start = entity.blockPosition();
        BlockPos target = BlockPos.containing(goal.x, goal.y, goal.z);
        ResourceLocation dimension = level.dimension().location();
        return new CacheKey(dimension, WORLD_VERSIONS.getOrDefault(dimension, 0L),
                start.asLong(), target.asLong(),
                Double.doubleToLongBits(clearance.halfWidth()),
                Double.doubleToLongBits(clearance.height()),
                Double.doubleToLongBits(clearance.centerOffset()),
                goalRadius, maxRadius, maxNodes, smooth);
    }

    private static boolean sameRoute(CacheKey a, CacheKey b) {
        return a.dimension().equals(b.dimension())
                && a.worldVersion() == b.worldVersion()
                && a.goal() == b.goal()
                && a.widthBits() == b.widthBits()
                && a.heightBits() == b.heightBits()
                && a.centerBits() == b.centerBits()
                && Double.compare(a.goalRadius(), b.goalRadius()) == 0
                && a.smooth() == b.smooth();
    }

    @Nullable
    private static Result findCachedPath(Level level, LivingEntity entity, Vec3 goal,
                                         double goalRadius, double maxRadius, int maxNodes,
                                         double extraHeight, @Nullable List<BlockPos> favored,
                                         boolean smooth) {
        long now = level.getGameTime();
        if (now < pathCacheTick) {
            PATH_CACHE.clear();
            ROUTE_REUSE_INDEX.clear();
        }
        pathCacheTick = now;
        prunePathCache(now);
        if (PATH_CACHE.isEmpty()) return null;

        BlockPos start = entity.blockPosition();
        if (!walkableFor(level, entity, extraHeight, start)) return null;
        CacheKey lookup = cacheKey(level, entity, goal, goalRadius, maxRadius, maxNodes,
                extraHeight, favored, smooth);
        CachedPath exact = PATH_CACHE.get(lookup);
        if (exact != null && exact.result().points().size() >= 2
                && exact.result().points().get(0).equals(start)) {
            logCacheHit("exact", start, lookup.goal(), exact.result().points().size());
            return exact.result();
        }

        for (CachedPath cached : PATH_CACHE.values()) {
            if (!sameRoute(cached.key(), lookup)) continue;
            Result attached = attachToCachedPath(level, entity, extraHeight, start, cached.result());
            if (attached != null) {
                logCacheHit("attach", start, lookup.goal(), attached.points().size());
                return attached;
            }
        }

        Result joined = reuseIndexedPath(level, entity, lookup, start, extraHeight);
        if (joined != null) {
            logCacheHit("join", start, lookup.goal(), joined.points().size());
            return joined;
        }
        return null;
    }

    @Nullable
    private static Result attachToCachedPath(Level level, LivingEntity entity,
                                             double extraHeight, BlockPos start,
                                             Result cached) {
        List<BlockPos> points = cached.points();
        int attachIndex = -1;
        for (int i = 0; i < points.size(); i++) {
            BlockPos node = points.get(i);
            if (node.equals(start)) {
                attachIndex = i;
                break;
            }
            int dx = Math.abs(node.getX() - start.getX());
            int dz = Math.abs(node.getZ() - start.getZ());
            int dy = Math.abs(node.getY() - start.getY());
            if (dx + dz == 1 && dy <= 1
                    && walkableFor(level, entity, extraHeight, node)) {
                attachIndex = i;
                break;
            }
        }
        if (attachIndex < 0) return null;
        if (attachIndex == 0 && points.get(0).equals(start)) return cached;

        List<BlockPos> attached = new ArrayList<>(points.size() - attachIndex + 1);
        attached.add(start);
        for (int i = attachIndex; i < points.size(); i++) attached.add(points.get(i));
        return new Result(attached, cached.reachedGoal(), cached.outcome(), cached.visitedNodes());
    }

    /**
     * 从已完整到达的旧路线中段接入。
     *   先用可见直线接到索引节点；线路被墙挡住时，再用一个小范围、低节点上限的
     *   连接搜索补出接头。连接搜索未真正到达节点时直接放弃，不把半路连接交给单位。
     */
    @Nullable
    private static Result reuseIndexedPath(Level level, LivingEntity entity, CacheKey lookup,
                                           BlockPos start, double extraHeight) {
        ReuseChoice choice = ROUTE_REUSE_INDEX.find(lookup, start);
        if (choice == null) return null;

        List<BlockPos> route = choice.path().result().points();
        if (choice.nodeIndex() < 0 || choice.nodeIndex() >= route.size()) return null;
        BlockPos joinNode = route.get(choice.nodeIndex());
        if (!walkableFor(level, entity, extraHeight, joinNode)) return null;

        List<BlockPos> prefix;
        int connectorVisited = 0;
        long started = System.nanoTime();

        if (start.equals(joinNode)) {
            prefix = List.of(start);
        } else if (straightWalkable(level, entity, clearanceOf(entity, extraHeight),
                start, joinNode)) {
            prefix = List.of(start, joinNode);
        } else {
            if (nanosUsed >= NANOS_PER_TICK) return null;
            double distance = Math.sqrt(start.distSqr(joinNode));
            double connectorRadius = Math.min(24.0, Math.max(8.0, distance + 4.0));
            Result connector = planInternal(level, entity, center(joinNode), 0.35,
                    connectorRadius, ROUTE_REUSE_CONNECTOR_NODES, extraHeight,
                    null, false, ROUTE_REUSE_CONNECTOR_BUDGET_NANOS);
            if (connector == null || !connector.reachedGoal() || connector.points().isEmpty()) {
                return null;
            }
            prefix = connector.points();
            connectorVisited = connector.visitedNodes();
        }
        nanosUsed += System.nanoTime() - started;

        if (prefix.isEmpty() || !prefix.get(prefix.size() - 1).equals(joinNode)) return null;

        int tailStart = choice.nodeIndex() + 1;
        List<BlockPos> joined = new ArrayList<>(prefix.size() + route.size() - tailStart);
        joined.addAll(prefix);
        for (int i = tailStart; i < route.size(); i++) {
            joined.add(route.get(i));
        }
        if (joined.size() < 2) return null;

        Result base = choice.path().result();
        return new Result(joined, base.reachedGoal(), base.outcome(),
                base.visitedNodes() + connectorVisited);
    }

    private static void storeCachedPath(Level level, LivingEntity entity, Vec3 goal,
                                        double goalRadius, double maxRadius, int maxNodes,
                                        double extraHeight, @Nullable List<BlockPos> favored,
                                        boolean smooth, @Nullable Result result) {
        if (result == null || result.points().size() < 2) return;
        CacheKey key = cacheKey(level, entity, goal, goalRadius, maxRadius, maxNodes,
                extraHeight, favored, smooth);
        cacheResult(level, key, result);
    }

    /** 写入共享路线缓存。 */
    private static void cacheResult(Level level, @Nullable CacheKey key, @Nullable Result result) {
        if (key == null || result == null || result.points().size() < 2) return;
        long now = level.getGameTime();
        if (now < pathCacheTick) {
            PATH_CACHE.clear();
            ROUTE_REUSE_INDEX.clear();
        }
        pathCacheTick = now;
        CachedPath path = new CachedPath(key, result, now);
        CachedPath previous = PATH_CACHE.put(key, path);
        if (previous != null) {
            ROUTE_REUSE_INDEX.remove(previous);
        }
        ROUTE_REUSE_INDEX.add(path);
        prunePathCache(now);
    }

    private static void prunePathCache(long now) {
        Iterator<Map.Entry<CacheKey, CachedPath>> iterator = PATH_CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            CachedPath path = iterator.next().getValue();
            if (now - path.createdTick() > PATH_CACHE_TICKS || now < path.createdTick()) {
                iterator.remove();
                ROUTE_REUSE_INDEX.remove(path);
            }
        }
        if (PATH_CACHE.size() <= PATH_CACHE_MAX_ENTRIES) return;
        List<CacheKey> keys = new ArrayList<>(PATH_CACHE.keySet());
        keys.sort(Comparator.comparingLong(key -> PATH_CACHE.get(key).createdTick()));
        int remove = PATH_CACHE.size() - PATH_CACHE_MAX_ENTRIES;
        for (int i = 0; i < remove; i++) {
            CachedPath removed = PATH_CACHE.remove(keys.get(i));
            if (removed != null) ROUTE_REUSE_INDEX.remove(removed);
        }
    }

    private static void logCacheHit(String type, BlockPos start, long goal, int points) {
        if (!GeneralConfig.debugMovementLog) return;
        Monster_conquest.LOGGER.info(
                "[ROUTE-CACHE] type={} start={} goal={} points={}",
                type, start.toShortString(), BlockPos.of(goal).toShortString(), points);
    }

    /**
     * 供"可达性搜索"复用：指定移动实体（骑乘时传坐骑 + 骑手高度）能不能站 / 通过这一格。
     *   与 {@link #plan} 内部用的是同一套判定，保证"可达性"和"能规划出路线"不打架。
     */
    public static boolean walkableFor(Level level, LivingEntity mover, double extraHeight,
                                      BlockPos pos) {
        return walkableUncached(level, mover, clearanceOf(mover, extraHeight), pos);
    }

    /**
     * 把已经算好的路线接到实体当前位置。规划跨 tick 时单位会继续沿原版路径移动，
     * 完成时只要它仍在路线节点上或紧邻节点，就从当前位置继续，不必回退到旧起点。
     */
    @Nullable
    public static Result attachToResult(Level level, LivingEntity entity, double extraHeight,
                                        Result result) {
        if (result == null || result.points().isEmpty()) return null;
        return attachToCachedPath(level, entity, extraHeight,
                entity.blockPosition(), result);
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
        PriorityQueue<Node> open = new PriorityQueue<>(
                Comparator.comparingDouble(node -> node.f(1.0)));

        Node startNode = new Node(start, 0.0, center(start).distanceTo(goal), null);
        nodes.put(start, startNode);
        open.add(startNode);

        Node reached = null;
        // 目标够不到时用的"离目标最近的可达节点"。
        Node closest = null;
        double closestH = Double.MAX_VALUE;
        Node[] bests = new Node[BEST_COEFFICIENTS.length];
        double[] bestScores = new double[BEST_COEFFICIENTS.length];
        Arrays.fill(bestScores, Double.MAX_VALUE);
        int visited = 0;
        boolean timeLimitHit = false;

        while (!open.isEmpty() && visited < nodeBudget) {
            if ((visited & 63) == 0 && System.nanoTime() > deadline) {
                timeLimitHit = true;
                break;
            }

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
                if (current.h < closestH) {
                    closestH = current.h;
                    closest = current;
                }
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
            // 目标够不到：取"离目标最近的可达节点"，让单位走到能到的最近位置
            //   （封闭中心的迷宫会走到中心外圈、封闭房间会走到房间墙边），
            //   而不是挑一个"离起点近、少绕路"的折中节点 —— 那样单位会守着附近的墙不动。
            end = closest;
            if (end == null) {
                for (Node best : bests) {
                    if (best != null) { end = best; break; }
                }
            }
        }
        if (end == null) {
            return null;
        }

        List<BlockPos> raw = reconstruct(end);
        if (raw.size() < 2) return null;
        Outcome outcome;
        if (reached != null) {
            outcome = Outcome.REACHED;
        } else if (timeLimitHit) {
            outcome = Outcome.PARTIAL_TIME_LIMIT;
        } else if (visited >= nodeBudget) {
            outcome = Outcome.PARTIAL_NODE_LIMIT;
        } else {
            outcome = Outcome.PARTIAL_FRONTIER_EMPTY;
        }
        return new Result(smooth ? smooth(level, entity, clearance, raw) : raw,
                reached != null, outcome, visited);
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
        // 梯子列：梯子不是"可站立面"，原版的水平 / 台阶扩展走不出"沿梯子上下"，
        //   这里显式连上：逐节上下 + 顺着列直接滑到下方第一个落点，上下都要连
        //   （上塔、下塔、借梯子翻墙绕路都靠它）。单位会照着路线上的梯子节点真的爬 /
        //   滑下去，动作由梯子感知移动控制器执行，不再要求"目标恰好在上方"。
        if (ClimbColumns.isClimbCell(level, current.pos)) {
            int clearanceHeight = Mth.ceil(clearance.height());
            for (int dy : new int[] { 1, -1 }) {
                BlockPos next = current.pos.above(dy);
                if (!ClimbColumns.isClimbCell(level, next)) continue;
                if (!walkable(level, entity, clearance, next)) continue;
                relaxNext(next, current, dy > 0 ? 1.0 : 0.9, goal, favor, start, maxRadiusSq, nodes, open);
            }
            BlockPos landing = ClimbColumns.findSlideLanding(
                    level, current.pos, clearanceHeight, SLIDE_LIMIT);
            if (landing != null && walkable(level, entity, clearance, landing)) {
                relaxNext(landing, current, 1.2, goal, favor, start, maxRadiusSq, nodes, open);
            }
        }

        for (int[] dir : DIRECTIONS) {
            int nx = current.pos.getX() + dir[0];
            int nz = current.pos.getZ() + dir[1];
            boolean diagonal = dir[0] != 0 && dir[1] != 0;
            for (int dy : STEP_LEVELS) {
                BlockPos next = new BlockPos(nx, current.pos.getY() + dy, nz);
                if (!walkable(level, entity, clearance, next)) continue;
                if (closed.contains(next)) continue;

                double dx = next.getX() - start.getX();
                double dz = next.getZ() - start.getZ();
                if (dx * dx + dz * dz > maxRadiusSq) continue;

                // 斜向必须检查候选节点所在高度的两个侧格；只看当前层会允许
                // 跨台阶时从墙角斜穿过去。
                if (diagonal) {
                    BlockPos sideX = new BlockPos(nx, next.getY(), current.pos.getZ());
                    BlockPos sideZ = new BlockPos(current.pos.getX(), next.getY(), nz);
                    if (!walkable(level, entity, clearance, sideX)
                            || !walkable(level, entity, clearance, sideZ)) {
                        continue;
                    }
                }

                double cost = diagonal ? 1.414 : 1.0;
                cost += dy > 0 ? dy * 0.8 : -dy * 0.25;
                if (isWater(level, next)) cost += 0.8;
                Double fav = favor.get(next);
                if (fav != null) cost *= fav;

                double g = current.g + cost;
                Node known = nodes.get(next);
                if (known != null && g >= known.g - 1.0E-6) continue;
                double h = center(next).distanceTo(goal);
                Node node = new Node(next, g, h, current);
                nodes.put(next, node);
                open.add(node);
                break;
            }
        }
    }

    /** 登记一条到 next 的边：比已知代价更低才更新（梯子列的上下 / 下滑用）。 */
    private static void relaxNext(BlockPos next, Node current, double baseCost, Vec3 goal,
                                  Map<BlockPos, Double> favor, BlockPos start, double maxRadiusSq,
                                  Map<BlockPos, Node> nodes, PriorityQueue<Node> open) {
        double dx = next.getX() - start.getX();
        double dz = next.getZ() - start.getZ();
        if (dx * dx + dz * dz > maxRadiusSq) return;
        double cost = baseCost;
        Double fav = favor.get(next);
        if (fav != null) cost *= fav;
        double g = current.g + cost;
        Node known = nodes.get(next);
        if (known != null && g >= known.g - 1.0E-6) return;
        Node node = new Node(next, g, center(next).distanceTo(goal), current);
        nodes.put(next, node);
        open.add(node);
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
        Map<Long, Boolean> walkCache = activeWalkCache;
        if (walkCache != null) {
            Long key = pos.asLong();
            Boolean cached = walkCache.get(key);
            if (cached != null) return cached;
            boolean result = walkableUncached(level, entity, clearance, pos);
            walkCache.put(key, result);
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

        // 门的上半格不是落脚点：能走的是门下半格那一层。
        //   把上半格也算可走会让 A* 从门外"斜着踩进门上半格"再下来（路线节点悬空一格），
        //   看着像能穿门，实际跟随要靠原版重新寻路兜底，复杂门洞/骑乘时就会卡在门口。
        boolean upperDoorHalf = feet.getBlock() instanceof DoorBlock
                && feet.hasProperty(DoorBlock.HALF)
                && feet.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER;
        if (upperDoorHalf) return false;
        boolean door = !upperDoorHalf && (openable(feet) || openable(head));
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
            if (blockedInDoorway(level, box)) return false;
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
        VoxelShape shape = opened.getCollisionShape(level, pos);
        if (shape.isEmpty()) return false;
        return Shapes.joinIsNotEmpty(
                shape.move(pos.getX(), pos.getY(), pos.getZ()),
                Shapes.create(box),
                BooleanOp.AND);
    }

    /**
     * 门洞净空：把门按"打开后的门板"算，其余方块按原样算。
     *   只检查门方块本身会漏掉门洞上方 / 门框的实心方块 ——
     *   两格高的门洞对"马 + 骑手"（约 2.75 格高）其实过不去，必须挡掉；
     *   否则规划会把骑兵路线引到门口，跟随时被门楣顶住。
     */
    private static boolean blockedInDoorway(Level level, AABB box) {
        int x0 = Mth.floor(box.minX);
        int x1 = Mth.floor(box.maxX - 1.0E-4);
        int y0 = Mth.floor(box.minY);
        int y1 = Mth.floor(box.maxY - 1.0E-4);
        int z0 = Mth.floor(box.minZ);
        int z1 = Mth.floor(box.maxZ - 1.0E-4);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(p);
                    if (openable(state)) {
                        if (collidesOpened(level, p, state, box)) return true;
                    } else if (state.isSolid()) {
                        return true;
                    }
                }
            }
        }
        return false;
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

        double f(double weight) { return g + h * weight; }
    }
}
