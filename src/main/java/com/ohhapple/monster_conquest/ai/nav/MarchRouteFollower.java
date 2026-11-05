package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 指定点（手动行军 / 手动移动）的"方块级 A* 绕路"路线，士兵与首领共用。
 *
 *   原版寻路遇到迷宫 / 长绕路时只会返回"最接近目标的半路路径"（甚至没有路），
 *   单位就会顶着墙原地打转。这里用 {@link RoutePlanner} 规划真正的绕路，然后
 *   把逐格节点包成原版 {@link Path} 交给原版寻路跟随 —— 转身、切角、速度都用原版那一套。
 *
 *   · 骑乘时先用"马 + 骑手"净空规划；只要骑马还能明显接近目标就先骑着走，
 *     只有骑马再也推进不了（窄道 / 迷宫口）才下马改用步行方案；
 *   · 每单位最短 10 tick 规划一次；时间片用完时保留 A* 搜索状态，跨 tick 继续，
 *     不把"时间到了"的外墙半路路线交给单位；
 *   · 全局节流：每个搜索时间片由 {@link RoutePlanner} 统一记账（所有调用点共用）；
 *     本 tick 轮不到的单位按"稍后重试"处理（不是"没有路"）；
 *   · 战斗目标出现 / 到达 / 超时 / 长时间顶着方块 → 放弃路线交回上层逻辑。
 */
public final class MarchRouteFollower {

    /**
     * 目的点到达判定（格²，水平距离）。贴着目的点才交回上层：
     *   路线规划本来就算到目的格，提前交回会让单位停在"目的点旁边一格"（迷宫里就是
     *   停在拐角另一侧），上层再按两格容差判到位，最终就停在离命令点两格的地方。
     */
    private static final double GOAL_REACHED_SQR = 1.5 * 1.5;
    /**
     * 路线规划的目的半径（格）：必须把路线算到目的格本身。
     *   留两格容差时路线会在"离目的点两格"处收尾（reachedGoal=true），
     *   单位跟着路线走完就是差两格不到位。
     */
    private static final double PLAN_GOAL_RADIUS = 0.6;
    /** 一直顶着方块多久算"这条路线走不通"（tick）。 */
    private static final int BLOCKED_LIMIT_TICKS = 100;
    /** 进度取样窗口（tick）：隔着窗口看"有没有真的往前走"。 */
    private static final int PROGRESS_SAMPLE_TICKS = 10;
    /** 一个取样窗口里位移小于这个距离（格）= 没在前进。 */
    private static final double PROGRESS_MIN_TRAVEL = 0.35;
    /** 同一单位两次规划之间的最短间隔（tick）。 */
    private static final int REPLAN_COOLDOWN_TICKS = 10;
    /** 规划失败（没有路 / 半路路线也没进展）时的退避上限（tick）。 */
    private static final int MAX_PLAN_BACKOFF_TICKS = 200;
    /** 路线最短有效期（tick）；长路线按节点数自动加长，避免走到一半过期停下。 */
    private static final int MIN_TTL_TICKS = 1200;
    /** 玩家 / 首领下达的指定点可以比战斗绕路搜得更远一些。 */
    private static final int DEFAULT_MAX_NODES = 24000;
    /** 困难等级上限；仅用于失败退避强度，搜索本身按时间片持续推进。 */
    private static final int MAX_HARD_LEVEL = 2;
    /** 复杂迷宫绕行可能远超直线距离：搜索半径上限给足。 */
    private static final double MAX_PLAN_RADIUS = 512.0;

    /** 攀爬豁免：只有"确实在往上爬（或刚停一下）"才不算顶着方块。 */
    private static final int CLIMB_GRACE_TICKS = 40;
    @Nullable private Path path;
    @Nullable private Vec3 goal;
    @Nullable private Mob trackedMover;
    private int expireTick;
    /** 攀爬进度追踪（梯子卡住的判定用）：上一 tick 的高度 / 连续没有上下进展多少 tick。 */
    private double lastLadderY = Double.NaN;
    private int climbStallTicks = 0;
    private int lastPlanTick = -10000;
    private int blockedTicks;
    /** 贴墙进度取样：上一次取样的位置 / 路径节点。 */
    private double progressLastX = Double.NaN;
    private double progressLastZ = Double.NaN;
    private int progressLastNode = -1;
    private int progressWindowTicks;
    /** 当前规划冷却（失败后指数退避；成功恢复到 REPLAN_COOLDOWN_TICKS）。 */
    private int planBackoffTicks = REPLAN_COOLDOWN_TICKS;
    /** 困难情况等级：越高，下一次规划用越大的时间预算。 */
    private int hardLevel = 0;
    /** 上一次 start() 返回 false 是否只是被全局节流 / 预算推迟（而不是真的规划不出路）。 */
    private boolean lastStartDeferred = false;
    /** 最近一次未完成路线的终点；重复得到同一终点时不再反复安装同一条路线。 */
    @Nullable private Vec3 lastPartialEnd;
    @Nullable private Vec3 lastPartialTarget;
    private int partialRepeatCount;
    private long lastPartialWorldVersion = Long.MIN_VALUE;
    /** 当前安装的路线是否真正把目标算进去了。 */
    private boolean routeReachesGoal;
    private long routeWorldVersion = Long.MIN_VALUE;
    @Nullable private PendingPlan pendingPlan;
    @Nullable private NextLeg nextLeg;

