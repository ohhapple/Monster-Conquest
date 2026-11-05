package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.ModSounds;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.MeleeReach;
import com.ohhapple.monster_conquest.arena.unit.ArenaSkillType;
import com.ohhapple.monster_conquest.arena.entity.ArenaGoat;
import com.ohhapple.monster_conquest.arena.entity.ArenaBreeze;
import com.ohhapple.monster_conquest.arena.entity.ArenaElderGuardian;
import com.ohhapple.monster_conquest.arena.entity.ArenaLingeringPotion;
import com.ohhapple.monster_conquest.arena.entity.ArenaPig;
import com.ohhapple.monster_conquest.arena.entity.ArenaFireball;
import com.ohhapple.monster_conquest.arena.entity.ArenaSnowGolem;
import com.ohhapple.monster_conquest.arena.entity.ArenaSnowball;
import com.ohhapple.monster_conquest.arena.entity.ArenaTrident;
import com.ohhapple.monster_conquest.arena.entity.ArenaWolf;
import com.ohhapple.monster_conquest.arena.entity.ArenaWindProjectile;
import com.ohhapple.monster_conquest.arena.entity.ArenaWither;
import com.ohhapple.monster_conquest.arena.entity.ArenaWitherSkull;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.projectile.ArmyArrow;
import com.ohhapple.monster_conquest.entity.projectile.WitchPotionEntity;
import com.ohhapple.monster_conquest.entity.soldiers.EvokerSoldier;
import com.ohhapple.monster_conquest.effect.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单个竞技单位的技能状态和结算。所有状态只服务竞技实体，不写入主世界单位数据。
 */
public final class ArenaSkillRuntime {

    private static final ResourceLocation STRENGTH_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_skill_strength");
    private static final ResourceLocation HUSK_ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_skill_husk_attack");
    private static final ResourceLocation HUSK_ARMOR_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_skill_husk_armor");
    private static final ResourceLocation WARDEN_ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_skill_warden_attack");
    private static final ResourceLocation WARDEN_MOVEMENT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_skill_warden_movement");
    private static final ResourceLocation WOLF_ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_skill_wolf_attack");
    private static final ResourceLocation POLAR_MOVEMENT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_skill_polar_movement");
    private static final ResourceLocation GIANT_ARMADILLO_ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_skill_giant_armadillo_attack");
    private static final ResourceLocation GIANT_ARMADILLO_MOVEMENT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_skill_giant_armadillo_movement");
    private static final ResourceLocation EXTERNAL_ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_skill_external_attack");
    private static final ResourceLocation OVERTIME_ATTACK_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_overtime_attack");
    private static final ResourceLocation OVERTIME_ATTACK_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_overtime_attack_speed");
    private static final ResourceLocation OVERTIME_MOVEMENT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena_overtime_movement");
    private static final int MACE_JUMP_TICKS = 12;
    private static final int IRON_GOLEM_WINDUP_TICKS = 8;
    private static final int RAVAGER_WINDUP_TICKS = 10;
    private static final int WARDEN_WINDUP_TICKS = 8;
    private static final int EVOKER_WINDUP_TICKS = 10;
    private static final int VINDICATOR_WINDUP_TICKS = 8;
    private static final double NORMAL_LEAP_HEIGHT = 3.0;
    private static final double SLIME_LEAP_HEIGHT = 5.0;

    private final AbstractArmySoldier soldier;
    private int stunTicks;
    private boolean stunFalling;
    private double stunFallVelocity;
    private int stunLaunchTicks;
    private Vec3 stunLaunchVelocity = Vec3.ZERO;
    private int rapidFireTicks;
    private int strengthTicks;
    private int tauntTicks;
    private int receivedTauntTicks;
    @Nullable private UUID tauntSourceId;
    private int arcaneShots;
    private int nextAttackStunTicks;
    private double nextAttackMultiplier = 1.0;
    private double nextAttackExecutionThreshold;
    private double vindicatorWaveDamageRatio;
    private double nextAttackMaxHealthDamage;
    private boolean vindicatorHitPending;
    private boolean sniperExecutionPending;
    private long witherDrainUntil;
    private boolean witherDomination;
    private long nextWitherDrainTick;
    private final Set<UUID> drainTargets = new HashSet<>();
    private double huskMaxHealthBonus;
    private double huskAttackBonus;
    private double huskArmorBonus;
    private double huskMagicResistanceBonus;
    private int huskGrowthStacks;
    private int wardenKillStacks;
    private double wardenMaxHealthBonus;
    private double wardenAttackBonus;
    private double wardenMovementBonus;
    private double wolfAttackPercent;
    private final Set<UUID> killParticipants = new HashSet<>();
    private final Set<UUID> summonIds = new HashSet<>();
    private int evokerSummonCooldown = -1;
    private int goatChargeTicks;
    @Nullable private UUID goatChargeTargetId;
    private int hoglinChargeTicks;
    @Nullable private UUID hoglinChargeTargetId;
    private int pandaRollTicks;
    @Nullable private UUID pandaRollTargetId;
    @Nullable private UUID endermanLockedTargetId;
    private int endermanTeleportCountdown;
    @Nullable private UUID forcedTrueTargetId;
    private float forcedTrueAmount;
    private int forcedTrueExpiresAt;
    private int wardenSonicWavesRemaining;
    private int wardenSonicNextTick;
    @Nullable private UUID wardenSonicTargetId;
    // 竞技单位不吃原版受击间隔，连续接触仍需单独限制伤害频率。
    private final Map<UUID, Integer> hoglinNextDamageTicks =
            new HashMap<>();
    private boolean polarFormActive;
    private int polarFormTicks;
    private double polarHealthBonus;
    private double polarBaseMaxHealth;
    private double previousSkillAttackSpeedBonus;
    private double previousSkillLifesteal;
    private double previousEntityScale = 1.0;
    private int skillDamageDepth;
    private int silenceTicks;
    private int supportAttackSpeedTicks;
    private double supportAttackSpeedBonus;
    private int armadilloRollTicks;
    @Nullable private UUID armadilloTargetId;
    private boolean armadilloHit;
    private long nextArmadilloPushTick;
    private boolean snifferDominanceActive;
    private final Map<ResourceLocation, Double>
            snifferDominanceAttackSpeedSources = new HashMap<>();
    private boolean elderGuardianLaserActive;
    private int nextElderGuardianLaserTick;
    @Nullable private UUID elderGuardianLaserTargetId;
    private int nextTurtleRegenTick;
    private int nextRavagerBloodShieldTick;
    private long nextWitherCastTick;
    private int giantArmadilloStacks;
    private int nextGiantArmadilloAttackTick;
    @Nullable private UUID giantArmadilloTargetId;
    private boolean blazeBarrageActive;
    private int blazeBarrageRemainingToSpawn;
    private int blazeBarrageFormationIndex;
    private int nextBlazeBarrageSpawnTick;
    private int nextBlazeBarrageLaunchTick;
    private float blazeBarrageDamage;
    private final List<ArenaFireball> blazeBarrageVisuals =
            new ArrayList<>();
    private boolean drownedTridentBarrageActive;
    private int drownedTridentRemainingToSpawn;
    private int drownedTridentFormationIndex;
    private int nextDrownedTridentSpawnTick;
    private int nextDrownedTridentLaunchTick;
    private float drownedTridentDamage;
    private int drownedTridentStunTicks;
    private int drownedTridentDebuffTicks;
    private final List<ArenaTrident> drownedTridentVisuals =
            new ArrayList<>();
    private boolean creeperExplosionResolved;
    private double temporaryDodgeBonus;
    private int temporaryDodgeExpiresAt;
    private final Map<ResourceLocation, TimedAttackModifier>
            externalAttackDamageModifiers = new HashMap<>();
    private final Map<ResourceLocation, TimedAttackSpeedModifier>
            externalAttackSpeedModifiers = new HashMap<>();
    private final Map<ResourceLocation, TimedMovementSpeedModifier>
            externalMovementSpeedModifiers = new HashMap<>();
    private boolean overtimeActive;
    private int overtimeSeconds;
    private double overtimeBaseAttack;
    private double overtimeBaseAttackSpeed;

    private int jumpTicks;
    private int jumpTotalTicks = MACE_JUMP_TICKS;
    private double jumpArcHeight = NORMAL_LEAP_HEIGHT;
    @Nullable private UUID jumpTargetId;
    private int maceLeapsRemaining;
    private int nextMaceLeapTick;
    private Vec3 jumpStart = Vec3.ZERO;
    private Vec3 jumpEnd = Vec3.ZERO;
    @Nullable private ArenaSkillType windupSkill;
    private int windupTicks;
    @Nullable private UUID windupTargetId;

    public ArenaSkillRuntime(AbstractArmySoldier soldier) {
        this.soldier = soldier;
    }

    public void tick() {
        if (!(soldier.level() instanceof ServerLevel level)) return;
        if (!soldier.isAlive()) {
            discardBlazeBarrage();
            discardDrownedTridentBarrage();
            discardArenaSummons(level);
            return;
        }
        tickTimedEffects();
        tickBlazeBarrage(level);
        tickDrownedTridentBarrage(level);
        tickTurtleRegeneration();
        tickRavagerBloodShield();
        tickElderGuardianLaser(level);
        if (stunTicks == 0 && stunFalling) {
            tickStunFall();
            return;
        }
        if (stunTicks > 0) {
            stunTicks--;
            soldier.setNoAi(true);
            soldier.setArenaStatusFlag(ArenaStatusFlags.STUN, true);
            lockStunnedMovement();
            tickStunLaunch();
            if (stunLaunchTicks <= 0) {
                tickStunFallPhysics();
            }
            if (stunTicks == 0) {
                if (stunHasFooting()) {
                    finishStun();
                } else {
                    stunFalling = true;
                }
            }
            return;
        }
        if (windupSkill != null) {
            tickSkillWindup(level);
            return;
        }
        if (jumpTicks > 0) {
            tickJump(level);
            return;
        }
        if (maceLeapsRemaining > 0) {
            tickMaceLeapSequence(level);
            return;
        }
        if (armadilloRollTicks > 0) {
            tickArmadilloRoll(level);
            return;
        }
        if (goatChargeTicks > 0) {
            tickGoatCharge(level);
            return;
        }
        if (hoglinChargeTicks > 0) {
            tickHoglinCharge(level);
            return;
        }
        if (pandaRollTicks > 0) {
            tickPandaRoll(level);
            return;
        }
        if (soldier.getArenaSkillType()
                == ArenaSkillType.GIANT_ARMADILLO_MOMENTUM) {
            tickGiantArmadillo(level);
            return;
        }

        tickEndermanLock(level);
        tickWardenSonicBarrage(level);
        cleanupSummonRoster(level);
        tickEvokerSummons(level);
        tickTaunt();
        tickWitherDrain(level);
        prepareCreeperReadySpeed();

        if (soldier.getArenaSkillCharge()
                >= soldier.getArenaSkillRequired()
                && soldier.tickCount >= nextWitherCastTick
                && castSkill(level)) {
            soldier.resetArenaSkillCharge();
            if (soldier.getArenaSkillType()
                    == ArenaSkillType.SLIME_LEAP_SMASH
                    && soldier.getArenaStar() >= 3) {
                soldier.addArenaSkillChargeRaw(
                        soldier.getArenaSkillRequired());
            }
            if (soldier.getArenaSkillType()
                    == ArenaSkillType.WITHER_SKULL_BARRAGE
                    && soldier.getArenaStar() >= 3) {
                soldier.addArenaSkillChargeRaw(
                        soldier.getArenaSkillRequired());
                nextWitherCastTick = soldier.tickCount
                        + ArenaSkillBalance
                        .WITHER_REPEAT_INTERVAL_TICKS;
            }
            tickTaunt();
        }
    }

    public float modifyOutgoingAttack(LivingEntity victim,
                                      DamageSource source, float amount) {
        if (!isBasicAttack(source) || isDealingSkillDamage()
                || soldier.isDealingArenaEffectDamage()) {
            return amount;
        }
        double multiplier = nextAttackMultiplier;
        nextAttackMultiplier = 1.0;
        if (multiplier != 1.0) {
            vindicatorHitPending = nextAttackExecutionThreshold > 0.0;
            amount *= (float) multiplier;
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.EMPOWERED_ATTACK, false);
        }

