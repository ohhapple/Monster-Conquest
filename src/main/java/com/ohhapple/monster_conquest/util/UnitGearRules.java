package com.ohhapple.monster_conquest.util;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.gun.GunItem;
import com.ohhapple.monster_conquest.item.LanceItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 单位装备栏准入规则：
 *   · 主手看兵种：近战兵只收近战武器（其它模组的近战武器同样放得进去，能打就行），
 *     弓箭手只收弓；枪手与双形态兵种不收自定义武器（都会打乱各自的换枪 / 换形态逻辑）；
 *   · 首领按武器形态放：弓首领收弓，近战 / 骑枪首领收近战武器；
 *   · 副手对普通兵种不限制（盾牌、备用武器都行）；
 *   · 骑枪算近战武器：骑兵拿上骑枪后会一直拿着，不会被自动换成别的。
 */
public final class UnitGearRules {

    private UnitGearRules() {}

    /** 近战武器判定：原版 / 本模组武器，或任何带主手攻击加成的物品（兼容其它模组的近战武器）。 */
    public static boolean isMeleeWeapon(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof MaceItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof LanceItem) {
            return true;
        }
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.slot() == EquipmentSlotGroup.MAINHAND
                    && entry.attribute().equals(Attributes.ATTACK_DAMAGE)
                    && entry.modifier().amount() > 0.0) {
                return true;
            }
        }
        return false;
    }

    /** 远程（弓）判定。 */
    public static boolean isBow(ItemStack stack) {
        return stack.getItem() instanceof BowItem;
    }

    /** 枪械判定。 */
    public static boolean isGun(ItemStack stack) {
        return stack.getItem() instanceof GunItem;
    }

    /** 骑枪判定。 */
    public static boolean isLance(ItemStack stack) {
        return stack.getItem() instanceof LanceItem;
    }

    /**
     * 护甲槽准入：按"这件东西能不能穿到目标槽"判断，原版与模组护甲同一套规则。
     *   优先用装备槽映射（Equipable / 模组自定义槽位）；没走这套通道的模组护甲，
     *   则用"该槽位上的护甲属性加成"兜底。
     */
    public static boolean fitsArmorSlot(LivingEntity wearer, ItemStack stack, EquipmentSlot target) {
        if (stack.isEmpty()) return true;
        if (wearer.getEquipmentSlotForItem(stack) == target) return true;
        EquipmentSlotGroup group = EquipmentSlotGroup.bySlot(target);
        for (ItemAttributeModifiers.Entry entry : stack.getAttributeModifiers().modifiers()) {
            if (entry.slot() == group
                    && entry.attribute().equals(Attributes.ARMOR)
                    && entry.modifier().amount() > 0.0) {
                return true;
            }
        }
        return false;
    }

    /** 主手准入。 */
    public static boolean mayPlaceMainHand(AbstractArmySoldier soldier, ItemStack stack) {
        if (stack.isEmpty()) return true;
        // 武器与专属攻击逻辑绑定的兵种（重锤兵等）不接自定义武器
        if (!soldier.allowsCustomWeapons()) return false;
        var type = soldier.getSoldierType();
        // 双形态：主手由"近战形态 ↔ 远程形态"自动换武器，玩家放的武器会被换掉、还会打乱原有攻击逻辑
        if (type.isDual()) return false;
        if (type.isArcher()) return isBow(stack);
        // 枪手不接自定义武器：换掉手上的枪会破坏弹药 / 装填 / 射击逻辑
        if (type.isGunner()) return false;
        return isMeleeWeapon(stack);
    }

    /** 副手准入：枪手 / 双形态不收武器，其它单位不限制（盾牌等非武器都行）。 */
    public static boolean mayPlaceOffHand(AbstractArmySoldier soldier, ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (!soldier.allowsCustomWeapons()) {
            return !isMeleeWeapon(stack) && !isBow(stack) && !isGun(stack);
        }
        var type = soldier.getSoldierType();
        // 枪手 / 双形态的副手不收武器（盾牌等非武器不受限）
        if (type.isGunner() || type.isDual()) {
            return !isMeleeWeapon(stack) && !isBow(stack) && !isGun(stack);
        }
        return true;
    }

    /**
     * 首领主手准入：首领在近战武器 / 弓之间切换武器形态，两种都收；
     *   枪械不收（首领不会用枪，放上去也不开火）。
     */
    public static boolean mayPlaceMainHand(AbstractArmyCommander commander, ItemStack stack) {
        if (stack.isEmpty()) return true;
        return isMeleeWeapon(stack) || isBow(stack);
    }

    /** 首领副手准入：不限制（盾牌、备用武器都行）。 */
    public static boolean mayPlaceOffHand(AbstractArmyCommander commander, ItemStack stack) {
        return true;
    }
}
