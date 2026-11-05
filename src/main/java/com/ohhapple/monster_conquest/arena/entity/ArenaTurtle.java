package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** 不继承原版海龟的产卵、回水与年龄行为。 */
public final class ArenaTurtle extends ArenaCreatureSoldier {

    public ArenaTurtle(EntityType<? extends PathfinderMob> type,
                       Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 0;
    }

    @Override
    public double getMovementSpeed() {
        return 0.26;
    }
}
