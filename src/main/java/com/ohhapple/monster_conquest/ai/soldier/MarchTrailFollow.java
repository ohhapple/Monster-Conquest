package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 行军路线跟随：士兵沿首领走过的路点依次前进（绕墙 / 穿门走首领同一条路），
 *   首领行军时整队只算这一条路线；走到轨迹末端返回 false，交回调用方（阵型 / 跟随）。
 */
public final class MarchTrailFollow {

    /** 前看距离（格）：目标取轨迹上至少这么远的点，
     *   让一次寻路能走好几格，避免每 1~2 格就重算一次路径。 */
    private static final double LOOKAHEAD_MIN = 4.0;
    /** 路点到达判定（格²）。 */
    private static final double WAYPOINT_REACHED_SQR = 2.0 * 2.0;
    /** 目标点没变时的最短重发间隔（tick）。 */
    private static final int REISSUE_INTERVAL = 40;
    /** 目标点变化判定（格²）：漂移小于这个距离视为同一个点，不重算路径。 */
    private static final double TARGET_SAME_SQR = 2.0 * 2.0;
    /** 轨迹太短（小范围游走）时不跟轨迹，交给调用方原本的逻辑。 */
    private static final int MIN_TRAIL_POINTS = 4;
    /** 跟随时每这么久检查一次有没有实际移动。 */
    private static final int STALL_CHECK_TICKS = 40;
    /** 一个检查窗口内至少要走这么多格，否则算没跟上。 */
    private static final double STALL_MIN_MOVE = 0.5;
    /** 连续这么多窗口没移动 → 放弃轨迹，交回调用方原本的逻辑（保证不会原地卡死）。 */
    private static final int STALL_LIMIT = 2;
    /** 掉队超过这个距离 → 直接朝首领本人赶，把队伍收紧（老兵不会越落越远）。 */
    private static final double CATCH_UP_ENTER = 16.0;
    /** 追到这么近 → 回到沿轨迹跟随（带滞回，避免来回切目标）。 */
    private static final double CATCH_UP_EXIT = 10.0;
    /** 跟随失效后交给调用方原本逻辑的时间（tick），避免每 tick 来回抢移动权。 */
    private static final int BAIL_TICKS = 100;

    private Vec3 issuedTarget = null;
    private int reissueTick = 0;
    @Nullable private Vec3 lastPos = null;
    private int stallCheckTick = 0;
    private int stallCount = 0;
    private boolean catchingUp = false;
    private int bailUntilTick = 0;

    public void reset() {
        issuedTarget = null;
        reissueTick = 0;
        lastPos = null;
        stallCheckTick = 0;
        stallCount = 0;
        catchingUp = false;
    }

