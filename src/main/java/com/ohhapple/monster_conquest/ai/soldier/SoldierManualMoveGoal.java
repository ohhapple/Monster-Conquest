package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 手动移动 Goal（控制工具指定点）：
 *   · 每 tick（或路径走完 / 每 10 tick）重发原版寻路，半路路径也能一路朝目标推进；
 *   · 原版只能给"半路路径"（长墙 / 迷宫）时，交给方块级 A* 绕路接管；
 *   · 中途改命令 → 立刻放弃旧路线，按新目标重新规划；
 *   · 到达判定用水平距离 + 高度容差，并在到达时停住惯性（骑乘时停坐骑）。
 *   · 移动命令优先：只有"玩家点名"的攻击目标（强制目标 / 精确打击）才能打断它，
 *     普通的自主索敌不会 —— 玩家让去哪就去哪（见 AttackConfiguredEnemyGoal 里的同名判定）。
 */
public class SoldierManualMoveGoal extends Goal {

    /**
     * 固定 1.0：不允许任何移动速度倍率。
     *   单位实际速度只由实体类里的 MOVEMENT_SPEED 属性决定（写多少就是多少）。
     */
    private static final double SPEED = 1.0D;
    /** 到达判定（格²）：1 格以内才算到，避免"离目标还有两三格就停下"。 */
    private static final double ARRIVE_SQR = 1.0;
    /** 原版路径最短重发间隔（tick）。 */
    private static final int REISSUE_INTERVAL = 10;
    /** 原版路径变"半路路径"时的检查间隔（tick）。 */
    private static final int PATH_CHECK_INTERVAL = 10;

    private final AbstractArmySoldier soldier;
    private int reissueTick = 0;
    private int pathCheckTick = 0;
    /** 到达辅助统计（防止快速坐骑在目标点附近来回跑永远判不到"到达"）。 */
    private Vec3 lastOrderTarget = null;
    private double bestDistSqr = Double.MAX_VALUE;
    private double lastDistSqr = Double.MAX_VALUE;
    private int nearTicks = 0;
    private int awayTicks = 0;

