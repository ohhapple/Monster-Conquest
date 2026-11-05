package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.arena.trait.ArenaTraitAnalyzer;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitRules;
import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;

import java.util.List;

/** 自走棋弈子最终属性；战斗实体、准备展示体和客户端 tooltip 共用。 */
public final class ArenaStatCalculator {

    public record Stats(
            double maxHealth,
            double attackDamage,
            double armor,
            double magicResistance,
            double attackSpeed,
            double movementSpeed,
            double magicPower,
            double dodgeChance,
            double healingPower,
            double lifesteal,
            double initialSkillCharge,
            double skillChargeGainPercent,
            double critChance,
            double critDamage,
            boolean skillCritEnabled,
            double attackRangeBonus,
            double shieldPower,
            double incomingDamageReduction,
            double skillDamageBonus,
            double startingShieldRatio,
            double traitAttackPercent,
            double traitAttackSpeedPercent,
            double traitMovementPercent) {
    }

    private ArenaStatCalculator() {
    }

    public static Stats calculate(ArenaPiece piece,
                                  List<ArenaPiece> roster) {
        return calculate(
                piece.unitIndex(),
                piece.star(),
                piece.equipment().stream().map(ArenaEquipment::ordinal).toList(),
                roster.stream().map(ArenaPiece::unitIndex).toList());
    }

    public static Stats calculate(int unitIndex, int star,
                                  List<Integer> equipmentIds,
                                  List<Integer> rosterUnitIndices) {
        ArenaUnit unit = ArenaUnitCatalog.get(unitIndex);
        double healthMultiplier = switch (Math.max(1, Math.min(3, star))) {
            case 2 -> 1.8;
            case 3 -> 3.24;
            default -> 1.0;
        };
        double damageMultiplier = switch (Math.max(1, Math.min(3, star))) {
            case 2 -> 1.5;
            case 3 -> 2.25;
            default -> 1.0;
        };
        double armorMultiplier = damageMultiplier;

        double equipmentHealth = 0.0;
        double equipmentArmor = 0.0;
        double equipmentMagicResistance = 0.0;
        double equipmentAttackPercent = 0.0;
        double equipmentAttackSpeedPercent = 0.0;
        double equipmentMovementSpeedPercent = 0.0;
        double equipmentMagicPower = 0.0;
        double equipmentDodgeChance = 0.0;
        double equipmentHealingPower = 0.0;
        double equipmentLifesteal = 0.0;
        double equipmentInitialSkillCharge = 0.0;
        double equipmentSkillChargeGain = 0.0;
        double equipmentCritChance = 0.0;
        double equipmentCritDamage = 0.0;
        boolean skillCritEnabled = false;
        for (int equipmentId : equipmentIds) {
            ArenaEquipment equipment = ArenaEquipment.byId(equipmentId);
            ArenaEquipmentStats stats = equipment.stats();
            equipmentHealth += stats.health();
            equipmentArmor += stats.armor();
            equipmentMagicResistance += stats.magicResistance();
            equipmentAttackPercent += stats.attackPercent();
            equipmentAttackSpeedPercent += stats.attackSpeedPercent();
            equipmentMovementSpeedPercent += stats.movementSpeedPercent();
            equipmentMagicPower += stats.magicPower();
            equipmentDodgeChance += stats.dodgeChance();
            equipmentHealingPower += stats.healingPower();
            equipmentLifesteal += stats.lifesteal();
            equipmentInitialSkillCharge += stats.initialSkillCharge();
            equipmentSkillChargeGain += stats.skillChargeGainPercent();
            equipmentCritChance += stats.critChance();
            equipmentCritDamage += stats.critDamage();
            skillCritEnabled |= stats.skillCritEnabled();
        }

        ArenaTraitAnalyzer.State traits =
                ArenaTraitAnalyzer.analyze(rosterUnitIndices);
        ArenaTraitRules.StatModifiers traitRules =
                ArenaTraitRules.modifiersFor(
                        unitIndex, traits);
        double movementSpeed = unit.movementSpeed()
                * (1.0 + equipmentMovementSpeedPercent
                + traitRules.movementPercent());
        return new Stats(
                unit.baseHealth() * healthMultiplier + equipmentHealth,
                unit.baseDamage() * damageMultiplier
                        * (1.0 + equipmentAttackPercent
                        + traitRules.attackPercent()),
                unit.armor() * armorMultiplier + equipmentArmor
                        + traitRules.armor(),
                unit.magicResistance() + equipmentMagicResistance
                        + traitRules.magicResistance(),
                unit.attackSpeed() * (1.0 + equipmentAttackSpeedPercent
                        + traitRules.attackSpeedPercent()),
                movementSpeed,
                equipmentMagicPower + traitRules.magicPower(),
                equipmentDodgeChance + traitRules.dodgeChance(),
                equipmentHealingPower + traitRules.healingPower(),
                equipmentLifesteal,
                equipmentInitialSkillCharge,
                equipmentSkillChargeGain
                        + traitRules.skillChargeGainPercent(),
                Math.min(1.0,
                        ArenaEquipmentRuntime.BASE_CRIT_CHANCE
                                + equipmentCritChance
                                + traitRules.critChance()),
                ArenaEquipmentRuntime.BASE_CRIT_DAMAGE
                        + equipmentCritDamage
                        + traitRules.critDamage(),
                skillCritEnabled,
                traitRules.attackRangeBonus(),
                traitRules.shieldPower(),
                traitRules.incomingDamageReduction(),
                traitRules.skillDamageBonus(),
                traitRules.startingShieldRatio(),
                traitRules.attackPercent(),
                traitRules.attackSpeedPercent(),
                traitRules.movementPercent());
    }
}
