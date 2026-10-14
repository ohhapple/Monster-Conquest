package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class HuskSoldier extends AbstractArmySoldier {

    /** 饥饿基础时长（tick）：TIER_0 = 200 tick = 10 秒，每级 +60 tick（与溺尸士兵的破甲一致）。 */
    private static final int HUNGER_BASE_DURATION = 200;
    private static final int HUNGER_DURATION_PER_TIER = 60;

    public HuskSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MELEE_INFANTRY; }
    @Override public int getRenderIndex() { return 2; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 22.0;
            case TIER_1 -> 32.0;
            case TIER_2 -> 44.0;
            case TIER_3 -> 60.0;
            case TIER_4 -> 86.0;
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
            case TIER_0 -> 3.5;
            case TIER_1 -> 5.0;
            case TIER_2 -> 6.5;
            case TIER_3 -> 8.5;
            case TIER_4 -> 12.0;
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

    @Override public double getCavalryHorseSpeed()   { return 0.50; }
    @Override public double getMovementSpeed()       { return 0.29; }
    @Override public double getCavalryChargeSpeed()  { return 0.55; }

    @Override public int getXpReward() { return 5; }

    /** 命中造成饥饿：等级 = 装备等级，时长与溺尸士兵的破甲同规则。 */
    @Override
    protected void applyMeleeEffects(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel)) return;
        if (!victim.isAlive() || !canAttack(victim)) return;

        int tierIdx = getTier().ordinal();
        int duration = HUNGER_BASE_DURATION + tierIdx * HUNGER_DURATION_PER_TIER;
        victim.addEffect(new MobEffectInstance(MobEffects.HUNGER, duration, tierIdx), this);
    }
}
