package com.ohhapple.monster_conquest.arena.entity;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

public final class ArenaPig extends ArenaAnimalSoldier {

    private static final EntityDataAccessor<Boolean> DATA_ZOMBIFIED =
            SynchedEntityData.defineId(
                    ArenaPig.class,
                    EntityDataSerializers.BOOLEAN);

    public ArenaPig(EntityType<? extends PathfinderMob> type,
                    Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(
            SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ZOMBIFIED, false);
    }

    @Override
    public int getRenderIndex() {
        return 42;
    }

    @Override
    public double getMovementSpeed() {
        return 0.30;
    }

    public boolean isZombified() {
        return entityData.get(DATA_ZOMBIFIED);
    }

    public void setZombified(boolean zombified) {
        if (isZombified() != zombified) {
            entityData.set(DATA_ZOMBIFIED, zombified);
            refreshDimensions();
        }
    }

    @Override
    protected EntityDimensions getDefaultDimensions(Pose pose) {
        if (isZombified()) {
            return EntityDimensions.scalable(0.6f, 1.95f)
                    .withEyeHeight(1.8f);
        }
        return super.getDefaultDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(
            EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_ZOMBIFIED.equals(accessor)) {
            refreshDimensions();
        }
    }
}
