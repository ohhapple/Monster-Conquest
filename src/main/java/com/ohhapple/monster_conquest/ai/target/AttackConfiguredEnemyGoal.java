package com.ohhapple.monster_conquest.ai.target;

import com.ohhapple.monster_conquest.ai.nav.PathProbe;
import com.ohhapple.monster_conquest.army.ArmyAnchor;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.PeacefulUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 主动搜索敌对目标的 Goal：已有活目标时保持稳定（除非被明显更近的敌人抢走）；
 * 新目标从最近的若干候选中按士兵 UUID 哈希选择，避免全员集火同一目标。
 */
public class AttackConfiguredEnemyGoal extends Goal {

    private static final int SCAN_INTERVAL = 20;
    private static final double DEFAULT_RANGE = 32.0;
    private static final double NEAR_SCAN_RANGE = 32.0;
    private static final double ARENA_SCAN_RANGE = 64.0;

    /** 分散选择：从"最近的前 N 个"候选里按 UUID 哈希挑一个。 */
    private static final int DISPERSE_PICK_N = 3;

    /** 目标重估滞后：当前目标距离 > 最近敌人距离 × KEEP_RATIO → 允许重估。 */
    private static final double KEEP_RATIO = 1.5;
    private static final double KEEP_RATIO_SQR = KEEP_RATIO * KEEP_RATIO;

    /** 目标在近战判定范围内 → 无条件保留。 */
    private static final double KEEP_IF_IN_MELEE_SQR = 4.0 * 4.0;

    /** 目标记忆：失去视线后仍继续追击的时长（tick）。8 秒 */
    private static final int TARGET_MEMORY_TICKS = 160;

    /** 严格不可达探测间隔：同一目标至少间隔 2 秒再确认一次。 */
    private static final int UNREACHABLE_PROBE_INTERVAL_TICKS = 40;
    /** 连续确认不可达的持续时间：3 次独立探测均失败后才允许放弃。 */
    private static final int UNREACHABLE_EVIDENCE_TICKS = 80;

    /** 每个扫描周期最多做多少次视线检测（防止大范围扫描时射线过多）。 */
    private static final int MAX_LOS_CHECKS = 8;

    /** 当前目标已连续失去视线的时长（tick，按扫描周期累加）。 */
    private int unseenTicks = 0;

    private static final TargetingConditions CONDITIONS =
            TargetingConditions.forCombat().ignoreLineOfSight();

    private final Mob mob;
    private int scanCooldown = -1;
    private long keepScanTick = -10000L;
    @Nullable private UUID keepScanTarget;
    private boolean keepScanResult;
    @Nullable private UUID unreachableEvidenceTarget;
    private long unreachableEvidenceSince = -1L;
    private long unreachableLastProbeTick = -10000L;
    private int unreachableDefinitiveProbes = 0;

    public AttackConfiguredEnemyGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public void start() {
        this.scanCooldown = nextScanCooldown();
    }

