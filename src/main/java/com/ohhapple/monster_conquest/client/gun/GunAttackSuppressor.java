package com.ohhapple.monster_conquest.client.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.gun.GunItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** 手持枪械时，左键不触发原版攻击 / 挖掘，也不播放挥臂动画。 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class GunAttackSuppressor {

    private GunAttackSuppressor() {}

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) return;

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (!(player.getMainHandItem().getItem() instanceof GunItem)) return;

        event.setCanceled(true);
        event.setSwingHand(false);
    }
}
