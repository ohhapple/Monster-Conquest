package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 指定点（手动行军 / 手动移动）的"方块级 A* 绕路"路线，士兵与首领共用。
 *
 *   原版寻路遇到迷宫 / 长绕路时只会返回"最接近目标的半路路径"（甚至没有路），
 *   单位就会顶着墙原地打转。这里用 {@link RoutePlanner} 规划真正的绕路，然后
 *   把逐格节点包成原版 {@link Path} 交给原版寻路跟随 —— 转身、切角、速度都用原版那一套。
 *
 *   · 骑乘时先用"马 + 骑手"净空规划；只要骑马还能明显接近目标就先骑着走，
 *     只有骑马再也推进不了（窄道 / 迷宫口）才下马改用步行方案；
 *   · 每单位最短 10 tick 规划一次；失败 / 半路无进展时逐级加大时间预算，
 *     到最高档仍不行才指数退避；
 *   · 全局节流：规划时间预算由 {@link RoutePlanner} 统一记账（所有调用点共用）；
 *     大预算（≥20ms）的硬算还要错开至少 {@link #HARD_PLAN_MIN_INTERVAL_TICKS} tick；
 *     本 tick 轮不到的单位按"稍后重试"处理（不是"没有路"）；
 *   · 战斗目标出现 / 到达 / 超时 / 长时间顶着方块 → 放弃路线交回上层逻辑。
 */
public final class MarchRouteFollower {

    /** 目的点到达判定（格²，水平距离）。留 1.5 格，最后一段交给原版寻路走到位。 */
    private static final double GOAL_REACHED_SQR = 1.5 * 1.5;
    /** 一直顶着方块多久算"这条路线走不通"（tick）。 */
    private static final int BLOCKED_LIMIT_TICKS = 60;
    /** 同一单位两次规划之间的最短间隔（tick）。 */
    private static final int REPLAN_COOLDOWN_TICKS = 10;
    /** 规划失败（没有路 / 半路路线也没进展）时的退避上限（tick）。 */
    private static final int MAX_PLAN_BACKOFF_TICKS = 200;
    /** 路线最短有效期（tick）；长路线按节点数自动加长，避免走到一半过期停下。 */
    private static final int MIN_TTL_TICKS = 1200;
    /** 玩家 / 首领下达的指定点可以比战斗绕路搜得更远一些。 */
    private static final int DEFAULT_MAX_NODES = 12000;
    /**
     * 指定点绕路的时间预算（逐级升级）。
     *   长绕路（绕大墙 / 迷宫）必须给足，否则只能算出"半路路线"，单位会停在墙前；
     *   第一次用普通预算，只得到半路路线 / 没路时下一次自动升级预算重算。
     */
    private static final long[] PLAN_TIME_BUDGETS = {
            12_000_000L, 25_000_000L, 40_000_000L
    };
    /** 复杂迷宫绕行可能远超直线距离：搜索半径上限给足。 */
    private static final double MAX_PLAN_RADIUS = 512.0;

    /** 大预算（≥20ms）规划：相邻两次之间至少隔这么多 tick，避免连续掉刻。 */
    private static final long HARD_PLAN_BUDGET_NANOS = 20_000_000L;
    private static final int HARD_PLAN_MIN_INTERVAL_TICKS = 20;

    /** 攀爬豁免：只有"确实在往上爬（或刚停一下）"才不算顶着方块。 */
    private static final int CLIMB_GRACE_TICKS = 40;
    private static int lastHardPlanTick = Integer.MIN_VALUE;

    @Nullable private Path path;
    @Nullable private Vec3 goal;
    @Nullable private Mob trackedMover;
    private int expireTick;
    /** 攀爬进度追踪（梯子卡住的判定用）：上一 tick 的高度 / 连续没升高多少 tick。 */
    private double lastLadderY = Double.NaN;
    private int notRisingTicks = 0;
    private int lastPlanTick = -10000;
    private int blockedTicks;
    /** 当前规划冷却（失败后指数退避；成功恢复到 REPLAN_COOLDOWN_TICKS）。 */
    private int planBackoffTicks = REPLAN_COOLDOWN_TICKS;
    /** 困难情况等级：越高，下一次规划用越大的时间预算。 */
    private int hardLevel = 0;
    /** 上一次 start() 返回 false 是否只是被全局节流 / 预算推迟（而不是真的规划不出路）。 */
    private boolean lastStartDeferred = false;

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
    }

    /** 规划失败 / 半路路线没进展：升级时间预算，用完最高档后再指数退避。 */
    private void escalate() {
        if (hardLevel < PLAN_TIME_BUDGETS.length - 1) {
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
                         @Nullable Runnable onDismount) {
        if (target == null || rider.level().isClientSide) return false;
        if (isFollowing() && goal != null
                && goal.distanceToSqr(target) < 9.0 && nowTick <= expireTick) {
            lastStartDeferred = false;
            return true;
        }
        if (nowTick - lastPlanTick < planBackoffTicks) {
            lastStartDeferred = true;
            return false;
        }
        long budget = PLAN_TIME_BUDGETS[Math.min(hardLevel, PLAN_TIME_BUDGETS.length - 1)];
        boolean heavyPlan = budget >= HARD_PLAN_BUDGET_NANOS;
        if (heavyPlan && nowTick - lastHardPlanTick < HARD_PLAN_MIN_INTERVAL_TICKS) {
            lastStartDeferred = true;
            return false;
        }
        lastPlanTick = nowTick;
        lastStartDeferred = false;
        lastLadderY = Double.NaN;
        notRisingTicks = 0;

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
        // 不平滑：一格一格的节点最适合一格宽迷宫（平滑后的长直线会穿墙 / 擦墙）
        RoutePlanner.Result planned = RoutePlanner.plan(mover.level(), mover, target,
                2.0, planRadius, DEFAULT_MAX_NODES, extraHeight, favored, false,
                budget);

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
                RoutePlanner.Result onFoot = RoutePlanner.plan(rider.level(), rider, target,
                        2.0, planRadius, DEFAULT_MAX_NODES, 0.0, favored, false, budget);
                if (onFoot != null && onFoot.points().size() >= 2
                        && (onFoot.reachedGoal()
                        || endDistance(onFoot, target)
                                < endDistance(planned, target) - 1.0)) {
                    onDismount.run();
                    planned = onFoot;
                    mover = rider;
                }
            }
        }
        if (heavyPlan) lastHardPlanTick = nowTick;

        if (planned == null || planned.points().size() < 2) {
            // 只是本 tick 的全局预算被占满（规划被推迟）→ 不升级退避，下一 tick 再试。
            if (RoutePlanner.wasLastCallDeferred()) {
                lastStartDeferred = true;
                return false;
            }
            escalate();
            return false;
        }

        // 半路路线（没真正到达目标）：终点必须比当前位置明显更接近目标才跟，
        // 否则就会出现"走过去 → 重新规划又走回来"的来回走。
        if (!planned.reachedGoal()) {
            BlockPos end = planned.points().get(planned.points().size() - 1);
            double endDistSqr = end.distToCenterSqr(target.x, target.y, target.z);
            // 只要求"终点比现在更接近目标"（0.5 格）即可接受：
            //   复杂迷宫里一段走廊可能不会明显缩短直线距离，门槛太高会直接把
            //   有用的半路路线判死 → 单位停在半路。
            if (endDistSqr > mover.position().distanceToSqr(target) - 1.0) {
                escalate();
                return false;
            }
        }
        hardLevel = 0;
        planBackoffTicks = REPLAN_COOLDOWN_TICKS;

        List<BlockPos> points = planned.points();
        List<Node> nodes = new ArrayList<>(points.size());
        for (BlockPos p : points) {
            Node node = new Node(p.getX(), p.getY(), p.getZ());
            node.type = PathType.WALKABLE;
            nodes.add(node);
        }
        Path route = new Path(nodes, BlockPos.containing(target), planned.reachedGoal());

        PathNavigation nav = mover.getNavigation();
        if (!nav.moveTo(route, 1.0D)) return false;

        path = route;
        goal = target;
        trackedMover = mover;
        expireTick = nowTick + Math.max(MIN_TTL_TICKS, nodes.size() * 12);
        blockedTicks = 0;
        return true;
    }

    /**
     * 跟随路线（原版导航在跑，这里只做"还该不该继续"的判定）。
     *
     * @param combatBusy 出现战斗目标 → 放弃绕路，交回战斗 AI
     * @return true = 本 tick 仍在按绕路路线前进
     */
    public boolean tick(Mob mover, int nowTick, boolean combatBusy) {
        if (path == null || goal == null) return false;
        // 到达判定用水平距离 + 高度容差（快速单位在目标上下 1~2 格时三维距离会永远差一点）
        double dxz = mover.getX() - goal.x;
        double dzz = mover.getZ() - goal.z;
        if (combatBusy || nowTick > expireTick
                || (dxz * dxz + dzz * dzz < GOAL_REACHED_SQR
                    && Math.abs(mover.getY() - goal.y) < 2.5)) {
            // 停的是"真正在走的实体"的导航：骑乘时必须停坐骑的导航，
            // 否则马会继续沿着旧路径跑（表现为围着目标点来回跑）。
            PathNavigation moverNav = mover.getNavigation();
            if (moverNav.getPath() == path) moverNav.stop();
            clear();
            return false;
        }

        PathNavigation nav = mover.getNavigation();
        if (nav.getPath() != path || nav.isDone()) {
            // 原版把路线走完 / 路线被别的目标顶掉 → 让上层重新规划
            clear();
            return false;
        }

        // 原版导航自己也会判定卡住；这里再加一道：长时间顶着方块就放弃，重新规划。
        //   爬梯子 / 藤蔓 / 脚手架时贴着方块是正常状态 —— 但只有"确实在往上爬（或刚停一下）"
        //   才算。否则"被错误路线带到梯子上顶着墙"会被永远豁免，单位就死在梯子 / 墙角，
        //   既不会重新规划也不会脱困（表现为个别士兵卡在墙角的梯子边不动）。
        double nowY = mover.getY();
        boolean climbingProgress = false;
        if (AiNavUtil.isOnClimbable(mover)) {
            if (Double.isNaN(lastLadderY) || nowY > lastLadderY + 0.005) {
                notRisingTicks = 0;
            } else {
                notRisingTicks++;
            }
            climbingProgress = notRisingTicks < CLIMB_GRACE_TICKS;
        } else {
            notRisingTicks = 0;
        }
        lastLadderY = nowY;
        if (mover.horizontalCollision && !climbingProgress) {
            if (++blockedTicks > BLOCKED_LIMIT_TICKS) {
                nav.stop();
                clear();
                return false;
            }
        } else {
            blockedTicks = 0;
        }
        return true;
    }
}
