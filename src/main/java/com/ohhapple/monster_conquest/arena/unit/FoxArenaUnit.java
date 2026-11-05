package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class FoxArenaUnit extends ArenaUnit {
    public FoxArenaUnit() {
        super("arena_fox", ModEntities.ARENA_FOX,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.FOX_STEAL, 10,
                760, 54, 4, 32, 0.73);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_fox");
    }
}
