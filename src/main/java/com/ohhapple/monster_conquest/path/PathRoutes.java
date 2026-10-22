package com.ohhapple.monster_conquest.path;

import com.ohhapple.monster_conquest.block.SpawnPointBlock;
import com.ohhapple.monster_conquest.block.SpawnPointBlockEntity;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 路径点工具设置的巡逻路线，按维度分别存在存档里（不写进方块 / 生物物品）：
 *   · unit:&lt;uuid&gt;              单个单位
 *   · squad:&lt;玩家&gt;:&lt;小队号&gt;     玩家小编队
 *   · army:&lt;首领 uuid&gt;         NPC 军队 / 巡逻队（走的是首领，士兵跟行军轨迹）
 *   · spawner:&lt;方块坐标&gt;       刷怪点（单位保留独立游标，源路线修改后动态同步）
 * 玩家当前选中的目标（中键选择）是临时状态，不存档。
 */
public final class PathRoutes extends SavedData {

    private static final String DATA_NAME = "monster_conquest_path_routes";

    /** 每个玩家当前选中的目标：存储 key + 界面显示名。 */
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    /**
     * 中键选中的目标。
     *
     * @param key   存储 key（unit: / squad: / army: / spawner:）
     * @param label 界面显示名
     * @param kind  类型（0 单位 / 1 军队 / 2 小编队 / 3 刷怪点，HUD 显示用）
     * @param squad 小编队号（kind = 2 时有意义）
     * @param pos   刷怪点坐标打包（kind = 3 时有意义）
     * @param owner 归属玩家（玩家阵营的单位 / 小编队才有；NPC 与刷怪点为 null）
     */
    public record Selection(String key, Component label, int kind, int squad, long pos,
                            @Nullable UUID owner) {
        public Selection(String key, Component label) {
            this(key, label, 0, 0, 0L, null);
        }
    }

    private final Map<String, PathRoute> routes = new HashMap<>();
    /** 刷怪点路线版本；用于让已生成单位识别源路线变化。 */
    private final Map<String, Long> routeRevisions = new HashMap<>();
    /** 刷怪点生成单位 → 源刷怪点路线及其已同步版本。 */
    private final Map<UUID, SpawnerBinding> spawnerBindings = new HashMap<>();
    /** 清理候选连续命中的轮数；避免区块刚卸载 / 单位短暂不可见时误删路线。 */
    private final Map<String, Integer> orphanPasses = new HashMap<>();
    private long lastCleanupGameTime = Long.MIN_VALUE;

    private record SpawnerBinding(String spawnerKey, long revision) {}

    // ===================== 存档 =====================

