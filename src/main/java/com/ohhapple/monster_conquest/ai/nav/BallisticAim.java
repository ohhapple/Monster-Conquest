package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * 抛射物弹道规划：
 *  - 目标：命中移动中的目标（迭代预判）
 *  - 同时保证飞行路径上不被方块或友军阻挡
 *  - 通过枚举不同速度系数（越低 → 抛物线越高），找到第一条畅通的弹道
 * 物理常量与 Arrow / ThrownTrident 一致：
 *   水平：vx *= 0.99
 *   垂直：vy = vy * 0.99 - 0.05
 */
public final class BallisticAim {

    private BallisticAim() {}

    /** 抛射物碰撞盒半宽（用于逐子步方块检查）。 */
    private static final double PROJECTILE_HALF = 0.2;

    private static final double DRAG = 0.99;
    private static final double GRAVITY = 0.05;

    private static final double MIN_PITCH_RAD   = -85.0 * Math.PI / 180.0;
    private static final double MAX_PITCH_RAD   =  85.0 * Math.PI / 180.0;
    /**
     * 45° 分界：
     *   [-85°, 45°] —— yAtTarget 关于 pitch 单调递增（低弧，飞行时间短）
     *   [45°, 85°]  —— yAtTarget 关于 pitch 单调递减（高弧，用于越障）
     * 单调性在 45° 处换向，整段二分不成立，需要分段二分。
     */
    private static final double APEX_PITCH_RAD  =  45.0 * Math.PI / 180.0;

    /** 依次尝试的速度系数：越低 → 抛物线越高 → 越容易越过前方障碍。 */
    private static final double[] SPEED_FACTORS = { 1.4, 1.2, 1.0, 0.85, 0.7, 0.55, 0.42, 0.32 };

    private static final int    MAX_FLIGHT_TICKS    = 600;
    private static final double HIT_REACH_SQ        = 1.6 * 1.6;
    private static final double ENTITY_CHECK_RADIUS = 0.3;
    private static final int    PREDICT_ITERATIONS  = 5;

    /** 解出的弹道：方向单位向量 + 初速 + 预计飞行 tick 数。 */
    public record Solution(double dirX, double dirY, double dirZ,
                           double speed, double flightTicks) {}

    @Nullable
    public static Solution solve(Level level,
                                 LivingEntity shooter,
                                 LivingEntity target,
                                 double baseSpeed,
                                 Predicate<Entity> isFriendly) {
        Vec3 vel = target.getDeltaMovement();

        // ============================================================
        // 1) 先求一个"瞄准当前位置"的弹道作为兜底。
        //    如果弹道被方块 / 友军完全挡住 → 直接返回 null，不进入迭代。
        // ============================================================
        Vec3 aimAtCurrent = target.position();
        Solution fallback = solveInternal(level, shooter, target, aimAtCurrent,
                baseSpeed, isFriendly);
        if (fallback == null) return null;

        // 目标静止 → 不需要预判
        if (vel.lengthSqr() < 0.0001) return fallback;

        // ============================================================
        // 2) 迭代预判：每次用上一次成功的 flightTicks 推算新瞄准点。
        //    每次迭代只用当前最新的 best，不累积 aimAt，避免误差叠加；
        //    5 次迭代对快速移动目标收敛更好。
        // ============================================================
        Solution best = fallback;

        for (int iter = 0; iter < PREDICT_ITERATIONS; iter++) {
            // 用当前 best 的 flightTicks 推算目标未来位置
            Vec3 next = target.position().add(vel.scale(best.flightTicks()));

            // 预测点几乎没变 → 已收敛，提前退出
            if (next.distanceToSqr(target.position()) < 0.04) break;

            Solution attempt = solveInternal(level, shooter, target, next,
                    baseSpeed, isFriendly);

            // 预判点不可达时保留上一次成功的解，不退回"瞄准当前位置"
            if (attempt == null) break;

            best = attempt;
        }

        return best;
    }

    @Nullable
    private static Solution solveInternal(Level level,
                                          LivingEntity shooter,
                                          LivingEntity target,
                                          Vec3 aimAt,
                                          double baseSpeed,
                                          Predicate<Entity> isFriendly) {
        double sx = shooter.getX();
        double sy = shooter.getEyeY() - 0.1;
        double sz = shooter.getZ();

        double tx = aimAt.x;
        double ty = aimAt.y + target.getEyeHeight();
        double tz = aimAt.z;

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        if (horiz < 0.001) {
            double dirY = dy >= 0 ? 1.0 : -1.0;
            Solution s = new Solution(0, dirY, 0, baseSpeed, 1.0);
            return pathClear(level, shooter, aimAt, target, s, isFriendly) ? s : null;
        }

        double dirX = dx / horiz;
        double dirZ = dz / horiz;

        for (double factor : SPEED_FACTORS) {
            double speed = baseSpeed * factor;

            double[] pt = solvePitchAndTime(dy, horiz, speed);
            if (pt == null) continue;
            double pitch = pt[0];
            double flightTicks = pt[1];

            double cosP = Math.cos(pitch);
            Solution s = new Solution(dirX * cosP, Math.sin(pitch), dirZ * cosP, speed, flightTicks);

            if (pathClear(level, shooter, aimAt, target, s, isFriendly)) {
                return s;
            }
        }
        return null;
    }

