package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 自走棋专属原版生物形态的公共属性。主世界不会生成这些实体，
 * 实际竞技数值仍由 ArenaStatCalculator 在战斗实体上覆盖。
 */
public abstract class ArenaCreatureSoldier extends AbstractArmySoldier {

    protected ArenaCreatureSoldier(EntityType<? extends PathfinderMob> type,
                                   Level level) {
        super(type, level);
    }

    @Override
    public double getMaxHealthForTier(EquipmentTier tier) {
        return 20.0;
    }

    @Override
    public double getArmorForTier(EquipmentTier tier) {
        return 0.0;
    }

    @Override
    public double getMeleeDamageForTier(EquipmentTier tier) {
        return 2.0;
    }

    @Override
    public float getRangedDamageForTier(EquipmentTier tier) {
        return 2.0f;
    }

    @Override
    public double getMovementSpeed() {
        return 0.3;
    }

    @Override
    public double getCavalryHorseArmorBonus() {
        return 0.0;
    }

    @Override
    public double getCavalryHorseSpeed() {
        return 0.0;
    }

    @Override
    public double getCavalryChargeSpeed() {
        return 0.0;
    }

    @Override
    public boolean canRide() {
        return false;
    }

    @Override
    protected boolean usesArmyEquipment() {
        return false;
    }

    @Override
    public int getXpReward() {
        return 0;
    }
}
