package com.ohhapple.monster_conquest.block;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.item.CommanderSpawnEggItem;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.item.SoldierSpawnEggItem;
import com.ohhapple.monster_conquest.path.PathRoutes;
import com.ohhapple.monster_conquest.world.ArmySpawnUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.registries.DeferredHolder;

import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;
import java.util.UUID;

/**
 * 刷怪点的服务端逻辑：
 *   · 界面请求：校验距离与取值，写进方块实体；
 *   · 刷怪：按间隔在生成面旁刷出配置的单位（或模板刷怪蛋对应的单位），
 *     并维护「已生成计数」与「同时存在数量」两道上限。
 * 单位不绑锚点、也不会远离玩家后消失：刷出来就是常驻单位，按自身阵营关系索敌；
 *   蛋里带玩家阵营时沿用蛋原本的归属（指挥官 = 该玩家）。
 */
public final class SpawnPointService {

    private SpawnPointService() {}

    /**
     * 首领 / 巡逻队长（没有装备等级）。
     * 下标会被写进存档的方块实体，调整顺序会让老存档的配置漂移，改动时注意。
     */
    public static final List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> COMMANDERS = List.of(
            ModEntities.UNDEAD_COMMANDER,
            ModEntities.VILLAGER_COMMANDER,
            ModEntities.ILLAGER_COMMANDER,
            ModEntities.NETHER_COMMANDER,
            ModEntities.END_COMMANDER,
            ModEntities.MONSTER_COMMANDER,
            ModEntities.NEUTRAL_COMMANDER,
            // 巡逻队长
            ModEntities.UNDEAD_PATROL_CAPTAIN,
            ModEntities.VILLAGER_PATROL_CAPTAIN,
            ModEntities.ILLAGER_PATROL_CAPTAIN,
            ModEntities.NETHER_PATROL_CAPTAIN,
            ModEntities.END_PATROL_CAPTAIN,
            ModEntities.MONSTER_PATROL_CAPTAIN,
            ModEntities.NEUTRAL_PATROL_CAPTAIN);

    /** 士兵（带 T0~T4 装备等级）。 */
    public static final List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> SOLDIERS = List.of(
            ModEntities.ZOMBIE_SOLDIER,
            ModEntities.HUSK_SOLDIER,
            ModEntities.DROWNED_SOLDIER,
            ModEntities.ZOMBIE_VILLAGER_SOLDIER,
            ModEntities.SKELETON_SOLDIER,
            ModEntities.STRAY_SOLDIER,
            ModEntities.BOGGED_SOLDIER,
            ModEntities.WITHER_SKELETON_SOLDIER,
            ModEntities.WITCH_SOLDIER,
            ModEntities.RIFLE_GUNNER_SOLDIER,
            ModEntities.SHOTGUN_GUNNER_SOLDIER,
            ModEntities.ROCKET_GUNNER_SOLDIER,
            ModEntities.SNIPER_GUNNER_SOLDIER,
            ModEntities.VINDICATOR_SOLDIER,
            ModEntities.EVOKER_SOLDIER,
            ModEntities.VILLAGER_MACE_SOLDIER,
            ModEntities.VILLAGER_ARCHER_SOLDIER,
            ModEntities.VILLAGER_PRIEST_SOLDIER,
            ModEntities.PIGLIN_SHIELD_SOLDIER,
            ModEntities.VEX_FLYING,
            ModEntities.ALLAY_FLYING);

    /** 界面列表顺序 = 首领 / 巡逻队长 + 士兵。 */
    public static final List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> UNITS =
            Stream.concat(COMMANDERS.stream(), SOLDIERS.stream()).toList();

    /** 可选阵营清单（顺序 = 界面循环顺序）。 */
    public static final List<ResourceLocation> FACTIONS = List.of(
            FactionIds.UNDEAD,
            FactionIds.VILLAGER,
            FactionIds.ILLAGER,
            FactionIds.NETHER,
            FactionIds.END,
            FactionIds.MONSTER,
            FactionIds.NEUTRAL);

    // ===================== 界面请求 =====================

