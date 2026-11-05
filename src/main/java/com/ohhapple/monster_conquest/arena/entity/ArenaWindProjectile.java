package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ArenaWindProjectile extends AbstractWindCharge {

    private float damage;
    private boolean skillDamage;
    @Nullable private AbstractArmySoldier arenaOwner;
    @Nullable private UUID arenaMatchId;
    private double explosionRadius =
            ArenaSkillBalance.NORMAL_WIND_EXPLOSION_RADIUS;
    // 竞技伤害会清空原版无敌帧，穿行中的大风弹必须自行节流接触伤害。
    private final Map<UUID, Integer> nextContactTicks = new HashMap<>();

    public ArenaWindProjectile(
            EntityType<? extends ArenaWindProjectile> type,
            Level level) {
        super(type, level);
    }

    public void configure(boolean skillDamage, float damage) {
        this.skillDamage = skillDamage;
        this.damage = Math.max(0.0f, damage);
        this.explosionRadius = isGaleProjectile()
                ? ArenaSkillBalance.GALE_EXPLOSION_RADIUS
                : ArenaSkillBalance.NORMAL_WIND_EXPLOSION_RADIUS;
        if (getOwner() instanceof AbstractArmySoldier owner) {
            arenaOwner = owner;
            arenaMatchId = owner.getArenaMatchId();
        }
    }

    public boolean isSkillProjectile() {
        return skillDamage;
    }

    public boolean isGaleProjectile() {
        return getType() == ModEntities.ARENA_GALE_CHARGE.get();
    }

    @Nullable
    public UUID getArenaMatchId() {
        return arenaMatchId;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (arenaOwner != null && arenaOwner.isArenaUnit()) {
            return target instanceof AbstractArmySoldier victim
                    && victim != arenaOwner
                    && arenaOwner.isArenaEnemy(victim);
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity entity = result.getEntity();
        if (!canHitEntity(entity)) return;
        int readyAt = nextContactTicks.getOrDefault(
                entity.getUUID(), Integer.MIN_VALUE);
        if (tickCount < readyAt) return;
        nextContactTicks.put(entity.getUUID(), tickCount
                + ArenaSkillBalance.GALE_CONTACT_COOLDOWN_TICKS);
        if (arenaOwner != null) {
            arenaOwner.setLastHurtMob(entity);
        }
        entity.hurt(projectileDamageSource(), damage);
        if (entity instanceof AbstractArmySoldier victim
                && victim.isAlive()) {
            applySkillSilence(victim);
        }
        windBurst(position());
    }

    @Override
    protected void onHit(HitResult result) {
        if (result.getType() == HitResult.Type.ENTITY) {
            onHitEntity((EntityHitResult) result);
            level().gameEvent(net.minecraft.world.level.gameevent.GameEvent
                            .PROJECTILE_LAND,
                    result.getLocation(),
                    net.minecraft.world.level.gameevent.GameEvent.Context
                            .of(this, null));
            if (!isGaleProjectile()) discard();
            return;
        }
        if (result instanceof BlockHitResult blockHit) {
            onHitBlock(blockHit);
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        explode(result.getLocation());
    }

    @Override
    protected void explode(Vec3 position) {
        windBurst(position);
    }

    @Override
    public void tick() {
        Entity owner = getOwner();
        if (!level().isClientSide && owner != null && owner.isRemoved()) {
            // 大风弹的寿命由地形和阶段重置决定，不跟随已经退场的主人。
            setOwner(null);
        }
        super.tick();
        if (!level().isClientSide && !isRemoved()
                && level() instanceof ServerLevel serverLevel) {
            boolean emit = isGaleProjectile()
                    ? (tickCount & 1) == 0
                    : (tickCount & 3) == 0;
            if (emit) {
                serverLevel.sendParticles(ParticleTypes.CLOUD,
                        getX(), getY(), getZ(),
                        isGaleProjectile() ? 4 : 1,
                        isGaleProjectile() ? 0.28 : 0.08,
                        isGaleProjectile() ? 0.20 : 0.06,
                        isGaleProjectile() ? 0.28 : 0.08,
                        0.015);
            }
            if (isGaleProjectile() && (tickCount % 6) == 0) {
                serverLevel.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        getX(), getY(), getZ(),
                        1, 0.35, 0.25, 0.35, 0.0);
            }
        }
    }

    private void windBurst(Vec3 center) {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        serverLevel.playSound(null, center.x, center.y, center.z,
                isGaleProjectile() ? SoundEvents.BREEZE_WIND_CHARGE_BURST
                        : SoundEvents.WIND_CHARGE_BURST,
                SoundSource.HOSTILE, 1.0f, 1.0f);
        serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_SMALL,
                center.x, center.y, center.z,
                1, 0.0, 0.0, 0.0, 0.0);
        serverLevel.sendParticles(ParticleTypes.GUST_EMITTER_LARGE,
                center.x, center.y, center.z,
                1, 0.0, 0.0, 0.0, 0.0);
        DamageSource areaDamage = damage > 0.0f
                ? projectileDamageSource() : null;
        for (AbstractArmySoldier enemy :
                serverLevel.getEntitiesOfClass(AbstractArmySoldier.class,
                        new net.minecraft.world.phys.AABB(center, center)
                                .inflate(explosionRadius),
                        other -> other.isAlive()
                                && other.isArenaUnit()
                                && isArenaEnemy(other))) {
            Vec3 away = enemy.getBoundingBox().getCenter()
                    .subtract(center);
            double distance = away.length();
            if (enemy.getBoundingBox().distanceToSqr(center)
                    > explosionRadius * explosionRadius) {
                continue;
            }
            Vec3 horizontal = distance < 1.0E-5
                    ? Vec3.ZERO
                    : new Vec3(away.x / distance, 0.0, away.z / distance);
            if (areaDamage != null) {
                if (arenaOwner != null) {
                    arenaOwner.setLastHurtMob(enemy);
                }
                // 大风弹的范围伤害与直接命中共用同一攻击倍率。
                enemy.hurt(areaDamage, damage);
                if (!enemy.isAlive()) continue;
                applySkillSilence(enemy);
            }
            double strength = 1.0 - distance / explosionRadius;
            enemy.addArenaImpulse(
                    horizontal.scale(strength * 1.15)
                            .add(0.0, strength * 0.7, 0.0));
        }
    }

    private DamageSource projectileDamageSource() {
        return damageSources().windCharge(this, arenaOwner);
    }

    private void applySkillSilence(AbstractArmySoldier target) {
        if (!skillDamage || arenaOwner == null
                || !target.isAlive()) {
            return;
        }
        target.arenaSkills().applySilence(
                ArenaSkillBalance.breezeSilenceTicks(
                        arenaOwner.getArenaStar()));
    }

    private boolean isArenaEnemy(AbstractArmySoldier target) {
        return arenaOwner != null && arenaOwner.isArenaEnemy(target);
    }
}
