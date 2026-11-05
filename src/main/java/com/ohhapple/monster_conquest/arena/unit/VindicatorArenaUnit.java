package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class VindicatorArenaUnit extends ArenaUnit {

    public VindicatorArenaUnit() {
        super("soldier_vindicator", ModEntities.VINDICATOR_SOLDIER,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.VINDICATOR_EXECUTE, 25,
                950, 58, 7, 32, 0.68);
    }
}
