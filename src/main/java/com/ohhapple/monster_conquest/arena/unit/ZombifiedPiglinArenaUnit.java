package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class ZombifiedPiglinArenaUnit extends ArenaUnit {
    public ZombifiedPiglinArenaUnit() {
        super("arena_zombified_piglin",
                ModEntities.ARENA_ZOMBIFIED_PIGLIN,
                SoldierType.MELEE_INFANTRY, 2,
                ArenaSkillType.ZOMBIFIED_PIGLIN_FRENZY, 20,
                900, 58, 7, 35, 0.70);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_zombified_piglin");
    }
}
