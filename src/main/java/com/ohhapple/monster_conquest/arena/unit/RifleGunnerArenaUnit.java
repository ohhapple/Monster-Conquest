package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class RifleGunnerArenaUnit extends ArenaUnit {

    public RifleGunnerArenaUnit() {
        super("soldier_gunner_rifle", ModEntities.RIFLE_GUNNER_SOLDIER,
                SoldierType.GUNNER_INFANTRY, 3,
                ArenaSkillType.RIFLE_RAPID_FIRE, 35,
                1250, 73, 5, 35, 0.78);
    }
}
