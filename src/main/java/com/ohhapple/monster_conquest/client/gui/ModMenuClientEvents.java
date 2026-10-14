package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.menu.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** 注册容器界面。 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class ModMenuClientEvents {

    private ModMenuClientEvents() {}

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.AMMO_BOX.get(), AmmoBoxScreen::new);
        event.register(ModMenus.TACTICAL_EQUIPMENT_SLOTS.get(), TacticalEquipmentSlotsScreen::new);
        event.register(ModMenus.BACKPACK.get(), BackpackScreen::new);
        event.register(ModMenus.UNIT.get(), UnitScreen::new);
    }
}
