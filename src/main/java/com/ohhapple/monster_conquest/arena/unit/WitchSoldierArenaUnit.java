package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class WitchSoldierArenaUnit extends ArenaUnit {

    public WitchSoldierArenaUnit() {
        super("soldier_witch", ModEntities.WITCH_SOLDIER,
                SoldierType.THROWER_INFANTRY, 4,
                ArenaSkillType.WITCH_LINGERING_POTION, 10,
                1750, 100, 8, 50, 0.70);
    }
}
