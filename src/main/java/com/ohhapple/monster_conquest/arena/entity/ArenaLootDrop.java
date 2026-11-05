package com.ohhapple.monster_conquest.arena.entity;

import com.ohhapple.monster_conquest.arena.game.ArenaMatchManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * 竞技场战利品展示实体。未结算奖励在 8 秒生命周期结束时按
 * matchId 与 ownerId 入包；即时经济奖励可先入包，再仅用实体展示。
 */
public final class ArenaLootDrop extends Entity {

    public static final int TYPE_GOLD = 0;
    public static final int TYPE_EQUIPMENT = 1;
    public static final int LIFETIME_TICKS = 8 * 20;
    private static final double GRAVITY = 0.04;
    private static final double HORIZONTAL_DRAG = 0.88;
    private static final double AIR_DRAG = 0.98;

    private static final EntityDataAccessor<Integer> DATA_DROP_TYPE =
            SynchedEntityData.defineId(
                    ArenaLootDrop.class,
                    EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VISUAL_PAYLOAD =
            SynchedEntityData.defineId(
                    ArenaLootDrop.class,
                    EntityDataSerializers.INT);

    private UUID ownerId;
    private UUID matchId;
    private int rewardPayload;
    private boolean rewardSettled;
    private boolean claimed;

    public ArenaLootDrop(EntityType<? extends ArenaLootDrop> type,
                         Level level) {
        super(type, level);
        setNoGravity(false);
        setInvulnerable(true);
        setSilent(true);
        noPhysics = false;
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        builder.define(DATA_DROP_TYPE, TYPE_GOLD);
        builder.define(DATA_VISUAL_PAYLOAD, 0);
    }

    public void configure(UUID matchId, UUID ownerId,
                          int rewardType, int rewardPayload) {
        configure(matchId, ownerId, rewardType, rewardPayload, false);
    }

    public void configure(UUID matchId, UUID ownerId,
                          int rewardType, int rewardPayload,
                          boolean rewardSettled) {
        this.matchId = matchId;
        this.ownerId = ownerId;
        this.rewardPayload = rewardPayload;
        this.rewardSettled = rewardSettled;
        int dropType = rewardType == TYPE_EQUIPMENT
                ? TYPE_EQUIPMENT : TYPE_GOLD;
        this.entityData.set(DATA_DROP_TYPE, dropType);
        this.entityData.set(DATA_VISUAL_PAYLOAD,
                dropType == TYPE_EQUIPMENT
                        ? Math.max(0, rewardPayload) : 0);
    }

    public int dropType() {
        return entityData.get(DATA_DROP_TYPE);
    }

    public boolean rewardSettled() {
        return rewardSettled;
    }

    public int visualPayload() {
        return entityData.get(DATA_VISUAL_PAYLOAD);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;
        tickFalling();
        if (tickCount >= LIFETIME_TICKS) {
            expireNow();
        }
    }

    /**
     * 战利品只由服务端推进物理并同步位置；客户端保持位置插值，
     * 避免两端独立重力造成落地高度不一致。
     */
    private void tickFalling() {
        Vec3 movement = getDeltaMovement();
        double vertical = onGround()
                ? 0.0
                : Math.max(-1.2, (movement.y - GRAVITY) * AIR_DRAG);
        move(MoverType.SELF, new Vec3(
                movement.x * HORIZONTAL_DRAG,
                vertical,
                movement.z * HORIZONTAL_DRAG));
        if (onGround() || verticalCollision) {
            setDeltaMovement(Vec3.ZERO);
        } else {
            setDeltaMovement(
                    movement.x * HORIZONTAL_DRAG,
                    vertical,
                    movement.z * HORIZONTAL_DRAG);
        }
    }

    public void expireNow() {
        if (claimed) return;
        claimed = true;
        if (!rewardSettled && !level().isClientSide
                && matchId != null && ownerId != null) {
            ArenaMatchManager.claimArenaLoot(
                    matchId, ownerId, dropType(), rewardPayload);
        }
        discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("DropType", dropType());
        tag.putInt("RewardPayload", rewardPayload);
        tag.putBoolean("RewardSettled", rewardSettled);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (matchId != null) tag.putUUID("Match", matchId);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        int dropType = tag.getInt("DropType") == TYPE_EQUIPMENT
                ? TYPE_EQUIPMENT : TYPE_GOLD;
        entityData.set(DATA_DROP_TYPE, dropType);
        rewardPayload = tag.getInt("RewardPayload");
        entityData.set(DATA_VISUAL_PAYLOAD,
                dropType == TYPE_EQUIPMENT
                        ? Math.max(0, rewardPayload) : 0);
        rewardSettled = tag.getBoolean("RewardSettled");
        ownerId = tag.hasUUID("Owner")
                ? tag.getUUID("Owner") : null;
        matchId = tag.hasUUID("Match")
                ? tag.getUUID("Match") : null;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
