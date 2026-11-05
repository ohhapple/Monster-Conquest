package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * UnitScreen owns the complete modal rendering surface. HUD layers, screen
 * layers and post-render hooks are skipped while it is open, without touching
 * any mod's settings or identifying individual recipe viewers.
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class UnitScreenGuiIsolation {

    private UnitScreenGuiIsolation() {}

    @SubscribeEvent
    public static void hideHud(RenderGuiEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof UnitScreen) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void isolateScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (Minecraft.getInstance().screen instanceof UnitScreen) {
            event.setCanceled(true);
        }
    }

    /**
     * Render.Post cannot be cancelled. Redraw our complete surface after all
     * post listeners so any overlay that already committed pixels is covered.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restoreAfterScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen active = Minecraft.getInstance().screen;
        if (event.getScreen() == active
                && active instanceof UnitScreen unitScreen) {
            unitScreen.render(event.getGuiGraphics(),
                    event.getMouseX(), event.getMouseY(), event.getPartialTick());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void isolateTooltipPre(RenderTooltipEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof UnitScreen) {
            event.getGraphics().enableScissor(0, 0, 0, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void restoreTooltipPre(RenderTooltipEvent.Pre event) {
        if (Minecraft.getInstance().screen instanceof UnitScreen) {
            event.getGraphics().disableScissor();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void isolateTooltipColor(RenderTooltipEvent.Color event) {
        if (Minecraft.getInstance().screen instanceof UnitScreen) {
            event.getGraphics().enableScissor(0, 0, 0, 0);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void restoreTooltipColor(RenderTooltipEvent.Color event) {
        if (Minecraft.getInstance().screen instanceof UnitScreen) {
            event.getGraphics().disableScissor();
        }
    }
}
