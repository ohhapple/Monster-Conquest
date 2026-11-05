package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class BoggedArenaUnit extends ArenaUnit {

    public BoggedArenaUnit() {
        super("soldier_bogged", ModEntities.BOGGED_SOLDIER,
                SoldierType.ARCHER_INFANTRY, 4,
                ArenaSkillType.BOGGED_POISON_VOLLEY, 12,
                1750, 96, 8, 40, 0.70);
    }
}
