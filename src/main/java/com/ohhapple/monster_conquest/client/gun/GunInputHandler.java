package com.ohhapple.monster_conquest.client.gun;

import com.ohhapple.monster_conquest.gun.GunItem;
import com.ohhapple.monster_conquest.network.serverbound.gun.GunFirePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端：按住左键时持续发送开火请求（服务端按射速节流）。 */
public final class GunInputHandler {

    private GunInputHandler() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        if (!mc.options.keyAttack.isDown()) return;

        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof GunItem gun)) return;
        if (player.getCooldowns().isOnCooldown(gun)) return;

        Vec3 look = player.getLookAngle();
        PacketDistributor.sendToServer(new GunFirePayload(
                (float) look.x, (float) look.y, (float) look.z));
    }
}
