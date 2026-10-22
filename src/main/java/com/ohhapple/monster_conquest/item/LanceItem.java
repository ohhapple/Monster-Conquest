package com.ohhapple.monster_conquest.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 骑枪：骑乘时根据坐骑水平速度提升伤害。
 *
 * 伤害公式：冲锋伤害 = 基础伤害 × (1 + (speed - 0.05) × 5)
 *   其中 speed 是坐骑水平速度（格/tick）。
 *   speed < 0.05 不额外加成，走 baseDamage。
 *   speed = 0.35（下界合金马的速度）→ 倍率 ≈ 2.5x。
 */
public class LanceItem extends Item {

    /** custom_model_data：骑枪冲锋 / 穿越时使用的平放模型。 */
    public static final int CHARGING_MODEL_DATA = 1;

    private final Tier tier;
    private final float baseDamage;

    public LanceItem(Tier tier, float baseDamage, Item.Properties props) {
        super(props);
        this.tier = tier;
        this.baseDamage = baseDamage;
    }

    public Tier getTier() { return tier; }
    public float getBaseDamage() { return baseDamage; }

    public static boolean hasChargingModel(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data != null && data.value() == CHARGING_MODEL_DATA;
    }

    /**
     * 写入 / 清除冲锋模型状态。
     *   调用方必须传入可安全修改的副本，并通过 setItemSlot 同步给客户端。
     */
    public static void setChargingModel(ItemStack stack, boolean charging) {
        if (charging) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA,
                    new CustomModelData(CHARGING_MODEL_DATA));
        } else {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
        }
    }

    public float getChargeDamage(LivingEntity attacker) {
        if (!attacker.isPassenger()) return baseDamage;
        Entity vehicle = attacker.getVehicle();
        if (vehicle == null) return baseDamage;

        Vec3 motion = vehicle.getDeltaMovement();
        double speed = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        double threshold = 0.05;
        if (speed < threshold) return baseDamage;

        float multiplier = 1.0f + (float) ((speed - threshold) * 5.0);
        return baseDamage * multiplier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("基础伤害：" + (int) baseDamage)
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.literal("冲锋时伤害 = 基础伤害 × (1 + (速度-0.05)×5)")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("  速度 = 坐骑水平速度（格/tick）")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("  例：速度 0.35 → ×2.5 倍")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("非骑乘/静止时按基础伤害计算")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
