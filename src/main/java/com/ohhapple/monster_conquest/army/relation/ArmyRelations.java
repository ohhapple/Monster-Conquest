package com.ohhapple.monster_conquest.army.relation;

import net.minecraft.world.Difficulty;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ArmyRelations {

    @Nullable private static MinecraftServer SERVER;

    private ArmyRelations() {}

    public static void setServer(@Nullable MinecraftServer server) { SERVER = server; }
    @Nullable public static MinecraftServer getServer() { return SERVER; }

    private static ArmyRelationsData data() {
        return SERVER == null ? null : ArmyRelationsData.get(SERVER);
    }

    public static void setHostile(UUID a, UUID b) {
        // 和平模式：军队之间不建立新的敌对关系
        if (SERVER != null
                && SERVER.getWorldData().getDifficulty() == Difficulty.PEACEFUL) return;
        ArmyRelationsData d = data();
        if (d != null) d.setHostile(a, b);
    }

    public static void clearHostile(UUID a, UUID b) {
        ArmyRelationsData d = data();
        if (d != null) d.clearHostile(a, b);
    }

    public static boolean isHostile(UUID a, UUID b) {
        ArmyRelationsData d = data();
        return d != null && d.isHostile(a, b);
    }

    public static void clear(UUID a) {
        ArmyRelationsData d = data();
        if (d != null) d.clearAll(a);
    }

    public static void clearAll() {
        ArmyRelationsData d = data();
        if (d != null) d.clearAll();
    }
}
