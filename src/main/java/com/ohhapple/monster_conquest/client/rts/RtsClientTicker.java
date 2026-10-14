package com.ohhapple.monster_conquest.client.rts;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.gun.GunClientState;
import com.ohhapple.monster_conquest.network.serverbound.rts.RtsTogglePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class RtsClientTicker {

    private static long lastNano = 0;

    @SubscribeEvent
    public static void onRenderFramePre(RenderFrameEvent.Pre event) {
        if (!RtsClientState.isRtsMode()) {
            lastNano = 0;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        long now = System.nanoTime();
        if (lastNano == 0) lastNano = now;
        double dt = (now - lastNano) / 1_000_000_000.0;
        lastNano = now;
        if (dt > 0.1) dt = 0.1;
        if (dt < 0) dt = 0;

        double fwd = 0, right = 0, up = 0;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_W)) fwd   += 1;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_S)) fwd   -= 1;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_A)) right -= 1;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_D)) right += 1;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_SPACE))       up += 1;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_LEFT_SHIFT))  up -= 1;
        if (RtsClientState.isKeyPressed(GLFW.GLFW_KEY_RIGHT_SHIFT)) up -= 1;

        if (fwd != 0 || right != 0 || up != 0) {
            RtsCameraController.move(fwd, right, up, dt);
        }

        VirtualCamera cam = RtsClientState.getCamera();
        if (cam == null) return;
        double x = RtsCameraController.getCamX();
        double y = RtsCameraController.getCamY();
        double z = RtsCameraController.getCamZ();
        float cy = RtsCameraController.getYaw();
        float cp = RtsCameraController.getPitch();

        cam.setPos(x, y, z);
        cam.xo = x; cam.yo = y; cam.zo = z;
        cam.xOld = x; cam.yOld = y; cam.zOld = z;

        cam.setYRot(cy); cam.yRotO = cy;
        cam.setXRot(cp); cam.xRotO = cp;
    }

    /** RTS 模式不渲染手臂。 */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (RtsClientState.isRtsMode()) {
            event.setCanceled(true);
        }
    }

    public static void enterRts(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        if (RtsClientState.isRtsMode()) return;

        // 开镜中切 RTS：先退出狙击镜 —— 否则 RTS 里会一直带着开镜的放大倍率
        //   和降低过的鼠标灵敏度（松开右键要等回到第一人称视角才会生效）。
        GunClientState.forceExitScope(mc);

        RtsClientState.setRtsMode(true);
        RtsCameraController.init(
                mc.player.position(),
                mc.player.getYRot(),
                mc.player.getXRot());

        VirtualCamera cam = new VirtualCamera(mc.level);
        double x = RtsCameraController.getCamX();
        double y = RtsCameraController.getCamY();
        double z = RtsCameraController.getCamZ();
        float cy = RtsCameraController.getYaw();
        float cp = RtsCameraController.getPitch();

        cam.setPos(x, y, z);
        cam.xo = x; cam.yo = y; cam.zo = z;
        cam.xOld = x; cam.yOld = y; cam.zOld = z;
        cam.setYRot(cy); cam.yRotO = cy;
        cam.setXRot(cp); cam.xRotO = cp;

        mc.level.addFreshEntity(cam);
        RtsClientState.setCamera(cam);
        mc.setCameraEntity(cam);

        PacketDistributor.sendToServer(new RtsTogglePayload(true));
        mc.setScreen(new RtsScreen());
    }

    public static void exitRts(Minecraft mc) {
        if (!RtsClientState.isRtsMode()) return;

        VirtualCamera cam = RtsClientState.getCamera();
        if (cam != null) {
            cam.discard();
            RtsClientState.setCamera(null);
        }
        if (mc.player != null) {
            mc.setCameraEntity(mc.player);
        }

        RtsClientState.setRtsMode(false);
        RtsCameraController.reset();
        RtsClientState.clearKeys();
        lastNano = 0;

        PacketDistributor.sendToServer(new RtsTogglePayload(false));
    }
}
