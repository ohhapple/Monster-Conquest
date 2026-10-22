package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;

import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 不改变单位当前移动状态的可达性探测。
 *   PathNavigation.createPath() 会更新它自己的目标格、到达距离和卡住计时；
 *   直接拿正在使用的导航查询会把“探测”和“当前路线”混在一起。这里为每个
 *   移动实体保留一个只在探测时使用的导航实例，并缓存短时间内的探测结果。
 */
public final class PathProbe {

    private static final int RESULT_TICKS = 12;
    private static final int DEFAULT_DISTANCE = 64;
    private static final int MAX_DISTANCE = 128;
    private static final Map<Mob, ProbeState> STATES = new WeakHashMap<>();

    private PathProbe() {}

    public static boolean canReach(Mob mover, BlockPos target) {
        return canReach(mover, target, DEFAULT_DISTANCE);
    }

    public static boolean canReach(Mob mover, BlockPos target, int maxDistance) {
        if (mover == null || target == null || mover.isRemoved() || !mover.isAlive()) return false;
        int distance = Math.max(8, Math.min(MAX_DISTANCE, maxDistance));
        long now = mover.level().getGameTime();
        long version = RoutePlanner.worldVersion(mover.level());
        ProbeKey key = new ProbeKey(mover.blockPosition().asLong(), target.asLong(), distance, version);

        ProbeState state = STATES.get(mover);
        if (state != null && key.equals(state.key) && now <= state.until) {
            return state.result;
        }

        PathNavigation navigation = state == null ? null : state.navigation.get();
        if (navigation == null || state.level != mover.level()) {
            navigation = createNavigation(mover);
            state = new ProbeState(mover.level(), navigation);
            STATES.put(mover, state);
        }
        copySettings(mover.getNavigation(), navigation);

        Path path;
        try {
            path = navigation.createPath(target, 1, distance);
        } catch (RuntimeException ignored) {
            path = null;
        }

        boolean result = path != null && path.canReach();
        state.key = key;
        state.result = result;
        state.until = now + RESULT_TICKS;
        return result;
    }

    public static void clearCaches() {
        STATES.clear();
    }

    private static PathNavigation createNavigation(Mob mover) {
        Level level = mover.level();
        if (mover instanceof AbstractArmySoldier
                || mover instanceof AbstractArmyCommander) {
            return new ClimbPathNavigation(mover, level);
        }
        return new GroundPathNavigation(mover, level);
    }

    private static void copySettings(@Nullable PathNavigation source, PathNavigation target) {
        if (source == null) return;
        target.setCanFloat(source.canFloat());
        NodeEvaluator sourceEvaluator = source.getNodeEvaluator();
        NodeEvaluator targetEvaluator = target.getNodeEvaluator();
        if (sourceEvaluator != null && targetEvaluator != null) {
            targetEvaluator.setCanPassDoors(sourceEvaluator.canPassDoors());
            targetEvaluator.setCanOpenDoors(sourceEvaluator.canOpenDoors());
            targetEvaluator.setCanWalkOverFences(sourceEvaluator.canWalkOverFences());
        }
    }

    private record ProbeKey(long start, long target, int distance, long worldVersion) {}

    private static final class ProbeState {
        private final Level level;
        private final WeakReference<PathNavigation> navigation;
        private ProbeKey key;
        private boolean result;
        private long until;

        private ProbeState(Level level, PathNavigation navigation) {
            this.level = level;
            this.navigation = new WeakReference<>(navigation);
            this.key = new ProbeKey(0L, 0L, 0, Long.MIN_VALUE);
        }
    }
}
