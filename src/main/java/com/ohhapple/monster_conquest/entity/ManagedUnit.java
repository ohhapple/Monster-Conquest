package com.ohhapple.monster_conquest.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 可以在单位界面里管理装备的单位（士兵 / 首领通用）。
 *
 * 装备栏的统一语义：
 *   · 手动装备栏为空 = 该部位用单位自带装备（自带装备不进界面）；
 *   · 放入手动装备 = 覆盖身上自带的那件；取下手动装备 = 立刻还原成自带装备；
 *   · 战术装备栏只收保暖衣 / 防弹衣这类被动生效的装备，背包 / 马哨 / 弹药箱属于玩家装备。
 */
public interface ManagedUnit {

    /** 手动装备栏下标：0..3 = 头盔 / 胸甲 / 护腿 / 靴子，4 = 主手，5 = 副手。 */
    int GEAR_SLOTS = 6;
    /** 战术装备栏格数（与玩家的战术装备栏一致）。 */
    int TACTICAL_SLOTS = 18;

    /** 手动装备栏下标 → 实体装备槽。 */
    static EquipmentSlot gearSlot(int index) {
        return switch (index) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            case 5 -> EquipmentSlot.OFFHAND;
            default -> EquipmentSlot.MAINHAND;
        };
    }

    /** 作为实体本身的视图（界面预览 / 距离判定用）。 */
    LivingEntity asEntity();

    /** 阵营 id（用于归属判定与界面显示）。 */
    @Nullable ResourceLocation getFactionId();

    /** 读取手动装备（空 = 使用自带装备）。 */
    ItemStack getManualGear(int index);

    /** 写入手动装备：非空覆盖自带装备，空则还原自带装备。 */
    void setManualGear(int index, ItemStack stack);

    /** 读取战术装备。 */
    ItemStack getManualTactical(int index);

    /** 写入战术装备。 */
    void setManualTactical(int index, ItemStack stack);

    /** 补一份自带装备快照（存档里没有快照时的兜底）。 */
    void ensureDefaultGear();

    /** 清空全部手动装备与战术装备，并把自带装备还原到身上。 */
    void clearManualEquipment();

    /** 战术装备栏里是否有满足条件的物品。 */
    boolean hasTactical(Predicate<ItemStack> test);

    /** 找到满足条件的战术装备槽位；没有返回 -1。 */
    int findTactical(Predicate<ItemStack> test);

    /** 清空某个战术装备槽。 */
    void clearTactical(int index);

    /** 某个手动装备栏里是否有玩家放的装备。 */
    default boolean hasManualGear(int index) {
        return !getManualGear(index).isEmpty();
    }
}
