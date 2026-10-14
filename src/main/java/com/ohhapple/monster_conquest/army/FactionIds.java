package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class FactionIds {
    private FactionIds() {}

    public static final ResourceLocation UNDEAD   = mc("undead");
    public static final ResourceLocation VILLAGER = mc("villager");
    public static final ResourceLocation ILLAGER  = mc("illager");
    public static final ResourceLocation NETHER   = mc("nether");
    public static final ResourceLocation END      = mc("end");
    public static final ResourceLocation MONSTER  = mc("monster");
    public static final ResourceLocation NEUTRAL  = mc("neutral");

    /** 大玩家阵营：所有小玩家阵营的父阵营。
     *  配置里写 "monster_conquest:player" 即表示"仇视所有玩家"。 */
    public static final ResourceLocation PLAYER   = mc("player");

    public static ResourceLocation mc(String path) {
        return ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, path);
    }

    /** 小玩家阵营 ID（具体某个玩家的）。 */
    public static ResourceLocation player(UUID uuid) {
        return ResourceLocation.fromNamespaceAndPath(
                Monster_conquest.MODID, "player/" + uuid.toString());
    }

    /** 解析小玩家阵营 ID 中的 UUID。非小玩家阵营返回 null。 */
    @Nullable
    public static UUID parsePlayer(@Nullable ResourceLocation id) {
        if (id == null) return null;
        if (!Monster_conquest.MODID.equals(id.getNamespace())) return null;
        String path = id.getPath();
        if (!path.startsWith("player/")) return null;
        try { return UUID.fromString(path.substring("player/".length())); }
        catch (Exception e) { return null; }
    }

    /** 是否是小玩家阵营（带 UUID 的）。 */
    public static boolean isPlayer(@Nullable ResourceLocation id) {
        return parsePlayer(id) != null;
    }

    /** 是否是大玩家阵营（不带 UUID，仅 monster_conquest:player）。 */
    public static boolean isBigPlayer(@Nullable ResourceLocation id) {
        return PLAYER.equals(id);
    }

    /** 是否属于玩家体系（大玩家阵营 或 小玩家阵营）。 */
    public static boolean isAnyPlayerFaction(@Nullable ResourceLocation id) {
        return isBigPlayer(id) || isPlayer(id);
    }
}