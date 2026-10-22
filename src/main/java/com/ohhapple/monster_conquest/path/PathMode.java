package com.ohhapple.monster_conquest.path;

import net.minecraft.network.chat.Component;

/** 路径点工具的两套走法。 */
public enum PathMode {
    /** 巡逻：按顺序走完全部点后回到第一个点继续循环。 */
    PATROL("msg.monster_conquest.path_tool.mode.patrol"),
    /** 路线：按顺序走完全部点后结束，单位恢复自由活动。 */
    ROUTE("msg.monster_conquest.path_tool.mode.route");

    private final String translationKey;

    PathMode(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component getDisplayName() {
        return Component.translatable(translationKey);
    }

    public PathMode next() {
        PathMode[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
