package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class ElderGuardianArenaUnit extends ArenaUnit {

    public ElderGuardianArenaUnit() {
        super("arena_elder_guardian", ModEntities.ARENA_ELDER_GUARDIAN,
                SoldierType.ARCHER_INFANTRY, 4,
                ArenaSkillType.ELDER_GUARDIAN_LASER, 30,
                1800, 100, 12, 50, 0.68);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_elder_guardian");
    }
}
