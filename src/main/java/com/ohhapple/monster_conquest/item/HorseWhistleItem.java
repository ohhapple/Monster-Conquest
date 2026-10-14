package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.player.PlayerMountUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 马哨：右键召唤 / 骑乘玩家战马；骑乘中再右键则下马（战马立即销毁）。
 *
 * 放在战术装备栏时，也可以由 R 轮盘"玩家策略"里的"玩家上马 / 玩家下马"控制。
 */
public class HorseWhistleItem extends Item {

    public HorseWhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            if (!PlayerMountUtil.mount(sp, false)) {
                PlayerMountUtil.dismount(sp);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.monster_conquest.horse_whistle.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.horse_whistle.tactical_slots_hint")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
