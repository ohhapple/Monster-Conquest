package com.ohhapple.monster_conquest.entity.soldiers;

import com.ohhapple.monster_conquest.ai.soldier.EvokerSpellGoal;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.effect.GrievousWoundEffect;
import com.ohhapple.monster_conquest.effect.ModEffects;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.AbstractFlyingSoldier;

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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;

/**
 * 唤魔者士兵（魔法师，灾厄阵营；也有骑兵模式）。
 *
 *   · 召唤幻魔者尖牙：目标在 20 格内时，在目标脚下（飞行目标为预判高度）召唤尖牙，
 *     尖牙的 owner = 本士兵 → 伤害归属、仇恨吸引全部记在士兵身上。
 *   · 技能冷却：由 {@link EvokerSpellGoal}
 *     由 {@code EvokerSpellGoal.FANG_COOLDOWN_TICKS} 控制（2 秒）。
 *   · 重伤：被尖牙命中 / 被近战命中的敌人获得"重伤"buff，
 *     等级 = 士兵装备等级（0 级 -10% 治疗，每级 +10%，上限 100%）。
 */
public class EvokerSoldier extends AbstractArmySoldier {

    public static final String ARENA_SKILL_FANG_TAG =
            "monster_conquest_arena_skill_fang";

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

    /** 每个唤魔者同时维持的恼鬼上限。 */
    private static final int MAX_SUMMONED_UNITS = 3;
    /** 补足一只恼鬼后的间隔，60 tick = 3 秒。 */
    private static final int SUMMON_INTERVAL_TICKS = 60;

    public EvokerSoldier(EntityType<? extends PathfinderMob> type, Level level) { super(type, level); }

    @Override public SoldierType getDefaultSoldierType() { return SoldierType.MAGE_INFANTRY; }
    @Override public int getRenderIndex() { return 14; }

    /** 子类可覆盖这些 getter 建立不同等级的唤魔者召唤节奏。 */
    public int getMaxSummonedUnits() { return MAX_SUMMONED_UNITS; }
    public int getSummonIntervalTicks() { return SUMMON_INTERVAL_TICKS; }

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
        return castFangsAtTarget(target, true, false, false);
    }

    /**
     * 竞技尖牙只结算该弈子的技能：每名敌人一颗，由技能条统一驱动，
     * 因此不沿用主世界施法冷却，也不要求施法前已有视线。
     */
    public boolean castArenaFangAtTarget(LivingEntity target) {
        return castFangsAtTarget(target, false, true, true);
    }

    private boolean castFangsAtTarget(LivingEntity target,
                                      boolean requireLineOfSight,
                                      boolean singleFang,
                                      boolean arenaSkillFang) {
        if (!(level() instanceof ServerLevel serverLevel)) return false;
        if (target == null || !target.isAlive()) return false;

        // 不穿墙：目标被方块挡住时不召唤尖牙（尖牙直接砸目标脚下，
        //   如果不检查就会隔墙命中，是唯一能穿墙的"施法"）
        if (requireLineOfSight) {
            Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (level().clip(new ClipContext(from, to,
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, this))
                    .getType() != HitResult.Type.MISS) {
                return false;
            }
        }

        // 按相邻 tick 的真实位移预判；飞行单位通过移动控制器 / 直接位移时
        //   deltaMovement 经常为零，不能只读速度向量。
        Vec3 velocity = AiNavUtil.observedMovement(target);
        double centerX = target.getX()
                + Mth.clamp(velocity.x * FANG_TRIGGER_DELAY_TICKS, -MAX_LEAD, MAX_LEAD);
        double centerY = target.getY()
                + Mth.clamp(velocity.y * FANG_TRIGGER_DELAY_TICKS, -1.5, 1.5);
        double centerZ = target.getZ()
                + Mth.clamp(velocity.z * FANG_TRIGGER_DELAY_TICKS, -MAX_LEAD, MAX_LEAD);

        // 只在目标脚下附近找地面（脚下一格 ~ 上方一格）
        double minY = target.getY() - 1.0;
        double maxY = target.getY() + 1.0;
        float yRot = (float) Mth.atan2(target.getZ() - this.getZ(),
                target.getX() - this.getX());

        int spawned = 0;
        if (target instanceof AbstractFlyingSoldier) {
            // 单位伤害统一忽略无敌帧；空中目标只生成一颗预判尖牙，
            //   避免同一簇多颗尖牙同 tick 重复结算把小体型目标秒杀。
            if (createAirFang(serverLevel, centerX, centerY, centerZ,
                    yRot, 0, arenaSkillFang)) {
                spawned = 1;
            }
        } else if (singleFang) {
            if (createFang(serverLevel, centerX, centerZ,
                    minY, maxY, yRot, 0, arenaSkillFang)) {
                spawned = 1;
            }
        } else {
            for (double[] offset : FANG_FEET_OFFSETS) {
                if (createFang(serverLevel, centerX + offset[0], centerZ + offset[1],
                        minY, maxY, yRot, 0, arenaSkillFang)) {
                    spawned++;
                }
            }
        }

        if (spawned > 0) {
            serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        return spawned > 0;
    }

    /**
     * 对飞行目标把尖牙生成在预判高度，而不是地面。
     *   尖牙碰撞盒从生成点向上延伸，与小体型飞行目标的下半身相交；
     *   高度限制在目标位置上下 1.5 格内，避免错误命中其他楼层。
     */
    private boolean createAirFang(ServerLevel level, double x, double y, double z,
                                  float yRot, int warmupTicks,
                                  boolean arenaSkillFang) {
        if (!level.isLoaded(BlockPos.containing(x, y, z))) return false;
        EvokerFangs fangs = new EvokerFangs(level, x, y, z, yRot, warmupTicks, this);
        if (arenaSkillFang) fangs.addTag(ARENA_SKILL_FANG_TAG);
        level.addFreshEntity(fangs);
        return true;
    }

    /** 在 (x,z) 附近找一块可站立地面，并在其上生成尖牙。 */
    private boolean createFang(ServerLevel level, double x, double z,
                               double minY, double maxY, float yRot,
                               int warmupTicks, boolean arenaSkillFang) {
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
        if (arenaSkillFang) fangs.addTag(ARENA_SKILL_FANG_TAG);
        level.addFreshEntity(fangs);
        return true;
    }
}
