package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class EndermiteArenaUnit extends ArenaUnit {

    public EndermiteArenaUnit() {
        super("arena_endermite", ModEntities.ARENA_ENDERMITE,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.ENDERMITE_SILENCE_STRIKE, 20,
                500, 38, 2, 25, 0.72);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_endermite");
    }
}
