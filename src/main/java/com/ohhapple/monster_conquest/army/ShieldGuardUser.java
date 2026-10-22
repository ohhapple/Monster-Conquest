package com.ohhapple.monster_conquest.army;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/**
 * 可举盾单位的共享状态接口。
 *
 * <p>实体本身只负责保存同步姿态和服务器计时；举盾条件、减伤属性和
 * 破盾恢复由 {@link ShieldGuardController} 统一处理。</p>
 */
public interface ShieldGuardUser {

    LivingEntity shieldGuardEntity();

    GuardStance getShieldGuardStance();

    void setShieldGuardStance(GuardStance stance);

    int getShieldGuardBraceTicks();

    void setShieldGuardBraceTicks(int ticks);

    int getShieldGuardRecoveryTicks();

    void setShieldGuardRecoveryTicks(int ticks);

    /** 攻击动作造成的短暂不举盾窗口（tick）；不是整个挥手动画。 */
    default int getShieldGuardAttackLockTicks() {
        return 0;
    }

    default void setShieldGuardAttackLockTicks(int ticks) {
    }

    default ItemStack shieldGuardStack() {
        return shieldGuardEntity().getOffhandItem();
    }

    default boolean hasShieldGuardStack() {
        return CombatRules.isShieldItem(shieldGuardStack());
    }

    /** 远程形态不举盾；近战单位默认允许。 */
    default boolean isShieldGuardRoleEligible() {
        return true;
    }

    default boolean hasShieldGuardTarget() {
        LivingEntity target = shieldGuardEntity() instanceof Mob mob ? mob.getTarget() : null;
        return target != null && target.isAlive();
    }

    /** 只在攻击动作发起的极短窗口内把盾牌让开，挥砍动画的其余时间仍保持举盾。 */
    default boolean isShieldGuardObstructed() {
        return getShieldGuardAttackLockTicks() > 0;
    }

    default double getShieldGuardChance() {
        var attribute = shieldGuardEntity().getAttribute(GuardAttributes.SHIELD_GUARD_CHANCE);
        return attribute == null ? 0.0D : Mth.clamp(attribute.getValue(), 0.0D, 1.0D);
    }

    default double getShieldGuardReduction() {
        var attribute = shieldGuardEntity().getAttribute(GuardAttributes.SHIELD_GUARD_REDUCTION);
        return attribute == null ? 0.0D : Mth.clamp(attribute.getValue(), 0.0D, 1.0D);
    }

    default double getShieldGuardLimit() {
        var attribute = shieldGuardEntity().getAttribute(GuardAttributes.SHIELD_GUARD_LIMIT);
        return attribute == null ? 0.0D : Math.max(0.0D, attribute.getValue());
    }

    default boolean isShieldGuardReadyForHit() {
        return hasShieldGuardStack()
                && getShieldGuardStance() == GuardStance.READY
                && !isShieldGuardObstructed();
    }

    /** 盾牌尚未完成第一次举盾准备时，需要优先维持防御。 */
    default boolean isShieldGuardBracePending() {
        GuardStance stance = getShieldGuardStance();
        return hasShieldGuardStack()
                && (stance == GuardStance.LOWERED || stance == GuardStance.BRACING);
    }

    default boolean isShieldGuardPoseVisible() {
        GuardStance stance = getShieldGuardStance();
        return (stance == GuardStance.BRACING || stance == GuardStance.READY)
                && !isShieldGuardObstructed();
    }
}
