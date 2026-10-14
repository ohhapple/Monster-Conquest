package com.ohhapple.monster_conquest.client.rts;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

public class VirtualCamera extends Entity {

    public VirtualCamera(Level level) {
        super(EntityType.MARKER, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder b) {}
    @Override protected void readAdditionalSaveData(CompoundTag t) {}
    @Override protected void addAdditionalSaveData(CompoundTag t) {}
    @Override public boolean shouldRenderAtSqrDistance(double d) { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean isSpectator() { return true; }
}