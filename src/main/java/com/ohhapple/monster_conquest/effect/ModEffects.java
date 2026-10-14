package com.ohhapple.monster_conquest.effect;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Monster_conquest.MODID);

    public static final DeferredHolder<MobEffect, ArmorBreakEffect> ARMOR_BREAK =
            EFFECTS.register("armor_break", ArmorBreakEffect::new);

    /** 重伤：降低目标受到的一切治疗（0 级 -10%，每级 +10%，上限 100%）。 */
    public static final DeferredHolder<MobEffect, GrievousWoundEffect> GRIEVOUS_WOUND =
            EFFECTS.register("grievous_wound", GrievousWoundEffect::new);

    private ModEffects() {}
}
