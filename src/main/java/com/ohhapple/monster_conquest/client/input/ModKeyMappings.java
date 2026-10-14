package com.ohhapple.monster_conquest.client.input;

import com.mojang.blaze3d.platform.InputConstants;
import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class ModKeyMappings {

    public static final KeyMapping OPEN_FACTION_GUI = new KeyMapping(
            "key.monster_conquest.open_faction_gui",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_N,
            "key.categories.monster_conquest");

    public static final KeyMapping COMMAND_WHEEL = new KeyMapping(
            "key.monster_conquest.command_wheel",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.monster_conquest");

    /** 打开战术装备栏（默认 H，可在按键设置里改）。 */
    public static final KeyMapping OPEN_EQUIPMENT = new KeyMapping(
            "key.monster_conquest.open_tactical_equipment_slots",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.monster_conquest");

    /** 打开战术装备栏里的背包（默认 B，可在按键设置里改）。 */
    public static final KeyMapping OPEN_BACKPACK = new KeyMapping(
            "key.monster_conquest.open_backpack",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.monster_conquest");

    // ===================== RTS 快捷按键 =====================

    public static final KeyMapping RTS_RIDE_CAVALRY = new KeyMapping(
            "key.monster_conquest.rts.ride_cavalry",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Z,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_RIDE_INFANTRY = new KeyMapping(
            "key.monster_conquest.rts.ride_infantry",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_FORMATION_1 = new KeyMapping(
            "key.monster_conquest.rts.formation_1",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_1,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_FORMATION_2 = new KeyMapping(
            "key.monster_conquest.rts.formation_2",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_2,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_FORMATION_3 = new KeyMapping(
            "key.monster_conquest.rts.formation_3",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_3,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_FORMATION_4 = new KeyMapping(
            "key.monster_conquest.rts.formation_4",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_4,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_FORMATION_5 = new KeyMapping(
            "key.monster_conquest.rts.formation_5",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_5,
            "key.categories.monster_conquest");

    public static final KeyMapping RTS_TOGGLE = new KeyMapping(
            "key.monster_conquest.rts.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.monster_conquest");

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_FACTION_GUI);
        event.register(COMMAND_WHEEL);
        event.register(OPEN_EQUIPMENT);
        event.register(OPEN_BACKPACK);
        event.register(RTS_RIDE_CAVALRY);
        event.register(RTS_RIDE_INFANTRY);
        event.register(RTS_FORMATION_1);
        event.register(RTS_FORMATION_2);
        event.register(RTS_FORMATION_3);
        event.register(RTS_FORMATION_4);
        event.register(RTS_FORMATION_5);
        event.register(RTS_TOGGLE);
    }
}
