package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.path.ClientPathPreview;
import com.ohhapple.monster_conquest.item.PathToolItem;
import com.ohhapple.monster_conquest.path.PathMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.List;

/**
 * 手持路径点工具时，把"当前选中目标"的巡逻路径画在世界上：
 *   · 每个点一个绿色小方框 + 上方的序号（第几个点）；
 *   · 相邻点之间连一条线，末端带箭头表示移动方向；
 *   · 巡逻模式额外画一条"最后一个点 → 第一个点"的回环箭头。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class PathPreviewRenderer {

    private static final float R = 0.35f, G = 1.0f, B = 0.45f, A = 0.9f;
    private static final float BOX = 0.18f;
    private static final double ARROW_LEN = 1.1;
    private static final double ARROW_WING = 0.45;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // 只在手持路径点工具时显示
        if (!(mc.player.getMainHandItem().getItem() instanceof PathToolItem)
                && !(mc.player.getOffhandItem().getItem() instanceof PathToolItem)) {
            return;
        }

        List<Vec3> points = ClientPathPreview.points();
        if (points.isEmpty()) return;

        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        PoseStack.Pose entry = pose.last();
        VertexConsumer vc = buffer.getBuffer(RenderType.lines());

        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            // 小方框
            box(vc, entry, point);
            // 与下一个点连线 + 箭头（巡逻模式最后回到第一个点）
            boolean last = i == points.size() - 1;
            if (!last) {
                arrow(vc, entry, point, points.get(i + 1));
            } else if (ClientPathPreview.mode() == PathMode.PATROL && points.size() > 1) {
                arrow(vc, entry, point, points.get(0));
            }
        }

        pose.popPose();
        buffer.endBatch(RenderType.lines());

        // 序号：世界空间里的公告板文字（每次单独推一次位姿）
        for (int i = 0; i < points.size(); i++) {
            Vec3 point = points.get(i);
            pose.pushPose();
            pose.translate(point.x - cam.x, point.y - cam.y + 0.65, point.z - cam.z);
            pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            pose.scale(-0.025f, -0.025f, 0.025f);
            Matrix4f matrix = pose.last().pose();
            Component text = Component.literal(String.valueOf(i + 1));
            float x = -mc.font.width(text) / 2.0f;
            mc.font.drawInBatch(text, x, 0.0f, 0xFFFFFF, false, matrix, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            pose.popPose();
        }
        buffer.endBatch();
    }

    private static void box(VertexConsumer vc, PoseStack.Pose pose, Vec3 point) {
        float x1 = (float) (point.x - BOX), x2 = (float) (point.x + BOX);
        float y1 = (float) (point.y + 0.02), y2 = (float) (point.y + 0.25);
        float z1 = (float) (point.z - BOX), z2 = (float) (point.z + BOX);

        line(vc, pose, x1, y1, z1, x2, y1, z1);
        line(vc, pose, x2, y1, z1, x2, y1, z2);
        line(vc, pose, x2, y1, z2, x1, y1, z2);
        line(vc, pose, x1, y1, z2, x1, y1, z1);
        line(vc, pose, x1, y2, z1, x2, y2, z1);
        line(vc, pose, x2, y2, z1, x2, y2, z2);
        line(vc, pose, x2, y2, z2, x1, y2, z2);
        line(vc, pose, x1, y2, z2, x1, y2, z1);
        line(vc, pose, x1, y1, z1, x1, y2, z1);
        line(vc, pose, x2, y1, z1, x2, y2, z1);
        line(vc, pose, x2, y1, z2, x2, y2, z2);
        line(vc, pose, x1, y1, z2, x1, y2, z2);
    }

    /** 从 from 到 to 画一条线，并在 to 附近画箭头。 */
    private static void arrow(VertexConsumer vc, PoseStack.Pose pose, Vec3 from, Vec3 to) {
        Vec3 start = from.add(0, 0.12, 0);
        Vec3 end = to.add(0, 0.12, 0);
        line(vc, pose, (float) start.x, (float) start.y, (float) start.z,
                (float) end.x, (float) end.y, (float) end.z);

        Vec3 dir = end.subtract(start);
        if (dir.lengthSqr() < 1.0e-6) return;
        dir = dir.normalize();
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = dir.cross(up).normalize().scale(ARROW_WING);

        Vec3 base = end.subtract(dir.scale(ARROW_LEN));
        Vec3 wing1 = base.add(side);
        Vec3 wing2 = base.subtract(side);
        line(vc, pose, (float) end.x, (float) end.y, (float) end.z,
                (float) wing1.x, (float) wing1.y, (float) wing1.z);
        line(vc, pose, (float) end.x, (float) end.y, (float) end.z,
                (float) wing2.x, (float) wing2.y, (float) wing2.z);
    }

    private static void line(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2) {
        vc.addVertex(pose.pose(), x1, y1, z1).setColor(R, G, B, A).setNormal(pose, 0f, 1f, 0f);
        vc.addVertex(pose.pose(), x2, y2, z2).setColor(R, G, B, A).setNormal(pose, 0f, 1f, 0f);
    }
}
