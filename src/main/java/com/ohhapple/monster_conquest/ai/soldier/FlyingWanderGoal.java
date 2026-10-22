package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

/**
 * 无战斗目标时的飞行单位自然游走。
 *   有召唤者时围绕召唤者；失去召唤者且没有队伍时围绕自己的悬停锚点。
 *   游走点只选择空气且碰撞盒完整的位置，避免遁地或堆进方块。
 */
public class FlyingWanderGoal extends Goal {

    private final AbstractFlyingSoldier flyingUnit;
    @Nullable private Vec3 destination;
    @Nullable private Vec3 anchorAtChoice;
    private int retryTick;

    public FlyingWanderGoal(AbstractFlyingSoldier flyingUnit) {
        this.flyingUnit = flyingUnit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (flyingUnit.getManualMoveTarget() != null
                || flyingUnit.isMoveOrderPriorityActive()
                || flyingUnit.isHardStationed()) return false;
        if (flyingUnit.getTarget() != null && flyingUnit.getTarget().isAlive()) return false;
        return activeAnchor() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        destination = null;
        anchorAtChoice = null;
        retryTick = 0;
    }

    @Override
    public void stop() {
        destination = null;
        anchorAtChoice = null;
        flyingUnit.stopFlyingMovement();
    }

    @Override
    public void tick() {
        Vec3 anchor = activeAnchor();
        if (anchor == null) return;

        boolean anchorMoved = anchorAtChoice == null
                || anchorAtChoice.distanceToSqr(anchor) > 16.0;
        boolean leftAnchorArea = flyingUnit.position().distanceToSqr(anchor) > 10.0 * 10.0;
        boolean tooFar = destination == null
                || flyingUnit.position().distanceToSqr(destination) > 12.0 * 12.0;
        if (destination == null || anchorMoved || leftAnchorArea || tooFar) {
            chooseDestination(anchor);
        }
        if (destination == null) {
            flyingUnit.stopFlyingMovement();
            return;
        }

        if (flyingUnit.position().distanceToSqr(destination) <= 0.8 * 0.8) {
            destination = null;
            retryTick = flyingUnit.tickCount + 30 + flyingUnit.getRandom().nextInt(40);
            flyingUnit.stopFlyingMovement();
            return;
        }
        flyingUnit.getLookControl().setLookAt(
                destination.x, destination.y, destination.z, 20.0f, 20.0f);
        flyingUnit.moveTowardPoint(destination);
    }

    @Nullable
    private Vec3 activeAnchor() {
        AbstractArmySoldier summoner = flyingUnit.getSummoner();
        if (summoner != null && summoner.isAlive()) {
            return flyingUnit.getHoverPointAbove(summoner.position());
        }
        if (flyingUnit.isSummonerGone() && !flyingUnit.hasGroup()) {
            return flyingUnit.getSummonRoamAnchor();
        }
        return null;
    }

    private void chooseDestination(Vec3 anchor) {
        if (flyingUnit.tickCount < retryTick) return;
        destination = flyingUnit.chooseWanderPoint(anchor);
        anchorAtChoice = anchor;
        if (destination == null) {
            retryTick = flyingUnit.tickCount + 20;
        }
    }
}
