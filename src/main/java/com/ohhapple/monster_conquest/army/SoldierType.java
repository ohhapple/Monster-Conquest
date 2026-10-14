package com.ohhapple.monster_conquest.army;

/** 描述士兵当前的形态（步兵或骑兵）。角色由 SoldierRole 决定。 */
public enum SoldierType {
    MELEE_INFANTRY(SoldierRole.MELEE, false),
    MELEE_CAVALRY(SoldierRole.MELEE, true),
    ARCHER_INFANTRY(SoldierRole.ARCHER, false),
    ARCHER_CAVALRY(SoldierRole.ARCHER, true),
    DUAL_INFANTRY(SoldierRole.DUAL, false),
    DUAL_CAVALRY(SoldierRole.DUAL, true),
    THROWER_INFANTRY(SoldierRole.THROWER, false),
    THROWER_CAVALRY(SoldierRole.THROWER, true),
    GUNNER_INFANTRY(SoldierRole.GUNNER, false),
    GUNNER_CAVALRY(SoldierRole.GUNNER, true),
    MAGE_INFANTRY(SoldierRole.MAGE, false),
    MAGE_CAVALRY(SoldierRole.MAGE, true);

    private final SoldierRole role;
    private final boolean cavalry;

    SoldierType(SoldierRole role, boolean cavalry) {
        this.role = role;
        this.cavalry = cavalry;
    }

    public SoldierRole getRole() { return role; }
    public boolean isCavalry()  { return cavalry; }
    public boolean isInfantry() { return !cavalry; }

    public boolean isMelee()   { return role == SoldierRole.MELEE; }
    public boolean isArcher()  { return role == SoldierRole.ARCHER; }
    public boolean isDual()    { return role == SoldierRole.DUAL; }
    public boolean isThrower() { return role == SoldierRole.THROWER; }
    public boolean isGunner()  { return role == SoldierRole.GUNNER; }
    public boolean isMage()    { return role == SoldierRole.MAGE; }
    public boolean isRanged()  { return role.isRanged(); }
    public boolean isWitch()   { return isThrower(); }

    /** 步兵 ↔ 骑兵 切换 */
    public SoldierType toggleMounted() {
        return switch (this) {
            case MELEE_INFANTRY -> MELEE_CAVALRY;
            case MELEE_CAVALRY -> MELEE_INFANTRY;
            case ARCHER_INFANTRY -> ARCHER_CAVALRY;
            case ARCHER_CAVALRY -> ARCHER_INFANTRY;
            case DUAL_INFANTRY -> DUAL_CAVALRY;
            case DUAL_CAVALRY -> DUAL_INFANTRY;
            case THROWER_INFANTRY -> THROWER_CAVALRY;
            case THROWER_CAVALRY -> THROWER_INFANTRY;
            case GUNNER_INFANTRY -> GUNNER_CAVALRY;
            case GUNNER_CAVALRY -> GUNNER_INFANTRY;
            case MAGE_INFANTRY -> MAGE_CAVALRY;
            case MAGE_CAVALRY -> MAGE_INFANTRY;
        };
    }
}
