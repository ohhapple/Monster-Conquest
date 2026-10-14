package com.ohhapple.monster_conquest.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 说明书界面：左侧章节列表（与书页同风格）+ 右侧纸张书页正文。
 *   正文自动换行（逐字折行、保留每段颜色），单位之间画分隔线，右侧滚动条可拖动。
 */
public class GuideBookScreen extends Screen {

    private static final int PADDING = 10;
    private static final int SIDEBAR_WIDTH = 116;
    private static final int ROW_HEIGHT = 20;
    private static final int LINE_HEIGHT = 11;
    /** 正文左留白 / 右留白：右侧留白较小，让文字尽量靠近书页边框。 */
    private static final int TEXT_LEFT_PAD = 6;
    private static final int TEXT_RIGHT_PAD = 10;
    /** 滚动条：紧贴书页最右侧边框内侧。 */
    private static final int SCROLLBAR_W = 3;
    private static final int SCROLLBAR_INSET = 5;

    /** 半透明深色底 + 不调用原版 renderBackground（避免菜单模糊滤镜），与 R 键轮盘写法一致。 */
    private static final int COLOR_BACKDROP = 0x90000000;
    private static final int COLOR_PANEL = 0xFFF4E9D2;
    private static final int COLOR_PANEL_BORDER = 0xFF6B4A2A;
    private static final int COLOR_ROW_HOVER = 0x30A07040;
    private static final int COLOR_ROW_SELECTED = 0xFFD9B37A;
    private static final int COLOR_SIDEBAR_TEXT = 0xFF3A2A1A;
    private static final int COLOR_SCROLLBAR = 0x906B4A2A;
    private static final int COLOR_TRACK = 0x306B4A2A;

    private record Line(@Nullable MutableComponent text, int indent, int gapBefore, boolean separator) {}

    private record StyledChar(char c, Style style) {}

    private final List<GuideBookContent.Chapter> chapters = GuideBookContent.CHAPTERS;
    private final List<Line> lines = new ArrayList<>();

    private int selected = 0;
    private int scroll = 0;
    private int contentHeight = 0;

    /** 滚动条拖动状态。 */
    private boolean draggingBar = false;
    private double dragOffset = 0;

    public GuideBookScreen() {
        super(Component.translatable("gui.monster_conquest.guide.title"));
    }

    /** 客户端打开界面。 */
    public static void open() {
        Minecraft.getInstance().setScreen(new GuideBookScreen());
    }

    @Override
    protected void init() {
        rebuildLines();
    }

    // ===================== 布局 =====================

    private int top() { return PADDING + 16; }
    private int bottom() { return this.height - PADDING - 14; }
    private int listHeight() { return bottom() - top(); }
    private int viewHeight() { return Math.max(1, listHeight() - 8); }

    private int listX() { return PADDING; }
    private int contentX() { return listX() + SIDEBAR_WIDTH + PADDING; }
    private int contentW() { return this.width - contentX() - PADDING; }

    /** 正文可写宽度：面板宽度减去左右留白。 */
    private int textWidth() { return Math.max(60, contentW() - TEXT_LEFT_PAD - TEXT_RIGHT_PAD); }

    private int maxScroll() { return Math.max(0, contentHeight - viewHeight()); }

    private int barHeight() {
        return Math.max(20, viewHeight() * viewHeight() / Math.max(1, contentHeight));
    }

    private int barX() { return contentX() + contentW() - SCROLLBAR_INSET; }

    private int barY() {
        int max = maxScroll();
        if (max <= 0) return top() + 4;
        return top() + 4 + (viewHeight() - barHeight()) * scroll / max;
    }

    // ===================== 文本排版 =====================

    private void rebuildLines() {
        lines.clear();
        if (selected < 0 || selected >= chapters.size()) return;

        GuideBookContent.Chapter chapter = chapters.get(selected);
        int width = textWidth();

        for (GuideBookContent.GuideLine line : GuideBookContent.body(chapter)) {
            if (line.separator()) {
                lines.add(new Line(null, 0, line.gapBefore(), true));
                continue;
            }
            if (line.blank() || line.text() == null) {
                lines.add(new Line(null, 0, line.gapBefore(), false));
                continue;
            }
            int indent = line.indent();
            int maxWidth = Math.max(40, width - indent);
            boolean first = true;
            for (MutableComponent part : wrapComponent(line.text(), maxWidth)) {
                lines.add(new Line(part, indent, first ? line.gapBefore() : 0, false));
                first = false;
            }
        }

        contentHeight = 0;
        for (Line line : lines) contentHeight += LINE_HEIGHT + line.gapBefore();
        scroll = 0;
    }

