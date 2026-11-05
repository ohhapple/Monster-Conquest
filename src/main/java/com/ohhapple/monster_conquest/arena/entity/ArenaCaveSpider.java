package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class ArenaCaveSpider extends ArenaCreatureSoldier {

    public ArenaCaveSpider(EntityType<? extends PathfinderMob> type,
                           Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 31;
    }

    @Override
    public double getMovementSpeed() {
        return 0.36;
    }
}
