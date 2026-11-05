package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.army.CombatRules;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.Comparator;
import java.util.UUID;

/** 溺尸竞技技能使用的独立三叉戟，不继承主世界装备伤害。 */
public final class ArenaTrident extends ThrownTrident {

    private static final double GUIDED_SPEED = 0.95;
    private static final int GUIDED_MAX_TICKS = 100;

    private float damage;
    private int stunTicks;
    private int debuffTicks;
    private boolean barrageVisual;
    private boolean guided;
    private int visualTicks;
    private int guidedTicks;
    @Nullable private UUID preferredTargetId;

    public ArenaTrident(
            EntityType<? extends ArenaTrident> type, Level level) {
        super(type, level);
        pickup = Pickup.DISALLOWED;
    }

    public void configureSkill(float damage, int stunTicks,
                               int debuffTicks) {
        this.damage = Math.max(0.0f, damage);
        this.stunTicks = Math.max(0, stunTicks);
        this.debuffTicks = Math.max(0, debuffTicks);
    }

    public void configureBarrageVisual() {
        barrageVisual = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
    }

    public void aimAt(LivingEntity target) {
        guided = true;
        preferredTargetId = target.getUUID();
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (barrageVisual) return false;
        return arenaOwner() != null
                && target instanceof AbstractArmySoldier victim
                && victim != arenaOwner()
                && arenaOwner().isArenaEnemy(victim);
    }

    @Override
    @Nullable
    protected EntityHitResult findHitEntity(
            Vec3 startVec, Vec3 endVec) {
        Vec3 move = endVec.subtract(startVec);
        AABB search = getBoundingBox()
                .expandTowards(move)
                .inflate(1.0);
        List<Entity> candidates = level().getEntities(
                this, search, this::canHitEntity);
        double bestDistance = Double.MAX_VALUE;
        Entity bestEntity = null;
        Vec3 bestHit = null;
        for (Entity candidate : candidates) {
            Optional<Vec3> hit = candidate.getBoundingBox()
                    .inflate(0.35)
                    .clip(startVec, endVec);
            if (hit.isEmpty()) continue;
            double distance = startVec.distanceToSqr(hit.get());
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            bestEntity = candidate;
            bestHit = hit.get();
        }
        return bestEntity == null
                ? null
                : new EntityHitResult(bestEntity, bestHit);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (level().isClientSide) {
            discard();
            return;
        }
        if (!(result.getEntity()
                instanceof AbstractArmySoldier victim)
                || !canHitEntity(victim)
                || !(getOwner()
                instanceof AbstractArmySoldier attacker)) {
            discard();
            return;
        }
        CombatRules.clearInvulnerabilityForNonManagedAttacker(
                victim, attacker);
        victim.hurt(
                damageSources().indirectMagic(this, attacker),
                damage);
        if (victim.isAlive()) {
            if (!victim.isArenaControlImmune()) {
                victim.arenaSkills().applyStun(stunTicks);
            }
            if (!victim.isArenaDebuffImmune()) {
                victim.addEffect(new MobEffectInstance(
                        ModEffects.ARMOR_BREAK, debuffTicks,
                        ArenaSkillBalance
                                .DROWNED_TRIDENT_ARMOR_BREAK_LEVEL - 1,
                        false, true), attacker);
            }
            victim.arenaEquipment()
                    .applyMagicResistancePercentReduction(
                            ArenaSkillBalance
                                    .DROWNED_TRIDENT_MR_REDUCTION,
                            debuffTicks);
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH,
                    getX(), getY(), getZ(), 14,
                    0.25, 0.25, 0.25, 0.08);
        }
        playSound(SoundEvents.TRIDENT_HIT, 1.0f, 1.0f);
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide) {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SPLASH,
                        result.getLocation().x,
                        result.getLocation().y,
                        result.getLocation().z,
                        10, 0.2, 0.2, 0.2, 0.05);
            }
            discard();
        }
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            Entity owner = getOwner();
            if (owner == null || owner.isRemoved()
                    || barrageVisual && ++visualTicks > 120) {
                discard();
                return;
            }
            if (!barrageVisual && guided) {
                tickGuidedProjectile();
                return;
            }
        }
        super.tick();
    }

    private void tickGuidedProjectile() {
        if (++guidedTicks > GUIDED_MAX_TICKS) {
            discard();
            return;
        }
        LivingEntity target = resolveTarget();
        if (target == null) {
            discard();
            return;
        }
        Vec3 aim = target.getBoundingBox().getCenter()
                .subtract(position());
        if (aim.lengthSqr() < 1.0E-6) {
            onHitEntity(new EntityHitResult(target));
            return;
        }
        setDeltaMovement(aim.normalize().scale(GUIDED_SPEED));
        super.tick();
        if (!isRemoved() && level() instanceof ServerLevel server) {
            server.sendParticles(
                    (guidedTicks & 1) == 0
                            ? ParticleTypes.ELECTRIC_SPARK
                            : ParticleTypes.BUBBLE,
                    getX(), getY(), getZ(),
                    2, 0.05, 0.05, 0.05, 0.015);
        }
    }

    @Nullable
    private LivingEntity resolveTarget() {
        AbstractArmySoldier owner = arenaOwner();
        if (owner == null) return null;
        if (preferredTargetId != null
                && level() instanceof ServerLevel server) {
            Entity entity = server.getEntity(preferredTargetId);
            if (entity instanceof AbstractArmySoldier target
                    && target.isAlive()
                    && owner.isArenaEnemy(target)) {
                return target;
            }
        }
        return level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        owner.getBoundingBox().inflate(32.0),
                        target -> target.isAlive()
                                && target.isArenaUnit()
                                && owner.isArenaEnemy(target))
                .stream()
                .min(Comparator.comparingDouble(
                        target -> target.distanceToSqr(position())))
                .map(target -> {
                    preferredTargetId = target.getUUID();
                    return (LivingEntity) target;
                })
                .orElse(null);
    }

    @Nullable
    private AbstractArmySoldier arenaOwner() {
        return getOwner() instanceof AbstractArmySoldier owner
                && owner.isArenaUnit() ? owner : null;
    }
}
