package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.arena.entity.ArenaLootDrop;
import net.minecraft.util.RandomSource;

import java.util.List;

/** 野怪回合、数量、属性缩放与奖励公式的独立规则。 */
public final class ArenaWildRules {

    public static final int ROUNDS_PER_STAGE = 7;
    public static final int MAX_WILD_COUNT = 6;
    public static final float MONSTER_BONUS_ITEM_DROP_CHANCE = 0.50f;

    public record StatScaling(
            double health,
            double attack,
            double armor,
            double magicResistance,
            double attackSpeed,
            double movement,
            double magicPower) {

        public ArenaStatCalculator.Stats apply(
                ArenaStatCalculator.Stats stats) {
            return new ArenaStatCalculator.Stats(
                    Math.max(1.0, stats.maxHealth() * health),
                    Math.max(0.0, stats.attackDamage() * attack),
                    Math.max(0.0, stats.armor() * armor),
                    Math.max(0.0,
                            stats.magicResistance() * magicResistance),
                    Math.max(0.05, stats.attackSpeed() * attackSpeed),
                    Math.max(0.01, stats.movementSpeed() * movement),
                    stats.magicPower() + magicPower,
                    stats.dodgeChance(),
                    stats.healingPower(),
                    stats.lifesteal(),
                    stats.initialSkillCharge(),
                    stats.skillChargeGainPercent(),
                    stats.critChance(),
                    stats.critDamage(),
                    stats.skillCritEnabled(),
                    stats.attackRangeBonus(),
                    stats.shieldPower(),
                    stats.incomingDamageReduction(),
                    stats.skillDamageBonus(),
                    stats.startingShieldRatio(),
                    stats.traitAttackPercent(),
                    stats.traitAttackSpeedPercent(),
                    stats.traitMovementPercent());
        }
    }

    private ArenaWildRules() {
    }

    public static int stageForRound(int absoluteRound) {
        return (Math.max(1, absoluteRound) - 1)
                / ROUNDS_PER_STAGE + 1;
    }

    public static boolean isWildRound(int absoluteRound) {
        int safeRound = Math.max(1, absoluteRound);
        int stage = stageForRound(safeRound);
        int step = (safeRound - 1) % ROUNDS_PER_STAGE + 1;
        return stage == 1 && step <= 3
                || step == ROUNDS_PER_STAGE;
    }

    public static int wildCount(int stage) {
        return Math.min(MAX_WILD_COUNT,
                Math.max(1, stage));
    }

    public static int goldReward(int stage) {
        return 2 + Math.max(1, stage) * 2;
    }

    public static ArenaEquipment randomBasicComponent(
            RandomSource random) {
        List<ArenaEquipment> components =
                ArenaEquipment.basicComponents();
        return components.get(
                random.nextInt(components.size()));
    }

    public static ArenaEquipment randomEquipmentReward(
            RandomSource random) {
        List<ArenaEquipment> components =
                ArenaEquipment.basicComponents();
        int roll = random.nextInt(components.size() + 1);
        return roll < components.size()
                ? components.get(roll)
                : ArenaEquipment.REMOVER;
    }

    /** 每只野怪独立判定一次额外掉落。 */
    public static ArenaEquipment rollMonsterBonusReward(
            RandomSource random) {
        return random.nextFloat() < MONSTER_BONUS_ITEM_DROP_CHANCE
                ? randomEquipmentReward(random) : null;
    }

    public static boolean applyReward(
            ArenaPlayerState owner, int rewardType,
            int payload) {
        if (owner == null) return false;
        if (rewardType == ArenaLootDrop.TYPE_GOLD) {
            owner.addGold(payload);
            return true;
        }
        if (rewardType != ArenaLootDrop.TYPE_EQUIPMENT
                || !validEquipmentReward(payload)) {
            return false;
        }
        return owner.addEquipment(
                ArenaEquipment.byId(payload));
    }

    public static boolean validEquipmentReward(int payload) {
        return payload >= 0
                && payload < ArenaEquipment.values().length
                && isEquipmentReward(ArenaEquipment.byId(payload));
    }

    private static boolean isEquipmentReward(
            ArenaEquipment equipment) {
        return equipment.component()
                || equipment == ArenaEquipment.REMOVER;
    }

    public static StatScaling scalingForStage(int stage) {
        int safeStage = Math.max(1, Math.min(
                MAX_WILD_COUNT, stage));
        double steps = safeStage - 1;
        return new StatScaling(
                0.28 + steps * 0.28,
                0.28 + steps * 0.20,
                steps * 0.25,
                steps * 0.20,
                0.72 + steps * 0.06,
                0.92 + steps * 0.02,
                steps * 10.0);
    }
}
