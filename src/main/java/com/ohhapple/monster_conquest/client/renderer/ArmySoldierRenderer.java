package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.renderer.layer.FactionBannerLayer;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.gun.GunItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.SkeletonModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;

public class ArmySoldierRenderer
        extends HumanoidMobRenderer<AbstractArmySoldier, HumanoidModel<AbstractArmySoldier>> {

    private static final ResourceLocation ZOMBIE =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/zombie.png");
    private static final ResourceLocation SKELETON =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/skeleton.png");
    private static final ResourceLocation HUSK =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/husk.png");
    private static final ResourceLocation DROWNED =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie/drowned.png");
    private static final ResourceLocation STRAY =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/stray.png");
    private static final ResourceLocation WITHER_SKELETON =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/wither_skeleton.png");
    private static final ResourceLocation ZOMBIE_VILLAGER =
            ResourceLocation.withDefaultNamespace("textures/entity/zombie_villager/zombie_villager.png");
    private static final ResourceLocation BOGGED =
            ResourceLocation.withDefaultNamespace("textures/entity/skeleton/bogged.png");
    private static final ResourceLocation WITCH_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/witch_soldier.png");
    /** 所有枪兵统一贴图（玩家模型皮肤布局）。 */
    private static final ResourceLocation GUNNER_SKIN =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/gunner.png");
    /** 卫道士士兵 / 唤魔者士兵皮肤（玩家模型格式，64x64）。 */
    private static final ResourceLocation VINDICATOR_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/vindicator_soldier.png");
    private static final ResourceLocation EVOKER_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/evoker_soldier.png");
    /** 村民重锤兵皮肤（玩家模型格式，64x64）。 */
    private static final ResourceLocation MACE_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/mace_soldier.png");
    /** 村民弓箭手 / 村民牧师皮肤（玩家模型格式，64x64）。 */
    private static final ResourceLocation VILLAGER_ARCHER_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/villager_archer_soldier.png");
    private static final ResourceLocation VILLAGER_PRIEST_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/villager_priest_soldier.png");
    /** 猪灵盾兵：玩家模型 + 64x64 皮肤。 */
    private static final ResourceLocation PIGLIN_SHIELD_SOLDIER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID, "textures/entity/soldier/piglin_shield_soldier.png");

    private final HumanoidModel<AbstractArmySoldier> zombieModel;
    private final SkeletonModel<AbstractArmySoldier> skeletonModel;
    /** 玩家模型：女巫 / 枪兵 / 村民系士兵使用（僵尸系仍用僵尸模型、骷髅系仍用骷髅模型）。 */
    private final PlayerModel<AbstractArmySoldier> playerModel;

    public ArmySoldierRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new SoldierZombieModel(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5f);
        this.zombieModel = this.model;
        this.skeletonModel = new SoldierSkeletonModel(ctx.bakeLayer(ModelLayers.SKELETON));
        this.playerModel = new SoldierPlayerModel(ctx.bakeLayer(ModelLayers.PLAYER), false);
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        // 阵营旗帜 Layer（胸章 / 背旗 / 肩带）
        this.addLayer(new FactionBannerLayer<>(this));
    }

    @Override
    public void render(AbstractArmySoldier entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        int idx = entity.getRenderIndex();
        if (idx == 1 || idx == 4 || idx == 5 || idx == 7) this.model = this.skeletonModel;
        else if (idx == 0 || idx == 2 || idx == 3 || idx == 6) this.model = this.zombieModel;
        else this.model = this.playerModel;
        this.model.riding = entity.isPassenger();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractArmySoldier entity) {
        return switch (entity.getRenderIndex()) {
            case 1 -> SKELETON;
            case 2 -> HUSK;
            case 3 -> DROWNED;
            case 4 -> STRAY;
            case 5 -> WITHER_SKELETON;
            case 6 -> ZOMBIE_VILLAGER;
            case 7 -> BOGGED;
            case 8 -> WITCH_SOLDIER;
            case 9, 10, 11, 12 -> GUNNER_SKIN;
            case 13 -> VINDICATOR_SOLDIER;
            case 14 -> EVOKER_SOLDIER;
            case 15 -> MACE_SOLDIER;
            case 16 -> VILLAGER_ARCHER_SOLDIER;
            case 17 -> VILLAGER_PRIEST_SOLDIER;
            case 18 -> PIGLIN_SHIELD_SOLDIER;
            default -> ZOMBIE;
        };
    }

    /** 优先使用服务端同步的挥砍时刻，保证原版挥动状态丢失时仍能完整播放动作。 */
    @Override
    protected float getAttackAnim(AbstractArmySoldier entity, float partialTicks) {
        if (entity.getRole().isGunner()) return 0.0f;
        if (entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                && entity.getUseItem().getUseAnimation() == UseAnim.BOW) {
            return 0.0f;
        }
        float anim = super.getAttackAnim(entity, partialTicks);
        return Math.max(anim, entity.getMeleeAttackAnimationProgress(partialTicks));
    }

    /**
     * 游泳姿势：水里（游泳状态）时像原版溺尸一样把身体往前倾一点，
     *   配合 HumanoidModel 的划水动画，避免"直立着在水里走"的观感。
     */
    @Override
    protected void setupRotations(AbstractArmySoldier entity, PoseStack poseStack, float ageInTicks,
                                  float rotationYaw, float partialTicks, float scale) {
        super.setupRotations(entity, poseStack, ageInTicks, rotationYaw, partialTicks, scale);
        float swim = entity.getSwimAmount(partialTicks);
        if (swim > 0.0F) {
            float tilt = Mth.lerp(swim, 0.0F, -10.0F - entity.getXRot());
            poseStack.rotateAround(Axis.XP.rotationDegrees(tilt),
                    0.0F, entity.getBbHeight() / 2.0F / scale, 0.0F);
        }
    }

    private static void applyArmPoses(HumanoidModel<AbstractArmySoldier> model,
                                      AbstractArmySoldier entity) {
        boolean drawingBow = entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                && entity.getUseItem().getUseAnimation() == UseAnim.BOW;
        if (drawingBow) {
            model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
        } else {
            model.rightArmPose = entity.getMainHandItem().getItem() instanceof GunItem
                    ? HumanoidModel.ArmPose.CROSSBOW_HOLD
                    : HumanoidModel.ArmPose.EMPTY;
            model.leftArmPose = HumanoidModel.ArmPose.EMPTY;
        }
        ShieldGuardPose.apply(model, entity);
    }

    private static final class SoldierZombieModel extends HumanoidModel<AbstractArmySoldier> {
        private SoldierZombieModel(ModelPart part) {
            super(part);
        }

        @Override
        public void setupAnim(AbstractArmySoldier entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            applyArmPoses(this, entity);
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private static final class SoldierSkeletonModel
            extends SkeletonModel<AbstractArmySoldier> {
        private SoldierSkeletonModel(ModelPart part) {
            super(part);
        }

        @Override
        public void setupAnim(AbstractArmySoldier entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            applyArmPoses(this, entity);
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private static final class SoldierPlayerModel
            extends PlayerModel<AbstractArmySoldier> {
        private SoldierPlayerModel(ModelPart part, boolean slim) {
            super(part, slim);
        }

        @Override
        public void setupAnim(AbstractArmySoldier entity, float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw, float headPitch) {
            applyArmPoses(this, entity);
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
