package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.effect.GrievousWoundEffect;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

/**
 * 唤魔者士兵（魔法师，灾厄阵营；也有骑兵模式）。
 *
 *   · 召唤幻魔者尖牙：目标在 20 格内时，直接在目标脚下召唤尖牙，
 *     尖牙的 owner = 本士兵 → 伤害归属、仇恨吸引全部记在士兵身上。
 *   · 技能冷却：由 {@link com.ohhapple.monster_conquest.ai.soldier.EvokerSpellGoal}
 *     由 {@code EvokerSpellGoal.FANG_COOLDOWN_TICKS} 控制（2 秒）。
 *   · 重伤：被尖牙命中 / 被近战命中的敌人获得"重伤"buff，
 *     等级 = 士兵装备等级（0 级 -10% 治疗，每级 +10%，上限 100%）。
 */
public class EvokerSoldier extends AbstractArmySoldier {

    /** 可在目标脚下召唤尖牙的最大距离（格）：目标在此距离内直接砸脚下。 */
    public static final double FANG_CAST_RANGE = 20.0;

    /** 尖牙从生成到扎出伤害的延迟（tick），用于按目标速度预判落点。 */
    private static final double FANG_TRIGGER_DELAY_TICKS = 8.0;
    /** 预判提前量上限（格），避免高速目标把落点甩得太远。 */
    private static final double MAX_LEAD = 2.5;
    /**
     * 脚下尖牙簇：中心 + 四向偏移，覆盖目标脚下的一小片区域。
     * 同一次施法的尖牙同 tick 触发 → 受无敌帧限制只会结算一次伤害，
     * 因此扩大覆盖面不会造成伤害翻倍，只是提高命中率。
     */
    private static final double[][] FANG_FEET_OFFSETS = {
            {0.0, 0.0}, {0.8, 0.0}, {-0.8, 0.0}, {0.0, 0.8}, {0.0, -0.8}
    };

    /**
     * 重伤时长（tick），与溺尸士兵的破甲保持一致：
     *   TIER_0 = 200 tick = 10 秒，每提升一级 +60 tick（3 秒）。
     *     TIER_0 → 10s / TIER_1 → 13s / TIER_2 → 16s / TIER_3 → 19s / TIER_4 → 22s
     */
    private static final int WOUND_BASE_DURATION = 200;
    private static final int WOUND_DURATION_PER_TIER = 60;

    public EvokerSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MAGE_INFANTRY; }
    @Override public int getRenderIndex() { return 14; }

    // ===================== 装备等级基础数值 =====================

    @Override public double getMaxHealthForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 26.0;
            case TIER_1 -> 34.0;
            case TIER_2 -> 42.0;
            case TIER_3 -> 52.0;
            case TIER_4 -> 64.0;
        };
    }

    @Override public double getArmorForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 0.0;
            case TIER_1 -> 1.0;
            case TIER_2 -> 2.0;
            case TIER_3 -> 3.0;
            case TIER_4 -> 4.0;
        };
    }

    /** 法术输出为主，近战伤害很低。 */
    @Override public double getMeleeDamageForTier(EquipmentTier tier) { return 2.0; }

    @Override public double getCavalryHorseArmorBonus() {
        return switch (getTier()) {
            case TIER_0 -> 0.0;
            case TIER_1 -> 2.0;
            case TIER_2 -> 4.0;
            case TIER_3 -> 6.0;
            case TIER_4 -> 8.0;
        };
    }

    @Override public double getCavalryHorseSpeed()   { return 0.40; }
    @Override public double getMovementSpeed()       { return 0.31; }
    @Override public double getCavalryChargeSpeed()  { return 0.45; }

    @Override public int getXpReward() { return 10; }

    // ===================== 重伤 =====================

    /** 给敌人施加重伤：等级 = 士兵装备等级（0 级起，上限 9 级 = 100% 禁疗）。 */
    public void applyGrievousWound(LivingEntity victim) {
        if (victim == null || !victim.isAlive()) return;
        int tierIdx = getTier().ordinal();
        int amplifier = Math.min(tierIdx, GrievousWoundEffect.MAX_AMPLIFIER);
        int duration = WOUND_BASE_DURATION + tierIdx * WOUND_DURATION_PER_TIER;
        victim.addEffect(new MobEffectInstance(
                ModEffects.GRIEVOUS_WOUND,
                duration,
                amplifier), this);
    }

    /** 近战命中同样施加重伤。 */
    @Override
    protected void applyMeleeEffects(LivingEntity victim) {
        applyGrievousWound(victim);
    }

    // ===================== 幻魔者尖牙 =====================

    /**
     * 在目标脚下直接召唤幻魔者尖牙（不再沿路径铺一条线）。
     *
     *   落点 = 目标当前位置 + 目标速度 × 尖牙触发延迟（简单预判），
     *   再以该点为中心在脚下铺一小簇，保证移动中的目标也能吃到。
     *   尖牙 owner = 本士兵：伤害归属士兵、被击中者仇恨士兵。
     *
     * @return 是否至少召唤出一颗尖牙
     */
    public boolean castFangsAtTarget(LivingEntity target) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        if (target == null || !target.isAlive()) return false;

        // 不穿墙：目标被方块挡住时不召唤尖牙（尖牙直接砸目标脚下，
        //   如果不检查就会隔墙命中，是唯一能穿墙的"施法"）
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        if (level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                .getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            return false;
        }

        // 简单预判：按目标当前速度提前"尖牙触发延迟"个 tick 求落点
        Vec3 velocity = target.getDeltaMovement();
        double centerX = target.getX()
                + Mth.clamp(velocity.x * FANG_TRIGGER_DELAY_TICKS, -MAX_LEAD, MAX_LEAD);
        double centerZ = target.getZ()
                + Mth.clamp(velocity.z * FANG_TRIGGER_DELAY_TICKS, -MAX_LEAD, MAX_LEAD);

        // 只在目标脚下附近找地面（脚下一格 ~ 上方一格）
        double minY = target.getY() - 1.0;
        double maxY = target.getY() + 1.0;
        float yRot = (float) Mth.atan2(target.getZ() - this.getZ(),
                target.getX() - this.getX());

        int spawned = 0;
        for (double[] offset : FANG_FEET_OFFSETS) {
            if (createFang(serverLevel, centerX + offset[0], centerZ + offset[1],
                    minY, maxY, yRot, 0)) {
                spawned++;
            }
        }

        if (spawned > 0) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return spawned > 0;
    }

    /** 在 (x,z) 附近找一块可站立地面，并在其上生成尖牙。 */
    private boolean createFang(ServerLevel level, double x, double z,
                               double minY, double maxY, float yRot, int warmupTicks) {
        BlockPos pos = BlockPos.containing(x, maxY, z);
        boolean foundGround = false;
        double yOffset = 0.0;

        while (true) {
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (belowState.isFaceSturdy(level, below, Direction.UP)) {
                if (!level.isEmptyBlock(pos)) {
                    BlockState hereState = level.getBlockState(pos);
                    VoxelShape shape = hereState.getCollisionShape(level, pos);
                    if (!shape.isEmpty()) yOffset = shape.max(Direction.Axis.Y);
                }
                foundGround = true;
                break;
            }
            pos = pos.below();
            if (pos.getY() < Mth.floor(minY) - 1) break;
        }

        if (!foundGround) return false;

        EvokerFangs fangs = new EvokerFangs(level, x, pos.getY() + yOffset, z,
                yRot, warmupTicks, this);
        level.addFreshEntity(fangs);
        return true;
    }
}
