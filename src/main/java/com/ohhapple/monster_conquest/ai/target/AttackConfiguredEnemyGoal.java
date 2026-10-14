package com.ohhapple.monster_conquest.ai.target;

import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    /** 分散选择：从"最近的前 N 个"候选里按 UUID 哈希挑一个。 */
    private static final int DISPERSE_PICK_N = 3;

    /** 目标重估滞后：当前目标距离 > 最近敌人距离 × KEEP_RATIO → 允许重估。 */
    private static final double KEEP_RATIO = 1.5;
    private static final double KEEP_RATIO_SQR = KEEP_RATIO * KEEP_RATIO;

    /** 目标在近战判定范围内 → 无条件保留。 */
    private static final double KEEP_IF_IN_MELEE_SQR = 4.0 * 4.0;

    /** 目标记忆：失去视线后仍继续追击的时长（tick）。8 秒 */
    private static final int TARGET_MEMORY_TICKS = 160;

    /** 每个扫描周期最多做多少次视线检测（防止大范围扫描时射线过多）。 */
    private static final int MAX_LOS_CHECKS = 8;

    /** 当前目标已连续失去视线的时长（tick，按扫描周期累加）。 */
    private int unseenTicks = 0;

    private static final TargetingConditions CONDITIONS =
            TargetingConditions.forCombat().ignoreLineOfSight();

    private final Mob mob;
    private int scanCooldown = -1;

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
        if (com.ohhapple.monster_conquest.util.PeacefulUtil.isPeaceful(this.mob)) return false;
        // "前往指定点"命令优先：命令执行期间不主动索敌，先把命令走完；
        //   玩家点名攻击（强制目标 / 精确打击）不受影响，仍然是攻击优先。
        //   无差别攻击是玩家明确的攻击策略：行军 / 驻守途中照样索敌。
        if (this.mob instanceof AbstractArmySoldier moveOrdered
                && !moveOrdered.hasPlayerOrderedTarget()
                && !moveOrdered.isFreeAggressiveStance()
                && moveOrdered.isMoveOrderPriorityActive()) {
            if (moveOrdered.getTarget() != null) moveOrdered.setTarget(null);
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
                this.mob.setTarget(null);
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
                    this.mob.setTarget(null);
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

        // ==================== 扫描冷却 ====================
        // 无目标时用短冷却（2~5 tick），尽快从共享仇恨列表
        //   拿到下一个敌人；有活目标时用正常冷却（10~20 tick），
        //   避免高频换目标。
        int cd = noTarget
                ? 2 + this.mob.getRandom().nextInt(4)         // 2..5
                : nextScanCooldown();                          // 10..20

        if (scanCooldown < 0) scanCooldown = cd;
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
                        this.mob.setTarget(null);
                        current = null;
                    }
                }
            }
        }

        if (current != null) {
            if (shouldKeepCurrentTarget(current)) {
                return false;      // 保留当前目标
            }
            this.mob.setTarget(null);
        }

        // ==================== 新目标选择 ====================
        LivingEntity found = findTarget();
        if (found != null) {
            unseenTicks = 0;
            this.mob.setTarget(found);
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
    @Nullable private java.util.UUID reachProbeTargetId = null;
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
        net.minecraft.world.entity.ai.navigation.PathNavigation nav = this.mob.getNavigation();
        net.minecraft.world.level.pathfinder.Path path = nav.createPath(target, 1);
        return path != null && path.canReach();
    }

    /** 刷新对目标的仇恨：重建军队敌对关系与玩家阵营共享仇恨。 */
    private void refreshHatred(LivingEntity target) {
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
        double curD = this.mob.distanceToSqr(current);
        if (curD < KEEP_IF_IN_MELEE_SQR) return true;

        double nearD = Double.MAX_VALUE;
        for (LivingEntity e : this.mob.level().getEntitiesOfClass(
                LivingEntity.class,
                this.mob.getBoundingBox().inflate(DEFAULT_RANGE),
                e -> e != this.mob && e != current && e.isAlive() && isEnemyOf(e))) {
            double d = this.mob.distanceToSqr(e);
            if (d < nearD) nearD = d;
        }
        if (nearD == Double.MAX_VALUE) return true;

        return curD <= nearD * KEEP_RATIO_SQR;
    }

    private int nextScanCooldown() {
        int half = Math.max(1, SCAN_INTERVAL / 2);
        return half + this.mob.getRandom().nextInt(SCAN_INTERVAL - half + 1);
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
        if (priority != null) return priority;

        double fullRange = DEFAULT_RANGE;
        var attr = this.mob.getAttribute(
                net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        if (attr != null) fullRange = attr.getValue();

        double nearRange = Math.min(fullRange, NEAR_SCAN_RANGE);
        LivingEntity close = scanInRadius(nearRange);
        if (close != null) return close;

        if (fullRange > nearRange) return scanInRadius(fullRange);
        return null;
    }

    /**
     * 优先目标获取：玩家强制目标 / 精确打击目标 / 威胁传播目标 / 共享仇恨目标。
     *
     *   这类目标一律按 GeneralConfig.armyFollowRange 的统一范围（默认 128 格）获取——
     *   兵种自然索敌半径（近战 32 / 弓手 64 等）只约束自动索敌，
     *   被命令或被威胁时部队会主动朝目标推进。
     */
    @Nullable
    private LivingEntity findPriorityTarget(double range) {
        if (!(this.mob.level() instanceof ServerLevel sl)) return null;

        if (this.mob instanceof AbstractArmySoldier soldier && soldier.isPlayerArmy()) {
            Player owner = soldier.getCommanderPlayer();
            if (owner != null) {
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (st != null) {
                    long nowGT = soldier.level().getGameTime();

                    UUID precise = st.getPreciseStrikeTarget(nowGT);
                    if (precise != null && st.isSquadInPreciseStrike(soldier.getSquadId())) {
                        LivingEntity e = nearestInGroup(sl, st.getPreciseStrikeGroup(), range);
                        if (e != null) return e;
                    }

                    // 无小队单兵读个人状态（与小队共用同一套强制目标逻辑）
                    var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
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
                                             com.ohhapple.monster_conquest.player.PlayerCommandState st,
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
                    var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                            .getIfPresent(owner.getUUID());
                    if (st != null) {
                        long nowGT = soldier.level().getGameTime();
                        UUID army = AbstractArmySoldier.resolveArmyUuid(entity, soldier.level());
                        if (army != null && st.isSharedHatredArmy(army, nowGT)) return true;
                        if (st.isSharedHatredEntity(entity.getUUID(), nowGT)) return true;
                    }
                }
            }
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
                e -> e != this.mob && e.isAlive() && isEnemyOf(e));
        if (candidates.isEmpty()) return null;

        candidates.sort(Comparator.comparingDouble(e -> e.distanceToSqr(this.mob)));

        // 视线过滤：只从"看得见"的敌人里挑目标（隔墙 / 隔山的敌人不再被锁定）。
        //   玩家指定的强制目标例外（命令打谁就打谁，哪怕暂时被地形挡住）。
        //   射线检测最多做 MAX_LOS_CHECKS 次，保证开锁成本可控。
        List<LivingEntity> visible = new ArrayList<>(DISPERSE_PICK_N);
        int checked = 0;
        for (LivingEntity e : candidates) {
            if (visible.size() >= DISPERSE_PICK_N || checked >= MAX_LOS_CHECKS) break;
            checked++;
            if (isPriorityTarget(e) || this.mob.hasLineOfSight(e)) {
                visible.add(e);
            }
        }
        if (visible.isEmpty()) return null;

        int pickRange = Math.min(visible.size(), DISPERSE_PICK_N);
        long h = this.mob.getUUID().getMostSignificantBits()
                ^ this.mob.getUUID().getLeastSignificantBits();
        int idx = (int) Math.floorMod(h, (long) pickRange);
        return visible.get(idx);
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
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;

        UUID precise = st.getPreciseStrikeTarget(soldier.level().getGameTime());
        if (precise != null && st.isSquadInPreciseStrike(soldier.getSquadId())
                && st.isPreciseStrikeTarget(entity.getUUID())) {
            return true;
        }
        return com.ohhapple.monster_conquest.player.PlayerCommandState
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
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
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
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return;

        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(soldier, st);
        if (ss.getAttack()
                != com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.COMMANDED) {
            return;
        }

        UUID forced = ss.getForcedTarget();
        if (forced == null) {
            ss.setAttackMode(
                    com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.AUTONOMOUS);
            return;
        }
        if (!(soldier.level() instanceof ServerLevel sl)) return;
        Entity e = sl.getEntities().get(forced);

        if ((e == null || !e.isAlive() || e.isRemoved())
                && !hasAliveGroupMember(sl, ss.getForcedTargetGroup())) {
            ss.setForcedTargetId(null);
            ss.setForcedTargetGroup(null);
            ss.setAttackMode(
                    com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.AUTONOMOUS);
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
        if (entity instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (entity instanceof WarHorseEntity) return false;
        // "寻路不通"黑名单：攻城时敌人站在城墙上够不到 → 暂时不当目标（避免反复推墙）
        if (this.mob instanceof AbstractArmySoldier soldierBlacklist
                && soldierBlacklist.isTargetBlacklisted(entity)) return false;
        if (this.mob instanceof AbstractArmyCommander commanderBlacklist
                && commanderBlacklist.isTargetBlacklisted(entity)) return false;
        // 不可选中的目标（无敌 / 旁观等）不作为敌人：例如袭击代理
        if (!entity.canBeSeenAsEnemy()) return false;
        // 锚点保护：村庄巡逻队 / 袭击增援不把活动范围之外的目标视为敌人
        if (!com.ohhapple.monster_conquest.army.ArmyAnchor.targetAllowed(this.mob, entity)) return false;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不仇视。
        if (NativePeace.isPeacefulPair(this.mob, entity)) return false;

        // 单向结盟
        ResourceLocation myFac0 = myFaction();
        ResourceLocation theirFac0 = factionOf(entity);
        if (myFac0 != null && theirFac0 != null
                && com.ohhapple.monster_conquest.army.relation.FactionAlly
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
                    var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                            .getIfPresent(owner.getUUID());
                    if (st != null) {
                        UUID precise = st.getPreciseStrikeTarget(
                                soldier.level().getGameTime());
                        if (precise != null
                                && st.isSquadInPreciseStrike(soldier.getSquadId())) {
                            // 精确打击目标是"整支军队"时，组内任意成员都是合法目标
                            return st.isPreciseStrikeTarget(entity.getUUID());
                        }

                        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                                .stateForCommand(soldier, st);
                        var attack = ss.getAttack();

                        // 硬驻守：只把驻守范围内的目标当作合法目标（站住不动，不追远处）。
                        if (ss.getMovement()
                                == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION
                                && !ss.isSoftStation()) {
                            if (!com.ohhapple.monster_conquest.player.PlayerCommandState
                                    .allowsHardStationTarget(
                                            soldier,
                                            ss.getFormationAnchor(),
                                            entity.position())) {
                                return false;
                            }
                        }
                        else if (attack == com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.COMMANDED) {
                            if (ss.getForcedTarget() != null) {
                                // 强制目标是"整支军队"时，组内任意成员都是合法目标
                                return ss.isForcedTarget(entity.getUUID());
                            }
                        }
                        else if (attack == com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.HOLD_GROUND) {
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
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
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
            if (com.ohhapple.monster_conquest.army.relation.FactionAlly
                    .isAlly(mine, theirs)) return false;
            if (com.ohhapple.monster_conquest.army.relation.FactionAlly
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
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;

        long nowGT = soldier.level().getGameTime();
        if (st.getPreciseStrikeTarget(nowGT) != null
                && st.isSquadInPreciseStrike(soldier.getSquadId())) {
            return st.isPreciseStrikeTarget(entity.getUUID());
        }

        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(soldier, st);
        if (!com.ohhapple.monster_conquest.player.PlayerCommandState
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
