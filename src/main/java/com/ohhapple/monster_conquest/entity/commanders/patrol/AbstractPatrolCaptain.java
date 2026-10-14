package com.ohhapple.monster_conquest.entity.commanders.patrol;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

/**
 * 巡逻队长：独立小队指挥官。生成时召唤 3 个跟随士兵，
 * 麾下不足 3 人时可以把附近同阵营的孤儿士兵收编进小队（总数不超过 3，不自动补兵），
 * 队长存活时其士兵不被其他首领招募，队长死亡后士兵可被同阵营首领 / 巡逻队长收编。
 */
public abstract class AbstractPatrolCaptain extends AbstractArmyCommander {

    /** 巡逻队固定人数。 */
    public static final int PATROL_SOLDIER_COUNT = 3;

    /** 是否已完成初始 3 人小队召唤（持久化，避免读档后重复召唤）。 */
    private boolean initialSquadSpawned = false;

    protected AbstractPatrolCaptain(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
    }

    // ===================== 不参与首领体系 =====================

    /** 巡逻队长不招募怪物。 */
    @Override
    protected void tryRecruit() { }

    /**
     * 巡逻队长：麾下士兵不足 3 名时，可以把附近同阵营的孤儿士兵纳入小队；
     *   携带的士兵总数不会超过 PATROL_SOLDIER_COUNT（3）。
     */
    @Override
    protected int recruitStrayCapacity() { return PATROL_SOLDIER_COUNT; }

    /** 巡逻队长不走 Config 自动补兵。 */
    @Override
    protected void trySummonIfFew() { }

    /**
     * 野外自然刷出的巡逻队长：远离玩家后像普通怪物一样消失（不往存档里堆积）；
     *   村庄 / 袭击锚定的巡逻队与刷怪蛋、指令刷出的队长保持常驻。
     */
    @Override
    public boolean removeWhenFarAway(double distance) { return isWild(); }

    // ===================== Goals =====================

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // 巡逻队长行为：随机漫游
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
    }

    // ===================== Tick =====================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // 只召唤一次：3 个士兵随队长出生
        //   不走 finalizeSpawn —— 刷怪蛋 / addFreshEntity 都不触发它
        if (!initialSquadSpawned) {
            initialSquadSpawned = true;
            for (int i = 0; i < PATROL_SOLDIER_COUNT; i++) {
                summonRandomSoldier();
            }
        }
    }

    // ===================== NBT =====================

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("PatrolInit", initialSquadSpawned);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        initialSquadSpawned = tag.getBoolean("PatrolInit");
    }
}
