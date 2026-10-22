package com.ohhapple.monster_conquest.army;

/** 战斗职责分类，用于 AI 与行为分派。 */
public enum SoldierRole {
    MELEE, ARCHER, DUAL, THROWER, GUNNER, MAGE, FLYING;

    public boolean isMelee()   { return this == MELEE; }
    public boolean isArcher()  { return this == ARCHER; }
    public boolean isDual()    { return this == DUAL; }
    public boolean isThrower() { return this == THROWER; }
    public boolean isGunner()  { return this == GUNNER; }
    /** 魔法师（唤魔者士兵）：召唤尖牙等法术输出，不投掷药水、不开枪。 */
    public boolean isMage()    { return this == MAGE; }
    /** 飞行单位：寻路与指挥使用飞行单位体系；是否为召唤物由独立属性决定。 */
    public boolean isFlying()  { return this == FLYING; }
    public boolean isRanged()  { return this == ARCHER || this == DUAL || this == THROWER
            || this == GUNNER || this == MAGE; }
}
