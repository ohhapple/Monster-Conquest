package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.RecruitableSoldiers;
import com.ohhapple.monster_conquest.config.RecruitConfig;
import com.ohhapple.monster_conquest.network.serverbound.recruit.RecruitSoldierPayload;
import com.ohhapple.monster_conquest.network.serverbound.recruit.UpgradeSoldierPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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

    private int panelX, panelY;
    private int listX, listY, listH;
    private int rightX, rightY, rightW;

    public RecruitStationScreen(BlockPos pos) {
        super(Component.translatable("gui.monster_conquest.recruit.title"));
        this.pos = pos;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_W) / 2;
        panelY = (this.height - PANEL_H) / 2;
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
        int max = Math.max(0, listContentHeight() - listH);
        listScroll = Mth.clamp(listScroll, 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX <= listX + LIST_W && mouseY >= listY && mouseY <= listY + listH) {
            listScroll -= (int) Math.round(scrollY * ROW_H * 2);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, COL_BORDER);
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, COL_BG);
        g.drawCenteredString(this.font, this.title, panelX + PANEL_W / 2, panelY + 8, COL_TEXT);
        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.recruit.list_title"),
                listX, panelY + 19, COL_TEXT_DIM);

        renderSpeciesList(g, mouseX, mouseY);
        renderTierRows(g);

        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.recruit.hint"),
                panelX + 8, panelY + PANEL_H - 14, COL_TEXT_DIM);
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
        if (listContentHeight() > listH) {
            int barX = listX + LIST_W - 3;
            int barH = Math.max(10, listH * listH / listContentHeight());
            int barY = listY + (listH - barH) * listScroll / Math.max(1, listContentHeight() - listH);
            g.fill(barX, listY, barX + 3, listY + listH, 0x40000000);
            g.fill(barX, barY, barX + 3, barY + barH, COL_TEXT_DIM);
        }
    }

    private void renderTierRows(GuiGraphics g) {
        RecruitableSoldiers.Entry entry = entries.get(selected);
        g.drawCenteredString(this.font,
                Component.translatable("entity.monster_conquest." + entry.id()),
                rightX + rightW / 2, panelY + 19, COL_TEXT);

        for (int t = 0; t < 5; t++) {
            int rowY = rightY + t * TIER_ROW_H;
            g.fill(rightX, rowY, rightX + rightW, rowY + TIER_ROW_H - 2, 0x30000000);

            ResourceLocation icon = ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "textures/item/egg/soldier/" + entry.id() + "_tier_" + t + ".png");
            g.blit(icon, rightX + 3, rowY + 3, 0, 0, 16, 16, 16, 16);

            g.drawString(this.font, "T" + t, rightX + 21, rowY + 7, COL_TEXT);

            // 军费 = 绿宝石 × e + 该等级金属 × m（每个兵种单独定价）
            int eNeed = RecruitConfig.emeraldCost(entry.id(), t);
            int mNeed = RecruitConfig.metalCost(entry.id(), t);
            ItemStack emeraldCost = new ItemStack(RecruitConfig.EMERALD_CURRENCY, eNeed);
            ItemStack metalCost = new ItemStack(RecruitConfig.metalItemFor(t), mNeed);
            int iconX = rightX + 36;
            g.renderItem(emeraldCost, iconX, rowY + 3);
            g.renderItemDecorations(this.font, emeraldCost, iconX, rowY + 3);
            g.renderItem(metalCost, iconX + 16, rowY + 3);
            g.renderItemDecorations(this.font, metalCost, iconX + 16, rowY + 3);

            int haveE = countItem(emeraldCost);
            int haveM = countItem(metalCost);
            boolean enough = haveE >= eNeed && haveM >= mNeed;
            g.drawString(this.font,
                    Component.translatable("gui.monster_conquest.recruit.owned_short", haveE, haveM),
                    iconX + 34, rowY + 7, enough ? COL_OK : COL_BAD);

            // 升级：蛋（低一档）+ 目标档半价
            Item egg = t >= 1 ? eggItem(entry.id(), t - 1) : null;
            boolean upgradeEnough = false;
            if (egg != null) {
                int ue = halfUp(eNeed);
                int um = halfUp(mNeed);
                upgradeEnough = countItems(egg) >= 1 && haveE >= ue && haveM >= um;
            }

            updateTooltips(t, entry, egg, eNeed, mNeed);
            if (t < recruitButtons.size()) {
                recruitButtons.get(t).active = enough;
                Button upgradeBtn = upgradeButtons.get(t);
                if (upgradeBtn != null) upgradeBtn.active = upgradeEnough;
            }
        }
    }

    private void updateTooltips(int t, RecruitableSoldiers.Entry entry,
                                @Nullable Item egg, int eNeed, int mNeed) {
        if (t >= recruitButtons.size()) return;
        Component metalName = new ItemStack(RecruitConfig.metalItemFor(t)).getHoverName();
        recruitButtons.get(t).setTooltip(Tooltip.create(
                Component.translatable("gui.monster_conquest.recruit.tip_cost",
                        eNeed, metalName, mNeed)));
        Button upgradeBtn = upgradeButtons.get(t);
        if (upgradeBtn != null) {
            if (egg == null) {
                upgradeBtn.active = false;
            } else {
                upgradeBtn.setTooltip(Tooltip.create(
                        Component.translatable("gui.monster_conquest.recruit.tip_upgrade",
                                new ItemStack(egg).getHoverName(),
                                halfUp(eNeed), metalName, halfUp(mNeed))));
            }
        }
    }

    private static int halfUp(int value) {
        return Math.max(1, (value + 1) / 2);
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
        n += com.ohhapple.monster_conquest.item.BackpackItem.countItem(mc.player, item);
        return n;
    }
}
