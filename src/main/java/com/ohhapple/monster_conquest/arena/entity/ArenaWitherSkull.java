package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/** 竞技凋灵专用的无爆炸、无方块破坏凋灵骷髅头。 */
public final class ArenaWitherSkull extends ThrowableItemProjectile {

    private float damage;
    private boolean skillProjectile;

    public ArenaWitherSkull(
            EntityType<? extends ArenaWitherSkull> type,
            Level level) {
        super(type, level);
    }

    public void configure(boolean skillProjectile, float damage) {
        this.skillProjectile = skillProjectile;
        this.damage = Math.max(0.0f, damage);
    }

    public boolean isSkillProjectile() {
        return skillProjectile;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.WITHER_SKELETON_SKULL;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0;
    }

    @Override
    protected boolean canHitEntity(Entity target) {
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
        super.tick();
        if (!level().isClientSide && !isRemoved()
                && level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL,
                    getX(), getY(), getZ(),
                    2, 0.04, 0.04, 0.04, 0.01);
            if ((tickCount & 1) == 0) {
                server.sendParticles(ParticleTypes.SMOKE,
                        getX(), getY(), getZ(),
                        1, 0.04, 0.04, 0.04, 0.005);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(result.getEntity() instanceof LivingEntity target)) return;
        Entity owner = getOwner();
        if (!(owner instanceof AbstractArmySoldier attacker)
                || !canHitEntity(target)) {
            return;
        }
        boolean hit = target.hurt(skillProjectile
                        ? damageSources().indirectMagic(this, attacker)
                        : damageSources().thrown(this, attacker),
                damage);
        if (hit) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.WITHER,
                    ArenaSkillBalance.WITHER_PASSIVE_TICKS,
                    ArenaSkillBalance.WITHER_EFFECT_AMPLIFIER,
                    false, true), attacker);
        }
        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(),
                    18, 0.32, 0.32, 0.32, 0.03);
            server.playSound(null, getX(), getY(), getZ(),
                    SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE,
                    0.9f, 0.7f);
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) discard();
    }
}
