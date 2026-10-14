package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.client.renderer.FactionBannerRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

/**
 * 客户端扩展（只在客户端加载）：给阵营长旗挂上自定义物品渲染器。
 */
public final class FactionBannerClientExtensions implements IClientItemExtensions {

    public static final FactionBannerClientExtensions INSTANCE = new FactionBannerClientExtensions();

    private BlockEntityWithoutLevelRenderer renderer;

    private FactionBannerClientExtensions() {}

    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        if (renderer == null) {
            Minecraft mc = Minecraft.getInstance();
            renderer = new FactionBannerRenderer(
                    mc.getBlockEntityRenderDispatcher(), mc.getEntityModels());
        }
        return renderer;
    }
}
