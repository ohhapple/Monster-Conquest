package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.damage.ModDamageTypes;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

/**
 * 卫道士士兵（近战兵，灾厄阵营）。
 *
 *   · 主手：斧子（等级越高品质越好：木 → 石 → 铁 → 钻石 → 下界合金）
 *   · 副手：阵营旗盾牌（格挡概率 / 图案与其他士兵完全一致）
 *   · 骑兵形态不拿骑枪，仍然只拿斧子 + 盾牌
 *   · 攻击破盾：使用 {@code monster_conquest:shield_break} 伤害类型
 *     （该类型在数据包里加入 {@code minecraft:bypasses_shield}），
 *     无视敌人（士兵 / 首领 / 玩家）的一切盾牌格挡逻辑。
 */
public class VindicatorSoldier extends AbstractArmySoldier {

    public VindicatorSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MELEE_INFANTRY; }
    @Override public int getRenderIndex() { return 13; }

    // ===================== 装备等级基础数值 =====================

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 26.0;
            case TIER_1 -> 36.0;
            case TIER_2 -> 46.0;
            case TIER_3 -> 60.0;
            case TIER_4 -> 78.0;
        };
    }

    /** 与其他近战兵同一档护甲，不额外堆高。 */
    @Override public double getArmorForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 1.0;
            case TIER_1 -> 2.0;
            case TIER_2 -> 3.0;
            case TIER_3 -> 4.0;
            case TIER_4 -> 5.0;
        };
    }

    /** 斧子比同级剑更重：伤害更高。 */
    @Override public double getMeleeDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 5.0;
            case TIER_1 -> 7.0;
            case TIER_2 -> 9.5;
            case TIER_3 -> 12.0;
            case TIER_4 -> 15.0;
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

    @Override public double getCavalryHorseSpeed()   { return 0.45; }
    @Override public double getMovementSpeed()       { return 0.36; }
    @Override public double getCavalryChargeSpeed()  { return 0.55; }

    @Override public int getXpReward() { return 10; }

    // ===================== 武器 =====================

    /** 主手斧子：等级越高品质越好。 */
    @Override
    public Item getMeleeWeaponForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> Items.WOODEN_AXE;
            case TIER_1 -> Items.STONE_AXE;
            case TIER_2 -> Items.IRON_AXE;
            case TIER_3 -> Items.DIAMOND_AXE;
            case TIER_4 -> Items.NETHERITE_AXE;
        };
    }

    /** 骑兵形态不拿骑枪：骑马也只用斧子。 */
    @Override
    public boolean usesLanceInCavalry() { return false; }

    /** 近战 / 骑兵冲锋统一使用破盾伤害类型。 */
    @Override
    protected DamageSource meleeDamageSource() {
        return ModDamageTypes.shieldBreak(this);
    }

    // ===================== 破盾攻击 =====================

    /**
     * 破盾攻击：与普通近战相同的判定流程，仅伤害来源换成
     *   {@link ModDamageTypes#SHIELD_BREAK}——该类型带有 bypasses_shield 标签，
     *   玩家举盾、士兵 / 首领的自定义盾牌格挡都无法减免本次伤害。
     */
    @Override
    public boolean doHurtTarget(Entity target) {
        if (isAttackStopped()) return false;
        if (!(target instanceof LivingEntity living)) return super.doHurtTarget(target);

        DamageSource source = meleeDamageSource();
        float damage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (this.level() instanceof ServerLevel serverLevel) {
            damage = EnchantmentHelper.modifyDamage(
                    serverLevel, this.getWeaponItem(), target, source, damage);
        }

        boolean hit = living.hurt(source, damage);
        if (hit) {
            float knockback = this.getKnockback(target, source);
            if (knockback > 0.0f) {
                living.knockback(knockback * 0.5,
                        Mth.sin(this.getYRot() * ((float) Math.PI / 180.0f)),
                        -Mth.cos(this.getYRot() * ((float) Math.PI / 180.0f)));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            }
            if (this.level() instanceof ServerLevel serverLevel) {
                EnchantmentHelper.doPostAttackEffects(serverLevel, target, source);
            }
            this.setLastHurtMob(target);
            this.playAttackSound();
            applyMeleeEffects(living);
            if (!level().isClientSide) {
                ThreatDispatcher.propagate(living, this);
            }
        }
        return hit;
    }
}
