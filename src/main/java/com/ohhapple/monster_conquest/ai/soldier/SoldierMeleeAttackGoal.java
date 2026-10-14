package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

public class SoldierMeleeAttackGoal extends Goal {

    private final AbstractArmySoldier soldier;
    private final double speedModifier;
    private final double attackReach;

    private int attackCooldown = 0;
    /** -1 = 还没用过：第一次调用先随机错开，避免整队同一 tick 一起跑寻路。 */
    private int pathCooldown = -1;
    /** 围攻槽位连续寻路失败次数：连续失败改用完整绕路规划。 */
    private int failedSlotPaths = 0;

    /**
     * 寻路 / 重规划的节流：有路径在走时 10~20 tick 重发一次，没路径时 5 tick 重试一次。
     *   没有节流时，几十个单位一起打墙后 / 封闭盒子里的敌人会每 tick 重算路径，
     *   把服务器 tick 占满。
     */
    private boolean shouldReissuePath(net.minecraft.world.entity.Mob mover) {
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
    private static final double ENCIRCLE_RADIUS = 1.8;

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
    public boolean canUse() {
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
        failedSlotPaths = 0;
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
    }

    @Override
    public void tick() {

        LivingEntity target = soldier.getTarget();
        if (target == null) return;

        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);

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
            if (!soldier.hasBodyLineOfSight(target)) {
                if (shouldReissuePath(horse)) {
                    soldier.replanRouteTo(target);
                }
                return;
            }

            // 只有贴近到 2 格内才停马；4 格内即可挥砍（继续骑过去）
            if (mountedDistSqr > MOUNTED_STOP_DISTANCE * MOUNTED_STOP_DISTANCE) {
                horse.getLookControl().setLookAt(target, 30.0f, 30.0f);
                if (shouldReissuePath(horse)) {
                    soldier.replanRouteTo(target);
                }
            } else {
                horse.getNavigation().stop();
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
        //   无论是否处在攻击距离内 —— 贴身隔墙时也要立即绕路，不能原地僵持。
        if (!soldier.hasBodyLineOfSight(target)) {
            if (shouldReissuePath(soldier)) {
                soldier.replanRouteTo(target);
            }
            return;
        }

        if (distSqr <= attackReach * attackReach) {
            soldier.getNavigation().stop();
            doAttack(target, distSqr);
            return;
        }

        double angle = encircleAngle();
        double slotX = target.getX() + Math.cos(angle) * ENCIRCLE_RADIUS;
        double slotZ = target.getZ() + Math.sin(angle) * ENCIRCLE_RADIUS;

        if (shouldReissuePath(soldier)) {
            boolean moved = soldier.getNavigation().moveTo(slotX, target.getY(), slotZ, speedModifier);
            if (!moved) {
                // 围攻槽位走不通（目标高速移动 / 槽位落在障碍里）：
                //   连续失败就改用完整绕路规划，避免站在原地僵住。
                if (++failedSlotPaths >= 2) {
                    failedSlotPaths = 0;
                    soldier.replanRouteTo(target);
                }
            } else {
                failedSlotPaths = 0;
            }
        }

        doAttack(target, distSqr);
    }

    private void doAttack(LivingEntity target, double distToTargetSqr) {
        doAttack(target, distToTargetSqr, attackReach);
    }

    private void doAttack(LivingEntity target, double distToTargetSqr, double reach) {
        if (distToTargetSqr > reach * reach) return;
        // 高度差限制：目标在墙头 / 高台上（高度差超过 1.5 格）→ 挥砍不到
        if (Math.abs(soldier.getY() - target.getY()) > MAX_MELEE_Y_DIFF) return;
        // 不穿墙：被方块挡住（身体中心连线被挡）时不挥砍，也不会造成伤害
        if (!soldier.hasBodyLineOfSight(target)) return;
        if (--attackCooldown <= 0) {
            // 攻击间隔由士兵决定（重锤兵等重型武器更慢）
            attackCooldown = Math.max(1, soldier.getMeleeAttackIntervalTicks());
            soldier.swing(InteractionHand.MAIN_HAND);
            soldier.doHurtTarget(target);
        }
    }

    private double encircleAngle() {
        long h = soldier.getUUID().getMostSignificantBits()
                ^ soldier.getUUID().getLeastSignificantBits();
        return ((h & 0xFFFFL) / (double) 0x10000L) * Math.PI * 2.0;
    }

    private boolean isDualLowHealth() {
        return soldier.getHealth() < soldier.getMaxHealth() * DUAL_LOW_HP_THRESHOLD;
    }

    private static double horizontalDistSqr(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }
}
