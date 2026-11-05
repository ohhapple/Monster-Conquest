package com.ohhapple.monster_conquest.arena.trait;

import com.ohhapple.monster_conquest.ai.nav.MeleeReach;
import com.ohhapple.monster_conquest.arena.game.ArenaEquipmentRuntime;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillRuntime;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单个竞技单位的羁绊运行态。羁绊定义、弈子映射和效果规则均不写进实体；
 * 此对象只根据独立羁绊状态执行一场战斗内的状态与事件。
 */
public final class ArenaTraitRuntime {

    private static final int TICKS_PER_SECOND = 20;
    private static final double UNDEAD_DEATH_DAMAGE_RATIO = 0.10;
    private static final double NETHER_DEATH_DAMAGE_RATIO = 0.12;

    private final AbstractArmySoldier soldier;
    private final ArenaSkillRuntime skills;
    private final Map<UUID, Integer> beastSixNextHit =
            new HashMap<>();
    private final Set<UUID> ambushFirstHits = new HashSet<>();

    private ArenaTraitAnalyzer.State state =
            ArenaTraitAnalyzer.analyze(List.of());
    private int unitIndex = -1;
    private boolean summon;
    private boolean initialized;
    private boolean reviveUsed;
    private boolean deathHandled;
    private boolean raiderFirstKillTriggered;
    private boolean endMovementEmpowered;
    private boolean gunnerBonusPending;
    private int basicAttackCount;
    private int nextOceanHealTick;
    private int nextGuardianShieldTick;
    private int nextHerdTick;
    private int nextArcaneChargeTick;
    private double summonSourceMagicPower;

    public ArenaTraitRuntime(AbstractArmySoldier soldier,
                             ArenaSkillRuntime skills) {
        this.soldier = soldier;
        this.skills = skills;
    }

    public void configure(int unitIndex, List<Integer> roster) {
        this.unitIndex = unitIndex;
        this.state = ArenaTraitAnalyzer.analyze(roster);
    }

    /**
     * 召唤物先继承来源羁绊状态，等召唤物全部属性复制完成后再调用
     * {@link #applySummonInheritance()}，避免生命、攻击被复制逻辑覆盖。
     */
    public void configureSummon(ArenaTraitRuntime source) {
        unitIndex = source.unitIndex;
        state = source.state;
        summonSourceMagicPower = source.soldier.getArenaMagicPower();
        summon = true;
    }

    public void applySummonInheritance() {
        syncRuntimeStats();
        if (!summon || !has(ArenaTrait.SUMMON)
                || !state.active(ArenaTrait.SUMMON, 2)) {
            return;
        }

        double maxHealth = Math.max(1.0,
                soldier.getArenaDisplayMaxHealth() * 1.40);
        soldier.setArenaDisplayMaxHealth(maxHealth);
        soldier.setArenaCurrentHealth(maxHealth);
        AttributeInstance health = soldier.getAttribute(
                Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(Math.min(1024.0, maxHealth));
        }

        AttributeInstance attack = soldier.getAttribute(
                Attributes.ATTACK_DAMAGE);
        if (attack != null) {
            attack.setBaseValue(attack.getBaseValue() * 1.30);
        }
        soldier.setArenaRangedDamage(
                soldier.getAttributeValue(Attributes.ATTACK_DAMAGE));

        if (state.active(ArenaTrait.SUMMON, 4)) {
            soldier.setArenaMagicPower(
                    soldier.getArenaMagicPower()
                            + summonSourceMagicPower * 0.30);
        }
        soldier.syncArenaHealthMirror();
    }

    public ArenaTraitAnalyzer.State state() {
        return state;
    }

    public ArenaTraitRules.StatModifiers modifiers() {
        if (unitIndex < 0) {
            return ArenaTraitRules.emptyModifiers();
        }
        return ArenaTraitRules.modifiersFor(unitIndex, state);
    }

    public int unitIndex() {
        return unitIndex;
    }

    /**
     * 静态羁绊中由装备运行态统一消费的闪避、治疗、技能条和暴击数值
     * 在此写入，不复制进装备定义。
     */
    public void syncRuntimeStats() {
        if (unitIndex < 0) return;
        ArenaTraitRules.StatModifiers modifiers = modifiers();
        soldier.setArenaShieldEffectiveness(
                1.0 + modifiers.shieldPower());
        soldier.arenaEquipment().setTraitStats(
                new ArenaEquipmentRuntime.InheritedStats(
                        modifiers.dodgeChance(),
                        modifiers.healingPower(),
                        0.0,
                        modifiers.skillChargeGainPercent(),
                        modifiers.critChance(),
                        modifiers.critDamage(),
                        false));
    }

