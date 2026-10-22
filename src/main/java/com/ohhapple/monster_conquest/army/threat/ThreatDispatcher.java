package com.ohhapple.monster_conquest.army.threat;

import com.ohhapple.monster_conquest.army.CombatRules;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.PeacefulUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 威胁传播统一入口：点传播（自己 / 首领）→ 军队面传播 → 同阵营跨军队传播；
 * 玩家军队的共享仇恨按军队 UUID 或实体 UUID 记录。
 * 停战窗口（resetHostility）内忽略所有传播。
 */
public final class ThreatDispatcher {

    private ThreatDispatcher() {}

    // ===================== 停战窗口 =====================

    /** 停战窗口持续时长（tick）。5 秒 = 100 tick，足够覆盖大部分投射物飞行时间。 */
    public static final int TRUCE_DURATION_TICKS = 100;

    /** 停战窗口结束的游戏刻。<= 当前刻 → 无停战。 */
    private static long truceUntilGameTime = Long.MIN_VALUE;

    /** 仇恨重置标记：数值变化时，各单位在 tick 里清掉自己的目标 / 仇恨 / 追击路线。 */
    private static long hatredResetStamp = 0L;

    /**
     * 仇恨传播限流：同一个"受害者"20 tick 内只做一次完整传播。
     *   完整传播要广播整支军队（大范围实体扫描），连续被命中时是掉刻的重要来源；
     *   第一次命中已建立仇恨与目标，后续命中只做便宜的刷新。
     */
    private static final int PROPAGATE_THROTTLE_TICKS = 20;
    private static final Map<UUID, Integer> lastPropagateTick = new HashMap<>();

    public static long hatredResetStamp() { return hatredResetStamp; }

    /** 请求清除所有单位当前的目标与仇恨（重置工具 / 和平模式使用）。 */
    public static void requestHatredReset() { hatredResetStamp++; }

    /** 开启一段停战窗口：从现在起 durationTicks 内所有 propagate 被忽略。 */
    public static void startTruce(long nowGT, int durationTicks) {
        long until = nowGT + Math.max(1, durationTicks);
        if (until > truceUntilGameTime) truceUntilGameTime = until;
    }

    /** 立即结束停战窗口。 */
    public static void clearTruce() {
        truceUntilGameTime = Long.MIN_VALUE;
    }

    public static boolean isTruceActive(long nowGT) {
        return nowGT < truceUntilGameTime;
    }

    // ===================== 入口 =====================

    public static void propagate(LivingEntity victim, LivingEntity attacker) {

        if (victim == null || attacker == null) return;
        if (victim == attacker) return;
        if (victim.level().isClientSide) return;
        if (!victim.isAlive() || !attacker.isAlive()) return;
        // 和平模式：不传播仇恨（单位保留但不结仇 / 不攻击）
        if (PeacefulUtil.isPeaceful(victim)) return;

        // 停战窗口：任何新攻击都不建立新的敌对关系
        long nowGT = victim.level().getGameTime();
        if (nowGT < truceUntilGameTime) return;

        if (attacker instanceof Player p
                && (p.isCreative() || p.isSpectator())) return;

        // 同军队 / 同阵营 / 任一方向结盟都属于友军摩擦：不传播仇恨、不写共享名单。
        if (CombatRules.isFriendly(victim, attacker)) return;

        // 限流只节流大范围广播；同一窗口内的后续攻击者仍必须写入
        // 点对点仇恨与玩家共享名单，否则群殴时只有第一个攻击者会被记住。
        int nowTick = (int) (nowGT % Integer.MAX_VALUE);
        Integer last = lastPropagateTick.get(victim.getUUID());
        if (last != null && nowTick - last < PROPAGATE_THROTTLE_TICKS) {
            tryEstablishArmyHostility(victim, attacker);
            markArmyLeaderHatredOnly(victim, attacker);
            markArmyLeaderHatredOnly(attacker, victim);
            markPlayerSharedHatredIfApplicable(victim, attacker);
            markPlayerSharedHatredIfApplicable(attacker, victim);
            return;
        }
        lastPropagateTick.put(victim.getUUID(), nowTick);
        if (lastPropagateTick.size() > 256) {
            lastPropagateTick.entrySet().removeIf(e -> nowTick - e.getValue() > 1200);
        }

        tryEstablishArmyHostility(victim, attacker);

        ArmyThreatUtil.propagateThreatToFaction(victim, attacker);

        dispatchToArmy(victim, attacker);
        dispatchToArmy(attacker, victim);

        notifyArmyMembers(victim, attacker);
        notifyArmyMembers(attacker, victim);

        markPlayerSharedHatredIfApplicable(victim, attacker);
        markPlayerSharedHatredIfApplicable(attacker, victim);
    }

