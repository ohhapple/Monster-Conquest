package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class ZombieSoldier extends AbstractArmySoldier {
    public ZombieSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MELEE_INFANTRY; }
    @Override public int getRenderIndex() { return 0; }

    // ===================== 生命 / 伤害 =====================
    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 20.0;
            case TIER_1 -> 30.0;
            case TIER_2 -> 40.0;
            case TIER_3 -> 55.0;
            case TIER_4 -> 80.0;
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
            case TIER_0 -> 3.0;
            case TIER_1 -> 4.5;
            case TIER_2 -> 6.0;
            case TIER_3 -> 8.0;
            case TIER_4 -> 11.0;
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

    // ===================== 速度 =====================
    @Override public double getCavalryHorseSpeed()   { return 0.50; }
    @Override public double getMovementSpeed()       { return 0.29; }   // 步兵
    @Override public double getCavalryChargeSpeed()  { return 0.55; }   // 马·冲锋

    @Override public int getXpReward() { return 5; }
}
