package com.ohhapple.monster_conquest.client.rts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Set;
import java.util.UUID;

/**
 * RTS 视觉：
 *   · 选中士兵脚下绿色方框
 *   · 右键移动标记处的黄色方块框
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class RtsSelectionRenderer {

    private static final float SR = 0.2f, SG = 1.0f, SB = 0.2f, SA = 0.9f;
    private static final float MR = 1.0f, MG = 0.9f, MB = 0.2f, MA = 0.95f;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!RtsClientState.isRtsMode()) return;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        Set<UUID> selected = RtsClientState.getRenderSelected();
        Vec3 marker = RtsClientState.getMoveMarker();
        if (selected.isEmpty() && marker == null) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffer = mc.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose entry = pose.last();

        VertexConsumer vc = buffer.getBuffer(RenderType.lines());

        if (!selected.isEmpty()) {
            // 用 getEntitiesOfClass 搜索附近士兵（ClientLevel.getEntities() 是 protected）
            AABB search = new AABB(
                    cam.x - 256, cam.y - 256, cam.z - 256,
                    cam.x + 256, cam.y + 256, cam.z + 256);
            for (AbstractArmySoldier s : mc.level.getEntitiesOfClass(
                    AbstractArmySoldier.class, search,
                    s -> s.isAlive() && selected.contains(s.getUUID()))) {
                drawFootBox(vc, entry, s.getBoundingBox());
            }
        }

        if (marker != null) {
            BlockPos bp = BlockPos.containing(marker);
            AABB box = new AABB(bp).inflate(0.02);
            drawBox(vc, entry, box);
        }

        pose.popPose();
        buffer.endBatch(RenderType.lines());
    }

    private static void drawFootBox(VertexConsumer vc, PoseStack.Pose pose, AABB bb) {
        float ex = (float) bb.getXsize() * 0.5f + 0.15f;
        float ez = (float) bb.getZsize() * 0.5f + 0.15f;
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        float y = (float) (bb.minY + 0.05);

        float x1 = (float) (cx - ex);
        float x2 = (float) (cx + ex);
        float z1 = (float) (cz - ez);
        float z2 = (float) (cz + ez);

        line(vc, pose, x1, y, z1, x2, y, z1, SR, SG, SB, SA);
        line(vc, pose, x2, y, z1, x2, y, z2, SR, SG, SB, SA);
        line(vc, pose, x2, y, z2, x1, y, z2, SR, SG, SB, SA);
        line(vc, pose, x1, y, z2, x1, y, z1, SR, SG, SB, SA);
    }

    private static void drawBox(VertexConsumer vc, PoseStack.Pose pose, AABB bb) {
        float x1 = (float) bb.minX, y1 = (float) bb.minY, z1 = (float) bb.minZ;
        float x2 = (float) bb.maxX, y2 = (float) bb.maxY, z2 = (float) bb.maxZ;

        line(vc, pose, x1, y1, z1, x2, y1, z1, MR, MG, MB, MA);
        line(vc, pose, x2, y1, z1, x2, y1, z2, MR, MG, MB, MA);
        line(vc, pose, x2, y1, z2, x1, y1, z2, MR, MG, MB, MA);
        line(vc, pose, x1, y1, z2, x1, y1, z1, MR, MG, MB, MA);

        line(vc, pose, x1, y2, z1, x2, y2, z1, MR, MG, MB, MA);
        line(vc, pose, x2, y2, z1, x2, y2, z2, MR, MG, MB, MA);
        line(vc, pose, x2, y2, z2, x1, y2, z2, MR, MG, MB, MA);
        line(vc, pose, x1, y2, z2, x1, y2, z1, MR, MG, MB, MA);

        line(vc, pose, x1, y1, z1, x1, y2, z1, MR, MG, MB, MA);
        line(vc, pose, x2, y1, z1, x2, y2, z1, MR, MG, MB, MA);
        line(vc, pose, x2, y1, z2, x2, y2, z2, MR, MG, MB, MA);
        line(vc, pose, x1, y1, z2, x1, y2, z2, MR, MG, MB, MA);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float r, float g, float b, float a) {
        vc.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
        vc.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, a).setNormal(pose, 0f, 1f, 0f);
    }
}