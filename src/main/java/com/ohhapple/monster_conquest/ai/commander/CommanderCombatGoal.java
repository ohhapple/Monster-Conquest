package com.ohhapple.monster_conquest.ai.commander;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.MeleeReach;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

public class CommanderCombatGoal extends Goal {

    /** 首领远程 / 近战的武器切换距离。卡住判定等外部逻辑也复用这些值。 */
    public static final double RANGED_THRESHOLD = 8.0;
    public static final double MELEE_RANGE = 3.0;
    /** 近战允许的最大高度差（格）：墙头 / 高台上的目标砍不到。 */
    public static final double MAX_MELEE_Y_DIFF = 1.5;
    /** 首领弓箭的最大射程（格）：与弓手实际弹道 / 索敌距离一致。 */
    public static final double RANGED_MAX_RANGE = 96.0;
    private static final int MELEE_INTERVAL = 10;
    private static final int RANGED_INTERVAL = 25;

    private static final double RETREAT_SAFE_DIST = 22.0;
    private static final double RETREAT_FIRE_MAX = 30.0;
    private static final double RETREAT_RELEASE_MARGIN = 2.5;
    private static final int RETREAT_COMMIT_TICKS = 30;
    private static final int BOW_DRAW_TICKS = 8;

    private final AbstractArmyCommander commander;
    private int cooldown = 0;
    /** 追击 / 撤退寻路节流：避免每 tick 重算路径（打不到的目标尤其费）。 */
    private int pathCooldown = 0;

    @Nullable private UUID retreatTargetId;
    @Nullable private Vec3 retreatDestination;
    private int retreatMoveTick = -1000;
    private int retreatCommitUntil = 0;
    private boolean retreatRouteReleased;
    private boolean directRetreat;
    private int retreatSideDirection = 1;
    private int bowDrawTicks;
    @Nullable private UUID bowDrawTargetId;

    public CommanderCombatGoal(AbstractArmyCommander commander) {
        this.commander = commander;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // 低血撤退与骑乘远程后撤都按单 tick 位移推进，必须每 tick 执行。
        return true;
    }

