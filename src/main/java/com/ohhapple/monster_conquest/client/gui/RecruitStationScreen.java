package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.army.RecruitableSoldiers;
import com.ohhapple.monster_conquest.config.RecruitConfig;
import com.ohhapple.monster_conquest.item.BackpackItem;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.serverbound.recruit.RecruitSoldierPayload;
import com.ohhapple.monster_conquest.network.serverbound.recruit.UpgradeSoldierPayload;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 募兵站 GUI：左侧选兵种，右侧按等级招募 / 升级（消耗材料）。
 *   材料是否够只作显示用；真正扣材料、生成士兵都在服务端校验。
 */
public class RecruitStationScreen extends Screen {

    private static final int PANEL_W = 400;
    private static final int PANEL_H = 190;
    private static final int LIST_W = 112;
    private static final int ROW_H = 12;
    private static final int TIER_ROW_H = 26;
    private static final int TIER_UNIT_ICON_X = 3;
    private static final int TIER_LABEL_X = 22;
    private static final int TIER_BASE_ICON_X = 48;
    private static final int TIER_PLUS_X = 66;
    private static final int TIER_EXTRA_ICON_X = 74;
    private static final int TIER_OWNED_X = 95;

    private static final int COL_BG = 0xD0101418;
    private static final int COL_BORDER = 0xFF3C4650;
    private static final int COL_TEXT = 0xFFD8DEE6;
    private static final int COL_TEXT_DIM = 0xFF8C98A4;
    private static final int COL_SELECTED = 0x805C86C0;
    private static final int COL_OK = 0xFF7ED07E;
    private static final int COL_BAD = 0xFFE07E7E;

    private final BlockPos pos;
    private final List<RecruitableSoldiers.Entry> entries = RecruitableSoldiers.ALL;
    private final List<Button> recruitButtons = new ArrayList<>();
    private final List<Button> upgradeButtons = new ArrayList<>();

    private int selected = 0;
    private int listScroll = 0;
    /** 是否正在拖列表滚动条；拖动时鼠标与滑块顶部的偏移。 */
    private boolean draggingBar = false;
    private double barDragOffset = 0.0;

    private int panelX, panelY;
    private int listX, listY, listH;
    private int rightX, rightY, rightW;
    private GuiViewport viewport = new GuiViewport(1.0f, 0, 0);

