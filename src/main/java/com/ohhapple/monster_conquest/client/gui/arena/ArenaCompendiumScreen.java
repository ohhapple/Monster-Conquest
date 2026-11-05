package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.arena.game.ArenaEquipment;
import com.ohhapple.monster_conquest.arena.game.ArenaEquipmentStats;
import com.ohhapple.monster_conquest.arena.game.ArenaStatCalculator;
import com.ohhapple.monster_conquest.arena.game.ArenaUnitCatalog;
import com.ohhapple.monster_conquest.arena.trait.ArenaTrait;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitCatalog;
import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;
import com.ohhapple.monster_conquest.client.gui.GuiViewport;
import com.ohhapple.monster_conquest.client.input.ModKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 房间与对局界面共享的只读弈子、装备和羁绊图鉴。 */
public final class ArenaCompendiumScreen extends Screen {

    private static final int PANEL_W = 1_000;
    private static final int PANEL_H = 640;
    private static final int LIST_X = 18;
    private static final int LIST_Y = 112;
    private static final int LIST_W = 964;
    private static final int LIST_H = 510;
    private static final int SCROLLBAR_W = 7;
    private static final int TAB_Y = 40;
    private static final int TAB_H = 24;
    private static final int TAB_W = 116;
    private static final int FILTER_Y = 76;
    private static final int FILTER_H = 22;
    private static final int OPTION_H = 18;
    private static final int UNIT_ROW_MIN_H = 88;
    private static final int EQUIPMENT_ROW_MIN_H = 68;
    private static final int ROW_GAP = 3;

    private static final int COL_BG = 0xFF0B1015;
    private static final int COL_PANEL = 0xFF151D24;
    private static final int COL_ROW = 0x90202A33;
    private static final int COL_ROW_ALT = 0x9019232B;
    private static final int COL_BORDER = 0xFF40515F;
    private static final int COL_TEXT = 0xFFE7EDF3;
    private static final int COL_DIM = 0xFF93A1AE;
    private static final int COL_ACCENT = 0xFF67B7FF;
    private static final int COL_GOLD = 0xFFF2C45E;
    private static final int COL_TRAIT = 0xFF78D7FF;
    private static final int COL_OK = 0xFF76DA8C;

    private static final List<ArenaUnit> UNITS = ArenaUnitCatalog.ALL.stream()
            .sorted(Comparator.comparingInt(ArenaUnit::cost)
                    .thenComparing(unit -> Component.translatable(
                            unit.translationKey()).getString()))
            .toList();

    private final Screen returnScreen;
    private GuiViewport viewport = new GuiViewport(1.0f, 0, 0);
    private Page page = Page.UNITS;
    private int scroll;
    private boolean draggingScrollbar;
    private double scrollbarGrabOffset;
    @Nullable private FilterType openFilter;
    private int costFilter;
    @Nullable private ArenaTrait traitFilter;
    @Nullable private ArenaEquipment.Category equipmentCategory;
    @Nullable private ArenaTrait.Category traitCategory;

    public ArenaCompendiumScreen(Screen returnScreen) {
        super(Component.translatable(
                "gui.monster_conquest.arena.compendium.title"));
        this.returnScreen = returnScreen;
    }

    @Override
    protected void init() {
        super.init();
        viewport = GuiViewport.fit(width, height, PANEL_W, PANEL_H, 8);
    }

    @Override
    public void onClose() {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(returnScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX,
                                 int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        int localMouseX = viewport.localX(mouseX);
        int localMouseY = viewport.localY(mouseY);
        graphics.fill(0, 0, width, height, 0xE0000000);
        viewport.push(graphics);
        graphics.fill(-1, -1, PANEL_W + 1, PANEL_H + 1, COL_BORDER);
        graphics.fill(0, 0, PANEL_W, PANEL_H, COL_BG);
        graphics.fill(0, 0, PANEL_W, 34, 0xFF171F27);
        graphics.fill(0, 33, PANEL_W, 34, COL_BORDER);
        graphics.drawString(font, title, 20, 12, COL_TEXT);
        drawClose(graphics, localMouseX, localMouseY);
        drawTabs(graphics, localMouseX, localMouseY);
        drawFilters(graphics, localMouseX, localMouseY);
        drawList(graphics);
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 400.0f);
        drawOpenFilter(graphics, localMouseX, localMouseY);
        graphics.pose().popPose();
        graphics.flush();
        viewport.pop(graphics);
    }

