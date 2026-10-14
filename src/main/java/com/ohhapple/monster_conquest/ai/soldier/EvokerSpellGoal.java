package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.soldiers.EvokerSoldier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * 唤魔者士兵的施法 Goal：与目标保持中距离，目标在 {@link EvokerSoldier#FANG_CAST_RANGE}
 * 内且有视线与冷却完毕时，经短暂前摇在目标脚下召唤尖牙；硬驻守时原地施法。
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

    /** 走位寻路的节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissueMove(net.minecraft.world.entity.Mob mover) {
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
        LivingEntity target = evoker.getTarget();
        if (target == null || !target.isAlive()) return;

        evoker.getLookControl().setLookAt(target, 30.0f, 30.0f);

        // ==================== 施法前摇 ====================
        if (castingTicks > 0) {
            stopMove();
            lookAt(target);
            if (--castingTicks == 0) {
                if (evoker.castFangsAtTarget(target)) {
                    cooldown = FANG_COOLDOWN_TICKS;
                    evoker.markAttackSuccess();
                    evoker.swing(InteractionHand.MAIN_HAND, true);
                } else {
                    // 地形不合适 → 短暂重试，避免刷屏
                    cooldown = 20;
                }
            }
            return;
        }

        if (cooldown > 0) cooldown--;

        // ==================== 走位 ====================
        boolean hardStation = evoker.isHardStationed();
        double dist = evoker.distanceTo(target);

        Mob mover = evoker.getVehicle() instanceof Mob mount ? mount : evoker;

        // 用"身体视线"而不是原版眼睛视线：隔着 1 格矮墙虽然"看得见"，
        //   但尖牙会从墙里冒出来，所以这里也要求身体中心之间没有方块遮挡；
        //   被挡住时会主动靠近（不会原地反复施法失败）。
        boolean hasSight = evoker.hasBodyLineOfSight(target);

        if (hardStation) {
            mover.getNavigation().stop();
        } else if (dist < MIN_DIST) {
            Vec3 away = evoker.position().subtract(target.position()).normalize();
            Vec3 dest = evoker.position().add(away.scale(5.0));
            if (shouldReissueMove(mover)) {
                mover.getNavigation().moveTo(dest.x, dest.y, dest.z, RETREAT_SPEED);
            }
        } else if (dist > MAX_DIST || !hasSight) {
            // 看不见目标（被墙挡住）→ 走完整绕路规划，而不是直着往上贴
            if (!evoker.replanRouteTo(target)) {
                if (shouldReissueMove(mover)) mover.getNavigation().moveTo(target, APPROACH_SPEED);
            }
        } else {
            mover.getNavigation().stop();
        }

        // ==================== 开火 ====================
        // 目标 <20 格内 → 直接在目标脚下召唤尖牙
        if (cooldown <= 0 && dist < CAST_RANGE && hasSight) {
            castingTicks = CAST_WARMUP;
            stopMove();
            lookAt(target);
        }
    }

    private void lookAt(LivingEntity target) {
        evoker.getLookControl().setLookAt(target, 30.0f, 30.0f);
        if (evoker.getVehicle() instanceof Mob mount) {
            mount.getLookControl().setLookAt(target, 30.0f, 30.0f);
        }
    }

    private void stopMove() {
        if (evoker.getVehicle() instanceof Mob mount) mount.getNavigation().stop();
        else evoker.getNavigation().stop();
    }
}
