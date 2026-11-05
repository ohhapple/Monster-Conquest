package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class MagmaCubeArenaUnit extends ArenaUnit {

    public MagmaCubeArenaUnit() {
        super("arena_magma_cube", ModEntities.ARENA_MAGMA_CUBE,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.MAGMA_CUBE_BLESSING, 24,
                1350, 71, 8, 45, 0.72);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_magma_cube");
    }
}
