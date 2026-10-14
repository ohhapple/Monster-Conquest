package com.ohhapple.monster_conquest.client.gui.hud;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.gun.GunClientState;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.gun.GunCombat;
import com.ohhapple.monster_conquest.gun.GunAmmoType;
import com.ohhapple.monster_conquest.gun.GunItem;
import com.ohhapple.monster_conquest.gun.GunSpec;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import org.jetbrains.annotations.Nullable;

/**
 * 手持枪械时在屏幕右下角显示"弹匣 / 备弹"和换弹进度（射击游戏式布局）。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class GunStatusHud {

    private GunStatusHud() {}

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "gun_status");

    /** 距右下角的边距。 */
    private static final int MARGIN_X = 18;
    private static final int MARGIN_Y = 44;

    /** 备弹数每客户端 tick 只重算一次（它会扫物品栏 + 战术装备栏 + 背包，每帧重算是白给的开销）。 */
    private static int reserveCache = -1;
    @Nullable private static GunAmmoType reserveCacheType;
    private static long reserveCacheTick = Long.MIN_VALUE;

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER_ID, GunStatusHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) return;

        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        GunSpec spec = gun.spec();

        int mag = GunCombat.getAmmo(stack, spec);
        boolean infinite = !CombatConfig.gunRequireAmmo || p.isCreative();

        Component text;
        if (infinite) {
            text = Component.translatable("hud.monster_conquest.gun.counter",
                    mag, Component.literal("∞"));
        } else {
            int reserve = reserveCount(p, spec.ammoType());
            text = Component.translatable("hud.monster_conquest.gun.counter", mag, reserve);
        }

        int right = g.guiWidth() - MARGIN_X;
        int y = g.guiHeight() - MARGIN_Y;
        int color = mag <= 0 && !infinite ? 0xFF5555 : 0xFFFFFF;
        g.drawString(mc.font, text, right - mc.font.width(text), y, color, true);

        if (GunClientState.isReloading()) {
            int barW = 60;
            int barX = right - barW;
            int barY = y + 12;
            g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 5, 0x80000000);
            g.fill(barX, barY, barX + (int) (barW * GunClientState.reloadProgress()), barY + 4,
                    0xFFFFAA33);
            Component reloading = Component.translatable("hud.monster_conquest.gun.reloading")
                    .withStyle(ChatFormatting.YELLOW);
            g.drawString(mc.font, reloading, right - mc.font.width(reloading), barY + 8, 0xFFFFFF, true);
        }
    }

    private static int reserveCount(LocalPlayer p, GunAmmoType type) {
        long now = p.level().getGameTime();
        if (reserveCache >= 0 && reserveCacheType == type && reserveCacheTick == now) {
            return reserveCache;
        }
        int count = TacticalEquipmentSlotsData.countReserve(p, type);
        reserveCache = count;
        reserveCacheType = type;
        reserveCacheTick = now;
        return count;
    }
}
