package com.ohhapple.monster_conquest.client.gui;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.client.input.ModKeyMappings;
import com.ohhapple.monster_conquest.network.serverbound.command.PlayerCommandPayload;
import com.ohhapple.monster_conquest.network.serverbound.command.SelectSquadPayload;
import com.ohhapple.monster_conquest.player.PlayerCommandState.FormationMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * 指挥轮盘。
 *  - 左侧：小分队切换按钮组（全选 + 1..N）
 *  - 中央：扇区数随分类动态变化的轮盘
 *  - 右侧：三个分类按钮（行动策略 / 攻击策略 / 阵型策略）
 *
 * currentCategory / currentSquad 为 static：
 *   关闭轮盘后再打开会保留上次的分类和小分队选择。
 */
public class CommandWheelScreen extends Screen {

    // ==================== 几何常量 ====================

    private static final int INNER_R = 50;
    private static final int OUTER_R = 135;

    private static final int BORDER_IDLE = 0xC0404040;
    private static final int BORDER_HOVER = 0xFFFFFFFF;
    private static final int FILL_ALPHA = 0xB0;
    private static final int INNER_FILL_COLOR = 0xFF181818;

    // ==================== 颜色 ====================

    private static final int COLOR_FORMATION = 0xFFB0C060;
    private static final int COLOR_MOVEMENT  = 0xFF60C0C0;
    private static final int COLOR_ATTACK    = 0xFFC06060;
    private static final int COLOR_VOLLEY    = 0xFFFFA000;
    private static final int COLOR_PLAYER    = 0xFFA080FF;

    private static final int COLOR_BUTTON_IDLE   = 0xC0303030;
    private static final int COLOR_BUTTON_HOVER  = 0xC0606060;
    private static final int COLOR_BUTTON_ACTIVE = 0xC060A0FF;
    private static final int COLOR_BUTTON_TEXT   = 0xFFFFFFFF;
    private static final int COLOR_BUTTON_BORDER = 0xFFFFFFFF;

    // ==================== 布局常量 ====================

    private static final int SIDE_X_MARGIN = 16;

    private static final int CATEGORY_BUTTON_W = 80;
    private static final int CATEGORY_BUTTON_H = 22;
    private static final int CATEGORY_BUTTON_GAP = 6;

    private static final int SQUAD_BUTTON_W = 60;
    private static final int SQUAD_BUTTON_H = 18;
    private static final int SQUAD_BUTTON_GAP = 3;

    private static final String LANG_PREFIX = "gui.monster_conquest.command_wheel.";

    // ==================== 分类 ====================
    private enum Category {
        MOVEMENT("category.movement"),
        ATTACK("category.attack"),
        FORMATION("category.formation"),
        // 玩家策略：放在分类列表最下面
        PLAYER("category.player");

        private final String key;
        Category(String key) { this.key = key; }
        Component label() { return Component.translatable(LANG_PREFIX + key); }
    }

    private record WheelOption(Component label, int action, int value, int color) {}

