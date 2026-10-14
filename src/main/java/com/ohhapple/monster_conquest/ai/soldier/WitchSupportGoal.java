package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.projectile.WitchPotionEntity;
import com.ohhapple.monster_conquest.entity.soldiers.VillagerPriestSoldier;
import com.ohhapple.monster_conquest.entity.soldiers.WitchSoldier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 投掷兵支援 Goal（女巫 / 村民牧师共用）：治疗队友与自身，对敌投掷药水
 * （女巫随机中毒 / 虚弱 / 伤害，牧师只投中毒）；硬驻守时只转向 + 投药。
 */
public class WitchSupportGoal extends Goal {

    private static final double MIN_DIST = 10.0;
    private static final double MAX_DIST = 24.0;
    /** 投掷药水的最大出手距离（格）：MAX_DIST + 4，卡住判定等外部逻辑复用。 */
    public static final double MAX_THROW_RANGE = MAX_DIST + 4.0;
    private static final int THROW_CD = 35;
    private static final int HEAL_CD = 30;
    private static final float ALLY_HEAL_THRESHOLD = 0.75f;
    private static final float LEADER_CRITICAL = 0.4f;
    private static final double LEADER_RUSH_DIST = 6.0;
    private static final double HEAL_APPROACH_DIST = 14.0;

    private static final double POTION_SPEED = 1.5;
    private static final double PROJECTILE_GRAVITY = WitchPotionEntity.FLIGHT_GRAVITY;
    private static final double PROJECTILE_DRAG    = WitchPotionEntity.FLIGHT_DRAG;

    private final AbstractArmySoldier witch;
    private int cooldown = 0;
    private int scanTimer = 0;
    /** 走位寻路节流：避免每 tick 重算路径（对着打不到的目标尤其费）。 */
    /** -1 = 还没用过：第一次调用先随机错开，避免整队同一 tick 一起跑寻路。 */
    private int moveCooldown = -1;
    @Nullable
    private LivingEntity cachedWounded = null;

