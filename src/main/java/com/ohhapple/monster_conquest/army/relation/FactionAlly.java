package com.ohhapple.monster_conquest.army.relation;

import com.ohhapple.monster_conquest.army.Faction;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FactionAlly {

    @Nullable private static MinecraftServer SERVER;
    private FactionAlly() {}

    public static void setServer(@Nullable MinecraftServer server) { SERVER = server; }
    @Nullable public static MinecraftServer getServer() { return SERVER; }

    private static FactionAllyData data() {
        return SERVER == null ? null : FactionAllyData.get(SERVER);
    }

    // ===================== 延迟清理队列 =====================

    /**
     * setAlly() 触发的 clearArmyRelationsBetweenFactions() 需要遍历所有已加载
     *   阵营单位（首领 / 士兵 / 在线玩家），单次调用可能有数十毫秒的开销。
     *   在 GUI 里快速点几下复选框会叠加成明显卡顿。
     *
     *   setAlly() 只入队，由 ServerTick 每 tick 统一处理一次：
     *     · 同一 tick 内多次 setAlly 同一个 pair → Set 自动去重
     *     · 最多延迟 1 tick（50ms），玩家无法感知
     *     · 队列空时处理成本几乎为 0
     */
    private record FactionPair(ResourceLocation a, ResourceLocation b) {
        static FactionPair of(ResourceLocation a, ResourceLocation b) {
            // 规范化顺序：避免 (A,B) 和 (B,A) 被当作两个不同的 pair
            return a.toString().compareTo(b.toString()) <= 0
                    ? new FactionPair(a, b)
                    : new FactionPair(b, a);
        }
    }

    private static final Set<FactionPair> PENDING_CLEANUPS = ConcurrentHashMap.newKeySet();

    public static void setAlly(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return;
        FactionAllyData d = data();
        if (d != null) d.setAlly(a, b);
        // 延迟清理：入队即可
        PENDING_CLEANUPS.add(FactionPair.of(a, b));
    }

    public static void clearAlly(ResourceLocation a, ResourceLocation b) {
        FactionAllyData d = data();
        if (d != null) d.clearAlly(a, b);
    }

    /** A 是否把 B 视为盟友（单向）。 */
    public static boolean isAlly(ResourceLocation a, ResourceLocation b) {
        FactionAllyData d = data();
        return d != null && d.isAlly(a, b);
    }

    public static Set<ResourceLocation> getAllyOf(ResourceLocation id) {
        FactionAllyData d = data();
        return d == null ? Set.of() : d.getAllyOf(id);
    }

    public static void removeFaction(ResourceLocation id) {
        FactionAllyData d = data();
        if (d != null) d.removeFaction(id);
    }

    public static void clearAll() {
        FactionAllyData d = data();
        if (d != null) d.clearAll();
        PENDING_CLEANUPS.clear();
    }

    /** 按 Config 补齐每个阵营的默认结盟（不覆盖已有）。 */
    public static void ensureDefaults() {
        FactionAllyData d = data();
        if (d == null) return;
        for (Faction f : FactionRegistry.all()) {
            if (f.isPlayerFaction()) continue;
            for (ResourceLocation other : f.getDefaultAllyFactions()) {
                if (!d.isAlly(f.getId(), other)) {
                    d.setAlly(f.getId(), other);
                }
            }
        }
    }

    /** 清空后重建为 Config 默认值。 */
    public static void resetToDefaults() {
        FactionAllyData d = data();
        if (d == null) return;
        d.clearAll();
        for (Faction f : FactionRegistry.all()) {
            if (f.isPlayerFaction()) continue;
            for (ResourceLocation other : f.getDefaultAllyFactions()) {
                d.setAlly(f.getId(), other);
            }
        }
    }

    // ===================== 延迟清理处理入口 =====================

    /**
     * 由 ModEvents.onServerTickPost 每 tick 调用一次。
     *   队列为空时直接 return，无任何开销。
     */
    public static void processPendingCleanups() {
        if (PENDING_CLEANUPS.isEmpty()) return;
        if (SERVER == null) { PENDING_CLEANUPS.clear(); return; }

        // 复制 + 清空：处理期间新入队的 pair 留到下一个 tick
        Set<FactionPair> batch = new HashSet<>(PENDING_CLEANUPS);
        PENDING_CLEANUPS.removeAll(batch);

        for (FactionPair p : batch) {
            clearArmyRelationsBetweenFactions(p.a(), p.b());
        }
    }

    // ===================== 结盟时清理 ArmyRelations =====================

    /**
     * 遍历双方阵营的所有已加载军队（NPC 首领 / 士兵 / 玩家），
     * 清理它们之间所有已建立的 ArmyRelations 敌对标记。
     *
     * 本方法开销较大，只能由 processPendingCleanups() 从 ServerTick
     *       里节流后调用，不要在 setAlly() 里同步调用。
     */
    private static void clearArmyRelationsBetweenFactions(ResourceLocation a, ResourceLocation b) {
        if (SERVER == null || a == null || b == null || a.equals(b)) return;

        Set<UUID> armiesA = new HashSet<>();
        Set<UUID> armiesB = new HashSet<>();

        // 大 AABB 覆盖整个世界
        AABB worldBox = new AABB(-3.0E7, -1000, -3.0E7, 3.0E7, 1000, 3.0E7);

        for (ServerLevel level : SERVER.getAllLevels()) {
            // NPC 首领
            for (AbstractArmyCommander c : level.getEntitiesOfClass(
                    AbstractArmyCommander.class, worldBox)) {
                ResourceLocation f = c.getFactionId();
                if (f == null) continue;
                if (f.equals(a)) armiesA.add(c.getUUID());
                else if (f.equals(b)) armiesB.add(c.getUUID());
            }
            // NPC 士兵
            for (AbstractArmySoldier s : level.getEntitiesOfClass(
                    AbstractArmySoldier.class, worldBox)) {
                ResourceLocation f = s.getFactionId();
                if (f == null) continue;
                UUID army = s.getCommanderUuid().orElse(s.getUUID());
                if (f.equals(a)) armiesA.add(army);
                else if (f.equals(b)) armiesB.add(army);
            }
        }

        // 玩家阵营：玩家本身就是军队 UUID
        for (ServerPlayer p : SERVER.getPlayerList().getPlayers()) {
            ResourceLocation pf = FactionIds.player(p.getUUID());
            if (a.equals(pf) || a.equals(FactionIds.PLAYER)) armiesA.add(p.getUUID());
            if (b.equals(pf) || b.equals(FactionIds.PLAYER)) armiesB.add(p.getUUID());
        }

        for (UUID ua : armiesA) {
            for (UUID ub : armiesB) {
                ArmyRelations.clearHostile(ua, ub);
            }
        }
    }
}