    public void initialize() {
        if (initialized || summon || unitIndex < 0) return;
        initialized = true;
        syncRuntimeStats();
        ArenaTraitRules.StatModifiers modifiers = modifiers();
        if (modifiers.startingShieldRatio() > 0.0) {
            soldier.grantArenaShield(
                    ArenaSkillRuntime.arenaMaxHealth(soldier)
                            * modifiers.startingShieldRatio());
        }
    }

    public void tick() {
        if (unitIndex < 0 || summon
                || !(soldier.level() instanceof ServerLevel level)
                || !soldier.isAlive()) {
            return;
        }
        tickOcean(level);
        tickGuardian(level);
        tickHerd(level);
        tickArcane();
    }

    private void tickOcean(ServerLevel level) {
        if (!has(ArenaTrait.OCEAN)
                || !active(ArenaTrait.OCEAN, 2)) {
            return;
        }
        if (nextOceanHealTick == 0) {
            nextOceanHealTick =
                    soldier.tickCount + 5 * TICKS_PER_SECOND;
            return;
        }
        if (soldier.tickCount < nextOceanHealTick) return;
        nextOceanHealTick =
                soldier.tickCount + 5 * TICKS_PER_SECOND;
        soldier.heal((float) (ArenaSkillRuntime
                .arenaMaxHealth(soldier) * 0.04));
        particle(level, soldier, ParticleTypes.BUBBLE, 8);
    }

    private void tickGuardian(ServerLevel level) {
        if (!has(ArenaTrait.GUARDIAN)
                || !active(ArenaTrait.GUARDIAN, 4)) {
            return;
        }
        if (nextGuardianShieldTick == 0) {
            nextGuardianShieldTick =
                    soldier.tickCount + 5 * TICKS_PER_SECOND;
            return;
        }
        if (soldier.tickCount < nextGuardianShieldTick) return;
        nextGuardianShieldTick =
                soldier.tickCount + 5 * TICKS_PER_SECOND;
        AbstractArmySoldier ally = lowestHealthAlly();
        if (ally == null) return;
        ally.grantArenaShieldFrom(soldier,
                ArenaSkillRuntime.arenaMaxHealth(ally) * 0.12);
        particle(level, ally, ParticleTypes.ENCHANT, 12);
    }

    private void tickHerd(ServerLevel level) {
        if (!has(ArenaTrait.HERD)
                || !active(ArenaTrait.HERD, 2)) {
            return;
        }
        if (nextHerdTick == 0) {
            nextHerdTick =
                    soldier.tickCount + 5 * TICKS_PER_SECOND;
            return;
        }
        if (soldier.tickCount < nextHerdTick) return;
        nextHerdTick =
                soldier.tickCount + 5 * TICKS_PER_SECOND;
        soldier.heal((float) (ArenaSkillRuntime
                .arenaMaxHealth(soldier) * 0.015));
        particle(level, soldier, ParticleTypes.HAPPY_VILLAGER, 6);
        if (!active(ArenaTrait.HERD, 6)) return;
        AbstractArmySoldier ally = lowestHealthAlly();
        if (ally == null) return;
        ally.grantArenaShieldFrom(soldier,
                ArenaSkillRuntime.arenaMaxHealth(ally) * 0.10);
        particle(level, ally, ParticleTypes.ENCHANT, 8);
    }

    private void tickArcane() {
        if (!has(ArenaTrait.ARCANE)) {
            nextArcaneChargeTick = 0;
            return;
        }
        int tier = state.tier(ArenaTrait.ARCANE);
        if (tier <= 0) return;
        if (nextArcaneChargeTick == 0) {
            nextArcaneChargeTick =
                    soldier.tickCount + TICKS_PER_SECOND;
            return;
        }
        if (soldier.tickCount < nextArcaneChargeTick) return;
        nextArcaneChargeTick =
                soldier.tickCount + TICKS_PER_SECOND;
        int charge = ArenaTraitRules
                .arcaneChargePerSecond(tier);
        // 原始技能条回复仍受沉默、形态锁条和技能施放锁条约束。
        soldier.addArenaSkillChargeRaw(charge);
    }