    public WitchSupportGoal(AbstractArmySoldier witch) {
        this.witch = witch;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    /** 走位寻路的节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissueMove(Mob mover) {
        if (moveCooldown < 0) {
            moveCooldown = witch.getRandom().nextInt(10);
        }
        if (moveCooldown > 0) {
            moveCooldown--;
            return false;
        }
        moveCooldown = mover.getNavigation().isDone() ? 5 : 10;
        return true;
    }

    @Override
    public boolean canUse() {
        if (!witch.getSoldierType().isThrower()) return false;

        if (--scanTimer <= 0) {
            scanTimer = 10;
            cachedWounded = findWoundedAlly();
        } else if (cachedWounded != null
                && (!cachedWounded.isAlive()
                || cachedWounded.getHealth() >= cachedWounded.getMaxHealth() * ALLY_HEAL_THRESHOLD)) {
            cachedWounded = findWoundedAlly();
        }

        if (witch.getHealth() < witch.getMaxHealth() * ALLY_HEAL_THRESHOLD) return true;

        LivingEntity leader = witch.getLeaderEntity();
        if (leader != null && leader.isAlive()
                && leader.getHealth() < leader.getMaxHealth() * LEADER_CRITICAL) return true;

        LivingEntity enemy = witch.getTarget();
        if (enemy != null && enemy.isAlive()) return true;

        return cachedWounded != null;
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() { this.cooldown = 0; this.moveCooldown = -1; }

    @Override
    public void tick() {
        if (cooldown > 0) cooldown--;

        boolean hardStation = witch.isHardStationed();
        // 玩家刚下了"前往指定点"命令 → 支援走位让路：只做原地能做的事，不往别处跑
        boolean holdingMoveOrder = witch.isMoveOrderPriorityActive();

        // 1. 女巫自己受伤 → 自疗
        if (witch.getHealth() < witch.getMaxHealth() * ALLY_HEAL_THRESHOLD
                && cooldown <= 0) {
            applySelfHeal();
            cooldown = HEAL_CD;
            stopMove();
            return;
        }

        LivingEntity leader = witch.getLeaderEntity();

        // 2. 无首领 → 战斗
        if (leader == null || !leader.isAlive()) {
            handleCombat(hardStation);
            return;
        }

        // 3. 首领濒死 → 紧急治疗
        if (leader.getHealth() < leader.getMaxHealth() * LEADER_CRITICAL) {
            handleEmergencyHeal(leader, hardStation || holdingMoveOrder);
            return;
        }

        // 4. 常规治疗
        LivingEntity healTarget = cachedWounded;
        if (healTarget != null && (!healTarget.isAlive()
                || healTarget.getHealth() >= healTarget.getMaxHealth() * ALLY_HEAL_THRESHOLD)) {
            healTarget = null;
        }
        if (healTarget == witch) healTarget = null;

        if (healTarget != null) {
            double distToHeal = witch.distanceTo(healTarget);
            // 硬驻守：不靠近治疗目标，原地尝试投药
            if (!hardStation && !holdingMoveOrder && distToHeal > HEAL_APPROACH_DIST) {
                Mob mover = witch.getVehicle() instanceof Mob h ? h : witch;
                // 移动速度不额外乘倍率：走自身 / 坐骑的常速
                if (shouldReissueMove(mover)) mover.getNavigation().moveTo(healTarget, 1.0D);
                witch.getLookControl().setLookAt(healTarget, 30f, 30f);
                return;
            }
            if (cooldown <= 0) {
                throwPotion(healTarget, WitchPotionEntity.Kind.HEAL);
                cooldown = HEAL_CD;
                witch.getLookControl().setLookAt(healTarget, 30f, 30f);
                if (witch.getTarget() == null || !witch.getTarget().isAlive()) stopMove();
                return;
            }
            stopMove();
            witch.getLookControl().setLookAt(healTarget, 30f, 30f);
            return;
        }

        // 5. 无治疗目标 → 战斗
        handleCombat(hardStation);
    }

    private void applySelfHeal() {
        // 村民牧师：净化 + 少量回血（无增益）；女巫：回血 + 再生 / 吸收。
        boolean priest = witch instanceof VillagerPriestSoldier;
        if (priest) {
            VillagerPriestSoldier.applyToSelf(witch);
        } else {
            WitchSoldier.applyToSelf(witch);
        }

        if (witch.level() instanceof ServerLevel sl) {
            sl.playSound(null, witch.getX(), witch.getY(), witch.getZ(),
                    priest ? SoundEvents.GENERIC_DRINK : SoundEvents.WITCH_DRINK,
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        }
    }

    private void handleEmergencyHeal(LivingEntity leader, boolean hardStation) {
        // 硬驻守：不换乘骑兵
        if (!hardStation && !witch.isPassenger()) witch.switchToCavalry();

        double dist = witch.distanceTo(leader);
        Mob mover = witch.getVehicle() instanceof Mob h ? h : witch;

        // 硬驻守：不移动，只转向
        if (!hardStation && dist > LEADER_RUSH_DIST) {
            if (shouldReissueMove(mover)) mover.getNavigation().moveTo(leader, 1.0D);
        } else {
            mover.getNavigation().stop();
            witch.getLookControl().setLookAt(leader, 30f, 30f);
        }

        if (cooldown <= 0 && dist < 16.0) {
            throwPotion(leader, WitchPotionEntity.Kind.HEAL);
            cooldown = HEAL_CD;
        }
    }

    private void handleCombat(boolean hardStation) {
        LivingEntity enemy = witch.getTarget();
        if (enemy == null || !enemy.isAlive()) { stopMove(); return; }
        double dist = witch.distanceTo(enemy);
        boolean inWater = witch.isInWater();

        Mob mover = witch.getVehicle() instanceof Mob h ? h : witch;

        // 硬驻守：完全不移动，只转向 + 投药
        if (hardStation) {
            mover.getNavigation().stop();
            witch.getLookControl().setLookAt(enemy, 30f, 30f);
        } else if (inWater) {
            if (dist > 4.0) {
                if (shouldReissueMove(mover)) mover.getNavigation().moveTo(enemy, 1.0D);
            }
            else {
                mover.getNavigation().stop();
                witch.getLookControl().setLookAt(enemy, 30f, 30f);
            }
        } else if (dist < MIN_DIST) {
            Vec3 away = witch.position().subtract(enemy.position()).normalize();
            Vec3 dest = witch.position().add(away.scale(6.0));
            // 被近身拉开距离（走位 / 撤退）同样不加速
            if (shouldReissueMove(mover)) mover.getNavigation().moveTo(dest.x, dest.y, dest.z, 1.0D);
        } else if (dist > MAX_DIST) {
            if (shouldReissueMove(mover)) mover.getNavigation().moveTo(enemy, 1.0D);
        } else if (!witch.hasClearShot(enemy)) {
            // 目标被墙 / 掩体挡住：投出去的药水只会砸在墙上 → 重新规划绕路
            witch.replanRouteTo(enemy);
        } else {
            mover.getNavigation().stop();
            witch.getLookControl().setLookAt(enemy, 30f, 30f);
        }

        if (cooldown <= 0 && dist < MAX_THROW_RANGE && witch.hasClearShot(enemy)) {
            WitchPotionEntity.Kind kind = pickDebuffKind();
            throwPotion(enemy, kind);
            cooldown = THROW_CD;
        }
    }

    private WitchPotionEntity.Kind pickDebuffKind() {
        // 村民牧师只投掷中毒药瓶；女巫在中毒 / 虚弱 / 伤害中随机。
        if (witch instanceof VillagerPriestSoldier) return WitchPotionEntity.Kind.POISON;

        int roll = witch.getRandom().nextInt(3);
        return switch (roll) {
            case 0 -> WitchPotionEntity.Kind.POISON;
            case 1 -> WitchPotionEntity.Kind.WEAKEN;
            default -> WitchPotionEntity.Kind.HARM;
        };
    }

    private LivingEntity findWoundedAlly() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;

        if (witch.getHealth() < witch.getMaxHealth() * ALLY_HEAL_THRESHOLD) {
            best = witch;
            bestDist = 0;
        }

        LivingEntity leader = witch.getLeaderEntity();
        if (leader != null && leader.isAlive()
                && leader.getHealth() < leader.getMaxHealth() * ALLY_HEAL_THRESHOLD) {
            double d = witch.distanceToSqr(leader);
            if (d < bestDist) { bestDist = d; best = leader; }
        }

        AbstractArmyCommander cmd = witch.getCommander();
        if (cmd != null) {
            List<AbstractArmySoldier> allies = cmd.getSoldiers();
            for (AbstractArmySoldier a : allies) {
                if (a == witch || !a.isAlive()) continue;
                if (a.getHealth() >= a.getMaxHealth() * ALLY_HEAL_THRESHOLD) continue;
                double d = witch.distanceToSqr(a);
                if (d < bestDist) { bestDist = d; best = a; }
            }
        } else if (witch.level() instanceof ServerLevel sl) {
            UUID myCmd = witch.getCommanderUuid().orElse(null);
            if (myCmd != null) {
                var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(myCmd);
                boolean preciseActive = state != null
                        && state.getPreciseStrikeTarget(witch.level().getGameTime()) != null;
                int mySquad = witch.getSquadId();

                for (AbstractArmySoldier a : sl.getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        witch.getBoundingBox().inflate(48.0),
                        s -> s.isAlive() && s != witch)) {
                    UUID theirCmd = a.getCommanderUuid().orElse(null);
                    if (!myCmd.equals(theirCmd)) continue;
                    if (preciseActive && a.getSquadId() != mySquad) continue;
                    if (a.getHealth() >= a.getMaxHealth() * ALLY_HEAL_THRESHOLD) continue;
                    double d = witch.distanceToSqr(a);
                    if (d < bestDist) { bestDist = d; best = a; }
                }
            }
        }
        return best;
    }

    private void stopMove() {
        if (witch.getVehicle() instanceof Mob h) h.getNavigation().stop();
        else witch.getNavigation().stop();
    }

    private void throwPotion(LivingEntity target, WitchPotionEntity.Kind kind) {
        WitchPotionEntity potion = new WitchPotionEntity(witch.level(), witch);
        potion.configure(kind, witch.getTier().ordinal(),
                witch.getCommanderUuid().orElse(null),
                witch.getFactionId(),
                witch instanceof VillagerPriestSoldier
                        ? WitchPotionEntity.Caster.PRIEST
                        : WitchPotionEntity.Caster.WITCH);

        double startX = witch.getX();
        double startY = witch.getEyeY() - 0.2;
        double startZ = witch.getZ();
        potion.setPos(startX, startY, startZ);

        double targetX = target.getX();
        double targetY = target.getY() + target.getBbHeight() * 0.5;
        double targetZ = target.getZ();

        Vec3 vel = target.getDeltaMovement();
        double horiz0 = Math.sqrt(
                (targetX - startX) * (targetX - startX) + (targetZ - startZ) * (targetZ - startZ));
        double approxTicks = horiz0 / POTION_SPEED;
        double leadTicks = approxTicks * 0.9;
        targetX += vel.x * leadTicks;
        targetZ += vel.z * leadTicks;

        double dx = targetX - startX;
        double dy = targetY - startY;
        double dz = targetZ - startZ;
        double horiz = Math.sqrt(dx * dx + dz * dz);

        double[] dir = solveThrow(dx, dz, dy, horiz, POTION_SPEED);
        if (dir == null) return;

        potion.shoot(dir[0], dir[1], dir[2], (float) POTION_SPEED, 0.0f);
        witch.level().addFreshEntity(potion);
        witch.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
    }

    private static double[] solveThrow(double dx, double dz, double dy, double horiz, double speed) {
        if (horiz < 0.001) return new double[]{0, 1, 0};
        double dirX = dx / horiz;
        double dirZ = dz / horiz;

        // 分段二分（同 BallisticAim）：
        //   yAtTarget 在 [-60°, 45°] 上关于 pitch 单调递增；
        //   在 [45°, 70°] 上关于 pitch 单调递减。
        //   原代码直接在 [-60°, 70°] 上二分 → 单调性不成立 → 近距离俯角解算可能
        //   收敛到错误方向。
        final double MIN_PITCH_RAD = -Math.PI / 3.0;          // -60°
        final double APEX_PITCH_RAD = Math.PI / 4.0;          //  45°
        final double MAX_PITCH_RAD  = 70.0 * Math.PI / 180.0; //  70°

        double[] low  = searchPitch(MIN_PITCH_RAD, APEX_PITCH_RAD, true,  dy, horiz, speed);
        double[] high = searchPitch(APEX_PITCH_RAD, MAX_PITCH_RAD, false, dy, horiz, speed);

        double pitch;
        if (low == null && high == null) return null;
        if (low == null) pitch = high[0];
        else if (high == null) pitch = low[0];
        else pitch = (low[1] <= high[1]) ? low[0] : high[0];   // 选飞行时间短的

        return new double[]{
                dirX * Math.cos(pitch),
                Math.sin(pitch),
                dirZ * Math.cos(pitch)
        };
    }

    /**
     * 在单调区间上二分求 pitch。
     *   increasing = true  → yAtTarget 随 pitch 递增
     *   increasing = false → yAtTarget 随 pitch 递减
     * 返回 [pitch, flightTicks]；区间内无解返回 null。
     */
    @Nullable
    private static double[] searchPitch(double lo, double hi, boolean increasing,
                                        double dy, double horiz, double speed) {
        boolean anySuccess = false;
        for (int iter = 0; iter < 40; iter++) {
            double mid = (lo + hi) / 2;
            double[] r = simulatePitch(mid, horiz, speed);
            if (r == null) {
                // 到不了 → pitch 越高越到不了 → 收窄到低 pitch
                hi = mid;
                continue;
            }
            anySuccess = true;
            boolean tooHigh = r[0] > dy;
            if (increasing) {
                if (tooHigh) hi = mid; else lo = mid;
            } else {
                if (tooHigh) lo = mid; else hi = mid;
            }
        }
        if (!anySuccess) return null;
        double pitch = (lo + hi) / 2;
        double[] r = simulatePitch(pitch, horiz, speed);
        if (r == null) return null;
        return new double[]{pitch, r[1]};
    }

    /**
     * 模拟 pitch 下的弹道，返回 [yAtTarget, flightTicks]；到不了返回 null。
     * 使用物理常量 PROJECTILE_DRAG / PROJECTILE_GRAVITY（与 Arrow 一致）。
     */
    @Nullable
    private static double[] simulatePitch(double pitch, double horiz, double speed) {
        double vh = Math.cos(pitch) * speed;
        double vy = Math.sin(pitch) * speed;
        if (vh <= 0.0001) return null;
        double px = 0, py = 0;
        double prevX = 0, prevY = 0;

        for (int t = 0; t < 500; t++) {
            prevX = px;
            prevY = py;
            px += vh;
            py += vy;
            if (px >= horiz) {
                double frac = (px - prevX) < 0.0001 ? 0 : (horiz - prevX) / (px - prevX);
                double exactY = prevY + (py - prevY) * frac;
                double exactTicks = t + frac;
                return new double[]{exactY, exactTicks};
            }
            vh *= PROJECTILE_DRAG;
            vy = vy * PROJECTILE_DRAG - PROJECTILE_GRAVITY;
        }
        return null;
    }
}
