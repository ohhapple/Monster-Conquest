package com.ohhapple.monster_conquest.army.relation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * 阵营结盟数据（单向存储）。
 *   key = 阵营 A，value = A 主动结盟的阵营集合。
 *   A 与 B 结盟 ≠ B 与 A 结盟。
 *   语义（单向）：谁结盟对方，谁就不攻击 / 不误伤对方。
 *     · A 结盟 B → A 不打 B（A 的 canAttack / isFriendlyFire 会拦）
 *     · B 未结盟 A → B 依然可以打 A
 *     · 只有双方都结盟 → 完全互不攻击
 */
public class FactionAllyData extends SavedData {

    private static final String DATA_NAME = "monster_conquest_faction_ally";

    private final Map<ResourceLocation, Set<ResourceLocation>> ally = new HashMap<>();

    public static FactionAllyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(FactionAllyData::new, FactionAllyData::load), DATA_NAME);
    }

    public void setAlly(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return;
        ally.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        setDirty();
    }

    public void clearAlly(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null) return;
        Set<ResourceLocation> s = ally.get(a);
        if (s != null) {
            s.remove(b);
            if (s.isEmpty()) ally.remove(a);
        }
        setDirty();
    }

    public boolean isAlly(ResourceLocation a, ResourceLocation b) {
        if (a == null || b == null || a.equals(b)) return false;
        Set<ResourceLocation> s = ally.get(a);
        return s != null && s.contains(b);
    }

    public void removeFaction(ResourceLocation id) {
        if (id == null) return;
        ally.remove(id);
        for (Set<ResourceLocation> s : ally.values()) s.remove(id);
        setDirty();
    }

    public void clearAll() { ally.clear(); setDirty(); }

    public Set<ResourceLocation> getAllyOf(ResourceLocation id) {
        Set<ResourceLocation> s = ally.get(id);
        return s == null ? Collections.emptySet() : Collections.unmodifiableSet(s);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<ResourceLocation, Set<ResourceLocation>> e : ally.entrySet()) {
            for (ResourceLocation b : e.getValue()) {
                CompoundTag ct = new CompoundTag();
                ct.putString("A", e.getKey().toString());
                ct.putString("B", b.toString());
                list.add(ct);
            }
        }
        tag.put("Pairs", list);
        return tag;
    }

    private static FactionAllyData load(CompoundTag tag, HolderLookup.Provider provider) {
        FactionAllyData data = new FactionAllyData();
        ListTag list = tag.getList("Pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            ResourceLocation a = ResourceLocation.tryParse(ct.getString("A"));
            ResourceLocation b = ResourceLocation.tryParse(ct.getString("B"));
            if (a != null && b != null) {
                data.ally.computeIfAbsent(a, k -> new HashSet<>()).add(b);
            }
        }
        return data;
    }
}
