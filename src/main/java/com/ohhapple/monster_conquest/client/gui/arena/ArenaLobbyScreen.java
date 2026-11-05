package com.ohhapple.monster_conquest.client.gui.arena;

import com.mojang.blaze3d.platform.InputConstants;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.gui.GuiViewport;
import com.ohhapple.monster_conquest.client.input.ModKeyMappings;
import com.ohhapple.monster_conquest.arena.ArenaCareer;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload.RoomSummary;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload.RoomView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLobbySnapshotPayload.SeatView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCareerPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaLaunchProgressPayload;
import com.ohhapple.monster_conquest.network.serverbound.arena.ArenaActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 自走棋大厅。界面持有固定逻辑布局，缩放后统一反算鼠标坐标；
 * 房间状态只读服务端快照，所有操作都以房间号和当前会话重新校验。
 */
public class ArenaLobbyScreen extends Screen {

    private static final int PANEL_W = 760;
    private static final int PANEL_H = 450;
    private static final int LEFT_X = 16;
    private static final int LEFT_W = 232;
    private static final int RIGHT_X = 264;
    private static final int RIGHT_W = 480;

    private static final int LIST_Y = 92;
    private static final int LIST_H = 250;
    private static final int ROOM_ROW_H = 38;
    private static final int VISIBLE_ROOM_ROWS = 6;
    private static final int ROOM_SCROLLBAR_W = 6;

    private static final int SEAT_W = 108;
    private static final int SEAT_H = 82;
    private static final int SEAT_GAP = 8;
    private static final int SEAT_TOP = 94;
    private static final int SEAT_ROW_GAP = 10;

    private static final int COL_BG = 0xF0101418;
    private static final int COL_PANEL = 0xB0182028;
    private static final int COL_PANEL_HOVER = 0xD0304050;
    private static final int COL_BORDER = 0xFF3C4C5C;
    private static final int COL_TEXT = 0xFFE4EAF0;
    private static final int COL_DIM = 0xFF91A0AE;
    private static final int COL_ACCENT = 0xFF6CB6FF;
    private static final int COL_OK = 0xFF73D68B;
    private static final int COL_WARN = 0xFFF0C060;
    private static final int COL_BAD = 0xFFF08080;

    private static final DateTimeFormatter MATCH_TIME =
            DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final UUID NO_TARGET = ArenaActionPayload.NO_TARGET;

    private GuiViewport viewport = new GuiViewport(1.0f, 0, 0);
    private EditBox searchBox;
    private Button joinCodeButton;
    private Button createButton;
    private Button joinSelectedButton;
    private Button autoFillButton;
    private Button readyButton;
    private Button startButton;
    private Button leaveButton;
    private Button compendiumButton;

    private int roomScroll;
    private boolean draggingRoomScroll;
    private double roomScrollDragOffset;
    @Nullable private Integer selectedRoomId;
    private boolean profileOpen;
    @Nullable private UUID profilePlayerId;
    private String profilePlayerName = "";
    private boolean sentOpen;
    private boolean keepSessionOnScreenChange;

