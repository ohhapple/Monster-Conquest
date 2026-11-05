package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class ArmadilloArenaUnit extends ArenaUnit {

    public ArmadilloArenaUnit() {
        super("arena_armadillo", ModEntities.ARENA_ARMADILLO,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.ARMADILLO_ROLL, 30,
                620, 36, 3, 28, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_armadillo");
    }
}
