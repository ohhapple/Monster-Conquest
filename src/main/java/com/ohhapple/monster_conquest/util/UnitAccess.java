package com.ohhapple.monster_conquest.util;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/** 单位访问判定：谁能打开 / 指挥某个单位。 */
public final class UnitAccess {

    private UnitAccess() {}

    /**
     * 能否打开这个单位的界面：
     *   · 创造模式或 OP：任何单位都能看；
     *   · 生存玩家：只能看自己玩家阵营（自己的）单位。
     */
    public static boolean canOpen(Player viewer, ManagedUnit unit) {
        if (viewer.isCreative() || viewer.hasPermissions(2)) return true;
        UUID owner = ownerOf(unit);
        return owner != null && owner.equals(viewer.getUUID());
    }

    /** 单位归属的玩家 UUID：非玩家阵营（原版阵营 / 其他玩家的单位）返回 null。 */
    public static UUID ownerOf(ManagedUnit unit) {
        var faction = unit.getFactionId();
        if (faction == null || !FactionIds.isPlayer(faction)) return null;
        return FactionIds.parsePlayer(faction);
    }
}
