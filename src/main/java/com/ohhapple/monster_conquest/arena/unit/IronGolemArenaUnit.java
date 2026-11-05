package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class IronGolemArenaUnit extends ArenaUnit {

    public IronGolemArenaUnit() {
        super("arena_iron_golem", ModEntities.ARENA_IRON_GOLEM,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.IRON_GOLEM_LEAP_SMASH, 30,
                1450, 68, 12, 45, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_iron_golem");
    }
}
