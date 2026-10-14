package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 士兵远程/双形态攻击 Goal。
 *
 * 齐射不依赖 getTarget()，所以齐射结束时 getTarget() 可能为 null。
 *   canUse() 在目标为空时主动从玩家军队共享仇恨列表里找最近的活敌人
 *   并立即设为目标，齐射结束后无缝继续射击，不用等索敌 Goal 的扫描周期。
 */
public class SoldierRangedAttackGoal extends Goal {

    private static final float DUAL_LOW_HP_THRESHOLD = 0.5f;
    private static final int REPOSITION_TICKS = 10;

    /** 回退目标扫描半径（格）。 */
    private static final double FALLBACK_SCAN_RANGE = 16.0;
    /** 回退目标扫描节流（tick）。 */
    private static final int FALLBACK_SCAN_COOLDOWN = 10;

    private final AbstractArmySoldier soldier;
    private int cooldown = 0;
    private int repositionTicks = 0;
    /** 走位寻路节流：目标没变时不必每 tick 重算路径（打不到的目标也一样）。 */
    /** -1 = 还没用过：第一次调用先随机错开，避免整队同一 tick 一起跑寻路。 */
    private int moveCooldown = -1;

    /** 走位寻路的节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissueMove(Mob mover) {
        if (moveCooldown < 0) {
            moveCooldown = soldier.getRandom().nextInt(10);
        }
        if (moveCooldown > 0) {
            moveCooldown--;
            return false;
        }
        moveCooldown = mover.getNavigation().isDone() ? 5 : 10;
        return true;
    }

    /** 回退目标扫描冷却。 */
    private int fallbackScanCooldown = 0;

    /** 连续开火失败（被墙 / 掩体挡住）次数：累计到阈值 → 重新规划绕路。 */
    private int failedShots = 0;
    private static final int ROUTE_AFTER_FAILED_SHOTS = 3;
    /** 重新规划路线后保持寻路的时间（tick），期间不被"站位停止"覆盖。 */
    private static final int ROUTE_TICKS = 40;

    public SoldierRangedAttackGoal(AbstractArmySoldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (isVolleyMode()) return true;

        SoldierRole r = soldier.getRole();
        if (r.isMage()) return false;
        if (r.isThrower()) return false;
        if (r.isMelee()) return false;

        LivingEntity target = soldier.getTarget();

        // 齐射刚结束 / 目标刚死 → 从共享仇恨列表里主动挑目标。
        if (target == null || !target.isAlive()) {
            LivingEntity fallback = findFallbackTarget();
            if (fallback == null) return false;
            soldier.setTarget(fallback);
            target = fallback;
        }

        if (r.isDual()) {
            if (isDualLowHealth()) return true;
            return soldier.distanceTo(target) > 5.0;
        }
        return r.isArcher() || r.isGunner();
    }

    @Override
    public boolean canContinueToUse() {
        if (isVolleyMode()) return true;

        SoldierRole r = soldier.getRole();
        if (r.isMage()) return false;
        if (r.isThrower()) return false;
        if (r.isMelee()) return false;
        LivingEntity target = soldier.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (r.isDual()) {
            if (isDualLowHealth()) return true;
            return soldier.distanceTo(target) > 4.0;
        }
        return r.isArcher() || r.isGunner();
    }

    @Override
    public void start() {
        this.cooldown = 0;
        this.repositionTicks = 0;
        this.failedShots = 0;
        this.moveCooldown = -1;
    }

    private boolean isVolleyMode() {
        if (!soldier.isVolleyActiveNow()) return false;
        SoldierRole r = soldier.getRole();
        return r.isArcher() || r.isDual() || r.isGunner();
    }

