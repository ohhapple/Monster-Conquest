package com.ohhapple.monster_conquest.damage;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 模组自定义伤害类型。
 *
 * {@link #SHIELD_BREAK}（破盾）：卫道士士兵的斧击专用伤害类型。
 *   它在数据包里被加入 {@code minecraft:bypasses_shield} 标签，
 *   因此：
 *     · 玩家举盾等原版盾牌格挡无效；
 *     · 士兵 / 首领的自定义盾牌格挡（同样检查该标签）也无效。
 */
public final class ModDamageTypes {

    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> SHIELD_BREAK =
            ResourceKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(
                            Monster_conquest.MODID, "shield_break"));

    /** 由 {@code attacker} 施加的破盾伤害（无视一切盾牌格挡）。 */
    public static DamageSource shieldBreak(LivingEntity attacker) {
        return attacker.damageSources().source(SHIELD_BREAK, attacker);
    }

    /** 由 {@code attacker} 施加的破盾伤害，附带独立的直接来源实体。 */
    public static DamageSource shieldBreak(LivingEntity attacker,
                                           @Nullable net.minecraft.world.entity.Entity directEntity) {
        return attacker.damageSources().source(SHIELD_BREAK, directEntity, attacker);
    }
}
