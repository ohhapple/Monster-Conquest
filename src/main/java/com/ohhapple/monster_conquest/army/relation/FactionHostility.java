package com.ohhapple.monster_conquest.army.relation;

import com.ohhapple.monster_conquest.army.Faction;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class FactionHostility {

    @Nullable private static MinecraftServer SERVER;

    private FactionHostility() {}

    public static void setServer(@Nullable MinecraftServer server) { SERVER = server; }
    @Nullable public static MinecraftServer getServer() { return SERVER; }

    private static FactionHostilityData data() {
        return SERVER == null ? null : FactionHostilityData.get(SERVER);
    }

    public static void setHostile(ResourceLocation a, ResourceLocation b) {
        FactionHostilityData d = data();
        if (d != null) {
            d.setHostile(a, b);
            // 玩家（GUI）/ 默认配置指定的关系 → 不再算"游戏内建立"
            d.forgetLearned(a, b);
        }
    }

    public static void clearHostile(ResourceLocation a, ResourceLocation b) {
        FactionHostilityData d = data();
        if (d != null) {
            d.clearHostile(a, b);
            d.forgetLearned(a, b);
        }
    }

    /** 游戏内建立的敌对（仇恨工具等）：重置 / 和平模式会清掉它。 */
    public static void setHostileLearned(ResourceLocation a, ResourceLocation b) {
        FactionHostilityData d = data();
        if (d == null) return;
        d.setHostile(a, b);
        // 默认配置里本来就有的敌对 → 不算"游戏内建立"，重置时保留
        if (isDefaultHostile(a, b) || isDefaultHostile(b, a)) return;
        d.markLearned(a, b);
    }

    private static boolean isDefaultHostile(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null) return false;
        Faction faction = FactionRegistry.get(a);
        return faction != null && faction.getDefaultHostileFactions().contains(b);
    }

    /** 只清游戏内建立的敌对关系，保留 N 键 GUI 设置与默认配置。 */
    public static void clearLearnedHostility() {
        FactionHostilityData d = data();
        if (d != null) d.clearLearnedHostility();
    }

    public static boolean isHostile(ResourceLocation a, ResourceLocation b) {
        FactionHostilityData d = data();
        return d != null && d.isHostile(a, b);
    }

    /**
     * 两个阵营之间是否"显式"设置过敌对（不含大玩家阵营继承等隐式规则）。
     *   N 键 GUI 的加减开关以它为准：靠配置 / 继承实际已经敌对的组合，
     *   第一次点击应该把它显式写下来，否则会被当成"取消"而毫无反应。
     */
    public static boolean isExplicitHostile(ResourceLocation a, ResourceLocation b) {
        FactionHostilityData d = data();
        return d != null && d.isExplicitHostile(a, b);
    }

    /** 移除某个阵营及其所有敌对关系。 */
    public static void removeFaction(ResourceLocation id) {
        FactionHostilityData d = data();
        if (d != null) d.removeFaction(id);
    }

    public static void resetToDefaults() {
        FactionHostilityData d = data();
        if (d == null) return;
        d.clearAll();
        // 清空"已初始化"标记，让在线玩家重新补默认值
        d.clearAllInitializedPlayers();
        for (Faction f : FactionRegistry.all()) {
            if (f.isPlayerFaction()) continue;
            for (ResourceLocation other : f.getDefaultHostileFactions()) {
                d.setHostile(f.getId(), other);
            }
        }
        if (SERVER != null) {
            for (ServerPlayer p : SERVER.getPlayerList().getPlayers()) {
                ensurePlayerDefaults(p.getUUID());
            }
        }
    }

    public static void ensureDefaults() {
        FactionHostilityData d = data();
        if (d == null) return;
        for (Faction f : FactionRegistry.all()) {
            if (f.isPlayerFaction()) continue;
            for (ResourceLocation other : f.getDefaultHostileFactions()) {
                if (!d.isHostile(f.getId(), other)) d.setHostile(f.getId(), other);
            }
        }
        if (SERVER != null) {
            for (ServerPlayer p : SERVER.getPlayerList().getPlayers()) {
                ensurePlayerDefaults(p.getUUID());
            }
        }
    }

    /**
     * 补齐玩家阵营的默认敌对关系。
     *   按"默认敌对名单签名"判断是否需要补：名单没变 → 跳过（玩家手动取消过的关系不会被回滚）；
     *   名单变了（改了配置）或老存档没有签名 → 把缺的默认关系显式补上。
     */
    public static void ensurePlayerDefaults(UUID playerUuid) {
        FactionHostilityData d = data();
        if (d == null) return;
        if (playerUuid == null) return;

        Faction faction = FactionRegistry.getOrCreatePlayer(playerUuid);
        if (faction == null) return;

        java.util.Set<ResourceLocation> defaults = faction.getDefaultHostileFactions();
        String signature = signatureOf(defaults);
        String applied = d.getPlayerDefaultSignature(playerUuid);
        if (signature.equals(applied)) return;

        for (ResourceLocation other : defaults) {
            if (!d.isExplicitHostile(faction.getId(), other)) {
                d.setHostile(faction.getId(), other);
            }
        }
        d.setPlayerDefaultSignature(playerUuid, signature);
    }

    /** 默认名单签名：排序后拼串，用来判断配置里的默认名单有没有变过。 */
    private static String signatureOf(java.util.Set<ResourceLocation> ids) {
        java.util.List<String> list = new java.util.ArrayList<>(ids.size());
        for (ResourceLocation id : ids) list.add(id.toString());
        java.util.Collections.sort(list);
        return String.join(",", list);
    }
}
