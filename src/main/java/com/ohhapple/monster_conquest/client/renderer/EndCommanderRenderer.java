package com.ohhapple.monster_conquest.client.renderer;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class EndCommanderRenderer extends MonsterCommanderRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/end_commander.png");

    public EndCommanderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, TEXTURE);
    }
}
