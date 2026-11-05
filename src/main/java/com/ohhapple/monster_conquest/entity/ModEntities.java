package com.ohhapple.monster_conquest.entity;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.entity.ArenaArmadillo;
import com.ohhapple.monster_conquest.arena.entity.ArenaBlaze;
import com.ohhapple.monster_conquest.arena.entity.ArenaCaveSpider;
import com.ohhapple.monster_conquest.arena.entity.ArenaEnderDragon;
import com.ohhapple.monster_conquest.arena.entity.ArenaEndermite;
import com.ohhapple.monster_conquest.arena.entity.ArenaFireball;
import com.ohhapple.monster_conquest.arena.entity.ArenaGoat;
import com.ohhapple.monster_conquest.arena.entity.ArenaBreeze;
import com.ohhapple.monster_conquest.arena.entity.ArenaHoglin;
import com.ohhapple.monster_conquest.arena.entity.ArenaIronGolem;
import com.ohhapple.monster_conquest.arena.entity.ArenaMagmaCube;
import com.ohhapple.monster_conquest.arena.entity.ArenaPolarBear;
import com.ohhapple.monster_conquest.arena.entity.ArenaRavager;
import com.ohhapple.monster_conquest.arena.entity.ArenaSniffer;
import com.ohhapple.monster_conquest.arena.entity.ArenaSlime;
import com.ohhapple.monster_conquest.arena.entity.ArenaElderGuardian;
import com.ohhapple.monster_conquest.arena.entity.ArenaRabbit;
import com.ohhapple.monster_conquest.arena.entity.ArenaDonkey;
import com.ohhapple.monster_conquest.arena.entity.ArenaSheep;
import com.ohhapple.monster_conquest.arena.entity.ArenaCow;
import com.ohhapple.monster_conquest.arena.entity.ArenaPig;
import com.ohhapple.monster_conquest.arena.entity.ArenaZombifiedPiglin;
import com.ohhapple.monster_conquest.arena.entity.ArenaFrog;
import com.ohhapple.monster_conquest.arena.entity.ArenaFox;
import com.ohhapple.monster_conquest.arena.entity.ArenaCreeper;
import com.ohhapple.monster_conquest.arena.entity.ArenaGiantArmadillo;
import com.ohhapple.monster_conquest.arena.entity.ArenaLingeringPotion;
import com.ohhapple.monster_conquest.arena.entity.ArenaLootDrop;
import com.ohhapple.monster_conquest.arena.entity.ArenaPanda;
import com.ohhapple.monster_conquest.arena.entity.ArenaEnderman;
import com.ohhapple.monster_conquest.arena.entity.ArenaSkeletonHorse;
import com.ohhapple.monster_conquest.arena.entity.ArenaTurtle;
import com.ohhapple.monster_conquest.arena.entity.ArenaTrident;
import com.ohhapple.monster_conquest.arena.entity.ArenaWanderingTrader;
import com.ohhapple.monster_conquest.arena.entity.ArenaWarden;
import com.ohhapple.monster_conquest.arena.entity.ArenaWither;
import com.ohhapple.monster_conquest.arena.entity.ArenaWitherSkull;
import com.ohhapple.monster_conquest.arena.entity.ArenaSnowGolem;
import com.ohhapple.monster_conquest.arena.entity.ArenaSnowball;
import com.ohhapple.monster_conquest.arena.entity.ArenaSpider;
import com.ohhapple.monster_conquest.arena.entity.ArenaWindProjectile;
import com.ohhapple.monster_conquest.arena.entity.ArenaWolf;
import com.ohhapple.monster_conquest.entity.commanders.*;
import com.ohhapple.monster_conquest.entity.commanders.patrol.*;
import com.ohhapple.monster_conquest.entity.projectile.ArmyArrow;
import com.ohhapple.monster_conquest.entity.projectile.ArmyBullet;
import com.ohhapple.monster_conquest.entity.projectile.ArmyRocket;
import com.ohhapple.monster_conquest.entity.projectile.ArmyTrident;
import com.ohhapple.monster_conquest.entity.projectile.WitchPotionEntity;
import com.ohhapple.monster_conquest.entity.soldiers.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Monster_conquest.MODID);

    private static <T extends Entity> DeferredHolder<EntityType<?>, EntityType<T>> reg(
            String name, EntityType.EntityFactory<T> factory, float width, float height, float eye, int track) {
        return ENTITY_TYPES.register(name, () ->
                EntityType.Builder.of(factory, MobCategory.MONSTER)
                        .sized(width, height)
                        .eyeHeight(eye)
                        .clientTrackingRange(track)
                        .build(name));
    }

    // ===================== 各阵营首领 =====================
    public static final DeferredHolder<EntityType<?>, EntityType<UndeadCommander>> UNDEAD_COMMANDER =
            reg("undead_commander", UndeadCommander::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<VillagerCommander>> VILLAGER_COMMANDER =
            reg("villager_commander", VillagerCommander::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<IllagerCommander>> ILLAGER_COMMANDER =
            reg("illager_commander", IllagerCommander::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<NetherCommander>> NETHER_COMMANDER =
            reg("nether_commander", NetherCommander::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<EndCommander>> END_COMMANDER =
            reg("end_commander", EndCommander::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<MonsterCommander>> MONSTER_COMMANDER =
            reg("monster_commander", MonsterCommander::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<NeutralCommander>> NEUTRAL_COMMANDER =
            reg("neutral_commander", NeutralCommander::new, 0.7f, 2.0f, 1.8f, 10);

    // ===================== 各阵营巡逻队长 =====================
    public static final DeferredHolder<EntityType<?>, EntityType<UndeadPatrolCaptain>> UNDEAD_PATROL_CAPTAIN =
            reg("undead_patrol_captain", UndeadPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<VillagerPatrolCaptain>> VILLAGER_PATROL_CAPTAIN =
            reg("villager_patrol_captain", VillagerPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<IllagerPatrolCaptain>> ILLAGER_PATROL_CAPTAIN =
            reg("illager_patrol_captain", IllagerPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<NetherPatrolCaptain>> NETHER_PATROL_CAPTAIN =
            reg("nether_patrol_captain", NetherPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<EndPatrolCaptain>> END_PATROL_CAPTAIN =
            reg("end_patrol_captain", EndPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<MonsterPatrolCaptain>> MONSTER_PATROL_CAPTAIN =
            reg("monster_patrol_captain", MonsterPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<NeutralPatrolCaptain>> NEUTRAL_PATROL_CAPTAIN =
            reg("neutral_patrol_captain", NeutralPatrolCaptain::new, 0.7f, 2.0f, 1.8f, 10);

    // ===================== 士兵 =====================
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieSoldier>> ZOMBIE_SOLDIER =
            reg("soldier_zombie", ZombieSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<HuskSoldier>> HUSK_SOLDIER =
            reg("soldier_husk", HuskSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<DrownedSoldier>> DROWNED_SOLDIER =
            reg("soldier_drowned", DrownedSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ZombieVillagerSoldier>> ZOMBIE_VILLAGER_SOLDIER =
            reg("soldier_zombie_villager", ZombieVillagerSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<SkeletonSoldier>> SKELETON_SOLDIER =
            reg("soldier_skeleton", SkeletonSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<StraySoldier>> STRAY_SOLDIER =
            reg("soldier_stray", StraySoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<BoggedSoldier>> BOGGED_SOLDIER =
            reg("soldier_bogged", BoggedSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<WitherSkeletonSoldier>> WITHER_SKELETON_SOLDIER =
            reg("soldier_wither_skeleton", WitherSkeletonSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<WitchSoldier>> WITCH_SOLDIER =
            reg("soldier_witch", WitchSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<RifleGunnerSoldier>> RIFLE_GUNNER_SOLDIER =
            reg("soldier_gunner_rifle", RifleGunnerSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ShotgunGunnerSoldier>> SHOTGUN_GUNNER_SOLDIER =
            reg("soldier_gunner_shotgun", ShotgunGunnerSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<RocketGunnerSoldier>> ROCKET_GUNNER_SOLDIER =
            reg("soldier_gunner_rocket", RocketGunnerSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<SniperGunnerSoldier>> SNIPER_GUNNER_SOLDIER =
            reg("soldier_gunner_sniper", SniperGunnerSoldier::new, 0.6f, 1.95f, 1.74f, 10);

    // 灾厄阵营新兵种
    public static final DeferredHolder<EntityType<?>, EntityType<VindicatorSoldier>> VINDICATOR_SOLDIER =
            reg("soldier_vindicator", VindicatorSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<EvokerSoldier>> EVOKER_SOLDIER =
            reg("soldier_evoker", EvokerSoldier::new, 0.6f, 1.95f, 1.74f, 10);

    // 村民阵营新兵种
    public static final DeferredHolder<EntityType<?>, EntityType<VillagerMaceSoldier>> VILLAGER_MACE_SOLDIER =
            reg("soldier_mace", VillagerMaceSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<VillagerArcherSoldier>> VILLAGER_ARCHER_SOLDIER =
            reg("soldier_villager_archer", VillagerArcherSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<VillagerPriestSoldier>> VILLAGER_PRIEST_SOLDIER =
            reg("soldier_villager_priest", VillagerPriestSoldier::new, 0.6f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<PiglinShieldSoldier>> PIGLIN_SHIELD_SOLDIER =
            reg("soldier_piglin_shield", PiglinShieldSoldier::new, 0.6f, 1.95f, 1.74f, 10);

    // 飞行单位（当前两种为单一等级；飞行角色本身支持其他等级制兵种）
    public static final DeferredHolder<EntityType<?>, EntityType<VexFlyingSoldier>> VEX_FLYING =
            reg("soldier_vex", VexFlyingSoldier::new, 0.4f, 0.8f, 0.51875f, 8);
    public static final DeferredHolder<EntityType<?>, EntityType<AllayFlyingSoldier>> ALLAY_FLYING =
            reg("soldier_allay", AllayFlyingSoldier::new, 0.35f, 0.6f, 0.36f, 8);

    // 自走棋专属原版生物形态。
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSnowGolem>> ARENA_SNOW_GOLEM =
            reg("arena_snow_golem", ArenaSnowGolem::new,
                    0.7f, 1.9f, 1.7f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSpider>> ARENA_SPIDER =
            reg("arena_spider", ArenaSpider::new,
                    1.4f, 0.9f, 0.65f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWolf>> ARENA_WOLF =
            reg("arena_wolf", ArenaWolf::new,
                    0.6f, 0.85f, 0.68f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaGoat>> ARENA_GOAT =
            reg("arena_goat", ArenaGoat::new,
                    0.9f, 1.3f, 1.1f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaBreeze>> ARENA_BREEZE =
            reg("arena_breeze", ArenaBreeze::new,
                    0.6f, 1.77f, 1.3452f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaRavager>> ARENA_RAVAGER =
            reg("arena_ravager", ArenaRavager::new,
                    1.95f, 2.2f, 2.0f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaPolarBear>> ARENA_POLAR_BEAR =
            reg("arena_polar_bear", ArenaPolarBear::new,
                    1.4f, 1.4f, 1.2f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaHoglin>> ARENA_HOGLIN =
            reg("arena_hoglin", ArenaHoglin::new,
                    1.3964844f, 1.4f, 1.2f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaIronGolem>> ARENA_IRON_GOLEM =
            reg("arena_iron_golem", ArenaIronGolem::new,
                    1.4f, 2.7f, 2.4f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaCaveSpider>> ARENA_CAVE_SPIDER =
            reg("arena_cave_spider", ArenaCaveSpider::new,
                    0.9f, 0.65f, 0.45f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaArmadillo>> ARENA_ARMADILLO =
            reg("arena_armadillo", ArenaArmadillo::new,
                    0.8f, 0.7f, 0.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaBlaze>> ARENA_BLAZE =
            reg("arena_blaze", ArenaBlaze::new,
                    0.6f, 1.8f, 1.6f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSniffer>> ARENA_SNIFFER =
            reg("arena_sniffer", ArenaSniffer::new,
                    1.9f, 1.75f, 1.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaEndermite>> ARENA_ENDERMITE =
            reg("arena_endermite", ArenaEndermite::new,
                    0.8f, 0.6f, 0.45f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSlime>> ARENA_SLIME =
            reg("arena_slime", ArenaSlime::new,
                    2.4f, 2.4f, 1.5f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaElderGuardian>> ARENA_ELDER_GUARDIAN =
            reg("arena_elder_guardian", ArenaElderGuardian::new,
                    2.0f, 2.0f, 1.5f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaRabbit>> ARENA_RABBIT =
            reg("arena_rabbit", ArenaRabbit::new,
                    1.0f, 1.0f, 0.7f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaDonkey>> ARENA_DONKEY =
            reg("arena_donkey", ArenaDonkey::new,
                    1.4f, 1.6f, 1.3f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSheep>> ARENA_SHEEP =
            reg("arena_sheep", ArenaSheep::new,
                    1.0f, 1.3f, 1.05f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaCow>> ARENA_COW =
            reg("arena_cow", ArenaCow::new,
                    1.1f, 1.4f, 1.2f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaPig>> ARENA_PIG =
            reg("arena_pig", ArenaPig::new,
                    0.9f, 0.9f, 0.75f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaZombifiedPiglin>> ARENA_ZOMBIFIED_PIGLIN =
            reg("arena_zombified_piglin", ArenaZombifiedPiglin::new,
                    0.6f, 1.95f, 1.8f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaFrog>> ARENA_FROG =
            reg("arena_frog", ArenaFrog::new,
                    1.2f, 1.0f, 0.75f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaFox>> ARENA_FOX =
            reg("arena_fox", ArenaFox::new,
                    0.8f, 0.8f, 0.65f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaCreeper>> ARENA_CREEPER =
            reg("arena_creeper", ArenaCreeper::new,
                    0.6f, 1.7f, 1.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaPanda>> ARENA_PANDA =
            reg("arena_panda", ArenaPanda::new,
                    1.3f, 1.25f, 1.0f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaEnderman>> ARENA_ENDERMAN =
            reg("arena_enderman", ArenaEnderman::new,
                    0.6f, 2.9f, 2.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWarden>> ARENA_WARDEN =
            reg("arena_warden", ArenaWarden::new,
                    0.9f, 2.9f, 2.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWanderingTrader>> ARENA_WANDERING_TRADER =
            reg("arena_wandering_trader", ArenaWanderingTrader::new,
                    0.7f, 1.95f, 1.74f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaMagmaCube>> ARENA_MAGMA_CUBE =
            reg("arena_magma_cube", ArenaMagmaCube::new,
                    2.4f, 2.4f, 1.5f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaTurtle>> ARENA_TURTLE =
            reg("arena_turtle", ArenaTurtle::new,
                    1.1f, 0.8f, 0.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWither>> ARENA_WITHER =
            reg("arena_wither", ArenaWither::new,
                    1.4f, 3.0f, 2.6f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSkeletonHorse>> ARENA_SKELETON_HORSE =
            reg("arena_skeleton_horse", ArenaSkeletonHorse::new,
                    1.3964844f, 1.6f, 1.52f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaGiantArmadillo>> ARENA_GIANT_ARMADILLO =
            reg("arena_giant_armadillo", ArenaGiantArmadillo::new,
                    0.8f, 0.7f, 0.55f, 10);
    public static final DeferredHolder<EntityType<?>, EntityType<ArenaEnderDragon>> ARENA_ENDER_DRAGON =
            reg("arena_ender_dragon", ArenaEnderDragon::new,
                    2.4f, 2.2f, 1.85f, 10);

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaLootDrop>> ARENA_LOOT_DROP =
            ENTITY_TYPES.register("arena_loot_drop", () ->
                    EntityType.Builder.<ArenaLootDrop>of(
                                    ArenaLootDrop::new, MobCategory.MISC)
                            .sized(0.6f, 0.6f)
                            .clientTrackingRange(8)
                            .updateInterval(2)
                            .build("arena_loot_drop"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaLingeringPotion>> ARENA_LINGERING_POTION =
            ENTITY_TYPES.register("arena_lingering_potion", () ->
                    EntityType.Builder.<ArenaLingeringPotion>of(
                                    ArenaLingeringPotion::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("arena_lingering_potion"));

    // ===================== 战马 / 三叉戟 / 药水 =====================
    public static final DeferredHolder<EntityType<?>, EntityType<WarHorseEntity>> WAR_HORSE =
            ENTITY_TYPES.register("war_horse", () ->
                    EntityType.Builder.<WarHorseEntity>of(WarHorseEntity::new, MobCategory.CREATURE)
                            .sized(1.3964844f, 1.6f)
                            .eyeHeight(1.52f)
                            .passengerAttachments(1.44375f)
                            .clientTrackingRange(10)
                            .build("war_horse"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArmyTrident>> ARMY_TRIDENT =
            ENTITY_TYPES.register("army_trident", () ->
                    EntityType.Builder.<ArmyTrident>of(ArmyTrident::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(6)
                            .build("army_trident"));

    public static final DeferredHolder<EntityType<?>, EntityType<WitchPotionEntity>> WITCH_POTION =
            ENTITY_TYPES.register("witch_potion", () ->
                    EntityType.Builder.<WitchPotionEntity>of(WitchPotionEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(6)
                            .build("witch_potion"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaSnowball>> ARENA_SNOWBALL =
            ENTITY_TYPES.register("arena_snowball", () ->
                    EntityType.Builder.<ArenaSnowball>of(
                                    ArenaSnowball::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(8)
                            .build("arena_snowball"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWindProjectile>> ARENA_WIND_CHARGE =
            ENTITY_TYPES.register("arena_wind_charge", () ->
                    EntityType.Builder.<ArenaWindProjectile>of(
                                    ArenaWindProjectile::new, MobCategory.MISC)
                            .sized(0.3125f, 0.3125f)
                            .eyeHeight(0.0f)
                            .clientTrackingRange(8)
                            .updateInterval(5)
                            .build("arena_wind_charge"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWindProjectile>> ARENA_GALE_CHARGE =
            ENTITY_TYPES.register("arena_gale_charge", () ->
                    EntityType.Builder.<ArenaWindProjectile>of(
                                    ArenaWindProjectile::new, MobCategory.MISC)
                            .sized(1.2f, 1.2f)
                            .eyeHeight(0.0f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("arena_gale_charge"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaFireball>> ARENA_FIREBALL =
            ENTITY_TYPES.register("arena_fireball", () ->
                    EntityType.Builder.<ArenaFireball>of(
                                    ArenaFireball::new, MobCategory.MISC)
                            .sized(0.3125f, 0.3125f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("arena_fireball"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaWitherSkull>> ARENA_WITHER_SKULL =
            ENTITY_TYPES.register("arena_wither_skull", () ->
                    EntityType.Builder.<ArenaWitherSkull>of(
                                    ArenaWitherSkull::new, MobCategory.MISC)
                            .sized(0.45f, 0.45f)
                            .clientTrackingRange(10)
                            .updateInterval(2)
                            .build("arena_wither_skull"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArenaTrident>> ARENA_SKILL_TRIDENT =
            ENTITY_TYPES.register("arena_skill_trident", () ->
                    EntityType.Builder.<ArenaTrident>of(
                                    ArenaTrident::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(10)
                            .updateInterval(1)
                            .build("arena_skill_trident"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArmyBullet>> ARMY_BULLET =
            ENTITY_TYPES.register("army_bullet", () ->
                    EntityType.Builder.<ArmyBullet>of(ArmyBullet::new, MobCategory.MISC)
                            .sized(0.2f, 0.2f)
                            .clientTrackingRange(10)
                            // 位置同步降频 + 不发送速度包，客户端按同一套物理模拟，避免弹道抖动
                            .setUpdateInterval(20)
                            .setShouldReceiveVelocityUpdates(false)
                            .build("army_bullet"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArmyRocket>> ARMY_ROCKET =
            ENTITY_TYPES.register("army_rocket", () ->
                    EntityType.Builder.<ArmyRocket>of(ArmyRocket::new, MobCategory.MISC)
                            .sized(0.3f, 0.3f)
                            .clientTrackingRange(10)
                            .setUpdateInterval(20)
                            .setShouldReceiveVelocityUpdates(false)
                            .build("army_rocket"));

    public static final DeferredHolder<EntityType<?>, EntityType<ArmyArrow>> ARMY_ARROW =
            ENTITY_TYPES.register("army_arrow", () ->
                    EntityType.Builder.<ArmyArrow>of(ArmyArrow::new, MobCategory.MISC)
                            .sized(0.5f, 0.5f)
                            .clientTrackingRange(6)
                            .build("army_arrow"));

    // ===================== 袭击锚点（袭击增援计入原版袭击系统的载体，客户端不渲染） =====================
    public static final DeferredHolder<EntityType<?>, EntityType<RaidAnchorEntity>> RAID_ANCHOR =
            reg("raid_anchor", RaidAnchorEntity::new, 0.6f, 1.95f, 1.74f, 4);
}
