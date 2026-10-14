package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.effect.ArmorBreakEffect;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 村民重锤兵：主手重锤（下界合金等级起附带破甲附魔），骑兵形态同样用重锤、不换骑枪；
 * 攻击间隔 20 tick（与默认近战一致）。命中时触发重锤落地冲击：
 * 粒子 + 落点音效 + 3.5 格范围伤害（按距离衰减）与击退，命中与范围伤害都会造成碎甲。
 */
public class VillagerMaceSoldier extends AbstractArmySoldier {

    /** 重锤落地冲击半径（格）。 */
    private static final double SMASH_RADIUS = 3.5;
    /** 落地冲击击退力度系数。 */
    private static final double SMASH_KNOCKBACK_POWER = 0.7;
    /** 落地粒子强度。 */
    private static final int SMASH_PARTICLES = 750;
    /** 近战攻击间隔（tick）：重锤挥击更慢（步兵 / 骑兵统一 20 tick = 1 秒）。 */
    private static final int MACE_ATTACK_INTERVAL = 20;
    /** 碎甲基础时长（tick）：TIER_0 = 10 秒，每级 +3 秒（与溺尸士兵一致）。 */
    private static final int ARMOR_BREAK_BASE_DURATION = 200;
    private static final int ARMOR_BREAK_DURATION_PER_TIER = 60;

