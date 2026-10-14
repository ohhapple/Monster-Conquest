package com.ohhapple.monster_conquest.client.renderer;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * 巡逻队长统一渲染器：按 entity 阵营切换贴图。
 * 贴图资源需放在 assets/monster_conquest/textures/entity/commander/ 下。
 */
public class PatrolCaptainRenderer extends MonsterCommanderRenderer {

    private static final ResourceLocation UNDEAD =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/undead_patrol_captain.png");
    private static final ResourceLocation VILLAGER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/villager_patrol_captain.png");
    private static final ResourceLocation ILLAGER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/illager_patrol_captain.png");
    private static final ResourceLocation NETHER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/nether_patrol_captain.png");
    private static final ResourceLocation END =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/end_patrol_captain.png");
    private static final ResourceLocation MONSTER =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/monster_patrol_captain.png");
    private static final ResourceLocation NEUTRAL =
            ResourceLocation.fromNamespaceAndPath(Monster_conquest.MODID,
                    "textures/entity/commander/neutral_patrol_captain.png");

    public PatrolCaptainRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, UNDEAD);
    }

    @Override
    public ResourceLocation getTextureLocation(AbstractArmyCommander entity) {
        ResourceLocation f = entity.getFactionId();
        if (f == null) return UNDEAD;
        if (f.equals(FactionIds.VILLAGER)) return VILLAGER;
        if (f.equals(FactionIds.ILLAGER))  return ILLAGER;
        if (f.equals(FactionIds.NETHER))   return NETHER;
        if (f.equals(FactionIds.END))      return END;
        if (f.equals(FactionIds.MONSTER))  return MONSTER;
        if (f.equals(FactionIds.NEUTRAL))  return NEUTRAL;
        return UNDEAD;
    }
}
