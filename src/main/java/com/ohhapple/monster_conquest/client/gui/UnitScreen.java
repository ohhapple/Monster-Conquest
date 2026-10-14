package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import com.ohhapple.monster_conquest.menu.UnitMenu;
import com.ohhapple.monster_conquest.network.serverbound.unit.UnitControlPayload;
import com.ohhapple.monster_conquest.network.serverbound.unit.UnitRetrievePayload;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.FactionDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 单位界面（士兵 / 首领通用）：
 *   · 左侧 3D 预览（跟随鼠标转动），预览右边是状态（生命 / 护甲 / 阵营 / 小队 / 形态 / 状态效果）；
 *   · 左侧下方是四套策略下拉框（行动 / 攻击 / 阵型 / 骑乘）——改的只是"被打开界面的这一个单位"，
 *     不会波及小队 / 全军；只有玩家阵营士兵才显示这组按钮，首领直接显示装备管理；
 *   · 右侧是手动装备 6 格（每格单独标注头盔 / 胸甲 / 护腿 / 靴子 / 主手 / 副手）、
 *     战术装备 18 格（6 × 3）与玩家背包。
 */
public class UnitScreen extends AbstractContainerScreen<UnitMenu> {

    private static final int PANEL_W = 360;
    private static final int PANEL_H = 236;

    /** 策略下拉框（两列两行）。 */
    private static final int DROPDOWN_W = 84;
    private static final int DROPDOWN_LABEL_DY = -10;
    private final List<Dropdown> dropdowns = new ArrayList<>();
    private Button followArmyButton;
    private Button retrieveButton;
    /** 效果行文字的屏幕 y（绝对坐标；-1 = 这一帧没画）。 */
    private int effectLineY = -1;

