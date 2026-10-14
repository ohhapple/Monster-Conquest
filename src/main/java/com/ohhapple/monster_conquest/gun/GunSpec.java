package com.ohhapple.monster_conquest.gun;

/**
 * 一把玩家枪械的全部数值。
 *
 * @param category              行为类别
 * @param ammoType              装填弹药类型
 * @param magazineSize          弹匣容量
 * @param reloadTicks           装填耗时（tick）
 * @param cooldownTicks         腰射射击间隔（tick）
 * @param adsCooldownTicks      瞄准射击间隔（tick）
 * @param damage                单发 / 单弹丸基础伤害
 * @param headshotMultiplier    爆头伤害倍率（1.0 = 无爆头加成）
 * @param armorPenetration      穿甲比例（0~1，无视目标护甲的百分比）
 * @param projectileSpeed       弹速（格/tick）
 * @param projectileGravity     重力（格/tick²）
 * @param projectileLifeTicks   弹丸最长存活（tick）
 * @param hipSpreadDegrees      腰射散布（度）
 * @param adsSpreadDegrees      瞄准散布（度）
 * @param pellets               每次射击弹丸数
 * @param explosionRadius       爆炸半径（仅火箭筒，0 = 无爆炸）
 * @param rangeBlocks           最大飞行距离（格），超过后弹丸自动消失
 */
public record GunSpec(
        GunCategory category,
        GunAmmoType ammoType,
        int magazineSize,
        int reloadTicks,
        int cooldownTicks,
        int adsCooldownTicks,
        float damage,
        float headshotMultiplier,
        float armorPenetration,
        double projectileSpeed,
        double projectileGravity,
        int projectileLifeTicks,
        double hipSpreadDegrees,
        double adsSpreadDegrees,
        int pellets,
        float explosionRadius,
        double rangeBlocks) {

    public boolean hasExplosion() {
        return explosionRadius > 0.0f;
    }
}
