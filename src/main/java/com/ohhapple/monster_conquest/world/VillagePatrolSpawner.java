package com.ohhapple.monster_conquest.world;

import com.ohhapple.monster_conquest.army.ArmyAnchor;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.commanders.patrol.VillagerPatrolCaptain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 村庄巡逻队生成器（村民阵营）：
 *   · 只以原版村庄判定为准：玩家附近"已认领的村庄兴趣点"（床 / 工作站，POI）即视为村庄；
 *     不再要求村庄里必须有村民——原版已经用 POI 定义村庄，村民被封住 / 埋在地里也不影响刷出；
 *   · 每个村庄最多 maxCaptains（默认 3）名巡逻队长；巡逻士兵总数硬上限自动按
 *     maxCaptains × 3 计算，只有"队长数 < maxCaptains 且 士兵总数 + 3 ≤ maxCaptains × 3"时才补刷
 *     （每次补刷都为新队长要带的 3 名士兵预留位置，士兵总数不会超上限）；
 *   · 生成位置会做安全检查：不落在房顶 / 沟底 / 危险方块上，也不生成到墙里；
 *   · 只影响自然刷出的单位：刷怪蛋 / 指令 / 玩家招募的单位不受影响。
 */
public final class VillagePatrolSpawner {

    /** 生成点头顶需要的最小连续净空（防止把巡逻队封在盒子里 / 房间里）。 */
    private static final int OPEN_SKY = 5;

    private VillagePatrolSpawner() {}

    /** 由 ServerTickEvent 每 tick 调用，内部按配置间隔执行。 */
    public static void tick(MinecraftServer server) {
        if (!FactionConfig.villagerVillageSpawnEnabled) return;
        if (FactionConfig.villagerVillageMaxCaptains <= 0) return;

        int interval = Math.max(20, FactionConfig.villagerVillageCheckInterval);
        if (server.getTickCount() % interval != 0) return;

        for (ServerLevel level : server.getAllLevels()) {
            trySpawnInLevel(level);
        }
    }

