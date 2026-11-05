package com.ohhapple.monster_conquest.arena.game;

import java.util.UUID;

/** 单个弈子在一个战斗回合（含加时）内的服务端权威表现统计。 */
public final class ArenaUnitPerformance {

    public enum DamageKind {
        PHYSICAL,
        MAGIC,
        TRUE
    }

    private double physicalDealt;
    private double magicDealt;
    private double trueDealt;
    private double healing;
    private double shieldHealing;
    private double physicalTaken;
    private double magicTaken;
    private double trueTaken;

    public void addDamageDealt(DamageKind kind, double amount) {
        if (amount <= 0.0) return;
        switch (kind) {
            case PHYSICAL -> physicalDealt += amount;
            case MAGIC -> magicDealt += amount;
            case TRUE -> trueDealt += amount;
        }
    }

    public void addDamageTaken(DamageKind kind, double amount) {
        if (amount > 0.0) {
            switch (kind) {
                case PHYSICAL -> physicalTaken += amount;
                case MAGIC -> magicTaken += amount;
                case TRUE -> trueTaken += amount;
            }
        }
    }

    public void addHealing(double amount) {
        if (amount > 0.0) healing += amount;
    }

    public void addShieldHealing(double amount) {
        if (amount > 0.0) shieldHealing += amount;
    }

    public Snapshot snapshot(UUID pieceId) {
        return new Snapshot(
                pieceId,
                physicalDealt, magicDealt, trueDealt,
                healing, shieldHealing,
                physicalTaken, magicTaken, trueTaken);
    }

    public record Snapshot(
            UUID pieceId,
            double physicalDealt,
            double magicDealt,
            double trueDealt,
            double healing,
            double shieldHealing,
            double physicalTaken,
            double magicTaken,
            double trueTaken) {

        public double totalDealt() {
            return physicalDealt + magicDealt + trueDealt;
        }

        public double totalTaken() {
            return physicalTaken + magicTaken + trueTaken;
        }

        public double totalHealing() {
            return healing + shieldHealing;
        }
    }
}
