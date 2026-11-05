package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

/** 亡灵十人口生成的专属末影龙弈子。 */
public final class EnderDragonArenaUnit extends ArenaUnit {

    public EnderDragonArenaUnit() {
        super("arena_ender_dragon",
                ModEntities.ARENA_ENDER_DRAGON,
                SoldierType.ARCHER_INFANTRY, 5,
                ArenaSkillType.ENDER_DRAGON_BOARD_SLAM, 35,
                2550, 138, 18, 60, 0.72);
    }

    @Override
    public boolean special() {
        return true;
    }

    @Override
    public double movementSpeed() {
        return 0.48;
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_ender_dragon");
    }
}
