package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class WardenArenaUnit extends ArenaUnit {
    public WardenArenaUnit() {
        super("arena_warden", ModEntities.ARENA_WARDEN,
                SoldierType.MELEE_INFANTRY, 5,
                ArenaSkillType.WARDEN_SONIC_BOOM, 25,
                2500, 132, 16, 55, 0.66);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_warden");
    }
}
