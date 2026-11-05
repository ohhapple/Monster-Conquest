package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaStatusFlags;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.PowerableMob;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 行走型竞技凋灵。没有原版 Boss 飞行、破坏方块、天空变化或出生无敌 AI；
 * 低生命护甲形态只保留原版凋灵的远程弹射免疫表现。
 */
public final class ArenaWither extends ArenaCreatureSoldier
        implements PowerableMob {

    private static final EntityDataAccessor<Boolean> DATA_ARMORED =
            SynchedEntityData.defineId(
                    ArenaWither.class, EntityDataSerializers.BOOLEAN);

    public ArenaWither(EntityType<? extends PathfinderMob> type,
                       Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ARMORED, false);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.ARCHER_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 0;
    }

    @Override
    public double getMovementSpeed() {
        return 0.25;
    }

    @Override
    public double getMinAttackRange(boolean mounted) {
        return 5.0;
    }

    @Override
    public double getPreferredAttackRange(boolean mounted) {
        return 11.0;
    }

    @Override
    public double getMaxAttackRange(boolean mounted) {
        return 24.0;
    }

    @Override
    protected double getProjectileSpeed() {
        return 1.35;
    }

    @Override
    protected boolean usesDirectFire() {
        return true;
    }

    @Override
    protected double getProjectileGravity() {
        return 0.0;
    }

    @Override
    protected double getShotInaccuracy() {
        return 0.0;
    }

    @Override
    protected SoundEvent getShootSound() {
        return SoundEvents.WITHER_SHOOT;
    }

    @Override
    protected Projectile createProjectile() {
        ArenaWitherSkull skull = new ArenaWitherSkull(
                ModEntities.ARENA_WITHER_SKULL.get(), level());
        skull.setOwner(this);
        skull.setPos(getX(), getEyeY() - 0.15, getZ());
        return skull;
    }

    @Override
    protected void applyArenaProjectileDamage(Projectile projectile,
                                              double damage) {
        if (projectile instanceof ArenaWitherSkull skull) {
            skull.configure(false, (float) damage);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !isArenaUnit()
                || armoredForm() || !isAlive()) {
            return;
        }
        if (getArenaDisplayMaxHealth() > 0.0f
                && getArenaHealthRatio() < 0.50f) {
            setArmoredForm(true);
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (armoredForm()) {
            if (source.getDirectEntity() instanceof AbstractArrow
                    || source.getDirectEntity()
                    instanceof AbstractWindCharge) {
                return false;
            }
        }
        return super.hurt(source, amount);
    }

    public boolean armoredForm() {
        return entityData.get(DATA_ARMORED);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("ArenaWitherArmored", armoredForm());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setArmoredForm(tag.getBoolean("ArenaWitherArmored"));
    }

    private void setArmoredForm(boolean armored) {
        entityData.set(DATA_ARMORED, armored);
        setArenaStatusFlag(ArenaStatusFlags.WITHER_ARMOR, armored);
        if (armored && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY() + getBbHeight() * 0.55, getZ(),
                    36, 0.8, 1.0, 0.8, 0.04);
            playSound(SoundEvents.WITHER_AMBIENT, 1.1f, 0.75f);
        }
    }

    @Override
    public boolean isPowered() {
        return armoredForm();
    }

    public int witherArmoredFormFlag() {
        return armoredForm() ? 1 : 0;
    }

    public boolean fireSkillSkulls(List<AbstractArmySoldier> targets,
                                   double damage) {
        if (!(level() instanceof ServerLevel server)
                || targets.isEmpty()) {
            return false;
        }
        boolean fired = false;
        for (AbstractArmySoldier target : targets) {
            ArenaWitherSkull skull = new ArenaWitherSkull(
                    ModEntities.ARENA_WITHER_SKULL.get(), level());
            skull.setOwner(this);
            skull.setPos(getX(), getEyeY() - 0.15, getZ());
            skull.configure(true, (float) damage);
            Vec3 direction = target.getBoundingBox().getCenter()
                    .subtract(skull.position());
            if (direction.lengthSqr() < 1.0E-6) continue;
            direction = direction.normalize();
            skull.shoot(direction.x, direction.y, direction.z,
                    1.35f, 0.0f);
            fired |= server.addFreshEntity(skull);
        }
        if (fired) {
            setShieldGuardAttackLockTicks(
                    ShieldGuardController.ATTACK_LOCK_TICKS);
            markAttackSuccess();
            level().playSound(null, this, SoundEvents.WITHER_SHOOT,
                    getSoundSource(), 1.4f, 0.72f);
        }
        return fired;
    }

    public float getHeadYRot(int head) {
        return getYRot() + (head == 0 ? -10.0f : 10.0f);
    }

    public float getHeadXRot(int head) {
        return getXRot();
    }
}
