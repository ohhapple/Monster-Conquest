package com.ohhapple.monster_conquest.client.renderer;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.arena.entity.ArenaGoat;
import com.ohhapple.monster_conquest.arena.entity.ArenaSnowGolem;
import com.ohhapple.monster_conquest.arena.entity.ArenaSpider;
import com.ohhapple.monster_conquest.arena.entity.ArenaWolf;
import net.minecraft.client.model.ColorableAgeableListModel;
import net.minecraft.client.model.GoatModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.SnowGolemModel;
import net.minecraft.client.model.SpiderModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

public final class ArenaCreatureRenderers {

    private ArenaCreatureRenderers() {
    }

    public static final class SnowGolemRenderer extends
            MobRenderer<ArenaSnowGolem,
                    SnowGolemModel<ArenaSnowGolem>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/snow_golem.png");

        public SnowGolemRenderer(EntityRendererProvider.Context context) {
            super(context, new SnowGolemModel<>(
                    context.bakeLayer(ModelLayers.SNOW_GOLEM)), 0.5f);
            addLayer(new PumpkinLayer(this, context.getItemRenderer()));
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaSnowGolem entity) {
            return TEXTURE;
        }
    }

    private static final class PumpkinLayer extends RenderLayer<
            ArenaSnowGolem, SnowGolemModel<ArenaSnowGolem>> {
        private final ItemRenderer itemRenderer;

        private PumpkinLayer(
                RenderLayerParent<ArenaSnowGolem,
                        SnowGolemModel<ArenaSnowGolem>> parent,
                ItemRenderer itemRenderer) {
            super(parent);
            this.itemRenderer = itemRenderer;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffer,
                           int packedLight, ArenaSnowGolem entity,
                           float limbSwing, float limbSwingAmount,
                           float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            pose.pushPose();
            getParentModel().getHead().translateAndRotate(pose);
            pose.translate(0.0f, -0.34375f, 0.0f);
            pose.mulPose(Axis.YP.rotationDegrees(180.0f));
            pose.scale(0.625f, -0.625f, -0.625f);
            itemRenderer.renderStatic(entity,
                    new ItemStack(Blocks.CARVED_PUMPKIN),
                    ItemDisplayContext.HEAD, false, pose, buffer,
                    entity.level(), packedLight,
                    LivingEntityRenderer.getOverlayCoords(entity, 0.0f),
                    entity.getId());
            pose.popPose();
        }
    }

    public static final class SpiderRenderer extends
            MobRenderer<ArenaSpider, SpiderModel<ArenaSpider>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/spider/spider.png");

        public SpiderRenderer(EntityRendererProvider.Context context) {
            super(context, new SpiderModel<>(
                    context.bakeLayer(ModelLayers.SPIDER)), 0.8f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaSpider entity) {
            return TEXTURE;
        }
    }

    public static final class WolfRenderer extends
            MobRenderer<ArenaWolf, ArenaWolfModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/wolf/wolf.png");

        public WolfRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaWolfModel(
                    context.bakeLayer(ModelLayers.WOLF)), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaWolf entity) {
            return TEXTURE;
        }
    }

    public static final class GoatRenderer extends
            MobRenderer<ArenaGoat, ArenaGoatModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/goat/goat.png");

        public GoatRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaGoatModel(
                    context.bakeLayer(ModelLayers.GOAT)), 0.7f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaGoat entity) {
            return TEXTURE;
        }
    }

    private static final class ArenaWolfModel
            extends ColorableAgeableListModel<ArenaWolf> {
        private final ModelPart head;
        private final ModelPart body;
        private final ModelPart upperBody;
        private final ModelPart rightHindLeg;
        private final ModelPart leftHindLeg;
        private final ModelPart rightFrontLeg;
        private final ModelPart leftFrontLeg;
        private final ModelPart tail;

        private ArenaWolfModel(ModelPart root) {
            head = root.getChild("head");
            body = root.getChild("body");
            upperBody = root.getChild("upper_body");
            rightHindLeg = root.getChild("right_hind_leg");
            leftHindLeg = root.getChild("left_hind_leg");
            rightFrontLeg = root.getChild("right_front_leg");
            leftFrontLeg = root.getChild("left_front_leg");
            tail = root.getChild("tail");
        }

        @Override
        protected Iterable<ModelPart> headParts() {
            return ImmutableList.of(head);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return ImmutableList.of(body, upperBody,
                    rightHindLeg, leftHindLeg,
                    rightFrontLeg, leftFrontLeg, tail);
        }

        @Override
        public void setupAnim(ArenaWolf entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            head.xRot = headPitch * (float) (Math.PI / 180.0);
            head.yRot = netHeadYaw * (float) (Math.PI / 180.0);
            tail.yRot = Mth.cos(limbSwing * 0.6662f)
                    * 1.4f * limbSwingAmount;
            rightHindLeg.xRot = Mth.cos(limbSwing * 0.6662f)
                    * 1.4f * limbSwingAmount;
            leftHindLeg.xRot = Mth.cos(limbSwing * 0.6662f
                    + (float) Math.PI) * 1.4f * limbSwingAmount;
            rightFrontLeg.xRot = Mth.cos(limbSwing * 0.6662f
                    + (float) Math.PI) * 1.4f * limbSwingAmount;
            leftFrontLeg.xRot = Mth.cos(limbSwing * 0.6662f)
                    * 1.4f * limbSwingAmount;
        }
    }

    private static final class ArenaGoatModel
            extends QuadrupedModel<ArenaGoat> {
        private ArenaGoatModel(ModelPart root) {
            super(root, true, 19.0f, 1.0f, 2.5f, 2.0f, 24);
        }

        @Override
        public void setupAnim(ArenaGoat entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
            head.getChild("left_horn").visible = true;
            head.getChild("right_horn").visible = true;
            super.setupAnim(entity, limbSwing, limbSwingAmount,
                    ageInTicks, netHeadYaw, headPitch);
        }
    }
}