    /**
     * 解出能命中目标的 pitch（弧度）和预计飞行 tick 数。
     *
     * yAtTarget 在 [-85°, 45°] 递增、[45°, 85°] 递减，因此分两段二分：
     * 低弧优先（飞行时间短），低弧无解再退到高弧。
     */
    @Nullable
    private static double[] solvePitchAndTime(double dy, double horiz, double speed) {
        double[] low  = binarySearchPitch(MIN_PITCH_RAD, APEX_PITCH_RAD, true,  dy, horiz, speed);
        double[] high = binarySearchPitch(APEX_PITCH_RAD, MAX_PITCH_RAD, false, dy, horiz, speed);

        if (low == null)  return high;
        if (high == null) return low;
        return low[1] <= high[1] ? low : high;
    }

    /**
     * 在单调区间上二分求解 pitch。
     *
     * @param increasing true：yAtTarget 随 pitch 递增；false：递减
     */
    @Nullable
    private static double[] binarySearchPitch(double minRad, double maxRad, boolean increasing,
                                              double dy, double horiz, double speed) {
        double lo = minRad;
        double hi = maxRad;
        boolean anySuccess = false;

        for (int i = 0; i < 28; i++) {
            double mid = (lo + hi) * 0.5;
            double[] r = simulateY(mid, horiz, speed);
            if (r == null) {
                // 到不了 → pitch 越高越到不了 → 收窄到低 pitch
                hi = mid;
                continue;
            }
            anySuccess = true;
            boolean tooHigh = r[0] > dy;
            if (increasing) {
                if (tooHigh) hi = mid;
                else lo = mid;
            } else {
                if (tooHigh) lo = mid;
                else hi = mid;
            }
        }

        if (!anySuccess) return null;
        double pitch = (lo + hi) * 0.5;
        double[] res = simulateY(pitch, horiz, speed);
        if (res == null) return null;
        return new double[]{pitch, res[1]};
    }

    @Nullable
    private static double[] simulateY(double pitch, double horiz, double speed) {
        double vh = Math.cos(pitch) * speed;
        double vy = Math.sin(pitch) * speed;
        if (vh <= 0.0001) return null;
        double x = 0, y = 0;
        double prevX = 0, prevY = 0;

        for (int t = 0; t < MAX_FLIGHT_TICKS; t++) {
            prevX = x;
            prevY = y;
            x += vh;
            y += vy;

            if (x >= horiz) {
                double frac = (horiz - prevX) / (x - prevX);
                double exactY = prevY + (y - prevY) * frac;
                double exactTicks = t + frac;
                return new double[]{ exactY, exactTicks };
            }

            vh *= DRAG;
            vy = vy * DRAG - GRAVITY;
        }
        return null;
    }

    private static boolean pathClear(Level level,
                                     LivingEntity shooter,
                                     Vec3 aimAt,
                                     LivingEntity target,
                                     Solution s,
                                     Predicate<Entity> isFriendly) {
        double x = shooter.getX();
        double y = shooter.getEyeY() - 0.1;
        double z = shooter.getZ();
        double vx = s.dirX() * s.speed();
        double vy = s.dirY() * s.speed();
        double vz = s.dirZ() * s.speed();

        double tx = aimAt.x;
        double ty = aimAt.y + target.getEyeHeight();
        double tz = aimAt.z;

        AABB selfBox = shooter.getBoundingBox().inflate(0.3);

        for (int t = 0; t < MAX_FLIGHT_TICKS; t++) {
            double dx = x - tx;
            double dy = y - ty;
            double dz = z - tz;
            if (dx * dx + dy * dy + dz * dz < HIT_REACH_SQ) return true;

            double nx = x + vx;
            double ny = y + vy;
            double nz = z + vz;

            double moveDist = Math.sqrt((nx - x) * (nx - x)
                    + (ny - y) * (ny - y)
                    + (nz - z) * (nz - z));
            int subSteps = Math.max(1, (int) Math.ceil(moveDist / 0.3));
            for (int sub = 1; sub <= subSteps; sub++) {
                double f = (double) sub / subSteps;
                double cx = x + (nx - x) * f;
                double cy = y + (ny - y) * f;
                double cz = z + (nz - z) * f;
                if (selfBox.contains(cx, cy, cz)) continue;
                if (!level.noCollision(aabbAt(cx, cy, cz))) return false;
            }

            x = nx; y = ny; z = nz;
            vx *= DRAG;
            vy = vy * DRAG - GRAVITY;
            vz *= DRAG;

            if (y < ty - 30 && vy < 0) return false;

            // 友军碰撞检测：0.3 格膨胀（原 0.6 太保守，友军稍近就误判）
            //   且跳过离射手 2 格以内的友军——箭刚从眼睛发出时必然紧贴射手。
            double dxFromShooter = x - shooter.getX();
            double dzFromShooter = z - shooter.getZ();
            if (dxFromShooter * dxFromShooter + dzFromShooter * dzFromShooter > 4.0) {
                AABB box = new AABB(
                        x - ENTITY_CHECK_RADIUS, y - ENTITY_CHECK_RADIUS, z - ENTITY_CHECK_RADIUS,
                        x + ENTITY_CHECK_RADIUS, y + ENTITY_CHECK_RADIUS, z + ENTITY_CHECK_RADIUS);
                var blockedE = level.getEntities(shooter, box,
                        e -> e != shooter && e != target && e.isAlive() && isFriendly.test(e));
                if (!blockedE.isEmpty()) return false;
            }
        }
        return false;
    }

    private static AABB aabbAt(double x, double y, double z) {
        return new AABB(x - PROJECTILE_HALF, y - PROJECTILE_HALF, z - PROJECTILE_HALF,
                x + PROJECTILE_HALF, y + PROJECTILE_HALF, z + PROJECTILE_HALF);
    }
}