    public SoldierManualMoveGoal(AbstractArmySoldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public void start() {
        // 手动指令优先：清掉可能残留的绕路路线（例如跟随首领轨迹时留下的 A* 路线）
        soldier.clearMarchRoute();
        lastOrderTarget = null;
        bestDistSqr = Double.MAX_VALUE;
        lastDistSqr = Double.MAX_VALUE;
        nearTicks = 0;
        awayTicks = 0;
    }

    @Override
    public boolean canUse() {
        if (soldier.getManualMoveTarget() == null) return false;
        // 只有"玩家点名的攻击目标"（强制目标 / 精确打击）才能打断手动移动；
        //   普通自主索敌不再打断 —— 移动命令优先（见 AttackConfiguredEnemyGoal 里的同名判定）。
        LivingEntity target = soldier.getTarget();
        if (target != null && target.isAlive() && soldier.isPlayerOrderedTarget(target)) {
            soldier.setManualMoveTarget(null);
            soldier.setManualFacingAnchor(null);
            return false;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (soldier.getManualMoveTarget() == null) return false;
        // 只有玩家点名的攻击目标才能打断手动移动（自主索敌不再打断，移动命令优先）
        LivingEntity target = soldier.getTarget();
        if (target != null && target.isAlive() && soldier.isPlayerOrderedTarget(target)) {
            soldier.setManualMoveTarget(null);
            soldier.setManualFacingAnchor(null);
            return false;
        }
        return true;
    }

    @Override
    public void tick() {
        Vec3 target = soldier.getManualMoveTarget();
        if (target == null) return;

        // 用"真正在走的实体"（骑乘 = 坐骑）算距离：马的身体大、速度快，
        // 用骑手坐标 / 1 格判定会出现"永远差一点 → 围着目标跑"。
        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        boolean mounted = mover != soldier;
        double dx = mover.getX() - target.x;
        double dz = mover.getZ() - target.z;
        double distSqr = dx * dx + dz * dz;
        double arriveSqr = mounted ? 2.0 * 2.0 : ARRIVE_SQR;

        // 换目标 → 重置统计
        if (lastOrderTarget == null || lastOrderTarget.distanceToSqr(target) > 1.0) {
            lastOrderTarget = target;
            bestDistSqr = distSqr;
            lastDistSqr = distSqr;
            nearTicks = 0;
            awayTicks = 0;
            // 中途改命令：不等 10 tick 的重发间隔，本 tick 就按新目标重新寻路
            //   （否则会先沿旧路径走一小段才转向新目的地）。
            reissueTick = 0;
        }

        boolean arrived = distSqr < arriveSqr && Math.abs(mover.getY() - target.y) < 2.5;
        if (!arrived && distSqr < 36.0) {
            // ① 一直在附近、却连续远离（来回跑）→ 视为到达
            if (distSqr < bestDistSqr - 0.25) {
                bestDistSqr = distSqr;
                awayTicks = 0;
            } else if (distSqr > lastDistSqr + 0.02) {
                awayTicks++;
            } else {
                awayTicks = 0;
            }
            nearTicks++;
            if (awayTicks > 8) arrived = true;
            // ② 在目标 3 格内磨蹭超过 7 秒还没到（例如被门框 / 地形卡角度）
            if (nearTicks > 140 && distSqr < 9.0) arrived = true;
        } else if (!arrived) {
            nearTicks = 0;
            awayTicks = 0;
        }
        lastDistSqr = distSqr;

        if (arrived) {
            soldier.setManualMoveTarget(null);
            soldier.setManualFacingAnchor(null);
            soldier.clearMarchRoute();
            mover.getNavigation().stop();
            Vec3 dmMover = mover.getDeltaMovement();
            mover.setDeltaMovement(0, dmMover.y, 0);
            AiNavUtil.stopMovementInput(mover);
            Vec3 dm = soldier.getDeltaMovement();
            soldier.setDeltaMovement(0, dm.y, 0);
            return;
        }

        // 方块级 A* 绕路接管中 → 不抢移动；中途改了目的地则立刻放弃旧路线
        // 正在下梯子（或刚悬在梯顶上方）：停掉寻路 / 绕路，交给实体自身的下滑逻辑。
        //   否则原版会一路给"爬回平台"的半路路径：在梯子上擦着砖块、下滑途中又想往上爬、
        //   落地后还被旧路线往回拉。
        if (AiNavUtil.isDescendingOnLadder(soldier, target)) {
            soldier.getNavigation().stop();
            soldier.clearMarchRoute();
            return;
        }

        // 最后 4 格：不再重发路径（原版 moveTo 的精度只有 1 格、还会在目标附近来回修正），
        //   直线没阻挡就直接驱动"真正在走的实体"走向目标点。
        // 按本体宽度判定：宽体坐骑擦着墙角时中心线是通的、身体会撞角 ——
        //   直线过不去就交给寻路绕过去，不能一直朝墙角推。
        if (distSqr < 16.0 && AiNavUtil.hasClearLineForBody(soldier.level(),
                mover.position().add(0, 0.5, 0), target.add(0, 0.5, 0),
                mover.getBbWidth() * 0.5 + 0.05)) {
            mover.getNavigation().stop();
            soldier.clearMarchRoute();
            mover.getMoveControl().setWantedPosition(target.x, target.y, target.z, SPEED);
            return;
        }
        // 原版把门 / 栅栏门当成一格台阶 → 走到门口会无故跳一下。
        //   每 tick 检查：发现就改用方块级 A* 精确路线（节点高度与脚齐平，不会再跳）。
        if (!soldier.isFollowingMarchRoute()
                && AiNavUtil.pathStepsOverDoor(soldier, soldier.getNavigation().getPath())) {
            soldier.startMarchRouteAround(target);
        }
        // 正前方就是门：停掉寻路，直接让移动控制器以"脚高度"穿过门洞 ——
        //   原版 MoveControl 会把门当成一格台阶而跳起来。
        BlockPos doorAhead = AiNavUtil.handDoorNear(soldier);
        if (doorAhead != null) {
            // 对准"没有门板碰撞"的那半边（开着的门板只有 3/16 厚，走正中心会蹭到）
            Vec3 pass = AiNavUtil.doorPassPoint(mover, doorAhead);
            double cx = pass.x;
            double cz = pass.z;
            // 沿"当前行进方向"穿过门洞，只朝空侧中心做 30% 的横向修正：
            //   · 不会在走过门洞中线后方向反转（来回顶门框）；
            //   · 又能把身体拉到没有门板的那半边，避免蹭到开着的门板减速。
            double dirX = 0;
            double dirZ = 0;
            Vec3 delta = mover.getDeltaMovement();
            double mvLen = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            if (mvLen > 0.02) {
                dirX = delta.x / mvLen;
                dirZ = delta.z / mvLen;
            } else {
                double toX = cx - mover.getX();
                double toZ = cz - mover.getZ();
                double toLen = Math.sqrt(toX * toX + toZ * toZ);
                if (toLen > 1.0E-3) {
                    dirX = toX / toLen;
                    dirZ = toZ / toLen;
                }
            }
            if (dirX != 0.0 || dirZ != 0.0) {
                mover.getNavigation().stop();
                soldier.clearMarchRoute();
                double aheadX = mover.getX() + dirX * 2.5;
                double aheadZ = mover.getZ() + dirZ * 2.5;
                mover.getMoveControl().setWantedPosition(
                        aheadX + (cx - aheadX) * 0.3, mover.getY(),
                        aheadZ + (cz - aheadZ) * 0.3, SPEED);
                return;
            }
        }
        if (soldier.isFollowingMarchRoute()) {
            if (soldier.isFollowingMarchTowards(target)) return;
            soldier.clearMarchRoute();
        }

        // 原版寻路：路径走完 / 每 10 tick 重发一次
        PathNavigation nav = soldier.getNavigation();
        if (soldier.tickCount >= reissueTick) {
            boolean idle = nav.isDone();
            // 没有路径时 5 tick 重试一次，有路径在走时按正常间隔重发 —— 不每 tick 重算，
            //   否则"目标点走不到"时每个 tick 都会跑一次完整寻路。
            reissueTick = soldier.tickCount + (idle ? 5 : REISSUE_INTERVAL);
            if (idle) {
                // 导航刚走完：清掉移动控制器残留的旧目标点（否则快速坐骑会掉头走向旧点）。
                AiNavUtil.stopMovementInput(mover);
            }
            // 目标在下方、身边能找到可下的梯子 → 先寻路到"登梯口"（梯子旁能站的一格），
            //   走到梯子旁再交给实体自身的攀爬逻辑下梯。
            //   否则原版只能给一条"走到平台边缘就停"的半路路径 → 单位站在边上不下去。
            Vec3 access = AiNavUtil.descentAccessPoint(soldier, target);
            if (access != null) {
                nav.moveTo(access.x, access.y, access.z, SPEED);
            } else {
                nav.moveTo(target.x, target.y, target.z, SPEED);
            }
        }

        // 原版只能给"半路路径"（长墙 / 迷宫）→ 立刻尝试方块级 A* 绕路
        if (soldier.tickCount >= pathCheckTick) {
            pathCheckTick = soldier.tickCount + PATH_CHECK_INTERVAL;
            Path path = nav.getPath();
            if (path == null || !path.canReach()
                    || AiNavUtil.pathStepsOverDoor(soldier, path)) {
                // 原版把门 / 栅栏门当台阶时，路径节点会比脚高一格 → 走到门口会无故跳；
                //   改用方块级 A* 精确路线（节点高度准确）。
                soldier.startMarchRouteAround(target);
            }
        }
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
    }
}
