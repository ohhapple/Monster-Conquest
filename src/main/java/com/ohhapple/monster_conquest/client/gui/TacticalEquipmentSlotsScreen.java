package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.menu.TacticalEquipmentSlotsMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 战术装备栏界面：18 个槽位（6 × 3）+ 玩家背包。 */
public class TacticalEquipmentSlotsScreen extends AbstractContainerScreen<TacticalEquipmentSlotsMenu> {

    public TacticalEquipmentSlotsScreen(TacticalEquipmentSlotsMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = 84;
    }

    /**
     * 原版各容器界面都是在自己重写的 render 里手动调用 renderTooltip ——
     *   AbstractContainerScreen 本身不会渲染物品提示，所以这里必须显式调用，
     *   否则鼠标悬停在物品上不会显示详情。
     */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF555555);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);

        // 战术装备栏槽（6 列 × 3 行，共 18 格）
        for (int i = 0; i < 18; i++) {
            int col = i % 6;
            int row = i / 6;
            drawSlot(g, x + 25 + col * 20, y + 17 + row * 20);
        }
        // 玩家背包槽
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 7 + col * 18, y + 87 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + 7 + col * 18, y + 145);
        }
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
