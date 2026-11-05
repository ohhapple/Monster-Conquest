package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class SheepArenaUnit extends ArenaUnit {
    public SheepArenaUnit() {
        super("arena_sheep", ModEntities.ARENA_SHEEP,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.SHEEP_HEAL, 10,
                600, 36, 3, 28, 0.64);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_sheep");
    }
}
