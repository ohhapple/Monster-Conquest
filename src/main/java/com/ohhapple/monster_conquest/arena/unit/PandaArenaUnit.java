package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class PandaArenaUnit extends ArenaUnit {
    public PandaArenaUnit() {
        super("arena_panda", ModEntities.ARENA_PANDA,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.PANDA_ROLL, 24,
                920, 57, 6, 34, 0.70);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_panda");
    }
}
