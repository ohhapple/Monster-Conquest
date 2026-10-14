package com.ohhapple.monster_conquest;

import com.mojang.logging.LogUtils;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.factions.*;
import com.ohhapple.monster_conquest.block.RecruitmentStationBlock;
import com.ohhapple.monster_conquest.block.TradeStationBlock;
import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.config.RecruitConfig;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.gun.*;
import com.ohhapple.monster_conquest.item.*;
import com.ohhapple.monster_conquest.network.*;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionAllySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.FactionHostilitySnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.faction.OpenFactionGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.GunStatusPayload;
import com.ohhapple.monster_conquest.network.clientbound.gun.SyncEquipmentPayload;
import com.ohhapple.monster_conquest.network.clientbound.recruit.OpenRecruitGuiPayload;
import com.ohhapple.monster_conquest.network.clientbound.trade.OpenTradeGuiPayload;
import com.ohhapple.monster_conquest.network.serverbound.gun.GunFirePayload;
import com.ohhapple.monster_conquest.network.serverbound.gun.OpenTacticalEquipmentSlotsPayload;
import com.ohhapple.monster_conquest.network.serverbound.gun.OpenAmmoBoxFromSlotPayload;
import com.ohhapple.monster_conquest.network.serverbound.recruit.RecruitSoldierPayload;
import com.ohhapple.monster_conquest.network.serverbound.recruit.UpgradeSoldierPayload;
import com.ohhapple.monster_conquest.network.serverbound.trade.BuyTradeOfferPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncRtsSelectionPayload;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncSquadPayload;
import com.ohhapple.monster_conquest.network.serverbound.command.PlayerCommandPayload;
import com.ohhapple.monster_conquest.network.serverbound.command.SelectSquadPayload;
import com.ohhapple.monster_conquest.network.serverbound.egg.CycleEggFactionPayload;
import com.ohhapple.monster_conquest.network.serverbound.egg.ToggleEggCavalryPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.DeleteFactionPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.RequestOpenFactionGuiPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.ToggleFactionAllyPayload;
import com.ohhapple.monster_conquest.network.serverbound.faction.ToggleFactionHostilityPayload;
import com.ohhapple.monster_conquest.network.serverbound.item.*;
import com.ohhapple.monster_conquest.network.serverbound.rts.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(Monster_conquest.MODID)
public class Monster_conquest {

    public static final String MODID = "monster_conquest";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ===================== 阵营首领刷怪蛋（每阵营独立） =====================

