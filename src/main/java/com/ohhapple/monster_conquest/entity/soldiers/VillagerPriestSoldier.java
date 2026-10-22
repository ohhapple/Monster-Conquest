package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.projectile.WitchPotionEntity;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 村民牧师士兵：AI 与女巫共用，效果不同 —— 治疗量更低、不带增益，
 * 治疗前先净化全部负面效果（含碎甲 / 重伤）并清空冻结 tick；
 * 对敌药水先传播威胁，再清除全部增益、造成直伤并施加中毒
 * （中毒时长 / 等级同女巫，直伤低于女巫 HARM）。
 */
public class VillagerPriestSoldier extends AbstractArmySoldier {

    /** 每个牧师同时维持的悦灵上限。 */
    private static final int MAX_SUMMONED_UNITS = 3;
    /** 补足一只悦灵后的间隔，60 tick = 3 秒。 */
    private static final int SUMMON_INTERVAL_TICKS = 60;

    public VillagerPriestSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.THROWER_INFANTRY; }
    @Override public int getRenderIndex() { return 17; }

    /** 子类可覆盖这些 getter 建立不同等级的牧师召唤节奏。 */
    public int getMaxSummonedUnits() { return MAX_SUMMONED_UNITS; }
    public int getSummonIntervalTicks() { return SUMMON_INTERVAL_TICKS; }

    // ===================== 装备等级基础数值（与女巫一致） =====================

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

    /** 治疗量：比女巫（1 + 等级）少一半。 */
    private static float healAmount(int tier) { return 0.5f + tier * 0.5f; }

    /** 对敌直伤：比女巫 HARM（4 + 等级）低。 */
    private static float harmAmount(int tier) { return 2.0f + tier * 0.5f; }

    /** 中毒持续时间 / 等级：与女巫完全一致。 */
    private static int poisonDuration(int tier)  { return 100 + tier * 40; }
    private static int poisonAmplifier(int tier) { return Math.max(0, tier - 2); }

    // ============================================================
    // 三个入口（与 WitchSoldier 一一对应，由 WitchPotionEntity 按投掷者分派）
    // ============================================================

    /**
     * 牧师对自己：净化负面效果 + 回血（比女巫少），不带任何增益 buff。
     */
    public static void applyToSelf(AbstractArmySoldier priest) {
        int tier = priest.getTier().ordinal();
        // 先净化再回血：否则重伤（禁疗）会把这次治疗直接削减掉。
        cleanseHarmfulEffects(priest);
        priest.heal(healAmount(tier));
    }

    /**
     * 药水命中队友：只对 HEAL 药瓶生效——净化负面效果 + 回血，不带增益 buff。
     */
    public static void applyToAlly(LivingEntity target,
                                   int tier,
                                   WitchPotionEntity.Kind kind,
                                   @Nullable Entity effectSource) {
        if (kind != WitchPotionEntity.Kind.HEAL) return;
        cleanseHarmfulEffects(target);
        target.heal(healAmount(tier));
    }

    /**
     * 药水命中敌人：只投掷中毒药瓶，命中时附带一次低于女巫 HARM 的直伤，
     * 负面效果只有中毒（等级 / 持续时间与女巫一致），并传播威胁。
     */
    public static void applyToEnemy(LivingEntity target,
                                    int tier,
                                    WitchPotionEntity.Kind kind,
                                    @Nullable Entity effectSource,
                                    @Nullable Entity damageOwner) {
        if (kind == WitchPotionEntity.Kind.HEAL) return;

        // 与女巫一致：药水命中即视为"牧师发起了攻击"，先让目标及其军队记住威胁。
        if (damageOwner instanceof LivingEntity priest
                && !target.level().isClientSide
                && target.isAlive()) {
            ThreatDispatcher.propagate(target, priest);
        }

        // 威胁建立后再清除增益，抗性 / 吸收等 buff 不能保护本次直伤。
        cleanseBeneficialEffects(target);

        if (kind != WitchPotionEntity.Kind.POISON) return;

        // 直伤低于女巫 HARM
        if (damageOwner != null) {
            target.hurt(target.damageSources().indirectMagic(effectSource, damageOwner),
                    harmAmount(tier));
        }
        if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(
                            MobEffects.POISON,
                            poisonDuration(tier),
                            poisonAmplifier(tier)),
                    effectSource);
        }
    }

    /** 清除目标身上的全部增益效果，保留中性 / 负面效果。 */
    public static void cleanseBeneficialEffects(LivingEntity target) {
        if (target.level().isClientSide) return;

        List<Holder<MobEffect>> beneficial = new ArrayList<>();
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                beneficial.add(instance.getEffect());
            }
        }
        for (Holder<MobEffect> effect : beneficial) {
            target.removeEffect(effect);
        }
    }

    /**
     * 净化目标身上的所有负面效果（MobEffectCategory.HARMFUL）。
     *
     * 按分类清除而不是写死列表：原版的中毒 / 虚弱 / 缓慢……
     *   以及本模组的碎甲（armor_break）、重伤（grievous_wound）等自定义 debuff
     *   都会被一并清除。
     */
    public static void cleanseHarmfulEffects(LivingEntity target) {
        if (target.level().isClientSide) return;

        List<Holder<MobEffect>> harmful = new ArrayList<>();
        for (MobEffectInstance instance : target.getActiveEffects()) {
            if (instance.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                harmful.add(instance.getEffect());
            }
        }
        for (Holder<MobEffect> effect : harmful) {
            target.removeEffect(effect);
        }

        // 冰冻不是 MobEffect：单独清空冻结 tick，
        //   结霜 / 发抖 / 冻结减速 / 后续的冻结伤害一并解除。
        if (target.getTicksFrozen() > 0) target.setTicksFrozen(0);
    }
}
