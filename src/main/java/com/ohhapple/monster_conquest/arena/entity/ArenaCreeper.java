package com.ohhapple.monster_conquest.arena.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;

public final class ArenaCreeper extends ArenaAnimalSoldier {

    public ArenaCreeper(EntityType<? extends PathfinderMob> type,
                        Level level) {
        super(type, level);
    }

    @Override
    public int getRenderIndex() {
        return 46;
    }

    @Override
    public double getMovementSpeed() {
        return 0.30;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide && isArenaUnit()) {
            arenaSkills().onArenaDeath();
        }
        super.die(source);
    }
}
