package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** 女巫技能落地后生成的竞技滞留药水地面效果。 */
public final class ArenaLingeringPotion extends Entity {

    @Nullable private UUID matchId;
    @Nullable private UUID casterId;
    @Nullable private UUID casterPieceId;
    private int arenaSide = -1;
    private int boardIndex = -1;
    private double radius;
    private double pulseDamage;
    private int durationTicks;
    private int nextDamageTick;
    private int nextHealingTick;

    public ArenaLingeringPotion(
            EntityType<? extends ArenaLingeringPotion> type,
            Level level) {
        super(type, level);
        setNoGravity(true);
        setInvulnerable(true);
        setSilent(true);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
    }

    public void configure(AbstractArmySoldier caster,
                          double pulseDamage, double radius,
                          int durationTicks) {
        this.matchId = caster.getArenaMatchId();
        this.casterId = caster.getUUID();
        this.casterPieceId = caster.getArenaSourcePieceId();
        this.arenaSide = caster.getArenaSide();
        this.boardIndex = caster.getArenaBoardIndex();
        this.pulseDamage = Math.max(0.0, pulseDamage);
        this.radius = Math.max(0.1, radius);
        this.durationTicks = Math.max(1, durationTicks);
        this.nextDamageTick = tickCount
                + ArenaSkillBalance
                .WITCH_LINGERING_DAMAGE_INTERVAL_TICKS;
        this.nextHealingTick = tickCount
                + ArenaSkillBalance
                .WITCH_LINGERING_HEAL_INTERVAL_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        if (matchId == null || tickCount >= durationTicks) {
            expire();
            return;
        }
        if ((tickCount % 5) == 0) {
            emitCloudParticles();
        }
        if (tickCount < nextDamageTick
                && tickCount < nextHealingTick) {
            return;
        }
        if (tickCount >= nextDamageTick) {
            nextDamageTick = tickCount
                    + ArenaSkillBalance
                    .WITCH_LINGERING_DAMAGE_INTERVAL_TICKS;
            damagePulse();
        }
        if (tickCount >= nextHealingTick) {
            nextHealingTick = tickCount
                    + ArenaSkillBalance
                    .WITCH_LINGERING_HEAL_INTERVAL_TICKS;
            healingPulse();
        }
    }

    private void healingPulse() {
        if (!(level() instanceof ServerLevel server)) return;
        AABB area = new AABB(
                getX() - radius, getY() - 2.5, getZ() - radius,
                getX() + radius, getY() + 2.5, getZ() + radius);
        for (AbstractArmySoldier ally
                : server.getEntitiesOfClass(
                AbstractArmySoldier.class, area,
                other -> other.isAlive()
                        && !other.isRemoved()
                        && other.isArenaUnit()
                        && matchId.equals(other.getArenaMatchId())
                        && other.getArenaBoardIndex() == boardIndex
                        && other.getArenaSide() == arenaSide)) {
            if (ally.getBoundingBox().distanceToSqr(position())
                    > radius * radius) {
                continue;
            }
            double maxHealth = ally.getArenaDisplayMaxHealth() > 0.0f
                    ? ally.getArenaDisplayMaxHealth()
                    : ally.getMaxHealth();
            float healing = (float) (maxHealth
                    * ArenaSkillBalance
                    .WITCH_LINGERING_ALLY_HEAL_RATIO);
            if (healing > 0.0f) {
                ally.healFromPieceId(casterPieceId, healing);
                server.sendParticles(ParticleTypes.HEART,
                        ally.getX(), ally.getY() + ally.getBbHeight() * 0.65,
                        ally.getZ(), 3,
                        0.18, 0.18, 0.18, 0.01);
            }
        }
    }

