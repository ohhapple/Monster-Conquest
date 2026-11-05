package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class SkeletonArenaUnit extends ArenaUnit {

    public SkeletonArenaUnit() {
        super("soldier_skeleton", ModEntities.SKELETON_SOLDIER,
                SoldierType.ARCHER_INFANTRY, 1,
                ArenaSkillType.SKELETON_VOLLEY, 10,
                500, 36, 2, 25, 0.72);
    }
}
