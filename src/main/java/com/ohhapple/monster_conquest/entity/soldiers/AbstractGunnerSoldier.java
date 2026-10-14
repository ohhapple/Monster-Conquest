package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.gun.GunSpec;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 枪兵基类。
 *
 * 伤害 / 穿甲 / 射程 / 弹丸属性（弹速、重力、弹丸数、散布）全部取自
 * 玩家使用的同款枪械数值（GunSpecs），并且不随士兵等级变化。
 */
public abstract class AbstractGunnerSoldier extends AbstractArmySoldier {

    protected AbstractGunnerSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    /** 该兵种使用的枪械数值（与玩家一致）。 */
    public abstract GunSpec getGunSpec();

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.GUNNER_INFANTRY; }

    @Override protected boolean usesDirectFire() { return true; }

    @Override public float getRangedDamageForTier(EquipmentTier tier) { return getGunSpec().damage(); }

    @Override protected double getProjectileSpeed() { return getGunSpec().projectileSpeed(); }

    @Override protected double getProjectileGravity() { return getGunSpec().projectileGravity(); }

    @Override public double getMaxAttackRange(boolean mounted) { return getGunSpec().rangeBlocks(); }

    @Override protected int getProjectilesPerShot() { return getGunSpec().pellets(); }

    @Override protected double getShotSpreadDegrees() { return getGunSpec().hipSpreadDegrees(); }

    /** 索敌范围 = min(枪械射程, 配置的统一索敌范围)，不会超出枪械射程。 */
    @Override public double getFollowRange() {
        return Math.min(getGunSpec().rangeBlocks(), GeneralConfig.armyFollowRange);
    }

    /** 射速与玩家枪械一致。 */
    @Override public int getRangedAttackCooldownTicks() { return getGunSpec().cooldownTicks(); }

    // ===================== 弹匣 / 换弹（后备弹药无限） =====================

    /** 当前弹匣余弹（-1 = 尚未初始化）。 */
    private int magazine = -1;
    /** 换弹剩余 tick（>0 表示正在换弹）。 */
    private int reloadTicks = 0;
    /** 上次开火的时间（game time），用于"停火后快速补弹"。 */
    private long lastShotGameTime = -1000L;

    /** 停火多久后快速补弹（与玩家逻辑一致）。 */
    private static final int TOP_UP_IDLE_TICKS = 25;

    private int magazineSize() { return getGunSpec().magazineSize(); }

    public int getMagazineAmmo() { return magazine < 0 ? magazineSize() : magazine; }

    public boolean isReloadingGun() { return reloadTicks > 0; }

    private void startReload() {
        reloadTicks = getGunSpec().reloadTicks();
        level().playSound(null, getX(), getEyeY(), getZ(),
                SoundEvents.PISTON_EXTEND, SoundSource.HOSTILE, 0.8f, 1.6f);
    }

    @Override
    public boolean tryPerformRangedAttack(LivingEntity target) {
        if (reloadTicks > 0) return false;
        if (magazine < 0) magazine = magazineSize();

        if (magazine <= 0) {
            startReload();
            return false;
        }

        boolean fired = super.tryPerformRangedAttack(target);
        if (fired) {
            magazine--;
            lastShotGameTime = level().getGameTime();
        }
        return fired;
    }

    /** 齐射同样走弹匣：打空后需要换弹。 */
    @Override
    public void performVolleyShot(net.minecraft.world.phys.Vec3 targetPos) {
        if (reloadTicks > 0) return;
        if (magazine < 0) magazine = magazineSize();
        if (magazine <= 0) {
            startReload();
            return;
        }
        super.performVolleyShot(targetPos);
        magazine--;
        lastShotGameTime = level().getGameTime();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (reloadTicks > 0) {
            reloadTicks--;
            if (reloadTicks == 0) {
                // 后备弹药无限：直接装满弹匣
                magazine = magazineSize();
                level().playSound(null, getX(), getEyeY(), getZ(),
                        SoundEvents.IRON_DOOR_CLOSE, SoundSource.HOSTILE, 0.8f, 1.6f);
            }
            return;
        }

        // 弹匣没满但停火一段时间 → 快速补弹（无换弹时间 / 动画，只有音效）
        if (magazine >= 0 && magazine < magazineSize()
                && level().getGameTime() - lastShotGameTime >= TOP_UP_IDLE_TICKS) {
            magazine = magazineSize();
            level().playSound(null, getX(), getEyeY(), getZ(),
                    SoundEvents.CROSSBOW_LOADING_END, SoundSource.HOSTILE, 0.7f, 1.7f);
        }
    }
}
