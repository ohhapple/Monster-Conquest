package com.ohhapple.monster_conquest.ai.commander;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CommanderCombatGoal extends Goal {

    /** 首领远程 / 近战的武器切换距离。卡住判定等外部逻辑也复用这些值。 */
    public static final double RANGED_THRESHOLD = 8.0;
    public static final double MELEE_RANGE = 3.0;
    /** 近战允许的最大高度差（格）：墙头 / 高台上的目标砍不到。 */
    public static final double MAX_MELEE_Y_DIFF = 1.5;
    /** 首领弓箭的最大射程（格）。 */
    public static final double RANGED_MAX_RANGE = 32.0;
    private static final int MELEE_INTERVAL = 10;
    private static final int RANGED_INTERVAL = 25;

    private static final double RETREAT_SAFE_DIST = 22.0;
    private static final double RETREAT_FLEE_DIST = 15.0;
    private static final double RETREAT_FIRE_MAX = 30.0;

    private final AbstractArmyCommander commander;
    private int cooldown = 0;
    /** 追击 / 撤退寻路节流：避免每 tick 重算路径（打不到的目标尤其费）。 */
    private int pathCooldown = 0;

    public CommanderCombatGoal(AbstractArmyCommander commander) {
        this.commander = commander;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** 寻路节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissuePath(net.minecraft.world.entity.Mob mover) {
        if (pathCooldown > 0) {
            pathCooldown--;
            return false;
        }
        pathCooldown = mover.getNavigation().isDone() ? 5 : 10;
        return true;
    }

    @Override
    public boolean canUse() {
        // 有手动行军目标时不进战斗，让 CommanderMarchGoal 接管
        if (commander.getManualMarchTarget() != null) return false;
        LivingEntity t = commander.getCombatTarget();
        return t != null && t.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() { cooldown = 0; }

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

        commander.getLookControl().setLookAt(target, 30.0f, 30.0f);

        // 唯一移动逻辑：靠近目标（骑乘首领的移动由 controlMount 处理）
        //   不再调用 stopAllMovement —— 那会和 controlMount 抢控制权
        //   正在按绕路路线追击时，移动交给路线跟随，避免半路路径覆盖路线
        if (!commander.isFollowingRoute()) {
            if (!mounted) {
                if (horizDist > MELEE_RANGE) {
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
            if (--cooldown <= 0) {
                commander.performRangedAttack(target, 1.0f);
                cooldown = RANGED_INTERVAL;
            }
        }

        // 近战：骑马时伤害由骑枪穿刺（LancePierce）结算，与近战士兵一致；
        //   避免骑马近战贴脸互推、和敌人重叠。
        if (!mounted && horizDist < MELEE_RANGE
                && Math.abs(commander.getY() - target.getY()) <= MAX_MELEE_Y_DIFF
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

        if (horizDist < RETREAT_SAFE_DIST) {
            Vec3 away = commander.position().subtract(target.position());
            if (away.lengthSqr() > 0.001) {
                Vec3 dir = away.normalize();
                Vec3 dest = commander.position().add(dir.scale(RETREAT_FLEE_DIST));
                // 逃跑倍率从首领实体读取（默认 1.0 = 不额外加速，子类可覆盖）
                if (shouldReissuePath(mover)) {
                    mover.getNavigation().moveTo(dest.x, dest.y, dest.z,
                            commander.getRetreatSpeedModifier());
                }
            }
        } else {
            mover.getNavigation().stop();
        }

        if (horizDist < RETREAT_FIRE_MAX && --cooldown <= 0) {
            commander.performRangedAttack(target, 1.0f);
            cooldown = RANGED_INTERVAL;
        }
    }

    @Override
    public void stop() { stopAllMovement(commander.isPassenger()); }

    private void stopAllMovement(boolean mounted) {
        if (mounted && commander.getVehicle() instanceof Mob horse) {
            horse.getNavigation().stop();
            Vec3 dm = horse.getDeltaMovement();
            horse.setDeltaMovement(dm.x * 0.5, dm.y, dm.z * 0.5);
        } else {
            commander.getNavigation().stop();
            Vec3 dm = commander.getDeltaMovement();
            commander.setDeltaMovement(dm.x * 0.5, dm.y, dm.z * 0.5);
        }
    }

    private static double horizontalDist(LivingEntity a, LivingEntity b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
