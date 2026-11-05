package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;

public final class SniperGunnerArenaUnit extends ArenaUnit {

    public SniperGunnerArenaUnit() {
        super("soldier_gunner_sniper", ModEntities.SNIPER_GUNNER_SOLDIER,
                SoldierType.GUNNER_INFANTRY, 5,
                ArenaSkillType.SNIPER_EXECUTE_SHOT, 3,
                2400, 132, 12, 45, 0.90);
    }
}
