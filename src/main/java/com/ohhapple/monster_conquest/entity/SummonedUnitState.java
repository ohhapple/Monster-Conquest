package com.ohhapple.monster_conquest.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 士兵的可选召唤物属性。
 *
 * <p>该状态只负责召唤者绑定、失去召唤者后的游走锚点和存档，
 * 不决定士兵角色、等级、装备、骑乘、寻路或阵型。任何士兵都可以绑定召唤者；
 * 飞行单位也可以完全不绑定召唤者。</p>
 */
public final class SummonedUnitState {

    private static final int SUMMONER_MISSING_GRACE_TICKS = 60;

    @Nullable private UUID summonerUuid;
    @Nullable private Vec3 roamAnchor;
    private int summonerMissingTicks;

    public void setSummoner(@Nullable UUID uuid) {
        this.summonerUuid = uuid;
        this.summonerMissingTicks = 0;
    }

    public boolean isSummoned() {
        return summonerUuid != null;
    }

    @Nullable
    public UUID getSummonerUuid() {
        return summonerUuid;
    }

    public boolean isSummonerGone() {
        return summonerUuid == null
                || summonerMissingTicks > SUMMONER_MISSING_GRACE_TICKS;
    }

    /**
     * 区块卸载和召唤者死亡都必须经过宽限期：
     *   未加载的 UUID 不能立即被当成死亡，否则重载后会重复补召唤物。
     */
    public void tickSummonerPresence(boolean summonerLoaded, Vec3 currentPosition) {
        if (summonerUuid == null) return;
        if (summonerLoaded) {
            summonerMissingTicks = 0;
            return;
        }
        summonerMissingTicks++;
        if (summonerMissingTicks == SUMMONER_MISSING_GRACE_TICKS + 1) {
            roamAnchor = currentPosition;
        }
    }

    public void setRoamAnchor(@Nullable Vec3 anchor) {
        this.roamAnchor = anchor;
    }

    public boolean hasRoamAnchor() {
        return roamAnchor != null;
    }

    public Vec3 getRoamAnchor(Vec3 fallback) {
        return roamAnchor != null ? roamAnchor : fallback;
    }

    public void save(CompoundTag tag) {
        if (summonerUuid != null) tag.putUUID("SummonedBy", summonerUuid);
        if (roamAnchor != null) {
            tag.putDouble("SummonRoamX", roamAnchor.x);
            tag.putDouble("SummonRoamY", roamAnchor.y);
            tag.putDouble("SummonRoamZ", roamAnchor.z);
        }
    }

    public void load(CompoundTag tag) {
        summonerUuid = tag.hasUUID("SummonedBy") ? tag.getUUID("SummonedBy") : null;
        summonerMissingTicks = 0;
        roamAnchor = tag.contains("SummonRoamX")
                ? new Vec3(
                        tag.getDouble("SummonRoamX"),
                        tag.getDouble("SummonRoamY"),
                        tag.getDouble("SummonRoamZ"))
                : null;
    }
}
