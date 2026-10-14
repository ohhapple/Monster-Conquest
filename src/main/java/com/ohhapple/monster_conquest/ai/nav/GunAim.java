package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 枪械弹道规划。
 *
 * 与弓箭的 BallisticAim 不同：
 *   · 重力极小（子弹几乎无下坠）
 *   · 不把友军当作阻挡 —— 子弹可以穿过友军身体继续飞
 *   · 只检查飞行路径上的方块阻挡
 *   · 依旧对移动目标做迭代预判
 */
public final class GunAim {

    private GunAim() {}

    private static final double PROJECTILE_HALF = 0.2;
    private static final double DRAG = 0.99;

    private static final double MIN_PITCH_RAD = -20.0 * Math.PI / 180.0;
    private static final double MAX_PITCH_RAD = 45.0 * Math.PI / 180.0;

    private static final int MAX_FLIGHT_TICKS = 600;
    private static final double HIT_REACH_SQ = 0.6 * 0.6;
    private static final int PREDICT_ITERATIONS = 5;

    public record Solution(double dirX, double dirY, double dirZ,
                           double speed, double flightTicks) {}

    @Nullable
    public static Solution solve(Level level,
                                 LivingEntity shooter,
                                 LivingEntity target,
                                 double baseSpeed,
                                 double gravity) {
        Vec3 vel = target.getDeltaMovement();

        // 每次射击随机选择瞄准点：1/3 瞄头、2/3 瞄身体中心。
        //   全部瞄头会让 AI 的每一发都触发爆头倍率，全部瞄身体又太软。
        final boolean aimHead = shooter.getRandom().nextInt(3) == 0;

        // 兜底：先解一个"瞄准当前位置"的弹道。
        Solution fallback = solveInternal(level, shooter, target, target.position(),
                baseSpeed, gravity, aimHead);
        if (fallback == null) return null;

        // 目标静止 → 不需要预判
        if (vel.lengthSqr() < 0.0001) return fallback;

        Solution best = fallback;
        for (int iter = 0; iter < PREDICT_ITERATIONS; iter++) {
            Vec3 next = target.position().add(vel.scale(best.flightTicks()));
            if (next.distanceToSqr(target.position()) < 0.04) break;

            Solution attempt = solveInternal(level, shooter, target, next,
                    baseSpeed, gravity, aimHead);
            // 预判点被方块挡住 → 保留上一次成功的解
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
                                          double gravity,
                                          boolean aimHead) {
        double sx = shooter.getX();
        double sy = shooter.getEyeY() - 0.1;
        double sz = shooter.getZ();

        double tx = aimAt.x;
        double ty = aimAt.y + aimHeight(target, aimHead);
        double tz = aimAt.z;

        double dx = tx - sx;
        double dy = ty - sy;
        double dz = tz - sz;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        if (horiz < 0.001) {
            double dirY = dy >= 0 ? 1.0 : -1.0;
            Solution s = new Solution(0, dirY, 0, baseSpeed, 1.0);
            return pathClear(level, shooter, aimAt, target, s, gravity, aimHead) ? s : null;
        }

        double dirX = dx / horiz;
        double dirZ = dz / horiz;

        double[] pt = solvePitchAndTime(dy, horiz, baseSpeed, gravity);
        if (pt == null) return null;
        double pitch = pt[0];
        double flightTicks = pt[1];
        double cosP = Math.cos(pitch);

        Solution s = new Solution(dirX * cosP, Math.sin(pitch), dirZ * cosP,
                baseSpeed, flightTicks);
        return pathClear(level, shooter, aimAt, target, s, gravity, aimHead) ? s : null;
    }

    /**
     * AI 瞄准高度（相对目标脚底）：
     *   · 瞄头 = 眼睛高度（爆头判定线为身高的 80%，眼睛一定高于该线）
     *   · 瞄身体 = 身高 60% 处（躯干中心）
     */
    private static double aimHeight(LivingEntity target, boolean aimHead) {
        return aimHead ? target.getEyeHeight() : target.getBbHeight() * 0.6;
    }

    /**
     * 在低弧单调区间二分求解 pitch。
     * 物理常量与抛射物一致：水平 vx *= 0.99，垂直 vy = vy * 0.99 - gravity。
     */
    @Nullable
    private static double[] solvePitchAndTime(double dy, double horiz,
                                              double speed, double gravity) {
        double lo = MIN_PITCH_RAD;
        double hi = MAX_PITCH_RAD;
        boolean anySuccess = false;

        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) * 0.5;
            double[] r = simulateY(mid, horiz, speed, gravity);
            if (r == null) {
                // pitch 越高水平速度越低，越到不了 → 收窄到低 pitch
                hi = mid;
                continue;
            }
            anySuccess = true;
            if (r[0] > dy) hi = mid;
            else lo = mid;
        }

        if (!anySuccess) return null;
        double pitch = (lo + hi) * 0.5;
        double[] res = simulateY(pitch, horiz, speed, gravity);
        if (res == null) return null;
        return new double[]{pitch, res[1]};
    }

    @Nullable
    private static double[] simulateY(double pitch, double horiz,
                                      double speed, double gravity) {
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
                return new double[]{exactY, exactTicks};
            }

            vh *= DRAG;
            vy = vy * DRAG - gravity;
        }
        return null;
    }

    private static boolean pathClear(Level level,
                                     LivingEntity shooter,
                                     Vec3 aimAt,
                                     LivingEntity target,
                                     Solution s,
                                     double gravity,
                                     boolean aimHead) {
        double x = shooter.getX();
        double y = shooter.getEyeY() - 0.1;
        double z = shooter.getZ();
        double vx = s.dirX() * s.speed();
        double vy = s.dirY() * s.speed();
        double vz = s.dirZ() * s.speed();

        double tx = aimAt.x;
        double ty = aimAt.y + aimHeight(target, aimHead);
        double tz = aimAt.z;

        AABB selfBox = shooter.getBoundingBox().inflate(0.3);

        for (int t = 0; t < MAX_FLIGHT_TICKS; t++) {
            double nx = x + vx;
            double ny = y + vy;
            double nz = z + vz;

            // 高速弹丸单步可跨 3~5 格，用"点到线段"距离判断是否已飞抵目标，
            // 避免一步跨过目标后被误判为弹道不通。
            double segX = nx - x;
            double segY = ny - y;
            double segZ = nz - z;
            double segLenSq = segX * segX + segY * segY + segZ * segZ;
            double param = segLenSq <= 1.0E-9 ? 0.0
                    : ((tx - x) * segX + (ty - y) * segY + (tz - z) * segZ) / segLenSq;
            param = Math.max(0.0, Math.min(1.0, param));
            double px = x + segX * param - tx;
            double py = y + segY * param - ty;
            double pz = z + segZ * param - tz;
            if (px * px + py * py + pz * pz < HIT_REACH_SQ) return true;

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

            x = nx;
            y = ny;
            z = nz;
            vx *= DRAG;
            vy = vy * DRAG - gravity;
            vz *= DRAG;

            if (y < ty - 30 && vy < 0) return false;
        }
        return false;
    }

    private static AABB aabbAt(double x, double y, double z) {
        return new AABB(x - PROJECTILE_HALF, y - PROJECTILE_HALF, z - PROJECTILE_HALF,
                x + PROJECTILE_HALF, y + PROJECTILE_HALF, z + PROJECTILE_HALF);
    }
}
