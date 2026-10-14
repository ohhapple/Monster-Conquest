package com.ohhapple.monster_conquest.client.gui.hud;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.item.ControlToolItem;
import com.ohhapple.monster_conquest.item.HatredWandItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.util.FactionDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 手持仇恨权杖 / 控制工具 / 收纳工具时，在屏幕右上角显示当前设置和选中信息。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class ToolStatusHud {

    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "tool_status");

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER_ID, ToolStatusHud::render);
    }

    private static void render(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        ItemStack stack = mc.player.getMainHandItem();
        List<Component> lines = new ArrayList<>();

        if (stack.getItem() instanceof HatredWandItem) {
            buildHatredWand(stack, lines);
        } else if (stack.getItem() instanceof ControlToolItem) {
            buildControlTool(stack, lines);
        } else if (stack.getItem() instanceof RetrievalToolItem) {
            buildRetrievalTool(stack, lines);
        }

        if (lines.isEmpty()) return;

        int x = g.guiWidth() - 4;
        int y = 4;
        for (Component line : lines) {
            int w = mc.font.width(line);
            g.drawString(mc.font, line, x - w, y, 0xFFFFFF, true);
            y += mc.font.lineHeight + 1;
        }
    }

    // ===================== 仇恨权杖 =====================

    private static void buildHatredWand(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("hud.monster_conquest.hatred_wand.title")
                .withStyle(ChatFormatting.GOLD));

        boolean faction = HatredWandItem.isFactionMode(stack);
        lines.add(Component.translatable("hud.monster_conquest.mode",
                Component.translatable(faction
                        ? "item.monster_conquest.hatred_wand.mode.faction"
                        : "item.monster_conquest.hatred_wand.mode.army")));

        if (faction) {
            ResourceLocation f = HatredWandItem.getSelectedFaction(stack);
            lines.add(Component.translatable("hud.monster_conquest.selected",
                    f == null
                            ? Component.translatable("item.monster_conquest.hatred_wand.none")
                            : FactionDisplay.displayName(f)));
        } else {
            UUID a = HatredWandItem.getSelectedArmy(stack);
            lines.add(Component.translatable("hud.monster_conquest.selected",
                    a == null
                            ? Component.translatable("item.monster_conquest.hatred_wand.none")
                            : Component.literal(a.toString().substring(0, 8) + "...")));
        }
    }

    // ===================== 控制工具 =====================
    private static void buildControlTool(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("hud.monster_conquest.control_tool.title")
                .withStyle(ChatFormatting.AQUA));

        ControlToolItem.Mode mode = ControlToolItem.getMode(stack);
        lines.add(Component.translatable("hud.monster_conquest.mode", mode.getDisplayName()));

        // 当前目标小队（与轮盘 selectedSquad 分离，是控制工具的独立字段）
        int squad = 0;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                    .getIfPresent(mc.player.getUUID());
            if (state != null) squad = state.getControlToolSelectedSquad();
        }
        Component squadText;
        if (squad == com.ohhapple.monster_conquest.player
                .PlayerCommandState.CONTROL_TOOL_LONE) {
            // 中键选中的是无小队单兵：显示"单兵"，不要显示成"全选"
            squadText = Component.translatable("hud.monster_conquest.squad.lone");
        } else if (squad == 0) {
            squadText = Component.translatable("hud.monster_conquest.squad.all");
        } else {
            squadText = Component.literal(String.valueOf(squad));
        }
        lines.add(Component.translatable("hud.monster_conquest.squad.target", squadText));

        UUID sel = ControlToolItem.getSelected(stack);
        if (sel == null) {
            lines.add(Component.translatable("hud.monster_conquest.selected",
                    Component.translatable("hud.monster_conquest.none")));
        } else {
            lines.add(Component.translatable("hud.monster_conquest.selected",
                    Component.literal(sel.toString().substring(0, 8) + "...")));
        }

        ResourceLocation f = ControlToolItem.getSelectedFaction(stack);
        if (f != null) {
            lines.add(Component.translatable("hud.monster_conquest.faction",
                    FactionDisplay.displayName(f)));
        }
    }

    // ===================== 收纳工具 =====================

    private static void buildRetrievalTool(ItemStack stack, List<Component> lines) {
        lines.add(Component.translatable("hud.monster_conquest.retrieval_tool.title")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        RetrievalToolItem.Mode mode = RetrievalToolItem.getMode(stack);
        lines.add(Component.translatable("hud.monster_conquest.mode", mode.getDisplayName()));
    }
}
