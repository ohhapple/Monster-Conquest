package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.block.TradeStationService;
import com.ohhapple.monster_conquest.item.BackpackItem;
import com.ohhapple.monster_conquest.network.serverbound.trade.BuyTradeOfferPayload;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易站 GUI：两列出售清单（子弹 / 枪械 / 工具），每页两列八行，顶部选数量与翻页。
 *   是否买得起只作显示用；真正扣材料、发物品都在服务端校验。
 */
public class TradeStationScreen extends Screen {

    private static final int PANEL_W = 430;
    private static final int PANEL_H = 238;
    private static final int COL_W = 203;
    private static final int ROW_H = 24;
    private static final int ROWS = 8;
    private static final int PAGE_SIZE = ROWS * 2;

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
    private int pageIndex = 0;
    private Button pagePrev;
    private Button pageNext;

    private int panelX, panelY;
    private GuiViewport viewport = new GuiViewport(1.0f, 0, 0);

    public TradeStationScreen(BlockPos pos) {
        super(Component.translatable("gui.monster_conquest.trade.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        viewport = GuiViewport.fit(width, height, PANEL_W, PANEL_H, 8);
        panelX = 0;
        panelY = 0;

        // 数量选择：×1 / ×8 / ×64
        qtyButtons.clear();
        int[] qtys = {1, 8, 64};
        for (int i = 0; i < qtys.length; i++) {
            final int q = qtys[i];
            Button b = Button.builder(Component.literal("×" + q), btn -> quantity = q)
                    .bounds(panelX + 64 + i * 34, panelY + 6, 30, 16)
                    .build();
            qtyButtons.add(this.addRenderableWidget(b));
        }

        pagePrev = this.addRenderableWidget(
                Button.builder(Component.literal("<"), btn -> changePage(-1))
                        .bounds(panelX + 318, panelY + 6, 20, 16)
                        .tooltip(Tooltip.create(Component.translatable(
                                "gui.monster_conquest.trade.prev_page")))
                        .build());
        pageNext = this.addRenderableWidget(
                Button.builder(Component.literal(">"), btn -> changePage(1))
                        .bounds(panelX + 402, panelY + 6, 20, 16)
                        .tooltip(Tooltip.create(Component.translatable(
                                "gui.monster_conquest.trade.next_page")))
                        .build());

        buyButtons.clear();
        for (int i = 0; i < offers.size(); i++) {
            final int offerIndex = i;
            int slot = i % PAGE_SIZE;
            int col = slot / ROWS;
            int row = slot % ROWS;
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

    private int pageCount() {
        return Math.max(1, (offers.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void changePage(int delta) {
        int next = pageIndex + delta;
        if (next < 0 || next >= pageCount()) return;
        pageIndex = next;
    }

    private void syncPageWidgets() {
        int pageStart = pageIndex * PAGE_SIZE;
        int pageEnd = Math.min(offers.size(), pageStart + PAGE_SIZE);
        for (int i = 0; i < buyButtons.size(); i++) {
            buyButtons.get(i).visible = i >= pageStart && i < pageEnd;
        }
        boolean paged = pageCount() > 1;
        pagePrev.visible = paged;
        pageNext.visible = paged;
        pagePrev.active = paged && pageIndex > 0;
        pageNext.active = paged && pageIndex + 1 < pageCount();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int localMouseX = viewport.localX(mouseX);
        int localMouseY = viewport.localY(mouseY);
        g.fill(0, 0, width, height, 0xB0000000);
        viewport.push(g);
        syncPageWidgets();
        super.render(g, localMouseX, localMouseY, partialTick);

        g.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, COL_BORDER);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);
        g.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 8, COL_TEXT);
        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.trade.qty_label"),
                panelX + 8, panelY + 10, COL_TEXT_DIM);
        if (pageCount() > 1) {
            g.drawCenteredString(this.font,
                    Component.translatable("gui.monster_conquest.trade.page",
                            pageIndex + 1, pageCount()),
                    panelX + 370, panelY + 10, COL_TEXT);
        }

        int pageStart = pageIndex * PAGE_SIZE;
        int pageEnd = Math.min(offers.size(), pageStart + PAGE_SIZE);
        for (int i = pageStart; i < pageEnd; i++) {
            renderOffer(g, i, localMouseX, localMouseY);
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
        viewport.pop(g);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(viewport.localX(mouseX), viewport.localY(mouseY), button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return super.mouseReleased(viewport.localX(mouseX), viewport.localY(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        return super.mouseDragged(viewport.localX(mouseX), viewport.localY(mouseY), button,
                viewport.localDelta(dragX), viewport.localDelta(dragY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        return super.mouseScrolled(viewport.localX(mouseX), viewport.localY(mouseY),
                scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(viewport.localX(mouseX), viewport.localY(mouseY));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 全屏遮罩由 render() 自己绘制，避免缩放后的原版背景越出面板。
    }

    private void renderOffer(GuiGraphics g, int index, int mouseX, int mouseY) {
        TradeStationService.Offer offer = offers.get(index);
        int slot = index - pageIndex * PAGE_SIZE;
        int col = slot / ROWS;
        int row = slot % ROWS;
        int colX = panelX + 8 + col * (COL_W + 8);
        int rowY = panelY + 30 + row * ROW_H;

        g.fill(colX, rowY, colX + COL_W, rowY + ROW_H - 2, 0x30000000);
        ItemStack result = new ItemStack(offer.item(), Math.min(offer.count(), 64));
        g.renderItem(result, colX + 3, rowY + 3);

        // 名称按实际像素宽度截断；缩放坐标系下不依赖 scissor。
        Component name = new ItemStack(offer.item()).getHoverName();
        String clippedName = this.font.plainSubstrByWidth(name.getString(), 72);
        g.drawString(this.font, Component.literal(clippedName),
                colX + 22, rowY + 7, COL_TEXT);

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
        n += BackpackItem.countItem(mc.player, item);
        return n;
    }
}
