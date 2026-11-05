package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

public final class ArenaBreeze extends ArenaCreatureSoldier {

    private static final double GALE_LAUNCH_EYE_OFFSET = 0.15;

    public ArenaBreeze(EntityType<? extends PathfinderMob> type,
                       Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.ARCHER_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 25;
    }

    @Override
    public double getMovementSpeed() {
        return 0.30;
    }

    @Override
    protected double getProjectileSpeed() {
        return 1.25;
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
        return SoundEvents.BREEZE_SHOOT;
    }

    @Override
    protected Projectile createProjectile() {
        ArenaWindProjectile projectile = new ArenaWindProjectile(
                ModEntities.ARENA_WIND_CHARGE.get(), level());
        projectile.setOwner(this);
        projectile.setPos(getX(), getEyeY() - 0.1, getZ());
        return projectile;
    }

    @Override
    protected void applyArenaProjectileDamage(Projectile projectile,
                                              double damage) {
        if (projectile instanceof ArenaWindProjectile wind) {
            wind.configure(false, (float) damage);
        }
    }

    @Override
    public boolean tryPerformRangedAttack(LivingEntity target) {
        if (getArenaStar() < 3) {
            return super.tryPerformRangedAttack(target);
        }
        if (!(level() instanceof ServerLevel serverLevel)
                || target == null || !target.isAlive()) {
            return false;
        }
        double maxRange = getMaxAttackRange(
                getSoldierType().isCavalry());
        if (distanceTo(target) > maxRange + 2.0
                || !hasClearShot(target)) {
            return false;
        }
        Vec3 direction = solveGaleAim(target);

        float damage = (float) (getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes
                        .ATTACK_DAMAGE)
                * ArenaSkillBalance
                .BREEZE_THREE_STAR_BASIC_MULTIPLIER);
        ArenaWindProjectile charge = createGaleProjectile(
                false, damage);
        charge.shoot(direction.x, direction.y, direction.z,
                (float) ArenaSkillBalance.GALE_PROJECTILE_SPEED, 0.0f);
        serverLevel.addFreshEntity(charge);
        arenaEquipment().beforeBasicAttack();
        setShieldGuardAttackLockTicks(
                ShieldGuardController.ATTACK_LOCK_TICKS);
        markAttackSuccess();
        playSound(SoundEvents.BREEZE_SHOOT, 1.0f, 1.0f);
        swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** 发射一个缓慢、可穿透的大风弹。 */
    public boolean fireGaleCharge() {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        AbstractArmySoldier target = selectGaleTarget(serverLevel);
        if (target == null) return false;

        float damage = (float) (getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.breezeGaleAttackMultiplier(
                getArenaStar(), getArenaMagicPower()));
        ArenaWindProjectile charge = createGaleProjectile(
                true, damage);
        Vec3 direction = solveGaleAim(target);
        charge.shoot(direction.x, direction.y, direction.z,
                (float) ArenaSkillBalance.GALE_PROJECTILE_SPEED, 0.0f);
        serverLevel.addFreshEntity(charge);
        setShieldGuardAttackLockTicks(
                ShieldGuardController.ATTACK_LOCK_TICKS);
        playSound(SoundEvents.BREEZE_SHOOT, 1.0f, 1.0f);
        markAttackSuccess();
        return true;
    }

    private ArenaWindProjectile createGaleProjectile(
            boolean skillDamage, float damage) {
        ArenaWindProjectile charge = new ArenaWindProjectile(
                ModEntities.ARENA_GALE_CHARGE.get(), level());
        charge.setOwner(this);
        Vec3 launch = galeLaunchPosition();
        charge.setPos(launch.x, launch.y, launch.z);
        charge.configure(skillDamage, damage);
        return charge;
    }

    private Vec3 solveGaleAim(LivingEntity target) {
        Vec3 velocity = AiNavUtil.observedHorizontalMovement(target);
        Vec3 launch = galeLaunchPosition();
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double speed = ArenaSkillBalance.GALE_PROJECTILE_SPEED;
        Vec3 aimPoint = solveIntercept(
                launch, targetCenter, velocity, speed);
        if (aimPoint == null) {
            aimPoint = targetCenter;
            for (int i = 0; i < 8; i++) {
                double flightTicks = aimPoint.distanceTo(launch) / speed;
                Vec3 next = targetCenter.add(velocity.scale(flightTicks));
                if (next.distanceToSqr(aimPoint) < 0.0025) {
                    aimPoint = next;
                    break;
                }
                aimPoint = next;
            }
        }
        Vec3 direction = aimPoint.subtract(launch);
        return direction.lengthSqr() < 1.0E-6
                ? getLookAngle() : direction.normalize();
    }

    private Vec3 galeLaunchPosition() {
        return new Vec3(getX(), getEyeY() - GALE_LAUNCH_EYE_OFFSET,
                getZ());
    }

    @Nullable
    private static Vec3 solveIntercept(Vec3 launch, Vec3 center,
                                       Vec3 velocity, double speed) {
        Vec3 relative = center.subtract(launch);
        double a = velocity.lengthSqr() - speed * speed;
        double b = 2.0 * relative.dot(velocity);
        double c = relative.lengthSqr();
        double time = Double.NaN;
        if (Math.abs(a) < 1.0E-7) {
            if (Math.abs(b) > 1.0E-7) time = -c / b;
        } else {
            double discriminant = b * b - 4.0 * a * c;
            if (discriminant < 0.0) return null;
            double root = Math.sqrt(discriminant);
            double first = (-b - root) / (2.0 * a);
            double second = (-b + root) / (2.0 * a);
            if (first > 0.0) time = first;
            if (second > 0.0 && (!(time > 0.0) || second < time)) {
                time = second;
            }
        }
        return time > 0.0
                ? center.add(velocity.scale(time)) : null;
    }

    @Nullable
    private AbstractArmySoldier selectGaleTarget(ServerLevel level) {
        if (getTarget() instanceof AbstractArmySoldier current
                && current.isAlive()
                && current.isArenaUnit()
                && isArenaEnemy(current)) {
            return current;
        }
        List<AbstractArmySoldier> targets = level.getEntitiesOfClass(
                AbstractArmySoldier.class,
                getBoundingBox().inflate(64.0),
                other -> other.isAlive()
                        && other.isArenaUnit()
                        && isArenaEnemy(other));
        targets.sort(Comparator.comparingDouble(this::distanceToSqr));
        return targets.isEmpty() ? null : targets.get(0);
    }
}
