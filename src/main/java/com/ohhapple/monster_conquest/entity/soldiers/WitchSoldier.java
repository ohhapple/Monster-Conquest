package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.projectile.WitchPotionEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 女巫士兵：药水效果、数值与持续时间的唯一定义处。
 * 三个入口：applyToSelf（自己）/ applyToAlly（队友）/ applyToEnemy（敌人）。
 */
public class WitchSoldier extends AbstractArmySoldier {

    public WitchSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.THROWER_INFANTRY; }
    @Override public int getRenderIndex() { return 8; }

    // ===================== 装备等级基础数值 =====================

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 26.0;
            case TIER_1 -> 34.0;
            case TIER_2 -> 40.0;
            case TIER_3 -> 46.0;
            case TIER_4 -> 52.0;
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

    @Override public double getCavalryHorseArmorBonus() {
        return switch (getTier()) {
            case TIER_0 -> 0.0;
            case TIER_1 -> 2.0;
            case TIER_2 -> 4.0;
            case TIER_3 -> 6.0;
            case TIER_4 -> 8.0;
        };
    }

    @Override public double getMeleeDamageForTier(EquipmentTier tier) { return 2.0; }

    @Override public double getCavalryHorseSpeed()   { return 0.40; }
    @Override public double getMovementSpeed()       { return 0.31; }
    @Override public double getCavalryChargeSpeed()  { return 0.45; }

    @Override public int getXpReward() { return 8; }

    // ============================================================
    // 数值表：想改数值 / 持续时间 / 等级，只改这一段
    // ============================================================

    // —— 治疗 ——
    private static float healAmount(int tier)              { return 1.0f + tier; }
    private static int   healRegenDuration(int tier)       { return 60 + tier * 20; }
    private static int   healRegenAmplifier(int tier)      { return Math.min(tier - 1, 1); }
    private static int   healAbsorptionDuration(int tier)  { return 200; }
    private static int   healAbsorptionAmplifier(int tier) { return tier - 2; }

    // —— 毒 ——
    private static int   poisonDuration(int tier)          { return 100 + tier * 40; }
    private static int   poisonAmplifier(int tier)         { return Math.max(0, tier - 2); }
    private static int   poisonSlowDuration(int tier)      { return 100; }
    private static int   poisonSlowAmplifier(int tier)     { return Math.min(tier - 1, 2); }

    // —— 直接伤害 ——
    private static float harmAmount(int tier)              { return 4.0f + tier; }

    // —— 虚弱 ——
    private static int   weakenDuration(int tier)          { return 120 + tier * 40; }
    private static int   weakenAmplifier(int tier)         { return Math.max(0, tier - 4); }
    private static int   weakenDigSlowDuration(int tier)   { return weakenDuration(tier); }
    private static int   weakenDigSlowAmplifier(int tier)  { return Math.min(tier - 1, 2); }

    // ============================================================
    // 三个入口：想改"给什么效果"就改这一段
    // ============================================================

    /**
     * 女巫对自己。
     * 效果源 = 自己。
     */
    public static void applyToSelf(AbstractArmySoldier witch) {
        int tier = witch.getTier().ordinal();

        // 直接回血
        witch.heal(healAmount(tier));

        // tier >= 2 → 附带再生
        if (tier >= 2) {
            witch.addEffect(new MobEffectInstance(
                            MobEffects.REGENERATION,
                            healRegenDuration(tier),
                            healRegenAmplifier(tier)),
                    witch);
        }
        // tier >= 3 → 附带吸收
        if (tier >= 3) {
            witch.addEffect(new MobEffectInstance(
                            MobEffects.ABSORPTION,
                            healAbsorptionDuration(tier),
                            healAbsorptionAmplifier(tier)),
                    witch);
        }
    }

    /**
     * 药水命中队友。
     */
    public static void applyToAlly(LivingEntity target,
                                   int tier,
                                   WitchPotionEntity.Kind kind,
                                   @Nullable Entity effectSource) {
        switch (kind) {
            case HEAL -> {
                target.heal(healAmount(tier));
                if (tier >= 2) {
                    target.addEffect(new MobEffectInstance(
                                    MobEffects.REGENERATION,
                                    healRegenDuration(tier),
                                    healRegenAmplifier(tier)),
                            effectSource);
                }
                if (tier >= 3) {
                    target.addEffect(new MobEffectInstance(
                                    MobEffects.ABSORPTION,
                                    healAbsorptionDuration(tier),
                                    healAbsorptionAmplifier(tier)),
                            effectSource);
                }
            }
            case POISON, HARM, WEAKEN -> { }
        }
    }

    /**
     * 药水命中敌人。
     *
     * 除 HEAL 外的药水命中敌人都传播威胁（让目标把女巫所在军队视为敌人）；
     *   HEAL 命中敌人无事发生。
     */
    public static void applyToEnemy(LivingEntity target,
                                    int tier,
                                    WitchPotionEntity.Kind kind,
                                    @Nullable Entity effectSource,
                                    @Nullable Entity damageOwner) {
        // 所有对敌人有效的药水都传播威胁
        if (kind != WitchPotionEntity.Kind.HEAL
                && damageOwner instanceof LivingEntity witch
                && !target.level().isClientSide
                && target.isAlive()) {
            ThreatDispatcher.propagate(target, witch);
        }

        switch (kind) {
            case POISON -> {
                target.addEffect(new MobEffectInstance(
                                MobEffects.POISON,
                                poisonDuration(tier),
                                poisonAmplifier(tier)),
                        effectSource);
                if (tier >= 2) {
                    target.addEffect(new MobEffectInstance(
                                    MobEffects.MOVEMENT_SLOWDOWN,
                                    poisonSlowDuration(tier),
                                    poisonSlowAmplifier(tier)),
                            effectSource);
                }
            }
            case HARM -> {
                target.hurt(target.damageSources().indirectMagic(effectSource, damageOwner),
                        harmAmount(tier));
            }
            case WEAKEN -> {
                target.addEffect(new MobEffectInstance(
                                MobEffects.WEAKNESS,
                                weakenDuration(tier),
                                weakenAmplifier(tier)),
                        effectSource);
                if (tier >= 2) {
                    target.addEffect(new MobEffectInstance(
                                    MobEffects.DIG_SLOWDOWN,
                                    weakenDigSlowDuration(tier),
                                    weakenDigSlowAmplifier(tier)),
                            effectSource);
                }
            }
            case HEAL -> { }
        }
    }
}
