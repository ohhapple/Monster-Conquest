package com.ohhapple.monster_conquest.army.relation;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class ArmyRelationsData extends SavedData {

    private static final String DATA_NAME = "monster_conquest_army_relations";

    private final Map<UUID, Set<UUID>> hostile = new HashMap<>();

    public static ArmyRelationsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ArmyRelationsData::new, ArmyRelationsData::load), DATA_NAME);
    }

    public void setHostile(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return;
        hostile.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        hostile.computeIfAbsent(b, k -> new HashSet<>()).add(a);
        setDirty();
    }

    public void clearHostile(UUID a, UUID b) {
        if (a == null || b == null) return;
        Set<UUID> sa = hostile.get(a);
        Set<UUID> sb = hostile.get(b);
        if (sa != null) {
            sa.remove(b);
            if (sa.isEmpty()) hostile.remove(a);
        }
        if (sb != null) {
            sb.remove(a);
            if (sb.isEmpty()) hostile.remove(b);
        }
        setDirty();
    }

    public boolean isHostile(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        Set<UUID> s = hostile.get(a);
        return s != null && s.contains(b);
    }

    public void clearAll() { hostile.clear(); setDirty(); }

    public void clearAll(UUID a) {
        if (a == null) return;
        Set<UUID> others = hostile.remove(a);
        if (others == null) return;
        for (UUID b : others) {
            Set<UUID> s = hostile.get(b);
            if (s == null) continue;
            s.remove(a);
            if (s.isEmpty()) hostile.remove(b);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        Set<String> written = new HashSet<>();
        for (Map.Entry<UUID, Set<UUID>> e : hostile.entrySet()) {
            for (UUID other : e.getValue()) {
                String a = e.getKey().toString();
                String b = other.toString();
                String key = a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
                if (!written.add(key)) continue;
                CompoundTag ct = new CompoundTag();
                ct.putUUID("A", e.getKey());
                ct.putUUID("B", other);
                list.add(ct);
            }
        }
        tag.put("Pairs", list);
        return tag;
    }

    private static ArmyRelationsData load(CompoundTag tag, HolderLookup.Provider provider) {
        ArmyRelationsData data = new ArmyRelationsData();
        ListTag list = tag.getList("Pairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            if (ct.hasUUID("A") && ct.hasUUID("B")) {
                UUID a = ct.getUUID("A");
                UUID b = ct.getUUID("B");
                data.hostile.computeIfAbsent(a, k -> new HashSet<>()).add(b);
                data.hostile.computeIfAbsent(b, k -> new HashSet<>()).add(a);
            }
        }
        return data;
    }
}