    public ArenaLobbyScreen() {
        super(Component.translatable("gui.monster_conquest.arena.title"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        minecraft.setScreen(new ArenaLobbyScreen());
    }

    @Override
    protected void init() {
        super.init();
        viewport = GuiViewport.fit(width, height, PANEL_W, PANEL_H, 8);

        searchBox = new EditBox(
                font, 22, 66, 148, 18,
                Component.translatable("gui.monster_conquest.arena.search_hint"));
        searchBox.setHint(Component.translatable("gui.monster_conquest.arena.search_hint"));
        searchBox.setMaxLength(6);
        searchBox.setFilter(value -> value.matches("\\d{0,6}"));
        searchBox.setResponder(value -> {
            roomScroll = 0;
            ensureSelection();
        });
        addRenderableWidget(searchBox);

        joinCodeButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.arena.join_code"),
                        ignored -> joinByCode())
                .bounds(176, 65, 64, 20)
                .build());

        createButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.arena.create"),
                        ignored -> sendAction(ArenaActionPayload.CREATE, 0, NO_TARGET))
                .bounds(22, 348, 218, 22)
                .build());

        joinSelectedButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.arena.join_room"),
                        ignored -> joinSelected())
                .bounds(612, 402, 124, 22)
                .build());

        autoFillButton = addRenderableWidget(Button.builder(
                        Component.empty(),
                        ignored -> {
                            RoomView room = currentRoom();
                            if (room != null) {
                                sendAction(ArenaActionPayload.TOGGLE_AUTO_FILL,
                                        room.roomId(), NO_TARGET);
                            }
                        })
                .bounds(RIGHT_X, 402, 132, 22)
                .build());

        readyButton = addRenderableWidget(Button.builder(
                        Component.empty(),
                        ignored -> {
                            RoomView room = currentRoom();
                            if (room != null) {
                                sendAction(ArenaActionPayload.SET_READY,
                                        room.roomId(), NO_TARGET);
                            }
                        })
                .bounds(RIGHT_X, 402, 116, 22)
                .build());

        startButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.arena.start"),
                        ignored -> {
                            RoomView room = currentRoom();
                            if (room != null) {
                                sendAction(ArenaActionPayload.START,
                                        room.roomId(), NO_TARGET);
                            }
                        })
                .bounds(RIGHT_X + 140, 402, 116, 22)
                .build());

        leaveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.arena.leave"),
                        ignored -> {
                            RoomView room = currentRoom();
                            sendAction(ArenaActionPayload.LEAVE,
                                    room == null ? 0 : room.roomId(), NO_TARGET);
                        })
                .bounds(RIGHT_X + 350, 402, 116, 22)
                .build());

        compendiumButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.monster_conquest.arena.compendium.button"),
                        ignored -> openCompendium())
                .bounds(PANEL_W - 96, 8, 82, 20)
                .build());

        syncWidgets();
        if (!sentOpen) {
            sentOpen = true;
            sendAction(ArenaActionPayload.OPEN, 0, NO_TARGET);
        }
    }

    public void onSnapshotUpdated() {
        ensureSelection();
        clampRoomScroll();
        syncWidgets();
    }

    private void syncWidgets() {
        ArenaLobbySnapshotPayload snapshot = snapshot();
        RoomView room = snapshot == null ? null : snapshot.currentRoom();
        boolean hasRoom = room != null;
        boolean host = isHost(room);
        boolean started = room != null && room.started();

        createButton.visible = !hasRoom;
        createButton.active = snapshot != null && !hasRoom;
        searchBox.visible = !hasRoom;
        joinCodeButton.visible = !hasRoom;
        joinCodeButton.active = parseRoomId(searchBox.getValue()) > 0;
        joinSelectedButton.visible = !hasRoom;

        RoomSummary selected = selectedRoom();
        boolean spectateSelected = selected != null && selected.started();
        joinSelectedButton.setMessage(Component.translatable(
                spectateSelected
                        ? "gui.monster_conquest.arena.spectate_room"
                        : "gui.monster_conquest.arena.join_room"));
        joinSelectedButton.active = selected != null
                && (selected.started()
                || selected.players() < ArenaLobbySnapshotPayload.MAX_SEATS);

        autoFillButton.visible = hasRoom && host;
        autoFillButton.active = hasRoom && host && !started;
        autoFillButton.setMessage(autoFillLabel(room != null && room.autoFillAi()));
        autoFillButton.setTooltip(Tooltip.create(
                Component.translatable("gui.monster_conquest.arena.auto_fill_tip")));

        readyButton.visible = hasRoom && !host;
        readyButton.active = hasRoom && !host && !started;
        SeatView ownSeat = ownSeat(room);
        boolean ready = ownSeat != null && ownSeat.ready();
        readyButton.setMessage(Component.translatable(ready
                        ? "gui.monster_conquest.arena.ready"
                        : "gui.monster_conquest.arena.not_ready")
                .withStyle(ready
                        ? ChatFormatting.GREEN : ChatFormatting.RED));
        readyButton.setTooltip(Tooltip.create(
                Component.translatable("gui.monster_conquest.arena.ready_tip")));

        startButton.visible = hasRoom && host;
        boolean allReady = room != null && allOtherPlayersReady(room);
        startButton.active = hasRoom && host && !started && allReady;
        ArenaLaunchProgressPayload progress = ArenaLobbyClientState.launchProgress();
        boolean currentLaunch = started && progress != null
                && room != null && progress.roomId() == room.roomId();
        startButton.setMessage(Component.translatable(currentLaunch
                ? "gui.monster_conquest.arena.launch.in_progress"
                : "gui.monster_conquest.arena.start"));
        startButton.setTooltip(Tooltip.create(Component.translatable(
                allReady
                        ? "gui.monster_conquest.arena.start_tip"
                        : "gui.monster_conquest.arena.start_need_ready")));

        leaveButton.visible = hasRoom;
        leaveButton.active = hasRoom && !started;
        compendiumButton.visible = !profileOpen;
        compendiumButton.active = !profileOpen;
        ensureSelection();
    }

    @Nullable
    private RoomSummary selectedRoom() {
        ArenaLobbySnapshotPayload snapshot = snapshot();
        if (snapshot == null || selectedRoomId == null) return null;
        for (RoomSummary room : snapshot.rooms()) {
            if (room.roomId() == selectedRoomId) return room;
        }
        return null;
    }

    @Nullable
    private RoomView currentRoom() {
        ArenaLobbySnapshotPayload snapshot = snapshot();
        return snapshot == null ? null : snapshot.currentRoom();
    }

    @Nullable
    private static ArenaLobbySnapshotPayload snapshot() {
        return ArenaLobbyClientState.snapshot();
    }

    private void ensureSelection() {
        ArenaLobbySnapshotPayload snapshot = snapshot();
        if (snapshot == null) return;
        if (selectedRoomId != null) {
            for (RoomSummary room : snapshot.rooms()) {
                if (room.roomId() == selectedRoomId) return;
            }
        }
        selectedRoomId = snapshot.rooms().isEmpty() ? null : snapshot.rooms().get(0).roomId();
    }

    private List<RoomSummary> filteredRooms() {
        ArenaLobbySnapshotPayload snapshot = snapshot();
        if (snapshot == null) return List.of();
        String query = searchBox == null ? "" : searchBox.getValue().trim();
        if (query.isEmpty()) return snapshot.rooms();

        List<RoomSummary> filtered = new ArrayList<>();
        for (RoomSummary room : snapshot.rooms()) {
            if (formatRoomId(room.roomId()).contains(query)
                    || Integer.toString(room.roomId()).contains(query)) {
                filtered.add(room);
            }
        }
        return filtered;
    }

    private void joinByCode() {
        int roomId = parseRoomId(searchBox.getValue());
        if (roomId <= 0) {
            ArenaLobbyClientState.showTranslatedNotice(
                    "gui.monster_conquest.arena.notice.invalid_room_code");
            return;
        }
        sendAction(ArenaActionPayload.JOIN, roomId, NO_TARGET);
    }

    private void joinSelected() {
        RoomSummary selected = selectedRoom();
        if (selected == null) {
            ArenaLobbyClientState.showTranslatedNotice(
                    "gui.monster_conquest.arena.notice.room_not_found");
            return;
        }
        sendAction(selected.started()
                        ? ArenaActionPayload.SPECTATE
                        : ArenaActionPayload.JOIN,
                selected.roomId(), NO_TARGET);
    }

    private static int parseRoomId(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.matches("\\d{1,6}")) return -1;
        int roomId = Integer.parseInt(trimmed);
        return roomId > 0 ? roomId : -1;
    }

    private void sendAction(int action, int roomId, UUID targetId) {
        sendAction(action, roomId, targetId, -1);
    }

    private void sendAction(int action, int roomId, UUID targetId,
                            int targetIndex) {
        if (Minecraft.getInstance().getConnection() == null) return;
        PacketDistributor.sendToServer(new ArenaActionPayload(
                action, roomId, targetId, targetIndex));
    }

    @Override
    public void removed() {
        if (!keepSessionOnScreenChange
                && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(
                    new ArenaActionPayload(ArenaActionPayload.CLOSE, 0, NO_TARGET));
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
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int localMouseX = viewport.localX(mouseX);
        int localMouseY = viewport.localY(mouseY);
        syncWidgets();

        graphics.fill(0, 0, width, height, 0xC0000000);
        viewport.push(graphics);

        if (profileOpen) {
            graphics.fill(0, 0, PANEL_W, PANEL_H, COL_BG);
            drawProfilePanel(graphics);
            viewport.pop(graphics);
            return;
        }

        graphics.fill(-1, -1, PANEL_W + 1, PANEL_H + 1, COL_BORDER);
        graphics.fill(0, 0, PANEL_W, PANEL_H, COL_BG);
        graphics.fill(0, 0, PANEL_W, 48, 0xFF171E25);
        graphics.fill(0, 47, PANEL_W, 48, COL_BORDER);

        drawProfileCard(graphics);
        graphics.drawCenteredString(font, title, 417, 17, COL_TEXT);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.close_hint"),
                588, 20, COL_DIM);

        drawRoomList(graphics, localMouseX, localMouseY);
        RoomView current = currentRoom();
        if (current != null) {
            drawCurrentRoom(graphics, current, localMouseX, localMouseY);
        } else {
            drawRoomDetails(graphics, localMouseX, localMouseY);
        }

        Component notice = ArenaLobbyClientState.activeNotice();
        if (notice != null) {
            graphics.drawCenteredString(font, notice, PANEL_W / 2, 436, COL_BAD);
        }

        super.render(graphics, localMouseX, localMouseY, partialTick);
        viewport.pop(graphics);
    }

    private void drawProfileCard(GuiGraphics graphics) {
        graphics.fill(16, 8, 246, 38, COL_PANEL);
        graphics.fill(16, 8, 18, 38, COL_ACCENT);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;

        drawPlayerFace(graphics, minecraft.player.getUUID(), 21, 11, 24);
        String name = trimToWidth(minecraft.player.getGameProfile().getName(), 174);
        graphics.drawString(font, Component.literal(name), 50, 11, COL_TEXT);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.profile_hint"),
                50, 23, COL_DIM);
    }

    private void drawRoomList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(LEFT_X, 54, LEFT_X + LEFT_W, 444, COL_PANEL);
        graphics.fill(LEFT_X, 54, LEFT_X + LEFT_W, 55, COL_BORDER);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.rooms"),
                22, 55, COL_TEXT);

        graphics.fill(22, LIST_Y, 238, LIST_Y + LIST_H, 0x50000000);
        ArenaLobbySnapshotPayload snapshot = snapshot();
        if (snapshot == null) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.monster_conquest.arena.loading"),
                    130, LIST_Y + LIST_H / 2 - 4, COL_DIM);
            return;
        }

        List<RoomSummary> rooms = filteredRooms();
        if (rooms.isEmpty()) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.monster_conquest.arena.no_rooms"),
                    130, LIST_Y + LIST_H / 2 - 4, COL_DIM);
            return;
        }

        clampRoomScroll();
        int first = roomScroll;
        int last = Math.min(rooms.size(), first + VISIBLE_ROOM_ROWS);
        for (int index = first; index < last; index++) {
            RoomSummary room = rooms.get(index);
            int rowY = LIST_Y + (index - first) * ROOM_ROW_H;
            boolean hovered = mouseX >= 22 && mouseX < 238
                    && mouseY >= rowY && mouseY < rowY + ROOM_ROW_H;
            boolean selected = selectedRoomId != null && selectedRoomId == room.roomId();

            int background = selected
                    ? 0xA0446A92
                    : (hovered ? COL_PANEL_HOVER : 0x40000000);
            graphics.fill(22, rowY + 1, 238, rowY + ROOM_ROW_H - 1, background);
            graphics.drawString(font,
                    Component.translatable("gui.monster_conquest.arena.room_number",
                            formatRoomId(room.roomId())),
                    27, rowY + 5, selected ? 0xFFFFFFFF : COL_TEXT);
            graphics.drawString(font,
                    Component.translatable("gui.monster_conquest.arena.room_owner",
                            trimToWidth(room.hostName(), 86)),
                    27, rowY + 17, COL_DIM);
            graphics.drawString(font,
                    Component.literal(room.players() + "/" + ArenaLobbySnapshotPayload.MAX_SEATS),
                    212, rowY + 5, room.players() >= 8 ? COL_BAD : COL_DIM);
            graphics.drawString(font,
                    Component.translatable(room.started()
                            ? "gui.monster_conquest.arena.started_short"
                            : "gui.monster_conquest.arena.waiting_short"),
                    162, rowY + 17,
                    room.started() ? COL_WARN : COL_OK);
        }

        int maxScroll = maxRoomScroll();
        if (maxScroll > 0) {
            int trackH = roomScrollTrackHeight();
            int barH = roomScrollThumbHeight(rooms.size());
            int barY = roomScrollThumbY(rooms.size());
            int scrollbarX = 238 - ROOM_SCROLLBAR_W;
            graphics.fill(scrollbarX, LIST_Y, 238,
                    LIST_Y + trackH, 0x40000000);
            graphics.fill(scrollbarX, barY, 238,
                    barY + barH, COL_DIM);
        }
    }

    private void drawRoomDetails(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(RIGHT_X, 54, RIGHT_X + RIGHT_W, 444, COL_PANEL);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.room_detail"),
                RIGHT_X + 12, 62, COL_TEXT);

        RoomSummary room = selectedRoom();
        if (room == null) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.monster_conquest.arena.select_room"),
                    RIGHT_X + RIGHT_W / 2, 210, COL_DIM);
            return;
        }

        int cardX = RIGHT_X + 12;
        int cardY = 88;
        int cardW = RIGHT_W - 24;
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 190, 0x60000000);
        graphics.fill(cardX, cardY, cardX + 4, cardY + 190, COL_ACCENT);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.room_number",
                        formatRoomId(room.roomId())),
                cardX + 16, cardY + 16, COL_TEXT);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.room_owner",
                        room.hostName()),
                cardX + 16, cardY + 38, COL_DIM);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.player_count",
                        room.players(), ArenaLobbySnapshotPayload.MAX_SEATS),
                cardX + 16, cardY + 58, COL_TEXT);
        graphics.drawString(font, autoFillLabel(room.autoFillAi()),
                cardX + 16, cardY + 80, COL_TEXT);
        graphics.drawString(font,
                Component.translatable(room.started()
                        ? "gui.monster_conquest.arena.started"
                        : "gui.monster_conquest.arena.waiting"),
                cardX + 16, cardY + 104,
                room.started() ? COL_WARN : COL_OK);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.seat_hint"),
                cardX + 16, cardY + 140, COL_DIM);
    }

    private void drawCurrentRoom(GuiGraphics graphics, RoomView room,
                                 int mouseX, int mouseY) {
        graphics.fill(RIGHT_X, 54, RIGHT_X + RIGHT_W, 444, COL_PANEL);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.room_number",
                        formatRoomId(room.roomId())),
                RIGHT_X + 8, 62, COL_TEXT);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.room_owner",
                        hostName(room)),
                RIGHT_X + 145, 62, COL_DIM);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.player_count",
                        playerCount(room), ArenaLobbySnapshotPayload.MAX_SEATS),
                RIGHT_X + 350, 62, COL_DIM);

        UUID selfId = Minecraft.getInstance().player == null
                ? NO_TARGET
                : Minecraft.getInstance().player.getUUID();
        for (int index = 0; index < ArenaLobbySnapshotPayload.MAX_SEATS; index++) {
            int row = index / 4;
            int col = index % 4;
            int x = RIGHT_X + 8 + col * (SEAT_W + SEAT_GAP);
            int y = SEAT_TOP + row * (SEAT_H + SEAT_ROW_GAP);
            SeatView seat = seatAt(room, index);
            drawSeat(graphics, room, seat, x, y, mouseX, mouseY, selfId);
        }

        int infoY = 280;
        graphics.fill(RIGHT_X + 8, infoY, RIGHT_X + RIGHT_W - 8, infoY + 96, 0x60000000);
        graphics.drawString(font, autoFillLabel(room.autoFillAi()),
                RIGHT_X + 18, infoY + 10, COL_TEXT);
        int filled = playerCount(room);
        int aiFill = room.autoFillAi()
                ? ArenaLobbySnapshotPayload.MAX_SEATS - filled
                : (filled == 1 ? 1 : 0);
        ArenaLaunchProgressPayload progress = ArenaLobbyClientState.launchProgress();
        if (room.started() && progress != null
                && progress.roomId() == room.roomId()) {
            int barX = RIGHT_X + 18;
            int barY = infoY + 53;
            int barW = RIGHT_W - 36;
            graphics.drawString(font,
                    Component.translatable(progressStageKey(progress.stage())),
                    barX, infoY + 30, COL_WARN);
            graphics.fill(barX, barY, barX + barW, barY + 10, 0x90000000);
            graphics.fill(barX, barY,
                    barX + Math.round(barW * progress.progress()), barY + 10,
                    COL_ACCENT);
            graphics.drawString(font,
                    Component.translatable(
                            "gui.monster_conquest.arena.launch_eta",
                            Math.max(0,
                                    (progress.estimatedRemainingMillis() + 999)
                                            / 1000)),
                    barX, infoY + 70, COL_DIM);
        } else {
            Component phaseStatus = Component.translatable(
                    room.started()
                            ? "gui.monster_conquest.arena.started"
                            : "gui.monster_conquest.arena.waiting");
            Component statusText;
            if (!room.autoFillAi() && filled == 1) {
                statusText = Component.translatable(
                        "gui.monster_conquest.arena.room_status_solo_fallback",
                        phaseStatus);
            } else {
                statusText = Component.translatable(
                        "gui.monster_conquest.arena.room_status",
                        phaseStatus,
                        aiFill);
            }
            graphics.drawString(font,
                    statusText,
                    RIGHT_X + 18, infoY + 30,
                    room.started() ? COL_WARN : COL_OK);
            graphics.drawString(font,
                    Component.translatable(isHost(room)
                            ? "gui.monster_conquest.arena.host_tip"
                            : "gui.monster_conquest.arena.guest_tip"),
                    RIGHT_X + 18, infoY + 52, COL_DIM);
        }

        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.leave_tip"),
                RIGHT_X + 8, 388, COL_DIM);
    }

    private void drawSeat(GuiGraphics graphics, RoomView room, SeatView seat,
                          int x, int y, int mouseX, int mouseY, UUID selfId) {
        boolean empty = seat.playerId() == null;
        boolean host = !empty && seat.playerId().equals(room.hostId());
        boolean self = !empty && seat.playerId().equals(selfId);
        boolean hovered = mouseX >= x && mouseX < x + SEAT_W
                && mouseY >= y && mouseY < y + SEAT_H;
        int border = host ? COL_WARN : (hovered && empty ? COL_ACCENT : COL_BORDER);

        graphics.fill(x, y, x + SEAT_W, y + SEAT_H, border);
        graphics.fill(x + 1, y + 1, x + SEAT_W - 1, y + SEAT_H - 1,
                empty ? 0x60000000 : 0xC0202A32);

        if (empty) {
            graphics.drawCenteredString(font,
                    Component.translatable("gui.monster_conquest.arena.empty_slot"),
                    x + SEAT_W / 2, y + 34, COL_DIM);
            return;
        }

        drawPlayerFace(graphics, seat.playerId(), x + 7, y + 7, 36);
        String displayName = self
                ? Component.translatable("gui.monster_conquest.arena.player_you",
                        seat.playerName()).getString()
                : seat.playerName();
        int nameWidth = isHost(room) && !host ? 37 : 55;
        graphics.drawString(font, Component.literal(trimToWidth(displayName, nameWidth)),
                x + 49, y + 9, COL_TEXT);
        graphics.drawString(font,
                Component.translatable(host
                        ? "gui.monster_conquest.arena.host"
                        : (seat.ready()
                                ? "gui.monster_conquest.arena.ready"
                                : "gui.monster_conquest.arena.not_ready")),
                x + 49, y + 23,
                host ? COL_WARN : (seat.ready() ? COL_OK : COL_BAD));
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.seat_number",
                        seat.index() + 1),
                x + 8, y + 65, COL_DIM);

        if (isHost(room) && !room.started() && !host) {
            int kickX = x + SEAT_W - 17;
            int kickY = y + 5;
            boolean kickHovered = mouseX >= kickX && mouseX < kickX + 12
                    && mouseY >= kickY && mouseY < kickY + 12;
            graphics.fill(kickX, kickY, kickX + 12, kickY + 12,
                    kickHovered ? 0xFFD05050 : 0x90502020);
            graphics.drawCenteredString(font, Component.literal("×"),
                    kickX + 6, kickY + 2, 0xFFFFFFFF);
        }
    }

    private void drawProfilePanel(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return;
        UUID playerId = profilePlayerId == null
                ? minecraft.player.getUUID() : profilePlayerId;
        String playerName = profilePlayerName.isBlank()
                ? minecraft.player.getGameProfile().getName()
                : profilePlayerName;
        boolean self = playerId.equals(minecraft.player.getUUID());

        int x = 28;
        int y = 54;
        int w = 480;
        int h = 312;
        graphics.fill(x - 1, y - 1, x + w + 1, y + h + 1, COL_BORDER);
        graphics.fill(x, y, x + w, y + h, 0xF0182028);
        graphics.fill(x, y, x + w, y + 34, 0xFF202B35);
        Component title = self
                ? Component.translatable(
                "gui.monster_conquest.arena.career_title")
                : Component.translatable(
                "gui.monster_conquest.arena.career_title_other",
                playerName);
        graphics.drawString(font, title,
                x + 12, y + 12, COL_TEXT);
        graphics.drawString(font, Component.literal("×"),
                x + w - 20, y + 8, COL_DIM);

        drawPlayerFace(graphics, playerId, x + 14, y + 50, 48);
        graphics.drawString(font,
                Component.literal(playerName),
                x + 74, y + 52, COL_TEXT);
        graphics.drawString(font,
                Component.translatable(self
                                ? "gui.monster_conquest.arena.career_subtitle"
                                : "gui.monster_conquest.arena.career_subtitle_other"),
                x + 74, y + 68, COL_DIM);

        ArenaLobbySnapshotPayload snapshot = snapshot();
        ArenaCareerPayload viewed = ArenaLobbyClientState.viewedCareer();
        ArenaCareer career;
        if (self && snapshot != null) {
            career = snapshot.career();
        } else if (viewed != null
                && playerId.equals(viewed.playerId())) {
            career = viewed.career();
        } else {
            career = new ArenaCareer(0, 0, 0, 0, 0, List.of());
        }
        int statY = y + 116;
        drawStat(graphics, x + 18, statY,
                "gui.monster_conquest.arena.matches", Integer.toString(career.matches()));
        drawStat(graphics, x + 18, statY + 28,
                "gui.monster_conquest.arena.wins", Integer.toString(career.wins()));
        drawStat(graphics, x + 18, statY + 56,
                "gui.monster_conquest.arena.top_half", Integer.toString(career.topHalf()));
        int winRate = career.matches() == 0
                ? 0 : career.topHalf() * 100 / career.matches();
        drawStat(graphics, x + 18, statY + 84,
                "gui.monster_conquest.arena.win_rate", winRate + "%");
        drawStat(graphics, x + 18, statY + 112,
                "gui.monster_conquest.arena.best_streak",
                Integer.toString(career.bestWinStreak()));

        int historyX = x + 222;
        int historyY = y + 104;
        graphics.fill(historyX - 1, historyY - 1, x + w - 12, y + h - 12, 0x50000000);
        graphics.drawString(font,
                Component.translatable("gui.monster_conquest.arena.history"),
                historyX + 7, historyY + 6, COL_TEXT);
        if (career.recentMatches().isEmpty()) {
            graphics.drawString(font,
                    Component.translatable("gui.monster_conquest.arena.no_history"),
                    historyX + 7, historyY + 30, COL_DIM);
        } else {
            int rowY = historyY + 26;
            int rows = Math.min(6, career.recentMatches().size());
            for (int i = 0; i < rows; i++) {
                var match = career.recentMatches().get(i);
                String time = MATCH_TIME.format(Instant.ofEpochMilli(
                        match.finishedAtEpochMillis()));
                graphics.drawString(font,
                        Component.translatable("gui.monster_conquest.arena.history_entry",
                                formatRoomId(match.roomId()),
                                match.placement(),
                                match.participants(),
                                time),
                        historyX + 7, rowY, match.placement() == 1 ? COL_WARN : COL_DIM);
                rowY += 22;
            }
        }
    }

    private void drawStat(GuiGraphics graphics, int x, int y,
                          String labelKey, String value) {
        graphics.drawString(font, Component.translatable(labelKey), x, y, COL_DIM);
        graphics.drawString(font, Component.literal(value), x + 118, y, COL_TEXT);
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
        graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, 0xD0000000);
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
    }

    private void openProfile(UUID playerId, String playerName,
                             boolean requestCareer) {
        profilePlayerId = playerId;
        profilePlayerName = playerName == null ? "" : playerName;
        profileOpen = true;
        RoomView room = currentRoom();
        if (requestCareer && room != null) {
            sendAction(ArenaActionPayload.VIEW_CAREER,
                    room.roomId(), playerId);
        }
    }

    private void closeProfile() {
        profileOpen = false;
        profilePlayerId = null;
        profilePlayerName = "";
    }

    private static String progressStageKey(int stage) {
        return switch (stage) {
            case ArenaLaunchProgressPayload.STARTING_PROCESS ->
                    "gui.monster_conquest.arena.launch.starting_process";
            case ArenaLaunchProgressPayload.LOADING_WORLD ->
                    "gui.monster_conquest.arena.launch.loading_world";
            case ArenaLaunchProgressPayload.TRANSFERRING_PLAYERS ->
                    "gui.monster_conquest.arena.launch.transferring";
            case ArenaLaunchProgressPayload.ENTERING_ARENA ->
                    "gui.monster_conquest.arena.launch.entering";
            default -> "gui.monster_conquest.arena.launch.queued";
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int localX = (int) Math.floor(viewport.localX(mouseX));
        int localY = (int) Math.floor(viewport.localY(mouseY));

        if (profileOpen) {
            if (button == 0) {
                if (localX < 28 || localX >= 508
                        || localY < 54 || localY >= 366
                        || (localX >= 488 && localX < 506
                                && localY >= 54 && localY < 90)) {
                    closeProfile();
                }
            }
            return true;
        }

        if (super.mouseClicked(localX, localY, button)) return true;
        if (button != 0) return false;

        if (!profileOpen && isOverRoomScrollbar(localX, localY)
                && beginRoomScrollDrag(localY)) {
            return true;
        }

        if (localX >= 16 && localX < 246 && localY >= 8 && localY < 38) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                openProfile(minecraft.player.getUUID(),
                        minecraft.player.getGameProfile().getName(), false);
            }
            return true;
        }

        if (localX >= 22 && localX < 238 - ROOM_SCROLLBAR_W
                && localY >= LIST_Y && localY < LIST_Y + LIST_H) {
            List<RoomSummary> rooms = filteredRooms();
            int index = roomScroll + (localY - LIST_Y) / ROOM_ROW_H;
            if (index >= 0 && index < rooms.size()) {
                selectedRoomId = rooms.get(index).roomId();
                return true;
            }
        }

        RoomView room = currentRoom();
        if (room != null) {
            for (int index = 0;
                 index < ArenaLobbySnapshotPayload.MAX_SEATS; index++) {
                SeatView seat = seatAt(room, index);
                if (seat.playerId() == null) continue;
                int row = index / 4;
                int col = index % 4;
                int seatX = RIGHT_X + 8 + col * (SEAT_W + SEAT_GAP);
                int seatY = SEAT_TOP + row * (SEAT_H + SEAT_ROW_GAP);
                if (localX >= seatX + 7 && localX < seatX + 43
                        && localY >= seatY + 7 && localY < seatY + 43) {
                    Minecraft minecraft = Minecraft.getInstance();
                    UUID selfId = minecraft.player == null
                            ? null : minecraft.player.getUUID();
                    openProfile(seat.playerId(), seat.playerName(),
                            !seat.playerId().equals(selfId));
                    return true;
                }
            }
        }

        if (room != null && isHost(room) && !room.started()) {
            for (int index = 0; index < ArenaLobbySnapshotPayload.MAX_SEATS; index++) {
                SeatView seat = seatAt(room, index);
                if (seat.playerId() == null
                        || seat.playerId().equals(room.hostId())) {
                    continue;
                }
                int row = index / 4;
                int col = index % 4;
                int seatX = RIGHT_X + 8 + col * (SEAT_W + SEAT_GAP);
                int seatY = SEAT_TOP + row * (SEAT_H + SEAT_ROW_GAP);
                int kickX = seatX + SEAT_W - 17;
                int kickY = seatY + 5;
                if (localX >= kickX && localX < kickX + 12
                        && localY >= kickY && localY < kickY + 12) {
                    sendAction(ArenaActionPayload.KICK, room.roomId(), seat.playerId());
                    return true;
                }
            }
        }

        if (room != null && !room.started()) {
            SeatView ownSeat = ownSeat(room);
            if (ownSeat != null) {
                for (int index = 0;
                     index < ArenaLobbySnapshotPayload.MAX_SEATS; index++) {
                    int row = index / 4;
                    int col = index % 4;
                    int seatX = RIGHT_X + 8 + col * (SEAT_W + SEAT_GAP);
                    int seatY = SEAT_TOP + row * (SEAT_H + SEAT_ROW_GAP);
                    if (localX < seatX || localX >= seatX + SEAT_W
                            || localY < seatY || localY >= seatY + SEAT_H) {
                        continue;
                    }
                    SeatView target = seatAt(room, index);
                    if (index != ownSeat.index()
                            && target.playerId() == null) {
                        sendAction(ArenaActionPayload.CHANGE_SEAT,
                                room.roomId(), NO_TARGET, index);
                        return true;
                    }
                    return false;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double scrollX, double scrollY) {
        int localX = (int) Math.floor(viewport.localX(mouseX));
        int localY = (int) Math.floor(viewport.localY(mouseY));
        if (!profileOpen && localX >= 22 && localX < 238
                && localY >= LIST_Y && localY < LIST_Y + LIST_H) {
            roomScroll -= (int) Math.round(scrollY);
            clampRoomScroll();
            return true;
        }
        return super.mouseScrolled(localX, localY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        int localY = (int) Math.floor(viewport.localY(mouseY));
        if (draggingRoomScroll && button == 0) {
            updateRoomScrollDrag(localY);
            return true;
        }
        return super.mouseDragged(
                viewport.localX(mouseX),
                localY,
                button,
                viewport.localDelta(dragX),
                viewport.localDelta(dragY));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingRoomScroll && button == 0) {
            draggingRoomScroll = false;
            return true;
        }
        return super.mouseReleased(
                viewport.localX(mouseX), viewport.localY(mouseY), button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(viewport.localX(mouseX), viewport.localY(mouseY));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (profileOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeProfile();
            return true;
        }
        if (ModKeyMappings.OPEN_ARENA_LOBBY.matches(keyCode, scanCode)
                || keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clampRoomScroll() {
        roomScroll = Mth.clamp(roomScroll, 0, maxRoomScroll());
    }

    private int maxRoomScroll() {
        return Math.max(0, filteredRooms().size() - VISIBLE_ROOM_ROWS);
    }

    private int roomScrollTrackHeight() {
        return VISIBLE_ROOM_ROWS * ROOM_ROW_H - 2;
    }

    private int roomScrollThumbHeight(int roomCount) {
        if (roomCount <= VISIBLE_ROOM_ROWS) return roomScrollTrackHeight();
        return Math.max(12, roomScrollTrackHeight()
                * VISIBLE_ROOM_ROWS / roomCount);
    }

    private int roomScrollThumbY(int roomCount) {
        int maxScroll = Math.max(0, roomCount - VISIBLE_ROOM_ROWS);
        if (maxScroll == 0) return LIST_Y;
        int range = roomScrollTrackHeight()
                - roomScrollThumbHeight(roomCount);
        return LIST_Y + range * roomScroll / maxScroll;
    }

    private static boolean isOverRoomScrollbar(int mouseX, int mouseY) {
        return mouseX >= 238 - ROOM_SCROLLBAR_W && mouseX < 238
                && mouseY >= LIST_Y
                && mouseY < LIST_Y + VISIBLE_ROOM_ROWS * ROOM_ROW_H - 2;
    }

    private boolean beginRoomScrollDrag(int mouseY) {
        int roomCount = filteredRooms().size();
        if (roomCount <= VISIBLE_ROOM_ROWS) return false;
        int thumbY = roomScrollThumbY(roomCount);
        int thumbH = roomScrollThumbHeight(roomCount);
        roomScrollDragOffset = mouseY >= thumbY && mouseY < thumbY + thumbH
                ? mouseY - thumbY : thumbH / 2.0;
        draggingRoomScroll = true;
        updateRoomScrollDrag(mouseY);
        return true;
    }

    private void updateRoomScrollDrag(int mouseY) {
        int roomCount = filteredRooms().size();
        int maxScroll = Math.max(0, roomCount - VISIBLE_ROOM_ROWS);
        int range = roomScrollTrackHeight()
                - roomScrollThumbHeight(roomCount);
        if (maxScroll == 0 || range <= 0) {
            roomScroll = 0;
            return;
        }
        double thumbY = Mth.clamp(
                mouseY - roomScrollDragOffset,
                LIST_Y, LIST_Y + range);
        roomScroll = Mth.clamp((int) Math.round(
                (thumbY - LIST_Y) * maxScroll / range), 0, maxScroll);
    }

    private static SeatView seatAt(RoomView room, int index) {
        for (SeatView seat : room.seats()) {
            if (seat.index() == index) return seat;
        }
        return new SeatView(index, null, "", false);
    }

    private static boolean isHost(@Nullable RoomView room) {
        Minecraft minecraft = Minecraft.getInstance();
        return room != null
                && minecraft.player != null
                && room.hostId().equals(minecraft.player.getUUID());
    }

    private static boolean allOtherPlayersReady(RoomView room) {
        for (SeatView seat : room.seats()) {
            if (seat.playerId() != null
                    && !seat.playerId().equals(room.hostId())
                    && !seat.ready()) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static SeatView ownSeat(@Nullable RoomView room) {
        Minecraft minecraft = Minecraft.getInstance();
        if (room == null || minecraft.player == null) return null;
        for (SeatView seat : room.seats()) {
            if (minecraft.player.getUUID().equals(seat.playerId())) return seat;
        }
        return null;
    }

    private static int playerCount(RoomView room) {
        int count = 0;
        for (SeatView seat : room.seats()) {
            if (seat.playerId() != null) count++;
        }
        return count;
    }

    private static String hostName(RoomView room) {
        for (SeatView seat : room.seats()) {
            if (room.hostId().equals(seat.playerId())) return seat.playerName();
        }
        return "";
    }

    private static Component autoFillLabel(boolean enabled) {
        return Component.translatable("gui.monster_conquest.arena.auto_fill",
                Component.translatable(enabled
                        ? "gui.monster_conquest.arena.on"
                        : "gui.monster_conquest.arena.off"));
    }

    private static String formatRoomId(int roomId) {
        return String.format(Locale.ROOT, "%06d", roomId);
    }

    private String trimToWidth(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }
}
