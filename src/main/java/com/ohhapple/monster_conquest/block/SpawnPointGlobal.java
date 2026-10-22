package com.ohhapple.monster_conquest.block;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 刷怪点的全局开关：由 /spawnpoints 指令控制，关掉后所有刷怪点都暂停刷怪。
 *   只影响"刷不刷"，不改变每个刷怪点自己的配置与开关状态。
 *
 * 状态存进主世界的存档数据（重启后仍然有效），内存里留一份静态镜像，
 *   供每个刷怪点每 tick 直接读取，不必反复查存档数据。
 */
public final class SpawnPointGlobal {

    private SpawnPointGlobal() {}

    private static final String DATA_NAME = "monster_conquest_spawn_points";

    private static volatile boolean disabled = false;

    /** 是否被指令全局禁止刷怪。 */
    public static boolean isDisabled() { return disabled; }

    /** 服务器启动：把存档里的开关读进内存。 */
    public static void load(MinecraftServer server) {
        disabled = data(server).disabled;
    }

    /** 服务器关闭：清掉内存镜像，避免同一进程换世界时串状态。 */
    public static void reset() {
        disabled = false;
    }

    /** 指令修改全局开关（同时写回存档）。 */
    public static void setDisabled(MinecraftServer server, boolean value) {
        disabled = value;
        Data data = data(server);
        if (data.disabled != value) {
            data.disabled = value;
            data.setDirty();
        }
    }

    private static Data data(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Data::new, Data::load), DATA_NAME);
    }

    /** 存档数据：只有一个开关。 */
    public static final class Data extends SavedData {

        private boolean disabled;

        private static Data load(CompoundTag tag, HolderLookup.Provider registries) {
            Data data = new Data();
            data.disabled = tag.getBoolean("Disabled");
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            tag.putBoolean("Disabled", disabled);
            return tag;
        }
    }
}
