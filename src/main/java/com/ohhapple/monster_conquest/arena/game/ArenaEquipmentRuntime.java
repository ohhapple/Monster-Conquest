package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.effect.GrievousWoundEffect;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 单个竞技单位的装备特效状态。普通攻击、技能、治疗和受击都由同一实例
 * 维护；战斗实体销毁时状态自然释放，不写回主世界实体。
 */
public final class ArenaEquipmentRuntime {

    private static final int TICKS_PER_SECOND = 20;
    private static final int GRIEVOUS_LEVEL_5 = 4;
    private static final int SWIFT_BLADE_MAX_STACKS = 100;
    public static final double BASE_CRIT_CHANCE = 0.25;
    public static final double BASE_CRIT_DAMAGE = 0.50;

    private final AbstractArmySoldier soldier;
    private final Map<UUID, BurnState> burns = new HashMap<>();
    private List<ArenaEquipment> equipment = List.of();
    private int basicAttackCount;
    private int swiftBladeStacks;
    private int galeBowStacks;
    private int stalkerKills;
    private int fortressBowStacks;
    private int heavyArmorStacks;
    private int magebaneTargets;
    private int armorBreakStacks;
    private int armorBreakExpiresAt;
    private int magicShredStacks;
    private int magicShredExpiresAt;
    private int magicShredPercentExpiresAt;
    private double magicShredPercentMultiplier = 1.0;
    private double skillChargeCarry;
    private boolean chargedBladeReady;
    private boolean bloodthirsterShieldUsed;
    private boolean shellShieldApplied;
    private boolean guerrillaCleanseUsed;
    private int nimbleBootsReadyAt;
    private int energyShellReadyAt;
    private int temporaryShieldExpiresAt;
    private float temporaryShieldRemaining;
    private boolean totemReviveUsed;
    private double totemLifestealBonus;
    private int nextConduitChargeTick;
    private InheritedStats inheritedStats = InheritedStats.NONE;
    private InheritedStats traitStats = InheritedStats.NONE;

    public ArenaEquipmentRuntime(AbstractArmySoldier soldier) {
        this.soldier = soldier;
    }

    public void setEquipment(List<Integer> equipmentIds) {
        List<ArenaEquipment> next = equipmentIds.stream()
                .map(ArenaEquipment::byId)
                .filter(item -> !item.tool())
                .toList();
        if (next.equals(equipment)) {
            refreshAll();
            return;
        }
        boolean hadControlImmunity = has(
                ArenaEquipment.Effect.GUERRILLA_ARMOR);
        equipment = next;
        if (!has(ArenaEquipment.Effect.SWIFT_BLADE)) {
            swiftBladeStacks = 0;
        }
        if (!has(ArenaEquipment.Effect.GALE_BOW)) {
            galeBowStacks = 0;
        }
        if (!has(ArenaEquipment.Effect.STALKER)) {
            stalkerKills = 0;
        }
        if (!has(ArenaEquipment.Effect.FORTRESS_BOW)) {
            fortressBowStacks = 0;
        }
        if (!has(ArenaEquipment.Effect.HEAVY_ARMOR)) {
            heavyArmorStacks = 0;
        }
        if (!has(ArenaEquipment.Effect.MAGEBANE_ARMOR)) {
            magebaneTargets = 0;
        }
        if (!has(ArenaEquipment.Effect.CHARGED_BLADE)) {
            chargedBladeReady = false;
        }
        if (!has(ArenaEquipment.Effect.CONDUIT)) {
            nextConduitChargeTick = 0;
        }
        refreshAll();
        if (!guerrillaCleanseUsed
                && !hadControlImmunity
                && has(ArenaEquipment.Effect.GUERRILLA_ARMOR)) {
            guerrillaCleanseUsed = true;
            soldier.arenaSkills().cleanseArenaDebuffs();
        }
    }

    public void tick() {
        if (!(soldier.level() instanceof ServerLevel level)
                || !soldier.isArenaUnit() || !soldier.isAlive()) {
            return;
        }
        int now = soldier.tickCount;
        expireDebuffs(now);
        tickControlImmunity(now);
        applyShellBulwark(now);
        applyBloodthirsterThreshold(now);
        tickRegeneration(now);
        tickMagebane(now);
        tickTemporaryShield(now);
        tickBurns(level, now);
        tickConduit();
        syncStatusIcons(now);
    }

    public double dodgeChance() {
        return Math.min(0.75,
                inheritedStats.dodgeChance()
                        + traitStats.dodgeChance()
                        + totalStats().dodgeChance()
                        + soldier.arenaSkills()
                        .temporaryDodgeBonus());
    }

