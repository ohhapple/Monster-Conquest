package com.ohhapple.monster_conquest.player;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 所有玩家指挥状态的全局管理器。每个玩家独立。
 * 与 PlayerArmyStateData 联合做持久化。
 */
public final class PlayerArmyManager {

    private static final Map<UUID, PlayerCommandState> STATES = new ConcurrentHashMap<>();

    /** 每个玩家上次刷新"有士兵的小队"的游戏刻。 */
    private static final Map<UUID, Long> LAST_SQUAD_REFRESH = new ConcurrentHashMap<>();
    /** 同一玩家的连续刷新最小间隔（tick）。5 tick = 0.25 秒，玩家感知不到。 */
    private static final int SQUAD_REFRESH_COOLDOWN_TICKS = 5;

    @Nullable private static MinecraftServer server;

    private PlayerArmyManager() {}

    public static void setServer(@Nullable MinecraftServer s) {
        server = s;
    }

    public static PlayerCommandState get(UUID playerUuid) {
        return STATES.computeIfAbsent(playerUuid, PlayerCommandState::new);
    }

    @Nullable
    public static PlayerCommandState getIfPresent(UUID playerUuid) {
        return STATES.get(playerUuid);
    }

    public static void clear(UUID playerUuid) {
        STATES.remove(playerUuid);
        LAST_SQUAD_REFRESH.remove(playerUuid);
        if (server != null) {
            PlayerArmyStateData.get(server).removeState(playerUuid);
        }
    }

    public static void clearAll() {
        STATES.clear();
        LAST_SQUAD_REFRESH.clear();
    }

    public static Map<UUID, PlayerCommandState> all() {
        return STATES;
    }

    // ===================== 小队占用刷新 =====================

    /**
     * 扫描附近属于该玩家的士兵，把"有士兵的小队"写回 state。
     *   - 带 5 tick 节流，避免连续命令造成重复大范围扫描
     *   - 只更新集合，不清除已存在的状态
     */
    public static void refreshOccupiedSquads(ServerPlayer player) {
        PlayerCommandState state = getIfPresent(player.getUUID());
        if (state == null) return;

        long now = player.level().getGameTime();
        Long last = LAST_SQUAD_REFRESH.get(player.getUUID());
        if (last != null && now - last < SQUAD_REFRESH_COOLDOWN_TICKS) return;
        LAST_SQUAD_REFRESH.put(player.getUUID(), now);

        Set<Integer> occupied = new HashSet<>();
        double range = GeneralConfig.armyFollowRange;
        for (AbstractArmySoldier s : player.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                player.getBoundingBox().inflate(range),
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null)))) {
            if (s.getSquadId() > 0) occupied.add(s.getSquadId());
        }

        // 单兵隐藏小队自动解散（只剩 1 人且空闲时），避免占用隐藏队号
        if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            state.normalizeLoneHiddenSquads(player, sl, now);
        }

        state.setOccupiedSquads(occupied);
    }

    /** 强制刷新（用于召唤后立即生效 / 需要立即拿到最新数据时）。 */
    public static void forceRefreshOccupiedSquads(ServerPlayer player) {
        LAST_SQUAD_REFRESH.remove(player.getUUID());
        refreshOccupiedSquads(player);
    }

    // ===================== 持久化 =====================

    public static void loadFromDisk() {
        if (server == null) return;
        STATES.clear();
        LAST_SQUAD_REFRESH.clear();
        PlayerArmyStateData data = PlayerArmyStateData.get(server);
        for (UUID uuid : data.allUuids()) {
            CompoundTag tag = data.getState(uuid);
            if (tag == null) continue;
            PlayerCommandState s = new PlayerCommandState(uuid);
            s.load(tag);
            STATES.put(uuid, s);
        }
    }

    public static void saveToDisk() {
        if (server == null) return;
        PlayerArmyStateData data = PlayerArmyStateData.get(server);
        Map<UUID, CompoundTag> snapshot = new HashMap<>();
        for (Map.Entry<UUID, PlayerCommandState> e : STATES.entrySet()) {
            snapshot.put(e.getKey(), e.getValue().save());
        }
        data.replaceAll(snapshot);
    }

    public static void saveOne(UUID uuid) {
        if (server == null) return;
        PlayerArmyStateData data = PlayerArmyStateData.get(server);
        PlayerCommandState s = STATES.get(uuid);
        if (s == null) data.removeState(uuid);
        else data.putState(uuid, s.save());
    }
}
