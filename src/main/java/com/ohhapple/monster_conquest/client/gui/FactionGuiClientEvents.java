package com.ohhapple.monster_conquest.client.gui;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionAllySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionHostilitySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class FactionGuiClientEvents {

    public static volatile Map<String, Set<String>> CACHE = new HashMap<>();
    public static volatile Map<String, Set<String>> ALLY_CACHE = new HashMap<>();

    public static void handleSnapshot(FactionHostilitySnapshotPayload payload, IPayloadContext ctx) {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var e : payload.hostility().entrySet()) copy.put(e.getKey(), new HashSet<>(e.getValue()));
        CACHE = copy;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FactionScreen fs) fs.updateHostility(copy);
    }

    public static void handleAllySnapshot(FactionAllySnapshotPayload payload, IPayloadContext ctx) {
        Map<String, Set<String>> copy = new HashMap<>();
        for (var e : payload.ally().entrySet()) copy.put(e.getKey(), new HashSet<>(e.getValue()));
        ALLY_CACHE = copy;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FactionScreen fs) fs.updateAlly(copy);
    }

    public static void handleOpen(OpenFactionGuiPayload payload, IPayloadContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new FactionScreen(CACHE, ALLY_CACHE));
    }

    @SubscribeEvent
    public static void onDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
        CACHE = new HashMap<>();
        ALLY_CACHE = new HashMap<>();

        // 重置轮盘的 static 选中队号：避免连到 B 服务器时还残留 A 服务器的选中状态
        CommandWheelScreen.resetSquadSelection();
    }
}
