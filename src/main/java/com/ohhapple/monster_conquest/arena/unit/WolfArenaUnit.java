package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class WolfArenaUnit extends ArenaUnit {

    public WolfArenaUnit() {
        super("arena_wolf", ModEntities.ARENA_WOLF,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.WOLF_CALL, 40,
                1400, 72, 10, 45, 0.76);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_wolf");
    }
}
