package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class SkeletonHorseArenaUnit extends ArenaUnit {

    public SkeletonHorseArenaUnit() {
        super("arena_skeleton_horse", ModEntities.ARENA_SKELETON_HORSE,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.SKELETON_HORSE_CLEANSE, 22,
                1400, 72, 10, 45, 0.78);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_skeleton_horse");
    }
}
