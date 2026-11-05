package com.ohhapple.monster_conquest.client.renderer;

/** 竞技单位状态图标在血条上方的居中换行布局。 */
final class ArenaStatusIconLayout {

    static final int COLUMNS = 6;
    static final float COLUMN_STEP = 11.0f;
    static final float ICON_HEIGHT = 10.0f;
    static final float ROW_GAP = 2.0f;
    static final float ROW_STEP = ICON_HEIGHT + ROW_GAP;
    static final float FIRST_ROW_Y = -5.0f;

    private ArenaStatusIconLayout() {
    }

    static float x(int slot, int total) {
        int row = slot / COLUMNS;
        int column = slot % COLUMNS;
        int rowCount = Math.min(
                COLUMNS, total - row * COLUMNS);
        return -(rowCount - 1) * COLUMN_STEP / 2.0f
                + column * COLUMN_STEP;
    }

    static float y(int slot) {
        return FIRST_ROW_Y - (slot / COLUMNS) * ROW_STEP;
    }
}
