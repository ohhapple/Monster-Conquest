package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class FrogArenaUnit extends ArenaUnit {
    public FrogArenaUnit() {
        super("arena_frog", ModEntities.ARENA_FROG,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.FROG_PULL, 25,
                1400, 72, 10, 45, 0.70);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_frog");
    }
}
