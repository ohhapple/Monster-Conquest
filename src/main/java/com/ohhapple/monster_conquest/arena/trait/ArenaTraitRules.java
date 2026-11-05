package com.ohhapple.monster_conquest.arena.trait;

public final class ArenaTraitRules {

    private static final StatModifiers EMPTY = new StatModifiers(
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            0.0, 0.0, 0.0);

    public record StatModifiers(
            double attackPercent,
            double attackSpeedPercent,
            double movementPercent,
            double armor,
            double magicResistance,
            double dodgeChance,
            double healingPower,
            double skillChargeGainPercent,
            double magicPower,
            double critChance,
            double critDamage,
            double attackRangeBonus,
            double shieldPower,
            double incomingDamageReduction,
            double skillDamageBonus,
            double startingShieldRatio) {
    }

    private ArenaTraitRules() {
    }

    public static StatModifiers emptyModifiers() {
        return EMPTY;
    }

    public static StatModifiers modifiersFor(
            int unitIndex, ArenaTraitAnalyzer.State state) {
        boolean undead = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.UNDEAD);
        boolean beast = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.BEAST);
        boolean nether = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.NETHER);
        boolean ocean = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.OCEAN);
        boolean end = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.END);
        boolean arthropod = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.ARTHROPOD);
        boolean construct = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.CONSTRUCT);
        boolean villager = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.VILLAGER);
        boolean herd = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.HERD);
        boolean gunner = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.GUNNER);
        boolean guardian = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.GUARDIAN);
        boolean arcane = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.ARCANE);
        boolean raider = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.RAIDER);
        boolean bulwark = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.BULWARK);
        boolean ambush = ArenaTraitCatalog.hasTrait(
                unitIndex, ArenaTrait.AMBUSH);

        double attackPercent = 0.0;
        double attackSpeedPercent = 0.0;
        double movementPercent = 0.0;
        double armor = 0.0;
        double magicResistance = 0.0;
        double dodgeChance = 0.0;
        double healingPower = 0.0;
        double skillChargeGain = 0.0;
        double magicPower = 0.0;
        double critChance = 0.0;
        double critDamage = 0.0;
        double attackRange = 0.0;
        double shieldPower = 0.0;
        double damageReduction = 0.0;
        double skillDamageBonus = 0.0;
        double startingShieldRatio = 0.0;

        if (undead && state.active(ArenaTrait.UNDEAD, 4)) {
            attackPercent += 0.25;
        }
        if (undead && state.active(ArenaTrait.UNDEAD, 8)) {
            skillChargeGain += 0.40;
        }

        if (beast && state.active(ArenaTrait.BEAST, 2)) {
            movementPercent += 0.20;
            dodgeChance += 0.08;
        }
        if (beast && state.active(ArenaTrait.BEAST, 4)) {
            attackSpeedPercent += 0.30;
        }
        if (state.active(ArenaTrait.BEAST, 8)) {
            attackPercent += 0.25;
            if (beast) attackSpeedPercent += 0.30;
        }

        if (nether && state.active(ArenaTrait.NETHER, 4)) {
            attackPercent += 0.30;
            magicPower += 35.0;
        }
        if (nether && state.active(ArenaTrait.NETHER, 8)) {
            attackPercent += 0.10;
        }
        if (ocean && state.active(ArenaTrait.OCEAN, 2)) {
            magicResistance += 30.0;
        }
        if (ocean && state.active(ArenaTrait.OCEAN, 3)) {
            startingShieldRatio += 0.30;
        }
        if (end && state.active(ArenaTrait.END, 2)) {
            dodgeChance += 0.25;
        }
        if (construct && state.active(ArenaTrait.CONSTRUCT, 2)) {
            armor += 20.0;
            magicResistance += 20.0;
        }
        if (construct && state.active(ArenaTrait.CONSTRUCT, 3)) {
            startingShieldRatio += 0.25;
        }
        if (villager && state.active(ArenaTrait.VILLAGER, 2)) {
            healingPower += 0.25;
            skillChargeGain += 0.20;
        }
        if (herd && state.active(ArenaTrait.HERD, 2)) {
            movementPercent += 0.15;
        }
        if (herd && state.active(ArenaTrait.HERD, 4)) {
            armor += 12.0;
            magicResistance += 12.0;
        }
        if (state.active(ArenaTrait.HERD, 8)) {
            attackSpeedPercent += 0.10;
        }
        if (state.active(ArenaTrait.HERD, 10)) {
            attackPercent += 0.15;
            if (herd) startingShieldRatio += 0.15;
        }
        if (gunner && state.active(ArenaTrait.GUNNER, 2)) {
            attackSpeedPercent += 0.25;
            attackRange += 0.75;
        }
        if (guardian && state.active(ArenaTrait.GUARDIAN, 2)) {
            armor += 15.0;
            shieldPower += 0.25;
        }
        if (guardian && state.active(ArenaTrait.GUARDIAN, 4)) {
            damageReduction += 0.15;
        }
        if (state.active(ArenaTrait.GUARDIAN, 6)) {
            damageReduction += 0.12;
            if (guardian) startingShieldRatio += 0.30;
        }
        if (bulwark && state.active(ArenaTrait.BULWARK, 2)) {
            armor += 15.0;
            magicResistance += 15.0;
            shieldPower += 0.20;
        }
        if (bulwark && state.active(ArenaTrait.BULWARK, 4)) {
            damageReduction += 0.10;
        }
        if (state.active(ArenaTrait.BULWARK, 10)) {
            damageReduction += 0.08;
            if (bulwark) startingShieldRatio += 0.25;
        }
        if (arcane && state.active(ArenaTrait.ARCANE, 2)) {
            magicPower += 40.0;
        }
        if (arcane) {
            skillDamageBonus += arcaneSkillDamageBonus(
                    state.tier(ArenaTrait.ARCANE));
        }
        if (arcane && state.active(ArenaTrait.ARCANE, 4)) {
            skillChargeGain += 0.20;
        }
        if (raider && state.active(ArenaTrait.RAIDER, 6)) {
            critChance += 0.25;
            critDamage += 0.30;
        }
        if (ambush && state.active(ArenaTrait.AMBUSH, 2)) {
            movementPercent += 0.15;
            critChance += 0.10;
        }
        if (ambush && state.active(ArenaTrait.AMBUSH, 4)) {
            attackSpeedPercent += 0.15;
        }
        if (ambush && state.active(ArenaTrait.AMBUSH, 6)) {
            critDamage += 0.20;
        }
        if (ambush && state.active(ArenaTrait.AMBUSH, 8)) {
            critChance += 0.15;
        }
        if (ambush && state.active(ArenaTrait.AMBUSH, 10)) {
            attackPercent += 0.15;
        }

        return new StatModifiers(
                attackPercent, attackSpeedPercent, movementPercent,
                armor, magicResistance, dodgeChance, healingPower,
                skillChargeGain, magicPower, critChance, critDamage,
                attackRange, shieldPower,
                Math.min(0.80, damageReduction),
                skillDamageBonus, startingShieldRatio);
    }

    public static int arcaneChargePerSecond(int tier) {
        return switch (tier) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 5;
            default -> 0;
        };
    }

    public static double arcaneSkillDamageBonus(int tier) {
        return switch (tier) {
            case 2 -> 0.25;
            case 3 -> 0.55;
            case 4 -> 1.15;
            default -> 0.0;
        };
    }
}