    @Override
    public boolean canUse() {

        // 和平模式：不索敌（单位保留，但没有任何敌意）
        if (PeacefulUtil.isPeaceful(this.mob)) return false;
        if (this.mob instanceof AbstractArmySoldier arenaSoldier
                && arenaSoldier.isArenaUnit()) {
            LivingEntity tauntTarget = arenaSoldier.getArenaTauntTarget();
            if (tauntTarget != null && tauntTarget.isAlive()
                    && arenaSoldier.canAttack(tauntTarget)) {
                arenaSoldier.setTarget(tauntTarget);
                return true;
            }
        }

        // 刚被攻击时优先直接锁定攻击者，不再等 4~20 tick 的扫描冷却。
        if (this.mob.getTarget() == null) {
            if (this.mob instanceof AbstractArmySoldier soldier
                    && soldier.isUnderRecentAttack()) {
                LivingEntity attacker = soldier.getLastHurtByMob();
                if (attacker != null && attacker.isAlive() && soldier.canAttack(attacker)) {
                    soldier.setTarget(attacker);
                    return true;
                }
            } else if (this.mob instanceof AbstractArmyCommander commander
                    && commander.isUnderRecentAttack()) {
                LivingEntity attacker = commander.getLastHurtByMob();
                if (attacker != null && attacker.isAlive() && commander.canAttack(attacker)) {
                    commander.setTarget(attacker);
                    return true;
                }
            }
        }

        // "前往指定点"命令优先：命令执行期间不主动索敌，先把命令走完；
        //   玩家点名攻击（强制目标 / 精确打击）不受影响，仍然是攻击优先。
        //   无差别攻击是玩家明确的攻击策略：行军 / 驻守途中照样索敌。
        if (this.mob instanceof AbstractArmySoldier moveOrdered
                && !moveOrdered.hasPlayerOrderedTarget()
                && !moveOrdered.isFreeAggressiveStance()
                && moveOrdered.isMoveOrderPriorityActive()) {
            if (moveOrdered.getTarget() != null) {
                clearTarget(moveOrdered.getTarget(), "move_order_priority");
            }
            return false;
        }
        // 精确打击 / 强制目标的清理必须在冷却检查之前，
        //   否则目标死亡后无法清理，士兵会一直无目标。
        clearPreciseStrikeIfTargetGone();
        clearStaleForcedTargetIfPlayerArmy();

        // ==================== 目标状态 ====================
        LivingEntity current = this.mob.getTarget();
        boolean noTarget;
        if (current != null) {
            if (!current.isAlive()) {
                clearTarget(current, "target_dead");
                current = null;
                noTarget = true;
                unseenTicks = 0;
            } else if (isSameFactionOrArmy(current)) {
                // 旧仇恨表 / 军队敌对数据可能留下同阵营目标；即使路线可达也必须立即丢弃。
                clearTarget(current, "same_faction_or_army");
                current = null;
                noTarget = true;
                unseenTicks = 0;
            } else if (!isEnemyOf(current)) {
                // 敌对关系暂时过期（共享仇恨 TTL / 军队关系未建立）：
                //   只要路线仍能真正到达目标就继续追击并刷新仇恨，完全不可达才放弃。
                if (canStillReachTarget(current)) {
                    refreshHatred(current);
                    noTarget = false;
                } else {
                    clearTarget(current, "not_enemy_and_unreachable");
                    current = null;
                    noTarget = true;
                    unseenTicks = 0;
                }
            } else {
                noTarget = false;
            }
        } else {
            noTarget = true;
        }

        // 普通仇恨 / 自主索敌只能追到巡逻路线附近；玩家点名目标不受限制。
        if (current != null && !isTargetWithinPatrolLeash(current)) {
            clearTarget(current, "patrol_leash");
            current = null;
            noTarget = true;
            unseenTicks = 0;
        }

        // ==================== 扫描冷却 ====================
        // 无目标时用短冷却（4~8 tick），尽快从共享仇恨列表
        //   拿到下一个敌人；有活目标时用正常冷却（10~20 tick），
        //   避免高频换目标。
        int cd = noTarget
                ? 4 + this.mob.getRandom().nextInt(5)         // 4..8
                : nextScanCooldown();                          // 10..20

        if (scanCooldown < 0) {
            long hash = this.mob.getUUID().getMostSignificantBits()
                    ^ this.mob.getUUID().getLeastSignificantBits();
            scanCooldown = 1 + (int) Math.floorMod(hash, 8L);
        }
        if (--scanCooldown > 0) return false;
        scanCooldown = cd;

        // ==================== 目标稳定性 ====================
        if (current != null) {
            // 视线与记忆：看得见就刷新记忆；看不见超过记忆时长后，
            //   只有路线完全无法到达才放弃目标（还能到达就继续追击）。
            //   玩家指定的强制目标 / 精确打击目标不受视线限制。
            if (isPriorityTarget(current)) {
                unseenTicks = 0;
            } else if (this.mob.hasLineOfSight(current)) {
                unseenTicks = 0;
            } else {
                unseenTicks += SCAN_INTERVAL;
                if (unseenTicks > TARGET_MEMORY_TICKS) {
                    unseenTicks = 0;
                    // 失去视线超时：只有"路线完全无法到达"才放弃目标（仇恨过期）。
                    if (!canStillReachTarget(current)) {
                        clearTarget(current, "unseen_unreachable");
                        current = null;
                    }
                }
            }
        }

        // 当前目标既有严格不可达证据、又处于攻击范围 / 视线之外时，
        //   才允许放弃。放弃前先寻找一个更近且现在就能攻击的替代目标。
        if (current != null && shouldDropDefinitivelyUnreachable(current)) {
            LivingEntity replacement = findAttackableCloserAlternative(current);
            markUnreachable(current);
            clearTarget(current, "definitively_unreachable");
            current = null;
            if (replacement != null) {
                unseenTicks = 0;
                this.mob.setTarget(replacement);
                logTargetSelection(replacement);
                return true;
            }
        }

        if (current != null) {
            if (shouldKeepCurrentTarget(current)) {
                return false;      // 保留当前目标
            }
            clearTarget(current, "superseded_by_nearer");
        }

        // ==================== 新目标选择 ====================
        LivingEntity found = findTarget();
        if (found != null) {
            unseenTicks = 0;
            this.mob.setTarget(found);
            logTargetSelection(found);
            return true;
        }
        return false;
    }

    /**
     * 目标当前是否仍有"能真正到达"的路线：士兵复用绕路规划（要求完整到达），
     *   其他生物用导航路径能否到达判定。完全不可达时才允许仇恨过期。
     */
    /** "能否到达目标"的探测缓存（路径规划开销大：同一目标 2 秒内只算一次，降低服务器压力）。 */
    private int reachProbeTick = -10000;
    @Nullable private UUID reachProbeTargetId = null;
    private boolean reachProbeResult = false;

    private boolean canStillReachTarget(LivingEntity target) {
        // 限流：同一目标 40 tick（2 秒）内只探测一次
        if (this.mob.tickCount - reachProbeTick < 40
                && reachProbeTargetId != null
                && reachProbeTargetId.equals(target.getUUID())) {
            return reachProbeResult;
        }
        boolean result = probeReachability(target);
        reachProbeTick = this.mob.tickCount;
        reachProbeTargetId = target.getUUID();
        reachProbeResult = result;
        return result;
    }

    private boolean probeReachability(LivingEntity target) {
        if (this.mob instanceof AbstractArmySoldier soldier) {
            return soldier.hasPathToTarget(target);
        }
        if (this.mob instanceof AbstractArmyCommander commander) {
            return commander.hasPathToTarget(target);
        }
        return PathProbe.canReach(
                this.mob, target.blockPosition(), 128);
    }

    private boolean canCurrentlyAttackTarget(LivingEntity target) {
        if (this.mob instanceof AbstractArmySoldier soldier) {
            return soldier.canCurrentlyAttack(target);
        }
        if (this.mob instanceof AbstractArmyCommander commander) {
            return commander.canCurrentlyAttack(target);
        }
        return this.mob.hasLineOfSight(target)
                && this.mob.distanceToSqr(target) <= 16.0;
    }

    /**
     * 玩家明确点名 / 强制攻击的目标不能被“不可达换目标”接管。
     *   威胁记忆、共享仇恨和个人受击记忆仍属于可降级仇恨：
     *   它们可以维持追击，但在严谨确认完全打不到后应让位给眼前可攻击的敌人。
     */
    private boolean isHardPriorityTarget(LivingEntity target) {
        if (this.mob instanceof AbstractArmySoldier soldier
                && soldier.isCommandedAttackTarget(target)) {
            return true;
        }
        return isPlayerForcedTarget(target);
    }

