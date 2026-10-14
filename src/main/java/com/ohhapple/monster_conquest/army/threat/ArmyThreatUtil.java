package com.ohhapple.monster_conquest.army.threat;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 阵营级威胁传播：同阵营但不同军队（不同首领 / 孤儿士兵 / 玩家阵营单位）
 * 互相支援 —— 对每个同阵营单位建立军队敌对关系并设置目标。
 */
public final class ArmyThreatUtil {

    private ArmyThreatUtil() {}

    /** 获取实体所属阵营（含玩家阵营）。 */
    @Nullable
    public static ResourceLocation factionOf(Entity e) {
        if (e instanceof AbstractArmyCommander c) return c.getFactionId();
        if (e instanceof AbstractArmySoldier s) return s.getFactionId();
        if (e instanceof WarHorseEntity h) {
            var owner = h.getOwnerUuid();
            if (owner == null || !(h.level() instanceof ServerLevel sl)) return null;
            var o = sl.getEntities().get(owner);
            return o == null ? null : factionOf(o);
        }
        if (e instanceof Player p) {
            return FactionIds.player(p.getUUID());
        }
        return null;
    }

    public static void propagateThreatToFaction(LivingEntity victim, LivingEntity attacker) {
        if (victim.level().isClientSide) return;
        if (!(victim.level() instanceof ServerLevel sl)) return;
        if (attacker == null || !attacker.isAlive()) return;
        if (attacker instanceof Player p && p.isCreative()) return;

        ResourceLocation faction = factionOf(victim);
        if (faction == null) return;

        ResourceLocation attackerFaction = factionOf(attacker);
        // 单向结盟：任一方向结盟 → 不传播
        if (attackerFaction != null
                && (FactionAlly.isAlly(faction, attackerFaction)
                || FactionAlly.isAlly(attackerFaction, faction))) {
            return;
        }

        // 同阵营之间不传播（友军互打交给 canAttack 处理）
        if (attackerFaction != null && faction.equals(attackerFaction)) return;

        UUID attackerArmy = AbstractArmySoldier.resolveArmyUuid(attacker, sl);

        double range = GeneralConfig.factionSupportRange;
        AABB box = victim.getBoundingBox().inflate(range);

        // ===================== 1) 通知同阵营 NPC 首领 =====================
        for (AbstractArmyCommander c : sl.getEntitiesOfClass(AbstractArmyCommander.class, box)) {
            if (!c.isAlive()) continue;
            if (c == victim) continue;
            if (!faction.equals(c.getFactionId())) continue;
            if (!c.canAttack(attacker)) continue;

            if (attackerArmy != null && !c.getUUID().equals(attackerArmy)) {
                ArmyRelations.setHostile(c.getUUID(), attackerArmy);
            }
            c.markAsHatred(attacker);
        }

        // ===================== 2) 通知同阵营所有士兵（含孤儿、含所有首领麾下） =====================
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive()) continue;
            if (s == victim) continue;
            if (!faction.equals(s.getFactionId())) continue;

            UUID sArmy = AbstractArmySoldier.resolveArmyUuid(s, sl);
            if (sArmy != null && attackerArmy != null && !sArmy.equals(attackerArmy)) {
                ArmyRelations.setHostile(sArmy, attackerArmy);
            }

            // 只对没有活目标的士兵设 target：
            //   混战里无条件刷目标会把整军 target 刷成最新攻击者，来回跑打不到人；
            //   有活目标 → 保留，否则才设新目标。
            LivingEntity cur = s.getTarget();
            if ((cur == null || !cur.isAlive()) && s.canAttack(attacker)) {
                s.setTarget(attacker);
            }
        }
    }
}