    public VillagerMaceSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MELEE_INFANTRY; }
    @Override public int getRenderIndex() { return 15; }

    // ===================== 装备等级基础数值 =====================

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 24.0;
            case TIER_1 -> 34.0;
            case TIER_2 -> 44.0;
            case TIER_3 -> 56.0;
            case TIER_4 -> 72.0;
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

    @Override public double getMeleeDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 13.0;
            case TIER_1 -> 16.0;
            case TIER_2 -> 19.0;
            case TIER_3 -> 22.0;
            case TIER_4 -> 26.0;
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

    @Override public double getCavalryHorseSpeed()   { return 0.42; }
    @Override public double getMovementSpeed()       { return 0.30; }
    @Override public double getCavalryChargeSpeed()  { return 0.52; }

    @Override public int getXpReward() { return 8; }

    // ===================== 重锤 =====================

    /** 主手始终是重锤。 */
    @Override
    public Item getMeleeWeaponForTier(EquipmentTier tier) { return Items.MACE; }

    /** 重锤不换：落地冲击 / 碎甲都绑定在重锤上，换别的武器会破坏这套攻击逻辑。 */
    @Override
    public boolean allowsCustomWeapons() { return false; }

    /** 骑兵形态也不换骑枪：骑马同样挥舞重锤。 */
    @Override
    public boolean usesLanceInCavalry() { return false; }

    /** 重锤攻击间隔：与默认近战一致 20 tick（1 秒一次）。 */
    @Override
    public int getMeleeAttackIntervalTicks() { return MACE_ATTACK_INTERVAL; }

    /** 破甲附魔：等级 = 装备等级（TIER_0 无附魔，最高 4 级），不附加普通武器附魔。 */
    @Override
    protected ItemStack buildMeleeWeapon(ItemStack base, EquipmentTier tier,
                                         HolderLookup.RegistryLookup<Enchantment> enchants,
                                         RandomSource rand) {
        int level = tier.ordinal();
        if (level > 0) {
            base.enchant(enchants.getOrThrow(Enchantments.BREACH), Math.min(level, 4));
        }
        return base;
    }

    // ===================== 下落攻击（落地冲击） =====================

    /**
     * 命中触发原版重锤"从高处下落攻击"的落地效果：
     *   · 目标脚下：原版碎块 + 冲击环粒子（levelEvent 2013，强度与玩家重锤一致）
     *   · 落点音效：目标在地面 → MACE_SMASH_GROUND；目标在空中 → MACE_SMASH_AIR
     *   · 3.5 格内敌人：范围伤害（中心 100% → 边缘 40%）+ 原版击退
     *   · 命中本体与范围伤害都会造成碎甲
     */
    @Override
    protected void applyMeleeEffects(LivingEntity victim) {
        if (!(level() instanceof ServerLevel sl)) return;
        if (victim == null || !victim.isAlive()) return;

        // ===================== 粒子 / 音效 =====================
        // 目标骑在坐骑上时 rider.onGround() 恒为 false，只看骑手会漏掉"落地冲击"特效；
        //   这里同时看坐骑是否落地，保证打骑兵也有重锤下落攻击的粒子。
        Entity mount = victim.getVehicle();
        boolean grounded = victim.onGround() || (mount != null && mount.onGround());
        BlockPos impact = grounded && mount != null && !victim.onGround()
                ? mount.getOnPos() : victim.getOnPos();
        if (grounded) {
            sl.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK, impact, SMASH_PARTICLES);
            sl.playSound(null,
                    impact.getX() + 0.5, impact.getY() + 0.5, impact.getZ() + 0.5,
                    SoundEvents.MACE_SMASH_GROUND, getSoundSource(), 1.0f, 1.0f);
        } else {
            // 空中目标也补一层落地冲击粒子，避免"打骑兵 / 打空中目标完全没有特效"
            sl.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK, impact, SMASH_PARTICLES);
            sl.playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                    SoundEvents.MACE_SMASH_AIR, getSoundSource(), 1.0f, 1.0f);
        }

        // 命中本体：碎甲
        applyArmorBreak(victim);

        // ===================== 范围伤害 + 原版击退 =====================
        float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 center = victim.position();
        double radiusSqr = SMASH_RADIUS * SMASH_RADIUS;

        for (LivingEntity other : sl.getEntitiesOfClass(LivingEntity.class,
                victim.getBoundingBox().inflate(SMASH_RADIUS),
                e -> e.isAlive() && e != this && e != victim)) {
            if (!canAttack(other)) continue;

            double distSqr = center.distanceToSqr(other.position());
            if (distSqr > radiusSqr) continue;

            // 不穿墙：冲击波从命中点向外扩散，被墙挡住的目标不吃范围伤害 / 击退
            Vec3 waveFrom = center.add(0, victim.getBbHeight() * 0.5, 0);
            Vec3 waveTo = other.position().add(0, other.getBbHeight() * 0.5, 0);
            if (sl.clip(new ClipContext(waveFrom, waveTo,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }

            double dist = Math.sqrt(distSqr);

            // 距离衰减：中心 100% → 边缘 40%
            float damage = (float) (baseDamage * (1.0 - 0.6 * (dist / SMASH_RADIUS)));
            if (damage > 0.0f) {
                other.invulnerableTime = 0;
                other.hurt(meleeDamageSource(), damage);
                if (!level().isClientSide) ThreatDispatcher.propagate(other, this);
                // 范围伤害同样附带碎甲
                applyArmorBreak(other);
            }

            // 原版击退：power = (3.5 - 距离) × 0.7 × (1 - 击退抗性)，向上 0.7
            double power = (SMASH_RADIUS - dist) * SMASH_KNOCKBACK_POWER
                    * (1.0 - other.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            if (power > 0.0) {
                Vec3 dir = other.position().subtract(center);
                Vec3 push = dir.lengthSqr() < 1.0E-6
                        ? Vec3.ZERO : dir.normalize().scale(power);
                other.push(push.x, 0.7, push.z);
            }
        }
    }

    /** 碎甲：等级 / 时长随装备等级递增（与溺尸士兵一致）。 */
    private void applyArmorBreak(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel)) return;
        if (!victim.isAlive() || !canAttack(victim)) return;

        int tierIdx = getTier().ordinal();
        int amplifier = Math.min(tierIdx, ArmorBreakEffect.MAX_AMPLIFIER);
        int duration = ARMOR_BREAK_BASE_DURATION + tierIdx * ARMOR_BREAK_DURATION_PER_TIER;
        victim.addEffect(new MobEffectInstance(ModEffects.ARMOR_BREAK, duration, amplifier), this);
    }
}
