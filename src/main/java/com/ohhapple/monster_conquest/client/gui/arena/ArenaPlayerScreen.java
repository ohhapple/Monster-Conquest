package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.arena.game.ArenaPhase;
import com.ohhapple.monster_conquest.arena.game.ArenaEquipment;
import com.ohhapple.monster_conquest.arena.game.ArenaPlayerState;
import com.ohhapple.monster_conquest.arena.game.ArenaStatCalculator;
import com.ohhapple.monster_conquest.arena.game.ArenaUnitCatalog;
import com.ohhapple.monster_conquest.arena.trait.ArenaTrait;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitAnalyzer;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitCatalog;
import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;
import com.ohhapple.monster_conquest.client.gui.GuiViewport;
import com.ohhapple.monster_conquest.client.input.ModKeyMappings;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCombatStatsPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.LiveStats;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.PieceView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.PlayerStatus;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.RoundView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.ShopCard;
import com.ohhapple.monster_conquest.network.serverbound.arena.ArenaIntentPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.UUID;

/**
 * 自走棋对局主界面。棋盘与备战席保持视觉中心，右侧只承担对手和观战入口，
 * 所有操作权限仍由服务端按阶段和存活状态做权威校验。
 */
public class ArenaPlayerScreen extends Screen {

    private static final int PANEL_W = 1080;
    private static final int PANEL_H = 680;
    private static final int OUTER_PAD = 14;

    private static final int LEFT_X = OUTER_PAD;
    private static final int LEFT_W = 190;
    private static final int RIGHT_W = 220;
    private static final int RIGHT_X = PANEL_W - OUTER_PAD - RIGHT_W;

    private static final int BOARD_Y = 142;
    private static final int CELL_W = 56;
    private static final int CELL_H = 56;
    private static final int BOARD_W = ArenaPlayerState.BOARD_COLUMNS * CELL_W;
    private static final int BOARD_H = ArenaPlayerState.BOARD_ROWS * CELL_H;
    private static final int BOARD_X = LEFT_X + LEFT_W
            + (RIGHT_X - LEFT_X - LEFT_W - BOARD_W) / 2;

    private static final int BENCH_CELL_W = CELL_W;
    private static final int BENCH_SLOT_W = 48;
    private static final int BENCH_CELL_H = 48;
    private static final int BENCH_W = ArenaPlayerState.BENCH_SIZE * BENCH_CELL_W;
    private static final int BENCH_X = BOARD_X;
    private static final int BENCH_Y = BOARD_Y + BOARD_H + 16;

    private static final int PLAYER_Y = 140;
    private static final int PLAYER_ROW_H = 52;
    private static final int EQUIPMENT_COLUMNS =
            ArenaPlayerState.BOARD_COLUMNS;
    private static final int EQUIPMENT_CELL_W =
            BOARD_W / EQUIPMENT_COLUMNS;
    private static final int EQUIPMENT_SLOT_W = 48;
    private static final int EQUIPMENT_SLOT_H = 48;
    private static final int EQUIPMENT_ROW_H = 49;
    private static final int EQUIPMENT_X = BENCH_X;
    private static final int EQUIPMENT_LABEL_Y = BENCH_Y + 54;
    private static final int EQUIPMENT_Y = BENCH_Y + 62;
    private static final int EQUIPMENT_ROWS =
            ArenaPlayerState.EQUIPMENT_ROWS;
    private static final int ROUND_DOT_SIZE = 20;
    private static final int ROUND_DOT_GAP = 5;
    private static final int RIGHT_TAB_X = RIGHT_X + 7;
    private static final int RIGHT_TAB_Y = PLAYER_Y + 6;
    private static final int RIGHT_TAB_W = 99;
    private static final int RIGHT_TAB_H = 20;
    private static final int RIGHT_STAT_Y = PLAYER_Y + 34;
    private static final int RIGHT_STAT_H = 18;
    private static final int RIGHT_ROWS_Y = PLAYER_Y + 61;
    private static final int RIGHT_UNIT_ROW_H = 39;

    private static final int COL_BG = 0xF00D1217;
    private static final int COL_PANEL = 0xD519232B;
    private static final int COL_PANEL_SOFT = 0xA0202A32;
    private static final int COL_BORDER = 0xFF40515F;
    private static final int COL_TEXT = 0xFFE7EDF3;
    private static final int COL_DIM = 0xFF93A1AE;
    private static final int COL_ACCENT = 0xFF67B7FF;
    private static final int COL_GOLD = 0xFFF2C45E;
    private static final int COL_OK = 0xFF76DA8C;
    private static final int COL_BAD = 0xFFF07C7C;
    private static final int COL_TRAIT = 0xFF78D7FF;
    private static final int COL_STAT = 0xFF9DD7FF;
    private static final int COL_ARCANE = 0xFFD5A6FF;
    private static final int COL_MOVEMENT = 0xFF9BE7B0;
    private static final int COL_PHYSICAL = 0xFFFF6262;
    private static final int COL_MAGIC = 0xFFB979FF;
    private static final int COL_TRUE = 0xFFFFFFFF;
    private static final int COL_HEALING = 0xFF65D88C;

    private enum RightPanelMode {
        PARTICIPANTS,
        UNIT_STATS
    }

    private enum UnitStatMode {
        DAMAGE_DEALT,
        HEALING,
        DAMAGE_TAKEN
    }

    private static RightPanelMode rightPanelMode =
            RightPanelMode.PARTICIPANTS;
    private static UnitStatMode unitStatMode =
            UnitStatMode.DAMAGE_DEALT;

    private static final int TRAIT_LABEL_Y = PLAYER_Y + 96;
    private static final int TRAIT_LIST_X = LEFT_X + 10;
    private static final int TRAIT_LIST_Y = PLAYER_Y + 116;
    private static final int TRAIT_LIST_W = LEFT_W - 32;
    private static final int TRAIT_ROW_H = 20;
    private static final int TRAIT_VISIBLE_ROWS = 8;
    private static final int TRAIT_SCROLLBAR_X = LEFT_X + LEFT_W - 10;
    private static final int TRAIT_SCROLLBAR_W = 5;
    private static final int SHOP_ODDS_TITLE_Y = 420;
    private static final int SHOP_ODDS_ENTRY_Y = 433;
    private static final int LEFT_REFRESH_Y = 458;
    private static final int LEFT_XP_Y = 487;
    private static final int LEFT_HOTKEYS_Y = 517;
    private static final int TRAIT_DETAIL_W = 700;
    private static final int TRAIT_DETAIL_H = 500;
    private static final int TRAIT_DETAIL_X =
            (PANEL_W - TRAIT_DETAIL_W) / 2;
    private static final int TRAIT_DETAIL_Y = 90;

    private GuiViewport viewport = new GuiViewport(1.0f, 0, 0);
    private Button refreshButton;
    private Button xpButton;
    private Button compendiumButton;
    private Button closeButton;
    private Button turnButton;
    private Button confirmSurrenderButton;
    private Button cancelSurrenderButton;
    @Nullable private UUID selectedPiece;
    @Nullable private Integer selectedEquipmentIndex;
    @Nullable private ArenaTrait selectedTrait;
    private int traitScroll;
    private boolean draggingTraitScrollbar;
    private double traitScrollGrabOffset;
    private boolean sentOpen;
    private boolean confirmingSurrender;
    private boolean keepSessionOnScreenChange;

    public ArenaPlayerScreen() {
        super(Component.translatable("gui.monster_conquest.arena.game.title"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(new ArenaPlayerScreen());
    }

    @Override
    protected void init() {
        super.init();
        viewport = GuiViewport.fit(width, height, PANEL_W, PANEL_H, 6);

        refreshButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.game.refresh_hotkey",
                                "D"),
                        ignored -> {
                            ArenaUiSounds.playButtonClick();
                            sendIntent(
                                    ArenaIntentPayload.REFRESH,
                                    0, 0, null);
                        })
                .bounds(LEFT_X + 12, LEFT_REFRESH_Y, LEFT_W - 24, 24)
                .build());
        refreshButton.setTooltip(Tooltip.create(Component.translatable(
                "gui.monster_conquest.arena.game.refresh_tip")));

        xpButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.game.buy_xp_hotkey",
                                "F"),
                        ignored -> {
                            ArenaUiSounds.playButtonClick();
                            sendIntent(
                                    ArenaIntentPayload.BUY_XP,
                                    0, 0, null);
                        })
                .bounds(LEFT_X + 12, LEFT_XP_Y, LEFT_W - 24, 24)
                .build());

        turnButton = addRenderableWidget(Button.builder(
                        Component.empty(),
                        ignored -> onTurnButton())
                .bounds(LEFT_X + 12, 584, LEFT_W - 24, 24)
                .build());

        confirmSurrenderButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.game.surrender_confirm"),
                        ignored -> {
                            confirmingSurrender = false;
                            sendIntent(
                                    ArenaIntentPayload.SURRENDER, 0, 0, null);
                            syncWidgets();
                        })
                .bounds(PANEL_W / 2 - 146, PANEL_H / 2 + 16, 136, 24)
                .build());

        cancelSurrenderButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.game.surrender_cancel"),
                        ignored -> {
                            confirmingSurrender = false;
                            syncWidgets();
                        })
                .bounds(PANEL_W / 2 + 10, PANEL_H / 2 + 16, 136, 24)
                .build());

        closeButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.game.close"),
                        ignored -> onClose())
                .bounds(PANEL_W - 92, 5, 78, 22)
                .build());

        compendiumButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.compendium.button"),
                        ignored -> openCompendium())
                .bounds(PANEL_W - 174, 5, 78, 22)
                .build());

        syncWidgets();
        if (!sentOpen) {
            sentOpen = true;
            sendIntent(ArenaIntentPayload.OPEN, 0, 0, null);
        }
    }

    public void onSnapshotUpdated() {
        ArenaPlayerSnapshotPayload snapshot = snapshot();
        if (snapshot == null) return;
        if (selectedPiece != null) {
            PieceView selected = pieceById(snapshot, selectedPiece);
            if (selected == null
                    || (benchOnlyPhase(snapshot) && selected.boardSlot() >= 0)) {
                selectedPiece = null;
            }
        }
        if (!snapshot.alive() || snapshot.spectator()) {
            confirmingSurrender = false;
        }
        if (snapshot.spectator()) {
            selectedEquipmentIndex = null;
            selectedPiece = null;
        }
        List<ArenaTraitAnalyzer.Status> presentTraits =
                presentTraitStatuses(snapshot);
        if (selectedTrait != null
                && presentTraits.stream().noneMatch(status ->
                status.trait() == selectedTrait)) {
            selectedTrait = null;
        }
        traitScroll = clampTraitScroll(
                traitScroll, presentTraits.size());
        if (selectedEquipmentIndex != null
                && (selectedEquipmentIndex
                >= snapshot.equipmentInventory().size()
                || snapshot.equipmentInventory()
                .get(selectedEquipmentIndex) < 0)) {
            selectedEquipmentIndex = null;
        }
        syncWidgets();
    }

    private void syncWidgets() {
        ArenaPlayerSnapshotPayload snapshot = snapshot();
        boolean active = snapshot != null && snapshot.active();
        boolean economy = active && canUseEconomy(snapshot);
        boolean spectator = snapshot != null && snapshot.spectator();
        boolean traitModal = selectedTrait != null;
        if (refreshButton != null) {
            refreshButton.visible = !traitModal && !spectator;
            refreshButton.active = economy && !traitModal;
        }
        if (xpButton != null) {
            xpButton.visible = !traitModal && !spectator;
            xpButton.active = economy && !traitModal;
        }

        boolean showTurn = active && !traitModal;
        boolean showConfirm = showTurn && !spectator && snapshot.alive()
                && confirmingSurrender;
        if (turnButton != null) {
            turnButton.visible = showTurn && !showConfirm;
            turnButton.active = showTurn;
            turnButton.setMessage(Component.translatable(snapshot != null
                    && snapshot.alive() && !spectator
                    ? "gui.monster_conquest.arena.game.surrender"
                    : "gui.monster_conquest.arena.game.exit_world"));
        }
        if (confirmSurrenderButton != null) {
            confirmSurrenderButton.visible = showConfirm;
            confirmSurrenderButton.active = showConfirm;
        }
        if (cancelSurrenderButton != null) {
            cancelSurrenderButton.visible = showConfirm;
            cancelSurrenderButton.active = showConfirm;
        }
        if (closeButton != null) {
            closeButton.visible = !traitModal;
            closeButton.active = !traitModal;
        }
        if (compendiumButton != null) {
            compendiumButton.visible = !traitModal
                    && !confirmingSurrender;
            compendiumButton.active = compendiumButton.visible;
        }
    }

    private void onTurnButton() {
        ArenaPlayerSnapshotPayload snapshot = snapshot();
        if (snapshot == null || !snapshot.active()) return;
        if (snapshot.spectator() || !snapshot.alive()) {
            sendIntent(ArenaIntentPayload.EXIT_MATCH, 0, 0, null);
            return;
        }
        confirmingSurrender = true;
        syncWidgets();
    }

    @Nullable
    private static ArenaPlayerSnapshotPayload snapshot() {
        return ArenaGameClientState.snapshot();
    }

    private static void sendIntent(int action, int targetType, int targetIndex,
                                   @Nullable UUID pieceId) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new ArenaIntentPayload(
                action, targetType, targetIndex,
                pieceId == null ? new UUID(0L, 0L) : pieceId));
    }

    @Override
    public void removed() {
        if (!keepSessionOnScreenChange
                && Minecraft.getInstance().getConnection() != null) {
            sendIntent(ArenaIntentPayload.CLOSE, 0, 0, null);
        }
        super.removed();
    }

    private void openCompendium() {
        keepSessionOnScreenChange = true;
        Minecraft.getInstance().setScreen(
                new ArenaCompendiumScreen(this));
        keepSessionOnScreenChange = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY,
                                 float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY,
                       float partialTick) {
        int localMouseX = viewport.localX(mouseX);
        int localMouseY = viewport.localY(mouseY);
        syncWidgets();

        graphics.fill(0, 0, width, height, 0xC0000000);
        viewport.push(graphics);
        graphics.fill(-1, -1, PANEL_W + 1, PANEL_H + 1, COL_BORDER);
        graphics.fill(0, 0, PANEL_W, PANEL_H, COL_BG);
        graphics.fill(0, 0, PANEL_W, 32, 0xE01A2229);
        graphics.drawString(font, title, 16, 11, COL_TEXT);

        ArenaPlayerSnapshotPayload snapshot = snapshot();
        List<Component> hoveredTooltip = null;
        if (snapshot == null || !snapshot.active()) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.no_match"),
                    PANEL_W / 2, PANEL_H / 2, COL_DIM);
        } else {
            drawHeader(graphics, snapshot);
            drawTimer(graphics, snapshot);
            List<Component> leftTooltip = drawLeftPanel(
                    graphics, snapshot, localMouseX, localMouseY);
            List<Component> boardTooltip = drawBoardArea(
                    graphics, snapshot, localMouseX, localMouseY);
            if (snapshot.spectator()) {
                hoveredTooltip = leftTooltip != null
                        ? leftTooltip : boardTooltip;
            } else {
                List<Component> shopTooltip = drawShop(
                        graphics, snapshot, localMouseX, localMouseY);
                List<Component> benchTooltip = drawBenchArea(
                        graphics, snapshot, localMouseX, localMouseY);
                List<Component> equipmentTooltip = drawEquipmentWarehouse(
                        graphics, snapshot, localMouseX, localMouseY);
                hoveredTooltip = leftTooltip != null
                        ? leftTooltip
                        : (shopTooltip != null
                        ? shopTooltip
                        : (boardTooltip != null
                        ? boardTooltip
                        : (benchTooltip != null
                        ? benchTooltip : equipmentTooltip)));
            }
            drawPlayerColumn(graphics, snapshot, localMouseX, localMouseY);
            drawFooter(graphics, snapshot);
            if (selectedTrait != null) {
                List<Component> traitDetailTooltip =
                        drawTraitDetails(
                                graphics, snapshot, selectedTrait,
                                localMouseX, localMouseY);
                hoveredTooltip = traitDetailTooltip;
            }
            if (confirmingSurrender) drawSurrenderConfirmation(graphics);
        }

        super.render(graphics, localMouseX, localMouseY, partialTick);
        ArenaPlayerSnapshotPayload finalSnapshot =
                ArenaGameClientState.snapshot();
        if (finalSnapshot != null) {
            drawChampionOverlay(graphics, finalSnapshot);
        }
        viewport.pop(graphics);
        if (hoveredTooltip != null) {
            renderClampedTooltip(
                    graphics, hoveredTooltip, mouseX, mouseY,
                    selectedTrait != null ? 600.0f : 0.0f);
        }
    }

    private void drawChampionOverlay(
            GuiGraphics graphics,
            ArenaPlayerSnapshotPayload snapshot) {
        if (snapshot.phase() != ArenaPhase.FINISHED.ordinal()
                || snapshot.championName().isBlank()) {
            return;
        }
        graphics.fill(0, 0, PANEL_W, PANEL_H, 0x66000000);
        Component title = Component.translatable(
                "gui.monster_conquest.arena.game.champion",
                snapshot.championName());
        float scale = Math.min(2.0f,
                Math.max(0.5f, (PANEL_W - 48.0f)
                        / Math.max(1, font.width(title))));
        int titleWidth = (int) (font.width(title) * scale);
        drawScaledStringShadowed(
                graphics, title,
                (PANEL_W - titleWidth) / 2,
                PANEL_H / 2 - 24,
                COL_GOLD, scale);
        graphics.drawCenteredString(
                font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.champion_exit"),
                PANEL_W / 2,
                PANEL_H / 2 + 32,
                COL_GOLD);
    }

    private void drawHeader(GuiGraphics graphics,
                            ArenaPlayerSnapshotPayload snapshot) {
        String phase = Component.translatable(phaseKey(snapshot.phase())).getString();
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.round_status",
                        stageRoundLabel(snapshot.round()), phase),
                PANEL_W / 2 - 180, 11,
                (snapshot.phase() == ArenaPhase.BATTLE.ordinal()
                || snapshot.phase() == ArenaPhase.OVERTIME.ordinal())
                        ? COL_BAD : COL_ACCENT);
        drawRoundDots(graphics, snapshot.rounds());
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.controls"),
                PANEL_W - 310, 11, COL_DIM);
    }

    private void drawRoundDots(
            GuiGraphics graphics, List<RoundView> rounds) {
        int startX = PANEL_W / 2 + 35;
        int y = 6;
        for (int index = 0; index < rounds.size(); index++) {
            RoundView round = rounds.get(index);
            int x = startX + index * (ROUND_DOT_SIZE
                    + ROUND_DOT_GAP);
            int centerX = x + ROUND_DOT_SIZE / 2;
            int centerY = y + ROUND_DOT_SIZE / 2;
            if (round.current()) {
                fillCircle(graphics, centerX, centerY, 11,
                        COL_ACCENT);
            }
            int border = roundBorderColor(round);
            fillCircle(graphics, centerX, centerY, 9, border);
            if (round.wild()) {
                fillCircle(graphics, centerX, centerY, 7,
                        0xFFF4F7F9);
                drawCreeperFace(
                        graphics, centerX - 4, centerY - 4);
            } else {
                fillCircle(graphics, centerX, centerY, 7,
                        roundBackgroundColor(round));
                if (!round.opponentId().equals(
                        new UUID(0L, 0L))) {
                    drawPlayerFace(graphics,
                            round.opponentId(),
                            centerX - 5, centerY - 5, 10);
                }
            }
        }
    }

    private static int roundBorderColor(RoundView round) {
        if (round.wild()) return 0xFFF6F8FA;
        return switch (round.result()) {
            case ArenaPlayerSnapshotPayload.BATTLE_WON ->
                    0xFF65DA83;
            case ArenaPlayerSnapshotPayload.BATTLE_LOST ->
                    0xFFF06D6D;
            default -> round.current()
                    ? COL_ACCENT : COL_BORDER;
        };
    }

    private static int roundBackgroundColor(RoundView round) {
        return switch (round.result()) {
            case ArenaPlayerSnapshotPayload.BATTLE_WON ->
                    0xFF214B30;
            case ArenaPlayerSnapshotPayload.BATTLE_LOST ->
                    0xFF512426;
            default -> round.current()
                    ? 0xFF263B52 : 0xFF151D24;
        };
    }

    private static void fillCircle(
            GuiGraphics graphics, int centerX, int centerY,
            int radius, int color) {
        for (int offsetY = -radius;
             offsetY <= radius; offsetY++) {
            int halfWidth = (int) Math.floor(Math.sqrt(
                    radius * radius - offsetY * offsetY));
            graphics.fill(centerX - halfWidth,
                    centerY + offsetY,
                    centerX + halfWidth + 1,
                    centerY + offsetY + 1, color);
        }
    }

    private static void drawCreeperFace(
            GuiGraphics graphics, int x, int y) {
        String[] face = {
                "..XXXX..",
                "..XXXX..",
                ".XXXXXX.",
                ".XXXXXX.",
                "XX.XX.XX",
                "XX.XX.XX",
                ".XX..XX.",
                "........"
        };
        for (int row = 0; row < face.length; row++) {
            for (int column = 0;
                 column < face[row].length(); column++) {
                if (face[row].charAt(column) == 'X') {
                    graphics.fill(x + column, y + row,
                            x + column + 1, y + row + 1,
                            0xFF1F3B2A);
                }
            }
        }
    }

    @Nullable
    private List<Component> drawShop(
            GuiGraphics graphics, ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.shop"),
                18, 38, COL_TEXT);
        int cardX = 18;
        int cardY = 52;
        int gap = 8;
        int cardW = (PANEL_W - cardX * 2 - gap * 4) / 5;
        int cardH = 56;
        boolean usable = canUseEconomy(snapshot);
        List<Component> tooltip = null;
        for (int i = 0; i < snapshot.shop().size(); i++) {
            ShopCard card = snapshot.shop().get(i);
            int x = cardX + i * (cardW + gap);
            boolean hovered = mouseX >= x && mouseX < x + cardW
                    && mouseY >= cardY && mouseY < cardY + cardH;
            int border = card.unitIndex() < 0
                    ? COL_BORDER
                    : shopBorderColor(ArenaUnitCatalog.get(
                    card.unitIndex()).cost());
            int background = card.unitIndex() < 0
                    ? COL_PANEL_SOFT
                    : shopBackgroundColor(ArenaUnitCatalog.get(
                    card.unitIndex()).cost(), hovered);
            graphics.fill(x - 1, cardY - 1, x + cardW + 1,
                    cardY + cardH + 1, border);
            graphics.fill(x, cardY, x + cardW, cardY + cardH, background);
            if (card.unitIndex() < 0) {
                graphics.drawCenteredString(font,
                        Component.translatable(
                                "gui.monster_conquest.arena.game.sold"),
                        x + cardW / 2, cardY + cardH / 2 - 4,
                        COL_DIM);
                continue;
            }
            ArenaUnit unit = ArenaUnitCatalog.get(card.unitIndex());
            if (hovered) {
                tooltip = unitTooltip(
                        snapshot, card.unitIndex(),
                        1, List.of(), null);
            }
            graphics.blit(unit.icon(), x + 9, cardY + 10,
                    32, 32, 0.0f, 0.0f, 32, 32, 32, 32);
            Component unitName =
                    Component.translatable(unit.translationKey());
            Component traitsText =
                    Component.literal(traitNames(card.traits()));
            int textX = x + 48;
            int textWidth = cardW - 56;
            drawScaledString(graphics,
                    unitName, textX, cardY + 5,
                    usable ? COL_TEXT : COL_DIM,
                    fittedTextScale(
                            unitName, textWidth,
                            1.20f, 0.90f));
            drawScaledString(graphics,
                    traitsText, textX, cardY + 22,
                    shopTraitColor(card.traits()),
                    fittedTextScale(
                            traitsText, textWidth,
                            1.08f,
                            usable ? 0.78f : 0.72f));
            Component costAndStar = Component.translatable(
                    "gui.monster_conquest.arena.game.cost_card",
                    unit.cost(), 1);
            drawScaledString(graphics,
                    costAndStar, textX, cardY + 39,
                    usable ? 0xFFC4A552 : COL_DIM,
                    0.72f);
        }
        return tooltip;
    }

    private float fittedTextScale(
            Component text, int maxWidth,
            float preferred, float minimum) {
        int width = font.width(text);
        if (width <= 0) return preferred;
        return Math.max(minimum,
                Math.min(preferred,
                        maxWidth / (float) width));
    }

    private static int shopTraitColor(List<Integer> traitIds) {
        if (traitIds.isEmpty()) return COL_DIM;
        int id = traitIds.get(0);
        ArenaTrait[] traits = ArenaTrait.values();
        if (id < 0 || id >= traits.length) return COL_TRAIT;
        return switch (traits[id].category()) {
            case ORIGIN -> COL_TRAIT;
            case TACTICAL -> COL_GOLD;
            case ECONOMY -> COL_MOVEMENT;
        };
    }

    private void drawTimer(GuiGraphics graphics,
                           ArenaPlayerSnapshotPayload snapshot) {
        int x = 18;
        int y = 116;
        int w = PANEL_W - 36;
        int h = 11;
        graphics.fill(x, y, x + w, y + h, 0x80000000);
        float fraction = snapshot.phaseTicksTotal() <= 0
                ? 0.0f
                : Math.max(0.0f, Math.min(1.0f,
                snapshot.phaseTicksLeft()
                        / (float) snapshot.phaseTicksTotal()));
        int color = snapshot.phase() == ArenaPhase.BATTLE.ordinal()
                || snapshot.phase() == ArenaPhase.OVERTIME.ordinal()
                ? COL_BAD : COL_ACCENT;
        graphics.fill(x, y, x + Math.round(w * fraction), y + h, color);
        String seconds = String.format(Locale.ROOT, "%.1f",
                snapshot.phaseTicksLeft() / 20.0);
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.timer", seconds),
                x + 6, y, 0xFFFFFFFF);
    }

    @Nullable
    private List<Component> drawLeftPanel(
            GuiGraphics graphics,
            ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        boolean spectator = snapshot.spectator();
        graphics.fill(LEFT_X, PLAYER_Y, LEFT_X + LEFT_W,
                PANEL_H - 18, COL_PANEL);
        List<Integer> roster = snapshot.pieces().stream()
                .filter(piece -> piece.boardSlot() >= 0)
                .map(PieceView::unitIndex)
                .toList();
        ArenaTraitAnalyzer.State traitState =
                ArenaTraitAnalyzer.analyze(roster);
        List<ArenaTraitAnalyzer.Status> present =
                traitState.presentStatuses();

        drawScaledString(graphics,
                Component.translatable(
                        "gui.monster_conquest.arena.game.status_health",
                        snapshot.health()),
                LEFT_X + 12, PLAYER_Y + 10,
                snapshot.alive() ? COL_TEXT : COL_DIM, 1.25f);
        List<Component> tooltip = null;
        if (!spectator) {
            Component goldText = Component.translatable(
                    "gui.monster_conquest.arena.game.status_resources_gold",
                    snapshot.gold());
            drawScaledString(graphics, goldText,
                    LEFT_X + 12, PLAYER_Y + 30,
                    snapshot.alive() ? COL_GOLD : COL_DIM, 1.20f);
            tooltip = isHoveredScaledText(
                    goldText, LEFT_X + 12, PLAYER_Y + 30,
                    1.20f, mouseX, mouseY)
                    ? goldTooltip(snapshot) : null;
            Component levelXpText = Component.translatable(
                    "gui.monster_conquest.arena.game.status_resources_level_xp",
                    snapshot.level(), snapshot.xp(), snapshot.xpToNext());
            drawScaledString(graphics, levelXpText,
                    LEFT_X + 12, PLAYER_Y + 48,
                    snapshot.alive() ? COL_GOLD : COL_DIM, 1.10f);
        }
        int opponentY = PLAYER_Y + (spectator ? 34 : 66);
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.opponent",
                        snapshot.opponentName().isEmpty()
                                ? "-" : snapshot.opponentName()),
                LEFT_X + 12, opponentY, COL_TEXT);
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.last_damage",
                        snapshot.lastDamage()),
                LEFT_X + 12, opponentY + 18,
                snapshot.lastDamage() > 0 ? COL_BAD : COL_DIM);

        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.traits"),
                LEFT_X + 12, TRAIT_LABEL_Y, COL_TEXT);
        traitScroll = clampTraitScroll(
                traitScroll, present.size());
        int visible = Math.min(
                TRAIT_VISIBLE_ROWS,
                present.size() - traitScroll);
        for (int index = 0; index < visible; index++) {
            ArenaTraitAnalyzer.Status status =
                    present.get(traitScroll + index);
            int rowX = TRAIT_LIST_X;
            int rowY = TRAIT_LIST_Y + index * TRAIT_ROW_H;
            boolean hovered = mouseX >= rowX
                    && mouseX < rowX + TRAIT_LIST_W
                    && mouseY >= rowY
                    && mouseY < rowY + TRAIT_ROW_H - 2;
            if (selectedTrait == status.trait() || hovered) {
                graphics.fill(rowX - 2, rowY - 1,
                        rowX + TRAIT_LIST_W,
                        rowY + TRAIT_ROW_H - 1,
                        selectedTrait == status.trait()
                                ? (status.tier() > 0
                                ? 0x70426A47 : 0x604A5159)
                                : 0x50304A5A);
            }
            drawScaledString(graphics,
                    traitProgressLabel(status),
                    rowX + 3, rowY + 4,
                    status.tier() > 0 ? COL_OK : COL_DIM,
                    1.30f);
            if (hovered) {
                tooltip = traitTooltip(status);
            }
        }
        if (present.size() > TRAIT_VISIBLE_ROWS) {
            int trackTop = traitListTop();
            int trackBottom = traitListBottom();
            graphics.fill(TRAIT_SCROLLBAR_X, trackTop,
                    TRAIT_SCROLLBAR_X + TRAIT_SCROLLBAR_W,
                    trackBottom, 0x70232C33);
            int thumbTop = traitScrollbarThumbY(
                    present.size(), traitScroll);
            int thumbBottom = thumbTop
                    + traitScrollbarThumbHeight(present.size());
            graphics.fill(TRAIT_SCROLLBAR_X - 1, thumbTop,
                    TRAIT_SCROLLBAR_X + TRAIT_SCROLLBAR_W + 1,
                    thumbBottom, 0xFF79C9E8);
        } else {
            traitScroll = 0;
        }

        if (spectator || !snapshot.alive()) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.spectator_mode"),
                    LEFT_X + LEFT_W / 2, 538, COL_BAD);
        } else {
            drawShopOdds(graphics, snapshot.level());

            graphics.drawString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.hotkeys_title"),
                    LEFT_X + 12, LEFT_HOTKEYS_Y, COL_TEXT);
            graphics.drawString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.hotkey_sell"),
                    LEFT_X + 12, LEFT_HOTKEYS_Y + 17, COL_DIM);
            graphics.drawString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.hotkey_space"),
                    LEFT_X + 12, LEFT_HOTKEYS_Y + 32, COL_DIM);
            graphics.drawString(font,
                    Component.translatable(benchOnlyPhase(snapshot)
                                    ? "gui.monster_conquest.arena.game.board_locked"
                                    : "gui.monster_conquest.arena.game.board_editable"),
                    LEFT_X + 12, LEFT_HOTKEYS_Y + 48,
                    benchOnlyPhase(snapshot) ? COL_BAD : COL_OK);
        }
        return tooltip;
    }

    private boolean isHoveredScaledText(
            Component text, int x, int y, float scale,
            int mouseX, int mouseY) {
        int width = Math.round(font.width(text) * scale);
        int height = Math.round(font.lineHeight * scale);
        return mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + height;
    }

    private static List<Component> goldTooltip(
            ArenaPlayerSnapshotPayload snapshot) {
        int total = snapshot.baseIncome()
                + snapshot.interestIncome()
                + snapshot.streakIncome();
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(
                        "gui.monster_conquest.arena.game.status_resources_gold",
                        snapshot.gold())
                .withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(
                        "gui.monster_conquest.arena.game.gold_tooltip.breakdown",
                        snapshot.baseIncome(),
                        snapshot.interestIncome(),
                        snapshot.streakIncome(),
                        total)
                .withStyle(ChatFormatting.YELLOW));
        if (snapshot.lastBattleOutcome()
                == ArenaPlayerState.BattleOutcome.WIN.ordinal()) {
            lines.add(Component.translatable(
                            "gui.monster_conquest.arena.game.gold_tooltip.victory_included")
                    .withStyle(ChatFormatting.GOLD));
        }
        lines.add(Component.translatable(
                        "gui.monster_conquest.arena.game.gold_tooltip.win_streak",
                        snapshot.winStreak())
                .withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable(
                        "gui.monster_conquest.arena.game.gold_tooltip.loss_streak",
                        snapshot.lossStreak())
                .withStyle(ChatFormatting.RED));
        return lines;
    }

    private void drawShopOdds(GuiGraphics graphics, int level) {
        int[] weights = ArenaUnitCatalog.costWeights(level);
        int totalWeight = 0;
        for (int weight : weights) totalWeight += weight;

        graphics.fill(LEFT_X + 6, SHOP_ODDS_TITLE_Y - 2,
                LEFT_X + LEFT_W - 6, SHOP_ODDS_ENTRY_Y + 10,
                0x60202A32);
        drawScaledString(graphics,
                Component.translatable(
                        "gui.monster_conquest.arena.game.shop_odds_title"),
                LEFT_X + 10, SHOP_ODDS_TITLE_Y, COL_TEXT, 1.0f);

        List<Component> entries = new java.util.ArrayList<>();
        int textWidth = 0;
        for (int cost = 1; cost <= weights.length; cost++) {
            int percent = totalWeight <= 0 ? 0 : Math.round(
                    weights[cost - 1] * 100.0f / totalWeight);
            Component entry = Component.translatable(
                    "gui.monster_conquest.arena.game.shop_odds_entry",
                    cost, percent);
            entries.add(entry);
            textWidth += font.width(entry);
        }
        int gap = 2;
        int available = LEFT_W - 20 - gap * (entries.size() - 1);
        float scale = Math.min(1.0f,
                textWidth <= 0 ? 1.0f
                        : available / (float) textWidth);

        int x = LEFT_X + 10;
        for (int cost = 1; cost <= entries.size(); cost++) {
            Component entry = entries.get(cost - 1);
            drawScaledString(graphics, entry, x, SHOP_ODDS_ENTRY_Y,
                    shopBorderColor(cost), scale);
            x += Math.round(font.width(entry) * scale) + gap;
        }
    }

    private static List<Component> traitTooltip(
            ArenaTraitAnalyzer.Status status) {
        List<Component> lines = new java.util.ArrayList<>();
        lines.add(Component.translatable(
                        status.trait().translationKey())
                .withStyle(status.tier() > 0
                        ? net.minecraft.ChatFormatting.GREEN
                        : net.minecraft.ChatFormatting.GRAY));
        for (int tier = 1;
             tier <= status.trait().maxTier(); tier++) {
            lines.add(Component.translatable(
                            status.trait().descriptionKey(tier))
                    .withStyle(tier <= status.tier()
                            ? traitTierColor(tier)
                            : net.minecraft.ChatFormatting.DARK_GRAY));
        }
        return lines;
    }

    private static net.minecraft.ChatFormatting traitTierColor(int tier) {
        return switch (tier) {
            case 1 -> net.minecraft.ChatFormatting.GREEN;
            case 2 -> net.minecraft.ChatFormatting.AQUA;
            case 3 -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
            default -> net.minecraft.ChatFormatting.GOLD;
        };
    }

    private static Component traitProgressLabel(
            ArenaTraitAnalyzer.Status status) {
        ArenaTrait trait = status.trait();
        if (status.tier() >= trait.maxTier()) {
            return Component.translatable(
                    "gui.monster_conquest.arena.game.trait.entry_max",
                    Component.translatable(trait.translationKey()),
                    status.count());
        }
        return Component.translatable(
                "gui.monster_conquest.arena.game.trait.entry_progress",
                Component.translatable(trait.translationKey()),
                status.count(),
                trait.threshold(status.tier() + 1));
    }

    private static int traitListTop() {
        return TRAIT_LIST_Y;
    }

    private static int traitListBottom() {
        return TRAIT_LIST_Y
                + TRAIT_VISIBLE_ROWS * TRAIT_ROW_H - 2;
    }

    private static int traitMaxScroll(int traitCount) {
        return Math.max(0,
                traitCount - TRAIT_VISIBLE_ROWS);
    }

    private static int clampTraitScroll(
            int scroll, int traitCount) {
        return Math.max(0, Math.min(
                traitMaxScroll(traitCount), scroll));
    }

    private static int traitScrollbarThumbHeight(int traitCount) {
        int trackHeight =
                traitListBottom() - traitListTop();
        return Math.max(18, Math.round(
                trackHeight * (TRAIT_VISIBLE_ROWS
                        / (float) Math.max(1, traitCount))));
    }

    private static int traitScrollbarThumbY(
            int traitCount, int scroll) {
        int trackHeight =
                traitListBottom() - traitListTop();
        int thumbHeight =
                traitScrollbarThumbHeight(traitCount);
        int maxScroll = traitMaxScroll(traitCount);
        if (maxScroll <= 0) return traitListTop();
        float progress = scroll / (float) maxScroll;
        return traitListTop() + Math.round(
                (trackHeight - thumbHeight) * progress);
    }

    @Nullable
    private List<Component> drawTraitDetails(
            GuiGraphics graphics,
            ArenaPlayerSnapshotPayload snapshot,
            ArenaTrait trait,
            int mouseX, int mouseY) {
        List<ArenaTraitAnalyzer.Status> present =
                presentTraitStatuses(snapshot);
        ArenaTraitAnalyzer.Status status = present.stream()
                .filter(candidate -> candidate.trait() == trait)
                .findFirst().orElse(null);
        if (status == null) {
            selectedTrait = null;
            return null;
        }

        int x = TRAIT_DETAIL_X;
        int y = TRAIT_DETAIL_Y;
        int right = x + TRAIT_DETAIL_W;
        int bottom = y + TRAIT_DETAIL_H;
        // 物品渲染使用独立深度层，详情面板必须位于更高层才能遮住装备仓库。
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, 500.0f);
        graphics.fill(0, 0, PANEL_W, PANEL_H, 0x90000000);
        graphics.fill(x - 2, y - 2, right + 2, bottom + 2,
                COL_BORDER);
        graphics.fill(x, y, right, bottom, 0xF018222B);
        graphics.fill(x, y, right, y + 34, 0xF024323D);

        Component title = Component.translatable(
                "gui.monster_conquest.arena.game.trait.detail.title",
                Component.translatable(trait.translationKey()),
                status.count(),
                status.tier());
        drawScaledStringShadowed(graphics, title,
                x + 16, y + 10,
                status.tier() > 0 ? COL_GOLD : COL_TEXT,
                1.42f);
        graphics.fill(right - 28, y + 7,
                right - 8, y + 27, 0x704A5965);
        graphics.drawCenteredString(font,
                Component.literal("×"),
                right - 18, y + 13, COL_TEXT);

        int contentY = y + 43;
        float detailScale = 1.50f;
        int textWidth = Math.round(
                (TRAIT_DETAIL_W - 34) / detailScale);
        for (int tier = 1; tier <= trait.maxTier(); tier++) {
            Component description = Component.translatable(
                    trait.descriptionKey(tier));
            List<net.minecraft.util.FormattedCharSequence> lines =
                    font.split(description, textWidth);
            int color = tier <= status.tier()
                    ? traitTierArgb(tier)
                    : 0xFFB8C2CB;
            for (var line : lines) {
                drawScaledFormattedString(
                        graphics, font, line, x + 16,
                        contentY, color, detailScale);
                contentY += 15;
            }
            contentY += 2;
        }

        if (trait == ArenaTrait.CARAVAN) {
            drawScaledStringShadowed(graphics,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.trait.detail.caravan_stacks",
                            snapshot.caravanStacks()),
                    x + 16, contentY + 4, COL_GOLD, 1.35f);
            contentY += 24;
        }

        List<Integer> units = unitIndicesForTrait(trait);
        drawScaledStringShadowed(graphics,
                Component.translatable(
                        "gui.monster_conquest.arena.game.trait.detail.units",
                        units.size()),
                x + 16, contentY + 4, COL_TRAIT, 1.35f);
        drawScaledStringShadowed(graphics,
                Component.translatable(
                        "gui.monster_conquest.arena.game.trait.detail.legend"),
                x + 16, contentY + 24, 0xFFD7E0E7, 1.30f);
        int gridY = contentY + 48;
        java.util.Set<Integer> owned = new java.util.HashSet<>();
        java.util.Set<Integer> deployed = new java.util.HashSet<>();
        java.util.Map<Integer, PieceView> bestPieces =
                new java.util.HashMap<>();
        for (PieceView piece : snapshot.pieces()) {
            owned.add(piece.unitIndex());
            if (piece.boardSlot() >= 0) {
                deployed.add(piece.unitIndex());
            }
            PieceView previous = bestPieces.get(piece.unitIndex());
            if (previous == null
                    || (piece.boardSlot() >= 0
                    && previous.boardSlot() < 0)
                    || ((piece.boardSlot() >= 0)
                    == (previous.boardSlot() >= 0)
                    && piece.star() > previous.star())) {
                bestPieces.put(piece.unitIndex(), piece);
            }
        }

        List<Component> tooltip = null;
        int columns = 10;
        int cellW = 58;
        int cellH = 45;
        int icon = 32;
        int gridX = x + (TRAIT_DETAIL_W - columns * cellW) / 2;
        for (int index = 0; index < units.size(); index++) {
            int unitIndex = units.get(index);
            int column = index % columns;
            int row = index / columns;
            int cellX = gridX + column * cellW;
            int cellY = gridY + row * cellH;
            int iconX = cellX + (cellW - icon) / 2;
            int iconY = cellY + 3;
            boolean deployedUnit = deployed.contains(unitIndex);
            boolean ownedUnit = owned.contains(unitIndex);
            boolean hovered = mouseX >= cellX
                    && mouseX < cellX + cellW - 6
                    && mouseY >= cellY
                    && mouseY < cellY + cellH - 3;
            int border = deployedUnit
                    ? COL_OK
                    : ownedUnit ? 0xFF667D8C : 0xFF303941;
            graphics.fill(cellX - 1, cellY - 1,
                    cellX + cellW - 7, cellY + cellH - 4,
                    border);
            graphics.fill(cellX, cellY,
                    cellX + cellW - 8, cellY + cellH - 5,
                    hovered ? 0xE03C4A54 : 0xC0202A32);
            float alpha = deployedUnit
                    ? 1.0f : ownedUnit ? 0.38f : 0.16f;
            graphics.setColor(1.0f, 1.0f, 1.0f, alpha);
            ArenaUnit unit = ArenaUnitCatalog.get(unitIndex);
            graphics.blit(unit.icon(), iconX, iconY,
                    icon, icon,
                    0.0f, 0.0f, 32, 32, 32, 32);
            graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
            if (deployedUnit) {
                graphics.fill(iconX, iconY, iconX + 3,
                        iconY + icon + 1, COL_OK);
            }
            if (hovered) {
                PieceView best = bestPieces.get(unitIndex);
                tooltip = unitTooltip(
                        snapshot, unitIndex,
                        best == null ? 1 : best.star(),
                        best == null
                                ? List.of() : best.equipment(),
                        best == null ? null : best.liveStats());
            }
        }
        graphics.pose().popPose();
        return tooltip;
    }

    private static int traitTierArgb(int tier) {
        return switch (tier) {
            case 1 -> COL_OK;
            case 2 -> COL_TRAIT;
            case 3 -> COL_ARCANE;
            default -> COL_GOLD;
        };
    }

    private static List<ArenaTraitAnalyzer.Status> presentTraitStatuses(
            ArenaPlayerSnapshotPayload snapshot) {
        return ArenaTraitAnalyzer.analyze(
                        snapshot.pieces().stream()
                                .filter(piece -> piece.boardSlot() >= 0)
                                .map(PieceView::unitIndex)
                                .toList())
                .presentStatuses();
    }

    private static List<Integer> unitIndicesForTrait(
            ArenaTrait trait) {
        List<Integer> result = new java.util.ArrayList<>();
        for (int index = 0;
             index < ArenaUnitCatalog.ALL.size(); index++) {
            if (ArenaTraitCatalog.traitsFor(index).contains(trait)) {
                result.add(index);
            }
        }
        result.sort(Comparator.comparingInt(
                index -> ArenaUnitCatalog.get(index).cost()));
        return result;
    }

    private void drawScaledString(GuiGraphics graphics, Component text,
                                  int x, int y, int color, float scale) {
        drawScaledString(graphics, text, x, y, color, scale, false);
    }

    private void renderClampedTooltip(
            GuiGraphics graphics, List<Component> lines,
            int mouseX, int mouseY, float z) {
        List<ClientTooltipComponent> components =
                ClientHooks.gatherTooltipComponents(
                        ItemStack.EMPTY, lines, mouseX,
                        width, height, font);
        if (components.isEmpty()) return;

        int contentWidth = 0;
        int contentHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            contentWidth = Math.max(
                    contentWidth, component.getWidth(font));
            contentHeight += component.getHeight();
        }
        contentWidth = Math.max(1, contentWidth);
        contentHeight = Math.max(1, contentHeight);

        float scale = Math.min(1.0f, Math.min(
                (width - 8.0f) / contentWidth,
                (height - 8.0f) / contentHeight));
        int scaledWidth = Math.max(
                1, Math.round(contentWidth * scale));
        int scaledHeight = Math.max(
                1, Math.round(contentHeight * scale));
        int x = mouseX + 12;
        if (x + scaledWidth > width - 4) {
            x = mouseX - scaledWidth - 12;
        }
        int y = mouseY + 12;
        if (y + scaledHeight > height - 4) {
            y = mouseY - scaledHeight - 12;
        }
        x = Math.max(4, Math.min(
                width - scaledWidth - 4, x));
        y = Math.max(4, Math.min(
                height - scaledHeight - 4, y));

        graphics.pose().pushPose();
        graphics.pose().translate(x, y, z);
        graphics.pose().scale(scale, scale, 1.0f);
        TooltipRenderUtil.renderTooltipBackground(
                graphics, 0, 0, contentWidth, contentHeight,
                400,
                0xF0100010, 0xF0100010,
                0x505000FF, 0x5028007F);
        graphics.pose().translate(0.0f, 0.0f, 400.0f);

        int textY = 0;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderText(
                    font, 0, textY, graphics.pose().last().pose(),
                    graphics.bufferSource());
            textY += component.getHeight() + (i == 0 ? 2 : 0);
        }
        int imageY = 0;
        for (int i = 0; i < components.size(); i++) {
            ClientTooltipComponent component = components.get(i);
            component.renderImage(font, 0, imageY, graphics);
            imageY += component.getHeight() + (i == 0 ? 2 : 0);
        }
        graphics.pose().popPose();
        graphics.flush();
    }

    private void drawScaledStringShadowed(
            GuiGraphics graphics, Component text,
            int x, int y, int color, float scale) {
        drawScaledString(graphics, text, x, y, color, scale, true);
    }

    private void drawScaledString(GuiGraphics graphics, Component text,
                                  int x, int y, int color, float scale,
                                  boolean shadow) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, shadow);
        graphics.pose().popPose();
    }

    private static void drawScaledFormattedString(
            GuiGraphics graphics, net.minecraft.client.gui.Font font,
            net.minecraft.util.FormattedCharSequence text,
            int x, int y, int color, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.drawString(font, text, 0, 0, color, true);
        graphics.pose().popPose();
    }

    @Nullable
    private List<Component> drawBoardArea(
            GuiGraphics graphics, ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        boolean benchOnly = !snapshot.spectator()
                && benchOnlyPhase(snapshot);
        List<Component> tooltip = null;
        graphics.fill(BOARD_X - 4, BOARD_Y - 4,
                BOARD_X + BOARD_W + 4, BOARD_Y + BOARD_H + 4, COL_BORDER);
        for (int row = 0; row < ArenaPlayerState.BOARD_ROWS; row++) {
            for (int column = 0;
                 column < ArenaPlayerState.BOARD_COLUMNS; column++) {
                int slot = row * ArenaPlayerState.BOARD_COLUMNS + column;
                int x = BOARD_X + column * CELL_W;
                int y = BOARD_Y + row * CELL_H;
                boolean hovered = mouseX >= x && mouseX < x + CELL_W
                        && mouseY >= y && mouseY < y + CELL_H;
                PieceView piece = pieceAtBoard(snapshot, slot);
                int cellBackground = piece == null
                        ? ((row + column) % 2 == 0
                        ? 0xB0222C35 : 0xB0161E25)
                        : shopBackgroundColor(
                        ArenaUnitCatalog.get(piece.unitIndex()).cost(),
                        hovered);
                graphics.fill(x + 1, y + 1,
                        x + CELL_W - 1, y + CELL_H - 1,
                        cellBackground);
                if (piece != null) {
                    drawCostBorder(graphics, x, y, CELL_W, CELL_H,
                            ArenaUnitCatalog.get(piece.unitIndex()).cost());
                }
                if (hovered && !benchOnly
                        && !snapshot.spectator() && piece == null) {
                    graphics.fill(x + 1, y + 1,
                            x + CELL_W - 1, y + CELL_H - 1,
                            0x504A9BD8);
                }
                if (piece != null) {
                    if (hovered) tooltip = pieceTooltip(snapshot, piece);
                    drawPiece(graphics, piece, x + 5, y + 4, 46, 40,
                            benchOnly);
                }
            }
        }
        if (benchOnly) {
            graphics.fill(BOARD_X, BOARD_Y,
                    BOARD_X + BOARD_W, BOARD_Y + BOARD_H,
                    0x44000000);
        }
        return tooltip;
    }

    @Nullable
    private List<Component> drawBenchArea(
            GuiGraphics graphics, ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        List<Component> tooltip = null;
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.bench"),
                BENCH_X, BENCH_Y - 14, COL_TEXT);
        Component deployed = Component.translatable(
                "gui.monster_conquest.arena.game.deployed",
                snapshot.boardCount(), snapshot.level());
        graphics.drawString(font, deployed,
                BENCH_X + BENCH_W - font.width(deployed),
                BENCH_Y - 14, COL_DIM);
        for (int slot = 0; slot < ArenaPlayerState.BENCH_SIZE; slot++) {
            int x = benchSlotX(slot);
            int y = BENCH_Y;
            PieceView piece = pieceAtBench(snapshot, slot);
            boolean hovered = mouseX >= x && mouseX < x + BENCH_SLOT_W
                    && mouseY >= y && mouseY < y + BENCH_CELL_H;
            graphics.fill(x - 1, y - 1,
                    x + BENCH_SLOT_W + 1, y + BENCH_CELL_H + 1,
                    piece == null ? COL_BORDER
                            : shopBorderColor(ArenaUnitCatalog.get(
                            piece.unitIndex()).cost()));
            graphics.fill(x, y, x + BENCH_SLOT_W,
                    y + BENCH_CELL_H,
                    piece == null
                            ? (hovered ? 0xD0344553 : 0xB019222A)
                            : shopBackgroundColor(
                            ArenaUnitCatalog.get(piece.unitIndex()).cost(),
                            hovered));
            if (piece != null) {
                if (hovered) tooltip = pieceTooltip(snapshot, piece);
                drawPiece(graphics, piece, x + 4, y + 4, 40, 40, false);
            }
        }
        return tooltip;
    }

    private static void drawCostBorder(GuiGraphics graphics,
                                       int x, int y, int width, int height,
                                       int cost) {
        int color = shopBorderColor(cost);
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y, x + 1, y + height, color);
        graphics.fill(x + width - 1, y, x + width, y + height, color);
    }

    @Nullable
    private List<Component> drawEquipmentWarehouse(
            GuiGraphics graphics, ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.equipment_bag"),
                EQUIPMENT_X, EQUIPMENT_LABEL_Y, COL_TEXT);
        List<Component> equipmentTooltip = null;
        int capacity = EQUIPMENT_COLUMNS * EQUIPMENT_ROWS;
        for (int i = 0; i < capacity; i++) {
            int x = equipmentSlotX(i);
            int y = equipmentSlotY(i);
            boolean hovered = mouseX >= x
                    && mouseX < x + EQUIPMENT_SLOT_W
                    && mouseY >= y
                    && mouseY < y + EQUIPMENT_SLOT_H;
            boolean selected = selectedEquipmentIndex != null
                    && selectedEquipmentIndex == i;
            graphics.fill(x - 1, y - 1,
                    x + EQUIPMENT_SLOT_W + 1,
                    y + EQUIPMENT_SLOT_H + 1,
                    selected ? COL_ACCENT : COL_BORDER);
            graphics.fill(x, y,
                    x + EQUIPMENT_SLOT_W,
                    y + EQUIPMENT_SLOT_H,
                    hovered ? 0xD0344553 : 0xB019222A);
            if (i >= snapshot.equipmentInventory().size()) continue;
            int equipmentId =
                    snapshot.equipmentInventory().get(i);
            if (equipmentId < 0) continue;
            ArenaEquipment equipment = ArenaEquipment.byId(
                    equipmentId);
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0f);
            graphics.pose().scale(3.0f, 3.0f, 1.0f);
            graphics.renderItem(equipment.iconStack(), 0, 0);
            graphics.pose().popPose();
            int stackCount = i < snapshot.equipmentCounts().size()
                    ? snapshot.equipmentCounts().get(i) : 1;
            if (stackCount > 1) {
                String countLabel = Integer.toString(stackCount);
                graphics.drawString(font, countLabel,
                        x + EQUIPMENT_SLOT_W - font.width(countLabel) - 3,
                        y + EQUIPMENT_SLOT_H - 10,
                        0xFFFFFFFF, true);
            }
            if (hovered) {
                List<Component> lines = new java.util.ArrayList<>();
                lines.add(Component.translatable(equipment.translationKey()));
                lines.add(Component.translatable(equipment.categoryKey())
                        .withStyle(net.minecraft.ChatFormatting.AQUA));
                lines.add(Component.translatable(equipment.descriptionKey())
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
                if (stackCount > 1) {
                    lines.add(Component.translatable(
                            "gui.monster_conquest.arena.game.equipment_stack",
                            stackCount)
                            .withStyle(net.minecraft.ChatFormatting.WHITE));
                }
                if (equipment.component()) {
                    lines.add(Component.translatable(
                            "gui.monster_conquest.arena.game.equipment_recipes")
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                    for (ArenaEquipment.Combination combination
                            : equipment.combinations()) {
                        boolean craftable =
                                hasWarehouseComponent(
                                        snapshot, i,
                                        combination.ingredient());
                        lines.add(Component.translatable(
                                "gui.monster_conquest.arena.game.equipment_recipe",
                                Component.translatable(
                                        equipment.translationKey()),
                                Component.translatable(
                                        combination.ingredient()
                                                .translationKey()),
                                Component.translatable(
                                        combination.result().translationKey()))
                                .withStyle(craftable
                                        ? net.minecraft.ChatFormatting.GREEN
                                        : net.minecraft.ChatFormatting.GRAY));
                    }
                }
                equipmentTooltip = lines;
            }
        }
        return equipmentTooltip;
    }

    private static boolean hasWarehouseComponent(
            ArenaPlayerSnapshotPayload snapshot,
            int hoveredIndex, ArenaEquipment ingredient) {
        for (int index = 0;
             index < snapshot.equipmentInventory().size(); index++) {
            if (index == hoveredIndex) continue;
            int equipmentId =
                    snapshot.equipmentInventory().get(index);
            if (equipmentId >= 0
                    && ArenaEquipment.byId(equipmentId)
                    == ingredient) {
                return true;
            }
        }
        return false;
    }

    private List<Component> pieceTooltip(
            ArenaPlayerSnapshotPayload snapshot, PieceView piece) {
        return unitTooltip(snapshot, piece.unitIndex(), piece.star(),
                piece.equipment(), piece.liveStats());
    }

    private List<Component> unitTooltip(
            ArenaPlayerSnapshotPayload snapshot, int unitIndex, int star,
            List<Integer> equipmentIds,
            @Nullable LiveStats liveStats) {
        ArenaUnit unit = ArenaUnitCatalog.get(unitIndex);
        List<Integer> roster = snapshot.pieces().stream()
                .filter(candidate -> candidate.boardSlot() >= 0)
                .map(PieceView::unitIndex)
                .toList();
        ArenaStatCalculator.Stats stats = ArenaStatCalculator.calculate(
                unitIndex, star, equipmentIds, roster);
        if (liveStats != null) {
            stats = applyLiveStats(stats, liveStats);
        }
        List<String> equipmentNames = new java.util.ArrayList<>();
        if (unit.builtInShield()) {
            equipmentNames.add(Component.translatable(
                    "gui.monster_conquest.arena.game.unit_tooltip.builtin_shield")
                    .getString());
        }
        equipmentIds.stream()
                .map(ArenaEquipment::byId)
                .map(item -> Component.translatable(
                        item.translationKey()).getString())
                .forEach(equipmentNames::add);
        String equipment = equipmentNames.isEmpty()
                ? Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.none")
                        .getString()
                : String.join(", ", equipmentNames);

        List<Component> tooltip = new java.util.ArrayList<>();
        tooltip.add(Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.title",
                        Component.translatable(unit.translationKey()),
                        star, unit.cost())
                .withStyle(net.minecraft.ChatFormatting.GOLD));
        if (unit.special()) {
            tooltip.add(Component.translatable(
                            "gui.monster_conquest.arena.game.unit_tooltip.special")
                    .withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
        }
        if (liveStats != null) {
            tooltip.add(Component.translatable(
                            "gui.monster_conquest.arena.game.unit_tooltip.live")
                    .withStyle(net.minecraft.ChatFormatting.GREEN));
        }
        tooltip.add(Component.empty()
                .append(coloredStat(
                        "health", liveStats == null
                                ? Long.toString(Math.round(
                                stats.maxHealth()))
                                : Math.round(liveStats.currentHealth())
                                + "/" + Math.round(
                                liveStats.maxHealth()),
                        net.minecraft.ChatFormatting.GREEN))
                .append(statSeparator())
                .append(coloredStat(
                        "attack", formatStat(stats.attackDamage()),
                        net.minecraft.ChatFormatting.RED))
                .append(statSeparator())
                .append(coloredStat(
                        "armor", formatStat(stats.armor()),
                        net.minecraft.ChatFormatting.GRAY))
                .append(statSeparator())
                .append(coloredStat(
                        "magic_resistance",
                        formatStat(stats.magicResistance()),
                        net.minecraft.ChatFormatting.BLUE))
                .append(statSeparator())
                .append(coloredStat(
                        "attack_speed",
                        formatStat(stats.attackSpeed()),
                        net.minecraft.ChatFormatting.GOLD)));
        tooltip.add(coloredStat(
                "movement",
                String.format(Locale.ROOT, "%.2f",
                        stats.movementSpeed()),
                net.minecraft.ChatFormatting.GREEN));
        tooltip.add(Component.translatable(
                "gui.monster_conquest.arena.game.unit_tooltip.traits",
                traitNames(ArenaTraitCatalog.traitsFor(unitIndex)
                        .stream().map(Enum::ordinal).toList()))
                .withStyle(net.minecraft.ChatFormatting.BLUE));
        ArenaTraitAnalyzer.State traitState =
                ArenaTraitAnalyzer.analyze(roster);
        boolean traitEffectsAdded = false;
        for (ArenaTrait trait :
                ArenaTraitCatalog.traitsFor(unitIndex)) {
            int tier = traitState.tier(trait);
            for (int activeTier = 1;
                 activeTier <= tier; activeTier++) {
                if (!traitEffectsAdded) {
                    tooltip.add(Component.translatable(
                                    "gui.monster_conquest.arena.game.unit_tooltip.trait_effects")
                            .withStyle(
                                    net.minecraft.ChatFormatting.GOLD));
                    traitEffectsAdded = true;
                }
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable(
                                trait.descriptionKey(activeTier)))
                        .withStyle(switch (activeTier) {
                            case 1 -> net.minecraft.ChatFormatting.GREEN;
                            case 2 -> net.minecraft.ChatFormatting.AQUA;
                            case 3 -> net.minecraft.ChatFormatting.LIGHT_PURPLE;
                            default -> net.minecraft.ChatFormatting.GOLD;
                        }));
            }
        }
        tooltip.add(Component.empty()
                .append(coloredStat(
                        "magic_power", formatStat(stats.magicPower()),
                        net.minecraft.ChatFormatting.LIGHT_PURPLE))
                .append(statSeparator())
                .append(coloredPercentStat(
                        "dodge", stats.dodgeChance(),
                        net.minecraft.ChatFormatting.AQUA))
                .append(statSeparator())
                .append(coloredPercentStat(
                        "healing", stats.healingPower(),
                        net.minecraft.ChatFormatting.GREEN))
                .append(statSeparator())
                .append(coloredPercentStat(
                        "lifesteal", stats.lifesteal(),
                        net.minecraft.ChatFormatting.RED)));
        tooltip.add(Component.empty()
                .append(coloredStat(
                        "initial_skill_charge",
                        formatStat(stats.initialSkillCharge()),
                        net.minecraft.ChatFormatting.GOLD))
                .append(statSeparator())
                .append(coloredPercentStat(
                        "skill_charge_gain",
                        stats.skillChargeGainPercent(),
                        net.minecraft.ChatFormatting.BLUE)));
        String skillCrit = stats.skillCritEnabled()
                ? Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.critical.skill")
                        .getString()
                : "";
        tooltip.add(Component.empty()
                .append(coloredPercentStat(
                        "crit_chance", stats.critChance(),
                        net.minecraft.ChatFormatting.RED))
                .append(statSeparator())
                .append(coloredPercentStat(
                        "crit_damage", stats.critDamage(),
                        net.minecraft.ChatFormatting.GOLD))
                .append(Component.literal(skillCrit)
                        .withStyle(
                                net.minecraft.ChatFormatting.LIGHT_PURPLE)));
        if (liveStats != null) {
            tooltip.add(Component.translatable(
                            "gui.monster_conquest.arena.game.unit_tooltip.live_shield",
                            formatStat(liveStats.shield()))
                    .withStyle(net.minecraft.ChatFormatting.AQUA));
            tooltip.add(Component.translatable(
                            "gui.monster_conquest.arena.game.unit_tooltip.live_skill_bar",
                            liveStats.skillCharge(),
                            liveStats.skillRequired())
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        List<Component> skillLines =
                ArenaSkillTooltip.describe(unit, star, stats);
        for (int index = 0; index < skillLines.size(); index++) {
            tooltip.add(Component.empty()
                    .append(skillLines.get(index))
                    .withStyle(index == 0
                            ? net.minecraft.ChatFormatting.AQUA
                            : index == 1
                            ? net.minecraft.ChatFormatting.GOLD
                            : net.minecraft.ChatFormatting.GRAY));
        }
        tooltip.add(
                Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.equipment",
                        equipment)
                        .withStyle(
                                net.minecraft.ChatFormatting.YELLOW));
        if (unit.builtInShield()) {
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable(
                            "gui.monster_conquest.arena.game.unit_tooltip.builtin_shield.effect"))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        for (int equipmentId : equipmentIds) {
            ArenaEquipment item = ArenaEquipment.byId(equipmentId);
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable(item.translationKey()))
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            tooltip.add(Component.literal("    ")
                    .append(Component.translatable(item.descriptionKey()))
                    .withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        return tooltip;
    }

    private static String formatStat(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String formatPercent(double value) {
        return Long.toString(Math.round(value * 100.0));
    }

    private static ArenaStatCalculator.Stats applyLiveStats(
            ArenaStatCalculator.Stats base, LiveStats live) {
        return new ArenaStatCalculator.Stats(
                live.maxHealth(),
                live.attackDamage(),
                live.armor(),
                live.magicResistance(),
                live.attackSpeed(),
                live.movementSpeed(),
                live.magicPower(),
                live.dodgeChance(),
                live.healingPower(),
                live.lifesteal(),
                base.initialSkillCharge(),
                live.skillChargeGainPercent(),
                live.critChance(),
                live.critDamage(),
                base.skillCritEnabled(),
                base.attackRangeBonus(),
                base.shieldPower(),
                base.incomingDamageReduction(),
                base.skillDamageBonus(),
                base.startingShieldRatio(),
                base.traitAttackPercent(),
                base.traitAttackSpeedPercent(),
                base.traitMovementPercent());
    }

    private static Component coloredStat(
            String stat, String value,
            net.minecraft.ChatFormatting color) {
        return Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.stat."
                                + stat)
                .append(Component.literal(" " + value))
                .withStyle(color);
    }

    private static Component coloredPercentStat(
            String stat, double value,
            net.minecraft.ChatFormatting color) {
        return coloredStat(
                stat, formatPercent(value) + "%", color);
    }

    private static Component statSeparator() {
        return Component.literal(" · ")
                .withStyle(net.minecraft.ChatFormatting.DARK_GRAY);
    }

    private static String traitNames(List<Integer> traits) {
        ArenaTrait[] values = ArenaTrait.values();
        return traits.stream()
                .filter(id -> id >= 0 && id < values.length)
                .map(id -> Component.translatable(
                        values[id].translationKey()).getString())
                .collect(java.util.stream.Collectors.joining(" · "));
    }

    private void drawPiece(GuiGraphics graphics, PieceView piece,
                           int x, int y, int width, int height,
                           boolean dimmed) {
        ArenaUnit unit = ArenaUnitCatalog.get(piece.unitIndex());
        if (selectedPiece != null && selectedPiece.equals(piece.id())) {
            graphics.fill(x - 1, y - 1,
                    x + width + 1, y + height + 1, COL_ACCENT);
        }
        graphics.blit(unit.icon(), x, y,
                width, height, 0.0f, 0.0f,
                32, 32, 32, 32);
        if (!confirmingSurrender) {
            drawPieceEquipment(graphics, piece, x + 2, y + 2);
            graphics.drawString(font, Component.literal(piece.star() + "★"),
                    x + 2, y + height - 9,
                    ArenaStarColors.color(piece.star(), dimmed));
        }
    }

    private static void drawPieceEquipment(GuiGraphics graphics, PieceView piece,
                                           int x, int y) {
        if (piece.equipment().isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        for (int i = 0; i < piece.equipment().size() && i < 3; i++) {
            ArenaEquipment equipment = ArenaEquipment.byId(
                    piece.equipment().get(i));
            ItemStack stack = equipment.iconStack();
            var sprite = minecraft.getItemRenderer().getModel(
                    stack, minecraft.level, minecraft.player, 0)
                    .getParticleIcon();
            graphics.blit(x + i * 9, y, 0, 8, 8, sprite);
        }
    }

    private static int shopBorderColor(int cost) {
        return switch (cost) {
            case 1 -> 0xFFA0A6AA;
            case 2 -> 0xFF8DD99B;
            case 3 -> 0xFF82C4F0;
            case 4 -> 0xFFC18AE8;
            case 5 -> 0xFFFFB65C;
            default -> COL_BORDER;
        };
    }

    private static int shopBackgroundColor(int cost, boolean hovered) {
        if (hovered) {
            return switch (cost) {
                case 1 -> 0xE03A4146;
                case 2 -> 0xE0334A37;
                case 3 -> 0xE0324659;
                case 4 -> 0xE047345B;
                case 5 -> 0xE05A3D20;
                default -> 0xD0354655;
            };
        }
        return switch (cost) {
            case 1 -> 0xD02B3034;
            case 2 -> 0xD0253829;
            case 3 -> 0xD0243342;
            case 4 -> 0xD0352843;
            case 5 -> 0xD0422D18;
            default -> COL_PANEL_SOFT;
        };
    }

    private void drawPlayerColumn(GuiGraphics graphics,
                                  ArenaPlayerSnapshotPayload snapshot,
                                  int mouseX, int mouseY) {
        graphics.fill(RIGHT_X, PLAYER_Y, RIGHT_X + RIGHT_W,
                PANEL_H - 18, COL_PANEL);
        drawRightPanelTabs(graphics, snapshot, mouseX, mouseY);
        RightPanelMode mode = snapshot.spectator()
                ? RightPanelMode.PARTICIPANTS : rightPanelMode;
        if (mode == RightPanelMode.UNIT_STATS) {
            drawUnitStatsColumn(
                    graphics, snapshot, mouseX, mouseY);
        } else {
            drawParticipantsColumn(
                    graphics, snapshot, mouseX, mouseY);
        }
    }

    private void drawRightPanelTabs(
            GuiGraphics graphics,
            ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        RightPanelMode mode = snapshot.spectator()
                ? RightPanelMode.PARTICIPANTS : rightPanelMode;
        drawRightTab(graphics,
                Component.translatable(
                        "gui.monster_conquest.arena.game.players"),
                RIGHT_TAB_X, mouseX, mouseY,
                mode == RightPanelMode.PARTICIPANTS,
                false);
        drawRightTab(graphics,
                Component.translatable(
                        "gui.monster_conquest.arena.game.unit_stats.tab"),
                RIGHT_TAB_X + RIGHT_TAB_W + 5,
                mouseX, mouseY,
                mode == RightPanelMode.UNIT_STATS,
                snapshot.spectator());
    }

    private void drawRightTab(
            GuiGraphics graphics, Component label,
            int x, int mouseX, int mouseY,
            boolean selected, boolean disabled) {
        boolean hovered = !disabled
                && mouseX >= x && mouseX < x + RIGHT_TAB_W
                && mouseY >= RIGHT_TAB_Y
                && mouseY < RIGHT_TAB_Y + RIGHT_TAB_H;
        int background = selected
                ? 0xF03A566E
                : hovered ? 0xE02D414F : 0xB522303A;
        graphics.fill(x, RIGHT_TAB_Y,
                x + RIGHT_TAB_W,
                RIGHT_TAB_Y + RIGHT_TAB_H, background);
        graphics.drawCenteredString(font, label,
                x + RIGHT_TAB_W / 2,
                RIGHT_TAB_Y + 6,
                disabled ? COL_DIM
                        : selected ? COL_TEXT : COL_DIM);
    }

    private void drawParticipantsColumn(
            GuiGraphics graphics,
            ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        UUID selfId = Minecraft.getInstance().player == null
                ? new UUID(0L, 0L)
                : Minecraft.getInstance().player.getUUID();
        List<PlayerStatus> players = snapshot.players();
        for (int i = 0; i < players.size()
                && i < ArenaLobbySnapshotPayload.MAX_SEATS; i++) {
            PlayerStatus status = players.get(i);
            int rowY = RIGHT_ROWS_Y + i * PLAYER_ROW_H;
            boolean hovered = mouseX >= RIGHT_X + 6
                    && mouseX < RIGHT_X + RIGHT_W - 6
                    && mouseY >= rowY && mouseY < rowY + PLAYER_ROW_H - 4;
            int background = battleRowColor(snapshot, status);
            if (background == 0) {
                background = status.id().equals(selfId)
                        ? 0xA0435C78
                        : (hovered ? 0xA0344553 : 0x50151D24);
            }
            graphics.fill(RIGHT_X + 6, rowY,
                    RIGHT_X + RIGHT_W - 6, rowY + PLAYER_ROW_H - 4,
                    background);
            drawPlayerFace(graphics, status.id(),
                    RIGHT_X + 11, rowY + 6, 28);
            int textColor = !status.alive()
                    ? COL_DIM : (status.health() <= 25 ? COL_BAD : COL_TEXT);
            graphics.drawString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.player_board",
                            status.boardIndex() + 1, status.name()),
                    RIGHT_X + 48, rowY + 6, textColor);
            graphics.drawString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.player_health_level",
                            status.health(), status.level()),
                    RIGHT_X + 48, rowY + 20,
                    status.alive() ? COL_DIM : COL_BAD);
            String battleLabel = battleLabelKey(snapshot, status);
            if (battleLabel != null) {
                graphics.drawString(font,
                        Component.translatable(battleLabel),
                        RIGHT_X + 150, rowY + 6,
                        battleLabelColor(status.battleResult()));
            } else if (!status.alive()) {
                graphics.drawString(font,
                        Component.translatable(
                                "gui.monster_conquest.arena.game.eliminated"),
                        RIGHT_X + 150, rowY + 6, COL_BAD);
            }
        }
        graphics.drawString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.click_to_spectate"),
                RIGHT_X + 10, PANEL_H - 38, COL_DIM);
    }

    private void drawUnitStatsColumn(
            GuiGraphics graphics,
            ArenaPlayerSnapshotPayload snapshot,
            int mouseX, int mouseY) {
        drawUnitStatModeButtons(
                graphics, mouseX, mouseY);
        List<PieceView> pieces = snapshot.pieces().stream()
                .filter(piece -> ArenaGameClientState
                        .combatStats(piece.id()) != null)
                .sorted(Comparator
                        .comparingDouble((PieceView piece) ->
                                statTotal(piece))
                        .reversed()
                        .thenComparingInt(piece ->
                                ArenaUnitCatalog.get(
                                        piece.unitIndex()).cost())
                        .thenComparing(piece ->
                                ArenaUnitCatalog.get(
                                        piece.unitIndex()).id()))
                .toList();
        double maximum = pieces.stream()
                .mapToDouble(this::statTotal)
                .max().orElse(0.0);
        for (int index = 0; index < pieces.size(); index++) {
            PieceView piece = pieces.get(index);
            int rowY = RIGHT_ROWS_Y
                    + index * RIGHT_UNIT_ROW_H;
            if (rowY + RIGHT_UNIT_ROW_H > PANEL_H - 25) break;
            drawUnitStatRow(graphics, piece,
                    RIGHT_X + 6, rowY,
                    RIGHT_W - 12, maximum);
        }
        drawUnitStatLegend(graphics);
    }

    private void drawUnitStatModeButtons(
            GuiGraphics graphics, int mouseX, int mouseY) {
        int gap = 3;
        int width = (RIGHT_W - 20 - gap * 2) / 3;
        UnitStatMode[] modes = UnitStatMode.values();
        for (int index = 0; index < modes.length; index++) {
            UnitStatMode mode = modes[index];
            int x = RIGHT_X + 10
                    + index * (width + gap);
            boolean hovered = mouseX >= x
                    && mouseX < x + width
                    && mouseY >= RIGHT_STAT_Y
                    && mouseY < RIGHT_STAT_Y + RIGHT_STAT_H;
            int background = unitStatMode == mode
                    ? 0xE03A566E
                    : hovered ? 0xD02C3D49 : 0xA61B262E;
            graphics.fill(x, RIGHT_STAT_Y,
                    x + width, RIGHT_STAT_Y + RIGHT_STAT_H,
                    background);
            graphics.drawCenteredString(font,
                    Component.translatable(
                            switch (mode) {
                                case DAMAGE_DEALT ->
                                        "gui.monster_conquest.arena.game.unit_stats.damage_dealt";
                                case HEALING ->
                                        "gui.monster_conquest.arena.game.unit_stats.healing";
                                case DAMAGE_TAKEN ->
                                        "gui.monster_conquest.arena.game.unit_stats.damage_taken";
                            }),
                    x + width / 2, RIGHT_STAT_Y + 5,
                    unitStatMode == mode
                            ? COL_TEXT : COL_DIM);
        }
    }

    private void drawUnitStatRow(
            GuiGraphics graphics, PieceView piece,
            int x, int y, int width, double maximum) {
        ArenaUnit unit = ArenaUnitCatalog.get(piece.unitIndex());
        ArenaCombatStatsPayload.UnitStats stats =
                ArenaGameClientState.combatStats(piece.id());
        double total = statTotal(piece);
        int background = (y / RIGHT_UNIT_ROW_H & 1) == 0
                ? 0x8019232B : 0x80151D24;
        graphics.fill(x, y, x + width,
                y + RIGHT_UNIT_ROW_H - 2, background);
        graphics.blit(unit.icon(),
                x + 5, y + 6, 24, 24,
                0.0f, 0.0f, 32, 32, 32, 32);
        String stars = "★".repeat(piece.star());
        graphics.drawString(font, stars,
                x + 34, y + 5,
                ArenaStarColors.color(piece.star()));
        int nameX = x + 37 + font.width(stars);
        String unitName = Component.translatable(
                unit.translationKey()).getString();
        graphics.drawString(font,
                font.plainSubstrByWidth(
                        unitName, width - (nameX - x) - 4),
                nameX, y + 5, COL_TEXT);
        graphics.drawString(font,
                Component.literal(formatStatValue(total)),
                x + 34, y + 16, COL_TRUE);

        int barX = x + 34;
        int barY = y + 29;
        int fullWidth = width - 40;
        graphics.fill(barX, barY,
                barX + fullWidth, barY + 5,
                0xFF0A0E12);
        if (maximum <= 0.0 || total <= 0.0) return;
        int usedWidth = Math.max(1, Math.round(
                fullWidth * (float) (total / maximum)));
        drawStatBar(graphics, stats,
                barX, barY, usedWidth, total);
    }

    private void drawStatBar(
            GuiGraphics graphics,
            @Nullable ArenaCombatStatsPayload.UnitStats stats,
            int x, int y, int width, double total) {
        if (stats == null || total <= 0.0) return;
        double physical;
        double magic;
        double white;
        if (unitStatMode == UnitStatMode.HEALING) {
            int cursor = x;
            cursor += drawSegment(graphics, cursor, y, width,
                    stats.healing() / total, COL_HEALING);
            drawSegment(graphics, cursor, y, width,
                    stats.shieldHealing() / total, COL_TRUE);
            return;
        }
        if (unitStatMode == UnitStatMode.DAMAGE_TAKEN) {
            physical = stats.physicalTaken();
            magic = stats.magicTaken();
            white = stats.trueTaken();
        } else {
            physical = stats.physicalDealt();
            magic = stats.magicDealt();
            white = stats.trueDealt();
        }
        int cursor = x;
        cursor += drawSegment(graphics, cursor, y, width,
                physical / total, COL_PHYSICAL);
        cursor += drawSegment(graphics, cursor, y, width,
                magic / total, COL_MAGIC);
        drawSegment(graphics, cursor, y, width,
                white / total, COL_TRUE);
    }

    private int drawSegment(
            GuiGraphics graphics, int x, int y, int width,
            double ratio, int color) {
        if (ratio <= 0.0) return 0;
        int segmentWidth = Math.max(1,
                Math.round(width * (float) ratio));
        graphics.fill(x, y, x + segmentWidth,
                y + 5, color);
        return segmentWidth;
    }

    private void drawUnitStatLegend(GuiGraphics graphics) {
        if (unitStatMode == UnitStatMode.HEALING) {
            int x = RIGHT_X + 8;
            x = drawLegend(graphics, x,
                    "gui.monster_conquest.arena.game.unit_stats.health_healing",
                    COL_HEALING);
            drawLegend(graphics, x,
                    "gui.monster_conquest.arena.game.unit_stats.shield_healing",
                    COL_TRUE);
            return;
        }
        int x = RIGHT_X + 8;
        x = drawLegend(graphics, x,
                "gui.monster_conquest.arena.game.unit_stats.physical",
                COL_PHYSICAL);
        x = drawLegend(graphics, x,
                "gui.monster_conquest.arena.game.unit_stats.magic",
                COL_MAGIC);
        drawLegend(graphics, x,
                "gui.monster_conquest.arena.game.unit_stats.true",
                COL_TRUE);
    }

    private int drawLegend(
            GuiGraphics graphics, int x,
            String key, int color) {
        graphics.fill(x, PANEL_H - 38,
                x + 5, PANEL_H - 33, color);
        Component label = Component.translatable(key);
        graphics.drawString(font, label,
                x + 7, PANEL_H - 40, color);
        return x + 9 + font.width(label);
    }

    private double statTotal(PieceView piece) {
        ArenaCombatStatsPayload.UnitStats stats =
                ArenaGameClientState.combatStats(piece.id());
        if (stats == null) return 0.0;
        return switch (unitStatMode) {
            case DAMAGE_DEALT -> stats.totalDealt();
            case HEALING -> stats.totalHealing();
            case DAMAGE_TAKEN -> stats.totalTaken();
        };
    }

    private static String formatStatValue(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return Long.toString(Math.round(value));
        }
        if (value >= 1000.0) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static int battleRowColor(
            ArenaPlayerSnapshotPayload snapshot, PlayerStatus status) {
        if (snapshot.phase() != ArenaPhase.BATTLE.ordinal()
                && snapshot.phase() != ArenaPhase.OVERTIME.ordinal()) {
            return 0;
        }
        return switch (status.battleResult()) {
            case ArenaPlayerSnapshotPayload.BATTLE_WON -> 0xA0326B43;
            case ArenaPlayerSnapshotPayload.BATTLE_LOST -> 0xA06B3232;
            case ArenaPlayerSnapshotPayload.BATTLE_FIGHTING,
                    ArenaPlayerSnapshotPayload.BATTLE_DRAW ->
                    0x90404448;
            default -> 0;
        };
    }

    @Nullable
    private static String battleLabelKey(
            ArenaPlayerSnapshotPayload snapshot, PlayerStatus status) {
        if (snapshot.phase() != ArenaPhase.BATTLE.ordinal()
                && snapshot.phase() != ArenaPhase.OVERTIME.ordinal()) {
            return null;
        }
        return switch (status.battleResult()) {
            case ArenaPlayerSnapshotPayload.BATTLE_WON ->
                    "gui.monster_conquest.arena.game.battle_won";
            case ArenaPlayerSnapshotPayload.BATTLE_LOST ->
                    "gui.monster_conquest.arena.game.battle_lost";
            case ArenaPlayerSnapshotPayload.BATTLE_FIGHTING,
                    ArenaPlayerSnapshotPayload.BATTLE_DRAW ->
                    "gui.monster_conquest.arena.game.battle_pending";
            default -> null;
        };
    }

    private static int battleLabelColor(int result) {
        return switch (result) {
            case ArenaPlayerSnapshotPayload.BATTLE_WON -> COL_OK;
            case ArenaPlayerSnapshotPayload.BATTLE_LOST -> COL_BAD;
            default -> COL_DIM;
        };
    }

    private static void drawPlayerFace(GuiGraphics graphics, UUID playerId,
                                       int x, int y, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        PlayerInfo info = minecraft.getConnection() == null
                ? null
                : minecraft.getConnection().getPlayerInfo(playerId);
        PlayerSkin skin = info == null
                ? DefaultPlayerSkin.get(playerId)
                : info.getSkin();
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private void drawFooter(GuiGraphics graphics,
                            ArenaPlayerSnapshotPayload snapshot) {
        Component notice = ArenaGameClientState.activeNotice();
        if (notice != null) {
            graphics.drawCenteredString(font, notice,
                    PANEL_W / 2, PANEL_H - 30, COL_BAD);
        } else if (selectedEquipmentIndex != null) {
            int equipmentId = snapshot != null
                    && selectedEquipmentIndex
                    < snapshot.equipmentInventory().size()
                    ? snapshot.equipmentInventory()
                    .get(selectedEquipmentIndex) : -1;
            ArenaEquipment equipment = equipmentId >= 0
                    ? ArenaEquipment.byId(equipmentId) : null;
            if (equipment != null) {
                graphics.drawCenteredString(font,
                        Component.translatable(
                                "gui.monster_conquest.arena.game.equipment_selected",
                                Component.translatable(
                                        equipment.translationKey())),
                        PANEL_W / 2, PANEL_H - 30, COL_ACCENT);
            }
        } else if (selectedPiece != null) {
            graphics.drawCenteredString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.selected_hint"),
                    PANEL_W / 2, PANEL_H - 30, COL_ACCENT);
        }
    }

    private void drawSurrenderConfirmation(GuiGraphics graphics) {
        graphics.fill(0, 0, PANEL_W, PANEL_H, 0x88000000);
        int x = PANEL_W / 2 - 170;
        int y = PANEL_H / 2 - 70;
        graphics.fill(x - 2, y - 2, x + 342, y + 112, COL_BORDER);
        graphics.fill(x, y, x + 340, y + 110, 0xF019232B);
        graphics.drawCenteredString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.surrender_question"),
                PANEL_W / 2, y + 24, COL_TEXT);
        graphics.drawCenteredString(font,
                Component.translatable(
                        "gui.monster_conquest.arena.game.surrender_warning"),
                PANEL_W / 2, y + 46, COL_BAD);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) Math.floor(viewport.localX(mouseX));
        int localY = (int) Math.floor(viewport.localY(mouseY));
        ArenaPlayerSnapshotPayload snapshot = snapshot();
        if (selectedTrait != null) {
            if (button == 0
                    && (clickTraitDetailClose(localX, localY)
                    || !insideTraitDetails(localX, localY))) {
                selectedTrait = null;
            }
            return true;
        }
        if (snapshot != null && snapshot.active()
                && button == 0) {
            if (clickRightPanelControls(
                    snapshot, localX, localY)) {
                return true;
            }
            if (clickTraitScrollbar(
                    snapshot, localX, localY)) {
                return true;
            }
            if (clickTraitRow(snapshot, localX, localY)) {
                return true;
            }
        }
        if (super.mouseClicked(localX, localY, button)) return true;
        if (button != 0 || confirmingSurrender) return false;

        if (snapshot == null || !snapshot.active()) return false;

        if (clickPlayerRow(snapshot, localX, localY)) return true;
        if (!canUseEconomy(snapshot)) return false;
        if (clickEquipmentSlot(snapshot, localX, localY)) return true;

        int cardX = 18;
        int cardY = 52;
        int gap = 8;
        int cardW = (PANEL_W - cardX * 2 - gap * 4) / 5;
        int cardH = 52;
        if (localY >= cardY && localY < cardY + cardH) {
            for (int i = 0; i < snapshot.shop().size(); i++) {
                int x = cardX + i * (cardW + gap);
                if (localX >= x && localX < x + cardW) {
                    if (snapshot.shop().get(i).unitIndex() < 0) {
                        return true;
                    }
                    ArenaUiSounds.playUnitClick();
                    sendIntent(ArenaIntentPayload.BUY,
                            snapshot.shop().get(i).unitIndex(), i, null);
                    return true;
                }
            }
        }

        if (localX >= BENCH_X
                && localX < BENCH_X + BENCH_W
                && localY >= BENCH_Y
                && localY < BENCH_Y + BENCH_CELL_H) {
            int slot = (localX - BENCH_X) / BENCH_CELL_W;
            int slotX = benchSlotX(slot);
            if (localX < slotX || localX >= slotX + BENCH_SLOT_W) {
                return false;
            }
            return clickSlot(snapshot, slot, false);
        }

        if (localX >= BOARD_X && localX < BOARD_X + BOARD_W
                && localY >= BOARD_Y && localY < BOARD_Y + BOARD_H) {
            int column = (localX - BOARD_X) / CELL_W;
            int row = (localY - BOARD_Y) / CELL_H;
            return clickSlot(snapshot,
                    row * ArenaPlayerState.BOARD_COLUMNS + column, true);
        }
        return false;
    }

    private boolean clickTraitRow(
            ArenaPlayerSnapshotPayload snapshot,
            int localX, int localY) {
        if (localX < TRAIT_LIST_X
                || localX >= TRAIT_LIST_X + TRAIT_LIST_W
                || localY < traitListTop()
                || localY >= traitListBottom()) {
            return false;
        }
        int row = (localY - traitListTop())
                / TRAIT_ROW_H;
        List<ArenaTraitAnalyzer.Status> present =
                presentTraitStatuses(snapshot);
        int index = traitScroll + row;
        if (row < 0 || row >= TRAIT_VISIBLE_ROWS
                || index < 0 || index >= present.size()) {
            return false;
        }
        selectedTrait = present.get(index).trait();
        return true;
    }

    private boolean clickTraitScrollbar(
            ArenaPlayerSnapshotPayload snapshot,
            int localX, int localY) {
        int traitCount =
                presentTraitStatuses(snapshot).size();
        if (traitCount <= TRAIT_VISIBLE_ROWS
                || localX < TRAIT_SCROLLBAR_X - 3
                || localX > TRAIT_SCROLLBAR_X
                + TRAIT_SCROLLBAR_W + 3
                || localY < traitListTop()
                || localY > traitListBottom()) {
            return false;
        }
        int thumbTop = traitScrollbarThumbY(
                traitCount, traitScroll);
        int thumbHeight =
                traitScrollbarThumbHeight(traitCount);
        traitScrollGrabOffset =
                localY >= thumbTop && localY < thumbTop + thumbHeight
                        ? localY - thumbTop
                        : thumbHeight / 2.0;
        draggingTraitScrollbar = true;
        updateTraitScrollFromDrag(
                traitCount, localY);
        return true;
    }

    private void updateTraitScrollFromDrag(
            int traitCount, int localY) {
        int trackHeight =
                traitListBottom() - traitListTop();
        int thumbHeight =
                traitScrollbarThumbHeight(traitCount);
        int travel = Math.max(1,
                trackHeight - thumbHeight);
        float progress = (float) ((localY - traitScrollGrabOffset
                - traitListTop()) / (double) travel);
        progress = Math.max(0.0f,
                Math.min(1.0f, progress));
        traitScroll = Math.round(
                traitMaxScroll(traitCount) * progress);
    }

    private static boolean insideTraitDetails(
            int localX, int localY) {
        return localX >= TRAIT_DETAIL_X
                && localX < TRAIT_DETAIL_X + TRAIT_DETAIL_W
                && localY >= TRAIT_DETAIL_Y
                && localY < TRAIT_DETAIL_Y + TRAIT_DETAIL_H;
    }

    private static boolean clickTraitDetailClose(
            int localX, int localY) {
        int right = TRAIT_DETAIL_X + TRAIT_DETAIL_W;
        return localX >= right - 30
                && localX < right - 6
                && localY >= TRAIT_DETAIL_Y + 5
                && localY < TRAIT_DETAIL_Y + 29;
    }

    private boolean clickPlayerRow(ArenaPlayerSnapshotPayload snapshot,
                                   int localX, int localY) {
        if (localX < RIGHT_X + 6 || localX >= RIGHT_X + RIGHT_W - 6) {
            return false;
        }
        for (int i = 0; i < snapshot.players().size(); i++) {
            int rowY = RIGHT_ROWS_Y + i * PLAYER_ROW_H;
            if (localY >= rowY && localY < rowY + PLAYER_ROW_H - 4) {
                sendIntent(ArenaIntentPayload.SPECTATE,
                        0, 0, snapshot.players().get(i).id());
                onClose();
                return true;
            }
        }
        return false;
    }

    private boolean clickRightPanelControls(
            ArenaPlayerSnapshotPayload snapshot,
            int localX, int localY) {
        if (localX < RIGHT_X || localX >= RIGHT_X + RIGHT_W
                || localY < PLAYER_Y || localY >= PANEL_H) {
            return false;
        }
        if (localY >= RIGHT_TAB_Y
                && localY < RIGHT_TAB_Y + RIGHT_TAB_H) {
            int secondX = RIGHT_TAB_X + RIGHT_TAB_W + 5;
            if (localX >= RIGHT_TAB_X
                    && localX < RIGHT_TAB_X + RIGHT_TAB_W) {
                rightPanelMode = RightPanelMode.PARTICIPANTS;
                return true;
            }
            if (localX >= secondX
                    && localX < secondX + RIGHT_TAB_W
                    && !snapshot.spectator()) {
                rightPanelMode = RightPanelMode.UNIT_STATS;
                return true;
            }
            return true;
        }
        RightPanelMode mode = snapshot.spectator()
                ? RightPanelMode.PARTICIPANTS : rightPanelMode;
        if (mode != RightPanelMode.UNIT_STATS) {
            return false;
        }
        if (localY >= RIGHT_STAT_Y
                && localY < RIGHT_STAT_Y + RIGHT_STAT_H) {
            int gap = 3;
            int width = (RIGHT_W - 20 - gap * 2) / 3;
            for (int index = 0;
                 index < UnitStatMode.values().length;
                 index++) {
                int x = RIGHT_X + 10
                        + index * (width + gap);
                if (localX >= x && localX < x + width) {
                    unitStatMode =
                            UnitStatMode.values()[index];
                    return true;
                }
            }
            return true;
        }
        return true;
    }

    private boolean clickSlot(ArenaPlayerSnapshotPayload snapshot,
                              int slot, boolean board) {
        PieceView existing = board
                ? pieceAtBoard(snapshot, slot)
                : pieceAtBench(snapshot, slot);
        if (selectedEquipmentIndex != null) {
            if (existing == null) return false;
            int equipmentId = snapshot.equipmentInventory()
                    .get(selectedEquipmentIndex);
            if (equipmentId < 0) {
                selectedEquipmentIndex = null;
                return false;
            }
            ArenaEquipment equipment =
                    ArenaEquipment.byId(equipmentId);
            if (equipment == ArenaEquipment.REMOVER) {
                if (isCombatEquipmentLocked(snapshot.phase())
                        && existing.boardSlot() >= 0) {
                    ArenaGameClientState.showNotice(
                            "gui.monster_conquest.arena.game.notice.equipment_locked");
                    selectedEquipmentIndex = null;
                    return true;
                }
                sendIntent(ArenaIntentPayload.DISASSEMBLE,
                        equipment.ordinal(), selectedEquipmentIndex,
                        existing.id());
            } else if (equipment.duplicator()) {
                sendIntent(ArenaIntentPayload.DUPLICATE,
                        equipment.ordinal(), selectedEquipmentIndex,
                        existing.id());
            } else {
                sendIntent(ArenaIntentPayload.EQUIP,
                        equipment.ordinal(), selectedEquipmentIndex,
                        existing.id());
            }
            selectedEquipmentIndex = null;
            return true;
        }
        if (benchOnlyPhase(snapshot) && board) return false;
        if (selectedPiece == null) {
            if (existing != null) {
                ArenaUiSounds.playUnitClick();
                selectedPiece = existing.id();
                return true;
            }
            return false;
        }
        if (benchOnlyPhase(snapshot)) {
            PieceView selected = pieceById(snapshot, selectedPiece);
            if (selected == null || selected.boardSlot() >= 0) {
                selectedPiece = null;
                return false;
            }
        }
        sendIntent(ArenaIntentPayload.MOVE,
                board ? ArenaIntentPayload.BOARD : ArenaIntentPayload.BENCH,
                slot, selectedPiece);
        selectedPiece = null;
        return true;
    }

    private boolean clickEquipmentSlot(
            ArenaPlayerSnapshotPayload snapshot, int mouseX, int mouseY) {
        int capacity = EQUIPMENT_COLUMNS * EQUIPMENT_ROWS;
        for (int i = 0; i < capacity; i++) {
            int x = equipmentSlotX(i);
            int y = equipmentSlotY(i);
            if (mouseX < x || mouseX >= x + EQUIPMENT_SLOT_W
                    || mouseY < y
                    || mouseY >= y + EQUIPMENT_SLOT_H) {
                continue;
            }
            if (i >= snapshot.equipmentInventory().size()) return false;
            int clickedEquipment =
                    snapshot.equipmentInventory().get(i);
            if (selectedEquipmentIndex == null) {
                if (clickedEquipment < 0) return false;
                ArenaUiSounds.playEquipmentClick();
                selectedEquipmentIndex = i;
                selectedPiece = null;
            } else if (selectedEquipmentIndex == i) {
                ArenaUiSounds.playEquipmentClick();
                selectedEquipmentIndex = null;
            } else {
                if (clickedEquipment >= 0) {
                    ArenaUiSounds.playEquipmentClick();
                }
                int sourceIndex = selectedEquipmentIndex;
                if (sourceIndex >= snapshot.equipmentInventory().size()
                        || snapshot.equipmentInventory()
                        .get(sourceIndex) < 0) {
                    selectedEquipmentIndex = null;
                    return false;
                }
                int action = ArenaIntentPayload.MOVE_EQUIPMENT;
                if (clickedEquipment >= 0) {
                    ArenaEquipment source = ArenaEquipment.byId(
                            snapshot.equipmentInventory().get(sourceIndex));
                    ArenaEquipment target = ArenaEquipment.byId(
                            clickedEquipment);
                    action = ArenaEquipment.combine(source, target) != null
                            ? ArenaIntentPayload.COMBINE_EQUIPMENT
                            : ArenaIntentPayload.SWAP_EQUIPMENT;
                }
                sendIntent(action, sourceIndex, i, null);
                selectedEquipmentIndex = null;
            }
            return true;
        }
        return false;
    }

    private static int equipmentSlotX(int index) {
        int column = index % EQUIPMENT_COLUMNS;
        return EQUIPMENT_X + column * EQUIPMENT_CELL_W
                + (EQUIPMENT_CELL_W - EQUIPMENT_SLOT_W) / 2;
    }

    private static int benchSlotX(int index) {
        return BENCH_X + index * BENCH_CELL_W
                + (BENCH_CELL_W - BENCH_SLOT_W) / 2;
    }

    private static int equipmentSlotY(int index) {
        int row = index / EQUIPMENT_COLUMNS;
        return EQUIPMENT_Y + row * EQUIPMENT_ROW_H;
    }

    @Nullable
    private static PieceView pieceById(ArenaPlayerSnapshotPayload snapshot,
                                       UUID id) {
        for (PieceView piece : snapshot.pieces()) {
            if (piece.id().equals(id)) return piece;
        }
        return null;
    }

    @Nullable
    private static PieceView pieceAtBoard(ArenaPlayerSnapshotPayload snapshot,
                                          int slot) {
        for (PieceView piece : snapshot.pieces()) {
            if (piece.boardSlot() == slot) return piece;
        }
        return null;
    }

    @Nullable
    private static PieceView pieceAtBench(ArenaPlayerSnapshotPayload snapshot,
                                          int slot) {
        for (PieceView piece : snapshot.pieces()) {
            if (piece.benchSlot() == slot) return piece;
        }
        return null;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(viewport.localX(mouseX), viewport.localY(mouseY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        int localX = (int) Math.floor(viewport.localX(mouseX));
        int localY = (int) Math.floor(viewport.localY(mouseY));
        if (selectedTrait != null) return true;
        ArenaPlayerSnapshotPayload snapshot = snapshot();
        if (snapshot == null || !snapshot.active()) {
            return super.mouseScrolled(
                    localX, localY, scrollX, scrollY);
        }
        int traitCount =
                presentTraitStatuses(snapshot).size();
        boolean overTraitList =
                localX >= TRAIT_LIST_X - 3
                        && localX <= TRAIT_SCROLLBAR_X
                        + TRAIT_SCROLLBAR_W + 3
                        && localY >= traitListTop()
                        && localY <= traitListBottom();
        if (overTraitList
                && traitCount > TRAIT_VISIBLE_ROWS
                && scrollY != 0.0) {
            int step = scrollY < 0.0 ? 1 : -1;
            traitScroll = clampTraitScroll(
                    traitScroll + step, traitCount);
            return true;
        }
        return super.mouseScrolled(
                localX, localY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        int localX = (int) Math.floor(viewport.localX(mouseX));
        int localY = (int) Math.floor(viewport.localY(mouseY));
        if (button == 0 && draggingTraitScrollbar) {
            ArenaPlayerSnapshotPayload snapshot = snapshot();
            if (snapshot != null && snapshot.active()) {
                updateTraitScrollFromDrag(
                        presentTraitStatuses(snapshot).size(), localY);
            }
            return true;
        }
        return super.mouseDragged(
                localX, localY, button,
                viewport.localDelta(dragX), viewport.localDelta(dragY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingTraitScrollbar) {
            draggingTraitScrollbar = false;
            return true;
        }
        return super.mouseReleased(
                viewport.localX(mouseX), viewport.localY(mouseY), button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (confirmingSurrender) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                confirmingSurrender = false;
                syncWidgets();
            }
            return true;
        }
        if (selectedTrait != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                selectedTrait = null;
                return true;
            }
            return super.keyPressed(
                    keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || ModKeyMappings.OPEN_ARENA_LOBBY.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        ArenaPlayerSnapshotPayload snapshot = snapshot();
        if (snapshot == null || !snapshot.active()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            sendIntent(ArenaIntentPayload.RETURN_TO_BOARD, 0, 0, null);
            onClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D && canUseEconomy(snapshot)) {
            ArenaUiSounds.playButtonClick();
            sendIntent(ArenaIntentPayload.REFRESH, 0, 0, null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F && canUseEconomy(snapshot)) {
            ArenaUiSounds.playButtonClick();
            sendIntent(ArenaIntentPayload.BUY_XP, 0, 0, null);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_E && canUseEconomy(snapshot)) {
            if (selectedPiece == null) {
                ArenaGameClientState.showNotice(
                        "gui.monster_conquest.arena.game.notice.select_to_sell");
            } else {
                sendIntent(ArenaIntentPayload.SELL,
                        0, 0, selectedPiece);
                selectedPiece = null;
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static boolean canUseEconomy(
            ArenaPlayerSnapshotPayload snapshot) {
        return !snapshot.spectator()
                && snapshot.alive()
                && (snapshot.phase() == ArenaPhase.SELECTION.ordinal()
                || snapshot.phase() == ArenaPhase.BATTLE.ordinal()
                || snapshot.phase() == ArenaPhase.OVERTIME.ordinal()
                || snapshot.phase() == ArenaPhase.RESULT.ordinal());
    }

    private static boolean isCombatEquipmentLocked(int phase) {
        return phase == ArenaPhase.BATTLE.ordinal()
                || phase == ArenaPhase.OVERTIME.ordinal()
                || phase == ArenaPhase.RESULT.ordinal();
    }

    private static boolean benchOnlyPhase(
            ArenaPlayerSnapshotPayload snapshot) {
        return snapshot.phase() == ArenaPhase.BATTLE.ordinal()
                || snapshot.phase() == ArenaPhase.OVERTIME.ordinal()
                || snapshot.phase() == ArenaPhase.RESULT.ordinal();
    }

    private static String phaseKey(int phase) {
        if (phase == ArenaPhase.SELECTION.ordinal()) {
            return "gui.monster_conquest.arena.game.phase.selection";
        }
        if (phase == ArenaPhase.BATTLE.ordinal()) {
            return "gui.monster_conquest.arena.game.phase.battle";
        }
        if (phase == ArenaPhase.OVERTIME.ordinal()) {
            return "gui.monster_conquest.arena.game.phase.overtime";
        }
        if (phase == ArenaPhase.RESULT.ordinal()) {
            return "gui.monster_conquest.arena.game.phase.result";
        }
        return "gui.monster_conquest.arena.game.phase.finished";
    }

    private static String stageRoundLabel(int absoluteRound) {
        int round = Math.max(1, absoluteRound);
        int stage = (round - 1) / 7 + 1;
        int step = (round - 1) % 7 + 1;
        return stage + "-" + step;
    }
}