    private static final WheelOption[] FORMATION_OPTIONS = new WheelOption[]{
            new WheelOption(Component.translatable(LANG_PREFIX + "action.formation.line"),
                    PlayerCommandPayload.ACT_SET_FORMATION,
                    FormationMode.LINE.getId(), COLOR_FORMATION),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.formation.shield_wall"),
                    PlayerCommandPayload.ACT_SET_FORMATION,
                    FormationMode.SHIELD_WALL.getId(), COLOR_FORMATION),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.formation.wedge"),
                    PlayerCommandPayload.ACT_SET_FORMATION,
                    FormationMode.WEDGE.getId(), COLOR_FORMATION),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.formation.skirmish"),
                    PlayerCommandPayload.ACT_SET_FORMATION,
                    FormationMode.SKIRMISH.getId(), COLOR_FORMATION),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.formation.free"),
                    PlayerCommandPayload.ACT_SET_FORMATION,
                    FormationMode.FREE.getId(), COLOR_FORMATION),
    };

    private static final WheelOption[] ATTACK_OPTIONS = new WheelOption[]{
            new WheelOption(Component.translatable(LANG_PREFIX + "action.autonomous"),
                    PlayerCommandPayload.ACT_AUTONOMOUS, 0, COLOR_ATTACK),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.stop_attack"),
                    PlayerCommandPayload.ACT_STOP_ATTACK, 0, COLOR_ATTACK),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.free_aggressive"),
                    PlayerCommandPayload.ACT_FREE_AGGRESSIVE, 0, COLOR_ATTACK),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.attack_look"),
                    PlayerCommandPayload.ACT_ATTACK_LOOK, 0, COLOR_ATTACK),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.volley_start"),
                    PlayerCommandPayload.ACT_VOLLEY_START, 0, COLOR_VOLLEY),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.volley_stop"),
                    PlayerCommandPayload.ACT_VOLLEY_STOP, 0, COLOR_VOLLEY),
    };

    private static final WheelOption[] MOVEMENT_OPTIONS = new WheelOption[]{
            new WheelOption(Component.translatable(LANG_PREFIX + "action.follow_me"),
                    PlayerCommandPayload.ACT_FOLLOW_ME, 0, COLOR_MOVEMENT),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.soft_follow"),
                    PlayerCommandPayload.ACT_SOFT_FOLLOW, 0, COLOR_MOVEMENT),
            // 自由移动（像原版生物一样自由游走）
            new WheelOption(Component.translatable(LANG_PREFIX + "action.free_move"),
                    PlayerCommandPayload.ACT_FREE_MOVE, 0, COLOR_MOVEMENT),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.hold"),
                    PlayerCommandPayload.ACT_HOLD, 0, COLOR_MOVEMENT),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.move_to_look"),
                    PlayerCommandPayload.ACT_MOVE_TO_LOOK, 0, COLOR_MOVEMENT),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.ride_cavalry"),
                    PlayerCommandPayload.ACT_RIDE_CAVALRY, 0, COLOR_MOVEMENT),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.ride_infantry"),
                    PlayerCommandPayload.ACT_RIDE_INFANTRY, 0, COLOR_MOVEMENT),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.ride_default"),
                    PlayerCommandPayload.ACT_RIDE_DEFAULT, 0, COLOR_MOVEMENT),
            // 把准星指着的小队并入 / 互换到当前选中的 1-5 分队
            new WheelOption(Component.translatable(LANG_PREFIX + "action.pull_squad"),
                    PlayerCommandPayload.ACT_PULL_SQUAD, 0, COLOR_MOVEMENT),
            // 把附近无小队单兵编入当前选中分队
            new WheelOption(Component.translatable(LANG_PREFIX + "action.enlist_lone"),
                    PlayerCommandPayload.ACT_ENLIST_LONE, 0, COLOR_MOVEMENT),
            // 解散当前选中分队（士兵变为无小队）
            new WheelOption(Component.translatable(LANG_PREFIX + "action.dissolve_squad"),
                    PlayerCommandPayload.ACT_DISSOLVE_SQUAD, 0, COLOR_MOVEMENT),
    };

    /** 玩家策略：马哨在战术装备栏时控制玩家上马 / 下马。 */
    private static final WheelOption[] PLAYER_OPTIONS = new WheelOption[]{
            new WheelOption(Component.translatable(LANG_PREFIX + "action.player_mount"),
                    PlayerCommandPayload.ACT_PLAYER_MOUNT, 0, COLOR_PLAYER),
            new WheelOption(Component.translatable(LANG_PREFIX + "action.player_dismount"),
                    PlayerCommandPayload.ACT_PLAYER_DISMOUNT, 0, COLOR_PLAYER),
    };

    // ==================== 状态（跨实例保留） ====================

    private static Category currentCategory = Category.MOVEMENT;
    private static int currentSquad = 0;

    public static void onServerSyncSquad(int squad) {
        currentSquad = squad;
    }
    /**
     * 断连 / 重连时清空静态缓存，避免跨服务器残留。
     *   下一次连接服务端会在 PlayerLoggedInEvent 里推 SyncSquadPayload 覆盖。
     */
    public static void resetSquadSelection() {
        currentSquad = 1;
    }

    private int hoveredSector = -1;
    private boolean clickedThisRound = false;
    private int hoveredCategoryButton = -1;
    private int hoveredSquadButton = -1;

    // ==================== 构造 ====================

    public CommandWheelScreen() {
        super(Component.translatable(LANG_PREFIX + "title"));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return false; }

    // ==================== 输入 ====================

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        int wheelKey = ModKeyMappings.COMMAND_WHEEL.getKey().getValue();
        if (keyCode == wheelKey) {
            if (!clickedThisRound && isValidSector(hoveredSector)) {
                sendOption(currentOptions()[hoveredSector]);
            }
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return true;

        int catHit = hitCategoryButton(mx, my);
        if (catHit >= 0) {
            currentCategory = Category.values()[catHit];
            hoveredSector = -1;
            return true;
        }

        int squadHit = hitSquadButton(mx, my);
        if (squadHit >= 0) {
            setSquad(squadIdAt(squadHit));   // 位置索引 → 队号
            return true;
        }

        float cx = this.width / 2f;
        float cy = this.height / 2f;
        updateHovered(mx, my, cx, cy);
        if (isValidSector(hoveredSector)) {
            sendOption(currentOptions()[hoveredSector]);
            clickedThisRound = true;
        }
        return true;
    }

    private static void sendOption(WheelOption opt) {
        PacketDistributor.sendToServer(
                new PlayerCommandPayload(opt.action(), opt.value()));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private void setSquad(int squad) {
        currentSquad = squad;
        PacketDistributor.sendToServer(new SelectSquadPayload(squad));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isValidSector(int i) {
        return i >= 0 && i < currentOptions().length;
    }

    private WheelOption[] currentOptions() {
        return switch (currentCategory) {
            case FORMATION -> FORMATION_OPTIONS;
            case ATTACK -> ATTACK_OPTIONS;
            case MOVEMENT -> MOVEMENT_OPTIONS;
            case PLAYER -> PLAYER_OPTIONS;
        };
    }

    // ==================== 按钮位置 ====================

    private int categoryButtonX() {
        return this.width - SIDE_X_MARGIN - CATEGORY_BUTTON_W;
    }

    private int categoryButtonY(int idx) {
        int total = Category.values().length;
        int totalH = total * CATEGORY_BUTTON_H + (total - 1) * CATEGORY_BUTTON_GAP;
        int startY = (this.height - totalH) / 2;
        return startY + idx * (CATEGORY_BUTTON_H + CATEGORY_BUTTON_GAP);
    }

    private int squadButtonX() {
        return SIDE_X_MARGIN;
    }

    /**
     * 位置索引 → 队号（用于绘制 / 点击）：
     *   0 .. max-1  → 1 .. max
     *   max         → 0（全选，放在最下面）
     */
    private int squadIdAt(int positionIdx) {
        int max = maxSquads();
        return positionIdx < max ? positionIdx + 1 : 0;
    }

    private int squadButtonY(int idx) {
        int total = maxSquads() + 1;
        int totalH = total * SQUAD_BUTTON_H + (total - 1) * SQUAD_BUTTON_GAP;
        int startY = (this.height - totalH) / 2;
        return startY + idx * (SQUAD_BUTTON_H + SQUAD_BUTTON_GAP);
    }

    private int maxSquads() {
        try {
            int n = PlayerConfig.maxPlayerSquads;
            return n > 0 ? n : 5;
        } catch (Throwable t) {
            return 5;
        }
    }

    private int hitCategoryButton(double mx, double my) {
        for (int i = 0; i < Category.values().length; i++) {
            int x = categoryButtonX();
            int y = categoryButtonY(i);
            if (mx >= x && mx < x + CATEGORY_BUTTON_W
                    && my >= y && my < y + CATEGORY_BUTTON_H) return i;
        }
        return -1;
    }

    private int hitSquadButton(double mx, double my) {
        int total = maxSquads() + 1;
        for (int i = 0; i < total; i++) {
            int x = squadButtonX();
            int y = squadButtonY(i);
            if (mx >= x && mx < x + SQUAD_BUTTON_W
                    && my >= y && my < y + SQUAD_BUTTON_H) return i;
        }
        return -1;
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0x90000000);

        float cx = this.width / 2f;
        float cy = this.height / 2f;
        updateHovered(mouseX, mouseY, cx, cy);

        hoveredCategoryButton = hitCategoryButton(mouseX, mouseY);
        hoveredSquadButton = hitSquadButton(mouseX, mouseY);

        // 所有扇区几何合并到一次 BufferBuilder / drawWithShader，避免大量 draw call
        final int n = currentOptions().length;
        final Matrix4f pose = g.pose().last().pose();
        final Tesselator tess = Tesselator.getInstance();
        final BufferBuilder buf = tess.begin(
                VertexFormat.Mode.TRIANGLES,
                DefaultVertexFormat.POSITION_COLOR);

        // 1) 内侧多边形封底
        addInnerPolygonFill(buf, pose, cx, cy, INNER_FILL_COLOR, n);

        // 2) 扇区填充
        for (int i = 0; i < n; i++) {
            boolean hovered = (i == hoveredSector);
            int baseColor = currentOptions()[i].color();
            int rgb = hovered ? brighten(baseColor, 0.45f) : baseColor;
            int color = (FILL_ALPHA << 24) | (rgb & 0x00FFFFFF);
            addPolygonSector(buf, pose, cx, cy, i, color, n);
        }

        // 3) 扇区边框
        for (int i = 0; i < n; i++) {
            boolean hovered = (i == hoveredSector);
            float thick = hovered ? 2.5f : 1.5f;
            int color = hovered ? BORDER_HOVER : BORDER_IDLE;
            addPolygonSectorBorder(buf, pose, cx, cy, i, thick, color, n);
        }

        MeshData data = buf.build();
        if (data != null) BufferUploader.drawWithShader(data);

        // 4) 扇区标签
        for (int i = 0; i < n; i++) {
            float midA = (float) Math.toRadians(
                    i * (360f / n) - 90f + (360f / n) / 2f);
            float innerR = INNER_R + 8f;
            float outerR = OUTER_R * 0.90f;
            float r = (innerR + outerR) / 2f;
            float lx = cx + (float) Math.cos(midA) * r;
            float ly = cy + (float) Math.sin(midA) * r;

            Component label = currentOptions()[i].label();
            int tw = font.width(label);
            boolean hovered = (i == hoveredSector);
            int textColor = hovered ? 0xFFFFD060 : 0xFFFFFF;
            g.drawString(font, label,
                    (int) (lx - tw / 2f), (int) (ly - 4),
                    textColor, true);
        }

        // 5) 中心提示
        if (hoveredSector >= 0 && hoveredSector < n) {
            Component label = currentOptions()[hoveredSector].label();
            int tw = font.width(label);
            g.drawString(font, label,
                    (int) (cx - tw / 2f), (int) cy - 20,
                    0xFFFFAA00, true);

            Component squadLine = squadLine();
            g.drawString(font, squadLine,
                    (int) (cx - font.width(squadLine) / 2f), (int) cy - 6,
                    0xFFFFC040, true);

            Component tip = Component.translatable(LANG_PREFIX + "tip.hover");
            g.drawString(font, tip,
                    (int) (cx - font.width(tip) / 2f), (int) cy + 6,
                    0xAAAAAA, true);
        } else {
            Component title = Component.translatable(LANG_PREFIX + "title");
            g.drawString(font, title,
                    (int) (cx - font.width(title) / 2f), (int) cy - 20,
                    0xFFFFFF, true);

            Component squadLine = squadLine();
            g.drawString(font, squadLine,
                    (int) (cx - font.width(squadLine) / 2f), (int) cy - 6,
                    0xFFFFC040, true);

            Component tip = Component.translatable(
                    LANG_PREFIX + (clickedThisRound ? "tip.clicked" : "tip.default"));
            g.drawString(font, tip,
                    (int) (cx - font.width(tip) / 2f), (int) cy + 6,
                    0xAAAAAA, true);
        }

        // 6) 分类按钮
        drawCategoryButtons(g);

        // 7) 小分队按钮
        drawSquadButtons(g);
    }

    /** 返回"当前：全选所有小分队"或"当前：小分队 N"的显示文本。 */
    private static Component squadLine() {
        if (currentSquad == 0) {
            return Component.translatable(LANG_PREFIX + "squad.current_all");
        }
        return Component.translatable(LANG_PREFIX + "squad.current_n", currentSquad);
    }

    private void drawCategoryButtons(GuiGraphics g) {
        Category[] cats = Category.values();
        for (int i = 0; i < cats.length; i++) {
            int x = categoryButtonX();
            int y = categoryButtonY(i);
            boolean active = (cats[i] == currentCategory);
            boolean hovered = (i == hoveredCategoryButton);

            int bg = active ? COLOR_BUTTON_ACTIVE
                    : (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON_IDLE);
            g.fill(x, y, x + CATEGORY_BUTTON_W, y + CATEGORY_BUTTON_H, bg);
            g.fill(x, y, x + CATEGORY_BUTTON_W, y + 1, COLOR_BUTTON_BORDER);
            g.fill(x, y + CATEGORY_BUTTON_H - 1,
                    x + CATEGORY_BUTTON_W, y + CATEGORY_BUTTON_H, COLOR_BUTTON_BORDER);
            g.fill(x, y, x + 1, y + CATEGORY_BUTTON_H, COLOR_BUTTON_BORDER);
            g.fill(x + CATEGORY_BUTTON_W - 1, y,
                    x + CATEGORY_BUTTON_W, y + CATEGORY_BUTTON_H, COLOR_BUTTON_BORDER);

            Component label = cats[i].label();
            int tw = font.width(label);
            int textColor = active ? 0xFFFFF080 : COLOR_BUTTON_TEXT;
            g.drawString(font, label,
                    x + (CATEGORY_BUTTON_W - tw) / 2,
                    y + (CATEGORY_BUTTON_H - 8) / 2,
                    textColor, true);
        }
    }

    private void drawSquadButtons(GuiGraphics g) {
        int total = maxSquads() + 1;
        for (int i = 0; i < total; i++) {
            int x = squadButtonX();
            int y = squadButtonY(i);
            int squadId = squadIdAt(i);             // 位置索引 → 队号
            boolean active = (squadId == currentSquad);
            boolean hovered = (i == hoveredSquadButton);

            int bg = active ? COLOR_BUTTON_ACTIVE
                    : (hovered ? COLOR_BUTTON_HOVER : COLOR_BUTTON_IDLE);
            g.fill(x, y, x + SQUAD_BUTTON_W, y + SQUAD_BUTTON_H, bg);
            g.fill(x, y, x + SQUAD_BUTTON_W, y + 1, COLOR_BUTTON_BORDER);
            g.fill(x, y + SQUAD_BUTTON_H - 1,
                    x + SQUAD_BUTTON_W, y + SQUAD_BUTTON_H, COLOR_BUTTON_BORDER);
            g.fill(x, y, x + 1, y + SQUAD_BUTTON_H, COLOR_BUTTON_BORDER);
            g.fill(x + SQUAD_BUTTON_W - 1, y,
                    x + SQUAD_BUTTON_W, y + SQUAD_BUTTON_H, COLOR_BUTTON_BORDER);

            Component label = (squadId == 0)
                    ? Component.translatable(LANG_PREFIX + "squad.all")
                    : Component.translatable(LANG_PREFIX + "squad.n", squadId);
            int tw = font.width(label);
            int textColor = active ? 0xFFFFF080 : COLOR_BUTTON_TEXT;
            g.drawString(font, label,
                    x + (SQUAD_BUTTON_W - tw) / 2,
                    y + (SQUAD_BUTTON_H - 8) / 2,
                    textColor, true);
        }
    }

    // ==================== 轮盘几何 ====================

    private int sectorCount() {
        return currentOptions().length;
    }

    private float[] innerVertex(int idx, float cx, float cy) {
        int n = sectorCount();
        float a = (float) Math.toRadians((idx % n) * (360f / n) - 90f);
        return new float[]{
                cx + (float) Math.cos(a) * INNER_R,
                cy + (float) Math.sin(a) * INNER_R
        };
    }

    private float[] outerVertex(int idx, float cx, float cy) {
        int n = sectorCount();
        float a = (float) Math.toRadians((idx % n) * (360f / n) - 90f);
        return new float[]{
                cx + (float) Math.cos(a) * OUTER_R,
                cy + (float) Math.sin(a) * OUTER_R
        };
    }

    /**
     * 内侧多边形封底：n 个扇形三角形（中心 + 相邻两个内圈顶点）。
     *   合并进同一个 TRIANGLES BufferBuilder。
     */
    private void addInnerPolygonFill(VertexConsumer buf, Matrix4f pose,
                                     float cx, float cy, int color, int n) {
        for (int i = 0; i < n; i++) {
            float[] v1 = innerVertex(i, cx, cy);
            float[] v2 = innerVertex(i + 1, cx, cy);
            addTriangle(buf, pose, cx, cy, v1[0], v1[1], v2[0], v2[1], color);
        }
    }

    /**
     * 扇区填充：四边形 i1-o1-o2-i2 拆成两个三角形。
     */
    private void addPolygonSector(VertexConsumer buf, Matrix4f pose,
                                  float cx, float cy, int i, int color, int n) {
        float[] i1 = innerVertex(i, cx, cy);
        float[] i2 = innerVertex(i + 1, cx, cy);
        float[] o1 = outerVertex(i, cx, cy);
        float[] o2 = outerVertex(i + 1, cx, cy);
        addTriangle(buf, pose, i1[0], i1[1], o1[0], o1[1], o2[0], o2[1], color);
        addTriangle(buf, pose, i1[0], i1[1], o2[0], o2[1], i2[0], i2[1], color);
    }

    /**
     * 扇区边框：外弧 / 内弧 / 一条径向线，各一条线段（由 quad 拆成两个三角形）。
     */
    private void addPolygonSectorBorder(VertexConsumer buf, Matrix4f pose,
                                        float cx, float cy, int i,
                                        float thickness, int color, int n) {
        float[] i1 = innerVertex(i, cx, cy);
        float[] i2 = innerVertex(i + 1, cx, cy);
        float[] o1 = outerVertex(i, cx, cy);
        float[] o2 = outerVertex(i + 1, cx, cy);

        addLineSegment(buf, pose, o1[0], o1[1], o2[0], o2[1], thickness, color);
        addLineSegment(buf, pose, i1[0], i1[1], i2[0], i2[1], thickness, color);
        addLineSegment(buf, pose, i1[0], i1[1], o1[0], o1[1], thickness, color);
    }

    private static void addLineSegment(VertexConsumer buf, Matrix4f pose,
                                       float x1, float y1, float x2, float y2,
                                       float thickness, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 0.001f) return;
        float nx = -dy / len * thickness * 0.5f;
        float ny = dx / len * thickness * 0.5f;

        addQuadAsTriangles(buf, pose,
                x1 + nx, y1 + ny,
                x2 + nx, y2 + ny,
                x2 - nx, y2 - ny,
                x1 - nx, y1 - ny,
                color);
    }

    private static void addTriangle(VertexConsumer buf, Matrix4f pose,
                                    float x1, float y1,
                                    float x2, float y2,
                                    float x3, float y3, int color) {
        buf.addVertex(pose, x1, y1, 0).setColor(color);
        buf.addVertex(pose, x2, y2, 0).setColor(color);
        buf.addVertex(pose, x3, y3, 0).setColor(color);
    }

    private static void addQuadAsTriangles(VertexConsumer buf, Matrix4f pose,
                                           float x1, float y1,
                                           float x2, float y2,
                                           float x3, float y3,
                                           float x4, float y4, int color) {
        // 两个三角形：1-2-3 与 1-3-4
        addTriangle(buf, pose, x1, y1, x2, y2, x3, y3, color);
        addTriangle(buf, pose, x1, y1, x3, y3, x4, y4, color);
    }

    private void updateHovered(double mx, double my, float cx, float cy) {
        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        int n = sectorCount();
        double innerMin = INNER_R * Math.cos(Math.PI / n);
        if (dist < innerMin) { hoveredSector = -1; return; }
        if (dist > OUTER_R) { hoveredSector = -1; return; }

        double ang = Math.toDegrees(Math.atan2(dy, dx)) + 90.0;
        if (ang < 0) ang += 360.0;
        hoveredSector = (int) (ang / (360.0 / n)) % n;
    }

    private static int brighten(int argb, float amount) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.min(255, (int) (r + (255 - r) * amount));
        g = Math.min(255, (int) (g + (255 - g) * amount));
        b = Math.min(255, (int) (b + (255 - b) * amount));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void init() {
        super.init();
        // 打开轮盘时从服务器 state 刷新当前小分队（通过 static 缓存）
        // 服务端中键选中小分队时已通过 SyncSquadPayload 更新 currentSquad
    }
}
