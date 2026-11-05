package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 近战水平与垂直攻击范围。
 *
 * <p>普通单位仍受同层高度限制；小体型飞行单位悬停在其他单位头顶时，
 * 攻击方可以按双方碰撞箱高度抬手攻击，但不能因此隔楼层命中。竞技大型
 * 单位则按双方碰撞箱宽度增加水平伸手，避免体型放大后因碰撞间隔够不着。</p>
 */
public final class MeleeReach {

    /** 竞技大型单位碰撞箱之外仍可挥击的水平余量。 */
    private static final double ARENA_SURFACE_REACH = 1.5;
    private static final double ARENA_BASE_REACH = 2.8;

    private MeleeReach() {}

    public static double horizontalReach(LivingEntity attacker,
                                         LivingEntity target,
                                         double normalReach) {
        if (!(attacker instanceof AbstractArmySoldier soldier)
                || !soldier.isArenaUnit()) {
            return normalReach;
        }
        double halfWidths = (attacker.getBbWidth()
                + target.getBbWidth()) * 0.5;
        return Math.max(Math.max(normalReach, ARENA_BASE_REACH),
                halfWidths + ARENA_SURFACE_REACH);
    }

    public static double horizontalSurfaceDistance(
            LivingEntity first, LivingEntity second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        double centerDistance = Math.sqrt(dx * dx + dz * dz);
        double halfWidths = (first.getBbWidth()
                + second.getBbWidth()) * 0.5;
        return Math.max(0.0, centerDistance - halfWidths);
    }

    public static double horizontalSurfaceDistance(
            Vec3 point, LivingEntity target) {
        double dx = point.x - target.getX();
        double dz = point.z - target.getZ();
        return Math.max(0.0,
                Math.sqrt(dx * dx + dz * dz)
                        - target.getBbWidth() * 0.5);
    }

    public static boolean canReachVertically(LivingEntity attacker,
                                             LivingEntity target,
                                             double normalLimit) {
        return canReachVertically(attacker, attacker.getY(), target, normalLimit);
    }

    public static boolean canReachVertically(LivingEntity attacker,
                                             double attackerY,
                                             LivingEntity target,
                                             double normalLimit) {
        double limit = target instanceof AbstractFlyingSoldier
                ? Math.max(normalLimit,
                        attacker.getBbHeight() + target.getBbHeight() + 0.5)
                : normalLimit;
        return Math.abs(attackerY - target.getY()) <= limit;
    }
}
