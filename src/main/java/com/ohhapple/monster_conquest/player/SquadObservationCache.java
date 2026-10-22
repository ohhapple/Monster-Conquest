package com.ohhapple.monster_conquest.player;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 小队级观测快照。
 *   软驻守、队友支援和阵型质心都需要同一批队员；如果每个士兵各查一次世界，
 *   大队伍会退化成平方级实体扫描。这里按队伍低频更新一份成员列表，查询方只读快照。
 */
public final class SquadObservationCache {

    private static final int REFRESH_TICKS = 15;
    private static final int MAX_ENTRIES = 128;
    private static final Map<Key, Snapshot> CACHE = new HashMap<>();

    private SquadObservationCache() {}

    public static boolean anyInCombat(Player player, int squadId) {
        return snapshot(player, squadId).anyInCombat;
    }

    public static Vec3 centroid(Player player, AbstractArmySoldier self, int squadId) {
        Snapshot snapshot = snapshot(player, squadId);
        double range = GeneralConfig.armyFollowRange;
        double rangeSq = range * range;
        double sx = self.getX();
        double sy = self.getY();
        double sz = self.getZ();
        int count = 1;
        for (AbstractArmySoldier member : snapshot.members) {
            if (member == self || !member.isAlive()) continue;
            if (member.distanceToSqr(self) > rangeSq) continue;
            sx += member.getX();
            sy += member.getY();
            sz += member.getZ();
            count++;
        }
        return new Vec3(sx / count, sy / count, sz / count);
    }

    @Nullable
    public static AbstractArmySoldier nearestFightingMate(Player player,
                                                          AbstractArmySoldier self,
                                                          int squadId) {
        AbstractArmySoldier best = null;
        double bestDist = Double.MAX_VALUE;
        for (AbstractArmySoldier member : snapshot(player, squadId).members) {
            if (member == self || !member.isAlive()) continue;
            if (member.getTarget() == null || !member.getTarget().isAlive()) continue;
            double dist = self.distanceToSqr(member);
            if (dist < bestDist) {
                bestDist = dist;
                best = member;
            }
        }
        return best;
    }

    public static void clearAll() {
        CACHE.clear();
    }

    public static void clearPlayer(UUID playerId) {
        if (playerId != null) CACHE.keySet().removeIf(key -> key.player.equals(playerId));
    }

    private static Snapshot snapshot(Player player, int squadId) {
        long now = player.level().getGameTime();
        Key key = new Key(player.level().dimension(), player.getUUID(), squadId);
        Snapshot cached = CACHE.get(key);
        if (cached != null && now - cached.refreshedTick <= REFRESH_TICKS) {
            return cached;
        }

        List<AbstractArmySoldier> members = new ArrayList<>();
        for (AbstractArmySoldier soldier : player.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                player.getBoundingBox().inflate(GeneralConfig.armyFollowRange),
                s -> s.isAlive() && !s.isRemoved()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId)) {
            members.add(soldier);
        }
        boolean inCombat = false;
        for (AbstractArmySoldier member : members) {
            if (member.getTarget() != null && member.getTarget().isAlive()) {
                inCombat = true;
                break;
            }
        }
        Snapshot snapshot = new Snapshot(now, List.copyOf(members), inCombat);
        CACHE.put(key, snapshot);
        prune(now);
        return snapshot;
    }

    private static void prune(long now) {
        if (CACHE.size() <= MAX_ENTRIES) return;
        Iterator<Map.Entry<Key, Snapshot>> iterator = CACHE.entrySet().iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().getValue().refreshedTick > 120) {
                iterator.remove();
            }
        }
        while (CACHE.size() > MAX_ENTRIES) {
            Iterator<Key> keys = CACHE.keySet().iterator();
            if (!keys.hasNext()) return;
            keys.next();
            keys.remove();
        }
    }

    private record Key(ResourceKey<Level> dimension, UUID player, int squad) {}

    private record Snapshot(long refreshedTick, List<AbstractArmySoldier> members,
                            boolean anyInCombat) {}
}
