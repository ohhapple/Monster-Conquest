package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public final class ArenaWarden extends ArenaCreatureSoldier {

    private static final int ATTACK_ANIMATION_TICKS = 8;
    private static final int SONIC_ANIMATION_TICKS = 60;
    private int attackAnimationStartTick = -1;
    private int sonicAnimationStartTick = -1;

    public ArenaWarden(EntityType<? extends PathfinderMob> type,
                       Level level) {
        super(type, level);
    }

    @Override
    public SoldierType getDefaultSoldierType() {
        return SoldierType.MELEE_INFANTRY;
    }

    @Override
    public int getRenderIndex() {
        return 49;
    }

    @Override
    public double getMovementSpeed() {
        return 0.30;
    }

    @Override
    public SoldierRole getRole() {
        if (isArenaUnit() && getArenaStar() >= 3) {
            return SoldierRole.ARCHER;
        }
        return super.getRole();
    }

    @Override
    public double getMinAttackRange(boolean mounted) {
        return isArenaUnit() && getArenaStar() >= 3
                ? 10.0 : super.getMinAttackRange(mounted);
    }

    @Override
    public double getMaxAttackRange(boolean mounted) {
        return isArenaUnit() && getArenaStar() >= 3
                ? ArenaSkillBalance.WARDEN_SONIC_RANGE
                : super.getMaxAttackRange(mounted);
    }

    @Override
    public double getPreferredAttackRange(boolean mounted) {
        return isArenaUnit() && getArenaStar() >= 3
                ? 18.0
                : super.getPreferredAttackRange(mounted);
    }

    @Override
    public boolean tryPerformRangedAttack(
            LivingEntity target) {
        if (isArenaUnit() && getArenaStar() >= 3) {
            return arenaSkills()
                    .fireWardenBasicSonic(target);
        }
        return super.tryPerformRangedAttack(target);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!level().isClientSide && isArenaUnit()) {
            level().broadcastEntityEvent(this, (byte) 4);
            playSound(SoundEvents.WARDEN_ATTACK_IMPACT,
                    2.5f, getVoicePitch());
        }
        return super.doHurtTarget(target);
    }

    @Override
    public void handleEntityEvent(byte eventId) {
        if (eventId == 4) {
            attackAnimationStartTick = tickCount;
            return;
        }
        if (eventId == 62) {
            sonicAnimationStartTick = tickCount;
            return;
        }
        super.handleEntityEvent(eventId);
    }

    public int attackAnimationStartTick() {
        return attackAnimationStartTick;
    }

    public int sonicAnimationStartTick() {
        return sonicAnimationStartTick;
    }

    public boolean isAttackAnimationActive() {
        return attackAnimationStartTick >= 0
                && tickCount - attackAnimationStartTick
                < ATTACK_ANIMATION_TICKS;
    }

    public boolean isSonicAnimationActive() {
        return sonicAnimationStartTick >= 0
                && tickCount - sonicAnimationStartTick
                < SONIC_ANIMATION_TICKS;
    }
}
