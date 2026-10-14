package com.ohhapple.monster_conquest.world;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.util.PeacefulUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 村庄防御：村庄巡逻队像铁傀儡一样保护村民。
 *   村民被攻击时，附近"属于同一村庄"的村民阵营巡逻队会立刻锁定攻击者。
 *   只对随村庄自然刷出的单位生效；刷怪蛋 / 指令 / 玩家招募的单位不受影响。
 */
public final class VillageDefense {

    private VillageDefense() {}

    public static void onVillagerAttacked(LivingEntity villager, LivingEntity attacker) {
        if (!FactionConfig.villagerVillageSpawnEnabled) return;
        if (!FactionConfig.villagerVillageDefend) return;
        if (!(villager instanceof Villager)) return;
        if (!(villager.level() instanceof ServerLevel level)) return;
        if (PeacefulUtil.isPeaceful(villager)) return;
        if (attacker == null || attacker == villager || !attacker.isAlive()) return;

        double range = Math.max(32.0, FactionConfig.villagerVillageRadius);
        AABB box = villager.getBoundingBox().inflate(range);

        for (AbstractArmyCommander commander : level.getEntitiesOfClass(AbstractArmyCommander.class, box)) {
            if (!commander.isAlive() || !commander.isVillageBound()) continue;
            if (!FactionIds.VILLAGER.equals(commander.getFactionId())) continue;
            if (!commander.getAnchor().contains(villager.position())) continue;
            // 复用"被攻击后反击"机制：把攻击者登记为自身攻击者，
            //   这样 canAttack / isEnemyOf 会放行（即使攻击者（如玩家）本来不在敌对名单里）。
            commander.setLastHurtByMob(attacker);
            if (commander.canAttack(attacker)) commander.markAsHatred(attacker);
        }

        for (AbstractArmySoldier soldier : level.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!soldier.isAlive() || !soldier.getAnchor().isVillage()) continue;
            if (!FactionIds.VILLAGER.equals(soldier.getFactionId())) continue;
            if (!soldier.getAnchor().contains(villager.position())) continue;
            soldier.setLastHurtByMob(attacker);
            if (!soldier.canAttack(attacker)) continue;
            LivingEntity current = soldier.getTarget();
            if (current == null || !current.isAlive()) soldier.setTarget(attacker);
        }
    }

    /**
     * 袭击期间：村庄巡逻队锁定所有袭击单位。
     *   · 不要求视线：仇恨由本方法持续刷新，不会在中途过期或被清除；
     *   · 直到袭击结束才停止刷新（之后仇恨按正常规则自然过期）；
     *   · 只作用于随村庄刷出的村民阵营单位（玩家 / 指令 / 其他方式生成的不受影响）。
     */
    public static void refreshRaidAlert(ServerLevel level, Player player) {
        if (PeacefulUtil.isPeaceful(player)) return;

        AABB box = player.getBoundingBox().inflate(128.0);

        // 同一场袭击的本模组参战部队只统计一次（避免每个村庄首领各查一遍实体，降低服务器开销）
        java.util.Map<Integer, List<LivingEntity>> raidUnitsCache = new java.util.HashMap<>();

        for (AbstractArmyCommander commander : level.getEntitiesOfClass(
                AbstractArmyCommander.class, box,
                c -> c.isAlive() && c.isVillageBound()
                        && FactionIds.VILLAGER.equals(c.getFactionId()))) {
            Raid raid = activeRaidAt(level, commander.getAnchorPos());
            if (raid == null) continue;
            List<LivingEntity> raidUnits = raidUnitsCache.computeIfAbsent(
                    raid.getId(), id -> collectRaidUnits(level, raid));
            markRaidUnits(raid, commander, raidUnits);
        }

        for (AbstractArmySoldier soldier : level.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive() && s.getAnchor().isVillage()
                        && FactionIds.VILLAGER.equals(s.getFactionId()))) {
            Raid raid = activeRaidAt(level, soldier.getAnchor().getPos());
            if (raid == null) continue;

            // 孤儿士兵（队长已阵亡）：没有首领仇恨列表，直接锁定最近的袭击单位
            AbstractArmyCommander commander = soldier.getCommander();
            if (commander != null && commander.isAlive()) continue;
            LivingEntity current = soldier.getTarget();
            if (current != null && current.isAlive()) continue;
            LivingEntity nearest = nearestRaidUnit(level, raid, soldier);
            if (nearest != null) soldier.setTarget(nearest);
        }
    }

    /** 该村庄锚点处正在进行的袭击（没有则返回 null）。 */
    @Nullable
    private static Raid activeRaidAt(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return null;
        Raid raid = level.getRaidAt(pos);
        if (raid == null || !raid.isStarted() || !raid.isActive() || raid.isOver()) return null;
        return raid;
    }

    /** 本模组在这场袭击里的参战部队（绑定了袭击锚点的灾厄单位）。 */
    private static List<LivingEntity> collectRaidUnits(ServerLevel level, Raid raid) {
        double radius = Math.max(96.0, FactionConfig.illagerRaidRadius + 64.0);
        AABB area = new AABB(raid.getCenter()).inflate(radius);
        List<LivingEntity> out = new ArrayList<>();
        for (AbstractArmySoldier soldier : level.getEntitiesOfClass(AbstractArmySoldier.class, area,
                s -> s.isAlive() && s.getAnchor().isRaid()
                        && FactionIds.ILLAGER.equals(s.getFactionId()))) {
            out.add(soldier);
        }
        for (AbstractArmyCommander other : level.getEntitiesOfClass(AbstractArmyCommander.class, area,
                c -> c.isAlive() && c.getAnchor().isRaid()
                        && FactionIds.ILLAGER.equals(c.getFactionId()))) {
            out.add(other);
        }
        return out;
    }

    /** 把整场袭击的所有单位都登记进首领的仇恨列表（持续刷新，直到袭击结束）。 */
    private static void markRaidUnits(Raid raid, AbstractArmyCommander commander,
                                      List<LivingEntity> raidUnits) {
        for (Raider raider : raid.getAllRaiders()) {
            if (raider == null || !raider.isAlive() || raider.isRemoved()) continue;
            if (raider.level() != raid.getLevel()) continue;
            commander.markAsHatred(raider);
        }
        for (LivingEntity unit : raidUnits) {
            if (unit == commander) continue;
            commander.markAsHatred(unit);
        }
    }

    /** 距离指定单位最近的袭击单位（原版掠夺者）。 */
    @Nullable
    private static LivingEntity nearestRaidUnit(ServerLevel level, Raid raid, LivingEntity from) {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Raider raider : raid.getAllRaiders()) {
            if (raider == null || !raider.isAlive() || raider.isRemoved()) continue;
            if (raider.level() != level) continue;
            double dist = from.distanceToSqr(raider);
            if (dist < bestDist) {
                bestDist = dist;
                best = raider;
            }
        }
        return best;
    }
}
