package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class CreeperArenaUnit extends ArenaUnit {
    public CreeperArenaUnit() {
        super("arena_creeper", ModEntities.ARENA_CREEPER,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.CREEPER_EXPLODE, 5,
                760, 58, 4, 30, 0.66);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_creeper");
    }
}
