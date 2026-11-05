package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

public class WarHorseRenderer
        extends AbstractHorseRenderer<WarHorseEntity, HorseModel<WarHorseEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_black.png");
    private static final ResourceLocation DYE_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/horse/horse_white.png");

    public WarHorseRenderer(EntityRendererProvider.Context ctx) {
        // AbstractHorseRenderer(ctx, model, scale)：
        //   第三参数 = 模型缩放（阴影半径固定 0.75）。
        //   原版 HorseRenderer 用的就是 1.1F。
        super(ctx, new HorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1F);
        addLayer(new DyeLayer(this));
    }

    @Override
    public ResourceLocation getTextureLocation(WarHorseEntity entity) {
        return TEXTURE;
    }

    private static final class DyeLayer extends
            RenderLayer<WarHorseEntity, HorseModel<WarHorseEntity>> {

        private DyeLayer(
                RenderLayerParent<WarHorseEntity,
                        HorseModel<WarHorseEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer,
                           int packedLight, WarHorseEntity entity,
                           float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            DyeColor color = entity.getDyeColor();
            if (color == null) return;
            int rgb = color.getTextureDiffuseColor() & 0x00FFFFFF;
            VertexConsumer vertices = buffer.getBuffer(
                    RenderType.entityTranslucent(DYE_TEXTURE));
            getParentModel().renderToBuffer(pose, vertices,
                    packedLight, OverlayTexture.NO_OVERLAY,
                    0xA8000000 | rgb);
        }
    }
}
