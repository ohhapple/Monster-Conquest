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

/** 步枪枪兵：数值与玩家步枪完全一致，不随等级变化。 */
public class RifleGunnerSoldier extends AbstractGunnerSoldier {

    public RifleGunnerSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public GunSpec getGunSpec() { return GunSpecs.RIFLE; }
    @Override public int getRenderIndex() { return 9; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 20.0;
            case TIER_1 -> 30.0;
            case TIER_2 -> 40.0;
            case TIER_3 -> 54.0;
            case TIER_4 -> 72.0;
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

    @Override public double getMinAttackRange(boolean mounted) { return mounted ? 14.0 : 12.0; }

    @Override public Item getGunItem() { return Monster_conquest.RIFLE.get(); }

    @Override
    protected Projectile createProjectile() {
        ArmyBullet bullet = new ArmyBullet(ModEntities.ARMY_BULLET.get(), this, this.level());
        bullet.setMaxLifeTicks(45);
        return bullet;
    }

    @Override
    protected SoundEvent getShootSound() { return ModSounds.RIFLE_SHOOT.get(); }
}
