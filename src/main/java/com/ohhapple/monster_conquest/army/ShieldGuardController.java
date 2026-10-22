package com.ohhapple.monster_conquest.army;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

/**
 * 举盾单位的公共状态推进。
 *
 * <p>这里只推进姿态和冷却，真正的伤害结算由
 * {@code ShieldGuardEvents} 统一处理。</p>
 */
public final class ShieldGuardController {

    /** 从发现目标到来得及格挡之间的最短准备时间。 */
    public static final int BRACE_TICKS = 5;
    /** 近战 / 骑枪发起攻击动作时短暂放盾的 tick 数。 */
    public static final int ATTACK_LOCK_TICKS = 10;
    /** 被可破盾武器击中后失去举盾能力的时间。 */
    public static final int BREAK_RECOVERY_TICKS = 100;

    private ShieldGuardController() {}

    public static void tick(ShieldGuardUser user) {
        LivingEntity entity = user.shieldGuardEntity();
        if (entity.level().isClientSide || !entity.isAlive()) return;

        int attackLock = user.getShieldGuardAttackLockTicks();
        if (attackLock > 0) {
            user.setShieldGuardAttackLockTicks(attackLock - 1);
        }

        int recovery = user.getShieldGuardRecoveryTicks();
        if (recovery > 0) {
            user.setShieldGuardRecoveryTicks(recovery - 1);
            user.setShieldGuardStance(GuardStance.RECOVERING);
            user.setShieldGuardBraceTicks(0);
        }

        boolean wantsGuard = user.hasShieldGuardStack()
                && user.isShieldGuardRoleEligible()
                && user.hasShieldGuardTarget();

        if (user.getShieldGuardRecoveryTicks() > 0) {
            user.setShieldGuardStance(GuardStance.RECOVERING);
            stopShieldGuardItem(entity);
            return;
        }

        if (!wantsGuard) {
            user.setShieldGuardStance(GuardStance.LOWERED);
            user.setShieldGuardBraceTicks(0);
            stopShieldGuardItem(entity);
            return;
        }

        switch (user.getShieldGuardStance()) {
            case LOWERED, RECOVERING -> startBrace(user);
            case BRACING -> advanceBrace(user);
            case READY -> {
                // 持续有目标时保持 READY；挥砍只会暂时隐藏姿态，不取消状态。
            }
        }

        if (user.getShieldGuardStance() == GuardStance.BRACING
                || user.getShieldGuardStance() == GuardStance.READY) {
            entity.startUsingItem(InteractionHand.OFF_HAND);
        }
    }

    private static void startBrace(ShieldGuardUser user) {
        user.setShieldGuardStance(GuardStance.BRACING);
        user.setShieldGuardBraceTicks(BRACE_TICKS);
    }

    private static void advanceBrace(ShieldGuardUser user) {
        int remaining = user.getShieldGuardBraceTicks() - 1;
        if (remaining <= 0) {
            user.setShieldGuardBraceTicks(0);
            user.setShieldGuardStance(GuardStance.READY);
        } else {
            user.setShieldGuardBraceTicks(remaining);
        }
    }

    public static boolean canBlock(ShieldGuardUser user, DamageSource source) {
        return user.isShieldGuardReadyForHit()
                && CombatRules.isCombatDamage(source)
                && !CombatRules.bypassesShieldBlock(source);
    }

    /** 格挡成功后播放反馈；斧类等可破盾武器会让单位进入恢复阶段。 */
    public static void onBlocked(ShieldGuardUser user, DamageSource source) {
        LivingEntity entity = user.shieldGuardEntity();
        entity.level().playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                SoundEvents.SHIELD_BLOCK, entity.getSoundSource(),
                1.0F, 0.8F + entity.getRandom().nextFloat() * 0.4F);

        if (source.getDirectEntity() instanceof LivingEntity attacker
                && attacker.canDisableShield()) {
            disableShield(user, BREAK_RECOVERY_TICKS);
        }
    }

    /** 强制让盾牌进入恢复状态；用于破盾攻击命中。 */
    public static void disableShield(ShieldGuardUser user, int ticks) {
        LivingEntity entity = user.shieldGuardEntity();
        user.setShieldGuardRecoveryTicks(Math.max(
                user.getShieldGuardRecoveryTicks(), Math.max(0, ticks)));
        user.setShieldGuardBraceTicks(0);
        user.setShieldGuardStance(GuardStance.RECOVERING);
        stopShieldGuardItem(entity);
    }

    /** 存档读取或换形态时清掉运行期姿态。 */
    public static void reset(ShieldGuardUser user) {
        user.setShieldGuardStance(GuardStance.LOWERED);
        user.setShieldGuardBraceTicks(0);
        user.setShieldGuardRecoveryTicks(0);
        user.setShieldGuardAttackLockTicks(0);
        stopShieldGuardItem(user.shieldGuardEntity());
    }

    private static void stopShieldGuardItem(LivingEntity entity) {
        if (entity.isUsingItem() && entity.getUsedItemHand() == InteractionHand.OFF_HAND) {
            entity.stopUsingItem();
        }
    }
}
