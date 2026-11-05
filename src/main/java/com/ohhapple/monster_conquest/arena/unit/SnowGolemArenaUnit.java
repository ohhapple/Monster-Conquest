package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class SnowGolemArenaUnit extends ArenaUnit {

    public SnowGolemArenaUnit() {
        super("arena_snow_golem", ModEntities.ARENA_SNOW_GOLEM,
                SoldierType.ARCHER_INFANTRY, 1,
                ArenaSkillType.SNOW_GOLEM_SNOWBALL_FAN, 5,
                600, 36, 3, 25, 0.64);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_snow_golem");
    }
}
