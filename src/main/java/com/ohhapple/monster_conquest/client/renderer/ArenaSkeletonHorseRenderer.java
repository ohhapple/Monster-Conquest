package com.ohhapple.monster_conquest.client.renderer;

import com.google.common.collect.ImmutableList;
import com.ohhapple.monster_conquest.arena.entity.ArenaSkeletonHorse;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaSkeletonHorseRenderer
        extends MobRenderer<ArenaSkeletonHorse,
        ArenaSkeletonHorseRenderer.SkeletonHorseModel> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/horse/horse_skeleton.png");

    public ArenaSkeletonHorseRenderer(
            EntityRendererProvider.Context context) {
        super(context, new SkeletonHorseModel(
                context.bakeLayer(ModelLayers.SKELETON_HORSE)), 1.0f);
    }

    @Override
    public ResourceLocation getTextureLocation(
            ArenaSkeletonHorse entity) {
        return TEXTURE;
    }

    static final class SkeletonHorseModel
            extends AgeableListModel<ArenaSkeletonHorse> {
        private static final String[] HIDDEN_PARTS = {
                "saddle", "left_saddle_mouth", "right_saddle_mouth",
                "left_saddle_line", "right_saddle_line",
                "head_saddle", "mouth_saddle_wrap",
                "left_hind_baby_leg", "right_hind_baby_leg",
                "left_front_baby_leg", "right_front_baby_leg"
        };

        private final ModelPart root;
        private final ModelPart body;
        private final ModelPart headParts;
        private final ModelPart head;
        private final ModelPart tail;
        private final ModelPart[] legs;

        private SkeletonHorseModel(ModelPart root) {
            super(true, 16.2f, 1.36f, 2.7272f, 2.0f, 20.0f);
            this.root = root;
            body = root.getChild("body");
            headParts = root.getChild("head_parts");
            head = headParts.getChild("head");
            tail = body.getChild("tail");
            legs = new ModelPart[]{
                    root.getChild("left_hind_leg"),
                    root.getChild("right_hind_leg"),
                    root.getChild("left_front_leg"),
                    root.getChild("right_front_leg")
            };
            for (String hidden : HIDDEN_PARTS) {
                root.getAllParts()
                        .filter(part -> part.hasChild(hidden))
                        .forEach(part -> part.getChild(hidden)
                                .visible = false);
            }
        }

        @Override
        protected Iterable<ModelPart> headParts() {
            return ImmutableList.of(headParts);
        }

        @Override
        protected Iterable<ModelPart> bodyParts() {
            return ImmutableList.<ModelPart>builder()
                    .add(body)
                    .add(legs)
                    .build();
        }

        @Override
        public void setupAnim(ArenaSkeletonHorse entity,
                              float limbSwing,
                              float limbSwingAmount,
                              float ageInTicks,
                              float netHeadYaw,
                              float headPitch) {
            root.getAllParts().forEach(ModelPart::resetPose);
            for (String hidden : HIDDEN_PARTS) {
                root.getAllParts()
                        .filter(part -> part.hasChild(hidden))
                        .forEach(part -> part.getChild(hidden)
                                .visible = false);
            }
            headParts.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            headParts.xRot = (float) (Math.PI / 6.0)
                    + headPitch * Mth.DEG_TO_RAD;
            float swing = Mth.cos(limbSwing * 0.6662f)
                    * 0.85f * limbSwingAmount;
            legs[0].xRot = swing;
            legs[1].xRot = -swing;
            legs[2].xRot = -swing;
            legs[3].xRot = swing;
            tail.xRot = (float) (Math.PI / 6.0)
                    + limbSwingAmount * 0.75f;
        }
    }
}
