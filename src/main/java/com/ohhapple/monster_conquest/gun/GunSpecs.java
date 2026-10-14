package com.ohhapple.monster_conquest.gun;

/** 四把玩家枪械的数值表（射速、弹匣、换弹时间按原有同类武器设定）。 */
public final class GunSpecs {

    private GunSpecs() {}

    /**
     * 步枪：步枪弹，30 发弹匣，2 tick 一发；开镜后降速到 5 tick。
     * 射程 96 格，散布极小，穿甲 50%。
     */
    public static final GunSpec RIFLE = new GunSpec(
            GunCategory.RIFLE, GunAmmoType.RIFLE_AMMO,
            30, 40, 2, 5,
            14.0f, 1.75f, 0.50f,
            12.0, 0.004, 40,
            0.6, 0.15,
            1,
            0.0f, 96.0);

    /** 霰弹枪：霰弹，2 发弹匣、按住连发（间隔 5 tick），射程 40 格，穿甲 40%。 */
    public static final GunSpec SHOTGUN = new GunSpec(
            GunCategory.SHOTGUN, GunAmmoType.SHOTGUN_SHELL,
            2, 33, 5, 5,
            6.0f, 1.5f, 0.40f,
            8.0, 0.012, 20,
            4.5, 3.2,
            7,
            0.0f, 40.0);

    /** 狙击枪：狙击弹，5 发弹匣、单发 18 tick；射程 128 格，开镜后零散布，穿甲 60%。 */
    public static final GunSpec SNIPER = new GunSpec(
            GunCategory.SNIPER, GunAmmoType.SNIPER_AMMO,
            5, 40, 18, 18,
            50.0f, 2.0f, 0.60f,
            22.0, 0.002, 20,
            0.4, 0.0,
            1,
            0.0f, 128.0);

    /** 火箭筒：火箭弹，1 发装填，射程 128 格，爆炸范围伤害，穿甲 60%。 */
    public static final GunSpec LAUNCHER = new GunSpec(
            GunCategory.LAUNCHER, GunAmmoType.ROCKET,
            1, 45, 30, 30,
            60.0f, 1.0f, 0.60f,
            4.0, 0.02, 60,
            0.2, 0.1,
            1,
            4.5f, 128.0);
}
