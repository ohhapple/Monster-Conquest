package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class SpiderArenaUnit extends ArenaUnit {

    public SpiderArenaUnit() {
        super("arena_spider", ModEntities.ARENA_SPIDER,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.SPIDER_SLOW_FIELD, 20,
                560, 38, 3, 25, 0.70);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_spider");
    }
}
