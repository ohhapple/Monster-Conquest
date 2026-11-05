package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class SnifferArenaUnit extends ArenaUnit {

    public SnifferArenaUnit() {
        super("arena_sniffer", ModEntities.ARENA_SNIFFER,
                SoldierType.MELEE_INFANTRY, 5,
                ArenaSkillType.SNIFFER_DIG, 50,
                2550, 128, 16, 55, 0.65);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_sniffer");
    }
}