    public static PathRoutes get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PathRoutes::new, PathRoutes::load), DATA_NAME);
    }

    private static PathRoutes load(CompoundTag tag, HolderLookup.Provider registries) {
        PathRoutes data = new PathRoutes();
        ListTag list = tag.getList("Routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            PathRoute route = new PathRoute();
            try {
                route.setMode(PathMode.valueOf(entry.getString("Mode")));
            } catch (Exception ignored) {
                route.setMode(PathMode.PATROL);
            }
            ListTag points = entry.getList("Points", Tag.TAG_COMPOUND);
            for (int p = 0; p < points.size(); p++) {
                CompoundTag point = points.getCompound(p);
                route.add(new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z")));
            }
            if (route.size() > 0) data.routes.put(entry.getString("Key"), route);
        }
        ListTag revisions = tag.getList("RouteRevisions", Tag.TAG_COMPOUND);
        for (int i = 0; i < revisions.size(); i++) {
            CompoundTag entry = revisions.getCompound(i);
            String key = entry.getString("Key");
            if (!key.isEmpty()) data.routeRevisions.put(key, entry.getLong("Revision"));
        }
        ListTag bindings = tag.getList("SpawnerBindings", Tag.TAG_COMPOUND);
        for (int i = 0; i < bindings.size(); i++) {
            CompoundTag entry = bindings.getCompound(i);
            try {
                UUID unit = NbtUtils.loadUUID(entry.get("Unit"));
                String spawnerKey = entry.getString("Spawner");
                if (!spawnerKey.isEmpty()) {
                    data.spawnerBindings.put(unit,
                            new SpawnerBinding(spawnerKey, entry.getLong("Revision")));
                }
            } catch (RuntimeException ignored) {
                // 损坏的绑定不应阻止其它路线载入。
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, PathRoute> e : routes.entrySet()) {
            PathRoute route = e.getValue();
            if (route.size() == 0) continue;
            CompoundTag entry = new CompoundTag();
            entry.putString("Key", e.getKey());
            entry.putString("Mode", route.getMode().name());
            ListTag points = new ListTag();
            for (Vec3 p : route.getPoints()) {
                CompoundTag point = new CompoundTag();
                point.putDouble("x", p.x);
                point.putDouble("y", p.y);
                point.putDouble("z", p.z);
                points.add(point);
            }
            entry.put("Points", points);
            list.add(entry);
        }
        tag.put("Routes", list);

        ListTag revisions = new ListTag();
        for (Map.Entry<String, Long> entry : routeRevisions.entrySet()) {
            CompoundTag revision = new CompoundTag();
            revision.putString("Key", entry.getKey());
            revision.putLong("Revision", entry.getValue());
            revisions.add(revision);
        }
        tag.put("RouteRevisions", revisions);

        ListTag bindings = new ListTag();
        for (Map.Entry<UUID, SpawnerBinding> entry : spawnerBindings.entrySet()) {
            CompoundTag binding = new CompoundTag();
            binding.put("Unit", NbtUtils.createUUID(entry.getKey()));
            binding.putString("Spawner", entry.getValue().spawnerKey());
            binding.putLong("Revision", entry.getValue().revision());
            bindings.add(binding);
        }
        tag.put("SpawnerBindings", bindings);
        return tag;
    }

    // ===================== key =====================

    public static String unitKey(UUID unit) { return "unit:" + unit; }

    public static String squadKey(UUID player, int squad) { return "squad:" + player + ":" + squad; }

    public static String armyKey(UUID commander) { return "army:" + commander; }

    public static String spawnerKey(BlockPos pos) { return "spawner:" + pos.asLong(); }

    // ===================== 读写 =====================

    @Nullable
    public PathRoute route(@Nullable String key) {
        return key == null ? null : routes.get(key);
    }

    /** 追加一个路径点；没有这条路线就顺手建一条。 */
    public PathRoute addPoint(String key, PathMode mode, Vec3 point) {
        PathRoute route = routes.computeIfAbsent(key, k -> new PathRoute());
        orphanPasses.remove(key);
        // 重新开始画点：模式按当下工具的模式走，光标回到开头
        if (route.size() == 0) route.setMode(mode);
        route.add(point);
        markRouteChanged(key);
        return route;
    }

    /** 覆盖一条路线（刷怪点单位同步源路线时使用）。 */
    public void setRoute(String key, PathMode mode, List<Vec3> points) {
        if (points.isEmpty()) {
            clear(key);
            return;
        }
        PathRoute route = routes.computeIfAbsent(key, k -> new PathRoute());
        orphanPasses.remove(key);
        route.setMode(mode);
        route.clearPoints();
        for (Vec3 point : points) route.add(point);
        markRouteChanged(key);
    }

    /** 已存在的选中路线立即切换巡逻 / 单次路线模式。 */
    public boolean setRouteMode(@Nullable String key, PathMode mode) {
        PathRoute route = route(key);
        if (route == null || route.getMode() == mode) return false;
        route.setMode(mode);
        markRouteChanged(key);
        return true;
    }

    public void clear(@Nullable String key) {
        if (key == null) return;
        orphanPasses.remove(key);
        boolean removed = routes.remove(key) != null;
        if (removed || key.startsWith("spawner:")) {
            bumpRouteRevision(key);
            setDirty();
        }
    }

    private void markRouteChanged(String key) {
        bumpRouteRevision(key);
        setDirty();
    }

    private void bumpRouteRevision(String key) {
        if (key.startsWith("spawner:")) {
            routeRevisions.merge(key, 1L, Long::sum);
        }
    }

    /** 记住单位来自哪个刷怪点；首次同步使用 MIN_VALUE 强制复制当前路线（没有路线则保持为空）。 */
    public void bindSpawnerUnit(UUID unit, BlockPos spawnerPos) {
        String spawnerKey = spawnerKey(spawnerPos);
        SpawnerBinding current = spawnerBindings.get(unit);
        if (current != null && current.spawnerKey().equals(spawnerKey)) return;
        spawnerBindings.put(unit, new SpawnerBinding(spawnerKey, Long.MIN_VALUE));
        setDirty();
    }

    public void unbindSpawnerUnit(UUID unit) {
        if (spawnerBindings.remove(unit) != null) setDirty();
    }

    /**
     * 源刷怪点路线变化后刷新该单位的独立路线副本。
     * 每单位仍保留自己的巡逻游标；源路线被清空时同时撤销单位副本。
     *
     * @return 是否实际替换或清除了单位路线
     */
    public boolean refreshSpawnerRoute(Entity entity) {
        SpawnerBinding binding = spawnerBindings.get(entity.getUUID());
        if (binding == null) return false;

        long revision = routeRevisions.getOrDefault(binding.spawnerKey(), 0L);
        if (binding.revision() == revision) return false;

        PathRoute source = route(binding.spawnerKey());
        String unitKey = unitKey(entity.getUUID());
        boolean changed;
        if (source == null) {
            changed = routes.remove(unitKey) != null;
            orphanPasses.remove(unitKey);
        } else {
            setRoute(unitKey, source.getMode(), List.copyOf(source.getPoints()));
            changed = true;
        }
        spawnerBindings.put(entity.getUUID(), new SpawnerBinding(binding.spawnerKey(), revision));
        setDirty();
        return changed;
    }

    /**
     * 清理已经不可能再被使用的路线。
     *   只看已经加载的区块，宁可晚一点清也不误删卸载区块里的单位路线；
     *   连续两轮确认无主才删除，避免区块边界 / 单位短暂离开加载范围造成误删。
     */
    public void cleanup(ServerLevel level, long nowGT) {
        if (nowGT == lastCleanupGameTime) return;
        lastCleanupGameTime = nowGT;

        boolean changed = false;
        Iterator<Map.Entry<String, PathRoute>> iterator = routes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PathRoute> entry = iterator.next();
            String key = entry.getKey();
            PathRoute route = entry.getValue();
            if (route == null || route.size() == 0) {
                iterator.remove();
                orphanPasses.remove(key);
                bumpRouteRevision(key);
                changed = true;
                continue;
            }

            Boolean inUse = routeInUse(level, key, route);
            if (inUse == null || inUse) {
                orphanPasses.remove(key);
                continue;
            }

            int passes = orphanPasses.getOrDefault(key, 0) + 1;
            if (passes >= 2) {
                iterator.remove();
                orphanPasses.remove(key);
                bumpRouteRevision(key);
                changed = true;
                if (GeneralConfig.debugMovementLog) {
                    Monster_conquest.LOGGER.info(
                            "[PATH-CLEANUP] removed={} dimension={}",
                            key, level.dimension().location());
                }
            } else {
                orphanPasses.put(key, passes);
            }
        }
        if (changed) setDirty();
    }

    /**
     * @return true = 仍在使用；false = 已确认无主；null = 区块未加载 / 玩家离线，暂不判断
     */
    @Nullable
    private Boolean routeInUse(ServerLevel level, String key, PathRoute route) {
        try {
            if (key.startsWith("spawner:")) {
                BlockPos pos = BlockPos.of(Long.parseLong(key.substring("spawner:".length())));
                if (!level.isLoaded(pos)) return null;
                return level.getBlockEntity(pos) instanceof SpawnPointBlockEntity
                        && level.getBlockState(pos).getBlock() instanceof SpawnPointBlock;
            }
            if (key.startsWith("unit:")) {
                UUID uuid = UUID.fromString(key.substring("unit:".length()));
                Entity entity = level.getEntities().get(uuid);
                if (entity != null) return !entity.isRemoved() && entity.isAlive();
                return allRouteChunksLoaded(level, route) ? Boolean.FALSE : null;
            }
            if (key.startsWith("army:")) {
                UUID uuid = UUID.fromString(key.substring("army:".length()));
                Entity entity = level.getEntities().get(uuid);
                if (entity != null) return !entity.isRemoved() && entity.isAlive();
                return allRouteChunksLoaded(level, route) ? Boolean.FALSE : null;
            }
            if (key.startsWith("squad:")) {
                String rest = key.substring("squad:".length());
                int split = rest.lastIndexOf(':');
                if (split <= 0) return Boolean.FALSE;
                UUID owner = UUID.fromString(rest.substring(0, split));
                int squadId = Integer.parseInt(rest.substring(split + 1));
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
                if (player == null) return null; // 玩家离线时保留，重新上线后可能继续巡逻
                if (!allRouteChunksLoaded(level, route)) return null;
                AABB area = routeArea(route);
                return !level.getEntitiesOfClass(AbstractArmySoldier.class, area,
                        s -> s.isAlive() && !s.isRemoved()
                                && owner.equals(s.getCommanderUuid().orElse(null))
                                && s.getSquadId() == squadId).isEmpty();
            }
        } catch (RuntimeException ignored) {
            // 损坏 / 旧版本 key：按无主处理，避免永久占着 SavedData
            return Boolean.FALSE;
        }
        return Boolean.FALSE;
    }

    private static boolean allRouteChunksLoaded(ServerLevel level, PathRoute route) {
        if (route.size() == 0) return true;
        for (Vec3 point : route.getPoints()) {
            if (!level.isLoaded(BlockPos.containing(point))) return false;
        }
        return true;
    }

    private static AABB routeArea(PathRoute route) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Vec3 point : route.getPoints()) {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
            maxZ = Math.max(maxZ, point.z);
        }
        double range = Math.max(32.0, GeneralConfig.armyFollowRange);
        return new AABB(minX - range, minY - 64.0, minZ - range,
                maxX + range, maxY + 64.0, maxZ + range);
    }

    /** 目标点是否仍在巡逻路线允许的活动范围内。 */
    public static boolean isWithinLeash(@Nullable PathRoute route, Vec3 target) {
        if (route == null || route.size() == 0) return true;
        return distanceToRoute(route, target) <= Math.max(1.0, GeneralConfig.patrolLeashRadius);
    }

    /** 点到巡逻折线的最短三维距离；巡逻模式包含首尾闭合段。 */
    public static double distanceToRoute(PathRoute route, Vec3 target) {
        if (route == null || route.size() == 0) return Double.MAX_VALUE;
        List<Vec3> points = route.getPoints();
        if (points.size() == 1) return target.distanceTo(points.get(0));
        double best = Double.MAX_VALUE;
        for (int i = 0; i + 1 < points.size(); i++) {
            best = Math.min(best, distanceToSegment(target, points.get(i), points.get(i + 1)));
        }
        if (route.getMode() == PathMode.PATROL && points.size() > 2) {
            best = Math.min(best,
                    distanceToSegment(target, points.get(points.size() - 1), points.get(0)));
        }
        return best;
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double lengthSqr = ab.lengthSqr();
        if (lengthSqr < 1.0E-6) return point.distanceTo(a);
        double t = point.subtract(a).dot(ab) / lengthSqr;
        t = Math.max(0.0, Math.min(1.0, t));
        return point.distanceTo(a.add(ab.scale(t)));
    }

    public int pointCount(@Nullable String key) {
        PathRoute route = route(key);
        return route == null ? 0 : route.size();
    }

    /**
     * 该士兵当前生效的路径 key：自己的 → 所在玩家小编队 → 所在 NPC 军队；
     *   都没有返回 null。
     */
    @Nullable
    public String effectiveKey(AbstractArmySoldier soldier) {
        String own = unitKey(soldier.getUUID());
        if (routes.containsKey(own)) return own;
        UUID commander = soldier.getCommanderUuid().orElse(null);
        if (commander == null) return null;
        if (soldier.isPlayerArmy()) {
            int squad = soldier.getSquadId();
            if (squad > 0) {
                String squadKey = squadKey(commander, squad);
                if (routes.containsKey(squadKey)) return squadKey;
            }
            return null;
        }
        String army = armyKey(commander);
        return routes.containsKey(army) ? army : null;
    }

    /** 首领自己生效的路径 key：自己的 → 自己带的军队。 */
    @Nullable
    public String effectiveKey(AbstractArmyCommander commander) {
        String own = unitKey(commander.getUUID());
        if (routes.containsKey(own)) return own;
        String army = armyKey(commander.getUUID());
        return routes.containsKey(army) ? army : null;
    }

    // ===================== 玩家选中 =====================

    public static void setSelection(UUID player, @Nullable Selection selection) {
        if (selection == null) SELECTIONS.remove(player);
        else SELECTIONS.put(player, selection);
    }

    @Nullable
    public static Selection selectionOf(UUID player) {
        return SELECTIONS.get(player);
    }
}
