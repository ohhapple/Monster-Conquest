package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** 自走棋动物弈子的公共实体基类。 */
public abstract class ArenaAnimalSoldier extends ArenaCreatureSoldier {

    protected ArenaAnimalSoldier(
            EntityType<? extends PathfinderMob> type,
            Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }
}
