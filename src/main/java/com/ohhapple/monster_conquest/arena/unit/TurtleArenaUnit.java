package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class TurtleArenaUnit extends ArenaUnit {

    public TurtleArenaUnit() {
        super("arena_turtle", ModEntities.ARENA_TURTLE,
                SoldierType.MELEE_INFANTRY, 3,
                ArenaSkillType.TURTLE_RESILIENCE, 10,
                1450, 68, 12, 50, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_turtle");
    }
}