    /**
     * 仇恨目标与召唤关系双向展开：
     *   召唤者 → 全部存活召唤物；召唤物 → 召唤者及其全部存活召唤物。
     * 返回顺序保持目标本身优先，供各仇恨名单逐项登记。
     */
    public static List<LivingEntity> expandSummonThreatTargets(
            LivingEntity target, ServerLevel level) {
        List<LivingEntity> out = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        addThreatTarget(out, seen, target);

        for (int i = 0; i < out.size(); i++) {
            if (!(out.get(i) instanceof AbstractArmySoldier soldier)) continue;
            if (soldier.isSummoned()) {
                addThreatTarget(out, seen, soldier.getSummoner());
            }
            for (AbstractArmySoldier summoned : soldier.getLivingSummonedSoldiers(level)) {
                addThreatTarget(out, seen, summoned);
            }
            for (AbstractArmySoldier nearby : level.getEntitiesOfClass(
                    AbstractArmySoldier.class,
                    soldier.getBoundingBox().inflate(GeneralConfig.armyFollowRange),
                    candidate -> candidate.isSummoned()
                            && soldier.getUUID().equals(candidate.getSummonerUuid()))) {
                addThreatTarget(out, seen, nearby);
            }
        }
        return out;
    }

    private static void addThreatTarget(List<LivingEntity> out, Set<UUID> seen,
                                        @Nullable LivingEntity target) {
        if (target == null || !target.isAlive() || target.isRemoved()) return;
        if (seen.add(target.getUUID())) out.add(target);
    }

    /** 玩家共享仇恨同样登记召唤关系闭包；军队名单覆盖整军，实体名单覆盖召唤链接。 */
    public static void markPlayerSharedHatred(PlayerCommandState state,
                                              LivingEntity target,
                                              ServerLevel level) {
        if (state == null || target == null) return;
        long nowGT = level.getGameTime();
        for (LivingEntity enemy : expandSummonThreatTargets(target, level)) {
            UUID enemyArmy = resolveArmyUuidOrphanFriendly(enemy);
            if (enemyArmy != null && !enemyArmy.equals(state.getOwnerUuid())) {
                state.markSharedHatredArmy(enemyArmy, nowGT);
            }
            state.markSharedHatredEntity(enemy.getUUID(), nowGT);
        }
        state.tickSharedHatred(nowGT);
        state.tickSharedHatredEntities(nowGT);
    }

    /** 节流窗口内的便宜刷新：只写首领仇恨表，不做整军广播或目标重设。 */
    private static void markArmyLeaderHatredOnly(LivingEntity member, LivingEntity enemy) {
        if (enemy == null || !enemy.isAlive()) return;
        if (member instanceof AbstractArmyCommander commander) {
            if (commander.canAttack(enemy)) commander.markAsHatred(enemy);
            return;
        }
        if (member instanceof AbstractArmySoldier soldier) {
            AbstractArmyCommander commander = soldier.getCommander();
            if (commander != null && commander.isAlive() && commander.canAttack(enemy)) {
                commander.markAsHatred(enemy);
            }
        }
    }

    // ===================== NPC 军队共享 =====================

    private static void notifyArmyMembers(LivingEntity member, LivingEntity enemy) {
        if (!(member.level() instanceof ServerLevel sl)) return;
        if (enemy == null || !enemy.isAlive()) return;

        UUID army = resolveArmyUuidOrphanFriendly(member);
        if (army == null) return;

        if (isPlayerArmyUuid(army, member.level())) return;

        Entity leader = sl.getEntities().get(army);
        boolean leaderKnows = false;
        if (leader instanceof AbstractArmyCommander cmd && cmd.isAlive()) {
            if (cmd.canAttack(enemy)) cmd.markAsHatred(enemy);
            for (LivingEntity h : cmd.getHatredList()) {
                if (h == enemy) { leaderKnows = true; break; }
            }
        }

        // 队长仇恨列表里已经有这个敌人 → 士兵会自动从队长仇恨列表锁定目标，
        //   不需要再做一次大范围实体扫描（群殴 / 连续命中时开销很大）。
        if (leaderKnows) return;

        if (leader instanceof AbstractArmyCommander commander) {
            // 首领已有编制缓存，直接通知自己的士兵，不再以受害者为中心扫世界。
            for (AbstractArmySoldier s : commander.getSoldiers()) {
                if (!s.isAlive() || s.isAttackStopped()) continue;
                if (!s.canAttack(enemy)) continue;
                LivingEntity cur = s.getTarget();
                if (cur == null || !cur.isAlive()) s.setTarget(enemy);
            }
            return;
        }

        double range = GeneralConfig.armyFollowRange * 2;
        AABB box = member.getBoundingBox().inflate(range);
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive()) continue;
            if (s == member) continue;

            UUID sArmy = s.getCommanderUuid().orElse(null);
            if (sArmy == null || !sArmy.equals(army)) continue;
            if (s.isAttackStopped()) continue;
            if (!s.canAttack(enemy)) continue;

