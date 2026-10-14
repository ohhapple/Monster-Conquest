package com.ohhapple.monster_conquest.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.army.FactionColors;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阵营图案绘制器：绘制 4x4 像素的阵营十字（底色 = 阵营色，十字 = 对比色），
 * 由 {@link FactionBannerLayer} 调用，参数适配不同模型的体型 / 衣服厚度。
 */
public final class FactionMarkPainter {

    private FactionMarkPainter() {}

    private static final ResourceLocation WHITE =
            ResourceLocation.withDefaultNamespace("textures/block/white_concrete.png");

    // 图案尺寸（像素 → 格）：1 像素 = 1/16 格
    private static final float PX   = 1f / 16f;
    private static final float SIZE = 4f * PX;    // 4 像素 = 0.25
    private static final float HALF = SIZE / 2f;  // 0.125
    private static final float BAR  = 1f * PX;    // 1 像素 = 0.0625
    private static final float HBAR = BAR / 2f;   // 0.03125

    // 图案在身体上的垂直中心
    private static final float Y_MID = 0.25f;
    private static final float Y_TOP = Y_MID - HALF;   // 0.125
    private static final float Y_BOT = Y_MID + HALF;   // 0.375
    private static final float Y_H1  = Y_MID - HBAR;   // 0.21875
    private static final float Y_H2  = Y_MID + HBAR;   // 0.28125

    /**
     * 身体正面 + 背面的阵营图案。
     *
     * @param body        身体模型部件
     * @param cornerColor 四角底色（与盾牌底色完全一致）
     * @param crossColor  十字颜色（与盾牌十字完全一致）
     * @param frontZ      身体正面（-z 侧）图案所在的 z（需在衣服表面之外）
     * @param backZ       身体背面（+z 侧）图案所在的 z
     */
    public static void renderBody(PoseStack pose, MultiBufferSource buffer, int packedLight,
                                  ModelPart body,
                                  int cornerColor, int crossColor,
                                  float frontZ, float backZ) {
        float pr = ((cornerColor >> 16) & 0xFF) / 255f;
        float pg = ((cornerColor >> 8) & 0xFF) / 255f;
        float pb = (cornerColor & 0xFF) / 255f;
        float sr = ((crossColor >> 16) & 0xFF) / 255f;
        float sg = ((crossColor >> 8) & 0xFF) / 255f;
        float sb = (crossColor & 0xFF) / 255f;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE));

        // ==================== 身体：正面 + 背面 ====================
        pose.pushPose();
        body.translateAndRotate(pose);
        PoseStack.Pose pBody = pose.last();

        drawCrossY(pBody, vc, packedLight, pr, pg, pb, sr, sg, sb,
                frontZ, frontZ - 0.001f, frontZ - 0.002f);
        drawCrossY(pBody, vc, packedLight, pr, pg, pb, sr, sg, sb,
                backZ, backZ + 0.001f, backZ + 0.002f);

        pose.popPose();
    }

    /**
     * 头顶的阵营图案。
     *
     * @param head     头部模型部件
     * @param headTopY 头顶图案的 y（需在头顶表面之上）
     */
    public static void renderHead(PoseStack pose, MultiBufferSource buffer, int packedLight,
                                  ModelPart head,
                                  int cornerColor, int crossColor,
                                  float headTopY) {
        float pr = ((cornerColor >> 16) & 0xFF) / 255f;
        float pg = ((cornerColor >> 8) & 0xFF) / 255f;
        float pb = (cornerColor & 0xFF) / 255f;
        float sr = ((crossColor >> 16) & 0xFF) / 255f;
        float sg = ((crossColor >> 8) & 0xFF) / 255f;
        float sb = (crossColor & 0xFF) / 255f;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(WHITE));

        // ==================== 头顶：x-z 平面十字 ====================
        pose.pushPose();
        head.translateAndRotate(pose);
        PoseStack.Pose pHead = pose.last();

        // 底色
        quad(vc, pHead, packedLight, pr, pg, pb,
                -HALF, headTopY, -HALF,
                HALF, headTopY, -HALF,
                HALF, headTopY,  HALF,
                -HALF, headTopY,  HALF);
        // 竖条（沿 z 轴）
        quad(vc, pHead, packedLight, sr, sg, sb,
                -HBAR, headTopY - 0.001f, -HALF,
                HBAR, headTopY - 0.001f, -HALF,
                HBAR, headTopY - 0.001f,  HALF,
                -HBAR, headTopY - 0.001f,  HALF);
        // 横条（沿 x 轴）
        quad(vc, pHead, packedLight, sr, sg, sb,
                -HALF, headTopY - 0.002f, -HBAR,
                HALF, headTopY - 0.002f, -HBAR,
                HALF, headTopY - 0.002f,  HBAR,
                -HALF, headTopY - 0.002f,  HBAR);

        pose.popPose();
    }

    private static void drawCrossY(PoseStack.Pose pose, VertexConsumer vc, int packedLight,
                                   float pr, float pg, float pb,
                                   float sr, float sg, float sb,
                                   float zBase, float zV, float zH) {
        // 底色
        quad(vc, pose, packedLight, pr, pg, pb,
                -HALF, Y_TOP, zBase,
                HALF, Y_TOP, zBase,
                HALF, Y_BOT, zBase,
                -HALF, Y_BOT, zBase);
        // 竖条
        quad(vc, pose, packedLight, sr, sg, sb,
                -HBAR, Y_TOP, zV,
                HBAR, Y_TOP, zV,
                HBAR, Y_BOT, zV,
                -HBAR, Y_BOT, zV);
        // 横条
        quad(vc, pose, packedLight, sr, sg, sb,
                -HALF, Y_H1, zH,
                HALF, Y_H1, zH,
                HALF, Y_H2, zH,
                -HALF, Y_H2, zH);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int packedLight,
                             float r, float g, float b,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4) {
        vertex(vc, pose, packedLight, x1, y1, z1, 0f, 0f, r, g, b);
        vertex(vc, pose, packedLight, x2, y2, z2, 1f, 0f, r, g, b);
        vertex(vc, pose, packedLight, x3, y3, z3, 1f, 1f, r, g, b);
        vertex(vc, pose, packedLight, x4, y4, z4, 0f, 1f, r, g, b);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, int packedLight,
                               float x, float y, float z, float u, float v,
                               float r, float g, float b) {
        vc.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, 1.0f)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0f, 0f, 1f);
    }

    // ===================== 颜色 =====================

    /** 该实体的四角底色（阵营）。 */
    public static int cornerColor(LivingEntity e) {
        return FactionColors.bannerCornerColor(factionOf(e));
    }

    /** 该实体的十字颜色（阵营）。 */
    public static int crossColor(LivingEntity e) {
        return FactionColors.bannerCrossColor(factionOf(e));
    }

    private static ResourceLocation factionOf(LivingEntity e) {
        if (e instanceof AbstractArmySoldier s) return s.getFactionId();
        if (e instanceof AbstractArmyCommander c) return c.getFactionId();
        return null;
    }
}
