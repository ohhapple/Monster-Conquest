package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.arena.entity.ArenaLootDrop;
import com.ohhapple.monster_conquest.arena.game.ArenaDropItems;
import com.ohhapple.monster_conquest.arena.game.ArenaEquipment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ArenaLootDropRenderer
        extends EntityRenderer<ArenaLootDrop> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "monster_conquest",
                    "textures/item/misc/arena_gold_coin.png");
    private final ItemRenderer itemRenderer;

    public ArenaLootDropRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(ArenaLootDrop entity, float entityYaw,
                       float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int packedLight) {
        float age = entity.tickCount + partialTick;
        pose.pushPose();
        pose.translate(0.0f,
                0.24f + Mth.sin(age * 0.16f) * 0.08f,
                0.0f);
        pose.mulPose(Axis.YP.rotationDegrees(age * 4.5f));
        float scale = entity.dropType()
                == ArenaLootDrop.TYPE_EQUIPMENT
                ? 2.4f : 2.0f;
        pose.scale(scale, scale, scale);
        ItemStack stack = entity.dropType()
                == ArenaLootDrop.TYPE_EQUIPMENT
                ? ArenaEquipment.byId(entity.visualPayload()).dropStack()
                : ArenaDropItems.goldCoin();
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                pose,
                buffer,
                entity.level(),
                entity.getId());
        pose.popPose();
        super.render(entity, entityYaw, partialTick,
                pose, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(
            ArenaLootDrop entity) {
        return TEXTURE;
    }
}
