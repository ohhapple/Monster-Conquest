package com.ohhapple.monster_conquest.client;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.client.renderer.*;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 7 个阵营首领
        event.registerEntityRenderer(ModEntities.UNDEAD_COMMANDER.get(), UndeadCommanderRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGER_COMMANDER.get(), VillagerCommanderRenderer::new);
        event.registerEntityRenderer(ModEntities.ILLAGER_COMMANDER.get(), IllagerCommanderRenderer::new);
        event.registerEntityRenderer(ModEntities.NETHER_COMMANDER.get(), NetherCommanderRenderer::new);
        event.registerEntityRenderer(ModEntities.END_COMMANDER.get(), EndCommanderRenderer::new);
        event.registerEntityRenderer(ModEntities.MONSTER_COMMANDER.get(), MonsterCommanderRenderer::new);
        event.registerEntityRenderer(ModEntities.NEUTRAL_COMMANDER.get(), NeutralCommanderRenderer::new);

        // 7 个巡逻队长（统一走 PatrolCaptainRenderer，按阵营切换贴图）
        event.registerEntityRenderer(ModEntities.UNDEAD_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGER_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);
        event.registerEntityRenderer(ModEntities.ILLAGER_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);
        event.registerEntityRenderer(ModEntities.NETHER_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);
        event.registerEntityRenderer(ModEntities.END_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);
        event.registerEntityRenderer(ModEntities.MONSTER_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);
        event.registerEntityRenderer(ModEntities.NEUTRAL_PATROL_CAPTAIN.get(), PatrolCaptainRenderer::new);

        // 士兵
        event.registerEntityRenderer(ModEntities.ZOMBIE_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.HUSK_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.DROWNED_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.ZOMBIE_VILLAGER_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.SKELETON_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.STRAY_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.BOGGED_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.WITHER_SKELETON_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.WITCH_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.RIFLE_GUNNER_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.SHOTGUN_GUNNER_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.ROCKET_GUNNER_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.SNIPER_GUNNER_SOLDIER.get(), ArmySoldierRenderer::new);

        // 灾厄系士兵（卫道士 / 唤魔者）：与其他士兵一致，使用玩家模型 + 自定义皮肤
        event.registerEntityRenderer(ModEntities.VINDICATOR_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.EVOKER_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGER_MACE_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGER_ARCHER_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.VILLAGER_PRIEST_SOLDIER.get(), ArmySoldierRenderer::new);

        event.registerEntityRenderer(ModEntities.WAR_HORSE.get(), WarHorseRenderer::new);
        // 袭击锚点：永远不渲染（袭击进度条 / 敲钟高亮的载体）
        event.registerEntityRenderer(ModEntities.RAID_ANCHOR.get(),
                net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.ARMY_TRIDENT.get(),
                net.minecraft.client.renderer.entity.ThrownTridentRenderer::new);
        event.registerEntityRenderer(ModEntities.ARMY_ARROW.get(),
                net.minecraft.client.renderer.entity.TippableArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.WITCH_POTION.get(),
                ctx -> new net.minecraft.client.renderer.entity.ThrownItemRenderer<>(ctx, 1.0f, true));
        event.registerEntityRenderer(ModEntities.ARMY_BULLET.get(),
                ArmyBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.ARMY_ROCKET.get(),
                ArmyRocketRenderer::new);
    }

    /**
     * 阵营长杆旗帜：注册自定义物品渲染器（旗杆 + 会飘动的旗面）。
     */
    @SubscribeEvent
    public static void registerClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(
                com.ohhapple.monster_conquest.item.FactionBannerClientExtensions.INSTANCE,
                Monster_conquest.FACTION_BANNER);
    }

    /** 阵营长杆旗帜：资源重载后让渲染器重新读取形状文件。 */
    @SubscribeEvent
    public static void registerClientReloadListeners(
            net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                com.ohhapple.monster_conquest.client.renderer.FactionBannerRenderer.reloadListener());
    }
}
