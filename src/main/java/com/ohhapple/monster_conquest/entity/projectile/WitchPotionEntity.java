package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.army.threat.ArmyThreatUtil;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
import com.ohhapple.monster_conquest.arena.game.ArenaSkillBalance;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.entity.soldiers.VillagerPriestSoldier;
import com.ohhapple.monster_conquest.entity.soldiers.WitchSoldier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class WitchPotionEntity extends ThrowableItemProjectile {

    public enum Kind { HEAL, POISON, HARM, WEAKEN, LINGERING }

    /**
     * 投掷者：决定药水结算走哪套"效果入口"。
     *   WITCH  → {@link WitchSoldier}（再生 / 吸收 / 随机三药）
     *   PRIEST → {@link VillagerPriestSoldier}（净化 + 少量回血 / 只有中毒）
     */
    public enum Caster { WITCH, PRIEST }

    public static final double SPLASH_RADIUS = 4.0;
    private static final double ARENA_SPLASH_RADIUS = 5.0;
    public static final float ARENA_ATTACK_RATIO = 1.50f;
    private static final double PROXIMITY_RADIUS = 2.0;
    private static final int MIN_FLIGHT_TICKS = 3;

    public static final double FLIGHT_GRAVITY = 0.03;
    public static final double FLIGHT_DRAG    = 0.99;
    public static final double THROW_SPEED = 1.5;

    private Kind kind = Kind.HARM;
    private Caster caster = Caster.WITCH;
    private int tier = 0;
    @Nullable private UUID commanderUuid;
    @Nullable private UUID intendedTargetUuid;
    @Nullable private UUID arenaMatchId;
    @Nullable private ResourceLocation factionId;
    private boolean arenaCombat;
    private float arenaDamage;
    private float arenaHeal;
    private boolean arenaLingeringSkill;

    public WitchPotionEntity(EntityType<? extends WitchPotionEntity> type, Level level) {
        super(type, level);
    }

    public WitchPotionEntity(Level level, LivingEntity shooter) {
        super(ModEntities.WITCH_POTION.get(), shooter, level);
        if (shooter instanceof AbstractArmySoldier s) {
            this.commanderUuid = s.getCommanderUuid().orElse(null);
            this.factionId = s.getFactionId();
            this.arenaMatchId = s.getArenaMatchId();
            if (s instanceof VillagerPriestSoldier) this.caster = Caster.PRIEST;
        }
    }

    public void configure(Kind kind, int tier,
                          @Nullable UUID commanderUuid,
                          @Nullable ResourceLocation factionId,
                          Caster caster) {
        this.kind = kind;
        this.caster = caster;
        this.tier = Math.max(0, Math.min(tier, 4));
        this.commanderUuid = commanderUuid;
        this.factionId = factionId;
    }

    public Caster getCaster() { return caster; }

    @Nullable
    public UUID getArenaMatchId() {
        return arenaMatchId;
    }

    /** 竞技女巫的普攻数值由召唤时攻击力快照决定。 */
    public void configureArenaCombat(double attackDamage) {
        arenaCombat = true;
        arenaDamage = (float) Math.max(0.0, attackDamage)
                * ARENA_ATTACK_RATIO;
        arenaHeal = (float) Math.max(0.0, attackDamage)
                * ARENA_ATTACK_RATIO;
    }

    public void configureArenaLingeringSkill(double attackDamage) {
        arenaCombat = true;
        arenaLingeringSkill = true;
        arenaDamage = (float) (Math.max(0.0, attackDamage)
                * ArenaSkillBalance.WITCH_LINGERING_ATTACK_RATIO);
        arenaHeal = 0.0f;
    }

    /** 治疗药水的目标；只在该目标附近提前结算，避免被路上的友军截胡。 */
    public void setIntendedTarget(@Nullable UUID targetUuid) {
        this.intendedTargetUuid = targetUuid;
    }

    @Override
    protected double getDefaultGravity() {
        return FLIGHT_GRAVITY;
    }

    @Override
    protected Item getDefaultItem() {
        return kind == Kind.LINGERING
                ? Items.LINGERING_POTION : Items.SPLASH_POTION;
    }

    /**
     * 判定"是否为阵营体系内的战斗单位"。
     *
     *   药水的提前引爆只在遇到这类实体时触发。普通生物（牛、猪、村民等）
     *   不进入判定 → 药水从它们头上飞过不会被引爆。
     */
    private static boolean isCombatUnit(Entity e) {
        return e instanceof AbstractArmySoldier
                || e instanceof AbstractArmyCommander
                || e instanceof Player
                || e instanceof WarHorseEntity;
    }

    /**
     * 友军 / 非预定目标不参与投射物命中判定。
     *
     *   仅在 onHit 里返回会让射线每 tick 继续选中同一个单位；药水贴着人飞时
     *   就会一直忽略实体命中，直到越过方块碰撞才停下。直接在命中判定阶段
     *   排除这些单位，射线会继续检查其后的方块与预定目标。
     */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!super.canHitEntity(target)) return false;
        if (!(target instanceof LivingEntity living)) return true;
        return kind == Kind.HEAL ? isIntendedTarget(living) : !isAlly(living);
    }

    @Override
    public void tick() {
        super.tick();
        // 命中方块的 tick 已经在 onHit 里结算并销毁；不要再用邻近判定重复溅射。
        if (this.isRemoved()) return;

        if (level().isClientSide) {
            if (tickCount % 4 == 0) {
                Vec3 v = getDeltaMovement();
                level().addParticle(ParticleTypes.WITCH,
                        getX() - v.x * 0.25,
                        getY() - v.y * 0.25 + 0.2,
                        getZ() - v.z * 0.25,
                        0, 0, 0);
            }
            return;
        }

        if (tickCount > MIN_FLIGHT_TICKS) {
            AABB box = getBoundingBox().inflate(PROXIMITY_RADIUS, PROXIMITY_RADIUS, PROXIMITY_RADIUS);
            Entity potionOwner = getOwner();
            for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box,
                    ent -> ent.isAlive() && ent != getOwner())) {

                // 只对阵营体系内的战斗单位做提前引爆判定
                if (!isCombatUnit(e)) continue;

                // 同族和平：不因原版灾厄生物 / 村民 / 铁傀儡而提前引爆。
                if (NativePeace.isPeacefulPair(potionOwner, e)) continue;

                // 治疗药水只认预定目标；路径上的其他友军不能让药水提前炸开。
                //   有害药水仍然遇到非友军提前结算，友军会直接穿过。
                boolean shouldTrigger = (kind == Kind.HEAL)
                        ? isIntendedTarget(e) : !isAlly(e);
                // 不允许穿墙：隔着墙的单位不会引爆药水
                if (shouldTrigger && hasLineOfSight(e)) {
                    applySplash();
                    this.discard();
                    return;
                }
            }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        if (level().isClientSide) return;
        // 命中判定阶段已经排除穿过的单位；这里再兜底一次，避免事件路径直接送入友军。
        if (result instanceof EntityHitResult entityHit
                && !canHitEntity(entityHit.getEntity())) return;

        applySplash();
        super.onHit(result);
        // ThrowableItemProjectile 不会自动销毁：不显式 discard 时，药水会带着
        //   当前速度继续穿过后面的方块，并且每 tick 重复结算。
        this.discard();
    }

    private boolean isIntendedTarget(Entity entity) {
        return intendedTargetUuid != null && intendedTargetUuid.equals(entity.getUUID());
    }

    private void applySplash() {
        if (!(level() instanceof ServerLevel sl)) return;

        double radius = arenaCombat
                ? ARENA_SPLASH_RADIUS : SPLASH_RADIUS;
        AABB box = getBoundingBox().inflate(radius, 2.0, radius);

        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0f, 1.0f);
        sl.sendParticles(ParticleTypes.WITCH,
                getX(), getY() + 0.3, getZ(),
                8, 0.6, 0.4, 0.6, 0.0);

        Entity owner = getOwner();
        if (arenaLingeringSkill
                && owner instanceof AbstractArmySoldier caster
                && caster.isArenaUnit()) {
            caster.arenaSkills().spawnWitchLingeringPotion(
                    sl, position(), arenaDamage,
                    ArenaSkillBalance.WITCH_LINGERING_RADIUS,
                    ArenaSkillBalance
                            .WITCH_LINGERING_DURATION_TICKS);
            return;
        }
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive())) {
            // 不允许穿墙：爆点与目标之间被方块挡住时不结算效果
            if (!hasLineOfSight(target)) continue;
            // 同族和平：原版灾厄生物 / 村民 / 铁傀儡按友军处理（只吃治疗，不吃减益）。
            boolean ally = isAlly(target)
                    || NativePeace.isPeacefulPair(owner, target);
            if (arenaCombat && caster == Caster.WITCH
                    && owner instanceof AbstractArmySoldier arenaOwner
                    && arenaOwner.isArenaUnit()) {
                if (!(target instanceof AbstractArmySoldier arenaTarget)
                        || !arenaTarget.isArenaUnit()) {
                    continue;
                }
                boolean arenaAlly = !arenaOwner.isArenaEnemy(arenaTarget);
                if (arenaAlly && kind == Kind.HEAL) {
                    arenaTarget.healFrom(arenaOwner, arenaHeal);
                } else if (!arenaAlly) {
                    arenaTarget.hurt(
                            damageSources().indirectMagic(
                                    this, arenaOwner),
                            arenaDamage);
                }
                continue;
            }
            if (ally) {
                if (caster == Caster.PRIEST) {
                    VillagerPriestSoldier.applyToAlly(target, tier, kind, this);
                } else {
                    WitchSoldier.applyToAlly(target, tier, kind, this);
                }
            } else {
                if (caster == Caster.PRIEST) {
                    VillagerPriestSoldier.applyToEnemy(target, tier, kind, this, owner);
                } else {
                    WitchSoldier.applyToEnemy(target, tier, kind, this, owner);
                }
            }
        }
    }

    /**
     * 药水不穿墙：从当前爆点向目标中心做一次方块遮挡检测。
     *   原版溅射药水可隔墙命中；这里要求被墙挡住的目标完全不吃效果。
     */
    private boolean hasLineOfSight(LivingEntity target) {
        Vec3 from = this.position();
        Vec3 to = target.getBoundingBox().getCenter();
        HitResult hit = level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS;
    }

    private boolean isAlly(LivingEntity e) {
        Entity projectileOwner = getOwner();
        if (projectileOwner instanceof AbstractArmySoldier arenaOwner
                && arenaOwner.isArenaUnit()
                && e instanceof AbstractArmySoldier arenaTarget
                && arenaTarget.isArenaUnit()) {
            return !arenaOwner.isArenaEnemy(arenaTarget);
        }
        if (commanderUuid != null) {
            if (e instanceof Player p && p.getUUID().equals(commanderUuid)) return true;
            if (e instanceof AbstractArmyCommander c && c.getUUID().equals(commanderUuid)) return true;
        }

        if (commanderUuid != null) {
            if (e instanceof AbstractArmySoldier s
                    && commanderUuid.equals(s.getCommanderUuid().orElse(null))) return true;
        }
        if (e instanceof WarHorseEntity horse) {
            UUID owner = horse.getOwnerUuid();
            if (owner != null && level() instanceof ServerLevel sl) {
                Entity ho = sl.getEntities().get(owner);
                if (ho instanceof AbstractArmySoldier s) {
                    if (commanderUuid != null
                            && commanderUuid.equals(s.getCommanderUuid().orElse(null))) return true;
                } else if (ho instanceof AbstractArmyCommander c) {
                    if (commanderUuid != null && c.getUUID().equals(commanderUuid)) return true;
                } else if (ho instanceof Player p) {
                    if (commanderUuid != null && p.getUUID().equals(commanderUuid)) return true;
                }
            }
        }

        if (factionId != null) {
            ResourceLocation targetFaction = ArmyThreatUtil.factionOf(e);
            if (targetFaction != null) {
                if (factionId.equals(targetFaction)) return true;
                if (FactionAlly.isAlly(factionId, targetFaction)) return true;
            }
        }

        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Kind", kind.name());
        tag.putString("Caster", caster.name());
        tag.putInt("Tier", tier);
        if (commanderUuid != null) tag.putUUID("Cmd", commanderUuid);
        if (intendedTargetUuid != null) tag.putUUID("IntendedTarget", intendedTargetUuid);
        if (factionId != null) tag.putString("Faction", factionId.toString());
        tag.putBoolean("ArenaCombat", arenaCombat);
        if (arenaMatchId != null) tag.putUUID("ArenaMatch", arenaMatchId);
        tag.putBoolean("ArenaLingeringSkill", arenaLingeringSkill);
        tag.putFloat("ArenaDamage", arenaDamage);
        tag.putFloat("ArenaHeal", arenaHeal);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        try { kind = Kind.valueOf(tag.getString("Kind")); }
        catch (Exception ignored) { kind = Kind.HARM; }
        try { caster = Caster.valueOf(tag.getString("Caster")); }
        catch (Exception ignored) { caster = Caster.WITCH; }
        tier = tag.getInt("Tier");
        if (tag.hasUUID("Cmd")) commanderUuid = tag.getUUID("Cmd");
        if (tag.hasUUID("IntendedTarget")) intendedTargetUuid = tag.getUUID("IntendedTarget");
        if (tag.contains("Faction")) factionId = ResourceLocation.tryParse(tag.getString("Faction"));
        arenaCombat = tag.getBoolean("ArenaCombat");
        arenaMatchId = tag.hasUUID("ArenaMatch")
                ? tag.getUUID("ArenaMatch") : null;
        arenaLingeringSkill = tag.getBoolean("ArenaLingeringSkill");
        arenaDamage = tag.getFloat("ArenaDamage");
        arenaHeal = tag.getFloat("ArenaHeal");
    }
}
