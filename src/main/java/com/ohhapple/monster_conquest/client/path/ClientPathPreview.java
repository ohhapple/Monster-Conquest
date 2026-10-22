package com.ohhapple.monster_conquest.client.path;

import com.ohhapple.monster_conquest.path.PathMode;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** 客户端保存"当前选中目标的巡逻路径"，供渲染器画点与箭头。 */
public final class ClientPathPreview {

    private static volatile List<Vec3> points = List.of();
    private static volatile PathMode mode = PathMode.PATROL;

    private ClientPathPreview() {}

    public static void set(int modeOrdinal, List<Vec3> list) {
        PathMode[] all = PathMode.values();
        mode = all[Math.max(0, Math.min(modeOrdinal, all.length - 1))];
        points = List.copyOf(list);
    }

    public static void clear() {
        points = List.of();
    }

    public static List<Vec3> points() {
        return points;
    }

    public static PathMode mode() {
        return mode;
    }
}
