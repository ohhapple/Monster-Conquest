package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.projectile.ArmyArrow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class BoggedSoldier extends AbstractArmySoldier {
    public BoggedSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.ARCHER_INFANTRY; }
    @Override public int getRenderIndex() { return 7; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 16.0;
            case TIER_1 -> 24.0;
            case TIER_2 -> 32.0;
            case TIER_3 -> 44.0;
            case TIER_4 -> 62.0;
        };
    }

    @Override public double getArmorForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 0.0;
            case TIER_1 -> 1.0;
            case TIER_2 -> 2.0;
            case TIER_3 -> 3.0;
            case TIER_4 -> 4.0;
        };
    }

    @Override public double getMeleeDamageForTier(EquipmentTier tier) { return 2.0; }

    @Override public float getRangedDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 3.0f;
            case TIER_1 -> 4.5f;
            case TIER_2 -> 6.5f;
            case TIER_3 -> 9.0f;
            case TIER_4 -> 12.0f;
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

    @Override public double getCavalryHorseSpeed()   { return 0.42; }
    @Override public double getMovementSpeed()       { return 0.31; }
    @Override public double getCavalryChargeSpeed()  { return 0.45; }
    @Override public double getRangedRetreatSpeedModifier() { return 1.0; }

    @Override
    protected void applyArrowEffects(ArmyArrow arrow) {
        int tierIdx = getTier().ordinal();
        int durationTicks = (4 + tierIdx * 2) * 20;
        int amplifier = Math.min(tierIdx, 3);
        arrow.addEffect(new MobEffectInstance(MobEffects.POISON,
                durationTicks, amplifier));
    }
}