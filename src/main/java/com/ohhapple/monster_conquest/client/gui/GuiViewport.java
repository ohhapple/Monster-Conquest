package com.ohhapple.monster_conquest.client.gui;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 固定逻辑尺寸界面的屏幕适配：只在可用区域不足时等比缩小，并负责鼠标坐标反算。
 */
public record GuiViewport(float scale, int offsetX, int offsetY) {

    public static GuiViewport fit(int screenWidth, int screenHeight,
                                  int contentWidth, int contentHeight, int margin) {
        float availableW = Math.max(1, screenWidth - margin * 2);
        float availableH = Math.max(1, screenHeight - margin * 2);
        float scale = Math.min(1.0f,
                Math.min(availableW / contentWidth, availableH / contentHeight));
        scale = Math.max(0.1f, scale);
        int offsetX = Math.round((screenWidth - contentWidth * scale) / 2.0f);
        int offsetY = Math.round((screenHeight - contentHeight * scale) / 2.0f);
        return new GuiViewport(scale, offsetX, offsetY);
    }

    public double localX(double screenX) {
        return (screenX - offsetX) / scale;
    }

    public double localY(double screenY) {
        return (screenY - offsetY) / scale;
    }

    public int localX(int screenX) {
        return (int) Math.floor(localX((double) screenX));
    }

    public int localY(int screenY) {
        return (int) Math.floor(localY((double) screenY));
    }

    public double localDelta(double delta) {
        return delta / scale;
    }

    public int screenX(double localX) {
        return Math.round(offsetX + (float) localX * scale);
    }

    public int screenY(double localY) {
        return Math.round(offsetY + (float) localY * scale);
    }

    public void push(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(offsetX, offsetY, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
    }

    /**
     * GuiGraphics 的裁剪框使用屏幕坐标，不经过当前 PoseStack 变换。
     */
    public void enableScissor(GuiGraphics graphics,
                              int left, int top, int right, int bottom) {
        graphics.enableScissor(screenX(left), screenY(top),
                screenX(right), screenY(bottom));
    }

    public void pop(GuiGraphics graphics) {
        graphics.pose().popPose();
    }
}
