package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class WitherArenaUnit extends ArenaUnit {

    public WitherArenaUnit() {
        super("arena_wither", ModEntities.ARENA_WITHER,
                SoldierType.ARCHER_INFANTRY, 5,
                ArenaSkillType.WITHER_SKULL_BARRAGE, 35,
                2500, 132, 16, 55, 0.66);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_wither");
    }
}
