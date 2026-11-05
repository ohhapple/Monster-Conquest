package com.ohhapple.monster_conquest.client;

import com.ohhapple.monster_conquest.client.gui.arena.ArenaGameClientState;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaLobbyClientState;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaUiSounds;
import com.ohhapple.monster_conquest.client.renderer.*;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.item.FactionBannerClientExtensions;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.entity.TippableArrowRenderer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public class ModClientEvents {

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ArenaUiSounds.tick();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ArenaGameClientState.clear();
        ArenaLobbyClientState.clear();
        ModVersionClientState.clear();
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        ModVersionClientState.showWarningIfNeeded(event.getPlayer());
    }

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
        event.registerEntityRenderer(ModEntities.PIGLIN_SHIELD_SOLDIER.get(), ArmySoldierRenderer::new);
        event.registerEntityRenderer(ModEntities.VEX_FLYING.get(), VexFlyingRenderer::new);
        event.registerEntityRenderer(ModEntities.ALLAY_FLYING.get(), AllayFlyingRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SNOW_GOLEM.get(),
                ArenaCreatureRenderers.SnowGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SPIDER.get(),
                ArenaCreatureRenderers.SpiderRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_WOLF.get(),
                ArenaCreatureRenderers.WolfRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_GOAT.get(),
                ArenaCreatureRenderers.GoatRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_BREEZE.get(),
                ArenaHeavyCreatureRenderers.BreezeRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_RAVAGER.get(),
                ArenaHeavyCreatureRenderers.RavagerRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_POLAR_BEAR.get(),
                ArenaHeavyCreatureRenderers.PolarBearRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_HOGLIN.get(),
                ArenaHeavyCreatureRenderers.HoglinRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_IRON_GOLEM.get(),
                ArenaAdditionalRenderers.IronGolemRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_CAVE_SPIDER.get(),
                ArenaAdditionalRenderers.CaveSpiderRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_ARMADILLO.get(),
                ArenaAdditionalRenderers.ArmadilloRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_BLAZE.get(),
                ArenaAdditionalRenderers.BlazeRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SNIFFER.get(),
                ArenaAdditionalRenderers.SnifferRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_ENDERMITE.get(),
                ArenaAdditionalRenderers.EndermiteRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SLIME.get(),
                ArenaAdditionalRenderers.SlimeRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_ELDER_GUARDIAN.get(),
                ArenaLaserGuardianRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_RABBIT.get(),
                ArenaAnimalRenderers.RabbitRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_DONKEY.get(),
                ArenaAnimalRenderers.DonkeyRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SHEEP.get(),
                ArenaAnimalRenderers.SheepRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_COW.get(),
                ArenaAnimalRenderers.CowRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_PIG.get(),
                ArenaAnimalRenderers.PigRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_ZOMBIFIED_PIGLIN.get(),
                ArenaAnimalRenderers.ZombifiedPiglinRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_FROG.get(),
                ArenaAnimalRenderers.FrogRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_FOX.get(),
                ArenaAnimalRenderers.FoxRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_CREEPER.get(),
                ArenaAnimalRenderers.CreeperRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_PANDA.get(),
                ArenaAnimalRenderers.PandaRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_ENDERMAN.get(),
                ArenaGreaterMobRenderers.ArenaEndermanRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_WARDEN.get(),
                ArenaGreaterMobRenderers.ArenaWardenRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_WANDERING_TRADER.get(),
                ArenaNewUnitRenderers.WanderingTraderRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_MAGMA_CUBE.get(),
                ArenaNewUnitRenderers.MagmaCubeRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_TURTLE.get(),
                ArenaNewUnitRenderers.TurtleRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_WITHER.get(),
                ArenaWitherRenderers.WitherRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SKELETON_HORSE.get(),
                ArenaSkeletonHorseRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_GIANT_ARMADILLO.get(),
                ArenaAdditionalRenderers.GiantArmadilloRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_ENDER_DRAGON.get(),
                ArenaEnderDragonRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_WITHER_SKULL.get(),
                ArenaWitherRenderers.WitherSkullRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_SKILL_TRIDENT.get(),
                ThrownTridentRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_LOOT_DROP.get(),
                ArenaLootDropRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_LINGERING_POTION.get(),
                NoopRenderer::new);

        event.registerEntityRenderer(ModEntities.WAR_HORSE.get(), WarHorseRenderer::new);
        // 袭击锚点：永远不渲染（袭击进度条 / 敲钟高亮的载体）
        event.registerEntityRenderer(ModEntities.RAID_ANCHOR.get(),
                NoopRenderer::new);
        event.registerEntityRenderer(ModEntities.ARMY_TRIDENT.get(),
                ThrownTridentRenderer::new);
        event.registerEntityRenderer(ModEntities.ARMY_ARROW.get(),
                TippableArrowRenderer::new);
        event.registerEntityRenderer(ModEntities.WITCH_POTION.get(),
                ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        event.registerEntityRenderer(ModEntities.ARENA_SNOWBALL.get(),
                ctx -> new ThrownItemRenderer<>(ctx, 1.0f, true));
        event.registerEntityRenderer(ModEntities.ARENA_WIND_CHARGE.get(),
                ArenaWindChargeRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_GALE_CHARGE.get(),
                ArenaWindChargeRenderer::new);
        event.registerEntityRenderer(ModEntities.ARENA_FIREBALL.get(),
                ArenaAdditionalRenderers.FireballRenderer::new);
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
            RegisterClientExtensionsEvent event) {
        event.registerItem(
                FactionBannerClientExtensions.INSTANCE,
                Monster_conquest.FACTION_BANNER);
    }

    /** 阵营长杆旗帜：资源重载后让渲染器重新读取形状文件。 */
    @SubscribeEvent
    public static void registerClientReloadListeners(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                FactionBannerRenderer.reloadListener());
    }
}