    /** 寻路节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissuePath(Mob mover) {
        if (pathCooldown > 0) {
            pathCooldown--;
            return false;
        }
        pathCooldown = mover.getNavigation().isDone() ? 5 : 10;
        return true;
    }

    @Override
    public boolean canUse() {
        // 有手动行军目标时不进战斗，让 CommanderMarchGoal 接管；
        //   但刚被攻击时必须允许反击，战斗结束后再继续原行军目标。
        if (commander.getManualMarchTarget() != null
                && !commander.isUnderRecentAttack()
                && !(commander.getTarget() != null && commander.getTarget().isAlive())) {
            return false;
        }
        LivingEntity t = commander.getCombatTarget();
        return t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        cancelBowDraw();
        cooldown = 0;
        retreatTargetId = null;
        retreatDestination = null;
        retreatMoveTick = -1000;
        retreatCommitUntil = 0;
        retreatRouteReleased = false;
        directRetreat = false;
        retreatSideDirection = commander.getRandom().nextBoolean() ? 1 : -1;
        bowDrawTicks = 0;
        bowDrawTargetId = null;
    }

    @Override
    public void tick() {
        LivingEntity target = commander.getCombatTarget();
        if (target == null) return;

        if (commander.isRetreating()) { tickRetreat(target); return; }

        // 打不到目标（距离 / 射界不满足）→ 走完整绕路规划追击（绕墙 / 穿门）
        if (!commander.canCurrentlyAttack(target) && commander.tickCount % 20 == 0) {
            commander.replanRouteTo(target);
        }

        double horizDist = horizontalDist(commander, target);
        boolean mounted = commander.isPassenger();

        if (horizDist > RANGED_THRESHOLD) {
            commander.setWeaponMode(AbstractArmyCommander.WeaponMode.BOW);
        } else if (mounted) {
            commander.setWeaponMode(AbstractArmyCommander.WeaponMode.LANCE);
        } else {
            commander.setWeaponMode(AbstractArmyCommander.WeaponMode.BLADE);
        }
        if (horizDist <= RANGED_THRESHOLD) cancelBowDraw();

        commander.getLookControl().setLookAt(target, 30.0f, 30.0f);

        // 唯一移动逻辑：靠近目标（骑乘首领的移动由 controlMount 处理）
        //   不再调用 stopAllMovement —— 那会和 controlMount 抢控制权
        //   正在按绕路路线追击时，移动交给路线跟随，避免半路路径覆盖路线
        if (!commander.isFollowingRoute()) {
            if (!mounted) {
                if (horizDist > MELEE_RANGE
                        || AiNavUtil.isMovingHorizontally(target)) {
                    if (shouldReissuePath(commander)) {
                        commander.getNavigation().moveTo(target, 1.0D);
                    }
                } else {
                    commander.getNavigation().stop();
                }
            }
        }

        // 远程：只在射程内射击，不阻止移动
        if (horizDist > RANGED_THRESHOLD && horizDist < RANGED_MAX_RANGE) {
            if (cooldown > 0) cooldown--;
            if (cooldown <= 0 && !holdBowDraw(target.getUUID())) {
                commander.performRangedAttack(target, 1.0f);
                cooldown = RANGED_INTERVAL;
            }
        }

        // 近战：骑马时伤害由骑枪穿刺（LancePierce）结算，与近战士兵一致；
        //   避免骑马近战贴脸互推、和敌人重叠。
        if (!mounted && horizDist < MELEE_RANGE
                && MeleeReach.canReachVertically(commander, target, MAX_MELEE_Y_DIFF)
                && --cooldown <= 0) {
            commander.doHurtTarget(target);
            cooldown = MELEE_INTERVAL;
        }
    }

    private void tickRetreat(LivingEntity target) {
        commander.setWeaponMode(AbstractArmyCommander.WeaponMode.BOW);
        commander.getLookControl().setLookAt(target, 30.0f, 30.0f);

        double horizDist = horizontalDist(commander, target);
        boolean mounted = commander.isPassenger();

        Mob mover = mounted && commander.getVehicle() instanceof Mob mount ? mount : commander;
        if (retreatTargetId == null || !retreatTargetId.equals(target.getUUID())) {
            retreatTargetId = target.getUUID();
            retreatDestination = null;
            retreatMoveTick = -1000;
            retreatCommitUntil = 0;
            retreatRouteReleased = false;
            directRetreat = false;
            retreatSideDirection = commander.getRandom().nextBoolean() ? 1 : -1;
        }

        boolean routeOwnsMovement = commander.isFollowingRoute()
                || commander.isMarchRoutePlanning()
                || commander.isFollowingMarchRoute();
        if (!retreatRouteReleased) {
            commander.releaseMovementRouteForRetreat();
            retreatRouteReleased = true;
            routeOwnsMovement = false;
        }
        double releaseDist = RETREAT_SAFE_DIST + RETREAT_RELEASE_MARGIN;
        if (!routeOwnsMovement && horizDist < releaseDist) {
            boolean routedWithdraw = !directRetreat && retreatDestination != null;
            Vec3 frameMovement = AiNavUtil.observedHorizontalMovement(mover);
            boolean routeStalled = routedWithdraw
                    && (mover.horizontalCollision || frameMovement.lengthSqr() < 1.0E-4);
            boolean allowDirectEscape = !routedWithdraw || routeStalled;
            if (allowDirectEscape && AiNavUtil.applyFullSpeedBackstep(
                    mover, mounted ? commander : null, target)) {
                retreatDestination = null;
                directRetreat = false;
                retreatCommitUntil = 0;
            } else if (allowDirectEscape && trySideStepRetreat(mover, target)) {
                retreatDestination = null;
                directRetreat = false;
                retreatCommitUntil = 0;
            } else {
                int now = commander.tickCount;
                boolean finished = mover.getNavigation().isDone();
                boolean stuck = mover.getNavigation().isStuck();
                boolean directReached = directRetreat && retreatDestination != null
                        && mover.position().distanceToSqr(retreatDestination) <= 1.0;
                boolean needPlan = retreatDestination == null
                        || now >= retreatCommitUntil
                        || directReached
                        || (!directRetreat && (finished || stuck) && now - retreatMoveTick >= 6);
                if (needPlan) {
                    mover.getNavigation().stop();
                    retreatDestination = AiNavUtil.findFallbackSpacingPoint(
                            mover, mounted ? commander : null, target, releaseDist);
                    directRetreat = false;
                    retreatMoveTick = -1000;
                    if (retreatDestination == null) {
                        retreatDestination = AiNavUtil.startRoutedRetreat(
                                mover, mounted ? commander : null, target, releaseDist);
                        if (retreatDestination != null) retreatMoveTick = now;
                    }
                    if (retreatDestination == null) {
                        retreatDestination = AiNavUtil.findImmediateRetreatPoint(
                                mover, mounted ? commander : null, target);
                        directRetreat = retreatDestination != null;
                    }
                    if (retreatDestination == null) {
                        retreatDestination = AiNavUtil.findStraightBackstepPoint(
                                mover, mounted ? commander : null, target);
                        directRetreat = retreatDestination != null;
                    }
                    retreatCommitUntil = now + RETREAT_COMMIT_TICKS;
                }
                if (retreatDestination != null && directRetreat) {
                    mover.getNavigation().stop();
                    mover.getMoveControl().setWantedPosition(
                            retreatDestination.x, retreatDestination.y,
                            retreatDestination.z, 1.0D);
                    retreatMoveTick = now;
                } else if (retreatDestination != null
                        && (now - retreatMoveTick >= 10 || finished || stuck)) {
                    if (mover.getNavigation().moveTo(
                            retreatDestination.x, retreatDestination.y,
                            retreatDestination.z, 1.0D)) {
                        retreatMoveTick = now;
                    } else {
                        retreatDestination = null;
                    }
                }
            }
        } else if (!routeOwnsMovement) {
            AiNavUtil.haltMovement(mover);
            retreatDestination = null;
            directRetreat = false;
        }
        if (mounted && commander.getVehicle() instanceof WarHorseEntity horse) {
            float yaw = horse.getYRot();
            commander.setYRot(yaw);
            commander.yBodyRot = yaw;
            commander.setYHeadRot(yaw);
            commander.yRotO = yaw;
            commander.yBodyRotO = yaw;
            commander.yHeadRotO = yaw;
        }

        if (horizDist < RETREAT_FIRE_MAX) {
            if (cooldown > 0) cooldown--;
            if (cooldown <= 0 && !holdBowDraw(target.getUUID())) {
                commander.performRangedAttack(target, 1.0f);
                cooldown = RANGED_INTERVAL;
            }
        }
    }

    @Override
    public void stop() {
        cancelBowDraw();
        stopAllMovement(commander.isPassenger());
        retreatTargetId = null;
        retreatDestination = null;
        retreatMoveTick = -1000;
        retreatCommitUntil = 0;
        retreatRouteReleased = false;
        directRetreat = false;
    }

    private boolean holdBowDraw(UUID targetId) {
        if (commander.getMainHandItem().getUseAnimation() != UseAnim.BOW) {
            cancelBowDraw();
            return false;
        }
        if (bowDrawTicks > 0) {
            if (!targetId.equals(bowDrawTargetId)) {
                cancelBowDraw();
                return true;
            }
            if (--bowDrawTicks > 0) return true;
            cancelBowDraw();
            return false;
        }
        commander.startUsingItem(InteractionHand.MAIN_HAND);
        bowDrawTicks = BOW_DRAW_TICKS;
        bowDrawTargetId = targetId;
        return true;
    }

    private void cancelBowDraw() {
        if (commander.isUsingItem() && commander.getUsedItemHand() == InteractionHand.MAIN_HAND
                && commander.getUseItem().getUseAnimation() == UseAnim.BOW) {
            commander.stopUsingItem();
        }
        bowDrawTicks = 0;
        bowDrawTargetId = null;
    }

    private void stopAllMovement(boolean mounted) {
        if (mounted && commander.getVehicle() instanceof Mob horse) {
            AiNavUtil.haltMovement(horse);
            AiNavUtil.stopMovementInput(commander);
        } else {
            AiNavUtil.haltMovement(commander);
        }
    }

    private boolean trySideStepRetreat(Mob mover, LivingEntity target) {
        LivingEntity rider = commander.isPassenger() ? commander : null;
        if (AiNavUtil.applyFullSpeedSidestep(mover, rider, target, retreatSideDirection)) {
            return true;
        }
        retreatSideDirection = -retreatSideDirection;
        return AiNavUtil.applyFullSpeedSidestep(mover, rider, target, retreatSideDirection);
    }

    private static double horizontalDist(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
