package com.ohhapple.monster_conquest.ai.commander;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CommanderMarchGoal extends Goal {

    /** 手动指令的移动速度。 */
    private static final double MANUAL_SPEED = 1.0D;
    /** 到达判定（格²）。 */
    /** 到达判定（格²）：1 格以内才算到。 */
    private static final double ARRIVE_SQR = 1.0;
    /** 原版路径最短重发间隔（tick）。 */
    private static final int REISSUE_INTERVAL = 10;
    /** 原版路径变"半路路径"时的检查间隔（tick）。 */
    private static final int PATH_CHECK_INTERVAL = 10;

    /** 自然游走：一次最多走多远（水平 / 垂直，格），和原版生物闲逛一致。 */
    private static final int WANDER_RANGE_XZ = 10;
    private static final int WANDER_RANGE_Y = 7;
    /** 自然游走速度。 */
    private static final double WANDER_SPEED = 1.0D;
    /** 两趟游走之间至少歇多久（tick）。 */
    private static final int WANDER_IDLE_MIN = 40;
    private static final int WANDER_IDLE_MAX = 120;

    private final AbstractArmyCommander commander;
    private int idleTicks = 0;
    private int reissueTick = 0;
    private int pathCheckTick = 0;
    /** 到达辅助统计（防止快速坐骑在目标点附近来回跑永远判不到"到达"）。 */
    private Vec3 lastOrderTarget = null;
    private double bestDistSqr = Double.MAX_VALUE;
    private double lastDistSqr = Double.MAX_VALUE;
    private int nearTicks = 0;
    private int awayTicks = 0;

    public CommanderMarchGoal(AbstractArmyCommander commander) {
        this.commander = commander;
        // 只占用移动；朝向交给原版的环顾 / 看玩家目标，走起来更自然
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (commander.getManualMarchTarget() != null) return true;
        if (!CombatConfig.enableMarch) return false;
        if (commander.isRetreating()) return false;
        if (commander.getCombatTarget() != null) return false;
        if (idleTicks > 0) { idleTicks--; return false; }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (commander.getManualMarchTarget() != null) return true;
        if (!CombatConfig.enableMarch) return false;
        if (commander.isRetreating()) return false;
        if (commander.getCombatTarget() != null) return false;
        return !commander.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (commander.getManualMarchTarget() != null) return;
        // 自然游走：像原版生物那样随机挑一个附近可达的位置走过去
        Vec3 destination = DefaultRandomPos.getPos(commander, WANDER_RANGE_XZ, WANDER_RANGE_Y);
        if (destination != null) {
            commander.getNavigation().moveTo(destination.x, destination.y, destination.z, WANDER_SPEED);
        }
    }

    @Override
    public void tick() {
        // 控制工具手动指定 → 直奔目标点
        Vec3 manual = commander.getManualMarchTarget();
        if (manual == null) return;   // 自动游走的路径在 start() 里已经给好了

        // 用"真正在走的实体"（骑乘 = 坐骑）算距离；骑乘判定放宽到 2 格。
        Mob mover = commander.getVehicle() instanceof Mob m ? m : commander;
        boolean mounted = mover != commander;
        double dx = mover.getX() - manual.x;
        double dz = mover.getZ() - manual.z;
        double distSqr = dx * dx + dz * dz;
        double arriveSqr = mounted ? 2.0 * 2.0 : ARRIVE_SQR;

        if (lastOrderTarget == null || lastOrderTarget.distanceToSqr(manual) > 1.0) {
            lastOrderTarget = manual;
            bestDistSqr = distSqr;
            lastDistSqr = distSqr;
            nearTicks = 0;
            awayTicks = 0;
            // 中途改命令：不等 10 tick 的重发间隔，本 tick 就按新目标重新寻路
            //   （否则会先沿旧路径走一小段才转向新目的地）。
            reissueTick = 0;
        }

        boolean arrived = distSqr < arriveSqr && Math.abs(mover.getY() - manual.y) < 2.5;
        if (!arrived && distSqr < 36.0) {
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
            if (nearTicks > 140 && distSqr < 9.0) arrived = true;
        } else if (!arrived) {
            nearTicks = 0;
            awayTicks = 0;
        }
        lastDistSqr = distSqr;

        if (arrived) {
            commander.setManualMarchTarget(null);
            commander.clearMarchRoute();
            mover.getNavigation().stop();
            Vec3 dmMover = mover.getDeltaMovement();
            mover.setDeltaMovement(0, dmMover.y, 0);
            com.ohhapple.monster_conquest.ai.nav.AiNavUtil.stopMovementInput(mover);
            Vec3 dm = commander.getDeltaMovement();
            commander.setDeltaMovement(0, dm.y, 0);
            return;
        }

        // 方块级 A* 绕路接管中 → 不抢移动；中途改了目的地则立刻放弃旧路线
        // 正在下梯子：停掉寻路 / 绕路，交给实体自身的下滑逻辑（否则会在梯子上来回顶）。
        if (com.ohhapple.monster_conquest.ai.nav.AiNavUtil
                .isDescendingOnLadder(commander, manual)) {
            commander.getNavigation().stop();
            commander.clearMarchRoute();
            return;
        }
        // 最后 4 格：不再重发路径，直线没阻挡就直接驱动"真正在走的实体"走向目标点。
        // 按本体宽度判定（坐骑宽 1.4 格时擦墙角会撞上，直线过不去就交给寻路绕）。
        if (distSqr < 16.0 && com.ohhapple.monster_conquest.ai.nav.AiNavUtil.hasClearLineForBody(
                commander.level(), mover.position().add(0, 0.5, 0),
                manual.add(0, 0.5, 0), mover.getBbWidth() * 0.5 + 0.05)) {
            mover.getNavigation().stop();
            commander.clearMarchRoute();
            mover.getMoveControl().setWantedPosition(manual.x, manual.y, manual.z, MANUAL_SPEED);
            return;
        }
        // 正前方就是门：以"脚高度"直接穿过，避免原版台阶跳。
        BlockPos doorAhead = com.ohhapple.monster_conquest.ai.nav.AiNavUtil
                .handDoorNear(commander);
        if (doorAhead != null) {
            // 对准"没有门板碰撞"的那半边（开着的门板只有 3/16 厚，走正中心会蹭到）
            Vec3 pass = com.ohhapple.monster_conquest.ai.nav.AiNavUtil
                    .doorPassPoint(mover, doorAhead);
            double cx = pass.x;
            double cz = pass.z;
            // 沿当前行进方向穿过，只朝空侧中心做 30% 横向修正（不会反转、也不蹭门板）
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
                commander.clearMarchRoute();
                double aheadX = mover.getX() + dirX * 2.5;
                double aheadZ = mover.getZ() + dirZ * 2.5;
                mover.getMoveControl().setWantedPosition(
                        aheadX + (cx - aheadX) * 0.3, mover.getY(),
                        aheadZ + (cz - aheadZ) * 0.3, MANUAL_SPEED);
                return;
            }
        }
        if (commander.isFollowingMarchRoute()) {
            if (commander.isFollowingMarchTowards(manual)) return;
            commander.clearMarchRoute();
        }

        // 原版寻路：路径走完 / 每 10 tick 重发一次（不硬掰朝向，移动交给原版导航）
        PathNavigation nav = commander.getNavigation();
        if (commander.tickCount >= reissueTick) {
            boolean idle = nav.isDone();
            // 没有路径时 5 tick 重试一次，有路径在走时按正常间隔重发 —— 不每 tick 重算，
            //   否则"目标点走不到"时每个 tick 都会跑一次完整寻路。
            reissueTick = commander.tickCount + (idle ? 5 : REISSUE_INTERVAL);
            if (idle) {
                // 导航刚走完：清掉移动控制器残留的旧目标点（否则快速坐骑会掉头走向旧点）。
                com.ohhapple.monster_conquest.ai.nav.AiNavUtil.stopMovementInput(mover);
            }
            // 目标在下方、身边能找到可下的梯子 → 先寻路到"登梯口"，再交给攀爬逻辑下梯。
            Vec3 access = com.ohhapple.monster_conquest.ai.nav.AiNavUtil
                    .descentAccessPoint(commander, manual);
            if (access != null) {
                nav.moveTo(access.x, access.y, access.z, MANUAL_SPEED);
            } else {
                nav.moveTo(manual.x, manual.y, manual.z, MANUAL_SPEED);
            }
        }

        // 原版只能给"半路路径"（长墙 / 迷宫）→ 立刻尝试方块级 A* 绕路
        if (commander.tickCount >= pathCheckTick) {
            pathCheckTick = commander.tickCount + PATH_CHECK_INTERVAL;
            Path path = nav.getPath();
            if (path == null || !path.canReach()
                    || com.ohhapple.monster_conquest.ai.nav.AiNavUtil
                    .pathStepsOverDoor(commander, path)) {
                // 门被原版当成台阶 → 换方块级 A* 精确路线，避免平地开门跳。
                commander.startMarchRouteAround(manual);
            }
        }
    }

    @Override
    public void stop() {
        commander.getNavigation().stop();
        idleTicks = WANDER_IDLE_MIN + commander.getRandom().nextInt(WANDER_IDLE_MAX - WANDER_IDLE_MIN);
    }
}
