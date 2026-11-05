package com.ohhapple.monster_conquest.client.gui.arena;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.arena.game.ArenaStatCalculator;
import com.ohhapple.monster_conquest.arena.unit.ArenaSkillType;
import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** 使用战斗结算同一套公式生成弈子技能 tooltip。 */
public final class ArenaSkillTooltip {

    private static final String PREFIX =
            "gui.monster_conquest.arena.game.unit_tooltip.skill.";

    private ArenaSkillTooltip() {
    }

    public static List<Component> describe(
            ArenaUnit unit, int star, ArenaStatCalculator.Stats stats) {
        ArenaSkillType type = unit.skillType();
        int safeStar = Math.max(1, Math.min(3, star));
        String base = PREFIX + type.name().toLowerCase(Locale.ROOT);
        return List.of(
                Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.skill",
                        Component.translatable(base + ".name")),
                Component.translatable(
                        "gui.monster_conquest.arena.game.unit_tooltip.skill_charge",
                        unit.skillChargeRequired()),
                description(type, safeStar, stats, base));
    }

    private static Component description(
            ArenaSkillType type, int star,
            ArenaStatCalculator.Stats stats, String base) {
        double ap = stats.magicPower();
        return switch (type) {
            case ZOMBIE_SHIELD -> Component.translatable(
                    base + ".description",
                    formatStat(stats.maxHealth()
                            * ArenaSkillBalance.shieldRatio(
                            0.20, star, ap)));
            case SKELETON_VOLLEY -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.SKELETON_TARGETS,
                    formatStat(stats.attackDamage()));
            case STRAY_ARCANE_SHOTS -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.STRAY_SHOTS,
                    formatStat(stats.attackDamage()
                            * ArenaSkillBalance.strayBonusAttackRatio(
                            star, ap)));
            case DROWNED_TRIDENT_STUN -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.DROWNED_TRIDENT_COUNT,
                    formatSeconds(ArenaSkillBalance
                            .drownedTridentStunTicks(star)),
                    formatPercent(1.0),
                    formatSeconds(ArenaSkillBalance
                            .drownedTridentDebuffTicks(star)),
                    ArenaSkillBalance
                            .DROWNED_TRIDENT_ARMOR_BREAK_LEVEL,
                    formatPercent(ArenaSkillBalance
                            .DROWNED_TRIDENT_MR_REDUCTION));
            case ZOMBIE_VILLAGER_STUN -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.zombieVillagerStunTicks(star)),
                    formatPercent(ArenaSkillBalance
                            .ZOMBIE_VILLAGER_HEAL_RATIO));
            case HUSK_BATTLE_GROWTH -> Component.translatable(
                    base + ".description",
                    formatStat(ArenaSkillBalance.huskHealthGain(star, ap)),
                    formatStat(ArenaSkillBalance.huskAttackGain(star, ap)),
                    formatStat(ArenaSkillBalance.huskArmorGain(star, ap)),
                    formatStat(ArenaSkillBalance.huskMagicResistanceGain(
                            star, ap)));
            case SNOW_GOLEM_SNOWBALL_FAN -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.SNOW_GOLEM_SNOWBALL_COUNT,
                    ArenaSkillBalance
                            .SNOW_GOLEM_LAUNCH_INTERVAL_TICKS,
                    formatPercent(ArenaSkillBalance
                            .snowGolemSnowballAttackMultiplier(
                                    star, ap)),
                    formatSeconds(
                            ArenaSkillBalance.snowGolemFreezeTicks(star)),
                    ArenaSkillBalance.SNOW_GOLEM_MAGIC_RESIST_STACKS * 15,
                    formatSeconds(
                            ArenaSkillBalance.snowGolemMagicResistTicks(star)));
            case SPIDER_SLOW_FIELD -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.SPIDER_SLOW_AMPLIFIER + 1,
                    formatSeconds(
                            ArenaSkillBalance.spiderSlowTicks(star)));
            case BREEZE_GALE_CHARGE -> Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.breezeGaleAttackMultiplier(
                                    star, ap)),
                    formatSeconds(
                            ArenaSkillBalance.breezeSilenceTicks(star)));
            case ARCHER_STRENGTH -> Component.translatable(
                    base + ".description",
                    formatPercent(ArenaSkillBalance.archerStrengthBonus(
                            star, ap)),
                    formatSeconds(ArenaSkillBalance.standardBuffTicks(star)));
            case VINDICATOR_EXECUTE -> Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.vindicatorAttackMultiplier(
                                    star, ap) - 1.0),
                    formatPercent(
                            ArenaSkillBalance.vindicatorExecuteThreshold(
                                    star, ap)));
            case MACE_LEAP_SMASH -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.MACE_LEAP_COUNT,
                    formatPercent(
                            ArenaSkillBalance.maceAttackMultiplier(star, ap)));
            case WOLF_CALL -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.wolfSpeedTicks(star)));
            case GOAT_CHARGE -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.goatStunTicks(star)));
            case RAVAGER_KNOCKUP_HEAL -> Component.translatable(
                    base + ".description",
                    formatStat(ArenaSkillBalance
                            .ravagerBloodShieldMultiplier(star)),
                    formatStat(
                            ArenaSkillBalance.RAVAGER_KNOCKUP_RADIUS),
                    formatSeconds(
                            ArenaSkillBalance.ravagerStunTicks(star)),
                    formatPercent(ArenaSkillBalance
                            .ravagerAttackMultiplier(ap)),
                    formatPercent(
                            ArenaSkillBalance.ravagerMissingHealthRatio(
                                    star, ap)));
            case POLAR_BEAR_FORM -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.POLAR_BEAR_FORM_TICKS),
                    formatStat(ArenaSkillBalance.polarBearHealthBonus(
                            star, ap)),
                    formatPercent(
                            ArenaSkillBalance.polarBearAttackSpeedMultiplier(
                                    star, ap) - 1.0),
                    formatPercent(
                            ArenaSkillBalance.polarBearMovementSpeedBonus(
                                    star, ap)),
                    formatPercent(
                            ArenaSkillBalance.polarBearLifestealBonus(
                                    star, ap)),
                    formatSeconds(
                            ArenaSkillBalance.polarBearFrostTicks(star)));
            case HOGLIN_CHARGE -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.HOGLIN_CHARGE_TICKS),
                    formatPercent(
                            ArenaSkillBalance.hoglinChargeAttackMultiplier(
                                    star, ap)));
            case BOGGED_POISON_VOLLEY -> Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.boggedAttackMultiplier(star, ap)),
                    formatSeconds(
                            ArenaSkillBalance.boggedPoisonTicks(star)),
                    ArenaSkillBalance.BOGGED_POISON_AMPLIFIER + 1);
            case RIFLE_RAPID_FIRE -> Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.rifleAttackSpeedMultiplier(
                                    star, ap) - 1.0),
                    formatSeconds(ArenaSkillBalance.standardBuffTicks(star)));
            case PIGLIN_TAUNT_SHIELD -> Component.translatable(
                    base + ".description",
                    formatStat(stats.maxHealth()
                            * ArenaSkillBalance.shieldRatio(
                            0.40, star, ap)),
                    star,
                    formatSeconds(ArenaSkillBalance.standardBuffTicks(star)));
            case WITHER_DRAIN -> star
                    >= ArenaSkillBalance.WITHER_DOMINATION_STAR
                    ? Component.translatable(
                    base + ".description.domination",
                    formatPercent(
                            ArenaSkillBalance.witherDrainRate(star, ap)))
                    : Component.translatable(
                    base + ".description",
                    ArenaSkillBalance.witherTargetLimit(star),
                    formatSeconds(
                            ArenaSkillBalance.witherMarkTicks(star)),
                    formatPercent(
                            ArenaSkillBalance.witherDrainRate(star, ap)));
            case SNIPER_EXECUTE_SHOT -> star >= 3
                    ? Component.translatable(base + ".description.execute")
                    : Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.sniperMaxHealthRatio(
                                    star, ap)));
            case EVOKER_FANG_STUN -> star >= 3
                    ? Component.translatable(
                    base + ".description.three_star",
                    formatSeconds(
                            ArenaSkillBalance.evokerFangStunTicks(star)))
                    : Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.evokerFangStunTicks(star)));
            case WITCH_LINGERING_POTION -> Component.translatable(
                    base + ".description",
                    formatStat(ArenaSkillBalance
                            .WITCH_LINGERING_RADIUS),
                    formatSeconds(ArenaSkillBalance
                            .WITCH_LINGERING_DURATION_TICKS),
                    ArenaSkillBalance
                            .WITCH_LINGERING_DAMAGE_INTERVAL_TICKS,
                    formatPercent(ArenaSkillBalance
                            .WITCH_LINGERING_ATTACK_RATIO),
                    formatPercent(ArenaSkillBalance
                            .WITCH_LINGERING_ALLY_HEAL_RATIO));
            case IRON_GOLEM_LEAP_SMASH -> Component.translatable(
                    base + ".description",
                    formatStat(
                            ArenaSkillBalance.IRON_GOLEM_LEAP_RADIUS),
                    formatPercent(
                            ArenaSkillBalance.ironGolemAttackMultiplier(
                                    star, ap)),
                    formatSeconds(
                            ArenaSkillBalance.ironGolemStunTicks(star)),
                    formatStat(stats.maxHealth()
                            * ArenaSkillBalance.ironGolemShieldRatio(
                            star, ap)));
            case CAVE_SPIDER_VENOM -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance
                                    .caveSpiderPassivePoisonTicks(star)),
                    ArenaSkillBalance.CAVE_SPIDER_PASSIVE_POISON_AMPLIFIER + 1,
                    ArenaSkillBalance.CAVE_SPIDER_SKILL_POISON_AMPLIFIER + 1,
                    formatSeconds(
                            ArenaSkillBalance
                                    .caveSpiderSkillPoisonTicks(star)),
                    formatSeconds(
                            ArenaSkillBalance
                                    .caveSpiderMiningFatigueTicks(star)),
                    ArenaSkillBalance.CAVE_SPIDER_MINING_FATIGUE_AMPLIFIER + 1,
                    formatPercent(
                            ArenaSkillBalance.caveSpiderHealRatio(
                                    star, ap)));
            case ARMADILLO_ROLL -> Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.ARMADILLO_REFLECT_RATIO),
                    formatSeconds(
                            ArenaSkillBalance.armadilloRollTicks(star)),
                    formatPercent(
                            ArenaSkillBalance.armadilloAttackMultiplier(
                                    star, ap)),
                    Math.min(2, star));
            case BLAZE_FIREBALL_BARRAGE -> Component.translatable(
                    base + ".description",
                    formatSeconds(
                            ArenaSkillBalance.blazeBurnTicks(star)),
                    ArenaSkillBalance.BLAZE_FIREBALL_COUNT,
                    ArenaSkillBalance
                            .BLAZE_LAUNCH_INTERVAL_TICKS,
                    formatPercent(
                            ArenaSkillBalance.blazeFireballMultiplier(
                                    star, ap)));
            case SNIFFER_DIG -> {
                Component result = star >= 3
                        ? Component.translatable(
                        base + ".description.three_star",
                        ArenaSkillBalance
                                .SNIFFER_DOMINANCE_GOLD_PER_SECOND,
                        formatStat(ArenaSkillBalance
                                .SNIFFER_DOMINANCE_ATTACK_BONUS),
                        formatPercent(ArenaSkillBalance
                                .SNIFFER_DOMINANCE_ATTACK_SPEED_BONUS),
                        formatPercent(ArenaSkillBalance
                                .SNIFFER_DOMINANCE_MOVEMENT_SPEED_BONUS))
                        : Component.translatable(
                        base + ".description",
                        formatSeconds(ArenaSkillBalance
                                .snifferBuffTicks(star)),
                        formatPercent(ArenaSkillBalance
                                .SNIFFER_ATTACK_SPEED_BONUS),
                        formatSeconds(ArenaSkillBalance
                                .snifferDebuffTicks(star)),
                        ArenaSkillBalance
                                .SNIFFER_DEBUFF_AMPLIFIER + 1);
                result = Component.empty().append(result).append(
                        Component.translatable(
                                base + ".description.shield",
                                formatPercent(star >= 3
                                        ? ArenaSkillBalance
                                        .SNIFFER_THREE_STAR_SHIELD_RATIO
                                        : ArenaSkillBalance
                                        .SNIFFER_SHIELD_RATIO)));
                yield result;
            }
            case ENDERMITE_SILENCE_STRIKE -> Component.translatable(
                    base + ".description",
                    formatPercent(
                            ArenaSkillBalance.endermiteAttackRatio(
                                    star, ap)),
                    formatSeconds(
                            ArenaSkillBalance.endermiteSilenceTicks(star)));
            case SLIME_LEAP_SMASH -> Component.translatable(
                    star >= 3
                            ? base + ".description.three_star"
                            : base + ".description",
                    formatStat(ArenaSkillBalance.SLIME_LEAP_RADIUS),
                    formatPercent(
                            ArenaSkillBalance.slimeAttackMultiplier(
                                    star, ap)),
                    formatPercent(
                            ArenaSkillBalance
                                    .slimeMissingHealthHealRatio(
                                            star, ap)));
            case ELDER_GUARDIAN_LASER ->
                    Component.translatable(
                            base + ".description",
                            formatStat(ArenaSkillBalance
                                    .elderGuardianLaserDamage(
                                            stats.attackDamage(),
                                            ap)));
            case RABBIT_LEAP -> Component.translatable(
                    base + ".description",
                    formatPercent(ArenaSkillBalance
                            .rabbitAttackMultiplier(star, ap)),
                    formatPercent(ArenaSkillBalance
                            .rabbitAttackSpeedSlow(star, ap)),
                    formatSeconds(ArenaSkillBalance
                            .scaledDurationTicks(100, star)),
                    formatPercent(ArenaSkillBalance
                            .rabbitDodgeBonus(star, ap)));
            case DONKEY_GIFT -> Component.translatable(
                    base + ".description",
                    ArenaSkillBalance
                            .DONKEY_SKILL_CHARGE_GRANT,
                    formatPercent(ArenaSkillBalance
                            .DONKEY_DEATH_SHIELD_RATIO
                            * ArenaSkillBalance.abilityScale(
                            star, ap)));
            case SHEEP_HEAL -> Component.translatable(
                    base + ".description",
                    formatPercent(ArenaSkillBalance.SHEEP_HEAL_RATIO
                            * ArenaSkillBalance.abilityScale(
                            star, ap)));
            case COW_CLEANSE -> Component.translatable(
                    base + ".description",
                    formatPercent(ArenaSkillBalance.COW_SHIELD_RATIO
                            * ArenaSkillBalance.abilityScale(
                            star, ap)));
            case PIG_TRANSFORM -> Component.translatable(
                    base + ".description",
                    formatStat(ArenaSkillBalance.PIG_HEAL
                            * ArenaSkillBalance.abilityScale(
                            star, ap)),
                    formatPercent(ArenaSkillBalance.PIG_SHIELD_RATIO
                            * ArenaSkillBalance.abilityScale(
                            star, ap)));
            case ZOMBIFIED_PIGLIN_FRENZY ->
                    Component.translatable(
                            base + ".description",
                            formatPercent(ArenaSkillBalance
                                    .zombifiedPiglinAttackSpeedBonus(
                                            star, ap)),
                            formatSeconds(ArenaSkillBalance
                                    .scaledDurationTicks(200, star)));
            case FROG_PULL -> Component.translatable(
                    base + ".description",
                    formatSeconds(ArenaSkillBalance
                            .frogStunTicks(star)),
                    formatPercent(ArenaSkillBalance
                            .frogMaxHealthDamage(star, ap)));
            case FOX_STEAL -> Component.translatable(
                    base + ".description",
                    formatPercent(ArenaSkillBalance
                            .foxAttackStealRatio(star, ap)),
                    formatPercent(ArenaSkillBalance
                            .foxAttackSpeedStealRatio(star, ap)));
            case CREEPER_EXPLODE -> Component.translatable(
                    base + ".description",
                    formatStat(ArenaSkillBalance
                            .CREEPER_TRIGGER_RADIUS),
                    formatPercent(ArenaSkillBalance
                            .creeperAttackMultiplier(star, ap)),
                    formatStat(ArenaSkillBalance
                            .CREEPER_EXPLOSION_RADIUS),
                    formatSeconds(ArenaSkillBalance
                            .creeperStunTicks(star)),
                    formatSeconds(ArenaSkillBalance
                            .scaledDurationTicks(
                                    ArenaSkillBalance
                                            .CREEPER_DEBUFF_TICKS,
                                    star)),
                    ArenaSkillBalance.CREEPER_SLOW_AMPLIFIER + 1,
                    ArenaSkillBalance.CREEPER_WEAKNESS_AMPLIFIER + 1,
                    ArenaSkillBalance
                            .CREEPER_ARMOR_BREAK_AMPLIFIER + 1,
                    formatPercent(ArenaSkillBalance
                            .CREEPER_MAGIC_RESIST_REDUCTION));
            case PANDA_ROLL -> Component.translatable(
                    base + ".description",
                    formatPercent(ArenaSkillBalance
                            .pandaRollAttackMultiplier(star, ap)),
                    formatSeconds(ArenaSkillBalance
                            .pandaResistanceTicks(star)),
                    ArenaSkillBalance
                            .pandaResistanceAmplifier(star) + 1);
            case ENDERMAN_LOCK -> Component.translatable(
                    base + ".description",
                    formatSeconds(ArenaSkillBalance
                            .endermanStunTicks(star)),
                    formatSeconds(ArenaSkillBalance
                            .ENDERMAN_TELEPORT_INTERVAL_TICKS),
                    formatSeconds(ArenaSkillBalance
                            .ENDERMAN_ATTACK_BUFF_TICKS),
                    formatPercent(ArenaSkillBalance
                            .ENDERMAN_ATTACK_BUFF_RATIO));
            case WARDEN_SONIC_BOOM -> Component.translatable(
                    base + ".description",
                    formatStat(ArenaSkillBalance
                            .wardenKillHealthGain(star)),
                    formatStat(ArenaSkillBalance
                            .wardenKillAttackGain(star)),
                    formatPercent(ArenaSkillBalance
                            .wardenKillMovementGain(star)),
                    ArenaSkillBalance.WARDEN_SONIC_WAVES,
                    formatPercent(ArenaSkillBalance
                            .wardenSonicAttackMultiplier(star, ap)),
                    formatSeconds(ArenaSkillBalance
                            .wardenSonicStunTicks(star)));
            case WANDERING_TRADER_SUPPLY ->
                    Component.translatable(
                            base + ".description",
                            ArenaSkillBalance
                                    .wanderingTraderTargets(star),
                            formatPercent(ArenaSkillBalance
                                    .wanderingTraderShieldRatio(
                                            star, ap)));
            case MAGMA_CUBE_BLESSING ->
                    Component.translatable(
                            base + ".description",
                            formatSeconds(ArenaSkillBalance
                                    .MAGMA_CUBE_BUFF_TICKS),
                            ArenaSkillBalance
                                    .MAGMA_CUBE_EFFECT_AMPLIFIER + 1,
                            formatSeconds(ArenaSkillBalance
                                    .MAGMA_CUBE_DEBUFF_TICKS),
                            ArenaSkillBalance
                                    .MAGMA_CUBE_EFFECT_AMPLIFIER + 1);
            case TURTLE_RESILIENCE ->
                    Component.translatable(
                            base + ".description",
                            formatPercent(ArenaSkillBalance
                                    .turtleRegenRatio(star, ap)),
                            formatSeconds(ArenaSkillBalance
                                    .TURTLE_REGEN_INTERVAL_TICKS),
                            formatSeconds(ArenaSkillBalance
                                    .turtleResistanceTicks(star)),
                            ArenaSkillBalance
                                    .TURTLE_RESISTANCE_AMPLIFIER + 1);
            case SHOTGUN_BARRAGE ->
                    Component.translatable(
                            base + ".description",
                            formatPercent(ArenaSkillBalance
                                    .shotgunAttackSpeedBonus(star, ap)),
                            formatPercent(ArenaSkillBalance
                                    .shotgunLifesteal(star, ap)),
                            formatSeconds(ArenaSkillBalance
                                    .SHOTGUN_BARRAGE_TICKS));
            case WITHER_SKULL_BARRAGE ->
                    Component.translatable(
                            star >= 3
                                    ? base + ".description.three_star"
                                    : base + ".description",
                            formatSeconds(ArenaSkillBalance
                                    .WITHER_PASSIVE_TICKS),
                            formatPercent(ArenaSkillBalance
                                    .witherSkullAttackMultiplier(
                                            star, ap)),
                            formatPercent(ArenaSkillBalance
                                    .witherTrueDamageRatio(
                                            ArenaSkillBalance
                                                    .WITHER_EFFECT_AMPLIFIER,
                                            star, ap)));
            case SKELETON_HORSE_CLEANSE ->
                    Component.translatable(
                            base + ".description",
                            formatPercent(ArenaSkillBalance
                                    .skeletonHorseShieldRatio(
                                            star, ap)),
                            formatSeconds(ArenaSkillBalance
                                    .SKELETON_HORSE_SPEED_TICKS),
                            ArenaSkillBalance
                                    .SKELETON_HORSE_SPEED_AMPLIFIER
                                    + 1);
            case GIANT_ARMADILLO_MOMENTUM ->
                    Component.translatable(
                            base + ".description",
                            formatPercent(ArenaSkillBalance
                                    .GIANT_ARMADILLO_STAT_STEP),
                            ArenaSkillBalance
                                    .GIANT_ARMADILLO_MAX_STACKS,
                            formatStat(ArenaSkillBalance
                                    .GIANT_ARMADILLO_MAX_SCALE));
            case ENDER_DRAGON_BOARD_SLAM ->
                    Component.translatable(
                            base + ".description",
                            ArenaSkillBalance
                                    .ENDER_DRAGON_BREATH_INTERVAL_TICKS,
                            formatPercent(ArenaSkillBalance
                                    .enderDragonSlamMultiplier(star)));
        };
    }

    private static String formatPercent(double ratio) {
        return formatStat(ratio * 100.0);
    }

    private static String formatSeconds(int ticks) {
        return formatStat(ticks / 20.0);
    }

    private static String formatStat(double value) {
        return Math.abs(value - Math.rint(value)) < 0.05
                ? Long.toString(Math.round(value))
                : String.format(Locale.ROOT, "%.1f", value);
    }
}
