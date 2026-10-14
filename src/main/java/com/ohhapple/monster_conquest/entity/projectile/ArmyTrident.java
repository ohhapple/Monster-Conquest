package com.ohhapple.monster_conquest.entity.projectile;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.entity.projectile.ThrownTrident;
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
import java.util.function.Consumer;

/**
 * 军团三叉戟。同 ArmyArrow：完全自研命中处理，不调 super.onHitEntity。
 */
public class ArmyTrident extends ThrownTrident {

    private static final String KEY_LANDED_AT_TICK = "MC_LandedAtTick";

    @Nullable
    private Consumer<LivingEntity> onHitCallback;

    @Nullable private UUID shooterCommanderUuid;
    @Nullable private ResourceLocation shooterFaction;

    public ArmyTrident(EntityType<? extends ThrownTrident> type, Level level) {
        super(type, level);
        this.pickup = Pickup.DISALLOWED;
    }

    public void setOnHitCallback(@Nullable Consumer<LivingEntity> callback) {
        this.onHitCallback = callback;
    }

    @Override
    public void setOwner(@Nullable Entity owner) {
        super.setOwner(owner);
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
    }

    @Override
    protected boolean canHitEntity(Entity target) {
        if (isFriendly(target)) return false;
        return super.canHitEntity(target);
    }

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

    /**
     * 自己实现，不调 super。友军直接返回，敌人自己做伤害 + 回调 + discard。
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        Entity victim = result.getEntity();

        if (isFriendly(victim)) return;

        // 客户端只负责让三叉戟消失，伤害由服务端结算。
        if (!(level() instanceof ServerLevel)) {
            this.discard();
            return;
        }

        if (!(victim instanceof LivingEntity living)) {
            this.discard();
            return;
        }

        living.invulnerableTime = 0;

        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().trident(
                this, owner != null ? owner : this);
        float dmg = (float) this.getBaseDamage();

        if (this.level() instanceof ServerLevel sl) {
            dmg = EnchantmentHelper.modifyDamage(
                    sl, this.getPickupItemStackOrigin(), living, source, dmg);
        }

        if (living.hurt(source, dmg)) {
            if (living.getType() != EntityType.ENDERMAN) {
                if (this.level() instanceof ServerLevel sl) {
                    EnchantmentHelper.doPostAttackEffectsWithItemSource(
                            sl, living, source, this.getPickupItemStackOrigin());
                }
                this.doKnockback(living, source);
                this.doPostHurtEffects(living);
            }
        } else {
            // hurt 失败（无敌帧 / 创造模式等）—— 不做 deflect，避免"被挡"错觉
        }

        // 命中回调（例如碎甲）
        if (onHitCallback != null) {
            onHitCallback.accept(living);
        }

        // 命中声音
        this.playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);

        this.discard();
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

    private boolean isFriendly(Entity target) {
        return ArmyProjectileUtil.isFriendly(shooterCommanderUuid, shooterFaction, target);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (shooterCommanderUuid != null) tag.putUUID("ShooterCommander", shooterCommanderUuid);
        if (shooterFaction != null) tag.putString("ShooterFaction", shooterFaction.toString());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("ShooterCommander")) shooterCommanderUuid = tag.getUUID("ShooterCommander");
        if (tag.contains("ShooterFaction")) {
            shooterFaction = ResourceLocation.tryParse(tag.getString("ShooterFaction"));
        }
    }
}