            LivingEntity cur = s.getTarget();
            if (cur == null || !cur.isAlive()) {
                s.setTarget(enemy);
            }
        }
    }

    // ===================== 玩家共享仇恨 =====================

    private static void markPlayerSharedHatredIfApplicable(LivingEntity owner, LivingEntity enemy) {
        if (owner == null || enemy == null) return;
        if (CombatRules.isFriendly(owner, enemy)) return;
        if (!(owner.level() instanceof ServerLevel sl)) return;

        Player playerOwner = resolvePlayerOwner(owner);
        if (playerOwner == null) return;

        var st = PlayerArmyManager
                .get(playerOwner.getUUID());
        markPlayerSharedHatred(st, enemy, sl);
    }

    @Nullable
    private static Player resolvePlayerOwner(LivingEntity e) {
        if (e instanceof Player p) return p;
        if (e instanceof AbstractArmySoldier s && s.isPlayerArmy()) {
            return s.getCommanderPlayer();
        }
        if (e instanceof WarHorseEntity h) {
            UUID owner = h.getOwnerUuid();
            if (owner == null || !(h.level() instanceof ServerLevel sl)) return null;
            Entity o = sl.getEntities().get(owner);
            if (o instanceof Player p) return p;
            if (o instanceof AbstractArmySoldier s && s.isPlayerArmy()) {
                return s.getCommanderPlayer();
            }
        }
        return null;
    }

    // ===================== 点传播 =====================

    private static void dispatchToArmy(LivingEntity member, LivingEntity enemy) {
        if (enemy == null || !enemy.isAlive()) return;

        if (member instanceof AbstractArmyCommander c) {
            if (c.canAttack(enemy)) c.markAsHatred(enemy);

        } else if (member instanceof AbstractArmySoldier s) {
            AbstractArmyCommander cmd = s.getCommander();
            if (cmd != null && cmd.isAlive() && cmd.canAttack(enemy)) {
                cmd.markAsHatred(enemy);
            }
            if (cmd == null || !cmd.isAlive()) {
                if (s.canAttack(enemy)) {
                    // 点对点仇恨必须记录，但不能让每一次新命中都抢走正在追击的目标。
                    // 否则一圈远程单位轮流射击时，近战兵会不断转身，最终谁都追不上。
                    s.markPersonalHatred(enemy);
                    LivingEntity current = s.getTarget();
                    if (current == null || !current.isAlive()) {
                        s.setTarget(enemy);
                    }
                }
            }

        } else if (member instanceof WarHorseEntity h) {
            UUID owner = h.getOwnerUuid();
            if (owner != null && h.level() instanceof ServerLevel sl) {
                Entity o = sl.getEntities().get(owner);
                if (o instanceof LivingEntity le) {
                    dispatchToArmy(le, enemy);
                }
            }
        }
    }

    // ===================== 军队敌对关系 =====================

    private static void tryEstablishArmyHostility(LivingEntity victim, LivingEntity attacker) {
        UUID victimArmy = resolveArmyUuidOrphanFriendly(victim);
        UUID attackerArmy = resolveArmyUuidOrphanFriendly(attacker);
        if (victimArmy == null || attackerArmy == null) return;
        if (victimArmy.equals(attackerArmy)) return;

        ResourceLocation victimFaction = ArmyThreatUtil.factionOf(victim);
        ResourceLocation attackerFaction = ArmyThreatUtil.factionOf(attacker);
        if (victimFaction != null && attackerFaction != null
                && victimFaction.equals(attackerFaction)) {
            return;
        }

        boolean victimIsPlayer = isPlayerArmyUuid(victimArmy, victim.level());
        boolean attackerIsPlayer = isPlayerArmyUuid(attackerArmy, attacker.level());
        if (victimIsPlayer && attackerIsPlayer
                && !PlayerConfig.autoHostilityBetweenPlayerArmies) {
            return;
        }

        if (ArmyRelations.isHostile(victimArmy, attackerArmy)) return;
        ArmyRelations.setHostile(victimArmy, attackerArmy);
    }

    private static UUID resolveArmyUuidOrphanFriendly(LivingEntity e) {
        if (e instanceof AbstractArmyCommander c) return c.getUUID();
        if (e instanceof AbstractArmySoldier s) {
            Optional<UUID> cmd = s.getCommanderUuid();
            if (cmd.isPresent()) return cmd.get();
            return s.getUUID();
        }
        if (e instanceof WarHorseEntity h) {
            UUID ownerUuid = h.getOwnerUuid();
            if (ownerUuid == null) return null;
            if (!(h.level() instanceof ServerLevel sl)) return null;
            Entity owner = sl.getEntities().get(ownerUuid);
            if (owner instanceof LivingEntity le) return resolveArmyUuidOrphanFriendly(le);
            return ownerUuid;
        }
        if (e instanceof Player p) return p.getUUID();
        return null;
    }

    private static boolean isPlayerArmyUuid(UUID army, Level level) {
        if (army == null) return false;
        if (!(level instanceof ServerLevel sl)) return false;
        if (sl.getServer().getPlayerList().getPlayer(army) != null) return true;
        var pc = sl.getServer().getProfileCache();
        return pc != null && pc.get(army).isPresent();
    }
}
