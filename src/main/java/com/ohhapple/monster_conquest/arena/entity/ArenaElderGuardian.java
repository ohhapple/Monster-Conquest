package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.ai.soldier.SoldierMeleeAttackGoal;
import com.ohhapple.monster_conquest.ai.soldier.SoldierRangedAttackGoal;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * 自走棋专属远古守卫者。它不继承原版 Guardian，因此完全没有脱水计时；
 * 寻路和攻击也分别使用竞技跳跃控制与即时射线，不复用主世界行为。
 */
public final class ArenaElderGuardian extends ArenaCreatureSoldier {

    private static final int LASER_STYLE_NONE = 0;
    private static final int LASER_STYLE_BASIC = 1;
    private static final int LASER_STYLE_SKILL = 2;
    private static final double MOVEMENT_SPEED = 0.30;
    private static final double MAX_ATTACK_RANGE = 24.0;
    private static final double PREFERRED_ATTACK_RANGE = 12.0;
    private static final int BASIC_LASER_VISUAL_TICKS = 4;

    private static final EntityDataAccessor<Integer> DATA_LASER_STYLE =
            SynchedEntityData.defineId(
                    ArenaElderGuardian.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_LASER_END_X =
            SynchedEntityData.defineId(
                    ArenaElderGuardian.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LASER_END_Y =
            SynchedEntityData.defineId(
                    ArenaElderGuardian.class,
                    EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LASER_END_Z =
            SynchedEntityData.defineId(
                    ArenaElderGuardian.class,
                    EntityDataSerializers.FLOAT);

    private int basicLaserDamageDepth;
    private int laserVisualUntil;

    public ArenaElderGuardian(EntityType<? extends PathfinderMob> type,
                              Level level) {
        super(type, level);
        this.moveControl = new HoppingMoveControl(this);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LASER_STYLE, LASER_STYLE_NONE);
        builder.define(DATA_LASER_END_X, 0.0f);
        builder.define(DATA_LASER_END_Y, 0.0f);
        builder.define(DATA_LASER_END_Z, 0.0f);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.ARCHER_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 37;
    }

    @Override
    public double getMovementSpeed() {
        return MOVEMENT_SPEED;
    }

    @Override
    public double getMinAttackRange(boolean mounted) {
        return 6.0;
    }

    @Override
    public double getMaxAttackRange(boolean mounted) {
        return MAX_ATTACK_RANGE;
    }

    @Override
    public double getPreferredAttackRange(boolean mounted) {
        return PREFERRED_ATTACK_RANGE;
    }

    @Override
    public void enterArenaMode(UUID matchId, int side,
                               int boardIndex) {
        super.enterArenaMode(matchId, side, boardIndex);
        goalSelector.removeAllGoals(goal ->
                goal instanceof SoldierMeleeAttackGoal
                        || goal instanceof SoldierRangedAttackGoal);
        goalSelector.addGoal(1, new ElderGuardianLaserGoal(this));
        this.moveControl = new HoppingMoveControl(this);
    }

    @Override
    public boolean tryPerformRangedAttack(LivingEntity target) {
        return fireBasicLaser(target);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide
                && getLaserStyle() != LASER_STYLE_NONE
                && tickCount > laserVisualUntil) {
            clearLaserVisual();
        }
    }

    @Override
    public void die(DamageSource source) {
        clearLaserVisual();
        super.die(source);
    }

    public boolean fireBasicLaser(LivingEntity target) {
        if (!(level() instanceof ServerLevel level)
                || target == null || !target.isAlive()
                || !isArenaEnemy(target)
                || distanceTo(target) > MAX_ATTACK_RANGE + 1.0) {
            return false;
        }
        LaserPath path = laserPath(
                target, MAX_ATTACK_RANGE,
                ArenaSkillBalance.ELDER_GUARDIAN_BASIC_RADIUS);
        if (path.targets.isEmpty()) return false;

        float damage = (float) getAttributeValue(
                Attributes.ATTACK_DAMAGE);
        arenaEquipment().beforeBasicAttack();
        beginBasicLaserDamage();
        try {
            DamageSource source = damageSources()
                    .indirectMagic(this, this);
            for (AbstractArmySoldier enemy : path.targets) {
                enemy.hurt(source, damage);
            }
        } finally {
            endBasicLaserDamage();
        }
        showLaser(path.end, LASER_STYLE_BASIC,
                BASIC_LASER_VISUAL_TICKS);
        setShieldGuardAttackLockTicks(
                ShieldGuardController.ATTACK_LOCK_TICKS);
        markAttackSuccess();
        level.playSound(null, getX(), getY(), getZ(),
                SoundEvents.GUARDIAN_ATTACK,
                SoundSource.HOSTILE, 1.2f, 1.0f);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                path.end.x, path.end.y, path.end.z,
                8, 0.25, 0.25, 0.25, 0.04);
        return true;
    }

