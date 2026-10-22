package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.block.SpawnPointBlockEntity;
import com.ohhapple.monster_conquest.block.SpawnPointService;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.item.CommanderSpawnEggItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.item.SoldierSpawnEggItem;
import com.ohhapple.monster_conquest.menu.SpawnPointMenu;
import com.ohhapple.monster_conquest.network.serverbound.spawn.SpawnPointConfigPayload;
import com.ohhapple.monster_conquest.util.FactionDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 刷怪点界面：
 *   · 左侧选要生成的单位（首领 / 巡逻队长 / 士兵，可滚动）；
 *   · 右侧配置阵营、形态（步兵 / 骑兵）、士兵等级、单次刷出数量、刷怪间隔、
 *     「最多生成」与「同时存在」两道数量上限（都可切无限，前者可手动重置计数）；
 *   · 左下角放模板刷怪蛋：放进去后按蛋里的数据生成（与直接放那颗蛋完全一致），蛋不消耗。
 * 所有数值都由服务端校验后回传（数据槽同步），界面只负责发请求与显示。
 */
public class SpawnPointScreen extends AbstractContainerScreen<SpawnPointMenu> {

    private static final int PANEL_W = 380;
    private static final int PANEL_H = 286;

    /** 单位列表。 */
    private static final int LIST_X = 8;
    private static final int LIST_Y = 18;
    private static final int LIST_W = 136;
    private static final int LIST_H = 126;
    private static final int ROW_H = 12;

    /** 右侧配置列。 */
    private static final int LABEL_X = 152;
    private static final int CTRL_L_X = 200;
    private static final int CTRL_R_X = 286;
    private static final int CYCLE_R_X = 352;
    private static final int WIDE_X = 322;
    private static final int WIDE_W = 50;
    private static final int ROW0 = 18;
    private static final int ROW_STEP = 20;
    private static final int BTN_H = 16;

    /** 背包槽位置（与菜单里加的槽位一一对应）。 */
    private static final int INV_X = 109;
    private static final int INV_Y = 206;
    private static final int HOTBAR_Y = 264;
    /** 巡逻路径那一行的位置。 */
    private static final int PATH_ROW_Y = 158;
    /** 统计行 / 状态行的位置。 */
    private static final int STATS_Y = 178;
    private static final int STATUS_Y = 192;

    /** 切到有限数量时的起始值。 */
    private static final int DEFAULT_MAX_TOTAL = 100;
    private static final int DEFAULT_MAX_ALIVE = 20;

    private static final int COL_BG = 0xF0101418;
    private static final int COL_BORDER = 0xFF3C4650;
    private static final int COL_TEXT = 0xFFD8DEE6;
    private static final int COL_TEXT_DIM = 0xFF8C98A4;
    private static final int COL_SELECTED = 0x805C86C0;
    private static final int COL_OK = 0xFF7ED07E;
    private static final int COL_WARN = 0xFFE0B870;

    private int listScroll = 0;
    /** 是否正在拖左侧滚动条；拖动时鼠标与滑块顶部的偏移。 */
    private boolean draggingBar = false;
    private double barDragOffset = 0.0;
    /** 模板蛋解析结果：放了蛋时界面按蛋的数据显示阵营 / 形态 / 等级。 */
    @Nullable private EggView eggView;

    /** 模板蛋给界面看的几个值（等级 -1 = 首领，-2 = 唯一等级的召唤物）。 */
    private record EggView(Component name, ResourceLocation faction,
                           boolean mounted, int tier, boolean singleLevel) {}

    private Button enabledButton;
    private Button factionPrev;
    private Button factionNext;
    private Button mountedButton;
    private Button tierPrev;
    private Button tierNext;
    private Button batchDown;
    private Button batchUp;
    private Button intervalDown;
    private Button intervalUp;
    private Button maxTotalDown;
    private Button maxTotalUp;
    private Button maxTotalToggle;
    private Button maxAliveDown;
    private Button maxAliveUp;
    private Button maxAliveToggle;
    private Button resetButton;
    private Button clearPathButton;
    private GuiViewport viewport = new GuiViewport(1.0f, 0, 0);

