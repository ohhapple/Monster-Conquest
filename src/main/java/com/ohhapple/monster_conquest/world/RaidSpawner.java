package com.ohhapple.monster_conquest.world;

import com.ohhapple.monster_conquest.army.ArmyAnchor;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.RaidAnchorEntity;
import com.ohhapple.monster_conquest.entity.commanders.patrol.IllagerPatrolCaptain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 袭击增援生成器（灾厄阵营）：
 *   · 随波次刷出：袭击每刷新一波，就立刻派出随机的灾厄巡逻队（不再按时间间隔刷），
 *     并且贴着本波掠夺者的刷新位置生成（原版掠夺者都是成批刷在一起）；
 *   · 波次越高派出越多，单波不超过 maxSquadsPerWave（默认 5 队，每队 1 名队长 + 3 名士兵）；
 *   · 计入袭击波次与进度条：本模组每一个参战单位（队长 / 士兵）都会有一个
 *     对应的「袭击锚点」实体（monster_conquest:raid_anchor）：
 *        · 锚点继承原版掠夺者，通过原版 API 加入袭击，所以「剩余袭击者」
 *          显示的就是实际人数（原版掠夺者 + 本模组每一个单位，一对一）；
 *        · 锚点血量逐单位镜像对应单位的血量，进度条据此变化；
 *        · 本模组单位全灭前，袭击不会推进到下一波。
 *     单位死亡或袭击结束时，对应锚点自动移除。
 *   · 敲钟高亮：锚点跟随对应单位的位置，钟会像原版一样把发光给它，
 *     再把发光同步给对应单位（只影响袭击刷出的单位）。
 */
public final class RaidSpawner {

    /** 锚点实体标签（随存档保存，用于识别与清理）。 */
    public static final String PROXY_TAG = "mc_raid_proxy";

    /** 锚点关联的参战单位 UUID 标签前缀。 */
    private static final String UNIT_TAG_PREFIX = "mc_raid_unit_";

    /** 波次检测 / 锚点维护间隔（tick）。1 秒一次，兼顾响应与服务器开销。 */
    private static final int MONITOR_INTERVAL = 20;

    /** 记录每个袭击已处理到的波次。key = 维度 + 袭击 ID。 */
    private static final Map<String, Integer> seenWave = new LinkedHashMap<>();

    private RaidSpawner() {}