    private boolean canDefinitivelyNotReach(LivingEntity target) {
        if (this.mob instanceof AbstractArmySoldier soldier) {
            return soldier.canDefinitivelyNotReachTarget(target);
        }
        if (this.mob instanceof AbstractArmyCommander commander) {
            return commander.canDefinitivelyNotReachTarget(target);
        }
        return !PathProbe.canReach(this.mob, target.blockPosition(), 128);
    }

    /**
     * 只有连续三次、间隔至少 2 秒的独立探测都确认不可达，且当前攻击判定也失败，
     *   才允许放弃。任何一次出现可达路线 / 可攻击状态都会清零证据。
     */
    private boolean shouldDropDefinitivelyUnreachable(LivingEntity target) {
        if (!target.isAlive()) return false;
        if (isHardPriorityTarget(target)) {
            resetUnreachableEvidence();
            return false;
        }
        if (canCurrentlyAttackTarget(target)) {
            resetUnreachableEvidence();
            return false;
        }

        long now = this.mob.level().getGameTime();
        if (unreachableEvidenceTarget == null
                || !unreachableEvidenceTarget.equals(target.getUUID())) {
            unreachableEvidenceTarget = target.getUUID();
            unreachableEvidenceSince = -1L;
            unreachableLastProbeTick = -10000L;
            unreachableDefinitiveProbes = 0;
        }

        if (now - unreachableLastProbeTick >= UNREACHABLE_PROBE_INTERVAL_TICKS) {
            unreachableLastProbeTick = now;
            if (canDefinitivelyNotReach(target)) {
                if (unreachableDefinitiveProbes == 0) {
                    unreachableEvidenceSince = now;
                }
                unreachableDefinitiveProbes++;
            } else {
                unreachableEvidenceSince = -1L;
                unreachableDefinitiveProbes = 0;
            }
        }

        return unreachableDefinitiveProbes >= 3
                && unreachableEvidenceSince >= 0
                && now - unreachableEvidenceSince >= UNREACHABLE_EVIDENCE_TICKS;
    }

    private void resetUnreachableEvidence() {
        unreachableEvidenceTarget = null;
        unreachableEvidenceSince = -1L;
        unreachableLastProbeTick = -10000L;
        unreachableDefinitiveProbes = 0;
    }

    /** 只换到更近、当前就能攻击且合法敌对的目标，避免换到另一个走不到的目标。 */
    @Nullable
    private LivingEntity findAttackableCloserAlternative(LivingEntity current) {
        double currentDistanceSqr = this.mob.distanceToSqr(current);
        LivingEntity best = null;
        double bestDistanceSqr = currentDistanceSqr;
        for (LivingEntity candidate : this.mob.level().getEntitiesOfClass(
                LivingEntity.class,
                this.mob.getBoundingBox().inflate(DEFAULT_RANGE),
                e -> e != this.mob
                        && e != current
                        && e.isAlive()
                        && isEnemyOf(e)
                        && isTargetWithinPatrolLeash(e))) {
            double distanceSqr = this.mob.distanceToSqr(candidate);
            if (distanceSqr >= bestDistanceSqr) continue;
            if (!canCurrentlyAttackTarget(candidate)) continue;
            best = candidate;
            bestDistanceSqr = distanceSqr;
        }
        return best;
    }

    private void markUnreachable(LivingEntity target) {
        if (this.mob instanceof AbstractArmySoldier soldier) {
            soldier.markTargetUnreachable(target);
        } else if (this.mob instanceof AbstractArmyCommander commander) {
            commander.markTargetUnreachable(target);
        }
    }

    private void logTargetSelection(LivingEntity target) {
        if (!GeneralConfig.debugMovementLog) return;
        Monster_conquest.LOGGER.info(
                "[COMBAT-TARGET] t={} unit={} target={} dist={}",
                this.mob.tickCount,
                this.mob.getUUID().toString().substring(0, 8),
                target.getUUID().toString().substring(0, 8),
                String.format(Locale.ROOT, "%.2f",
                        Math.sqrt(this.mob.distanceToSqr(target))));
    }

    /** 刷新对目标的仇恨：重建军队敌对关系与玩家阵营共享仇恨。 */
    private void refreshHatred(LivingEntity target) {
        if (isSameFactionOrArmy(target)) return;
        if (this.mob instanceof AbstractArmySoldier soldier) {
            soldier.refreshHatredOf(target);
        } else if (this.mob instanceof AbstractArmyCommander commander) {
            commander.markAsHatred(target);
        }
    }

    /**
     * 是否应保留当前目标。
     *
     *   返回 true  → 保留（本 Goal 不参与，交给战斗 Goal）
     *   返回 false → 允许重估
     *
     *   策略（滞后阈值，避免来回切换）：
     *     · 目标在近战范围（4 格内）→ 保留
     *     · 没有其他可攻击敌人              → 保留
     *     · 当前目标距离 ≤ 最近敌人距离 × 1.5 → 保留
     *     · 否则                              → 允许重估
     */
    private boolean shouldKeepCurrentTarget(LivingEntity current) {
        // 玩家点名目标 / 军队仇恨目标 / 单位受击记忆不应被旁边的更近敌人顶掉。
        if (isPriorityTarget(current)) return true;
        // 当前目标此刻已经能攻击时优先维持输出，绝不为“更近但同样可攻击”的单位抖动换目标。
        if (canCurrentlyAttackTarget(current)) return true;

        double curD = this.mob.distanceToSqr(current);
        if (curD < KEEP_IF_IN_MELEE_SQR) return true;

        long now = this.mob.level().getGameTime();
        if (keepScanTarget != null && keepScanTarget.equals(current.getUUID())
                && now - keepScanTick < 30) {
            return keepScanResult;
        }

        double nearD = Double.MAX_VALUE;
        for (LivingEntity e : this.mob.level().getEntitiesOfClass(
                LivingEntity.class,
                this.mob.getBoundingBox().inflate(DEFAULT_RANGE),
                e -> e != this.mob && e != current && e.isAlive() && isEnemyOf(e)
                        && canCurrentlyAttackTarget(e))) {
            double d = this.mob.distanceToSqr(e);
            if (d < nearD) nearD = d;
        }
        boolean keep = nearD == Double.MAX_VALUE || curD <= nearD * KEEP_RATIO_SQR;
        keepScanTarget = current.getUUID();
        keepScanTick = now;
        keepScanResult = keep;
        return keep;
    }

