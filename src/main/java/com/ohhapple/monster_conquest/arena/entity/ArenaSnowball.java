package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaEquipmentRuntime;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class ArenaSnowball extends Snowball {

    private static final double GUIDED_SPEED = 0.90;
    private static final int GUIDED_MAX_TICKS = 100;

    private float arenaDamage;
    private boolean skillProjectile;
    private boolean visualOnly;
    private boolean guided;
    private int visualTicks;
    private int guidedTicks;
    @Nullable private UUID preferredTargetId;

    public ArenaSnowball(EntityType<? extends Snowball> type, Level level) {
        super(type, level);
    }

    public ArenaSnowball(Level level, LivingEntity owner,
                         boolean skillProjectile) {
        super(level, owner);
        this.skillProjectile = skillProjectile;
    }

    public void setArenaProjectileDamage(float damage) {
        arenaDamage = Math.max(0.0f, damage);
    }

    public boolean isSkillProjectile() {
        return skillProjectile;
    }

    public void aimAt(LivingEntity target) {
        guided = true;
        preferredTargetId = target.getUUID();
        setNoGravity(true);
    }

    public void configureVisualOnly() {
        visualOnly = true;
        skillProjectile = true;
        setNoGravity(true);
        setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected double getDefaultGravity() {
        // BallisticAim 按箭矢 0.05 重力求解；雪球必须使用同一物理参数，
        // 否则实际轨迹会高于瞄准点并从目标头顶飞过。
        return 0.05;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (visualOnly) return false;
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
        if (!level().isClientSide) {
            Entity owner = getOwner();
            if (owner == null || owner.isRemoved()
                    || visualOnly && ++visualTicks > 120) {
                discard();
                return;
            }
            if (!visualOnly && guided) {
                tickGuided();
                return;
            }
        }
        super.tick();
    }

    private void tickGuided() {
        if (++guidedTicks > GUIDED_MAX_TICKS) {
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
            onHitEntity(new EntityHitResult(target));
            discard();
            return;
        }
        setDeltaMovement(aim.normalize().scale(GUIDED_SPEED));
        super.tick();
        if (isRemoved()) return;
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY(), getZ(),
                    2, 0.05, 0.05, 0.05, 0.01);
        }
        AbstractArmySoldier swept = findSweptTarget(
                from, position());
        if (swept != null) {
            onHitEntity(new EntityHitResult(swept));
            discard();
        }
    }

    @Nullable
    private LivingEntity resolveTarget() {
        if (!(getOwner() instanceof AbstractArmySoldier owner)
                || !owner.isArenaUnit()) {
            return null;
        }
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
    private AbstractArmySoldier findSweptTarget(
            Vec3 from, Vec3 to) {
        AABB sweep = new AABB(from, to).inflate(0.45);
        for (AbstractArmySoldier target :
                level().getEntitiesOfClass(
                        AbstractArmySoldier.class, sweep,
                        this::canHitEntity)) {
            Optional<Vec3> hit = target.getBoundingBox()
                    .inflate(0.45)
                    .clip(from, to);
            if (hit.isPresent()) return target;
        }
        return null;
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity living)) return;
        if (!canHitEntity(living)) return;
        Entity owner = getOwner();
        if (arenaDamage > 0.0f) {
            living.hurt(skillProjectile
                            ? damageSources().indirectMagic(
                            this, owner)
                            : damageSources().thrown(
                            this, owner),
                    arenaDamage);
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ITEM_SNOWBALL,
                    getX(), getY(), getZ(),
                    12, 0.22, 0.22, 0.22, 0.025);
            server.sendParticles(ParticleTypes.SNOWFLAKE,
                    getX(), getY(), getZ(),
                    8, 0.28, 0.20, 0.28, 0.018);
        }
        if (!skillProjectile) return;
        if (living instanceof AbstractArmySoldier arenaVictim
                && arenaVictim.isArenaUnit()) {
            int star = owner instanceof AbstractArmySoldier arenaOwner
                    ? arenaOwner.getArenaStar() : 1;
            if (!arenaVictim.isArenaControlImmune()) {
                arenaVictim.setTicksFrozen(Math.min(800,
                        arenaVictim.getTicksFrozen()
                                + ArenaSkillBalance.snowGolemFreezeTicks(star)));
            }
            ArenaEquipmentRuntime equipment =
                    arenaVictim.arenaEquipment();
            equipment.applyMagicResistanceShred(
                    ArenaSkillBalance.SNOW_GOLEM_MAGIC_RESIST_STACKS,
                    ArenaSkillBalance.snowGolemMagicResistTicks(star));
        }
    }
}
