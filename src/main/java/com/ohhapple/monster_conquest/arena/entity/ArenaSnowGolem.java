package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.ai.nav.MeleeReach;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ArenaSnowGolem extends ArenaCreatureSoldier {

    private boolean skillBarrageActive;
    private int remainingToSpawn;
    private int formationIndex;
    private int nextSpawnTick;
    private int nextLaunchTick;
    private final List<ArenaSnowball> visualSnowballs =
            new ArrayList<>();

    public ArenaSnowGolem(EntityType<? extends PathfinderMob> type,
                          Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.ARCHER_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 21;
    }

    @Override
    public double getMovementSpeed() {
        return 0.25;
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && skillBarrageActive) {
            tickSkillSnowballs();
        }
    }

    @Override
    protected double getProjectileSpeed() {
        return 1.5;
    }

    @Override
    protected SoundEvent getShootSound() {
        return SoundEvents.SNOW_GOLEM_SHOOT;
    }

    @Override
    protected Projectile createProjectile() {
        return new ArenaSnowball(level(), this, false);
    }

    @Override
    protected void applyArenaProjectileDamage(Projectile projectile,
                                              double damage) {
        if (projectile instanceof ArenaSnowball snowball) {
            snowball.setArenaProjectileDamage((float) damage);
        }
    }

    public boolean beginSkillSnowballs() {
        if (skillBarrageActive
                || !(level() instanceof ServerLevel serverLevel)) {
            return false;
        }
        List<AbstractArmySoldier> candidates =
                serverLevel.getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        getBoundingBox().inflate(
                                ArenaSkillBalance.SNOW_GOLEM_RANGE + 2.0),
                        other -> other.isAlive()
                                && other.isArenaUnit()
                                && isArenaEnemy(other)
                                && MeleeReach.horizontalSurfaceDistance(
                                this, other)
                                <= ArenaSkillBalance.SNOW_GOLEM_RANGE);
        if (candidates.isEmpty()) return false;
        candidates.sort(Comparator.comparingDouble(
                this::distanceToSqr));
        faceSkillTarget(selectSkillTarget(candidates));
        skillBarrageActive = true;
        remainingToSpawn =
                ArenaSkillBalance.SNOW_GOLEM_SNOWBALL_COUNT;
        formationIndex = 0;
        nextSpawnTick = tickCount;
        nextLaunchTick = Integer.MAX_VALUE;
        visualSnowballs.clear();
        setShieldGuardAttackLockTicks(
                ShieldGuardController.ATTACK_LOCK_TICKS);
        markAttackSuccess();
        playMeleeAttackAnimation();
        playSound(getShootSound(), 1.0f, 0.85f);
        double rotation = tickCount * 0.22;
        for (int index = 0; index < 16; index++) {
            double angle = rotation
                    + index * Math.PI * 2.0 / 16.0;
            serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                    getX() + Math.cos(angle) * 1.35,
                    getY() + 0.18,
                    getZ() + Math.sin(angle) * 1.35,
                    1, 0.03, 0.08, 0.03, 0.01);
        }
        return true;
    }

    public boolean hasActiveSkillBarrage() {
        return skillBarrageActive;
    }

    private void tickSkillSnowballs() {
        visualSnowballs.removeIf(ArenaSnowball::isRemoved);
        if (remainingToSpawn > 0) {
            if (tickCount < nextSpawnTick) return;
            ArenaSnowball visual = new ArenaSnowball(
                    level(), this, true);
            visual.setPos(snowballFormationPoint(formationIndex++));
            visual.configureVisualOnly();
            if (!level().addFreshEntity(visual)) {
                discardSkillBarrage();
                return;
            }
            visualSnowballs.add(visual);
            if (level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                        net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                        visual.getX(), visual.getY(), visual.getZ(),
                        8, 0.16, 0.16, 0.16, 0.02);
            }
            remainingToSpawn--;
            nextSpawnTick = tickCount
                    + ArenaSkillBalance
                    .SNOW_GOLEM_FORMATION_INTERVAL_TICKS;
            if (remainingToSpawn == 0) {
                nextLaunchTick = tickCount + 5;
            }
            return;
        }
        if (visualSnowballs.isEmpty()) {
            skillBarrageActive = false;
            return;
        }
        if (tickCount < nextLaunchTick) return;
        List<AbstractArmySoldier> targets =
                nearestSkillTargets();
        if (targets.isEmpty()) {
            discardSkillBarrage();
            return;
        }
        ArenaSnowball visual = visualSnowballs.remove(0);
        Vec3 origin = visual.position();
        visual.discard();
        launchSkillSnowball(origin, targets.get(0));
        if (visualSnowballs.isEmpty()) {
            skillBarrageActive = false;
        } else {
            nextLaunchTick = tickCount
                    + ArenaSkillBalance
                    .SNOW_GOLEM_LAUNCH_INTERVAL_TICKS;
        }
    }

    private List<AbstractArmySoldier> nearestSkillTargets() {
        if (!(level() instanceof ServerLevel serverLevel)) {
            return List.of();
        }
        List<AbstractArmySoldier> targets =
                serverLevel.getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        getBoundingBox().inflate(
                                ArenaSkillBalance.SNOW_GOLEM_RANGE + 2.0),
                        other -> other.isAlive()
                                && other.isArenaUnit()
                                && isArenaEnemy(other));
        targets.sort(Comparator.comparingDouble(
                this::distanceToSqr));
        return targets;
    }

    private Vec3 snowballFormationPoint(int index) {
        double lane = index
                - (ArenaSkillBalance.SNOW_GOLEM_SNOWBALL_COUNT - 1)
                / 2.0;
        float yaw = getYRot();
        Vec3 forward = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 right = Vec3.directionFromRotation(
                0.0f, yaw + 90.0f);
        double arcHeight = 0.30 - Math.abs(lane) * 0.10;
        return position()
                .add(0.0, getBbHeight() + 0.80
                        + arcHeight, 0.0)
                .add(right.scale(lane * 0.48))
                .add(forward.scale(0.18));
    }

    private void discardSkillBarrage() {
        skillBarrageActive = false;
        remainingToSpawn = 0;
        formationIndex = 0;
        nextLaunchTick = Integer.MAX_VALUE;
        for (ArenaSnowball visual : visualSnowballs) {
            if (!visual.isRemoved()) visual.discard();
        }
        visualSnowballs.clear();
    }

    private AbstractArmySoldier selectSkillTarget(
            List<AbstractArmySoldier> candidates) {
        if (getTarget() instanceof AbstractArmySoldier current
                && candidates.contains(current)) {
            return current;
        }
        return candidates.get(0);
    }

    private void faceSkillTarget(AbstractArmySoldier target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        setYRot(yaw);
        yRotO = yaw;
        yBodyRot = yaw;
        yBodyRotO = yaw;
        setYHeadRot(yaw);
        yHeadRotO = yaw;
    }

    private boolean launchSkillSnowball(
            Vec3 origin, LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        double speed = getProjectileSpeed();
        ArenaSnowball snowball =
                new ArenaSnowball(level(), this, true);
        snowball.setPos(origin);
        snowball.setArenaProjectileDamage((float) (
                getAttributeValue(Attributes.ATTACK_DAMAGE)
                        * ArenaSkillBalance
                        .snowGolemSnowballAttackMultiplier(
                                getArenaStar(),
                                getArenaMagicPower())));
        Vec3 direction = target.getBoundingBox().getCenter()
                .subtract(origin);
        if (direction.lengthSqr() < 1.0E-6) return false;
        direction = direction.normalize();
        snowball.aimAt(target);
        snowball.shoot(direction.x, direction.y, direction.z,
                (float) speed, 0.0f);
        if (!level().addFreshEntity(snowball)) return false;
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.SNOWFLAKE,
                origin.x, origin.y, origin.z,
                10, 0.12, 0.12, 0.12, 0.025);
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.ITEM_SNOWBALL,
                target.getX(), target.getY() + 0.5, target.getZ(),
                6, 0.2, 0.2, 0.2, 0.02);
        playSound(getShootSound(), 0.8f, 1.1f);
        return true;
    }

}