    public static final int ACTION_UNIT = 0;
    public static final int ACTION_FACTION = 1;
    public static final int ACTION_MOUNTED = 2;
    public static final int ACTION_TIER = 3;
    public static final int ACTION_BATCH = 4;
    public static final int ACTION_INTERVAL = 5;
    public static final int ACTION_MAX_TOTAL = 6;
    public static final int ACTION_MAX_CONCURRENT = 7;
    public static final int ACTION_RESET_COUNT = 8;
    public static final int ACTION_ENABLED = 9;
    /** 清空这个刷怪点设置的巡逻路径（路径由路径点工具设置，只存在世界里）。 */
    public static final int ACTION_CLEAR_PATH = 10;

    /**
     * 处理界面的一次配置请求（服务端重新校验取值）。
     *   写了模板蛋时不允许手动选单位：模板蛋优先，取出后才能在界面上重新手选。
     */
    public static void applyAction(SpawnPointBlockEntity spawner, int action, int value) {
        switch (action) {
            case ACTION_UNIT -> {
                if (spawner.getEgg().isEmpty() && unitAt(value) != null) spawner.setUnitIndex(value);
            }
            case ACTION_FACTION -> {
                if (value >= 0 && value < FACTIONS.size()) spawner.setFaction(FACTIONS.get(value));
            }
            case ACTION_MOUNTED -> spawner.setMounted(value != 0);
            case ACTION_TIER -> {
                if (tierAt(value) != null) spawner.setTier(value);
            }
            case ACTION_BATCH -> spawner.setBatch(value);
            case ACTION_INTERVAL -> spawner.setIntervalSeconds(value);
            case ACTION_MAX_TOTAL -> spawner.setMaxTotal(value);
            case ACTION_MAX_CONCURRENT -> spawner.setMaxConcurrent(value);
            case ACTION_RESET_COUNT -> spawner.resetSpawnedTotal();
            case ACTION_ENABLED -> spawner.setEnabled(value != 0);
            default -> { }
        }
    }

    // ===================== 查询 =====================

    /** 下拉清单里的单位；下标越界返回 null。 */
    @Nullable
    public static DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> unitAt(int index) {
        return index >= 0 && index < UNITS.size() ? UNITS.get(index) : null;
    }

    /** 该下标是不是首领 / 巡逻队长（界面上没有等级可选）。 */
    public static boolean isCommanderIndex(int index) {
        return index >= 0 && index < COMMANDERS.size();
    }

    /**
     * 首领 / 巡逻队长自带的固定阵营（界面只显示、不允许切换）；士兵返回 null。
     *   清单顺序 = 每阵营先首领后巡逻队长、阵营循环排列，与它们的默认阵营一一对应。
     */
    @Nullable
    public static ResourceLocation fixedFactionOf(int index) {
        if (!isCommanderIndex(index)) return null;
        return FACTIONS.get(index % FACTIONS.size());
    }

    /** 单位对应的等级档；首领没有装备等级，返回 null（界面上不显示等级）。 */
    @Nullable
    public static EquipmentTier tierAt(int index) {
        EquipmentTier[] all = EquipmentTier.values();
        return index >= 0 && index < all.length ? all[index] : null;
    }

    /** 恼鬼 / 悦灵固定单一等级，不提供 T0~T4 与骑乘切换。 */
    public static boolean isFixedTierIndex(int index) {
        DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> unit = unitAt(index);
        return unit == ModEntities.VEX_FLYING || unit == ModEntities.ALLAY_FLYING;
    }

    public static boolean isFlyingIndex(int index) {
        DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> unit = unitAt(index);
        return unit == ModEntities.VEX_FLYING || unit == ModEntities.ALLAY_FLYING;
    }

    /** 是不是可放进刷怪点当模板的单位刷怪蛋（本模组的士兵 / 首领蛋）。 */
    public static boolean isUnitEgg(ItemStack stack) {
        return stack.getItem() instanceof SoldierSpawnEggItem
                || stack.getItem() instanceof CommanderSpawnEggItem;
    }

    // ===================== 刷怪 =====================

