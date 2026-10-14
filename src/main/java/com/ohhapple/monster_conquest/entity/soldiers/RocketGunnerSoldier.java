package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.ModSounds;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.projectile.ArmyRocket;
import com.ohhapple.monster_conquest.gun.GunSpec;
import com.ohhapple.monster_conquest.gun.GunSpecs;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** 火箭筒兵：数值与玩家火箭筒完全一致（含爆炸半径），不随等级变化。 */
public class RocketGunnerSoldier extends AbstractGunnerSoldier {

    public RocketGunnerSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    @Override public GunSpec getGunSpec() { return GunSpecs.LAUNCHER; }
    @Override public int getRenderIndex() { return 11; }

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 24.0;
            case TIER_1 -> 34.0;
            case TIER_2 -> 44.0;
            case TIER_3 -> 58.0;
            case TIER_4 -> 76.0;
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

    @Override public double getCavalryHorseSpeed()   { return 0.40; }
    @Override public double getMovementSpeed()      { return 0.32; }
    @Override public double getCavalryChargeSpeed() { return 0.44; }
    @Override public int getXpReward() { return 8; }

    @Override public double getMinAttackRange(boolean mounted) { return mounted ? 16.0 : 14.0; }

    @Override public Item getGunItem() { return Monster_conquest.ROCKET_LAUNCHER.get(); }

    @Override
    protected Projectile createProjectile() {
        ArmyRocket rocket = new ArmyRocket(ModEntities.ARMY_ROCKET.get(), this, this.level());
        rocket.setExplosionRadius(getGunSpec().explosionRadius());
        rocket.setMaxLifeTicks(100);
        return rocket;
    }

    @Override
    protected SoundEvent getShootSound() { return ModSounds.ROCKET_LAUNCH.get(); }
}