    public double criticalChance() {
        return Math.min(1.0,
                BASE_CRIT_CHANCE
                        + inheritedStats.critChance()
                        + traitStats.critChance()
                        + totalStats().critChance());
    }

    public double criticalDamageMultiplier() {
        return 1.0 + BASE_CRIT_DAMAGE
                + inheritedStats.critDamage()
                + traitStats.critDamage()
                + totalStats().critDamage();
    }

    public boolean canSkillCrit() {
        return inheritedStats.skillCritEnabled()
                || totalStats().skillCritEnabled();
    }

    public boolean rollCritical() {
        return soldier.getRandom().nextDouble()
                < criticalChance();
    }

    public boolean tryDodge(@Nullable AbstractArmySoldier attacker,
                            DamageSource source) {
        if (attacker == null || attacker.isDealingArenaEffectDamage()) {
            return false;
        }
        double chance = dodgeChance();
        if (chance <= 0.0 || soldier.getRandom().nextDouble() >= chance) {
            return false;
        }
        if (soldier.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    soldier.getX(), soldier.getY() + soldier.getBbHeight() * 0.5,
                    soldier.getZ(), 5, 0.35, 0.35, 0.35, 0.02);
        }
        return true;
    }

    public float modifyIncomingAttack(@Nullable AbstractArmySoldier attacker,
                                      DamageSource source, float amount) {
        if (attacker == null
                || attacker.arenaSkills().isDealingSkillDamage()
                || !ArenaSkillRuntime.isBasicAttack(source)) {
            return amount;
        }
        if (has(ArenaEquipment.Effect.WARRIOR_PLATE)) {
            amount *= 0.75f;
        }
        return amount;
    }

    public float modifyOutgoingAttack(LivingEntity victim,
                                      DamageSource source, float amount) {
        if (soldier.isDealingArenaEffectDamage()
                || soldier.arenaSkills().isDealingSkillDamage()
                || !ArenaSkillRuntime.isBasicAttack(source)) {
            return amount;
        }
        if (has(ArenaEquipment.Effect.GIANT_BLADE)
                && basicAttackCount % 3 == 0) {
            amount *= 1.5f;
        }
        if (chargedBladeReady
                && has(ArenaEquipment.Effect.CHARGED_BLADE)) {
            amount *= 1.4f;
            chargedBladeReady = false;
        }
        return amount;
    }

    public void beforeBasicAttack() {
        if (soldier.isDealingArenaEffectDamage()) return;
        basicAttackCount++;
        if (has(ArenaEquipment.Effect.GIANT_BLADE)
                && basicAttackCount % 3 == 0) {
            soldier.markArenaAttackIgnoresShield(20);
        }
    }

    public void afterBasicAttack(LivingEntity victim, DamageSource source) {
        if (soldier.isDealingArenaEffectDamage()
                || soldier.arenaSkills().isDealingSkillDamage()
                || !ArenaSkillRuntime.isBasicAttack(source)) {
            return;
        }
        if (has(ArenaEquipment.Effect.SWIFT_BLADE)
                && swiftBladeStacks < SWIFT_BLADE_MAX_STACKS) {
            swiftBladeStacks++;
            refreshAttackSpeed();
        }
        if (has(ArenaEquipment.Effect.PULSE_BOW)
                && basicAttackCount % 3 == 0) {
            pulseBowChain(victim);
        }
    }

    public void afterDamageDealt(LivingEntity victim, DamageSource source,
                                 float dealtDamage) {
        // 命中型装备按直接伤害统一触发，使普攻与技能遵循同一套特效语义；
        // 持续伤害和反伤由 isDealingArenaEffectDamage 排除，避免特效递归。
        if (!soldier.isDealingArenaEffectDamage()
                && victim instanceof AbstractArmySoldier target
                && soldier.isArenaEnemy(target)) {
            applyDirectHitEffects(target, source);
        }
        if (dealtDamage <= 0.0f || soldier.isDealingArenaEffectDamage()) {
            return;
        }
        double lifesteal = inheritedStats.lifesteal()
                + totalStats().lifesteal()
                + soldier.getArenaSkillLifesteal();
        if (lifesteal > 0.0) {
            soldier.heal((float) (dealtDamage * lifesteal));
        }
        if (has(ArenaEquipment.Effect.VAMPIRIC_BOW)) {
            AbstractArmySoldier ally = lowestHealthAlly();
            if (ally != null) {
                ally.healFrom(soldier, dealtDamage * 0.30f);
            }
        }
    }

    private void applyDirectHitEffects(
            AbstractArmySoldier target, DamageSource source) {
        if (has(ArenaEquipment.Effect.SPELLBREAKER)) {
            target.arenaEquipment().applySpellbreakerDebuff();
        }
        if (has(ArenaEquipment.Effect.RAPID_CROSSBOW)) {
            target.arenaEquipment().applyRapidCrossbowDebuff();
        }
        if (has(ArenaEquipment.Effect.FLAME_BLADE)) {
            applyBurn(target, 5 * TICKS_PER_SECOND,
                    0.02, 0.0, true);
            applyGrievous(target, 5 * TICKS_PER_SECOND);
        }
        if (has(ArenaEquipment.Effect.ROCKET_BOW)
                && source.getDirectEntity() instanceof
                net.minecraft.world.entity.projectile.Projectile) {
            applyBurn(target, 5 * TICKS_PER_SECOND,
                    0.02, 0.0, false);
            applyGrievous(target, 5 * TICKS_PER_SECOND);
        }
        if (has(ArenaEquipment.Effect.FLAME_TREADS)) {
            applyBurn(target, 3 * TICKS_PER_SECOND,
                    0.01, 0.0, false);
        }
    }

    public void afterDamageTaken(@Nullable AbstractArmySoldier attacker,
                                 DamageSource source, float takenDamage) {
        if (takenDamage <= 0.0f) return;
        int now = soldier.tickCount;
        if (has(ArenaEquipment.Effect.FORTRESS_BOW)) {
            fortressBowStacks = Math.min(5, fortressBowStacks + 1);
        }
        if (has(ArenaEquipment.Effect.HEAVY_ARMOR)) {
            heavyArmorStacks++;
        }
        if (has(ArenaEquipment.Effect.GALE_BOW)) {
            galeBowStacks = Math.min(3, galeBowStacks + 1);
            refreshAttackSpeed();
        }
        if (has(ArenaEquipment.Effect.NIMBLE_BOOTS)
                && now >= nimbleBootsReadyAt) {
            nimbleBootsReadyAt = now + 5 * TICKS_PER_SECOND;
            soldier.heal(soldier.getArenaDisplayMaxHealth() * 0.06f);
        }
        if (attacker == null || attacker == soldier
                || attacker.isDealingArenaEffectDamage()) {
            return;
        }
        if (has(ArenaEquipment.Effect.THORNS_ARMOR)) {
            effectDamage(attacker, takenDamage * 0.15f,
                    soldier.damageSources().thorns(soldier));
        }
        if (has(ArenaEquipment.Effect.MOLTEN_ARMOR)) {
            attacker.arenaEquipment().applyBurnTo(soldier,
                    5 * TICKS_PER_SECOND, 0.015, 0.0, false);
            attacker.arenaEquipment().applyGrievousTo(soldier,
                    5 * TICKS_PER_SECOND);
        }
        if (has(ArenaEquipment.Effect.MOLTEN_SHELL)) {
            double perSecond = soldier.getArenaMagicPower() * 0.05
                    + soldier.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes
                            .ATTACK_DAMAGE) * 0.033333;
            attacker.arenaEquipment().applyBurnTo(soldier,
                    3 * TICKS_PER_SECOND, 0.0, perSecond, false);
        }
    }

    public void afterDamageResolved() {
        applyBloodthirsterThreshold(soldier.tickCount);
    }

    public void onKill() {
        if (!has(ArenaEquipment.Effect.STALKER)) return;
        stalkerKills++;
        refreshAttackSpeed();
        refreshMovementSpeed();
    }

    public void onSkillCast() {
        if (has(ArenaEquipment.Effect.CHARGED_BLADE)) {
            chargedBladeReady = true;
        }
        if (has(ArenaEquipment.Effect.ENCHANTED_GOLDEN_APPLE)) {
            soldier.arenaSkills().cleanseArenaDebuffs();
            soldier.heal(soldier.getArenaDisplayMaxHealth() * 0.12f);
        }
        if (has(ArenaEquipment.Effect.ENERGY_SHELL)
                && soldier.tickCount >= energyShellReadyAt) {
            float shield = soldier.getArenaDisplayMaxHealth() * 0.12f;
            temporaryShieldRemaining +=
                    soldier.addArenaShield(shield);
            temporaryShieldExpiresAt = soldier.tickCount
                    + 3 * TICKS_PER_SECOND;
            energyShellReadyAt = soldier.tickCount
                    + 4 * TICKS_PER_SECOND;
        }
        if (has(ArenaEquipment.Effect.FLAME_STAFF)) {
            for (AbstractArmySoldier enemy : nearbyEnemies(12.0, 64, null)) {
                applyBurnTo(enemy, 3 * TICKS_PER_SECOND,
                        0.01, 0.0, false);
            }
        }
    }

    public void cleanseHarmfulArenaEffects() {
        burns.clear();
        armorBreakStacks = 0;
        armorBreakExpiresAt = 0;
        magicShredStacks = 0;
        magicShredExpiresAt = 0;
        magicShredPercentExpiresAt = 0;
        magicShredPercentMultiplier = 1.0;
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.BURN, false);
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.MAGIC_RESIST_SHRED, false);
    }

    public float modifyHealing(float amount) {
        if (amount <= 0.0f) return 0.0f;
        double healing = Math.max(0.0,
                1.0 + inheritedStats.healingPower()
                        + traitStats.healingPower()
                        + totalStats().healingPower());
        MobEffectInstance wound = soldier.getEffect(
                ModEffects.GRIEVOUS_WOUND);
        if (wound != null) {
            healing *= GrievousWoundEffect.healingMultiplier(
                    wound.getAmplifier());
        }
        return (float) (amount * healing);
    }

    public void onHeal(float actualHealing, float requestedHealing) {
        if (actualHealing <= 0.0f && requestedHealing <= 0.0f) return;
        if (has(ArenaEquipment.Effect.BURNING_CHARM)
                && actualHealing > 0.0f) {
            for (AbstractArmySoldier enemy : nearbyEnemies(8.0, 32, null)) {
                applyBurnTo(enemy, 3 * TICKS_PER_SECOND,
                        0.01, 0.0, false);
            }
        }
        float overheal = Math.max(0.0f, requestedHealing - actualHealing);
        if (overheal <= 0.0f) return;
        double capPercent = 0.0;
        if (has(ArenaEquipment.Effect.HOLY_SHELL)) capPercent = 0.20;
        if (has(ArenaEquipment.Effect.HEALING_CORE)) {
            capPercent = Math.max(capPercent, 0.15);
        }
        if (capPercent > 0.0) {
            float cap = (float) (soldier.getArenaDisplayMaxHealth()
                    * capPercent);
            float currentShield = soldier.getArenaShield();
            float added = Math.min(cap,
                    currentShield + overheal) - currentShield;
            if (added > 0.0f) {
                soldier.addArenaShield(added);
            }
        }
    }

    public int initialSkillChargeBonus() {
        return (int) Math.floor(totalStats().initialSkillCharge());
    }

    public int modifySkillChargeGain(int amount) {
        if (amount <= 0) return amount;
        double rate = 1.0 + Math.max(0.0,
                inheritedStats.skillChargeGainPercent()
                        + traitStats.skillChargeGainPercent()
                        + totalStats().skillChargeGainPercent());
        skillChargeCarry += amount * rate;
        int granted = (int) Math.floor(skillChargeCarry);
        skillChargeCarry -= granted;
        return Math.max(0, granted);
    }

    /**
     * 分裂体只继承母体当前的派生属性数值，不复制装备实例或触发型特效。
     * 这样攻防面板与母体一致，同时避免一件装备在多只分裂体上重复生效。
     */
    public InheritedStats snapshotInheritedStats() {
        ArenaEquipmentStats stats = totalStats();
        return new InheritedStats(
                inheritedStats.dodgeChance()
                        + stats.dodgeChance(),
                inheritedStats.healingPower()
                        + stats.healingPower(),
                inheritedStats.lifesteal()
                        + stats.lifesteal(),
                inheritedStats.skillChargeGainPercent()
                        + stats.skillChargeGainPercent(),
                inheritedStats.critChance()
                        + stats.critChance(),
                inheritedStats.critDamage()
                        + stats.critDamage(),
                inheritedStats.skillCritEnabled()
                        || stats.skillCritEnabled());
    }

    public void inheritStats(InheritedStats stats) {
        inheritedStats = stats == null ? InheritedStats.NONE : stats;
        refreshAll();
    }

    public void setTraitStats(InheritedStats stats) {
        traitStats = stats == null ? InheritedStats.NONE : stats;
    }

    public double effectiveMagicResistance() {
        double flat = soldier.getArenaMagicResistance();
        if (soldier.tickCount < magicShredExpiresAt) {
            flat -= magicShredStacks * 15.0;
        }
        flat += fortressBowStacks * 5.0 + heavyArmorStacks
                + magebaneTargets * 8.0;
        double value = Math.max(0.0, flat);
        if (soldier.tickCount < magicShredPercentExpiresAt) {
            value *= magicShredPercentMultiplier;
        }
        return value;
    }

    public double healingPowerBonus() {
        return inheritedStats.healingPower()
                + traitStats.healingPower()
                + totalStats().healingPower();
    }

    public double lifestealRatio() {
        return inheritedStats.lifesteal()
                + traitStats.lifesteal()
                + totalStats().lifesteal()
                + soldier.getArenaSkillLifesteal()
                + totemLifestealBonus;
    }

    public double skillChargeGainBonus() {
        return inheritedStats.skillChargeGainPercent()
                + traitStats.skillChargeGainPercent()
                + totalStats().skillChargeGainPercent();
    }

    public boolean isControlImmune() {
        return has(ArenaEquipment.Effect.GUERRILLA_ARMOR);
    }

    private void applyShellBulwark(int now) {
        if (shellShieldApplied
                || !has(ArenaEquipment.Effect.SHELL_BULWARK)
                || soldier.getArenaDisplayMaxHealth() <= 0.0f) {
            return;
        }
        shellShieldApplied = true;
        soldier.addArenaShield(
                soldier.getArenaDisplayMaxHealth() * 0.30f);
    }

    private void applyBloodthirsterThreshold(int now) {
        if (bloodthirsterShieldUsed
                || !has(ArenaEquipment.Effect.BLOODTHIRSTER)
                || soldier.getArenaDisplayMaxHealth() <= 0.0f
                || soldier.getArenaCurrentHealth() <= 0.0f
                || soldier.getArenaHealthRatio() >= 0.40f) {
            return;
        }
        bloodthirsterShieldUsed = true;
        soldier.addArenaShield(
                soldier.getArenaDisplayMaxHealth() * 0.40f);
    }

    private void tickRegeneration(int now) {
        if (!has(ArenaEquipment.Effect.REGENERATION_ARMOR)
                || now % TICKS_PER_SECOND != 0) {
            return;
        }
        soldier.heal(soldier.getArenaDisplayMaxHealth() * 0.025f);
    }

    private void tickMagebane(int now) {
        if (!has(ArenaEquipment.Effect.MAGEBANE_ARMOR)) {
            magebaneTargets = 0;
            refreshArmor();
            return;
        }
        if (now % 10 != 0) return;
        int targets = 0;
        for (AbstractArmySoldier enemy : nearbyEnemies(24.0, 64, null)) {
            if (enemy.getTarget() == soldier
                    || enemy.getArenaTauntTarget() == soldier) {
                targets++;
            }
        }
        magebaneTargets = targets;
        refreshArmor();
    }

    private void tickControlImmunity(int now) {
        if (!isControlImmune()) return;
        soldier.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        soldier.setTicksFrozen(0);
        soldier.markShieldGuardKnockbackImmunity(2);
    }

    private void tickTemporaryShield(int now) {
        if (temporaryShieldRemaining <= 0.0f
                || now < temporaryShieldExpiresAt) {
            return;
        }
        temporaryShieldRemaining = Math.min(
                temporaryShieldRemaining, soldier.getArenaShield());
        soldier.setArenaShield(Math.max(0.0f,
                soldier.getArenaShield() - temporaryShieldRemaining));
        temporaryShieldRemaining = 0.0f;
    }

    private void tickBurns(ServerLevel level, int now) {
        // 灼烧伤害可能在同一 tick 通过反伤或装备联动重新施加状态。
        // 使用键快照避免在伤害回调中修改 burns 时破坏 HashMap 迭代器。
        for (UUID sourceId : new ArrayList<>(burns.keySet())) {
            BurnState burn = burns.get(sourceId);
            if (burn == null) continue;
            if (now >= burn.expiresAt) {
                burns.remove(sourceId);
                continue;
            }
            if (now < burn.nextTick) continue;
            burn.nextTick = now + TICKS_PER_SECOND;
            double amount = soldier.getArenaDisplayMaxHealth()
                    * burn.maxHealthPercentPerSecond
                    + burn.flatPerSecond;
            if (amount <= 0.0) continue;
            AbstractArmySoldier sourceEntity =
                    level.getEntity(burn.sourceId)
                            instanceof AbstractArmySoldier source
                            && source.isAlive() ? source : null;
            DamageSource source = burn.trueDamage
                    ? soldier.damageSources().genericKill()
                    : (sourceEntity == null
                    ? soldier.damageSources().magic()
                    : soldier.damageSources().indirectMagic(
                    sourceEntity, sourceEntity));
            if (sourceEntity != null) sourceEntity.beginArenaEffectDamage();
            soldier.setNextArenaDamageSourcePieceId(
                    burn.sourcePieceId);
            try {
                soldier.hurt(source, (float) amount);
            } finally {
                soldier.clearNextArenaDamageSourcePieceId();
                if (sourceEntity != null) sourceEntity.endArenaEffectDamage();
            }
        }
    }

    private void tickConduit() {
        if (!has(ArenaEquipment.Effect.CONDUIT)) return;
        if (nextConduitChargeTick == 0) {
            nextConduitChargeTick =
                    soldier.tickCount + TICKS_PER_SECOND;
            return;
        }
        if (soldier.tickCount < nextConduitChargeTick) return;
        nextConduitChargeTick =
                soldier.tickCount + TICKS_PER_SECOND;
        soldier.addArenaSkillChargeRaw(3);
    }

    /**
     * 不死图腾由竞技死亡入口调用。复活发生在原版 die() 之前，
     * 因此战斗方存活数不会在复活完成前把本回合判负。
     */
    public boolean tryTotemRevive() {
        if (totemReviveUsed
                || !has(ArenaEquipment.Effect.TOTEM_OF_UNDYING)
                || !(soldier.level() instanceof ServerLevel level)) {
            return false;
        }
        totemReviveUsed = true;
        double maxHealth = Math.max(1.0,
                soldier.getArenaDisplayMaxHealth());
        soldier.setArenaCurrentHealth(maxHealth * 0.30);
        soldier.syncArenaHealthMirror();
        soldier.arenaSkills().cleanseArenaDebuffs();
        soldier.setTarget(null);
        soldier.getNavigation().stop();
        ResourceLocation source =
                ResourceLocation.fromNamespaceAndPath(
                        "monster_conquest",
                        "arena_equipment/totem/" + soldier.getUUID());
        soldier.arenaSkills().addAttackSpeedModifier(
                source,
                soldier.getArenaBaseAttackSpeed() * 1.50,
                0, false);
        soldier.arenaSkills().addMovementSpeedModifier(
                source, 1.00, 0, false);
        totemLifestealBonus = 0.10;
        soldier.grantArenaShield(maxHealth * 0.10);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                soldier.getX(), soldier.getY() + 0.8,
                soldier.getZ(), 40, 0.5, 0.8, 0.5, 0.15);
        level.playSound(null, soldier.getX(), soldier.getY(),
                soldier.getZ(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0f, 1.0f);
        return true;
    }

    private void expireDebuffs(int now) {
        if (now >= armorBreakExpiresAt) {
            armorBreakStacks = 0;
            refreshArmor();
        }
        if (now >= magicShredExpiresAt) magicShredStacks = 0;
    }

    private void syncStatusIcons(int now) {
        soldier.setArenaStatusFlag(ArenaStatusFlags.BURN,
                !burns.isEmpty());
        soldier.setArenaStatusFlag(
                ArenaStatusFlags.MAGIC_RESIST_SHRED,
                (magicShredStacks > 0 && now < magicShredExpiresAt)
                        || now < magicShredPercentExpiresAt);
    }

    private void applySpellbreakerDebuff() {
        if (soldier.isArenaDebuffImmune()) return;
        magicShredStacks = Math.min(2, magicShredStacks + 1);
        magicShredExpiresAt = soldier.tickCount + 3 * TICKS_PER_SECOND;
    }

    /** 外部竞技技能每层固定削减 15 魔抗。 */
    public void applyMagicResistanceShred(int stacks,
                                          int durationTicks) {
        if (stacks <= 0 || durationTicks <= 0) return;
        if (soldier.isArenaDebuffImmune()) return;
        magicShredStacks = Math.max(magicShredStacks,
                Math.min(2, stacks));
        magicShredExpiresAt = Math.max(magicShredExpiresAt,
                soldier.tickCount + durationTicks);
    }

    private void applyRapidCrossbowDebuff() {
        if (soldier.isArenaDebuffImmune()) return;
        applyArmorBreakDebuff(3, 3 * TICKS_PER_SECOND);
        applyMagicResistancePercentReduction(
                0.30, 3 * TICKS_PER_SECOND);
    }

    public void applyMagicResistancePercentReduction(
            double fraction, int durationTicks) {
        if (fraction <= 0.0 || durationTicks <= 0) return;
        if (soldier.isArenaDebuffImmune()) return;
        double multiplier = Math.max(
                0.0, 1.0 - Math.min(1.0, fraction));
        if (soldier.tickCount >= magicShredPercentExpiresAt) {
            magicShredPercentMultiplier = 1.0;
        }
        magicShredPercentMultiplier = Math.min(
                magicShredPercentMultiplier, multiplier);
        magicShredPercentExpiresAt = Math.max(
                magicShredPercentExpiresAt,
                soldier.tickCount + durationTicks);
    }

    public void applyArmorBreakDebuff(int stacks, int durationTicks) {
        if (soldier.isArenaDebuffImmune()) return;
        armorBreakStacks = Math.max(armorBreakStacks, stacks);
        armorBreakExpiresAt = soldier.tickCount
                + Math.max(1, durationTicks);
        refreshArmor();
    }

    private void applyBurn(LivingEntity target, int durationTicks,
                           double maxHealthPercentPerSecond,
                           double flatPerSecond, boolean trueDamage) {
        if (!(target instanceof AbstractArmySoldier arenaTarget)
                || !soldier.isArenaEnemy(arenaTarget)) {
            return;
        }
        arenaTarget.arenaEquipment().applyBurnTo(soldier,
                durationTicks, maxHealthPercentPerSecond,
                flatPerSecond, trueDamage);
    }

    private void applyBurnTo(AbstractArmySoldier source, int durationTicks,
                             double maxHealthPercentPerSecond,
                             double flatPerSecond, boolean trueDamage) {
        if (soldier.isArenaDebuffImmune()) return;
        BurnState current = burns.get(source.getUUID());
        int expiresAt = soldier.tickCount + durationTicks;
        if (current == null) {
            burns.put(source.getUUID(), new BurnState(
                    source.getUUID(),
                    source.getArenaSourcePieceId(),
                    expiresAt,
                    soldier.tickCount + TICKS_PER_SECOND,
                    maxHealthPercentPerSecond, flatPerSecond,
                    trueDamage));
            return;
        }
        current.expiresAt = Math.max(current.expiresAt, expiresAt);
        current.maxHealthPercentPerSecond = Math.max(
                current.maxHealthPercentPerSecond,
                maxHealthPercentPerSecond);
        current.flatPerSecond = Math.max(current.flatPerSecond, flatPerSecond);
        current.trueDamage |= trueDamage;
    }

    /** 供非装备技能复用同一套竞技点燃、状态图标和结算周期。 */
    public void applyBurnFrom(AbstractArmySoldier source,
                              int durationTicks,
                              double maxHealthPercentPerSecond,
                              double flatPerSecond,
                              boolean trueDamage) {
        if (source == null || source == soldier) return;
        applyBurnTo(source, durationTicks,
                maxHealthPercentPerSecond, flatPerSecond, trueDamage);
    }

    private void applyGrievous(LivingEntity target, int durationTicks) {
        target.addEffect(new MobEffectInstance(
                ModEffects.GRIEVOUS_WOUND, durationTicks,
                GRIEVOUS_LEVEL_5, false, true));
    }

    private void applyGrievousTo(AbstractArmySoldier target,
                                 int durationTicks) {
        if (target.isArenaDebuffImmune()) return;
        target.addEffect(new MobEffectInstance(
                ModEffects.GRIEVOUS_WOUND, durationTicks,
                GRIEVOUS_LEVEL_5, false, true));
    }

    private void pulseBowChain(LivingEntity primary) {
        List<AbstractArmySoldier> targets = nearbyEnemies(
                12.0, 64, primary);
        targets.sort(Comparator.comparingDouble(primary::distanceToSqr));
        double damage = soldier.getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes
                        .ATTACK_DAMAGE) * 0.30;
        if (damage <= 0.0) return;
        int hit = 0;
        Vec3 chainFrom = primary.getBoundingBox().getCenter();
        for (AbstractArmySoldier target : targets) {
            effectDamage(target, (float) damage,
                    soldier.damageSources().indirectMagic(soldier, soldier));
            Vec3 chainTo = target.getBoundingBox().getCenter();
            if (soldier.level() instanceof ServerLevel level) {
                emitPulseBowLightning(
                        level, chainFrom, chainTo);
            }
            chainFrom = chainTo;
            if (++hit >= 2) break;
        }
    }

    private void emitPulseBowLightning(
            ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 delta = to.subtract(from);
        double distance = delta.length();
        if (distance < 1.0E-5) return;
        Vec3 side = new Vec3(-delta.z, 0.0, delta.x)
                .normalize();
        int points = Math.max(6,
                Math.min(32, (int) Math.ceil(distance * 2.0)));
        for (int index = 0; index <= points; index++) {
            double t = index / (double) points;
            double jitter = (index == 0 || index == points)
                    ? 0.0
                    : Math.sin(index * 2.7
                    + soldier.tickCount * 0.31) * 0.10;
            Vec3 point = from.lerp(to, t)
                    .add(side.scale(jitter));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z,
                    1, 0.025, 0.025, 0.025, 0.01);
            if (index % 3 == 0) {
                level.sendParticles(ParticleTypes.END_ROD,
                        point.x, point.y, point.z,
                        1, 0.015, 0.015, 0.015, 0.005);
            }
        }
    }

    private void effectDamage(LivingEntity target, float amount,
                              DamageSource source) {
        if (amount <= 0.0f || !target.isAlive()) return;
        soldier.beginArenaEffectDamage();
        try {
            target.hurt(source, amount);
        } finally {
            soldier.endArenaEffectDamage();
        }
    }

    @Nullable
    private AbstractArmySoldier lowestHealthAlly() {
        List<AbstractArmySoldier> allies = soldier.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                soldier.getBoundingBox().inflate(64.0),
                other -> other.isAlive() && other.isArenaUnit()
                        && !soldier.isArenaEnemy(other));
        return allies.stream()
                .min(Comparator.comparingDouble(
                        AbstractArmySoldier::getArenaHealthRatio))
                .orElse(null);
    }

    private List<AbstractArmySoldier> nearbyEnemies(
            double radius, int limit, @Nullable LivingEntity excluded) {
        List<AbstractArmySoldier> enemies = new ArrayList<>(
                soldier.level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        soldier.getBoundingBox().inflate(radius),
                        other -> other != excluded
                                && other.isAlive()
                                && other.isArenaUnit()
                                && soldier.isArenaEnemy(other)));
        enemies.sort(Comparator.comparingDouble(soldier::distanceToSqr));
        return enemies.size() > limit
                ? enemies.subList(0, limit) : enemies;
    }

    private boolean has(ArenaEquipment.Effect effect) {
        return equipment.stream().anyMatch(
                item -> item.effect() == effect);
    }

    private ArenaEquipmentStats totalStats() {
        double health = 0.0;
        double attackPercent = 0.0;
        double attackSpeedPercent = 0.0;
        double armor = 0.0;
        double magicResistance = 0.0;
        double movementSpeedPercent = 0.0;
        double dodgeChance = 0.0;
        double healingPower = 0.0;
        double lifesteal = 0.0;
        double magicPower = 0.0;
        double initialSkillCharge = 0.0;
        double skillChargeGainPercent = 0.0;
        double critChance = 0.0;
        double critDamage = 0.0;
        boolean skillCritEnabled = false;
        for (ArenaEquipment item : equipment) {
            ArenaEquipmentStats stats = item.stats();
            health += stats.health();
            attackPercent += stats.attackPercent();
            attackSpeedPercent += stats.attackSpeedPercent();
            armor += stats.armor();
            magicResistance += stats.magicResistance();
            movementSpeedPercent += stats.movementSpeedPercent();
            dodgeChance += stats.dodgeChance();
            healingPower += stats.healingPower();
            lifesteal += stats.lifesteal();
            magicPower += stats.magicPower();
            initialSkillCharge += stats.initialSkillCharge();
            skillChargeGainPercent += stats.skillChargeGainPercent();
            critChance += stats.critChance();
            critDamage += stats.critDamage();
            skillCritEnabled |= stats.skillCritEnabled();
        }
        return new ArenaEquipmentStats(
                health, attackPercent, attackSpeedPercent, armor,
                magicResistance, movementSpeedPercent, dodgeChance,
                healingPower, lifesteal, magicPower,
                initialSkillCharge, skillChargeGainPercent,
                critChance, critDamage, skillCritEnabled);
    }

    public record InheritedStats(
            double dodgeChance,
            double healingPower,
            double lifesteal,
            double skillChargeGainPercent,
            double critChance,
            double critDamage,
            boolean skillCritEnabled) {
        public static final InheritedStats NONE =
                new InheritedStats(
                        0.0, 0.0, 0.0, 0.0,
                        0.0, 0.0, false);
    }

    private void refreshAll() {
        refreshAttackSpeed();
        refreshMovementSpeed();
        refreshArmor();
    }

    private void refreshAttackSpeed() {
        double bonus = swiftBladeStacks * 0.05
                + galeBowStacks * 0.15
                + stalkerKills * 0.20;
        soldier.setArenaEquipmentAttackSpeedBonus(bonus);
    }

    private void refreshMovementSpeed() {
        soldier.setArenaEquipmentMovementMultiplier(
                stalkerKills * 0.10);
    }

    private void refreshArmor() {
        double modifier = magebaneTargets * 5.0
                - armorBreakStacks * 5.0;
        soldier.setArenaEquipmentArmorModifier(modifier);
    }

    private static final class BurnState {
        private final UUID sourceId;
        @Nullable private final UUID sourcePieceId;
        private int expiresAt;
        private int nextTick;
        private double maxHealthPercentPerSecond;
        private double flatPerSecond;
        private boolean trueDamage;

        private BurnState(UUID sourceId, @Nullable UUID sourcePieceId,
                          int expiresAt, int nextTick,
                          double maxHealthPercentPerSecond,
                          double flatPerSecond, boolean trueDamage) {
            this.sourceId = sourceId;
            this.sourcePieceId = sourcePieceId;
            this.expiresAt = expiresAt;
            this.nextTick = nextTick;
            this.maxHealthPercentPerSecond = maxHealthPercentPerSecond;
            this.flatPerSecond = flatPerSecond;
            this.trueDamage = trueDamage;
        }
    }
}
