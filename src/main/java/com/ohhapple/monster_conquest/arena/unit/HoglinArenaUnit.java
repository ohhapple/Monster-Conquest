package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class HoglinArenaUnit extends ArenaUnit {

    public HoglinArenaUnit() {
        super("arena_hoglin", ModEntities.ARENA_HOGLIN,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.HOGLIN_CHARGE, 20,
                950, 58, 7, 35, 0.70);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_hoglin");
    }
}
