package com.ohhapple.monster_conquest.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 重伤（禁疗）：每级使目标受到的治疗 -10%（amplifier 9 = 完全禁疗），
 * 由 {@code ModEvents#onLivingHeal} 统一削减所有 heal 途径；施放方需 clamp amplifier。
 */
public class GrievousWoundEffect extends MobEffect {

    /** 最高等级（= 100% 禁疗）。 */
    public static final int MAX_AMPLIFIER = 9;

    /** 每一级的禁疗比例。 */
    public static final float REDUCTION_PER_LEVEL = 0.10f;

    public GrievousWoundEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A0B0B);
    }

    /** 治疗量倍率：0 级 0.9 / 1 级 0.8 / … / 9 级 0.0。 */
    public static float healingMultiplier(int amplifier) {
        int amp = Math.max(0, Math.min(amplifier, MAX_AMPLIFIER));
        return Math.max(0.0f, 1.0f - REDUCTION_PER_LEVEL * (amp + 1));
    }
}
