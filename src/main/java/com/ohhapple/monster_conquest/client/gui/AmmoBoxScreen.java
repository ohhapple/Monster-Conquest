package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.menu.AmmoBoxMenu;
import com.ohhapple.monster_conquest.item.AmmoBoxItem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** 弹药箱界面：3 排 27 格弹药 + 玩家背包。 */
public class AmmoBoxScreen extends AbstractContainerScreen<AmmoBoxMenu> {

    public AmmoBoxScreen(AmmoBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 168;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /** 显式渲染物品提示（AbstractContainerScreen 不会自动调用）。 */
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        // 面板
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF555555);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);

        // 弹药槽
        for (int row = 0; row < AmmoBoxItem.ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 7 + col * 18, y + 17 + row * 18);
            }
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
