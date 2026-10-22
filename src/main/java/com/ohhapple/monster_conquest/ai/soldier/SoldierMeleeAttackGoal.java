package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.MeleeReach;
import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

public class SoldierMeleeAttackGoal extends Goal {

    private final AbstractArmySoldier soldier;
    private final double speedModifier;
    private final double attackReach;

    private int attackCooldown = 0;
    /** -1 = 还没用过：第一次调用先随机错开，避免整队同一 tick 一起跑寻路。 */
    private int pathCooldown = -1;
    /** 连续寻路失败次数：连续失败改用完整绕路规划。 */
    private int failedTargetPaths = 0;
    /** 诊断采样独立计时：Goal 默认隔 tick 执行，不能依赖 tickCount 的固定奇偶。 */
    private int lastDebugTick = -20;
    private int lastHitDebugTick = -20;
    private int lastMissDebugTick = -20;
    @Nullable private UUID approachTargetId;
    private double approachDirX;
    private double approachDirZ;
    private int approachRefreshTick;
    @Nullable private UUID motionTargetId;
    private Vec3 lastTargetPosition = Vec3.ZERO;
    private Vec3 trackedTargetMotion = Vec3.ZERO;
    private int lastTargetMotionTick = Integer.MIN_VALUE;
    @Nullable private UUID directMoveTargetId;
    private Vec3 directMovePoint = Vec3.ZERO;
    private int directMoveRefreshTick;

    /**
     * 寻路 / 重规划的节流：有路径在走时 10~20 tick 重发一次，没路径时 5 tick 重试一次。
     *   没有节流时，几十个单位一起打墙后 / 封闭盒子里的敌人会每 tick 重算路径，
     *   把服务器 tick 占满。
     */
    private boolean shouldReissuePath(Mob mover) {
        if (pathCooldown < 0) {
            pathCooldown = soldier.getRandom().nextInt(10);
        }
        if (pathCooldown > 0) {
            pathCooldown--;
            return false;
        }
        pathCooldown = mover.getNavigation().isDone() ? 5 : 10 + soldier.getRandom().nextInt(10);
        return true;
    }

    /** 近战攻击距离（格）。卡住判定等外部逻辑也复用这个值。 */
    public static final double MELEE_REACH = 2.4;
    /** 近战允许的最大高度差（格）：站在墙头 / 高处的目标挥砍不到。 */
    public static final double MAX_MELEE_Y_DIFF = 1.5;
    /** 近战最后一段直接逼近距离（格）。 */
    private static final double DIRECT_APPROACH_SQR = 6.0 * 6.0;

    /**
     * 骑马接近目标时的"停马距离"（格）。
     *   与攻击距离 {@code getMountedMeleeReach()}（非骑枪骑兵 4 格）区分：
     *   4 格内就能出手，但马要继续骑到 2 格内才停下，避免远远停住追不上逃跑的目标。
     */
    private static final double MOUNTED_STOP_DISTANCE = 2.0;

    private static final float DUAL_LOW_HP_THRESHOLD = 0.5f;
    private static final double DUAL_MELEE_ENTER_SQ = 5.0 * 5.0;
    private static final double DUAL_MELEE_EXIT_SQ  = 6.0 * 6.0;

