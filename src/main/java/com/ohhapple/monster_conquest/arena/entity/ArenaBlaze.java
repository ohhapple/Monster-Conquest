package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;

public final class ArenaBlaze extends ArenaCreatureSoldier {

    public ArenaBlaze(EntityType<? extends PathfinderMob> type,
                      Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.ARCHER_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 33;
    }

    @Override
    public double getMovementSpeed() {
        return 0.29;
    }

    @Override
    protected double getProjectileSpeed() {
        return 1.15;
    }

    @Override
    protected boolean usesDirectFire() {
        return true;
    }

    @Override
    protected double getProjectileGravity() {
        return 0.0;
    }

    @Override
    protected double getShotInaccuracy() {
        return 0.0;
    }

    @Override
    protected SoundEvent getShootSound() {
        return SoundEvents.BLAZE_SHOOT;
    }

    @Override
    protected Projectile createProjectile() {
        ArenaFireball fireball = new ArenaFireball(
                ModEntities.ARENA_FIREBALL.get(), level());
        fireball.setOwner(this);
        fireball.setPos(getX(), getEyeY() - 0.15, getZ());
        return fireball;
    }

    @Override
    protected void applyArenaProjectileDamage(Projectile projectile,
                                              double damage) {
        if (projectile instanceof ArenaFireball fireball) {
            fireball.configure(false, (float) damage,
                    getArenaStar());
        }
    }
}
