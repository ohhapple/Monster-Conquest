package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.GuardStance;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 猪灵盾兵：主手剑、副手盾，战斗中以维持举盾为优先，攻击频率很低。
 * 生命值低于一半后进一步放慢出手，让盾牌有更长的保持时间。
 */
public class PiglinShieldSoldier extends AbstractArmySoldier {

    /** 常态近战攻击间隔（5 秒）。 */
    private static final int NORMAL_ATTACK_INTERVAL = 100;
    /** 半血以下的近战攻击间隔（8 秒）。 */
    private static final int WOUNDED_ATTACK_INTERVAL = 160;
    private static final float WOUNDED_HEALTH_RATIO = 0.5f;

    public PiglinShieldSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MELEE_INFANTRY; }
    @Override public int getRenderIndex() { return 18; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 30.0;
            case TIER_1 -> 42.0;
            case TIER_2 -> 54.0;
            case TIER_3 -> 68.0;
            case TIER_4 -> 86.0;
        };
    }

    @Override public double getArmorForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 3.0;
            case TIER_1 -> 4.0;
            case TIER_2 -> 6.0;
            case TIER_3 -> 8.0;
            case TIER_4 -> 10.0;
        };
    }

    @Override public double getMeleeDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 4.0;
            case TIER_1 -> 5.5;
            case TIER_2 -> 6.0;
            case TIER_3 -> 7.0;
            case TIER_4 -> 8.0;
        };
    }

    @Override public double getCavalryHorseArmorBonus() {
        return switch (getTier()) {
            case TIER_0 -> 0.0;
            case TIER_1 -> 2.0;
            case TIER_2 -> 4.0;
            case TIER_3 -> 6.0;
            case TIER_4 -> 8.0;
        };
    }

    @Override public double getMovementSpeed() { return 0.30; }
    @Override public double getCavalryHorseSpeed() { return 0.45; }
    @Override public double getCavalryChargeSpeed() { return 0.48; }
    @Override public int getXpReward() { return 9; }

    /** 发现目标后先等盾牌进入 READY，再发起第一次攻击。 */
    @Override public boolean prefersShieldGuardBeforeAttack() { return true; }

    /** 骑乘时仍使用剑盾，不切换骑枪。 */
    @Override public boolean usesLanceInCavalry() { return false; }

    @Override
    public int getMeleeAttackIntervalTicks() {
        // 破盾后盾牌强制收起，不再维持长攻击间隔，改用普通近战节奏。
        if (getShieldGuardStance() == GuardStance.RECOVERING || !hasShieldGuardStack()) {
            return super.getMeleeAttackIntervalTicks();
        }
        return getHealth() < getMaxHealth() * WOUNDED_HEALTH_RATIO
                ? WOUNDED_ATTACK_INTERVAL
                : NORMAL_ATTACK_INTERVAL;
    }
}
