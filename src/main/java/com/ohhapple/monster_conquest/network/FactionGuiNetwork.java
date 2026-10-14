package com.ohhapple.monster_conquest.network;

import com.ohhapple.monster_conquest.army.relation.FactionAllyData;
import com.ohhapple.monster_conquest.army.relation.FactionHostilityData;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionAllySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionHostilitySnapshotPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class FactionGuiNetwork {
    private FactionGuiNetwork() {}

    public static void sendSnapshot(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        FactionHostilityData hostData = FactionHostilityData.get(server);
        FactionAllyData allyData = FactionAllyData.get(server);

        // ==================== 敌对快照 ====================
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (var faction : FactionRegistry.all()) {
            ResourceLocation id = faction.getId();
            snapshot.put(id.toString(), copyHostileOf(hostData, id));
        }
        snapshot.put(FactionIds.PLAYER.toString(), copyHostileOf(hostData, FactionIds.PLAYER));
        ResourceLocation own = PlayerFactionRegistry.factionIdOf(player);
        snapshot.computeIfAbsent(own.toString(), k -> copyHostileOf(hostData, own));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ResourceLocation pid = FactionIds.player(p.getUUID());
            snapshot.computeIfAbsent(pid.toString(), k -> copyHostileOf(hostData, pid));
        }
        PacketDistributor.sendToPlayer(player, new FactionHostilitySnapshotPayload(snapshot));

        // ==================== 结盟快照 ====================
        Map<String, Set<String>> allySnap = new HashMap<>();
        for (var faction : FactionRegistry.all()) {
            ResourceLocation id = faction.getId();
            allySnap.put(id.toString(), copyAllyOf(allyData, id));
        }
        allySnap.put(FactionIds.PLAYER.toString(), copyAllyOf(allyData, FactionIds.PLAYER));
        allySnap.computeIfAbsent(own.toString(), k -> copyAllyOf(allyData, own));
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            ResourceLocation pid = FactionIds.player(p.getUUID());
            allySnap.computeIfAbsent(pid.toString(), k -> copyAllyOf(allyData, pid));
        }
        PacketDistributor.sendToPlayer(player, new FactionAllySnapshotPayload(allySnap));
    }

    private static Set<String> copyHostileOf(FactionHostilityData data, ResourceLocation id) {
        Set<String> out = new HashSet<>();
        for (ResourceLocation other : data.getHostileOf(id)) out.add(other.toString());
        return out;
    }

    private static Set<String> copyAllyOf(FactionAllyData data, ResourceLocation id) {
        Set<String> out = new HashSet<>();
        for (ResourceLocation other : data.getAllyOf(id)) out.add(other.toString());
        return out;
    }

    /**
     * 面向玩家的阵营显示名（服务端消息用）。
     *   NPC 阵营读语言文件；玩家阵营区分"我"与在线玩家名；查不到的用退回文本。
     */
    public static Component describeFaction(ResourceLocation id, ServerPlayer viewer) {
        if (FactionIds.isBigPlayer(id)) {
            return Component.translatable("faction.monster_conquest.player_all");
        }
        UUID owner = FactionIds.parsePlayer(id);
        if (owner != null) {
            if (owner.equals(viewer.getUUID())) {
                return Component.translatable("faction.monster_conquest.player_self");
            }
            ServerPlayer online = viewer.getServer() == null
                    ? null
                    : viewer.getServer().getPlayerList().getPlayer(owner);
            if (online != null) return Component.literal(online.getGameProfile().getName());
            return Component.literal("player/" + owner.toString().substring(0, 8));
        }
        return Component.translatableWithFallback(
                "faction.monster_conquest." + id.getPath(), id.toString());
    }
}