    /**
     * 沿首领的行军轨迹前进一步。
     *
     * @return true = 已按轨迹下达移动；false = 轨迹已走到末端 / 不可用
     */
    public boolean tick(AbstractArmySoldier soldier, AbstractArmyCommander commander, double speed) {
        // 上次跟丢了 → 先让调用方原本的逻辑跑一段时间
        if (soldier.tickCount < bailUntilTick) return false;
        // 正在按方块级 A* 绕路（迷宫 / 隔墙）前进 → 移动交给绕路跟随
        if (soldier.isFollowingMarchRoute()) return true;

        java.util.List<Vec3> trail = commander.getMarchTrail();
        int size = trail.size();
        if (size < MIN_TRAIL_POINTS) {
            reset();
            return false;
        }

        Vec3 pos = soldier.position();

        // 停滞看门狗：跟了一段却没有实际位移 → 放弃轨迹，交回调用方原本逻辑
        if (stallCheckTick == 0) {
            stallCheckTick = soldier.tickCount + STALL_CHECK_TICKS;
            lastPos = pos;
        } else if (soldier.tickCount >= stallCheckTick) {
            stallCheckTick = soldier.tickCount + STALL_CHECK_TICKS;
            double moved = lastPos == null ? STALL_MIN_MOVE : pos.distanceTo(lastPos);
            lastPos = pos;
            if (moved < STALL_MIN_MOVE) {
                if (++stallCount >= STALL_LIMIT) {
                    reset();
                    bailUntilTick = soldier.tickCount + BAIL_TICKS;
                    return false;
                }
            } else {
                stallCount = 0;
            }
        }

        // 掉队太远 → 直接朝首领本人赶（收紧队伍），追近后再回到沿轨迹跟随
        Vec3 leader = commander.position();
        double leaderDistSqr = leader.distanceToSqr(pos);
        if (!catchingUp && leaderDistSqr > CATCH_UP_ENTER * CATCH_UP_ENTER) catchingUp = true;
        else if (catchingUp && leaderDistSqr < CATCH_UP_EXIT * CATCH_UP_EXIT) catchingUp = false;

        Vec3 target;
        if (catchingUp) {
            target = clampToGround(soldier, leader);
            if (target == null) {
                reset();
                return false;
            }
        } else {
            // 先找离自己最近的轨迹点，再从那里往"更靠近首领"的方向前看：
            //   脱队的兵自动接上轨迹，已经走过终点的兵不会被拉回轨迹起点。
            int nearest = 0;
            double nearestSqr = Double.MAX_VALUE;
            for (int i = 0; i < size; i++) {
                double d = trail.get(i).distanceToSqr(pos);
                if (d < nearestSqr) {
                    nearestSqr = d;
                    nearest = i;
                }
            }

            // 目标 = 轨迹上第一个离自己超过 LOOKAHEAD_MIN 的点；
            //   如果轨迹太短（首领就在几步外），退到轨迹端点。
            target = null;
            for (int i = nearest; i < size; i++) {
                Vec3 p = trail.get(i);
                if (p.distanceToSqr(pos) > LOOKAHEAD_MIN * LOOKAHEAD_MIN) {
                    target = clampToGround(soldier, p);
                    break;
                }
            }

            if (target == null) {
                // 轨迹已走完：首领还在行军就直接跟住首领，否则交回调用方
                if (commander.isMarching() && leaderDistSqr > WAYPOINT_REACHED_SQR) {
                    target = clampToGround(soldier, leader);
                    if (target == null) return false;
                } else {
                    reset();
                    return false;
                }
            }
        }

        // 追赶首领时目标点在持续移动，放宽"同一个点"的判定，避免每几 tick 重算一次路径
        double sameSqr = catchingUp ? 16.0 : TARGET_SAME_SQR;
        boolean changed = issuedTarget == null || issuedTarget.distanceToSqr(target) > sameSqr;
        if (changed) reissueTick = 0;
        // 没有路径时 5 tick 重试一次，有路径在走时按正常间隔重发 —— 不每 tick 重算，
        //   否则"轨迹点走不到"时每个 tick 都会跑一次完整寻路。
        boolean idle = soldier.getNavigation().isDone();
        if (changed || soldier.tickCount >= reissueTick) {
            reissueTick = soldier.tickCount + (idle ? 5 : REISSUE_INTERVAL);
            issuedTarget = target;
            soldier.getNavigation().moveTo(target.x, target.y, target.z, speed);
            // 普通导航只给出"半路路径"或没有路（迷宫 / 隔墙）→ 用 A* 算一条真正的绕路。
            //   只在目标较近时才启用：目标很远时的"半路路径"只是寻路半径限制，
            //   沿它走本来就在接近目标，不需要额外 A*（避免大规模军队的额外开销）。
            net.minecraft.world.level.pathfinder.Path path = soldier.getNavigation().getPath();
            if ((path == null || !path.canReach())
                    && soldier.position().distanceToSqr(target) <= 48.0 * 48.0) {
                soldier.startMarchRouteAround(target);
            }
        }
        return true;
    }

    /**
     * 首领在天上（飞行 / 站在建筑上）时，轨迹点的高度不可达：把目标压到"单位附近的地面高度"，
     *   避免单位一路朝着空中的点顶墙 / 顶建筑。
     *   下方在单位高度附近找不到可站地面（建筑顶 / 悬空）→ 返回 null，表示这个点够不到，
     *   交由调用方原本的逻辑处理（不再对着那面墙推）。
     */
    @Nullable
    private static Vec3 clampToGround(AbstractArmySoldier soldier, Vec3 p) {
        if (p.y <= soldier.getY() + 2.0) return p;
        double gy = AiNavUtil.groundNear(soldier.level(), p.x, soldier.getY(), p.z);
        if (Double.isNaN(gy)) return null;
        return new Vec3(p.x, gy, p.z);
    }
}
