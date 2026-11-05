package com.ohhapple.monster_conquest.arena.game;

/** 同步给竞技客户端的状态图标位。 */
public final class ArenaStatusFlags {

    public static final int STUN = 1;
    public static final int STRENGTH = 1 << 1;
    public static final int RAPID_FIRE = 1 << 2;
    public static final int TAUNT = 1 << 3;
    public static final int ARCANE_SHOTS = 1 << 4;
    public static final int EMPOWERED_ATTACK = 1 << 5;
    public static final int WITHER_DRAIN = 1 << 6;
    public static final int SNIPER_READY = 1 << 7;
    public static final int DROWNED_READY = 1 << 8;
    public static final int MAGIC_RESIST_SHRED = 1 << 9;
    public static final int BURN = 1 << 10;
    public static final int STUN_READY = 1 << 11;
    public static final int BATTLE_GROWTH = 1 << 12;
    public static final int GOAT_CHARGE = 1 << 13;
    public static final int POLAR_FORM = 1 << 14;
    public static final int HOGLIN_CHARGE = 1 << 15;
    public static final int ARMADILLO_ROLL = 1 << 16;
    public static final int SILENCE = 1 << 17;
    public static final int PANDA_ROLL = 1 << 18;
    public static final int ENDERMAN_LOCK = 1 << 19;
    public static final int WARDEN_GROWTH = 1 << 20;
    public static final int WITHER_ARMOR = 1 << 21;
    public static final int GIANT_ARMADILLO_MOMENTUM = 1 << 22;
    public static final int ARMADILLO_MOVING = 1 << 23;

    private ArenaStatusFlags() {
    }
}