    public UnitScreen(UnitMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        dropdowns.clear();

        dropdowns.add(new Dropdown(8, 110, DROPDOWN_W, UnitMenu.DATA_MOVEMENT,
                UnitControlPayload.ACTION_MOVEMENT,
                Component.translatable("gui.monster_conquest.unit.movement"),
                List.of(
                        new Option(PlayerCommandState.MovementMode.FOLLOW.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.follow_me")),
                        new Option(PlayerCommandState.MovementMode.SOFT_FOLLOW.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.soft_follow")),
                        new Option(PlayerCommandState.MovementMode.STATION.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.hold")),
                        new Option(PlayerCommandState.MovementMode.FREE.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.free_move")))));

        dropdowns.add(new Dropdown(100, 110, DROPDOWN_W, UnitMenu.DATA_ATTACK,
                UnitControlPayload.ACTION_ATTACK,
                Component.translatable("gui.monster_conquest.unit.attack"),
                List.of(
                        new Option(PlayerCommandState.AttackMode.AUTONOMOUS.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.autonomous")),
                        new Option(PlayerCommandState.AttackMode.FREE_AGGRESSIVE.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.free_aggressive")),
                        new Option(PlayerCommandState.AttackMode.HOLD_GROUND.getId(),
                                Component.translatable("gui.monster_conquest.unit.attack.hold_ground")),
                        new Option(PlayerCommandState.AttackMode.STOP.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.stop_attack")))));

        dropdowns.add(new Dropdown(8, 144, DROPDOWN_W, UnitMenu.DATA_FORMATION,
                UnitControlPayload.ACTION_FORMATION,
                Component.translatable("gui.monster_conquest.unit.formation"),
                List.of(
                        new Option(PlayerCommandState.FormationMode.DEFAULT.getId(),
                                Component.translatable("gui.monster_conquest.unit.formation.default")),
                        new Option(PlayerCommandState.FormationMode.LINE.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.formation.line")),
                        new Option(PlayerCommandState.FormationMode.WEDGE.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.formation.wedge")),
                        new Option(PlayerCommandState.FormationMode.SHIELD_WALL.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.formation.shield_wall")),
                        new Option(PlayerCommandState.FormationMode.SKIRMISH.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.formation.skirmish")),
                        new Option(PlayerCommandState.FormationMode.FREE.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.formation.free")))));

        dropdowns.add(new Dropdown(100, 144, DROPDOWN_W, UnitMenu.DATA_RIDE,
                UnitControlPayload.ACTION_RIDE,
                Component.translatable("gui.monster_conquest.unit.ride"),
                List.of(
                        new Option(PlayerCommandState.RideMode.AUTO.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.ride_default")),
                        new Option(PlayerCommandState.RideMode.FORCED_CAVALRY.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.ride_cavalry")),
                        new Option(PlayerCommandState.RideMode.FORCED_INFANTRY.getId(),
                                Component.translatable("gui.monster_conquest.command_wheel.action.ride_infantry")))));

        // "解除个体策略"：把被单独调过的单位交还给小队 / 全军指令（没被调过时置灰）
        followArmyButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.unit.follow_army"),
                        b -> PacketDistributor.sendToServer(new UnitControlPayload(
                                menu.getUnitId(), UnitControlPayload.ACTION_FOLLOW_ARMY, 0)))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.monster_conquest.unit.follow_army.tip")))
                .bounds(leftPos + 8, topPos + 170, 84, 18).build());

        // "收纳该单位"：和收纳工具同一条逻辑（生存玩家只能收自己的单位、背包要有空位）
        retrieveButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.monster_conquest.unit.retrieve"),
                        b -> PacketDistributor.sendToServer(new UnitRetrievePayload(menu.getUnitId())))
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.monster_conquest.unit.retrieve.tip")))
                .bounds(leftPos + 100, topPos + 170, 84, 18).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean strategy = menu.canConfigureStrategy();
        followArmyButton.visible = strategy;
        followArmyButton.active = menu.getSync(UnitMenu.DATA_OVERRIDE) == 1;
        // 没有策略按钮的单位（首领等）把"收纳"挪到左半边，避免留下大片空白
        retrieveButton.setPosition(leftPos + (strategy ? 100 : 8), topPos + 170);
        super.render(g, mouseX, mouseY, partialTick);
        Dropdown hovering = null;
        for (Dropdown dropdown : dropdowns) {
            dropdown.renderPopup(g, this.font, mouseX, mouseY);
        }
        for (Dropdown dropdown : dropdowns) {
            if (dropdown.isMouseOverPopup(mouseX, mouseY)) hovering = dropdown;
        }
        // 鼠标在展开的选项列表上时不显示物品提示，避免提示盖住下拉框
        if (hovering != null) return;
        if (isHoveringEffectLine(mouseX, mouseY)) {
            List<Component> lines = effectTooltipLines();
            if (!lines.isEmpty()) {
                g.renderComponentTooltip(this.font, lines, mouseX, mouseY);
                return;
            }
        }
        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // 标题 / 背包标签都自己画（布局不是原版模板）
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (menu.canConfigureStrategy()) {
            for (Dropdown dropdown : dropdowns) {
                if (dropdown.mouseClicked(mouseX, mouseY, button)) return true;
            }
        } else {
            for (Dropdown dropdown : dropdowns) dropdown.close();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFFC6C6C6);
        g.fill(x, y, x + imageWidth, y + 1, 0xFFFFFFFF);
        g.fill(x, y, x + 1, y + imageHeight, 0xFFFFFFFF);
        g.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, 0xFF555555);
        g.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, 0xFF555555);

        // 预览区
        g.fill(x + 7, y + 12, x + 85, y + 75, 0xFF3A3A44);
        g.fill(x + 8, y + 13, x + 84, y + 74, 0xFF6E6E7A);
        ManagedUnit unit = menu.getUnit();
        if (unit instanceof LivingEntity living) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g,
                    x + 12, y + 14, x + 80, y + 72, 28, 0.0625F,
                    (float) mouseX, (float) mouseY, living);
        }

        // 状态区
        g.fill(x + 88, y + 6, x + 186, y + 92, 0xFFB0B0B0);
        drawStatus(g);

        // 策略区（只有玩家阵营士兵显示）
        if (menu.canConfigureStrategy()) {
            for (Dropdown dropdown : dropdowns) dropdown.renderButton(g, this.font);
        } else {
            // 首领 / NPC 单位没有小队策略按钮：说明一句，避免左下方空着像是界面缺东西
            g.drawString(this.font, Component.translatable("gui.monster_conquest.unit.strategy.none1"),
                    x + 8, y + 110, 0xFF505050, false);
            g.drawString(this.font, Component.translatable("gui.monster_conquest.unit.strategy.none2"),
                    x + 8, y + 122, 0xFF505050, false);
        }

        // 手动装备：6 格，每格下方单独标注（不放标题，槽位标注已经说明是哪一件）
        for (int i = 0; i < ManagedUnit.GEAR_SLOTS; i++) {
            int sx = x + 190 + i * 26;
            drawSlot(g, sx, y + 24);
            Component label = Component.translatable(gearLabelKey(i));
            g.drawString(this.font, label, sx + (18 - this.font.width(label)) / 2,
                    y + 44, 0xFF303030, false);
        }

        // 战术装备：18 格（6 × 3）
        g.drawString(this.font, Component.translatable("gui.monster_conquest.unit.tactical"),
                x + 190, y + 60, 0xFF404040, false);
        for (int i = 0; i < ManagedUnit.TACTICAL_SLOTS; i++) {
            int col = i % 6;
            int row = i / 6;
            drawSlot(g, x + 190 + col * 20, y + 72 + row * 20);
        }

        // 玩家背包
        g.drawString(this.font, Component.translatable("gui.monster_conquest.unit.inventory"),
                x + 190, y + 138, 0xFF404040, false);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawSlot(g, x + 190 + col * 18, y + 148 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            drawSlot(g, x + 190 + col * 18, y + 210);
        }
    }

    /** 手动装备槽的标注（头盔 / 胸甲 / 护腿 / 靴子 / 主手 / 副手）。 */
    private static String gearLabelKey(int index) {
        return "gui.monster_conquest.unit.gear." + switch (index) {
            case 0 -> "helmet";
            case 1 -> "chestplate";
            case 2 -> "leggings";
            case 3 -> "boots";
            case 4 -> "mainhand";
            default -> "offhand";
        };
    }

    private void drawStatus(GuiGraphics g) {
        int x = leftPos + 92;
        int y = topPos + 10;
        effectLineY = -1;
        ManagedUnit managed = menu.getUnit();
        if (!(managed instanceof LivingEntity unit)) {
            statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.loading"),
                    0xFF404040);
            return;
        }

        statusLine(g, x, y, unit.getDisplayName(), 0xFF202020);
        y += 10;
        if (managed instanceof AbstractArmyCommander commander) {
            y = drawCommanderStatus(g, x, y, commander);
        } else if (managed instanceof AbstractArmySoldier soldier) {
            y = drawSoldierStatus(g, x, y, soldier);
        }
        effectLineY = y;
        drawEffectLine(g, x, y);
    }

    private int drawSoldierStatus(GuiGraphics g, int x, int y, AbstractArmySoldier soldier) {
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.health",
                String.format("%.0f/%.0f", soldier.getHealth(), soldier.getMaxHealth())),
                0xFF902020);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.armor",
                soldier.getArmorValue()), 0xFF205090);
        y += 10;
        statusLine(g, x, y, Component.translatable("tooltip.monster_conquest.tier",
                Component.translatable("tooltip.monster_conquest.tier_name."
                        + soldier.getTier().name().toLowerCase())), 0xFF705010);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.faction",
                FactionDisplay.displayName(soldier.getFactionId())), 0xFF205020);
        y += 10;
        int squad = soldier.getSquadId();
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.squad",
                squad > 0 ? String.valueOf(squad)
                        : Component.translatable("gui.monster_conquest.unit.squad.none").getString()),
                0xFF303080);
        y += 10;
        Component form = Component.translatable(soldier.getSoldierType().isCavalry()
                ? "tooltip.monster_conquest.form.cavalry"
                : "tooltip.monster_conquest.form.infantry");
        if (soldier.isPassenger()) {
            form = form.copy().append(
                    Component.translatable("gui.monster_conquest.unit.mounted"));
        }
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.form",
                form), 0xFF804010);
        return y + 10;
    }

    private int drawCommanderStatus(GuiGraphics g, int x, int y, AbstractArmyCommander commander) {
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.role",
                Component.translatable(commander instanceof AbstractPatrolCaptain
                        ? "gui.monster_conquest.unit.role.captain"
                        : "gui.monster_conquest.unit.role.commander")), 0xFF606060);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.health",
                String.format("%.0f/%.0f", commander.getHealth(), commander.getMaxHealth())),
                0xFF902020);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.armor",
                commander.getArmorValue()), 0xFF205090);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.faction",
                FactionDisplay.displayName(commander.getFactionId())), 0xFF205020);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.form",
                Component.translatable(commander.isPassenger()
                        ? "tooltip.monster_conquest.form.cavalry"
                        : "tooltip.monster_conquest.form.infantry")), 0xFF804010);
        y += 10;
        statusLine(g, x, y, Component.translatable("gui.monster_conquest.unit.weapon",
                Component.translatable("gui.monster_conquest.unit.weapon."
                        + commander.getWeaponMode().name().toLowerCase())), 0xFF705010);
        return y + 10;
    }

    /** 状态区一行文本：按状态块宽度裁剪，避免长阵营名 / 长名字压到右边装备栏。 */
    private void statusLine(GuiGraphics g, int x, int y, Component text, int color) {
        g.drawString(this.font, this.font.plainSubstrByWidth(text.getString(), 92),
                x, y, color, false);
    }

    /**
     * 状态效果一行文本（最多 4 个，多的用 +N 表示）。
     *   效果数据来自服务端同步的菜单数据槽——客户端实体自己拿不到别的生物的效果列表。
     */
    private void drawEffectLine(GuiGraphics g, int x, int y) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < UnitMenu.EFFECT_SHOWN; i++) {
            int packed = menu.getSync(UnitMenu.DATA_EFFECTS + i);
            if (packed == 0) break;
            var effect = BuiltInRegistries.MOB_EFFECT.byId((packed >>> 8) - 1);
            if (effect == null) continue;
            int amplifier = packed & 0xFF;
            String name = effect.getDisplayName().getString();
            parts.add(amplifier > 0 ? name + " " + toRoman(amplifier + 1) : name);
        }
        int extra = Math.max(0, menu.getSync(UnitMenu.DATA_EFFECT_TOTAL) - parts.size());
        String text = parts.isEmpty()
                ? Component.translatable("gui.monster_conquest.unit.effects.none").getString()
                : String.join(", ", parts) + (extra > 0 ? " +" + extra : "");
        Component line = Component.translatable("gui.monster_conquest.unit.effects", text);
        g.drawString(this.font, this.font.plainSubstrByWidth(line.getString(), 92),
                x, y, 0xFF106010, false);
    }

    /** 鼠标是否悬停在状态效果那一行上（悬停时用提示框列出名称 / 等级 / 剩余时间）。 */
    private boolean isHoveringEffectLine(int mouseX, int mouseY) {
        // effectLineY 是文字的屏幕 y（drawString 画在 y..y+8）
        return effectLineY >= 0
                && mouseX >= leftPos + 92 && mouseX < leftPos + 186
                && mouseY >= effectLineY - 1 && mouseY < effectLineY + 9;
    }

    /** 状态效果详情（名称 + 等级 + 剩余时间）。 */
    private List<Component> effectTooltipLines() {
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < UnitMenu.EFFECT_SHOWN; i++) {
            int packed = menu.getSync(UnitMenu.DATA_EFFECTS + i);
            if (packed == 0) break;
            var effect = BuiltInRegistries.MOB_EFFECT.byId((packed >>> 8) - 1);
            if (effect == null) continue;
            int amplifier = packed & 0xFF;
            int seconds = menu.getSync(UnitMenu.DATA_EFFECT_DURATION + i);
            String line = effect.getDisplayName().getString();
            if (amplifier > 0) line += " " + toRoman(amplifier + 1);
            if (seconds < 0) line += "  ∞";
            else if (seconds > 0) line += "  " + String.format("%d:%02d", seconds / 60, seconds % 60);
            lines.add(Component.literal(line));
        }
        int extra = Math.max(0, menu.getSync(UnitMenu.DATA_EFFECT_TOTAL) - lines.size());
        if (extra > 0) lines.add(Component.literal("+" + extra));
        return lines;
    }

    private static String toRoman(int value) {
        return switch (value) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(value);
        };
    }

    private static void drawSlot(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    /** 一个策略下拉框：按钮显示当前值，点开后弹出选项列表。 */
    private final class Dropdown {
        private static final int ROW_H = 12;
        private final int x;
        private final int y;
        private final int w;
        private final int dataIndex;
        private final int action;
        private final Component label;
        private final List<Option> options;
        private boolean open;

        Dropdown(int x, int y, int w, int dataIndex, int action,
                 Component label, List<Option> options) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.dataIndex = dataIndex;
            this.action = action;
            this.label = label;
            this.options = options;
        }

        private Component current() {
            int value = menu.getSync(dataIndex);
            for (Option option : options) {
                if (option.value == value) return option.text;
            }
            return options.isEmpty() ? Component.empty() : options.get(0).text;
        }

        void close() { open = false; }

        void renderButton(GuiGraphics g, Font font) {
            int bx = leftPos + x;
            int by = topPos + y;
            g.fill(bx, by, bx + w, by + 18, 0xFF4A4A52);
            g.fill(bx + 1, by + 1, bx + w - 1, by + 17, open ? 0xFF6C6C80 : 0xFF5A5A64);
            g.drawString(font, label, bx + 1, by + DROPDOWN_LABEL_DY, 0xFF303030, false);
            Component value = current();
            String text = font.plainSubstrByWidth(value.getString(), w - 5);
            g.drawString(font, text, bx + 3, by + 5, 0xFFF0F0F0, false);
        }

        /**
         * 展开的选项列表：用 +z 抬一层再画——物品堆叠是画在 z=150 的，
         *   不抬层的话列表会被后画的物品盖住。
         */
        void renderPopup(GuiGraphics g, Font font, int mouseX, int mouseY) {
            if (!open) return;
            int bx = leftPos + x;
            int by = topPos + y + 19;
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 200.0F);
            g.fill(bx, by - 1, bx + w, by + options.size() * ROW_H + 1, 0xFF202024);
            for (int i = 0; i < options.size(); i++) {
                Option option = options.get(i);
                int oy = by + i * ROW_H;
                boolean hover = isMouseOverPopup(mouseX, mouseY) && mouseY >= oy && mouseY < oy + ROW_H
                        && mouseX >= bx && mouseX < bx + w;
                g.fill(bx + 1, oy, bx + w - 1, oy + ROW_H,
                        hover ? 0xFF8A8AA8 : 0xFF5A5A64);
                String text = font.plainSubstrByWidth(option.text.getString(), w - 6);
                g.drawString(font, text, bx + 3, oy + 2, 0xFFF0F0F0, false);
            }
            g.pose().popPose();
        }

        /** 鼠标是否在展开的选项列表上（列表打开时吞掉物品提示）。 */
        boolean isMouseOverPopup(double mouseX, double mouseY) {
            if (!open) return false;
            int bx = leftPos + x;
            int by = topPos + y + 19;
            return mouseX >= bx && mouseX < bx + w
                    && mouseY >= by - 1 && mouseY < by + options.size() * ROW_H + 1;
        }

        boolean mouseClicked(double mouseX, double mouseY, int button) {
            int bx = leftPos + x;
            int by = topPos + y;
            if (mouseX >= bx && mouseX < bx + w && mouseY >= by && mouseY < by + 18) {
                open = !open;
                if (open) {
                    for (Dropdown other : dropdowns) if (other != this) other.open = false;
                }
                return true;
            }
            if (!open) return false;
            int listY = by + 19;
            for (int i = 0; i < options.size(); i++) {
                int oy = listY + i * ROW_H;
                if (mouseX >= bx && mouseX < bx + w && mouseY >= oy && mouseY < oy + ROW_H) {
                    PacketDistributor.sendToServer(new UnitControlPayload(
                            menu.getUnitId(), action, options.get(i).value));
                    open = false;
                    return true;
                }
            }
            open = false;
            return true;      // 弹层打开时吞掉点击，避免误触下方槽位
        }
    }

    private record Option(int value, Component text) {}
}
