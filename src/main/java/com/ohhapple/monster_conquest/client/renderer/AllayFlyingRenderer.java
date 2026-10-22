package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 悦灵士兵渲染器：几何结构、贴图和扇翅动画与原版悦灵一致。
 *   召唤物不会被原版悦灵拾物 / 复制 / 唱片机 Brain 接管。
 */
public class AllayFlyingRenderer
        extends MobRenderer<AbstractFlyingSoldier, AllayFlyingRenderer.Model> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/allay/allay.png");

    public AllayFlyingRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.ALLAY)), 0.4f);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractFlyingSoldier entity) {
        return TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(AbstractFlyingSoldier entity, BlockPos pos) {
        return 15;
    }

    static final class Model extends HierarchicalModel<AbstractFlyingSoldier>
            implements ArmedModel {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart body;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightWing;
        private final ModelPart leftWing;

        private Model(ModelPart root) {
            super(RenderType::entityTranslucent);
            this.root = root.getChild("root");
            this.head = this.root.getChild("head");
            this.body = this.root.getChild("body");
            this.rightArm = this.body.getChild("right_arm");
            this.leftArm = this.body.getChild("left_arm");
            this.rightWing = this.body.getChild("right_wing");
            this.leftWing = this.body.getChild("left_wing");
        }

        static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition bodyRoot = root.addOrReplaceChild(
                    "root", CubeListBuilder.create(), PartPose.offset(0.0f, 23.5f, 0.0f));
            bodyRoot.addOrReplaceChild(
                    "head",
                    CubeListBuilder.create().texOffs(0, 0).addBox(
                            -2.5f, -5.0f, -2.5f, 5.0f, 5.0f, 5.0f,
                            new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, -3.99f, 0.0f));
            PartDefinition body = bodyRoot.addOrReplaceChild(
                    "body",
                    CubeListBuilder.create()
                            .texOffs(0, 10).addBox(
                                    -1.5f, 0.0f, -1.0f, 3.0f, 4.0f, 2.0f,
                                    new CubeDeformation(0.0f))
                            .texOffs(0, 16).addBox(
                                    -1.5f, 0.0f, -1.0f, 3.0f, 5.0f, 2.0f,
                                    new CubeDeformation(-0.2f)),
                    PartPose.offset(0.0f, -4.0f, 0.0f));
            body.addOrReplaceChild(
                    "right_arm",
                    CubeListBuilder.create().texOffs(23, 0).addBox(
                            -0.75f, -0.5f, -1.0f, 1.0f, 4.0f, 2.0f,
                            new CubeDeformation(-0.01f)),
                    PartPose.offset(-1.75f, 0.5f, 0.0f));
            body.addOrReplaceChild(
                    "left_arm",
                    CubeListBuilder.create().texOffs(23, 6).addBox(
                            -0.25f, -0.5f, -1.0f, 1.0f, 4.0f, 2.0f,
                            new CubeDeformation(-0.01f)),
                    PartPose.offset(1.75f, 0.5f, 0.0f));
            body.addOrReplaceChild(
                    "right_wing",
                    CubeListBuilder.create().texOffs(16, 14).addBox(
                            0.0f, 1.0f, 0.0f, 0.0f, 5.0f, 8.0f,
                            new CubeDeformation(0.0f)),
                    PartPose.offset(-0.5f, 0.0f, 0.6f));
            body.addOrReplaceChild(
                    "left_wing",
                    CubeListBuilder.create().texOffs(16, 14).addBox(
                            0.0f, 1.0f, 0.0f, 0.0f, 5.0f, 8.0f,
                            new CubeDeformation(0.0f)),
                    PartPose.offset(0.5f, 0.0f, 0.6f));
            return LayerDefinition.create(mesh, 32, 32);
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(AbstractFlyingSoldier entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            root().getAllParts().forEach(ModelPart::resetPose);
            float phase = ageInTicks * 20.0f * Mth.DEG_TO_RAD + limbSwing;
            float wingSwing = Mth.cos(phase) * (float) Math.PI * 0.15f + limbSwingAmount;
            float bobPhase = ageInTicks * 9.0f * Mth.DEG_TO_RAD;
            float moving = Math.min(limbSwingAmount / 0.3f, 1.0f);
            float idle = 1.0f - moving;

            head.xRot = headPitch * Mth.DEG_TO_RAD;
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            rightWing.xRot = 0.43633232f * idle;
            rightWing.yRot = (float) (-Math.PI / 4.0) + wingSwing;
            leftWing.xRot = 0.43633232f * idle;
            leftWing.yRot = (float) (Math.PI / 4.0) - wingSwing;
            body.xRot = moving * (float) (Math.PI / 4.0);
            root.y += (float) Math.cos(bobPhase) * 0.25f * idle;

            // 悦灵始终持有钻石剑，使用原版持有物品时的双臂前抱姿态。
            float armXRot = Mth.lerp(moving,
                    (float) (-Math.PI / 3.0), -1.134464f);
            rightArm.xRot = armXRot;
            leftArm.xRot = armXRot;
            rightArm.yRot = 0.27925268f;
            leftArm.yRot = -0.27925268f;
            rightArm.zRot = 0.43633232f;
            leftArm.zRot = -0.43633232f;
        }

        @Override
        public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
            root.translateAndRotate(poseStack);
            body.translateAndRotate(poseStack);
            poseStack.translate(0.0f, 0.0625f, 0.1875f);
            poseStack.mulPose(Axis.XP.rotation(rightArm.xRot));
            poseStack.scale(0.7f, 0.7f, 0.7f);
            poseStack.translate(0.0625f, 0.0f, 0.0f);
        }
    }
}
