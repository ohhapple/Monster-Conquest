package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class DrownedArenaUnit extends ArenaUnit {

    public DrownedArenaUnit() {
        super("soldier_drowned", ModEntities.DROWNED_SOLDIER,
                SoldierType.DUAL_INFANTRY, 2,
                ArenaSkillType.DROWNED_TRIDENT_STUN, 25,
                900, 56, 5, 35, 0.66);
    }
}
