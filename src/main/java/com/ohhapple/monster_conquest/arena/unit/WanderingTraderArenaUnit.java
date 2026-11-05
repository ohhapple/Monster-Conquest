package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class WanderingTraderArenaUnit extends ArenaUnit {

    public WanderingTraderArenaUnit() {
        super("arena_wandering_trader",
                ModEntities.ARENA_WANDERING_TRADER,
                SoldierType.MELEE_INFANTRY, 4,
                ArenaSkillType.WANDERING_TRADER_SUPPLY, 15,
                1850, 98, 10, 45, 0.66);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_wandering_trader");
    }
}
