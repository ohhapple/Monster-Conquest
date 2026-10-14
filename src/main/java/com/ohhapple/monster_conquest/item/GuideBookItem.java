package com.ohhapple.monster_conquest.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 说明书：右键打开本模组的图文（文字）说明界面。
 *   界面由客户端打开，服务端只负责返回交互成功。
 */
public class GuideBookItem extends Item {

    public GuideBookItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // 客户端在 PlayerInteractEvent.RightClickItem 里打开界面
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.monster_conquest.guide_book.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.guide_book.contents")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
