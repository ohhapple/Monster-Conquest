package com.ohhapple.monster_conquest.util;

import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;

/** 和平模式：模组单位不清除，但也不结仇、不索敌、不攻击。 */
public final class PeacefulUtil {

    private PeacefulUtil() {}

    public static boolean isPeaceful(Entity entity) {
        return entity != null
                && !entity.level().isClientSide
                && entity.level().getDifficulty() == Difficulty.PEACEFUL;
    }
}
