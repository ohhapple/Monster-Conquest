package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.ModSounds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.world.damagesource.CombatRules;

/**
 * 枪械弹丸基类。
 * 与 ArmyArrow 相同：
 *   · 友军永远不出现在命中判定里 → 子弹直接穿过友军身体（包括结盟的玩家）
 *   · 命中友军时不 deflect、不减速、不 discard
 *   · 命中敌人时自己做伤害，然后 discard
 */
public abstract class ArmyGunProjectile extends ThrowableItemProjectile {

    /**
     * 初始速度同步：生成包的速度上限为 3.9 格/tick，高速子弹会被截断导致
     *   客户端弹道偏差；这里单独同步真实初速，客户端收到后还原。
     */
    private static final EntityDataAccessor<Float> DATA_INITIAL_SPEED =
            SynchedEntityData.defineId(ArmyGunProjectile.class, EntityDataSerializers.FLOAT);
    private static final double PACKET_SPEED_LIMIT = 3.9;
    private boolean packetSpeedApplied = false;

    private static final EntityDataAccessor<String> DATA_SHOOTER_FACTION =
            SynchedEntityData.defineId(ArmyGunProjectile.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SHOOTER_COMMANDER =
            SynchedEntityData.defineId(ArmyGunProjectile.class, EntityDataSerializers.OPTIONAL_UUID);

    @Nullable
    private UUID shooterCommanderUuid;
    @Nullable
    private ResourceLocation shooterFaction;

    private float baseDamage = 6.0f;
    /** 爆头伤害倍率（>1.0 时启用头部命中判定，玩家枪械使用）。 */
    private float headshotMultiplier = 1.0f;
    /** 穿甲比例（0~1）：无视目标护甲的百分比。 */
    private float armorPenetration = 0.0f;
    private float explosionRadius = 0.0f;
    private int maxLifeTicks = 100;
    /** 弹丸重力覆盖值（NaN = 使用子类默认值）。 */
    private double gravityOverride = Double.NaN;
    /** 最大飞行距离（格），0 = 不限制；超过后自动消失，避免弹丸无限累积。 */
    private double maxRange = 0.0;
    private double traveled = 0.0;

    protected ArmyGunProjectile(EntityType<? extends ArmyGunProjectile> type, Level level) {
        super(type, level);
    }

    protected ArmyGunProjectile(EntityType<? extends ArmyGunProjectile> type,
                                LivingEntity shooter, Level level) {
        super(type, shooter, level);
        captureShooter(shooter);
    }

    private void captureShooter(LivingEntity owner) {
        if (owner instanceof AbstractArmySoldier s) {
            this.shooterCommanderUuid = s.getCommanderUuid().orElse(null);
            this.shooterFaction = s.getFactionId();
        } else if (owner instanceof AbstractArmyCommander c) {
            this.shooterCommanderUuid = c.getUUID();
            this.shooterFaction = c.getFactionId();
        } else if (owner instanceof Player p) {
            this.shooterCommanderUuid = p.getUUID();
            this.shooterFaction = FactionIds.player(p.getUUID());
        }

        // 同步到客户端：客户端也要知道谁是友军，否则子弹会在客户端
        //   提前"撞"在友军身上消失，出现视觉错位。
        this.entityData.set(DATA_SHOOTER_COMMANDER,
                shooterCommanderUuid == null ? Optional.empty() : Optional.of(shooterCommanderUuid));
        this.entityData.set(DATA_SHOOTER_FACTION,
                shooterFaction == null ? "" : shooterFaction.toString());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SHOOTER_COMMANDER, Optional.empty());
        builder.define(DATA_SHOOTER_FACTION, "");
        builder.define(DATA_INITIAL_SPEED, 0.0f);
    }

    @Override
    public void shoot(double x, double y, double z, float velocity, float inaccuracy) {
        super.shoot(x, y, z, velocity, inaccuracy);
        if (!level().isClientSide) {
            this.entityData.set(DATA_INITIAL_SPEED, Math.max(0.001f, velocity));
        }
    }