    @Override
    public void tick() {

        if (cooldown > 0) cooldown--;
        if (repositionTicks > 0) repositionTicks--;

        // ==================== 齐射 ====================
        if (isVolleyMode()) {
            Player owner = soldier.getCommanderPlayer();
            var cmdState = owner != null
                    ? PlayerArmyManager.getIfPresent(owner.getUUID()) : null;
            Vec3 vt = cmdState != null
                    ? com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(soldier, cmdState).getVolleyTarget()
                    : null;
            if (vt != null) {
                Mob mover = soldier.getVehicle() instanceof Mob h ? h : soldier;
                mover.getNavigation().stop();

                double dx = vt.x - soldier.getX();
                double dy = vt.y - soldier.getEyeY();
                double dz = vt.z - soldier.getZ();
                double horiz = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

                soldier.setYRot(yaw);
                soldier.yRotO = yaw;
                soldier.yBodyRot = yaw;
                soldier.yBodyRotO = yaw;
                soldier.setYHeadRot(yaw);
                soldier.yHeadRotO = yaw;
                soldier.setXRot(pitch);
                soldier.xRotO = pitch;

                if (cooldown <= 0) {
                    soldier.performVolleyShot(vt);
                    swingAfterShot();
                    cooldown = soldier.getRangedAttackCooldownTicks();
                }
                return;
            }
        }

        // ==================== 普通射击 ====================
        LivingEntity target = soldier.getTarget();
        if (target == null) return;

        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (soldier.getVehicle() instanceof Mob horse) {
            horse.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }

        boolean mounted = soldier.getSoldierType().isCavalry();
        double min = soldier.getMinAttackRange(mounted);
        double max = soldier.getMaxAttackRange(mounted);
        // 理想站位距离（默认 = 最大射程；霰弹枪兵更近）
        double hold = soldier.getPreferredAttackRange(mounted);
        double dist = soldier.distanceTo(target);

        Mob mover = soldier.getVehicle() instanceof Mob h ? h : soldier;

        // 硬驻守：不移动，直接尝试开火。
        //   能否命中交给 tryPerformRangedAttack 判断
        //   （弓箭走 BallisticAim，枪械走 GunAim + 显式射程闸门）。
        if (soldier.isHardStationed()) {
            mover.getNavigation().stop();
            if (cooldown <= 0) {
                boolean fired = soldier.tryPerformRangedAttack(target);
                if (fired) {
                    swingAfterShot();
                    cooldown = soldier.getRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                }
            }
            return;
        }

        boolean isDual = soldier.getRole().isDual();
        boolean highHpDual = isDual && !isDualLowHealth();

        // 撤退倍率从士兵实体读取（默认 1.0 = 与自身 / 坐骑速度一致，子类可覆盖）
        double retreatMod = soldier.getRangedRetreatSpeedModifier();

        if (highHpDual) {
            if (repositionTicks > 0) {
                // 保持导航路径
            } else if (dist > 4.5) {
                // 主动靠近：原值 1.0；节流重发，避免每 tick 重算路径
                if (shouldReissueMove(mover)) mover.getNavigation().moveTo(target, 1.0D);
            } else {
                mover.getNavigation().stop();
            }

            if (cooldown <= 0 && dist > 4.5) {
                boolean fired = soldier.tryPerformRangedAttack(target);
                if (fired) {
                    failedShots = 0;
                    swingAfterShot();
                    cooldown = soldier.getRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                    onShotFailed(mover, target);
                }
            }
        } else {
            if (repositionTicks > 0) {
                // reposition 期间不覆盖 navigation
            } else if (dist < min) {
                // 被近身 → 拉距离（使用士兵实体的可覆盖倍率）
                Vec3 away = soldier.position().subtract(target.position()).normalize();
                Vec3 dest = soldier.position().add(away.scale(6.0));
                if (shouldReissueMove(mover)) {
                    mover.getNavigation().moveTo(dest.x, dest.y, dest.z, retreatMod);
                }
            } else if (dist > hold) {
                // 主动靠近到"理想站位距离"（默认最大射程，霰弹枪兵为近身距离）
                if (shouldReissueMove(mover)) mover.getNavigation().moveTo(target, 1.0D);
            } else {
                mover.getNavigation().stop();
            }

            // 目标超出最大射程时只靠近、不开火：
            //   否则每次失败都会用 1 格微移覆盖整段寻路，导致原地反复重算路径
            if (cooldown <= 0 && dist <= max + 1.0) {
                boolean fired = soldier.tryPerformRangedAttack(target);
                if (fired) {
                    failedShots = 0;
                    swingAfterShot();
                    cooldown = soldier.getRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                    onShotFailed(mover, target);
                }
            }
        }
    }

