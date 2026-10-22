package com.ohhapple.monster_conquest.path;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 让设过巡逻路径的单位按顺序走点（服务端每 tick 检查一次）。
 * 复用现有的"手动移动 / 手动行军"通道，不自己碰导航：
 *   · 士兵 → setManualMoveTarget（SoldierManualMoveGoal 负责寻路，速度仍是实体自身速度）；
 *   · 首领 → setManualMarchTarget（麾下士兵跟行军轨迹，整支军队一起走）。
 * 有战斗目标时先让位给战斗 AI，打完再继续走；路线模式走完最后一个点就撤掉路线、恢复自由活动。
 */
public final class PathRouteFollower {

    /** "还是同一个点"的判定（格²）：比对下发记录与路线当前点，0.1 格以内算同一点。 */
    private static final double SAME_POINT_SQR = 0.01;
    /** 每个实体上次读取的路线对象与版本；路线编辑后必须释放旧的手动目标。 */
    private static final Map<Entity, ObservedRoute> OBSERVED_ROUTES = new WeakHashMap<>();

    private record ObservedRoute(PathRoute route, long revision) {}

    private PathRouteFollower() {}

    public static void tick(Entity entity) {
        if (!(entity instanceof AbstractArmySoldier) && !(entity instanceof AbstractArmyCommander)) return;
        if (!(entity.level() instanceof ServerLevel level)) return;
        PathRoutes routes = PathRoutes.get(level);
        boolean routeChanged = routes.refreshSpawnerRoute(entity);
        if (entity instanceof AbstractArmySoldier soldier) {
            tickSoldier(routes, soldier, routeChanged);
        } else if (entity instanceof AbstractArmyCommander commander) {
            tickCommander(routes, commander, routeChanged);
        }
    }

    private static void tickSoldier(PathRoutes routes, AbstractArmySoldier soldier,
                                    boolean routeChanged) {
        String key = routes.effectiveKey(soldier);
        PathRoute route = routes.route(key);
        routeChanged |= noteRouteChange(soldier, route);
        if (routeChanged) {
            soldier.cancelManualMoveOrder();
            soldier.setPathIssuedPoint(null);
        }
        if (route == null) return;

        // 玩家刚下达的"前往"命令优先于持久巡逻路线；否则路径点工具
        //   每 tick 重新写入的旧目标会把新的移动 Goal 挡住。
        //   这里只让路，不清 manualMoveTarget：路径点工具自己的当前目标
        //   也走同一个字段，先清再判断会把巡逻单位的目标每 tick 抹掉。
        if (soldier.isPlayerMoveOrderUnderway()) {
            soldier.setPathIssuedPoint(null);
            return;
        }

        Vec3 point = route.point();
        if (point == null) {
            routes.clear(key);
            soldier.cancelManualMoveOrder();
            soldier.setPathIssuedPoint(null);
            return;
        }

        // 控制工具的直接移动目标与巡逻点不同 → 这是玩家刚下的命令，
        //   巡逻让路，不能因为旁边有敌人就把 direct target 清掉。
        Vec3 manual = soldier.getManualMoveTarget();
        if (manual != null && manual.distanceToSqr(point) > 1.0) return;

        // 有敌人：先打完（撤掉手动目标，让战斗 AI 接管），战斗结束再继续巡逻
        LivingEntity target = soldier.getTarget();
        if (target != null && target.isAlive()) {
            // 玩家点名攻击是明确命令，可以越过巡逻范围；普通仇恨 / 自主索敌
            //   只能追到路线附近，避免整支巡逻队被一个逃跑目标带到天涯海角。
            if (soldier.isPatrolTargetAllowed(target)) {
                soldier.cancelManualMoveOrder();
                soldier.setPathIssuedPoint(null);
                return;
            }
            if (GeneralConfig.debugMovementLog) {
                Monster_conquest.LOGGER.info(
                        "[PATROL-LEASH] t={} unit={} target={} dist={} limit={}",
                        soldier.tickCount, soldier.getUUID().toString().substring(0, 8),
                        target.getUUID().toString().substring(0, 8),
                        String.format(Locale.ROOT, "%.1f",
                                PathRoutes.distanceToRoute(route, target.position())),
                        String.format(Locale.ROOT, "%.1f",
                                GeneralConfig
                                        .patrolLeashRadius));
            }
            soldier.setTarget(null);
            soldier.setLastHurtByMob(null);
            soldier.cancelManualMoveOrder();
            soldier.setPathIssuedPoint(null);
            // 继续往下走：立即把单位重新拉回当前巡逻点。
        }

        // 这个点已经下发过了：手动移动 Goal 清掉目标就代表"走到 / 判到位"→ 换下一个点
        Vec3 issued = soldier.getPathIssuedPoint();
        if (issued != null && issued.distanceToSqr(point) <= SAME_POINT_SQR) {
            if (soldier.getManualMoveTarget() != null) return;
            // 手动目标也可能被战斗 / 最近受击保护清掉，不能把这种情况当成"到达当前点"。
            //   没真正走到点上就继续下发同一个点，避免直接推进到下一个巡逻点。
            if (!isAtIssuedPoint(soldier, point)) {
                soldier.setManualMoveTarget(point);
                return;
            }
            if (route.advance()) {
                Vec3 next = route.point();
                // 单点巡逻路线推进后又回到同一个点 → 原地待命：
                //   继续重复下发会和到达判定形成"到达 → 再下发 → 立刻到达"的循环，
                //   单位被每 tick 清零速度、卡在点旁边抖动。
                if (next != null && next.distanceToSqr(point) > SAME_POINT_SQR) {
                    soldier.setPathIssuedPoint(next);
                    soldier.setManualMoveTarget(next);
                }
            } else {
                routes.clear(key);
                soldier.cancelManualMoveOrder();
                soldier.setPathIssuedPoint(null);
            }
            return;
        }

        soldier.setPathIssuedPoint(point);
        soldier.setManualMoveTarget(point);
    }