    /** 跨 tick 规划上下文；骑乘方案无进展时还要在同一上下文里补算步行方案。 */
    private static final class PendingPlan {
        final RoutePlanner.SearchState search;
        final Vec3 target;
        final Mob rider;
        @Nullable final Mob mount;
        final double extraHeight;
        final double planRadius;
        @Nullable final List<BlockPos> favored;
        final boolean allowPartial;
        final boolean footPhase;
        @Nullable final Runnable onDismount;
        @Nullable final RoutePlanner.Result mountReference;

        PendingPlan(RoutePlanner.SearchState search, Vec3 target, Mob rider,
                    @Nullable Mob mount, double extraHeight, double planRadius,
                    @Nullable List<BlockPos> favored,
                    boolean allowPartial,
                    boolean footPhase, @Nullable Runnable onDismount,
                    @Nullable RoutePlanner.Result mountReference) {
            this.search = search;
            this.target = target;
            this.rider = rider;
            this.mount = mount;
            this.extraHeight = extraHeight;
            this.planRadius = planRadius;
            this.favored = favored;
            this.allowPartial = allowPartial;
            this.footPhase = footPhase;
            this.onDismount = onDismount;
            this.mountReference = mountReference;
        }
    }

    /** 骑乘前缀走完后要接着执行的步行路线。 */
    private static final class NextLeg {
        final List<BlockPos> points;
        final Vec3 target;
        final Mob rider;
        final boolean reachesGoal;
        final boolean allowPartial;
        @Nullable final Runnable onDismount;

        NextLeg(List<BlockPos> points, Vec3 target, Mob rider, boolean reachesGoal,
                boolean allowPartial, @Nullable Runnable onDismount) {
            this.points = points;
            this.target = target;
            this.rider = rider;
            this.reachesGoal = reachesGoal;
            this.allowPartial = allowPartial;
            this.onDismount = onDismount;
        }
    }

    /**
     * 上一次 {@link #start} 返回 false 是否只是"被推迟"（本 tick 预算用满 / 还在冷却 /
     * 大预算还没轮到），而不是"真的没有路线"。
     *   调用方据此区分"稍后重试"和"无路"，避免把正在排队规划的单位当无路处理（例如传送）。
     */
    public boolean isStartDeferred() {
        return lastStartDeferred;
    }

    public boolean isFollowing() {
        return path != null;
    }

    /**
     * 当前正在步行跟一条路线时，剩余的每个节点是否都能容纳“马 + 骑手”。
     *   单位走出窄道后可在开阔段重新上马，不必等整条命令结束。
     */
    public boolean canMountRemaining(Mob rider) {
        if (path == null || trackedMover != rider || rider.isPassenger()) return false;
        if (!AiNavUtil.mountFitsAt(rider.level(), rider,
                rider.getX(), rider.getY(), rider.getZ())) {
            return false;
        }

        int start = Math.max(0, path.getNextNodeIndex());
        if (start >= path.getNodeCount()) return false;
        BlockPos previous = null;
        for (int i = start; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (rider.level().getBlockState(pos)
                    .is(BlockTags.CLIMBABLE)
                    || rider.level().getBlockState(pos.above())
                            .is(BlockTags.CLIMBABLE)) {
                return false;
            }
            if (!AiNavUtil.mountStandFits(rider.level(), rider,
                    pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)) {
                return false;
            }
            if (previous != null) {
                int dx = pos.getX() - previous.getX();
                int dz = pos.getZ() - previous.getZ();
                if (dx != 0 && dz != 0) {
                    BlockPos sideX = new BlockPos(pos.getX(), pos.getY(), previous.getZ());
                    BlockPos sideZ = new BlockPos(previous.getX(), pos.getY(), pos.getZ());
                    if (!AiNavUtil.mountStandFits(rider.level(), rider,
                            sideX.getX() + 0.5, sideX.getY(), sideX.getZ() + 0.5)
                            || !AiNavUtil.mountStandFits(rider.level(), rider,
                            sideZ.getX() + 0.5, sideZ.getY(), sideZ.getZ() + 0.5)) {
                        return false;
                    }
                }
            }
            previous = pos;
        }
        return true;
    }

    /** 重新骑上马并让坐骑继续跟随当前路线。 */
    public boolean rebindMounted(Mob rider) {
        if (path == null) return false;
        if (!(rider.getVehicle() instanceof Mob mount)) return false;
        PathNavigation riderNav = rider.getNavigation();
        if (riderNav.getPath() == path) riderNav.stop();
        trackedMover = mount;
        blockedTicks = 0;
        progressWindowTicks = 0;
        progressLastX = Double.NaN;
        progressLastZ = Double.NaN;
        progressLastNode = -1;
        lastLadderY = Double.NaN;
        climbStallTicks = 0;
        PathNavigation mountNav = mount.getNavigation();
        mountNav.stop();
        if (!mountNav.moveTo(path, 1.0D)) {
            release();
            return false;
        }
        routeWorldVersion = RoutePlanner.worldVersion(mount.level());
        return true;
    }

