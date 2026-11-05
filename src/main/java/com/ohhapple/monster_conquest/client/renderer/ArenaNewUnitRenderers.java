package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ohhapple.monster_conquest.arena.entity.ArenaMagmaCube;
import com.ohhapple.monster_conquest.arena.entity.ArenaTurtle;
import com.ohhapple.monster_conquest.arena.entity.ArenaWanderingTrader;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.QuadrupedModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ArenaNewUnitRenderers {

    private ArenaNewUnitRenderers() {
    }

    public static final class WanderingTraderRenderer extends
            MobRenderer<ArenaWanderingTrader,
                    VillagerModel<ArenaWanderingTrader>> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/wandering_trader.png");

        public WanderingTraderRenderer(
                EntityRendererProvider.Context context) {
            super(context, new VillagerModel<>(
                    context.bakeLayer(ModelLayers.WANDERING_TRADER)), 0.5f);
        }

        @Override
        protected void scale(ArenaWanderingTrader entity, PoseStack pose,
                             float partialTick) {
            pose.scale(0.9375f, 0.9375f, 0.9375f);
        }

        @Override
        public ResourceLocation getTextureLocation(
                ArenaWanderingTrader entity) {
            return TEXTURE;
        }
    }

    public static final class TurtleRenderer extends
            MobRenderer<ArenaTurtle, ArenaTurtleModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/turtle/big_sea_turtle.png");

        public TurtleRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaTurtleModel(
                    context.bakeLayer(ModelLayers.TURTLE)), 0.7f);
        }

        @Override
        protected void scale(ArenaTurtle entity, PoseStack pose,
                             float partialTick) {
            pose.scale(1.35f, 1.35f, 1.35f);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaTurtle entity) {
            return TEXTURE;
        }
    }

    public static final class MagmaCubeRenderer extends
            MobRenderer<ArenaMagmaCube, ArenaMagmaCubeModel> {
        private static final ResourceLocation TEXTURE =
                ResourceLocation.withDefaultNamespace(
                        "textures/entity/slime/magmacube.png");

        public MagmaCubeRenderer(EntityRendererProvider.Context context) {
            super(context, new ArenaMagmaCubeModel(
                    context.bakeLayer(ModelLayers.MAGMA_CUBE)), 0.8f);
        }

        @Override
        protected int getBlockLightLevel(ArenaMagmaCube entity,
                                         net.minecraft.core.BlockPos pos) {
            return 15;
        }

        @Override
        protected void scale(ArenaMagmaCube entity, PoseStack pose,
                             float partialTick) {
            float size = 4.2f;
            float bounce = 1.0f + Mth.sin(
                    (entity.tickCount + partialTick) * 0.25f) * 0.035f;
            pose.scale(size, size / bounce, size);
            shadowRadius = 0.42f;
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaMagmaCube entity) {
            return TEXTURE;
        }
    }

    private static final class ArenaTurtleModel
            extends QuadrupedModel<ArenaTurtle> {
        private ArenaTurtleModel(ModelPart root) {
            super(root, true, 120.0f, 0.0f, 9.0f, 6.0f, 120);
        }
    }

    private static final class ArenaMagmaCubeModel
            extends HierarchicalModel<ArenaMagmaCube> {
        private final ModelPart root;
        private final ModelPart[] bodyCubes;

        private ArenaMagmaCubeModel(ModelPart root) {
            this.root = root;
            bodyCubes = new ModelPart[8];
            for (int i = 0; i < bodyCubes.length; i++) {
                bodyCubes[i] = root.getChild("cube" + i);
            }
        }

        @Override
        public void prepareMobModel(ArenaMagmaCube entity,
                                    float limbSwing,
                                    float limbSwingAmount,
                                    float partialTick) {
            float bounce = 1.0f + Mth.sin(
                    (entity.tickCount + partialTick) * 0.25f) * 0.04f;
            for (int i = 0; i < bodyCubes.length; i++) {
                bodyCubes[i].y = -(4 - i) * bounce * 0.12f;
            }
        }

        @Override
        public void setupAnim(ArenaMagmaCube entity, float limbSwing,
                              float limbSwingAmount, float ageInTicks,
                              float netHeadYaw, float headPitch) {
        }

        @Override
        public ModelPart root() {
            return root;
        }
    }
}
