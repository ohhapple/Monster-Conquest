package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class CowArenaUnit extends ArenaUnit {
    public CowArenaUnit() {
        super("arena_cow", ModEntities.ARENA_COW,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.COW_CLEANSE, 15,
                620, 36, 3, 28, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_cow");
    }
}