    public static final DeferredItem<CommanderSpawnEggItem> UNDEAD_COMMANDER_EGG =
            ITEMS.register("undead_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.UNDEAD,
                            () -> ModEntities.UNDEAD_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CommanderSpawnEggItem> VILLAGER_COMMANDER_EGG =
            ITEMS.register("villager_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.VILLAGER,
                            () -> ModEntities.VILLAGER_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CommanderSpawnEggItem> ILLAGER_COMMANDER_EGG =
            ITEMS.register("illager_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.ILLAGER,
                            () -> ModEntities.ILLAGER_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CommanderSpawnEggItem> NETHER_COMMANDER_EGG =
            ITEMS.register("nether_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.NETHER,
                            () -> ModEntities.NETHER_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CommanderSpawnEggItem> END_COMMANDER_EGG =
            ITEMS.register("end_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.END,
                            () -> ModEntities.END_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CommanderSpawnEggItem> MONSTER_COMMANDER_EGG =
            ITEMS.register("monster_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.MONSTER,
                            () -> ModEntities.MONSTER_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));

    public static final DeferredItem<CommanderSpawnEggItem> NEUTRAL_COMMANDER_EGG =
            ITEMS.register("neutral_commander_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.NEUTRAL,
                            () -> ModEntities.NEUTRAL_COMMANDER.get(),
                            new Item.Properties().rarity(Rarity.RARE)));


    // ===================== 巡逻队长刷怪蛋 =====================

    public static final DeferredItem<CommanderSpawnEggItem> UNDEAD_PATROL_CAPTAIN_EGG =
            ITEMS.register("undead_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.UNDEAD,
                            () -> ModEntities.UNDEAD_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CommanderSpawnEggItem> VILLAGER_PATROL_CAPTAIN_EGG =
            ITEMS.register("villager_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.VILLAGER,
                            () -> ModEntities.VILLAGER_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CommanderSpawnEggItem> ILLAGER_PATROL_CAPTAIN_EGG =
            ITEMS.register("illager_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.ILLAGER,
                            () -> ModEntities.ILLAGER_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CommanderSpawnEggItem> NETHER_PATROL_CAPTAIN_EGG =
            ITEMS.register("nether_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.NETHER,
                            () -> ModEntities.NETHER_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CommanderSpawnEggItem> END_PATROL_CAPTAIN_EGG =
            ITEMS.register("end_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.END,
                            () -> ModEntities.END_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CommanderSpawnEggItem> MONSTER_PATROL_CAPTAIN_EGG =
            ITEMS.register("monster_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.MONSTER,
                            () -> ModEntities.MONSTER_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final DeferredItem<CommanderSpawnEggItem> NEUTRAL_PATROL_CAPTAIN_EGG =
            ITEMS.register("neutral_patrol_captain_spawn_egg",
                    () -> new CommanderSpawnEggItem(
                            FactionIds.NEUTRAL,
                            () -> ModEntities.NEUTRAL_PATROL_CAPTAIN.get(),
                            new Item.Properties().rarity(Rarity.UNCOMMON)));

    // ===================== 士兵刷怪蛋工具方法 =====================

    private static DeferredItem<SoldierSpawnEggItem> soldierEgg(
            String id,
            EquipmentTier tier,
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
            SoldierType def) {
        return ITEMS.register(id, () -> new SoldierSpawnEggItem(type, def, tier, new Item.Properties()));
    }

    // ===================== 僵尸 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_T0 =
            soldierEgg("soldier_zombie_tier_0", EquipmentTier.TIER_0, ModEntities.ZOMBIE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_T1 =
            soldierEgg("soldier_zombie_tier_1", EquipmentTier.TIER_1, ModEntities.ZOMBIE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_T2 =
            soldierEgg("soldier_zombie_tier_2", EquipmentTier.TIER_2, ModEntities.ZOMBIE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_T3 =
            soldierEgg("soldier_zombie_tier_3", EquipmentTier.TIER_3, ModEntities.ZOMBIE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_T4 =
            soldierEgg("soldier_zombie_tier_4", EquipmentTier.TIER_4, ModEntities.ZOMBIE_SOLDIER, SoldierType.MELEE_INFANTRY);

    // ===================== 尸壳 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_HUSK_T0 =
            soldierEgg("soldier_husk_tier_0", EquipmentTier.TIER_0, ModEntities.HUSK_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_HUSK_T1 =
            soldierEgg("soldier_husk_tier_1", EquipmentTier.TIER_1, ModEntities.HUSK_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_HUSK_T2 =
            soldierEgg("soldier_husk_tier_2", EquipmentTier.TIER_2, ModEntities.HUSK_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_HUSK_T3 =
            soldierEgg("soldier_husk_tier_3", EquipmentTier.TIER_3, ModEntities.HUSK_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_HUSK_T4 =
            soldierEgg("soldier_husk_tier_4", EquipmentTier.TIER_4, ModEntities.HUSK_SOLDIER, SoldierType.MELEE_INFANTRY);

    // ===================== 溺尸 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_DROWNED_T0 =
            soldierEgg("soldier_drowned_tier_0", EquipmentTier.TIER_0, ModEntities.DROWNED_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_DROWNED_T1 =
            soldierEgg("soldier_drowned_tier_1", EquipmentTier.TIER_1, ModEntities.DROWNED_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_DROWNED_T2 =
            soldierEgg("soldier_drowned_tier_2", EquipmentTier.TIER_2, ModEntities.DROWNED_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_DROWNED_T3 =
            soldierEgg("soldier_drowned_tier_3", EquipmentTier.TIER_3, ModEntities.DROWNED_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_DROWNED_T4 =
            soldierEgg("soldier_drowned_tier_4", EquipmentTier.TIER_4, ModEntities.DROWNED_SOLDIER, SoldierType.DUAL_INFANTRY);

    // ===================== 僵尸村民 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_VILLAGER_T0 =
            soldierEgg("soldier_zombie_villager_tier_0", EquipmentTier.TIER_0, ModEntities.ZOMBIE_VILLAGER_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_VILLAGER_T1 =
            soldierEgg("soldier_zombie_villager_tier_1", EquipmentTier.TIER_1, ModEntities.ZOMBIE_VILLAGER_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_VILLAGER_T2 =
            soldierEgg("soldier_zombie_villager_tier_2", EquipmentTier.TIER_2, ModEntities.ZOMBIE_VILLAGER_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_VILLAGER_T3 =
            soldierEgg("soldier_zombie_villager_tier_3", EquipmentTier.TIER_3, ModEntities.ZOMBIE_VILLAGER_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_ZOMBIE_VILLAGER_T4 =
            soldierEgg("soldier_zombie_villager_tier_4", EquipmentTier.TIER_4, ModEntities.ZOMBIE_VILLAGER_SOLDIER, SoldierType.MELEE_INFANTRY);

    // ===================== 骷髅 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_SKELETON_T0 =
            soldierEgg("soldier_skeleton_tier_0", EquipmentTier.TIER_0, ModEntities.SKELETON_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_SKELETON_T1 =
            soldierEgg("soldier_skeleton_tier_1", EquipmentTier.TIER_1, ModEntities.SKELETON_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_SKELETON_T2 =
            soldierEgg("soldier_skeleton_tier_2", EquipmentTier.TIER_2, ModEntities.SKELETON_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_SKELETON_T3 =
            soldierEgg("soldier_skeleton_tier_3", EquipmentTier.TIER_3, ModEntities.SKELETON_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_SKELETON_T4 =
            soldierEgg("soldier_skeleton_tier_4", EquipmentTier.TIER_4, ModEntities.SKELETON_SOLDIER, SoldierType.ARCHER_INFANTRY);

    // ===================== 流浪者 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_STRAY_T0 =
            soldierEgg("soldier_stray_tier_0", EquipmentTier.TIER_0, ModEntities.STRAY_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_STRAY_T1 =
            soldierEgg("soldier_stray_tier_1", EquipmentTier.TIER_1, ModEntities.STRAY_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_STRAY_T2 =
            soldierEgg("soldier_stray_tier_2", EquipmentTier.TIER_2, ModEntities.STRAY_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_STRAY_T3 =
            soldierEgg("soldier_stray_tier_3", EquipmentTier.TIER_3, ModEntities.STRAY_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_STRAY_T4 =
            soldierEgg("soldier_stray_tier_4", EquipmentTier.TIER_4, ModEntities.STRAY_SOLDIER, SoldierType.ARCHER_INFANTRY);

    // ===================== 沼骸 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_BOGGED_T0 =
            soldierEgg("soldier_bogged_tier_0", EquipmentTier.TIER_0, ModEntities.BOGGED_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_BOGGED_T1 =
            soldierEgg("soldier_bogged_tier_1", EquipmentTier.TIER_1, ModEntities.BOGGED_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_BOGGED_T2 =
            soldierEgg("soldier_bogged_tier_2", EquipmentTier.TIER_2, ModEntities.BOGGED_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_BOGGED_T3 =
            soldierEgg("soldier_bogged_tier_3", EquipmentTier.TIER_3, ModEntities.BOGGED_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_BOGGED_T4 =
            soldierEgg("soldier_bogged_tier_4", EquipmentTier.TIER_4, ModEntities.BOGGED_SOLDIER, SoldierType.ARCHER_INFANTRY);

    // ===================== 凋零骷髅 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITHER_SKELETON_T0 =
            soldierEgg("soldier_wither_skeleton_tier_0", EquipmentTier.TIER_0, ModEntities.WITHER_SKELETON_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITHER_SKELETON_T1 =
            soldierEgg("soldier_wither_skeleton_tier_1", EquipmentTier.TIER_1, ModEntities.WITHER_SKELETON_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITHER_SKELETON_T2 =
            soldierEgg("soldier_wither_skeleton_tier_2", EquipmentTier.TIER_2, ModEntities.WITHER_SKELETON_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITHER_SKELETON_T3 =
            soldierEgg("soldier_wither_skeleton_tier_3", EquipmentTier.TIER_3, ModEntities.WITHER_SKELETON_SOLDIER, SoldierType.DUAL_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITHER_SKELETON_T4 =
            soldierEgg("soldier_wither_skeleton_tier_4", EquipmentTier.TIER_4, ModEntities.WITHER_SKELETON_SOLDIER, SoldierType.DUAL_INFANTRY);

    // ===================== 女巫 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITCH_T0 =
            soldierEgg("soldier_witch_tier_0", EquipmentTier.TIER_0, ModEntities.WITCH_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITCH_T1 =
            soldierEgg("soldier_witch_tier_1", EquipmentTier.TIER_1, ModEntities.WITCH_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITCH_T2 =
            soldierEgg("soldier_witch_tier_2", EquipmentTier.TIER_2, ModEntities.WITCH_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITCH_T3 =
            soldierEgg("soldier_witch_tier_3", EquipmentTier.TIER_3, ModEntities.WITCH_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_WITCH_T4 =
            soldierEgg("soldier_witch_tier_4", EquipmentTier.TIER_4, ModEntities.WITCH_SOLDIER, SoldierType.THROWER_INFANTRY);

    // ===================== 掠夺者步枪兵 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_RIFLE_T0 =
            soldierEgg("soldier_gunner_rifle_tier_0", EquipmentTier.TIER_0, ModEntities.RIFLE_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_RIFLE_T1 =
            soldierEgg("soldier_gunner_rifle_tier_1", EquipmentTier.TIER_1, ModEntities.RIFLE_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_RIFLE_T2 =
            soldierEgg("soldier_gunner_rifle_tier_2", EquipmentTier.TIER_2, ModEntities.RIFLE_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_RIFLE_T3 =
            soldierEgg("soldier_gunner_rifle_tier_3", EquipmentTier.TIER_3, ModEntities.RIFLE_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_RIFLE_T4 =
            soldierEgg("soldier_gunner_rifle_tier_4", EquipmentTier.TIER_4, ModEntities.RIFLE_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);

    // ===================== 掠夺者霰弹枪兵 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SHOTGUN_T0 =
            soldierEgg("soldier_gunner_shotgun_tier_0", EquipmentTier.TIER_0, ModEntities.SHOTGUN_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SHOTGUN_T1 =
            soldierEgg("soldier_gunner_shotgun_tier_1", EquipmentTier.TIER_1, ModEntities.SHOTGUN_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SHOTGUN_T2 =
            soldierEgg("soldier_gunner_shotgun_tier_2", EquipmentTier.TIER_2, ModEntities.SHOTGUN_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SHOTGUN_T3 =
            soldierEgg("soldier_gunner_shotgun_tier_3", EquipmentTier.TIER_3, ModEntities.SHOTGUN_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SHOTGUN_T4 =
            soldierEgg("soldier_gunner_shotgun_tier_4", EquipmentTier.TIER_4, ModEntities.SHOTGUN_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);

    // ===================== 掠夺者火箭筒兵 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_ROCKET_T0 =
            soldierEgg("soldier_gunner_rocket_tier_0", EquipmentTier.TIER_0, ModEntities.ROCKET_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_ROCKET_T1 =
            soldierEgg("soldier_gunner_rocket_tier_1", EquipmentTier.TIER_1, ModEntities.ROCKET_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_ROCKET_T2 =
            soldierEgg("soldier_gunner_rocket_tier_2", EquipmentTier.TIER_2, ModEntities.ROCKET_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_ROCKET_T3 =
            soldierEgg("soldier_gunner_rocket_tier_3", EquipmentTier.TIER_3, ModEntities.ROCKET_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_ROCKET_T4 =
            soldierEgg("soldier_gunner_rocket_tier_4", EquipmentTier.TIER_4, ModEntities.ROCKET_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);

    // ===================== 掠夺者狙击手 =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SNIPER_T0 =
            soldierEgg("soldier_gunner_sniper_tier_0", EquipmentTier.TIER_0, ModEntities.SNIPER_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SNIPER_T1 =
            soldierEgg("soldier_gunner_sniper_tier_1", EquipmentTier.TIER_1, ModEntities.SNIPER_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SNIPER_T2 =
            soldierEgg("soldier_gunner_sniper_tier_2", EquipmentTier.TIER_2, ModEntities.SNIPER_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SNIPER_T3 =
            soldierEgg("soldier_gunner_sniper_tier_3", EquipmentTier.TIER_3, ModEntities.SNIPER_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_GUNNER_SNIPER_T4 =
            soldierEgg("soldier_gunner_sniper_tier_4", EquipmentTier.TIER_4, ModEntities.SNIPER_GUNNER_SOLDIER, SoldierType.GUNNER_INFANTRY);

    // ===================== 卫道士士兵（近战 · 破盾） =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_VINDICATOR_T0 =
            soldierEgg("soldier_vindicator_tier_0", EquipmentTier.TIER_0, ModEntities.VINDICATOR_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VINDICATOR_T1 =
            soldierEgg("soldier_vindicator_tier_1", EquipmentTier.TIER_1, ModEntities.VINDICATOR_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VINDICATOR_T2 =
            soldierEgg("soldier_vindicator_tier_2", EquipmentTier.TIER_2, ModEntities.VINDICATOR_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VINDICATOR_T3 =
            soldierEgg("soldier_vindicator_tier_3", EquipmentTier.TIER_3, ModEntities.VINDICATOR_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VINDICATOR_T4 =
            soldierEgg("soldier_vindicator_tier_4", EquipmentTier.TIER_4, ModEntities.VINDICATOR_SOLDIER, SoldierType.MELEE_INFANTRY);

    // ===================== 唤魔者士兵（魔法师 · 尖牙 / 重伤） =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_EVOKER_T0 =
            soldierEgg("soldier_evoker_tier_0", EquipmentTier.TIER_0, ModEntities.EVOKER_SOLDIER, SoldierType.MAGE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_EVOKER_T1 =
            soldierEgg("soldier_evoker_tier_1", EquipmentTier.TIER_1, ModEntities.EVOKER_SOLDIER, SoldierType.MAGE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_EVOKER_T2 =
            soldierEgg("soldier_evoker_tier_2", EquipmentTier.TIER_2, ModEntities.EVOKER_SOLDIER, SoldierType.MAGE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_EVOKER_T3 =
            soldierEgg("soldier_evoker_tier_3", EquipmentTier.TIER_3, ModEntities.EVOKER_SOLDIER, SoldierType.MAGE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_EVOKER_T4 =
            soldierEgg("soldier_evoker_tier_4", EquipmentTier.TIER_4, ModEntities.EVOKER_SOLDIER, SoldierType.MAGE_INFANTRY);

    // ===================== 村民重锤兵（近战 · 破甲重锤） =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_MACE_T0 =
            soldierEgg("soldier_mace_tier_0", EquipmentTier.TIER_0, ModEntities.VILLAGER_MACE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_MACE_T1 =
            soldierEgg("soldier_mace_tier_1", EquipmentTier.TIER_1, ModEntities.VILLAGER_MACE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_MACE_T2 =
            soldierEgg("soldier_mace_tier_2", EquipmentTier.TIER_2, ModEntities.VILLAGER_MACE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_MACE_T3 =
            soldierEgg("soldier_mace_tier_3", EquipmentTier.TIER_3, ModEntities.VILLAGER_MACE_SOLDIER, SoldierType.MELEE_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_MACE_T4 =
            soldierEgg("soldier_mace_tier_4", EquipmentTier.TIER_4, ModEntities.VILLAGER_MACE_SOLDIER, SoldierType.MELEE_INFANTRY);

    // ===================== 村民弓箭手士兵（远程 · 细雪冻结） =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_ARCHER_T0 =
            soldierEgg("soldier_villager_archer_tier_0", EquipmentTier.TIER_0, ModEntities.VILLAGER_ARCHER_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_ARCHER_T1 =
            soldierEgg("soldier_villager_archer_tier_1", EquipmentTier.TIER_1, ModEntities.VILLAGER_ARCHER_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_ARCHER_T2 =
            soldierEgg("soldier_villager_archer_tier_2", EquipmentTier.TIER_2, ModEntities.VILLAGER_ARCHER_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_ARCHER_T3 =
            soldierEgg("soldier_villager_archer_tier_3", EquipmentTier.TIER_3, ModEntities.VILLAGER_ARCHER_SOLDIER, SoldierType.ARCHER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_ARCHER_T4 =
            soldierEgg("soldier_villager_archer_tier_4", EquipmentTier.TIER_4, ModEntities.VILLAGER_ARCHER_SOLDIER, SoldierType.ARCHER_INFANTRY);

    // ===================== 村民牧师士兵（投掷 · 净化治疗 / 中毒） =====================

    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_PRIEST_T0 =
            soldierEgg("soldier_villager_priest_tier_0", EquipmentTier.TIER_0, ModEntities.VILLAGER_PRIEST_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_PRIEST_T1 =
            soldierEgg("soldier_villager_priest_tier_1", EquipmentTier.TIER_1, ModEntities.VILLAGER_PRIEST_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_PRIEST_T2 =
            soldierEgg("soldier_villager_priest_tier_2", EquipmentTier.TIER_2, ModEntities.VILLAGER_PRIEST_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_PRIEST_T3 =
            soldierEgg("soldier_villager_priest_tier_3", EquipmentTier.TIER_3, ModEntities.VILLAGER_PRIEST_SOLDIER, SoldierType.THROWER_INFANTRY);
    public static final DeferredItem<SoldierSpawnEggItem> EGG_VILLAGER_PRIEST_T4 =
            soldierEgg("soldier_villager_priest_tier_4", EquipmentTier.TIER_4, ModEntities.VILLAGER_PRIEST_SOLDIER, SoldierType.THROWER_INFANTRY);

    // ===================== 枪械与弹药 =====================

    // 玩家可用枪械（射击 / 装填 / 蓄力逻辑见 gun 包）
    public static final DeferredItem<GunItem> RIFLE =
            ITEMS.register("rifle", () -> new GunItem(GunSpecs.RIFLE,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GunItem> SHOTGUN =
            ITEMS.register("shotgun", () -> new GunItem(GunSpecs.SHOTGUN,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GunItem> ROCKET_LAUNCHER =
            ITEMS.register("rocket_launcher", () -> new GunItem(GunSpecs.LAUNCHER,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<GunItem> SNIPER_RIFLE =
            ITEMS.register("sniper_rifle", () -> new GunItem(GunSpecs.SNIPER,
                    new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> ROCKET_AMMO =
            ITEMS.register("rocket_ammo", () -> new Item(new Item.Properties()));

    /** 火箭弹实体专用外观物品（隐藏，不进创造栏）：与火箭弹物品模型解耦。 */
    public static final DeferredItem<Item> ROCKET_PROJECTILE =
            ITEMS.register("rocket_projectile", () -> new Item(new Item.Properties()));

    /** 子弹实体专用外观物品（隐藏，不进创造栏）：与子弹物品模型解耦。 */
    public static final DeferredItem<Item> BULLET_PROJECTILE =
            ITEMS.register("bullet_projectile", () -> new Item(new Item.Properties()));

    // 可装填弹药（与弹体解耦：发射时统一使用军团子弹 / 火箭弹实体）
    public static final DeferredItem<Item> RIFLE_AMMO =
            ITEMS.register("rifle_ammo", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SHOTGUN_SHELL =
            ITEMS.register("shotgun_shell", () -> new Item(new Item.Properties()));
    public static final DeferredItem<Item> SNIPER_AMMO =
            ITEMS.register("sniper_ammo", () -> new Item(new Item.Properties()));

    // 装备：弹药箱（右键打开 2 排弹药容器）
    public static final DeferredItem<AmmoBoxItem> AMMO_BOX =
            ITEMS.register("ammo_box", () -> new AmmoBoxItem(new Item.Properties().stacksTo(1)));

    // ===================== 战术装备栏：马哨 =====================

    /** 马哨：右键召唤 / 骑乘玩家战马；放在战术装备栏可由 R 轮盘控制上下马。 */
    public static final DeferredItem<HorseWhistleItem> HORSE_WHISTLE =
            ITEMS.register("horse_whistle", () -> new HorseWhistleItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ===================== 战术装备栏：保暖衣 =====================

    /** 保暖衣：放入战术装备栏后免疫冻结效果（冻结箭 / 细雪结霜与冻结伤害）。 */
    public static final DeferredItem<WarmClothesItem> WARM_CLOTHES =
            ITEMS.register("warm_clothes", () -> new WarmClothesItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    /** 防弹衣：放入战术装备栏后，被子弹 / 炮弹 / 弹射物击中伤害 -70%，按减免量扣耐久（初始 500）。 */
    public static final DeferredItem<BulletproofVestItem> BULLETPROOF_VEST =
            ITEMS.register("bulletproof_vest", () -> new BulletproofVestItem(
                    new Item.Properties().stacksTo(1)
                            .durability(BulletproofVestItem.MAX_DURABILITY)
                            .rarity(Rarity.UNCOMMON)));

    /** 背包：72 格容器（8 排 × 9 格）；右键 / 战术装备栏里按 B 键打开；枪械与设施会直接读取里面的物品。 */
    public static final DeferredItem<com.ohhapple.monster_conquest.item.BackpackItem> BACKPACK =
            ITEMS.register("backpack", () -> new com.ohhapple.monster_conquest.item.BackpackItem(
                    new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    // ===================== 骑枪 =====================

    public static final DeferredItem<LanceItem> IRON_LANCE =
            ITEMS.register("iron_lance",
                    () -> new LanceItem(Tiers.IRON, 6.0f,
                            new Item.Properties().attributes(createLanceAttributes(Tiers.IRON, 6.0f))));

    public static final DeferredItem<LanceItem> DIAMOND_LANCE =
            ITEMS.register("diamond_lance",
                    () -> new LanceItem(Tiers.DIAMOND, 8.0f,
                            new Item.Properties().attributes(createLanceAttributes(Tiers.DIAMOND, 8.0f))));

    public static final DeferredItem<LanceItem> NETHERITE_LANCE =
            ITEMS.register("netherite_lance",
                    () -> new LanceItem(Tiers.NETHERITE, 11.0f,
                            new Item.Properties().fireResistant()
                                    .attributes(createLanceAttributes(Tiers.NETHERITE, 11.0f))));

    private static ItemAttributeModifiers createLanceAttributes(Tier tier, float baseBonus) {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                baseBonus + tier.getAttackDamageBonus(),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID,
                                -2.4f,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    // ===================== 首领武器 =====================

    public static final DeferredItem<CommanderBlade> COMMANDER_BLADE =
            ITEMS.register("commander_blade",
                    () -> new CommanderBlade(Tiers.NETHERITE,
                            new Item.Properties()
                                    .rarity(Rarity.EPIC)
                                    .fireResistant()
                                    .attributes(SwordItem.createAttributes(Tiers.NETHERITE, 5, -2.2f))));

    /** 阵营长杆旗帜：首领 / 巡逻队长手持；颜色图案随阵营（客户端按阵营数据染色）。 */
    public static final DeferredItem<FactionBannerItem> FACTION_BANNER =
            ITEMS.register("faction_banner",
                    () -> new FactionBannerItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // ===================== 工具 =====================

    public static final DeferredItem<HatredWandItem> HATRED_WAND =
            ITEMS.register("hatred_wand",
                    () -> new HatredWandItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<RetrievalToolItem> RETRIEVAL_TOOL =
            ITEMS.register("retrieval_tool",
                    () -> new RetrievalToolItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 说明书：右键打开模组玩法 / RTS / 阵营 / 士兵等详细说明。 */
    public static final DeferredItem<com.ohhapple.monster_conquest.item.GuideBookItem> GUIDE_BOOK =
            ITEMS.register("guide_book",
                    () -> new com.ohhapple.monster_conquest.item.GuideBookItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    // 控制工具
    public static final DeferredItem<ControlToolItem> CONTROL_TOOL =
            ITEMS.register("control_tool",
                    () -> new ControlToolItem(
                            new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final DeferredItem<Item> TAB_ICON =
            ITEMS.register("tab_icon",
                    () -> new Item(new Item.Properties()));

    // ===================== 募兵站（方块 + 物品） =====================

    public static final net.neoforged.neoforge.registries.DeferredBlock<RecruitmentStationBlock> RECRUITMENT_STATION =
            BLOCKS.register("recruitment_station", () -> new RecruitmentStationBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.STONE)
                            .strength(3.5f)
                            .sound(net.minecraft.world.level.block.SoundType.STONE)));

    public static final DeferredItem<BlockItem> RECRUITMENT_STATION_ITEM =
            ITEMS.register("recruitment_station", () -> new BlockItem(
                    RECRUITMENT_STATION.get(), new Item.Properties()));

    // ===================== 交易站（方块 + 物品） =====================

    public static final net.neoforged.neoforge.registries.DeferredBlock<TradeStationBlock> TRADE_STATION =
            BLOCKS.register("trade_station", () -> new TradeStationBlock(
                    net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                            .mapColor(net.minecraft.world.level.material.MapColor.WOOD)
                            .strength(3.5f)
                            .sound(net.minecraft.world.level.block.SoundType.WOOD)));

    public static final DeferredItem<BlockItem> TRADE_STATION_ITEM =
            ITEMS.register("trade_station", () -> new BlockItem(
                    TRADE_STATION.get(), new Item.Properties()));

    // ===================== 创造模式标签页 =====================

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MC_TAB =
            CREATIVE_MODE_TABS.register("monster_conquest_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.monster_conquest"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> TAB_ICON.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        // 7 个阵营首领
                        output.accept(UNDEAD_COMMANDER_EGG.get());
                        output.accept(VILLAGER_COMMANDER_EGG.get());
                        output.accept(ILLAGER_COMMANDER_EGG.get());
                        output.accept(NETHER_COMMANDER_EGG.get());
                        output.accept(END_COMMANDER_EGG.get());
                        output.accept(MONSTER_COMMANDER_EGG.get());
                        output.accept(NEUTRAL_COMMANDER_EGG.get());

                        // 7 个巡逻队长
                        output.accept(UNDEAD_PATROL_CAPTAIN_EGG.get());
                        output.accept(VILLAGER_PATROL_CAPTAIN_EGG.get());
                        output.accept(ILLAGER_PATROL_CAPTAIN_EGG.get());
                        output.accept(NETHER_PATROL_CAPTAIN_EGG.get());
                        output.accept(END_PATROL_CAPTAIN_EGG.get());
                        output.accept(MONSTER_PATROL_CAPTAIN_EGG.get());
                        output.accept(NEUTRAL_PATROL_CAPTAIN_EGG.get());

                        // 僵尸
                        output.accept(EGG_ZOMBIE_T0.get());
                        output.accept(EGG_ZOMBIE_T1.get());
                        output.accept(EGG_ZOMBIE_T2.get());
                        output.accept(EGG_ZOMBIE_T3.get());
                        output.accept(EGG_ZOMBIE_T4.get());

                        // 尸壳
                        output.accept(EGG_HUSK_T0.get());
                        output.accept(EGG_HUSK_T1.get());
                        output.accept(EGG_HUSK_T2.get());
                        output.accept(EGG_HUSK_T3.get());
                        output.accept(EGG_HUSK_T4.get());

                        // 溺尸
                        output.accept(EGG_DROWNED_T0.get());
                        output.accept(EGG_DROWNED_T1.get());
                        output.accept(EGG_DROWNED_T2.get());
                        output.accept(EGG_DROWNED_T3.get());
                        output.accept(EGG_DROWNED_T4.get());

                        // 僵尸村民
                        output.accept(EGG_ZOMBIE_VILLAGER_T0.get());
                        output.accept(EGG_ZOMBIE_VILLAGER_T1.get());
                        output.accept(EGG_ZOMBIE_VILLAGER_T2.get());
                        output.accept(EGG_ZOMBIE_VILLAGER_T3.get());
                        output.accept(EGG_ZOMBIE_VILLAGER_T4.get());

                        // 骷髅
                        output.accept(EGG_SKELETON_T0.get());
                        output.accept(EGG_SKELETON_T1.get());
                        output.accept(EGG_SKELETON_T2.get());
                        output.accept(EGG_SKELETON_T3.get());
                        output.accept(EGG_SKELETON_T4.get());

                        // 流浪者
                        output.accept(EGG_STRAY_T0.get());
                        output.accept(EGG_STRAY_T1.get());
                        output.accept(EGG_STRAY_T2.get());
                        output.accept(EGG_STRAY_T3.get());
                        output.accept(EGG_STRAY_T4.get());

                        // 沼骸
                        output.accept(EGG_BOGGED_T0.get());
                        output.accept(EGG_BOGGED_T1.get());
                        output.accept(EGG_BOGGED_T2.get());
                        output.accept(EGG_BOGGED_T3.get());
                        output.accept(EGG_BOGGED_T4.get());

                        // 凋零骷髅
                        output.accept(EGG_WITHER_SKELETON_T0.get());
                        output.accept(EGG_WITHER_SKELETON_T1.get());
                        output.accept(EGG_WITHER_SKELETON_T2.get());
                        output.accept(EGG_WITHER_SKELETON_T3.get());
                        output.accept(EGG_WITHER_SKELETON_T4.get());

                        // 女巫
                        output.accept(EGG_WITCH_T0.get());
                        output.accept(EGG_WITCH_T1.get());
                        output.accept(EGG_WITCH_T2.get());
                        output.accept(EGG_WITCH_T3.get());
                        output.accept(EGG_WITCH_T4.get());

                        // 掠夺者步枪兵
                        output.accept(EGG_GUNNER_RIFLE_T0.get());
                        output.accept(EGG_GUNNER_RIFLE_T1.get());
                        output.accept(EGG_GUNNER_RIFLE_T2.get());
                        output.accept(EGG_GUNNER_RIFLE_T3.get());
                        output.accept(EGG_GUNNER_RIFLE_T4.get());

                        // 掠夺者霰弹枪兵
                        output.accept(EGG_GUNNER_SHOTGUN_T0.get());
                        output.accept(EGG_GUNNER_SHOTGUN_T1.get());
                        output.accept(EGG_GUNNER_SHOTGUN_T2.get());
                        output.accept(EGG_GUNNER_SHOTGUN_T3.get());
                        output.accept(EGG_GUNNER_SHOTGUN_T4.get());

                        // 掠夺者火箭筒兵
                        output.accept(EGG_GUNNER_ROCKET_T0.get());
                        output.accept(EGG_GUNNER_ROCKET_T1.get());
                        output.accept(EGG_GUNNER_ROCKET_T2.get());
                        output.accept(EGG_GUNNER_ROCKET_T3.get());
                        output.accept(EGG_GUNNER_ROCKET_T4.get());

                        // 掠夺者狙击手
                        output.accept(EGG_GUNNER_SNIPER_T0.get());
                        output.accept(EGG_GUNNER_SNIPER_T1.get());
                        output.accept(EGG_GUNNER_SNIPER_T2.get());
                        output.accept(EGG_GUNNER_SNIPER_T3.get());
                        output.accept(EGG_GUNNER_SNIPER_T4.get());

                        // 卫道士士兵
                        output.accept(EGG_VINDICATOR_T0.get());
                        output.accept(EGG_VINDICATOR_T1.get());
                        output.accept(EGG_VINDICATOR_T2.get());
                        output.accept(EGG_VINDICATOR_T3.get());
                        output.accept(EGG_VINDICATOR_T4.get());

                        // 唤魔者士兵
                        output.accept(EGG_EVOKER_T0.get());
                        output.accept(EGG_EVOKER_T1.get());
                        output.accept(EGG_EVOKER_T2.get());
                        output.accept(EGG_EVOKER_T3.get());
                        output.accept(EGG_EVOKER_T4.get());

                        // 村民重锤兵
                        output.accept(EGG_MACE_T0.get());
                        output.accept(EGG_MACE_T1.get());
                        output.accept(EGG_MACE_T2.get());
                        output.accept(EGG_MACE_T3.get());
                        output.accept(EGG_MACE_T4.get());

                        // 村民弓箭手士兵
                        output.accept(EGG_VILLAGER_ARCHER_T0.get());
                        output.accept(EGG_VILLAGER_ARCHER_T1.get());
                        output.accept(EGG_VILLAGER_ARCHER_T2.get());
                        output.accept(EGG_VILLAGER_ARCHER_T3.get());
                        output.accept(EGG_VILLAGER_ARCHER_T4.get());

                        // 村民牧师士兵
                        output.accept(EGG_VILLAGER_PRIEST_T0.get());
                        output.accept(EGG_VILLAGER_PRIEST_T1.get());
                        output.accept(EGG_VILLAGER_PRIEST_T2.get());
                        output.accept(EGG_VILLAGER_PRIEST_T3.get());
                        output.accept(EGG_VILLAGER_PRIEST_T4.get());

                        // 枪械
                        output.accept(RIFLE.get());
                        output.accept(SHOTGUN.get());
                        output.accept(ROCKET_LAUNCHER.get());
                        output.accept(SNIPER_RIFLE.get());
                        // 弹药
                        output.accept(ROCKET_AMMO.get());
                        output.accept(RIFLE_AMMO.get());
                        output.accept(SHOTGUN_SHELL.get());
                        output.accept(SNIPER_AMMO.get());
                        // 战术装备（可放入战术装备栏：保暖衣 / 马哨 / 弹药箱）
                        output.accept(AMMO_BOX.get());
                        output.accept(HORSE_WHISTLE.get());
                        output.accept(WARM_CLOTHES.get());
                        output.accept(BULLETPROOF_VEST.get());
                        output.accept(BACKPACK.get());
                        // 近战武器
                        output.accept(IRON_LANCE.get());
                        output.accept(DIAMOND_LANCE.get());
                        output.accept(NETHERITE_LANCE.get());
                        output.accept(COMMANDER_BLADE.get());
                        output.accept(FACTION_BANNER.get());
                        // 工具
                        output.accept(HATRED_WAND.get());
                        output.accept(RETRIEVAL_TOOL.get());
                        output.accept(CONTROL_TOOL.get());
                        // 设施方块：募兵站 / 交易站
                        output.accept(RECRUITMENT_STATION_ITEM.get());
                        output.accept(TRADE_STATION_ITEM.get());
                        // 指南
                        output.accept(GUIDE_BOOK.get());
                    })
                    .build());

    // ===================== 构造 =====================

    public Monster_conquest(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        ModEntities.ENTITY_TYPES.register(modEventBus);
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
        com.ohhapple.monster_conquest.menu.ModMenus.MENUS.register(modEventBus);
        com.ohhapple.monster_conquest.equipment.ModAttachments.ATTACHMENTS.register(modEventBus);

        // 配置文件按分类拆分，统一放在 config/monster_conquest/ 目录下
        modContainer.registerConfig(ModConfig.Type.COMMON, GeneralConfig.SPEC,
                "monster_conquest/general-common.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, FactionConfig.SPEC,
                "monster_conquest/factions-common.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, CombatConfig.SPEC,
                "monster_conquest/combat-common.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, PlayerConfig.SPEC,
                "monster_conquest/player-common.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, RecruitConfig.SPEC,
                "monster_conquest/recruit-common.toml");

        // 注册所有内置阵营
        FactionRegistry.register(UndeadFaction.INSTANCE);
        FactionRegistry.register(VillagerFaction.INSTANCE);
        FactionRegistry.register(IllagerFaction.INSTANCE);
        FactionRegistry.register(NetherFaction.INSTANCE);
        FactionRegistry.register(EndFaction.INSTANCE);
        FactionRegistry.register(MonsterFaction.INSTANCE);
        FactionRegistry.register(NeutralFaction.INSTANCE);
    }

    // ===================== Payload =====================
    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");

        // ===================== 客户端 → 服务端 =====================
        reg.playToServer(CrosshairSelectPayload.TYPE, CrosshairSelectPayload.STREAM_CODEC,
                CrosshairSelectPayload::handle);
        reg.playToServer(CrosshairUsePayload.TYPE, CrosshairUsePayload.STREAM_CODEC,
                CrosshairUsePayload::handle);

        reg.playToServer(HatredWandModePayload.TYPE, HatredWandModePayload.STREAM_CODEC,
                HatredWandModePayload::handle);
        reg.playToServer(CycleEggFactionPayload.TYPE, CycleEggFactionPayload.STREAM_CODEC,
                CycleEggFactionPayload::handle);
        reg.playToServer(ToggleEggCavalryPayload.TYPE, ToggleEggCavalryPayload.STREAM_CODEC,
                ToggleEggCavalryPayload::handle);
        reg.playToServer(RecruitSoldierPayload.TYPE, RecruitSoldierPayload.STREAM_CODEC,
                RecruitSoldierPayload::handle);
        reg.playToServer(UpgradeSoldierPayload.TYPE, UpgradeSoldierPayload.STREAM_CODEC,
                UpgradeSoldierPayload::handle);
        reg.playToServer(BuyTradeOfferPayload.TYPE, BuyTradeOfferPayload.STREAM_CODEC,
                BuyTradeOfferPayload::handle);
        reg.playToServer(com.ohhapple.monster_conquest.network.serverbound.backpack
                        .OpenBackpackFromSlotPayload.TYPE,
                com.ohhapple.monster_conquest.network.serverbound.backpack
                        .OpenBackpackFromSlotPayload.STREAM_CODEC,
                com.ohhapple.monster_conquest.network.serverbound.backpack
                        .OpenBackpackFromSlotPayload::handle);
        reg.playToServer(com.ohhapple.monster_conquest.network.serverbound.backpack
                        .OpenEquippedBackpackPayload.TYPE,
                com.ohhapple.monster_conquest.network.serverbound.backpack
                        .OpenEquippedBackpackPayload.STREAM_CODEC,
                com.ohhapple.monster_conquest.network.serverbound.backpack
                        .OpenEquippedBackpackPayload::handle);
        reg.playToServer(ControlToolModePayload.TYPE, ControlToolModePayload.STREAM_CODEC,
                ControlToolModePayload::handle);
        reg.playToServer(RetrievalToolModePayload.TYPE, RetrievalToolModePayload.STREAM_CODEC,
                RetrievalToolModePayload::handle);
        reg.playToServer(com.ohhapple.monster_conquest.network.serverbound.unit
                        .UnitControlPayload.TYPE,
                com.ohhapple.monster_conquest.network.serverbound.unit
                        .UnitControlPayload.STREAM_CODEC,
                com.ohhapple.monster_conquest.network.serverbound.unit
                        .UnitControlPayload::handle);
        reg.playToServer(com.ohhapple.monster_conquest.network.serverbound.unit
                        .UnitRetrievePayload.TYPE,
                com.ohhapple.monster_conquest.network.serverbound.unit
                        .UnitRetrievePayload.STREAM_CODEC,
                com.ohhapple.monster_conquest.network.serverbound.unit
                        .UnitRetrievePayload::handle);

        reg.playToServer(ToggleFactionHostilityPayload.TYPE,
                ToggleFactionHostilityPayload.STREAM_CODEC,
                ToggleFactionHostilityPayload::handle);
        reg.playToServer(ToggleFactionAllyPayload.TYPE,
                ToggleFactionAllyPayload.STREAM_CODEC,
                ToggleFactionAllyPayload::handle);
        reg.playToServer(RequestOpenFactionGuiPayload.TYPE,
                RequestOpenFactionGuiPayload.STREAM_CODEC,
                RequestOpenFactionGuiPayload::handle);
        reg.playToServer(DeleteFactionPayload.TYPE,
                DeleteFactionPayload.STREAM_CODEC,
                DeleteFactionPayload::handle);

        reg.playToServer(PlayerCommandPayload.TYPE, PlayerCommandPayload.STREAM_CODEC,
                PlayerCommandPayload::handle);
        reg.playToServer(SelectSquadPayload.TYPE, SelectSquadPayload.STREAM_CODEC,
                SelectSquadPayload::handle);

        reg.playToServer(RtsTogglePayload.TYPE, RtsTogglePayload.STREAM_CODEC,
                RtsTogglePayload::handle);
        reg.playToServer(RtsSelectPayload.TYPE, RtsSelectPayload.STREAM_CODEC,
                RtsSelectPayload::handle);
        reg.playToServer(RtsSquadClickPayload.TYPE, RtsSquadClickPayload.STREAM_CODEC,
                RtsSquadClickPayload::handle);
        reg.playToServer(RtsSelectSimilarPayload.TYPE, RtsSelectSimilarPayload.STREAM_CODEC,
                RtsSelectSimilarPayload::handle);
        reg.playToServer(RtsCommandPayload.TYPE, RtsCommandPayload.STREAM_CODEC,
                RtsCommandPayload::handle);
        reg.playToServer(RtsOrderPayload.TYPE, RtsOrderPayload.STREAM_CODEC,
                RtsOrderPayload::handle);

        // 枪械 / 战术装备栏
        reg.playToServer(GunFirePayload.TYPE, GunFirePayload.STREAM_CODEC,
                GunFirePayload::handle);
        reg.playToServer(OpenTacticalEquipmentSlotsPayload.TYPE, OpenTacticalEquipmentSlotsPayload.STREAM_CODEC,
                OpenTacticalEquipmentSlotsPayload::handle);
        reg.playToServer(OpenAmmoBoxFromSlotPayload.TYPE, OpenAmmoBoxFromSlotPayload.STREAM_CODEC,
                OpenAmmoBoxFromSlotPayload::handle);

        // ===================== 服务端 → 客户端 =====================
        // 全部走 ClientboundPayloadRouter：服务端只加载纯 common 路由类，
        //   客户端逻辑延迟到实际收到包时反射加载，避免专用服务端加载客户端类。
        reg.playToClient(FactionHostilitySnapshotPayload.TYPE,
                FactionHostilitySnapshotPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(FactionAllySnapshotPayload.TYPE,
                FactionAllySnapshotPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(OpenFactionGuiPayload.TYPE,
                OpenFactionGuiPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(SyncSquadPayload.TYPE, SyncSquadPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(SyncRtsSelectionPayload.TYPE, SyncRtsSelectionPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);

        // 枪械：装填状态
        reg.playToClient(GunStatusPayload.TYPE, GunStatusPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(SyncEquipmentPayload.TYPE, SyncEquipmentPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(OpenRecruitGuiPayload.TYPE, OpenRecruitGuiPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
        reg.playToClient(OpenTradeGuiPayload.TYPE, OpenTradeGuiPayload.STREAM_CODEC,
                ClientboundPayloadRouter::dispatch);
    }

    // ===================== 通用 =====================
    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Monster Conquest common setup complete.");
    }

}
