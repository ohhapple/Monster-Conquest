package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.effect.ArmorBreakEffect;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.projectile.ArmyTrident;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;

public class DrownedSoldier extends AbstractArmySoldier {

    /**
     * 破甲基础时长（tick）：TIER_0 = 200 tick = 10 秒。
     *   每提升一级，时长 +60 tick（3 秒）：
     *     TIER_0 → 200 (10s)
     *     TIER_1 → 260 (13s)
     *     TIER_2 → 320 (16s)
     *     TIER_3 → 380 (19s)
     *     TIER_4 → 440 (22s)
     */
    private static final int ARMOR_BREAK_BASE_DURATION = 200;
    private static final int ARMOR_BREAK_DURATION_PER_TIER = 60;

    /** 破甲基础等级（amplifier）。 */
    private static final int ARMOR_BREAK_BASE_AMPLIFIER = 0;

    public DrownedSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // 水域也是"正常路线"（和原版溺尸一致）：不再背默认的 8 点水域寻路惩罚，
        //   它的寻路会直接规划穿过水面 / 湖面的路线，而不是绕远路或者在岸边停住。
        this.setPathfindingMalus(PathType.WATER, 0.0F);
    }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.DUAL_INFANTRY; }
    @Override public int getRenderIndex() { return 3; }

    @Override protected boolean usesTridentForDual() { return true; }

    /**
     * 溺尸士兵骑兵形态手持三叉戟（不是骑枪）：
     *   走"无骑枪骑兵"通道 → 骑马时正常挥砍 / 冲锋劈砍，
     *   攻速与步兵一致（不再出现骑兵形态完全打不到人的问题）。
     */
    @Override public boolean usesLanceInCavalry() { return false; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 22.0;
            case TIER_1 -> 34.0;
            case TIER_2 -> 46.0;
            case TIER_3 -> 62.0;
            case TIER_4 -> 88.0;
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
            case TIER_0 -> 4.0;
            case TIER_1 -> 5.5;
            case TIER_2 -> 7.0;
            case TIER_3 -> 9.0;
            case TIER_4 -> 12.0;
        };
    }

    @Override public float getRangedDamageForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 7.0f;
            case TIER_1 -> 8.5f;
            case TIER_2 -> 10.0f;
            case TIER_3 -> 12.0f;
            case TIER_4 -> 14.0f;
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

    @Override public double getCavalryHorseSpeed()   { return 0.38; }
    @Override public double getMovementSpeed()       { return 0.29; }
    @Override public double getCavalryChargeSpeed()  { return 0.48; }

    /**
     * 游泳标记（水里移动 / 姿势的总开关）：
     *   · 服务端：脚下那格是水（= 走 travelDrowned 这套水里移动）就是游泳状态；
     *   · 客户端：不做判断 —— 原版默认实现会在客户端把标记清掉，
     *     那样模型里的划水动作（游泳姿势）就永远出不来。
     */
    @Override
    public void updateSwimming() {
        if (this.level().isClientSide) return;
        boolean inWaterBlock = !this.isPassenger()
                && this.level().getBlockState(this.blockPosition()).getFluidState().is(FluidTags.WATER);
        this.setSwimming(inWaterBlock);
    }

    /** 让模型进入游泳姿势：原版溺尸就是覆写这一行（返回 isSwimming()）。 */
    @Override
    public boolean isVisuallySwimming() { return this.isSwimming(); }

    @Override
    protected Projectile createProjectile() {
        ArmyTrident trident = new ArmyTrident(
                com.ohhapple.monster_conquest.entity.ModEntities.ARMY_TRIDENT.get(),
                this.level());
        trident.setOwner(this);
        trident.setPos(this.getX(), this.getEyeY() - 0.1, this.getZ());
        trident.setOnHitCallback(this::applyArmorBreak);
        return trident;
    }

    @Override protected double getProjectileSpeed() { return 3.0; }

    @Override protected SoundEvent getShootSound() {
        return SoundEvents.TRIDENT_THROW.value();
    }

    @Override
    protected void applyMeleeEffects(LivingEntity victim) {
        applyArmorBreak(victim);
    }

    private void applyArmorBreak(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel)) return;
        if (!victim.isAlive()) return;
        if (!canAttack(victim)) return;

        int tierIdx = getTier().ordinal();

        int amplifier = ARMOR_BREAK_BASE_AMPLIFIER + tierIdx;
        if (amplifier > ArmorBreakEffect.MAX_AMPLIFIER) {
            amplifier = ArmorBreakEffect.MAX_AMPLIFIER;
        }

        // 时长随等级递增：TIER_0 = 10s，每级 +3s
        int duration = ARMOR_BREAK_BASE_DURATION + tierIdx * ARMOR_BREAK_DURATION_PER_TIER;

        victim.addEffect(new MobEffectInstance(
                        ModEffects.ARMOR_BREAK,
                        duration,
                        amplifier),
                this);
    }
}
