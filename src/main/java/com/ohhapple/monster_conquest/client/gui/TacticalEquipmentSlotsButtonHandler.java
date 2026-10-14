package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.serverbound.gun.OpenTacticalEquipmentSlotsPayload;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** 在生存 / 创造背包界面左侧添加"战术装备栏"按钮（原版按钮控件）。 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class TacticalEquipmentSlotsButtonHandler {

    private TacticalEquipmentSlotsButtonHandler() {}

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof InventoryScreen) && !(screen instanceof CreativeModeInventoryScreen)) return;
        if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) return;

        int x = containerScreen.getGuiLeft() - 62 - 6;
        int y = containerScreen.getGuiTop() + 6;

        event.addListener(new Button.Builder(
                Component.translatable("gui.monster_conquest.tactical_equipment_slots.button"),
                b -> PacketDistributor.sendToServer(new OpenTacticalEquipmentSlotsPayload()))
                .pos(x, y)
                .size(62, 20)
                .build());
    }
}
