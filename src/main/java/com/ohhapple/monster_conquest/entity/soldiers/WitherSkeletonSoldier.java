package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class WitherSkeletonSoldier extends AbstractArmySoldier {
    public WitherSkeletonSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.DUAL_INFANTRY; }
    @Override public int getRenderIndex() { return 5; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 26.0;
            case TIER_1 -> 38.0;
            case TIER_2 -> 52.0;
            case TIER_3 -> 70.0;
            case TIER_4 -> 98.0;
        };
    }

    @Override public double getArmorForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 1.0;
            case TIER_1 -> 2.0;
            case TIER_2 -> 3.0;
            case TIER_3 -> 4.0;
            case TIER_4 -> 5.0;
        };
    }

    @Override public double getMeleeDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 5.0;
            case TIER_1 -> 6.5;
            case TIER_2 -> 8.0;
            case TIER_3 -> 10.0;
            case TIER_4 -> 13.5;
        };
    }

    @Override public float getRangedDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 4.5f;
            case TIER_1 -> 6.0f;
            case TIER_2 -> 8.0f;
            case TIER_3 -> 10.5f;
            case TIER_4 -> 13.5f;
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

    /** 速度偏慢：高生命 + 凋零效果已经很强，移动速度下调。 */
    @Override public double getCavalryHorseSpeed()   { return 0.38; }
    @Override public double getMovementSpeed()       { return 0.29; }
    @Override public double getCavalryChargeSpeed()  { return 0.46; }

    @Override
    protected void applyMeleeEffects(LivingEntity victim) {
        int tierIdx = getTier().ordinal();
        int durationTicks = (3 + tierIdx * 2) * 20;
        int amplifier = Math.min(tierIdx, 2);
        victim.addEffect(new MobEffectInstance(MobEffects.WITHER,
                durationTicks, amplifier), this);
    }
}