    public void beforeBasicAttack() {
        if (summon) return;
        basicAttackCount++;
        gunnerBonusPending = has(ArenaTrait.GUNNER)
                && active(ArenaTrait.GUNNER, 3)
                && basicAttackCount % 4 == 0;
    }

    public float modifyOutgoingDamage(
            LivingEntity victim, DamageSource source, float amount) {
        if (amount <= 0.0f || summon
                || soldier.isDealingArenaEffectDamage()) {
            return amount;
        }
        boolean basic = ArenaSkillRuntime.isBasicAttack(source);
        boolean skill = ArenaSkillRuntime.isArenaSkillDamage(source);
        if (basic && endMovementEmpowered
                && has(ArenaTrait.END)) {
            amount += (float) (soldier.getAttributeValue(
                    Attributes.ATTACK_DAMAGE) * 0.60);
            endMovementEmpowered = false;
        }
        if (has(ArenaTrait.ARTHROPOD)
                && active(ArenaTrait.ARTHROPOD, 3)
                && victim.hasEffect(MobEffects.POISON)) {
            amount *= 1.25f;
        }
        if (has(ArenaTrait.ILLAGER)
                && active(ArenaTrait.ILLAGER, 2)) {
            float maxHealth =
                    ArenaSkillRuntime.arenaMaxHealth(victim);
            if (maxHealth > 0.0f
                    && ArenaSkillRuntime
                    .arenaCurrentHealth(victim)
                    / maxHealth < 0.60f) {
                amount *= 1.25f;
            }
        }
        if (skill && has(ArenaTrait.ARCANE)) {
            amount *= (float) (1.0
                    + modifiers().skillDamageBonus());
        }
        return amount;
    }

    public float modifyIncomingDamage(
            LivingEntity attacker,
            DamageSource source, float amount) {
        if (amount <= 0.0f) return amount;
        if (summon) {
            double teamReduction =
                    active(ArenaTrait.GUARDIAN, 6) ? 0.12 : 0.0;
            if (active(ArenaTrait.BULWARK, 10)) {
                teamReduction += 0.08;
            }
            return amount * (float) (1.0 - teamReduction);
        }
        double reduction =
                modifiers().incomingDamageReduction();
        if (has(ArenaTrait.OCEAN)
                && active(ArenaTrait.OCEAN, 3)
                && soldier.getArenaShield() > 0.0f) {
            reduction += 0.20;
        }
        if (has(ArenaTrait.CONSTRUCT)
                && active(ArenaTrait.CONSTRUCT, 3)
                && soldier.getArenaShield() > 0.0f) {
            reduction += 0.10;
        }
        return amount * (float) (1.0
                - Math.min(0.80, reduction));
    }

