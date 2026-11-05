package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ohhapple.monster_conquest.arena.entity.ArenaWither;
import com.ohhapple.monster_conquest.arena.entity.ArenaWitherSkull;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.SkullModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.EnergySwirlLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaWitherRenderers {

    private ArenaWitherRenderers() {
    }

    public static final class WitherRenderer extends
            MobRenderer<ArenaWither, ArenaWitherModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/wither/wither.png");

        public WitherRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaWitherModel(
                    context.bakeLayer(ModelLayers.WITHER)), 1.0f);
            addLayer(new WitherArmorLayer(this, context));
        }

        @Override
        protected int getBlockLightLevel(ArenaWither entity,
                                         BlockPos pos) {
            return 15;
        }

        @Override
        protected void scale(ArenaWither entity, PoseStack pose,
                             float partialTick) {
            pose.scale(1.65f, 1.65f, 1.65f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaWither entity) {
            return TEXTURE;
        }
    }

    public static final class WitherSkullRenderer extends
            EntityRenderer<ArenaWitherSkull> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/wither/wither.png");
        private final SkullModel model;

        public WitherSkullRenderer(
                EntityRendererProvider.Context context) {
            super(context);
            model = new SkullModel(
                    context.bakeLayer(ModelLayers.WITHER_SKULL));
        }

        public static LayerDefinition createSkullLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            root.addOrReplaceChild("head",
                    CubeListBuilder.create()
                            .texOffs(0, 35)
                            .addBox(-4.0f, -8.0f, -4.0f,
                                    8.0f, 8.0f, 8.0f),
                    net.minecraft.client.model.geom.PartPose.ZERO);
            return LayerDefinition.create(mesh, 64, 64);
        }

        @Override
        protected int getBlockLightLevel(
                ArenaWitherSkull entity, BlockPos pos) {
            return 15;
        }

        @Override
        public void render(ArenaWitherSkull entity, float yaw,
                           float partialTick, PoseStack pose,
                           MultiBufferSource buffer, int packedLight) {
            pose.pushPose();
            pose.scale(-1.0f, -1.0f, 1.0f);
            float yRot = Mth.rotLerp(
                    partialTick, entity.yRotO, entity.getYRot());
            float xRot = Mth.lerp(
                    partialTick, entity.xRotO, entity.getXRot());
            VertexConsumer vertices = buffer.getBuffer(
                    model.renderType(TEXTURE));
            model.setupAnim(0.0f, yRot, xRot);
            model.renderToBuffer(pose, vertices, packedLight,
                    net.minecraft.client.renderer.texture
                            .OverlayTexture.NO_OVERLAY);
            pose.popPose();
            super.render(entity, yaw, partialTick, pose, buffer,
                    packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(
                ArenaWitherSkull entity) {
            return TEXTURE;
        }
    }

    private static final class WitherArmorLayer
            extends EnergySwirlLayer<ArenaWither, ArenaWitherModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/wither/wither_armor.png");
        private final ArenaWitherModel model;

        private WitherArmorLayer(
                RenderLayerParent<ArenaWither,
                        ArenaWitherModel> parent,
                EntityRendererProvider.Context context) {
            super(parent);
            model = new ArenaWitherModel(
                    context.bakeLayer(ModelLayers.WITHER_ARMOR));
        }

        @Override
        protected float xOffset(float ageInTicks) {
            return Mth.cos(ageInTicks * 0.02f) * 3.0f;
        }

        @Override
        protected ResourceLocation getTextureLocation() {
            return TEXTURE;
        }

        @Override
        protected EntityModel<ArenaWither> model() {
            return model;
        }
    }

    private static final class ArenaWitherModel
            extends HierarchicalModel<ArenaWither> {
        private final ModelPart root;
        private final ModelPart centerHead;
        private final ModelPart rightHead;
        private final ModelPart leftHead;
        private final ModelPart ribcage;
        private final ModelPart tail;

        private ArenaWitherModel(ModelPart root) {
            this.root = root;
            ribcage = root.getChild("ribcage");
            tail = root.getChild("tail");
            centerHead = root.getChild("center_head");
            rightHead = root.getChild("right_head");
            leftHead = root.getChild("left_head");
        }

        @Override
        public void setupAnim(ArenaWither entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            float sway = Mth.cos(ageInTicks * 0.1f);
            ribcage.xRot = (0.065f + 0.05f * sway)
                    * (float) Math.PI;
            tail.setPos(-2.0f,
                    6.9f + Mth.cos(ribcage.xRot) * 10.0f,
                    -0.5f + Mth.sin(ribcage.xRot) * 10.0f);
            tail.xRot = (0.265f + 0.1f * sway)
                    * (float) Math.PI;
            centerHead.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            centerHead.xRot = headPitch * Mth.DEG_TO_RAD;
            rightHead.yRot = (entity.getHeadYRot(0)
                    - entity.yBodyRot) * Mth.DEG_TO_RAD;
            rightHead.xRot = entity.getHeadXRot(0) * Mth.DEG_TO_RAD;
            leftHead.yRot = (entity.getHeadYRot(1)
                    - entity.yBodyRot) * Mth.DEG_TO_RAD;
            leftHead.xRot = entity.getHeadXRot(1) * Mth.DEG_TO_RAD;
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }
}
