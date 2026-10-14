package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.projectile.ArmyBullet;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** 子弹实体渲染：只画一个正对摄像机的极小贴片，近距离不渲染。 */
public class ArmyBulletRenderer extends EntityRenderer<ArmyBullet> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/bullet.png");

    /** 贴片缩放（很小，只作为曳光中一颗飞行的弹头）。 */
    private static final float SCALE = 0.09f;

    /** 距摄像机多近就不渲染（格）——第一人称 5 格内不显示子弹。 */
    private static final double MIN_CAMERA_DISTANCE_SQ = 5.0 * 5.0;

    public ArmyBulletRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ArmyBullet bullet, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        // 摄像机与子弹插值位置的距离过近（8 格内）不绘制
        Vec3 cameraPos = this.entityRenderDispatcher.camera.getPosition();
        double bulletX = Mth.lerp(partialTicks, bullet.xo, bullet.getX());
        double bulletY = Mth.lerp(partialTicks, bullet.yo, bullet.getY());
        double bulletZ = Mth.lerp(partialTicks, bullet.zo, bullet.getZ());
        if (cameraPos.distanceToSqr(bulletX, bulletY, bulletZ) < MIN_CAMERA_DISTANCE_SQ) {
            return;
        }

        poseStack.pushPose();
        poseStack.scale(SCALE, SCALE, SCALE);

        // billboarding：始终正对摄像机
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer builder = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        addVertex(builder, pose, -1, -1, 0, 0, 1, light);
        addVertex(builder, pose, 1, -1, 0, 1, 1, light);
        addVertex(builder, pose, 1, 1, 0, 1, 0, light);
        addVertex(builder, pose, -1, 1, 0, 0, 0, light);

        poseStack.popPose();
        super.render(bullet, entityYaw, partialTicks, poseStack, buffer, light);
    }

    private static void addVertex(VertexConsumer builder, PoseStack.Pose pose,
                                  float x, float y, float z,
                                  float u, float v, int light) {
        builder.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0, 0, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(ArmyBullet entity) {
        return TEXTURE;
    }
}
