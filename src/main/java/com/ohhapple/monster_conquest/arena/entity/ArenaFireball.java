package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class ArenaFireball extends ThrowableItemProjectile {

    private static final double SKILL_HOMING_SPEED = 0.90;
    private static final double SKILL_HIT_PADDING = 0.45;
    private static final int SKILL_MAX_LIFETIME_TICKS = 100;
    private static final int BARRAGE_VISUAL_MAX_TICKS = 120;

    private float damage;
    private int burnTicks;
    private boolean skillProjectile;
    private boolean guidedSkill;
    private boolean barrageVisual;
    private int guidedTicks;
    private int visualTicks;
    @Nullable private UUID preferredTargetId;

    public ArenaFireball(EntityType<? extends ArenaFireball> type,
                         Level level) {
        super(type, level);
    }

    public void configure(boolean skillProjectile, float damage,
                          int star) {
        this.skillProjectile = skillProjectile;
        this.guidedSkill = skillProjectile;
        this.damage = Math.max(0.0f, damage);
        this.burnTicks = 100
                + (Math.max(1, Math.min(3, star)) - 1) * 20;
    }

    public void configureBarrageVisual() {
        barrageVisual = true;
        skillProjectile = false;
        guidedSkill = false;
        damage = 0.0f;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
    }

    public void aimAt(LivingEntity target) {
        preferredTargetId = target.getUUID();
    }

    public boolean isSkillProjectile() {
        return skillProjectile;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.FIRE_CHARGE;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (barrageVisual) return false;
        Entity owner = getOwner();
        if (owner instanceof AbstractArmySoldier attacker
                && attacker.isArenaUnit()
                && target instanceof LivingEntity living
                && living != owner) {
            return target instanceof AbstractArmySoldier victim
                    && attacker.isArenaEnemy(victim);
        }
        return super.canHitEntity(target);
    }

    @Override
    public void tick() {
        if (!level().isClientSide && barrageVisual) {
            Entity owner = getOwner();
            if (owner == null || owner.isRemoved()
                    || ++visualTicks > BARRAGE_VISUAL_MAX_TICKS) {
                discard();
                return;
            }
            super.tick();
            if (level() instanceof ServerLevel server
                    && (tickCount & 3) == 0) {
                server.sendParticles(ParticleTypes.SMALL_FLAME,
                        getX(), getY(), getZ(),
                        1, 0.04, 0.04, 0.04, 0.005);
            }
            return;
        }
        if (!level().isClientSide && guidedSkill) {
            tickGuidedSkillProjectile();
            return;
        }
        super.tick();
    }

    private void tickGuidedSkillProjectile() {
        if (++guidedTicks > SKILL_MAX_LIFETIME_TICKS) {
            discard();
            return;
        }
        LivingEntity target = resolveTarget();
        if (target == null) {
            discard();
            return;
        }
        Vec3 from = position();
        Vec3 aim = target.getBoundingBox().getCenter()
                .subtract(from);
        if (aim.lengthSqr() < 1.0E-6) {
            hitTarget(target);
            return;
        }
        setDeltaMovement(aim.normalize().scale(
                SKILL_HOMING_SPEED));
        super.tick();
        if (isRemoved()) return;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME,
                    getX(), getY(), getZ(),
                    2, 0.05, 0.05, 0.05, 0.01);
            server.sendParticles(ParticleTypes.SMOKE,
                    getX(), getY(), getZ(),
                    1, 0.06, 0.06, 0.06, 0.005);
        }
        AbstractArmySoldier swept = findSweptTarget(
                from, position());
        if (swept != null) hitTarget(swept);
    }

    @Nullable
    private LivingEntity resolveTarget() {
        if (!(getOwner() instanceof AbstractArmySoldier attacker)
                || !attacker.isArenaUnit()) {
            return null;
        }
        if (preferredTargetId != null
                && level() instanceof ServerLevel server) {
            Entity preferred = server.getEntity(preferredTargetId);
            if (preferred instanceof LivingEntity living
                    && living.isAlive()
                    && living instanceof AbstractArmySoldier victim
                    && attacker.isArenaEnemy(victim)) {
                return living;
            }
        }
        return level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        attacker.getBoundingBox().inflate(32.0),
                        enemy -> enemy.isAlive()
                                && enemy.isArenaUnit()
                                && attacker.isArenaEnemy(enemy))
                .stream()
                .min(Comparator.comparingDouble(
                        enemy -> enemy.distanceToSqr(position())))
                .map(enemy -> {
                    preferredTargetId = enemy.getUUID();
                    return (LivingEntity) enemy;
                })
                .orElse(null);
    }

    @Nullable
    private AbstractArmySoldier findSweptTarget(
            Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(
                SKILL_HIT_PADDING);
        for (AbstractArmySoldier target :
                level().getEntitiesOfClass(
                        AbstractArmySoldier.class, sweep,
                        this::canHitEntity)) {
            Optional<Vec3> hit = target.getBoundingBox()
                    .inflate(SKILL_HIT_PADDING)
                    .clip(from, to);
            if (hit.isPresent()) return target;
        }
        return null;
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide) return;
        if (result instanceof EntityHitResult hit
                && hit.getEntity() instanceof LivingEntity target
                && canHitEntity(target)) {
            hitTarget(target);
            return;
        }
        discardWithImpact();
    }

    private void hitTarget(LivingEntity target) {
        Entity owner = getOwner();
        if (owner instanceof AbstractArmySoldier attacker
                && attacker.isArenaUnit()
                && (!(target instanceof AbstractArmySoldier victim)
                || !attacker.isArenaEnemy(victim))) {
            return;
        }
        target.setRemainingFireTicks(Math.max(
                target.getRemainingFireTicks(), burnTicks));
        if (owner instanceof AbstractArmySoldier attacker) {
            target.hurt(skillProjectile
                            ? damageSources().indirectMagic(this, attacker)
                            : damageSources().thrown(this, attacker),
                    damage);
        }
        discardWithImpact();
    }

    private void discardWithImpact() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME,
                    getX(), getY() + 0.1, getZ(),
                    12, 0.25, 0.25, 0.25, 0.02);
            server.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE,
                    0.8f, 1.15f);
        }
        discard();
    }
}
