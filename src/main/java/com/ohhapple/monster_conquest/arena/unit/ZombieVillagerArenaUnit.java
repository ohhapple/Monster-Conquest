package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class ZombieVillagerArenaUnit extends ArenaUnit {

    public ZombieVillagerArenaUnit() {
        super("soldier_zombie_villager",
                ModEntities.ZOMBIE_VILLAGER_SOLDIER,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.ZOMBIE_VILLAGER_STUN, 10,
                560, 36, 3, 25, 0.64);
    }
}