    /** 把真实初速按比例压缩进 add 包（客户端再用同步的初速还原）。 */
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        Entity owner = getOwner();
        Vec3 pos = serverEntity.getPositionBase();
        Vec3 lastMovement = serverEntity.getLastSentMovement();

        float speed = this.entityData.get(DATA_INITIAL_SPEED);
        if (speed < 0.01f) {
            // 兜底：未通过 shoot() 生成时，用当前速度作为初速
            speed = (float) Math.max(0.001, lastMovement.length());
            this.entityData.set(DATA_INITIAL_SPEED, speed);
        }
        Vec3 movement = lastMovement.scale(PACKET_SPEED_LIMIT / speed);

        return new ClientboundAddEntityPacket(
                getId(), getUUID(),
                pos.x(), pos.y(), pos.z(),
                serverEntity.getLastSentXRot(), serverEntity.getLastSentYRot(),
                getType(), owner != null ? owner.getId() : 0,
                movement, serverEntity.getLastSentYHeadRot());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        Vec3 packetVelocity = new Vec3(packet.getXa(), packet.getYa(), packet.getZa());
        // 先还原成"单位方向"，等 DATA_INITIAL_SPEED 同步到位后再乘回真实速度
        setDeltaMovement(packetVelocity.scale(1.0 / PACKET_SPEED_LIMIT));
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_INITIAL_SPEED.equals(accessor) && level().isClientSide && !packetSpeedApplied) {
            float speed = this.entityData.get(DATA_INITIAL_SPEED);
            if (speed > 0.0f) {
                setDeltaMovement(getDeltaMovement().scale(speed));
                packetSpeedApplied = true;
            }
        }
    }

    public void setBaseDamage(float damage) { this.baseDamage = damage; }

    public void setHeadshotMultiplier(float multiplier) {
        this.headshotMultiplier = Math.max(1.0f, multiplier);
    }

    public void setArmorPenetration(float penetration) {
        this.armorPenetration = Math.max(0.0f, Math.min(1.0f, penetration));
    }

    public void setExplosionRadius(float radius) { this.explosionRadius = Math.max(0.0f, radius); }

    public void setMaxLifeTicks(int ticks) { this.maxLifeTicks = Math.max(1, ticks); }

    /** 覆盖弹丸重力（玩家枪械按各自的枪械数值设置）。 */
    public void setGravity(double gravity) { this.gravityOverride = gravity; }

    public void setMaxRange(double range) { this.maxRange = Math.max(0.0, range); }

    /** 子类默认重力。 */
    protected double defaultGravity() { return 0.01; }

    @Override
    protected double getDefaultGravity() {
        return Double.isNaN(gravityOverride) ? defaultGravity() : gravityOverride;
    }

    // ===================== 命中判定：友军不参与 =====================

    @Override
    protected boolean canHitEntity(Entity target) {
        if (isFriendly(target)) return false;
        return super.canHitEntity(target);
    }

    @Override
    protected ProjectileDeflection hitTargetOrDeflectSelf(HitResult hitResult) {
        if (hitResult.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hitResult).getEntity();
            if (isFriendly(entity)) {
                return ProjectileDeflection.NONE;
            }
        }
        return super.hitTargetOrDeflectSelf(hitResult);
    }

    @Override
    public boolean deflect(ProjectileDeflection deflection,
                           @Nullable Entity entity,
                           @Nullable Entity owner,
                           boolean b) {
        if (entity != null && isFriendly(entity)) return false;
        return super.deflect(deflection, entity, owner, b);
    }

    // ===================== 命中处理 =====================

    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity victim = result.getEntity();
        if (isFriendly(victim)) return;

        // 客户端只负责让弹丸消失，伤害由服务端结算。
        if (!(level() instanceof ServerLevel)) {
            this.discard();
            return;
        }

        if (explosionRadius > 0.0f) {
            // 直击伤害：火箭弹命中实体时先结算弹体直伤，再爆炸。
            //   爆炸会跳过被直击命中的实体，避免同一发火箭把同一目标算两次。
            if (victim instanceof LivingEntity direct) {
                applyDirectHit(direct);
            }
            explode(victim);
            this.discard();
            return;
        }

        if (!(victim instanceof LivingEntity living)) {
            this.discard();
            return;
        }

        // 清零无敌帧，保证高速弹必定结算伤害
        living.invulnerableTime = 0;

        Entity owner = this.getOwner();
        LivingEntity ownerLiving = owner instanceof LivingEntity le ? le : null;
        DamageSource source = ownerLiving != null
                ? this.damageSources().mobProjectile(this, ownerLiving)
                : this.damageSources().explosion(this, this);

        // 爆头判定：命中点高于身体高度 80% 的位置（约等于颈部以上）。
        //   实体命中时 EntityHitResult 不带交点，getLocation() 会退化成脚底坐标，
        //   据此判不出爆头，这里自己求弹道进入点。
        Vec3 hitPos = resolveImpactPoint(living);
        boolean headshot = headshotMultiplier > 1.0f
                && living.getBbHeight() >= 1.3
                && hitPos.y >= living.getY() + living.getBbHeight() * 0.8;
        float damage = headshot ? baseDamage * headshotMultiplier : baseDamage;
        damage = applyArmorPenetration(living, damage, source);

        if (ownerLiving != null) {
            ownerLiving.setLastHurtMob(living);
            // 子弹伤害计入射手的仇恨：受击方阵营会正确把射手列为威胁
            ThreatDispatcher.propagate(living, ownerLiving);
        }
        boolean damaged = living.hurt(source, damage);

        // 只对真正吃到伤害的目标追加击退：
        //   · NPC 骑兵的战马 hurt() 返回 false（伤害转嫁给骑手）→ 不推马，
        //     否则马+骑手会被反向甩向射手，出现"跳向玩家"的假击退；
        //   · knockback 的入参是"受击者 → 攻击者"方向，这里传弹道反方向，
        //     与近战 / 箭矢保持一致：把目标推离射手。
        if (damaged && !(living instanceof WarHorseEntity)) {
            double dx = this.getDeltaMovement().x;
            double dz = this.getDeltaMovement().z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (horiz > 0.001) {
                living.knockback(0.25, -dx / horiz, -dz / horiz);
            }
        }

        if (headshot && level() instanceof ServerLevel sl) {
            // 爆头反馈：命中点迸出暴击粒子 + 更高亢的命中音。
            sl.sendParticles(ParticleTypes.CRIT,
                    hitPos.x, hitPos.y, hitPos.z, 8, 0.12, 0.12, 0.12, 0.25);
            sl.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    hitPos.x, hitPos.y, hitPos.z, 4, 0.08, 0.08, 0.08, 0.15);
        }
        this.playSound(ModSounds.BULLET_HIT.get(), headshot ? 1.3f : 1.0f,
                (headshot ? 1.75f : 1.2f) / (this.random.nextFloat() * 0.2f + 0.9f));
        this.discard();
    }

    /**
     * 真实命中点：1.21 的实体命中结果只带实体，getLocation() 会退化成脚底坐标。
     *   这里用本 tick 的弹道线段（当前位置 → 下一位置）与目标碰撞箱求交，
     *   得到子弹真正进入身体的位置，用于爆头高度判定。
     */
    private Vec3 resolveImpactPoint(LivingEntity target) {
        Vec3 from = this.position();
        Vec3 step = this.getDeltaMovement();
        Vec3 to = from.add(step);

        Optional<Vec3> entry = target.getBoundingBox().inflate(0.3).clip(from, to);
        if (entry.isPresent()) return entry.get();

        // 兜底：线段与碰撞箱无交点时，取离目标中心最近的一点
        Vec3 center = target.getBoundingBox().getCenter();
        double lenSqr = step.lengthSqr();
        double t = lenSqr > 1.0E-6 ? center.subtract(from).dot(step) / lenSqr : 0.0;
        return from.add(step.scale(Mth.clamp(t, 0.0, 1.0)));
    }

    /**
     * 穿甲：期望伤害 = 护甲削减后的伤害 + 被护甲吃掉的伤害 × 穿甲比例。
     * 护甲减伤非线性，直接放大输入伤害会连锁放大穿透率，
     * 这里用二分反解输入伤害，使护甲结算后恰好等于期望伤害。
     */
    private float applyArmorPenetration(LivingEntity target, float damage, DamageSource source) {
        if (armorPenetration <= 0.0f) return damage;
        float armor = target.getArmorValue();
        if (armor <= 0.0f) return damage;

        float toughness = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float surviving = CombatRules.getDamageAfterAbsorb(target, damage, source, armor, toughness);
        if (surviving <= 0.0f || damage <= 0.0f) return damage;

        float desired = surviving + (damage - surviving) * armorPenetration;
        if (desired >= damage) return damage;

        // 上界：从原伤害出发逐步放大，直到护甲结算结果 ≥ 期望值
        float lo = desired;
        float hi = damage;
        for (int i = 0; i < 8
                && CombatRules.getDamageAfterAbsorb(target, hi, source, armor, toughness) < desired; i++) {
            hi *= 1.5f;
        }

        // 护甲结算在伤害轴上是连续单调的，二分 24 次即可精确到 1e-5 以内
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5f;
            if (CombatRules.getDamageAfterAbsorb(target, mid, source, armor, toughness) < desired) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (explosionRadius > 0.0f) {
            // 小体型生物常常没被高速弹的实体射线命中（火箭打在它们脚下的方块上），
            //   这里在落点附近补一次"贴脸直击"判定，保证直伤不会因为目标体积小而丢失。
            Entity direct = findNearbyDirectTarget(result.getLocation());
            if (direct instanceof LivingEntity living) {
                applyDirectHit(living);
            }
            explode(direct);
        } else if (level() instanceof ServerLevel) {
            this.playSound(ModSounds.BULLET_HIT.get(), 1.0f,
                    1.2f / (this.random.nextFloat() * 0.2f + 0.9f));
            // 命中方块：在落点溅射方块粒子
            if (level() instanceof ServerLevel sl) {
                var state = level().getBlockState(result.getBlockPos());
                if (!state.isAir()) {
                    Vec3 hit = result.getLocation();
                    sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                                    net.minecraft.core.particles.ParticleTypes.BLOCK, state),
                            hit.x, hit.y, hit.z, 12, 0.12, 0.12, 0.12, 0.12);
                }
            }
        }
        super.onHitBlock(result);
        this.discard();
    }

    /** 直击结算：清零无敌帧 → 仇恨传递 → 按穿甲系数结算直伤 → 命中音。 */
    private void applyDirectHit(LivingEntity target) {
        if (!(level() instanceof ServerLevel)) return;
        Entity owner = this.getOwner();
        LivingEntity ownerLiving = owner instanceof LivingEntity le ? le : null;
        DamageSource source = ownerLiving != null
                ? this.damageSources().mobProjectile(this, ownerLiving)
                : this.damageSources().explosion(this, this);

        target.invulnerableTime = 0;
        if (ownerLiving != null) {
            ownerLiving.setLastHurtMob(target);
            ThreatDispatcher.propagate(target, ownerLiving);
        }
        float damage = applyArmorPenetration(target, baseDamage, source);
        target.hurt(source, damage);
        this.playSound(ModSounds.BULLET_HIT.get(), 1.1f, 0.9f);
    }

    /**
     * 在爆炸落点附近寻找"本应被直击"的目标。
     * 用碰撞箱膨胀 + 最近距离判断，小体型生物（蜜蜂、银色鲷鱼、幼年怪等）也能可靠命中。
     */
    @Nullable
    private Entity findNearbyDirectTarget(Vec3 hit) {
        AABB box = new AABB(hit, hit).inflate(1.3, 1.0, 1.3);
        Entity owner = this.getOwner();
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!e.isAlive() || e == owner || isFriendly(e)) continue;
            double d = e.getBoundingBox().getCenter().distanceToSqr(hit);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 飞行音效（空气摩擦声），null = 不播放。 */
    @Nullable
    protected net.minecraft.sounds.SoundEvent getFlightSound() { return null; }

    protected int getFlightSoundInterval() { return 4; }

    protected float getFlightSoundVolume() { return 0.3f; }

    protected float getFlightSoundPitch() { return 1.0f; }

    /** 是否播放飞行音效（霰弹枪等多弹丸射击只让第一颗发声，避免音效叠加）。 */
    private boolean flightSoundEnabled = true;

    public void setFlightSoundEnabled(boolean enabled) { this.flightSoundEnabled = enabled; }

    /** 是否绘制拖尾粒子（多发弹丸只让第一颗绘制）。 */
    private boolean trailEnabled = true;

    public void setTrailEnabled(boolean enabled) { this.trailEnabled = enabled; }

    protected void explode() {
        explode(null);
    }

    /** 爆炸；exclude 命中的实体会被跳过（用于火箭弹直击目标，避免直伤 + 爆炸重复结算）。 */
    protected void explode(@Nullable Entity exclude) {
        if (!(level() instanceof ServerLevel sl)) return;

        float radius = explosionRadius;
        Entity owner = this.getOwner();
        LivingEntity ownerLiving = owner instanceof LivingEntity le ? le : null;
        DamageSource source = ownerLiving != null
                ? this.damageSources().mobProjectile(this, ownerLiving)
                : this.damageSources().explosion(this, this);

        AABB box = new AABB(
                getX() - radius, getY() - radius, getZ() - radius,
                getX() + radius, getY() + radius, getZ() + radius);
        List<LivingEntity> targets = level().getEntitiesOfClass(
                LivingEntity.class, box,
                e -> e.isAlive() && e != owner && e != exclude && !isFriendly(e));

        for (LivingEntity living : targets) {
            double dist = living.distanceTo(this);
            if (dist > radius) continue;

            double falloff = Math.max(0.0, 1.0 - (dist / radius) * 0.7);
            float dmg = (float) (baseDamage * falloff);
            if (dmg <= 0.0f) continue;
            // 爆炸伤害同样套用穿甲系数（与物品介绍里写的"穿甲"一致）
            dmg = applyArmorPenetration(living, dmg, source);

            // 军团火箭弹爆炸伤害不受无敌帧影响，每个目标都必定结算
            living.invulnerableTime = 0;

            if (ownerLiving != null) {
                ownerLiving.setLastHurtMob(living);
                ThreatDispatcher.propagate(living, ownerLiving);
            }
            if (living.hurt(source, dmg)) {
                double dx = living.getX() - getX();
                double dz = living.getZ() - getZ();
                double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                double strength = 0.8 * (1.0 - dist / radius) + 0.2;
                living.knockback(strength, -dx / len, -dz / len);
            }
        }

        // 爆炸特效：粒子数量与扩散范围都已收敛。
        //   中心保留一发 TNT 级爆闪，其余粒子只围绕爆心小范围铺开，
        //   不再按整个伤害半径铺满，避免视觉过载。
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        sl.sendParticles(ParticleTypes.EXPLOSION,
                getX(), getY() + 0.2, getZ(),
                Math.max(3, (int) (radius * 2.0)),
                radius * 0.30, radius * 0.25, radius * 0.30, 0.15);
        sl.sendParticles(ParticleTypes.FLAME,
                getX(), getY() + 0.2, getZ(),
                Math.max(3, (int) (radius * 1.5)),
                radius * 0.25, radius * 0.20, radius * 0.25, 0.05);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                getX(), getY() + 0.4, getZ(),
                Math.max(2, (int) (radius * 1.2)),
                radius * 0.35, radius * 0.30, radius * 0.35, 0.02);
        sl.playSound(null, getX(), getY(), getZ(),
                ModSounds.ROCKET_EXPLODE.get(), SoundSource.HOSTILE, 1.2f, 1.0f);
    }

    @Override
    public void tick() {
        double px = getX();
        double py = getY();
        double pz = getZ();

        super.tick();

        // 距离上限：飞够指定距离后直接清除（防止飞进未加载区域堆积）
        if (maxRange > 0.0 && !this.isRemoved()) {
            traveled += Math.sqrt((getX() - px) * (getX() - px)
                    + (getY() - py) * (getY() - py)
                    + (getZ() - pz) * (getZ() - pz));
            if (traveled > maxRange) {
                this.discard();
                return;
            }
        }

        // 拖尾粒子统一由服务端按权威位置生成（客户端预测位置会导致折线/拐弯）
        if (level().isClientSide) return;

        if (tickCount > maxLifeTicks) {
            this.discard();
            return;
        }

        if (trailEnabled && level() instanceof ServerLevel trailLevel) {
            spawnTrailParticles(trailLevel);
        }

        // 飞行音效：经过附近的玩家能听到轻微空气摩擦声
        net.minecraft.sounds.SoundEvent flightSound = getFlightSound();
        if (flightSoundEnabled && flightSound != null
                && tickCount % Math.max(1, getFlightSoundInterval()) == 0) {
            level().playSound(null, getX(), getY(), getZ(), flightSound,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    getFlightSoundVolume(), getFlightSoundPitch());
        }
    }

    /** 拖尾粒子（服务端按权威位置生成后广播给附近玩家）。 */
    protected abstract void spawnTrailParticles(ServerLevel level);

    // ===================== 友军判定 =====================

    private boolean isFriendly(Entity target) {
        return ArmyProjectileUtil.isFriendly(syncedCommander(), syncedFaction(), target);
    }

    @Nullable
    private UUID syncedCommander() {
        Optional<UUID> synced = this.entityData.get(DATA_SHOOTER_COMMANDER);
        if (synced.isPresent()) return synced.get();
        return shooterCommanderUuid;
    }

    @Nullable
    private ResourceLocation syncedFaction() {
        String s = this.entityData.get(DATA_SHOOTER_FACTION);
        if (s != null && !s.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) return rl;
        }
        return shooterFaction;
    }

    // ===================== 存档 =====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (shooterCommanderUuid != null) tag.putUUID("ShooterCommander", shooterCommanderUuid);
        if (shooterFaction != null) tag.putString("ShooterFaction", shooterFaction.toString());
        tag.putFloat("BaseDamage", baseDamage);
        tag.putFloat("HeadshotMultiplier", headshotMultiplier);
        tag.putFloat("ArmorPenetration", armorPenetration);
        tag.putFloat("ExplosionRadius", explosionRadius);
        tag.putInt("MaxLifeTicks", maxLifeTicks);
        if (!Double.isNaN(gravityOverride)) tag.putDouble("Gravity", gravityOverride);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ShooterCommander")) shooterCommanderUuid = tag.getUUID("ShooterCommander");
        if (tag.contains("ShooterFaction")) {
            shooterFaction = ResourceLocation.tryParse(tag.getString("ShooterFaction"));
        }
        if (tag.contains("BaseDamage")) baseDamage = tag.getFloat("BaseDamage");
        if (tag.contains("HeadshotMultiplier")) headshotMultiplier = tag.getFloat("HeadshotMultiplier");
        if (tag.contains("ArmorPenetration")) armorPenetration = tag.getFloat("ArmorPenetration");
        if (tag.contains("ExplosionRadius")) explosionRadius = tag.getFloat("ExplosionRadius");
        if (tag.contains("MaxLifeTicks")) maxLifeTicks = tag.getInt("MaxLifeTicks");
        if (tag.contains("Gravity")) gravityOverride = tag.getDouble("Gravity");
    }
}
