package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 保暖衣（战术装备）：放入战术装备栏后完全免疫冻结（玩家与单位都生效）
 * （冻结箭不叠加冻结 tick，细雪等来源的冻结每 tick 被清空）。
 */
public class WarmClothesItem extends Item {

    public WarmClothesItem(Properties properties) {
        super(properties);
    }

    /** 战术装备栏里是否有保暖衣：玩家走玩家附件，单位（士兵 / 首领）走单位的战术装备栏。 */
    public static boolean isEquipped(LivingEntity entity) {
        if (entity instanceof Player player) {
            for (ItemStack stack : TacticalEquipmentSlotsData.get(player)) {
                if (stack.is(Monster_conquest.WARM_CLOTHES.get())) return true;
            }
            return false;
        }
        if (entity instanceof ManagedUnit unit) {
            return unit.hasTactical(stack -> stack.is(Monster_conquest.WARM_CLOTHES.get()));
        }
        return false;
    }

    /**
     * 冻结免疫：自己穿了保暖衣；作为坐骑时，骑手穿了保暖衣也算
     *   （骑手保暖，胯下的战马同样不受冻结）。
     */
    public static boolean isFrostImmune(LivingEntity entity) {
        if (isEquipped(entity)) return true;
        return entity.getFirstPassenger() instanceof LivingEntity rider && isEquipped(rider);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.monster_conquest.warm_clothes.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.warm_clothes.tactical_slots_hint")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
