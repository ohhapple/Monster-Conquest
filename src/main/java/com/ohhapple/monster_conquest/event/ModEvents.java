package com.ohhapple.monster_conquest.event;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.CombatRules;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
import com.ohhapple.monster_conquest.army.threat.ArmyThreatUtil;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.effect.GrievousWoundEffect;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.entity.soldiers.EvokerSoldier;
import com.ohhapple.monster_conquest.item.WarmClothesItem;
import com.ohhapple.monster_conquest.network.clientbound.sync.SyncSquadPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

@EventBusSubscriber(modid = Monster_conquest.MODID)
public class ModEvents {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        // 首领
        event.put(ModEntities.UNDEAD_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.VILLAGER_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.ILLAGER_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.NETHER_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.END_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.MONSTER_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.NEUTRAL_COMMANDER.get(), AbstractArmyCommander.createAttributes().build());

        // 巡逻队长
        event.put(ModEntities.UNDEAD_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.VILLAGER_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.ILLAGER_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.NETHER_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.END_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.MONSTER_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());
        event.put(ModEntities.NEUTRAL_PATROL_CAPTAIN.get(), AbstractArmyCommander.createAttributes().build());

        // 士兵
        event.put(ModEntities.ZOMBIE_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.HUSK_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.DROWNED_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.ZOMBIE_VILLAGER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.SKELETON_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.STRAY_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.BOGGED_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.WITHER_SKELETON_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.WITCH_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.RIFLE_GUNNER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.SHOTGUN_GUNNER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.ROCKET_GUNNER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.SNIPER_GUNNER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());

        // 新兵种
        event.put(ModEntities.VINDICATOR_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.EVOKER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.VILLAGER_MACE_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.VILLAGER_ARCHER_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());
        event.put(ModEntities.VILLAGER_PRIEST_SOLDIER.get(), AbstractArmySoldier.createAttributes().build());

        event.put(ModEntities.WAR_HORSE.get(), WarHorseEntity.createAttributes().build());

