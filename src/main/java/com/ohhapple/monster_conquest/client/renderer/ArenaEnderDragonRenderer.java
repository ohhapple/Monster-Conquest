package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.arena.entity.ArenaEnderDragon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaEnderDragonRenderer
        extends EntityRenderer<ArenaEnderDragon> {

    private static final ResourceLocation DRAGON_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/enderdragon/dragon.png");
    private static final ResourceLocation EYES_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/enderdragon/dragon_eyes.png");
    private static final RenderType DRAGON_RENDER_TYPE =
            RenderType.entityCutoutNoCull(DRAGON_TEXTURE);
    private static final RenderType EYES_RENDER_TYPE =
            RenderType.eyes(EYES_TEXTURE);
    private static final float RENDER_SCALE = 0.56f;

    private final ArenaDragonModel model;

    public ArenaEnderDragonRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.65f;
        model = new ArenaDragonModel(
                context.bakeLayer(ModelLayers.ENDER_DRAGON));
    }

    @Override
    public void render(ArenaEnderDragon dragon, float entityYaw,
                       float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        float age = dragon.tickCount + partialTick;
        float bodyYaw = Mth.rotLerp(partialTick,
                dragon.yBodyRotO, dragon.yBodyRot);
        float bodyPitch = Mth.lerp(partialTick,
                dragon.xRotO, dragon.getXRot());
        model.prepare(dragon, age, bodyPitch);

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180.0f - bodyYaw));
        pose.translate(0.0f, 0.0f, 1.0f);
        pose.scale(-RENDER_SCALE, -RENDER_SCALE, RENDER_SCALE);
        pose.translate(0.0f, -1.5f, 0.0f);
        int overlay = OverlayTexture.pack(
                0.0f, dragon.hurtTime > 0);
        VertexConsumer vertices = buffer.getBuffer(DRAGON_RENDER_TYPE);
        model.renderToBuffer(pose, vertices,
                packedLight, overlay);
        model.renderToBuffer(pose,
                buffer.getBuffer(EYES_RENDER_TYPE),
                0xF000F0, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(dragon, entityYaw, partialTick,
                pose, buffer, packedLight);
        ArenaUnitHealthRenderer.renderOverlay(
                dragon, pose, buffer);
    }

    @Override
    public ResourceLocation getTextureLocation(
            ArenaEnderDragon entity) {
        return DRAGON_TEXTURE;
    }

    private static final class ArenaDragonModel
            extends EntityModel<ArenaEnderDragon> {
        private final ModelPart head;
        private final ModelPart jaw;
        private final ModelPart neck;
        private final ModelPart body;
        private final ModelPart leftWing;
        private final ModelPart leftWingTip;
        private final ModelPart rightWing;
        private final ModelPart rightWingTip;
        private final ModelPart leftFrontLeg;
        private final ModelPart leftFrontLegTip;
        private final ModelPart leftFrontFoot;
        private final ModelPart rightFrontLeg;
        private final ModelPart rightFrontLegTip;
        private final ModelPart rightFrontFoot;
        private final ModelPart leftRearLeg;
        private final ModelPart leftRearLegTip;
        private final ModelPart leftRearFoot;
        private final ModelPart rightRearLeg;
        private final ModelPart rightRearLegTip;
        private final ModelPart rightRearFoot;

        private ArenaEnderDragon dragon;
        private float age;
        private float pitch;

        private ArenaDragonModel(ModelPart root) {
            head = root.getChild("head");
            jaw = head.getChild("jaw");
            neck = root.getChild("neck");
            body = root.getChild("body");
            leftWing = root.getChild("left_wing");
            leftWingTip = leftWing.getChild("left_wing_tip");
            rightWing = root.getChild("right_wing");
            rightWingTip = rightWing.getChild("right_wing_tip");
            leftFrontLeg = root.getChild("left_front_leg");
            leftFrontLegTip =
                    leftFrontLeg.getChild("left_front_leg_tip");
            leftFrontFoot =
                    leftFrontLegTip.getChild("left_front_foot");
            rightFrontLeg = root.getChild("right_front_leg");
            rightFrontLegTip =
                    rightFrontLeg.getChild("right_front_leg_tip");
            rightFrontFoot =
                    rightFrontLegTip.getChild("right_front_foot");
            leftRearLeg = root.getChild("left_hind_leg");
            leftRearLegTip =
                    leftRearLeg.getChild("left_hind_leg_tip");
            leftRearFoot =
                    leftRearLegTip.getChild("left_hind_foot");
            rightRearLeg = root.getChild("right_hind_leg");
            rightRearLegTip =
                    rightRearLeg.getChild("right_hind_leg_tip");
            rightRearFoot =
                    rightRearLegTip.getChild("right_hind_foot");
        }

        private void prepare(ArenaEnderDragon dragon,
                             float age, float pitch) {
            this.dragon = dragon;
            this.age = age;
            this.pitch = pitch;
        }

        @Override
        public void setupAnim(ArenaEnderDragon entity,
                              float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw,
                              float headPitch) {
            dragon = entity;
            age = ageInTicks;
            pitch = headPitch;
        }

        @Override
        public void renderToBuffer(PoseStack pose,
                                   VertexConsumer vertices,
                                   int packedLight, int overlay,
                                   int color) {
            float flap = Mth.cos(age * 0.32f);
            float bob = Mth.sin(age * 0.32f - 1.0f);
            float wingSweep = Mth.sin(age * 0.32f + 2.0f);
            float pitchRadians = pitch * Mth.DEG_TO_RAD;
            float headYaw = dragon == null ? 0.0f
                    : Mth.wrapDegrees(
                    dragon.getYHeadRot() - dragon.yBodyRot)
                    * Mth.DEG_TO_RAD;

            jaw.xRot = dragon != null && dragon.isBreathing()
                    ? 0.46f + Mth.sin(age * 0.8f) * 0.08f
                    : (Mth.sin(age * 0.16f) + 1.0f) * 0.10f;

            pose.pushPose();
            pose.translate(0.0f, bob * 0.6f - 2.0f, -3.0f);
            pose.mulPose(Axis.XP.rotationDegrees(bob * 2.0f));

            float y = 20.0f;
            float z = -12.0f;
            float x = 0.0f;
            for (int index = 0; index < 5; index++) {
                neck.xRot = pitchRadians * 0.14f
                        + Mth.sin(age * 0.12f + index) * 0.035f;
                neck.yRot = headYaw * 0.18f;
                neck.zRot = 0.0f;
                neck.x = x;
                neck.y = y;
                neck.z = z;
                neck.render(pose, vertices, packedLight, overlay, color);
                double step = 10.0;
                y += Mth.sin(neck.xRot) * step;
                z -= Mth.cos(neck.yRot)
                        * Mth.cos(neck.xRot) * step;
                x -= Mth.sin(neck.yRot)
                        * Mth.cos(neck.xRot) * step;
            }
            head.x = x;
            head.y = y;
            head.z = z;
            head.xRot = pitchRadians + 0.04f;
            head.yRot = headYaw;
            head.zRot = 0.0f;
            head.render(pose, vertices, packedLight, overlay, color);

            pose.pushPose();
            pose.translate(0.0f, 1.0f, 0.0f);
            pose.mulPose(Axis.ZP.rotationDegrees(
                    Mth.sin(age * 0.08f) * 2.0f));
            pose.translate(0.0f, -1.0f, 0.0f);
            body.render(pose, vertices, packedLight, overlay, color);

            leftWing.xRot = 0.125f - flap * 0.20f;
            leftWing.yRot = -0.25f;
            leftWing.zRot = -(flap + 0.35f) * 0.42f;
            leftWingTip.zRot = (wingSweep + 0.5f) * 0.48f;
            rightWing.xRot = leftWing.xRot;
            rightWing.yRot = -leftWing.yRot;
            rightWing.zRot = -leftWing.zRot;
            rightWingTip.zRot = -leftWingTip.zRot;
            renderSide(pose, vertices, packedLight, overlay, color,
                    leftWing, leftFrontLeg, leftFrontLegTip,
                    leftFrontFoot, leftRearLeg, leftRearLegTip,
                    leftRearFoot);
            renderSide(pose, vertices, packedLight, overlay, color,
                    rightWing, rightFrontLeg, rightFrontLegTip,
                    rightFrontFoot, rightRearLeg, rightRearLegTip,
                    rightRearFoot);
            pose.popPose();

            y = 10.0f;
            z = 60.0f;
            x = 0.0f;
            for (int index = 0; index < 10; index++) {
                neck.xRot = 0.04f
                        + Mth.sin(age * 0.09f + index * 0.5f) * 0.04f;
                neck.yRot = (float) Math.PI;
                neck.zRot = Mth.sin(age * 0.07f + index) * 0.05f;
                neck.x = x;
                neck.y = y;
                neck.z = z;
                neck.render(pose, vertices, packedLight, overlay, color);
                y += Mth.sin(neck.xRot) * 7.0f;
                z += Math.cos(neck.zRot) * 10.0f;
                x -= Mth.sin(neck.zRot) * 10.0f;
            }
            pose.popPose();
        }

        private static void renderSide(
                PoseStack pose, VertexConsumer vertices,
                int packedLight, int overlay, int color,
                ModelPart wing, ModelPart frontLeg,
                ModelPart frontLegTip, ModelPart frontFoot,
                ModelPart rearLeg, ModelPart rearLegTip,
                ModelPart rearFoot) {
            rearLeg.xRot = 1.05f;
            rearLegTip.xRot = 0.55f;
            rearFoot.xRot = 0.78f;
            frontLeg.xRot = 1.32f;
            frontLegTip.xRot = -0.52f;
            frontFoot.xRot = 0.76f;
            wing.render(pose, vertices, packedLight, overlay, color);
            frontLeg.render(pose, vertices, packedLight,
                    overlay, color);
            rearLeg.render(pose, vertices, packedLight,
                    overlay, color);
        }
    }
}