    private static void tickCommander(PathRoutes routes, AbstractArmyCommander commander,
                                      boolean routeChanged) {
        String key = routes.effectiveKey(commander);
        PathRoute route = routes.route(key);
        routeChanged |= noteRouteChange(commander, route);
        if (routeChanged) {
            commander.setManualMarchTarget(null);
            commander.setPathIssuedPoint(null);
        }
        if (route == null) return;

        Vec3 point = route.point();
        Vec3 manual = commander.getManualMarchTarget();
        if (point == null) {
            routes.clear(key);
            if (manual == null) {
                commander.setPathIssuedPoint(null);
            }
            return;
        }
        // 控制工具的直接行军目标与巡逻点不同 → 让路给玩家命令。
        if (manual != null && manual.distanceToSqr(point) > 1.0) return;

        LivingEntity target = commander.getTarget();
        if (target != null && target.isAlive()) {
            if (commander.isPatrolTargetAllowed(target)) {
                commander.setManualMarchTarget(null);
                commander.setPathIssuedPoint(null);
                return;
            }
            if (GeneralConfig.debugMovementLog) {
                Monster_conquest.LOGGER.info(
                        "[PATROL-LEASH] t={} commander={} target={} dist={} limit={}",
                        commander.tickCount,
                        commander.getUUID().toString().substring(0, 8),
                        target.getUUID().toString().substring(0, 8),
                        String.format(Locale.ROOT, "%.1f",
                                PathRoutes.distanceToRoute(route, target.position())),
                        String.format(Locale.ROOT, "%.1f",
                                GeneralConfig
                                        .patrolLeashRadius));
            }
            commander.setTarget(null);
            commander.setLastHurtByMob(null);
            commander.setManualMarchTarget(null);
            commander.setPathIssuedPoint(null);
        }

        Vec3 issued = commander.getPathIssuedPoint();
        if (issued != null && issued.distanceToSqr(point) <= SAME_POINT_SQR) {
            if (commander.getManualMarchTarget() != null) return;
            // 与士兵分支一致：手动行军目标被战斗清理 ≠ 已到达当前巡逻点。
            if (!isAtIssuedPoint(commander, point)) {
                commander.setManualMarchTarget(point);
                return;
            }
            if (route.advance()) {
                Vec3 next = route.point();
                // 单点巡逻路线推进后又回到同一个点 → 原地待命（见士兵分支的同名说明）
                if (next != null && next.distanceToSqr(point) > SAME_POINT_SQR) {
                    commander.setPathIssuedPoint(next);
                    commander.setManualMarchTarget(next);
                }
            } else {
                routes.clear(key);
                commander.setManualMarchTarget(null);
                commander.setPathIssuedPoint(null);
            }
            return;
        }

        commander.setPathIssuedPoint(point);
        commander.setManualMarchTarget(point);
    }

    private static boolean noteRouteChange(Entity entity, @Nullable PathRoute route) {
        ObservedRoute previous = OBSERVED_ROUTES.get(entity);
        if (route == null) {
            OBSERVED_ROUTES.remove(entity);
            return previous != null;
        }

        if (previous == null) {
            OBSERVED_ROUTES.put(entity, new ObservedRoute(route, route.revision()));
            return false;
        }
        if (previous.route() == route && previous.revision() == route.revision()) {
            return false;
        }
        OBSERVED_ROUTES.put(entity, new ObservedRoute(route, route.revision()));
        return true;
    }

    /** 只有真正站在当前巡逻点附近，才允许推进路线光标。 */
    private static boolean isAtIssuedPoint(Entity entity, Vec3 point) {
        double dx = entity.getX() - point.x;
        double dz = entity.getZ() - point.z;
        return dx * dx + dz * dz <= 2.25
                && Math.abs(entity.getY() - point.y) <= 1.5;
    }
}