    /**
     * 逐字折行并保留颜色：中文没有空格，按像素宽度逐字断行可填满整行，
     *   不会出现右侧还有空间却提前换行；折行后按颜色合并连续片段。
     */
    private List<MutableComponent> wrapComponent(MutableComponent source, int maxWidth) {
        List<StyledChar> chars = new ArrayList<>();
        source.visit((style, text) -> {
            for (int i = 0; i < text.length(); i++) {
                chars.add(new StyledChar(text.charAt(i), style));
            }
            return Optional.empty();
        }, Style.EMPTY);

        List<MutableComponent> out = new ArrayList<>();
        List<StyledChar> current = new ArrayList<>();
        int currentWidth = 0;
        for (StyledChar sc : chars) {
            // 按“带样式的实际渲染宽度”计算（加粗、全角等不会算少导致右侧溢出）
            int charWidth = font.width(
                    Component.literal(String.valueOf(sc.c())).withStyle(sc.style()));
            if (!current.isEmpty() && currentWidth + charWidth > maxWidth) {
                out.add(joinStyled(current));
                current = new ArrayList<>();
                currentWidth = 0;
            }
            current.add(sc);
            currentWidth += charWidth;
        }
        if (!current.isEmpty()) out.add(joinStyled(current));
        return out;
    }

    /** 把同一行内颜色相同的连续字符合并成一个组件，减少绘制节点。 */
    private MutableComponent joinStyled(List<StyledChar> chars) {
        MutableComponent out = Component.empty();
        StringBuilder run = new StringBuilder();
        int runColor = 0;
        for (StyledChar sc : chars) {
            int color = colorOf(sc.style());
            if (run.isEmpty()) {
                runColor = color;
            } else if (color != runColor) {
                out.append(styled(run.toString(), runColor));
                run.setLength(0);
                runColor = color;
            }
            run.append(sc.c());
        }
        if (!run.isEmpty()) out.append(styled(run.toString(), runColor));
        return out;
    }

    private static MutableComponent styled(String text, int color) {
        return Component.literal(text).withStyle(s -> s.withColor(color));
    }

    private static int colorOf(Style style) {
        return style.getColor() == null ? 0xFFFFFFFF : style.getColor().getValue();
    }

    // ===================== 渲染 =====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 自己铺半透明深色背景（和 R 键轮盘一致）：不调用原版 renderBackground，避免模糊滤镜
        g.fill(0, 0, this.width, this.height, COLOR_BACKDROP);

        int top = top();
        int bottom = bottom();
        g.drawString(font, title, PADDING, PADDING, 0xFFFFFFFF);

        // ===================== 左侧目录（与书页同款纸张风格） =====================
        int listX = listX();
        g.fill(listX, top, listX + SIDEBAR_WIDTH, bottom, COLOR_PANEL);
        drawBorder(g, listX, top, listX + SIDEBAR_WIDTH, bottom);

        for (int i = 0; i < chapters.size(); i++) {
            int rowY = top + i * ROW_HEIGHT;
            if (rowY + ROW_HEIGHT > bottom) break;
            boolean sel = i == selected;
            boolean hovered = mouseX >= listX && mouseX < listX + SIDEBAR_WIDTH
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (sel) {
                g.fill(listX + 2, rowY + 1, listX + SIDEBAR_WIDTH - 2, rowY + ROW_HEIGHT - 1, COLOR_ROW_SELECTED);
            } else if (hovered) {
                g.fill(listX + 2, rowY + 1, listX + SIDEBAR_WIDTH - 2, rowY + ROW_HEIGHT - 1, COLOR_ROW_HOVER);
            }
            g.drawString(font, chapters.get(i).title(), listX + 8, rowY + 6,
                    sel ? 0xFF4A2A10 : COLOR_SIDEBAR_TEXT, false);
        }

        // ===================== 右侧书页 =====================
        int contentX = contentX();
        int contentW = contentW();
        g.fill(contentX, top, contentX + contentW, bottom, COLOR_PANEL);
        drawBorder(g, contentX, top, contentX + contentW, bottom);

