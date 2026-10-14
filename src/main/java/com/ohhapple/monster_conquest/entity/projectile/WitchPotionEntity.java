package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.army.threat.ArmyThreatUtil;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class WitchPotionEntity extends ThrowableItemProjectile {

    public enum Kind { HEAL, POISON, HARM, WEAKEN }

    /**
     * 投掷者：决定药水结算走哪套"效果入口"。
     *   WITCH  → {@link WitchSoldier}（再生 / 吸收 / 随机三药）
     *   PRIEST → {@link VillagerPriestSoldier}（净化 + 少量回血 / 只有中毒）
     */
    public enum Caster { WITCH, PRIEST }

    public static final double SPLASH_RADIUS = 4.0;
    private static final double PROXIMITY_RADIUS = 2.0;
    private static final int MIN_FLIGHT_TICKS = 3;

    public static final double FLIGHT_GRAVITY = 0.03;
    public static final double FLIGHT_DRAG    = 0.99;

    private Kind kind = Kind.HARM;
    private Caster caster = Caster.WITCH;
    private int tier = 0;
    @Nullable private UUID commanderUuid;
    @Nullable private ResourceLocation factionId;

    public WitchPotionEntity(EntityType<? extends WitchPotionEntity> type, Level level) {
        super(type, level);
    }

    public WitchPotionEntity(Level level, LivingEntity shooter) {
        super(ModEntities.WITCH_POTION.get(), shooter, level);
        if (shooter instanceof AbstractArmySoldier s) {
            this.commanderUuid = s.getCommanderUuid().orElse(null);
            this.factionId = s.getFactionId();
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

    @Override
    protected double getDefaultGravity() {
        return FLIGHT_GRAVITY;
    }

    @Override
    protected Item getDefaultItem() { return Items.SPLASH_POTION; }

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

    @Override
    public void tick() {
        super.tick();

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

                boolean shouldTrigger = (kind == Kind.HEAL) ? isAlly(e) : !isAlly(e);
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
        if (!level().isClientSide) applySplash();
        super.onHit(result);
    }

    private void applySplash() {
        if (!(level() instanceof ServerLevel sl)) return;

        AABB box = getBoundingBox().inflate(SPLASH_RADIUS, 2.0, SPLASH_RADIUS);

        sl.playSound(null, getX(), getY(), getZ(),
                SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0f, 1.0f);
        sl.sendParticles(ParticleTypes.WITCH,
                getX(), getY() + 0.3, getZ(),
                8, 0.6, 0.4, 0.6, 0.0);

        Entity owner = getOwner();
        for (LivingEntity target : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive())) {
            // 不允许穿墙：爆点与目标之间被方块挡住时不结算效果
            if (!hasLineOfSight(target)) continue;
            // 同族和平：原版灾厄生物 / 村民 / 铁傀儡按友军处理（只吃治疗，不吃减益）。
            if (isAlly(target) || NativePeace.isPeacefulPair(owner, target)) {
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
        if (factionId != null) tag.putString("Faction", factionId.toString());
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
        if (tag.contains("Faction")) factionId = ResourceLocation.tryParse(tag.getString("Faction"));
    }
}
