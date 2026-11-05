package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class HuskArenaUnit extends ArenaUnit {

    public HuskArenaUnit() {
        super("soldier_husk", ModEntities.HUSK_SOLDIER,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.HUSK_BATTLE_GROWTH, 20,
                920, 58, 7, 35, 0.63);
    }
}
