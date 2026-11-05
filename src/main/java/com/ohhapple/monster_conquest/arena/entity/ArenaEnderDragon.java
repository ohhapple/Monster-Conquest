package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.ai.soldier.SoldierMeleeAttackGoal;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 自走棋专属步行末影龙。寻路使用普通碰撞导航，不会破坏方块，
 * 也不会继承原版末影龙的飞行、传送或终末之池行为。
 */
public final class ArenaEnderDragon extends ArenaCreatureSoldier {

    private static final EntityDataAccessor<Boolean> DATA_BREATHING =
            SynchedEntityData.defineId(
                    ArenaEnderDragon.class,
                    EntityDataSerializers.BOOLEAN);

    private int breathVisualUntil;

    public ArenaEnderDragon(EntityType<? extends PathfinderMob> type,
                            Level level) {
        super(type, level);
        setNoGravity(false);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BREATHING, false);
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
        return 0.48;
    }

    @Override
    public double getMinAttackRange(boolean mounted) {
        return 6.0;
    }

    @Override
    public double getPreferredAttackRange(boolean mounted) {
        return 15.0;
    }

    @Override
    public double getMaxAttackRange(boolean mounted) {
        return ArenaSkillBalance.ENDER_DRAGON_BREATH_RANGE;
    }

    @Override
    public int getRangedAttackCooldownTicks() {
        return ArenaSkillBalance
                .ENDER_DRAGON_BREATH_INTERVAL_TICKS;
    }

    @Override
    public int getArenaRangedAttackCooldownTicks() {
        return ArenaSkillBalance
                .ENDER_DRAGON_BREATH_INTERVAL_TICKS;
    }

    @Override
    public void enterArenaMode(UUID matchId, int side, int boardIndex) {
        super.enterArenaMode(matchId, side, boardIndex);
        goalSelector.removeAllGoals(
                goal -> goal instanceof SoldierMeleeAttackGoal);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide
                && isArenaUnit()
                && !isNoAi()
                && getTarget() instanceof LivingEntity target
                && target.isAlive()
                && isArenaEnemy(target)) {
            faceArenaTarget(target);
        }
        if (!level().isClientSide
                && entityData.get(DATA_BREATHING)
                && tickCount > breathVisualUntil) {
            setBreathing(false);
        }
    }

    private void faceArenaTarget(LivingEntity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx)
                * (180.0 / Math.PI)) - 90.0f;
        setYRot(yaw);
        yRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
        setYBodyRot(yaw);
        yBodyRotO = yaw;
        getLookControl().setLookAt(target, 45.0f, 45.0f);
    }

    @Override
    public boolean tryPerformRangedAttack(LivingEntity target) {
        if (!(level() instanceof ServerLevel server)
                || target == null
                || !target.isAlive()
                || !isArenaEnemy(target)
                || distanceTo(target)
                > ArenaSkillBalance.ENDER_DRAGON_BREATH_RANGE + 1.0) {
            return false;
        }

        Vec3 origin = breathOrigin();
        Vec3 aim = target.getBoundingBox().getCenter();
        Vec3 delta = aim.subtract(origin);
        if (delta.lengthSqr() < 1.0E-6) return false;
        Vec3 direction = delta.normalize();
        BlockHitResult centerHit = server.clip(new ClipContext(
                origin, origin.add(direction.scale(
                ArenaSkillBalance.ENDER_DRAGON_BREATH_RANGE)),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        double centerDistance = centerHit.getType() == HitResult.Type.MISS
                ? ArenaSkillBalance.ENDER_DRAGON_BREATH_RANGE
                : origin.distanceTo(centerHit.getLocation());
        if (centerDistance < 0.5
                || origin.distanceToSqr(aim) > centerDistance * centerDistance) {
            return false;
        }

        AABB search = getBoundingBox().inflate(
                ArenaSkillBalance.ENDER_DRAGON_BREATH_RANGE);
        List<AbstractArmySoldier> victims = new ArrayList<>(
                server.getEntitiesOfClass(
                        AbstractArmySoldier.class, search,
                        enemy -> enemy.isAlive()
                                && isArenaEnemy(enemy)
                                && breathLineClear(origin,
                                enemy.getBoundingBox().getCenter(),
                                centerDistance)));
        victims.removeIf(enemy -> !insideBreathCone(
                origin, direction, centerDistance, enemy));
        if (victims.isEmpty()) return false;

        float damage = (float) getAttributeValue(
                Attributes.ATTACK_DAMAGE);
        arenaEquipment().beforeBasicAttack();
        arenaTraits().beforeBasicAttack();
        DamageSource source = damageSources().mobAttack(this);
        for (AbstractArmySoldier enemy : victims) {
            enemy.hurt(source, damage);
        }
        setBreathing(true);
        setShieldGuardAttackLockTicks(
                ShieldGuardController.ATTACK_LOCK_TICKS);
        markAttackSuccess();
        emitBreath(server, origin, direction, centerDistance);
        server.playSound(null, getX(), getY(), getZ(),
                SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 1.15f, 0.78f);
        return true;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENDER_DRAGON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENDER_DRAGON_DEATH;
    }

    public boolean isBreathing() {
        return entityData.get(DATA_BREATHING);
    }

    private void setBreathing(boolean breathing) {
        entityData.set(DATA_BREATHING, breathing);
        breathVisualUntil = breathing ? tickCount + 6 : 0;
    }

    private Vec3 breathOrigin() {
        return new Vec3(getX(), getY() + getBbHeight() * 0.78, getZ())
                .add(getLookAngle().scale(1.0));
    }

    private boolean insideBreathCone(
            Vec3 origin, Vec3 direction, double maxDistance,
            AbstractArmySoldier enemy) {
        Vec3 delta = enemy.getBoundingBox().getCenter().subtract(origin);
        double forward = delta.dot(direction);
        if (forward < 0.0 || forward > maxDistance) return false;
        double coneRadius =
                ArenaSkillBalance.ENDER_DRAGON_BREATH_BASE_RADIUS
                        + forward * Math.tan(Math.toRadians(
                        ArenaSkillBalance
                                .ENDER_DRAGON_BREATH_HALF_ANGLE_DEGREES));
        double lateral = delta.subtract(
                direction.scale(forward)).length();
        return lateral <= coneRadius + enemy.getBbWidth() * 0.45;
    }

    private boolean breathLineClear(
            Vec3 origin, Vec3 target, double maxDistance) {
        Vec3 delta = target.subtract(origin);
        if (delta.lengthSqr() > maxDistance * maxDistance) {
            delta = delta.normalize().scale(maxDistance);
        }
        HitResult hit = level().clip(new ClipContext(
                origin, origin.add(delta),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    private void emitBreath(ServerLevel level, Vec3 origin,
                            Vec3 direction, double distance) {
        Vec3 side = direction.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6) {
            side = direction.cross(new Vec3(1.0, 0.0, 0.0));
        }
        side = side.normalize();
        Vec3 vertical = direction.cross(side).normalize();
        int steps = 10;
        for (int step = 1; step <= steps; step++) {
            double forward = distance * step / steps;
            double radius = ArenaSkillBalance
                    .ENDER_DRAGON_BREATH_BASE_RADIUS
                    + forward * Math.tan(Math.toRadians(
                    ArenaSkillBalance
                            .ENDER_DRAGON_BREATH_HALF_ANGLE_DEGREES));
            Vec3 center = origin.add(direction.scale(forward));
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    center.x, center.y, center.z,
                    4, radius * 0.36, radius * 0.28,
                    radius * 0.36, 0.025);
            if ((step & 1) == 0) {
                Vec3 swirl = side.scale(Math.sin(
                        tickCount * 0.6 + step) * radius * 0.45)
                        .add(vertical.scale(Math.cos(
                                tickCount * 0.6 + step) * radius * 0.24));
                level.sendParticles(ParticleTypes.END_ROD,
                        center.x + swirl.x, center.y + swirl.y,
                        center.z + swirl.z, 1,
                        0.02, 0.02, 0.02, 0.005);
            }
        }
    }
}