    private void drawClose(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, 914, 8, 76, 20);
        graphics.fill(914, 8, 990, 28,
                hovered ? 0xFF344858 : 0xFF202C36);
        graphics.drawCenteredString(font, Component.literal("×"),
                953, 14, COL_TEXT);
    }

    private void drawTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = 220;
        for (Page candidate : Page.values()) {
            boolean selected = candidate == page;
            boolean hovered = inside(mouseX, mouseY, x, TAB_Y, TAB_W, TAB_H);
            graphics.fill(x, TAB_Y, x + TAB_W, TAB_Y + TAB_H,
                    selected ? 0xFF365573
                            : (hovered ? 0xFF293946 : 0xFF1C2730));
            if (selected) {
                graphics.fill(x, TAB_Y + TAB_H - 2,
                        x + TAB_W, TAB_Y + TAB_H, COL_ACCENT);
            }
            graphics.drawCenteredString(font,
                    Component.translatable(candidate.translationKey),
                    x + TAB_W / 2, TAB_Y + 8,
                    selected ? COL_TEXT : COL_DIM);
            x += TAB_W + 8;
        }
    }

    private void drawFilters(GuiGraphics graphics, int mouseX, int mouseY) {
        if (page == Page.UNITS) {
            drawFilterBox(graphics, FilterType.COST, 24, 194,
                    costLabel(), mouseX, mouseY);
            drawFilterBox(graphics, FilterType.UNIT_TRAIT, 226, 244,
                    traitLabel(), mouseX, mouseY);
        } else if (page == Page.EQUIPMENT) {
            drawFilterBox(graphics, FilterType.EQUIPMENT_CATEGORY,
                    24, 244, equipmentCategoryLabel(), mouseX, mouseY);
        } else {
            drawFilterBox(graphics, FilterType.TRAIT_CATEGORY,
                    24, 244, traitCategoryLabel(), mouseX, mouseY);
        }
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.compendium.count",
                        filteredCount()),
                760, 83, COL_DIM);
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.compendium.scroll_hint"),
                850, 83, COL_DIM);
    }

    private void drawFilterBox(GuiGraphics graphics, FilterType type,
                               int x, int width, Component label,
                               int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, FILTER_Y,
                width, FILTER_H);
        graphics.fill(x, FILTER_Y, x + width, FILTER_Y + FILTER_H,
                hovered ? 0xFF2B3D4B : 0xFF202B34);
        graphics.drawString(font, label, x + 6, FILTER_Y + 7,
                COL_TEXT);
        graphics.drawString(font, Component.literal("▼"),
                x + width - 14, FILTER_Y + 7, COL_DIM);
    }

    private void drawList(GuiGraphics graphics) {
        graphics.fill(LIST_X, LIST_Y, LIST_X + LIST_W,
                LIST_Y + LIST_H, 0x80202A33);
        int contentHeight = contentHeight();
        clampScroll(contentHeight);
        viewport.enableScissor(graphics, LIST_X, LIST_Y,
                LIST_X + LIST_W - SCROLLBAR_W - 2,
                LIST_Y + LIST_H);
        if (page == Page.UNITS) drawUnits(graphics);
        else if (page == Page.EQUIPMENT) drawEquipment(graphics);
        else drawTraits(graphics);
        graphics.disableScissor();
        drawScrollbar(graphics, contentHeight);
    }

    private void drawUnits(GuiGraphics graphics) {
        List<ArenaUnit> units = filteredUnits();
        int y = LIST_Y + 4 - scroll;
        for (int index = 0; index < units.size(); index++) {
            ArenaUnit unit = units.get(index);
            int rowHeight = unitRowHeight(unit);
            if (y + rowHeight >= LIST_Y && y < LIST_Y + LIST_H) {
                int background = index % 2 == 0 ? COL_ROW : COL_ROW_ALT;
                graphics.fill(LIST_X + 4, y, LIST_X + LIST_W
                        - SCROLLBAR_W - 6, y + rowHeight, background);
                graphics.fill(LIST_X + 4, y, LIST_X + 7,
                        y + rowHeight, costColor(unit.cost()));
                graphics.blit(unit.icon(), LIST_X + 13, y + 12,
                        32, 32, 0.0f, 0.0f, 32, 32, 32, 32);
                graphics.drawString(font,
                        Component.translatable(unit.translationKey()),
                        LIST_X + 54, y + 7, COL_TEXT);
                graphics.drawString(font,
                        Component.translatable(
                                "gui.monster_conquest.arena.compendium.cost",
                                unit.cost()),
                        LIST_X + 835, y + 7, costColor(unit.cost()));
                graphics.drawString(font, traitNames(unit),
                        LIST_X + 54, y + 20, COL_TRAIT);
                int lineY = y + 34;
                ArenaStatCalculator.Stats stats = baseStats(unit);
                for (Component detail : unitStatLines(unit, stats)) {
                    lineY = drawWrapped(graphics, detail,
                            LIST_X + 54, lineY,
                            LIST_W - 90, 20, COL_OK);
                }
                for (Component skillLine : unitSkillLines(unit)) {
                    lineY = drawWrapped(graphics, skillLine,
                            LIST_X + 54, lineY,
                            LIST_W - 90, 20, COL_DIM);
                }
            }
            y += rowHeight + ROW_GAP;
        }
    }

    private void drawEquipment(GuiGraphics graphics) {
        List<ArenaEquipment> equipment = filteredEquipment();
        int y = LIST_Y + 4 - scroll;
        for (int index = 0; index < equipment.size(); index++) {
            ArenaEquipment item = equipment.get(index);
            int rowHeight = equipmentRowHeight(item);
            if (y + rowHeight >= LIST_Y && y < LIST_Y + LIST_H) {
                graphics.fill(LIST_X + 4, y, LIST_X + LIST_W
                                - SCROLLBAR_W - 6, y + rowHeight,
                        index % 2 == 0 ? COL_ROW : COL_ROW_ALT);
                graphics.renderItem(item.iconStack(),
                        LIST_X + 13, y + 12);
                graphics.drawString(font,
                        Component.translatable(item.translationKey()),
                        LIST_X + 48, y + 6, COL_TEXT);
                graphics.drawString(font,
                        Component.translatable(item.categoryKey()),
                        LIST_X + 835, y + 6, COL_ACCENT);
                graphics.drawString(font, equipmentStats(item.stats()),
                        LIST_X + 48, y + 20, COL_OK);
                int lineY = drawWrapped(graphics,
                        Component.translatable(item.descriptionKey()),
                        LIST_X + 48, y + 34,
                        LIST_W - 90, 20, COL_DIM);
                drawWrapped(graphics, equipmentRecipe(item),
                        LIST_X + 48, lineY,
                        LIST_W - 90, 20, COL_GOLD);
            }
            y += rowHeight + ROW_GAP;
        }
    }

    private void drawTraits(GuiGraphics graphics) {
        List<ArenaTrait> traits = filteredTraits();
        int y = LIST_Y + 4 - scroll;
        for (int index = 0; index < traits.size(); index++) {
            ArenaTrait trait = traits.get(index);
            int rowHeight = traitRowHeight(trait);
            if (y + rowHeight >= LIST_Y && y < LIST_Y + LIST_H) {
                graphics.fill(LIST_X + 4, y, LIST_X + LIST_W
                                - SCROLLBAR_W - 6, y + rowHeight,
                        index % 2 == 0 ? COL_ROW : COL_ROW_ALT);
                graphics.fill(LIST_X + 4, y, LIST_X + 7,
                        y + rowHeight, COL_TRAIT);
                graphics.drawString(font,
                        Component.translatable(trait.translationKey()),
                        LIST_X + 16, y + 7, COL_TEXT);
                graphics.drawString(font,
                        Component.translatable(categoryKey(trait.category())),
                        LIST_X + 158, y + 7, COL_ACCENT);
                graphics.drawString(font, thresholds(trait),
                        LIST_X + 270, y + 7, COL_GOLD);
                int lineY = y + 22;
                for (int tier = 1; tier <= trait.maxTier(); tier++) {
                    Component text = Component.translatable(
                            trait.descriptionKey(tier));
                    lineY = drawWrapped(graphics, text,
                            LIST_X + 16, lineY,
                            LIST_W - 105, 6, COL_DIM);
                }
                Component units = Component.translatable(
                        "gui.monster_conquest.arena.compendium.trait_units",
                        unitNamesForTrait(trait));
                drawWrapped(graphics, units, LIST_X + 16,
                        lineY, LIST_W - 105, 3, COL_TRAIT);
            }
            y += rowHeight + ROW_GAP;
        }
    }

    private void drawOpenFilter(GuiGraphics graphics,
                                int mouseX, int mouseY) {
        if (openFilter == null) return;
        int x = switch (openFilter) {
            case UNIT_TRAIT -> 226;
            default -> 24;
        };
        int width = switch (openFilter) {
            case COST -> 194;
            case UNIT_TRAIT, EQUIPMENT_CATEGORY, TRAIT_CATEGORY -> 244;
        };
        int y = FILTER_Y + FILTER_H + 2;
        int count = filterOptionCount(openFilter);
        graphics.fill(x - 1, y - 1, x + width + 1,
                y + count * OPTION_H + 1, COL_BORDER);
        graphics.fill(x, y, x + width,
                y + count * OPTION_H, 0xFF182129);
        for (int index = 0; index < count; index++) {
            int optionY = y + index * OPTION_H;
            boolean hovered = inside(mouseX, mouseY, x, optionY,
                    width, OPTION_H);
            if (hovered || filterSelected(openFilter, index)) {
                graphics.fill(x, optionY, x + width,
                        optionY + OPTION_H,
                        hovered ? 0xFF36516A : 0xFF294052);
            }
            graphics.drawString(font,
                    filterOptionLabel(openFilter, index),
                    x + 7, optionY + 5,
                    index == 0 ? COL_ACCENT : COL_TEXT);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int contentHeight) {
        int maxScroll = Math.max(0, contentHeight - LIST_H);
        if (maxScroll <= 0) return;
        int trackX = LIST_X + LIST_W - SCROLLBAR_W;
        int thumbH = scrollbarThumbHeight(contentHeight);
        int thumbY = scrollbarThumbY(maxScroll, thumbH);
        graphics.fill(trackX, LIST_Y, trackX + SCROLLBAR_W,
                LIST_Y + LIST_H, 0x80202A33);
        graphics.fill(trackX, thumbY,
                trackX + SCROLLBAR_W, thumbY + thumbH, COL_ACCENT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) Math.floor(viewport.localX(mouseX));
        int y = (int) Math.floor(viewport.localY(mouseY));
        if (button != 0) return false;
        if (inside(x, y, 914, 8, 76, 20)) {
            onClose();
            return true;
        }
        FilterType previousFilter = openFilter;
        if (openFilter != null) {
            int popupX = openFilter == FilterType.UNIT_TRAIT ? 226 : 24;
            int popupWidth = openFilter == FilterType.COST ? 194 : 244;
            int popupY = FILTER_Y + FILTER_H + 2;
            int option = (y - popupY) / OPTION_H;
            if (inside(x, y, popupX, popupY, popupWidth,
                    filterOptionCount(openFilter) * OPTION_H)
                    && option >= 0
                    && option < filterOptionCount(openFilter)) {
                selectFilter(openFilter, option);
                openFilter = null;
                return true;
            }
            openFilter = null;
        }
        Page clickedPage = tabAt(x, y);
        if (clickedPage != null) {
            setPage(clickedPage);
            return true;
        }
        FilterType clickedFilter = filterAt(x, y);
        if (clickedFilter != null) {
            openFilter = clickedFilter == previousFilter
                    ? null : clickedFilter;
            return true;
        }
        if (isInsideScrollbar(x, y)
                && beginScrollbarDrag(y)) {
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        int x = (int) Math.floor(viewport.localX(mouseX));
        int y = (int) Math.floor(viewport.localY(mouseY));
        if (inside(x, y, LIST_X, LIST_Y,
                LIST_W, LIST_H) && scrollY != 0.0) {
            scroll -= (int) Math.round(scrollY * 28.0);
            clampScroll(contentHeight());
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY,
                                int button, double dragX,
                                double dragY) {
        if (button == 0 && draggingScrollbar) {
            updateScrollbarDrag(
                    (int) Math.floor(viewport.localY(mouseY)));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY,
                                 int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || ModKeyMappings.OPEN_ARENA_LOBBY.matches(
                keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll += LIST_H - 40;
            clampScroll(contentHeight());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll -= LIST_H - 40;
            clampScroll(contentHeight());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void setPage(Page page) {
        if (this.page == page) return;
        this.page = page;
        scroll = 0;
        openFilter = null;
        draggingScrollbar = false;
    }

    @Nullable
    private Page tabAt(int x, int y) {
        if (y < TAB_Y || y >= TAB_Y + TAB_H) return null;
        int tabX = 220;
        for (Page candidate : Page.values()) {
            if (x >= tabX && x < tabX + TAB_W) return candidate;
            tabX += TAB_W + 8;
        }
        return null;
    }

    @Nullable
    private FilterType filterAt(int x, int y) {
        if (y < FILTER_Y || y >= FILTER_Y + FILTER_H) return null;
        if (page == Page.UNITS) {
            if (x >= 24 && x < 218) return FilterType.COST;
            if (x >= 226 && x < 470) return FilterType.UNIT_TRAIT;
        } else if (page == Page.EQUIPMENT
                && x >= 24 && x < 268) {
            return FilterType.EQUIPMENT_CATEGORY;
        } else if (page == Page.TRAITS
                && x >= 24 && x < 268) {
            return FilterType.TRAIT_CATEGORY;
        }
        return null;
    }

    private List<ArenaUnit> filteredUnits() {
        List<ArenaUnit> result = new ArrayList<>();
        for (ArenaUnit unit : UNITS) {
            if (costFilter > 0 && unit.cost() != costFilter) continue;
            if (traitFilter != null
                    && !ArenaTraitCatalog.traitsFor(unit)
                    .contains(traitFilter)) {
                continue;
            }
            result.add(unit);
        }
        return result;
    }

    private List<ArenaEquipment> filteredEquipment() {
        return Arrays.stream(ArenaEquipment.values())
                .filter(item -> equipmentCategory == null
                        || item.category() == equipmentCategory)
                .toList();
    }

    private List<ArenaTrait> filteredTraits() {
        return Arrays.stream(ArenaTrait.values())
                .filter(trait -> traitCategory == null
                        || trait.category() == traitCategory)
                .toList();
    }

    private int filteredCount() {
        return switch (page) {
            case UNITS -> filteredUnits().size();
            case EQUIPMENT -> filteredEquipment().size();
            case TRAITS -> filteredTraits().size();
        };
    }

    private int contentHeight() {
        if (page == Page.UNITS) {
            int height = 8;
            for (ArenaUnit unit : filteredUnits()) {
                height += unitRowHeight(unit) + ROW_GAP;
            }
            return height;
        }
        if (page == Page.EQUIPMENT) {
            int height = 8;
            for (ArenaEquipment item : filteredEquipment()) {
                height += equipmentRowHeight(item) + ROW_GAP;
            }
            return height;
        }
        int height = 8;
        for (ArenaTrait trait : filteredTraits()) {
            height += traitRowHeight(trait) + ROW_GAP;
        }
        return height;
    }

    private int unitRowHeight(ArenaUnit unit) {
        ArenaStatCalculator.Stats stats = baseStats(unit);
        int height = 34;
        for (Component detail : unitStatLines(unit, stats)) {
            height += wrappedHeight(
                    detail, LIST_W - 90, 20);
        }
        for (Component skillLine : unitSkillLines(unit)) {
            height += wrappedHeight(
                    skillLine, LIST_W - 90, 20);
        }
        return Math.max(UNIT_ROW_MIN_H, height + 8);
    }

    private int equipmentRowHeight(ArenaEquipment item) {
        int height = 34;
        height += wrappedHeight(Component.translatable(
                item.descriptionKey()), LIST_W - 90, 20);
        height += wrappedHeight(equipmentRecipe(item),
                LIST_W - 90, 20);
        return Math.max(EQUIPMENT_ROW_MIN_H, height + 8);
    }

    private int traitRowHeight(ArenaTrait trait) {
        int height = 24;
        for (int tier = 1; tier <= trait.maxTier(); tier++) {
            height += wrappedHeight(Component.translatable(
                    trait.descriptionKey(tier)), LIST_W - 105, 6);
        }
        height += wrappedHeight(Component.translatable(
                        "gui.monster_conquest.arena.compendium.trait_units",
                        unitNamesForTrait(trait)),
                LIST_W - 105, 3);
        return Math.max(52, height + 8);
    }

    private int wrappedHeight(Component text, int width, int maxLines) {
        return Math.min(maxLines, font.split(text, width).size()) * 10;
    }

    private int drawWrapped(GuiGraphics graphics, Component text,
                            int x, int y, int width, int maxLines,
                            int color) {
        List<net.minecraft.util.FormattedCharSequence> lines =
                font.split(text, width);
        int count = Math.min(maxLines, lines.size());
        for (int index = 0; index < count; index++) {
            graphics.drawString(font, lines.get(index),
                    x, y + index * 10, color);
        }
        return y + count * 10;
    }

    private ArenaStatCalculator.Stats baseStats(
            ArenaUnit unit, int star) {
        int unitIndex = ArenaUnitCatalog.ALL.indexOf(unit);
        return ArenaStatCalculator.calculate(
                unitIndex, star, List.of(), List.of(unitIndex));
    }

    private ArenaStatCalculator.Stats baseStats(ArenaUnit unit) {
        return baseStats(unit, 1);
    }

    private List<Component> unitSkillLines(ArenaUnit unit) {
        List<Component> lines = new ArrayList<>();
        List<Component> oneStar = ArenaSkillTooltip.describe(
                unit, 1, baseStats(unit, 1));
        lines.add(oneStar.get(0));
        lines.add(oneStar.get(1));
        for (int star = 1; star <= 3; star++) {
            Component effect = ArenaSkillTooltip.describe(
                    unit, star, baseStats(unit, star)).get(2);
            lines.add(Component.translatable(
                    "gui.monster_conquest.arena.compendium.unit_skill_star",
                    star, effect));
        }
        return lines;
    }

    private List<Component> unitStatLines(
            ArenaUnit unit, ArenaStatCalculator.Stats stats) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                "gui.monster_conquest.arena.compendium.unit_stats_physical",
                number(stats.maxHealth()),
                number(stats.attackDamage()),
                number(stats.armor()),
                number(stats.magicResistance()),
                number(stats.attackSpeed()),
                number(stats.movementSpeed()),
                number(stats.magicPower())));
        lines.add(Component.translatable(
                "gui.monster_conquest.arena.compendium.unit_stats_utility",
                percent(stats.dodgeChance()),
                percent(stats.healingPower()),
                percent(stats.lifesteal()),
                number(stats.initialSkillCharge()),
                percent(stats.skillChargeGainPercent())));
        lines.add(Component.translatable(
                "gui.monster_conquest.arena.compendium.unit_stats_crit",
                percent(stats.critChance()),
                percent(stats.critDamage()),
                Component.translatable(stats.skillCritEnabled()
                        ? "gui.monster_conquest.arena.compendium.yes"
                        : "gui.monster_conquest.arena.compendium.no"),
                number(stats.attackRangeBonus()),
                percent(stats.shieldPower())));
        lines.add(Component.translatable(
                "gui.monster_conquest.arena.compendium.unit_stats_modifiers",
                percent(stats.incomingDamageReduction()),
                percent(stats.skillDamageBonus()),
                percent(stats.startingShieldRatio()),
                percent(stats.traitAttackPercent()),
                percent(stats.traitAttackSpeedPercent()),
                percent(stats.traitMovementPercent())));
        lines.add(Component.translatable(
                "gui.monster_conquest.arena.compendium.unit_template",
                Component.translatable(unit.builtInShield()
                        ? "gui.monster_conquest.arena.compendium.yes"
                        : "gui.monster_conquest.arena.compendium.no")));
        return lines;
    }

    private Component traitNames(ArenaUnit unit) {
        return Component.literal(
                ArenaTraitCatalog.traitsFor(unit).stream()
                        .map(trait -> Component.translatable(
                                trait.translationKey()).getString())
                        .reduce((left, right) -> left + " / " + right)
                        .orElse("-"));
    }

    private Component equipmentStats(ArenaEquipmentStats stats) {
        List<String> values = new ArrayList<>();
        add(values, "health", stats.health());
        if (stats.attackPercent() != 0.0) {
            values.add(label("attack") + " +" + percent(stats.attackPercent()));
        }
        if (stats.attackSpeedPercent() != 0.0) {
            values.add(label("attack_speed") + " +"
                    + percent(stats.attackSpeedPercent()));
        }
        add(values, "armor", stats.armor());
        add(values, "magic_resistance", stats.magicResistance());
        if (stats.movementSpeedPercent() != 0.0) {
            values.add(label("movement") + " +"
                    + percent(stats.movementSpeedPercent()));
        }
        if (stats.dodgeChance() != 0.0) {
            values.add(label("dodge") + " +"
                    + percent(stats.dodgeChance()));
        }
        if (stats.healingPower() != 0.0) {
            values.add(label("healing") + " +"
                    + percent(stats.healingPower()));
        }
        if (stats.lifesteal() != 0.0) {
            values.add(label("lifesteal") + " +"
                    + percent(stats.lifesteal()));
        }
        add(values, "magic_power", stats.magicPower());
        add(values, "initial_skill_charge",
                stats.initialSkillCharge());
        if (stats.skillChargeGainPercent() != 0.0) {
            values.add(label("skill_charge_gain") + " +"
                    + percent(stats.skillChargeGainPercent()));
        }
        if (stats.critChance() != 0.0) {
            values.add(label("crit_chance") + " +"
                    + percent(stats.critChance()));
        }
        if (stats.critDamage() != 0.0) {
            values.add(label("crit_damage") + " +"
                    + percent(stats.critDamage()));
        }
        if (stats.skillCritEnabled()) {
            values.add(Component.translatable(
                    "gui.monster_conquest.arena.compendium.skill_crit")
                    .getString());
        }
        return Component.literal(values.isEmpty()
                ? "-" : String.join(" · ", values));
    }

    private Component equipmentRecipe(ArenaEquipment item) {
        if (item.component()) {
            String combinations = item.combinations().stream()
                    .map(combination ->
                            Component.translatable(item.translationKey())
                                    .getString()
                                    + " + "
                                    + Component.translatable(
                                    combination.ingredient()
                                            .translationKey()).getString()
                                    + " = "
                                    + Component.translatable(
                                    combination.result()
                                            .translationKey()).getString())
                    .reduce((left, right) -> left + "；" + right)
                    .orElse("-");
            return Component.translatable(
                    "gui.monster_conquest.arena.compendium.recipes_from_basic",
                    combinations);
        }
        if (item.category() != ArenaEquipment.Category.COMPLETED) {
            return Component.translatable(
                    "gui.monster_conquest.arena.compendium.recipe_special");
        }
        String recipes = completedRecipeSources(item).stream()
                .map(combination ->
                        Component.translatable(
                                combination.ingredient().translationKey())
                                .getString()
                                + " + "
                                + Component.translatable(
                                combination.result().translationKey())
                                .getString())
                .reduce((left, right) -> left + "；" + right)
                .orElse("-");
        return Component.translatable(
                "gui.monster_conquest.arena.compendium.recipe_completed",
                recipes);
    }

    private static List<ArenaEquipment.Combination> completedRecipeSources(
            ArenaEquipment target) {
        List<ArenaEquipment> components = ArenaEquipment.basicComponents();
        List<ArenaEquipment.Combination> sources = new ArrayList<>();
        for (int first = 0; first < components.size(); first++) {
            for (int second = first; second < components.size(); second++) {
                ArenaEquipment left = components.get(first);
                ArenaEquipment right = components.get(second);
                if (ArenaEquipment.combine(left, right) == target) {
                    sources.add(new ArenaEquipment.Combination(left, right));
                }
            }
        }
        return sources;
    }

    private static void add(List<String> values, String key, double value) {
        if (value == 0.0) return;
        values.add(label(key) + " +" + number(value));
    }

    private static String label(String key) {
        return Component.translatable(
                "gui.monster_conquest.arena.game.unit_tooltip.stat."
                        + key).getString();
    }

    private String unitNamesForTrait(ArenaTrait trait) {
        return UNITS.stream()
                .filter(unit -> ArenaTraitCatalog.traitsFor(unit)
                        .contains(trait))
                .map(unit -> Component.translatable(
                        unit.translationKey()).getString())
                .reduce((left, right) -> left + "、" + right)
                .orElse("-");
    }

    private static String thresholds(ArenaTrait trait) {
        return Arrays.stream(trait.thresholds())
                .mapToObj(Integer::toString)
                .reduce((left, right) -> left + "/" + right)
                .orElse("-");
    }

    private static int costColor(int cost) {
        return switch (cost) {
            case 1 -> 0xFFB8C0C7;
            case 2 -> 0xFF73D68B;
            case 3 -> 0xFF6CB6FF;
            case 4 -> 0xFFD08CFF;
            default -> 0xFFFFB04F;
        };
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String percent(double value) {
        return number(value * 100.0) + "%";
    }

    private Component costLabel() {
        return costFilter == 0
                ? Component.translatable(
                "gui.monster_conquest.arena.compendium.filter.all_costs")
                : Component.translatable(
                "gui.monster_conquest.arena.compendium.cost",
                costFilter);
    }

    private Component traitLabel() {
        return traitFilter == null
                ? Component.translatable(
                "gui.monster_conquest.arena.compendium.filter.all_traits")
                : Component.translatable(traitFilter.translationKey());
    }

    private Component equipmentCategoryLabel() {
        return equipmentCategory == null
                ? Component.translatable(
                "gui.monster_conquest.arena.compendium.filter.all_categories")
                : Component.translatable(
                "gui.monster_conquest.arena.equipment.category."
                        + equipmentCategory.name().toLowerCase(Locale.ROOT));
    }

    private Component traitCategoryLabel() {
        return traitCategory == null
                ? Component.translatable(
                "gui.monster_conquest.arena.compendium.filter.all_categories")
                : Component.translatable(categoryKey(traitCategory));
    }

    private int filterOptionCount(FilterType type) {
        return switch (type) {
            case COST -> 6;
            case UNIT_TRAIT -> ArenaTrait.values().length + 1;
            case EQUIPMENT_CATEGORY ->
                    ArenaEquipment.Category.values().length + 1;
            case TRAIT_CATEGORY -> ArenaTrait.Category.values().length + 1;
        };
    }

    private Component filterOptionLabel(FilterType type, int index) {
        if (index == 0) {
            return Component.translatable(switch (type) {
                case COST ->
                        "gui.monster_conquest.arena.compendium.filter.all_costs";
                case UNIT_TRAIT ->
                        "gui.monster_conquest.arena.compendium.filter.all_traits";
                case EQUIPMENT_CATEGORY, TRAIT_CATEGORY ->
                        "gui.monster_conquest.arena.compendium.filter.all_categories";
            });
        }
        return switch (type) {
            case COST -> Component.translatable(
                    "gui.monster_conquest.arena.compendium.cost", index);
            case UNIT_TRAIT -> Component.translatable(
                    ArenaTrait.values()[index - 1].translationKey());
            case EQUIPMENT_CATEGORY -> Component.translatable(
                    "gui.monster_conquest.arena.equipment.category."
                            + ArenaEquipment.Category.values()[index - 1]
                            .name().toLowerCase(Locale.ROOT));
            case TRAIT_CATEGORY -> Component.translatable(
                    categoryKey(ArenaTrait.Category.values()[index - 1]));
        };
    }

    private boolean filterSelected(FilterType type, int index) {
        return switch (type) {
            case COST -> costFilter == index;
            case UNIT_TRAIT -> index == 0
                    ? traitFilter == null
                    : traitFilter == ArenaTrait.values()[index - 1];
            case EQUIPMENT_CATEGORY -> index == 0
                    ? equipmentCategory == null
                    : equipmentCategory
                    == ArenaEquipment.Category.values()[index - 1];
            case TRAIT_CATEGORY -> index == 0
                    ? traitCategory == null
                    : traitCategory
                    == ArenaTrait.Category.values()[index - 1];
        };
    }

    private void selectFilter(FilterType type, int index) {
        switch (type) {
            case COST -> costFilter = index;
            case UNIT_TRAIT -> traitFilter = index == 0
                    ? null : ArenaTrait.values()[index - 1];
            case EQUIPMENT_CATEGORY -> equipmentCategory = index == 0
                    ? null : ArenaEquipment.Category.values()[index - 1];
            case TRAIT_CATEGORY -> traitCategory = index == 0
                    ? null : ArenaTrait.Category.values()[index - 1];
        }
        scroll = 0;
    }

    private static String categoryKey(ArenaTrait.Category category) {
        return "gui.monster_conquest.arena.compendium.category."
                + category.name().toLowerCase(Locale.ROOT);
    }

    private boolean isInsideScrollbar(int x, int y) {
        return x >= LIST_X + LIST_W - SCROLLBAR_W
                && x < LIST_X + LIST_W
                && y >= LIST_Y && y < LIST_Y + LIST_H
                && contentHeight() > LIST_H;
    }

    private boolean beginScrollbarDrag(int mouseY) {
        int contentHeight = contentHeight();
        int maxScroll = Math.max(0, contentHeight - LIST_H);
        if (maxScroll <= 0) return false;
        int thumbH = scrollbarThumbHeight(contentHeight);
        int thumbY = scrollbarThumbY(maxScroll, thumbH);
        draggingScrollbar = true;
        scrollbarGrabOffset = mouseY >= thumbY && mouseY < thumbY + thumbH
                ? mouseY - thumbY : thumbH / 2.0;
        updateScrollbarDrag(mouseY);
        return true;
    }

    private void updateScrollbarDrag(int mouseY) {
        int contentHeight = contentHeight();
        int maxScroll = Math.max(0, contentHeight - LIST_H);
        int thumbH = scrollbarThumbHeight(contentHeight);
        int travel = LIST_H - thumbH;
        if (maxScroll <= 0 || travel <= 0) {
            scroll = 0;
            return;
        }
        double relative = (mouseY - scrollbarGrabOffset - LIST_Y) / travel;
        scroll = Mth.clamp((int) Math.round(relative * maxScroll),
                0, maxScroll);
    }

    private int scrollbarThumbHeight(int contentHeight) {
        if (contentHeight <= LIST_H) return LIST_H;
        return Math.max(24, LIST_H * LIST_H / contentHeight);
    }

    private int scrollbarThumbY(int maxScroll, int thumbHeight) {
        int travel = LIST_H - thumbHeight;
        return LIST_Y + (maxScroll == 0 ? 0
                : travel * scroll / maxScroll);
    }

    private void clampScroll(int contentHeight) {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - LIST_H));
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y,
                                  int width, int height) {
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    private enum Page {
        UNITS("gui.monster_conquest.arena.compendium.tab.units"),
        EQUIPMENT("gui.monster_conquest.arena.compendium.tab.equipment"),
        TRAITS("gui.monster_conquest.arena.compendium.tab.traits");

        private final String translationKey;

        Page(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private enum FilterType {
        COST,
        UNIT_TRAIT,
        EQUIPMENT_CATEGORY,
        TRAIT_CATEGORY
    }
}
