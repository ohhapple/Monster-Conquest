package com.ohhapple.monster_conquest.arena.game;

/** 一件竞技装备提供的静态属性；百分比字段使用 0.10 表示 10%。 */
public record ArenaEquipmentStats(
        double health,
        double attackPercent,
        double attackSpeedPercent,
        double armor,
        double magicResistance,
        double movementSpeedPercent,
        double dodgeChance,
        double healingPower,
        double lifesteal,
        double magicPower,
        double initialSkillCharge,
        double skillChargeGainPercent,
        double critChance,
        double critDamage,
        boolean skillCritEnabled) {

    public static final ArenaEquipmentStats EMPTY = builder().build();

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private double health;
        private double attackPercent;
        private double attackSpeedPercent;
        private double armor;
        private double magicResistance;
        private double movementSpeedPercent;
        private double dodgeChance;
        private double healingPower;
        private double lifesteal;
        private double magicPower;
        private double initialSkillCharge;
        private double skillChargeGainPercent;
        private double critChance;
        private double critDamage;
        private boolean skillCritEnabled;

        public Builder health(double value) {
            health = value;
            return this;
        }

        public Builder attackPercent(double value) {
            attackPercent = value;
            return this;
        }

        public Builder attackSpeedPercent(double value) {
            attackSpeedPercent = value;
            return this;
        }

        public Builder armor(double value) {
            armor = value;
            return this;
        }

        public Builder magicResistance(double value) {
            magicResistance = value;
            return this;
        }

        public Builder movementSpeedPercent(double value) {
            movementSpeedPercent = value;
            return this;
        }

        public Builder dodgeChance(double value) {
            dodgeChance = value;
            return this;
        }

        public Builder healingPower(double value) {
            healingPower = value;
            return this;
        }

        public Builder lifesteal(double value) {
            lifesteal = value;
            return this;
        }

        public Builder magicPower(double value) {
            magicPower = value;
            return this;
        }

        public Builder initialSkillCharge(double value) {
            initialSkillCharge = value;
            return this;
        }

        public Builder skillChargeGainPercent(double value) {
            skillChargeGainPercent = value;
            return this;
        }

        public Builder critChance(double value) {
            critChance = value;
            return this;
        }

        public Builder critDamage(double value) {
            critDamage = value;
            return this;
        }

        public Builder skillCritEnabled(boolean value) {
            skillCritEnabled = value;
            return this;
        }

        public ArenaEquipmentStats build() {
            return new ArenaEquipmentStats(
                    health, attackPercent, attackSpeedPercent, armor,
                    magicResistance, movementSpeedPercent, dodgeChance,
                    healingPower, lifesteal, magicPower,
                    initialSkillCharge, skillChargeGainPercent,
                    critChance, critDamage, skillCritEnabled);
        }
    }
}
