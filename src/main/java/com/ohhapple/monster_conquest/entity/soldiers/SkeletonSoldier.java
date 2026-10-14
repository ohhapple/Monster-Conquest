package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class SkeletonSoldier extends AbstractArmySoldier {
    public SkeletonSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.ARCHER_INFANTRY; }
    @Override public int getRenderIndex() { return 1; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 18.0;
            case TIER_1 -> 26.0;
            case TIER_2 -> 34.0;
            case TIER_3 -> 46.0;
            case TIER_4 -> 65.0;
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
            case TIER_0 -> 3.5f;
            case TIER_1 -> 5.0f;
            case TIER_2 -> 7.0f;
            case TIER_3 -> 9.5f;
            case TIER_4 -> 12.5f;
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

    @Override public int getXpReward() { return 5; }
}