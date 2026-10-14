package com.ohhapple.monster_conquest.entity.commanders;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class VillagerCommander extends AbstractArmyCommander {
    public VillagerCommander(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public ResourceLocation getDefaultFactionId() { return FactionIds.VILLAGER; }

    // ===================== 基础数值 =====================
    @Override public double getCommanderMaxHealth()           { return 150.0; }
    @Override public double getCommanderAttackDamage()        { return 7.0; }
    @Override public double getCommanderArmor()               { return 5.0; }
    @Override public double getCommanderKnockbackResistance() { return 0.6; }
    @Override public float  getCommanderRangedDamage()        { return 3.0f; }
    @Override public double getCommanderFollowRange()         { return 128.0; }
    @Override public int    getCommanderXpReward()            { return 50; }
    @Override public EquipmentTier getCommanderTier()         { return EquipmentTier.TIER_4; }

    // ===================== 速度 =====================
    @Override public double getCommanderHorseSpeed()    { return 0.38; }
    @Override public double getCommanderMovementSpeed() { return 0.31; }
    @Override public double getCommanderChargeSpeed()   { return 0.48; }

    // ===================== 战马 =====================
    @Override public double getCommanderHorseMaxHealth() { return 150.0; }
    @Override public Item   getCommanderHorseArmor()     { return Items.GOLDEN_HORSE_ARMOR; }
    @Override public double getCommanderHorseArmorBonus() { return 8.0; }
}
