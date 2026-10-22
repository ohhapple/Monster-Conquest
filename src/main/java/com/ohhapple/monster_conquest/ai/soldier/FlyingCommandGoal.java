package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 玩家阵营飞行单位的位置命令执行器。
 *
 * <p>普通士兵由 FormationGoal 解释“前往 / 驻守 / 跟随 / 自由移动”，
 * 飞行单位不进入地面阵型，因此必须用空中目标和落点读取同一个
 * {@link PlayerCommandState.SquadState}。
 * 移动仍交给飞行导航 / 移动控制器，RTS、控制工具和命令轮盘共用这一入口。</p>
 */
public class FlyingCommandGoal extends Goal {

    private static final double FOLLOW_STOP_DISTANCE = 0.5;
    private static final double ARRIVE_DISTANCE = 1.0;

    private final AbstractFlyingSoldier flyingUnit;
    @Nullable private Vec3 targetPoint;
    @Nullable private PlayerCommandState.SquadState commandState;
    private int lastMoveOrderStamp = Integer.MIN_VALUE;
    private int lastCommandMoveOverrideStamp = Integer.MIN_VALUE;

    public FlyingCommandGoal(AbstractFlyingSoldier flyingUnit) {
        this.flyingUnit = flyingUnit;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        PlayerCommandState.SquadState command = currentCommandState();
        if (command == null) return false;
        boolean commandMove = applyCommandMoveOverride(command);
        if (flyingUnit.getManualMoveTarget() != null && !commandMove) return false;
        LivingEntity target = flyingUnit.getTarget();
        if (target != null && target.isAlive() && !commandMove) return false;
        if (flyingUnit.isUnderRecentAttack() && !commandMove) return false;
        this.commandState = command;
        return resolveTarget(command) != null;
    }

    @Override
    public boolean canContinueToUse() {
        PlayerCommandState.SquadState current = currentCommandState();
        if (current == null) return false;
        if (commandState != current) {
            commandState = current;
            targetPoint = resolveTarget(current);
            lastMoveOrderStamp = Integer.MIN_VALUE;
            lastCommandMoveOverrideStamp = Integer.MIN_VALUE;
        }
        boolean commandMove = applyCommandMoveOverride(commandState);
        if (flyingUnit.getManualMoveTarget() != null && !commandMove) return false;
        LivingEntity target = flyingUnit.getTarget();
        if (target != null && target.isAlive() && !commandMove) return false;
        if (flyingUnit.isUnderRecentAttack() && !commandMove) return false;
        return resolveTarget(commandState) != null;
    }

    @Override
    public void start() {
        targetPoint = commandState == null ? null : resolveTarget(commandState);
        lastMoveOrderStamp = Integer.MIN_VALUE;
        lastCommandMoveOverrideStamp = Integer.MIN_VALUE;
        flyingUnit.setAtFormationSlot(false);
    }

    @Override
    public void stop() {
        targetPoint = null;
        flyingUnit.stopFlyingMovement();
    }

    @Override
    public void tick() {
        PlayerCommandState.SquadState current = currentCommandState();
        if (current != null && current != commandState) {
            commandState = current;
            targetPoint = resolveTarget(current);
            lastMoveOrderStamp = Integer.MIN_VALUE;
            lastCommandMoveOverrideStamp = Integer.MIN_VALUE;
        }
        if (commandState == null) return;
        targetPoint = resolveTarget(commandState);
        if (targetPoint == null) return;

        PlayerCommandState.MovementMode movement = commandState.getMovement();
        boolean following = movement == PlayerCommandState.MovementMode.FOLLOW
                || movement == PlayerCommandState.MovementMode.SOFT_FOLLOW;
        if (commandState.getMoveOrderStamp() != lastMoveOrderStamp) {
            lastMoveOrderStamp = commandState.getMoveOrderStamp();
            flyingUnit.stopFlyingMovement();
        }

        boolean commandMove = commandState.isMoveOrderCommandActive(
                flyingUnit.level().getGameTime());
        if (!commandMove
                && commandState.getMovement() == PlayerCommandState.MovementMode.STATION
                && commandState.getFormationAnchor() == null) {
            flyingUnit.stopFlyingMovement();
            return;
        }

        flyingUnit.getLookControl().setLookAt(
                targetPoint.x, targetPoint.y + 0.5, targetPoint.z, 30.0f, 30.0f);
        boolean arrived;
        if (following) {
            double dx = flyingUnit.getX() - targetPoint.x;
            double dz = flyingUnit.getZ() - targetPoint.z;
            arrived = dx * dx + dz * dz <= FOLLOW_STOP_DISTANCE * FOLLOW_STOP_DISTANCE
                    && Math.abs(flyingUnit.getY() - targetPoint.y) <= 0.75;
        } else {
            arrived = flyingUnit.position().distanceToSqr(targetPoint)
                    <= ARRIVE_DISTANCE * ARRIVE_DISTANCE;
        }
        if (arrived) {
            flyingUnit.stopFlyingMovement();
            if (commandMove) flyingUnit.markMoveOrderPointReached();
            if (commandState.getMovement() == PlayerCommandState.MovementMode.STATION) {
                flyingUnit.setAtFormationSlot(true);
            }
            return;
        }

        flyingUnit.setAtFormationSlot(false);
        flyingUnit.moveTowardPoint(targetPoint);
    }

