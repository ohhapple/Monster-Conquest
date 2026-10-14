package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.item.LanceItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 骑枪穿刺：骑兵冲锋 / 穿越时，对马前方扇形内的敌人逐个造成伤害。
 *
 * 近战士兵与首领 / 巡逻队长共用这一份逻辑，保证两者的骑兵近战表现完全一致：
 *   · 必须"有活目标 + 马正在冲锋或穿越"才结算（行军 / 归队途中不会误伤）；
 *   · 每 tick 结算一次，同一目标 10 tick 内只吃一次伤害；
 *   · 伤害 = 近战攻击力 + 当前冲锋速度 × 8，命中后把目标推离冲锋方向。
 */
public final class LancePierce {

    /** 马前方扇形判定半径（格）。 */
    public static final double RADIUS = 2.8;
    /** 扇形半角（35°）的余弦阈值。 */
    private static final double HALF_COS = Math.cos(Math.toRadians(35.0));
    /** 同一目标两次穿刺的最小间隔（tick）。 */
    private static final int PER_TARGET_CD = 10;
    /** 骑枪穿刺允许的最大高度差（格）：站在墙头 / 高台上的目标刺不到（冲锋是水平方向）。 */
    private static final double MAX_PIERCE_Y_DIFF = 2.0;

    private LancePierce() {}

    /**
     * @param rider       骑手
     * @param target      骑手当前的活目标（没有目标时完全不穿刺）
     * @param chargeSpeed 骑手记录的冲锋速度
     * @param age         骑手自己的 tick 计数（用于冷却）
     * @param hitCooldown 每个骑手独立的目标冷却表
     * @param canAttack   友军 / 结盟过滤（直接复用实体自己的 canAttack）
     * @return 本次结算命中的目标数量（供调用方标记"最近成功攻击"）
     */
    public static int tick(LivingEntity rider,
                           @Nullable LivingEntity target,
                           double chargeSpeed,
                           int age,
                           Map<UUID, Integer> hitCooldown,
                           Predicate<LivingEntity> canAttack) {
        if (!rider.isPassenger()) return 0;
        if (!(rider.getMainHandItem().getItem() instanceof LanceItem)) return 0;
        if (target == null || !target.isAlive()) return 0;
        if (!(rider.getVehicle() instanceof WarHorseEntity horse)) return 0;
        if (!horse.isCharging() && !horse.isPassingThrough()) return 0;

        int hits = 0;

        Vec3 forward = Vec3.directionFromRotation(0, horse.getYRot());

        for (LivingEntity victim : rider.level().getEntitiesOfClass(LivingEntity.class,
                rider.getBoundingBox().inflate(RADIUS + 1.5),
                e -> e.isAlive() && e != rider && canAttack.test(e))) {

            Vec3 to = victim.position().subtract(rider.position());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            if (horiz > RADIUS) continue;
            // 高度差限制：墙头 / 高台上的目标不会被骑枪穿刺命中
            if (Math.abs(victim.getY() - rider.getY()) > MAX_PIERCE_Y_DIFF) continue;
            if (horiz > 0.5) {
                Vec3 dir = new Vec3(to.x, 0, to.z).normalize();
                if (dir.dot(forward) < HALF_COS) continue;
            }

            // 不穿墙：骑手身体中心到目标身体中心被方块挡住 → 不穿刺
            Vec3 fromCenter = rider.position().add(0, rider.getBbHeight() * 0.5, 0);
            Vec3 toCenter = victim.position().add(0, victim.getBbHeight() * 0.5, 0);
            if (rider.level().clip(new ClipContext(fromCenter, toCenter,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rider))
                    .getType() != HitResult.Type.MISS) {
                continue;
            }

            int nextAllowed = hitCooldown.getOrDefault(victim.getUUID(), 0);
            if (age < nextAllowed) continue;
            hitCooldown.put(victim.getUUID(), age + PER_TARGET_CD);

            victim.invulnerableTime = 0;
            float dmg = (float) (rider.getAttributeValue(Attributes.ATTACK_DAMAGE)
                    + chargeSpeed * 8.0);
            boolean hit = victim.hurt(rider.damageSources().mobAttack(rider), dmg);
            if (hit) {
                hits++;
                double len = Math.max(0.001, horiz);
                victim.knockback(0.8, -to.x / len, -to.z / len);
            }
        }

        if (age % 100 == 0) {
            hitCooldown.entrySet().removeIf(e -> e.getValue() < age - 100);
        }
        return hits;
    }
}
