package com.ohhapple.monster_conquest.client.renderer;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class NeutralCommanderRenderer extends MonsterCommanderRenderer {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/neutral_commander.png");

    public NeutralCommanderRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, TEXTURE);
    }
}
