package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class StrayArenaUnit extends ArenaUnit {

    public StrayArenaUnit() {
        super("soldier_stray", ModEntities.STRAY_SOLDIER,
                SoldierType.ARCHER_INFANTRY, 2,
                ArenaSkillType.STRAY_ARCANE_SHOTS, 8,
                760, 50, 3, 30, 0.70);
    }
}