    public RecruitStationScreen(BlockPos pos) {
        super(Component.translatable("gui.monster_conquest.recruit.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        viewport = GuiViewport.fit(width, height, PANEL_W, PANEL_H, 8);
        panelX = 0;
        panelY = 0;
        listX = panelX + 8;
        listY = panelY + 30;
        listH = PANEL_H - 30 - 22;
        rightX = panelX + 8 + LIST_W + 10;
        rightY = panelY + 30;
        rightW = PANEL_W - (8 + LIST_W + 10) - 8;
        clampScroll();

        recruitButtons.clear();
        upgradeButtons.clear();
        for (int t = 0; t < 5; t++) {
            final int tier = t;
            int by = rightY + t * TIER_ROW_H + 5;

            Button recruitBtn = Button.builder(
                            Component.translatable("gui.monster_conquest.recruit.button"),
                            b -> recruit(tier))
                    .bounds(rightX + rightW - 110, by, 52, 16)
                    .build();
            recruitButtons.add(this.addRenderableWidget(recruitBtn));

            if (t >= 1) {
                Button upgradeBtn = Button.builder(
                                Component.translatable("gui.monster_conquest.recruit.upgrade"),
                                b -> upgrade(tier))
                        .bounds(rightX + rightW - 54, by, 52, 16)
                        .build();
                upgradeButtons.add(this.addRenderableWidget(upgradeBtn));
            } else {
                upgradeButtons.add(null);
            }
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void recruit(int tier) {
        PacketDistributor.sendToServer(new RecruitSoldierPayload(pos, selected, tier));
    }

    private void upgrade(int tier) {
        PacketDistributor.sendToServer(new UpgradeSoldierPayload(pos, selected, tier));
    }

    private int listContentHeight() {
        return entries.size() * ROW_H;
    }

    private void clampScroll() {
        listScroll = Mth.clamp(listScroll, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, listContentHeight() - listH);
    }

    private int barHeight() {
        int content = listContentHeight();
        return content <= 0 ? listH : Math.max(10, listH * listH / content);
    }

    private int barY() {
        int max = maxScroll();
        if (max <= 0) return listY;
        return listY + (listH - barHeight()) * listScroll / max;
    }

    /** 鼠标是否落在列表滚动条上（判定区比画出来的滑块宽一些，方便按住）。 */
    private boolean inScrollbar(double mouseX, double mouseY) {
        int barX = listX + LIST_W - 3;
        return maxScroll() > 0
                && mouseX >= barX - 3 && mouseX <= barX + 5
                && mouseY >= listY && mouseY <= listY + listH;
    }

    /** 把滚动条拖到鼠标所在的纵向位置。 */
    private void applyBarDrag(double mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int barHeight = barHeight();
        int track = Math.max(1, listH - barHeight);
        double y = Mth.clamp(mouseY - barDragOffset, listY, listY + track);
        listScroll = Mth.clamp((int) Math.round((y - listY) * max / (double) track), 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = viewport.localX(mouseX);
        mouseY = viewport.localY(mouseY);
        if (mouseX >= listX && mouseX <= listX + LIST_W && mouseY >= listY && mouseY <= listY + listH) {
            listScroll -= (int) Math.round(scrollY * ROW_H * 2);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        mouseX = viewport.localX(mouseX);
        mouseY = viewport.localY(mouseY);
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        // 滚动条：点滑块直接拖，点轨道跳到该位置
        if (button == 0 && inScrollbar(mouseX, mouseY)) {
            int barHeight = barHeight();
            int barY = barY();
            if (mouseY >= barY && mouseY <= barY + barHeight) {
                barDragOffset = mouseY - barY;
            } else {
                barDragOffset = barHeight / 2.0;
                applyBarDrag(mouseY);
            }
            draggingBar = true;
            return true;
        }
        if (button == 0
                && mouseX >= listX && mouseX <= listX + LIST_W
                && mouseY >= listY && mouseY <= listY + listH) {
            int index = (int) ((mouseY - listY + listScroll) / ROW_H);
            if (index >= 0 && index < entries.size()) {
                selected = index;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        mouseX = viewport.localX(mouseX);
        mouseY = viewport.localY(mouseY);
        dragX = viewport.localDelta(dragX);
        dragY = viewport.localDelta(dragY);
        if (draggingBar) {
            applyBarDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mouseX = viewport.localX(mouseX);
        mouseY = viewport.localY(mouseY);
        if (button == 0 && draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(viewport.localX(mouseX), viewport.localY(mouseY));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 全屏遮罩由 render() 自己绘制，避免缩放后的原版背景越出面板。
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int localMouseX = viewport.localX(mouseX);
        int localMouseY = viewport.localY(mouseY);
        g.fill(0, 0, width, height, 0xB0000000);
        viewport.push(g);
        syncTierWidgets(entries.get(selected));
        super.render(g, localMouseX, localMouseY, partialTick);

        g.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, COL_BORDER);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);
        g.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 8, COL_TEXT);
        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.recruit.list_title"),
                listX, panelY + 19, COL_TEXT_DIM);

        renderSpeciesList(g, localMouseX, localMouseY);
        renderTierRows(g);

        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.recruit.hint"),
                panelX + 8, panelY + PANEL_H - 14, COL_TEXT_DIM);
        viewport.pop(g);
    }

    private void renderSpeciesList(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(listX, listY, listX + LIST_W, listY + listH, 0x40000000);
        for (int i = 0; i < entries.size(); i++) {
            int rowY = listY + i * ROW_H - listScroll;
            if (rowY + ROW_H < listY || rowY > listY + listH) continue;
            boolean hovered = mouseX >= listX && mouseX <= listX + LIST_W
                    && mouseY >= Math.max(rowY, listY) && mouseY < Math.min(rowY + ROW_H, listY + listH);
            if (i == selected) {
                g.fill(listX, rowY, listX + LIST_W, rowY + ROW_H, COL_SELECTED);
            } else if (hovered) {
                g.fill(listX, rowY, listX + LIST_W, rowY + ROW_H, 0x30FFFFFF);
            }
            Component name = Component.translatable("entity.monster_conquest." + entries.get(i).id());
            g.drawString(this.font, name, listX + 4, rowY + 2, COL_TEXT);
        }
        if (maxScroll() > 0) {
            int barX = listX + LIST_W - 3;
            g.fill(barX, listY, barX + 3, listY + listH, 0x40000000);
            g.fill(barX, barY(), barX + 3, barY() + barHeight(),
                    draggingBar ? COL_TEXT : COL_TEXT_DIM);
        }
    }

    private void renderTierRows(GuiGraphics g) {
        RecruitableSoldiers.Entry entry = entries.get(selected);
        g.drawCenteredString(this.font,
                Component.translatable("entity.monster_conquest." + entry.id()),
                rightX + rightW / 2, panelY + 19, COL_TEXT);

        int rows = tierRows(entry);
        for (int t = 0; t < rows; t++) {
            int rowY = rightY + t * TIER_ROW_H;
            g.fill(rightX, rowY, rightX + rightW, rowY + TIER_ROW_H - 2, 0x30000000);

            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "textures/item/egg/soldier/" + entry.id()
                            + (entry.fixedTier() ? "" : "_tier_" + t) + ".png");
            g.blit(icon, rightX + TIER_UNIT_ICON_X, rowY + 3, 0, 0, 16, 16, 16, 16);

            g.drawString(this.font,
                    entry.fixedTier()
                            ? Component.translatable("gui.monster_conquest.recruit.single_level_short")
                            : Component.literal("T" + t),
                    rightX + TIER_LABEL_X, rowY + 7, COL_TEXT);

            // 固定列宽避免固定等级、价格图标与持有数量互相覆盖。
            int need = RecruitConfig.recruitCost(entry.id(), t);
            ItemStack baseCost = new ItemStack(RecruitConfig.currencyItem(), need);
            int baseX = rightX + TIER_BASE_ICON_X;
            g.renderItem(baseCost, baseX, rowY + 3);
            g.renderItemDecorations(this.font, baseCost, baseX, rowY + 3);

            int extraNeed = t == 4 ? RecruitConfig.tier4ExtraCost() : 0;
            Item extraItem = extraNeed > 0 ? RecruitConfig.tier4ExtraItem() : null;
            int baseHave = countItems(RecruitConfig.currencyItem());
            int extraHave = extraItem == null ? 0 : countItems(extraItem);
            boolean baseEnough = baseHave >= need;
            boolean extraEnough = extraNeed <= 0 || extraHave >= extraNeed;
            boolean enough = baseEnough && extraEnough;
            if (extraItem != null) {
                g.drawString(this.font, "+", rightX + TIER_PLUS_X, rowY + 7, COL_TEXT_DIM);
                ItemStack extraCost = new ItemStack(extraItem, extraNeed);
                int extraX = rightX + TIER_EXTRA_ICON_X;
                if (!extraEnough) {
                    int left = extraX - 1;
                    int top = rowY + 2;
                    int right = extraX + 17;
                    int bottom = rowY + 20;
                    g.fill(left, top, right, top + 1, COL_BAD);
                    g.fill(left, bottom - 1, right, bottom, COL_BAD);
                    g.fill(left, top + 1, left + 1, bottom - 1, COL_BAD);
                    g.fill(right - 1, top + 1, right, bottom - 1, COL_BAD);
                }
                g.renderItem(extraCost, extraX, rowY + 3);
                g.renderItemDecorations(this.font, extraCost, extraX, rowY + 3);
            }

            int ownedX = rightX + TIER_OWNED_X;
            int ownedRight = rightX + rightW - 112;
            viewport.enableScissor(g, ownedX, rowY + 5, ownedRight, rowY + 16);
            g.drawString(this.font,
                    Component.translatable("gui.monster_conquest.recruit.owned_short", baseHave, need),
                    ownedX, rowY + 7, baseEnough ? COL_OK : COL_BAD);
            g.disableScissor();

            // 升级：蛋（低一档）+ 目标档半价
            Item egg = !entry.fixedTier() && t >= 1 ? eggItem(entry.id(), t - 1) : null;
            boolean upgradeEnough = false;
            if (egg != null) {
                int upgradeCost = halfUp(need);
                upgradeEnough = countItems(egg) >= 1 && baseHave >= upgradeCost && extraEnough;
            }

            updateTooltips(t, egg, need, extraItem, extraNeed);
            if (t < recruitButtons.size()) {
                recruitButtons.get(t).active = enough;
                Button upgradeBtn = upgradeButtons.get(t);
                if (upgradeBtn != null) upgradeBtn.active = upgradeEnough;
            }
        }
    }

    private void updateTooltips(int t, @Nullable Item egg, int need,
                                @Nullable Item extraItem, int extraNeed) {
        if (t >= recruitButtons.size()) return;
        Component currencyName = new ItemStack(RecruitConfig.currencyItem()).getHoverName();
        Component recruitTip = Component.translatable(
                "gui.monster_conquest.recruit.tip_cost", currencyName, need);
        recruitButtons.get(t).setTooltip(Tooltip.create(withExtraCost(recruitTip, extraItem, extraNeed)));
        Button upgradeBtn = upgradeButtons.get(t);
        if (upgradeBtn != null) {
            if (egg == null) {
                upgradeBtn.active = false;
            } else {
                Component upgradeTip = Component.translatable(
                        "gui.monster_conquest.recruit.tip_upgrade",
                        new ItemStack(egg).getHoverName(), currencyName, halfUp(need));
                upgradeBtn.setTooltip(Tooltip.create(
                        withExtraCost(upgradeTip, extraItem, extraNeed)));
            }
        }
    }

    private static Component withExtraCost(Component tip, @Nullable Item extraItem, int extraNeed) {
        if (extraItem == null || extraNeed <= 0) return tip;
        return Component.empty()
                .append(tip)
                .append("\n")
                .append(Component.translatable(
                        "gui.monster_conquest.recruit.tip_tier4_extra",
                        new ItemStack(extraItem).getHoverName(), extraNeed));
    }

    private static int halfUp(int value) {
        return Math.max(1, (value + 1) / 2);
    }

    private static int tierRows(RecruitableSoldiers.Entry entry) {
        return entry.fixedTier() ? 1 : 5;
    }

    private void syncTierWidgets(RecruitableSoldiers.Entry entry) {
        int rows = tierRows(entry);
        for (int t = 0; t < recruitButtons.size(); t++) {
            boolean visible = t < rows;
            recruitButtons.get(t).visible = visible;
            Button upgrade = upgradeButtons.get(t);
            if (upgrade != null) upgrade.visible = visible;
        }
    }

    /** 士兵刷怪蛋物品（soldier_<兵种>_tier_<n>）。 */
    @Nullable
    private static Item eggItem(String speciesId, int tier) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                Monster_conquest.MODID, speciesId + "_tier_" + tier);
        Item item = BuiltInRegistries.ITEM.get(rl);
        return (item == null || item == Items.AIR) ? null : item;
    }

    private int countItem(ItemStack sample) {
        return countItems(sample.getItem());
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
