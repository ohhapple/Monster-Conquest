package com.ohhapple.monster_conquest.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 袭击锚点（Raid Anchor）：本模组袭击小队在袭击系统中的"载体"。
 * 它继承原版掠夺者（Raider），因此可以用原版公开 API 加入袭击，
 * 让本模组的小队被计入「剩余袭击者」、袭击进度条与敲钟高亮等原版机制；
 * 实体本身则完全不可交互：
 *   · 客户端注册 NoopRenderer —— 任何情况下都不渲染（包括发光轮廓）；
 *   · 无 AI、无重力、无碰撞、无声、隐身、无敌，永远不会被索敌或攻击；
 *   · 只由 RaidSpawner 在袭击期间生成与回收，正常游玩不会被玩家察觉。
 */
public class RaidAnchorEntity extends Pillager {

    public RaidAnchorEntity(EntityType<? extends Pillager> type, Level level) {
        super(type, level);
        enforceAnchorState();
    }

    /**
     * 强制锚点状态：不可见 / 无敌 / 无声 / 无重力 / 无碰撞 / 静止。
     *   构造时与每次维护时都会调用，即使被其它 mod 改动也会被恢复。
     */
    public void enforceAnchorState() {
        this.setNoAi(true);
        this.setInvulnerable(true);
        this.setInvisible(true);
        this.setSilent(true);
        this.setNoGravity(true);
        this.noPhysics = true;
        this.setDeltaMovement(Vec3.ZERO);
        this.setPersistenceRequired();
    }

    /** 不注册任何 AI goal。 */
    @Override
    protected void registerGoals() { }

    /** 完全不执行 AI / 移动，保持原地静止。 */
    @Override
    public void aiStep() { }

    /**
     * 免疫一切普通伤害（攻击 / 爆炸 / 投射物 / 火焰 / 溺水等）；
     * 只有带「无视无敌」标签的伤害（例如 /kill）才放行，方便手动清理。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean canBeSeenAsEnemy() { return false; }

    /** 和平难度下也不清除（袭击结束由 RaidSpawner 统一回收）。 */
    @Override
    protected boolean shouldDespawnInPeaceful() { return false; }
}
