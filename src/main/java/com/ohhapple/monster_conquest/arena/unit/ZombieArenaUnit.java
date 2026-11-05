package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class ZombieArenaUnit extends ArenaUnit {

    public ZombieArenaUnit() {
        super("soldier_zombie", ModEntities.ZOMBIE_SOLDIER,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.ZOMBIE_SHIELD, 20,
                620, 38, 3, 28, 0.62);
    }
}
