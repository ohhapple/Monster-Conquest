package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class ArenaPolarBear extends ArenaCreatureSoldier {

    public ArenaPolarBear(EntityType<? extends PathfinderMob> type,
                          Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 27;
    }

    @Override
    public double getMovementSpeed() {
        return 0.30;
    }
}
