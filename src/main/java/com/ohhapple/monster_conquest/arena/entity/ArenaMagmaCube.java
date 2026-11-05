package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaMatchManager;
import com.ohhapple.monster_conquest.arena.unit.ArenaSkillType;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.ModEntities;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 大、中、小岩浆怪共用同一实体类型，分裂阶段只影响体型与显示。
 * 原版 {@code MagmaCube} 的着火、跳跃 AI 和尺寸逻辑不会进入竞技场。
 */
public final class ArenaMagmaCube extends ArenaCreatureSoldier {

    public static final int STAGE_LARGE = 0;
    public static final int STAGE_MEDIUM = 1;
    public static final int STAGE_SMALL = 2;

    private static final EntityDataAccessor<Integer> DATA_SPLIT_STAGE =
            SynchedEntityData.defineId(
                    ArenaMagmaCube.class, EntityDataSerializers.INT);

    public ArenaMagmaCube(EntityType<? extends PathfinderMob> type,
                          Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SPLIT_STAGE, STAGE_LARGE);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 0;
    }

    @Override
    public double getMovementSpeed() {
        return 0.30;
    }

    public int getSplitStage() {
        return entityData.get(DATA_SPLIT_STAGE);
    }

    public void setSplitStage(int stage) {
        entityData.set(DATA_SPLIT_STAGE,
                Math.max(STAGE_LARGE, Math.min(STAGE_SMALL, stage)));
        var scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(getVisualScale());
        }
        refreshDimensions();
    }

    public float getVisualScale() {
        return switch (getSplitStage()) {
            case STAGE_MEDIUM -> 0.5f;
            case STAGE_SMALL -> 0.25f;
            default -> 1.0f;
        };
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ArenaMagmaCubeStage", getSplitStage());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSplitStage(tag.getInt("ArenaMagmaCubeStage"));
    }

    @Override
    protected void onArenaPermanentDeath(DamageSource source) {
        if (!level().isClientSide
                && getSplitStage() == STAGE_LARGE) {
            splitOnDeath((ServerLevel) level());
        }
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        if (!level().isClientSide && onGround()
                && isArenaUnit()
                && target instanceof LivingEntity living
                && isArenaEnemy(living)) {
            hopTowardAttackTarget(target);
        }
        return super.doHurtTarget(target);
    }

    private void splitOnDeath(ServerLevel level) {
        if (getArenaMatchId() == null) return;
        int childStage = Math.min(
                STAGE_SMALL, getSplitStage() + 1);
        boolean created = false;
        for (int i = 0; i < 3; i++) {
            ArenaMagmaCube child = ModEntities.ARENA_MAGMA_CUBE.get()
                    .create(level);
            if (child == null) continue;
            child.setSplitStage(childStage);
            Vec3 spot = position();
            child.moveTo(spot.x, spot.y, spot.z,
                    getYRot(), 0.0f);
            copySplitState(child);
            if (level.addFreshEntity(child)) {
                if (!ArenaMatchManager.registerArenaCombatEntity(child)) {
                    child.discard();
                } else {
                    created = true;
                }
            }
        }
        if (created) arenaTraits().onSummonCreated();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.LAVA,
                getX(), getY() + getBbHeight() * 0.45,
                getZ(), 36, 0.8, 0.7, 0.8, 0.08);
        level.playSound(null, getX(), getY(), getZ(),
                SoundEvents.MAGMA_CUBE_SQUISH, SoundSource.HOSTILE,
                1.2f, 0.72f);
    }

    private void hopTowardAttackTarget(
            net.minecraft.world.entity.Entity target) {
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        double movementX = distance < 1.0E-5
                ? 0.0 : dx / distance * 0.12;
        double movementZ = distance < 1.0E-5
                ? 0.0 : dz / distance * 0.12;
        setDeltaMovement(movementX, 0.20, movementZ);
        hasImpulse = true;
        hurtMarked = true;
        playSound(SoundEvents.MAGMA_CUBE_SQUISH_SMALL,
                0.45f, 1.12f);
    }

    private void copySplitState(ArenaMagmaCube child) {
        child.setFactionId(getFactionId());
        getCommanderUuid().ifPresent(child::setCommanderUuid);
        child.setSquadId(getSquadId());
        child.enterArenaMode(getArenaMatchId(),
                getArenaSide(), getArenaBoardIndex());
        child.copyArenaSourcePiece(this);
        child.arenaTraits().configureSummon(arenaTraits());
        child.setArenaStar(getArenaStar());
        child.configureArenaSkill(
                ArenaSkillType.MAGMA_CUBE_BLESSING, 24);
        child.setArenaEquipmentIds(List.of());
        child.arenaEquipment().setEquipment(List.of());
        child.arenaEquipment().inheritStats(
                arenaEquipment().snapshotInheritedStats());
        child.applyArenaEquipmentAppearance(
                null, null, null, null, false);

        double maxHealth = Math.max(1.0,
                getArenaDisplayMaxHealth() * 0.5);
        double attack = Math.max(0.0,
                getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5);
        child.setArenaDisplayMaxHealth(maxHealth);
        child.setArenaCurrentHealth(maxHealth);
        child.setArenaRangedDamage(attack);
        child.setArenaAttackSpeed(
                getArenaEffectiveAttackSpeed());
        child.setArenaBaseAttackSpeed(
                getArenaBaseAttackSpeed());
        child.setArenaMagicResistance(
                getArenaEffectiveMagicResistance());
        child.setArenaMagicPower(getArenaMagicPower());
        copyAttribute(child, Attributes.MAX_HEALTH,
                Math.min(1024.0, maxHealth));
        copyAttribute(child, Attributes.ATTACK_DAMAGE, attack);
        copyAttribute(child, Attributes.ARMOR,
                getAttributeValue(Attributes.ARMOR));
        copyAttribute(child, Attributes.MOVEMENT_SPEED,
                getAttributeValue(Attributes.MOVEMENT_SPEED));
        copyAttribute(child, Attributes.ATTACK_SPEED,
                getArenaEffectiveAttackSpeed());
        child.arenaTraits().applySummonInheritance();
        child.setPersistenceRequired();
        child.addTag("monster_conquest_arena_magma_cube_split");
        child.syncArenaHealthMirror();
    }

    private static void copyAttribute(
            ArenaMagmaCube target,
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            double value) {
        var instance = target.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }
}
