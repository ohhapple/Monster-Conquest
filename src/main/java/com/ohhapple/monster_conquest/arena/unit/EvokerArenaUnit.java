package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class EvokerArenaUnit extends ArenaUnit {

    public EvokerArenaUnit() {
        super("soldier_evoker", ModEntities.EVOKER_SOLDIER,
                SoldierType.MAGE_INFANTRY, 5,
                ArenaSkillType.EVOKER_FANG_STUN, 25,
                2500, 128, 12, 50, 0.65);
    }
}