    /** 枪械射击不播放挥臂动画；只有弓 / 投掷等武器才挥动手臂。 */
    private void swingAfterShot() {
        if (!soldier.getRole().isGunner()) {
            soldier.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * 开火失败处理：直线被挡（墙 / 掩体）时立即重新规划完整绕路路线；
     *   否则先小幅挪位继续尝试，连续失败同样转入绕路。绕路期间保持路线不被打断。
     */
    private void onShotFailed(Mob mover, LivingEntity target) {
        if (!soldier.hasClearShot(target) || ++failedShots >= ROUTE_AFTER_FAILED_SHOTS) {
            failedShots = 0;
            // 只有真的规划出路线才进入"保持路线"状态；否则退回小幅挪位，避免站着不动
            if (soldier.replanRouteTo(target)) {
                repositionTicks = ROUTE_TICKS;
            } else {
                repositionTicks = REPOSITION_TICKS;
                tryReposition(mover);
            }
        } else {
            repositionTicks = REPOSITION_TICKS;
            tryReposition(mover);
        }
    }

    private void tryReposition(Mob mover) {
        LivingEntity target = soldier.getTarget();
        if (target == null) return;
        // 小幅挪位同样要节流：每次最多 3 次寻路，不能每 tick 都来一遍
        if (!shouldReissueMove(mover)) return;

        if (mover instanceof WarHorseEntity horse) {
            horse.setCharging(false);
            horse.setPassThroughTicks(0);
        }

        double dx = target.getX() - soldier.getX();
        double dz = target.getZ() - soldier.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.5) return;

        // 挪位一小步：原值 1.0
        double nx = soldier.getX() + dx / horiz * 1.0;
        double nz = soldier.getZ() + dz / horiz * 1.0;
        if (mover.getNavigation().moveTo(nx, soldier.getY(), nz, 1.0D)) return;

        double perpX = -dz / horiz;
        double perpZ = dx / horiz;
        for (int sign : new int[]{1, -1}) {
            double sx = soldier.getX() + perpX * sign * 1.0;
            double sz = soldier.getZ() + perpZ * sign * 1.0;
            if (mover.getNavigation().moveTo(sx, soldier.getY(), sz, 1.0D)) return;
        }
    }

    private boolean isDualLowHealth() {
        return soldier.getHealth() < soldier.getMaxHealth() * DUAL_LOW_HP_THRESHOLD;
    }

    @Override
    public void stop() {
        if (soldier.getVehicle() instanceof Mob h) h.getNavigation().stop();
        else soldier.getNavigation().stop();
        repositionTicks = 0;
    }

    // ===================== 回退目标搜索 =====================

    /**
     * 从玩家军队共享仇恨列表里找最近的活敌人。
     *
     *   · 只对玩家军队士兵生效（NPC 军队走 AttackConfiguredEnemyGoal + 首领 hatredMap）
     *   · 10 tick 节流 + 16 格扫描半径 → 开销可忽略
     *   · 只查 sharedHatredArmies / sharedHatredEntities（玩家军队当前仇恨的敌人）
     */
    @Nullable
    private LivingEntity findFallbackTarget() {
        if (fallbackScanCooldown > 0) {
            fallbackScanCooldown--;
            return null;
        }
        fallbackScanCooldown = FALLBACK_SCAN_COOLDOWN;

        if (!soldier.isPlayerArmy()) return null;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return null;
        var st = PlayerArmyManager.getIfPresent(owner.getUUID());
        if (st == null) return null;

        long nowGT = soldier.level().getGameTime();

        LivingEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity e : soldier.level().getEntitiesOfClass(
                LivingEntity.class,
                soldier.getBoundingBox().inflate(FALLBACK_SCAN_RANGE),
                e -> e.isAlive() && e != soldier && e.canBeHitByProjectile())) {

            if (!soldier.canAttack(e)) continue;

            UUID theirArmy = AbstractArmySoldier.resolveArmyUuid(e, soldier.level());
            boolean isEnemy = false;
            if (theirArmy != null && st.isSharedHatredArmy(theirArmy, nowGT)) {
                isEnemy = true;
            } else if (st.isSharedHatredEntity(e.getUUID(), nowGT)) {
                isEnemy = true;
            }
            if (!isEnemy) continue;

            double d = soldier.distanceToSqr(e);
            if (d < bestDistSqr) {
                bestDistSqr = d;
                best = e;
            }
        }
        return best;
    }
}