    private int nextScanCooldown() {
        int half = Math.max(1, SCAN_INTERVAL / 2);
        return half + this.mob.getRandom().nextInt(SCAN_INTERVAL - half + 1);
    }

    private void clearTarget(@Nullable LivingEntity target, String reason) {
        if (GeneralConfig.debugMovementLog) {
            String squad = "-";
            String attack = "-";
            String precise = "false";
            String forced = "false";
            if (this.mob instanceof AbstractArmySoldier soldier) {
                squad = String.valueOf(soldier.getSquadId());
                Player owner = soldier.getCommanderPlayer();
                if (owner != null) {
                    var st = PlayerArmyManager
                            .getIfPresent(owner.getUUID());
                    if (st != null) {
                        long now = soldier.level().getGameTime();
                        var ss = PlayerCommandState
                                .stateForCommand(soldier, st);
                        attack = ss.getAttack().name();
                        UUID preciseId = st.getPreciseStrikeTarget(now);
                        precise = String.valueOf(preciseId != null
                                && st.isSquadInPreciseStrike(soldier.getSquadId())
                                && st.isPreciseStrikeTarget(
                                        target == null ? null : target.getUUID()));
                        forced = String.valueOf(target != null
                                && ss.isForcedTarget(target.getUUID()));
                    }
                }
            }
            Monster_conquest.LOGGER.info(
                    "[TARGET-CLEAR] t={} unit={} target={} reason={} squad={}"
                            + " attack={} precise={} forced={}",
                    this.mob.tickCount,
                    this.mob.getUUID().toString().substring(0, 8),
                    target == null ? "-" : target.getUUID().toString().substring(0, 8),
                    reason, squad, attack, precise, forced);
        }
        this.mob.setTarget(null);
        // 目标死亡 / 失效后必须立刻重新选目标。目标 Goal 运行期间不会推进
        //   scanCooldown；若沿用战斗前留下的旧值，会白白站 10~20 tick，
        //   这段时间还可能被自然游走接管。
        this.scanCooldown = 0;
        this.keepScanTarget = null;
        this.keepScanTick = -10000L;
        this.keepScanResult = false;
        this.unseenTicks = 0;
        resetUnreachableEvidence();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity t = this.mob.getTarget();
        if (t == null || !t.isAlive()) return false;
        return isEnemyOf(t);
    }

    @Override
    public void stop() {
        // 不清除 target，让其他 goal 接管
    }

    @Nullable
    private LivingEntity findTarget() {
        // 优先目标（玩家强制 / 精确打击 / 威胁传播 / 共享仇恨）：
        //   使用配置的统一范围（GeneralConfig.armyFollowRange），
        //   不受兵种"自然索敌半径"限制，也不要求视线。
        LivingEntity priority = findPriorityTarget(GeneralConfig.armyFollowRange);
        if (priority != null && isTargetWithinPatrolLeash(priority)) return priority;

        double fullRange = DEFAULT_RANGE;
        var attr = this.mob.getAttribute(
                Attributes.FOLLOW_RANGE);
        if (attr != null) fullRange = attr.getValue();
        if (this.mob instanceof AbstractArmySoldier arenaSoldier
                && arenaSoldier.isArenaUnit()) {
            fullRange = Math.max(fullRange, ARENA_SCAN_RANGE);
        }

        double nearRange = Math.min(fullRange, NEAR_SCAN_RANGE);
        LivingEntity close = scanInRadius(nearRange);
        if (close != null) return close;
        return fullRange > nearRange ? scanInRadius(fullRange) : null;
    }

