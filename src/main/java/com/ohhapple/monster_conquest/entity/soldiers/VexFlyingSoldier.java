package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 恼鬼飞行士兵。
 *   穿墙属于飞行兵种能力；召唤者绑定由可选的召唤物属性单独处理。
 */
public class VexFlyingSoldier extends AbstractFlyingSoldier {

    public VexFlyingSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.FLYING; }
    @Override public boolean hasFixedTier() { return true; }
    @Override public int getRenderIndex() { return 19; }
    @Override public double getMaxHealthForTier(EquipmentTier tier) { return 16.0; }
    @Override public double getArmorForTier(EquipmentTier tier) { return 8.0; }
    @Override public double getMeleeDamageForTier(EquipmentTier tier) { return 8.0; }
    @Override public double getMovementSpeed() { return 0.74; }
    @Override public int getMeleeAttackIntervalTicks() { return 20; }
    @Override public int getXpReward() { return 3; }

    @Override
    protected ItemStack createFlyingMainHandItem() {
        return new ItemStack(Items.IRON_SWORD);
    }

    @Override public double getFlyingAttackReach() { return 2; }

    @Override protected boolean usesArmyEquipment() { return false; }
    @Override public boolean allowsCustomWeapons() { return false; }

    @Override
    public boolean canPassThroughWalls() {
        return true;
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    public void tick() {
        boolean previous = this.noPhysics;
        this.noPhysics = true;
        try {
            super.tick();
        } finally {
            this.noPhysics = previous;
        }
    }

    @Override
    public void move(MoverType type, Vec3 movement) {
        Vec3 before = position();
        super.move(type, movement);
        // 穿墙时只允许水平 / 向上穿越。向下进入原本位于脚下的实心方块时，
        //   撤回本 tick 的纵向位移，避免恼鬼沿墙面或目标高度逐渐沉入地下。
        if (movement.y < -1.0E-5) {
            BlockPos belowNew = BlockPos.containing(getX(), getY() - 0.05, getZ());
            BlockPos belowOld = BlockPos.containing(before.x, before.y - 0.05, before.z);
            if (level().getBlockState(belowNew).isSolid()
                    && !level().getBlockState(belowOld).isSolid()) {
                this.setPos(getX(), before.y, getZ());
                Vec3 delta = getDeltaMovement();
                this.setDeltaMovement(delta.x, 0.0, delta.z);
            }
        }
        this.checkInsideBlocks();
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.VEX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.VEX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.VEX_DEATH;
    }

    @Override
    public float getLightLevelDependentMagicValue() {
        return 1.0f;
    }
}
