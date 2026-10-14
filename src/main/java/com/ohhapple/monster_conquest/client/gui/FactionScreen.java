package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.army.Faction;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.network.serverbound.faction.DeleteFactionPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.ToggleFactionAllyPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.ToggleFactionHostilityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class FactionScreen extends Screen {

    private static final int PADDING = 8;
    private static final int ROW_HEIGHT = 18;
    /** 列表滚动条宽度 / 距列表右边距。 */
    private static final int SCROLLBAR_W = 3;
    private static final int SCROLLBAR_GAP = 1;

    /** 滚动条几何（trackY/trackH = 轨道，y/h = 滑块）。 */
    private record Bar(int x, int trackY, int trackH, int y, int h, int max) {}

    /** 正在拖动的滚动条：0 = 无，1 = 左侧列表，2 = 右侧列表。 */
    private int draggingBar = 0;
    private double dragOffset = 0;

    private Map<String, Set<String>> hostility;
    private Map<String, Set<String>> ally;
    /** false = 编辑敌对；true = 编辑结盟 */
    private boolean allyMode = false;

    private final List<ResourceLocation> allFactions = new ArrayList<>();
    private final List<ResourceLocation> filtered = new ArrayList<>();
    private final List<ResourceLocation> rightFiltered = new ArrayList<>();

    private ResourceLocation selected;
    private int scroll = 0;
    private int rightScroll = 0;

    private EditBox searchBox;
    private EditBox rightSearchBox;
    private Button deleteButton;
    private Button modeButton;

    private ResourceLocation myFaction;

    public FactionScreen(Map<String, Set<String>> hostility, Map<String, Set<String>> ally) {
        super(Component.translatable("gui.monster_conquest.faction.title"));
        this.hostility = hostility;
        this.ally = ally;
        refreshMyFaction();
        rebuildFactionList();
    }

    public void updateHostility(Map<String, Set<String>> newHostility) {
        this.hostility = newHostility;
        refreshMyFaction();
        rebuildFactionList();
        applyRightSearch(rightSearchBox == null ? "" : rightSearchBox.getValue());
    }

    public void updateAlly(Map<String, Set<String>> newAlly) {
        this.ally = newAlly;
        applyRightSearch(rightSearchBox == null ? "" : rightSearchBox.getValue());
    }

    private void refreshMyFaction() {
        Minecraft mc = Minecraft.getInstance();
        myFaction = mc.player != null
                ? PlayerFactionRegistry.factionIdOf(mc.player.getUUID())
                : null;
    }

    private void rebuildFactionList() {
        allFactions.clear();
        Set<ResourceLocation> seen = new HashSet<>();

        for (String s : hostility.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id == null) continue;
            if (FactionIds.isBigPlayer(id)) continue;
            if (seen.add(id)) allFactions.add(id);
        }
        for (String s : ally.keySet()) {
            ResourceLocation id = ResourceLocation.tryParse(s);
            if (id == null) continue;
            if (FactionIds.isBigPlayer(id)) continue;
            if (seen.add(id)) allFactions.add(id);
        }
        for (Faction f : FactionRegistry.all()) {
            ResourceLocation id = f.getId();
            if (FactionIds.isBigPlayer(id)) continue;
            if (seen.add(id)) allFactions.add(id);
        }
        if (myFaction != null && seen.add(myFaction)) allFactions.add(myFaction);

        final ResourceLocation mine = myFaction;
        allFactions.sort((a, b) -> {
            boolean aMine = a.equals(mine);
            boolean bMine = b.equals(mine);
            if (aMine != bMine) return aMine ? -1 : 1;
            boolean aBig = FactionIds.isBigPlayer(a);
            boolean bBig = FactionIds.isBigPlayer(b);
            if (aBig != bBig) return aBig ? -1 : 1;
            return a.toString().compareTo(b.toString());
        });

        filtered.clear();
        filtered.addAll(allFactions);
        if (!filtered.isEmpty() && (selected == null || !filtered.contains(selected))) {
            selected = filtered.get(0);
        }
        applyRightSearch(rightSearchBox == null ? "" : rightSearchBox.getValue());
    }

    @Override
    protected void init() {
        super.init();
        refreshMyFaction();

        searchBox = new EditBox(this.font, PADDING, 24, 160, 16,
                Component.translatable("gui.monster_conquest.faction.search"));
        searchBox.setHint(Component.translatable("gui.monster_conquest.faction.search"));
        searchBox.setResponder(this::onSearch);
        addRenderableWidget(searchBox);

        int detailX = PADDING + 160 + 16;
        int searchW = Math.max(80, this.width - detailX - PADDING - 4);
        rightSearchBox = new EditBox(this.font, detailX, 24, searchW, 16,
                Component.translatable("gui.monster_conquest.faction.enemy_search"));
        rightSearchBox.setHint(Component.translatable("gui.monster_conquest.faction.enemy_search"));
        rightSearchBox.setResponder(this::applyRightSearch);
        addRenderableWidget(rightSearchBox);

        modeButton = Button.builder(modeButtonLabel(), b -> {
                    allyMode = !allyMode;
                    b.setMessage(modeButtonLabel());
                })
                .bounds(this.width - 240, this.height - 24, 76, 18)
                .build();
        addRenderableWidget(modeButton);

        deleteButton = Button.builder(
                        Component.translatable("gui.monster_conquest.faction.delete_button"),
                        b -> onDeleteClicked())
                .bounds(this.width - 160, this.height - 24, 76, 18)
                .build();
        addRenderableWidget(deleteButton);

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width - 80, this.height - 24, 72, 18).build());

        rebuildFactionList();
        updateDeleteButtonVisibility();
    }

    private Component modeButtonLabel() {
        return Component.translatable(allyMode
                ? "gui.monster_conquest.faction.mode.ally"
                : "gui.monster_conquest.faction.mode.hostile");
    }

    private void onSearch(String keyword) {
        filtered.clear();
        String kw = keyword.toLowerCase();
        for (ResourceLocation id : allFactions) {
            if (matchesKeyword(id, kw)) filtered.add(id);
        }
        scroll = 0;
        if (selected == null || !filtered.contains(selected)) {
            selected = filtered.isEmpty() ? null : filtered.get(0);
        }
    }

    private boolean matchesKeyword(ResourceLocation id, String kw) {
        if (kw == null || kw.isEmpty()) return true;
        return id.toString().toLowerCase().contains(kw)
                || factionDisplayName(id).toLowerCase().contains(kw);
    }

    private void applyRightSearch(String keyword) {
        rightFiltered.clear();
        String kw = keyword == null ? "" : keyword.toLowerCase();

        Minecraft mc = Minecraft.getInstance();
        boolean canSeeAllPlayers = mc.player != null
                && mc.player.hasPermissions(2)
                && mc.player.isCreative();

        boolean viewingNonPlayer = selected != null
                && !FactionIds.isAnyPlayerFaction(selected);

        if (viewingNonPlayer) {
            if (matchesKeyword(FactionIds.PLAYER, kw)) {
                rightFiltered.add(FactionIds.PLAYER);
            }
        }
        for (ResourceLocation id : allFactions) {
            if (selected != null && id.equals(selected)) continue;
            if (FactionIds.isBigPlayer(id)) continue;
            if (FactionIds.isPlayer(id)) {
                boolean isMe = myFaction != null && id.equals(myFaction);
                if (!isMe && !canSeeAllPlayers) continue;
            }
            if (matchesKeyword(id, kw)) rightFiltered.add(id);
        }

        final ResourceLocation mine = myFaction;
        rightFiltered.sort((a, b) -> {
            boolean aMine = a.equals(mine);
            boolean bMine = b.equals(mine);
            if (aMine != bMine) return aMine ? -1 : 1;
            boolean aBig = FactionIds.isBigPlayer(a);
            boolean bBig = FactionIds.isBigPlayer(b);
            if (aBig != bBig) return aBig ? -1 : 1;
            return a.toString().compareTo(b.toString());
        });
        rightScroll = 0;
    }

    private void updateDeleteButtonVisibility() {
        if (deleteButton == null) return;
        Minecraft mc = Minecraft.getInstance();
        boolean canDelete = mc.player != null
                && mc.player.hasPermissions(2)
                && mc.player.isCreative()
                && selected != null
                && FactionIds.isPlayer(selected);
        deleteButton.visible = canDelete;
        deleteButton.active = canDelete;
    }

    private void onDeleteClicked() {
        if (selected == null) return;
        PacketDistributor.sendToServer(new DeleteFactionPayload(selected.toString()));
    }

    /**
     * 是否使用可点击的编辑面板。
     *   普通玩家在 NPC 阵营页面也能编辑，但只能改与自己玩家阵营相关的关系（见 canToggleRow）。
     */
    private boolean canEdit(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.player.hasPermissions(2) && mc.player.isCreative()) return true;
        if (id.equals(PlayerFactionRegistry.factionIdOf(mc.player.getUUID()))) return true;
        return !FactionIds.isAnyPlayerFaction(id);
    }

    /** 当前页面下某一行是否真的能切换（不可切换的行只展示状态，点击由服务端拒绝并提示）。 */
    private boolean canToggleRow(ResourceLocation page, ResourceLocation row) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        if (mc.player.hasPermissions(2) && mc.player.isCreative()) return true;
        ResourceLocation own = PlayerFactionRegistry.factionIdOf(mc.player.getUUID());
        if (FactionIds.isBigPlayer(row)) {
            // "所有玩家阵营"行：在 NPC 阵营页面点击 = 与自己的玩家阵营建立关系（服务端会转换）；
            //   在自己 / 其他玩家阵营页面没有意义。
            return !FactionIds.isAnyPlayerFaction(page) && !page.equals(own);
        }
        return page.equals(own) || row.equals(own);
    }

    private boolean isOpCreative() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.hasPermissions(2) && mc.player.isCreative();
    }

    private String factionDisplayName(ResourceLocation id) {
        if (FactionIds.isBigPlayer(id)) {
            return Component.translatable("faction.monster_conquest.player_all").getString();
        }
        if (FactionIds.isPlayer(id)) {
            UUID owner = FactionIds.parsePlayer(id);
            if (owner != null) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.getUUID().equals(owner)) {
                    return Component.translatable("faction.monster_conquest.player_self").getString();
                }
                if (mc.getConnection() != null) {
                    PlayerInfo info = mc.getConnection().getPlayerInfo(owner);
                    if (info != null && info.getProfile() != null) {
                        return info.getProfile().getName();
                    }
                }
                String s = owner.toString();
                return "player/" + s.substring(0, 8);
            }
        }
        String key = "faction.monster_conquest." + id.getPath();
        Component t = Component.translatable(key);
        String raw = t.getString();
        if (!raw.equals(key)) return raw;
        return id.toString();
    }

    private boolean factionHatesMyFaction(ResourceLocation faction, ResourceLocation my) {
        if (my == null || faction == null || faction.equals(my)) return false;
        Set<String> h = hostility.get(faction.toString());
        if (h == null) return false;
        if (h.contains(my.toString())) return true;
        if (h.contains(FactionIds.PLAYER.toString())) return true;
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);

        updateDeleteButtonVisibility();
        refreshMyFaction();

        g.drawString(font, title, PADDING, 8, 0xFFFFFF);

        int listX = PADDING;
        int listY = 46;
        int listW = 160;
        int listH = this.height - listY - 32;

        g.fill(listX - 1, listY - 1, listX + listW + 1, listY + listH + 1, 0x80000000);

        int visibleRows = listH / ROW_HEIGHT;
        int startIdx = Math.min(scroll, Math.max(0, filtered.size() - visibleRows));
        for (int i = 0; i < visibleRows && startIdx + i < filtered.size(); i++) {
            ResourceLocation id = filtered.get(startIdx + i);
            int rowY = listY + i * ROW_HEIGHT;
            boolean hovered = mouseX >= listX && mouseX < listX + listW
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            boolean sel = id.equals(selected);
            boolean hatesMe = !allyMode && factionHatesMyFaction(id, myFaction);

            int bg;
            if (sel) bg = 0xC060A0FF;
            else if (hatesMe) bg = 0x80FF4040;
            else if (hovered) bg = 0x80808080;
            else bg = 0;

            if (bg != 0) g.fill(listX, rowY, listX + listW, rowY + ROW_HEIGHT, bg);

            int textColor = sel ? 0xFFFFFF : (hatesMe ? 0xFFFF8080 : 0xE0E0E0);
            g.drawString(font, factionDisplayName(id), listX + 4, rowY + 5, textColor);
        }
        drawListBar(g, leftBar());

        int detailX = listX + listW + 16;
        int detailY = listY;
        int detailW = this.width - detailX - PADDING;
        int detailH = this.height - detailY - 32;
        g.fill(detailX - 1, detailY - 1, detailX + detailW + 1, detailY + detailH + 1, 0x80000000);

        if (selected == null) {
            g.drawString(font,
                    Component.translatable("gui.monster_conquest.faction.empty"),
                    detailX + 4, detailY + 4, 0xA0A0A0);
            return;
        }

        g.drawString(font, Component.literal(factionDisplayName(selected)),
                detailX + 4, detailY + 4, 0xFFFFFF);

        // 模式提示（普通玩家在 NPC 阵营页面是"只能改与自己相关的关系"的受限编辑）
        boolean limitedEdit = !isOpCreative()
                && !FactionIds.isAnyPlayerFaction(selected);
        g.drawString(font, Component.translatable(limitedEdit
                        ? "gui.monster_conquest.faction.subtitle.limited"
                        : (allyMode
                                ? "gui.monster_conquest.faction.subtitle.ally"
                                : "gui.monster_conquest.faction.subtitle.hostile")),
                detailX + 4, detailY + 18,
                limitedEdit ? 0xFFD070 : (allyMode ? 0x80FF80 : 0xFF8080));

        boolean canEdit = canEdit(selected);

        // 根据模式取当前集合
        Set<String> activeSet = allyMode
                ? ally.getOrDefault(selected.toString(), Set.of())
                : hostility.getOrDefault(selected.toString(), Set.of());
        boolean toggledAllPlayers = activeSet.contains(FactionIds.PLAYER.toString());

        if (canEdit) {
            int rowY = detailY + 36;
            int listStartY = rowY;
            int listEndY = detailY + detailH;
            int visibleR = Math.max(0, (listEndY - listStartY) / ROW_HEIGHT);
            int startR = Math.min(rightScroll, Math.max(0, rightFiltered.size() - visibleR));

            for (int i = 0; i < visibleR && startR + i < rightFiltered.size(); i++) {
                ResourceLocation id = rightFiltered.get(startR + i);
                int y = listStartY + i * ROW_HEIGHT;

                boolean stored = activeSet.contains(id.toString());
                boolean marked;
                if (FactionIds.isBigPlayer(id)) {
                    marked = toggledAllPlayers;
                } else if (FactionIds.isPlayer(id)) {
                    marked = toggledAllPlayers || stored;
                } else {
                    marked = stored;
                }

                // 隐式敌对：对方把这边的（小玩家）阵营列进了它的敌对名单（例如配置里"某阵营仇视玩家"），
                //   实际已经互相敌对、但本侧没显式设置过 —— 淡红标出来，点一下就会显式写下来。
                boolean inherited = !marked && !allyMode && FactionIds.isPlayer(selected)
                        && hostility.getOrDefault(id.toString(), Set.of())
                                .contains(FactionIds.PLAYER.toString());

                boolean rowToggle = canToggleRow(selected, id);
                boolean hovered = rowToggle
                        && mouseX >= detailX && mouseX < detailX + detailW
                        && mouseY >= y && mouseY < y + ROW_HEIGHT;

                // 根据模式显示不同颜色：敌对红 / 结盟绿
                int bgColor;
                int textColor;
                if (rowToggle) {
                    if (marked) {
                        bgColor = allyMode ? 0x8040C040 : 0x80FF4040;
                    } else if (inherited) {
                        bgColor = 0x40FF6060;
                    } else {
                        bgColor = hovered ? 0x80808080 : 0x40000000;
                    }
                    textColor = marked ? 0xFFFFFF : (inherited ? 0xFFD0D0 : 0xC0C0C0);
                } else {
                    // 受限编辑下不可切换的行：同样显示状态，但整体调暗表示点不了
                    bgColor = marked ? (allyMode ? 0x4040C040 : 0x40FF4040) : 0x30000000;
                    textColor = 0x8A8A8A;
                }
                g.fill(detailX, y, detailX + detailW, y + ROW_HEIGHT, bgColor);
                g.drawString(font, factionDisplayName(id), detailX + 4, y + 5, textColor);
            }
            drawListBar(g, rightBar());
        } else {
            List<String> list = new ArrayList<>(activeSet);
            list.sort(String::compareTo);
            int rowY = detailY + 42;
            if (list.isEmpty()) {
                g.drawString(font,
                        Component.translatable("gui.monster_conquest.faction.none"),
                        detailX + 4, rowY + 5, 0xA0A0A0);
            } else {
                for (String s : list) {
                    if (rowY + ROW_HEIGHT > detailY + detailH) break;
                    g.drawString(font, Component.literal(displayFactionIdString(s)),
                            detailX + 4, rowY + 5, 0xE0E0E0);
                    rowY += ROW_HEIGHT;
                }
            }
        }
    }

    private String displayFactionIdString(String s) {
        ResourceLocation rl = ResourceLocation.tryParse(s);
        if (rl == null) return s;
        return factionDisplayName(rl);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;

        // 滚动条：点滑块直接拖，点轨道跳到该位置
        if (button == 0) {
            int hit = hitListBar(mx, my);
            if (hit != 0) {
                Bar bar = hit == 1 ? leftBar() : rightBar();
                if (bar != null) {
                    if (my >= bar.y() && my <= bar.y() + bar.h()) {
                        dragOffset = my - bar.y();
                    } else {
                        dragOffset = bar.h() / 2.0;
                        applyBarDrag(hit, my);
                    }
                    draggingBar = hit;
                    return true;
                }
            }
        }

        int listX = PADDING;
        int listY = 46;
        int listW = 160;
        int listH = this.height - listY - 32;

        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int visibleRows = listH / ROW_HEIGHT;
            int startIdx = Math.min(scroll, Math.max(0, filtered.size() - visibleRows));
            int idx = startIdx + (int) ((my - listY) / ROW_HEIGHT);
            if (idx >= 0 && idx < filtered.size()) {
                selected = filtered.get(idx);
                applyRightSearch(rightSearchBox == null ? "" : rightSearchBox.getValue());
                return true;
            }
        }

        if (selected == null || !canEdit(selected)) return false;

        int detailX = listX + listW + 16;
        int detailY = listY;
        int detailW = this.width - detailX - PADDING;
        int detailH = this.height - detailY - 32;

        int listStartY = detailY + 36;
        int listEndY = detailY + detailH;
        int visibleR = Math.max(0, (listEndY - listStartY) / ROW_HEIGHT);
        int startR = Math.min(rightScroll, Math.max(0, rightFiltered.size() - visibleR));

        if (mx >= detailX && mx < detailX + detailW && my >= listStartY && my < listEndY) {
            int idxInView = (int) ((my - listStartY) / ROW_HEIGHT);
            int idx = startR + idxInView;
            if (idx >= 0 && idx < rightFiltered.size()) {
                ResourceLocation id = rightFiltered.get(idx);

                String key;
                if (FactionIds.isBigPlayer(id)) key = FactionIds.PLAYER.toString();
                else key = id.toString();

                // 不在本地修改集合：等服务端 sendSnapshot 回包后，
                //   由 FactionGuiClientEvents.handleSnapshot / handleAllySnapshot
                //   通过 updateHostility / updateAlly 刷新界面，
                //   避免服务端拒绝时本地"闪一下又回来"的错乱。
                if (allyMode) {
                    PacketDistributor.sendToServer(new ToggleFactionAllyPayload(
                            selected.toString(), key));
                } else {
                    PacketDistributor.sendToServer(new ToggleFactionHostilityPayload(
                            selected.toString(), key));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int listX = PADDING;
        int listY = 46;
        int listW = 160;
        int listH = this.height - listY - 32;

        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            int visibleRows = listH / ROW_HEIGHT;
            int max = Math.max(0, filtered.size() - visibleRows);
            scroll = Math.max(0, Math.min(max, scroll - (int) Math.signum(dy)));
            return true;
        }
        int detailX = listX + listW + 16;
        int detailY = listY;
        int detailH = this.height - detailY - 32;
        if (mx >= detailX && mx < this.width - PADDING && my >= detailY + 36 && my < detailY + detailH) {
            int visibleR = Math.max(0, (detailH - 36) / ROW_HEIGHT);
            int max = Math.max(0, rightFiltered.size() - visibleR);
            rightScroll = Math.max(0, Math.min(max, rightScroll - (int) Math.signum(dy)));
            return true;
        }
        return false;
    }

    // ===================== 列表滚动条（可拖动） =====================

    @Nullable
    private Bar leftBar() {
        int listY = 46;
        int listH = this.height - listY - 32;
        int visibleRows = listH / ROW_HEIGHT;
        int max = Math.max(0, filtered.size() - visibleRows);
        if (max <= 0) return null;
        int trackY = listY;
        int trackH = visibleRows * ROW_HEIGHT;
        int h = Math.max(12, trackH * visibleRows / Math.max(1, filtered.size()));
        int y = trackY + (trackH - h) * Math.max(0, Math.min(max, scroll)) / max;
        int x = PADDING + 160 - SCROLLBAR_GAP - SCROLLBAR_W;
        return new Bar(x, trackY, trackH, y, h, max);
    }

    @Nullable
    private Bar rightBar() {
        int listY = 46;
        int listW = 160;
        int detailX = PADDING + listW + 16;
        int detailW = this.width - detailX - PADDING;
        int detailH = this.height - listY - 32;
        int listStartY = listY + 36;
        int trackH = Math.max(0, (detailH - 36) / ROW_HEIGHT * ROW_HEIGHT);
        int visibleR = trackH / ROW_HEIGHT;
        int max = Math.max(0, rightFiltered.size() - visibleR);
        if (max <= 0 || trackH <= 0) return null;
        int h = Math.max(12, trackH * visibleR / Math.max(1, rightFiltered.size()));
        int y = listStartY + (trackH - h) * Math.max(0, Math.min(max, rightScroll)) / max;
        int x = detailX + detailW - SCROLLBAR_GAP - SCROLLBAR_W;
        return new Bar(x, listStartY, trackH, y, h, max);
    }

    private void drawListBar(GuiGraphics g, @Nullable Bar bar) {
        if (bar == null) return;
        g.fill(bar.x(), bar.trackY(), bar.x() + SCROLLBAR_W, bar.trackY() + bar.trackH(), 0x40000000);
        g.fill(bar.x() - 1, bar.y(), bar.x() + SCROLLBAR_W + 1, bar.y() + bar.h(), 0x90FFFFFF);
    }

    /** 返回命中的滚动条：1 = 左，2 = 右；0 = 没命中。 */
    private int hitListBar(double mx, double my) {
        Bar left = leftBar();
        if (left != null && mx >= left.x() - 3 && mx <= left.x() + SCROLLBAR_W + 3
                && my >= left.trackY() && my <= left.trackY() + left.trackH()) {
            return 1;
        }
        Bar right = rightBar();
        if (right != null && mx >= right.x() - 3 && mx <= right.x() + SCROLLBAR_W + 3
                && my >= right.trackY() && my <= right.trackY() + right.trackH()) {
            return 2;
        }
        return 0;
    }

    private void applyBarDrag(int which, double mouseY) {
        Bar bar = which == 1 ? leftBar() : rightBar();
        if (bar == null) return;
        int track = Math.max(1, bar.trackH() - bar.h());
        double y = Mth.clamp(mouseY - dragOffset, bar.trackY(), bar.trackY() + track);
        int value = (int) Math.round((y - bar.trackY()) * bar.max() / (double) track);
        if (which == 1) scroll = Math.max(0, Math.min(bar.max(), value));
        else rightScroll = Math.max(0, Math.min(bar.max(), value));
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (draggingBar != 0) {
            applyBarDrag(draggingBar, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingBar != 0) {
            draggingBar = 0;
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
