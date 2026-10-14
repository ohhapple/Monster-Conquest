package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.item.WarmClothesItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 军团箭矢：自研命中判定与命中处理 —— 友军不参与命中、不被 deflect；
 * 命中敌人时自行结算伤害、击退与后效后销毁箭矢。
 */
public class ArmyArrow extends Arrow {

    private static final String KEY_LANDED_AT_TICK = "MC_LandedAtTick";

    private static final EntityDataAccessor<String> DATA_SHOOTER_FACTION =
            SynchedEntityData.defineId(ArmyArrow.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Optional<UUID>> DATA_SHOOTER_COMMANDER =
            SynchedEntityData.defineId(ArmyArrow.class, EntityDataSerializers.OPTIONAL_UUID);
    /** 命中时注入的"细雪冻结" tick（0 = 无冻结效果）。 */
    private static final EntityDataAccessor<Integer> DATA_FREEZE_TICKS =
            SynchedEntityData.defineId(ArmyArrow.class, EntityDataSerializers.INT);

    /** 叠加冻结 tick 的上限，避免多箭叠出荒唐的冻结时长。 */
    /** 冻结 tick 上限（衰减 2/tick → 800 约等于 20 秒）。 */
    private static final int MAX_TOTAL_FREEZE_TICKS = 800;

    @Nullable
    private UUID shooterCommanderUuid;
    @Nullable
    private ResourceLocation shooterFaction;

    public ArmyArrow(EntityType<? extends Arrow> type, Level level) {
        super(type, level);
    }

    public ArmyArrow(Level level, LivingEntity owner, ItemStack pickupItemStack,
                     @Nullable ItemStack weapon) {
        super(ModEntities.ARMY_ARROW.get(), level);
        // 等价于 AbstractArrow(EntityType, LivingEntity, Level, ItemStack, ItemStack)，
        // 但 super() 只能调用直接父类 Arrow 的构造器，因此这里手动补齐初始化。
        this.setPickupItemStack(pickupItemStack.copy());
        this.setCustomName(pickupItemStack.get(DataComponents.CUSTOM_NAME));
        this.setPos(owner.getX(), owner.getEyeY() - 0.1F, owner.getZ());
        this.setOwner(owner);
        captureShooter(owner);
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

        // 同步到客户端：客户端也要知道谁是友军，否则箭矢会在客户端
        //   提前撞在友军身上弹开 / 消失，出现"被队友挡住"的视觉错位。
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
        builder.define(DATA_FREEZE_TICKS, 0);
    }

    /**
     * 设置命中敌人时注入的细雪冻结 tick。
     * 由村民弓箭手在 {@code applyArrowEffects} 里调用（其他箭矢保持 0，行为不变）。
     */
    public void setFreezeTicks(int ticks) {
        this.entityData.set(DATA_FREEZE_TICKS, Math.max(0, ticks));
    }

    public int getFreezeTicks() {
        return this.entityData.get(DATA_FREEZE_TICKS);
    }

    // ===================== 第一层：命中判定 =====================

    @Override
    protected boolean canHitEntity(Entity target) {
        if (isFriendly(target)) return false;
        return super.canHitEntity(target);
    }

    // ===================== 第二层：自研实体命中检测 =====================

    @Override
    @Nullable
    protected EntityHitResult findHitEntity(Vec3 startVec, Vec3 endVec) {
        Vec3 move = endVec.subtract(startVec);
        AABB search = this.getBoundingBox()
                .expandTowards(move)
                .inflate(1.0);

        List<Entity> candidates = this.level().getEntities(
                this,
                search,
                e -> e != null
                        && !e.isSpectator()
                        && e.isPickable()
                        && e.canBeHitByProjectile()
                        && !isFriendly(e));

        double bestDistSq = Double.MAX_VALUE;
        Entity bestEntity = null;
        Vec3 bestHit = null;

        for (Entity e : candidates) {
            AABB box = e.getBoundingBox().inflate(0.3);
            Optional<Vec3> clip = box.clip(startVec, endVec);
            if (clip.isEmpty()) continue;
            Vec3 hit = clip.get();
            double d = startVec.distanceToSqr(hit);
            if (d < bestDistSq) {
                bestDistSq = d;
                bestEntity = e;
                bestHit = hit;
            }
        }

        if (bestEntity == null) return null;
        return new EntityHitResult(bestEntity, bestHit);
    }

    // ===================== 第三层：自研命中处理（不调用 super） =====================

    /**
     * 完全自己实现，不调用 super.onHitEntity。
     *
     *   这样即使因为竞态或漏判而命中友军，也永远不会触发
     *   AbstractArrow.onHitEntity 里的 deflect(REVERSE) 分支 ——
     *   友军直接静默返回，速度、方向、生命周期全部不变。
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity victim = result.getEntity();

        // 友军：任何状态都不改，箭矢继续飞（不 discard）
        if (isFriendly(victim)) return;

        // 客户端只负责让箭矢消失，伤害由服务端结算。
        if (!(level() instanceof ServerLevel)) {
            this.discard();
            return;
        }

        if (!(victim instanceof LivingEntity living)) {
            // 非生物实体（罕见），直接丢弃
            this.discard();
            return;
        }

        selfHandleHit(living);
        this.discard();
    }

    /**
     * 自己实现原版 AbstractArrow.onHitEntity 的"命中成功"分支。
     * 不处理 deflect(REVERSE) —— 因为走到这里一定是敌人。
     */
    private void selfHandleHit(LivingEntity living) {
        // 清零无敌帧（保持原版行为，让箭矢必定造成伤害）
        living.invulnerableTime = 0;

        double base = this.getBaseDamage();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().arrow(
                this, owner != null ? owner : this);

        // 附魔伤害修正
        if (this.getWeaponItem() != null && this.level() instanceof ServerLevel sl) {
            base = EnchantmentHelper.modifyDamage(
                    sl, this.getWeaponItem(), living, source, (float) base);
        }

        float f = (float) this.getDeltaMovement().length();
        int dmg = Mth.ceil(Mth.clamp(f * base, 0.0, Integer.MAX_VALUE));

        // 暴击
        if (this.isCritArrow()) {
            long k = this.random.nextInt(dmg / 2 + 2);
            dmg = (int) Math.min(k + (long) dmg, Integer.MAX_VALUE);
        }

        // 记录 owner 的 lastHurtMob
        if (owner instanceof LivingEntity le) {
            le.setLastHurtMob(living);
        }

        // 火焰
        boolean isEnderman = living.getType() == EntityType.ENDERMAN;
        int prevFireTicks = living.getRemainingFireTicks();
        if (this.isOnFire() && !isEnderman) {
            living.igniteForSeconds(5.0F);
        }

        if (living.hurt(source, dmg)) {
            // 命中成功
            if (!this.level().isClientSide && !isEnderman) {
                living.setArrowCount(living.getArrowCount() + 1);
            }

            // 击退
            this.doKnockback(living, source);

            // 附魔后效
            if (this.level() instanceof ServerLevel sl) {
                EnchantmentHelper.doPostAttackEffectsWithItemSource(
                        sl, living, source, this.getWeaponItem());
            }

            // 子类后效（药水等）
            this.doPostHurtEffects(living);

            // 命中音效
            this.playSound(this.getHitGroundSoundEvent(),
                    1.0F, 1.2F / (this.random.nextFloat() * 0.2F + 0.9F));
        } else {
            // 命中失败（无敌帧/创造模式玩家等）—— 恢复火焰状态
            living.setRemainingFireTicks(prevFireTicks);
        }

        // 细雪冻结：命中即结算，与伤害是否成功无关（NPC 战马 hurt() 返回 false）
        applyFreeze(living);
    }

    /**
     * 细雪冻结：所有命中目标都叠加冻结 tick（不按 canFreeze 过滤，
     *   缺少的冻结伤害由 ModEvents 补齐）；装备保暖衣的目标免疫（骑手穿了，坐骑同样免疫）。
     *   射中骑手或坐骑时两者一起冻结。
     */
    private void applyFreeze(LivingEntity hit) {
        int freeze = getFreezeTicks();
        if (freeze <= 0) return;

        applyFreezeTo(hit, freeze);

        // 骑兵：马与骑手一起冻结（只联动一层，不会重复叠加）
        if (hit.getVehicle() instanceof LivingEntity mount && mount != hit) {
            applyFreezeTo(mount, freeze);
        }
        for (Entity passenger : hit.getPassengers()) {
            if (passenger instanceof LivingEntity rider) applyFreezeTo(rider, freeze);
        }
    }

    /** 对单个目标叠加冻结 tick（保暖衣玩家免疫）。 */
    private void applyFreezeTo(LivingEntity target, int freeze) {
        // 保暖衣：战术装备栏内的玩家 / 单位免疫冻结效果；骑手穿了则坐骑也免疫
        if (WarmClothesItem.isFrostImmune(target)) return;

        int total = Math.min(target.getTicksFrozen() + freeze, MAX_TOTAL_FREEZE_TICKS);
        target.setTicksFrozen(total);
    }

    /**
     * 第四层防护：盾牌 deflection 拦截。
     *   MC 在 onHit 之前会先调 entity.deflection(this)：
     *     拿盾的 LivingEntity → DEFLECT
     *   友军一旦被 deflect，箭矢会被弹开，即使 onHitEntity 被覆盖也没用。
     *   这里先拦截友军，直接返回 NONE，箭矢继续飞。
     */
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

    // ===================== 兜底：deflect 覆盖 =====================

    @Override
    public boolean deflect(ProjectileDeflection deflection,
                           @Nullable Entity entity,
                           @Nullable Entity owner,
                           boolean b) {
        // 友军不 deflect
        if (entity != null && isFriendly(entity)) return false;
        return super.deflect(deflection, entity, owner, b);
    }

    // ===================== tick =====================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        CompoundTag pd = getPersistentData();

        if (this.inGround) {
            if (!pd.contains(KEY_LANDED_AT_TICK)) {
                pd.putInt(KEY_LANDED_AT_TICK, tickCount);
                return;
            }
            int landedAt = pd.getInt(KEY_LANDED_AT_TICK);
            if (tickCount - landedAt > CombatConfig.arrowLingerTicks) {
                this.discard();
            }
        } else {
            pd.remove(KEY_LANDED_AT_TICK);
        }
    }

    // ===================== 友军判定 =====================

    private boolean isFriendly(Entity target) {
        return ArmyProjectileUtil.isFriendly(syncedCommander(), syncedFaction(), target);
    }

    @Nullable
    private UUID syncedCommander() {
        Optional<UUID> synced = this.entityData.get(DATA_SHOOTER_COMMANDER);
        return synced.isPresent() ? synced.get() : shooterCommanderUuid;
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
        tag.putInt("MC_FreezeTicks", getFreezeTicks());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ShooterCommander")) shooterCommanderUuid = tag.getUUID("ShooterCommander");
        if (tag.contains("ShooterFaction")) {
            shooterFaction = ResourceLocation.tryParse(tag.getString("ShooterFaction"));
        }
        setFreezeTicks(tag.getInt("MC_FreezeTicks"));
    }
}
