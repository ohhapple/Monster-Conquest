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

/** 狙击枪兵：数值与玩家狙击枪完全一致，不随等级变化。 */
public class SniperGunnerSoldier extends AbstractGunnerSoldier {

    public SniperGunnerSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public GunSpec getGunSpec() { return GunSpecs.SNIPER; }
    @Override public int getRenderIndex() { return 12; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 18.0;
            case TIER_1 -> 28.0;
            case TIER_2 -> 38.0;
            case TIER_3 -> 50.0;
            case TIER_4 -> 66.0;
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

    @Override public double getCavalryHorseSpeed()   { return 0.38; }
    @Override public double getMovementSpeed()      { return 0.31; }
    @Override public double getCavalryChargeSpeed() { return 0.42; }
    @Override public int getXpReward() { return 10; }

    @Override public double getMinAttackRange(boolean mounted) { return mounted ? 24.0 : 20.0; }

    @Override public Item getGunItem() { return Monster_conquest.SNIPER_RIFLE.get(); }

    @Override
    protected Projectile createProjectile() {
        ArmyBullet bullet = new ArmyBullet(ModEntities.ARMY_BULLET.get(), this, this.level());
        bullet.setMaxLifeTicks(60);
        return bullet;
    }

    @Override
    protected SoundEvent getShootSound() { return ModSounds.SNIPER_SHOOT.get(); }
}
