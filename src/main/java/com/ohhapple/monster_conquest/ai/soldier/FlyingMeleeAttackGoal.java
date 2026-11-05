package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * 飞行单位的追击与近身攻击。
 *   恼鬼可直接穿墙追击；悦灵只会在方块碰撞允许的飞行路线中接近目标。
 */
public class FlyingMeleeAttackGoal extends Goal {

    private static final double MAX_ATTACK_Y_DIFF = 3.0;

    private final AbstractFlyingSoldier flyingUnit;
    private int attackCooldown;

    public FlyingMeleeAttackGoal(AbstractFlyingSoldier flyingUnit) {
        this.flyingUnit = flyingUnit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (flyingUnit.isMoveOrderPriorityActive()) return false;
        LivingEntity target = flyingUnit.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        attackCooldown = 0;
        LivingEntity target = flyingUnit.getTarget();
        if (target != null && target.isAlive()) flyingUnit.beginCombatWith(target);
    }

    @Override
    public void stop() {
        flyingUnit.clearCombatAltitude();
        flyingUnit.stopFlyingMovement();
    }

    @Override
    public void tick() {
        LivingEntity target = flyingUnit.getTarget();
        if (target == null || !target.isAlive()) return;

        flyingUnit.getLookControl().setLookAt(target, 30.0f, 30.0f);
        double distanceSqr = flyingUnit.distanceToSqr(target);
        double dy = Math.abs(flyingUnit.getY() - target.getY());
        boolean aligned = dy <= MAX_ATTACK_Y_DIFF;
        boolean visible = flyingUnit.canPassThroughWalls() || flyingUnit.hasMeleeLineOfSight(target);
        double attackReach = flyingUnit.getFlyingAttackReach();

        if (distanceSqr <= attackReach * attackReach && aligned && visible) {
            double stopDistance = flyingUnit.getFlyingAttackStopDistance();
            if (distanceSqr <= stopDistance * stopDistance) flyingUnit.stopFlyingMovement();
            attack(target);
            return;
        }

        flyingUnit.moveTowardCombatTarget(target);
    }

    private void attack(LivingEntity target) {
        int interval = Math.max(1, flyingUnit.isArenaUnit()
                ? flyingUnit.getArenaMeleeAttackIntervalTicks()
                : flyingUnit.getMeleeAttackIntervalTicks());
        if (attackCooldown > interval) attackCooldown = interval;
        if (--attackCooldown > 0) return;
        attackCooldown = interval;
        flyingUnit.playMeleeAttackAnimation();
        flyingUnit.doHurtTarget(target);
    }
}
