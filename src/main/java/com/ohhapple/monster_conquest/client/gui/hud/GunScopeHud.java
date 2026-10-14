package com.ohhapple.monster_conquest.client.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.gun.GunCategory;
import com.ohhapple.monster_conquest.gun.GunItem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 狙击枪开镜时绘制原版望远镜镜筒界面（与望远镜一致的遮罩和圆形视野）。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class GunScopeHud {

    private GunScopeHud() {}

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "gun_scope");

    private static final ResourceLocation SCOPE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/misc/spyglass_scope.png");

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, LAYER_ID, GunScopeHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!mc.options.getCameraType().isFirstPerson()) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        if (gun.spec().category() != GunCategory.SNIPER) return;
        if (!player.isUsingItem()) return;

        // 与 Gui#renderSpyglassOverlay 相同的绘制方式
        float size = (float) Math.min(g.guiWidth(), g.guiHeight());
        if (size <= 0.0f) return;
        float scale = Math.min((float) g.guiWidth() / size, (float) g.guiHeight() / size) * 1.125f;
        int width = Mth.floor(size * scale);
        int height = Mth.floor(size * scale);
        int left = (g.guiWidth() - width) / 2;
        int top = (g.guiHeight() - height) / 2;
        int right = left + width;
        int bottom = top + height;

        RenderSystem.enableBlend();
        g.blit(SCOPE_TEXTURE, left, top, -90, 0.0F, 0.0F, width, height, width, height);
        RenderSystem.disableBlend();

        g.fill(RenderType.guiOverlay(), 0, bottom, g.guiWidth(), g.guiHeight(), -90, -16777216);
        g.fill(RenderType.guiOverlay(), 0, 0, g.guiWidth(), top, -90, -16777216);
        g.fill(RenderType.guiOverlay(), 0, top, left, bottom, -90, -16777216);
        g.fill(RenderType.guiOverlay(), right, top, g.guiWidth(), bottom, -90, -16777216);
    }
}
