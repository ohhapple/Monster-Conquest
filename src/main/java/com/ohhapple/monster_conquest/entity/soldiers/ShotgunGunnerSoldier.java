package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.ModSounds;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.projectile.ArmyBullet;
import com.ohhapple.monster_conquest.gun.GunSpec;
import com.ohhapple.monster_conquest.gun.GunSpecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 霰弹枪兵：数值与玩家霰弹枪完全一致，不随等级变化。 */
public class ShotgunGunnerSoldier extends AbstractGunnerSoldier {

    public ShotgunGunnerSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public GunSpec getGunSpec() { return GunSpecs.SHOTGUN; }
    @Override public int getRenderIndex() { return 10; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 22.0;
            case TIER_1 -> 32.0;
            case TIER_2 -> 42.0;
            case TIER_3 -> 56.0;
            case TIER_4 -> 74.0;
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

    @Override public double getMeleeDamageForTier(EquipmentTier tier) { return 3.0; }

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
    @Override public double getMovementSpeed()      { return 0.33; }
    @Override public double getCavalryChargeSpeed() { return 0.46; }
    @Override public int getXpReward() { return 7; }

    @Override public double getMinAttackRange(boolean mounted) { return mounted ? 5.0 : 4.0; }

    /**
     * 霰弹枪兵：贴身输出。
     *   霰弹散布大（7 颗弹丸 / 4.5°），站在枪械标称射程（40 格）开火几乎全空，
     *   所以把"理想站位"压到近身距离：步兵 4.0 格 / 骑马 5.0 格内就停下，
     *   更远时优先前进贴近目标；射程 / 开火判定仍按枪械本身数值（可以边靠近边开火）。
     */
    @Override public double getPreferredAttackRange(boolean mounted) { return mounted ? 5.0 : 4.0; }

    @Override public Item getGunItem() { return Monster_conquest.SHOTGUN.get(); }

    @Override
    protected Projectile createProjectile() {
        ArmyBullet bullet = new ArmyBullet(ModEntities.ARMY_BULLET.get(), this, this.level());
        bullet.setMaxLifeTicks(30);
        return bullet;
    }

    @Override
    protected SoundEvent getShootSound() { return ModSounds.SHOTGUN_SHOOT.get(); }
}
