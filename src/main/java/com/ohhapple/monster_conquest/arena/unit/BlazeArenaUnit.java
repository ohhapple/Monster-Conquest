package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class BlazeArenaUnit extends ArenaUnit {

    public BlazeArenaUnit() {
        super("arena_blaze", ModEntities.ARENA_BLAZE,
                SoldierType.ARCHER_INFANTRY, 3,
                ArenaSkillType.BLAZE_FIREBALL_BARRAGE, 20,
                1250, 73, 5, 50, 0.72);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_blaze");
    }
}
