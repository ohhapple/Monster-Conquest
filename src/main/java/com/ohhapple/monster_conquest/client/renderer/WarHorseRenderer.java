package com.ohhapple.monster_conquest.client.renderer;

import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.client.model.HorseModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class WarHorseRenderer
        extends AbstractHorseRenderer<WarHorseEntity, HorseModel<WarHorseEntity>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/horse/horse_black.png");

    public WarHorseRenderer(EntityRendererProvider.Context ctx) {
        // AbstractHorseRenderer(ctx, model, scale)：
        //   第三参数 = 模型缩放（阴影半径固定 0.75）。
        //   原版 HorseRenderer 用的就是 1.1F。
        super(ctx, new HorseModel<>(ctx.bakeLayer(ModelLayers.HORSE)), 1.1F);
    }

    @Override
    public ResourceLocation getTextureLocation(WarHorseEntity entity) {
        return TEXTURE;
    }
}