    /**
     * 一个刷怪周期：先清点仍然存活的本刷怪点单位，再按两道数量上限决定这一轮刷几个。
     *   到顶就这一轮不刷 —— 下一个间隔会重新判定，数量降下来自然恢复，不需要玩家干预。
     */
    public static void trySpawn(ServerLevel level, BlockPos pos, BlockState state, SpawnPointBlockEntity spawner) {
        if (!spawner.isEnabled() || SpawnPointGlobal.isDisabled()) return;
        // 界面开着时暂停：等玩家把配置改完、关掉界面再开始刷
        if (spawner.isBeingEdited()) return;
        // 还没配置过（没选单位也没放模板蛋）/ 单次刷出 0 / 间隔无限：什么都不刷
        if (!spawner.hasSpawnSource() || !spawner.hasSpawnTiming()) return;

        int alive = countAlive(level, spawner);
        spawner.setAliveCount(alive);

        int budget = Integer.MAX_VALUE;
        if (spawner.getMaxTotal() != SpawnPointBlockEntity.UNLIMITED) {
            budget = spawner.getMaxTotal() - spawner.getSpawnedTotal();
        }
        if (budget <= 0) return;
        if (spawner.getMaxConcurrent() != SpawnPointBlockEntity.UNLIMITED) {
            budget = Math.min(budget, spawner.getMaxConcurrent() - alive);
        }
        if (budget <= 0) return;

        int wanted = Math.min(spawner.getBatch(), budget);
        Direction facing = state.getValue(SpawnPointBlock.FACING);
        float yaw = facing.toYRot();

        int spawned = 0;
        for (int i = 0; i < wanted; i++) {
            Mob unit = spawnOne(level, pos, facing, yaw, spawner);
            if (unit == null) break;
            spawner.getTracked().add(unit.getUUID());
            spawned++;
        }
        if (spawned > 0) {
            spawner.addSpawned(spawned);
            spawner.setAliveCount(countAlive(level, spawner));
        }
    }

    /**
     * 清点本刷怪点刷出、当前仍存活的单位，顺手把死亡 / 消失的从名单里去掉。
     *   只统计已加载的单位：单位跑进未加载区块后不再占用「同时存在」名额。
     */
    public static int countAlive(ServerLevel level, SpawnPointBlockEntity spawner) {
        PathRoutes routes = PathRoutes.get(level);
        int alive = 0;
        Iterator<UUID> it = spawner.getTracked().iterator();
        while (it.hasNext()) {
            UUID id = it.next();
            Entity entity = level.getEntities().get(id);
            if (entity == null) {
                it.remove();
                continue;
            }
            if (entity.isRemoved() || !entity.isAlive()) {
                routes.unbindSpawnerUnit(id);
                it.remove();
                continue;
            }
            routes.bindSpawnerUnit(id, spawner.getBlockPos());
            alive++;
        }
        return alive;
    }

    /** 刷出一个配置中的单位；创建失败 / 放不下时返回 null。 */
    @Nullable
    private static Mob spawnOne(ServerLevel level, BlockPos pos, Direction facing,
                                float yaw, SpawnPointBlockEntity spawner) {
        ItemStack egg = spawner.getEgg();
        CompoundTag eggTag = egg.isEmpty()
                ? new CompoundTag()
                : egg.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        boolean mounted;
        Mob unit;
        if (!egg.isEmpty()) {
            // 蛋的形态只看蛋自己（与直接放这颗蛋一致）：蛋里没写就是步兵
            mounted = eggTag.getBoolean(RetrievalToolItem.KEY_CAVALRY);
            unit = createFromEgg(level, spawner, egg, eggTag);
        } else {
            mounted = spawner.isMounted();
            unit = createFromConfig(level, spawner);
        }
        if (unit == null) return null;

        Vec3 spot = findSpawnSpot(level, pos, facing, unit);
        if (spot == null) {
            unit.discard();
            return null;
        }
        unit.moveTo(spot.x, spot.y, spot.z, yaw, 0.0f);
        level.addFreshEntity(unit);
        applyMount(unit, mounted);

        // 刷怪点路线由源路线按版本动态同步到每个单位的独立副本。
        var routes = PathRoutes.get(level);
        routes.bindSpawnerUnit(unit.getUUID(), pos);
        routes.refreshSpawnerRoute(unit);
        return unit;
    }

    // ===================== 按界面配置生成 =====================

    @Nullable
    private static Mob createFromConfig(ServerLevel level, SpawnPointBlockEntity spawner) {
        DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> holder = unitAt(spawner.getUnitIndex());
        if (holder == null) return null;
        if (!(holder.get().create(level) instanceof Mob unit)) return null;

        if (unit instanceof AbstractArmySoldier soldier) {
            SoldierType base = soldier.getDefaultSoldierType();
            soldier.setSoldierType(spawner.isMounted() ? base.toggleMounted() : base);
            EquipmentTier tier = soldier.hasFixedTier()
                    ? EquipmentTier.TIER_0 : tierAt(spawner.getTier());
            if (tier == null) tier = EquipmentTier.TIER_0;
            soldier.setTier(tier);
            soldier.applyEquipment(tier);
            soldier.setHealth(soldier.getMaxHealth());
            soldier.setFactionId(spawner.getFaction());
        } else if (unit instanceof AbstractArmyCommander commander) {
            // 首领 / 巡逻队长是特定阵营的单位，阵营跟着它自己走，不随刷怪点设置切换
            commander.setFactionId(commander.getDefaultFactionId());
        }
        return unit;
    }

