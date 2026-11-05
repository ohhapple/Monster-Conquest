package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class GiantArmadilloArenaUnit extends ArenaUnit {

    public GiantArmadilloArenaUnit() {
        super("arena_giant_armadillo",
                ModEntities.ARENA_GIANT_ARMADILLO,
                SoldierType.MELEE_INFANTRY, 4,
                ArenaSkillType.GIANT_ARMADILLO_MOMENTUM, 6,
                1900, 102, 16, 45, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_giant_armadillo");
    }
}
