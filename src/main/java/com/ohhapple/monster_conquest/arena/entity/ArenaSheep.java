package com.ohhapple.monster_conquest.arena.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class ArenaSheep extends ArenaAnimalSoldier {

    public ArenaSheep(EntityType<? extends PathfinderMob> type,
                      Level level) {
        super(type, level);
    }

    @Override
    public int getRenderIndex() {
        return 40;
    }

    @Override
    public double getMovementSpeed() {
        return 0.28;
    }
}
