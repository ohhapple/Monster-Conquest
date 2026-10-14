package com.ohhapple.monster_conquest.player;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 玩家指挥状态的存档容器。
 * 数据存在主世界 DataStorage，键 = 玩家 UUID，值 = PlayerCommandState 序列化出的 CompoundTag。
 */
public class PlayerArmyStateData extends SavedData {

    private static final String DATA_NAME = "monster_conquest_player_army_state";

    private final Map<UUID, CompoundTag> states = new HashMap<>();

    public static PlayerArmyStateData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerArmyStateData::new, PlayerArmyStateData::load),
                DATA_NAME);
    }

    public CompoundTag getState(UUID uuid) {
        CompoundTag t = states.get(uuid);
        return t == null ? null : t.copy();
    }

    public void putState(UUID uuid, CompoundTag tag) {
        CompoundTag copy = tag.copy();
        CompoundTag prev = states.get(uuid);
        // 内容没变就不标脏：登出 / 打字重存这类高频调用不该白白触发一次写盘。
        if (prev != null && prev.equals(copy)) return;
        states.put(uuid, copy);
        setDirty();
    }

    public void removeState(UUID uuid) {
        if (states.remove(uuid) != null) setDirty();
    }

    public Set<UUID> allUuids() {
        return new HashSet<>(states.keySet());
    }

    public void replaceAll(Map<UUID, CompoundTag> newStates) {
        // 内容没变就什么都不做：全量保存每 10 秒跑一次，
        //   无条件标脏会让存档数据每 10 秒白写一次盘。
        if (states.equals(newStates)) return;
        states.clear();
        for (Map.Entry<UUID, CompoundTag> e : newStates.entrySet()) {
            states.put(e.getKey(), e.getValue().copy());
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, CompoundTag> e : states.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID("Owner", e.getKey());
            ct.put("State", e.getValue());
            list.add(ct);
        }
        tag.put("States", list);
        return tag;
    }

    private static PlayerArmyStateData load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerArmyStateData data = new PlayerArmyStateData();
        ListTag list = tag.getList("States", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            if (ct.hasUUID("Owner")) {
                data.states.put(ct.getUUID("Owner"), ct.getCompound("State"));
            }
        }
        return data;
    }
}
