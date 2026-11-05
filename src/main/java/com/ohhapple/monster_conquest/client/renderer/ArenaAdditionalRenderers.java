package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ohhapple.monster_conquest.arena.entity.ArenaArmadillo;
import com.ohhapple.monster_conquest.arena.entity.ArenaBlaze;
import com.ohhapple.monster_conquest.arena.entity.ArenaCaveSpider;
import com.ohhapple.monster_conquest.arena.entity.ArenaEndermite;
import com.ohhapple.monster_conquest.arena.entity.ArenaFireball;
import com.ohhapple.monster_conquest.arena.entity.ArenaGiantArmadillo;
import com.ohhapple.monster_conquest.arena.entity.ArenaIronGolem;
import com.ohhapple.monster_conquest.arena.entity.ArenaSniffer;
import com.ohhapple.monster_conquest.arena.entity.ArenaSlime;
import com.ohhapple.monster_conquest.arena.game.ArenaStatusFlags;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.client.model.BlazeModel;
import net.minecraft.client.model.EndermiteModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.SlimeModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.layers.SlimeOuterLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaAdditionalRenderers {

    private ArenaAdditionalRenderers() {
    }

    public static final class IronGolemRenderer extends
            MobRenderer<ArenaIronGolem, ArenaIronGolemModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/iron_golem/iron_golem.png");

        public IronGolemRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaIronGolemModel(
                    context.bakeLayer(ModelLayers.IRON_GOLEM)), 1.0f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaIronGolem entity) {
            return TEXTURE;
        }
    }

    public static final class CaveSpiderRenderer extends
            MobRenderer<ArenaCaveSpider, SpiderModel<ArenaCaveSpider>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/spider/cave_spider.png");

        public CaveSpiderRenderer(EntityRendererProvider.Context context) {
            super(context, new SpiderModel<>(
                    context.bakeLayer(ModelLayers.CAVE_SPIDER)), 0.55f);
        }

        @Override
        protected void scale(ArenaCaveSpider entity, PoseStack pose,
                             float partialTick) {
            pose.scale(0.72f, 0.72f, 0.72f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaCaveSpider entity) {
            return TEXTURE;
        }
    }

    public static final class ArmadilloRenderer extends
            MobRenderer<ArenaArmadillo,
                    ArenaArmadilloModel<ArenaArmadillo>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/armadillo.png");

        public ArmadilloRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaArmadilloModel<>(
                    ArenaArmadilloModel.createArmadilloLayer()), 0.55f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaArmadillo entity) {
            return TEXTURE;
        }
    }

    public static final class GiantArmadilloRenderer extends
            MobRenderer<ArenaGiantArmadillo,
                    ArenaArmadilloModel<ArenaGiantArmadillo>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/armadillo.png");

        public GiantArmadilloRenderer(
                EntityRendererProvider.Context context) {
            super(context, new ArenaArmadilloModel<>(
                    ArenaArmadilloModel.createArmadilloLayer()), 0.85f);
        }

        @Override
        public ResourceLocation getTextureLocation(
                ArenaGiantArmadillo entity) {
            return TEXTURE;
        }
    }

    public static final class BlazeRenderer extends
            MobRenderer<ArenaBlaze, BlazeModel<ArenaBlaze>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/blaze.png");

        public BlazeRenderer(EntityRendererProvider.Context context) {
            super(context, new BlazeModel<>(
                    context.bakeLayer(ModelLayers.BLAZE)), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaBlaze entity) {
            return TEXTURE;
        }
    }

    public static final class SnifferRenderer extends
            MobRenderer<ArenaSniffer, ArenaSnifferModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/sniffer/sniffer.png");

        public SnifferRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaSnifferModel(
                    context.bakeLayer(ModelLayers.SNIFFER)), 1.15f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaSniffer entity) {
            return TEXTURE;
        }
    }

    public static final class EndermiteRenderer extends
            MobRenderer<ArenaEndermite, EndermiteModel<ArenaEndermite>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/endermite.png");

        public EndermiteRenderer(EntityRendererProvider.Context context) {
            super(context, new EndermiteModel<>(
                    context.bakeLayer(ModelLayers.ENDERMITE)), 0.6f);
        }

        @Override
        protected void scale(ArenaEndermite entity, PoseStack pose,
                             float partialTick) {
            pose.scale(2.0f, 2.0f, 2.0f);
        }

        @Override
        protected float getFlipDegrees(ArenaEndermite entity) {
            return 180.0f;
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaEndermite entity) {
            return TEXTURE;
        }
    }

    public static final class SlimeRenderer extends
            MobRenderer<ArenaSlime, SlimeModel<ArenaSlime>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/slime/slime.png");

        public SlimeRenderer(EntityRendererProvider.Context context) {
            super(context, new SlimeModel<>(
                    context.bakeLayer(ModelLayers.SLIME)), 0.9f);
            addLayer(new SlimeOuterLayer<>(this,
                    context.getModelSet()));
        }

        @Override
        protected void scale(ArenaSlime entity, PoseStack pose,
                             float partialTick) {
            float size = 4.8f;
            float bounce = 1.0f + Mth.sin(
                    (entity.tickCount + partialTick) * 0.25f) * 0.035f;
            pose.scale(size, size / bounce, size);
            shadowRadius = 0.45f;
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaSlime entity) {
            return TEXTURE;
        }
    }

    public static final class FireballRenderer
            extends ThrownItemRenderer<ArenaFireball> {
        public FireballRenderer(EntityRendererProvider.Context context) {
            super(context, 1.35f, true);
        }
    }

    private static final class ArenaIronGolemModel
            extends HierarchicalModel<ArenaIronGolem> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart rightArm;
        private final ModelPart leftArm;
        private final ModelPart rightLeg;
        private final ModelPart leftLeg;

        private ArenaIronGolemModel(ModelPart root) {
            this.root = root;
            head = root.getChild("head");
            rightArm = root.getChild("right_arm");
            leftArm = root.getChild("left_arm");
            rightLeg = root.getChild("right_leg");
            leftLeg = root.getChild("left_leg");
        }

        @Override
        public void setupAnim(ArenaIronGolem entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            rightLeg.xRot = -1.4f * Mth.triangleWave(
                    limbSwing, 13.0f) * limbSwingAmount;
            leftLeg.xRot = 1.4f * Mth.triangleWave(
                    limbSwing, 13.0f) * limbSwingAmount;
            float attack = entity.getMeleeAttackAnimationProgress(
                    Mth.frac(ageInTicks));
            if (attack > 0.0f) {
                // 与原版铁傀儡一致：攻击时双臂同时抬起到同一角度。
                float swing = -2.0f + 1.5f * Mth.triangleWave(
                        attack * 10.0f, 10.0f);
                rightArm.xRot = swing;
                leftArm.xRot = swing;
            } else {
                rightArm.xRot += rightLeg.xRot * 0.35f;
                leftArm.xRot += leftLeg.xRot * 0.35f;
            }
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }

    private static final class ArenaArmadilloModel<
            T extends AbstractArmySoldier>
            extends HierarchicalModel<T> {
        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart cube;
        private final ModelPart head;
        private final ModelPart tail;
        private final ModelPart[] legs;

        private static LayerDefinition createArmadilloLayer() {
            MeshDefinition mesh = new MeshDefinition();
            PartDefinition root = mesh.getRoot();
            PartDefinition body = root.addOrReplaceChild(
                    "body",
                    CubeListBuilder.create()
                            .texOffs(0, 20)
                            .addBox(-4.0f, -7.0f, -10.0f,
                                    8.0f, 8.0f, 12.0f,
                                    new CubeDeformation(0.3f))
                            .texOffs(0, 40)
                            .addBox(-4.0f, -7.0f, -10.0f,
                                    8.0f, 8.0f, 12.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, 21.0f, 4.0f));
            body.addOrReplaceChild(
                    "tail",
                    CubeListBuilder.create()
                            .texOffs(44, 53)
                            .addBox(-0.5f, -0.0865f, 0.0933f,
                                    1.0f, 6.0f, 1.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offsetAndRotation(
                            0.0f, -3.0f, 1.0f,
                            0.5061f, 0.0f, 0.0f));
            PartDefinition head = body.addOrReplaceChild(
                    "head", CubeListBuilder.create(),
                    PartPose.offset(0.0f, -2.0f, -11.0f));
            head.addOrReplaceChild(
                    "head_cube",
                    CubeListBuilder.create()
                            .texOffs(43, 15)
                            .addBox(-1.5f, -1.0f, -1.0f,
                                    3.0f, 5.0f, 2.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offsetAndRotation(
                            0.0f, 0.0f, 0.0f,
                            -0.3927f, 0.0f, 0.0f));
            PartDefinition rightEar = head.addOrReplaceChild(
                    "right_ear", CubeListBuilder.create(),
                    PartPose.offset(-1.0f, -1.0f, 0.0f));
            rightEar.addOrReplaceChild(
                    "right_ear_cube",
                    CubeListBuilder.create()
                            .texOffs(43, 10)
                            .addBox(-2.0f, -3.0f, 0.0f,
                                    2.0f, 5.0f, 0.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offsetAndRotation(
                            -0.5f, 0.0f, -0.6f,
                            0.1886f, -0.3864f, -0.0718f));
            PartDefinition leftEar = head.addOrReplaceChild(
                    "left_ear", CubeListBuilder.create(),
                    PartPose.offset(1.0f, -2.0f, 0.0f));
            leftEar.addOrReplaceChild(
                    "left_ear_cube",
                    CubeListBuilder.create()
                            .texOffs(47, 10)
                            .addBox(0.0f, -3.0f, 0.0f,
                                    2.0f, 5.0f, 0.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offsetAndRotation(
                            0.5f, 1.0f, -0.6f,
                            0.1886f, 0.3864f, 0.0718f));
            root.addOrReplaceChild(
                    "right_hind_leg",
                    CubeListBuilder.create()
                            .texOffs(51, 31)
                            .addBox(-1.0f, 0.0f, -1.0f,
                                    2.0f, 3.0f, 2.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offset(-2.0f, 21.0f, 4.0f));
            root.addOrReplaceChild(
                    "left_hind_leg",
                    CubeListBuilder.create()
                            .texOffs(42, 31)
                            .addBox(-1.0f, 0.0f, -1.0f,
                                    2.0f, 3.0f, 2.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offset(2.0f, 21.0f, 4.0f));
            root.addOrReplaceChild(
                    "right_front_leg",
                    CubeListBuilder.create()
                            .texOffs(51, 43)
                            .addBox(-1.0f, 0.0f, -1.0f,
                                    2.0f, 3.0f, 2.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offset(-2.0f, 21.0f, -4.0f));
            root.addOrReplaceChild(
                    "left_front_leg",
                    CubeListBuilder.create()
                            .texOffs(42, 43)
                            .addBox(-1.0f, 0.0f, -1.0f,
                                    2.0f, 3.0f, 2.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offset(2.0f, 21.0f, -4.0f));
            // 原版壳中心为枢轴 (0,24,0) + 盒中心 (0,-5,-1)。
            // 把盒几何平移到以该中心为枢轴的局部坐标后，旋转不会再偏心。
            root.addOrReplaceChild(
                    "cube",
                    CubeListBuilder.create()
                            .texOffs(0, 0)
                            .addBox(-5.0f, -5.0f, -5.0f,
                                    10.0f, 10.0f, 10.0f,
                                    new CubeDeformation(0.0f)),
                    PartPose.offset(0.0f, 19.0f, -1.0f));
            return LayerDefinition.create(mesh, 64, 64);
        }

        private ArenaArmadilloModel(LayerDefinition layer) {
            this(layer.bakeRoot());
        }

        private ArenaArmadilloModel(ModelPart root) {
            this.root = root;
            body = root.getChild("body");
            cube = root.getChild("cube");
            head = body.getChild("head");
            tail = body.getChild("tail");
            legs = new ModelPart[]{
                    root.getChild("right_hind_leg"),
                    root.getChild("left_hind_leg"),
                    root.getChild("right_front_leg"),
                    root.getChild("left_front_leg")
            };
        }

        @Override
        public void setupAnim(T entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            boolean rolling = (entity.getArenaStatusFlags()
                    & ArenaStatusFlags.ARMADILLO_ROLL) != 0;
            body.skipDraw = rolling;
            head.visible = !rolling;
            tail.visible = !rolling;
            cube.visible = rolling;
            for (ModelPart leg : legs) leg.visible = !rolling;
            if (rolling) {
                if (entity.isArenaCombatPresentation()
                        && entity.isAlive()
                        && (entity.getArenaStatusFlags()
                        & ArenaStatusFlags.ARMADILLO_MOVING) != 0) {
                    float radiansPerTick =
                            entity instanceof ArenaGiantArmadillo
                                    ? 0.55850536f : 0.4886922f;
                    cube.xRot = ageInTicks * radiansPerTick;
                }
                return;
            }
            head.xRot = Mth.clamp(headPitch, -22.5f, 25.0f)
                    * Mth.DEG_TO_RAD;
            head.yRot = Mth.clamp(netHeadYaw, -32.5f, 32.5f)
                    * Mth.DEG_TO_RAD;
            float movement = Mth.cos(limbSwing * 0.6662f)
                    * 1.2f * limbSwingAmount;
            legs[0].xRot = movement;
            legs[1].xRot = -movement;
            legs[2].xRot = -movement;
            legs[3].xRot = movement;
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }

    private static final class ArenaSnifferModel
            extends HierarchicalModel<ArenaSniffer> {
        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart[] legs;

        private ArenaSnifferModel(ModelPart root) {
            this.root = root.getChild("root");
            ModelPart bone = this.root.getChild("bone");
            ModelPart body = bone.getChild("body");
            head = body.getChild("head");
            legs = new ModelPart[]{
                    bone.getChild("right_front_leg"),
                    bone.getChild("right_mid_leg"),
                    bone.getChild("right_hind_leg"),
                    bone.getChild("left_front_leg"),
                    bone.getChild("left_mid_leg"),
                    bone.getChild("left_hind_leg")
            };
        }

        @Override
        public void setupAnim(ArenaSniffer entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            for (int i = 0; i < legs.length; i++) {
                float phase = (i % 3) * (float) Math.PI * 0.5f
                        + (i >= 3 ? (float) Math.PI : 0.0f);
                legs[i].xRot = Mth.cos(limbSwing * 0.45f + phase)
                        * 0.35f * limbSwingAmount;
            }
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }
}
