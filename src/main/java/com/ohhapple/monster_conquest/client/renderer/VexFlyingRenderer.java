package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
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
import net.minecraft.world.item.ItemStack;

/**
 * 恼鬼士兵渲染器：几何结构、贴图与扇翅节奏与原版恼鬼一致。
 *   模型改为泛型士兵模型，以便本模组实体继续使用同一套阵营 / 单位系统。
 */
public class VexFlyingRenderer
        extends MobRenderer<AbstractFlyingSoldier, VexFlyingRenderer.Model> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/illager/vex.png");
    private static final ResourceLocation CHARGING_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/illager/vex_charging.png");

    public VexFlyingRenderer(EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(ModelLayers.VEX)), 0.3f);
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractFlyingSoldier entity) {
        return entity.isCombatCharging() ? CHARGING_TEXTURE : TEXTURE;
    }

    @Override
    protected int getBlockLightLevel(AbstractFlyingSoldier entity, BlockPos pos) {
        return 15;
    }

    static final class Model extends HierarchicalModel<AbstractFlyingSoldier>
            implements ArmedModel {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightWing;
        private final ModelPart leftWing;
        private final ModelPart head;

        private Model(ModelPart root) {
            super(RenderType::entityTranslucent);
            this.root = root.getChild("root");
            this.body = this.root.getChild("body");
            this.rightArm = this.body.getChild("right_arm");
            this.leftArm = this.body.getChild("left_arm");
            this.rightWing = this.body.getChild("right_wing");
            this.leftWing = this.body.getChild("left_wing");
            this.head = this.root.getChild("head");
        }

        static LayerDefinition createBodyLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition bodyRoot = root.addOrReplaceChild(
                    "root", CubeListBuilder.create(), PartPose.offset(0.0f, -2.5f, 0.0f));
            bodyRoot.addOrReplaceChild(
                    "head",
                    CubeListBuilder.create().texOffs(0, 0).addBox(
                            -2.5f, -5.0f, -2.5f, 5.0f, 5.0f, 5.0f, new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, 20.0f, 0.0f));
            PartDefinition body = bodyRoot.addOrReplaceChild(
                    "body",
                    CubeListBuilder.create()
                            .texOffs(0, 10).addBox(
                                    -1.5f, 0.0f, -1.0f, 3.0f, 4.0f, 2.0f,
                                    new CubeDeformation(0.0f))
                            .texOffs(0, 16).addBox(
                                    -1.5f, 1.0f, -1.0f, 3.0f, 5.0f, 2.0f,
                                    new CubeDeformation(-0.2f)),
                    PartPose.offset(0.0f, 20.0f, 0.0f));
            body.addOrReplaceChild(
                    "right_arm",
                    CubeListBuilder.create().texOffs(23, 0).addBox(
                            -1.25f, -0.5f, -1.0f, 2.0f, 4.0f, 2.0f,
                            new CubeDeformation(-0.1f)),
                    PartPose.offset(-1.75f, 0.25f, 0.0f));
            body.addOrReplaceChild(
                    "left_arm",
                    CubeListBuilder.create().texOffs(23, 6).addBox(
                            -0.75f, -0.5f, -1.0f, 2.0f, 4.0f, 2.0f,
                            new CubeDeformation(-0.1f)),
                    PartPose.offset(1.75f, 0.25f, 0.0f));
            body.addOrReplaceChild(
                    "left_wing",
                    CubeListBuilder.create().texOffs(16, 14).mirror().addBox(
                            0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 8.0f,
                            new CubeDeformation(0.0f)).mirror(false),
                    PartPose.offset(0.5f, 1.0f, 1.0f));
            body.addOrReplaceChild(
                    "right_wing",
                    CubeListBuilder.create().texOffs(16, 14).addBox(
                            0.0f, 0.0f, 0.0f, 0.0f, 5.0f, 8.0f,
                            new CubeDeformation(0.0f)),
                    PartPose.offset(-0.5f, 1.0f, 1.0f));
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
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            float flap = Mth.cos(ageInTicks * 5.5f * Mth.DEG_TO_RAD) * 0.1f;
            rightArm.zRot = (float) (Math.PI / 5.0) + flap;
            leftArm.zRot = -((float) (Math.PI / 5.0) + flap);
            if (entity.isCombatCharging()) {
                body.xRot = 0.0f;
                setArmsCharging(entity.getMainHandItem(), entity.getOffhandItem(), flap);
            } else {
                body.xRot = (float) (Math.PI / 20.0);
            }
            leftWing.yRot = 1.0995574f
                    + Mth.cos(ageInTicks * 45.836624f * Mth.DEG_TO_RAD)
                    * Mth.DEG_TO_RAD * 16.2f;
            rightWing.yRot = -leftWing.yRot;
            leftWing.xRot = 0.47123888f;
            leftWing.zRot = -0.47123888f;
            rightWing.xRot = 0.47123888f;
            rightWing.zRot = 0.47123888f;
        }

        private void setArmsCharging(ItemStack mainHand, ItemStack offhand, float flap) {
            if (mainHand.isEmpty() && offhand.isEmpty()) {
                rightArm.xRot = -1.2217305f;
                rightArm.yRot = (float) (Math.PI / 12.0);
                rightArm.zRot = -0.47123888f - flap;
                leftArm.xRot = -1.2217305f;
                leftArm.yRot = (float) (-Math.PI / 12.0);
                leftArm.zRot = 0.47123888f + flap;
                return;
            }
            if (!mainHand.isEmpty()) {
                rightArm.xRot = (float) (Math.PI * 7.0 / 6.0);
                rightArm.yRot = (float) (Math.PI / 12.0);
                rightArm.zRot = -0.47123888f - flap;
            }
            if (!offhand.isEmpty()) {
                leftArm.xRot = (float) (Math.PI * 7.0 / 6.0);
                leftArm.yRot = (float) (-Math.PI / 12.0);
                leftArm.zRot = 0.47123888f + flap;
            }
        }

        @Override
        public void translateToHand(HumanoidArm arm, PoseStack poseStack) {
            boolean right = arm == HumanoidArm.RIGHT;
            ModelPart modelPart = right ? rightArm : leftArm;
            root.translateAndRotate(poseStack);
            body.translateAndRotate(poseStack);
            modelPart.translateAndRotate(poseStack);
            poseStack.scale(0.55f, 0.55f, 0.55f);
            if (right) {
                poseStack.translate(0.046875, -0.15625, 0.078125);
            } else {
                poseStack.translate(-0.046875, -0.15625, 0.078125);
            }
        }
    }
}
