package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class MaceArenaUnit extends ArenaUnit {

    public MaceArenaUnit() {
        super("soldier_mace", ModEntities.VILLAGER_MACE_SOLDIER,
                SoldierType.MELEE_INFANTRY, 4,
                ArenaSkillType.MACE_LEAP_SMASH, 25,
                1900, 105, 16, 45, 0.62);
    }
}