    public SoldierMeleeAttackGoal(AbstractArmySoldier soldier) {
        this.soldier = soldier;
        this.speedModifier = 1.0D;
        this.attackReach = MELEE_REACH;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (soldier.isMoveOrderPriorityActive()) return false;
        SoldierRole r = soldier.getRole();
        if (r.isMage()) return false;
        if (r.isThrower()) return false;
        if (r.isArcher()) return false;
        if (r.isGunner()) return false;
        LivingEntity target = soldier.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (r.isDual()) {
            if (isDualLowHealth()) return false;
            return horizontalDistSqr(soldier, target) <= DUAL_MELEE_ENTER_SQ;
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (soldier.isMoveOrderPriorityActive()) return false;
        SoldierRole r = soldier.getRole();
        if (r.isMage()) return false;
        if (r.isThrower()) return false;
        if (r.isArcher()) return false;
        if (r.isGunner()) return false;
        LivingEntity target = soldier.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (r.isDual()) {
            if (isDualLowHealth()) return false;
            return horizontalDistSqr(soldier, target) <= DUAL_MELEE_EXIT_SQ;
        }
        return true;
    }

    @Override
    public void start() {
        attackCooldown = 0;
        pathCooldown = -1;
        failedTargetPaths = 0;
        resetDirectApproachState();
    }

    @Override
    public void stop() {
        Mob mover = soldier.getVehicle() instanceof Mob mount ? mount : soldier;
        AiNavUtil.haltMovement(mover);
        if (mover != soldier) {
            AiNavUtil.stopMovementInput(soldier);
        }
        resetDirectApproachState();
    }

    @Override
    public void tick() {
        // 新命令可能在 Goal 已经运行后才生效：本 tick 立即让出移动，
        //   不等下一次 canContinueToUse 重新选择。
        if (soldier.isMoveOrderPriorityActive()) return;

        LivingEntity target = soldier.getTarget();
        if (target == null) return;

        Vec3 targetMotion = trackTargetMotion(target);
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (GeneralConfig.debugMovementLog && soldier.tickCount - lastDebugTick >= 20) {
            lastDebugTick = soldier.tickCount;
            double debugDistSqr = horizontalDistSqr(soldier, target);
            Monster_conquest.LOGGER.info(
                    "[MELEE-DIAG] t={} unit={} target={} targetPos=({},{},{})"
                            + " dist={} yDiff={} bodyLos={} meleeLos={} cd={} navDone={} wanted=({},{},{})",
                    soldier.tickCount,
                    soldier.getUUID().toString().substring(0, 8),
                    target.getUUID().toString().substring(0, 8),
                    String.format(Locale.ROOT, "%.2f", target.getX()),
                    String.format(Locale.ROOT, "%.2f", target.getY()),
                    String.format(Locale.ROOT, "%.2f", target.getZ()),
                    String.format(Locale.ROOT, "%.2f", Math.sqrt(debugDistSqr)),
                    String.format(Locale.ROOT, "%.2f", target.getY() - soldier.getY()),
                    soldier.hasBodyLineOfSight(target),
                    soldier.hasMeleeLineOfSight(target),
                    attackCooldown,
                    soldier.getNavigation().isDone(),
                    String.format(Locale.ROOT, "%.2f",
                            soldier.getMoveControl().getWantedX()),
                    String.format(Locale.ROOT, "%.2f",
                            soldier.getMoveControl().getWantedY()),
                    String.format(Locale.ROOT, "%.2f",
                            soldier.getMoveControl().getWantedZ()));
        }

        // 方阵 / 战斗追击的方块级路线正在由 MarchRouteFollower 执行时，
        //   近战 Goal 不能再用原版导航或移动控制器覆盖路线。
        if (soldier.isFollowingMarchRoute() || soldier.isMarchRoutePlanning()) return;

        // ============================================================
        // 骑兵分支：
        //   · 骑枪骑兵：冲锋 / 掉头 / 穿刺由 controlMount + tickLancePierce 处理，
        //     这里不做导航或攻击（只有骑枪才有穿刺伤害）。
        //   · 非骑枪骑兵：按普通近战打（靠近 + 挥砍，单体）。
        // ============================================================
        if (soldier.getVehicle() instanceof WarHorseEntity horse) {
            // 骑枪骑兵：冲锋 / 掉头 / 穿刺由 controlMount + tickLancePierce 处理。
            if (soldier.usesLanceInCavalry()) return;
            // 非骑枪骑兵（卫道士 / 溺尸 / 重锤兵）：骑马按普通近战打 ——
            //   靠近目标并在攻击范围内挥砍，攻速 = getMeleeAttackIntervalTicks()（与步兵一致）。
            //   只有骑枪骑兵才有"穿刺路径上所有敌人"的伤害。
            soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
            // 骑马时攻击距离更远（马身会挡开目标，追击逃跑的远程兵 2.8 格容易够不着）
            double mountedReach = soldier.getMountedMeleeReach();
            double mountedDistSqr = horizontalDistSqr(soldier, target);

            // 看不到目标（被墙 / 掩体挡住）：立即重新寻路绕行，不能原地停马僵持
            if (!soldier.hasMeleeLineOfSight(target)) {
                if (shouldReissuePath(horse)) {
                    soldier.replanRouteTo(target);
                }
                return;
            }

            // 逃跑中的目标不能按"到 2 格就停马"处理：目标每一 tick 都在移动，
            //   停马会立刻被拉开距离。近距离直接追到目标身后的停步点，
            //   目标停下后才真正刹停；4 格内仍可边走边挥砍。
            boolean targetMoving = isRetreating(target, soldier, targetMotion);
            if (targetMoving) {
                Vec3 pursuit = mountedPursuitPoint(horse, target);
                pursuit = clampDirectMovePoint(horse, pursuit, 5.5);
                // 非骑枪追击沿用冲锋的稳定瞄准：视线通畅时不分远近，持续直指目标本体。
                driveDirectMove(horse, target, pursuit, 1);
            } else if (mountedDistSqr > MOUNTED_STOP_DISTANCE * MOUNTED_STOP_DISTANCE) {
                horse.getLookControl().setLookAt(target, 30.0f, 30.0f);
                if (mountedDistSqr <= 36.0) {
                    Vec3 stop = stableApproachStopPoint(target,
                            MOUNTED_STOP_DISTANCE, targetMotion);
                    driveDirectMove(horse, target, stop, 3);
                } else if (shouldReissuePath(horse)) {
                    soldier.replanRouteTo(target);
                }
            } else {
                AiNavUtil.haltMovement(horse);
                AiNavUtil.stopMovementInput(soldier);
                pathCooldown = 0;
            }
            doAttack(target, mountedDistSqr, mountedReach);
            return;
        }

        double distSqr = horizontalDistSqr(soldier, target);

        // ============================================================
        // 硬驻守：
        //   · 近战兵：允许在警戒半径（CombatConfig.hardStationRange，默认 48 格）内机动追击，
        //     与骑兵形态一致。目标是否越界由
        //     PlayerCommandState.allowsHardStationTarget / enforceHardStationAnchor 统一把关，
        //     目标被清除后 FormationGoal 会把士兵带回驻守槽位。
        //   · 其他（双形态等）：原地反击，不移动。
        // ============================================================
        if (soldier.isHardStationed() && !soldier.getRole().isMelee()) {
            soldier.getNavigation().stop();
            doAttack(target, distSqr);
            return;
        }

        // ================= 步兵分支 =================

        // 看不到目标（被墙 / 掩体挡住）：直接以目标本体为终点重新寻路（绕墙 / 穿门），
        //   路线入口统一交给完整规划（近战会进入 MarchRouteFollower）：
        //   不在近战 Goal 里用局部攻击点走一条可能只到墙边的半路路径。
        if (!soldier.hasMeleeLineOfSight(target)) {
            if (shouldReissuePath(soldier)) {
                // 直接进入战斗 March 路线，避免 replanRouteTo 被旧的半路路线
                //   提前返回；只有 March 明确失败时才走通用重规划兜底。
                boolean started = soldier.startCombatMarchRouteAround(target);
                if (!started && !soldier.isMarchRoutePlanning()) {
                    soldier.replanRouteTo(target);
                }
            }
            return;
        }

        // 目标还在跑时不要原地站桩挥砍：继续按直接逼近点移动，
        //   否则会陷入"追上 → 停下 → 目标拉开 → 再追"的循环。
        if (distSqr <= attackReach * attackReach
                && !isRetreating(target, soldier, targetMotion)) {
            soldier.getNavigation().stop();
            doAttack(target, distSqr);
            return;
        }

        // 近距离且视线通畅时直接逼近目标边缘。多个友军会从各自方向同时压上，
        //   不再抢同一个固定围攻点，避免只有最先到的单位能进入攻击距离。
        if (distSqr <= DIRECT_APPROACH_SQR) {
            double stopDistance = attackReach * 0.7;
            Vec3 stop = stableApproachStopPoint(target, stopDistance, targetMotion);
            driveDirectMove(soldier, target, stop, 3);
            doAttack(target, distSqr);
            return;
        }

        if (shouldReissuePath(soldier)) {
            boolean moved = soldier.getNavigation().moveTo(target, speedModifier);
            if (!moved) {
                // 原版对目标没有路径（长墙 / 迷宫 / 目标高速移动）：
                //   连续失败就改用完整绕路规划，避免站在原地僵住。
                if (++failedTargetPaths >= 2) {
                    failedTargetPaths = 0;
                    soldier.replanRouteTo(target);
                }
            } else {
                failedTargetPaths = 0;
            }
        }

        doAttack(target, distSqr);
    }

    private void doAttack(LivingEntity target, double distToTargetSqr) {
        doAttack(target, distToTargetSqr, attackReach);
    }

    private void doAttack(LivingEntity target, double distToTargetSqr, double reach) {
        if (distToTargetSqr > reach * reach) return;
        // 普通目标仍限制高度差；头顶的小体型飞行单位允许按碰撞箱高度攻击。
        if (!MeleeReach.canReachVertically(soldier, target, MAX_MELEE_Y_DIFF)) return;
        // 不穿墙：被方块挡住（身体中心连线被挡）时不挥砍，也不会造成伤害
        if (!soldier.hasMeleeLineOfSight(target)) return;
        // 猪灵盾兵优先把盾举稳：准备举盾的这一小段时间内不出手。
        if (soldier.prefersShieldGuardBeforeAttack() && soldier.isShieldGuardBracePending()) {
            return;
        }
        int interval = Math.max(1, soldier.getMeleeAttackIntervalTicks());
        // 兵种状态可能把攻击间隔调短（例如盾兵破盾后恢复普通节奏）：
        // 已经排队的旧冷却也要立刻收紧，不能继续沿用破盾前的长间隔。
        if (attackCooldown > interval) attackCooldown = interval;
        if (--attackCooldown <= 0) {
            // 攻击间隔由士兵决定（重锤兵等重型武器更慢）
            attackCooldown = interval;
            // 近战单位统一播放主手挥砍；枪手不进入这个 Goal，不会有攻击动画。
            soldier.playMeleeAttackAnimation();
            boolean hit = soldier.doHurtTarget(target);
            if (hit && GeneralConfig.debugMovementLog
                    && soldier.tickCount - lastHitDebugTick >= 20) {
                lastHitDebugTick = soldier.tickCount;
                Monster_conquest.LOGGER.info(
                        "[MELEE-HIT] t={} unit={} target={} dist={} targetHp={}/{}",
                        soldier.tickCount,
                        soldier.getUUID().toString().substring(0, 8),
                        target.getUUID().toString().substring(0, 8),
                        String.format(Locale.ROOT, "%.2f",
                                Math.sqrt(distToTargetSqr)),
                        String.format(Locale.ROOT, "%.2f", target.getHealth()),
                        String.format(Locale.ROOT, "%.2f", target.getMaxHealth()));
            } else if (!hit && GeneralConfig.debugMovementLog
                    && soldier.tickCount - lastMissDebugTick >= 20) {
                lastMissDebugTick = soldier.tickCount;
                Monster_conquest.LOGGER.info(
                        "[MELEE-MISS] t={} unit={} target={} dist={} yDiff={}"
                                + " bodyLos={} meleeLos={} stopped={}",
                        soldier.tickCount,
                        soldier.getUUID().toString().substring(0, 8),
                        target.getUUID().toString().substring(0, 8),
                        String.format(Locale.ROOT, "%.2f",
                                Math.sqrt(distToTargetSqr)),
                        String.format(Locale.ROOT, "%.2f",
                                target.getY() - soldier.getY()),
                        soldier.hasBodyLineOfSight(target),
                        soldier.hasMeleeLineOfSight(target),
                        soldier.isAttackStopped());
            }
        }
    }

    private boolean isDualLowHealth() {
        return soldier.getHealth() < soldier.getMaxHealth() * DUAL_LOW_HP_THRESHOLD;
    }

    private static double horizontalDistSqr(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    /** 目标是否正在远离追击者：逃跑中的目标不能靠"到点刹停"追击。 */
    private static boolean isRetreating(LivingEntity target, LivingEntity chaser,
                                        Vec3 targetMotion) {
        Vec3 toTarget = target.position().subtract(chaser.position());
        double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        if (horizontal < 1.0E-4) return false;
        return (targetMotion.x * toTarget.x
                + targetMotion.z * toTarget.z) / horizontal > 0.01;
    }

    /**
     * 锁定一条进攻方位，避免目标移动时停步点围绕目标每 tick 抖动。
     *   目标正在逃跑时优先沿其移动反方向追，目标停下后才退回"目标到追击者"的方位。
     */
    private Vec3 stableApproachStopPoint(LivingEntity target, double distance,
                                         Vec3 targetMotion) {
        int now = soldier.tickCount;
        boolean targetChanged = approachTargetId == null
                || !approachTargetId.equals(target.getUUID());
        if (targetChanged) {
            approachTargetId = target.getUUID();
            approachRefreshTick = now - 1;
            directMoveTargetId = null;
            directMoveRefreshTick = 0;
        }

        Vec3 currentAway = soldier.position().subtract(target.position());
        double currentLen = Math.sqrt(currentAway.x * currentAway.x + currentAway.z * currentAway.z);
        if (currentLen < 1.0E-4) {
            currentAway = new Vec3(
                    -Math.sin(target.getYRot() * ((float) Math.PI / 180f)),
                    0.0,
                    Math.cos(target.getYRot() * ((float) Math.PI / 180f)));
            currentLen = 1.0;
        }
        double currentDirX = currentAway.x / currentLen;
        double currentDirZ = currentAway.z / currentLen;

        if (targetChanged || now >= approachRefreshTick) {
            double targetSpeed = Math.sqrt(
                    targetMotion.x * targetMotion.x
                            + targetMotion.z * targetMotion.z);
            double desiredX = currentDirX;
            double desiredZ = currentDirZ;
            if (targetSpeed > 0.02) {
                double fleeX = -targetMotion.x / targetSpeed;
                double fleeZ = -targetMotion.z / targetSpeed;
                if (fleeX * currentDirX + fleeZ * currentDirZ > 0.35) {
                    desiredX = fleeX;
                    desiredZ = fleeZ;
                }
            }
            if (targetChanged || desiredX * approachDirX + desiredZ * approachDirZ < 0.25) {
                approachDirX = desiredX;
                approachDirZ = desiredZ;
            }
            approachRefreshTick = now + (targetSpeed > 0.02 ? 8 : 20);
        }

        double effectiveDistance = Math.min(distance, currentLen);
        double x = target.getX() + approachDirX * effectiveDistance;
        double z = target.getZ() + approachDirZ * effectiveDistance;
        double y = target.getY();
        if (soldier.level() instanceof ServerLevel level) {
            double ground = AiNavUtil.groundNear(level, x, target.getY(), z);
            if (!Double.isNaN(ground)) y = ground;
        }
        return new Vec3(x, y, z);
    }

    /** 与骑枪冲锋一致，稳定指向目标本体的水平位置。 */
    private static Vec3 mountedPursuitPoint(WarHorseEntity horse, LivingEntity target) {
        Vec3 position = target.getVehicle() instanceof LivingEntity vehicle
                ? vehicle.position() : target.position();
        return new Vec3(position.x, horse.getY(), position.z);
    }

    /**
     * 根据目标在相邻 tick 的实际位置变化判断运动方向。
     *   紧急后撤会直接 move 并清掉水平 delta，只看 delta 会把仍在逃跑的目标误判为静止。
     */
    private Vec3 trackTargetMotion(LivingEntity target) {
        int now = soldier.tickCount;
        boolean sameTarget = motionTargetId != null
                && motionTargetId.equals(target.getUUID());
        int elapsed = sameTarget ? now - lastTargetMotionTick : 0;
        Vec3 sampled = sameTarget && elapsed > 0 && elapsed <= 2
                ? target.position().subtract(lastTargetPosition).scale(1.0 / elapsed)
                : Vec3.ZERO;
        double sampledSpeed = Math.sqrt(sampled.x * sampled.x + sampled.z * sampled.z);
        if (sampledSpeed > 4.0) sampled = Vec3.ZERO;

        if (sampledSpeed < 1.0E-4) {
            Vec3 delta = target.getVehicle() instanceof LivingEntity vehicle
                    ? vehicle.getDeltaMovement() : target.getDeltaMovement();
            sampled = new Vec3(delta.x, 0.0, delta.z);
        } else {
            sampled = new Vec3(sampled.x, 0.0, sampled.z);
        }

        motionTargetId = target.getUUID();
        lastTargetPosition = target.position();
        lastTargetMotionTick = now;
        trackedTargetMotion = sampled;
        return trackedTargetMotion;
    }

    /**
     * MoveControl 读取一次目标后就会进入 WAIT；方向可以节流缓存，但目标必须每 tick 续写，
     * 否则马只在缓存刷新的那一 tick 移动，表现为追近时一停一顿。
     */
    private void driveDirectMove(Mob mover, LivingEntity target,
                                 Vec3 desired, int refreshTicks) {
        int now = soldier.tickCount;
        boolean targetChanged = directMoveTargetId == null
                || !directMoveTargetId.equals(target.getUUID());
        if (targetChanged || now >= directMoveRefreshTick) {
            directMoveTargetId = target.getUUID();
            directMovePoint = desired;
            directMoveRefreshTick = now + Math.max(1, refreshTicks);
        }
        mover.getNavigation().stop();
        mover.getMoveControl().setWantedPosition(
                directMovePoint.x, directMovePoint.y, directMovePoint.z, 1.0D);
    }

    /** 前导点仍以目标为锚，但不能远到触发坐骑移动目标的串线保护。 */
    private static Vec3 clampDirectMovePoint(Mob mover, Vec3 point, double maxDistance) {
        double dx = point.x - mover.getX();
        double dz = point.z - mover.getZ();
        double distSqr = dx * dx + dz * dz;
        if (distSqr <= maxDistance * maxDistance) return point;
        double scale = maxDistance / Math.sqrt(distSqr);
        return new Vec3(
                mover.getX() + dx * scale,
                point.y,
                mover.getZ() + dz * scale);
    }

    private void resetDirectApproachState() {
        approachTargetId = null;
        approachDirX = 0.0;
        approachDirZ = 0.0;
        approachRefreshTick = 0;
        motionTargetId = null;
        lastTargetPosition = Vec3.ZERO;
        trackedTargetMotion = Vec3.ZERO;
        lastTargetMotionTick = Integer.MIN_VALUE;
        directMoveTargetId = null;
        directMovePoint = Vec3.ZERO;
        directMoveRefreshTick = 0;
    }
}