    private void damagePulse() {
        if (!(level() instanceof ServerLevel server)) return;
        AbstractArmySoldier caster = casterId != null
                && server.getEntity(casterId)
                instanceof AbstractArmySoldier soldier
                ? soldier : null;
        AABB area = new AABB(
                getX() - radius, getY() - 2.5, getZ() - radius,
                getX() + radius, getY() + 2.5, getZ() + radius);
        for (AbstractArmySoldier target
                : server.getEntitiesOfClass(
                AbstractArmySoldier.class, area,
                other -> other.isAlive()
                        && !other.isRemoved()
                        && other.isArenaUnit()
                        && matchId.equals(other.getArenaMatchId())
                        && other.getArenaBoardIndex() == boardIndex
                        && other.getArenaSide() != arenaSide)) {
            if (target.getBoundingBox().distanceToSqr(position())
                    > radius * radius) {
                continue;
            }
            DamageSource source = caster == null
                    ? damageSources().indirectMagic(this, this)
                    : damageSources().indirectMagic(this, caster);
            if (caster != null) {
                caster.setLastHurtMob(target);
                target.setNextArenaDamageSourcePieceId(casterPieceId);
                try {
                    caster.arenaSkills().dealSkillDamage(
                            target, source, (float) pulseDamage);
                } finally {
                    target.clearNextArenaDamageSourcePieceId();
                }
            } else {
                target.setNextArenaDamageSourcePieceId(casterPieceId);
                try {
                    target.hurt(source, (float) pulseDamage);
                } finally {
                    target.clearNextArenaDamageSourcePieceId();
                }
            }
            server.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + 0.4,
                    target.getZ(), 6,
                    0.25, 0.25, 0.25, 0.02);
        }
    }

    private void emitCloudParticles() {
        if (!(level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.WITCH,
                getX(), getY() + 0.08, getZ(),
                12, radius * 0.55, 0.08, radius * 0.55, 0.01);
        server.sendParticles(ParticleTypes.ITEM_SLIME,
                getX(), getY() + 0.05, getZ(),
                4, radius * 0.42, 0.04, radius * 0.42, 0.01);
        if ((tickCount % 20) == 0) {
            server.sendParticles(ParticleTypes.DRAGON_BREATH,
                    getX(), getY() + 0.14, getZ(),
                    8, radius * 0.35, 0.08, radius * 0.35, 0.01);
        }
    }

    private void expire() {
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD,
                    getX(), getY() + 0.12, getZ(),
                    24, radius * 0.55, 0.15, radius * 0.55, 0.04);
            server.sendParticles(ParticleTypes.WITCH,
                    getX(), getY() + 0.12, getZ(),
                    16, radius * 0.45, 0.10, radius * 0.45, 0.03);
        }
        discard();
    }

    @Nullable
    public UUID getArenaMatchId() {
        return matchId;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (matchId != null) tag.putUUID("ArenaMatch", matchId);
        if (casterId != null) tag.putUUID("Caster", casterId);
        if (casterPieceId != null) tag.putUUID(
                "CasterPiece", casterPieceId);
        tag.putInt("ArenaSide", arenaSide);
        tag.putInt("BoardIndex", boardIndex);
        tag.putDouble("Radius", radius);
        tag.putDouble("PulseDamage", pulseDamage);
        tag.putInt("Duration", durationTicks);
        tag.putInt("NextDamage", nextDamageTick);
        tag.putInt("NextHealing", nextHealingTick);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        matchId = tag.hasUUID("ArenaMatch")
                ? tag.getUUID("ArenaMatch") : null;
        casterId = tag.hasUUID("Caster")
                ? tag.getUUID("Caster") : null;
        casterPieceId = tag.hasUUID("CasterPiece")
                ? tag.getUUID("CasterPiece") : null;
        arenaSide = tag.getInt("ArenaSide");
        boardIndex = tag.getInt("BoardIndex");
        radius = tag.getDouble("Radius");
        pulseDamage = tag.getDouble("PulseDamage");
        durationTicks = tag.getInt("Duration");
        nextDamageTick = tag.getInt("NextDamage");
        nextHealingTick = tag.getInt("NextHealing");
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
