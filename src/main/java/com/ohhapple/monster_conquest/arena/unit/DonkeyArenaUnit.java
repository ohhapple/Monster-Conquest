package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.resources.ResourceLocation;

public final class DonkeyArenaUnit extends ArenaUnit {
    public DonkeyArenaUnit() {
        super("arena_donkey", ModEntities.ARENA_DONKEY,
                SoldierType.MELEE_INFANTRY, 1,
                ArenaSkillType.DONKEY_GIFT, 15,
                620, 36, 3, 28, 0.62);
    }

    @Override
    public ResourceLocation icon() {
        return customIcon("arena_donkey");
    }
}
