package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.block.TradeStationService;
import com.ohhapple.monster_conquest.network.serverbound.trade.BuyTradeOfferPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易站 GUI：两列出售清单（子弹 / 枪械 / 战术工具），顶部选数量（×1/×8/×64）。
 *   是否买得起只作显示用；真正扣材料、发物品都在服务端校验。
 */
public class TradeStationScreen extends Screen {

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 238;
    private static final int COL_W = 203;
    private static final int ROW_H = 24;
    private static final int ROWS = 8;

    private static final int COL_BG = 0xD0101418;
    private static final int COL_BORDER = 0xFF3C4650;
    private static final int COL_TEXT = 0xFFD8DEE6;
    private static final int COL_TEXT_DIM = 0xFF8C98A4;
    private static final int COL_OK = 0xFF7ED07E;
    private static final int COL_BAD = 0xFFE07E7E;

    private final BlockPos pos;
    private final List<TradeStationService.Offer> offers = TradeStationService.OFFERS;
    private final List<Button> buyButtons = new ArrayList<>();
    private final List<Button> qtyButtons = new ArrayList<>();

    private int quantity = 1;

    private int panelX, panelY;

    public TradeStationScreen(BlockPos pos) {
        super(Component.translatable("gui.monster_conquest.trade.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;

        // 数量选择：×1 / ×8 / ×64
        qtyButtons.clear();
        int[] qtys = {1, 8, 64};
        for (int i = 0; i < qtys.length; i++) {
            final int q = qtys[i];
            Button b = Button.builder(Component.literal("×" + q), btn -> quantity = q)
                    .bounds(panelX + PANEL_W - 8 - (qtys.length - i) * 34, panelY + 6, 30, 16)
                    .build();
            qtyButtons.add(this.addRenderableWidget(b));
        }

        buyButtons.clear();
        for (int i = 0; i < offers.size(); i++) {
            final int offerIndex = i;
            int col = i / ROWS;
            int row = i % ROWS;
            int colX = panelX + 8 + col * (COL_W + 8);
            int rowY = panelY + 30 + row * ROW_H;
            Button b = Button.builder(
                            Component.translatable("gui.monster_conquest.trade.buy"),
                            // Shift + 点击 = 直接买 ×64（方便子弹这种大批量采购）
                            btn -> buy(offerIndex, Screen.hasShiftDown() ? 64 : quantity))
                    .bounds(colX + COL_W - 47, rowY + 4, 45, 16)
                    .build();
            buyButtons.add(this.addRenderableWidget(b));
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void buy(int offerIndex, int qty) {
        PacketDistributor.sendToServer(new BuyTradeOfferPayload(pos, offerIndex, qty));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, COL_BORDER);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);
        g.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 8, COL_TEXT);
        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.trade.qty_label"),
                panelX + 8, panelY + 10, COL_TEXT_DIM);

        for (int i = 0; i < offers.size(); i++) {
            renderOffer(g, i, mouseX, mouseY);
        }
        for (int i = 0; i < qtyButtons.size(); i++) {
            qtyButtons.get(i).active = switch (i) {
                case 0 -> quantity != 1;
                case 1 -> quantity != 8;
                default -> quantity != 64;
            };
        }
        // 购买按钮上直接显示当前数量（Shift 点击 = ×64）
        for (Button b : buyButtons) {
            b.setMessage(Component.translatable("gui.monster_conquest.trade.buy_qty", quantity));
        }

        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.trade.hint"),
                panelX + 8, panelY + PANEL_H - 12, COL_TEXT_DIM);
    }

    private void renderOffer(GuiGraphics g, int index, int mouseX, int mouseY) {
        TradeStationService.Offer offer = offers.get(index);
        int col = index / ROWS;
        int row = index % ROWS;
        int colX = panelX + 8 + col * (COL_W + 8);
        int rowY = panelY + 30 + row * ROW_H;

        g.fill(colX, rowY, colX + COL_W, rowY + ROW_H - 2, 0x30000000);
        ItemStack result = new ItemStack(offer.item(), Math.min(offer.count(), 64));
        g.renderItem(result, colX + 3, rowY + 3);

        // 名称（超宽裁剪）
        Component name = new ItemStack(offer.item()).getHoverName();
        g.enableScissor(colX + 22, rowY + 4, colX + 96, rowY + 16);
        g.drawString(this.font, name, colX + 22, rowY + 7, COL_TEXT);
        g.disableScissor();

        // 价格图标（带所需数量）
        int px = colX + 98;
        boolean affordable = true;
        for (int i = 0; i < offer.price().size() && i < 3; i++) {
            TradeStationService.Price price = offer.price().get(i);
            int need = price.count() * quantity;
            ItemStack costStack = new ItemStack(price.item(), Math.min(need, 64));
            int ix = px + i * 17;
            g.renderItem(costStack, ix, rowY + 3);
            g.renderItemDecorations(this.font, costStack, ix, rowY + 3);
            if (countItems(price.item()) < need) affordable = false;
        }
        if (affordable) {
            g.drawString(this.font, "✓", colX + 98 + Math.min(3, offer.price().size()) * 17 + 2,
                    rowY + 8, COL_OK);
        }

        if (index < buyButtons.size()) {
            buyButtons.get(index).active = affordable;
        }
        updateBuyTooltip(index, offer, affordable);
    }

    private void updateBuyTooltip(int index, TradeStationService.Offer offer, boolean affordable) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < offer.price().size(); i++) {
            TradeStationService.Price price = offer.price().get(i);
            int need = price.count() * quantity;
            if (i > 0) sb.append(" + ");
            sb.append(new ItemStack(price.item()).getHoverName().getString())
              .append(" ×").append(need)
              .append("（持有 ").append(countItems(price.item())).append("）");
        }
        Component tip = Component.translatable("gui.monster_conquest.trade.tip_buy",
                new ItemStack(offer.item()).getHoverName(), quantity, sb.toString());
        if (index < buyButtons.size()) {
            buyButtons.get(index).setTooltip(Tooltip.create(tip));
        }
    }

    private int countItems(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (!s.isEmpty() && s.is(item)) n += s.getCount();
        }
        // 背包（物品）里的物品也算
        n += com.ohhapple.monster_conquest.item.BackpackItem.countItem(mc.player, item);
        return n;
    }
}
