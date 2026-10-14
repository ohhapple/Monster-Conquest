package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.item.AmmoBoxItem;
import com.ohhapple.monster_conquest.network.serverbound.gun.OpenAmmoBoxFromSlotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 在背包 / 战术装备栏界面里右键弹药箱 → 打开弹药箱容器，方便快速补充弹药。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class AmmoBoxOpenHandler {

    private AmmoBoxOpenHandler() {}

    @SubscribeEvent
    public static void onMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1) return;
        if (!(event.getScreen() instanceof AbstractContainerScreen<?> screen)) return;

        Slot slot = screen.getSlotUnderMouse();
        if (slot == null) return;
        ItemStack stack = slot.getItem();
        if (!(stack.getItem() instanceof AmmoBoxItem)) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        int source;
        int index;
        if (slot.container == player.getInventory()) {
            source = TacticalEquipmentSlotsData.SOURCE_INVENTORY;
            index = slot.getContainerSlot();
        } else if (screen instanceof TacticalEquipmentSlotsScreen
                && slot.getContainerSlot() < TacticalEquipmentSlotsData.SLOT_COUNT) {
            source = TacticalEquipmentSlotsData.SOURCE_TACTICAL_SLOTS;
            index = slot.getContainerSlot();
        } else {
            return;
        }

        PacketDistributor.sendToServer(new OpenAmmoBoxFromSlotPayload(source, index));
        event.setCanceled(true);
    }
}
