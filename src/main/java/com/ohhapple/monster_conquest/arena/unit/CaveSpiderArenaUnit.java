package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class CaveSpiderArenaUnit extends ArenaUnit {

    public CaveSpiderArenaUnit() {
        super("arena_cave_spider", ModEntities.ARENA_CAVE_SPIDER,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.CAVE_SPIDER_VENOM, 22,
                760, 52, 4, 32, 0.73);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_cave_spider");
    }
}
