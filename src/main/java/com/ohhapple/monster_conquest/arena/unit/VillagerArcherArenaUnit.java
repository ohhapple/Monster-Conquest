package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class VillagerArcherArenaUnit extends ArenaUnit {

    public VillagerArcherArenaUnit() {
        super("soldier_villager_archer",
                ModEntities.VILLAGER_ARCHER_SOLDIER,
                SoldierType.ARCHER_INFANTRY, 2,
                ArenaSkillType.ARCHER_STRENGTH, 20,
                700, 48, 3, 32, 0.73);
    }
}
