package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
import com.ohhapple.monster_conquest.army.threat.ArmyThreatUtil;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 军团抛射物共用的友军判定。
 * 箭 / 三叉戟 / 枪械弹丸共用同一套规则：
 *   · 同一指挥官（同军队）→ 友军
 *   · 同一阵营 → 友军
 *   · 任一方向结盟（包括结盟玩家）→ 友军
 * 友军永远不出现在抛射物命中判定里，因此不会被队友身体挡住。
 */
public final class ArmyProjectileUtil {

    private ArmyProjectileUtil() {}

    @Nullable
    public static UUID resolveCommander(@Nullable Entity e) {
        if (e == null) return null;
        if (e instanceof AbstractArmySoldier s) return s.getCommanderUuid().orElse(null);
        if (e instanceof AbstractArmyCommander c) return c.getUUID();
        if (e instanceof Player p) return p.getUUID();
        if (e instanceof WarHorseEntity h) {
            UUID owner = h.getOwnerUuid();
            if (owner == null) return null;
            if (!(h.level() instanceof ServerLevel sl)) return null;
            return resolveCommander(sl.getEntities().get(owner));
        }
        return null;
    }

    public static boolean isFriendly(@Nullable UUID shooterCommander,
                                     @Nullable ResourceLocation shooterFaction,
                                     Entity target) {
        if (shooterCommander != null) {
            UUID tc = resolveCommander(target);
            if (tc != null && tc.equals(shooterCommander)) return true;
        }
        if (shooterFaction != null) {
            ResourceLocation tf = ArmyThreatUtil.factionOf(target);
            if (tf != null) {
                if (tf.equals(shooterFaction)) return true;
                // 任一方向结盟都视为友军，保证结盟玩家 / 结盟军队不会被挡或误伤
                if (FactionAlly.isAlly(shooterFaction, tf)
                        || FactionAlly.isAlly(tf, shooterFaction)) return true;
            }
            // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，
            //   抛射物直接穿过，不会命中也不会误伤。
            if (NativePeace.isKin(target, NativePeace.kinOf(shooterFaction))) return true;
        }
        return false;
    }
}
