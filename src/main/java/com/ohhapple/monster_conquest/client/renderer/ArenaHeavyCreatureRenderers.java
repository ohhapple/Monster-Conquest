package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.google.common.collect.ImmutableList;
import com.ohhapple.monster_conquest.arena.entity.ArenaBreeze;
import com.ohhapple.monster_conquest.arena.entity.ArenaHoglin;
import com.ohhapple.monster_conquest.arena.entity.ArenaPolarBear;
import com.ohhapple.monster_conquest.arena.entity.ArenaRavager;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaHeavyCreatureRenderers {

    private ArenaHeavyCreatureRenderers() {
    }

    public static final class BreezeRenderer extends
            MobRenderer<ArenaBreeze, ArenaBreezeModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/breeze/breeze.png");

        public BreezeRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaBreezeModel(
                    context.bakeLayer(ModelLayers.BREEZE)), 0.5f);
            addLayer(new BreezeWindLayer(context, this));
            addLayer(new BreezeEyesLayer(this));
        }

        @Override
        public void render(ArenaBreeze entity, float entityYaw,
                           float partialTick, PoseStack pose,
                           MultiBufferSource buffer, int packedLight) {
            ArenaBreezeModel model = getModel();
            model.setVisible(model.head(), model.rods());
            super.render(entity, entityYaw, partialTick, pose, buffer,
                    packedLight);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaBreeze entity) {
            return TEXTURE;
        }
    }

    private static final class BreezeWindLayer extends
            RenderLayer<ArenaBreeze, ArenaBreezeModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/breeze/breeze_wind.png");
        private final ArenaBreezeModel model;

        private BreezeWindLayer(
                EntityRendererProvider.Context context,
                RenderLayerParent<ArenaBreeze, ArenaBreezeModel> parent) {
            super(parent);
            model = new ArenaBreezeModel(
                    context.bakeLayer(ModelLayers.BREEZE_WIND));
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer,
                           int packedLight, ArenaBreeze entity,
                           float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            float textureAge = entity.tickCount + partialTick;
            VertexConsumer vertices = buffer.getBuffer(
                    RenderType.breezeWind(TEXTURE,
                            xOffset(textureAge) % 1.0f, 0.0f));
            model.setupAnim(entity, limbSwing, limbSwingAmount,
                    ageInTicks, netHeadYaw, headPitch);
            model.setVisible(model.wind());
            model.renderToBuffer(pose, vertices, packedLight,
                    OverlayTexture.NO_OVERLAY);
        }

        private static float xOffset(float ageInTicks) {
            return ageInTicks * 0.02f;
        }
    }

    private static final class BreezeEyesLayer extends
            RenderLayer<ArenaBreeze, ArenaBreezeModel> {
        private static final RenderType EYES = RenderType.breezeEyes(
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/breeze/breeze_eyes.png"));

        private BreezeEyesLayer(
                RenderLayerParent<ArenaBreeze, ArenaBreezeModel> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer,
                           int packedLight, ArenaBreeze entity,
                           float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            VertexConsumer vertices = buffer.getBuffer(EYES);
            ArenaBreezeModel model = getParentModel();
            model.setVisible(model.head(), model.eyes());
            model.renderToBuffer(pose, vertices, packedLight,
                    OverlayTexture.NO_OVERLAY);
        }
    }

    public static final class RavagerRenderer extends
            MobRenderer<ArenaRavager, ArenaRavagerModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/illager/ravager.png");

        public RavagerRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaRavagerModel(
                    context.bakeLayer(ModelLayers.RAVAGER)), 1.1f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaRavager entity) {
            return TEXTURE;
        }
    }

    public static final class PolarBearRenderer extends
            MobRenderer<ArenaPolarBear, ArenaPolarBearModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/bear/polarbear.png");

        public PolarBearRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaPolarBearModel(
                    context.bakeLayer(ModelLayers.POLAR_BEAR)), 0.9f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaPolarBear entity) {
            return TEXTURE;
        }

        @Override
        protected void scale(ArenaPolarBear entity,
                             com.mojang.blaze3d.vertex.PoseStack pose,
                             float partialTick) {
            pose.scale(1.2f, 1.2f, 1.2f);
            super.scale(entity, pose, partialTick);
        }
    }

    public static final class HoglinRenderer extends
            MobRenderer<ArenaHoglin, ArenaHoglinModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/hoglin/hoglin.png");

        public HoglinRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaHoglinModel(
                    context.bakeLayer(ModelLayers.HOGLIN)), 0.7f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaHoglin entity) {
            return TEXTURE;
        }
    }

    private static final class ArenaBreezeModel
            extends HierarchicalModel<ArenaBreeze> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart eyes;
        private final ModelPart wind;
        private final ModelPart windTop;
        private final ModelPart windMid;
        private final ModelPart windBottom;
        private final ModelPart rods;

        private ArenaBreezeModel(ModelPart root) {
            super(RenderType::entityTranslucent);
            this.root = root;
            this.head = root.getChild("body").getChild("head");
            this.eyes = head.getChild("eyes");
            this.wind = root.getChild("wind_body");
            this.windBottom = wind.getChild("wind_bottom");
            this.windMid = windBottom.getChild("wind_mid");
            this.windTop = windMid.getChild("wind_top");
            this.rods = root.getChild("body").getChild("rods");
        }

        @Override
        public void setupAnim(ArenaBreeze entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            root().getAllParts().forEach(ModelPart::resetPose);
            float spin = ageInTicks * (float) Math.PI * 0.1f;
            windTop.x = Mth.cos(spin) * 0.6f;
            windTop.z = Mth.sin(spin) * 0.6f;
            windMid.x = Mth.sin(spin) * 0.4f;
            windMid.z = Mth.cos(spin) * 0.8f;
            windBottom.x = Mth.cos(spin) * -0.25f;
            windBottom.z = Mth.sin(spin) * -0.25f;
            rods.yRot = spin;
            head.y = 4.0f + Mth.cos(spin) * 0.25f;
        }

        private void setVisible(ModelPart... visibleParts) {
            head.visible = false;
            eyes.visible = false;
            wind.visible = false;
            rods.visible = false;
            for (ModelPart part : visibleParts) {
                part.visible = true;
            }
        }

        private ModelPart head() {
            return head;
        }

        private ModelPart eyes() {
            return eyes;
        }

        private ModelPart wind() {
            return wind;
        }

        private ModelPart rods() {
            return rods;
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }

    private static final class ArenaRavagerModel
            extends HierarchicalModel<ArenaRavager> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart rightHindLeg;
        private final ModelPart leftHindLeg;
        private final ModelPart rightFrontLeg;
        private final ModelPart leftFrontLeg;

        private ArenaRavagerModel(ModelPart root) {
            this.root = root;
            ModelPart neck = root.getChild("neck");
            head = neck.getChild("head");
            rightHindLeg = root.getChild("right_hind_leg");
            leftHindLeg = root.getChild("left_hind_leg");
            rightFrontLeg = root.getChild("right_front_leg");
            leftFrontLeg = root.getChild("left_front_leg");
        }

        @Override
        public void setupAnim(ArenaRavager entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            head.xRot = headPitch * (float) (Math.PI / 180.0);
            head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
            float movement = 0.4f * limbSwingAmount;
            rightHindLeg.xRot = Mth.cos(limbSwing * 0.6662f) * movement;
            leftHindLeg.xRot = Mth.cos(limbSwing * 0.6662f
                    + (float) Math.PI) * movement;
            rightFrontLeg.xRot = Mth.cos(limbSwing * 0.6662f
                    + (float) Math.PI) * movement;
            leftFrontLeg.xRot = Mth.cos(limbSwing * 0.6662f) * movement;
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }

    private static final class ArenaPolarBearModel
            extends QuadrupedModel<ArenaPolarBear> {
        private ArenaPolarBearModel(ModelPart root) {
            super(root, true, 16.0f, 4.0f, 2.25f, 2.0f, 24);
        }
    }

    private static final class ArenaHoglinModel
            extends AgeableListModel<ArenaHoglin> {
        private final ModelPart head;
        private final ModelPart rightEar;
        private final ModelPart leftEar;
        private final ModelPart body;
        private final ModelPart rightFrontLeg;
        private final ModelPart leftFrontLeg;
        private final ModelPart rightHindLeg;
        private final ModelPart leftHindLeg;

        private ArenaHoglinModel(ModelPart root) {
            super(true, 8.0f, 6.0f, 1.9f, 2.0f, 24.0f);
            body = root.getChild("body");
            head = root.getChild("head");
            rightEar = head.getChild("right_ear");
            leftEar = head.getChild("left_ear");
            rightFrontLeg = root.getChild("right_front_leg");
            leftFrontLeg = root.getChild("left_front_leg");
            rightHindLeg = root.getChild("right_hind_leg");
            leftHindLeg = root.getChild("left_hind_leg");
        }

        @Override
        protected Iterable<ModelPart> headParts() {
            return ImmutableList.of(head);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return ImmutableList.of(body, rightFrontLeg, leftFrontLeg,
                    rightHindLeg, leftHindLeg);
        }

        @Override
        public void setupAnim(ArenaHoglin entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            rightEar.zRot = (float) (-Math.PI * 2.0 / 9.0)
                    - limbSwingAmount * Mth.sin(limbSwing);
            leftEar.zRot = (float) (Math.PI * 2.0 / 9.0)
                    + limbSwingAmount * Mth.sin(limbSwing);
            head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
            head.xRot = Mth.lerp(0.35f, 0.87266463f,
                    headPitch * (float) (Math.PI / 180.0));
            float movement = 1.2f * limbSwingAmount;
            rightFrontLeg.xRot = Mth.cos(limbSwing) * movement;
            leftFrontLeg.xRot = Mth.cos(limbSwing + (float) Math.PI)
                    * movement;
            rightHindLeg.xRot = leftFrontLeg.xRot;
            leftHindLeg.xRot = rightFrontLeg.xRot;
        }
    }
}