    /** 由 ServerTickEvent 每 tick 调用，内部按固定间隔执行。 */
    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % MONITOR_INTERVAL != 0) return;

        // 1) 收集玩家附近正在进行的袭击（同一场袭击只处理一次）
        Map<String, Raid> active = new LinkedHashMap<>();
        Map<String, ServerLevel> levels = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!(player.level() instanceof ServerLevel level)) continue;
            Raid raid = level.getRaidAt(player.blockPosition());
            if (raid == null || !raid.isStarted() || !raid.isActive() || raid.isOver()) continue;
            String key = raidKey(level, raid);
            active.putIfAbsent(key, raid);
            levels.putIfAbsent(key, level);
        }

        // 2) 检测到新波次 → 立刻派出增援
        if (FactionConfig.illagerRaidSpawnEnabled) {
            for (Map.Entry<String, Raid> entry : active.entrySet()) {
                Raid raid = entry.getValue();
                int wave = raid.getGroupsSpawned();
                Integer last = seenWave.get(entry.getKey());
                if (last == null) {
                    // 第一次看到这场袭击（刚开打 / 中途接手 / 读档）：记录当前波次，不补刷旧波
                    seenWave.put(entry.getKey(), wave);
                    continue;
                }
                if (wave <= last) continue;
                seenWave.put(entry.getKey(), wave);
                spawnWaveReinforcements(levels.get(entry.getKey()), raid, wave);
            }
        }
        seenWave.keySet().retainAll(active.keySet());

        // 3) 维护袭击锚点：每个参战单位一个（人数计数 / 进度条 / 敲钟高亮）
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) continue;
            maintainAnchors(level);
        }

        // 4) 袭击期间：村庄巡逻队持续锁定所有袭击单位（不要求视线，直到袭击结束）
        if (server.getTickCount() % (MONITOR_INTERVAL * 2) == 0) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level() instanceof ServerLevel level) {
                    VillageDefense.refreshRaidAlert(level, player);
                }
            }
        }
    }

    // ===================== 按波次增援 =====================

    private static void spawnWaveReinforcements(ServerLevel level, Raid raid, int wave) {
        RandomSource random = level.random;

        int maxSquads = Math.max(1, FactionConfig.illagerRaidMaxSquadsPerWave);
        int squads = Math.min(maxSquads, wave);       // 波次越高派出越多
        if (squads > 1) squads -= random.nextInt(2);  // 少量随机
        squads = Math.max(1, squads);

        // 和本波掠夺者刷新在一起：以本波掠夺者的平均位置作为刷出点
        Vec3 base = waveSpawnPoint(level, raid, wave);
        if (base == null) base = Vec3.atCenterOf(raid.getCenter());
        for (int i = 0; i < squads; i++) {
            spawnPatrolSquad(level, raid, base);
        }
    }

    /** 本波掠夺者（含原版 + 本模组，排除锚点）的平均位置。 */
    @Nullable
    private static Vec3 waveSpawnPoint(ServerLevel level, Raid raid, int wave) {
        double x = 0.0, y = 0.0, z = 0.0;
        int count = 0;
        for (Raider raider : raid.getAllRaiders()) {
            if (raider == null || !raider.isAlive() || raider.isRemoved()) continue;
            if (raider.getWave() != wave) continue;
            if (raider.level() != level) continue;
            if (raider instanceof RaidAnchorEntity) continue;   // 排除本模组锚点
            x += raider.getX();
            y += raider.getY();
            z += raider.getZ();
            count++;
        }
        if (count == 0) return null;
        return new Vec3(x / count, y / count, z / count);
    }

    private static void spawnPatrolSquad(ServerLevel level, Raid raid, Vec3 base) {
        Vec3 pos = ArmySpawnUtil.findSafeSpawnNear(level, base, 2.0, 6.0, 3, level.random);
        if (pos == null) {
            pos = ArmySpawnUtil.findSurfaceSpawn(
                    level, BlockPos.containing(base), level.random, 2.0, 6.0);
        }
        if (pos == null) pos = base;

        IllagerPatrolCaptain captain = ModEntities.ILLAGER_PATROL_CAPTAIN.get().create(level);
        if (captain == null) return;
        captain.moveTo(pos.x, pos.y, pos.z, level.random.nextFloat() * 360.0f, 0.0f);
        captain.setFactionId(FactionIds.ILLAGER);
        captain.getAnchor().bind(ArmyAnchor.Type.RAID, raid.getCenter());
        level.addFreshEntity(captain);
        // 锚点由 maintainAnchors 在 0.5 秒内按"每个单位一个"自动补齐（含该队长的 3 名士兵）
    }

    // ===================== 袭击锚点（逐单位计入袭击） =====================

    /**
     * 维护本维度的袭击锚点：
     *   · 每个本模组参战单位（袭击锚定的灾厄队长 / 士兵）对应一个锚点；
     *   · 缺失就补建、已存在就同步位置 / 血量 / 敲钟高亮；
     *   · 单位死亡或离开后回收对应锚点；
     *   · 同时清理残留的袭击代理实体。
     */
    private static void maintainAnchors(ServerLevel level) {
        Map<UUID, RaidAnchorEntity> anchors = new HashMap<>();
        Map<UUID, LivingEntity> units = new HashMap<>();

        for (ServerPlayer player : level.players()) {
            AABB box = player.getBoundingBox().inflate(256.0);

            for (RaidAnchorEntity anchor : level.getEntitiesOfClass(RaidAnchorEntity.class, box)) {
                UUID unitId = linkedUnitId(anchor);
                if (unitId != null) anchors.put(unitId, anchor);
            }

            // 残留的袭击代理（原版掠夺者）：直接回收
            for (Pillager legacy : level.getEntitiesOfClass(Pillager.class, box,
                    p -> !(p instanceof RaidAnchorEntity) && p.getTags().contains(PROXY_TAG))) {
                discardLegacyProxy(legacy);
            }

            for (AbstractArmySoldier soldier : level.getEntitiesOfClass(AbstractArmySoldier.class, box,
                    s -> s.isAlive() && s.getAnchor().isRaid()
                            && FactionIds.ILLAGER.equals(s.getFactionId()))) {
                units.put(soldier.getUUID(), soldier);
            }
            for (AbstractArmyCommander commander : level.getEntitiesOfClass(AbstractArmyCommander.class, box,
                    c -> c.isAlive() && c.getAnchor().isRaid()
                            && FactionIds.ILLAGER.equals(c.getFactionId()))) {
                units.put(commander.getUUID(), commander);
            }
        }

        // 缺锚点 → 补建；有锚点 → 同步
        for (Map.Entry<UUID, LivingEntity> entry : units.entrySet()) {
            RaidAnchorEntity anchor = anchors.get(entry.getKey());
            if (anchor == null) {
                createUnitAnchor(level, entry.getValue());
            } else {
                updateUnitAnchor(level, anchor, entry.getValue());
            }
        }

        // 对应单位已经死亡 / 不在附近的锚点 → 回收
        for (Map.Entry<UUID, RaidAnchorEntity> entry : anchors.entrySet()) {
            if (!units.containsKey(entry.getKey())) {
                removeUnitAnchor(entry.getValue());
            }
        }
    }

    private static void createUnitAnchor(ServerLevel level, LivingEntity unit) {
        Raid raid = level.getRaidAt(unit.blockPosition());
        if (raid == null || !raid.isStarted() || !raid.isActive() || raid.isOver()) return;

        RaidAnchorEntity anchor = ModEntities.RAID_ANCHOR.get().create(level);
        if (anchor == null) return;

        anchor.moveTo(unit.getX(), unit.getY(), unit.getZ(), 0.0f, 0.0f);
        anchor.enforceAnchorState();
        anchor.addTag(PROXY_TAG);
        anchor.addTag(UNIT_TAG_PREFIX + unit.getUUID());

        var attr = anchor.getAttribute(Attributes.MAX_HEALTH);
        if (attr != null) attr.setBaseValue(Math.max(1.0f, unit.getMaxHealth()));
        anchor.setHealth(Math.max(0.5f, unit.getHealth()));

        level.addFreshEntity(anchor);
        // 4 参数版本：pos = null、recruited = true → 只登记进袭击（计入总血量 / 进度条），
        // 不重新定位、不加袭击 buff，也不重复生成实体。
        raid.joinRaid(Math.max(1, raid.getGroupsSpawned()), anchor, null, true);
        raid.updateBossbar();
    }

    private static void updateUnitAnchor(ServerLevel level, RaidAnchorEntity anchor, LivingEntity unit) {
        anchor.enforceAnchorState();

        // 跟随对应单位的位置（钟的 48 格高亮范围因此与原版单位判定一致）
        if (anchor.position().distanceToSqr(unit.position()) > 1.0) {
            anchor.moveTo(unit.getX(), unit.getY(), unit.getZ(), 0.0f, 0.0f);
        }

        // 血量逐单位镜像（进度条据此变化）
        float health = Math.max(0.5f, unit.getHealth());
        if (Math.abs(anchor.getHealth() - health) > 0.05f) {
            anchor.setHealth(health);
            Raid raid = level.getRaidAt(anchor.blockPosition());
            if (raid != null) raid.updateBossbar();
        }

        // 敲钟高亮：锚点被钟高亮时，把发光同步给对应单位
        MobEffectInstance glow = anchor.getEffect(MobEffects.GLOWING);
        if (glow != null) {
            unit.addEffect(new MobEffectInstance(
                    MobEffects.GLOWING, Math.max(20, glow.getDuration()), 0, false, false));
        }
    }

    private static void removeUnitAnchor(RaidAnchorEntity anchor) {
        Raid raid = anchor.getCurrentRaid();
        if (raid != null) raid.removeFromRaid(anchor, false);
        anchor.discard();
    }

    /** 锚点关联的单位 UUID（没有单位标签的锚点返回 null）。 */
    @Nullable
    private static UUID linkedUnitId(RaidAnchorEntity anchor) {
        for (String tag : anchor.getTags()) {
            if (!tag.startsWith(UNIT_TAG_PREFIX)) continue;
            try {
                return UUID.fromString(tag.substring(UNIT_TAG_PREFIX.length()));
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    /** 残留的袭击代理（原版掠夺者）：从袭击中移除并销毁。 */
    private static void discardLegacyProxy(Pillager legacy) {
        Raid raid = legacy.getCurrentRaid();
        if (raid != null) raid.removeFromRaid(legacy, false);
        legacy.discard();
    }

    private static String raidKey(ServerLevel level, Raid raid) {
        return level.dimension().location() + "#" + raid.getId();
    }
}
