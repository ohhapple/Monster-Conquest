package com.ohhapple.monster_conquest.client.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/** 客户端：开火输入、装填状态 tick、狙击枪开镜变焦。 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class GunClientEvents {

    private GunClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        GunClientState.tick();
        GunInputHandler.tick();
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        // 按帧更新开镜动画，避免 20Hz 步进造成的卡顿
        float deltaTicks = Minecraft.getInstance().getTimer().getRealtimeDeltaTicks();
        GunClientState.updateScopeZoom(deltaTicks);

        float zoom = GunClientState.scopeZoom();
        if (zoom <= 0.0f) return;
        // 满倍率时视野缩小到 25%（约 4 倍放大）
        event.setFOV(event.getFOV() * (1.0 - 0.75 * zoom));
    }
}
