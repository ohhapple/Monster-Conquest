package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class BreezeArenaUnit extends ArenaUnit {

    public BreezeArenaUnit() {
        super("arena_breeze", ModEntities.ARENA_BREEZE,
                SoldierType.ARCHER_INFANTRY, 5,
                ArenaSkillType.BREEZE_GALE_CHARGE, 20,
                2450, 132, 12, 55, 0.78);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_breeze");
    }
}
