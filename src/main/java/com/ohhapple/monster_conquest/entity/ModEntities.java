package com.ohhapple.monster_conquest.entity;

import com.ohhapple.monster_conquest.Monster_conquest;
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
