package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class PiglinShieldArenaUnit extends ArenaUnit {

    public PiglinShieldArenaUnit() {
        super("soldier_piglin_shield",
                ModEntities.PIGLIN_SHIELD_SOLDIER,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.PIGLIN_TAUNT_SHIELD, 40,
                1450, 68, 12, 50,
                0.62, true);
    }
}
