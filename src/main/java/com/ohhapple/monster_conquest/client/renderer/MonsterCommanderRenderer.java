package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.renderer.layer.FactionBannerLayer;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class MonsterCommanderRenderer
        extends HumanoidMobRenderer<AbstractArmyCommander, HumanoidModel<AbstractArmyCommander>> {

    public static final ResourceLocation DEFAULT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/monster_commander.png");

    private final ResourceLocation texture;

    public MonsterCommanderRenderer(EntityRendererProvider.Context ctx) {
        this(ctx, DEFAULT_TEXTURE);
    }

    public MonsterCommanderRenderer(EntityRendererProvider.Context ctx, ResourceLocation texture) {
        // 首领 / 巡逻队长统一使用玩家模型
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.7f);
        this.texture = texture;
        this.addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)),
                ctx.getModelManager()));
        this.addLayer(new ItemInHandLayer<>(this, ctx.getItemInHandRenderer()));
        // 阵营旗帜 Layer（胸章 / 背旗 / 肩带）
        this.addLayer(new FactionBannerLayer<>(this));
    }

    @Override
    public void render(AbstractArmyCommander entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        this.model.riding = entity.isPassenger();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractArmyCommander entity) {
        return texture;
    }
}