    private static void trySpawnInLevel(ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        // 和平难度 + 和平清除单位：不刷（刷出来也会立刻被清除）
        if (level.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL
                && GeneralConfig.peacefulRemovesUnits) {
            return;
        }

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        RandomSource random = level.random;
        double radius = Math.max(16.0, FactionConfig.villagerVillageRadius);
        // 村庄合并判定半径：同一村庄的巡逻队锚点可能散布在多个兴趣点附近，
        //   统计时用更大的半径把整个村庄算作一处，避免同一村庄重复刷出远超上限的队伍。
        double mergeRadius = Math.min(128.0, Math.max(64.0, radius * 2.0));

        // 1) 收集玩家附近已认领的村庄兴趣点（床 / 工作站）—— 原版的村庄就是这些 POI
        Set<BlockPos> villagePois = new LinkedHashSet<>();
        for (ServerPlayer player : players) {
            level.getPoiManager()
                    .getInRange(type -> type.is(PoiTypeTags.VILLAGE), player.blockPosition(),
                            96, PoiManager.Occupancy.IS_OCCUPIED)
                    .forEach(record -> villagePois.add(record.getPos().immutable()));
        }
        if (villagePois.isEmpty()) return;

        List<BlockPos> candidates = new ArrayList<>(villagePois);
        int attempts = Math.min(candidates.size(), 6);
        for (int i = 0; i < attempts; i++) {
            BlockPos seed = candidates.get(random.nextInt(candidates.size()));
            if (!level.isVillage(seed)) continue;

            // 村庄中心 = 该兴趣点周围 64 格内所有已认领村庄兴趣点的平均位置。
            //   同一村庄算出来的中心几乎一致，巡逻队锚点因此统一，计数才不会漏。
            BlockPos center = villageCenter(level, seed);
            if (center == null) continue;

            AABB villageBox = new AABB(center).inflate(mergeRadius);
            // 实体查询范围必须比统计范围大：巡逻队会在自己锚点附近活动（活动半径 + 硬拉回余量），
            //   查询框太小会把跑到村庄边缘的队伍漏掉，误判成"没满"继续刷。
            AABB queryBox = new AABB(center).inflate(mergeRadius + radius + 32.0);

            // 2) 上限条件：队长数 < maxCaptains 且 士兵总数 + 3 ≤ maxCaptains × 3 才补刷
            //    （为新队长出生时携带的 3 名士兵预留位置，士兵总数硬上限不会被突破）
            int captains = 0;
            for (VillagerPatrolCaptain captain : level.getEntitiesOfClass(
                    VillagerPatrolCaptain.class, queryBox,
                    c -> c.isAlive() && c.isVillageBound())) {
                BlockPos anchor = captain.getAnchorPos();
                if (anchor != null && villageBox.contains(Vec3.atCenterOf(anchor))) captains++;
            }
            if (captains >= FactionConfig.villagerVillageMaxCaptains) continue;

            int soldiers = 0;
            for (AbstractArmySoldier soldier : level.getEntitiesOfClass(
                    AbstractArmySoldier.class, queryBox,
                    s -> s.isAlive() && s.getAnchor().isVillage())) {
                BlockPos anchor = soldier.getAnchor().getPos();
                if (anchor != null && villageBox.contains(Vec3.atCenterOf(anchor))) soldiers++;
            }
            if (soldiers + VillagerPatrolCaptain.PATROL_SOLDIER_COUNT
                    > FactionConfig.villagerVillageMaxCaptains * VillagerPatrolCaptain.PATROL_SOLDIER_COUNT) {
                continue;
            }

            // 3) 安全生成点：锚点用村庄中心。
            //    向下扫到"下方有连续实心支撑"的位置再落脚（不看方块种类）：
            //      屋顶 / 二楼地板（下方是房间空气）、树冠（下方是空气）都会被排除，
            //      而地面上铺的木板 / 羊毛 / 台阶 / 土径（下面是泥土 / 石头）可以正常落点。
            //    优先"能直视 + 头顶开阔"（常规村庄）；村民 / 工作方块被方块围起来时，
            //    退到忽略墙体遮挡的更大范围搜索，但仍然要求落点没有被方块封住。
            Vec3 pos = ArmySpawnUtil.findSupportedSpawn(
                    level, Vec3.atCenterOf(center), 2.0, 12.0, 6, 16, OPEN_SKY, true, random);
            if (pos == null) {
                pos = ArmySpawnUtil.findSupportedSpawn(
                        level, Vec3.atCenterOf(center), 3.0, 24.0, 8, 20, OPEN_SKY, false, random);
            }
            if (pos == null) continue;

            // 4) 生成队长：3 名士兵由队长自己在首个 tick 召唤（继承村庄锚点）
            VillagerPatrolCaptain captain = ModEntities.VILLAGER_PATROL_CAPTAIN.get().create(level);
            if (captain == null) continue;
            captain.moveTo(pos.x, pos.y, pos.z, random.nextFloat() * 360.0f, 0.0f);
            captain.setFactionId(FactionIds.VILLAGER);
            captain.getAnchor().bind(ArmyAnchor.Type.VILLAGE, center);
            level.addFreshEntity(captain);
            return; // 每次检测每个维度最多补 1 支巡逻队
        }
    }

    /**
     * 村庄中心：以兴趣点为种子，取周围 64 格内所有已认领村庄兴趣点的平均位置。
     *   同一村庄算出来的中心几乎一致，巡逻队锚点因此统一，计数才不会漏。
     */
    private static BlockPos villageCenter(ServerLevel level, BlockPos seed) {
        List<PoiRecord> records = level.getPoiManager()
                .getInRange(type -> type.is(PoiTypeTags.VILLAGE), seed, 64, PoiManager.Occupancy.IS_OCCUPIED)
                .toList();
        if (records.isEmpty()) return null;

        long sx = 0, sy = 0, sz = 0;
        for (PoiRecord record : records) {
            BlockPos pos = record.getPos();
            sx += pos.getX();
            sy += pos.getY();
            sz += pos.getZ();
        }
        int n = records.size();
        return new BlockPos((int) Math.round((double) sx / n),
                (int) Math.round((double) sy / n),
                (int) Math.round((double) sz / n));
    }
}