        if (nextAttackMaxHealthDamage > 0.0) {
            amount += (float) (arenaMaxHealth(victim)
                    * nextAttackMaxHealthDamage);
            nextAttackMaxHealthDamage = 0.0;
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.SNIPER_READY, false);
        }
        if (soldier.getArenaSkillType() == ArenaSkillType.SNIPER_EXECUTE_SHOT
                && soldier.getArenaStar() >= 3) {
            sniperExecutionPending = true;
        }
        return amount;
    }

    public void afterDamageDealt(LivingEntity victim, DamageSource source) {
        if (!isBasicAttack(source) || isDealingSkillDamage()) return;
        int star = soldier.getArenaStar();

        if (polarFormActive
                && victim instanceof AbstractArmySoldier arenaVictim
                && !arenaVictim.isArenaControlImmune()) {
            arenaVictim.setTicksFrozen(Math.min(800,
                    arenaVictim.getTicksFrozen()
                            + ArenaSkillBalance.polarBearFrostTicks(star)));
        }

        if (isBasicAttack(source)
                && soldier.getArenaSkillType()
                == ArenaSkillType.CAVE_SPIDER_VENOM) {
            victim.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    ArenaSkillBalance.caveSpiderPassivePoisonTicks(star),
                    ArenaSkillBalance.CAVE_SPIDER_PASSIVE_POISON_AMPLIFIER,
                    false, true), soldier);
        }

        if (isBasicAttack(source)
                && soldier.getArenaSkillType()
                == ArenaSkillType.BLAZE_FIREBALL_BARRAGE) {
            int burnTicks = ArenaSkillBalance.blazeBurnTicks(star);
            if (victim instanceof AbstractArmySoldier arenaVictim) {
                if (!arenaVictim.isArenaDebuffImmune()) {
                    victim.setRemainingFireTicks(Math.max(
                            victim.getRemainingFireTicks(),
                            burnTicks));
                    arenaVictim.arenaEquipment().applyBurnFrom(
                            soldier, burnTicks, 0.01, 0.0, false);
                }
            } else {
                victim.setRemainingFireTicks(Math.max(
                        victim.getRemainingFireTicks(), burnTicks));
            }
        }

        if (nextAttackStunTicks > 0) {
            int ticks = nextAttackStunTicks;
            nextAttackStunTicks = 0;
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.DROWNED_READY, false);
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.STUN_READY, false);
            victim.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true));
            if (victim instanceof AbstractArmySoldier arenaVictim) {
                if (arenaVictim.isArenaControlImmune()) {
                    victim.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    victim.setTicksFrozen(0);
                } else {
                    arenaVictim.arenaSkills().applyStun(ticks);
                }
            }
            if (soldier.getArenaSkillType()
                    == ArenaSkillType.ZOMBIE_VILLAGER_STUN) {
                soldier.heal((float) (arenaMaxHealth(soldier)
                        * ArenaSkillBalance
                        .ZOMBIE_VILLAGER_HEAL_RATIO));
                if (soldier.level() instanceof ServerLevel level) {
                    particleBurst(level, soldier,
                            ParticleTypes.HEART, 8);
                }
            }
        }

        if (vindicatorHitPending) {
            vindicatorHitPending = false;
            double threshold = nextAttackExecutionThreshold;
            nextAttackExecutionThreshold = 0.0;
            if (soldier.level() instanceof ServerLevel level) {
                emitVindicatorSwordWave(
                        level, victim, threshold,
                        vindicatorWaveDamageRatio);
            }
            vindicatorWaveDamageRatio = 0.0;
            if (victim.isAlive()
                    && arenaCurrentHealth(victim)
                    <= arenaMaxHealth(victim) * threshold) {
                dealSkillDamage(victim, soldier.damageSources().mobAttack(soldier),
                        arenaCurrentHealth(victim) + arenaMaxHealth(victim));
            }
        }

        if (sniperExecutionPending) {
            sniperExecutionPending = false;
            if (victim.isAlive()) {
                dealSkillDamage(victim, soldier.damageSources().mobAttack(soldier),
                        arenaCurrentHealth(victim) + arenaMaxHealth(victim));
            }
        }

        if (arcaneShots > 0
                && source.getDirectEntity() instanceof Projectile) {
            arcaneShots--;
            if (arcaneShots == 0) {
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.ARCANE_SHOTS, false);
            }
            double bonus = soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE)
                    * ArenaSkillBalance.strayBonusAttackRatio(
                    star, soldier.getArenaMagicPower());
            dealSkillDamage(victim,
                    soldier.damageSources().indirectMagic(soldier, soldier),
                    (float) bonus);
        }

        if (soldier.getArenaSkillType() == ArenaSkillType.WITHER_DRAIN
                && (witherDomination || soldier.tickCount < witherDrainUntil)) {
            int targetLimit = ArenaSkillBalance.witherTargetLimit(star);
            if (drainTargets.contains(victim.getUUID())
                    || drainTargets.size() < targetLimit) {
                drainTargets.add(victim.getUUID());
            }
        }
    }

    public void afterDamageTaken(@Nullable AbstractArmySoldier attacker,
                                 DamageSource source,
                                 float takenDamage) {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.ARMADILLO_ROLL
                || armadilloRollTicks <= 0
                || attacker == null
                || attacker == soldier
                || takenDamage <= 0.0f
                || source.is(DamageTypes.THORNS)
                || attacker.isDealingArenaEffectDamage()
                || !attacker.isAlive()) {
            return;
        }
        float reflected = (float) (takenDamage
                * ArenaSkillBalance.ARMADILLO_REFLECT_RATIO);
        if (reflected <= 0.0f) return;
        soldier.beginArenaEffectDamage();
        try {
            attacker.hurt(soldier.damageSources().thorns(soldier),
                    reflected);
        } finally {
            soldier.endArenaEffectDamage();
        }
        if (soldier.level() instanceof ServerLevel level) {
            particleBurst(level, attacker,
                    ParticleTypes.CRIT, 8);
        }
    }

    /** 唤魔者尖牙命中后的竞技控制只作用于同场敌人。 */
    public void afterArenaFangHit(LivingEntity victim) {
        if (!(victim instanceof AbstractArmySoldier arenaVictim)
                || !soldier.isArenaEnemy(arenaVictim)
                || !arenaVictim.isAlive()
                || arenaVictim.isArenaControlImmune()) {
            return;
        }
        int stunTicks = ArenaSkillBalance.evokerFangStunTicks(
                soldier.getArenaStar());
        arenaVictim.arenaSkills().applyStun(stunTicks);
        arenaVictim.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SLOWDOWN,
                stunTicks,
                6, false, true));
    }

    public void applyStun(int ticks) {
        if (ticks <= 0) return;
        if (soldier.isArenaControlImmune()) return;
        ticks = modifyIncomingControlTicks(ticks);
        // 项目内控制语义是“失去 AI”：阻止 Goal 选择和攻击，并清空导航/惯性。
        stunTicks = mergeStunTicks(stunTicks, ticks);
        stunFalling = false;
        stunFallVelocity = Math.max(-1.2, Math.min(0.8,
                soldier.getDeltaMovement().y));
        soldier.setNoAi(true);
        soldier.setArenaStatusFlag(ArenaStatusFlags.STUN, true);
        lockStunnedMovement();
    }

    public static int mergeStunTicks(int currentTicks,
                                     int incomingTicks) {
        return Math.max(
                Math.max(0, currentTicks),
                Math.max(0, incomingTicks));
    }

    public void applyStunWithLaunch(int ticks, Vec3 launchVelocity) {
        if (soldier.isArenaControlImmune()) return;
        applyStun(ticks);
        if (soldier.isArenaKnockbackImmune()) return;
        stunLaunchTicks = Math.max(stunLaunchTicks, 8);
        stunLaunchVelocity = launchVelocity;
    }

    private void lockStunnedMovement() {
        soldier.setTarget(null);
        Mob mover = stunMover();
        AiNavUtil.haltMovement(mover);
        if (mover != soldier) {
            AiNavUtil.stopMovementInput(soldier);
        }
    }

    private void tickStunLaunch() {
        if (stunLaunchTicks <= 0
                || stunLaunchVelocity.lengthSqr() < 1.0E-6) {
            return;
        }
        Mob mover = stunMover();
        Vec3 velocity = stunLaunchVelocity;
        mover.move(MoverType.SELF, velocity);
        if (mover != soldier) {
            AiNavUtil.stopMovementInput(soldier);
        }
        double nextX = mover.horizontalCollision
                ? 0.0 : velocity.x * 0.82;
        double nextZ = mover.horizontalCollision
                ? 0.0 : velocity.z * 0.82;
        double nextY = mover.verticalCollision
                ? 0.0 : velocity.y - 0.06;
        stunLaunchVelocity = new Vec3(nextX, nextY, nextZ);
        stunLaunchTicks--;
        if (stunLaunchTicks > 0) {
            mover.setDeltaMovement(Vec3.ZERO);
        } else {
            mover.setDeltaMovement(Vec3.ZERO);
            stunFallVelocity = nextY;
            stunLaunchVelocity = Vec3.ZERO;
        }
    }

    private void tickStunFallPhysics() {
        Mob mover = stunMover();
        if (mover.isInWater() || mover.onClimbable()) {
            mover.setDeltaMovement(Vec3.ZERO);
            stunFallVelocity = 0.0;
            return;
        }
        stunFallVelocity = Math.max(-1.2,
                stunFallVelocity - 0.08);
        mover.move(MoverType.SELF, new Vec3(
                0.0, stunFallVelocity, 0.0));
        mover.setDeltaMovement(Vec3.ZERO);
        if (mover.verticalCollision || stunHasFooting()) {
            stunFallVelocity = 0.0;
        }
        if (mover != soldier) {
            AiNavUtil.stopMovementInput(soldier);
        }
    }

    private void tickStunFall() {
        soldier.setNoAi(true);
        soldier.setArenaStatusFlag(ArenaStatusFlags.STUN, true);
        lockStunnedMovement();
        tickStunFallPhysics();
        if (stunHasFooting()) {
            finishStun();
        }
    }

    private Mob stunMover() {
        return soldier.getVehicle() instanceof Mob mount
                ? mount : soldier;
    }

    private boolean stunHasFooting() {
        Mob mover = stunMover();
        if (mover.isInWater() || mover.onClimbable()) {
            return true;
        }
        AABB probe = mover.getBoundingBox().move(
                0.0, -0.08, 0.0).deflate(0.001);
        return !soldier.level().noCollision(mover, probe);
    }

    private void finishStun() {
        stunFalling = false;
        stunFallVelocity = 0.0;
        stunLaunchTicks = 0;
        stunLaunchVelocity = Vec3.ZERO;
        soldier.setNoAi(
                soldier.getArenaSkillType()
                        == ArenaSkillType.GIANT_ARMADILLO_MOMENTUM);
        soldier.setArenaStatusFlag(ArenaStatusFlags.STUN, false);
    }

    public void applyTaunt(LivingEntity source, int ticks) {
        if (ticks <= 0 || !(source instanceof AbstractArmySoldier)) return;
        if (soldier.isArenaControlImmune()) return;
        ticks = modifyIncomingControlTicks(ticks);
        tauntSourceId = source.getUUID();
        receivedTauntTicks = Math.max(receivedTauntTicks, ticks);
        soldier.setTarget(source);
    }

    @Nullable
    public LivingEntity arenaTauntTarget() {
        if (receivedTauntTicks <= 0 || tauntSourceId == null
                || !(soldier.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity entity = level.getEntity(tauntSourceId);
        return entity instanceof LivingEntity living && living.isAlive()
                ? living : null;
    }

    public boolean isDealingSkillDamage() {
        return skillDamageDepth > 0;
    }

    private boolean castSkill(ServerLevel level) {
        ArenaSkillType type = soldier.getArenaSkillType();
        if (type == null || silenceTicks > 0) return false;
        int star = soldier.getArenaStar();

        switch (type) {
            case ZOMBIE_SHIELD -> {
                double shield = arenaMaxHealth(soldier)
                        * ArenaSkillBalance.shieldRatio(
                        0.20, star, soldier.getArenaMagicPower());
                soldier.grantArenaShield(shield);
                particleBurst(level, soldier, ParticleTypes.SOUL, 12);
                return completeSkill();
            }
            case SKELETON_VOLLEY -> {
                List<AbstractArmySoldier> targets = nearestEnemies(
                        ArenaSkillBalance.SKELETON_TARGETS);
                if (targets.isEmpty()) return false;
                for (AbstractArmySoldier target : targets) {
                    soldier.tryPerformRangedAttack(target);
                }
                return completeSkill();
            }
            case STRAY_ARCANE_SHOTS -> {
                arcaneShots = ArenaSkillBalance.STRAY_SHOTS;
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.ARCANE_SHOTS, true);
                return completeSkill();
            }
            case DROWNED_TRIDENT_STUN -> {
                return beginDrownedTridentBarrage()
                        && completeSkill();
            }
            case ZOMBIE_VILLAGER_STUN -> {
                nextAttackStunTicks =
                        ArenaSkillBalance.zombieVillagerStunTicks(star);
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.STUN_READY, true);
                return completeSkill();
            }
            case HUSK_BATTLE_GROWTH -> {
                applyHuskGrowth(level);
                particleBurst(level, soldier,
                        ParticleTypes.CRIT, 18);
                return completeSkill();
            }
            case SNOW_GOLEM_SNOWBALL_FAN -> {
                if (!(soldier instanceof ArenaSnowGolem snowGolem)) {
                    return false;
                }
                return snowGolem.beginSkillSnowballs()
                        && completeSkill();
            }
            case SPIDER_SLOW_FIELD -> {
                return applySpiderSlow(level)
                        && completeSkill();
            }
            case BREEZE_GALE_CHARGE -> {
                return soldier instanceof ArenaBreeze breeze
                        && breeze.fireGaleCharge()
                        && completeSkill();
            }
            case ARCHER_STRENGTH -> {
                strengthTicks =
                        ArenaSkillBalance.standardBuffTicks(star);
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.STRENGTH, true);
                applyStrength(ArenaSkillBalance.archerStrengthBonus(
                        star, soldier.getArenaMagicPower()));
                return completeSkill();
            }
            case VINDICATOR_EXECUTE -> {
                return beginSkillWindup(level, type, nearestEnemy())
                        && completeSkill();
            }
            case MACE_LEAP_SMASH -> {
                maceLeapsRemaining =
                        ArenaSkillBalance.MACE_LEAP_COUNT;
                if (!continueMaceLeapSequence(level)) {
                    maceLeapsRemaining = 0;
                    return false;
                }
                return completeSkill();
            }
            case WOLF_CALL -> {
                if (!summonArenaWolf(level)) return false;
                wolfAttackPercent +=
                        ArenaSkillBalance.WOLF_ATTACK_BONUS_PER_CAST;
                setAttributeModifier(Attributes.ATTACK_DAMAGE,
                        WOLF_ATTACK_MODIFIER, wolfAttackPercent,
                        AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                soldier.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        ArenaSkillBalance.wolfSpeedTicks(star),
                        1, false, true));
                particleBurst(level, soldier,
                        ParticleTypes.HAPPY_VILLAGER, 12);
                return completeSkill();
            }
            case GOAT_CHARGE -> {
                return beginGoatCharge() && completeSkill();
            }
            case RAVAGER_KNOCKUP_HEAL -> {
                return beginSkillWindup(level, type,
                        nearestEnemyWithinRadius(
                                ArenaSkillBalance
                                        .RAVAGER_KNOCKUP_RADIUS))
                        && completeSkill();
            }
            case POLAR_BEAR_FORM -> {
                beginPolarForm(level);
                return completeSkill();
            }
            case HOGLIN_CHARGE -> {
                return beginHoglinCharge() && completeSkill();
            }
            case BOGGED_POISON_VOLLEY -> {
                List<AbstractArmySoldier> targets =
                        nearestEnemies(Integer.MAX_VALUE);
                if (targets.isEmpty()) return false;
                int poisonTicks =
                        ArenaSkillBalance.boggedPoisonTicks(star);
                double attack = soldier.getAttributeValue(
                        Attributes.ATTACK_DAMAGE)
                        * ArenaSkillBalance.boggedAttackMultiplier(
                        star, soldier.getArenaMagicPower());
                boolean fired = false;
                for (AbstractArmySoldier target : targets) {
                    fired |= soldier.tryPerformArenaSkillArrow(
                            target, attack, poisonTicks,
                            ArenaSkillBalance.BOGGED_POISON_AMPLIFIER);
                    particleBurst(level, target,
                            ParticleTypes.ITEM_SLIME, 8);
                }
                return fired && completeSkill();
            }
            case RIFLE_RAPID_FIRE -> {
                rapidFireTicks =
                        ArenaSkillBalance.standardBuffTicks(star);
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.RAPID_FIRE, true);
                soldier.setArenaSkillAttackSpeedBonus(
                        ArenaSkillBalance.rifleAttackSpeedMultiplier(
                                star, soldier.getArenaMagicPower()) - 1.0);
                return completeSkill();
            }
            case PIGLIN_TAUNT_SHIELD -> {
                double shield = arenaMaxHealth(soldier)
                        * ArenaSkillBalance.shieldRatio(
                        0.40, star, soldier.getArenaMagicPower());
                soldier.grantArenaShield(shield);
                int duration =
                        ArenaSkillBalance.standardBuffTicks(star);
                tauntTicks = duration;
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.TAUNT, true);
                soldier.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE,
                        duration, star - 1, false, true));
                particleBurst(level, soldier, ParticleTypes.ENCHANT, 20);
                return completeSkill();
            }
            case WITHER_DRAIN -> {
                witherDomination = star
                        >= ArenaSkillBalance.WITHER_DOMINATION_STAR;
                witherDrainUntil = witherDomination
                        ? Long.MAX_VALUE
                        : soldier.tickCount
                        + ArenaSkillBalance.witherMarkTicks(star);
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.WITHER_DRAIN, true);
                drainTargets.clear();
                int targetLimit =
                        ArenaSkillBalance.witherTargetLimit(star);
                for (AbstractArmySoldier enemy :
                        nearestEnemies(targetLimit)) {
                    drainTargets.add(enemy.getUUID());
                    particleBurst(level, enemy, ParticleTypes.SOUL, 12);
                }
                emitGroundRing(level, soldier.position(),
                        1.65, ParticleTypes.SWEEP_ATTACK, 8);
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                        soldier.getX(),
                        soldier.getY() + soldier.getBbHeight() * 0.58,
                        soldier.getZ(), 20,
                        0.55, 0.75, 0.55, 0.035);
                return completeSkill();
            }
            case SNIPER_EXECUTE_SHOT -> {
                nextAttackMaxHealthDamage =
                        ArenaSkillBalance.sniperMaxHealthRatio(
                                star, soldier.getArenaMagicPower());
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.SNIPER_READY, true);
                return completeSkill();
            }
            case EVOKER_FANG_STUN -> {
                return beginSkillWindup(level, type, nearestEnemy())
                        && completeSkill();
            }
            case WITCH_LINGERING_POTION -> {
                return throwWitchLingeringPotion(level)
                        && completeSkill();
            }
            case IRON_GOLEM_LEAP_SMASH -> {
                return beginSkillWindup(level, type, nearestEnemy())
                        && completeSkill();
            }
            case CAVE_SPIDER_VENOM -> {
                return caveSpiderVenomBurst(level)
                        && completeSkill();
            }
            case ARMADILLO_ROLL -> {
                return beginArmadilloRoll()
                        && completeSkill();
            }
            case BLAZE_FIREBALL_BARRAGE -> {
                return beginBlazeBarrage(level)
                        && completeSkill();
            }
            case SNIFFER_DIG -> {
                snifferDig(level);
                applySnifferShield(level, star);
                return completeSkill();
            }
            case ENDERMITE_SILENCE_STRIKE -> {
                return endermiteSilenceStrike(level)
                        && completeSkill();
            }
            case SLIME_LEAP_SMASH -> {
                return beginMaceJump(level) && completeSkill();
            }
            case ELDER_GUARDIAN_LASER -> {
                if (elderGuardianLaserActive) return false;
                AbstractArmySoldier target = farthestEnemy();
                if (target == null) return false;
                elderGuardianLaserActive = true;
                elderGuardianLaserTargetId = target.getUUID();
                soldier.setTarget(target);
                nextElderGuardianLaserTick =
                        soldier.tickCount + ArenaSkillBalance
                                .ELDER_GUARDIAN_DAMAGE_INTERVAL_TICKS;
                return completeSkill();
            }
            case RABBIT_LEAP -> {
                return beginMaceJump(level) && completeSkill();
            }
            case DONKEY_GIFT -> {
                AbstractArmySoldier ally =
                        nearestAlly(true);
                if (ally == null) return false;
                ally.addArenaSkillChargeRaw(
                        ArenaSkillBalance
                                .DONKEY_SKILL_CHARGE_GRANT);
                particleBurst(level, ally,
                        ParticleTypes.HAPPY_VILLAGER, 16);
                return completeSkill();
            }
            case SHEEP_HEAL -> {
                soldier.heal((float) ArenaSkillBalance
                        .sheepHeal(arenaMaxHealth(soldier),
                                star,
                                soldier.getArenaMagicPower()));
                particleBurst(level, soldier,
                        ParticleTypes.HEART, 10);
                return completeSkill();
            }
            case COW_CLEANSE -> {
                AbstractArmySoldier ally =
                        nearestAlly(true);
                if (ally == null) return false;
                ally.arenaSkills().cleanseArenaDebuffs();
                double shield = arenaMaxHealth(ally)
                        * ArenaSkillBalance.COW_SHIELD_RATIO
                        * ArenaSkillBalance.abilityScale(
                        star, soldier.getArenaMagicPower());
                ally.grantArenaShieldFrom(soldier, shield);
                particleBurst(level, ally,
                        ParticleTypes.ENCHANT, 20);
                return completeSkill();
            }
            case PIG_TRANSFORM -> {
                if (!(soldier instanceof ArenaPig pig)) {
                    return false;
                }
                pig.setZombified(true);
                double scale = ArenaSkillBalance.abilityScale(
                        star, soldier.getArenaMagicPower());
                soldier.heal((float) (ArenaSkillBalance.PIG_HEAL
                        * scale));
                soldier.grantArenaShield(
                        arenaMaxHealth(soldier)
                                * ArenaSkillBalance.PIG_SHIELD_RATIO
                                * scale);
                particleBurst(level, soldier,
                        ParticleTypes.WITCH, 24);
                level.playSound(null, soldier.getX(),
                        soldier.getY(), soldier.getZ(),
                        SoundEvents.ZOMBIFIED_PIGLIN_ANGRY,
                        SoundSource.HOSTILE, 1.2f, 0.8f);
                return completeSkill();
            }
            case ZOMBIFIED_PIGLIN_FRENZY -> {
                int duration = ArenaSkillBalance
                        .scaledDurationTicks(200, star);
                double bonus = ArenaSkillBalance
                        .zombifiedPiglinAttackSpeedBonus(
                                star, soldier.getArenaMagicPower());
                soldier.arenaSkills().addAttackSpeedModifier(
                        skillSourceId("piglin_frenzy"),
                        soldier.getArenaBaseAttackSpeed()
                                * bonus,
                        duration, false);
                particleBurst(level, soldier,
                        ParticleTypes.ANGRY_VILLAGER, 18);
                return completeSkill();
            }
            case FROG_PULL -> {
                return frogPullFarthest(level)
                        && completeSkill();
            }
            case FOX_STEAL -> {
                return foxStealTarget(level)
                        && completeSkill();
            }
            case CREEPER_EXPLODE -> {
                if (!creeperHasContactTarget()) return false;
                creeperExplode(level);
                return completeSkill();
            }
            case PANDA_ROLL -> {
                return beginPandaRoll() && completeSkill();
            }
            case ENDERMAN_LOCK -> {
                return endermanLockFarthest(level)
                        && completeSkill();
            }
            case WARDEN_SONIC_BOOM -> {
                return beginSkillWindup(level, type, nearestEnemy())
                        && completeSkill();
            }
            case WANDERING_TRADER_SUPPLY -> {
                return wanderingTraderSupply(level)
                        && completeSkill();
            }
            case MAGMA_CUBE_BLESSING -> {
                return magmaCubeBlessing(level)
                        && completeSkill();
            }
            case TURTLE_RESILIENCE -> {
                int duration = ArenaSkillBalance
                        .turtleResistanceTicks(star);
                soldier.addEffect(new MobEffectInstance(
                        MobEffects.DAMAGE_RESISTANCE, duration,
                        ArenaSkillBalance
                                .TURTLE_RESISTANCE_AMPLIFIER,
                        false, true), soldier);
                particleBurst(level, soldier,
                        ParticleTypes.TOTEM_OF_UNDYING, 18);
                return completeSkill();
            }
            case SHOTGUN_BARRAGE -> {
                double attackSpeedBonus = ArenaSkillBalance
                        .shotgunAttackSpeedBonus(
                                star, soldier.getArenaMagicPower());
                rapidFireTicks =
                        ArenaSkillBalance.SHOTGUN_BARRAGE_TICKS;
                soldier.setArenaSkillAttackSpeedBonus(
                        attackSpeedBonus);
                soldier.setArenaSkillLifesteal(
                        ArenaSkillBalance.shotgunLifesteal(
                                star, soldier.getArenaMagicPower()));
                soldier.setArenaStatusFlag(
                        ArenaStatusFlags.RAPID_FIRE, true);
                particleBurst(level, soldier,
                        ParticleTypes.ANGRY_VILLAGER, 20);
                level.playSound(null, soldier.getX(),
                        soldier.getY(), soldier.getZ(),
                        ModSounds.SHOTGUN_SHOOT.get(),
                        SoundSource.HOSTILE, 1.2f, 0.85f);
                return completeSkill();
            }
            case WITHER_SKULL_BARRAGE -> {
                return witherSkullBarrage(level)
                        && completeSkill();
            }
            case SKELETON_HORSE_CLEANSE -> {
                soldier.arenaSkills().cleanseArenaDebuffs();
                soldier.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED,
                        ArenaSkillBalance.SKELETON_HORSE_SPEED_TICKS,
                        ArenaSkillBalance
                                .SKELETON_HORSE_SPEED_AMPLIFIER,
                        false, true), soldier);
                double shield = arenaMaxHealth(soldier)
                        * ArenaSkillBalance
                        .skeletonHorseShieldRatio(
                                star, soldier.getArenaMagicPower());
                soldier.grantArenaShield(shield);
                particleBurst(level, soldier,
                        ParticleTypes.SOUL, 18);
                return completeSkill();
            }
            case GIANT_ARMADILLO_MOMENTUM -> {
                applyGiantArmadilloMomentum(level);
                return completeSkill();
            }
            case ENDER_DRAGON_BOARD_SLAM -> {
                return beginEnderDragonSlam(level)
                        && completeSkill();
            }
        }
        return false;
    }

    private boolean completeSkill() {
        soldier.arenaEquipment().onSkillCast();
        return true;
    }

    @Nullable
    private AbstractArmySoldier nearestEnemyWithinRadius(
            double radius) {
        AbstractArmySoldier nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (AbstractArmySoldier enemy : nearestEnemies(
                Integer.MAX_VALUE)) {
            double distance = MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy);
            if (distance > radius
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0
                    || distance >= nearestDistance) {
                continue;
            }
            nearest = enemy;
            nearestDistance = distance;
        }
        return nearest;
    }

    private boolean beginSkillWindup(
            ServerLevel level, ArenaSkillType skill,
            @Nullable AbstractArmySoldier target) {
        if (target == null || windupSkill != null
                || jumpTicks > 0
                || wardenSonicWavesRemaining > 0) {
            return false;
        }
        windupSkill = skill;
        windupTicks = switch (skill) {
            case IRON_GOLEM_LEAP_SMASH ->
                    IRON_GOLEM_WINDUP_TICKS;
            case RAVAGER_KNOCKUP_HEAL -> RAVAGER_WINDUP_TICKS;
            case WARDEN_SONIC_BOOM -> WARDEN_WINDUP_TICKS;
            case EVOKER_FANG_STUN -> EVOKER_WINDUP_TICKS;
            case VINDICATOR_EXECUTE -> VINDICATOR_WINDUP_TICKS;
            default -> 0;
        };
        if (windupTicks <= 0) {
            windupSkill = null;
            return false;
        }
        windupTargetId = target.getUUID();
        soldier.setNoAi(true);
        soldier.setTarget(target);
        soldier.getNavigation().stop();
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
        soldier.playMeleeAttackAnimation();
        if (skill == ArenaSkillType.WARDEN_SONIC_BOOM) {
            level.broadcastEntityEvent(soldier, (byte) 62);
        }
        SoundEvent beginSound = switch (skill) {
            case IRON_GOLEM_LEAP_SMASH ->
                    SoundEvents.IRON_GOLEM_ATTACK;
            case RAVAGER_KNOCKUP_HEAL ->
                    SoundEvents.RAVAGER_ATTACK;
            case EVOKER_FANG_STUN ->
                    SoundEvents.EVOKER_PREPARE_ATTACK;
            case VINDICATOR_EXECUTE ->
                    SoundEvents.VINDICATOR_AMBIENT;
            default -> SoundEvents.WARDEN_SONIC_CHARGE;
        };
        level.playSound(null,
                soldier.getX(), soldier.getY(), soldier.getZ(),
                beginSound, SoundSource.HOSTILE,
                skill == ArenaSkillType.WARDEN_SONIC_BOOM
                        ? 3.0f : 1.25f,
                skill == ArenaSkillType.RAVAGER_KNOCKUP_HEAL
                        ? 0.72f : 0.9f);
        emitSkillWindupPulse(level, skill);
        return true;
    }

    private void tickSkillWindup(ServerLevel level) {
        ArenaSkillType skill = windupSkill;
        if (skill == null) return;
        AbstractArmySoldier target = resolveWindupTarget(level);
        if (target != null) {
            soldier.setTarget(target);
            soldier.getLookControl().setLookAt(
                    target, 40.0f, 40.0f);
        }
        if ((windupTicks & 1) == 0) {
            emitSkillWindupPulse(level, skill);
        }
        if (--windupTicks > 0) return;

        windupSkill = null;
        windupTargetId = null;
        soldier.setNoAi(false);
        emitSkillReleasePulse(level, skill);
        switch (skill) {
            case IRON_GOLEM_LEAP_SMASH ->
                    beginMaceJump(level, target);
            case RAVAGER_KNOCKUP_HEAL ->
                    ravagerKnockupAndHeal(level);
            case EVOKER_FANG_STUN ->
                    castEvokerFangBarrage(level);
            case VINDICATOR_EXECUTE ->
                    prepareVindicatorSlash(level);
            case WARDEN_SONIC_BOOM ->
                    startWardenSonicBarrage(level, target);
            default -> {
            }
        }
    }

    @Nullable
    private AbstractArmySoldier resolveWindupTarget(
            ServerLevel level) {
        if (windupTargetId != null
                && level.getEntity(windupTargetId)
                instanceof AbstractArmySoldier target
                && target.isAlive()
                && soldier.isArenaEnemy(target)) {
            return target;
        }
        AbstractArmySoldier target = nearestEnemy();
        windupTargetId = target == null ? null : target.getUUID();
        return target;
    }

    private void emitSkillWindupPulse(
            ServerLevel level, ArenaSkillType skill) {
        double radius = switch (skill) {
            case IRON_GOLEM_LEAP_SMASH -> 2.1;
            case RAVAGER_KNOCKUP_HEAL -> 2.8;
            case EVOKER_FANG_STUN -> 2.4;
            case VINDICATOR_EXECUTE -> 2.0;
            default -> 1.7;
        };
        int points = skill == ArenaSkillType.RAVAGER_KNOCKUP_HEAL
                ? 18 : 14;
        double rotation = soldier.tickCount * 0.16;
        BlockState ground = level.getBlockState(
                soldier.getOnPos().below());
        for (int index = 0; index < points; index++) {
            double angle = rotation
                    + index * Math.PI * 2.0 / points;
            double x = soldier.getX() + Math.cos(angle) * radius;
            double z = soldier.getZ() + Math.sin(angle) * radius;
            if (skill == ArenaSkillType.RAVAGER_KNOCKUP_HEAL) {
                level.sendParticles(new BlockParticleOption(
                                ParticleTypes.BLOCK, ground),
                        x, soldier.getY() + 0.12, z,
                        2, 0.08, 0.05, 0.08, 0.05);
            } else {
                level.sendParticles(
                        switch (skill) {
                            case WARDEN_SONIC_BOOM ->
                                    ParticleTypes.SCULK_SOUL;
                            case EVOKER_FANG_STUN ->
                                    ParticleTypes.WITCH;
                            case VINDICATOR_EXECUTE ->
                                    ParticleTypes.SWEEP_ATTACK;
                            default -> ParticleTypes.CRIT;
                        },
                        x, soldier.getY() + 0.18, z,
                        2, 0.04, 0.10, 0.04, 0.015);
            }
        }
        level.sendParticles(
                switch (skill) {
                    case WARDEN_SONIC_BOOM ->
                            ParticleTypes.SONIC_BOOM;
                    case EVOKER_FANG_STUN -> ParticleTypes.WITCH;
                    case VINDICATOR_EXECUTE ->
                            ParticleTypes.ENCHANTED_HIT;
                    default -> ParticleTypes.ENCHANT;
                },
                soldier.getX(),
                soldier.getY() + soldier.getBbHeight() * 0.55,
                soldier.getZ(), 3,
                0.25, 0.35, 0.25, 0.02);
    }

    private void emitSkillReleasePulse(
            ServerLevel level, ArenaSkillType skill) {
        soldier.playMeleeAttackAnimation();
        if (skill == ArenaSkillType.WARDEN_SONIC_BOOM) {
            level.sendParticles(ParticleTypes.SONIC_BOOM,
                    soldier.getX(),
                    soldier.getY()
                            + soldier.getBbHeight() * 0.55,
                    soldier.getZ(),
                    1, 0.0, 0.0, 0.0, 0.0);
            return;
        }
        if (skill == ArenaSkillType.EVOKER_FANG_STUN) {
            level.sendParticles(ParticleTypes.WITCH,
                    soldier.getX(), soldier.getY() + 0.3,
                    soldier.getZ(), 28,
                    2.4, 0.4, 2.4, 0.06);
            return;
        }
        if (skill == ArenaSkillType.VINDICATOR_EXECUTE) {
            Vec3 look = soldier.getLookAngle();
            for (int index = 0; index < 7; index++) {
                Vec3 point = soldier.position()
                        .add(look.scale(0.8 + index * 0.55))
                        .add(0.0, soldier.getBbHeight() * 0.55, 0.0);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                        point.x, point.y, point.z,
                        1, 0.12, 0.12, 0.12, 0.0);
            }
            level.playSound(null,
                    soldier.getX(), soldier.getY(), soldier.getZ(),
                    SoundEvents.PLAYER_ATTACK_SWEEP,
                    SoundSource.HOSTILE, 1.2f, 0.72f);
            return;
        }
        level.sendParticles(ParticleTypes.CLOUD,
                soldier.getX(), soldier.getY() + 0.18,
                soldier.getZ(), 18,
                skill == ArenaSkillType.RAVAGER_KNOCKUP_HEAL
                        ? 2.2 : 1.4,
                0.12,
                skill == ArenaSkillType.RAVAGER_KNOCKUP_HEAL
                        ? 2.2 : 1.4,
                0.08);
    }

    private boolean castEvokerFangBarrage(ServerLevel level) {
        if (!(soldier instanceof EvokerSoldier evoker)) {
            return false;
        }
        boolean spawned = false;
        for (AbstractArmySoldier target :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (!evoker.castArenaFangAtTarget(target)) continue;
            spawned = true;
            level.sendParticles(ParticleTypes.WITCH,
                    target.getX(), target.getY() + 0.25,
                    target.getZ(), 12,
                    0.65, 0.15, 0.65, 0.03);
            level.sendParticles(ParticleTypes.ENCHANT,
                    target.getX(), target.getY() + 0.8,
                    target.getZ(), 8,
                    0.45, 0.45, 0.45, 0.02);
        }
        return spawned;
    }

    private void prepareVindicatorSlash(ServerLevel level) {
        int star = soldier.getArenaStar();
        double magicPower = soldier.getArenaMagicPower();
        nextAttackMultiplier =
                ArenaSkillBalance.vindicatorAttackMultiplier(
                        star, magicPower);
        nextAttackExecutionThreshold =
                ArenaSkillBalance.vindicatorExecuteThreshold(
                        star, magicPower);
        vindicatorWaveDamageRatio = Math.max(
                0.55, nextAttackMultiplier - 1.0);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.EMPOWERED_ATTACK, true);
        soldier.playMeleeAttackAnimation();
        level.playSound(null,
                soldier.getX(), soldier.getY(), soldier.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.0f, 1.15f);
    }

    private void emitVindicatorSwordWave(
            ServerLevel level, LivingEntity primary,
            double executeThreshold, double damageRatio) {
        Vec3 start = soldier.position().add(
                0.0, soldier.getBbHeight() * 0.52, 0.0);
        Vec3 toPrimary = primary.getBoundingBox().getCenter()
                .subtract(start);
        Vec3 forward = new Vec3(
                toPrimary.x, 0.0, toPrimary.z);
        if (forward.lengthSqr() < 1.0E-6) return;
        forward = forward.normalize();
        double length = Math.min(12.0,
                Math.max(6.0,
                        Math.sqrt(toPrimary.x * toPrimary.x
                                + toPrimary.z * toPrimary.z) + 3.0));
        Vec3 end = start.add(forward.scale(length));
        for (int index = 1; index <= 10; index++) {
            Vec3 point = start.add(forward.scale(
                    length * index / 10.0));
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    point.x, point.y, point.z,
                    1, 0.18, 0.10, 0.18, 0.0);
            if ((index & 1) == 0) {
                level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                        point.x, point.y, point.z,
                        2, 0.20, 0.15, 0.20, 0.01);
            }
        }

        float waveDamage = (float) (
                soldier.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        * damageRatio);
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (enemy == primary || !enemy.isAlive()
                    || Math.abs(enemy.getY() - start.y) > 2.0) {
                continue;
            }
            Vec3 center = enemy.getBoundingBox().getCenter();
            Vec3 offset = center.subtract(start);
            double forwardDistance =
                    offset.x * forward.x + offset.z * forward.z;
            if (forwardDistance <= 0.0
                    || forwardDistance > length) {
                continue;
            }
            double lateralSquared =
                    offset.x * offset.x + offset.z * offset.z
                            - forwardDistance * forwardDistance;
            if (lateralSquared > 1.75 * 1.75) continue;
            dealSkillDamage(enemy,
                    soldier.damageSources().mobAttack(soldier),
                    waveDamage);
            particleBurst(level, enemy,
                    ParticleTypes.SWEEP_ATTACK, 8);
            if (enemy.isAlive()
                    && arenaCurrentHealth(enemy)
                    <= arenaMaxHealth(enemy) * executeThreshold) {
                dealSkillDamage(enemy,
                        soldier.damageSources().mobAttack(soldier),
                        arenaCurrentHealth(enemy)
                                + arenaMaxHealth(enemy));
            }
        }
        level.playSound(null,
                primary.getX(), primary.getY(), primary.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.HOSTILE, 1.2f, 0.78f);
    }

    private boolean beginMaceJump(ServerLevel level) {
        return beginMaceJump(level, nearestEnemy());
    }

    private boolean beginMaceJump(
            ServerLevel level, @Nullable AbstractArmySoldier target) {
        if (target == null) return false;
        jumpTargetId = target.getUUID();
        jumpStart = soldier.position();
        jumpEnd = target.position();
        jumpArcHeight = soldier.getArenaSkillType()
                == ArenaSkillType.SLIME_LEAP_SMASH
                ? SLIME_LEAP_HEIGHT : NORMAL_LEAP_HEIGHT;
        jumpTotalTicks = MACE_JUMP_TICKS;
        jumpTicks = MACE_JUMP_TICKS;
        soldier.setNoAi(true);
        soldier.setTarget(target);
        soldier.getNavigation().stop();
        return true;
    }

    private void tickJump(ServerLevel level) {
        int elapsed = jumpTotalTicks - jumpTicks;
        double progress = Math.min(1.0,
                (elapsed + 1.0) / jumpTotalTicks);
        double x = jumpStart.x + (jumpEnd.x - jumpStart.x) * progress;
        double z = jumpStart.z + (jumpEnd.z - jumpStart.z) * progress;
        double y = jumpStart.y + (jumpEnd.y - jumpStart.y) * progress
                + Math.sin(progress * Math.PI) * jumpArcHeight;
        soldier.teleportWithMount(x, y, z, false);
        if (soldier.getArenaSkillType()
                == ArenaSkillType.ENDER_DRAGON_BOARD_SLAM) {
            emitEnderDragonJumpParticles(level, progress);
        }
        jumpTicks--;
        if (jumpTicks > 0) return;

        soldier.setNoAi(false);
        Entity jumpEntity = jumpTargetId == null
                ? null : level.getEntity(jumpTargetId);
        LivingEntity target = jumpEntity instanceof LivingEntity living
                ? living : null;
        jumpTargetId = null;
        if (soldier.getArenaSkillType()
                == ArenaSkillType.IRON_GOLEM_LEAP_SMASH) {
            ironGolemLeapImpact(level);
            return;
        }
        if (soldier.getArenaSkillType()
                == ArenaSkillType.SLIME_LEAP_SMASH) {
            slimeLeapImpact(level);
            return;
        }
        if (soldier.getArenaSkillType()
                == ArenaSkillType.RABBIT_LEAP) {
            rabbitLeapImpact(level, target);
            return;
        }
        if (soldier.getArenaSkillType()
                == ArenaSkillType.ENDER_DRAGON_BOARD_SLAM) {
            enderDragonSlamImpact(level);
            return;
        }
        if (target != null && target.isAlive()) {
            float damage = (float) (soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE)
                    * ArenaSkillBalance.maceAttackMultiplier(
                    soldier.getArenaStar(),
                    soldier.getArenaMagicPower()));
            dealSkillDamage(target,
                    soldier.damageSources().mobAttack(soldier), damage);
            particleBurst(level, target, ParticleTypes.CRIT, 18);
            if (!target.isAlive()) {
                soldier.addArenaSkillCharge(
                        soldier.getArenaSkillRequired());
            }
        }
        if (soldier.getArenaSkillType()
                == ArenaSkillType.MACE_LEAP_SMASH) {
            nextMaceLeapTick = soldier.tickCount
                    + ArenaSkillBalance.MACE_LEAP_INTERVAL_TICKS;
        }
    }

    private boolean continueMaceLeapSequence(ServerLevel level) {
        if (maceLeapsRemaining <= 0) return false;
        AbstractArmySoldier target = nearestEnemy();
        if (target == null || !beginMaceJump(level, target)) {
            maceLeapsRemaining = 0;
            return false;
        }
        maceLeapsRemaining--;
        return true;
    }

    private void tickMaceLeapSequence(ServerLevel level) {
        if (soldier.tickCount < nextMaceLeapTick) return;
        if (!continueMaceLeapSequence(level)) {
            maceLeapsRemaining = 0;
        }
    }

    private boolean beginEnderDragonSlam(ServerLevel level) {
        if (nearestEnemy() == null) return false;
        jumpTargetId = null;
        jumpStart = soldier.position();
        jumpEnd = jumpStart;
        jumpArcHeight = ArenaSkillBalance
                .ENDER_DRAGON_SLAM_JUMP_HEIGHT;
        jumpTotalTicks =
                ArenaSkillBalance.ENDER_DRAGON_SLAM_JUMP_TICKS;
        jumpTicks = jumpTotalTicks;
        soldier.setNoAi(true);
        soldier.getNavigation().stop();
        soldier.setDeltaMovement(Vec3.ZERO);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 1.8f, 0.72f);
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                soldier.getX(),
                soldier.getY() + soldier.getBbHeight() * 0.6,
                soldier.getZ(), 36,
                1.2, 0.8, 1.2, 0.05);
        return true;
    }

    private void emitEnderDragonJumpParticles(
            ServerLevel level, double progress) {
        double angle = soldier.tickCount * 0.42;
        double radius = 1.4 + Math.sin(progress * Math.PI) * 1.5;
        for (int index = 0; index < 4; index++) {
            double current = angle + index * Math.PI * 0.5;
            double x = soldier.getX() + Math.cos(current) * radius;
            double z = soldier.getZ() + Math.sin(current) * radius;
            level.sendParticles(ParticleTypes.END_ROD,
                    x, soldier.getY() + soldier.getBbHeight() * 0.55,
                    z, 2, 0.05, 0.12, 0.05, 0.018);
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    x, soldier.getY() + soldier.getBbHeight() * 0.45,
                    z, 1, 0.08, 0.08, 0.08, 0.01);
        }
        if ((soldier.tickCount & 1) == 0) {
            level.sendParticles(ParticleTypes.PORTAL,
                    soldier.getX(),
                    soldier.getY() + soldier.getBbHeight() * 0.6,
                    soldier.getZ(), 8,
                    0.65, 0.45, 0.65, 0.04);
        }
    }

    private void enderDragonSlamImpact(ServerLevel level) {
        int star = soldier.getArenaStar();
        double magicScale = 1.0
                + Math.max(0.0, soldier.getArenaMagicPower()) / 100.0;
        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.enderDragonSlamMultiplier(star)
                * magicScale);
        List<AbstractArmySoldier> enemies =
                nearestEnemies(Integer.MAX_VALUE);
        DamageSource source = soldier.damageSources()
                .indirectMagic(soldier, soldier);
        for (AbstractArmySoldier enemy : enemies) {
            dealSkillDamage(enemy, source, damage);
            particleBurst(level, enemy,
                    ParticleTypes.DRAGON_BREATH, 18);
        }

        BlockPos impact = soldier.getOnPos();
        level.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK,
                impact, 600);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                soldier.getX(), soldier.getY() + 0.4, soldier.getZ(),
                2, 1.0, 0.2, 1.0, 0.0);
        level.sendParticles(ParticleTypes.FLASH,
                soldier.getX(), soldier.getY() + 1.1, soldier.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.SONIC_BOOM,
                soldier.getX(), soldier.getY() + 0.8, soldier.getZ(),
                1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                soldier.getX(), soldier.getY() + 1.0, soldier.getZ(),
                64, 5.5, 0.8, 5.5, 0.12);
        for (int index = 0; index < 12; index++) {
            double angle = index * Math.PI * 2.0 / 12.0
                    + soldier.tickCount * 0.05;
            double radius = 2.5 + (index % 3) * 2.0;
            double x = soldier.getX() + Math.cos(angle) * radius;
            double z = soldier.getZ() + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD,
                    x, soldier.getY() + 6.5, z,
                    16, 0.10, 3.2, 0.10, 0.10);
        }
        emitGroundRing(level, soldier.position(), 2.5,
                ParticleTypes.DRAGON_BREATH, 18);
        emitGroundRing(level, soldier.position(), 6.0,
                ParticleTypes.END_ROD, 26);
        emitGroundRing(level, soldier.position(), 12.0,
                ParticleTypes.END_ROD, 38);
        emitGroundRing(level, soldier.position(), 20.0,
                ParticleTypes.DRAGON_BREATH, 54);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.ENDER_DRAGON_GROWL,
                SoundSource.HOSTILE, 2.4f, 0.62f);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 1.8f, 0.76f);
    }

    private void ironGolemLeapImpact(ServerLevel level) {
        int star = soldier.getArenaStar();
        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.ironGolemAttackMultiplier(
                star, soldier.getArenaMagicPower()));
        int stunTicks = ArenaSkillBalance.ironGolemStunTicks(star);
        BlockPos impact = soldier.getOnPos();
        BlockState ground = level.getBlockState(impact.below());
        level.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK,
                impact, 600);
        level.playSound(null,
                impact.getX() + 0.5,
                impact.getY() + 0.5,
                impact.getZ() + 0.5,
                SoundEvents.MACE_SMASH_GROUND,
                SoundSource.HOSTILE, 1.2f, 0.85f);
        level.sendParticles(new BlockParticleOption(
                        ParticleTypes.BLOCK, ground),
                soldier.getX(), soldier.getY() + 0.15, soldier.getZ(),
                36, 2.0, 0.15, 2.0, 0.08);

        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    > ArenaSkillBalance.IRON_GOLEM_LEAP_RADIUS
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            dealSkillDamage(enemy,
                    soldier.damageSources().mobAttack(soldier),
                    damage);
            Vec3 away = enemy.position().subtract(soldier.position());
            double horizontal = Math.sqrt(
                    away.x * away.x + away.z * away.z);
            Vec3 outward = horizontal < 1.0E-5
                    ? Vec3.ZERO
                    : new Vec3(away.x / horizontal, 0.0,
                    away.z / horizontal);
            enemy.arenaSkills().applyStunWithLaunch(
                    stunTicks,
                    outward.scale(0.65).add(0.0, 0.8, 0.0));
            particleBurst(level, enemy, ParticleTypes.CRIT, 16);
        }

        double shield = arenaMaxHealth(soldier)
                * ArenaSkillBalance.ironGolemShieldRatio(
                star, soldier.getArenaMagicPower());
        soldier.grantArenaShield(shield);
        particleBurst(level, soldier, ParticleTypes.ENCHANT, 20);
    }

    private void slimeLeapImpact(ServerLevel level) {
        int star = soldier.getArenaStar();
        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.slimeAttackMultiplier(
                star, soldier.getArenaMagicPower()));
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    > ArenaSkillBalance.SLIME_LEAP_RADIUS
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            dealSkillDamage(enemy,
                    soldier.damageSources().mobAttack(soldier),
                    damage);
            particleBurst(level, enemy,
                    ParticleTypes.ITEM_SLIME, 16);
        }
        double missing = Math.max(0.0,
                soldier.getArenaDisplayMaxHealth()
                        - soldier.getArenaCurrentHealth());
        if (missing > 0.0) {
            soldier.heal((float) (missing
                    * ArenaSkillBalance
                    .slimeMissingHealthHealRatio(
                            star, soldier.getArenaMagicPower())));
        }
        particleBurst(level, soldier,
                ParticleTypes.ITEM_SLIME, 30);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.SLIME_SQUISH,
                SoundSource.HOSTILE, 1.3f, 0.75f);
    }

    private void rabbitLeapImpact(
            ServerLevel level, @Nullable LivingEntity target) {
        int star = soldier.getArenaStar();
        int duration = ArenaSkillBalance
                .scaledDurationTicks(100, star);
        if (target != null && target.isAlive()
                && target instanceof AbstractArmySoldier enemy
                && soldier.isArenaEnemy(enemy)) {
            float damage = (float) (soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE)
                    * ArenaSkillBalance.rabbitAttackMultiplier(
                    star, soldier.getArenaMagicPower()));
            dealSkillDamage(enemy,
                    soldier.damageSources().mobAttack(soldier),
                    damage);
            double slow = enemy.getArenaEffectiveAttackSpeed()
                    * ArenaSkillBalance.rabbitAttackSpeedSlow(
                    star, soldier.getArenaMagicPower());
            enemy.arenaSkills().addAttackSpeedModifier(
                    skillSourceId("rabbit_slow"),
                    -slow, duration, true);
            particleBurst(level, enemy,
                    ParticleTypes.CRIT, 16);
        }
        applyTemporaryDodge(
                ArenaSkillBalance.rabbitDodgeBonus(
                        star, soldier.getArenaMagicPower()),
                duration);
        particleBurst(level, soldier,
                ParticleTypes.CLOUD, 18);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.RABBIT_JUMP,
                SoundSource.HOSTILE, 1.0f, 0.9f);
    }

    private boolean frogPullFarthest(ServerLevel level) {
        List<AbstractArmySoldier> enemies =
                nearestEnemies(Integer.MAX_VALUE);
        if (enemies.isEmpty()) return false;
        AbstractArmySoldier target = enemies.get(enemies.size() - 1);
        Vec3 look = soldier.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0, look.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 destination = soldier.position().add(
                horizontal.normalize().scale(2.0));
        boolean controllable = !target.isArenaControlImmune();
        if (controllable) {
            Vec3 delta = destination.subtract(target.position());
            if (level.noCollision(target,
                    target.getBoundingBox().move(delta))) {
                target.teleportWithMount(destination.x,
                        destination.y, destination.z, false);
            }
            int stunTicks = ArenaSkillBalance
                    .frogStunTicks(soldier.getArenaStar());
            target.arenaSkills().applyStun(stunTicks);
        }
        double damage = arenaMaxHealth(target)
                * ArenaSkillBalance.frogMaxHealthDamage(
                soldier.getArenaStar(),
                soldier.getArenaMagicPower());
        dealTrueSkillDamage(target,
                soldier.damageSources().indirectMagic(
                        soldier, soldier), (float) damage);
        particleBurst(level, target,
                ParticleTypes.SPLASH, 24);
        level.playSound(null, target.getX(), target.getY(),
                target.getZ(), SoundEvents.FROG_TONGUE,
                SoundSource.HOSTILE, 1.2f, 0.8f);
        return true;
    }

    private boolean foxStealTarget(ServerLevel level) {
        AbstractArmySoldier target = nearestEnemy();
        if (target == null || !target.isAlive()) return false;
        int star = soldier.getArenaStar();
        double attackStolen = target.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.foxAttackStealRatio(
                star, soldier.getArenaMagicPower());
        double speedStolen = target.getArenaEffectiveAttackSpeed()
                * ArenaSkillBalance.foxAttackSpeedStealRatio(
                star, soldier.getArenaMagicPower());
        ResourceLocation sourceId = skillSourceId(
                "fox_steal/" + soldier.tickCount);
        soldier.arenaSkills().addAttackDamageModifier(
                sourceId, attackStolen, 0, false);
        target.arenaSkills().addAttackDamageModifier(
                sourceId, -attackStolen, 0, true);
        soldier.arenaSkills().addAttackSpeedModifier(
                sourceId, speedStolen, 0, false);
        target.arenaSkills().addAttackSpeedModifier(
                sourceId, -speedStolen, 0, true);
        particleBurst(level, target,
                ParticleTypes.WITCH, 18);
        particleBurst(level, soldier,
                ParticleTypes.HAPPY_VILLAGER, 16);
        return true;
    }

    private void creeperExplode(ServerLevel level) {
        if (creeperExplosionResolved) return;
        creeperExplosionResolved = true;
        int star = soldier.getArenaStar();
        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.creeperAttackMultiplier(
                star, soldier.getArenaMagicPower()));
        int stunTicks = ArenaSkillBalance.creeperStunTicks(star);
        int duration = ArenaSkillBalance.scaledDurationTicks(
                ArenaSkillBalance.CREEPER_DEBUFF_TICKS, star);
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    > ArenaSkillBalance.CREEPER_EXPLOSION_RADIUS
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            dealSkillDamage(enemy,
                    soldier.damageSources().mobAttack(soldier),
                    damage);
            if (enemy.isAlive()) {
                enemy.arenaSkills().applyStun(stunTicks);
            }
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, duration,
                    ArenaSkillBalance.CREEPER_SLOW_AMPLIFIER,
                    false, true));
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, duration,
                    ArenaSkillBalance.CREEPER_WEAKNESS_AMPLIFIER,
                    false, true));
            enemy.addEffect(new MobEffectInstance(
                    ModEffects.ARMOR_BREAK, duration,
                    ArenaSkillBalance
                            .CREEPER_ARMOR_BREAK_AMPLIFIER,
                    false, true));
            enemy.arenaEquipment()
                    .applyMagicResistancePercentReduction(
                            ArenaSkillBalance
                                    .CREEPER_MAGIC_RESIST_REDUCTION,
                            duration);
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                soldier.getX(), soldier.getY() + 0.5,
                soldier.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 1.5f, 0.8f);
        if (soldier.isAlive()) {
            soldier.kill();
        }
    }

    private boolean creeperHasContactTarget() {
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    <= ArenaSkillBalance.CREEPER_TRIGGER_RADIUS
                    && Math.abs(enemy.getY() - soldier.getY())
                    <= 2.0) {
                return true;
            }
        }
        return false;
    }

    private void prepareCreeperReadySpeed() {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.CREEPER_EXPLODE
                || silenceTicks > 0
                || soldier.getArenaSkillCharge()
                < soldier.getArenaSkillRequired()) {
            return;
        }
        MobEffectInstance current = soldier.getEffect(
                MobEffects.MOVEMENT_SPEED);
        if (current != null
                && current.getAmplifier()
                >= ArenaSkillBalance.CREEPER_READY_SPEED_AMPLIFIER
                && current.isInfiniteDuration()) {
            return;
        }
        soldier.removeEffect(MobEffects.MOVEMENT_SPEED);
        soldier.addEffect(new MobEffectInstance(
                MobEffects.MOVEMENT_SPEED,
                MobEffectInstance.INFINITE_DURATION,
                ArenaSkillBalance.CREEPER_READY_SPEED_AMPLIFIER,
                false, true));
    }

    public void onArenaDeath() {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.CREEPER_EXPLODE
                || creeperExplosionResolved
                || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        creeperExplode(level);
    }

    private boolean beginPandaRoll() {
        AbstractArmySoldier target = nearestEnemy();
        if (target == null) return false;
        pandaRollTicks = 20;
        pandaRollTargetId = target.getUUID();
        soldier.setNoAi(true);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.PANDA_ROLL, true);
        soldier.setTarget(target);
        soldier.getNavigation().stop();
        soldier.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                ArenaSkillBalance.pandaResistanceTicks(
                        soldier.getArenaStar()),
                ArenaSkillBalance.pandaResistanceAmplifier(
                        soldier.getArenaStar()),
                false, true), soldier);
        return true;
    }

    private void tickPandaRoll(ServerLevel level) {
        AbstractArmySoldier target =
                pandaRollTargetId == null ? null
                        : level.getEntity(pandaRollTargetId)
                        instanceof AbstractArmySoldier enemy
                        && enemy.isAlive()
                        && soldier.isArenaEnemy(enemy)
                        ? enemy : null;
        if (target == null) {
            finishPandaRoll();
            return;
        }
        pandaRollTicks--;
        soldier.getLookControl().setLookAt(
                target, 30.0f, 30.0f);
        double surfaceDistance =
                MeleeReach.horizontalSurfaceDistance(
                        soldier, target);
        if (surfaceDistance <= 1.25
                || (pandaRollTicks <= 0
                && surfaceDistance <= 2.5)) {
            float damage = (float) (soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE)
                    * ArenaSkillBalance.pandaRollAttackMultiplier(
                    soldier.getArenaStar(),
                    soldier.getArenaMagicPower()));
            dealSkillDamage(target,
                    soldier.damageSources().mobAttack(soldier),
                    damage);
            particleBurst(level, target,
                    ParticleTypes.CRIT, 18);
            level.playSound(null, target.getX(), target.getY(),
                    target.getZ(), SoundEvents.PANDA_BITE,
                    SoundSource.HOSTILE, 1.2f, 0.8f);
            finishPandaRoll();
            return;
        }
        Vec3 delta = target.position()
                .subtract(soldier.position());
        Vec3 horizontal = new Vec3(
                delta.x, 0.0, delta.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            finishPandaRoll();
            return;
        }
        double step = Math.min(0.55,
                horizontal.length())
                * soldier.getArenaFreezeSpeedMultiplier();
        Vec3 movement = horizontal.normalize().scale(step);
        soldier.move(MoverType.SELF, new Vec3(
                movement.x, soldier.getDeltaMovement().y,
                movement.z));
        if ((soldier.tickCount & 1) == 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    soldier.getX(), soldier.getY() + 0.3,
                    soldier.getZ(), 1,
                    0.22, 0.12, 0.22, 0.0);
        }
        if (soldier.horizontalCollision) {
            finishPandaRoll();
        }
    }

    private void finishPandaRoll() {
        pandaRollTicks = 0;
        pandaRollTargetId = null;
        soldier.setNoAi(false);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.PANDA_ROLL, false);
    }

    private boolean endermanLockFarthest(ServerLevel level) {
        List<AbstractArmySoldier> enemies =
                nearestEnemies(Integer.MAX_VALUE);
        if (enemies.isEmpty()) return false;
        AbstractArmySoldier target =
                enemies.get(enemies.size() - 1);
        endermanLockedTargetId = target.getUUID();
        endermanTeleportCountdown =
                ArenaSkillBalance.ENDERMAN_TELEPORT_INTERVAL_TICKS;
        soldier.setTarget(target);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.ENDERMAN_LOCK, true);
        teleportBesideEndermanTarget(level, target);
        target.arenaSkills().applyStun(
                ArenaSkillBalance.endermanStunTicks(
                        soldier.getArenaStar()));
        ResourceLocation attackBuffId =
                skillSourceId("enderman_lock_attack");
        double existingBuff = externalAttackDamageModifiers
                .getOrDefault(attackBuffId,
                        new TimedAttackModifier(0.0, 0, false))
                .amount;
        double baseAttack = Math.max(0.0,
                soldier.getAttributeValue(Attributes.ATTACK_DAMAGE)
                        - existingBuff);
        addAttackDamageModifier(attackBuffId,
                baseAttack * ArenaSkillBalance
                        .ENDERMAN_ATTACK_BUFF_RATIO,
                ArenaSkillBalance.ENDERMAN_ATTACK_BUFF_TICKS,
                false);
        particleBurst(level, target,
                ParticleTypes.REVERSE_PORTAL, 22);
        return true;
    }

    private void tickEndermanLock(ServerLevel level) {
        if (endermanLockedTargetId == null) return;
        AbstractArmySoldier target =
                level.getEntity(endermanLockedTargetId)
                        instanceof AbstractArmySoldier enemy
                        && enemy.isAlive()
                        && soldier.isArenaEnemy(enemy)
                        ? enemy : null;
        if (target == null) {
            clearEndermanLock();
            return;
        }
        soldier.setTarget(target);
        if (endermanTeleportCountdown > 0) {
            endermanTeleportCountdown--;
        }
        if (endermanTeleportCountdown <= 0) {
            teleportBesideEndermanTarget(level, target);
            endermanTeleportCountdown =
                    ArenaSkillBalance
                            .ENDERMAN_TELEPORT_INTERVAL_TICKS;
        }
    }

    private void teleportBesideEndermanTarget(
            ServerLevel level, AbstractArmySoldier target) {
        Vec3 fromTarget = soldier.position()
                .subtract(target.position());
        Vec3 horizontal = new Vec3(
                fromTarget.x, 0.0, fromTarget.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(1.0, 0.0, 0.0);
        }
        double distance = target.getBbWidth() * 0.5
                + soldier.getBbWidth() * 0.5 + 0.25;
        Vec3 destination = target.position().add(
                horizontal.normalize().scale(distance));
        if (!level.noCollision(soldier,
                soldier.getBoundingBox().move(
                        destination.subtract(
                                soldier.position())))) {
            destination = target.position();
        }
        level.sendParticles(ParticleTypes.PORTAL,
                soldier.getX(), soldier.getY() + 0.5,
                soldier.getZ(), 20, 0.25, 0.7, 0.25, 0.12);
        soldier.teleportWithMount(
                destination.x, destination.y,
                destination.z, false);
        soldier.arenaTraits().onDashOrTeleport();
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                destination.x, destination.y + 0.5,
                destination.z, 20, 0.25, 0.7, 0.25, 0.12);
        level.playSound(null, destination.x, destination.y,
                destination.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0f, 1.15f);
    }

    private void clearEndermanLock() {
        if (endermanLockedTargetId != null) {
            soldier.setTarget(null);
        }
        endermanLockedTargetId = null;
        endermanTeleportCountdown = 0;
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.ENDERMAN_LOCK, false);
    }

    public boolean dealsTrueDamageTo(
            LivingEntity target, DamageSource source) {
        boolean locked = endermanLockedTargetId != null
                && endermanLockedTargetId.equals(
                        target.getUUID());
        boolean prepared = forcedTrueTargetId != null
                && forcedTrueTargetId.equals(target.getUUID())
                && soldier.tickCount <= forcedTrueExpiresAt;
        return (locked || prepared)
                && !isDealingSkillDamage()
                && !soldier.isDealingArenaEffectDamage()
                && isBasicAttack(source);
    }

    public void prepareForcedTrueHit(
            LivingEntity target) {
        forcedTrueTargetId = target.getUUID();
        forcedTrueAmount = 0.0f;
        forcedTrueExpiresAt = soldier.tickCount + 2;
    }

    public void markForcedTrueDamage(
            LivingEntity target, float amount) {
        if (amount <= 0.0f) return;
        forcedTrueTargetId = target.getUUID();
        forcedTrueAmount = amount;
        forcedTrueExpiresAt = soldier.tickCount + 2;
    }

    public float takeForcedTrueDamage(
            LivingEntity target, DamageSource source) {
        if (forcedTrueTargetId == null
                || !forcedTrueTargetId.equals(target.getUUID())
                || soldier.tickCount > forcedTrueExpiresAt
                || !isBasicAttack(source)) {
            forcedTrueTargetId = null;
            forcedTrueAmount = 0.0f;
            forcedTrueExpiresAt = 0;
            return -1.0f;
        }
        float amount = forcedTrueAmount;
        forcedTrueTargetId = null;
        forcedTrueAmount = 0.0f;
        forcedTrueExpiresAt = 0;
        return amount;
    }

    private boolean startWardenSonicBarrage(
            ServerLevel level, @Nullable AbstractArmySoldier target) {
        if (target == null) target = nearestEnemy();
        if (target == null) return false;
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 3.0f, 1.0f);
        wardenSonicTargetId = target.getUUID();
        wardenSonicWavesRemaining =
                ArenaSkillBalance.WARDEN_SONIC_WAVES;
        fireWardenSonicWave(level, target, false);
        wardenSonicWavesRemaining--;
        wardenSonicNextTick = soldier.tickCount
                + 10;
        return true;
    }

    private void tickWardenSonicBarrage(ServerLevel level) {
        if (wardenSonicWavesRemaining <= 0
                || soldier.tickCount < wardenSonicNextTick) {
            return;
        }
        AbstractArmySoldier target =
                wardenSonicTargetId == null ? null
                        : level.getEntity(wardenSonicTargetId)
                        instanceof AbstractArmySoldier enemy
                        && enemy.isAlive()
                        && soldier.isArenaEnemy(enemy)
                        ? enemy : null;
        if (target == null) {
            target = nearestEnemy();
            if (target == null) {
                wardenSonicWavesRemaining = 0;
                wardenSonicTargetId = null;
                return;
            }
            wardenSonicTargetId = target.getUUID();
        }
        fireWardenSonicWave(level, target, false);
        wardenSonicWavesRemaining--;
        wardenSonicNextTick = soldier.tickCount + 10;
        if (wardenSonicWavesRemaining <= 0) {
            wardenSonicTargetId = null;
        }
    }

    public boolean fireWardenBasicSonic(
            LivingEntity target) {
        if (!(soldier.level() instanceof ServerLevel level)
                || soldier.getArenaStar() < 3
                || !(target instanceof AbstractArmySoldier enemy)
                || !enemy.isAlive()
                || !soldier.isArenaEnemy(enemy)) {
            return false;
        }
        soldier.arenaEquipment().beforeBasicAttack();
        return fireWardenSonicWave(level, enemy, true);
    }

    private boolean fireWardenSonicWave(
            ServerLevel level, AbstractArmySoldier target,
            boolean basicAttack) {
        level.broadcastEntityEvent(soldier, (byte) 62);
        Vec3 start = new Vec3(
                soldier.getX(),
                soldier.getY()
                        + soldier.getBbHeight() * 0.55,
                soldier.getZ());
        Vec3 aim = target.getBoundingBox().getCenter();
        double dx = aim.x - start.x;
        double dz = aim.z - start.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-6) {
            Vec3 look = soldier.getLookAngle();
            dx = look.x;
            dz = look.z;
            horizontal = Math.max(1.0E-6,
                    Math.sqrt(dx * dx + dz * dz));
        }
        float yaw = (float) Math.atan2(dz, dx);
        double vertical = aim.y - start.y;
        double pitch = Math.abs(vertical) > 0.20
                ? Math.max(-0.65, Math.min(0.85,
                Math.atan2(vertical, horizontal)))
                : 0.0;
        Vec3 direction = new Vec3(
                Math.cos(pitch) * Math.cos(yaw),
                Math.sin(pitch),
                Math.cos(pitch) * Math.sin(yaw)).normalize();
        Vec3 rangeEnd = start.add(direction.scale(
                ArenaSkillBalance.WARDEN_SONIC_RANGE));
        Vec3 end = clipSonicBandEnd(
                level, start, rangeEnd);
        double range = Math.sqrt(
                start.distanceToSqr(end))
                + ArenaSkillBalance.WARDEN_SONIC_RADIUS;
        double rangeSqr = range * range;

        List<AbstractArmySoldier> candidates =
                level.getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        new AABB(start, end).inflate(
                                ArenaSkillBalance
                                        .WARDEN_SONIC_RADIUS + 2.0),
                        enemy -> enemy != soldier
                                && enemy.isAlive()
                                && enemy.isArenaUnit()
                                && soldier.isArenaEnemy(enemy));
        candidates.sort(Comparator.comparingDouble(
                enemy -> enemy.distanceToSqr(start)));
        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.wardenSonicAttackMultiplier(
                soldier.getArenaStar(),
                soldier.getArenaMagicPower()));
        int stunTicks = ArenaSkillBalance
                .wardenSonicStunTicks(
                        soldier.getArenaStar());
        boolean hitAny = false;
        for (AbstractArmySoldier enemy : candidates) {
            Vec3 enemyCenter =
                    enemy.getBoundingBox().getCenter();
            if (start.distanceToSqr(enemyCenter)
                    > rangeSqr
                    || enemy.getBoundingBox()
                    .inflate(ArenaSkillBalance
                            .WARDEN_SONIC_RADIUS)
                    .clip(start, end).isEmpty()
                    || clipSonicBandEnd(level, start,
                    enemyCenter).distanceToSqr(start)
                    < start.distanceToSqr(enemyCenter)
                    - 0.01) {
                continue;
            }
            if (basicAttack) {
                prepareForcedTrueHit(enemy);
                enemy.hurt(
                        soldier.damageSources()
                                .sonicBoom(soldier),
                        damage);
            } else {
                dealTrueSkillDamage(
                        enemy,
                        soldier.damageSources()
                                .sonicBoom(soldier),
                        damage);
            }
            // 音波已与完整碰撞箱相交，控制不能因护盾或免伤吃掉伤害而失效。
            hitAny = true;
            if (enemy.isAlive()) {
                enemy.arenaSkills().applyStun(stunTicks);
            }
        }
        int visualSteps = Math.max(1,
                (int) Math.floor(
                        Math.sqrt(start.distanceToSqr(end))));
        for (int step = 1; step <= visualSteps; step++) {
            Vec3 point = start.lerp(end,
                    step / (double) visualSteps);
            level.sendParticles(
                    ParticleTypes.SONIC_BOOM,
                    point.x, point.y, point.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.WARDEN_SONIC_BOOM,
                SoundSource.HOSTILE, 3.0f, 1.0f);
        return hitAny;
    }

    private Vec3 clipSonicBandEnd(
            ServerLevel level, Vec3 start, Vec3 end) {
        Vec3 delta = end.subtract(start);
        Vec3 horizontal = new Vec3(
                delta.x, 0.0, delta.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            return end;
        }
        Vec3 direction = horizontal.normalize();
        Vec3 perpendicular = new Vec3(
                -direction.z, 0.0, direction.x);
        double radius = ArenaSkillBalance
                .WARDEN_SONIC_RADIUS;
        double[] sides = {
                -radius, -radius * 0.5, 0.0,
                radius * 0.5, radius
        };
        double[] verticals = {
                0.0
        };
        double best = start.distanceToSqr(end);
        Vec3 result = end;
        for (double side : sides) {
            for (double vertical : verticals) {
                Vec3 offset = perpendicular.scale(side)
                        .add(0.0, vertical, 0.0);
                BlockHitResult hit = level.clip(
                        new ClipContext(
                                start.add(offset),
                                end.add(offset),
                                ClipContext.Block.COLLIDER,
                                ClipContext.Fluid.NONE,
                                soldier));
                if (hit.getType() == HitResult.Type.MISS) {
                    continue;
                }
                double distance = start.distanceToSqr(
                        hit.getLocation());
                if (distance < best) {
                    best = distance;
                    result = hit.getLocation();
                }
            }
        }
        return result;
    }

    public void recordKillParticipant(
            @Nullable AbstractArmySoldier attacker) {
        if (attacker == null || attacker == soldier
                || !attacker.isAlive()
                || !attacker.isArenaUnit()
                || !attacker.isArenaEnemy(soldier)) {
            return;
        }
        killParticipants.add(attacker.getUUID());
    }

    public void awardKillParticipants() {
        if (killParticipants.isEmpty()
                || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        List<UUID> participants =
                new ArrayList<>(killParticipants);
        killParticipants.clear();
        for (UUID id : participants) {
            if (level.getEntity(id)
                    instanceof AbstractArmySoldier participant
                    && participant.isAlive()
                    && participant.isArenaEnemy(soldier)) {
                participant.arenaSkills()
                        .onKillParticipation();
                participant.arenaTraits()
                        .onKillParticipation();
            }
        }
    }

    private void onKillParticipation() {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.WARDEN_SONIC_BOOM) {
            return;
        }
        int star = soldier.getArenaStar();
        double healthGain = ArenaSkillBalance
                .wardenKillHealthGain(star);
        double attackGain = ArenaSkillBalance
                .wardenKillAttackGain(star);
        double movementGain = ArenaSkillBalance
                .wardenKillMovementGain(star);
        wardenKillStacks++;
        wardenMaxHealthBonus += healthGain;
        wardenAttackBonus += attackGain;
        wardenMovementBonus += movementGain;

        double newMax = soldier.getArenaDisplayMaxHealth()
                + healthGain;
        soldier.setArenaDisplayMaxHealth(newMax);
        soldier.setArenaCurrentHealth(Math.min(
                newMax, soldier.getArenaCurrentHealth()
                        + healthGain));
        setMaxHealthMirror(newMax);
        setAdditiveModifier(Attributes.ATTACK_DAMAGE,
                WARDEN_ATTACK_MODIFIER, wardenAttackBonus);
        setAttributeModifier(Attributes.ATTACK_DAMAGE,
                WOLF_ATTACK_MODIFIER, wolfAttackPercent,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setWardenMovementSpeedBonus(
                wardenMovementBonus);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.WARDEN_GROWTH, true);
        applyArenaGrowthScale();
        soldier.syncArenaHealthMirror();
        if (soldier.level() instanceof ServerLevel level) {
            particleBurst(level, soldier,
                    ParticleTypes.SCULK_SOUL, 24);
        }
    }

    public int wardenKillStacks() {
        return wardenKillStacks;
    }

    private void applyArenaGrowthScale() {
        double scale;
        if (soldier.getArenaSkillType()
                == ArenaSkillType.HUSK_BATTLE_GROWTH) {
            scale = Math.min(
                    ArenaSkillBalance.HUSK_GROWTH_SCALE_MAX,
                    1.0 + huskGrowthStacks
                            * ArenaSkillBalance
                            .HUSK_GROWTH_SCALE_STEP);
        } else if (soldier.getArenaSkillType()
                == ArenaSkillType.WARDEN_SONIC_BOOM) {
            scale = Math.min(
                    ArenaSkillBalance.WARDEN_GROWTH_SCALE_MAX,
                    1.0 + wardenKillStacks
                            * ArenaSkillBalance
                            .WARDEN_GROWTH_SCALE_STEP);
        } else if (soldier.getArenaSkillType()
                == ArenaSkillType.GIANT_ARMADILLO_MOMENTUM) {
            scale = Math.min(
                    ArenaSkillBalance.GIANT_ARMADILLO_MAX_SCALE,
                    ArenaSkillBalance.GIANT_ARMADILLO_BASE_SCALE
                            + giantArmadilloStacks
                            * ArenaSkillBalance
                            .GIANT_ARMADILLO_SCALE_STEP);
        } else {
            return;
        }
        AttributeInstance scaleAttribute =
                soldier.getAttribute(Attributes.SCALE);
        if (scaleAttribute == null) return;
        scaleAttribute.setBaseValue(scale);
        soldier.refreshDimensions();
    }

    private void tickTimedEffects() {
        tickExternalModifiers();
        if (silenceTicks > 0 && --silenceTicks == 0) {
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.SILENCE, false);
        }
        if (supportAttackSpeedTicks > 0
                && --supportAttackSpeedTicks == 0) {
            supportAttackSpeedBonus = 0.0;
            soldier.setArenaSupportAttackSpeedBonus(0.0);
        }
        if (polarFormActive && --polarFormTicks <= 0) {
            endPolarForm();
        }
        if (rapidFireTicks > 0 && --rapidFireTicks == 0) {
            soldier.setArenaSkillAttackSpeedBonus(0.0);
            if (soldier.getArenaSkillType()
                    == ArenaSkillType.SHOTGUN_BARRAGE) {
                soldier.setArenaSkillLifesteal(0.0);
            }
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.RAPID_FIRE, false);
        }
        if (strengthTicks > 0 && --strengthTicks == 0) {
            AttributeInstance attack = soldier.getAttribute(
                    Attributes.ATTACK_DAMAGE);
            if (attack != null) attack.removeModifier(STRENGTH_MODIFIER);
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.STRENGTH, false);
        }
        if (tauntTicks > 0 && --tauntTicks == 0) {
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.TAUNT, false);
        }
        if (receivedTauntTicks > 0 && --receivedTauntTicks == 0) {
            tauntSourceId = null;
        }
        if (temporaryDodgeExpiresAt != 0
                && soldier.tickCount >= temporaryDodgeExpiresAt) {
            temporaryDodgeBonus = 0.0;
            temporaryDodgeExpiresAt = 0;
        }
    }

    private void tickExternalModifiers() {
        boolean changed = false;
        int now = soldier.tickCount;
        var damageIterator =
                externalAttackDamageModifiers.values().iterator();
        while (damageIterator.hasNext()) {
            TimedAttackModifier modifier = damageIterator.next();
            if (now >= modifier.expiresAt) {
                damageIterator.remove();
                changed = true;
            }
        }
        var speedIterator =
                externalAttackSpeedModifiers.values().iterator();
        while (speedIterator.hasNext()) {
            TimedAttackSpeedModifier modifier = speedIterator.next();
            if (now >= modifier.expiresAt) {
                speedIterator.remove();
                changed = true;
            }
        }
        var movementIterator =
                externalMovementSpeedModifiers.entrySet().iterator();
        while (movementIterator.hasNext()) {
            Map.Entry<ResourceLocation, TimedMovementSpeedModifier>
                    entry = movementIterator.next();
            if (now >= entry.getValue().expiresAt) {
                movementIterator.remove();
                AttributeInstance movement = soldier.getAttribute(
                        Attributes.MOVEMENT_SPEED);
                if (movement != null) {
                    movement.removeModifier(entry.getKey());
                }
                changed = true;
            }
        }
        if (changed) refreshExternalStatModifiers();
    }

    public void applyArenaOvertime(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        if (!overtimeActive) {
            overtimeActive = true;
            overtimeSeconds = 0;
            overtimeBaseAttack = soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE);
            overtimeBaseAttackSpeed =
                    soldier.getArenaBaseAttackSpeed();
            addMovementSpeedModifier(
                    OVERTIME_MOVEMENT_MODIFIER, 1.0,
                    0, false);
        }
        if (overtimeSeconds == safeSeconds) return;
        overtimeSeconds = safeSeconds;
        addAttackDamageModifier(
                OVERTIME_ATTACK_MODIFIER,
                overtimeBaseAttack * 0.30 * overtimeSeconds,
                0, false);
        addAttackSpeedModifier(
                OVERTIME_ATTACK_SPEED_MODIFIER,
                overtimeBaseAttackSpeed * 0.30 * overtimeSeconds,
                0, false);
    }

    public void clearArenaOvertime() {
        if (!overtimeActive) return;
        overtimeActive = false;
        overtimeSeconds = 0;
        overtimeBaseAttack = 0.0;
        overtimeBaseAttackSpeed = 0.0;
        externalAttackDamageModifiers.remove(
                OVERTIME_ATTACK_MODIFIER);
        externalAttackSpeedModifiers.remove(
                OVERTIME_ATTACK_SPEED_MODIFIER);
        externalMovementSpeedModifiers.remove(
                OVERTIME_MOVEMENT_MODIFIER);
        AttributeInstance movement = soldier.getAttribute(
                Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.removeModifier(OVERTIME_MOVEMENT_MODIFIER);
        }
        refreshExternalStatModifiers();
    }

    public void applyArenaOvertimeToSummons(int seconds) {
        if (!(soldier.level() instanceof ServerLevel level)) return;
        for (UUID summonId : summonIds) {
            Entity entity = level.getEntity(summonId);
            if (entity instanceof AbstractArmySoldier summon
                    && summon.isAlive()
                    && !summon.isRemoved()
                    && summon.isArenaUnit()) {
                summon.arenaSkills().applyArenaOvertime(seconds);
            }
        }
    }

    public void clearArenaOvertimeFromSummons() {
        if (!(soldier.level() instanceof ServerLevel level)) return;
        for (UUID summonId : summonIds) {
            Entity entity = level.getEntity(summonId);
            if (entity instanceof AbstractArmySoldier summon
                    && summon.isArenaUnit()) {
                summon.arenaSkills().clearArenaOvertime();
            }
        }
    }

    public boolean isArenaOvertimeActive() {
        return overtimeActive;
    }

    public float modifyIncomingHealing(float amount) {
        return amount <= 0.0f
                ? amount
                : (float) (amount * arenaOvertimeEffectMultiplier());
    }

    public double modifyIncomingShield(double amount) {
        return amount <= 0.0
                ? amount
                : amount * arenaOvertimeEffectMultiplier();
    }

    public int modifyIncomingControlTicks(int ticks) {
        if (ticks <= 0) return ticks;
        return Math.max(1, (int) Math.round(
                ticks * arenaOvertimeEffectMultiplier()));
    }

    private double arenaOvertimeEffectMultiplier() {
        if (!overtimeActive) return 1.0;
        return Math.max(0.30,
                1.0 - 0.10 * overtimeSeconds);
    }

    public void addAttackDamageModifier(
            ResourceLocation id, double amount,
            int ticks, boolean harmful) {
        if (harmful && soldier.isArenaDebuffImmune()) return;
        externalAttackDamageModifiers.put(id,
                new TimedAttackModifier(amount,
                        expiryTick(ticks), harmful));
        refreshExternalStatModifiers();
    }

    public void addAttackSpeedModifier(
            ResourceLocation id, double flatAttackSpeed,
            int ticks, boolean harmful) {
        if (harmful && soldier.isArenaDebuffImmune()) return;
        externalAttackSpeedModifiers.put(id,
                new TimedAttackSpeedModifier(
                        flatAttackSpeed,
                        expiryTick(ticks), harmful));
        refreshExternalStatModifiers();
    }

    public void addMovementSpeedModifier(
            ResourceLocation id, double percent,
            int ticks, boolean harmful) {
        if (percent == 0.0
                || harmful && soldier.isArenaDebuffImmune()) {
            return;
        }
        externalMovementSpeedModifiers.put(id,
                new TimedMovementSpeedModifier(
                        percent, expiryTick(ticks), harmful));
        refreshExternalStatModifiers();
    }

    private int expiryTick(int ticks) {
        if (ticks <= 0) return Integer.MAX_VALUE;
        long expiry = (long) soldier.tickCount + ticks;
        return expiry >= Integer.MAX_VALUE
                ? Integer.MAX_VALUE : (int) expiry;
    }

    private void refreshExternalStatModifiers() {
        double damage = externalAttackDamageModifiers.values()
                .stream().mapToDouble(
                        modifier -> modifier.amount).sum();
        setAdditiveModifier(Attributes.ATTACK_DAMAGE,
                EXTERNAL_ATTACK_MODIFIER, damage);
        double speed = externalAttackSpeedModifiers.values()
                .stream().mapToDouble(
                        modifier -> modifier.amount).sum();
        soldier.setArenaExternalAttackSpeedFlatBonus(speed);
        AttributeInstance movement = soldier.getAttribute(
                Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            for (Map.Entry<ResourceLocation,
                    TimedMovementSpeedModifier> entry
                    : externalMovementSpeedModifiers.entrySet()) {
                movement.addOrUpdateTransientModifier(
                        new AttributeModifier(
                                entry.getKey(),
                                entry.getValue().amount,
                                AttributeModifier.Operation
                                        .ADD_MULTIPLIED_BASE));
            }
        }
    }

    public void applyTemporaryDodge(double bonus, int ticks) {
        if (bonus <= 0.0 || ticks <= 0) return;
        temporaryDodgeBonus = Math.max(
                temporaryDodgeBonus, bonus);
        temporaryDodgeExpiresAt = Math.max(
                temporaryDodgeExpiresAt,
                expiryTick(ticks));
    }

    public double temporaryDodgeBonus() {
        return temporaryDodgeBonus;
    }

    public void onArenaTraitShieldBroken() {
        soldier.arenaTraits().onShieldBroken();
    }

    private void tickTaunt() {
        if (tauntTicks <= 0) return;
        for (AbstractArmySoldier enemy : nearestEnemies(Integer.MAX_VALUE)) {
            enemy.arenaSkills().applyTaunt(soldier, 2);
        }
    }

    private void tickWitherDrain(ServerLevel level) {
        if (!witherDomination
                && soldier.tickCount >= witherDrainUntil) {
            drainTargets.clear();
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.WITHER_DRAIN, false);
            return;
        }
        if (soldier.tickCount < nextWitherDrainTick) return;
        nextWitherDrainTick = soldier.tickCount + 20;
        if (witherDomination) {
            for (AbstractArmySoldier enemy :
                    nearestEnemies(Integer.MAX_VALUE)) {
                drainTargets.add(enemy.getUUID());
            }
        }
        if (drainTargets.isEmpty()) return;
        double rate = ArenaSkillBalance.witherDrainRate(
                soldier.getArenaStar(), soldier.getArenaMagicPower());
        List<UUID> expired = new ArrayList<>();
        for (UUID id : drainTargets) {
            if (!(level.getEntity(id) instanceof LivingEntity target)
                    || !target.isAlive()
                    || !soldier.isArenaEnemy(target)) {
                expired.add(id);
                continue;
            }
            // 星级百分比明确按最大生命计算，避免目标越残血偷取越少。
            float stolen = (float) (arenaMaxHealth(target) * rate);
            if (stolen <= 0.0f) continue;
            float drained = dealTrueSkillDamage(target,
                    soldier.damageSources().indirectMagic(soldier, soldier),
                    stolen);
            soldier.heal(drained);
            emitParticleLine(level,
                    soldier.getBoundingBox().getCenter(),
                    target.getBoundingBox().getCenter(),
                    ParticleTypes.SOUL, 8);
            particleBurst(level, target, ParticleTypes.SOUL, 6);
        }
        drainTargets.removeAll(expired);
    }

    private void applyStrength(double multiplier) {
        AttributeInstance attack = soldier.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attack == null) return;
        attack.addOrUpdateTransientModifier(new AttributeModifier(
                STRENGTH_MODIFIER, multiplier,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    public boolean dealSkillDamage(LivingEntity target, DamageSource source,
                                   float amount) {
        if (amount <= 0.0f) return false;
        skillDamageDepth++;
        try {
            return target.hurt(source, amount);
        } finally {
            skillDamageDepth--;
        }
    }

    private float dealTrueSkillDamage(LivingEntity target, DamageSource source,
                                      float amount) {
        if (amount <= 0.0f || !target.isAlive()) return 0.0f;
        float before = arenaCurrentHealth(target);
        skillDamageDepth++;
        soldier.beginArenaTrueDamage(amount);
        try {
            target.hurt(source, amount);
        } finally {
            soldier.endArenaTrueDamage();
            skillDamageDepth--;
        }
        return Math.max(0.0f, before - arenaCurrentHealth(target));
    }

    @Nullable
    private AbstractArmySoldier nearestEnemy() {
        List<AbstractArmySoldier> enemies = nearestEnemies(1);
        return enemies.isEmpty() ? null : enemies.get(0);
    }

    private List<AbstractArmySoldier> nearestEnemies(int limit) {
        List<AbstractArmySoldier> enemies = new ArrayList<>(
                soldier.level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        soldier.getBoundingBox().inflate(64.0),
                        other -> other.isAlive()
                                && other.isArenaUnit()
                                && soldier.isArenaEnemy(other)));
        enemies.sort(Comparator.comparingDouble(soldier::distanceToSqr));
        if (enemies.size() > limit) return enemies.subList(0, limit);
        return enemies;
    }

    /**
     * 属性刷新会先写回卡面基础值，再重放尸壳成长和北极熊临时形态。
     * 生命比例沿用装备刷新前，装备变化不会凭空治疗或额外扣血。
     */
    public void reapplyArenaStatBonuses(double healthRatio) {
        double baseMax = soldier.getArenaDisplayMaxHealth();
        if (polarFormActive) {
            polarBaseMaxHealth = baseMax;
        }
        double maxHealth = Math.max(1.0,
                baseMax + huskMaxHealthBonus
                        + wardenMaxHealthBonus
                        + (polarFormActive ? polarHealthBonus : 0.0));
        soldier.setArenaDisplayMaxHealth(maxHealth);
        soldier.setArenaCurrentHealth(Math.max(0.0,
                maxHealth * Math.max(0.0, Math.min(1.0, healthRatio))));
        setMaxHealthMirror(maxHealth);
        setAdditiveModifier(Attributes.ATTACK_DAMAGE,
                HUSK_ATTACK_MODIFIER, huskAttackBonus);
        setAdditiveModifier(Attributes.ATTACK_DAMAGE,
                WARDEN_ATTACK_MODIFIER, wardenAttackBonus);
        setAdditiveModifier(Attributes.ARMOR,
                HUSK_ARMOR_MODIFIER, huskArmorBonus);
        setWardenMovementSpeedBonus(wardenMovementBonus);
        if (soldier.getArenaSkillType()
                == ArenaSkillType.GIANT_ARMADILLO_MOMENTUM) {
            double bonus = giantArmadilloStacks
                    * ArenaSkillBalance.GIANT_ARMADILLO_STAT_STEP;
            setAttributeModifier(Attributes.ATTACK_DAMAGE,
                    GIANT_ARMADILLO_ATTACK_MODIFIER, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            setAttributeModifier(Attributes.MOVEMENT_SPEED,
                    GIANT_ARMADILLO_MOVEMENT_MODIFIER, bonus,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            soldier.setArenaSkillAttackSpeedBonus(bonus);
        }
        soldier.setArenaMagicResistance(
                soldier.getArenaMagicResistance()
                        + huskMagicResistanceBonus);
        applyArenaGrowthScale();
        soldier.syncArenaHealthMirror();
    }

    private void applyHuskGrowth(ServerLevel level) {
        int star = soldier.getArenaStar();
        double magicPower = soldier.getArenaMagicPower();
        double healthGain = ArenaSkillBalance.huskHealthGain(
                star, magicPower);
        double attackGain = ArenaSkillBalance.huskAttackGain(
                star, magicPower);
        double armorGain = ArenaSkillBalance.huskArmorGain(
                star, magicPower);
        double magicResistanceGain =
                ArenaSkillBalance.huskMagicResistanceGain(
                        star, magicPower);

        huskMaxHealthBonus += healthGain;
        huskAttackBonus += attackGain;
        huskArmorBonus += armorGain;
        huskMagicResistanceBonus += magicResistanceGain;
        huskGrowthStacks++;

        double oldMax = soldier.getArenaDisplayMaxHealth();
        double maxHealth = oldMax + healthGain;
        soldier.setArenaDisplayMaxHealth(maxHealth);
        soldier.setArenaCurrentHealth(Math.min(maxHealth,
                soldier.getArenaCurrentHealth() + healthGain));
        setMaxHealthMirror(maxHealth);
        setAdditiveModifier(Attributes.ATTACK_DAMAGE,
                HUSK_ATTACK_MODIFIER, huskAttackBonus);
        setAdditiveModifier(Attributes.ARMOR,
                HUSK_ARMOR_MODIFIER, huskArmorBonus);
        soldier.setArenaMagicResistance(
                soldier.getArenaMagicResistance()
                        + magicResistanceGain);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.BATTLE_GROWTH, true);
        applyArenaGrowthScale();
        soldier.syncArenaHealthMirror();
    }

    private boolean applySpiderSlow(ServerLevel level) {
        int duration = ArenaSkillBalance.spiderSlowTicks(
                soldier.getArenaStar());
        boolean affected = false;
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    > ArenaSkillBalance.SPIDER_SLOW_RADIUS
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0
                    || enemy.isArenaControlImmune()) {
                continue;
            }
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    duration,
                    ArenaSkillBalance.SPIDER_SLOW_AMPLIFIER,
                    false, true));
            particleBurst(level, enemy, ParticleTypes.ITEM_SLIME, 10);
            affected = true;
        }
        if (affected) {
            emitGroundRing(level, soldier.position(),
                    ArenaSkillBalance.SPIDER_SLOW_RADIUS,
                    ParticleTypes.ITEM_SLIME, 20);
        }
        return affected;
    }

    private boolean caveSpiderVenomBurst(ServerLevel level) {
        int star = soldier.getArenaStar();
        boolean affected = false;
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    > ArenaSkillBalance.CAVE_SPIDER_SKILL_RADIUS
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.POISON,
                    ArenaSkillBalance.caveSpiderSkillPoisonTicks(star),
                    ArenaSkillBalance.CAVE_SPIDER_SKILL_POISON_AMPLIFIER,
                    false, true), soldier);
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN,
                    ArenaSkillBalance.caveSpiderMiningFatigueTicks(star),
                    ArenaSkillBalance.CAVE_SPIDER_MINING_FATIGUE_AMPLIFIER,
                    false, true));
            particleBurst(level, enemy,
                    ParticleTypes.ITEM_SLIME, 14);
            affected = true;
        }
        if (!affected) return false;
        emitGroundRing(level, soldier.position(),
                ArenaSkillBalance.CAVE_SPIDER_SKILL_RADIUS,
                ParticleTypes.ITEM_SLIME, 28);
        level.sendParticles(ParticleTypes.WITCH,
                soldier.getX(), soldier.getY() + 0.25,
                soldier.getZ(), 18,
                1.5, 0.3, 1.5, 0.025);
        double heal = arenaMaxHealth(soldier)
                * ArenaSkillBalance.caveSpiderHealRatio(
                star, soldier.getArenaMagicPower());
        soldier.heal((float) heal);
        return true;
    }

    private boolean beginArmadilloRoll() {
        AbstractArmySoldier target = nearestEnemy();
        if (target == null) return false;
        armadilloTargetId = target.getUUID();
        armadilloRollTicks =
                ArenaSkillBalance.armadilloRollTicks(
                        soldier.getArenaStar());
        armadilloHit = false;
        nextArmadilloPushTick = soldier.tickCount;
        soldier.setNoAi(true);
        soldier.setTarget(target);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.ARMADILLO_ROLL, true);
        lockStunnedMovement();
        soldier.addEffect(new MobEffectInstance(
                MobEffects.DAMAGE_RESISTANCE,
                armadilloRollTicks,
                Math.min(1, soldier.getArenaStar() - 1),
                false, true), soldier);
        return true;
    }

    private void tickArmadilloRoll(ServerLevel level) {
        Entity entity = armadilloTargetId == null
                ? null : level.getEntity(armadilloTargetId);
        AbstractArmySoldier target =
                entity instanceof AbstractArmySoldier living
                        && living.isAlive()
                        && soldier.isArenaEnemy(living)
                        ? living : nearestEnemy();
        if (target == null) {
            finishArmadilloRoll();
            return;
        }
        armadilloTargetId = target.getUUID();
        Vec3 delta = target.position().subtract(soldier.position());
        double horizontal = Math.sqrt(
                delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-5) {
            finishArmadilloRoll();
            return;
        }
        double nx = delta.x / horizontal;
        double nz = delta.z / horizontal;
        double step = Math.min(
                ArenaSkillBalance.ARMADILLO_ROLL_STEP,
                horizontal) * soldier.getArenaFreezeSpeedMultiplier();
        soldier.move(MoverType.SELF, new Vec3(
                nx * step, soldier.getDeltaMovement().y,
                nz * step));
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);

        if (MeleeReach.horizontalSurfaceDistance(
                soldier, target)
                <= ArenaSkillBalance.ARMADILLO_HIT_RADIUS
                && Math.abs(target.getY() - soldier.getY()) <= 2.0) {
            if (!armadilloHit) {
                armadilloHit = true;
                float damage = (float) (soldier.getAttributeValue(
                        Attributes.ATTACK_DAMAGE)
                        * ArenaSkillBalance.armadilloAttackMultiplier(
                        soldier.getArenaStar(),
                        soldier.getArenaMagicPower()));
                dealSkillDamage(target,
                        soldier.damageSources().mobAttack(soldier),
                        damage);
                particleBurst(level, target,
                        ParticleTypes.CRIT, 16);
            }
            if (soldier.tickCount >= nextArmadilloPushTick) {
                nextArmadilloPushTick = soldier.tickCount + 5;
                target.applyArenaKnockback(0.45, nx, nz);
            }
        }

        if (--armadilloRollTicks <= 0) {
            finishArmadilloRoll();
        }
    }

    private void finishArmadilloRoll() {
        armadilloRollTicks = 0;
        armadilloTargetId = null;
        armadilloHit = false;
        nextArmadilloPushTick = 0;
        soldier.setNoAi(false);
        soldier.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.ARMADILLO_ROLL, false);
        soldier.setTarget(nearestEnemy());
    }

    private boolean beginBlazeBarrage(ServerLevel level) {
        if (blazeBarrageActive
                || nearestEnemies(1).isEmpty()) {
            return false;
        }
        blazeBarrageActive = true;
        blazeBarrageRemainingToSpawn =
                ArenaSkillBalance.BLAZE_FIREBALL_COUNT;
        blazeBarrageFormationIndex = 0;
        nextBlazeBarrageSpawnTick = soldier.tickCount;
        nextBlazeBarrageLaunchTick = Integer.MAX_VALUE;
        blazeBarrageDamage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.blazeFireballMultiplier(
                soldier.getArenaStar(),
                soldier.getArenaMagicPower()));
        blazeBarrageVisuals.clear();
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.BLAZE_SHOOT,
                SoundSource.HOSTILE, 1.2f, 0.9f);
        return true;
    }

    private void tickBlazeBarrage(ServerLevel level) {
        if (!blazeBarrageActive) return;
        blazeBarrageVisuals.removeIf(ArenaFireball::isRemoved);
        if (blazeBarrageRemainingToSpawn > 0) {
            if (soldier.tickCount < nextBlazeBarrageSpawnTick) {
                return;
            }
            ArenaFireball visual = new ArenaFireball(
                    ModEntities.ARENA_FIREBALL.get(), level);
            visual.setOwner(soldier);
            visual.setPos(blazeFormationPoint(
                    blazeBarrageFormationIndex++));
            visual.configureBarrageVisual();
            if (!level.addFreshEntity(visual)) {
                discardBlazeBarrage();
                return;
            }
            blazeBarrageVisuals.add(visual);
            level.sendParticles(ParticleTypes.FLAME,
                    visual.getX(), visual.getY(), visual.getZ(),
                    6, 0.12, 0.12, 0.12, 0.01);
            blazeBarrageRemainingToSpawn--;
            nextBlazeBarrageSpawnTick = soldier.tickCount
                    + ArenaSkillBalance
                    .BLAZE_FORMATION_INTERVAL_TICKS;
            if (blazeBarrageRemainingToSpawn == 0) {
                nextBlazeBarrageLaunchTick = soldier.tickCount + 5;
            }
            return;
        }
        if (blazeBarrageVisuals.isEmpty()) {
            blazeBarrageActive = false;
            return;
        }
        if (soldier.tickCount < nextBlazeBarrageLaunchTick) {
            return;
        }
        List<AbstractArmySoldier> targets = nearestEnemies(1);
        if (targets.isEmpty()) {
            discardBlazeBarrage();
            return;
        }
        ArenaFireball visual = blazeBarrageVisuals.remove(0);
        launchBlazeBarrageFireball(
                level, visual, targets.get(0));
        if (blazeBarrageVisuals.isEmpty()) {
            blazeBarrageActive = false;
        } else {
            nextBlazeBarrageLaunchTick = soldier.tickCount
                    + ArenaSkillBalance
                    .BLAZE_LAUNCH_INTERVAL_TICKS;
        }
    }

    private Vec3 blazeFormationPoint(int index) {
        double lane = index
                - (ArenaSkillBalance.BLAZE_FIREBALL_COUNT - 1)
                / 2.0;
        float yaw = soldier.getYRot();
        Vec3 forward = Vec3.directionFromRotation(0.0f, yaw);
        Vec3 right = Vec3.directionFromRotation(
                0.0f, yaw + 90.0f);
        double arcHeight = 0.34
                - Math.abs(lane) * 0.11;
        return soldier.position()
                .add(0.0, soldier.getBbHeight() + 0.90
                        + arcHeight, 0.0)
                .add(right.scale(lane * 0.43))
                .add(forward.scale(0.18));
    }

    private void launchBlazeBarrageFireball(
            ServerLevel level, ArenaFireball visual,
            AbstractArmySoldier target) {
        Vec3 origin = visual.position();
        visual.discard();
        ArenaFireball fireball = new ArenaFireball(
                ModEntities.ARENA_FIREBALL.get(), level);
        fireball.setOwner(soldier);
        fireball.setPos(origin);
        fireball.configure(true, blazeBarrageDamage,
                soldier.getArenaStar());
        fireball.aimAt(target);
        Vec3 direction = target.getBoundingBox().getCenter()
                .subtract(origin);
        if (direction.lengthSqr() < 1.0E-6) {
            fireball.discard();
            return;
        }
        direction = direction.normalize();
        fireball.shoot(direction.x, direction.y, direction.z,
                0.9f, 0.0f);
        level.addFreshEntity(fireball);
    }

    private void discardBlazeBarrage() {
        blazeBarrageActive = false;
        blazeBarrageRemainingToSpawn = 0;
        blazeBarrageFormationIndex = 0;
        nextBlazeBarrageLaunchTick = Integer.MAX_VALUE;
        for (ArenaFireball visual : blazeBarrageVisuals) {
            if (!visual.isRemoved()) visual.discard();
        }
        blazeBarrageVisuals.clear();
    }

    private boolean beginDrownedTridentBarrage() {
        if (drownedTridentBarrageActive
                || nearestEnemies(1).isEmpty()) {
            return false;
        }
        int star = soldier.getArenaStar();
        drownedTridentBarrageActive = true;
        drownedTridentRemainingToSpawn =
                ArenaSkillBalance.DROWNED_TRIDENT_COUNT;
        drownedTridentFormationIndex = 0;
        nextDrownedTridentSpawnTick = soldier.tickCount;
        nextDrownedTridentLaunchTick = Integer.MAX_VALUE;
        drownedTridentDamage = (float) ArenaSkillBalance
                .drownedTridentDamage(soldier.getAttributeValue(
                        Attributes.ATTACK_DAMAGE));
        drownedTridentStunTicks =
                ArenaSkillBalance.drownedTridentStunTicks(star);
        drownedTridentDebuffTicks =
                ArenaSkillBalance.drownedTridentDebuffTicks(star);
        drownedTridentVisuals.clear();
        return true;
    }

    private void tickDrownedTridentBarrage(ServerLevel level) {
        if (!drownedTridentBarrageActive) return;
        drownedTridentVisuals.removeIf(ArenaTrident::isRemoved);
        aimDrownedTridentVisuals();
        if (drownedTridentRemainingToSpawn > 0) {
            if (soldier.tickCount < nextDrownedTridentSpawnTick) {
                return;
            }
            List<AbstractArmySoldier> targets = nearestEnemies(1);
            if (targets.isEmpty()) {
                discardDrownedTridentBarrage();
                return;
            }
            ArenaTrident visual = new ArenaTrident(
                    ModEntities.ARENA_SKILL_TRIDENT.get(), level);
            visual.setOwner(soldier);
            visual.setPos(drownedTridentFormationPoint(
                    drownedTridentFormationIndex++));
            visual.configureBarrageVisual();
            aimDrownedTrident(visual, targets.get(0));
            if (!level.addFreshEntity(visual)) {
                discardDrownedTridentBarrage();
                return;
            }
            drownedTridentVisuals.add(visual);
            level.sendParticles(ParticleTypes.BUBBLE,
                    visual.getX(), visual.getY(), visual.getZ(),
                    6, 0.1, 0.1, 0.1, 0.02);
            drownedTridentRemainingToSpawn--;
            nextDrownedTridentSpawnTick = soldier.tickCount
                    + ArenaSkillBalance
                    .DROWNED_TRIDENT_FORMATION_INTERVAL_TICKS;
            if (drownedTridentRemainingToSpawn == 0) {
                nextDrownedTridentLaunchTick = soldier.tickCount + 5;
            }
            return;
        }
        if (drownedTridentVisuals.isEmpty()) {
            drownedTridentBarrageActive = false;
            return;
        }
        if (soldier.tickCount < nextDrownedTridentLaunchTick) {
            return;
        }
        List<AbstractArmySoldier> targets = nearestEnemies(1);
        if (targets.isEmpty()) {
            discardDrownedTridentBarrage();
            return;
        }
        ArenaTrident visual = drownedTridentVisuals.remove(0);
        aimDrownedTrident(visual, targets.get(0));
        launchDrownedTrident(level, visual, targets.get(0));
        if (drownedTridentVisuals.isEmpty()) {
            drownedTridentBarrageActive = false;
        } else {
            nextDrownedTridentLaunchTick = soldier.tickCount
                    + ArenaSkillBalance
                    .DROWNED_TRIDENT_LAUNCH_INTERVAL_TICKS;
        }
    }

    private Vec3 drownedTridentFormationPoint(int index) {
        float yaw = soldier.getYRot()
                + index * 120.0f;
        Vec3 radial = Vec3.directionFromRotation(0.0f, yaw);
        return soldier.position()
                .add(radial.scale(0.88))
                .add(0.0, soldier.getBbHeight() * 0.55, 0.0);
    }

    private void aimDrownedTridentVisuals() {
        if (drownedTridentVisuals.isEmpty()) return;
        List<AbstractArmySoldier> targets = nearestEnemies(1);
        if (targets.isEmpty()) return;
        for (ArenaTrident visual : drownedTridentVisuals) {
            aimDrownedTrident(visual, targets.get(0));
        }
    }

    private void aimDrownedTrident(
            ArenaTrident trident, AbstractArmySoldier target) {
        Vec3 direction = target.getBoundingBox().getCenter()
                .subtract(trident.position());
        if (direction.lengthSqr() < 1.0E-6) return;
        direction = direction.normalize();
        trident.shoot(direction.x, direction.y, direction.z,
                0.0f, 0.0f);
        trident.setDeltaMovement(Vec3.ZERO);
    }

    private void launchDrownedTrident(
            ServerLevel level, ArenaTrident visual,
            AbstractArmySoldier target) {
        Vec3 origin = visual.position();
        visual.discard();
        ArenaTrident trident = new ArenaTrident(
                ModEntities.ARENA_SKILL_TRIDENT.get(), level);
        trident.setOwner(soldier);
        trident.setPos(origin);
        trident.configureSkill(drownedTridentDamage,
                drownedTridentStunTicks, drownedTridentDebuffTicks);
        trident.aimAt(target);
        Vec3 direction = target.getBoundingBox().getCenter()
                .subtract(origin);
        if (direction.lengthSqr() < 1.0E-6) {
            trident.discard();
            return;
        }
        direction = direction.normalize();
        trident.shoot(direction.x, direction.y, direction.z,
                1.25f, 0.0f);
        level.playSound(null, origin.x, origin.y, origin.z,
                SoundEvents.TRIDENT_THROW.value(),
                SoundSource.HOSTILE, 0.9f, 1.0f);
        level.addFreshEntity(trident);
    }

    private void discardDrownedTridentBarrage() {
        drownedTridentBarrageActive = false;
        drownedTridentRemainingToSpawn = 0;
        drownedTridentFormationIndex = 0;
        nextDrownedTridentLaunchTick = Integer.MAX_VALUE;
        for (ArenaTrident visual : drownedTridentVisuals) {
            if (!visual.isRemoved()) visual.discard();
        }
        drownedTridentVisuals.clear();
    }

    private void snifferDig(ServerLevel level) {
        BlockPos digPos = soldier.getOnPos();
        BlockState ground = level.getBlockState(digPos.below());
        level.sendParticles(new BlockParticleOption(
                        ParticleTypes.BLOCK, ground),
                soldier.getX(), soldier.getY() + 0.1, soldier.getZ(),
                48, 1.4, 0.15, 1.4, 0.08);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.SNIFFER_DIGGING,
                SoundSource.HOSTILE, 1.2f, 1.0f);

        if (soldier.getArenaStar() >= 3) {
            if (!snifferDominanceActive) {
                snifferDominanceActive =
                        ArenaMatchManager.activateSnifferDominance(
                                soldier);
            }
            particleBurst(level, soldier,
                    ParticleTypes.HAPPY_VILLAGER, 30);
            return;
        }

        int roll = soldier.getRandom().nextInt(100);
        if (roll < 10) {
            grantSnifferEquipment();
        } else if (roll < 30) {
            grantSnifferGold();
        } else if (roll < 60) {
            grantSnifferBuffs(level);
        } else {
            applySnifferDebuffs(level);
        }
    }

    private void applySnifferShield(ServerLevel level, int star) {
        double ratio = star >= 3
                ? ArenaSkillBalance
                .SNIFFER_THREE_STAR_SHIELD_RATIO
                : ArenaSkillBalance.SNIFFER_SHIELD_RATIO;
        for (AbstractArmySoldier ally :
                arenaAllies(Integer.MAX_VALUE)) {
            double shield = arenaMaxHealth(ally) * ratio;
            ally.grantArenaShieldFrom(soldier, shield);
            particleBurst(level, ally,
                    ParticleTypes.ENCHANT, 16);
        }
    }

    private void tickElderGuardianLaser(ServerLevel level) {
        if (!elderGuardianLaserActive
                || !(soldier instanceof ArenaElderGuardian guardian)) {
            return;
        }
        Entity locked = elderGuardianLaserTargetId == null
                ? null : level.getEntity(elderGuardianLaserTargetId);
        if (!(locked instanceof AbstractArmySoldier target)
                || !target.isAlive() || target.isRemoved()
                || !soldier.isArenaEnemy(target)) {
            guardian.stopSkillLaser();
            elderGuardianLaserActive = false;
            elderGuardianLaserTargetId = null;
            nextElderGuardianLaserTick = 0;
            soldier.setTarget(null);
            return;
        }
        if (!ArenaMatchManager.isArenaCombatActive(soldier)) {
            return;
        }
        soldier.setTarget(target);
        boolean damagePulse = soldier.tickCount
                >= nextElderGuardianLaserTick;
        if (guardian.tickSkillLaser(damagePulse)
                && damagePulse) {
            nextElderGuardianLaserTick = soldier.tickCount
                    + ArenaSkillBalance
                    .ELDER_GUARDIAN_DAMAGE_INTERVAL_TICKS;
        }
    }

    private void tickTurtleRegeneration() {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.TURTLE_RESILIENCE
                || !ArenaMatchManager.isArenaCombatActive(soldier)) {
            return;
        }
        if (nextTurtleRegenTick == 0) {
            nextTurtleRegenTick = soldier.tickCount
                    + ArenaSkillBalance.TURTLE_REGEN_INTERVAL_TICKS;
            return;
        }
        if (soldier.tickCount < nextTurtleRegenTick) return;
        nextTurtleRegenTick = soldier.tickCount
                + ArenaSkillBalance.TURTLE_REGEN_INTERVAL_TICKS;
        float missing = Math.max(0.0f,
                arenaMaxHealth(soldier)
                        - arenaCurrentHealth(soldier));
        double ratio = ArenaSkillBalance.turtleRegenRatio(
                soldier.getArenaStar(), soldier.getArenaMagicPower());
        float healed = (float) (missing * ratio);
        if (healed <= 0.0f) return;
        soldier.heal(healed);
        if (soldier.level() instanceof ServerLevel level) {
            particleBurst(level, soldier, ParticleTypes.HEART, 10);
        }
    }

    private void tickRavagerBloodShield() {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.RAVAGER_KNOCKUP_HEAL
                || !ArenaMatchManager.isArenaCombatActive(soldier)) {
            return;
        }
        if (nextRavagerBloodShieldTick == 0) {
            nextRavagerBloodShieldTick = soldier.tickCount
                    + ArenaSkillBalance
                    .RAVAGER_BLOOD_SHIELD_INTERVAL_TICKS;
            return;
        }
        if (soldier.tickCount < nextRavagerBloodShieldTick) {
            return;
        }
        nextRavagerBloodShieldTick = soldier.tickCount
                + ArenaSkillBalance
                .RAVAGER_BLOOD_SHIELD_INTERVAL_TICKS;

        float maxHealth = arenaMaxHealth(soldier);
        float currentHealth = arenaCurrentHealth(soldier);
        if (maxHealth <= 0.0f
                || currentHealth <= maxHealth
                * ArenaSkillBalance
                .RAVAGER_BLOOD_SHIELD_MIN_HEALTH_RATIO) {
            return;
        }

        float healthCost = (float) (maxHealth
                * ArenaSkillBalance
                .RAVAGER_BLOOD_SHIELD_HEALTH_RATIO);
        soldier.setArenaCurrentHealth(
                Math.max(0.0f, currentHealth - healthCost));
        soldier.addArenaShield(healthCost
                * ArenaSkillBalance
                .ravagerBloodShieldMultiplier(
                        soldier.getArenaStar()));
        soldier.syncArenaHealthMirror();
        if (soldier.level() instanceof ServerLevel level) {
            particleBurst(level, soldier,
                    ParticleTypes.SOUL_FIRE_FLAME, 8);
        }
    }

    private void grantSnifferEquipment() {
        ArenaPlayerState owner =
                ArenaMatchManager.combatOwner(soldier);
        if (owner == null) return;
        List<ArenaEquipment> components = new ArrayList<>();
        for (ArenaEquipment equipment : ArenaEquipment.values()) {
            if (equipment.component()) components.add(equipment);
        }
        if (components.isEmpty()) return;
        owner.addEquipment(components.get(
                soldier.getRandom().nextInt(components.size())));
    }

    private void grantSnifferGold() {
        ArenaPlayerState owner =
                ArenaMatchManager.combatOwner(soldier);
        if (owner != null) owner.addGold(3);
    }

    private boolean wanderingTraderSupply(ServerLevel level) {
        int targetLimit = ArenaSkillBalance
                .wanderingTraderTargets(soldier.getArenaStar());
        List<AbstractArmySoldier> targets = new ArrayList<>();
        // 临时装备保留在棋子装备栏中，因此后续施法会继续给仍有空槽的目标叠加。
        for (AbstractArmySoldier ally :
                arenaAllies(Integer.MAX_VALUE)) {
            ArenaPiece piece =
                    ArenaMatchManager.combatPieceFor(ally);
            if (piece != null && piece.canEquip()) {
                targets.add(ally);
                if (targets.size() >= targetLimit) break;
            }
        }
        if (targets.isEmpty()) return false;

        List<ArenaEquipment> completed =
                ArenaEquipment.craftableCompleted();
        if (completed.isEmpty()) return false;

        boolean equippedAny = false;
        double shieldRatio = ArenaSkillBalance
                .wanderingTraderShieldRatio(
                        soldier.getArenaStar(),
                        soldier.getArenaMagicPower());
        for (AbstractArmySoldier target : targets) {
            ArenaEquipment item = completed.get(
                    soldier.getRandom().nextInt(completed.size()));
            if (!ArenaMatchManager.grantTemporaryCompletedArenaItem(
                    target, item)) {
                continue;
            }
            double shield = arenaMaxHealth(target) * shieldRatio;
            target.grantArenaShieldFrom(soldier, shield);
            emitParticleLine(level,
                    soldier.getBoundingBox().getCenter(),
                    target.getBoundingBox().getCenter(),
                    ParticleTypes.HAPPY_VILLAGER, 10);
            particleBurst(level, target,
                    ParticleTypes.HAPPY_VILLAGER, 16);
            equippedAny = true;
        }
        return equippedAny;
    }

    private boolean magmaCubeBlessing(ServerLevel level) {
        List<AbstractArmySoldier> allies =
                randomArenaAllies(3);
        List<AbstractArmySoldier> enemies =
                randomArenaEnemies(3);
        for (AbstractArmySoldier ally : allies) {
            ally.addEffect(new MobEffectInstance(
                    MobEffects.FIRE_RESISTANCE,
                    ArenaSkillBalance.MAGMA_CUBE_BUFF_TICKS,
                    ArenaSkillBalance
                            .MAGMA_CUBE_EFFECT_AMPLIFIER,
                    false, true), soldier);
            particleBurst(level, ally,
                    ParticleTypes.FLAME, 12);
        }
        for (AbstractArmySoldier enemy : enemies) {
            enemy.addEffect(new MobEffectInstance(
                    ModEffects.GRIEVOUS_WOUND,
                    ArenaSkillBalance.MAGMA_CUBE_DEBUFF_TICKS,
                    ArenaSkillBalance
                            .MAGMA_CUBE_EFFECT_AMPLIFIER,
                    false, true), soldier);
            particleBurst(level, enemy,
                    ParticleTypes.LAVA, 10);
        }
        return !allies.isEmpty() || !enemies.isEmpty();
    }

    private boolean witherSkullBarrage(ServerLevel level) {
        if (!(soldier instanceof ArenaWither wither)) {
            return false;
        }
        List<AbstractArmySoldier> enemies =
                nearestEnemies(Integer.MAX_VALUE);
        if (enemies.isEmpty()) return false;

        float skullDamage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.witherSkullAttackMultiplier(
                        soldier.getArenaStar(),
                        soldier.getArenaMagicPower()));
        boolean fired = wither.fireSkillSkulls(
                enemies, skullDamage);
        boolean drained = false;
        int star = soldier.getArenaStar();
        for (AbstractArmySoldier enemy : enemies) {
            MobEffectInstance witherEffect =
                    enemy.getEffect(MobEffects.WITHER);
            if (witherEffect == null) continue;
            double ratio = ArenaSkillBalance
                    .witherTrueDamageRatio(
                            witherEffect.getAmplifier(), star,
                            soldier.getArenaMagicPower());
            float trueDamage = (float) (arenaMaxHealth(enemy)
                    * ratio);
            float dealt = dealTrueSkillDamage(enemy,
                    soldier.damageSources().indirectMagic(
                            soldier, soldier),
                    trueDamage);
            if (dealt <= 0.0f) continue;
            soldier.heal(dealt);
            particleBurst(level, enemy,
                    ParticleTypes.SOUL_FIRE_FLAME, 18);
            drained = true;
        }
        return fired || drained;
    }

    private void tickGiantArmadillo(ServerLevel level) {
        soldier.setNoAi(true);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.ARMADILLO_ROLL, true);
        if (soldier.getArenaSkillCharge()
                >= soldier.getArenaSkillRequired()
                && castSkill(level)) {
            soldier.resetArenaSkillCharge();
            tickTaunt();
        }

        Entity selected = giantArmadilloTargetId == null
                ? null : level.getEntity(giantArmadilloTargetId);
        AbstractArmySoldier target =
                selected instanceof AbstractArmySoldier living
                        && living.isAlive()
                        && !living.isRemoved()
                        && soldier.isArenaEnemy(living)
                        ? living : farthestEnemy();
        if (target == null) {
            giantArmadilloTargetId = null;
            soldier.setTarget(null);
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.ARMADILLO_MOVING, false);
            // 滚动结束不等于悬停：无目标且在空中时必须恢复正常重力，
            // 并立即清除可能残留的向上速度。
            soldier.setNoGravity(false);
            if (!soldier.isInWater()
                    && !soldier.onClimbable()) {
                Vec3 delta = soldier.getDeltaMovement();
                soldier.setDeltaMovement(
                        delta.x * 0.8,
                        Math.min(delta.y, -0.08),
                        delta.z * 0.8);
                soldier.hasImpulse = true;
                soldier.move(MoverType.SELF,
                        new Vec3(0.0, -0.08, 0.0));
            }
            return;
        }
        giantArmadilloTargetId = target.getUUID();
        soldier.setTarget(target);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.ARMADILLO_MOVING, true);
        Vec3 delta = target.position().subtract(soldier.position());
        double horizontal = Math.sqrt(
                delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-5) {
            tryGiantArmadilloHit(level, target);
            return;
        }
        double nx = delta.x / horizontal;
        double nz = delta.z / horizontal;
        double reach = MeleeReach.horizontalReach(
                soldier, target, 2.8);
        if (MeleeReach.horizontalSurfaceDistance(
                soldier, target) <= reach
                && Math.abs(target.getY() - soldier.getY()) <= 1.5
                && soldier.hasMeleeLineOfSight(target)) {
            if (tryGiantArmadilloHit(level, target)) {
                return;
            }
        }

        double step = Math.min(
                ArenaSkillBalance.GIANT_ARMADILLO_ROLL_STEP,
                horizontal) * soldier.getArenaFreezeSpeedMultiplier();
        Vec3 before = soldier.position();
        soldier.move(MoverType.SELF, new Vec3(
                nx * step, soldier.getDeltaMovement().y,
                nz * step));
        if (soldier.position().distanceToSqr(before) < 1.0E-6) {
            giantArmadilloTargetId = null;
        }
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
    }

    private boolean tryGiantArmadilloHit(
            ServerLevel level, AbstractArmySoldier target) {
        if (soldier.tickCount < nextGiantArmadilloAttackTick) {
            return false;
        }
        if (!soldier.doHurtTarget(target)) return false;
        nextGiantArmadilloAttackTick = soldier.tickCount
                + Math.max(1,
                soldier.getArenaMeleeAttackIntervalTicks());
        giantArmadilloTargetId = null;
        particleBurst(level, target, ParticleTypes.CRIT, 14);
        return true;
    }

    @Nullable
    private AbstractArmySoldier farthestEnemy() {
        List<AbstractArmySoldier> enemies =
                nearestEnemies(Integer.MAX_VALUE);
        return enemies.isEmpty()
                ? null : enemies.get(enemies.size() - 1);
    }

    private void applyGiantArmadilloMomentum(ServerLevel level) {
        giantArmadilloStacks = Math.min(
                ArenaSkillBalance.GIANT_ARMADILLO_MAX_STACKS,
                giantArmadilloStacks + 1);
        double bonus = giantArmadilloStacks
                * ArenaSkillBalance.GIANT_ARMADILLO_STAT_STEP;
        setAttributeModifier(Attributes.ATTACK_DAMAGE,
                GIANT_ARMADILLO_ATTACK_MODIFIER, bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setAttributeModifier(Attributes.MOVEMENT_SPEED,
                GIANT_ARMADILLO_MOVEMENT_MODIFIER, bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        soldier.setArenaSkillAttackSpeedBonus(bonus);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.GIANT_ARMADILLO_MOMENTUM, true);
        applyArenaGrowthScale();
        particleBurst(level, soldier,
                ParticleTypes.HAPPY_VILLAGER, 20);
    }

    private void grantSnifferBuffs(ServerLevel level) {
        int star = soldier.getArenaStar();
        int duration = ArenaSkillBalance.snifferBuffTicks(star);
        List<net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect>>
                choices = List.of(
                MobEffects.MOVEMENT_SPEED,
                MobEffects.DIG_SPEED,
                MobEffects.DAMAGE_RESISTANCE,
                MobEffects.REGENERATION,
                MobEffects.DAMAGE_BOOST,
                MobEffects.ABSORPTION,
                MobEffects.FIRE_RESISTANCE,
                MobEffects.JUMP);
        List<AbstractArmySoldier> allies = arenaAllies(
                ArenaSkillBalance.SNIFFER_TARGET_LIMIT);
        for (AbstractArmySoldier ally : allies) {
            var effect = choices.get(
                    soldier.getRandom().nextInt(choices.size()));
            ally.addEffect(new MobEffectInstance(
                    effect, duration,
                    soldier.getRandom().nextInt(2),
                    false, true), soldier);
            ally.arenaSkills().applySupportAttackSpeedBonus(
                    ArenaSkillBalance.SNIFFER_ATTACK_SPEED_BONUS,
                    duration);
            particleBurst(level, ally,
                    ParticleTypes.HAPPY_VILLAGER, 12);
        }
    }

    private void applySnifferDebuffs(ServerLevel level) {
        int duration = ArenaSkillBalance.snifferDebuffTicks(
                soldier.getArenaStar());
        for (AbstractArmySoldier enemy : nearestEnemies(
                ArenaSkillBalance.SNIFFER_TARGET_LIMIT)) {
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.WEAKNESS, duration,
                    ArenaSkillBalance.SNIFFER_DEBUFF_AMPLIFIER,
                    false, true));
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, duration,
                    ArenaSkillBalance.SNIFFER_DEBUFF_AMPLIFIER,
                    false, true));
            enemy.addEffect(new MobEffectInstance(
                    MobEffects.DIG_SLOWDOWN, duration,
                    ArenaSkillBalance.SNIFFER_DEBUFF_AMPLIFIER,
                    false, true));
            particleBurst(level, enemy, ParticleTypes.WITCH, 12);
        }
    }

    private List<AbstractArmySoldier> arenaAllies(int limit) {
        List<AbstractArmySoldier> allies = new ArrayList<>();
        allies.add(soldier);
        List<AbstractArmySoldier> others =
                soldier.level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        soldier.getBoundingBox().inflate(64.0),
                        other -> other != soldier
                                && other.isAlive()
                                && other.isArenaUnit()
                                && other.getArenaSide()
                                == soldier.getArenaSide()
                                && !soldier.isArenaEnemy(other)
                                && !other.getTags().contains(
                                "monster_conquest_arena_summon"));
        others.sort(Comparator.comparingDouble(
                soldier::distanceToSqr));
        for (AbstractArmySoldier other : others) {
            if (allies.size() >= limit) break;
            allies.add(other);
        }
        return allies;
    }

    private List<AbstractArmySoldier> randomArenaAllies(int limit) {
        return randomSample(
                arenaAllies(Integer.MAX_VALUE), limit);
    }

    private List<AbstractArmySoldier> randomArenaEnemies(int limit) {
        return randomSample(
                nearestEnemies(Integer.MAX_VALUE), limit);
    }

    private List<AbstractArmySoldier> randomSample(
            List<AbstractArmySoldier> source, int limit) {
        if (source.isEmpty() || limit <= 0) return List.of();
        List<AbstractArmySoldier> pool = new ArrayList<>(source);
        int count = Math.min(limit, pool.size());
        for (int i = 0; i < count; i++) {
            int selected = i + soldier.getRandom()
                    .nextInt(pool.size() - i);
            AbstractArmySoldier value = pool.get(i);
            pool.set(i, pool.get(selected));
            pool.set(selected, value);
        }
        return List.copyOf(pool.subList(0, count));
    }

    @Nullable
    private AbstractArmySoldier nearestAlly(boolean fallbackSelf) {
        List<AbstractArmySoldier> others =
                soldier.level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        soldier.getBoundingBox().inflate(64.0),
                        other -> other != soldier
                                && other.isAlive()
                                && other.isArenaUnit()
                                && other.getArenaSide()
                                == soldier.getArenaSide()
                                && !soldier.isArenaEnemy(other)
                                && !other.getTags().contains(
                                "monster_conquest_arena_summon"));
        others.sort(Comparator.comparingDouble(
                soldier::distanceToSqr));
        if (!others.isEmpty()) return others.get(0);
        return fallbackSelf ? soldier : null;
    }

    public void grantNearestAllyShield(
            double maxHealthRatio, boolean fallbackSelf) {
        AbstractArmySoldier ally =
                nearestAlly(fallbackSelf);
        if (ally == null) return;
        double shield = arenaMaxHealth(ally)
                * Math.max(0.0, maxHealthRatio);
        ally.grantArenaShieldFrom(soldier, shield);
        if (soldier.level() instanceof ServerLevel level) {
            particleBurst(level, ally,
                    ParticleTypes.ENCHANT, 20);
        }
    }

    private ResourceLocation skillSourceId(String suffix) {
        return ResourceLocation.fromNamespaceAndPath(
                Monster_conquest.MODID,
                "arena_skill/" + suffix + "/"
                        + soldier.getUUID());
    }

    private boolean endermiteSilenceStrike(ServerLevel level) {
        List<AbstractArmySoldier> enemies =
                nearestEnemies(Integer.MAX_VALUE);
        if (enemies.isEmpty()) return false;
        AbstractArmySoldier target = enemies.get(enemies.size() - 1);
        Vec3 fromTarget = soldier.position()
                .subtract(target.position());
        Vec3 horizontal = new Vec3(
                fromTarget.x, 0.0, fromTarget.z);
        if (horizontal.lengthSqr() < 1.0E-6) {
            horizontal = new Vec3(1.0, 0.0, 0.0);
        }
        Vec3 destination = target.position().add(
                horizontal.normalize().scale(1.1));
        if (!level.noCollision(soldier,
                soldier.getBoundingBox().move(
                        destination.subtract(soldier.position())))) {
            destination = target.position();
        }
        level.sendParticles(ParticleTypes.PORTAL,
                soldier.getX(), soldier.getY() + 0.4, soldier.getZ(),
                20, 0.3, 0.5, 0.3, 0.08);
        soldier.teleportWithMount(destination.x,
                destination.y, destination.z, false);
        soldier.setTarget(target);
        soldier.arenaTraits().onDashOrTeleport();
        level.playSound(null, destination.x, destination.y,
                destination.z, SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.HOSTILE, 1.0f, 1.2f);

        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.endermiteAttackRatio(
                soldier.getArenaStar(),
                soldier.getArenaMagicPower()));
        dealTrueSkillDamage(target,
                soldier.damageSources().indirectMagic(soldier, soldier),
                damage);
        target.arenaSkills().applySilence(
                ArenaSkillBalance.endermiteSilenceTicks(
                        soldier.getArenaStar()));
        particleBurst(level, target, ParticleTypes.REVERSE_PORTAL, 16);
        return true;
    }

    private boolean throwWitchLingeringPotion(ServerLevel level) {
        AbstractArmySoldier target = null;
        for (AbstractArmySoldier enemy
                : nearestEnemies(Integer.MAX_VALUE)) {
            if (soldier.hasClearShot(enemy)) {
                target = enemy;
                break;
            }
        }
        if (target == null) return false;
        soldier.setTarget(target);
        soldier.getLookControl().setLookAt(target, 40.0f, 40.0f);
        WitchPotionEntity potion = new WitchPotionEntity(level, soldier);
        potion.configure(WitchPotionEntity.Kind.LINGERING,
                soldier.getTier().ordinal(),
                soldier.getCommanderUuid().orElse(null),
                soldier.getFactionId(),
                WitchPotionEntity.Caster.WITCH);
        potion.configureArenaLingeringSkill(
                soldier.getAttributeValue(Attributes.ATTACK_DAMAGE));
        potion.setIntendedTarget(target.getUUID());

        Vec3 start = new Vec3(
                soldier.getX(), soldier.getEyeY() - 0.2,
                soldier.getZ());
        potion.setPos(start);
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        double horizontal = Math.sqrt(
                Math.pow(target.getX() - start.x, 2.0)
                        + Math.pow(target.getZ() - start.z, 2.0));
        double leadTicks = horizontal / WitchPotionEntity.THROW_SPEED * 0.9;
        Vec3 destination = targetCenter.add(new Vec3(
                target.getDeltaMovement().x * leadTicks,
                0.0,
                target.getDeltaMovement().z * leadTicks));
        Vec3 direction = destination.subtract(start);
        if (direction.lengthSqr() < 1.0E-6) return false;
        direction = direction.normalize();
        potion.setNoGravity(true);
        potion.shoot(direction.x, direction.y, direction.z,
                (float) WitchPotionEntity.THROW_SPEED, 0.0f);
        level.playSound(null, start.x, start.y, start.z,
                SoundEvents.WITCH_THROW, SoundSource.HOSTILE,
                1.0f, 0.92f);
        level.sendParticles(ParticleTypes.WITCH,
                start.x, start.y, start.z,
                10, 0.22, 0.18, 0.22, 0.025);
        soldier.swing(InteractionHand.MAIN_HAND, true);
        return level.addFreshEntity(potion);
    }

    public void spawnWitchLingeringPotion(
            ServerLevel level, Vec3 impact,
            double pulseDamage, double radius, int durationTicks) {
        Vec3 from = impact.add(0.0, 0.5, 0.0);
        Vec3 to = impact.subtract(0.0, 8.0, 0.0);
        HitResult ground = level.clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, soldier));
        Vec3 origin = ground.getType() == HitResult.Type.MISS
                ? impact : ground.getLocation().add(0.0, 0.06, 0.0);
        ArenaLingeringPotion potion =
                new ArenaLingeringPotion(
                        ModEntities.ARENA_LINGERING_POTION.get(),
                        level);
        potion.setPos(origin);
        potion.configure(soldier, pulseDamage,
                radius, durationTicks);
        if (!level.addFreshEntity(potion)) return;
        level.sendParticles(ParticleTypes.WITCH,
                origin.x, origin.y + 0.10, origin.z,
                28, radius * 0.45, 0.15, radius * 0.45, 0.02);
        level.sendParticles(ParticleTypes.ITEM_SLIME,
                origin.x, origin.y + 0.08, origin.z,
                12, radius * 0.35, 0.08, radius * 0.35, 0.015);
    }

    private boolean summonArenaWolf(ServerLevel level) {
        if (!(ModEntities.ARENA_WOLF.get().create(level)
                instanceof ArenaWolf wolf)) {
            return false;
        }
        UUID matchId = soldier.getArenaMatchId();
        if (matchId == null) return false;
        Vec3 spot = findSummonSpot(wolf);
        if (spot == null) return false;

        wolf.moveTo(spot.x, spot.y, spot.z,
                soldier.getYRot(), 0.0f);
        wolf.setFactionId(soldier.getFactionId());
        soldier.getCommanderUuid().ifPresent(wolf::setCommanderUuid);
        wolf.setSquadId(soldier.getSquadId());
        wolf.enterArenaMode(matchId, soldier.getArenaSide(),
                soldier.getArenaBoardIndex());
        wolf.copyArenaSourcePiece(soldier);
        wolf.arenaTraits().configureSummon(
                soldier.arenaTraits());
        wolf.setArenaStar(soldier.getArenaStar());
        wolf.setArenaEquipmentIds(List.of());
        wolf.applyArenaEquipmentAppearance(
                null, null, null, null, false);
        wolf.configureArenaSkill(null, Integer.MAX_VALUE);
        copyArenaCombatStats(soldier, wolf);
        wolf.arenaTraits().applySummonInheritance();
        wolf.addTag("monster_conquest_arena_summon");
        if (!level.addFreshEntity(wolf)) return false;
        if (!ArenaMatchManager.registerArenaCombatEntity(wolf)) {
            wolf.discard();
            return false;
        }

        summonIds.add(wolf.getUUID());
        soldier.arenaTraits().onSummonCreated();
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE, 1.0f, 1.1f);
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.WOLF_AMBIENT,
                SoundSource.HOSTILE, 0.9f, 1.0f);
        level.sendParticles(ParticleTypes.POOF,
                spot.x, spot.y + 0.3, spot.z,
                12, 0.35, 0.3, 0.35, 0.02);
        return true;
    }

    private void copyArenaCombatStats(AbstractArmySoldier source,
                                      AbstractArmySoldier target) {
        double maxHealth = Math.max(1.0,
                source.getArenaDisplayMaxHealth());
        target.setArenaDisplayMaxHealth(maxHealth);
        target.setArenaCurrentHealth(maxHealth);
        target.setArenaRangedDamage(source.getAttributeValue(
                Attributes.ATTACK_DAMAGE));
        target.setArenaAttackSpeed(source.getArenaEffectiveAttackSpeed());
        target.setArenaMagicResistance(
                source.getArenaEffectiveMagicResistance());
        target.setArenaMagicPower(source.getArenaMagicPower());
        setAttributeBaseIfPresent(target, Attributes.MAX_HEALTH,
                Math.min(1024.0, maxHealth));
        setAttributeBaseIfPresent(target, Attributes.ATTACK_DAMAGE,
                source.getAttributeValue(Attributes.ATTACK_DAMAGE));
        setAttributeBaseIfPresent(target, Attributes.ARMOR,
                source.getAttributeValue(Attributes.ARMOR));
        setAttributeBaseIfPresent(target, Attributes.MOVEMENT_SPEED,
                source.getAttributeValue(Attributes.MOVEMENT_SPEED));
        target.syncArenaHealthMirror();
    }

    private static void setAttributeBaseIfPresent(
            AbstractArmySoldier entity,
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private boolean ravagerKnockupAndHeal(ServerLevel level) {
        boolean affected = false;
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            double edgeDistance =
                    MeleeReach.horizontalSurfaceDistance(
                            soldier, enemy);
            if (edgeDistance > ArenaSkillBalance.RAVAGER_KNOCKUP_RADIUS
                    || Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            affected = true;
            float skillDamage = (float) (
                    soldier.getAttributeValue(Attributes.ATTACK_DAMAGE)
                            * ArenaSkillBalance
                            .ravagerAttackMultiplier(
                                    soldier.getArenaMagicPower()));
            dealSkillDamage(enemy,
                    soldier.damageSources().mobAttack(soldier),
                    skillDamage);
            if (!enemy.isAlive()) {
                particleBurst(level, enemy,
                        ParticleTypes.CRIT, 12);
                continue;
            }
            Vec3 away = enemy.position().subtract(soldier.position());
            double horizontal = Math.sqrt(
                    away.x * away.x + away.z * away.z);
            Vec3 outward = horizontal < 1.0E-5
                    ? Vec3.ZERO
                    : new Vec3(away.x / horizontal, 0.0,
                    away.z / horizontal);
            enemy.arenaSkills().applyStunWithLaunch(
                    ArenaSkillBalance.ravagerStunTicks(
                            soldier.getArenaStar()),
                    outward.scale(0.55).add(0.0, 0.65, 0.0));
            particleBurst(level, enemy, ParticleTypes.CRIT, 12);
        }
        if (!affected) return false;
        ravagerSlamImpact(level);
        double missingHealth = Math.max(0.0,
                soldier.getArenaDisplayMaxHealth()
                        - soldier.getArenaCurrentHealth());
        if (missingHealth > 0.0) {
            double beforeHeal = soldier.getArenaCurrentHealth();
            soldier.heal((float) (missingHealth
                    * ArenaSkillBalance.ravagerMissingHealthRatio(
                    soldier.getArenaStar(),
                    soldier.getArenaMagicPower())));
            double healed = Math.max(0.0,
                    soldier.getArenaCurrentHealth() - beforeHeal);
            if (healed > 0.0) {
                soldier.grantArenaShield(healed);
            }
        }
        return true;
    }

    private void ravagerSlamImpact(ServerLevel level) {
        BlockPos impact = soldier.getOnPos();
        BlockState ground = level.getBlockState(impact.below());
        level.levelEvent(LevelEvent.PARTICLES_SMASH_ATTACK,
                impact, 600);
        level.sendParticles(new BlockParticleOption(
                        ParticleTypes.BLOCK, ground),
                soldier.getX(), soldier.getY() + 0.20,
                soldier.getZ(), 96,
                2.8, 0.25, 2.8, 0.22);
        for (int index = 0; index < 16; index++) {
            double angle = index * Math.PI * 2.0 / 16.0;
            double radius = 0.9 + (index & 1) * 1.15;
            level.sendParticles(new BlockParticleOption(
                            ParticleTypes.BLOCK, ground),
                    soldier.getX() + Math.cos(angle) * radius,
                    soldier.getY() + 0.12,
                    soldier.getZ() + Math.sin(angle) * radius,
                    5, 0.22, 0.12, 0.22, 0.16);
        }
        level.sendParticles(ParticleTypes.CLOUD,
                soldier.getX(), soldier.getY() + 0.25,
                soldier.getZ(), 28,
                2.3, 0.12, 2.3, 0.10);
        level.sendParticles(ParticleTypes.EXPLOSION,
                soldier.getX(), soldier.getY() + 0.25,
                soldier.getZ(), 2,
                1.4, 0.0, 1.4, 0.0);
        level.playSound(null,
                soldier.getX(), soldier.getY(), soldier.getZ(),
                SoundEvents.MACE_SMASH_GROUND,
                SoundSource.HOSTILE, 1.35f, 0.72f);
        level.playSound(null,
                soldier.getX(), soldier.getY(), soldier.getZ(),
                SoundEvents.RAVAGER_ROAR,
                SoundSource.HOSTILE, 1.0f, 0.82f);
    }

    private void beginPolarForm(ServerLevel level) {
        if (polarFormActive) return;
        int star = soldier.getArenaStar();
        double magicPower = soldier.getArenaMagicPower();
        polarHealthBonus = ArenaSkillBalance.polarBearHealthBonus(
                star, magicPower);
        polarBaseMaxHealth = soldier.getArenaDisplayMaxHealth();
        previousSkillAttackSpeedBonus =
                soldier.getArenaSkillAttackSpeedBonus();
        previousSkillLifesteal = soldier.getArenaSkillLifesteal();
        AttributeInstance scaleAttribute =
                soldier.getAttribute(Attributes.SCALE);
        previousEntityScale = scaleAttribute == null
                ? 1.0 : scaleAttribute.getValue();

        double maxHealth = polarBaseMaxHealth + polarHealthBonus;
        soldier.setArenaDisplayMaxHealth(maxHealth);
        soldier.setArenaCurrentHealth(Math.min(maxHealth,
                soldier.getArenaCurrentHealth() + polarHealthBonus));
        setMaxHealthMirror(maxHealth);
        soldier.setArenaSkillAttackSpeedBonus(
                previousSkillAttackSpeedBonus
                        + ArenaSkillBalance.polarBearAttackSpeedMultiplier(
                        star, magicPower) - 1.0);
        soldier.setArenaSkillLifesteal(previousSkillLifesteal
                + ArenaSkillBalance.polarBearLifestealBonus(
                star, magicPower));
        setPolarMovementSpeedBonus(
                ArenaSkillBalance.polarBearMovementSpeedBonus(
                        star, magicPower));
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(2.0);
        }
        polarFormActive = true;
        polarFormTicks = ArenaSkillBalance.POLAR_BEAR_FORM_TICKS;
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.POLAR_FORM, true);
        particleBurst(level, soldier, ParticleTypes.SNOWFLAKE, 24);
        soldier.syncArenaHealthMirror();
    }

    private void endPolarForm() {
        if (!polarFormActive) return;
        double currentHealth = soldier.getArenaCurrentHealth();
        double baseMax = Math.max(1.0, polarBaseMaxHealth);
        soldier.setArenaDisplayMaxHealth(baseMax);
        soldier.setArenaCurrentHealth(Math.min(baseMax,
                Math.max(0.0, currentHealth - polarHealthBonus)));
        setMaxHealthMirror(baseMax);
        soldier.setArenaSkillAttackSpeedBonus(
                previousSkillAttackSpeedBonus);
        soldier.setArenaSkillLifesteal(previousSkillLifesteal);
        setPolarMovementSpeedBonus(0.0);
        AttributeInstance scaleAttribute =
                soldier.getAttribute(Attributes.SCALE);
        if (scaleAttribute != null) {
            scaleAttribute.setBaseValue(previousEntityScale);
        }
        polarFormActive = false;
        polarFormTicks = 0;
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.POLAR_FORM, false);
        soldier.syncArenaHealthMirror();
    }

    /** 北极熊变身等持续形态期间锁定技能条。 */
    public boolean blocksSkillChargeGain() {
        if (silenceTicks > 0) return true;
        if (polarFormActive) return true;
        if (snifferDominanceActive) return true;
        if (blazeBarrageActive) return true;
        if (drownedTridentBarrageActive) return true;
        if (soldier instanceof ArenaSnowGolem snowGolem
                && snowGolem.hasActiveSkillBarrage()) {
            return true;
        }
        if (elderGuardianLaserActive) return true;
        if (endermanLockedTargetId != null) return true;
        return false;
    }

    public boolean isElderGuardianLaserActive() {
        return elderGuardianLaserActive;
    }

    public void applySnifferDominance(ResourceLocation sourceId) {
        if (!soldier.isAlive() || !soldier.isArenaUnit()) return;
        ResourceLocation attackId =
                ResourceLocation.fromNamespaceAndPath(
                        sourceId.getNamespace(),
                        sourceId.getPath() + "/attack");
        ResourceLocation movementId =
                ResourceLocation.fromNamespaceAndPath(
                        sourceId.getNamespace(),
                        sourceId.getPath() + "/movement");
        setAdditiveModifier(Attributes.ATTACK_DAMAGE, attackId,
                ArenaSkillBalance
                        .SNIFFER_DOMINANCE_ATTACK_BONUS);
        AttributeInstance movement =
                soldier.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement != null) {
            movement.addOrUpdateTransientModifier(
                    new AttributeModifier(
                            movementId,
                            ArenaSkillBalance
                                    .SNIFFER_DOMINANCE_MOVEMENT_SPEED_BONUS,
                            AttributeModifier.Operation
                                    .ADD_MULTIPLIED_BASE));
        }
        snifferDominanceAttackSpeedSources.put(
                sourceId,
                ArenaSkillBalance.SNIFFER_DOMINANCE_ATTACK_SPEED_BONUS);
        double totalAttackSpeedBonus =
                snifferDominanceAttackSpeedSources.values().stream()
                        .mapToDouble(Double::doubleValue).sum();
        soldier.setArenaAuraAttackSpeedBonus(
                totalAttackSpeedBonus);
    }

    public boolean blocksKnockback() {
        return polarFormActive;
    }

    public boolean isDebuffImmune() {
        return soldier.getArenaSkillType()
                == ArenaSkillType.SKELETON_HORSE_CLEANSE
                && soldier.getArenaShield() > 0.0f;
    }

    public boolean applySilence(int ticks) {
        if (ticks <= 0 || soldier.isArenaControlImmune()) {
            return false;
        }
        ticks = modifyIncomingControlTicks(ticks);
        silenceTicks = Math.max(silenceTicks, ticks);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.SILENCE, true);
        return true;
    }

    public void applySupportAttackSpeedBonus(double bonus,
                                             int ticks) {
        if (bonus <= 0.0 || ticks <= 0 || !soldier.isAlive()) {
            return;
        }
        supportAttackSpeedBonus = Math.max(
                supportAttackSpeedBonus, bonus);
        supportAttackSpeedTicks = Math.max(
                supportAttackSpeedTicks, ticks);
        soldier.setArenaSupportAttackSpeedBonus(
                supportAttackSpeedBonus);
    }

    private boolean beginHoglinCharge() {
        AbstractArmySoldier target = null;
        double bestDistance = ArenaSkillBalance.HOGLIN_CHARGE_RANGE;
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            double distance = MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy);
            if (distance <= bestDistance) {
                bestDistance = distance;
                target = enemy;
            }
        }
        if (target == null) return false;
        hoglinChargeTargetId = target.getUUID();
        hoglinChargeTicks = ArenaSkillBalance.HOGLIN_CHARGE_TICKS;
        hoglinNextDamageTicks.clear();
        soldier.setNoAi(true);
        soldier.setTarget(null);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.HOGLIN_CHARGE, true);
        lockStunnedMovement();
        return true;
    }

    private void tickHoglinCharge(ServerLevel level) {
        Entity entity = hoglinChargeTargetId == null
                ? null : level.getEntity(hoglinChargeTargetId);
        if (!(entity instanceof AbstractArmySoldier target)
                || !target.isAlive()
                || !soldier.isArenaEnemy(target)) {
            finishHoglinCharge();
            return;
        }
        Vec3 delta = target.position().subtract(soldier.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 1.0E-5) {
            finishHoglinCharge();
            return;
        }
        double nx = delta.x / horizontal;
        double nz = delta.z / horizontal;
        double step = ArenaSkillBalance.HOGLIN_CHARGE_STEP
                * soldier.getArenaFreezeSpeedMultiplier();
        soldier.move(MoverType.SELF, new Vec3(
                nx * step, soldier.getDeltaMovement().y, nz * step));
        level.sendParticles(ParticleTypes.FLAME,
                soldier.getX(), soldier.getY() + 0.25,
                soldier.getZ(), 2,
                0.18, 0.10, 0.18, 0.018);
        if ((soldier.tickCount & 1) == 0) {
            level.sendParticles(ParticleTypes.SMOKE,
                    soldier.getX(), soldier.getY() + 0.15,
                    soldier.getZ(), 2,
                    0.22, 0.08, 0.22, 0.012);
        }
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);

        float damage = (float) (soldier.getAttributeValue(
                Attributes.ATTACK_DAMAGE)
                * ArenaSkillBalance.hoglinChargeAttackMultiplier(
                soldier.getArenaStar(),
                soldier.getArenaMagicPower()));
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            if (MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy)
                    > ArenaSkillBalance.HOGLIN_HIT_RADIUS) {
                continue;
            }
            Vec3 away = enemy.position().subtract(soldier.position());
            double awayLength = Math.sqrt(
                    away.x * away.x + away.z * away.z);
            if (awayLength > 1.0E-5) {
                enemy.applyArenaKnockback(0.35,
                        -away.x / awayLength,
                        -away.z / awayLength);
            }
            int readyAt = hoglinNextDamageTicks.getOrDefault(
                    enemy.getUUID(), Integer.MIN_VALUE);
            if (soldier.tickCount >= readyAt) {
                hoglinNextDamageTicks.put(enemy.getUUID(),
                        soldier.tickCount
                                + ArenaSkillBalance
                                .HOGLIN_DAMAGE_COOLDOWN_TICKS);
                dealSkillDamage(enemy,
                        soldier.damageSources().mobAttack(soldier),
                        damage);
                particleBurst(level, enemy, ParticleTypes.CRIT, 12);
            }
        }

        hoglinChargeTicks--;
        if (hoglinChargeTicks <= 0 || soldier.horizontalCollision) {
            finishHoglinCharge();
        }
    }

    private void finishHoglinCharge() {
        hoglinChargeTicks = 0;
        hoglinChargeTargetId = null;
        hoglinNextDamageTicks.clear();
        soldier.setNoAi(false);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.HOGLIN_CHARGE, false);
    }

    private boolean beginGoatCharge() {
        AbstractArmySoldier target = null;
        double bestDistance = ArenaSkillBalance.GOAT_CHARGE_RANGE;
        for (AbstractArmySoldier enemy :
                nearestEnemies(Integer.MAX_VALUE)) {
            if (Math.abs(enemy.getY() - soldier.getY()) > 2.0) {
                continue;
            }
            double distance = MeleeReach.horizontalSurfaceDistance(
                    soldier, enemy);
            if (distance <= bestDistance) {
                bestDistance = distance;
                target = enemy;
            }
        }
        if (target == null) return false;
        goatChargeTargetId = target.getUUID();
        goatChargeTicks = ArenaSkillBalance.GOAT_CHARGE_TICKS;
        soldier.setNoAi(true);
        soldier.setTarget(null);
        soldier.setArenaStatusFlag(ArenaStatusFlags.GOAT_CHARGE, true);
        lockStunnedMovement();
        return true;
    }

    private void tickGoatCharge(ServerLevel level) {
        Entity entity = goatChargeTargetId == null
                ? null : level.getEntity(goatChargeTargetId);
        if (!(entity instanceof AbstractArmySoldier target)
                || !target.isAlive()
                || !soldier.isArenaEnemy(target)) {
            finishGoatCharge();
            return;
        }

        Vec3 delta = target.position().subtract(soldier.position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (MeleeReach.horizontalSurfaceDistance(
                soldier, target) <= 1.8
                && Math.abs(target.getY() - soldier.getY()) <= 2.0) {
            double nx = delta.x / Math.max(horizontal, 1.0E-5);
            double nz = delta.z / Math.max(horizontal, 1.0E-5);
            target.applyArenaKnockback(0.8, -nx, -nz);
            target.arenaSkills().applyStun(
                    ArenaSkillBalance.goatStunTicks(
                            soldier.getArenaStar()));
            particleBurst(level, target, ParticleTypes.CRIT, 16);
            finishGoatCharge();
            return;
        }

        double step = Math.min(ArenaSkillBalance.GOAT_CHARGE_STEP,
                horizontal) * soldier.getArenaFreezeSpeedMultiplier();
        Vec3 movement = new Vec3(
                delta.x / horizontal * step,
                soldier.getDeltaMovement().y,
                delta.z / horizontal * step);
        soldier.move(MoverType.SELF, movement);
        if ((soldier.tickCount & 1) == 0) {
            level.sendParticles(ParticleTypes.CLOUD,
                    soldier.getX(), soldier.getY() + 0.12,
                    soldier.getZ(), 3,
                    0.20, 0.08, 0.20, 0.015);
            level.sendParticles(ParticleTypes.CRIT,
                    soldier.getX(), soldier.getY() + 0.4,
                    soldier.getZ(), 1,
                    0.10, 0.12, 0.10, 0.01);
        }
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
        goatChargeTicks--;
        if (goatChargeTicks <= 0 || soldier.horizontalCollision) {
            finishGoatCharge();
        }
    }

    private void finishGoatCharge() {
        goatChargeTicks = 0;
        goatChargeTargetId = null;
        soldier.setNoAi(false);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.GOAT_CHARGE, false);
    }

    private void setAdditiveModifier(
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id, double amount) {
        AttributeInstance instance = soldier.getAttribute(attribute);
        if (instance == null) return;
        if (Math.abs(amount) < 0.001) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(new AttributeModifier(
                id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private void setAttributeModifier(
            net.minecraft.core.Holder<
                    net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id, double amount,
            AttributeModifier.Operation operation) {
        AttributeInstance instance = soldier.getAttribute(attribute);
        if (instance == null) return;
        if (Math.abs(amount) < 0.001) {
            instance.removeModifier(id);
            return;
        }
        instance.addOrUpdateTransientModifier(
                new AttributeModifier(id, amount, operation));
    }

    private void setMaxHealthMirror(double maxHealth) {
        AttributeInstance maxHealthAttribute =
                soldier.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(
                    Math.min(1024.0, Math.max(1.0, maxHealth)));
        }
    }

    private void setPolarMovementSpeedBonus(double bonus) {
        AttributeInstance speed = soldier.getAttribute(
                Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (bonus <= 0.0) {
            speed.removeModifier(POLAR_MOVEMENT_MODIFIER);
            return;
        }
        speed.addOrUpdateTransientModifier(new AttributeModifier(
                POLAR_MOVEMENT_MODIFIER, bonus,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    private void setWardenMovementSpeedBonus(double bonus) {
        AttributeInstance speed = soldier.getAttribute(
                Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (Math.abs(bonus) < 0.001) {
            speed.removeModifier(WARDEN_MOVEMENT_MODIFIER);
            return;
        }
        speed.addOrUpdateTransientModifier(
                new AttributeModifier(
                        WARDEN_MOVEMENT_MODIFIER, bonus,
                        AttributeModifier.Operation
                                .ADD_MULTIPLIED_BASE));
    }

    private void cleanupSummonRoster(ServerLevel level) {
        summonIds.removeIf(id -> {
            Entity entity = level.getEntity(id);
            return !(entity instanceof AbstractArmySoldier summon)
                    || !summon.isAlive()
                    || summon.isRemoved()
                    || !summon.isArenaUnit()
                    || !soldier.getArenaMatchId()
                    .equals(summon.getArenaMatchId())
                    || soldier.getArenaSide() != summon.getArenaSide()
                    || soldier.getArenaBoardIndex()
                    != summon.getArenaBoardIndex();
        });
    }

    private void tickEvokerSummons(ServerLevel level) {
        if (soldier.getArenaSkillType()
                != ArenaSkillType.EVOKER_FANG_STUN) {
            return;
        }
        if (summonIds.size()
                >= ArenaSkillBalance.EVOKER_MAX_SUMMONS) {
            return;
        }

        if (evokerSummonCooldown < 0) {
            evokerSummonCooldown = 20 + soldier.getRandom().nextInt(20);
        }
        if (--evokerSummonCooldown > 0) return;
        if (nearestEnemies(1).isEmpty()) {
            evokerSummonCooldown = 20;
            return;
        }
        evokerSummonCooldown =
                spawnEvokerSummon(level)
                        ? ArenaSkillBalance.EVOKER_SUMMON_INTERVAL_TICKS
                        : 20;
    }

    private boolean spawnEvokerSummon(ServerLevel level) {
        if (!(ModEntities.VEX_FLYING.get().create(level)
                instanceof AbstractArmySoldier summon)) {
            return false;
        }
        UUID matchId = soldier.getArenaMatchId();
        if (matchId == null) return false;

        Vec3 spot = findSummonSpot(summon);
        if (spot == null) return false;
        summon.moveTo(spot.x, spot.y, spot.z,
                soldier.getYRot(), 0.0f);
        summon.setFactionId(soldier.getFactionId());
        soldier.getCommanderUuid().ifPresent(summon::setCommanderUuid);
        summon.setSquadId(soldier.getSquadId());
        summon.enterArenaMode(matchId, soldier.getArenaSide(),
                soldier.getArenaBoardIndex());
        summon.copyArenaSourcePiece(soldier);
        summon.arenaTraits().configureSummon(
                soldier.arenaTraits());
        summon.setArenaStar(soldier.getArenaStar());
        summon.setArenaEquipmentIds(List.of());
        summon.applyArenaEquipmentAppearance(
                null, null, null, null, false);
        applyArenaSummonStats(summon);
        summon.arenaTraits().applySummonInheritance();
        summon.addTag("monster_conquest_arena_summon");
        if (!level.addFreshEntity(summon)) return false;
        if (!ArenaMatchManager.registerArenaCombatEntity(summon)) {
            summon.discard();
            return false;
        }

        summonIds.add(summon.getUUID());
        soldier.arenaTraits().onSummonCreated();
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.EVOKER_PREPARE_SUMMON,
                SoundSource.HOSTILE, 1.0f, 1.0f);
        level.playSound(null, spot.x, spot.y, spot.z,
                SoundEvents.VEX_CHARGE,
                SoundSource.HOSTILE, 0.8f, 1.15f);
        level.sendParticles(ParticleTypes.POOF,
                spot.x, spot.y + 0.35, spot.z,
                14, 0.35, 0.35, 0.35, 0.02);
        return true;
    }

    private Vec3 findSummonSpot(AbstractArmySoldier summon) {
        Vec3 spot = soldier.position();
        summon.moveTo(spot.x, spot.y, spot.z,
                soldier.getYRot(), 0.0f);
        return spot;
    }

    private void applyArenaSummonStats(AbstractArmySoldier summon) {
        // 移速沿用恼鬼实体自身的主世界属性；飞行输入仍在其
        // AbstractFlyingSoldier 层按 MOVEMENT_SPEED × 0.1 统一换算。
        double movementSpeed = summon.getAttributeValue(
                Attributes.MOVEMENT_SPEED);
        double attackSpeed = Math.max(0.01,
                soldier.getArenaEffectiveAttackSpeed());
        double maxHealth;
        double attack;
        double armor;
        double magicResistance;
        double magicPower;
        if (soldier.getArenaStar() >= 3) {
            maxHealth = ArenaSkillBalance
                    .EVOKER_THREE_STAR_SUMMON_HEALTH;
            attack = ArenaSkillBalance
                    .EVOKER_THREE_STAR_SUMMON_ATTACK;
            // 原版 generic.armor 的 RangedAttribute 上限就是 30。
            armor = ArenaSkillBalance
                    .EVOKER_THREE_STAR_SUMMON_ARMOR;
            magicResistance = ArenaSkillBalance
                    .EVOKER_THREE_STAR_SUMMON_MAGIC_RESISTANCE;
            magicPower = ArenaSkillBalance
                    .EVOKER_THREE_STAR_SUMMON_MAGIC_POWER;
        } else {
            double ratio = ArenaSkillBalance.EVOKER_SUMMON_STAT_RATIO;
            maxHealth = Math.max(1.0,
                    soldier.getArenaDisplayMaxHealth() * ratio);
            attack = soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE) * ratio;
            armor = Math.min(
                    ArenaSkillBalance.EVOKER_THREE_STAR_SUMMON_ARMOR,
                    soldier.getAttributeValue(Attributes.ARMOR) * ratio);
            magicResistance =
                    soldier.getArenaEffectiveMagicResistance() * ratio;
            magicPower =
                    soldier.getArenaMagicPower() * ratio;
        }
        summon.configureArenaSkill(null, Integer.MAX_VALUE);
        summon.setArenaRangedDamage(attack);
        summon.setArenaAttackSpeed(attackSpeed);
        summon.setArenaMagicResistance(magicResistance);
        summon.setArenaMagicPower(magicPower);
        AttributeInstance maxHealthAttribute =
                summon.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(
                    Math.min(1024.0, maxHealth));
        }
        AttributeInstance attackAttribute =
                summon.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttribute != null) {
            attackAttribute.setBaseValue(attack);
        }
        AttributeInstance armorAttribute =
                summon.getAttribute(Attributes.ARMOR);
        if (armorAttribute != null) {
            armorAttribute.setBaseValue(armor);
        }
        AttributeInstance speedAttribute =
                summon.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttribute != null) {
            speedAttribute.setBaseValue(movementSpeed);
        }
        summon.setArenaDisplayMaxHealth(maxHealth);
        summon.setArenaCurrentHealth(maxHealth);
        summon.syncArenaHealthMirror();
    }

    public void discardArenaSummons() {
        if (soldier.level() instanceof ServerLevel level) {
            discardArenaSummons(level);
        } else {
            summonIds.clear();
        }
    }

    private void discardArenaSummons(ServerLevel level) {
        if (summonIds.isEmpty()) return;
        for (UUID id : List.copyOf(summonIds)) {
            if (level.getEntity(id) instanceof Entity entity
                    && !entity.isRemoved()) {
                entity.discard();
            }
        }
        summonIds.clear();
        evokerSummonCooldown = -1;
    }

    public static boolean isBasicAttack(DamageSource source) {
        if (source.is(DamageTypes.THORNS)) return false;
        if (source.getDirectEntity() instanceof ArmyArrow arrow
                && arrow.isArenaSkillArrow()) {
            return false;
        }
        if (source.getDirectEntity() instanceof ArenaSnowball snowball
                && snowball.isSkillProjectile()) {
            return false;
        }
        if (source.getDirectEntity() instanceof ArenaWindProjectile wind
                && wind.isSkillProjectile()) {
            return false;
        }
        if (source.getDirectEntity() instanceof ArenaFireball fireball
                && fireball.isSkillProjectile()) {
            return false;
        }
        if (source.getEntity()
                instanceof ArenaElderGuardian guardian
                && guardian.isDealingBasicLaserDamage()) {
            return true;
        }
        return source.getEntity() != null
                && !source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                && !source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC);
    }

    public static boolean isArenaSkillDamage(DamageSource source) {
        if (source.getDirectEntity() instanceof ArmyArrow arrow
                && arrow.isArenaSkillArrow()) {
            return true;
        }
        if (source.getDirectEntity() instanceof ArenaSnowball snowball
                && snowball.isSkillProjectile()) {
            return true;
        }
        if (source.getDirectEntity() instanceof ArenaWindProjectile wind
                && wind.isSkillProjectile()) {
            return true;
        }
        if (source.getDirectEntity() instanceof ArenaFireball fireball
                && fireball.isSkillProjectile()) {
            return true;
        }
        if (source.getDirectEntity() instanceof ArenaWitherSkull skull
                && skull.isSkillProjectile()) {
            return true;
        }
        if (source.getDirectEntity() instanceof EvokerFangs fangs
                && fangs.getTags().contains(
                EvokerSoldier.ARENA_SKILL_FANG_TAG)) {
            return true;
        }
        return source.getEntity() instanceof AbstractArmySoldier attacker
                && (attacker.arenaSkills().isDealingSkillDamage()
                || attacker.isDealingArenaTrueDamage());
    }

    public void cleanseArenaDebuffs() {
        soldier.arenaEquipment().cleanseHarmfulArenaEffects();
        boolean modifiersRemoved =
                externalAttackDamageModifiers.values()
                        .removeIf(modifier -> modifier.harmful)
                        | externalAttackSpeedModifiers.values()
                        .removeIf(modifier -> modifier.harmful);
        var movementIterator =
                externalMovementSpeedModifiers.entrySet().iterator();
        while (movementIterator.hasNext()) {
            Map.Entry<ResourceLocation, TimedMovementSpeedModifier>
                    entry = movementIterator.next();
            if (!entry.getValue().harmful) continue;
            movementIterator.remove();
            AttributeInstance movement = soldier.getAttribute(
                    Attributes.MOVEMENT_SPEED);
            if (movement != null) {
                movement.removeModifier(entry.getKey());
            }
            modifiersRemoved = true;
        }
        if (modifiersRemoved) {
            refreshExternalStatModifiers();
        }
        if (silenceTicks > 0) {
            silenceTicks = 0;
            soldier.setArenaStatusFlag(
                    ArenaStatusFlags.SILENCE, false);
        }
        if (stunTicks > 0 || stunFalling) {
            stunTicks = 0;
            stunFalling = false;
            stunFallVelocity = 0.0;
            stunLaunchTicks = 0;
            stunLaunchVelocity = Vec3.ZERO;
            soldier.setNoAi(
                    soldier.getArenaSkillType()
                            == ArenaSkillType
                            .GIANT_ARMADILLO_MOMENTUM);
            soldier.setArenaStatusFlag(ArenaStatusFlags.STUN, false);
        }
        List<MobEffectInstance> harmful = soldier.getActiveEffects().stream()
                .filter(effect -> !effect.getEffect().value().isBeneficial())
                .toList();
        for (MobEffectInstance effect : harmful) {
            soldier.removeEffect(effect.getEffect());
        }
        receivedTauntTicks = 0;
        tauntSourceId = null;
        soldier.setTicksFrozen(0);
    }

    public static float arenaCurrentHealth(LivingEntity entity) {
        if (entity instanceof AbstractArmySoldier arenaUnit
                && arenaUnit.getArenaDisplayMaxHealth() > 0.0f) {
            return arenaUnit.getArenaCurrentHealth();
        }
        return entity.getHealth();
    }

    public static float arenaMaxHealth(LivingEntity entity) {
        if (entity instanceof AbstractArmySoldier arenaUnit
                && arenaUnit.getArenaDisplayMaxHealth() > 0.0f) {
            return arenaUnit.getArenaDisplayMaxHealth();
        }
        return entity.getMaxHealth();
    }

    private static void emitGroundRing(
            ServerLevel level, Vec3 center, double radius,
            ParticleOptions particle, int points) {
        double rotation = level.random.nextDouble()
                * Math.PI * 2.0;
        for (int index = 0; index < points; index++) {
            double angle = rotation
                    + index * Math.PI * 2.0 / points;
            level.sendParticles(particle,
                    center.x + Math.cos(angle) * radius,
                    center.y + 0.12,
                    center.z + Math.sin(angle) * radius,
                    2, 0.05, 0.04, 0.05, 0.01);
        }
    }

    private static void emitParticleLine(
            ServerLevel level, Vec3 start, Vec3 end,
            ParticleOptions particle, int points) {
        Vec3 delta = end.subtract(start);
        for (int index = 0; index <= points; index++) {
            Vec3 point = start.add(delta.scale(
                    index / (double) points));
            level.sendParticles(particle,
                    point.x, point.y, point.z,
                    1, 0.04, 0.04, 0.04, 0.005);
        }
    }

    private static void particleBurst(ServerLevel level, LivingEntity target,
                                      net.minecraft.core.particles.ParticleOptions particle,
                                      int count) {
        level.sendParticles(particle,
                target.getX(), target.getY() + target.getBbHeight() * 0.5,
                target.getZ(), count, 0.35, 0.5, 0.35, 0.02);
    }

    private record TimedAttackModifier(
            double amount, int expiresAt, boolean harmful) {
    }

    private record TimedAttackSpeedModifier(
            double amount, int expiresAt, boolean harmful) {
    }

    private record TimedMovementSpeedModifier(
            double amount, int expiresAt, boolean harmful) {
    }
}
