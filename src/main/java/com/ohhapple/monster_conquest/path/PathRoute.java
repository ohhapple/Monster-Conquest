package com.ohhapple.monster_conquest.path;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 一组路径点（某个单位 / 玩家小编队 / NPC 军队 / 刷怪点当前设置的巡逻路线）。
 *   光标只是运行状态，不存档：重载后从第一个点重新走。
 */
public final class PathRoute {

    private final List<Vec3> points = new ArrayList<>();
    private PathMode mode = PathMode.PATROL;
    private int cursor = 0;
    private long revision = 0L;

    public PathMode getMode() { return mode; }

    public void setMode(PathMode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        revision++;
    }

    public List<Vec3> getPoints() { return points; }

    public int size() { return points.size(); }

    public long revision() { return revision; }

    public void add(Vec3 point) {
        points.add(point);
        revision++;
    }

    public void clearPoints() {
        points.clear();
        cursor = 0;
        revision++;
    }

    /** 当前要前往的点；没有点为 null。 */
    @Nullable
    public Vec3 point() {
        if (points.isEmpty()) return null;
        return points.get(Math.min(cursor, points.size() - 1));
    }

    /**
     * 走到下一个点。
     *
     * @return 是否还有下一个点：巡逻模式永远返回 true（循环回第一个点）；
     *         路线模式走完最后一个点返回 false（本次路径结束）
     */
    public boolean advance() {
        if (points.isEmpty()) return false;
        if (cursor + 1 < points.size()) {
            cursor++;
            return true;
        }
        if (mode == PathMode.PATROL) {
            cursor = 0;
            return true;
        }
        return false;
    }

    public void resetCursor() { cursor = 0; }
}
