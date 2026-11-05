package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.arena.entity.ArenaWindProjectile;
import net.minecraft.client.model.WindChargeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaWindChargeRenderer
        extends EntityRenderer<ArenaWindProjectile> {

    private static final float MIN_CAMERA_DISTANCE_SQUARED =
            Mth.square(3.5f);
    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/projectiles/wind_charge.png");
    private final WindChargeModel model;

    public ArenaWindChargeRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        model = new WindChargeModel(
                context.bakeLayer(ModelLayers.WIND_CHARGE));
    }

    @Override
    public void render(ArenaWindProjectile entity, float entityYaw,
                       float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        if (entity.tickCount < 2
                && entityRenderDispatcher.camera.getEntity()
                .distanceToSqr(entity)
                < MIN_CAMERA_DISTANCE_SQUARED) {
            return;
        }
        float age = entity.tickCount + partialTick;
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.breezeWind(TEXTURE, xOffset(age) % 1.0f, 0.0f));
        model.setupAnim(entity, 0.0f, 0.0f, age, 0.0f, 0.0f);
        pose.pushPose();
        float scale = entity.isGaleProjectile() ? 3.84f : 1.0f;
        pose.scale(scale, scale, scale);
        model.renderToBuffer(pose, vertices, packedLight,
                OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffer,
                packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(
            ArenaWindProjectile entity) {
        return TEXTURE;
    }

    private static float xOffset(float ageInTicks) {
        return ageInTicks * 0.03f;
    }
}
