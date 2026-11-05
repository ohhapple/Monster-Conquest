package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class GoatArenaUnit extends ArenaUnit {

    public GoatArenaUnit() {
        super("arena_goat", ModEntities.ARENA_GOAT,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.GOAT_CHARGE, 18,
                620, 37, 3, 28, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_goat");
    }
}
