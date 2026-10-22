package com.ohhapple.monster_conquest.army;

/** 举盾动作的阶段。状态只描述当前姿态，具体格挡在事件结算时再判定。 */
public enum GuardStance {
    LOWERED(0),
    BRACING(1),
    READY(2),
    RECOVERING(3);

    private final int id;

    GuardStance(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static GuardStance byId(int id) {
        for (GuardStance stance : values()) {
            if (stance.id == id) return stance;
        }
        return LOWERED;
    }
}