    public SpawnPointScreen(SpawnPointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
        // 标签全部自己画（默认的"物品栏"标题会压到配置区）
        this.inventoryLabelY = PANEL_H + 10;
    }

    @Override
    protected void init() {
        super.init();
        viewport = GuiViewport.fit(width, height, PANEL_W, PANEL_H, 8);
        leftPos = 0;
        topPos = 0;
        listScroll = 0;
        clampScroll();

        int lx = leftPos;
        int ty = topPos;

        enabledButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleEnabled())
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.enabled.tip")))
                .bounds(lx + 288, ty + 2, 84, BTN_H).build());

        factionPrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> cycleFaction(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.faction.tip")))
                .bounds(lx + CTRL_L_X, ty + ROW0 + 2, 18, BTN_H).build());
        factionNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> cycleFaction(1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.faction.tip")))
                .bounds(lx + CYCLE_R_X, ty + ROW0 + 2, 18, BTN_H).build());

        mountedButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleMounted())
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.form.tip")))
                .bounds(lx + CTRL_L_X, ty + ROW0 + ROW_STEP + 2, 84, BTN_H).build());

        tierPrev = addRenderableWidget(Button.builder(Component.literal("<"), b -> stepTier(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.tier.tip")))
                .bounds(lx + CTRL_L_X, ty + ROW0 + ROW_STEP * 2 + 2, 18, BTN_H).build());
        tierNext = addRenderableWidget(Button.builder(Component.literal(">"), b -> stepTier(1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.tier.tip")))
                .bounds(lx + CYCLE_R_X, ty + ROW0 + ROW_STEP * 2 + 2, 18, BTN_H).build());

        batchDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> stepBatch(-1))
                .bounds(lx + CTRL_L_X, ty + ROW0 + ROW_STEP * 3 + 2, 18, BTN_H).build());
        batchUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> stepBatch(1))
                .bounds(lx + CTRL_R_X, ty + ROW0 + ROW_STEP * 3 + 2, 18, BTN_H).build());

        intervalDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> stepInterval(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.shift_tip")))
                .bounds(lx + CTRL_L_X, ty + ROW0 + ROW_STEP * 4 + 2, 18, BTN_H).build());
        intervalUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> stepInterval(1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.shift_tip")))
                .bounds(lx + CTRL_R_X, ty + ROW0 + ROW_STEP * 4 + 2, 18, BTN_H).build());

        maxTotalDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> stepMaxTotal(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.shift_tip")))
                .bounds(lx + CTRL_L_X, ty + ROW0 + ROW_STEP * 5 + 2, 18, BTN_H).build());
        maxTotalUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> stepMaxTotal(1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.shift_tip")))
                .bounds(lx + CTRL_R_X, ty + ROW0 + ROW_STEP * 5 + 2, 18, BTN_H).build());
        maxTotalToggle = addRenderableWidget(Button.builder(Component.empty(), b -> toggleMaxTotal())
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.max_total.tip")))
                .bounds(lx + WIDE_X, ty + ROW0 + ROW_STEP * 5 + 2, WIDE_W, BTN_H).build());

        maxAliveDown = addRenderableWidget(Button.builder(Component.literal("-"), b -> stepMaxAlive(-1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.shift_tip")))
                .bounds(lx + CTRL_L_X, ty + ROW0 + ROW_STEP * 6 + 2, 18, BTN_H).build());
        maxAliveUp = addRenderableWidget(Button.builder(Component.literal("+"), b -> stepMaxAlive(1))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.shift_tip")))
                .bounds(lx + CTRL_R_X, ty + ROW0 + ROW_STEP * 6 + 2, 18, BTN_H).build());
        maxAliveToggle = addRenderableWidget(Button.builder(Component.empty(), b -> toggleMaxAlive())
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.max_alive.tip")))
                .bounds(lx + WIDE_X, ty + ROW0 + ROW_STEP * 6 + 2, WIDE_W, BTN_H).build());

        resetButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.spawn_point.reset"),
                        b -> send(SpawnPointService.ACTION_RESET_COUNT, 0))
                .tooltip(Tooltip.create(Component.translatable("gui.monster_conquest.spawn_point.reset.tip")))
                .bounds(lx + WIDE_X, ty + STATS_Y - 2, WIDE_W, BTN_H).build());

        clearPathButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.spawn_point.clear_path"),
                        b -> send(SpawnPointService.ACTION_CLEAR_PATH, 0))
                .tooltip(Tooltip.create(Component.translatable(
                        "gui.monster_conquest.spawn_point.clear_path.tip")))
                .bounds(lx + WIDE_X, ty + PATH_ROW_Y, WIDE_W, BTN_H).build());
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ===================== 交互 =====================

    private void send(int action, int value) {
        PacketDistributor.sendToServer(new SpawnPointConfigPayload(action, value));
    }

    private void cycleFaction(int delta) {
        int size = SpawnPointService.FACTIONS.size();
        int current = menu.getSync(SpawnPointMenu.DATA_FACTION);
        send(SpawnPointService.ACTION_FACTION, Math.floorMod(current + delta, size));
    }

    private void toggleMounted() {
        send(SpawnPointService.ACTION_MOUNTED, menu.getSync(SpawnPointMenu.DATA_MOUNTED) == 1 ? 0 : 1);
    }

    private void toggleEnabled() {
        send(SpawnPointService.ACTION_ENABLED, menu.getSync(SpawnPointMenu.DATA_ENABLED) == 1 ? 0 : 1);
    }

    private void stepTier(int delta) {
        send(SpawnPointService.ACTION_TIER, menu.getSync(SpawnPointMenu.DATA_TIER) + delta);
    }

    private void stepBatch(int delta) {
        send(SpawnPointService.ACTION_BATCH, menu.getSync(SpawnPointMenu.DATA_BATCH) + step(delta));
    }

    private void stepInterval(int delta) {
        send(SpawnPointService.ACTION_INTERVAL, menu.getSync(SpawnPointMenu.DATA_INTERVAL) + step(delta));
    }

    private void stepMaxTotal(int delta) {
        send(SpawnPointService.ACTION_MAX_TOTAL, menu.getSync(SpawnPointMenu.DATA_MAX_TOTAL) + step(delta * 10));
    }

    private void stepMaxAlive(int delta) {
        send(SpawnPointService.ACTION_MAX_CONCURRENT, menu.getSync(SpawnPointMenu.DATA_MAX_CONCURRENT) + step(delta));
    }

    private void toggleMaxTotal() {
        int current = menu.getSync(SpawnPointMenu.DATA_MAX_TOTAL);
        send(SpawnPointService.ACTION_MAX_TOTAL, current < 0 ? DEFAULT_MAX_TOTAL : -1);
    }

    private void toggleMaxAlive() {
        int current = menu.getSync(SpawnPointMenu.DATA_MAX_CONCURRENT);
        send(SpawnPointService.ACTION_MAX_CONCURRENT, current < 0 ? DEFAULT_MAX_ALIVE : -1);
    }

    /** Shift + 点击 = 一次调 10 倍步长。 */
    private static int step(int delta) {
        return Screen.hasShiftDown() ? delta * 10 : delta;
    }

    private boolean inList(double mouseX, double mouseY) {
        return mouseX >= leftPos + LIST_X && mouseX <= leftPos + LIST_X + LIST_W
                && mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + LIST_H;
    }

    /** 鼠标是否落在左侧滚动条上（判定区比画出来的滑块宽一些，方便按住）。 */
    private boolean inScrollbar(double mouseX, double mouseY) {
        int barX = leftPos + LIST_X + LIST_W - 3;
        return maxListScroll() > 0
                && mouseX >= barX - 3 && mouseX <= barX + 5
                && mouseY >= topPos + LIST_Y && mouseY <= topPos + LIST_Y + LIST_H;
    }

    private int listContentHeight() {
        return SpawnPointService.UNITS.size() * ROW_H;
    }

    private int maxListScroll() {
        return Math.max(0, listContentHeight() - LIST_H);
    }

    private int listBarHeight() {
        int content = listContentHeight();
        return content <= 0 ? LIST_H : Math.max(10, LIST_H * LIST_H / content);
    }

    private int listBarY() {
        int max = maxListScroll();
        if (max <= 0) return topPos + LIST_Y;
        return topPos + LIST_Y + (LIST_H - listBarHeight()) * listScroll / max;
    }

    /** 把滚动条拖到鼠标所在的纵向位置。 */
    private void applyBarDrag(double mouseY) {
        int max = maxListScroll();
        if (max <= 0) return;
        int barHeight = listBarHeight();
        int track = Math.max(1, LIST_H - barHeight);
        double minY = topPos + LIST_Y;
        double y = Mth.clamp(mouseY - barDragOffset, minY, minY + track);
        listScroll = Mth.clamp((int) Math.round((y - minY) * max / (double) track), 0, max);
    }

    private void clampScroll() {
        listScroll = Mth.clamp(listScroll, 0, maxListScroll());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        mouseX = viewport.localX(mouseX);
        mouseY = viewport.localY(mouseY);
        if (inList(mouseX, mouseY)) {
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
        // 滚动条：点滑块直接拖，点轨道跳到该位置
        if (button == 0 && inScrollbar(mouseX, mouseY)) {
            int barHeight = listBarHeight();
            int barY = listBarY();
            if (mouseY >= barY && mouseY <= barY + barHeight) {
                barDragOffset = mouseY - barY;
            } else {
                barDragOffset = barHeight / 2.0;
                applyBarDrag(mouseY);
            }
            draggingBar = true;
            return true;
        }
        // 单位列表不是控件，先处理它的点击（容器界面的 super 会吃掉所有左键点击）
        if (button == 0 && inList(mouseX, mouseY)) {
            // 放了模板刷怪蛋时以蛋为准，不能再手选单位
            if (!isUnitLocked()) {
                int index = (int) ((mouseY - (topPos + LIST_Y) + listScroll) / ROW_H);
                if (index >= 0 && index < SpawnPointService.UNITS.size()) {
                    send(SpawnPointService.ACTION_UNIT, index);
                    return true;
                }
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
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
        // 跳过原版全屏背景，但仍绘制自定义面板、槽位和背包底图。
        renderBg(g, partialTick, mouseX, mouseY);
    }

    /** 模板蛋槽里有蛋：单位列表以蛋为准，不能手选。 */
    private boolean isUnitLocked() {
        return !menu.getSlot(SpawnPointMenu.EGG_SLOT).getItem().isEmpty();
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int localMouseX = viewport.localX(mouseX);
        int localMouseY = viewport.localY(mouseY);
        g.fill(0, 0, width, height, 0xB0000000);
        viewport.push(g);
        updateEggView();
        updateWidgets();
        super.render(g, localMouseX, localMouseY, partialTick);
        renderUnitList(g, localMouseX, localMouseY);
        renderTooltip(g, localMouseX, localMouseY);
        viewport.pop(g);
    }

    /** 读模板蛋槽里的蛋，解析出界面要显示的阵营 / 形态 / 等级（与刷怪逻辑同一套规则）。 */
    private void updateEggView() {
        ItemStack egg = menu.getSlot(SpawnPointMenu.EGG_SLOT).getItem();
        if (egg.isEmpty()) {
            eggView = null;
            return;
        }
        CompoundTag tag = egg.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        ResourceLocation tagged = parseFaction(tag);
        // 形态只看蛋自己（与刷怪规则一致）：蛋里没写就是步兵
        boolean mounted = tag.getBoolean(RetrievalToolItem.KEY_CAVALRY);

        int tier = -1;
        boolean singleLevel = false;
        if (egg.getItem() instanceof SoldierSpawnEggItem soldierEgg) {
            singleLevel = soldierEgg.isSingleLevel();
            if (singleLevel) {
                mounted = false;
                tier = -2;
            } else {
                int rolled = tag.contains("Tier")
                        ? tag.getInt("Tier") : soldierEgg.getDefaultTier().ordinal();
                tier = Mth.clamp(rolled, 0, EquipmentTier.values().length - 1);
            }
        }

        ResourceLocation faction;
        if (tagged != null) {
            faction = tagged;
        } else if (egg.getItem() instanceof CommanderSpawnEggItem commanderEgg) {
            // 首领 / 巡逻队长蛋自带阵营
            faction = commanderEgg.getDefaultFaction();
        } else {
            faction = SpawnPointService.FACTIONS.get(Mth.clamp(
                    menu.getSync(SpawnPointMenu.DATA_FACTION), 0, SpawnPointService.FACTIONS.size() - 1));
        }
        eggView = new EggView(egg.getHoverName(), faction, mounted, tier, singleLevel);
    }

    @Nullable
    private static ResourceLocation parseFaction(CompoundTag tag) {
        String raw = tag.getString(RetrievalToolItem.KEY_FACTION);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }

    /** 阵营显示名：玩家阵营尽量显示玩家名，拿不到就用模组统一的短名 / 原文 id。 */
    private static Component factionLabel(ResourceLocation id) {
        UUID owner = FactionIds.parsePlayer(id);
        if (owner != null) {
            String name = playerName(owner);
            if (name != null) {
                return Component.translatable("faction.monster_conquest.player_short", name);
            }
        }
        return FactionDisplay.displayNameOrId(id);
    }

    @Nullable
    private static String playerName(UUID owner) {
        try {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                var info = connection.getPlayerInfo(owner);
                if (info != null && info.getProfile() != null) return info.getProfile().getName();
            }
        } catch (Throwable ignored) {
            // 玩家可能已下线：退回短名
        }
        return null;
    }

    /** 每次显示前按最新同步值刷新按钮文字 / 可用状态。 */
    private void updateWidgets() {
        boolean globalDisabled = menu.getSync(SpawnPointMenu.DATA_GLOBAL_DISABLED) == 1;
        boolean enabled = menu.getSync(SpawnPointMenu.DATA_ENABLED) == 1;
        enabledButton.setMessage(Component.translatable(globalDisabled
                ? "gui.monster_conquest.spawn_point.global_disabled"
                : enabled
                        ? "gui.monster_conquest.spawn_point.switch_on"
                        : "gui.monster_conquest.spawn_point.switch_off"));
        enabledButton.active = !globalDisabled;

        // 放了模板蛋：阵营 / 形态 / 等级按蛋的数据显示，不能再手调
        boolean eggMode = eggView != null;
        boolean commander = SpawnPointService.isCommanderIndex(menu.getSync(SpawnPointMenu.DATA_UNIT));
        boolean fixedTier = SpawnPointService.isFixedTierIndex(menu.getSync(SpawnPointMenu.DATA_UNIT));
        boolean flying = SpawnPointService.isFlyingIndex(menu.getSync(SpawnPointMenu.DATA_UNIT));
        factionPrev.active = !commander && !eggMode;
        factionNext.active = !commander && !eggMode;

        boolean mounted = eggMode ? eggView.mounted() : menu.getSync(SpawnPointMenu.DATA_MOUNTED) == 1;
        mountedButton.setMessage((eggMode && eggView.singleLevel()) || flying
                ? Component.translatable("tooltip.monster_conquest.role.flying")
                : Component.translatable(mounted
                        ? "gui.monster_conquest.spawn_point.form.cavalry"
                        : "gui.monster_conquest.spawn_point.form.infantry"));
        mountedButton.active = !eggMode && !flying;

        tierPrev.active = !commander && !eggMode && !fixedTier;
        tierNext.active = !commander && !eggMode && !fixedTier;

        boolean totalUnlimited = menu.getSync(SpawnPointMenu.DATA_MAX_TOTAL) < 0;
        maxTotalDown.active = !totalUnlimited;
        maxTotalUp.active = !totalUnlimited;
        maxTotalToggle.setMessage(limitLabel(totalUnlimited));

        boolean aliveUnlimited = menu.getSync(SpawnPointMenu.DATA_MAX_CONCURRENT) < 0;
        maxAliveDown.active = !aliveUnlimited;
        maxAliveUp.active = !aliveUnlimited;
        maxAliveToggle.setMessage(limitLabel(aliveUnlimited));

        resetButton.active = menu.getSync(SpawnPointMenu.DATA_SPAWNED) > 0;
        clearPathButton.active = menu.getSync(SpawnPointMenu.DATA_PATH_POINTS) > 0;
    }

    private static Component limitLabel(boolean unlimited) {
        return Component.translatable(unlimited
                ? "gui.monster_conquest.spawn_point.unlimited"
                : "gui.monster_conquest.spawn_point.limited");
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        g.fill(x - 1, y - 1, x + PANEL_W + 1, y + PANEL_H + 1, COL_BORDER);
        g.fill(x, y, x + PANEL_W, y + PANEL_H, COL_BG);

        // 单位列表底
        g.fill(x + LIST_X, y + LIST_Y, x + LIST_X + LIST_W, y + LIST_Y + LIST_H, 0x40000000);

        // 模板蛋槽
        drawSlot(g, x + SpawnPointMenu.EGG_SLOT_X, y + SpawnPointMenu.EGG_SLOT_Y);
        if (!menu.getSlot(SpawnPointMenu.EGG_SLOT).getItem().isEmpty()) {
            int sx = x + SpawnPointMenu.EGG_SLOT_X;
            int sy = y + SpawnPointMenu.EGG_SLOT_Y;
            g.fill(sx - 1, sy - 1, sx + 19, sy, COL_OK);
            g.fill(sx - 1, sy + 18, sx + 19, sy + 19, COL_OK);
            g.fill(sx - 1, sy, sx, sy + 18, COL_OK);
            g.fill(sx + 18, sy, sx + 19, sy + 18, COL_OK);
        }

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + INV_X + col * 18, y + INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + INV_X + col * 18, y + HOTBAR_Y);
        }
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** 文字：标题 + 表头 + 各项数值 + 统计 / 状态行。 */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawCenteredString(this.font, this.title, PANEL_W / 2, 6, COL_TEXT);
        g.drawString(this.font, Component.translatable("gui.monster_conquest.spawn_point.list_title"),
                LIST_X, 6, COL_TEXT_DIM, false);
        g.drawString(this.font, Component.translatable("gui.monster_conquest.spawn_point.config"),
                LABEL_X, 6, COL_TEXT_DIM, false);

        // 模板蛋说明
        g.drawString(this.font, Component.translatable("gui.monster_conquest.spawn_point.egg_title"),
                LIST_X, 148, COL_TEXT_DIM, false);
        g.drawString(this.font, Component.translatable("gui.monster_conquest.spawn_point.egg_hint1"),
                30, 158, COL_TEXT_DIM, false);
        g.drawString(this.font, Component.translatable("gui.monster_conquest.spawn_point.egg_hint2"),
                30, 168, COL_TEXT_DIM, false);

        rowLabel(g, 0, "faction");
        rowLabel(g, 1, "form");
        rowLabel(g, 2, "tier");
        rowLabel(g, 3, "batch");
        rowLabel(g, 4, "interval");
        rowLabel(g, 5, "max_total");
        rowLabel(g, 6, "max_alive");

        int unitIndex = menu.getSync(SpawnPointMenu.DATA_UNIT);
        boolean commander = SpawnPointService.isCommanderIndex(unitIndex);
        boolean fixedTier = SpawnPointService.isFixedTierIndex(unitIndex);
        // 首领 / 巡逻队长阵营固定：显示它自己的阵营，只有士兵显示所选阵营
        ResourceLocation fixed = SpawnPointService.fixedFactionOf(unitIndex);
        ResourceLocation faction;
        if (eggView != null) {
            // 放了模板蛋：阵营按蛋里的数据（含玩家阵营）显示
            faction = eggView.faction();
        } else {
            faction = fixed != null
                    ? fixed
                    : SpawnPointService.FACTIONS.get(Mth.clamp(menu.getSync(SpawnPointMenu.DATA_FACTION), 0,
                            SpawnPointService.FACTIONS.size() - 1));
        }
        drawCentered(g, factionLabel(faction),
                CTRL_L_X + 26, CYCLE_R_X - 2, ROW0 + 6, COL_TEXT);

        Component tierText;
        if (eggView != null) {
            // 放了模板蛋：等级按蛋里的等级（首领蛋没有等级）
            tierText = eggView.singleLevel()
                    ? Component.translatable("tooltip.monster_conquest.single_level")
                    : eggView.tier() < 0
                            ? Component.literal("-")
                            : Component.literal("T" + eggView.tier());
        } else {
            tierText = commander
                    ? Component.literal("-")
                    : fixedTier
                            ? Component.translatable("tooltip.monster_conquest.single_level")
                            : Component.literal("T" + menu.getSync(SpawnPointMenu.DATA_TIER));
        }
        drawCentered(g, tierText, CTRL_L_X + 26, CYCLE_R_X - 2, ROW0 + ROW_STEP * 2 + 6, COL_TEXT);

        drawCentered(g, Component.literal(String.valueOf(menu.getSync(SpawnPointMenu.DATA_BATCH))),
                CTRL_L_X + 20, CTRL_R_X - 6, ROW0 + ROW_STEP * 3 + 6, COL_TEXT);
        int intervalSeconds = menu.getSync(SpawnPointMenu.DATA_INTERVAL);
        drawCentered(g, intervalSeconds <= 0
                        ? Component.translatable("gui.monster_conquest.spawn_point.unlimited")
                        : Component.translatable("gui.monster_conquest.spawn_point.seconds", intervalSeconds),
                CTRL_L_X + 20, CTRL_R_X - 6, ROW0 + ROW_STEP * 4 + 6, COL_TEXT);

        int maxTotal = menu.getSync(SpawnPointMenu.DATA_MAX_TOTAL);
        if (maxTotal >= 0) {
            drawCentered(g, Component.literal(String.valueOf(maxTotal)),
                    CTRL_L_X + 20, CTRL_R_X - 6, ROW0 + ROW_STEP * 5 + 6, COL_TEXT);
        }
        int maxAlive = menu.getSync(SpawnPointMenu.DATA_MAX_CONCURRENT);
        if (maxAlive >= 0) {
            drawCentered(g, Component.literal(String.valueOf(maxAlive)),
                    CTRL_L_X + 20, CTRL_R_X - 6, ROW0 + ROW_STEP * 6 + 6, COL_TEXT);
        }

        // 巡逻路径行：显示刷怪点身上有几个路径点（路径点工具设置，清空按钮清掉）
        int pathPoints = menu.getSync(SpawnPointMenu.DATA_PATH_POINTS);
        g.drawString(this.font, Component.translatable("gui.monster_conquest.spawn_point.path_route"),
                LABEL_X, PATH_ROW_Y + 6, COL_TEXT_DIM, false);
        drawCentered(g, pathPoints <= 0
                        ? Component.translatable("gui.monster_conquest.spawn_point.path_none")
                        : Component.translatable("gui.monster_conquest.spawn_point.path_points", pathPoints),
                CTRL_L_X + 20, WIDE_X - 8, PATH_ROW_Y + 6, COL_TEXT);

        // 统计行
        Component stats = Component.translatable("gui.monster_conquest.spawn_point.stats",
                Component.literal(String.valueOf(menu.getSync(SpawnPointMenu.DATA_SPAWNED))),
                limitText(maxTotal),
                Component.literal(String.valueOf(menu.getSync(SpawnPointMenu.DATA_ALIVE))),
                limitText(maxAlive));
        g.drawString(this.font, stats, LABEL_X, STATS_Y, COL_TEXT_DIM, false);

        // 状态行
        g.drawString(this.font, statusText(menu.getSync(SpawnPointMenu.DATA_STATUS), eggView),
                LABEL_X, STATUS_Y, statusColor(menu.getSync(SpawnPointMenu.DATA_STATUS)), false);
    }

    private void rowLabel(GuiGraphics g, int row, String key) {
        g.drawString(this.font,
                Component.translatable("gui.monster_conquest.spawn_point." + key),
                LABEL_X, ROW0 + ROW_STEP * row + 6, COL_TEXT_DIM, false);
    }

    private void drawCentered(GuiGraphics g, Component text, int x1, int x2, int y, int color) {
        g.drawCenteredString(this.font, text, (x1 + x2) / 2, y, color);
    }

    private static Component limitText(int value) {
        return value < 0
                ? Component.translatable("gui.monster_conquest.spawn_point.unlimited")
                : Component.literal(String.valueOf(value));
    }

    private static Component unitName(DeferredHolder<EntityType<?>, ? extends EntityType<?>> unit) {
        return Component.translatable(unit.get().getDescriptionId());
    }

    private static Component statusText(int status, @Nullable EggView egg) {
        return switch (status) {
            case SpawnPointBlockEntity.STATUS_LIMIT_TOTAL ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.limit_total");
            case SpawnPointBlockEntity.STATUS_LIMIT_ALIVE ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.limit_alive");
            case SpawnPointBlockEntity.STATUS_NO_UNIT ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.no_unit");
            case SpawnPointBlockEntity.STATUS_EGG ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.egg",
                            egg == null ? Component.empty() : egg.name());
            case SpawnPointBlockEntity.STATUS_SWITCHED_OFF ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.switched_off");
            case SpawnPointBlockEntity.STATUS_GLOBAL_DISABLED ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.global_disabled");
            case SpawnPointBlockEntity.STATUS_EDITING ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.editing");
            case SpawnPointBlockEntity.STATUS_NOT_READY ->
                    Component.translatable("gui.monster_conquest.spawn_point.status.not_ready");
            default -> Component.translatable("gui.monster_conquest.spawn_point.status.waiting");
        };
    }

    private static int statusColor(int status) {
        return switch (status) {
            case SpawnPointBlockEntity.STATUS_WAITING, SpawnPointBlockEntity.STATUS_EGG -> COL_OK;
            case SpawnPointBlockEntity.STATUS_NO_UNIT, SpawnPointBlockEntity.STATUS_EDITING -> COL_TEXT_DIM;
            default -> COL_WARN;
        };
    }

    /** 单位列表（滚动的行 + 选中高亮 + 滚动条）。 */
    private void renderUnitList(GuiGraphics g, int mouseX, int mouseY) {
        int x = leftPos + LIST_X;
        int y = topPos + LIST_Y;
        boolean locked = isUnitLocked();

        var units = SpawnPointService.UNITS;
        int selected = locked ? -2 : menu.getSync(SpawnPointMenu.DATA_UNIT);
        g.enableScissor(
                viewport.screenX(x),
                viewport.screenY(y),
                viewport.screenX(x + LIST_W),
                viewport.screenY(y + LIST_H));
        for (int i = 0; i < units.size(); i++) {
            int rowY = y + i * ROW_H - listScroll;
            if (rowY + ROW_H <= y || rowY >= y + LIST_H) continue;
            int top = Math.max(rowY, y);
            int bottom = Math.min(rowY + ROW_H, y + LIST_H);
            boolean hovered = mouseX >= x && mouseX < x + LIST_W && mouseY >= top && mouseY < bottom;
            if (i == selected && !locked) {
                g.fill(x, top, x + LIST_W, bottom, COL_SELECTED);
            } else if (hovered && !locked) {
                g.fill(x, top, x + LIST_W, bottom, 0x30FFFFFF);
            }
            String name = this.font.plainSubstrByWidth(
                    unitName(units.get(i)).getString(), LIST_W - 10);
            g.drawString(this.font, name, x + 4, rowY + 2,
                    locked ? 0xFF666C74 : (i == selected ? 0xFFFFFFFF : COL_TEXT), false);
        }

        // 放了模板蛋：列表压暗，提示以蛋为准
        if (locked) {
            g.fill(x, y, x + LIST_W, y + LIST_H, 0xA0000000);
            g.drawCenteredString(this.font,
                    Component.translatable("gui.monster_conquest.spawn_point.list_locked"),
                    x + LIST_W / 2, y + LIST_H / 2 - 12, COL_WARN);
            g.drawCenteredString(this.font,
                    Component.translatable("gui.monster_conquest.spawn_point.list_locked2"),
                    x + LIST_W / 2, y + LIST_H / 2, COL_TEXT_DIM);
        }

        if (maxListScroll() > 0) {
            int barX = x + LIST_W - 3;
            g.fill(barX, y, barX + 3, y + LIST_H, 0x40000000);
            g.fill(barX, listBarY(), barX + 3, listBarY() + listBarHeight(),
                    draggingBar ? COL_TEXT : COL_TEXT_DIM);
        }
        g.disableScissor();
    }
}