    /**
     * 每 tick 更新技能大激光的可见轨迹；damagePulse 为 true 时
     * 对该直线上的全部敌人结算一次技能魔法伤害。
     */
    public boolean tickSkillLaser(boolean damagePulse) {
        if (!(level() instanceof ServerLevel level)) return false;
        LivingEntity target = findLaserTarget();
        if (target == null) return false;
        getLookControl().setLookAt(target, 30.0f, 30.0f);
        LaserPath path = laserPath(
                target, ArenaSkillBalance.ELDER_GUARDIAN_SKILL_RANGE,
                ArenaSkillBalance.ELDER_GUARDIAN_SKILL_RADIUS);
        showLaser(path.end, LASER_STYLE_SKILL, 2);
        if (!damagePulse || path.targets.isEmpty()) return true;

        float damage = (float) ArenaSkillBalance
                .elderGuardianLaserDamage(
                        getAttributeValue(Attributes.ATTACK_DAMAGE),
                        getArenaMagicPower());
        DamageSource source = damageSources()
                .indirectMagic(this, this);
        for (AbstractArmySoldier enemy : path.targets) {
            arenaSkills().dealSkillDamage(enemy, source, damage);
        }
        level.playSound(null, getX(), getY(), getZ(),
                SoundEvents.GUARDIAN_ATTACK,
                SoundSource.HOSTILE, 1.5f, 0.75f);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                path.end.x, path.end.y, path.end.z,
                16, 0.45, 0.45, 0.45, 0.06);
        return true;
    }

    public boolean isDealingBasicLaserDamage() {
        return basicLaserDamageDepth > 0;
    }

    public int getLaserStyle() {
        return entityData.get(DATA_LASER_STYLE);
    }

    public boolean hasVisibleLaser() {
        return getLaserStyle() != LASER_STYLE_NONE;
    }

    public boolean isLargeLaser() {
        return getLaserStyle() == LASER_STYLE_SKILL;
    }

    public Vec3 getLaserEnd() {
        return new Vec3(
                entityData.get(DATA_LASER_END_X),
                entityData.get(DATA_LASER_END_Y),
                entityData.get(DATA_LASER_END_Z));
    }

    public void stopSkillLaser() {
        clearLaserVisual();
    }

    private void beginBasicLaserDamage() {
        basicLaserDamageDepth++;
    }

    private void endBasicLaserDamage() {
        if (basicLaserDamageDepth > 0) basicLaserDamageDepth--;
    }

    private void showLaser(Vec3 end, int style, int ticks) {
        entityData.set(DATA_LASER_STYLE, style);
        entityData.set(DATA_LASER_END_X, (float) end.x);
        entityData.set(DATA_LASER_END_Y, (float) end.y);
        entityData.set(DATA_LASER_END_Z, (float) end.z);
        laserVisualUntil = tickCount + Math.max(1, ticks);
    }

    private void clearLaserVisual() {
        entityData.set(DATA_LASER_STYLE, LASER_STYLE_NONE);
        laserVisualUntil = 0;
    }

    private LaserPath laserPath(LivingEntity target, double range,
                                double radius) {
        ServerLevel level = (ServerLevel) level();
        Vec3 start = getEyePosition();
        Vec3 aim = target.getBoundingBox().getCenter();
        Vec3 direction = aim.subtract(start);
        if (direction.lengthSqr() < 1.0E-6) {
            direction = getLookAngle();
        }
        direction = direction.normalize();
        Vec3 rangeEnd = start.add(direction.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(
                start, rangeEnd, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        Vec3 end = blockHit.getType() == HitResult.Type.MISS
                ? rangeEnd : blockHit.getLocation();
        AABB search = new AABB(start, end).inflate(radius + 2.0);
        List<AbstractArmySoldier> targets =
                level.getEntitiesOfClass(
                        AbstractArmySoldier.class, search,
                        enemy -> enemy != this
                                && enemy.isAlive()
                                && enemy.isArenaUnit()
                                && isArenaEnemy(enemy)
                                && enemy.getBoundingBox()
                                .inflate(radius)
                                .clip(start, end).isPresent());
        targets.sort(Comparator.comparingDouble(
                enemy -> enemy.distanceToSqr(start.x,
                        start.y, start.z)));
        return new LaserPath(start, end,
                List.copyOf(targets));
    }

    @Nullable
    private LivingEntity findLaserTarget() {
        LivingEntity current = getTarget();
        if (current != null && current.isAlive()
                && current instanceof AbstractArmySoldier enemy
                && isArenaEnemy(enemy)) {
            return current;
        }
        if (!(level() instanceof ServerLevel level)) return null;
        List<AbstractArmySoldier> candidates =
                level.getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        getBoundingBox().inflate(
                                ArenaSkillBalance
                                        .ELDER_GUARDIAN_SKILL_RANGE),
                        enemy -> enemy.isAlive()
                                && enemy.isArenaUnit()
                                && isArenaEnemy(enemy));
        candidates.sort(Comparator.comparingDouble(
                this::distanceToSqr));
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private record LaserPath(
            Vec3 start, Vec3 end,
            List<AbstractArmySoldier> targets) {
    }

    private static final class ElderGuardianLaserGoal extends Goal {
        private final ArenaElderGuardian guardian;
        private int cooldown;

        private ElderGuardianLaserGoal(
                ArenaElderGuardian guardian) {
            this.guardian = guardian;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public boolean canUse() {
            LivingEntity target = guardian.getTarget();
            return guardian.isArenaUnit()
                    && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            cooldown = 0;
        }

        @Override
        public void tick() {
            LivingEntity target = guardian.getTarget();
            if (target == null || !target.isAlive()) return;
            guardian.getLookControl().setLookAt(
                    target, 30.0f, 30.0f);
            guardian.getNavigation().stop();

            double distance = guardian.distanceTo(target);
            boolean clearShot = guardian.hasClearShot(target);
            HoppingMoveControl movement =
                    guardian.hoppingMoveControl();
            if (distance
                    > guardian.getPreferredAttackRange(false)
                    || !clearShot) {
                double dx = target.getX() - guardian.getX();
                double dz = target.getZ() - guardian.getZ();
                float yaw = (float) (Math.atan2(dz, dx)
                        * 180.0 / Math.PI) - 90.0f;
                movement.setDirection(yaw);
                movement.setWantedMovement(
                        distance > 18.0 ? 1.2 : 1.0);
            } else {
                movement.stopMovement();
            }

            if (cooldown > 0) cooldown--;
            if (guardian.arenaSkills()
                    .isElderGuardianLaserActive()) {
                return;
            }
            if (cooldown <= 0 && clearShot
                    && distance
                    <= guardian.getMaxAttackRange(false) + 1.0) {
                if (guardian.tryPerformRangedAttack(target)) {
                    cooldown = guardian
                            .getArenaRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                }
            }
        }
    }

    private static final class HoppingMoveControl
            extends net.minecraft.world.entity.ai.control.MoveControl {
        private final ArenaElderGuardian guardian;
        private float targetYaw;
        private int jumpDelay = 6;

        private HoppingMoveControl(ArenaElderGuardian guardian) {
            super(guardian);
            this.guardian = guardian;
            targetYaw = guardian.getYRot();
        }

        private void setDirection(float yaw) {
            targetYaw = yaw;
        }

        private void setWantedMovement(double speed) {
            speedModifier = speed;
            operation = Operation.MOVE_TO;
        }

        private void stopMovement() {
            operation = Operation.WAIT;
            guardian.setZza(0.0f);
            guardian.setSpeed(0.0f);
        }

        private int getJumpDelay() {
            return 7 + guardian.getRandom().nextInt(8);
        }

        @Override
        public void tick() {
            guardian.setYRot(rotlerp(
                    guardian.getYRot(), targetYaw, 90.0f));
            guardian.yHeadRot = guardian.getYRot();
            guardian.yBodyRot = guardian.getYRot();
            if (operation != Operation.MOVE_TO) {
                guardian.setZza(0.0f);
                return;
            }
            operation = Operation.WAIT;
            guardian.setZza(1.0f);
            guardian.setSpeed((float) (speedModifier
                    * guardian.getAttributeValue(
                    Attributes.MOVEMENT_SPEED)));
            if (guardian.onGround()) {
                if (jumpDelay-- > 0) {
                    guardian.setZza(0.0f);
                    guardian.setSpeed(0.0f);
                    return;
                }
                jumpDelay = getJumpDelay();
                guardian.getJumpControl().jump();
                guardian.playSound(
                        SoundEvents.SLIME_SQUISH_SMALL,
                        0.35f, 0.75f);
            }
        }
    }

    private HoppingMoveControl hoppingMoveControl() {
        return (HoppingMoveControl) moveControl;
    }
}
