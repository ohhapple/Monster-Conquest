package com.ohhapple.monster_conquest.event;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.ControlToolItem;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.network.serverbound.item.CrosshairUsePayload;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 仇恨权杖 / 收纳工具 / 控制工具：右键统一走 CrosshairUsePayload。
 * 左键（onLeftClickBlock）在这里不做任何模式切换：
 *   模式切换完全交给客户端 ClientInputEvents 发 payload → 服务端切换。
 *   如果这里也切一次，会和客户端 payload 叠加成"切两次"导致回弹。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public class ItemInteractionEvents {

    private static boolean isCrosshairTool(ItemStack stack) {
        return stack.getItem() instanceof HatredWandItem
                || stack.getItem() instanceof RetrievalToolItem
                || stack.getItem() instanceof ControlToolItem;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!isCrosshairTool(event.getItemStack())) return;
        if (event.getLevel().isClientSide()) {
            PacketDistributor.sendToServer(new CrosshairUsePayload());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!isCrosshairTool(event.getItemStack())) return;
        if (event.getLevel().isClientSide()) {
            PacketDistributor.sendToServer(new CrosshairUsePayload());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!isCrosshairTool(event.getItemStack())) return;
        if (event.getLevel().isClientSide()) {
            PacketDistributor.sendToServer(new CrosshairUsePayload());
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    /**
     * 左键方块：只阻止挖掘，不做任何模式切换。
     * 模式切换由客户端 ClientInputEvents 发 payload、服务端 handle 处理。
     */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        ItemStack stack = player.getMainHandItem();
        if (!isCrosshairTool(stack)) return;
        event.setCanceled(true);
    }
}