    /** 是否正在跨 tick 计算路线（尚未安装路径）。 */
    public boolean isPlanning() {
        return pendingPlan != null;
    }

    /** 当前跟的路线是不是就通向这个点（用于"中途改命令"时判断要不要重新规划）。 */
    public boolean isFollowingTowards(Vec3 target) {
        return path != null && goal != null && target != null
                && goal.distanceToSqr(target) < 9.0;
    }

    /** 当前正在走的路径点（脱困传送用；没有返回 null）。 */
    @Nullable
    public Vec3 currentWaypoint() {
        Mob mover = trackedMover;
        if (mover == null) return null;
        PathNavigation nav = mover.getNavigation();
        Path active = nav.getPath();
        if (active == null || active.isDone()) return null;
        BlockPos pos = active.getNextNodePos();
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public void clear() {
        path = null;
        goal = null;
        trackedMover = null;
        expireTick = 0;
        blockedTicks = 0;
        routeReachesGoal = false;
        routeWorldVersion = Long.MIN_VALUE;
        pendingPlan = null;
        nextLeg = null;
        lastPartialEnd = null;
        lastPartialTarget = null;
        partialRepeatCount = 0;
        // 路线清掉（走完一段 / 中途改命令 / 上马下马）后立刻允许重新规划：
        //   否则要等 planBackoffTicks（失败退避时最多 200 tick）才重新算路，
        //   这段时间单位会沿旧半路路径移动 / 停在半路不动。
        planBackoffTicks = 0;
        hardLevel = 0;
        lastPlanTick = -10000;
    }

    /**
     * 丢下当前路线，但保留规划节流状态（冷却 / 退避 / 困难等级）。
     *   用于"改目标 / 回巢"这类中途放弃：与 {@link #clear()} 的区别是不会把节流清零，
     *   否则每次重新下令都会立刻重算一次大范围 A*，多个单位一起就是掉刻。
     */
    public void release() {
        path = null;
        goal = null;
        trackedMover = null;
        expireTick = 0;
        blockedTicks = 0;
        routeReachesGoal = false;
        routeWorldVersion = Long.MIN_VALUE;
        pendingPlan = null;
        nextLeg = null;
    }

    /** 规划失败 / 半路路线没进展：升级时间预算，用完最高档后再指数退避。 */
    private void escalate() {
        if (hardLevel < MAX_HARD_LEVEL) {
            hardLevel++;
            planBackoffTicks = 20 * hardLevel;
        } else {
            planBackoffTicks = Math.min(planBackoffTicks * 2, MAX_PLAN_BACKOFF_TICKS);
        }
    }

    /** 路线终点离目标多远（比较骑马 / 步行两条半路路线谁更好）。 */
    private static double endDistance(RoutePlanner.Result route, Vec3 target) {
        if (route == null || route.points().isEmpty()) return Double.MAX_VALUE;
        BlockPos end = route.points().get(route.points().size() - 1);
        return Math.sqrt(end.distToCenterSqr(target.x, target.y, target.z));
    }

    /** 返回第一个“马 + 骑手”无法通过的节点下标；-1 表示整条路线都能骑。 */
    private static int firstMountBlockedIndex(Mob mount, double extraHeight,
                                              RoutePlanner.Result planned) {
        if (!planned.reachedGoal()) return 0;
        BlockPos previous = null;
        List<BlockPos> points = planned.points();
        for (int i = 0; i < points.size(); i++) {
            BlockPos node = points.get(i);
            if (mount.level().getBlockState(node)
                    .is(BlockTags.CLIMBABLE)
                    || mount.level().getBlockState(node.above())
                            .is(BlockTags.CLIMBABLE)) {
                return i;
            }
            if (!RoutePlanner.walkableFor(mount.level(), mount, extraHeight, node)) {
                return i;
            }
            if (previous != null) {
                int dx = node.getX() - previous.getX();
                int dz = node.getZ() - previous.getZ();
                if (dx != 0 && dz != 0) {
                    BlockPos sideX = new BlockPos(node.getX(), node.getY(), previous.getZ());
                    BlockPos sideZ = new BlockPos(previous.getX(), node.getY(), node.getZ());
                    if (!RoutePlanner.walkableFor(mount.level(), mount, extraHeight, sideX)
                            || !RoutePlanner.walkableFor(mount.level(), mount, extraHeight, sideZ)) {
                        return i;
                    }
                }
            }
            previous = node;
        }
        return -1;
    }

    @Nullable
    private static RoutePlanner.Result rebaseResult(Level level, LivingEntity entity,
                                                    double extraHeight,
                                                    RoutePlanner.Result planned) {
        RoutePlanner.Result attached = RoutePlanner.attachToResult(
                level, entity, extraHeight, planned);
        if (attached != null) return attached;
        BlockPos start = planned.points().get(0);
        Vec3 startCenter = new Vec3(start.getX() + 0.5, start.getY(), start.getZ() + 0.5);
        return entity.position().distanceToSqr(startCenter) <= 16.0 ? planned : null;
    }

    /**
     * 玩家骑兵的默认命令先按步兵净空规划，再验证马能否走完整条路线。
     *   迷宫入口 / 低矮通道里，马用宽净空搜索会先把附近区域搜完仍只能得到半路路线；
     *   步兵路线更快，且一旦发现某格马过不去就可当场下马，不用等马先走到墙前。
     */
    private boolean installFootFirstRoute(RoutePlanner.Result planned, Vec3 target,
                                          Mob rider, Mob mount, double extraHeight,
                                          int nowTick, boolean allowPartial,
                                          @Nullable Runnable onDismount) {
        if (planned == null || planned.points().size() < 2) {
            escalate();
            return false;
        }
        RoutePlanner.Result rebased = rebaseResult(rider.level(), rider, 0.0, planned);
        if (rebased == null) {
            escalate();
            return false;
        }
        planned = rebased;
        int blockedIndex = onDismount == null ? -1
                : firstMountBlockedIndex(mount, extraHeight, planned);
        if (blockedIndex > 1) {
            List<BlockPos> points = planned.points();
            List<BlockPos> mountedPart = new ArrayList<>(points.subList(0, blockedIndex));
            List<BlockPos> footPart = new ArrayList<>(points.subList(
                    blockedIndex - 1, points.size()));
            if (installPath(mountedPart, target, mount, false, nowTick)) {
                nextLeg = new NextLeg(footPart, target, rider, planned.reachedGoal(),
                        allowPartial, onDismount);
                if (GeneralConfig.debugMovementLog) {
                    Monster_conquest.LOGGER.info(
                            "[ROUTE-MOUNT] rider={} plan=SPLIT mountNodes={} footNodes={} target={}",
                            rider.getUUID().toString().substring(0, 8),
                            mountedPart.size(), footPart.size(),
                            BlockPos.containing(target).toShortString());
                }
                return true;
            }
            return false;
        }
        boolean mounted = blockedIndex < 0;
        if (!mounted && onDismount != null) onDismount.run();
        if (GeneralConfig.debugMovementLog) {
            Monster_conquest.LOGGER.info(
                    "[ROUTE-MOUNT] rider={} plan={} nodes={} target={}",
                    rider.getUUID().toString().substring(0, 8),
                    mounted ? "MOUNT" : "FOOT", planned.points().size(),
                    BlockPos.containing(target).toShortString());
        }
        return installPlannedRoute(planned, target, rider,
                mounted ? mount : rider, mounted ? extraHeight : 0.0,
                nowTick, allowPartial);
    }

    /**
     * 规划一条到指定点的绕路路线，并把逐格节点交给原版寻路跟随。
     *
     * @param rider            骑手（步兵就是单位自己）
     * @param mount            坐骑（没有传 null）；骑乘时先用"马 + 骑手"的净空规划
     * @param mountExtraHeight 骑手在马背上的额外高度
     * @param onDismount       决定下马步行时执行的动作（切换步兵 / 清坐骑等）；
     *                         {@code null} = 不允许地形下马（例如玩家强制骑兵），
     *                         骑马方案走不通时直接放弃这条路线
     * @return 是否已开始按绕路路线前进
     */
    public boolean start(Mob rider, @Nullable Mob mount, Vec3 target, int nowTick,
                         double mountExtraHeight, @Nullable List<BlockPos> favored,
                         @Nullable Runnable onDismount, boolean allowPartial) {
        if (target == null || rider.level().isClientSide) return false;
        if (pendingPlan != null) {
            if (pendingPlan.rider == rider
                    && pendingPlan.target.distanceToSqr(target) < 9.0) {
                lastStartDeferred = true;
                return false;
            }
            pendingPlan = null;
        }
        if (isFollowing() && goal != null
                && goal.distanceToSqr(target) < 9.0 && nowTick <= expireTick) {
            lastStartDeferred = false;
            return true;
        }
        if (nowTick - lastPlanTick < planBackoffTicks) {
            if (GeneralConfig.debugMovementLog) {
                Monster_conquest.LOGGER.info(
                        "[ROUTE-SKIP] t={} cooldown={} backoff={} hardLevel={} mover=({},{},{})",
                        nowTick, nowTick - lastPlanTick, planBackoffTicks, hardLevel,
                        fmt(rider.getX()), fmt(rider.getY()), fmt(rider.getZ()));
            }
            lastStartDeferred = true;
            return false;
        }
        lastPlanTick = nowTick;
        lastStartDeferred = false;
        lastLadderY = Double.NaN;
        climbStallTicks = 0;

        // 防呆：骑乘中的单位一律按坐骑净空规划。
        //   按骑手（0.6 宽）算出来的贴墙节点交给宽坐骑跟随时，原版宽的转向点
        //   （节点中心 = 节点 + (int)(宽度+1)/2 格）会正好落进墙里 —— 顶着墙角抽搐。
        if (mount == null && rider.getVehicle() instanceof Mob vehicle) {
            mount = vehicle;
            if (mountExtraHeight <= 0.0) mountExtraHeight = WarHorseEntity.RIDER_HEIGHT;
        }
        Mob mover = mount != null ? mount : rider;
        double extraHeight = mount != null ? mountExtraHeight : 0.0;
        double dist = Math.sqrt(mover.position().distanceToSqr(target));
        double planRadius = Math.min(MAX_PLAN_RADIUS, dist * 1.5 + 48.0);
        if (mount != null && onDismount != null) {
            RoutePlanner.SearchState footFirst = RoutePlanner.beginSearch(
                    rider.level(), rider, target, PLAN_GOAL_RADIUS, planRadius,
                    DEFAULT_MAX_NODES, 0.0, favored, false);
            if (footFirst == null) {
                escalate();
                return false;
            }
            if (!RoutePlanner.advanceSearch(footFirst, rider.level(), rider)) {
                pendingPlan = new PendingPlan(footFirst, target, rider, mount,
                        extraHeight, planRadius, favored, allowPartial,
                        true, onDismount, null);
                lastStartDeferred = true;
                return false;
            }
            return installFootFirstRoute(footFirst.result(), target, rider, mount,
                    extraHeight, nowTick, allowPartial, onDismount);
        }
        // 不平滑：一格一格的节点最适合一格宽迷宫（平滑后的长直线会穿墙 / 擦墙）
        RoutePlanner.SearchState search = RoutePlanner.beginSearch(
                mover.level(), mover, target, PLAN_GOAL_RADIUS, planRadius,
                DEFAULT_MAX_NODES, extraHeight, favored, false);
        if (search == null) {
            escalate();
            return false;
        }
        if (!RoutePlanner.advanceSearch(search, mover.level(), mover)) {
            pendingPlan = new PendingPlan(search, target, rider, mount, extraHeight,
                    planRadius, favored, allowPartial, false, onDismount, null);
            lastStartDeferred = true;
            return false;
        }
        RoutePlanner.Result planned = search.result();
        if (planned == null || planned.points().size() < 2) {
            escalate();
            return false;
        }

        // 骑马方案：只要还能明显往目标推进就先骑着走（例如开阔地 → 迷宫口），
        // 只有骑马这一步完全没进展（到了窄道 / 迷宫外就进不去）才改用步行方案下马。
        if (mount != null) {
            boolean mountUsable = planned != null && planned.points().size() >= 2;
            boolean mountMakesProgress = mountUsable
                    && (planned.reachedGoal()
                        || endDistance(planned, target)
                                < mount.position().distanceTo(target) - 4.0);
            // 不允许下马（例如玩家强制骑兵）时不做步行回退：把骑手宽的路线塞给坐骑
            //   就是"顶着墙角推"的来源，宁可这条路线走不通。
            if (!mountMakesProgress && onDismount != null) {
                RoutePlanner.SearchState footSearch = RoutePlanner.beginSearch(
                        rider.level(), rider, target, PLAN_GOAL_RADIUS, planRadius,
                        DEFAULT_MAX_NODES, 0.0, favored, false);
                if (footSearch == null) {
                    escalate();
                    return false;
                }
                if (!RoutePlanner.advanceSearch(footSearch, rider.level(), rider)) {
                    pendingPlan = new PendingPlan(footSearch, target, rider, null, 0.0,
                            planRadius, favored, allowPartial, true, onDismount, planned);
                    lastStartDeferred = true;
                    return false;
                }
                RoutePlanner.Result onFoot = footSearch.result();
                if (onFoot != null && onFoot.points().size() >= 2
                        && (onFoot.reachedGoal()
                        || endDistance(onFoot, target)
                                < endDistance(planned, target) - 1.0)) {
                    onDismount.run();
                    planned = onFoot;
                    mover = rider;
                    extraHeight = 0.0;
                }
            }
        }
        return installPlannedRoute(planned, target, rider, mover, extraHeight,
                nowTick, allowPartial);
    }

    /** 把已经完成的 A* 结果安装成原版 Path。 */
    private boolean installPlannedRoute(RoutePlanner.Result planned, Vec3 target,
                                        Mob rider, Mob mover, double extraHeight,
                                        int nowTick, boolean allowPartial) {
        lastStartDeferred = false;
        if (planned == null || planned.points().size() < 2) {
            escalate();
            return false;
        }
        RoutePlanner.Result rebased = rebaseResult(
                mover.level(), mover, extraHeight, planned);
        if (rebased == null) {
            escalate();
            return false;
        }
        planned = rebased;
        // 调试诊断（general-common.toml 的 debugMovementLog）：规划结果到底到没到、
        //   终点在哪、起点/目标格本身能不能站 —— 排查"围在迷宫外不动"时先看这一行。
        if (GeneralConfig.debugMovementLog) {
            BlockPos end = planned.points().get(planned.points().size() - 1);
            Monster_conquest.LOGGER.info(
                    "[ROUTE-DIAG] t={} reached={} outcome={} visited={} points={} end={} endDist={} moverDist={}"
                            + " startWalkable={} goalWalkable={}",
                    nowTick, planned.reachedGoal(), planned.outcome(), planned.visitedNodes(),
                    planned.points().size(),
                    end.toShortString(),
                    String.format(Locale.ROOT, "%.2f",
                            Math.sqrt(end.distToCenterSqr(target.x, target.y, target.z))),
                    String.format(Locale.ROOT, "%.2f",
                            Math.sqrt(mover.position().distanceToSqr(target))),
                    RoutePlanner.walkableFor(rider.level(), rider, extraHeight, mover.blockPosition()),
                    RoutePlanner.walkableFor(rider.level(), rider, extraHeight,
                            BlockPos.containing(target.x, target.y, target.z)));
        }

        // 半路路线（没真正到达目标）：终点必须比当前位置明显更接近目标才跟，
        // 否则就会出现"走过去 → 重新规划又走回来"的来回走。
        if (!planned.reachedGoal()) {
            long worldVersion = RoutePlanner.worldVersion(mover.level());
            if (worldVersion != lastPartialWorldVersion) {
                lastPartialWorldVersion = worldVersion;
                lastPartialTarget = null;
                lastPartialEnd = null;
                partialRepeatCount = 0;
            }
            BlockPos end = planned.points().get(planned.points().size() - 1);
            Vec3 partialEnd = new Vec3(end.getX() + 0.5, end.getY(), end.getZ() + 0.5);
            double endDistSqr = end.distToCenterSqr(target.x, target.y, target.z);
            // 只要求"终点比现在更接近目标"（0.5 格）即可接受：
            //   复杂迷宫里一段走廊可能不会明显缩短直线距离，门槛太高会直接把
            //   有用的半路路线判死 → 单位停在半路。
            boolean usefulPartial = allowPartial
                    && mover == rider
                    && planned.points().size() >= 4
                    && !end.equals(mover.blockPosition());
            boolean sameTarget = lastPartialTarget != null
                    && lastPartialTarget.distanceToSqr(target) < 9.0;
            boolean sameEnd = lastPartialEnd != null
                    && lastPartialEnd.distanceToSqr(partialEnd) < 1.0;
            boolean alreadyAtEnd = lastPartialEnd != null
                    && mover.position().distanceToSqr(lastPartialEnd) < 4.0;
            if (usefulPartial && sameTarget && sameEnd && alreadyAtEnd) {
                partialRepeatCount++;
                // 同一条部分路线已经走到终点仍没有靠近目标：继续重装只会重复同一段路。
                planBackoffTicks = Math.max(planBackoffTicks,
                        Math.min(MAX_PLAN_BACKOFF_TICKS, 40 * partialRepeatCount));
                escalate();
                return false;
            }
            if (endDistSqr > mover.position().distanceToSqr(target) - 1.0
                    && !usefulPartial) {
                if (GeneralConfig.debugMovementLog) {
                    Monster_conquest.LOGGER.info(
                            "[ROUTE-REJECT] t={} end={} endDistSq={} currentDistSq={} points={} mover=({},{},{})",
                            nowTick, end.toShortString(), fmt(Math.sqrt(endDistSqr)),
                            fmt(Math.sqrt(mover.position().distanceToSqr(target))),
                            planned.points().size(), fmt(mover.getX()), fmt(mover.getY()),
                            fmt(mover.getZ()));
                }
                escalate();
                return false;
            }
            if (!sameTarget || !sameEnd) partialRepeatCount = 0;
            lastPartialTarget = target;
            lastPartialEnd = partialEnd;
            if (usefulPartial
                    && GeneralConfig.debugMovementLog) {
                Monster_conquest.LOGGER.info(
                        "[ROUTE-PARTIAL] t={} end={} points={} mover=({},{},{})",
                        nowTick, end.toShortString(), planned.points().size(),
                        fmt(mover.getX()), fmt(mover.getY()), fmt(mover.getZ()));
            }
            hardLevel = Math.min(MAX_HARD_LEVEL, hardLevel + 1);
            planBackoffTicks = Math.max(REPLAN_COOLDOWN_TICKS, 20);
        } else {
            hardLevel = 0;
            planBackoffTicks = REPLAN_COOLDOWN_TICKS;
            lastPartialTarget = null;
            lastPartialEnd = null;
            partialRepeatCount = 0;
        }

        return installPath(planned.points(), target, mover,
                planned.reachedGoal(), nowTick);
    }

    /** 安装一段通过碰撞检查的逐格路线；nextLeg 由调用方在成功后补上。 */
    private boolean installPath(List<BlockPos> points, Vec3 target, Mob mover,
                                boolean reachesGoal, int nowTick) {
        if (points.size() < 2) {
            escalate();
            return false;
        }
        List<Node> nodes = new ArrayList<>(points.size());
        for (BlockPos p : points) {
            Node node = new Node(p.getX(), p.getY(), p.getZ());
            node.type = PathType.WALKABLE;
            nodes.add(node);
        }
        Path route = new Path(nodes, BlockPos.containing(target), reachesGoal);

        PathNavigation nav = mover.getNavigation();
        // 旧路径可能已经走完但仍留在导航里：新路线节点完全相同时 Path.sameAs 会认为
        //   是"同一条路"，moveTo 直接返回 false，单位就会原地站着不动。先清掉旧路径。
        Path previous = nav.getPath();
        boolean previousDone = previous != null && previous.isDone();
        boolean sameAsPrevious = previous != null && route.sameAs(previous);
        nav.stop();
        boolean accepted = nav.moveTo(route, 1.0D);
        if (GeneralConfig.debugMovementLog) {
            LivingEntity routeRider = mover.getFirstPassenger() instanceof LivingEntity passenger
                    ? passenger : mover;
            Monster_conquest.LOGGER.info(
                    "[ROUTE-INSTALL] t={} accepted={} previousDone={} sameAsPrevious={}"
                            + " nodes={} navDone={} rider={} mover=({},{},{})",
                    nowTick, accepted, previousDone, sameAsPrevious, nodes.size(),
                    nav.isDone(), routeRider.getUUID().toString().substring(0, 8),
                    fmt(mover.getX()), fmt(mover.getY()), fmt(mover.getZ()));
        }
        if (!accepted) {
            escalate();
            return false;
        }

        path = route;
        goal = target;
        trackedMover = mover;
        pendingPlan = null;
        nextLeg = null;
        lastStartDeferred = false;
        expireTick = nowTick + Math.max(MIN_TTL_TICKS, nodes.size() * 12);
        blockedTicks = 0;
        routeReachesGoal = reachesGoal;
        routeWorldVersion = RoutePlanner.worldVersion(mover.level());
        return true;
    }

    private static String fmt(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * 路线没有到达目标就结束：保留失败记忆和退避，避免下一次立刻重装同一条路线。
     */
    private void abandonUnfinishedRoute(String reason, int nowTick) {
        boolean plannedReach = routeReachesGoal;
        if (path != null && path.getNodeCount() > 0) {
            Node end = path.getNode(path.getNodeCount() - 1);
            lastPartialEnd = new Vec3(end.x + 0.5, end.y, end.z + 0.5);
        }
        escalate();
        release();
        if (GeneralConfig.debugMovementLog) {
            Monster_conquest.LOGGER.info(
                    "[ROUTE-END] t={} reason={} plannedReach={} backoff={} hardLevel={}",
                    nowTick, reason, plannedReach, planBackoffTicks, hardLevel);
        }
    }

    /**
     * 跟随路线（原版导航在跑，这里只做"还该不该继续"的判定）。
     *
     * @param combatBusy 出现战斗目标 → 放弃绕路，交回战斗 AI
     * @return true = 本 tick 仍在按绕路路线前进
     */
    public boolean tick(Mob mover, int nowTick, boolean combatBusy) {
        if (pendingPlan != null) return tickPendingPlan(nowTick, combatBusy);
        if (path == null || goal == null) return false;
        if (routeWorldVersion != RoutePlanner.worldVersion(mover.level())) {
            mover.getNavigation().stop();
            abandonUnfinishedRoute("world_changed", nowTick);
            return false;
        }
        // 到达判定用水平距离 + 高度容差（快速单位在目标上下 1~2 格时三维距离会永远差一点）
        double dxz = mover.getX() - goal.x;
        double dzz = mover.getZ() - goal.z;
        boolean arrived = dxz * dxz + dzz * dzz < GOAL_REACHED_SQR
                && Math.abs(mover.getY() - goal.y) < 2.5;
        if (arrived && nextLeg != null) {
            mover.getNavigation().stop();
            return installNextLeg(nowTick);
        }
        if (arrived || combatBusy || nowTick > expireTick) {
            // 停的是"真正在走的实体"的导航：骑乘时必须停坐骑的导航，
            // 否则马会继续沿着旧路径跑（表现为围着目标点来回跑）。
            PathNavigation moverNav = mover.getNavigation();
            if (moverNav.getPath() == path) moverNav.stop();
            if (arrived) {
                clear();
            } else if (combatBusy) {
                release();
            } else {
                abandonUnfinishedRoute("expired", nowTick);
            }
            return false;
        }

        PathNavigation nav = mover.getNavigation();
        if (nav.getPath() != path || nav.isDone()) {
            boolean ownedPath = nav.getPath() == path;
            if (ownedPath) nav.stop();
            if (ownedPath) {
                if (nextLeg != null) {
                    return installNextLeg(nowTick);
                }
                abandonUnfinishedRoute("path_done", nowTick);
            } else {
                release();
            }
            return false;
        }

        // 原版导航自己也会判定卡住；这里再加一道：长时间顶着方块就放弃，重新规划。
        //   爬梯子 / 藤蔓 / 脚手架时贴着方块是正常状态 —— 但只有"确实在上下移动（或刚停一下）"
        //   才算。否则"被错误路线带到梯子上顶着墙"会被永远豁免，单位就死在梯子 / 墙角，
        //   既不会重新规划也不会脱困（表现为个别士兵卡在墙角的梯子边不动）。
        double nowY = mover.getY();
        boolean climbingProgress = false;
        if (AiNavUtil.isOnClimbable(mover)) {
            if (Double.isNaN(lastLadderY) || Math.abs(nowY - lastLadderY) > 0.005) {
                climbStallTicks = 0;
            } else {
                climbStallTicks++;
            }
            climbingProgress = climbStallTicks < CLIMB_GRACE_TICKS;
        } else {
            climbStallTicks = 0;
        }
        lastLadderY = nowY;
        if (mover.horizontalCollision && !climbingProgress) {
            // 一格宽走廊里贴着墙走是常态：只看"碰到方块"会把正常绕路每隔几十 tick 打断一次
            //   （深层迷宫表现为走几步停一段、很久才挪进去）。按取样窗口看实际位移和
            //   路径节点是否推进，只有真的没往前走才累计"卡住"。
            progressWindowTicks++;
            if (progressWindowTicks >= PROGRESS_SAMPLE_TICKS) {
                PathNavigation active = mover.getNavigation();
                Path activePath = active.getPath();
                int node = activePath == null ? -1 : activePath.getNextNodeIndex();
                boolean advanced = node > progressLastNode;
                double travelled = Double.isNaN(progressLastX) ? 0.0
                        : Math.sqrt(mover.distanceToSqr(progressLastX, mover.getY(), progressLastZ));
                if (advanced || travelled >= PROGRESS_MIN_TRAVEL) {
                    blockedTicks = 0;
                } else {
                    blockedTicks += progressWindowTicks;
                }
                progressLastX = mover.getX();
                progressLastZ = mover.getZ();
                progressLastNode = node;
                progressWindowTicks = 0;
            }
            if (blockedTicks > BLOCKED_LIMIT_TICKS) {
                nav.stop();
                abandonUnfinishedRoute("blocked", nowTick);
                return false;
            }
        } else {
            blockedTicks = 0;
            progressWindowTicks = 0;
            progressLastX = Double.NaN;
            progressLastZ = Double.NaN;
            progressLastNode = -1;
        }
        return true;
    }

    /** 骑乘前缀到达窄道入口后，先下马再沿剩余步行路线继续。 */
    private boolean installNextLeg(int nowTick) {
        NextLeg leg = nextLeg;
        nextLeg = null;
        if (leg == null) return false;
        if (leg.rider.isRemoved() || !leg.rider.isAlive()) {
            release();
            return false;
        }
        if (leg.onDismount != null) leg.onDismount.run();
        return installPath(leg.points, leg.target, leg.rider, leg.reachesGoal, nowTick);
    }

    /**
     * 推进跨 tick 规划。规划完成前保持"稍后重试"状态，调用方不会误判为无路。
     */
    private boolean tickPendingPlan(int nowTick, boolean combatBusy) {
        PendingPlan pending = pendingPlan;
        if (pending == null) return false;
        if (combatBusy || pending.rider.isRemoved() || !pending.rider.isAlive()
                || (pending.mount != null && pending.rider.getVehicle() != pending.mount)) {
            pendingPlan = null;
            lastStartDeferred = false;
            return false;
        }

        Mob mover = pending.footPhase ? pending.rider
                : (pending.mount != null ? pending.mount : pending.rider);
        if (!RoutePlanner.advanceSearch(pending.search, mover.level(), mover)) {
            lastStartDeferred = true;
            return false;
        }

        RoutePlanner.Result planned = pending.search.result();
        if (planned == null || planned.points().size() < 2) {
            pendingPlan = null;
            lastStartDeferred = false;
            escalate();
            return false;
        }

        if (pending.footPhase) {
            if (pending.mount != null) {
                pendingPlan = null;
                return installFootFirstRoute(planned, pending.target, pending.rider,
                        pending.mount, pending.extraHeight, nowTick,
                        pending.allowPartial, pending.onDismount);
            }
            boolean better = pending.mountReference == null
                    || planned.reachedGoal()
                    || endDistance(planned, pending.target)
                            < endDistance(pending.mountReference, pending.target) - 1.0;
            pendingPlan = null;
            if (!better) {
                lastStartDeferred = false;
                escalate();
                return false;
            }
            if (pending.onDismount != null) pending.onDismount.run();
            return installPlannedRoute(planned, pending.target, pending.rider,
                    pending.rider, 0.0, nowTick, pending.allowPartial);
        }

        if (pending.mount != null) {
            boolean mountedProgress = planned.reachedGoal()
                    || endDistance(planned, pending.target)
                            < pending.mount.position().distanceTo(pending.target) - 4.0;
            if (!mountedProgress && pending.onDismount != null) {
                RoutePlanner.SearchState footSearch = RoutePlanner.beginSearch(
                        pending.rider.level(), pending.rider, pending.target,
                        PLAN_GOAL_RADIUS, pending.planRadius, DEFAULT_MAX_NODES,
                        0.0, pending.favored, false);
                if (footSearch != null) {
                    if (!RoutePlanner.advanceSearch(footSearch,
                            pending.rider.level(), pending.rider)) {
                        pendingPlan = new PendingPlan(footSearch, pending.target,
                                pending.rider, null, 0.0, pending.planRadius, pending.favored,
                                pending.allowPartial, true, pending.onDismount, planned);
                        lastStartDeferred = true;
                        return false;
                    }
                    RoutePlanner.Result onFoot = footSearch.result();
                    if (onFoot != null && onFoot.points().size() >= 2
                            && (onFoot.reachedGoal()
                            || endDistance(onFoot, pending.target)
                                    < endDistance(planned, pending.target) - 1.0)) {
                        pending.onDismount.run();
                        pendingPlan = null;
                        return installPlannedRoute(onFoot, pending.target, pending.rider,
                                pending.rider, 0.0, nowTick, pending.allowPartial);
                    }
                }
            }
        }

        pendingPlan = null;
        return installPlannedRoute(planned, pending.target, pending.rider,
                mover, pending.extraHeight, nowTick, pending.allowPartial);
    }
}
