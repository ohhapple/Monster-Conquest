package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.world.entity.LivingEntity;

/**
 * 近战垂直攻击范围。
 *
 * <p>普通单位仍受同层高度限制；小体型飞行单位悬停在其他单位头顶时，
 * 攻击方可以按双方碰撞箱高度抬手攻击，但不能因此隔楼层命中。</p>
 */
public final class MeleeReach {

    private MeleeReach() {}

    public static boolean canReachVertically(LivingEntity attacker,
                                             LivingEntity target,
                                             double normalLimit) {
        return canReachVertically(attacker, attacker.getY(), target, normalLimit);
    }

    public static boolean canReachVertically(LivingEntity attacker,
                                             double attackerY,
                                             LivingEntity target,
                                             double normalLimit) {
        double limit = target instanceof AbstractFlyingSoldier
                ? Math.max(normalLimit,
                        attacker.getBbHeight() + target.getBbHeight() + 0.5)
                : normalLimit;
        return Math.abs(attackerY - target.getY()) <= limit;
    }
}