        // 袭击锚点：与原版掠夺者一致的属性（本身不参战，仅作为袭击载体）
        event.put(ModEntities.RAID_ANCHOR.get(), net.minecraft.world.entity.monster.Pillager.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacement(RegisterSpawnPlacementsEvent event) {
        var op = RegisterSpawnPlacementsEvent.Operation.REPLACE;

        event.register(ModEntities.UNDEAD_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.VILLAGER_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.ILLAGER_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.NETHER_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.END_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.MONSTER_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.NEUTRAL_COMMANDER.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);

        event.register(ModEntities.UNDEAD_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.VILLAGER_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.ILLAGER_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.NETHER_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.END_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.MONSTER_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
        event.register(ModEntities.NEUTRAL_PATROL_CAPTAIN.get(),
                SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ModEvents::checkArmySpawnRules, op);
    }

    /**
     * 单位（士兵 / 首领）的刷怪规则：与原版怪物完全一致（和平难度不刷、按亮度），
     *   只是单位本身不再继承 Monster 类（避免被其它模组的傀儡等按"敌对生物"默认锁定）。
     */
    private static <T extends Mob> boolean checkArmySpawnRules(
            EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason,
            BlockPos pos, RandomSource random) {
        return level.getDifficulty() != Difficulty.PEACEFUL
                && (MobSpawnType.ignoresLightRequirements(reason)
                    || isDarkEnoughToSpawn(level, pos, random))
                && Mob.checkMobSpawnRules(type, level, reason, pos, random);
    }

    /** 与原版 Monster#isDarkEnoughToSpawn 相同的光照判定（单位不再是 Monster，这里自备一份）。 */
    private static boolean isDarkEnoughToSpawn(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        if (level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32)) return false;
        int blockLightLimit = level.dimensionType().monsterSpawnBlockLightLimit();
        if (blockLightLimit < 15 && level.getBrightness(LightLayer.BLOCK, pos) > blockLightLimit) {
            return false;
        }
        int light = level.getLevel().isThundering()
                ? level.getMaxLocalRawBrightness(pos, 10)
                : level.getMaxLocalRawBrightness(pos);
        return light <= level.dimensionType().monsterSpawnLightTest().sample(random);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ArmyRelations.setServer(event.getServer());
        FactionHostility.setServer(event.getServer());
        FactionAlly.setServer(event.getServer());
        FactionAlly.ensureDefaults();
        FactionHostility.ensureDefaults();

        com.ohhapple.monster_conquest.player.PlayerArmyManager.setServer(event.getServer());
        com.ohhapple.monster_conquest.player.PlayerArmyManager.loadFromDisk();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        com.ohhapple.monster_conquest.player.PlayerArmyManager.saveToDisk();
        com.ohhapple.monster_conquest.player.PlayerArmyManager.setServer(null);

        ArmyRelations.setServer(null);
        FactionHostility.setServer(null);
        FactionAlly.setServer(null);

        // 清空停战窗口：ThreatDispatcher.truceUntilGameTime 是静态字段，
        //   如果不清理，退出世界再开新世界（同一进程）时会残留，
        //   导致新世界头几秒所有攻击被忽略。
        ThreatDispatcher.clearTruce();
    }

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        FactionHostility.ensurePlayerDefaults(p.getUUID());
        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .get(p.getUUID());

        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                p,
                new SyncSquadPayload(state.getSelectedSquad()));
    }

    @SubscribeEvent
    public static void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        com.ohhapple.monster_conquest.equipment.TacticalMending.clear(p);
        com.ohhapple.monster_conquest.player.PlayerArmyManager.saveOne(p.getUUID());
    }

    /**
     * 拾取经验球：战术装备栏里的物品（防弹衣等）也吃经验修补。
     *   这里只打个标记 —— 真正动手是在下面 XpChange（原版把身上的装备修完之后）。
     */
    @SubscribeEvent
    public static void onPickupXp(net.neoforged.neoforge.event.entity.player.PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        com.ohhapple.monster_conquest.equipment.TacticalMending.noteOrbPickup(player);
    }

    /**
     * 经验到手前：用这次的经验补修战术装备栏里带"经验修补"的损伤物品。
     *   此时原版（身上的装备）已经先修完，这里只花剩下的经验；没有可修的东西时
     *   经验原样给玩家，原版行为不受影响。
     */
    @SubscribeEvent
    public static void onXpChange(net.neoforged.neoforge.event.entity.player.PlayerXpEvent.XpChange event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!com.ohhapple.monster_conquest.equipment.TacticalMending.consumeOrbPickup(player)) return;
        int amount = event.getAmount();
        if (amount <= 0) return;
        int used = com.ohhapple.monster_conquest.equipment.TacticalMending.repair(player, amount);
        if (used > 0) event.setAmount(amount - used);
    }

    @SubscribeEvent
    public static void onServerTickPost(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // 每 tick 处理延迟的阵营结盟清理（队列空时几乎无开销）
        FactionAlly.processPendingCleanups();

        if (event.getServer().getTickCount() % 200 == 0) {
            com.ohhapple.monster_conquest.player.PlayerArmyManager.saveToDisk();
        }

        // 每 100 tick 清理空隐藏小队
        if (event.getServer().getTickCount() % 100 == 0) {
            for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(sp.getUUID());
                if (st != null) st.cleanupEmptyHiddenSquads(sp);
            }
        }

        // 每 20 tick 清理过期的移动标记
        if (event.getServer().getTickCount() % 20 == 0) {
            long nowGT = event.getServer().overworld().getGameTime();
            for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(sp.getUUID());
                // 标记过期时补一次同步，客户端的地面黄框才会跟着消失
                if (st != null && st.tickRtsMarker(nowGT)) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,
                            new com.ohhapple.monster_conquest.network.clientbound.sync
                                    .SyncRtsSelectionPayload(
                                    st.collectRtsSelectedSoldierUuids(sp),
                                    st.getRtsMoveMarker()));
                }
            }

            // 和平模式：持续清除游戏内建立的仇恨（反击 / 控制工具 / 仇恨工具 / RTS 命令）
            if (event.getServer().getWorldData().getDifficulty()
                    == net.minecraft.world.Difficulty.PEACEFUL) {
                com.ohhapple.monster_conquest.item.HatredWandItem
                        .clearRuntimeHatred(event.getServer().overworld());
            }
        }

        // 世界生成：村庄巡逻队（村民阵营）自然刷出
        com.ohhapple.monster_conquest.world.VillagePatrolSpawner.tick(event.getServer());
        // 世界生成：袭击增援（灾厄阵营）
        com.ohhapple.monster_conquest.world.RaidSpawner.tick(event.getServer());
        // 世界生成：野外巡逻队（各阵营，按维度与配置开关）
        com.ohhapple.monster_conquest.world.NaturalPatrolSpawner.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;

        var src = event.getSource();
        LivingEntity attacker = CombatRules.damageAttacker(src);

        // 防弹衣：被子弹 / 炮弹 / 其它弹射物击中时伤害 -70%，按减免量扣防弹衣耐久
        if (victim instanceof net.minecraft.server.level.ServerPlayer vestPlayer
                && (src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                    || src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))) {
            float before = event.getAmount();
            float after = com.ohhapple.monster_conquest.item.BulletproofVestItem
                    .applyProjectileReduction(vestPlayer, before);
            if (after < before) event.setAmount(after);
        }
        // 单位同理（士兵 / 首领）：战术装备栏里放了防弹衣就一起生效
        if (victim instanceof com.ohhapple.monster_conquest.entity.ManagedUnit vestUnit
                && (src.getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile
                    || src.is(net.minecraft.tags.DamageTypeTags.IS_PROJECTILE))) {
            float before = event.getAmount();
            float after = com.ohhapple.monster_conquest.item.BulletproofVestItem
                    .applyProjectileReduction(vestUnit, before);
            if (after < before) event.setAmount(after);
        }

        if (attacker == null || attacker == victim) return;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不伤害。
        if (NativePeace.isPeacefulPair(victim, attacker)) {
            event.setCanceled(true);
            return;
        }

        // 村庄巡逻队保护村民（像铁傀儡一样）：村民被打 → 同村庄的巡逻队立刻锁定攻击者
        if (victim instanceof net.minecraft.world.entity.npc.Villager villagerVictim) {
            com.ohhapple.monster_conquest.world.VillageDefense
                    .onVillagerAttacked(villagerVictim, attacker);
        }

        // 唤魔者尖牙不会误伤友军（同军队 / 同阵营 / 结盟，含战马）。
        if (src.getDirectEntity() instanceof EvokerFangs
                && CombatRules.isFriendly(attacker, victim)) {
            event.setCanceled(true);
            return;
        }

        // 仇恨传播：
        //   · 玩家被打 → 玩家共享仇恨
        //   · 军团单位（士兵 / 首领，含其召唤的尖牙）打人 → 受害者仇恨该单位，
        //     整个军团与玩家共享仇恨也随之联动（唤魔者尖牙的仇恨归属即此）。
        boolean armyAttacker = attacker instanceof AbstractArmySoldier
                || attacker instanceof AbstractArmyCommander;
        if (armyAttacker || victim instanceof Player) {
            ThreatDispatcher.propagate(victim, attacker);
        }
    }

    /**
     * 唤魔者士兵的尖牙命中 → 给受害者施加重伤（等级 = 士兵装备等级）。
     *   用 Post 事件确保"真的造成了伤害"才上 buff。
     */
    @SubscribeEvent
    public static void onLivingDamagePost(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0f) return;
        if (!(event.getSource().getDirectEntity() instanceof EvokerFangs)) return;
        if (event.getSource().getEntity() instanceof EvokerSoldier evoker) {
            evoker.applyGrievousWound(event.getEntity());
        }
    }

    /**
     * 重伤（禁疗）：削减目标受到的一切治疗。
     *   覆盖直接回血、生命恢复 buff、瞬间治疗等所有走 heal() 的途径。
     */
    @SubscribeEvent
    public static void onLivingHeal(LivingHealEvent event) {
        MobEffectInstance wound = event.getEntity().getEffect(ModEffects.GRIEVOUS_WOUND);
        if (wound == null) return;
        float amount = event.getAmount();
        if (amount <= 0f) return;

        float multiplier = GrievousWoundEffect.healingMultiplier(wound.getAmplifier());
        if (multiplier <= 0f) {
            event.setCanceled(true);
            return;
        }
        event.setAmount(amount * multiplier);
    }

    /**
     * 同族和平：阻止原版生物把对应阵营单位设为攻击目标。
     *   （例：铁傀儡不再仇视村民阵营士兵；原版灾厄生物不再仇视灾厄阵营士兵。）
     *
     * 铁傀儡：只允许锁定"仇视铁傀儡的单位" —— 判定读配置里该单位阵营的"仇视生物类型"
     *   名单（{@code factionEnemies.*}，例如 factionEnemies.undead 里就写着 minecraft:iron_golem），
     *   看里面有没有 {@code minecraft:iron_golem}。
     *   另外，已经和它结仇（打过它 / 正在打它）的单位永远允许被还击。
     */
    @SubscribeEvent
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget == null) return;
        if (NativePeace.isPeacefulPair(event.getEntity(), newTarget)) {
            event.setCanceled(true);
            return;
        }
        if (event.getEntity() instanceof IronGolem golem
                && isModUnit(newTarget)
                && !unitHatesIronGolems(newTarget)
                && !golemHasGrudge(golem, newTarget)) {
            event.setCanceled(true);
        }
    }

    /**
     * 该单位是否"仇视铁傀儡"：读配置里该单位阵营的"仇视生物类型"名单 ——
     *   {@code factionEnemies.<阵营>}（玩家阵营对应 {@code playerFactionEnemies}），
     *   名单里写了 {@code minecraft:iron_golem} 才算仇视铁傀儡。
     *   默认配置下亡灵 / 灾厄这类阵营写了铁傀儡 → 它们会被铁傀儡默认攻击；
     *   玩家阵营的名单只有原版怪物（没有铁傀儡）→ 玩家军队不会无端挨打。
     *   与 {@code AttackConfiguredEnemyGoal} 判断"单位该不该打某个原版生物"用的是同一张表。
     */
    private static boolean unitHatesIronGolems(LivingEntity unit) {
        ResourceLocation fac = ArmyThreatUtil.factionOf(unit);
        if (fac == null) return false;
        String ironGolemKey = EntityType.getKey(EntityType.IRON_GOLEM).toString();
        java.util.Set<String> enemies;
        if (FactionIds.isPlayer(fac)) {
            enemies = com.ohhapple.monster_conquest.config.FactionConfig.playerFactionEnemies;
        } else {
            var faction = com.ohhapple.monster_conquest.army.FactionRegistry.get(fac);
            enemies = faction == null ? null : faction.getDefaultHostileEntityTypes();
        }
        if (enemies != null && enemies.contains(ironGolemKey)) return true;
        // 全局"额外仇视生物"名单（配置 defaultEnemies，默认空）
        java.util.Set<String> global =
                com.ohhapple.monster_conquest.config.FactionConfig.defaultEnemies;
        return global != null && global.contains(ironGolemKey);
    }

    /** 是不是模组单位（士兵 / 首领 / 战马）。 */
    private static boolean isModUnit(LivingEntity target) {
        return target instanceof AbstractArmySoldier
                || target instanceof AbstractArmyCommander
                || target instanceof WarHorseEntity;
    }

    /**
     * 这只铁傀儡和该目标是否"确实结仇"：
     *   · 目标（战马 → 骑手 / 主人）打过它、正在打它；或
     *   · 它已经在和这个目标交战（不打断进行中的锁定）。
     *   只有结仇的目标才允许被铁傀儡锁定 —— 其余模组单位默认中立。
     */
    private static boolean golemHasGrudge(
            net.minecraft.world.entity.animal.IronGolem golem, LivingEntity target) {
        if (golem.getTarget() == target) return true;
        LivingEntity unit = target;
        if (target instanceof WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            if (rider instanceof LivingEntity le) {
                unit = le;
            } else {
                java.util.UUID owner = horse.getOwnerUuid();
                if (owner != null && horse.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    Entity e = sl.getEntities().get(owner);
                    if (e instanceof LivingEntity le) unit = le;
                }
            }
        }
        for (LivingEntity u : new LivingEntity[]{target, unit}) {
            if (u == null) continue;
            if (golem.getLastHurtByMob() == u) return true;
            if (u.getLastHurtMob() == golem) return true;
            if (u instanceof net.minecraft.world.entity.Mob m && m.getTarget() == golem) return true;
        }
        return false;
    }

    /**
     * 铁傀儡主动索敌（每 20 tick 一次）：只锁定"仇视铁傀儡"的模组单位 ——
     *   判定读配置里该单位阵营的"仇视生物类型"名单（factionEnemies.* 里有没有 iron_golem）。
     *   单位不仇视铁傀儡 → 铁傀儡不理它。
     */
    private static void tickIronGolemAggro(IronGolem golem) {
        if (golem.tickCount % 20 != 0) return;
        LivingEntity cur = golem.getTarget();
        if (cur != null && cur.isAlive()) return;
        if (!(golem.level() instanceof ServerLevel sl)) return;

        net.minecraft.world.phys.AABB box = golem.getBoundingBox().inflate(16.0);
        LivingEntity best = null;
        double bestSq = Double.MAX_VALUE;
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive() || !unitHatesIronGolems(s)) continue;
            double d = golem.distanceToSqr(s);
            if (d < bestSq) {
                bestSq = d;
                best = s;
            }
        }
        for (AbstractArmyCommander c : sl.getEntitiesOfClass(AbstractArmyCommander.class, box)) {
            if (!c.isAlive() || !unitHatesIronGolems(c)) continue;
            double d = golem.distanceToSqr(c);
            if (d < bestSq) {
                bestSq = d;
                best = c;
            }
        }
        if (best != null && golem.canAttack(best)) {
            golem.setTarget(best);
        }
    }

    /**
     * 骑兵不被击退：马背上的单位被击退会贴着马滑动 / 抖动，还会把阵型挤散。
     *   马自身挨打不结算击退（伤害转嫁给骑手），所以把骑手这一侧的击退取消即可。
     */
    @SubscribeEvent
    public static void onLivingKnockBack(LivingKnockBackEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.isPassenger()) return;
        if (entity instanceof AbstractArmySoldier || entity instanceof AbstractArmyCommander) {
            event.setCanceled(true);
        }
    }

    /**
     * 实体 tick 前（仅服务端）：
     *   1. 装备保暖衣的玩家每 tick 清空冻结 tick（完全免疫冻结）；
     *   2. 对免疫冻结但已完全冻结的目标补上"每 40 tick 1 点"的冻结伤害。
     */
    @SubscribeEvent
    public static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (living.level().isClientSide) return;
        // 铁傀儡：只主动打"仇视铁傀儡"的单位
        if (living instanceof IronGolem golem) {
            tickIronGolemAggro(golem);
        }
        if (living.getTicksFrozen() <= 0) return;

        // 1. 保暖衣：完全免疫冻结（玩家与单位都算；骑手穿了，坐骑也一起免疫）
        if (WarmClothesItem.isFrostImmune(living)) {
            living.setTicksFrozen(0);
            return;
        }

        // 2. 原版免疫冻结的目标：补上相同的冻结伤害（完全冻结 + 每 40 tick 1 点）
        if (living.tickCount % 40 == 0 && living.isFullyFrozen() && !living.canFreeze()) {
            living.hurt(living.damageSources().freeze(), 1.0F);
        }
    }
}
