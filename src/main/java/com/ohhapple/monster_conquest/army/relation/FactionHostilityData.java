package com.ohhapple.monster_conquest.army.relation;

import com.ohhapple.monster_conquest.army.FactionIds;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FactionHostilityData extends SavedData {

    private static final String DATA_NAME = "monster_conquest_faction_hostility";

    private final Map<ResourceLocation, Set<ResourceLocation>> hostile = new HashMap<>();

    /**
     * 玩家已应用的"默认敌对名单签名"：名单没变过就不再重复套用，
     *   避免玩家手动取消某条默认关系后，下次登录又被回滚；
     *   名单变了（改配置 / 老存档没有签名）就补上缺的默认关系。
     */
    private final Map<UUID, String> playerDefaultSignatures = new HashMap<>();

    /** 游戏内建立的敌对关系（仇恨工具 / 反击等）：重置时只清这些，
     *  不动 N 键 GUI 设置的关系与默认配置。 */
    private final Set<String> learnedPairs = new HashSet<>();

    public static FactionHostilityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(FactionHostilityData::new, FactionHostilityData::load), DATA_NAME);
    }

    public void setHostile(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return;
        hostile.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        hostile.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        setDirty();
    }

    public void clearHostile(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null) return;
        Set<ResourceLocation> sa = hostile.get(a);
        Set<ResourceLocation> sb = hostile.get(b);
        if (sa != null) sa.remove(b);
        if (sb != null) sb.remove(a);
        setDirty();
    }

    /** 记录"游戏内建立"的敌对关系键。 */
    public void markLearned(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return;
        if (learnedPairs.add(pairKey(a, b))) setDirty();
    }

    /** 该对关系由玩家 / 默认配置管理：从"游戏内建立"集合里移除。 */
    public void forgetLearned(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null) return;
        if (learnedPairs.remove(pairKey(a, b))) setDirty();
    }

    /** 只清除游戏内建立的敌对关系（保留 GUI 设置与默认配置）。 */
    public void clearLearnedHostility() {
        if (learnedPairs.isEmpty()) return;
        for (String key : new ArrayList<>(learnedPairs)) {
            int split = key.indexOf('|');
            if (split <= 0) continue;
            ResourceLocation a = ResourceLocation.tryParse(key.substring(0, split));
            ResourceLocation b = ResourceLocation.tryParse(key.substring(split + 1));
            if (a == null || b == null) continue;
            Set<ResourceLocation> sa = hostile.get(a);
            Set<ResourceLocation> sb = hostile.get(b);
            if (sa != null) sa.remove(b);
            if (sb != null) sb.remove(a);
        }
        learnedPairs.clear();
        setDirty();
    }

    private static String pairKey(ResourceLocation a, ResourceLocation b) {
        String sa = a.toString();
        String sb = b.toString();
        return sa.compareTo(sb) <= 0 ? sa + "|" + sb : sb + "|" + sa;
    }

    /**
     * a 是否仇视 b。
     *
     * 大玩家阵营继承规则：
     *   若 a 的敌对列表里包含 PLAYER（大玩家阵营），则 a 仇视任何小玩家阵营 b。
     *   反之，若 b 的敌对列表里包含 PLAYER，则 b 仇视任何小玩家阵营 a。
     *
     * 两个不同的小玩家阵营之间默认互不仇视（除非显式设置过关系）。
     */
    public boolean isHostile(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return false;

        Set<ResourceLocation> sa = hostile.get(a);
        Set<ResourceLocation> sb = hostile.get(b);

        // 1) 直接命中
        if (sa != null && sa.contains(b)) return true;

        // 2) a 仇视大玩家阵营 → a 仇视任何小玩家阵营 b
        if (FactionIds.isPlayer(b) && sa != null && sa.contains(FactionIds.PLAYER)) return true;

        // 3) b 仇视大玩家阵营 → b 仇视任何小玩家阵营 a（对称存储下 a 也仇视 b）
        if (FactionIds.isPlayer(a) && sb != null && sb.contains(FactionIds.PLAYER)) return true;

        return false;
    }

    /**
     * 两个阵营之间是否"显式"存过敌对关系（不含大玩家阵营继承之类的隐式规则）。
     *   GUI 的加/减开关以它为准：隐式敌对（对方配置仇视大玩家阵营等）第一次点击应该被显式写下来。
     */
    public boolean isExplicitHostile(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return false;
        Set<ResourceLocation> sa = hostile.get(a);
        if (sa != null && sa.contains(b)) return true;
        Set<ResourceLocation> sb = hostile.get(b);
        return sb != null && sb.contains(a);
    }

    public Set<ResourceLocation> getHostileOf(ResourceLocation id) {
        Set<ResourceLocation> s = hostile.get(id);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    public void clearAll() { hostile.clear(); learnedPairs.clear(); setDirty(); }

    /** 移除某个阵营及其所有敌对关系。 */
    public void removeFaction(ResourceLocation id) {
        if (id == null) return;
        hostile.remove(id);
        for (Set<ResourceLocation> s : hostile.values()) {
            s.remove(id);
        }
        setDirty();
    }

    // ===================== 玩家初始化标记 =====================

    /** 该玩家上次应用过的默认敌对名单签名；没记录过（含老存档）返回 null。 */
    @Nullable
    public String getPlayerDefaultSignature(UUID uuid) {
        return uuid == null ? null : playerDefaultSignatures.get(uuid);
    }

    public void setPlayerDefaultSignature(UUID uuid, String signature) {
        if (uuid == null) return;
        if (!signature.equals(playerDefaultSignatures.put(uuid, signature))) setDirty();
    }

    public void clearAllInitializedPlayers() {
        if (playerDefaultSignatures.isEmpty()) return;
        playerDefaultSignatures.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        Set<String> written = new HashSet<>();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : hostile.entrySet()) {
            for (ResourceLocation other : e.getValue()) {
                String a = e.getKey().toString();
                String b = other.toString();
                String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
                if (!written.add(key)) continue;
                CompoundTag ct = new CompoundTag();
                ct.putString("A", a);
                ct.putString("B", b);
                list.add(ct);
            }
        }
        tag.put("Pairs", list);

        // 保存玩家已应用的默认敌对名单签名
        ListTag initList = new ListTag();
        for (Map.Entry<UUID, String> e : playerDefaultSignatures.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID("U", e.getKey());
            ct.putString("S", e.getValue());
            initList.add(ct);
        }
        tag.put("InitPlayers", initList);

        // 保存"游戏内建立"的关系键
        ListTag learnedList = new ListTag();
        for (String key : learnedPairs) {
            CompoundTag ct = new CompoundTag();
            ct.putString("K", key);
            learnedList.add(ct);
        }
        tag.put("LearnedPairs", learnedList);

        return tag;
    }

    private static FactionHostilityData load(CompoundTag tag, HolderLookup.Provider provider) {
        FactionHostilityData data = new FactionHostilityData();
        ListTag list = tag.getList("Pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            ResourceLocation a = ResourceLocation.tryParse(ct.getString("A"));
            ResourceLocation b = ResourceLocation.tryParse(ct.getString("B"));
            if (a != null && b != null) {
                data.hostile.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                data.hostile.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }
        }

        // 读取玩家已应用的默认敌对名单签名（老存档只有 UUID → 下次登录会补应用默认关系）
        ListTag initList = tag.getList("InitPlayers", Tag.TAG_COMPOUND);
        for (int i = 0; i < initList.size(); i++) {
            CompoundTag ct = initList.getCompound(i);
            if (ct.hasUUID("U")) {
                data.playerDefaultSignatures.put(ct.getUUID("U"), ct.getString("S"));
            }
        }

        // 读取"游戏内建立"的关系键
        ListTag learnedList = tag.getList("LearnedPairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < learnedList.size(); i++) {
            String key = learnedList.getCompound(i).getString("K");
            if (!key.isEmpty()) data.learnedPairs.add(key);
        }

        return data;
    }
}
