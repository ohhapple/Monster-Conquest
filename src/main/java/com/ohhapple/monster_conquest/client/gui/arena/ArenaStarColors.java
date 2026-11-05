package com.ohhapple.monster_conquest.client.gui.arena;

/** 所有弈子星级标志共用的颜色。 */
public final class ArenaStarColors {

    private static final int COPPER = 0xFFC9793D;
    private static final int SILVER = 0xFFC7D0DA;
    private static final int GOLD = 0xFFFFD45A;

    private ArenaStarColors() {
    }

    public static int color(int star) {
        return switch (Math.max(1, Math.min(3, star))) {
            case 1 -> COPPER;
            case 2 -> SILVER;
            default -> GOLD;
        };
    }

    public static int color(int star, boolean dimmed) {
        int color = color(star);
        if (!dimmed) return color;
        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;
        return 0xC0000000
                | (red * 3 / 5) << 16
                | (green * 3 / 5) << 8
                | blue * 3 / 5;
    }
}