    @Nullable
    private PlayerCommandState.SquadState currentCommandState() {
        UUID ownerUuid = flyingUnit.getCommanderUuid().orElse(null);
        if (ownerUuid == null) return null;
        PlayerCommandState ownerState = PlayerArmyManager.getIfPresent(ownerUuid);
        return ownerState == null ? null
                : PlayerCommandState.stateForCommand(flyingUnit, ownerState);
    }

    /** 明确的移动命令优先于旧手动目标、普通目标和近期受击让位条件。 */
    private boolean applyCommandMoveOverride(PlayerCommandState.SquadState command) {
        boolean commandMove = command.isMoveOrderCommandActive(
                flyingUnit.level().getGameTime());
        if (!commandMove || command.getMoveOrderStamp() == lastCommandMoveOverrideStamp) {
            return commandMove;
        }
        lastCommandMoveOverrideStamp = command.getMoveOrderStamp();
        flyingUnit.cancelManualMoveOrder();
        flyingUnit.setTarget(null);
        flyingUnit.setLastHurtByMob(null);
        flyingUnit.clearPersonalHatred();
        flyingUnit.clearCommandedAttackTarget();
        flyingUnit.clearRoute();
        flyingUnit.clearMarchRoute();
        flyingUnit.clearReachProbeMemo();
        return true;
    }

    @Nullable
    private Vec3 resolveTarget(PlayerCommandState.SquadState state) {
        PlayerCommandState.MovementMode movement = state.getMovement();
        if (movement == PlayerCommandState.MovementMode.FREE) return null;

        if (movement == PlayerCommandState.MovementMode.FOLLOW
                || movement == PlayerCommandState.MovementMode.SOFT_FOLLOW) {
            return resolveFollowPoint(state.getFollowTargetUuid());
        }

        if (movement != PlayerCommandState.MovementMode.STATION) return null;
        Vec3 moveDest = state.getMoveOrderDest();
        Vec3 anchor = state.getFormationAnchor();
        if (anchor == null && moveDest != null) anchor = moveDest;
        return anchor == null ? null : flyingUnit.getStationFormationPoint(anchor);
    }

    @Nullable
    private Vec3 resolveFollowPoint(@Nullable UUID followUuid) {
        if (!(flyingUnit.level() instanceof ServerLevel level)) return null;
        if (followUuid != null) {
            Entity entity = level.getEntities().get(followUuid);
            if (entity instanceof LivingEntity living && living.isAlive()) {
                return flyingUnit.getPlayerFollowPoint(living, followUuid);
            }
        }
        ServerPlayer owner = playerOwner();
        return owner != null && owner.isAlive()
                ? flyingUnit.getPlayerFollowPoint(owner, owner.getUUID()) : null;
    }

    @Nullable
    private ServerPlayer playerOwner() {
        if (!(flyingUnit.level() instanceof ServerLevel level)) return null;
        UUID ownerUuid = flyingUnit.getCommanderUuid().orElse(null);
        return ownerUuid == null ? null
                : level.getServer().getPlayerList().getPlayer(ownerUuid);
    }
}
