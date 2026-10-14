package com.ohhapple.monster_conquest.gun;

/** 玩家枪械的行为类别。 */
public enum GunCategory {

    /** 步枪：按住右键连发。 */
    RIFLE,

    /** 霰弹枪：按住右键连发，弹匣很小，打空后换弹。 */
    SHOTGUN,

    /** 狙击枪：单发、射程极远，潜行开镜（散布极低 + 变焦），潜行蓄力后伤害翻倍。 */
    SNIPER,

    /** 火箭筒：按住右键蓄力，松开发射；蓄力越久弹速 / 伤害 / 爆炸半径越高。 */
    LAUNCHER
}
