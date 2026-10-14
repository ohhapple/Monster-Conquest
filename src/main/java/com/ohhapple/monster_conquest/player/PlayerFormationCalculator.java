package com.ohhapple.monster_conquest.player;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.RoutePlanner;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 玩家军队阵型槽位计算：本地坐标（面朝 +z）算完后按 yaw 旋转到世界坐标。
 * LINE 按兵种由近到远排列，SHIELD_WALL 近战在前，WEDGE 人字形，SKIRMISH 环绕散布。
 */
public final class PlayerFormationCalculator {

    private PlayerFormationCalculator() {}

    private static final int PER_ROW = 16;

    private static final double CELL_INFANTRY = 1.4;
    private static final double ROW_GAP_INFANTRY = 2.2;

    private static final double CELL_CAVALRY = 2.8;
    private static final double ROW_GAP_CAVALRY = 3.8;

    @Nullable
    public static Vec3 getSlot(Player player, AbstractArmySoldier soldier,
                               PlayerCommandState.SquadState ss,
                               PlayerCommandState.FormationMode mode,
                               Vec3 anchor, float yaw) {
        return getSlot(player, soldier, ss, mode, anchor, yaw, false);
    }

    /**
     * @param centerOnAnchor true = 阵型以锚点为中心展开（"前往 / 驻守"这类指定点命令用），
     *                       false = 阵型排在锚点后方（跟随用，队伍跟在主人身后）。
     */
    @Nullable
    public static Vec3 getSlot(Player player, AbstractArmySoldier soldier,
                               PlayerCommandState.SquadState ss,
                               PlayerCommandState.FormationMode mode,
                               Vec3 anchor, float yaw, boolean centerOnAnchor) {
        if (ss.shouldRebuildFormation(player.tickCount, mode, anchor, yaw)) {
            rebuild(player, soldier, ss, mode, anchor, yaw, centerOnAnchor);
            ss.commitFormationCache(player.tickCount, mode, anchor, yaw);
        }
        Vec3 own = ss.getFormationSlots().get(soldier.getUUID());
        if (own != null) return own;
        // 没排到阵位（例如这名士兵不在本次重建的扫描范围内 —— 掉队的个别单位）：
        //   先按"前往命令点 / 跟随对象"处理，等它走进范围后下一次重建自然就会排位。
        //   返回 null 会让它原地卡住 —— 个别单位顶着墙角不动就是这么来的。
        return anchor;
    }

    private static void rebuild(Player player, AbstractArmySoldier soldier,
                                PlayerCommandState.SquadState ss,
                                PlayerCommandState.FormationMode mode,
                                Vec3 anchor, float yaw, boolean centerOnAnchor) {
        ss.getFormationSlots().clear();

        // FREE：不走此路径
        if (mode == PlayerCommandState.FormationMode.FREE) return;

        // 无小队单兵：个人状态只服务自己 —— 阵位就是锚点本身，
        //   不能把"其它同样无小队的士兵"也算进来占格。
        int squadId = soldier.getSquadId();
        boolean lone = squadId == 0;

        // 扫描范围取玩家位置与锚点位置两个盒子的并集：
        //   士兵无论在玩家身边还是已接近锚点，都能被扫到并分配阵位。
        double range = GeneralConfig.armyFollowRange;
        double minX = Math.min(player.getX(), anchor.x) - range;
        double maxX = Math.max(player.getX(), anchor.x) + range;
        double minZ = Math.min(player.getZ(), anchor.z) - range;
        double maxZ = Math.max(player.getZ(), anchor.z) + range;
        double minY = Math.min(player.getY(), anchor.y) - 32.0;
        double maxY = Math.max(player.getY(), anchor.y) + 32.0;

        // 扫描范围还要覆盖"正在请求阵位的这名士兵"：掉队的个别单位也要能被排到，
        //   否则它们会一直拿不到阵位、原地卡住。
        minX = Math.min(minX, soldier.getX() - range);
        maxX = Math.max(maxX, soldier.getX() + range);
        minZ = Math.min(minZ, soldier.getZ() - range);
        maxZ = Math.max(maxZ, soldier.getZ() + range);
        minY = Math.min(minY, soldier.getY() - 32.0);
        maxY = Math.max(maxY, soldier.getY() + 32.0);

        AABB scanBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);

