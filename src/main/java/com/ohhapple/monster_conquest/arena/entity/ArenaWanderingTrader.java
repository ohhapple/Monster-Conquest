package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/** 仅用于自走棋的原版流浪商人形态，不继承原版交易与闲逛 AI。 */
public final class ArenaWanderingTrader extends ArenaCreatureSoldier {

    public ArenaWanderingTrader(EntityType<? extends PathfinderMob> type,
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
