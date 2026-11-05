package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class RabbitArenaUnit extends ArenaUnit {
    public RabbitArenaUnit() {
        super("arena_rabbit", ModEntities.ARENA_RABBIT,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.RABBIT_LEAP, 15,
                520, 38, 2, 25, 0.72);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_rabbit");
    }
}
