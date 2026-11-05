package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class ShotgunGunnerArenaUnit extends ArenaUnit {

    public ShotgunGunnerArenaUnit() {
        super("soldier_gunner_shotgun",
                ModEntities.SHOTGUN_GUNNER_SOLDIER,
                SoldierType.GUNNER_INFANTRY, 4,
                ArenaSkillType.SHOTGUN_BARRAGE, 15,
                1800, 104, 10, 40, 0.72);
    }
}
