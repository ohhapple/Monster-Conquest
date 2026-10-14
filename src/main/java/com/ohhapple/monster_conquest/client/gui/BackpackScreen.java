package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.item.BackpackItem;
import com.ohhapple.monster_conquest.menu.BackpackMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * 背包界面：8 排 × 9 格（72 格）+ 玩家背包（自绘半透明面板 + 槽位底板）。
 *   配色与募兵站 / 交易站一致：半透明深色面板，能看到后面的世界，不再整屏糊黑。
 */
public class BackpackScreen extends AbstractContainerScreen<BackpackMenu> {

    private static final int COL_BG = 0xD0101418;
    private static final int COL_BORDER = 0xFF3C4650;
    private static final int COL_SLOT_BG = 0x90232A32;
    private static final int COL_SLOT_EDGE = 0xB03C4650;

    public BackpackScreen(BackpackMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 18 + BackpackItem.ROWS * 18 + 14 + 3 * 18 + 4 + 18 + 8;
        this.inventoryLabelY = 18 + BackpackItem.ROWS * 18 + 16;
        this.titleLabelY = 6;
    }

    /**
     * 不再手动调用 renderBackground：super.render() 内部本来就会调一次，
     *   重复调用会把世界背景（模糊 + 压暗）画两遍，表现为"整屏全黑"。
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

        // 面板
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, COL_BORDER);
        g.fill(x, y, x + imageWidth, y + imageHeight, COL_BG);

        // 槽位底板
        for (Slot slot : this.menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, COL_SLOT_BG);
            g.fill(sx - 1, sy - 1, sx + 17, sy, COL_SLOT_EDGE);
            g.fill(sx - 1, sy - 1, sx, sy + 17, COL_SLOT_EDGE);
        }
    }
}
