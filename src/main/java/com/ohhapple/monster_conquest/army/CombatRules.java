package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.threat.ArmyThreatUtil;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.common.ItemAbilities;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 军团战斗通用规则：魔法伤害无视盾牌格挡（含自定义 magic_damage 标签兜底）；
 * 同军队（UUID）或结盟阵营视为友军。
 */
public final class CombatRules {

    private CombatRules() {}

    /** 自定义魔法伤害标签：默认含原版魔法类伤害，数据包可追加模组魔法伤害。 */
    public static final TagKey<DamageType> MAGIC_DAMAGE =
            TagKey.create(Registries.DAMAGE_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "magic_damage"));

    /** 是否魔法伤害（含原版 magic / indirect_magic / 音爆 / 龙息 / 凋零）。 */
    public static boolean isMagicDamage(DamageSource source) {
        return source.is(MAGIC_DAMAGE)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || source.is(DamageTypes.SONIC_BOOM)
                || source.is(DamageTypes.DRAGON_BREATH)
                || source.is(DamageTypes.WITHER);
    }

    /**
     * 本次伤害是否无视盾牌格挡。
     *   · 原版 bypasses_shield 标签（含 magic / indirect_magic / 破盾伤害类型）
     *   · 自定义 magic_damage 标签 / 原版魔法伤害兜底
     */
    public static boolean bypassesShieldBlock(DamageSource source) {
        return source.is(DamageTypeTags.BYPASSES_SHIELD) || isMagicDamage(source);
    }

    /**
     * 是不是"能格挡的盾牌"：原版盾、模组盾（ShieldItem 子类），
     *   或任何声明了原版盾格挡能力（shield_block）的物品 —— 自定义 / 模组盾牌同样能触发格挡。
     */
    public static boolean isShieldItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return stack.is(Items.SHIELD)
                || stack.getItem() instanceof ShieldItem
                || stack.canPerformAction(ItemAbilities.SHIELD_BLOCK);
    }

    /** 解析伤害来源的施加者（投射物取发射者）。 */
    @Nullable
    public static LivingEntity damageAttacker(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) attacker = source.getDirectEntity();
        if (attacker instanceof Projectile projectile
                && projectile.getOwner() instanceof LivingEntity owner) {
            attacker = owner;
        }
        return attacker instanceof LivingEntity living ? living : null;
    }

    /** 两个实体是否属于同一方（同军队 / 同阵营 / 单向结盟都算）。 */
    public static boolean isFriendly(@Nullable Entity a, @Nullable Entity b) {
        if (a == null || b == null) return false;
        if (a == b) return true;

        UUID armyA = AbstractArmySoldier.resolveArmyUuid(a, a.level());
        UUID armyB = AbstractArmySoldier.resolveArmyUuid(b, b.level());
        if (armyA != null && armyA.equals(armyB)) return true;

        ResourceLocation facA = ArmyThreatUtil.factionOf(a);
        ResourceLocation facB = ArmyThreatUtil.factionOf(b);
        if (facA == null || facB == null) return false;
        if (facA.equals(facB)) return true;
        return FactionAlly.isAlly(facA, facB)
                || FactionAlly.isAlly(facB, facA);
    }
}
