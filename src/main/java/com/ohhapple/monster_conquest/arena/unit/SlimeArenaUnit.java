package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class SlimeArenaUnit extends ArenaUnit {

    public SlimeArenaUnit() {
        super("arena_giant_slime", ModEntities.ARENA_SLIME,
                SoldierType.MELEE_INFANTRY, 5,
                ArenaSkillType.SLIME_LEAP_SMASH, 30,
                2550, 126, 16, 50, 0.66);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_giant_slime");
    }
}
