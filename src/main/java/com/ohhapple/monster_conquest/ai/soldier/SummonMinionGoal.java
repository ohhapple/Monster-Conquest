package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 战斗中自动补充召唤物，战斗完全结束后清除。
 *   没有移动旗标，不会与施法 / 治疗 / 追击抢导航；每个召唤者独立按 UUID
 *   记录自己的召唤物，最多同时存在 {@code maxAlive} 个。
 */
public class SummonMinionGoal extends Goal {

    private static final int CHECK_INTERVAL = 20;

    private final AbstractArmySoldier summoner;
    private final DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type;
    private final int maxAlive;
    private final int summonInterval;
    private int cooldown = -1;

    public SummonMinionGoal(
            AbstractArmySoldier summoner,
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
            int maxAlive,
            int summonInterval) {
        this.summoner = summoner;
        this.type = type;
        this.maxAlive = Math.max(1, maxAlive);
        this.summonInterval = Math.max(CHECK_INTERVAL, summonInterval);
        this.setFlags(EnumSet.noneOf(Goal.Flag.class));
    }

    @Override
    public boolean canUse() {
        return !summoner.level().isClientSide
                && summoner.level() instanceof ServerLevel
                && summoner.isAlive()
                && !summoner.isRemoved();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        cooldown = 20 + summoner.getRandom().nextInt(30);
    }

    @Override
    public void tick() {
        if (!(summoner.level() instanceof ServerLevel serverLevel)) return;
        if (--cooldown > 0) return;

        if (!armyHasCombatOrHatred(serverLevel)) {
            summoner.clearSummonedUnits(serverLevel);
            cooldown = CHECK_INTERVAL;
            return;
        }

        int alive = summoner.countLivingSummons(serverLevel);
        if (alive >= maxAlive) {
            cooldown = CHECK_INTERVAL;
            return;
        }

        AbstractArmySoldier summonedUnit = createSummonedUnit(serverLevel);
        if (summonedUnit == null) {
            cooldown = summonInterval;
            return;
        }

        Vec3 spot = findSpawnSpot(serverLevel, summonedUnit);
        if (spot == null) {
            summonedUnit.discard();
            cooldown = summonInterval;
            return;
        }

        summonedUnit.moveTo(spot.x, spot.y, spot.z, summoner.getYRot(), 0.0f);
        summonedUnit.setTier(EquipmentTier.TIER_0);
        summonedUnit.applyEquipment(EquipmentTier.TIER_0);
        summonedUnit.setHealth(summonedUnit.getMaxHealth());
        summonedUnit.setSummoner(summoner.getUUID());
        summonedUnit.setSummonRoamAnchor(spot);
        summonedUnit.setFactionId(summoner.getFactionId());
        summonedUnit.setSquadId(summoner.getSquadId());
        summoner.getCommanderUuid().ifPresent(summonedUnit::setCommanderUuid);
        if (summoner.level() instanceof ServerLevel level
                && level.addFreshEntity(summonedUnit)) {
            summoner.registerSummonedUnit(summonedUnit.getUUID());
            playSummonEffects(serverLevel, spot);
        }
        cooldown = summonInterval;
    }

    /**
     * 召唤与清除共用同一条战斗状态链：当前目标 / 近期受击 / 个人受击记忆，
     * 同一军队内所有士兵与召唤物，NPC 首领仇恨表，以及玩家共享仇恨名单。
     */
    private boolean armyHasCombatOrHatred(ServerLevel level) {
        if (summoner.hasActiveCombatOrHatred()) return true;
        for (AbstractArmySoldier summoned : summoner.getLivingSummonedSoldiers(level)) {
            if (summoned.hasActiveCombatOrHatred()) return true;
        }

        UUID army = AbstractArmySoldier.resolveArmyUuid(summoner, level);
        AABB box = summoner.getBoundingBox()
                .inflate(GeneralConfig.armyFollowRange * 2.0);
        for (AbstractArmySoldier member : level.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                soldier -> soldier.isAlive()
                        && army != null
                        && army.equals(AbstractArmySoldier.resolveArmyUuid(soldier, level)))) {
            if (member.hasActiveCombatOrHatred()) return true;
        }

        AbstractArmyCommander commander = summoner.getCommander();
        if (commander != null && commander.isAlive()) {
            LivingEntity target = commander.getTarget();
            if (target != null && target.isAlive()) return true;
            if (commander.isUnderRecentAttack()
                    || commander.getCurrentThreat() != null
                    || !commander.getHatredList().isEmpty()) {
                return true;
            }
        }

        UUID ownerId = summoner.getCommanderUuid().orElse(null);
        if (ownerId != null) {
            var state = PlayerArmyManager.getIfPresent(ownerId);
            if (state != null && state.hasActiveSharedHatred(level.getGameTime())) {
                return true;
            }
        }
        return false;
    }

    private AbstractArmySoldier createSummonedUnit(ServerLevel level) {
        return type.get().create(level) instanceof AbstractArmySoldier summonedUnit
                ? summonedUnit : null;
    }

    private Vec3 findSpawnSpot(ServerLevel level, AbstractArmySoldier summonedUnit) {
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = summoner.getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 1.5 + summoner.getRandom().nextDouble() * 2.5;
            double x = summoner.getX() + Math.cos(angle) * radius;
            double z = summoner.getZ() + Math.sin(angle) * radius;
            double y = summoner.getY() - 0.35 + summoner.getRandom().nextDouble() * 2.35;
            y = Math.max(y, level.getMinBuildHeight() + 1.0);

            summonedUnit.moveTo(x, y, z, summoner.getYRot(), 0.0f);
            AABB box = summonedUnit.getBoundingBox();
            if (!level.noCollision(summonedUnit, box)) continue;
            return new Vec3(x, y, z);
        }
        return null;
    }

    private void playSummonEffects(ServerLevel level, Vec3 spot) {
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.0f, 1.0f);
        if (type == ModEntities.VEX_FLYING) {
            level.playSound(null, spot.x, spot.y, spot.z,
                    SoundEvents.VEX_CHARGE, SoundSource.HOSTILE, 0.8f, 1.15f);
        } else {
            level.playSound(null, spot.x, spot.y, spot.z,
                    SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, SoundSource.NEUTRAL, 1.0f, 1.1f);
        }
        level.sendParticles(ParticleTypes.POOF,
                spot.x, spot.y + 0.4, spot.z, 14, 0.35, 0.35, 0.35, 0.02);
        level.sendParticles(ParticleTypes.ENCHANT,
                spot.x, spot.y + 0.5, spot.z, 12, 0.45, 0.55, 0.45, 0.6);
    }
}
