package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** 自走棋步行骷髅马，不继承原版马匹驯服、骑乘或陷阱行为。 */
public final class ArenaSkeletonHorse extends ArenaCreatureSoldier {

    public ArenaSkeletonHorse(EntityType<? extends PathfinderMob> type,
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
        return 0.38;
    }
}
