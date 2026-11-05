package com.ohhapple.monster_conquest.arena.game;

/** 技能描述与战斗结算共用的数值公式。 */
public final class ArenaSkillBalance {

    public static final int SKELETON_TARGETS = 3;
    public static final int STRAY_SHOTS = 3;
    public static final int WITHER_DOMINATION_STAR = 3;
    public static final int EVOKER_MAX_SUMMONS = 3;
    public static final int EVOKER_SUMMON_INTERVAL_TICKS = 60;
    public static final double EVOKER_SUMMON_STAT_RATIO = 1.0 / 3.0;
    public static final int EVOKER_THREE_STAR_STUN_TICKS = 20 * 60;
    public static final double EVOKER_THREE_STAR_SUMMON_HEALTH = 6666.0;
    public static final double EVOKER_THREE_STAR_SUMMON_ATTACK = 666.0;
    public static final double EVOKER_THREE_STAR_SUMMON_ARMOR = 30.0;
    public static final double EVOKER_THREE_STAR_SUMMON_MAGIC_RESISTANCE = 666.0;
    public static final double EVOKER_THREE_STAR_SUMMON_MAGIC_POWER = 666.0;
    public static final int BOGGED_POISON_AMPLIFIER = 14;
    public static final double SNOW_GOLEM_RANGE = 32.0;
    public static final int SNOW_GOLEM_SNOWBALL_COUNT = 3;
    public static final int SNOW_GOLEM_FORMATION_INTERVAL_TICKS = 2;
    public static final int SNOW_GOLEM_LAUNCH_INTERVAL_TICKS = 10;
    public static final int SNOW_GOLEM_MAGIC_RESIST_STACKS = 1;
    public static final double SNOW_GOLEM_SNOWBALL_ATTACK_MULTIPLIER = 1.50;
    public static final double SNOW_GOLEM_ATTACK_MULTIPLIER_STAR_STEP = 0.25;
    public static final int WITHER_ONE_STAR_TARGETS = 5;
    public static final int WITHER_TWO_STAR_TARGETS = 8;
    public static final double SPIDER_SLOW_RADIUS = 2.0;
    public static final int SPIDER_SLOW_AMPLIFIER = 2;
    public static final double GOAT_CHARGE_RANGE = 12.0;
    public static final int GOAT_CHARGE_TICKS = 24;
    public static final double GOAT_CHARGE_STEP = 0.55;
    public static final double GALE_PROJECTILE_SPEED = 0.50;
    public static final int GALE_CONTACT_COOLDOWN_TICKS = 10;
    public static final double BREEZE_THREE_STAR_BASIC_MULTIPLIER = 30.0;
    public static final double NORMAL_WIND_EXPLOSION_RADIUS = 3.0;
    public static final double GALE_EXPLOSION_RADIUS = 3.0;
    public static final double RAVAGER_KNOCKUP_RADIUS = 3.0;
    public static final int RAVAGER_BLOOD_SHIELD_INTERVAL_TICKS = 10;
    public static final double RAVAGER_BLOOD_SHIELD_HEALTH_RATIO = 0.01;
    public static final double RAVAGER_BLOOD_SHIELD_MIN_HEALTH_RATIO = 0.10;
    public static final int BLAZE_FIREBALL_COUNT = 5;
    public static final int BLAZE_FORMATION_INTERVAL_TICKS = 2;
    public static final int BLAZE_LAUNCH_INTERVAL_TICKS = 10;
    public static final int POLAR_BEAR_FORM_TICKS = 15 * 20;
    public static final double POLAR_BEAR_HEALTH_BONUS = 500.0;
    public static final double POLAR_BEAR_LIFESTEAL_BONUS = 0.50;
    public static final double HOGLIN_CHARGE_STEP = 0.42;
    public static final int HOGLIN_CHARGE_TICKS = 60;
    public static final double HOGLIN_CHARGE_RANGE = 16.0;
    public static final double HOGLIN_HIT_RADIUS = 1.8;
    public static final int HOGLIN_DAMAGE_COOLDOWN_TICKS = 10;
    public static final double IRON_GOLEM_LEAP_RADIUS = 2.5;
    public static final double IRON_GOLEM_SHIELD_RATIO = 0.20;
    public static final int MACE_LEAP_COUNT = 5;
    public static final int MACE_LEAP_INTERVAL_TICKS = 2;
    public static final double CAVE_SPIDER_SKILL_RADIUS = 2.0;
    public static final int CAVE_SPIDER_PASSIVE_POISON_AMPLIFIER = 6;
    public static final int CAVE_SPIDER_SKILL_POISON_AMPLIFIER = 14;
    public static final int ARTHROPOD_POISON_AMPLIFIER = 3;
    public static final int CAVE_SPIDER_MINING_FATIGUE_AMPLIFIER = 1;
    public static final int ARMADILLO_ROLL_BASE_TICKS = 8 * 20;
    public static final double ARMADILLO_REFLECT_RATIO = 0.20;
    public static final double ARMADILLO_ROLL_STEP = 0.38;
    public static final double ARMADILLO_HIT_RADIUS = 1.5;
    public static final int SNIFFER_TARGET_LIMIT = 5;
    public static final double SNIFFER_ATTACK_SPEED_BONUS = 1.0;
    public static final int SNIFFER_DEBUFF_AMPLIFIER = 1;
    public static final int SNIFFER_DOMINANCE_GOLD_PER_SECOND = 5;
    public static final double SNIFFER_DOMINANCE_ATTACK_BONUS = 500.0;
    public static final double SNIFFER_DOMINANCE_ATTACK_SPEED_BONUS = 5.0;
    public static final double SNIFFER_DOMINANCE_MOVEMENT_SPEED_BONUS = 3.0;
    public static final int SNIFFER_DOMINANCE_GOLD_INTERVAL_TICKS = 20;
    public static final double SNIFFER_SHIELD_RATIO = 0.10;
    public static final double SNIFFER_THREE_STAR_SHIELD_RATIO = 10.0;
    public static final double SLIME_LEAP_RADIUS = 2.5;
    public static final int ELDER_GUARDIAN_DAMAGE_INTERVAL_TICKS = 10;
    public static final double ELDER_GUARDIAN_SKILL_RANGE = 32.0;
    public static final double ELDER_GUARDIAN_BASIC_RADIUS = 0.18;
    public static final double ELDER_GUARDIAN_SKILL_RADIUS = 0.55;
    public static final double RABBIT_LEAP_ATTACK_MULTIPLIER = 1.80;
    public static final double RABBIT_ATTACK_SPEED_SLOW = 0.30;
    public static final double RABBIT_DODGE_BONUS = 0.50;
    public static final int DONKEY_SKILL_CHARGE_GRANT = 5;
    public static final double DONKEY_DEATH_SHIELD_RATIO = 0.20;
    public static final double SHEEP_HEAL_RATIO = 0.08;
    public static final double COW_SHIELD_RATIO = 0.10;
    public static final double PIG_HEAL = 500.0;
    public static final double PIG_SHIELD_RATIO = 0.30;
    public static final double ZOMBIFIED_PIGLIN_ATTACK_SPEED_BONUS = 1.0;
    public static final int FROG_STUN_BASE_TICKS = 100;
    public static final double FROG_MAX_HEALTH_DAMAGE_RATIO = 0.10;
    public static final double FOX_ATTACK_STEAL_RATIO = 0.10;
    public static final double FOX_ATTACK_SPEED_STEAL_RATIO = 0.15;
    public static final double CREEPER_ATTACK_MULTIPLIER = 2.50;
    public static final double CREEPER_TRIGGER_RADIUS = 1.6;
    public static final double CREEPER_EXPLOSION_RADIUS = 4.5;
    public static final int CREEPER_READY_SPEED_AMPLIFIER = 2;
    public static final int CREEPER_STUN_BASE_TICKS = 100;
    public static final int CREEPER_DEBUFF_TICKS = 200;
    public static final int CREEPER_SLOW_AMPLIFIER = 2;
    public static final int CREEPER_WEAKNESS_AMPLIFIER = 1;
    public static final int CREEPER_ARMOR_BREAK_AMPLIFIER = 4;
    public static final double CREEPER_MAGIC_RESIST_REDUCTION = 0.50;
    public static final double PANDA_ROLL_ATTACK_MULTIPLIER = 2.0;
    public static final int PANDA_RESISTANCE_BASE_TICKS = 100;
    public static final int ENDERMAN_STUN_BASE_TICKS = 120;
    public static final int ENDERMAN_TELEPORT_INTERVAL_TICKS = 60;
    public static final int ENDERMAN_ATTACK_BUFF_TICKS = 8 * 20;
    public static final double ENDERMAN_ATTACK_BUFF_RATIO = 1.0;
    public static final double WITCH_LINGERING_RADIUS = 4.0;
    public static final int WITCH_LINGERING_DURATION_TICKS = 15 * 20;
    public static final int WITCH_LINGERING_DAMAGE_INTERVAL_TICKS = 10;
    public static final double WITCH_LINGERING_ATTACK_RATIO = 0.80;
    public static final int WITCH_LINGERING_HEAL_INTERVAL_TICKS = 20;
    public static final double WITCH_LINGERING_ALLY_HEAL_RATIO = 0.025;
    public static final double WARDEN_SONIC_ATTACK_MULTIPLIER = 2.50;
    public static final int WARDEN_SONIC_STUN_BASE_TICKS = 100;
    public static final int WARDEN_SONIC_WAVES = 3;
    public static final double WARDEN_SONIC_RANGE = 32.0;
    public static final double WARDEN_SONIC_RADIUS = 0.90;
    public static final double WARDEN_KILL_HEALTH_GAIN = 200.0;
    public static final double WARDEN_KILL_ATTACK_GAIN = 25.0;
    public static final double WARDEN_KILL_MOVEMENT_GAIN = 0.20;
    public static final double WOLF_ATTACK_BONUS_PER_CAST = 0.50;
    public static final int DROWNED_TRIDENT_COUNT = 3;
    public static final int DROWNED_TRIDENT_FORMATION_INTERVAL_TICKS = 3;
    public static final int DROWNED_TRIDENT_LAUNCH_INTERVAL_TICKS = 10;
    public static final int DROWNED_TRIDENT_ARMOR_BREAK_LEVEL = 3;
    public static final double DROWNED_TRIDENT_MR_REDUCTION = 0.30;
    public static final double ZOMBIE_VILLAGER_HEAL_RATIO = 0.05;
    public static final double WANDERING_TRADER_SHIELD_RATIO = 0.25;
    public static final int MAGMA_CUBE_BUFF_TICKS = 10 * 20;
    public static final int MAGMA_CUBE_DEBUFF_TICKS = 8 * 20;
    public static final int MAGMA_CUBE_EFFECT_AMPLIFIER = 4;
    public static final int TURTLE_RESISTANCE_AMPLIFIER = 1;
    public static final double TURTLE_REGEN_MISSING_HEALTH_RATIO = 0.15;
    public static final int TURTLE_REGEN_INTERVAL_TICKS = 5 * 20;
    public static final double SHOTGUN_ATTACK_SPEED_BONUS = 1.50;
    public static final double SHOTGUN_LIFESTEAL_RATIO = 0.30;
    public static final int SHOTGUN_BARRAGE_TICKS = 10 * 20;
    public static final int WITHER_EFFECT_AMPLIFIER = 7;
    public static final int WITHER_PASSIVE_TICKS = 10 * 20;
    public static final int WITHER_REPEAT_INTERVAL_TICKS = 10;
    public static final double SKELETON_HORSE_SHIELD_RATIO = 0.20;
    public static final int SKELETON_HORSE_SPEED_AMPLIFIER = 1;
    public static final int SKELETON_HORSE_SPEED_TICKS = 10 * 20;
    public static final int GIANT_ARMADILLO_MAX_STACKS = 30;
    public static final double GIANT_ARMADILLO_STAT_STEP = 0.10;
    public static final double GIANT_ARMADILLO_BASE_SCALE = 2.0;
    public static final double GIANT_ARMADILLO_SCALE_STEP = 0.10;
    public static final double GIANT_ARMADILLO_MAX_SCALE = 5.0;
    public static final double GIANT_ARMADILLO_ROLL_STEP = 0.44;
    public static final double GIANT_ARMADILLO_HIT_RADIUS = 1.35;
    public static final double HUSK_GROWTH_SCALE_STEP = 0.08;
    public static final double HUSK_GROWTH_SCALE_MAX = 1.50;
    public static final double WARDEN_GROWTH_SCALE_STEP = 0.06;
    public static final double WARDEN_GROWTH_SCALE_MAX = 1.35;
    public static final int ENDER_DRAGON_BREATH_INTERVAL_TICKS = 10;
    public static final double ENDER_DRAGON_BREATH_RANGE = 40.0;
    public static final double ENDER_DRAGON_BREATH_BASE_RADIUS = 1.0;
    public static final double ENDER_DRAGON_BREATH_HALF_ANGLE_DEGREES = 24.0;
    public static final int ENDER_DRAGON_SLAM_JUMP_TICKS = 40;
    public static final double ENDER_DRAGON_SLAM_JUMP_HEIGHT = 13.0;