        List<AbstractArmySoldier> all = player.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                scanBox,
                s -> s.isAlive() && !s.isRemoved()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && (lone ? s == soldier : s.getSquadId() == squadId));
        if (all.isEmpty()) return;
        all.sort(Comparator.comparing(e -> e.getUUID().toString()));

        Map<AbstractArmySoldier, Integer> indexMap = new HashMap<>();
        for (int i = 0; i < all.size(); i++) indexMap.put(all.get(i), i);

        List<Vec3> offsets = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) offsets.add(Vec3.ZERO);

        // 无小队单兵：锚点即站位（offset 0），不参与小队阵型排布
        if (!lone) {
            // 地形适配：按锚点朝向测出横向能放几列；1~2 列时整队收缩成单列纵队。
            int perRow = terrainRowLimit(player, anchor, yaw, PER_ROW);
            if (perRow <= 2) {
                columnOffsets(all, offsets);
            } else {
                switch (mode) {
                    case SHIELD_WALL -> shieldWallOffsets(all, indexMap, offsets, perRow);
                    case WEDGE -> wedgeOffsets(all, indexMap, offsets, perRow);
                    case SKIRMISH -> skirmishOffsets(all, offsets, perRow);
                    case LINE, DEFAULT -> lineOffsets(all, indexMap, offsets, perRow);
                    case FREE -> { /* 无槽位 */ }
                }
            }
        }

        float rad = yaw * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);

        // "前往 / 驻守"命令：把整片阵型前后居中到命令点上 ——
        //   否则阵型整体排在锚点后方（线列阵第一排就在锚点后 5 格、后排兵种更远），
        //   命令点在墙 / 障碍另一侧时站位会落在障碍这一侧，表现为"不会绕路、被挡住"。
        double zShift = 0.0;
        if (centerOnAnchor && !offsets.isEmpty()) {
            double zMin = Double.MAX_VALUE;
            double zMax = -Double.MAX_VALUE;
            for (Vec3 off : offsets) {
                zMin = Math.min(zMin, off.z);
                zMax = Math.max(zMax, off.z);
            }
            zShift = -(zMin + zMax) / 2.0;
        }

        // 同一次重建里已经用掉的落点：多个不可用的理想点可能收缩到同一格，
        //   不排除的话一群士兵会盯着同一格互相顶（表现为原地打转、谁也站不住）。
        // 阵位只能落在"士兵真的能走过去"的落点上：落点在封闭结构里 / 需要攀爬的高处时，
        //   士兵会对着障碍一直推、还会反复尝试跳上去。搜索起点取士兵这一侧，
        //   避免用指挥官（可能飞在空中 / 站在结构顶）的位置把高处误判成可达。
        // 可达性分两套：步兵按人身宽、骑乘单位按坐骑净空（宽体走不进 1 格宽的缝，
        //   也就不会被排到缝那边的落点上）。两套共用同一个缓存窗口。
        Mob mountSample = null;
        for (AbstractArmySoldier s : all) {
            if (s.getVehicle() instanceof Mob mount
                    && (mountSample == null || mount.getBbWidth() > mountSample.getBbWidth())) {
                mountSample = mount;
            }
        }
        Reach reach = reachableSpots(player, ss, anchor, offsets, all, mountSample);
        Set<Long> reachable = reach.foot();
        List<Long> reachOrdered = sortByDistance(reachable, anchor);
        Set<Long> reachableMount = reach.mounted();
        List<Long> reachOrderedMount = reachableMount == null
                ? null : sortByDistance(reachableMount, anchor);
        // 命令点落在方块里 / 不可达区域时，阵型中心换成"可达落点里离命令点最近的那个"：
        //   否则所有候选都会因为"从锚点看不见"被拒掉，最后退回理想点（很可能就在墙里）。
        Vec3 center = reachOrdered.isEmpty() ? anchor
                : new Vec3(BlockPos.getX(reachOrdered.get(0)) + 0.5,
                           BlockPos.getY(reachOrdered.get(0)),
                           BlockPos.getZ(reachOrdered.get(0)) + 0.5);
        int[] reachCursor = {0};
        int[] reachCursorMount = {0};

        List<Vec3> claimed = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Vec3 off = offsets.get(i);
            double oz = off.z + zShift;         // 居中位移必须先加进偏移再旋转，否则朝向非 0° 时会丢
            double rx = off.x * cos - oz * sin;
            double rz = off.x * sin + oz * cos;
            double wx = center.x + rx;
            double wz = center.z + rz;
            AbstractArmySoldier s = all.get(i);
            boolean riding = s.getVehicle() instanceof Mob && reachableMount != null;
            ss.getFormationSlots().put(s.getUUID(), resolveSlot(player, s, wx, center.y, wz, center,
                    claimed,
                    riding ? reachableMount : reachable,
                    riding ? reachOrderedMount : reachOrdered,
                    riding ? reachCursorMount : reachCursor));
        }
    }

    /**
     * 把理想槽位修正到"真正能站、也能走出来"的位置：
     *   · 理想点落在墙里 / 悬空 / 封闭空穴（四周全堵）时，先在附近螺旋扩大搜索
     *     （优先"从阵型中心看得见"的落点 —— 看得见基本就走得到）；
     *   · 都没有就朝阵型中心收缩（中心附近一般站得下，至少能贴着队伍）；
     *   · 所有候选都必须是"可达落点"（从指挥官身边能走过去），
     *     最后兜底直接取"可达落点里离阵型中心最近的" —— 保证士兵能走到、站得住；
     *     不返回这名士兵当前站的位置（那会把士兵永远钉在他刚好路过的地方）。
     *   · 同一次重建里已被别的士兵占用的落点会跳过（claimed）。
     *   · 骑乘单位只在"马 + 骑手"站得下的落点里选（宽体不能站进贴墙的缝 / 死角）。
     */
    private static Vec3 resolveSlot(Player player, AbstractArmySoldier soldier,
                                    double wx, double baseY, double wz,
                                    Vec3 anchor, List<Vec3> claimed,
                                    Set<Long> reachable, List<Long> reachOrdered, int[] reachCursor) {
        Level level = player.level();
        double wy = findGroundY(player, wx, baseY, wz);
        if (!claimedNear(claimed, wx, wz) && isUsableSlot(level, anchor, wx, wy, wz)
                && isReachable(reachable, wx, wy, wz)
                && moverFits(soldier, level, wx, wy, wz)) {
            return claim(claimed, wx, wy, wz);
        }

        // 附近螺旋搜索：8 个方向 × 1~5 格
        for (double r : new double[]{1.0, 2.0, 3.0, 4.0, 5.0}) {
            for (int k = 0; k < 8; k++) {
                double a = k * (Math.PI / 4.0);
                double nx = wx + Math.cos(a) * r;
                double nz = wz + Math.sin(a) * r;
                double ny = findGroundY(player, nx, baseY, nz);
                if (claimedNear(claimed, nx, nz)) continue;
                if (isUsableSlot(level, anchor, nx, ny, nz)
                        && isReachable(reachable, nx, ny, nz)
                        && moverFits(soldier, level, nx, ny, nz)) {
                    return claim(claimed, nx, ny, nz);
                }
            }
        }

        // 朝锚点收缩
        for (int step = 1; step <= 8; step++) {
            double t = step / 8.0;
            double nx = wx + (anchor.x - wx) * t;
            double nz = wz + (anchor.z - wz) * t;
            double ny = findGroundY(player, nx, anchor.y, nz);
            if (claimedNear(claimed, nx, nz)) continue;
            if (isUsableSlot(level, anchor, nx, ny, nz)
                    && isReachable(reachable, nx, ny, nz)
                    && moverFits(soldier, level, nx, ny, nz)) {
                return claim(claimed, nx, ny, nz);
            }
        }

        // 兜底一：能站 + 可达就行；从阵型中心一侧往外找 ——
        //   中心附近更接近大部队、也更容易走到。
        for (int step = 8; step >= 1; step--) {
            double t = step / 8.0;
            double nx = wx + (anchor.x - wx) * t;
            double nz = wz + (anchor.z - wz) * t;
            double ny = findGroundY(player, nx, anchor.y, nz);
            if (!claimedNear(claimed, nx, nz)
                    && AiNavUtil.isOpenStandingSpot(level, nx, ny, nz)
                    && isReachable(reachable, nx, ny, nz)
                    && moverFits(soldier, level, nx, ny, nz)) {
                return claim(claimed, nx, ny, nz);
            }
        }
        for (double r : new double[]{1.0, 2.0, 3.0, 4.0, 5.0}) {
            for (int k = 0; k < 8; k++) {
                double a = k * (Math.PI / 4.0);
                double nx = wx + Math.cos(a) * r;
                double nz = wz + Math.sin(a) * r;
                double ny = findGroundY(player, nx, baseY, nz);
                if (!claimedNear(claimed, nx, nz)
                        && AiNavUtil.isOpenStandingSpot(level, nx, ny, nz)
                        && isReachable(reachable, nx, ny, nz)
                        && moverFits(soldier, level, nx, ny, nz)) {
                    return claim(claimed, nx, ny, nz);
                }
            }
        }

        // 兜底二：可达落点里离阵型中心最近的（按距离排好的表，跳过已占用的）。
        while (reachCursor[0] < reachOrdered.size()) {
            long key = reachOrdered.get(reachCursor[0]++);
            double fx = BlockPos.getX(key) + 0.5;
            double fy = BlockPos.getY(key);
            double fz = BlockPos.getZ(key) + 0.5;
            if (claimedNear(claimed, fx, fz)) continue;
            if (!moverFits(soldier, level, fx, fy, fz)) continue;
            return claim(claimed, fx, fy, fz);
        }
        // 理论上到不了这里（可达集合至少含指挥官脚下那格）；保底保留理想落点。
        return new Vec3(wx, wy, wz);
    }

    /** 这个落点（脚所在格）是不是"可达落点"。 */
    private static boolean isReachable(Set<Long> reachable, double x, double y, double z) {
        if (reachable.isEmpty()) return true;   // 没有任何可达信息时不额外限制（保底行为）
        return reachable.contains(BlockPos.containing(x, y + 0.5, z).asLong());
    }

    /** 一次重建要用的两套可达落点：foot = 步兵（人身宽），mounted = 骑乘（无骑乘单位时为 null）。 */
    private record Reach(Set<Long> foot, @Nullable Set<Long> mounted) {}

    /**
     * 从士兵一侧出发、"能走过去"的落点集合（有界搜索，向命令点方向优先展开）。
     *   走法与绕路器一致：八方向、上 1 下 3、不切角；梯子 / 藤蔓 / 脚手架算可通行。
     *   步兵和骑乘单位分开算：骑乘那套用与绕路器相同的坐骑净空判定，
     *   宽体到不了的地方不会被排成阵位。
     *   结果按"命令点 / 起点 / 半径"缓存，没明显变化时不重算。
     */
    private static Reach reachableSpots(Player player, PlayerCommandState.SquadState ss,
                                        Vec3 anchor, List<Vec3> offsets,
                                        List<AbstractArmySoldier> squad,
                                        @Nullable Mob mountSample) {
        double maxOff = 0.0;
        for (Vec3 off : offsets) maxOff = Math.max(maxOff, Math.max(Math.abs(off.x), Math.abs(off.z)));

        // 起点取"士兵这一侧"（离命令点最近的那名士兵）：指挥官可能站在屋顶 / 飞在建筑上方，
        //   从它的位置出发会把"屋顶"当成可达区域，阵位就会排到士兵上不去的高处。
        BlockPos seed = null;
        AbstractArmySoldier closest = null;
        double best = Double.MAX_VALUE;
        for (AbstractArmySoldier s : squad) {
            double d = s.position().distanceToSqr(anchor);
            if (d < best) {
                best = d;
                closest = s;
            }
        }
        Level level = player.level();
        if (closest != null) {
            seed = findWalkableSeed(level, closest.getX(), closest.getY(), closest.getZ());
        }
        if (seed == null) {
            seed = findWalkableSeed(level, player.getX(), player.getY(), player.getZ());
        }
        // 半径必须覆盖"起点 → 命令点"的整段距离 + 阵型展开尺寸：远距离命令时
        //   搜索才不会在半路被截断（截断会让阵位判定失效、退回理想点）。
        double radius = Math.min(160.0, Math.max(32.0,
                seed == null ? 48.0
                        : Math.hypot(seed.getX() + 0.5 - anchor.x, seed.getZ() + 0.5 - anchor.z)
                                + maxOff + 12.0));
        // 半径按 16 格取整后再做缓存判定：否则单位一动半径就变，缓存永远不命中，
        //   每次重建都要跑一遍全量搜索（部队移动时就是每 20 tick 一次的卡顿来源）。
        radius = Math.ceil(radius / 16.0) * 16.0;
        boolean anchorSame = Math.abs(ss.reachCacheAnchorX - anchor.x) < 4.0
                && Math.abs(ss.reachCacheAnchorZ - anchor.z) < 4.0
                && Math.abs(ss.reachCacheAnchorY - anchor.y) < 6.0;

        boolean footCached = seed != null && ss.reachCache != null
                && ss.reachCacheRadius >= radius - 0.5 && anchorSame;
        if (!footCached) {
            ss.reachCache = floodReachable(level, anchor, seed, radius,
                    p -> walkableSpot(level, p));
            ss.reachCacheAnchorX = anchor.x;
            ss.reachCacheAnchorZ = anchor.z;
            ss.reachCacheAnchorY = anchor.y;
            ss.reachCacheRadius = radius;
        }

        // 骑乘那套：只在有骑乘单位时算；跟随同一个缓存窗口。
        Set<Long> mounted = null;
        if (mountSample != null) {
            double mountWidth = mountSample.getBbWidth();
            double mountExtra = mountSample instanceof WarHorseEntity
                    ? WarHorseEntity.RIDER_HEIGHT : 0.0;
            LivingEntity rider = mountSample.getFirstPassenger() instanceof LivingEntity r
                    ? r : mountSample;
            // 节点要同时满足两件事才算骑乘可达：
            //   ① 绕路器语义下能通过（节点 + 转向偏移的净空）；
            //   ② 这个"格"本身能让马站在格中心（阵位落点用的就是这个点）。
            Predicate<BlockPos> mountWalkable = mountSample instanceof WarHorseEntity
                    ? p -> RoutePlanner.walkableFor(level, mountSample, mountExtra, p)
                            && AiNavUtil.mountStandFits(level, rider,
                                    p.getX() + 0.5, p.getY(), p.getZ() + 0.5)
                    : p -> RoutePlanner.walkableFor(level, mountSample, mountExtra, p);
            boolean mountCached = ss.reachCacheMount != null
                    && Math.abs(ss.reachCacheMountWidth - mountWidth) < 1.0E-4
                    && Math.abs(ss.reachCacheMountExtra - mountExtra) < 1.0E-4
                    && ss.reachCacheMountRadius >= radius - 0.5 && anchorSame;
            if (mountCached) {
                mounted = ss.reachCacheMount;
            } else {
                BlockPos mountSeed = findMountSeed(mountSample.getX(), mountSample.getY(),
                        mountSample.getZ(), mountWalkable);
                if (mountSeed != null) {
                    Set<Long> set = floodReachable(level, anchor, mountSeed, radius, mountWalkable);
                    if (!set.isEmpty()) {
                        ss.reachCacheMount = set;
                        ss.reachCacheMountRadius = radius;
                        ss.reachCacheMountWidth = mountWidth;
                        ss.reachCacheMountExtra = mountExtra;
                        mounted = set;
                    }
                }
            }
        }
        return new Reach(ss.reachCache, mounted);
    }

    /**
     * 有界广度搜索：从 seed 出发，走法与绕路器一致（八方向、上 1 下 3、不切角）。
     *   同一格子的通行判定做记忆化 —— 邻居展开会反复查同一格，宽体判定更贵，缓存能省很多。
     */
    private static Set<Long> floodReachable(Level level, Vec3 anchor, @Nullable BlockPos seed,
                                            double radius, Predicate<BlockPos> walkable) {
        Set<Long> result = new HashSet<>();
        if (seed == null) return result;
        result.add(seed.asLong());
        Map<Long, Boolean> passCache = new HashMap<>();
        Predicate<BlockPos> pass = p -> passCache.computeIfAbsent(p.asLong(), k -> walkable.test(p));
        double radiusSq = radius * radius;
        // 优先朝命令点方向展开（命令点很远时也不会把预算全花在路上）
        java.util.PriorityQueue<BlockPos> queue = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(p -> {
                    double dx = p.getX() + 0.5 - anchor.x;
                    double dz = p.getZ() + 0.5 - anchor.z;
                    double dy = p.getY() - anchor.y;
                    return dx * dx + dz * dz + dy * dy * 4.0;
                }));
        queue.add(seed);
        while (!queue.isEmpty() && result.size() < 5000) {
            BlockPos cur = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy : STEP_DYS) {
                        BlockPos next = new BlockPos(cur.getX() + dx, cur.getY() + dy, cur.getZ() + dz);
                        double ddx = next.getX() + 0.5 - anchor.x;
                        double ddz = next.getZ() + 0.5 - anchor.z;
                        if (ddx * ddx + ddz * ddz > radiusSq) continue;
                        if (!pass.test(next)) continue;
                        if (dx != 0 && dz != 0
                                && (!pass.test(new BlockPos(cur.getX() + dx, next.getY(), cur.getZ()))
                                    || !pass.test(new BlockPos(cur.getX(), next.getY(), cur.getZ() + dz)))) {
                            continue;   // 不切角
                        }
                        if (result.add(next.asLong())) queue.add(next);
                        break;   // 每个方向只用最上面那个能走的层
                    }
                }
            }
        }
        return result;
    }

    /** 按"到命令点（锚点）的距离"把可达落点排好，供兜底逐格取用。 */
    private static List<Long> sortByDistance(Set<Long> spots, Vec3 anchor) {
        List<Long> ordered = new ArrayList<>(spots);
        ordered.sort(Comparator.comparingDouble(k -> {
            double dx = BlockPos.getX(k) + 0.5 - anchor.x;
            double dz = BlockPos.getZ(k) + 0.5 - anchor.z;
            double dy = BlockPos.getY(k) - anchor.y;
            return dx * dx + dz * dz + dy * dy * 4.0;
        }));
        return ordered;
    }

    /**
     * 骑乘起步格：从给定位置上下 / 周围一圈找最近一处"坐骑能站 / 能通过"的格子（找不到返回 null）。
     *   骑乘单位常被挤在墙边 / 台阶边，只在原地上下找会找不到起点。
     */
    @Nullable
    private static BlockPos findMountSeed(double x, double y, double z,
                                          Predicate<BlockPos> walkable) {
        int sx = Mth.floor(x);
        int sy = Mth.floor(y);
        int sz = Mth.floor(z);
        for (int r = 0; r <= 3; r++) {
            BlockPos best = null;
            int bestDy = Integer.MAX_VALUE;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : new int[]{0, -1, 1, -2, 2, -3}) {
                        BlockPos p = new BlockPos(sx + dx, sy + dy, sz + dz);
                        if (!walkable.test(p)) continue;
                        if (Math.abs(dy) < bestDy) {
                            bestDy = Math.abs(dy);
                            best = p;
                        }
                        break;
                    }
                }
            }
            if (best != null) return best;
        }
        return null;
    }

    /** 各方向尝试的高度层：先同层，再上 1 格（台阶），最后往下 1~3 格。 */
    private static final int[] STEP_DYS = {0, 1, -1, -2, -3};

    /** 从给定位置向下找一个"能走过去"的起步格（最多找 48 格）；找不到返回 null。 */
    @Nullable
    private static BlockPos findWalkableSeed(Level level, double x, double y, double z) {
        int sx = Mth.floor(x);
        int sy = Mth.floor(y);
        int sz = Mth.floor(z);
        for (int dy = 0; dy >= -48; dy--) {
            BlockPos p = new BlockPos(sx, sy + dy, sz);
            if (walkableSpot(level, p)) return p;
        }
        return null;
    }

    /** 这一格能不能走过去：能站（两格净空 + 地面）、能用手开的门、或可攀爬（梯子 / 藤蔓 / 脚手架）。 */
    private static boolean walkableSpot(Level level, BlockPos pos) {
        if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.CLIMBABLE)
                || level.getBlockState(pos.above()).is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
            return true;
        }
        // 木门 / 栅栏门：单位会自己开门走过去，算可通行
        if (AiNavUtil.isHandOpenable(level.getBlockState(pos))
                || AiNavUtil.isHandOpenable(level.getBlockState(pos.above()))) {
            return true;
        }
        return AiNavUtil.isOpenStandingSpot(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /** 这个落点是否已经被同一次重建里别的士兵占了（水平 1.2 格内视为同一格）。 */
    private static boolean claimedNear(List<Vec3> claimed, double x, double z) {
        for (Vec3 c : claimed) {
            double dx = c.x - x;
            double dz = c.z - z;
            if (dx * dx + dz * dz < 1.44) return true;
        }
        return false;
    }

    private static Vec3 claim(List<Vec3> claimed, double x, double y, double z) {
        Vec3 p = new Vec3(x, y, z);
        claimed.add(p);
        return p;
    }

    /**
     * 站位是否可用：站得住 + 至少两个方向通气 + 从阵型锚点能直视到。
     *   最后一条用来排除"能站、但在墙 / 盒子里面、根本走不到"的位置
     *   （封闭盒子内部、墙后口袋等）—— 否则士兵会围着障碍一直绕、永远不回位。
     */
    private static boolean isUsableSlot(Level level, Vec3 anchor, double x, double y, double z) {
        if (!AiNavUtil.isOpenStandingSpot(level, x, y, z)) return false;
        return AiNavUtil.hasClearLine(level,
                new Vec3(anchor.x, anchor.y + 0.5, anchor.z),
                new Vec3(x, y + 0.5, z));
    }

    /**
     * 骑乘单位：落点必须容得下"马 + 骑手"（宽 1.4 的马站不进贴墙的缝里）。
     *   站不下的落点会被跳过、换到附近站得下的格子，而不是把马指过去顶着墙。
     *   步兵 / 未知坐骑不做额外限制（未知坐骑只做自身尺寸的净空检查）。
     */
    private static boolean moverFits(AbstractArmySoldier soldier, Level level,
                                     double x, double y, double z) {
        if (!(soldier.getVehicle() instanceof Mob mount)) return true;
        if (mount instanceof WarHorseEntity) {
            return AiNavUtil.mountStandFits(level, soldier, x, y, z);
        }
        AABB box = mount.getType().getDimensions().makeBoundingBox(x, y, z);
        return level.noBlockCollision(mount, box);
    }

    // ===================== 地形适配 =====================

    /**
     * 测出当前朝向下阵型横向能站下多少列。
     *   取最左 / 最右两列，在队伍前、中、后三个位置采样，全部能站人才算可行。
     */
    private static int terrainRowLimit(Player player, Vec3 anchor, float yaw, int maxCols) {
        // 只测"锚点自己附近"能不能站人：
        //   采样整个阵型外侧两列时，锚点旁边有墙 / 箱子就会被误判成极窄地形，
        //   整队缩成一条几十格长的纵队，士兵绕着障碍走、不肯归位站定；
        //   个别被方块挡住的站位交给 resolveSlot 逐个修正。
        return anchorAreaOpen(player, anchor, yaw) ? maxCols : 1;
    }

    /** 锚点左右各 1 格多的地方站不站得下人（判断这里是不是真的窄到只能排纵队）。 */
    private static boolean anchorAreaOpen(Player player, Vec3 anchor, float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        float cos = Mth.cos(rad);
        float sin = Mth.sin(rad);
        for (double offX : new double[]{-1.4, 1.4}) {
            double rx = offX * cos;
            double rz = offX * sin;
            double wx = anchor.x + rx;
            double wz = anchor.z + rz;
            double wy = findGroundY(player, wx, anchor.y, wz);
            if (!AiNavUtil.isSafeStandingSpot(player.level(), wx, wy, wz)) return false;
        }
        return true;
    }

    /** 纵队：锚点卡在极窄地形（1~2 格宽）时用；分成两条交错的纵列，避免排成几十格长的一字长蛇。 */
    private static void columnOffsets(List<AbstractArmySoldier> all, List<Vec3> out) {
        double z = -4.0;
        for (int i = 0; i < all.size(); i++) {
            double x = (i % 2 == 0) ? -0.7 : 0.7;
            out.set(i, new Vec3(x, 0, z));
            if (i % 2 == 1) z -= ROW_GAP_INFANTRY;
        }
    }

    // ===================== 线列阵 =====================

    private static void lineOffsets(List<AbstractArmySoldier> all,
                                    Map<AbstractArmySoldier, Integer> idx,
                                    List<Vec3> out, int perRow) {
        List<AbstractArmySoldier> melee = new ArrayList<>();
        List<AbstractArmySoldier> dual = new ArrayList<>();
        List<AbstractArmySoldier> thrower = new ArrayList<>();
        List<AbstractArmySoldier> mage = new ArrayList<>();
        List<AbstractArmySoldier> gunner = new ArrayList<>();
        List<AbstractArmySoldier> archer = new ArrayList<>();

        for (AbstractArmySoldier s : all) {
            SoldierType t = s.getSoldierType();
            if (t.isMage()) mage.add(s);
            else if (t.isThrower()) thrower.add(s);
            else if (t.isDual()) dual.add(s);
            else if (t.isGunner()) gunner.add(s);
            else if (t.isArcher()) archer.add(s);
            else melee.add(s);
        }

        double z = -5.0;
        z = placeRow(melee, idx, out, z, -1, perRow);
        z = placeRow(dual, idx, out, z, -1, perRow);
        z = placeRow(thrower, idx, out, z, -1, perRow);
        z = placeRow(mage, idx, out, z, -1, perRow);
        z = placeRow(gunner, idx, out, z, -1, perRow);
        z = placeRow(archer, idx, out, z, -1, perRow);
    }

    // ===================== 盾墙阵 =====================

    private static void shieldWallOffsets(List<AbstractArmySoldier> all,
                                          Map<AbstractArmySoldier, Integer> idx,
                                          List<Vec3> out, int perRow) {
        List<AbstractArmySoldier> melee = new ArrayList<>();
        List<AbstractArmySoldier> ranged = new ArrayList<>();

        for (AbstractArmySoldier s : all) {
            if (s.getRole().isMelee()) melee.add(s);
            else ranged.add(s);
        }

        double zFront = 7.0;
        placeRow(melee, idx, out, zFront, +1, perRow);

        double zBack = -7.0;
        placeRow(ranged, idx, out, zBack, -1, perRow);
    }

    // ===================== 楔形阵 =====================
    private static void wedgeOffsets(List<AbstractArmySoldier> all,
                                     Map<AbstractArmySoldier, Integer> idx,
                                     List<Vec3> out, int perRow) {
        List<AbstractArmySoldier> melee = new ArrayList<>();
        List<AbstractArmySoldier> ranged = new ArrayList<>();

        for (AbstractArmySoldier s : all) {
            if (s.getRole().isMelee()) melee.add(s);
            else ranged.add(s);
        }

        int cavCount = 0;
        for (AbstractArmySoldier s : melee) {
            if (s.getSoldierType().isCavalry()) cavCount++;
        }
        boolean cavalry = !melee.isEmpty() && cavCount * 2 > melee.size();
        double cellStep = cavalry ? 2.6 : 1.6;
        double rowStep  = cavalry ? 3.2 : 2.0;
        double zStart   = cavalry ? 5.0 : 4.0;

        // 楔形两侧各自最多 8 个 → 超出换到下一段楔形
        final int WEDGE_HALF_WIDTH = 8;
        // 地形适配：窄地形下楔形变窄，多余的兵换到下一段楔形
        int wedgeHalf = Math.max(1,
                Math.min(WEDGE_HALF_WIDTH, Math.max(1, (perRow - 1) / 2)));
        int segSize = wedgeHalf * 2;

        for (int i = 0; i < melee.size(); i++) {
            int seg = i / segSize;
            int within = i % segSize;
            int row = within / 2;
            boolean left = (within % 2 == 0);
            int sign = left ? -1 : 1;
            double x = sign * (1.6 + row * cellStep);
            double z = zStart + seg * (wedgeHalf + 1) * rowStep + row * rowStep;
            out.set(idx.get(melee.get(i)), new Vec3(x, 0, z));
        }

        double zBack = -6.0;
        placeRow(ranged, idx, out, zBack, -1, perRow);
    }

    // ===================== 散兵阵 =====================
    private static void skirmishOffsets(List<AbstractArmySoldier> all,
                                        List<Vec3> out, int perRow) {
        // 散兵用多行网格（交错列偏移保留散布感）
        final int COLS = Math.max(1, Math.min(10, perRow));
        final double CELL = 2.4;
        final double ROW_GAP = 2.6;
        for (int i = 0; i < all.size(); i++) {
            int row = i / COLS;
            int col = i % COLS;
            double x = (col - (COLS - 1) / 2.0) * CELL;
            if (row % 2 == 1) x += CELL * 0.5;
            double z = -5.0 - row * ROW_GAP;
            out.set(i, new Vec3(x, 0, z));
        }
    }
    // ===================== 通用铺排 =====================

    private static double placeRow(List<AbstractArmySoldier> group,
                                   Map<AbstractArmySoldier, Integer> idx,
                                   List<Vec3> out,
                                   double startZ, int direction, int perRow) {
        if (group.isEmpty()) return startZ;

        int i = 0;
        int rowLimit = Math.max(1, Math.min(PER_ROW, perRow));
        double z = startZ;
        while (i < group.size()) {
            int n = Math.min(group.size() - i, rowLimit);
            // 行内只要有骑乘单位，这一排就按骑兵间距铺开：
        //   马的表宽 1.4 格，和步兵挤在 1.4 格的格子里会互相顶，
        //   双方都被反复挤出"到位"判定圈 → 一群人在原地来回挪动。
            boolean rowCavalry = false;
            for (int k = 0; k < n; k++) {
                AbstractArmySoldier s = group.get(i + k);
                if (s.getSoldierType().isCavalry() || s.isPassenger()) {
                    rowCavalry = true;
                    break;
                }
            }
            double cell = rowCavalry ? CELL_CAVALRY : CELL_INFANTRY;
            for (int k = 0; k < n; k++) {
                double x = (k - (n - 1) / 2.0) * cell;
                out.set(idx.get(group.get(i + k)), new Vec3(x, 0, z));
            }
            i += n;
            z += direction * (rowCavalry ? ROW_GAP_CAVALRY : ROW_GAP_INFANTRY);
        }
        return z;
    }

    private static double findGroundY(Player player, double x, double baseY, double z) {
        int bx = Mth.floor(x);
        int by = Mth.floor(baseY);
        int bz = Mth.floor(z);
        for (int y = by + 2; y >= by - 5; y--) {
            BlockPos p = new BlockPos(bx, y, bz);
            if (player.level().getBlockState(p).isAir()
                    && player.level().getBlockState(p.below()).isSolid()) {
                return y;
            }
        }
        return baseY;
    }
}