        scroll = Mth.clamp(scroll, 0, maxScroll());
        int textX = contentX + TEXT_LEFT_PAD;
        // 从可视区顶部 +4 开始：这样滚动到底时“最后一行”也能完整落在可视区内
        int y = top + 4 - scroll;
        // 裁剪到书页内部：即使某行因字体 / 语言差异略宽，也不会画到屏幕外
        g.enableScissor(contentX + 2, top + 2, contentX + contentW - 2, bottom - 2);
        for (Line line : lines) {
            y += line.gapBefore();
            if (y >= top + 4 && y + LINE_HEIGHT <= bottom - 4) {
                if (line.separator()) {
                    int lineY = y + LINE_HEIGHT / 2;
                    int x1 = textX + 4;
                    int x2 = Math.max(x1 + 1, contentX + contentW - TEXT_RIGHT_PAD - 4);
                    g.fill(x1, lineY, x2, lineY + 1, GuideBookContent.COLOR_SEPARATOR);
                } else if (line.text() != null) {
                    // 纸张底 + 深色文字：关阴影（和原版书页一样），中文最清晰
                    g.drawString(font, line.text(), textX + line.indent(), y, 0xFFFFFFFF, false);
                }
            }
            y += LINE_HEIGHT;
        }
        g.disableScissor();

        // ===================== 滚动条（紧贴书页最右侧，可拖动） =====================
        if (maxScroll() > 0) {
            g.fill(barX(), top + 4, barX() + SCROLLBAR_W, bottom - 4, COLOR_TRACK);
            g.fill(barX() - 1, barY(), barX() + SCROLLBAR_W + 1, barY() + barHeight(), COLOR_SCROLLBAR);
        }

        // 页脚同样裁剪，避免窄窗口下横向溢出屏幕
        g.enableScissor(0, this.height - PADDING - 10, this.width, this.height);
        g.drawString(font, Component.translatable("gui.monster_conquest.guide.footer"),
                PADDING, this.height - PADDING - 8, 0xFFD0D0D0);
        g.disableScissor();

        // 不调用 super.render()：Screen.render 会在最后调用 renderBackground（菜单模糊后处理），
        //   会把已经画好的正文一起糊掉。
    }

    private void drawBorder(GuiGraphics g, int x1, int y1, int x2, int y2) {
        g.fill(x1, y1, x2, y1 + 2, COLOR_PANEL_BORDER);
        g.fill(x1, y2 - 2, x2, y2, COLOR_PANEL_BORDER);
        g.fill(x1, y1, x1 + 2, y2, COLOR_PANEL_BORDER);
        g.fill(x2 - 2, y1, x2, y2, COLOR_PANEL_BORDER);
    }

    // ===================== 交互 =====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        // 滚动条：点滑块直接拖，点轨道跳到该位置
        if (maxScroll() > 0
                && mouseX >= barX() - 3 && mouseX <= barX() + SCROLLBAR_W + 3
                && mouseY >= top() && mouseY <= bottom()) {
            int h = barHeight();
            if (mouseY >= barY() && mouseY <= barY() + h) {
                dragOffset = mouseY - barY();
            } else {
                dragOffset = h / 2.0;
                applyBarDrag(mouseY);
            }
            draggingBar = true;
            return true;
        }

        int top = top();
        int bottom = bottom();
        int listX = listX();
        if (mouseX >= listX && mouseX < listX + SIDEBAR_WIDTH) {
            int idx = (int) ((mouseY - top) / ROW_HEIGHT);
            if (mouseY >= top && mouseY < bottom && idx >= 0 && idx < chapters.size()
                    && top + idx * ROW_HEIGHT + ROW_HEIGHT <= bottom) {
                if (idx != selected) {
                    selected = idx;
                    rebuildLines();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingBar) {
            applyBarDrag(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingBar) {
            draggingBar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void applyBarDrag(double mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int barH = barHeight();
        int track = Math.max(1, viewHeight() - barH);
        double minY = top() + 4;
        double y = Mth.clamp(mouseY - dragOffset, minY, minY + track);
        scroll = (int) Math.round((y - minY) * max / (double) track);
        scroll = Mth.clamp(scroll, 0, max);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int max = maxScroll();
        if (max > 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 12, 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