    private ArenaSkillBalance() {
    }

    public static double starScale(int star) {
        return 1.0 + 0.5 * (Math.max(1, Math.min(3, star)) - 1);
    }

    public static double abilityScale(int star, double magicPower) {
        return starScale(star)
                * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static double shieldRatio(double baseRatio, int star,
                                     double magicPower) {
        return baseRatio * abilityScale(star, magicPower);
    }

    public static int standardBuffTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static int drownedStunTicks(int star) {
        return 60 + (safeStar(star) - 1) * 20;
    }

    public static int zombieVillagerStunTicks(int star) {
        return 40 + (safeStar(star) - 1) * 20;
    }

    public static int drownedTridentStunTicks(int star) {
        return 60 + (safeStar(star) - 1) * 20;
    }

    public static int drownedTridentDebuffTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static double drownedTridentDamage(double attackDamage) {
        return Math.max(0.0, attackDamage);
    }

    public static int evokerFangStunTicks(int star) {
        int safeStar = safeStar(star);
        if (safeStar >= 3) {
            return EVOKER_THREE_STAR_STUN_TICKS;
        }
        return 60 + (safeStar - 1) * 20;
    }

    public static int boggedPoisonTicks(int star) {
        return 200 + (safeStar(star) - 1) * 40;
    }

    public static int spiderSlowTicks(int star) {
        return 60 + (safeStar(star) - 1) * 20;
    }

    public static int snowGolemFreezeTicks(int star) {
        // 离开细雪后 frozen ticks 每秒衰减 40，因此 200 tick 约等于 5 秒。
        return 200 + (safeStar(star) - 1) * 40;
    }

    public static int snowGolemMagicResistTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static double snowGolemSnowballAttackMultiplier(
            int star, double magicPower) {
        double starMultiplier =
                SNOW_GOLEM_SNOWBALL_ATTACK_MULTIPLIER
                        + (safeStar(star) - 1)
                        * SNOW_GOLEM_ATTACK_MULTIPLIER_STAR_STEP;
        return starMultiplier
                * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static int witherTargetLimit(int star) {
        return switch (safeStar(star)) {
            case 1 -> WITHER_ONE_STAR_TARGETS;
            case 2 -> WITHER_TWO_STAR_TARGETS;
            default -> Integer.MAX_VALUE;
        };
    }

    public static int wolfSpeedTicks(int star) {
        return 120 + (safeStar(star) - 1) * 20;
    }

    public static int goatStunTicks(int star) {
        return 20 + (safeStar(star) - 1) * 20;
    }

    public static double polarBearHealthBonus(int star,
                                              double magicPower) {
        return POLAR_BEAR_HEALTH_BONUS * abilityScale(star, magicPower);
    }

    public static double polarBearAttackSpeedMultiplier(int star,
                                                        double magicPower) {
        return 1.0 + abilityScale(star, magicPower);
    }

    public static double polarBearMovementSpeedBonus(int star,
                                                     double magicPower) {
        return abilityScale(star, magicPower);
    }

    public static double polarBearLifestealBonus(int star,
                                                 double magicPower) {
        return POLAR_BEAR_LIFESTEAL_BONUS
                * abilityScale(star, magicPower);
    }

    public static int polarBearFrostTicks(int star) {
        // 离开细雪后 frozen ticks 每秒衰减 40；8 秒需要写入 320。
        return 320 + (safeStar(star) - 1) * 40;
    }

    public static double breezeGaleAttackMultiplier(int star,
                                                    double magicPower) {
        return 1.5 * abilityScale(star, magicPower);
    }

    public static double ravagerMissingHealthRatio(int star,
                                                   double magicPower) {
        double base = switch (safeStar(star)) {
            case 2 -> 0.15;
            case 3 -> 0.20;
            default -> 0.10;
        };
        return base * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static double ravagerBloodShieldMultiplier(int star) {
        return switch (safeStar(star)) {
            case 2 -> 5.0;
            case 3 -> 9.0;
            default -> 3.0;
        };
    }

    public static int ravagerStunTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static double ravagerAttackMultiplier(double magicPower) {
        return 2.50
                * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static int breezeSilenceTicks(int star) {
        return switch (safeStar(star)) {
            case 2 -> 9 * 20;
            case 3 -> 15 * 20;
            default -> 5 * 20;
        };
    }

    public static double hoglinChargeAttackMultiplier(int star,
                                                      double magicPower) {
        return 1.5 * abilityScale(star, magicPower);
    }

    public static double boggedAttackMultiplier(int star,
                                                double magicPower) {
        double base = switch (safeStar(star)) {
            case 2 -> 1.60;
            case 3 -> 3.00;
            default -> 1.20;
        };
        return base * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static double huskHealthGain(int star, double magicPower) {
        return 300.0 * abilityScale(star, magicPower);
    }

    public static double huskAttackGain(int star, double magicPower) {
        return 20.0 * abilityScale(star, magicPower);
    }

    public static double huskArmorGain(int star, double magicPower) {
        return abilityScale(star, magicPower);
    }

    public static double huskMagicResistanceGain(int star,
                                                 double magicPower) {
        return 5.0 * abilityScale(star, magicPower);
    }

    public static double strayBonusAttackRatio(int star,
                                               double magicPower) {
        double base = switch (safeStar(star)) {
            case 2 -> 2.40;
            case 3 -> 3.00;
            default -> 1.80;
        };
        return base * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static double archerStrengthBonus(int star,
                                             double magicPower) {
        return 0.5 * abilityScale(star, magicPower);
    }

    public static double vindicatorAttackMultiplier(int star,
                                                    double magicPower) {
        return 1.5 * abilityScale(star, magicPower);
    }

    public static double vindicatorExecuteThreshold(int star,
                                                    double magicPower) {
        double base = switch (safeStar(star)) {
            case 2 -> 0.225;
            case 3 -> 0.30;
            default -> 0.15;
        };
        return Math.min(0.45, base
                * (1.0 + Math.max(0.0, magicPower) / 100.0));
    }

    public static double rifleAttackSpeedMultiplier(int star,
                                                    double magicPower) {
        return 1.0 + abilityScale(star, magicPower);
    }

    public static double witherDrainRate(int star, double magicPower) {
        return 0.10 * abilityScale(star, magicPower);
    }

    public static double sniperMaxHealthRatio(int star,
                                              double magicPower) {
        double base = switch (safeStar(star)) {
            case 2 -> 0.35;
            case 3 -> 0.55;
            default -> 0.25;
        };
        return base * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static double maceAttackMultiplier(int star,
                                              double magicPower) {
        return 1.5 * abilityScale(star, magicPower);
    }

    public static double ironGolemAttackMultiplier(int star,
                                                   double magicPower) {
        return 2.0 * abilityScale(star, magicPower);
    }

    public static double ironGolemShieldRatio(int star,
                                              double magicPower) {
        return shieldRatio(IRON_GOLEM_SHIELD_RATIO, star, magicPower);
    }

    public static int ironGolemStunTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static int caveSpiderPassivePoisonTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static int caveSpiderSkillPoisonTicks(int star) {
        return 200 + (safeStar(star) - 1) * 20;
    }

    public static int caveSpiderMiningFatigueTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static double caveSpiderHealRatio(int star,
                                             double magicPower) {
        return 0.10 * abilityScale(star, magicPower);
    }

    public static int armadilloRollTicks(int star) {
        return ARMADILLO_ROLL_BASE_TICKS
                + (safeStar(star) - 1) * 20;
    }

    public static double armadilloAttackMultiplier(int star,
                                                   double magicPower) {
        return 1.5 * abilityScale(star, magicPower);
    }

    public static double blazeFireballMultiplier(int star,
                                                 double magicPower) {
        double base = switch (safeStar(star)) {
            case 2 -> 3.0;
            case 3 -> 4.0;
            default -> 2.0;
        };
        return base * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    public static int blazeBurnTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static int witherMarkTicks(int star) {
        return switch (safeStar(star)) {
            case 2 -> 240;
            case 3 -> 320;
            default -> 160;
        };
    }

    public static int snifferBuffTicks(int star) {
        return 120 + (safeStar(star) - 1) * 20;
    }

    public static int snifferDebuffTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static double endermiteAttackRatio(int star,
                                              double magicPower) {
        return 0.80 * abilityScale(star, magicPower);
    }

    public static int endermiteSilenceTicks(int star) {
        return 100 + (safeStar(star) - 1) * 20;
    }

    public static double slimeAttackMultiplier(int star,
                                              double magicPower) {
        // 三星大史莱姆是统治级终结效果；法强仍按通用技能公式放大。
        double baseMultiplier = safeStar(star) >= 3
                ? 30.0 : 2.0;
        return baseMultiplier * abilityScale(star, magicPower);
    }

    public static double slimeMissingHealthHealRatio(int star,
                                                     double magicPower) {
        return 0.20 * abilityScale(star, magicPower);
    }

    public static double elderGuardianLaserDamage(double attackDamage,
                                                  double magicPower) {
        return Math.max(0.0, attackDamage)
                + Math.max(0.0, magicPower);
    }

    public static int scaledDurationTicks(int baseTicks, int star) {
        return baseTicks + (safeStar(star) - 1) * 20;
    }

    public static double rabbitAttackMultiplier(int star,
                                               double magicPower) {
        return RABBIT_LEAP_ATTACK_MULTIPLIER
                * abilityScale(star, magicPower);
    }

    public static double rabbitAttackSpeedSlow(int star,
                                              double magicPower) {
        return RABBIT_ATTACK_SPEED_SLOW
                * abilityScale(star, magicPower);
    }

    public static double rabbitDodgeBonus(int star,
                                         double magicPower) {
        return RABBIT_DODGE_BONUS
                * abilityScale(star, magicPower);
    }

    public static double sheepHeal(double maxHealth, int star,
                                   double magicPower) {
        return maxHealth * SHEEP_HEAL_RATIO
                * abilityScale(star, magicPower);
    }

    public static double zombifiedPiglinAttackSpeedBonus(
            int star, double magicPower) {
        return ZOMBIFIED_PIGLIN_ATTACK_SPEED_BONUS
                * abilityScale(star, magicPower);
    }

    public static int frogStunTicks(int star) {
        return scaledDurationTicks(FROG_STUN_BASE_TICKS, star);
    }

    public static double frogMaxHealthDamage(int star,
                                             double magicPower) {
        return FROG_MAX_HEALTH_DAMAGE_RATIO
                * abilityScale(star, magicPower);
    }

    public static double foxAttackStealRatio(int star,
                                             double magicPower) {
        return FOX_ATTACK_STEAL_RATIO
                * abilityScale(star, magicPower);
    }

    public static double foxAttackSpeedStealRatio(int star,
                                                  double magicPower) {
        return FOX_ATTACK_SPEED_STEAL_RATIO
                * abilityScale(star, magicPower);
    }

    public static double creeperAttackMultiplier(int star,
                                                double magicPower) {
        return CREEPER_ATTACK_MULTIPLIER
                * abilityScale(star, magicPower);
    }

    public static int creeperStunTicks(int star) {
        return scaledDurationTicks(CREEPER_STUN_BASE_TICKS, star);
    }

    public static double pandaRollAttackMultiplier(
            int star, double magicPower) {
        return PANDA_ROLL_ATTACK_MULTIPLIER
                * abilityScale(star, magicPower);
    }

    public static int pandaResistanceTicks(int star) {
        return scaledDurationTicks(PANDA_RESISTANCE_BASE_TICKS, star);
    }

    public static int pandaResistanceAmplifier(int star) {
        return safeStar(star) - 1;
    }

    public static int endermanStunTicks(int star) {
        return scaledDurationTicks(ENDERMAN_STUN_BASE_TICKS, star);
    }

    public static double wardenSonicAttackMultiplier(
            int star, double magicPower) {
        return WARDEN_SONIC_ATTACK_MULTIPLIER
                * abilityScale(star, magicPower);
    }

    public static int wardenSonicStunTicks(int star) {
        return scaledDurationTicks(WARDEN_SONIC_STUN_BASE_TICKS, star);
    }

    public static int wanderingTraderTargets(int star) {
        return switch (safeStar(star)) {
            case 2 -> 5;
            case 3 -> 7;
            default -> 3;
        };
    }

    public static double wanderingTraderShieldRatio(int star,
                                                    double magicPower) {
        return WANDERING_TRADER_SHIELD_RATIO
                * abilityScale(star, magicPower);
    }

    public static int turtleResistanceTicks(int star) {
        return switch (safeStar(star)) {
            case 2 -> 7 * 20;
            case 3 -> 9 * 20;
            default -> 5 * 20;
        };
    }

    public static double turtleRegenRatio(int star, double magicPower) {
        return TURTLE_REGEN_MISSING_HEALTH_RATIO
                * abilityScale(star, magicPower);
    }

    public static double shotgunAttackSpeedBonus(int star,
                                                 double magicPower) {
        return SHOTGUN_ATTACK_SPEED_BONUS
                * abilityScale(star, magicPower);
    }

    public static double shotgunLifesteal(int star, double magicPower) {
        return SHOTGUN_LIFESTEAL_RATIO
                * abilityScale(star, magicPower);
    }

    public static double witherSkullAttackMultiplier(
            int star, double magicPower) {
        return 2.0 * abilityScale(star, magicPower);
    }

    public static double witherTrueDamageRatio(
            int witherAmplifier, int star, double magicPower) {
        // 百分比没有其它伤害基数限定，按目标最大生命作为真实伤害基数。
        int witherLevel = Math.max(1,
                Math.min(10, witherAmplifier + 1));
        return witherLevel * safeStar(star) / 100.0
                * (1.0 + Math.max(0.0, magicPower) / 100.0);
    }

    /**
     * 原版持续伤害每次固定只扣 1 点生命；竞技单位使用独立高生命池，
     * 因此按效果等级和目标最大生命换算竞技持续伤害。
     */
    public static double poisonTickDamage(
            double maxHealth, int amplifier) {
        return Math.max(1.0, Math.max(0.0, maxHealth)
                * 0.0015 * (Math.max(0, amplifier) + 1));
    }

    public static double witherTickDamage(
            double maxHealth, int amplifier) {
        return Math.max(1.0, Math.max(0.0, maxHealth)
                * 0.0040 * (Math.max(0, amplifier) + 1));
    }

    public static double freezeTickDamage(double maxHealth) {
        // 冰冻已有 50% 攻速/移速压制，伤害只按最大生命换算，避免重复过强。
        return Math.max(1.0, Math.max(0.0, maxHealth) * 0.020);
    }

    public static double skeletonHorseShieldRatio(
            int star, double magicPower) {
        return SKELETON_HORSE_SHIELD_RATIO
                * abilityScale(star, magicPower);
    }

    public static double wardenKillHealthGain(int star) {
        return switch (safeStar(star)) {
            case 2 -> 500.0;
            case 3 -> 800.0;
            default -> WARDEN_KILL_HEALTH_GAIN;
        };
    }

    public static double wardenKillAttackGain(int star) {
        return switch (safeStar(star)) {
            case 2 -> 50.0;
            case 3 -> 75.0;
            default -> WARDEN_KILL_ATTACK_GAIN;
        };
    }

    public static double wardenKillMovementGain(int star) {
        return switch (safeStar(star)) {
            case 2 -> 0.30;
            case 3 -> 0.40;
            default -> WARDEN_KILL_MOVEMENT_GAIN;
        };
    }

    public static double enderDragonSlamMultiplier(int star) {
        return switch (safeStar(star)) {
            case 2 -> 5.50;
            case 3 -> 99.99;
            default -> 3.50;
        };
    }

    private static int safeStar(int star) {
        return Math.max(1, Math.min(3, star));
    }
}