    public void afterDamageDealt(
            LivingEntity victim, DamageSource source,
            float dealtDamage) {
        if (dealtDamage <= 0.0f || summon
                || soldier.isDealingArenaEffectDamage()
                || !(soldier.level() instanceof ServerLevel level)
                || !(victim instanceof AbstractArmySoldier target)
                || !target.isAlive()
                || !soldier.isArenaEnemy(target)) {
            return;
        }
        boolean basic =
                ArenaSkillRuntime.isBasicAttack(source);
        boolean skill =
                ArenaSkillRuntime.isArenaSkillDamage(source);

        if (basic && has(ArenaTrait.UNDEAD)
                && active(ArenaTrait.UNDEAD, 4)) {
            soldier.heal((float) (ArenaSkillRuntime
                    .arenaMaxHealth(soldier) * 0.025));
        }
        if (basic && has(ArenaTrait.NETHER)
                && active(ArenaTrait.NETHER, 2)) {
            applyNetherDebuffs(
                    target, 5 * TICKS_PER_SECOND / 2);
        }
        if (basic && has(ArenaTrait.NETHER)
                && active(ArenaTrait.NETHER, 8)) {
            soldier.heal(dealtDamage * 0.15f);
            particle(level, soldier,
                    ParticleTypes.SOUL_FIRE_FLAME, 8);
        }
        if (basic && has(ArenaTrait.BEAST)
                && active(ArenaTrait.BEAST, 6)) {
            int now = soldier.tickCount;
            if (now >= beastSixNextHit.getOrDefault(
                    target.getUUID(), 0)) {
                beastSixNextHit.put(
                        target.getUUID(), now + 2 * TICKS_PER_SECOND);
                float bonus = Math.min(400.0f,
                        (float) (target.getArenaCurrentHealth() * 0.05));
                effectDamage(target, bonus,
                        soldier.damageSources().mobAttack(soldier));
                particle(level, target, ParticleTypes.CRIT, 10);
            }
        }
        if (basic && has(ArenaTrait.ARTHROPOD)
                && active(ArenaTrait.ARTHROPOD, 2)) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.POISON, 3 * TICKS_PER_SECOND,
                    ArenaSkillBalance.ARTHROPOD_POISON_AMPLIFIER,
                    false, true), soldier);
        }
        if (basic && has(ArenaTrait.GUNNER) && gunnerBonusPending
                && active(ArenaTrait.GUNNER, 3)) {
            gunnerBonusPending = false;
            effectDamage(target,
                    (float) (soldier.getAttributeValue(
                            Attributes.ATTACK_DAMAGE) * 1.00),
                    soldier.damageSources().mobAttack(soldier));
        }
        if (skill && has(ArenaTrait.ARTHROPOD)
                && active(ArenaTrait.ARTHROPOD, 3)) {
            target.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN,
                    4 * TICKS_PER_SECOND, 2,
                    false, true), soldier);
        }
        if (skill && has(ArenaTrait.ILLAGER)
                && active(ArenaTrait.ILLAGER, 4)) {
            target.addEffect(new MobEffectInstance(
                    ModEffects.GRIEVOUS_WOUND,
                    4 * TICKS_PER_SECOND, 4,
                    false, true), soldier);
            target.arenaEquipment().applyArmorBreakDebuff(
                    5, 4 * TICKS_PER_SECOND);
            target.arenaEquipment().applyMagicResistancePercentReduction(
                    0.25, 4 * TICKS_PER_SECOND);
        }
        if (basic && has(ArenaTrait.AMBUSH)
                && active(ArenaTrait.AMBUSH, 2)
                && ambushFirstHits.add(target.getUUID())) {
            int tier = state.tier(ArenaTrait.AMBUSH);
            double bonus = switch (tier) {
                case 2 -> 0.25;
                case 3 -> 0.35;
                case 4 -> 0.50;
                case 5 -> 0.75;
                default -> 0.15;
            };
            effectDamage(target,
                    (float) (soldier.getAttributeValue(
                            Attributes.ATTACK_DAMAGE) * bonus),
                    soldier.damageSources().mobAttack(soldier));
            particle(level, target, ParticleTypes.CRIT, 12);
        }
    }

    public void afterDamageTaken(
            AbstractArmySoldier attacker,
            DamageSource source, float takenDamage) {
        if (summon || takenDamage <= 0.0f || attacker == null
                || attacker.isDealingArenaEffectDamage()
                || !has(ArenaTrait.NETHER)
                || !active(ArenaTrait.NETHER, 6)) {
            return;
        }
        applyNetherDebuffs(attacker, 4 * TICKS_PER_SECOND);
    }

    public void onDashOrTeleport() {
        if (!summon && has(ArenaTrait.END)
                && active(ArenaTrait.END, 2)) {
            endMovementEmpowered = true;
        }
    }

    public void onShieldBroken() {
        if (summon
                || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        boolean constructBreak = has(ArenaTrait.CONSTRUCT)
                && active(ArenaTrait.CONSTRUCT, 3);
        boolean bulwarkBreak = has(ArenaTrait.BULWARK)
                && active(ArenaTrait.BULWARK, 6);
        if (!constructBreak && !bulwarkBreak) return;
        double radius = bulwarkBreak ? 1.75 : 1.5;
        int stunTicks = bulwarkBreak
                ? 2 * TICKS_PER_SECOND
                : 3 * TICKS_PER_SECOND / 2;
        for (AbstractArmySoldier enemy : enemiesWithin(radius)) {
            enemy.arenaSkills().applyStun(
                    stunTicks);
        }
        particle(level, soldier,
                ParticleTypes.ITEM_SNOWBALL, 16);
    }

    public void onKill() {
        onRaiderKill(true);
        onAmbushKill(true);
    }

    public void onKillParticipation() {
        onRaiderKill(false);
        onAmbushKill(false);
    }

    private void onRaiderKill(boolean directKill) {
        if (summon || !has(ArenaTrait.RAIDER)
                || !active(ArenaTrait.RAIDER, 2)) {
            return;
        }
        int tier = state.tier(ArenaTrait.RAIDER);
        if ((tier == 1 || !directKill)
                && raiderFirstKillTriggered) {
            return;
        }
        if (!directKill && tier > 1) return;
        raiderFirstKillTriggered = true;
        ResourceLocation source = id(
                "trait/raider/" + soldier.getUUID());
        skills.addAttackSpeedModifier(source,
                soldier.getArenaBaseAttackSpeed() * 0.25,
                8 * TICKS_PER_SECOND, false);
        skills.addMovementSpeedModifier(
                source, 0.25,
                8 * TICKS_PER_SECOND, false);
        if (tier >= 2) {
            soldier.heal((float) ((ArenaSkillRuntime
                    .arenaMaxHealth(soldier)
                    - soldier.getArenaCurrentHealth()) * 0.25));
        }
    }

    private void onAmbushKill(boolean directKill) {
        if (summon || !directKill
                || !has(ArenaTrait.AMBUSH)
                || !active(ArenaTrait.AMBUSH, 6)) {
            return;
        }
        int tier = state.tier(ArenaTrait.AMBUSH);
        double speedBonus = tier >= 5 ? 0.30 : 0.20;
        double healRatio = tier >= 5 ? 0.20 : 0.12;
        ResourceLocation source = id(
                "trait/ambush/" + soldier.getUUID());
        skills.addAttackSpeedModifier(source,
                soldier.getArenaBaseAttackSpeed() * speedBonus,
                4 * TICKS_PER_SECOND, false);
        skills.addMovementSpeedModifier(
                source, speedBonus,
                4 * TICKS_PER_SECOND, false);
        soldier.heal((float) ((ArenaSkillRuntime
                .arenaMaxHealth(soldier)
                - soldier.getArenaCurrentHealth()) * healRatio));
        if (soldier.level() instanceof ServerLevel level) {
            particle(level, soldier, ParticleTypes.CRIT, 16);
        }
    }

    public boolean tryRevive() {
        if (summon || reviveUsed || !has(ArenaTrait.UNDEAD)
                || !active(ArenaTrait.UNDEAD, 6)) {
            return false;
        }
        reviveUsed = true;
        int undeadTier = state.tier(ArenaTrait.UNDEAD);
        double ratio = undeadTier >= 4
                ? 0.50 : (undeadTier >= 3 ? 0.35 : 0.25);
        soldier.setArenaCurrentHealth(
                ArenaSkillRuntime.arenaMaxHealth(soldier) * ratio);
        soldier.resetArenaSkillCharge();
        soldier.syncArenaHealthMirror();
        if (soldier.level() instanceof ServerLevel level) {
            particle(level, soldier,
                    ParticleTypes.SOUL_FIRE_FLAME, 34);
        }
        return true;
    }

    public void onDeath() {
        if (deathHandled || summon
                || !(soldier.level() instanceof ServerLevel level)
                || unitIndex < 0) {
            return;
        }
        deathHandled = true;
        if (has(ArenaTrait.UNDEAD)
                && active(ArenaTrait.UNDEAD, 2)) {
            applyDeathDamage(level,
                    soldier.damageSources().magic(),
                    ParticleTypes.SOUL,
                    UNDEAD_DEATH_DAMAGE_RATIO, 2.0);
        }
        if (has(ArenaTrait.NETHER)
                && active(ArenaTrait.NETHER, 6)) {
            applyDeathDamage(level,
                    soldier.damageSources().explosion(soldier, null),
                    ParticleTypes.EXPLOSION,
                    NETHER_DEATH_DAMAGE_RATIO, 1.75);
        }
        if (has(ArenaTrait.VILLAGER)
                && active(ArenaTrait.VILLAGER, 4)) {
            AbstractArmySoldier ally = nearestAlly();
            if (ally != null) {
                ResourceLocation source = id(
                        "trait/villager/" + soldier.getUUID());
                ally.arenaSkills().addAttackDamageModifier(
                        source,
                        ally.getAttributeValue(Attributes.ATTACK_DAMAGE)
                                * 0.30,
                        7 * TICKS_PER_SECOND, false);
                ally.arenaSkills().addAttackSpeedModifier(
                        source,
                        ally.getArenaBaseAttackSpeed() * 0.25,
                        7 * TICKS_PER_SECOND, false);
                particle(level, ally,
                        ParticleTypes.HAPPY_VILLAGER, 16);
            }
        }
    }

    private void applyDeathDamage(
            ServerLevel level, DamageSource source,
            ParticleOptions particleType, double damageRatio,
            double radius) {
        float damage = (float) (ArenaSkillRuntime
                .arenaMaxHealth(soldier) * damageRatio);
        for (AbstractArmySoldier enemy : enemiesWithin(radius)) {
            effectDamage(enemy, damage, source);
        }
        particle(level, soldier, particleType,
                particleType == ParticleTypes.EXPLOSION ? 1 : 24);
    }

    public void onSummonCreated() {
        if (summon
                || !has(ArenaTrait.SUMMON)
                || !active(ArenaTrait.SUMMON, 4)
                || !(soldier.level() instanceof ServerLevel level)) {
            return;
        }
        ResourceLocation source = id(
                "trait/summon/" + soldier.getUUID()
                        + "/" + level.getGameTime());
        for (AbstractArmySoldier ally : allies()) {
            ally.arenaSkills().addAttackSpeedModifier(
                    source,
                    ally.getArenaBaseAttackSpeed() * 0.20,
                    7 * TICKS_PER_SECOND, false);
            ally.arenaSkills().addMovementSpeedModifier(
                    source, 0.20,
                    7 * TICKS_PER_SECOND, false);
        }
        particle(level, soldier, ParticleTypes.POOF, 10);
    }

    public boolean isSummon() {
        return summon;
    }

    public boolean hasRevived() {
        return reviveUsed;
    }

    /** 复活后允许下一次真正死亡再次结算一次性死亡触发。 */
    public void onRevive() {
        deathHandled = false;
    }

    private void applyNetherDebuffs(
            AbstractArmySoldier target, int ticks) {
        if (target.isArenaDebuffImmune()) return;
        target.setRemainingFireTicks(Math.max(
                target.getRemainingFireTicks(), ticks));
        target.arenaEquipment().applyBurnFrom(
                soldier, ticks, 0.01, 0.0, false);
        target.addEffect(new MobEffectInstance(
                ModEffects.GRIEVOUS_WOUND, ticks, 4,
                false, true), soldier);
    }

    private void effectDamage(
            LivingEntity target, float amount,
            DamageSource source) {
        if (amount <= 0.0f || !target.isAlive()) return;
        soldier.beginArenaEffectDamage();
        try {
            target.hurt(source, amount);
        } finally {
            soldier.endArenaEffectDamage();
        }
    }

    private List<AbstractArmySoldier> enemiesWithin(double radius) {
        return soldier.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                soldier.getBoundingBox().inflate(radius + 1.0),
                enemy -> enemy.isAlive()
                        && enemy.isArenaUnit()
                        && soldier.isArenaEnemy(enemy)
                        && Math.abs(enemy.getY() - soldier.getY()) <= 2.0
                        && MeleeReach.horizontalSurfaceDistance(
                        soldier, enemy) <= radius);
    }

    private AbstractArmySoldier lowestHealthAlly() {
        return allies().stream()
                .min(Comparator.comparingDouble(
                        AbstractArmySoldier::getArenaHealthRatio))
                .orElse(null);
    }

    private AbstractArmySoldier nearestAlly() {
        return allies().stream()
                .filter(ally -> ally != soldier)
                .findFirst().orElse(null);
    }

    private List<AbstractArmySoldier> allies() {
        Set<AbstractArmySoldier> unique = new LinkedHashSet<>(
                soldier.level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        soldier.getBoundingBox().inflate(64.0),
                        other -> other.isAlive()
                                && other.isArenaUnit()
                                && other.getArenaSide()
                                == soldier.getArenaSide()
                                && !soldier.isArenaEnemy(other)));
        unique.add(soldier);
        List<AbstractArmySoldier> allies =
                new ArrayList<>(unique);
        allies.sort(Comparator.comparingDouble(
                soldier::distanceToSqr));
        return allies;
    }

    private boolean has(ArenaTrait trait) {
        return ArenaTraitCatalog.hasTrait(unitIndex, trait);
    }

    private boolean active(ArenaTrait trait, int threshold) {
        return state.active(trait, threshold);
    }

    private static void particle(
            ServerLevel level, LivingEntity entity,
            ParticleOptions particleType, int count) {
        level.sendParticles(particleType,
                entity.getX(),
                entity.getY() + entity.getBbHeight() * 0.5,
                entity.getZ(), count,
                0.35, 0.5, 0.35, 0.03);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(
                "monster_conquest", "arena/" + path);
    }
}
