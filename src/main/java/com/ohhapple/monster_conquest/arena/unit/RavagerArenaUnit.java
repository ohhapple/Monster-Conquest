package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class RavagerArenaUnit extends ArenaUnit {

    public RavagerArenaUnit() {
        super("arena_ravager", ModEntities.ARENA_RAVAGER,
                SoldierType.MELEE_INFANTRY, 4,
                ArenaSkillType.RAVAGER_KNOCKUP_HEAL, 35,
                1900, 102, 16, 45, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_ravager");
    }
}
