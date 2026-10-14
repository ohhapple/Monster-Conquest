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

public class MonsterCommander extends AbstractArmyCommander {
    public MonsterCommander(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public ResourceLocation getDefaultFactionId() { return FactionIds.MONSTER; }

    // ===================== 基础数值 =====================
    @Override public double getCommanderMaxHealth()           { return 140.0; }
    @Override public double getCommanderAttackDamage()        { return 9.0; }
    @Override public double getCommanderArmor()               { return 4.0; }
    @Override public double getCommanderKnockbackResistance() { return 0.5; }
    @Override public float  getCommanderRangedDamage()        { return 3.0f; }
    @Override public double getCommanderFollowRange()         { return 128.0; }
    @Override public int    getCommanderXpReward()            { return 50; }
    @Override public EquipmentTier getCommanderTier()         { return EquipmentTier.TIER_4; }

    // ===================== 速度 =====================
    @Override public double getCommanderHorseSpeed()    { return 0.40; }
    @Override public double getCommanderMovementSpeed() { return 0.32; }
    @Override public double getCommanderChargeSpeed()   { return 0.50; }

    // ===================== 战马 =====================
    @Override public double getCommanderHorseMaxHealth() { return 140.0; }
    @Override public Item   getCommanderHorseArmor()     { return Items.IRON_HORSE_ARMOR; }
    @Override public double getCommanderHorseArmorBonus() { return 8.0; }
}
