package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class EndermanArenaUnit extends ArenaUnit {
    public EndermanArenaUnit() {
        super("arena_enderman", ModEntities.ARENA_ENDERMAN,
                SoldierType.MELEE_INFANTRY, 4,
                ArenaSkillType.ENDERMAN_LOCK, 15,
                1800, 100, 12, 50, 0.78);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_enderman");
    }
}
