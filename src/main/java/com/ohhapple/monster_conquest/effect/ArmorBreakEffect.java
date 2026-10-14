package com.ohhapple.monster_conquest.effect;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * 碎甲：每级 -10% 护甲值与 -10% 盔甲韧性（amplifier 9 = 两者都 -100%）；
 *   施放方需把 amplifier clamp 到 {@link #MAX_AMPLIFIER}。
 */
public class ArmorBreakEffect extends MobEffect {

    /** 最高等级（= -100% 护甲值与盔甲韧性）。 */
    public static final int MAX_AMPLIFIER = 9;

    public ArmorBreakEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
        this.addAttributeModifier(
                Attributes.ARMOR,
                ResourceLocation.fromNamespaceAndPath(
                        Monster_conquest.MODID, "effect.armor_break"),
                -0.1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        this.addAttributeModifier(
                Attributes.ARMOR_TOUGHNESS,
                ResourceLocation.fromNamespaceAndPath(
                        Monster_conquest.MODID, "effect.armor_break_toughness"),
                -0.1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
