package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class WitherSkeletonArenaUnit extends ArenaUnit {

    public WitherSkeletonArenaUnit() {
        super("soldier_wither_skeleton",
                ModEntities.WITHER_SKELETON_SOLDIER,
                SoldierType.DUAL_INFANTRY, 5,
                ArenaSkillType.WITHER_DRAIN, 35,
                2550, 138, 18, 60, 0.72);
    }
}