    /**
     * 优先目标获取：玩家强制目标 / 精确打击目标 / 威胁传播目标 / 共享仇恨目标。
     *
     *   这类目标一律按 GeneralConfig.armyFollowRange 的统一范围（默认 128 格）获取——
     *   兵种自然索敌半径（近战 32 / 弓手 96 等）只约束自动索敌，
     *   被命令或被威胁时部队会主动朝目标推进。
     */
    @Nullable
    private LivingEntity findPriorityTarget(double range) {
        if (!(this.mob.level() instanceof ServerLevel sl)) return null;

        if (this.mob instanceof AbstractArmySoldier soldier) {
            UUID commandedId = soldier.getCommandedAttackTargetId();
            if (commandedId != null) {
                LivingEntity commanded = livingByUuid(sl, commandedId);
                if (commanded != null && soldier.isCommandedAttackTarget(commanded)
                        && this.mob.distanceToSqr(commanded) <= range * range) {
                    return commanded;
                }
            }
        }

        if (this.mob instanceof AbstractArmySoldier soldier && soldier.isPlayerArmy()) {
            Player owner = soldier.getCommanderPlayer();
            if (owner != null) {
                var st = PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (st != null) {
                    long nowGT = soldier.level().getGameTime();

                    UUID precise = st.getPreciseStrikeTarget(nowGT);
                    if (precise != null && st.isSquadInPreciseStrike(soldier.getSquadId())) {
                        LivingEntity e = nearestInGroup(sl, st.getPreciseStrikeGroup(), range);
                        if (e != null) return e;
                    }

                    // 无小队单兵读个人状态（与小队共用同一套强制目标逻辑）
                    var ss = PlayerCommandState
                            .stateForCommand(soldier, st);
                    if (ss.getForcedTarget() != null) {
                        LivingEntity e = nearestInGroup(sl, ss.getForcedTargetGroup(), range);
                        if (e != null) return e;
                    }

                    LivingEntity shared = nearestSharedHatred(sl, st, range);
                    if (shared != null) return shared;
                }
            }
        }

        // NPC 军队：指挥官仇恨列表（威胁传播 / 仇恨权杖标记）
        if (this.mob instanceof AbstractArmySoldier soldier) {
            AbstractArmyCommander cmd = soldier.getCommander();
            if (cmd != null && cmd.isAlive()) {
                LivingEntity h = nearestHatred(cmd.getHatredList(), range);
                if (h != null) return h;
            }
        } else if (this.mob instanceof AbstractArmyCommander cmd) {
            LivingEntity h = nearestHatred(cmd.getHatredList(), range);
            if (h != null) return h;
        }
        if (this.mob instanceof AbstractArmySoldier soldier) {
            LivingEntity remembered = soldier.pickPersonalHatredTarget(range);
            if (remembered != null) return remembered;
        }
        return null;
    }

    @Nullable
    private static LivingEntity livingByUuid(ServerLevel sl, UUID uuid) {
        Entity e = sl.getEntities().get(uuid);
        return (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) ? le : null;
    }

    /** 目标组里最近的、仍可攻击的目标（整支军队一起锁定）。 */
    @Nullable
    private LivingEntity nearestInGroup(ServerLevel sl, Collection<UUID> group, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (UUID id : group) {
            LivingEntity e = livingByUuid(sl, id);
            if (e == null || !isEnemyOf(e)) continue;
            double d = this.mob.distanceToSqr(e);
            if (d <= bestD) {
                bestD = d;
                best = e;
            }
        }
        return best;
    }

