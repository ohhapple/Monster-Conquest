package com.ohhapple.monster_conquest.world;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 野外巡逻队生成器：像原版掠夺者巡逻队那样，让各阵营巡逻队在世界上自然出现。
 *
 *   · 每隔 checkIntervalTicks（默认 1200 tick = 1 分钟）检测一次，且每次只有 spawnChance
 *     （默认 25%）的概率真正尝试 —— 平均约 4 分钟一支，频率和原版一样偏低；
 *   · 每次检测随机取一名非旁观玩家，在其周围 24~48 格（原版同款距离）找安全落点；
 *     附近已有巡逻队长时这次跳过，避免同一片区域越堆越多；
 *   · 维度与阵营固定对应：主世界出亡灵 / 灾厄 / 中立 / 怪物，下界出下界，末地出末地；
 *     每个阵营在 factions-common.toml 的 naturalPatrol 段里各有开关（默认只开亡灵与灾厄）；
 *   · 生成的巡逻队不绑锚点：远离玩家后会像普通怪物一样消失，不会在存档里越积越多；
 *     刷怪蛋 / 指令 / 玩家招募的单位不受影响（见 AbstractArmyCommander#isWild）。
 *   · 尊重原版规则：doPatrolSpawning / doMobSpawning 关掉时不刷，和平难度不刷，
 *     玩家待在村庄附近时不刷（村庄有自己的巡逻队与袭击体系），永不刷巡逻队的生物群系不刷。
 */
public final class NaturalPatrolSpawner {

    /** 与原版掠夺者巡逻队一致：与玩家的水平距离范围（格）。 */
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 48;
    /** 落点周围 ±10 格必须已加载，避免把队伍生成到未加载区块（原版同款检查）。 */
    private static final int CHUNK_RADIUS = 10;
    /** 这个半径内已有巡逻队长就跳过本次生成，防止扎堆。 */
    private static final double CROWD_RADIUS = 48.0;
    /** 落点头顶需要的净空格数（防止生成在密闭空间里）。 */
    private static final int OPEN_SKY = 3;

    private NaturalPatrolSpawner() {}

    /** 由 ServerTickEvent 每 tick 调用，内部按配置间隔执行。 */
    public static void tick(MinecraftServer server) {
        int interval = Math.max(200, FactionConfig.naturalPatrolCheckInterval);
        if (server.getTickCount() % interval != 0) return;
        if (FactionConfig.naturalPatrolChance <= 0.0) return;

        for (ServerLevel level : server.getAllLevels()) {
            trySpawnInLevel(level);
        }
    }

    private static void trySpawnInLevel(ServerLevel level) {
        if (!level.getGameRules().getBoolean(GameRules.RULE_DO_PATROL_SPAWNING)) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;

        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;

        RandomSource random = level.random;
        if (random.nextDouble() >= FactionConfig.naturalPatrolChance) return;

        // 本维度可用且已启用的阵营
        List<PatrolKind> candidates = new ArrayList<>();
        for (PatrolKind kind : PatrolKind.values()) {
            if (kind.dimension == level.dimension() && kind.enabled()) candidates.add(kind);
        }
        if (candidates.isEmpty()) return;

        ServerPlayer player = players.get(random.nextInt(players.size()));
        if (player.isSpectator()) return;
        // 村庄附近不刷：村庄有自己的巡逻队与袭击体系
        if (level.isCloseToVillage(player.blockPosition(), 2)) return;

        int dx = (MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1))
                * (random.nextBoolean() ? -1 : 1);
        int dz = (MIN_DISTANCE + random.nextInt(MAX_DISTANCE - MIN_DISTANCE + 1))
                * (random.nextBoolean() ? -1 : 1);
        BlockPos target = player.blockPosition().offset(dx, 0, dz);

        if (!level.hasChunksAt(target.getX() - CHUNK_RADIUS, target.getZ() - CHUNK_RADIUS,
                target.getX() + CHUNK_RADIUS, target.getZ() + CHUNK_RADIUS)) {
            return;
        }
        if (level.getBiome(target).is(BiomeTags.WITHOUT_PATROL_SPAWNS)) return;

        Vec3 pos = ArmySpawnUtil.findSupportedSpawn(level, Vec3.atCenterOf(target),
                0.5, 6.0, 4, 10, OPEN_SKY, false, random);
        if (pos == null) return;

        if (!level.getEntitiesOfClass(AbstractPatrolCaptain.class,
                new AABB(pos, pos).inflate(CROWD_RADIUS), AbstractPatrolCaptain::isAlive).isEmpty()) {
            return;
        }

        PatrolKind kind = candidates.get(random.nextInt(candidates.size()));
        AbstractPatrolCaptain captain = kind.create(level);
        if (captain == null) return;

        captain.moveTo(pos.x, pos.y, pos.z, random.nextFloat() * 360.0F, 0.0F);
        captain.setFactionId(kind.faction);
        captain.setWild(true);          // 野外巡逻队：不绑锚点，远离玩家后消失
        level.addFreshEntity(captain);
    }

    /** 各阵营的野外巡逻队：归属维度、配置开关、队长实体类型。 */
    private enum PatrolKind {
        UNDEAD(FactionIds.UNDEAD, Level.OVERWORLD),
        ILLAGER(FactionIds.ILLAGER, Level.OVERWORLD),
        NEUTRAL(FactionIds.NEUTRAL, Level.OVERWORLD),
        MONSTER(FactionIds.MONSTER, Level.OVERWORLD),
        NETHER(FactionIds.NETHER, Level.NETHER),
        END(FactionIds.END, Level.END);

        private final ResourceLocation faction;
        private final ResourceKey<Level> dimension;

        PatrolKind(ResourceLocation faction, ResourceKey<Level> dimension) {
            this.faction = faction;
            this.dimension = dimension;
        }

        boolean enabled() {
            return switch (this) {
                case UNDEAD -> FactionConfig.naturalPatrolUndeadEnabled;
                case ILLAGER -> FactionConfig.naturalPatrolIllagerEnabled;
                case NEUTRAL -> FactionConfig.naturalPatrolNeutralEnabled;
                case MONSTER -> FactionConfig.naturalPatrolMonsterEnabled;
                case NETHER -> FactionConfig.naturalPatrolNetherEnabled;
                case END -> FactionConfig.naturalPatrolEndEnabled;
            };
        }

        @Nullable
        AbstractPatrolCaptain create(ServerLevel level) {
            EntityType<? extends AbstractPatrolCaptain> type = switch (this) {
                case UNDEAD -> ModEntities.UNDEAD_PATROL_CAPTAIN.get();
                case ILLAGER -> ModEntities.ILLAGER_PATROL_CAPTAIN.get();
                case NEUTRAL -> ModEntities.NEUTRAL_PATROL_CAPTAIN.get();
                case MONSTER -> ModEntities.MONSTER_PATROL_CAPTAIN.get();
                case NETHER -> ModEntities.NETHER_PATROL_CAPTAIN.get();
                case END -> ModEntities.END_PATROL_CAPTAIN.get();
            };
            return type.create(level);
        }
    }
}
