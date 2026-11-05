package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class PolarBearArenaUnit extends ArenaUnit {

    public PolarBearArenaUnit() {
        super("arena_polar_bear", ModEntities.ARENA_POLAR_BEAR,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.POLAR_BEAR_FORM, 40,
                1450, 70, 10, 45, 0.68);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_polar_bear");
    }
}
