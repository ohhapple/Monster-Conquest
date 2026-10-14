package com.ohhapple.monster_conquest.army;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class FactionRegistry {

    private static final Map<ResourceLocation, Faction> FACTIONS = new LinkedHashMap<>();

    private FactionRegistry() {}

    public static void register(Faction faction) {
        FACTIONS.put(faction.getId(), faction);
    }

    /** 移除已注册阵营（用于删除玩家阵营）。 */
    public static boolean unregister(ResourceLocation id) {
        if (id == null) return false;
        return FACTIONS.remove(id) != null;
    }

    /** 只查表，不创建（避免间接调用产生幽灵阵营）；需要创建请用 getOrCreatePlayer。 */
    @Nullable
    public static Faction get(ResourceLocation id) {
        if (id == null) return null;
        return FACTIONS.get(id);
    }

    public static Faction getOrCreatePlayer(@Nullable UUID player) {
        if (player == null) return null;
        ResourceLocation id = FactionIds.player(player);
        return FACTIONS.computeIfAbsent(id, k -> new PlayerFaction(player));
    }

    public static Collection<Faction> all() {
        return Collections.unmodifiableCollection(FACTIONS.values());
    }
}
