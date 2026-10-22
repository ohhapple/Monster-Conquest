package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

/**
 * 村民牧师士兵召唤的悦灵士兵。
 *   与恼鬼共享召唤物生命周期和阵营逻辑，但保持方块碰撞，不能穿墙。
 */
public class AllayFlyingSoldier extends AbstractFlyingSoldier {

    public AllayFlyingSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.FLYING; }
    @Override public boolean hasFixedTier() { return true; }
    @Override public int getRenderIndex() { return 20; }
    @Override public double getMaxHealthForTier(EquipmentTier tier) { return 20.0; }
    @Override public double getArmorForTier(EquipmentTier tier) { return 10.0; }
    @Override public double getMeleeDamageForTier(EquipmentTier tier) { return 6.0; }
    @Override public double getMovementSpeed() { return 0.66; }
    @Override public int getMeleeAttackIntervalTicks() { return 20; }
    @Override public int getXpReward() { return 3; }

    @Override
    protected ItemStack createFlyingMainHandItem() {
        return new ItemStack(Items.DIAMOND_SWORD);
    }

    @Override public double getFlyingAttackReach() { return 2; }

    @Override protected boolean usesArmyEquipment() { return false; }
    @Override public boolean allowsCustomWeapons() { return false; }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ALLAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ALLAY_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4f;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0f;
    }
}
