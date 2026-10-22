package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * 召唤者死亡后的队伍跟随。
 *   优先跟随仍在世的军队首领 / 玩家；首领也不在时跟随同小队存活成员的中心。
 */
public class FlyingGroupFollowGoal extends Goal {

    private static final double GROUP_SCAN_RADIUS = 64.0;
    private static final double STOP_DISTANCE = 5.0;

    private final AbstractFlyingSoldier flyingUnit;
    @Nullable private Vec3 targetPoint;
    private int refreshTick;

    public FlyingGroupFollowGoal(AbstractFlyingSoldier flyingUnit) {
        this.flyingUnit = flyingUnit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (flyingUnit.getManualMoveTarget() != null
                || flyingUnit.isMoveOrderPriorityActive()
                || flyingUnit.isHardStationed()) return false;
        if (flyingUnit.getTarget() != null && flyingUnit.getTarget().isAlive()) return false;
        if ((flyingUnit.isSummoned() && !flyingUnit.isSummonerGone())
                || !flyingUnit.hasGroup()) return false;
        targetPoint = findGroupPoint();
        refreshTick = flyingUnit.tickCount + 20;
        return targetPoint != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (flyingUnit.getManualMoveTarget() != null
                || flyingUnit.isMoveOrderPriorityActive()
                || flyingUnit.isHardStationed()) return false;
        if (flyingUnit.getTarget() != null && flyingUnit.getTarget().isAlive()) return false;
        if ((flyingUnit.isSummoned() && !flyingUnit.isSummonerGone())
                || !flyingUnit.hasGroup()) return false;
        refreshGroupPoint();
        return targetPoint != null;
    }

    @Override
    public void start() {
        targetPoint = findGroupPoint();
        refreshTick = flyingUnit.tickCount + 20;
    }

    @Override
    public void stop() {
        targetPoint = null;
        flyingUnit.stopFlyingMovement();
    }

    @Override
    public void tick() {
        refreshGroupPoint();
        if (targetPoint == null) return;
        double dx = flyingUnit.getX() - targetPoint.x;
        double dz = flyingUnit.getZ() - targetPoint.z;
        boolean arrivedHorizontally =
                dx * dx + dz * dz <= STOP_DISTANCE * STOP_DISTANCE;
        boolean arrivedVertically = Math.abs(flyingUnit.getY() - targetPoint.y) <= 0.75;
        if (arrivedHorizontally && arrivedVertically) {
            flyingUnit.stopFlyingMovement();
            flyingUnit.getLookControl().setLookAt(
                    targetPoint.x, targetPoint.y + 1.0, targetPoint.z, 20.0f, 20.0f);
            return;
        }
        flyingUnit.getLookControl().setLookAt(
                targetPoint.x, targetPoint.y + 1.0, targetPoint.z, 30.0f, 30.0f);
        flyingUnit.moveTowardPoint(targetPoint);
    }

    @Nullable
    private Vec3 findGroupPoint() {
        LivingEntity leader = flyingUnit.getLeaderEntity();
        if (leader != null && leader.isAlive()) {
            return flyingUnit.getHoverPointAbove(leader.position());
        }
        if (!(flyingUnit.level() instanceof ServerLevel level)) return null;

        List<AbstractArmySoldier> members = level.getEntitiesOfClass(
                AbstractArmySoldier.class,
                flyingUnit.getBoundingBox().inflate(GROUP_SCAN_RADIUS),
                other -> other != flyingUnit
                        && other.isAlive()
                        && !other.isRemoved()
                        && !other.isSummoned()
                        && sameGroup(other));
        if (members.isEmpty()) return null;

        Vec3 center = Vec3.ZERO;
        for (AbstractArmySoldier member : members) center = center.add(member.position());
        return flyingUnit.getHoverPointAbove(center.scale(1.0 / members.size()));
    }

    private void refreshGroupPoint() {
        LivingEntity leader = flyingUnit.getLeaderEntity();
        if (leader != null && leader.isAlive()) {
            targetPoint = flyingUnit.getHoverPointAbove(leader.position());
            refreshTick = flyingUnit.tickCount + 20;
            return;
        }
        if (flyingUnit.tickCount < refreshTick) return;
        targetPoint = findGroupPoint();
        refreshTick = flyingUnit.tickCount + 20;
    }

    private boolean sameGroup(AbstractArmySoldier other) {
        return other.getCommanderUuid().equals(flyingUnit.getCommanderUuid())
                && other.getSquadId() == flyingUnit.getSquadId();
    }
}