    /** 仇恨列表里最近的、仍可攻击的目标。 */
    @Nullable
    private LivingEntity nearestHatred(List<LivingEntity> hatred, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity h : hatred) {
            if (h == null || !h.isAlive() || h.isRemoved()) continue;
            if (!isEnemyOf(h)) continue;
            double d = this.mob.distanceToSqr(h);
            if (d <= bestD) {
                bestD = d;
                best = h;
            }
        }
        return best;
    }

    /** 玩家军队共享仇恨列表里最近的敌人（含仇恨军队的指挥官与士兵）。 */
    @Nullable
    private LivingEntity nearestSharedHatred(ServerLevel sl,
                                             PlayerCommandState st,
                                             double range) {
        long nowGT = sl.getGameTime();
        LivingEntity best = null;
        double bestD = range * range;

        for (Map.Entry<UUID, Long> e : new ArrayList<>(st.getSharedHatredEntities().entrySet())) {
            if (e.getValue() < nowGT) continue;
            LivingEntity le = livingByUuid(sl, e.getKey());
            if (le == null || !isEnemyOf(le)) continue;
            double d = this.mob.distanceToSqr(le);
            if (d <= bestD) {
                bestD = d;
                best = le;
            }
        }

        for (Map.Entry<UUID, Long> e : new ArrayList<>(st.getSharedHatredArmies().entrySet())) {
            if (e.getValue() < nowGT) continue;
            Entity leader = sl.getEntities().get(e.getKey());
            List<LivingEntity> members = new ArrayList<>();
            if (leader instanceof AbstractArmyCommander cmd) {
                members.add(cmd);
                members.addAll(cmd.getSoldiers());
            } else if (leader instanceof Player p) {
                members.add(p);
                members.addAll(sl.getEntitiesOfClass(AbstractArmySoldier.class,
                        p.getBoundingBox().inflate(Math.min(range, 64.0)),
                        s -> e.getKey().equals(s.getCommanderUuid().orElse(null))));
            }
            for (LivingEntity m : members) {
                if (m == null || !m.isAlive()) continue;
                if (!isEnemyOf(m)) continue;
                double d = this.mob.distanceToSqr(m);
                if (d <= bestD) {
                    bestD = d;
                    best = m;
                }
            }
        }
        return best;
    }

    /** 优先目标：玩家强制 / 精确打击 / 威胁传播 / 共享仇恨（不受视线与自然半径限制）。 */
    private boolean isPriorityTarget(LivingEntity entity) {
        if (this.mob instanceof AbstractArmySoldier soldier
                && soldier.isCommandedAttackTarget(entity)) {
            return true;
        }
        if (isPlayerForcedTarget(entity)) return true;

        if (this.mob instanceof AbstractArmySoldier soldier) {
            AbstractArmyCommander cmd = soldier.getCommander();
            if (cmd != null && cmd.isAlive()) {
                for (LivingEntity h : cmd.getHatredList()) {
                    if (h == entity) return true;
                }
            }
            if (soldier.isPlayerArmy()) {
                Player owner = soldier.getCommanderPlayer();
                if (owner != null) {
                    var st = PlayerArmyManager
                            .getIfPresent(owner.getUUID());
                    if (st != null) {
                        long nowGT = soldier.level().getGameTime();
                        UUID army = AbstractArmySoldier.resolveArmyUuid(entity, soldier.level());
                        if (army != null && st.isSharedHatredArmy(army, nowGT)) return true;
                        if (st.isSharedHatredEntity(entity.getUUID(), nowGT)) return true;
                    }
                }
            }
            if (soldier.isPersonalHatredTarget(entity)) return true;
        } else if (this.mob instanceof AbstractArmyCommander cmd) {
            for (LivingEntity h : cmd.getHatredList()) {
                if (h == entity) return true;
            }
        }
        return false;
    }

    /**
     * 从附近敌人里选一个目标。
     *   与"永远最近"的差异：
     *     · 先把候选按距离排序
     *     · 从"最近的前 DISPERSE_PICK_N 个"里按 mob UUID 哈希选一个
     *   效果：不同士兵倾向不同目标 → 混战分散，不集火。
     *   候选数 ≤ DISPERSE_PICK_N 时退化为顺序遍历后的哈希选择；
     *   候选数 == 1 时就是唯一那个。
     */
    @Nullable
    private LivingEntity scanInRadius(double range) {
        List<LivingEntity> candidates = this.mob.level().getEntitiesOfClass(
                LivingEntity.class,
                this.mob.getBoundingBox().inflate(range),
                e -> e != this.mob && e.isAlive() && isEnemyOf(e)
                        && isTargetWithinPatrolLeash(e));
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this.mob)));

        // 视线过滤：只从"看得见"的敌人里挑目标（隔墙 / 隔山的敌人不再被锁定）。
        //   玩家指定的强制目标例外（命令打谁就打谁，哪怕暂时被地形挡住）。
        //   射线检测最多做 MAX_LOS_CHECKS 次，保证开锁成本可控。
        List<LivingEntity> visible = new ArrayList<>(DISPERSE_PICK_N);
        int checked = 0;
        for (LivingEntity e : candidates) {
            if (checked >= MAX_LOS_CHECKS) break;
            checked++;
            if (isPriorityTarget(e) || this.mob.hasLineOfSight(e)) {
                visible.add(e);
            }
        }
        if (visible.isEmpty()) return null;

        // 当前就能攻击的敌人优先，避免锁到一个看得见但暂时打不到的远处目标，
        //   把旁边已经贴脸的单位晾着。没有可攻击候选时才回退到其余可见敌人。
        List<LivingEntity> attackable = new ArrayList<>(DISPERSE_PICK_N);
        for (LivingEntity candidate : visible) {
            if (canCurrentlyAttackTarget(candidate)) attackable.add(candidate);
        }
        List<LivingEntity> pool = attackable.isEmpty() ? visible : attackable;

        int pickRange = Math.min(pool.size(), DISPERSE_PICK_N);
        long h = this.mob.getUUID().getMostSignificantBits()
                ^ this.mob.getUUID().getLeastSignificantBits();
        int idx = (int) Math.floorMod(h, (long) pickRange);
        return pool.get(idx);
    }

    /**
     * 玩家指定的强制目标（精确打击 / 集结攻击）不受视线限制：
     *   玩家命令打谁，部队就会朝谁推进，哪怕中途被地形挡住视线。
     */
    private boolean isPlayerForcedTarget(LivingEntity entity) {
        if (!(this.mob instanceof AbstractArmySoldier soldier)) return false;
        if (!soldier.isPlayerArmy() || !soldier.isPlayerOrderAccepted()) return false;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return false;
        var st = PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;

        UUID precise = st.getPreciseStrikeTarget(soldier.level().getGameTime());
        if (precise != null && st.isSquadInPreciseStrike(soldier.getSquadId())
                && st.isPreciseStrikeTarget(entity.getUUID())) {
            return true;
        }
        return PlayerCommandState
                .stateForCommand(soldier, st).isForcedTarget(entity.getUUID());
    }

    /**
     * 精确打击目标死亡或被移除（get(uuid) 返回 null）时立即解除，
     *   否则索敌会一直返回 false，软驻守无法解除。
     */
    private void clearPreciseStrikeIfTargetGone() {
        if (!(this.mob instanceof AbstractArmySoldier soldier)) return;
        if (!soldier.isPlayerArmy()) return;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return;
        var st = PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return;

        UUID precise = st.getPreciseStrikeTarget(
                soldier.level().getGameTime());
        if (precise == null) return;

        if (!(soldier.level() instanceof ServerLevel sl)) return;
        Entity e = sl.getEntities().get(precise);

        if ((e == null || !e.isAlive() || e.isRemoved())
                && !hasAliveGroupMember(sl, st.getPreciseStrikeGroup())) {
            // 目标组里还有活人 → 精确打击继续（整支军队都是目标）；
            //   全部阵亡 / 消失才结束。
            st.endPreciseStrike();
        }
    }

    /**
     * 检查玩家军队的强制目标（COMMANDED 模式）是否已消失。
     *   目标死亡 / 被 discard → 清除强制目标并回退到 AUTONOMOUS 模式。
     */
    private void clearStaleForcedTargetIfPlayerArmy() {
        if (!(this.mob instanceof AbstractArmySoldier soldier)) return;
        if (!soldier.isPlayerArmy()) return;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return;
        var st = PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return;

        var ss = PlayerCommandState
                .stateForCommand(soldier, st);
        if (ss.getAttack()
                != PlayerCommandState.AttackMode.COMMANDED) {
            return;
        }

        UUID forced = ss.getForcedTarget();
        if (forced == null) {
            ss.setAttackMode(
                    PlayerCommandState.AttackMode.AUTONOMOUS);
            return;
        }
        if (!(soldier.level() instanceof ServerLevel sl)) return;
        Entity e = sl.getEntities().get(forced);

        if ((e == null || !e.isAlive() || e.isRemoved())
                && !hasAliveGroupMember(sl, ss.getForcedTargetGroup())) {
            ss.setForcedTargetId(null);
            ss.setForcedTargetGroup(null);
            ss.setAttackMode(
                    PlayerCommandState.AttackMode.AUTONOMOUS);
        }
    }

    /** 目标组里是否还有活着的成员（用于判断"整队目标"是否已经打完）。 */
    private static boolean hasAliveGroupMember(ServerLevel sl, Collection<UUID> group) {
        for (UUID id : group) {
            Entity e = sl.getEntities().get(id);
            if (e instanceof LivingEntity le && le.isAlive() && !le.isRemoved()) return true;
        }
        return false;
    }

    private boolean isEnemyOf(LivingEntity entity) {
        if (this.mob instanceof AbstractArmySoldier arena && arena.isArenaUnit()) {
            return arena.isArenaEnemy(entity);
        }
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (entity instanceof WarHorseEntity) return false;
        // 同阵营 / 同军队是绝对友军，不能被个人记忆、共享仇恨或旧军队敌对标记覆盖。
        if (isSameFactionOrArmy(entity)) return false;
        // "寻路不通"黑名单：攻城时敌人站在城墙上够不到 → 暂时不当目标（避免反复推墙）
        if (this.mob instanceof AbstractArmySoldier soldierBlacklist
                && soldierBlacklist.isTargetBlacklisted(entity)) return false;
        if (this.mob instanceof AbstractArmyCommander commanderBlacklist
                && commanderBlacklist.isTargetBlacklisted(entity)) return false;
        // 不可选中的目标（无敌 / 旁观等）不作为敌人：例如袭击代理
        if (!entity.canBeSeenAsEnemy()) return false;
        // 锚点保护：村庄巡逻队 / 袭击增援不把活动范围之外的目标视为敌人
        if (!ArmyAnchor.targetAllowed(this.mob, entity)) return false;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不仇视。
        if (NativePeace.isPeacefulPair(this.mob, entity)) return false;

        // 单向结盟
        ResourceLocation myFac0 = myFaction();
        ResourceLocation theirFac0 = factionOf(entity);
        if (myFac0 != null && theirFac0 != null
                && FactionAlly
                .isAlly(myFac0, theirFac0)) return false;

        // 反击判定
        if (this.mob.getLastHurtByMob() == entity) {
            ResourceLocation myF = myFaction();
            ResourceLocation theirF = factionOf(entity);
            if (myF == null || theirF == null || !myF.equals(theirF)) {
                return true;
            }
        }

        // 玩家军队分支（含硬驻守锚点检查）必须放在 NPC 仇恨短路之前
        if (this.mob instanceof AbstractArmySoldier soldier) {
            if (soldier.isAttackStopped()) return false;

            // 无差别攻击：玩家明确下的攻击策略，不受"是否接受过命令"限制；
            //   硬驻守时仍只打驻守范围内的目标（站住不动，不追远处）。
            if (soldier.isFreeAggressiveStance()) {
                return isFreeAggressiveTargetAllowed(soldier, entity);
            }

            if (soldier.isPlayerOrderAccepted()) {
                Player owner = soldier.getCommanderPlayer();
                if (owner != null) {
                    var st = PlayerArmyManager
                            .getIfPresent(owner.getUUID());
                    if (st != null) {
                        UUID precise = st.getPreciseStrikeTarget(
                                soldier.level().getGameTime());
                        if (precise != null
                                && st.isSquadInPreciseStrike(soldier.getSquadId())) {
                            // 精确打击目标是"整支军队"时，组内任意成员都是合法目标
                            return st.isPreciseStrikeTarget(entity.getUUID());
                        }

                        var ss = PlayerCommandState
                                .stateForCommand(soldier, st);
                        var attack = ss.getAttack();

                        // 硬驻守：只把驻守范围内的目标当作合法目标（站住不动，不追远处）。
                        if (ss.getMovement()
                                == PlayerCommandState.MovementMode.STATION
                                && !ss.isSoftStation()) {
                            if (!PlayerCommandState
                                    .allowsHardStationTarget(
                                            soldier,
                                            ss.getFormationAnchor(),
                                            entity.position())) {
                                return false;
                            }
                        }
                        else if (attack == PlayerCommandState.AttackMode.COMMANDED) {
                            if (ss.getForcedTarget() != null) {
                                // 强制目标是"整支军队"时，组内任意成员都是合法目标
                                return ss.isForcedTarget(entity.getUUID());
                            }
                        }
                        else if (attack == PlayerCommandState.AttackMode.HOLD_GROUND) {
                            if (this.mob.distanceToSqr(entity) > 64.0) return false;
                        }
                    }
                }
            }
        }

        // NPC 军队共享仇恨（放在硬驻守锚点检查之后）
        if (this.mob instanceof AbstractArmySoldier soldier) {
            AbstractArmyCommander cmd = soldier.getCommander();
            if (cmd != null && cmd.isAlive()) {
                for (LivingEntity h : cmd.getHatredList()) {
                    if (h == entity) return true;
                }
            }
        }

        Set<String> globalEnemies = FactionConfig.defaultEnemies;
        if (globalEnemies != null && !globalEnemies.isEmpty()) {
            var key = EntityType.getKey(entity.getType());
            if (key != null && globalEnemies.contains(key.toString())) return true;
        }

        UUID myArmy = myArmy();
        UUID theirArmy = AbstractArmySoldier.resolveArmyUuid(entity, this.mob.level());
        if (myArmy != null && theirArmy != null) {
            if (theirArmy.equals(myArmy)) return false;
            if (ArmyRelations.isHostile(myArmy, theirArmy)) return true;
        }
        if (this.mob instanceof AbstractArmySoldier soldierPersonal
                && soldierPersonal.isPersonalHatredTarget(entity)) {
            return true;
        }
        if (this.mob instanceof AbstractArmySoldier soldierCommanded
                && soldierCommanded.isCommandedAttackTarget(entity)) {
            return true;
        }

        ResourceLocation myFaction = myFaction();
        if (myFaction != null) {
            var key = EntityType.getKey(entity.getType());
            if (key != null) {
                Set<String> enemies;
                if (FactionIds.isPlayer(myFaction)) {
                    enemies = FactionConfig.playerFactionEnemies;
                } else {
                    var faction = FactionRegistry.get(myFaction);
                    enemies = faction == null
                            ? null
                            : faction.getDefaultHostileEntityTypes();
                }
                if (enemies != null && enemies.contains(key.toString())) return true;
            }
        }

        ResourceLocation theirFaction = factionOf(entity);
        if (myFaction != null && theirFaction != null
                && !myFaction.equals(theirFaction)
                && FactionHostility.isHostile(myFaction, theirFaction)) return true;

        if (this.mob instanceof AbstractArmySoldier soldier && soldier.isPlayerArmy()) {
            Player owner = soldier.getCommanderPlayer();
            if (owner != null) {
                var st = PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (st != null) {
                    long nowGT = soldier.level().getGameTime();
                    if (theirArmy != null && st.isSharedHatredArmy(theirArmy, nowGT)) {
                        return true;
                    }
                    if (st.isSharedHatredEntity(entity.getUUID(), nowGT)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private boolean isSameFactionOrArmy(LivingEntity entity) {
        ResourceLocation mine = myFaction();
        ResourceLocation theirs = factionOf(entity);
        if (mine != null && mine.equals(theirs)) return true;

        UUID myArmy = myArmy();
        UUID theirArmy = AbstractArmySoldier.resolveArmyUuid(
                entity, this.mob.level());
        return myArmy != null && myArmy.equals(theirArmy);
    }

    /**
     * 有巡逻路线的单位只追击路线附近的普通目标。
     *   玩家通过强制目标 / 精确打击点名的目标不受限制，仍然按玩家命令追击。
     *   路线数据所在的区块未加载时由 PathRoutes.cleanup 自行处理，这里只做活动范围过滤。
     */
    private boolean isTargetWithinPatrolLeash(@Nullable LivingEntity target) {
        if (target == null) return true;
        if (this.mob instanceof AbstractArmySoldier soldier) {
            return soldier.isPatrolTargetAllowed(target);
        }
        if (this.mob instanceof AbstractArmyCommander commander) {
            return commander.isPatrolTargetAllowed(target);
        }
        return true;
    }

    @Nullable
    private UUID myArmy() {
        if (this.mob instanceof AbstractArmyCommander c) return c.getUUID();
        if (this.mob instanceof AbstractArmySoldier s) {
            return AbstractArmySoldier.resolveArmyUuid(s, this.mob.level());
        }
        return null;
    }

    /**
     * 无差别攻击：打一切"非自己阵营、非结盟阵营"的单位。
     *   · 玩家阵营 id 按玩家区分（每人一份独立小阵营），所以别的玩家的单位照打；
     *   · 自己阵营（自己的兵 / 自己的首领）与结盟阵营（双向判定）不打；
     *   · 传进来的目标已经过通用过滤（创造玩家 / 战马 / 同族和平 / 锚点等）。
     */
    private static boolean isFreeAggressiveTarget(AbstractArmySoldier soldier, LivingEntity entity) {
        ResourceLocation mine = soldier.getFactionId();
        ResourceLocation theirs = factionOf(entity);
        if (mine != null && theirs != null) {
            if (mine.equals(theirs)) return false;
            if (FactionAlly
                    .isAlly(mine, theirs)) return false;
            if (FactionAlly
                    .isAlly(theirs, mine)) return false;
        }
        UUID myArmy = soldier.getCommanderUuid().orElse(null);
        UUID theirArmy = AbstractArmySoldier.resolveArmyUuid(entity, soldier.level());
        if (myArmy != null && myArmy.equals(theirArmy)) return false;
        return true;
    }

    /**
     * 无差别攻击的目标是否合法（含玩家命令的优先约束）：
     *   · 精确打击点名期间只打点名目标；
     *   · 硬驻守时只打驻守范围内的目标（站住不动，不追远处）；
     *   其余交给 {@link #isFreeAggressiveTarget} 的阵营判定。
     */
    private static boolean isFreeAggressiveTargetAllowed(AbstractArmySoldier soldier,
                                                         LivingEntity entity) {
        if (!soldier.isPlayerArmy()) return false;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return false;
        var st = PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;

        long nowGT = soldier.level().getGameTime();
        if (st.getPreciseStrikeTarget(nowGT) != null
                && st.isSquadInPreciseStrike(soldier.getSquadId())) {
            return st.isPreciseStrikeTarget(entity.getUUID());
        }

        var ss = PlayerCommandState
                .stateForCommand(soldier, st);
        if (!PlayerCommandState
                .allowsHardStationTarget(soldier, ss.getFormationAnchor(), entity.position())) {
            return false;
        }
        return isFreeAggressiveTarget(soldier, entity);
    }

    @Nullable
    private ResourceLocation myFaction() {
        if (this.mob instanceof AbstractArmyCommander c) return c.getFactionId();
        if (this.mob instanceof AbstractArmySoldier s) return s.getFactionId();
        return null;
    }

    @Nullable
    private static ResourceLocation factionOf(LivingEntity e) {
        if (e instanceof AbstractArmyCommander c) return c.getFactionId();
        if (e instanceof AbstractArmySoldier s) return s.getFactionId();
        if (e instanceof Player p) return FactionIds.player(p.getUUID());
        if (e instanceof WarHorseEntity h) {
            UUID o = h.getOwnerUuid();
            if (o == null || !(h.level() instanceof ServerLevel sl)) return null;
            var owner = sl.getEntities().get(o);
            if (owner instanceof LivingEntity le) return factionOf(le);
        }
        return null;
    }

}
