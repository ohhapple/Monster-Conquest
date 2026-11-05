package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * 竞技对局客户端不展示原版 Toast。第三方模组只要使用标准 Toast 队列，
 * 同样会被清空；直接绘制到 HUD 的自定义提示仍由对应模组负责。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class ArenaClientToastSuppressor {

    private ArenaClientToastSuppressor() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ArenaGameClientState.isArenaSessionContext()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.getToasts().clear();
        }
    }
}