    // ===================== 按模板刷怪蛋生成 =====================

    /**
     * 用模板蛋生成单位：士兵 / 首领的等级、装备、形态都按蛋里的数据还原（与直接放蛋一致）。
     *   蛋里没有阵营时用刷怪点自己设置的阵营。
     */
    @Nullable
    private static Mob createFromEgg(ServerLevel level, SpawnPointBlockEntity spawner,
                                     ItemStack egg, CompoundTag eggTag) {
        ResourceLocation tagged = parseFaction(eggTag);

        if (egg.getItem() instanceof SoldierSpawnEggItem) {
            AbstractArmySoldier soldier = SoldierSpawnEggItem.createSoldierFromEgg(level, egg);
            if (soldier == null) return null;
            applyFaction(soldier, tagged != null ? tagged : spawner.getFaction());
            return soldier;
        }
        if (egg.getItem() instanceof CommanderSpawnEggItem commanderEgg) {
            AbstractArmyCommander commander = CommanderSpawnEggItem.createCommanderFromEgg(level, egg);
            if (commander == null) return null;
            // 首领自带阵营：蛋里有阵营数据（收纳工具做的蛋）就按蛋走，否则用这颗蛋自己的阵营
            commander.setFactionId(tagged != null ? tagged : commanderEgg.getDefaultFaction());
            return commander;
        }
        return null;
    }

    /** 士兵的阵营：玩家阵营顺带挂上指挥官（与刷怪蛋落地一致）。 */
    private static void applyFaction(AbstractArmySoldier soldier, ResourceLocation faction) {
        soldier.setFactionId(faction);
        UUID owner = FactionIds.parsePlayer(faction);
        if (owner == null) return;
        FactionRegistry.getOrCreatePlayer(owner);
        soldier.setCommanderUuid(owner);
        soldier.setSquadId(0);
    }

    /** 骑乘形态：加进世界之后再上马（与刷怪蛋一致）。 */
    private static void applyMount(Mob unit, boolean mounted) {
        if (!mounted) return;
        if (unit instanceof AbstractArmySoldier soldier) {
            if (!soldier.canRide()) return;
            if (!soldier.getSoldierType().isCavalry()) {
                soldier.setSoldierType(soldier.getSoldierType().toggleMounted());
            }
            soldier.switchToCavalry();
        } else if (unit instanceof AbstractArmyCommander commander) {
            commander.spawnAndMountHorse();
        }
    }

    // ===================== 生成位置 =====================

    /**
     * 生成位置：优先紧贴生成面的那一侧（与募兵站同一套候选位置），
     *   同批多个单位挤不下时，再在生成面附近找一块能站人的地面。
     */
    @Nullable
    private static Vec3 findSpawnSpot(ServerLevel level, BlockPos pos, Direction facing, Mob unit) {
        BlockPos face = pos.relative(facing);
        BlockPos[] candidates = {face, face.above(), face.below(), face.above(2), pos.above(2)};
        for (BlockPos c : candidates) {
            Vec3 spot = new Vec3(c.getX() + 0.5, c.getY(), c.getZ() + 0.5);
            unit.moveTo(spot.x, spot.y, spot.z, 0.0f, 0.0f);
            if (level.noCollision(unit)) return spot;
        }

        Vec3 near = ArmySpawnUtil.findSafeSpawnNear(
                level, Vec3.atCenterOf(face), 1.0, 3.0, 2, level.random);
        if (near != null) {
            unit.moveTo(near.x, near.y, near.z, 0.0f, 0.0f);
            if (level.noCollision(unit)) return near;
        }
        return null;
    }

    // ===================== 工具 =====================

    /** 蛋里记录的阵营（没有 / 解析不出来时为 null）。 */
    @Nullable
    private static ResourceLocation parseFaction(CompoundTag eggTag) {
        String raw = eggTag.getString(RetrievalToolItem.KEY_FACTION);
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }
}
