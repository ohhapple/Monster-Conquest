package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.entity.soldiers.EvokerSoldier;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 唤魔者士兵的施法 Goal：与目标保持中距离，目标在 {@link EvokerSoldier#FANG_CAST_RANGE}
 * 内且有视线与冷却完毕时，经短暂前摇在目标脚下召唤尖牙；飞行目标则在预判高度召唤。
 * 硬驻守时原地施法。
 */
public class EvokerSpellGoal extends Goal {

    /** 召唤尖牙的技能冷却（tick）。40 tick = 2 秒。 */
    public static final int FANG_COOLDOWN_TICKS = 40;

    /** 太近 → 后撤的距离（格）。 */
    private static final double MIN_DIST = 6.0;
    /** 太远 → 靠近的距离（格）。 */
    private static final double MAX_DIST = 16.0;
    /** 施法距离上限（格）：目标在 20 格内即可直接在脚下召唤尖牙。 */
    private static final double CAST_RANGE = EvokerSoldier.FANG_CAST_RANGE;
    /** 施法前摇（tick）。 */
    private static final int CAST_WARMUP = 10;
    /** 后撤速度倍率。 */
    private static final double RETREAT_SPEED = 1.0;
    /** 靠近速度倍率。 */
    private static final double APPROACH_SPEED = 1.0;

    private final EvokerSoldier evoker;
    private int cooldown = 0;
    private int castingTicks = 0;
    /* 走位寻路节流：避免每 tick 重算路径（对着打不到的目标尤其费）。 */
    /** -1 = 还没用过：第一次调用先随机错开，避免整队同一 tick 一起跑寻路。 */
    private int moveCooldown = -1;

    public EvokerSpellGoal(EvokerSoldier evoker) {
        this.evoker = evoker;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // 施法前摇和冷却都按 tick 计数，隔 tick 执行会让停顿时间翻倍。
        return true;
    }

    /** 走位寻路的节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissueMove(Mob mover) {
        if (moveCooldown < 0) {
            moveCooldown = evoker.getRandom().nextInt(10);
        }
        if (moveCooldown > 0) {
            moveCooldown--;
            return false;
        }
        moveCooldown = mover.getNavigation().isDone() ? 5 : 10;
        return true;
    }

    @Override
    public boolean canUse() {
        if (!evoker.getRole().isMage()) return false;
        if (evoker.isAttackStopped()) return false;
        // 前往命令期间不施法、不抢占移动。
        if (evoker.isMoveOrderPriorityActive()) return false;
        LivingEntity target = evoker.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        this.castingTicks = 0;
        this.moveCooldown = -1;
    }

    @Override
    public void stop() {
        stopMove();
        this.castingTicks = 0;
    }

    @Override
    public void tick() {
        if (evoker.isMoveOrderPriorityActive()) return;
        LivingEntity target = evoker.getTarget();
        if (target == null || !target.isAlive()) return;

        evoker.getLookControl().setLookAt(target, 30.0f, 30.0f);

        // ==================== 施法前摇 ====================
        if (castingTicks > 0) {
            // 目标仍在移动时继续修正站位，只保留转向；施法不应强行冻结整个单位。
            updateCombatPosition(target);
            lookAt(target);
            if (--castingTicks == 0) {
                if (evoker.castFangsAtTarget(target)) {
                    evoker.setShieldGuardAttackLockTicks(
                            ShieldGuardController.ATTACK_LOCK_TICKS);
                    cooldown = evoker.isArenaUnit()
                            ? evoker.getArenaRangedAttackCooldownTicks()
                            : FANG_COOLDOWN_TICKS;
                    if (evoker.isArenaUnit()) {
                        evoker.arenaEquipment().beforeBasicAttack();
                    }
                    evoker.markAttackSuccess();
                    evoker.swing(InteractionHand.MAIN_HAND, true);
                } else {
                    // 地形不合适 → 短暂重试，避免刷屏
                    cooldown = 20;
                }
            }
            return;
        }

        if (evoker.isArenaUnit()) {
            int interval = evoker.getArenaRangedAttackCooldownTicks();
            if (cooldown > interval) cooldown = interval;
        }
        if (cooldown > 0) cooldown--;

        // ==================== 走位 ====================
        double dist = evoker.distanceTo(target);

        // 用"身体视线"而不是原版眼睛视线：隔着 1 格矮墙虽然"看得见"，
        //   但尖牙会从墙里冒出来，所以这里也要求身体中心之间没有方块遮挡；
        //   被挡住时会主动靠近（不会原地反复施法失败）。
        boolean hasSight = evoker.hasBodyLineOfSight(target);

        updateCombatPosition(target);

        // ==================== 开火 ====================
        // 目标 <20 格内 → 直接在目标脚下召唤尖牙
        if (cooldown <= 0 && dist < CAST_RANGE && hasSight) {
            castingTicks = CAST_WARMUP;
            lookAt(target);
        }
    }

    /** 走位只受距离、视线和目标是否仍在移动影响，不因准备攻击而长期锁住停步。 */
    private void updateCombatPosition(LivingEntity target) {
        boolean hardStation = evoker.isHardStationed();
        double dist = evoker.distanceTo(target);
        Mob mover = evoker.getVehicle() instanceof Mob mount ? mount : evoker;
        boolean hasSight = evoker.hasBodyLineOfSight(target);

        if (hardStation) {
            stopMove();
        } else if (dist < MIN_DIST) {
            Vec3 away = evoker.position().subtract(target.position()).normalize();
            Vec3 dest = evoker.position().add(away.scale(5.0));
            if (shouldReissueMove(mover)) {
                mover.getNavigation().moveTo(dest.x, dest.y, dest.z, RETREAT_SPEED);
            }
        } else if (dist > MAX_DIST || !hasSight) {
            // 主世界可追加方块级绕路；竞技场只有原版导航所有权，
            // 不能安装竞技 tick 不会推进的自定义路线。
            boolean routed = !evoker.isArenaUnit()
                    && evoker.replanRouteTo(target);
            if (!routed && shouldReissueMove(mover)) {
                mover.getNavigation().moveTo(target, APPROACH_SPEED);
            }
        } else if (AiNavUtil.isMovingHorizontally(target)) {
            Vec3 dest = movingHoldPoint(target);
            if (shouldReissueMove(mover)) {
                mover.getNavigation().moveTo(dest.x, dest.y, dest.z, APPROACH_SPEED);
            }
        } else {
            stopMove();
        }
    }

    /** 目标移动时维持中距离，而不是停在原地等它跑出施法范围。 */
    private Vec3 movingHoldPoint(LivingEntity target) {
        Vec3 away = evoker.position().subtract(target.position());
        double horizontal = Math.sqrt(away.x * away.x + away.z * away.z);
        if (horizontal < 1.0E-4) {
            away = new Vec3(
                    -Math.sin(target.getYRot() * ((float) Math.PI / 180f)),
                    0.0,
                    Math.cos(target.getYRot() * ((float) Math.PI / 180f)));
            horizontal = 1.0;
        }
        double preferred = (MIN_DIST + MAX_DIST) * 0.5;
        return new Vec3(
                target.getX() + away.x / horizontal * preferred,
                evoker.getY(),
                target.getZ() + away.z / horizontal * preferred);
    }

    private void lookAt(LivingEntity target) {
        evoker.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (evoker.getVehicle() instanceof Mob mount) {
            mount.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
    }

    private void stopMove() {
        Mob mover = evoker.getVehicle() instanceof Mob mount ? mount : evoker;
        AiNavUtil.haltMovement(mover);
        if (mover != evoker) {
            AiNavUtil.stopMovementInput(evoker);
        }
    }
}
