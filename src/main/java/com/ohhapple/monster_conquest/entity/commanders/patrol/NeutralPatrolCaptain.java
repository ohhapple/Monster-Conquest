package com.ohhapple.monster_conquest.entity.commanders.patrol;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public class NeutralPatrolCaptain extends AbstractPatrolCaptain {
    public NeutralPatrolCaptain(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public ResourceLocation getDefaultFactionId() { return FactionIds.NEUTRAL; }

    // ===================== 基础数值 =====================
    @Override public double getCommanderMaxHealth()           { return 60.0; }
    @Override public double getCommanderAttackDamage()        { return 11.5; }
    @Override public double getCommanderArmor()               { return 2.0; }
    @Override public double getCommanderKnockbackResistance() { return 0.3; }
    @Override public float  getCommanderRangedDamage()        { return 2.5f; }
    @Override public EquipmentTier getCommanderTier()         { return EquipmentTier.TIER_4; }

    // ===================== 速度 =====================
    @Override public double getCommanderHorseSpeed()    { return 0.40; }
    @Override public double getCommanderMovementSpeed() { return 0.33; }
    @Override public double getCommanderChargeSpeed()   { return 0.48; }

    // ===================== 战马 =====================
    @Override public double getCommanderHorseMaxHealth()  { return 60.0; }
    @Override public Item   getCommanderHorseArmor()      { return Items.GOLDEN_HORSE_ARMOR; }
    @Override public double getCommanderHorseArmorBonus() { return 4.0; }
}
