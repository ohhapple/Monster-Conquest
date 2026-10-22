package com.ohhapple.monster_conquest.ai.nav;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * AI 导航辅助：
 *  - 统一的危险方块判定（岩浆 / 火 / 仙人掌 / 浆果丛 / 细雪 / 岩浆块 / 营火）
 *  - 主动打开附近木门 / 栅栏门
 */
public final class AiNavUtil {

    private AiNavUtil() {}

    /** 全局节流：昂贵的"完整绕路尝试"（放宽寻路半径 + 最多十次原版寻路 + 自研 A*）每 tick 只放行 1 个单位。 */
    private static final int HEAVY_ROUTE_ATTEMPTS_PER_TICK = 1;
    /** 两次昂贵尝试之间至少隔这么多 tick（单次可能几十毫秒，连着跑会持续卡顿）。 */
    private static final int HEAVY_ROUTE_MIN_INTERVAL_TICKS = 4;
    /** 找"通得到目标那一层"的梯子时向外扫多少格（骑乘下马 / 补发寻路用）。 */
    private static final int CLIMB_SEARCH_RADIUS = 8;
    private static long heavyAttemptTick = Long.MIN_VALUE;
    private static int heavyAttemptUsed = 0;
    private static long lastHeavyAttemptTick = Long.MIN_VALUE;
    private static boolean lastHeavyAttemptDeferred = false;
    private static final Map<Mob, DoorProbe> DOOR_PROBES = new WeakHashMap<>();
    private static final Map<Mob, LadderProbe> LADDER_PROBES = new WeakHashMap<>();
    private static final Map<LineKey, LineProbe> LINE_PROBES = new HashMap<>();

    private record RetreatCandidate(Vec3 point, double score) {}

    /**
     * 申请一次"昂贵绕路尝试"的名额。整队同时下命令 / 一堆单位同时锁定走不到的目标时，
     *   只有 1 个单位能在本 tick 真正跑完整尝试，其余单位下一 tick 再轮到 ——
     *   否则几十个单位各跑十次大范围寻路，一 tick 就能把服务器卡住好几秒。
     */
    public static boolean acquireHeavyRouteAttempt(long nowTick) {
        if (nowTick != heavyAttemptTick) {
            heavyAttemptTick = nowTick;
            heavyAttemptUsed = 0;
        }
        if (heavyAttemptUsed >= HEAVY_ROUTE_ATTEMPTS_PER_TICK) return false;
        if (nowTick - lastHeavyAttemptTick < HEAVY_ROUTE_MIN_INTERVAL_TICKS) return false;
        heavyAttemptUsed++;
        lastHeavyAttemptTick = nowTick;
        return true;
    }

    /** 标记 / 读取：上一次昂贵尝试是否只是因为"本 tick 名额用完"被推迟（不是真的没有路）。 */
    public static void markHeavyAttemptDeferred(boolean deferred) {
        lastHeavyAttemptDeferred = deferred;
    }

    public static boolean wasLastHeavyAttemptDeferred() {
        return lastHeavyAttemptDeferred;
    }

    /** 普通（一次原版寻路）重规划的全局上限：整队同时下令时不要同一 tick 全跑一遍。 */
    private static final int QUICK_ROUTE_ATTEMPTS_PER_TICK = 4;
    private static long quickAttemptTick = Long.MIN_VALUE;
    private static int quickAttemptUsed = 0;

    /** 申请一次"普通重规划"名额；本 tick 名额用完时由调用方按"稍后重试"处理。 */
    public static boolean acquireQuickRouteAttempt(long nowTick) {
        if (nowTick != quickAttemptTick) {
            quickAttemptTick = nowTick;
            quickAttemptUsed = 0;
        }
        if (quickAttemptUsed >= QUICK_ROUTE_ATTEMPTS_PER_TICK) return false;
        quickAttemptUsed++;
        return true;
    }

    /**
     * 给远程单位选一个可以短暂拉开距离的落脚点。
     *   正后方被墙、悬崖或队友堵住时，会依次尝试左右斜后方；候选点必须同层、
     *   身体通得过、目标仍可射击，并优先远离当前威胁。
     *
     * @param mover          实际移动实体，骑乘时传入坐骑
     * @param rider          骑手；步行时传 null
     * @param target         当前威胁
     * @param wantedDistance 希望退到的距离
     */
    @Nullable
    public static Vec3 findFallbackSpacingPoint(Mob mover, @Nullable LivingEntity rider,
                                                LivingEntity target, double wantedDistance) {
        Level level = mover.level();
        Vec3 origin = mover.position();
        Vec3 toTarget = target.position().subtract(origin);
        double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        if (horizontal < 1.0E-4) return null;

        double awayX = -toTarget.x / horizontal;
        double awayZ = -toTarget.z / horizontal;
        double currentDistance = origin.distanceTo(target.position());
        double baseStep = Mth.clamp(wantedDistance - currentDistance + 1.0, 2.0, 6.0);
        double[] steps = {baseStep, Math.min(7.5, baseStep + 2.5), 7.5};
        double[] angles = {0.0, 35.0, -35.0, 70.0, -70.0, 105.0, -105.0};
        List<LivingEntity> nearbyBodies = level.getEntitiesOfClass(
                LivingEntity.class,
                mover.getBoundingBox().inflate(11.0),
                e -> e != mover && e != rider && e != target && e.isAlive());

        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (double step : steps) {
            for (double degrees : angles) {
                double radians = Math.toRadians(degrees);
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                double dirX = awayX * cos - awayZ * sin;
                double dirZ = awayX * sin + awayZ * cos;
                double x = origin.x + dirX * step;
                double z = origin.z + dirZ * step;
                double y = rider != null && rider.isPassenger()
                        ? findMountY(level, rider, x, origin.y, z)
                        : groundNear(level, x, origin.y, z);
                if (Double.isNaN(y) || Math.abs(y - origin.y) > 1.5) continue;

                Vec3 candidate = new Vec3(x, y, z);
                if (!hasClearLineForBody(level, origin.add(0.0, 0.1, 0.0),
                        candidate.add(0.0, 0.1, 0.0), mover.getBbWidth() * 0.5)) {
                    continue;
                }

                AABB candidateBox = mover.getBoundingBox()
                        .move(candidate.x - origin.x, candidate.y - origin.y, candidate.z - origin.z)
                        .inflate(0.05);
                if (!level.noBlockCollision(mover, candidateBox)) continue;
                boolean occupied = false;
                for (LivingEntity other : nearbyBodies) {
                    if (candidateBox.intersects(other.getBoundingBox())) {
                        occupied = true;
                        break;
                    }
                }

                double eyeHeight = rider != null && rider.isPassenger()
                        ? WarHorseEntity.RIDER_HEIGHT + rider.getEyeHeight()
                        : mover.getEyeHeight();
                Vec3 shotFrom = new Vec3(candidate.x, candidate.y + eyeHeight, candidate.z);
                boolean clearShot = hasClearLine(level, shotFrom, target.getBoundingBox().getCenter());
                double distance = candidate.distanceTo(target.position());
                double score = distance * 3.0
                        + (clearShot ? 8.0 : -12.0)
                        + (Math.abs(degrees) > 0.1 ? 0.75 : 0.0)
                        - Math.abs(candidate.y - origin.y) * 4.0
                        - Math.abs(degrees) * 0.015
                        - (occupied ? 18.0 : 0.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        return best;
    }

    /**
     * 直线后撤被墙挡住时，找一个可以真正寻路到达的墙后 / 侧后方落脚点。
     *   候选落点不要求与当前位置直线连通，但必须同层、自身无方块碰撞、未被其它生物占用，
     *   且原版寻路能够完整到达；找到后直接安装路线，由导航负责绕墙。
     */
    @Nullable
    public static Vec3 startRoutedRetreat(Mob mover, @Nullable LivingEntity rider,
                                          LivingEntity target, double wantedDistance) {
        Level level = mover.level();
        long nowTick = level.getGameTime();
        if (!acquireQuickRouteAttempt(nowTick)) return null;

        Vec3 origin = mover.position();
        Vec3 toTarget = target.position().subtract(origin);
        double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        if (horizontal < 1.0E-4) return null;

        double awayX = -toTarget.x / horizontal;
        double awayZ = -toTarget.z / horizontal;
        double currentDistance = origin.distanceTo(target.position());
        double baseStep = Mth.clamp(wantedDistance - currentDistance + 1.5, 4.0, 8.0);
        double[] steps = {baseStep, Math.max(4.0, baseStep - 2.0), 9.0, 12.0};
        double[] angles = {0.0, 35.0, -35.0, 70.0, -70.0, 105.0, -105.0};
        List<LivingEntity> nearbyBodies = level.getEntitiesOfClass(
                LivingEntity.class,
                mover.getBoundingBox().inflate(14.0),
                e -> e != mover && e != rider && e != target && e.isAlive());

        List<RetreatCandidate> candidates = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (double step : steps) {
            for (double degrees : angles) {
                double radians = Math.toRadians(degrees);
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                double dirX = awayX * cos - awayZ * sin;
                double dirZ = awayX * sin + awayZ * cos;
                double x = origin.x + dirX * step;
                double z = origin.z + dirZ * step;
                BlockPos cell = BlockPos.containing(x, origin.y, z);
                if (!seen.add(cell.asLong())) continue;

                double y = rider != null && rider.isPassenger()
                        ? findMountY(level, rider, x, origin.y, z)
                        : groundNear(level, x, origin.y, z);
                if (Double.isNaN(y) || Math.abs(y - origin.y) > 1.5) continue;

                Vec3 candidate = new Vec3(x, y, z);
                AABB candidateBox = mover.getBoundingBox()
                        .move(candidate.x - origin.x, candidate.y - origin.y, candidate.z - origin.z)
                        .inflate(0.05);
                if (!level.noBlockCollision(mover, candidateBox)) continue;

                boolean occupied = nearbyBodies.stream()
                        .anyMatch(other -> candidateBox.intersects(other.getBoundingBox()));
                if (occupied) continue;

                double distance = candidate.distanceTo(target.position());
                double score = distance * 4.0 - Math.abs(degrees) * 0.035
                        - Math.abs(candidate.y - origin.y) * 4.0;
                candidates.add(new RetreatCandidate(candidate, score));
            }
        }
        candidates.sort((a, b) -> Double.compare(b.score(), a.score()));

        int attempts = 0;
        for (RetreatCandidate candidate : candidates) {
            if (attempts >= 4) break;
            if (!acquireQuickRouteAttempt(nowTick)) break;
            attempts++;

            Path path = mover.getNavigation().createPath(
                    BlockPos.containing(candidate.point()), 1);
            if (path == null || !path.canReach()) continue;
            if (mover.getNavigation().moveTo(path, 1.0D)) {
                if (GeneralConfig.debugMovementLog) {
                    Monster_conquest.LOGGER.info(
                            "[RANGED-KITE] unit={} phase=routed destination={}",
                            mover.getUUID().toString().substring(0, 8),
                            String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                                    candidate.point().x, candidate.point().y,
                                    candidate.point().z));
                }
                return candidate.point();
            }
        }
        return null;
    }

    /**
     * 完整路线暂不可用时的短距离自保步。只选 1~3 格内、身体能直接通过的
     * 同层落脚点，由移动控制器直接执行，不等待寻路完成。
     */
    @Nullable
    public static Vec3 findImmediateRetreatPoint(Mob mover, @Nullable LivingEntity rider,
                                                 LivingEntity target) {
        Level level = mover.level();
        Vec3 origin = mover.position();
        Vec3 toTarget = target.position().subtract(origin);
        double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        if (horizontal < 1.0E-4) return null;

        double awayX = -toTarget.x / horizontal;
        double awayZ = -toTarget.z / horizontal;
        double[] steps = {1.2, 2.0, 3.0};
        double[] angles = {0.0, 45.0, -45.0, 90.0, -90.0, 135.0, -135.0};
        List<LivingEntity> nearbyBodies = level.getEntitiesOfClass(
                LivingEntity.class,
                mover.getBoundingBox().inflate(5.0),
                e -> e != mover && e != rider && e != target && e.isAlive());

        Vec3 best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int sameLevel = 0;
        int clearLine = 0;
        int clearBlocks = 0;
        for (double step : steps) {
            for (double degrees : angles) {
                double radians = Math.toRadians(degrees);
                double cos = Math.cos(radians);
                double sin = Math.sin(radians);
                double dirX = awayX * cos - awayZ * sin;
                double dirZ = awayX * sin + awayZ * cos;
                double x = origin.x + dirX * step;
                double z = origin.z + dirZ * step;
                double y = rider != null && rider.isPassenger()
                        ? findMountY(level, rider, x, origin.y, z)
                        : groundNear(level, x, origin.y, z);
                if (Double.isNaN(y) || Math.abs(y - origin.y) > 1.0) continue;
                sameLevel++;

                Vec3 candidate = new Vec3(x, y, z);
                if (!hasClearLineForBody(level, origin.add(0.0, 0.1, 0.0),
                        candidate.add(0.0, 0.1, 0.0), mover.getBbWidth() * 0.5)) {
                    continue;
                }
                clearLine++;
                AABB candidateBox = mover.getBoundingBox()
                        .move(candidate.x - origin.x, candidate.y - origin.y, candidate.z - origin.z)
                        .inflate(0.02);
                if (!level.noBlockCollision(mover, candidateBox)) continue;
                clearBlocks++;

                boolean occupied = false;
                for (LivingEntity other : nearbyBodies) {
                    if (candidateBox.intersects(other.getBoundingBox())) {
                        occupied = true;
                        break;
                    }
                }
                double score = step * 4.0
                        - Math.abs(degrees) * 0.03
                        - Math.abs(candidate.y - origin.y) * 5.0
                        - (occupied ? 12.0 : 0.0);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }
        if (GeneralConfig.debugMovementLog && mover.tickCount % 20 == 0) {
            Monster_conquest.LOGGER.info(
                    "[RANGED-KITE] unit={} phase=immediate-scan sameLevel={} line={} blocks={} origin=({},{},{})",
                    mover.getUUID().toString().substring(0, 8),
                    sameLevel, clearLine, clearBlocks,
                    String.format(java.util.Locale.ROOT, "%.2f", origin.x),
                    String.format(java.util.Locale.ROOT, "%.2f", origin.y),
                    String.format(java.util.Locale.ROOT, "%.2f", origin.z));
        }
        return best;
    }

    /**
     * 选一个 3~6 格外的直线后退点，让 MoveControl 有足够距离持续加速。
     * 只检查落脚支撑、身体净空和骑手净空。
     */
    @Nullable
    public static Vec3 findStraightBackstepPoint(Mob mover, @Nullable LivingEntity rider,
                                                 LivingEntity target) {
        double dx = target.getX() - mover.getX();
        double dz = target.getZ() - mover.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4 || !mover.onGround()) return null;

        double[] steps = {6.0, 4.5, 3.0};
        for (double step : steps) {
            double x = mover.getX() - dx / horizontal * step;
            double z = mover.getZ() - dz / horizontal * step;
            double y = mover.getY();
            BlockPos support = BlockPos.containing(x, y - 0.08, z);
            if (!mover.level().getBlockState(support).isSolid()) continue;

            AABB body = mover.getBoundingBox().move(x - mover.getX(), 0.0, z - mover.getZ());
            if (!mover.level().noBlockCollision(mover, body)) continue;
            if (rider != null && rider.isPassenger()) {
                AABB riderBody = rider.getBoundingBox().move(x - mover.getX(), 0.0, z - mover.getZ());
                if (!mover.level().noBlockCollision(rider, riderBody)) continue;
            }
            return new Vec3(x, y, z);
        }
        return null;
    }

    /**
     * 读取相邻 tick 的真实位移；骑乘时取坐骑。
     *   位移异常大（传送 / 区块恢复）时返回零，避免把它当成速度用于追击或预判。
     */
    public static Vec3 observedMovement(Entity entity) {
        Entity mover = entity.getVehicle() instanceof Entity vehicle ? vehicle : entity;
        Vec3 observed = new Vec3(
                mover.getX() - mover.xo,
                mover.getY() - mover.yo,
                mover.getZ() - mover.zo);
        if (observed.lengthSqr() > 16.0) return Vec3.ZERO;
        if (observed.lengthSqr() > 1.0E-8) return observed;

        Vec3 delta = mover.getDeltaMovement();
        return delta.lengthSqr() > 16.0 ? Vec3.ZERO : delta;
    }

    /** 相邻 tick 的实际水平位移；直接 move 清掉 delta 后仍然有效。 */
    public static Vec3 observedHorizontalMovement(Entity entity) {
        Vec3 movement = observedMovement(entity);
        return new Vec3(movement.x, 0.0, movement.z);
    }

    /**
     * 目标是否仍在真实水平移动。
     *   攻击 Goal 只能用这个结果决定“站定输出”，不能仅凭进入射程就停止跟随；
     *   否则直接位移、后撤或控制器切换后的目标会被误判为静止。
     */
    public static boolean isMovingHorizontally(LivingEntity entity) {
        return entity != null && observedHorizontalMovement(entity).lengthSqr() > 0.0025;
    }

    /**
     * 按实体自身 MOVEMENT_SPEED 执行一 tick 的安全直线后退。
     * 直接物理移动可以避免 MoveControl 的近目标急停与朝向补偿造成抽搐。
     */
    public static boolean applyFullSpeedBackstep(Mob mover, @Nullable LivingEntity rider,
                                                 LivingEntity target) {
        double dx = target.getX() - mover.getX();
        double dz = target.getZ() - mover.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4 || !mover.onGround()) return false;

        double step = Math.max(1.2, mover.getBbWidth() * 1.1);
        double x = mover.getX() - dx / horizontal * step;
        double z = mover.getZ() - dz / horizontal * step;
        double y = mover.getY();
        BlockPos support = BlockPos.containing(x, y - 0.08, z);
        if (!mover.level().getBlockState(support).isSolid()) return false;

        AABB body = mover.getBoundingBox().move(x - mover.getX(), 0.0, z - mover.getZ());
        if (!mover.level().noBlockCollision(mover, body)) return false;
        if (rider != null && rider.isPassenger()) {
            AABB riderBody = rider.getBoundingBox().move(x - mover.getX(), 0.0, z - mover.getZ());
            if (!mover.level().noBlockCollision(rider, riderBody)) return false;
        }

        double desiredX = -dx / horizontal;
        double desiredZ = -dz / horizontal;
        float currentYaw = mover.getYRot();
        double currentYawRad = Math.toRadians(currentYaw);
        double forwardX = -Math.sin(currentYawRad);
        double forwardZ = Math.cos(currentYawRad);
        // 已朝向撤离方向时保持航向，目标小幅横移不重新摆头。
        double dirX = forwardX * desiredX + forwardZ * desiredZ > 0.65
                ? forwardX : desiredX;
        double dirZ = forwardX * desiredX + forwardZ * desiredZ > 0.65
                ? forwardZ : desiredZ;
        double dirLen = Math.sqrt(dirX * dirX + dirZ * dirZ);
        dirX /= dirLen;
        dirZ /= dirLen;

        // 朝撤离方向奔跑，避免战马身体朝敌人、却沿反方向“倒着走”的异常步态。
        float yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        mover.setYRot(yaw);
        mover.yBodyRot = yaw;
        mover.setYHeadRot(yaw);
        mover.yRotO = yaw;
        mover.yBodyRotO = yaw;
        mover.yHeadRotO = yaw;
        if (rider != null && rider.isPassenger()) {
            rider.setYRot(yaw);
            rider.yBodyRot = yaw;
            rider.setYHeadRot(yaw);
            rider.yRotO = yaw;
            rider.yBodyRotO = yaw;
            rider.yHeadRotO = yaw;
        }
        mover.getNavigation().stop();
        stopMovementInput(mover);

        double speed = mover.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Vec3 dm = mover.getDeltaMovement();
        mover.setDeltaMovement(0.0, dm.y, 0.0);
        mover.move(MoverType.SELF,
                new Vec3(dirX * speed, 0.0, dirZ * speed));
        mover.setDeltaMovement(0.0, dm.y, 0.0);
        mover.hasImpulse = true;

        if (GeneralConfig.debugMovementLog && mover.tickCount % 20 == 0) {
            Monster_conquest.LOGGER.info(
                    "[RANGED-KITE] unit={} phase=backstep speed={} destination=({},{})",
                    mover.getUUID().toString().substring(0, 8),
                    String.format(java.util.Locale.ROOT, "%.3f", speed),
                    String.format(java.util.Locale.ROOT, "%.2f", x),
                    String.format(java.util.Locale.ROOT, "%.2f", z));
        }
        return true;
    }

    /** 检查并执行一 tick 的完整实体速度侧移，不使用 MoveControl.strafe。 */
    public static boolean applyFullSpeedSidestep(Mob mover, @Nullable LivingEntity rider,
                                                 LivingEntity target, int direction) {
        if (direction == 0) return false;
        Level level = mover.level();
        Vec3 origin = mover.position();
        double dx = target.getX() - origin.x;
        double dz = target.getZ() - origin.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0E-4) return false;

        double sideX = -dz / horizontal * direction;
        double sideZ = dx / horizontal * direction;
        double speed = mover.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double x = origin.x + sideX * speed;
        double z = origin.z + sideZ * speed;
        double y = origin.y;

        // 贴墙横移只需验证本 tick 的实际位移，不能预先检查两格外并让平行检测线擦到墙。
        BlockPos support = BlockPos.containing(x, y - 0.08, z);
        if (!level.getBlockState(support).isSolid()) return false;
        AABB candidateBox = mover.getBoundingBox()
                .move(x - origin.x, 0.0, z - origin.z);
        if (!level.noBlockCollision(mover, candidateBox)) return false;
        if (rider != null && rider.isPassenger()) {
            AABB riderBox = rider.getBoundingBox()
                    .move(x - origin.x, 0.0, z - origin.z);
            if (!level.noBlockCollision(rider, riderBox)) return false;
        }
        if (!level.getEntitiesOfClass(
                LivingEntity.class,
                candidateBox,
                e -> e != mover && e != rider && e != target && e.isAlive()).isEmpty()) {
            return false;
        }

        double eyeHeight = rider != null && rider.isPassenger()
                ? WarHorseEntity.RIDER_HEIGHT + rider.getEyeHeight()
                : mover.getEyeHeight();
        Vec3 shotFrom = new Vec3(x, y + eyeHeight, z);
        if (!hasClearLine(level, shotFrom, target.getBoundingBox().getCenter())) return false;

        float yaw = (float) Math.toDegrees(Math.atan2(-sideX, sideZ));
        mover.setYRot(yaw);
        mover.yBodyRot = yaw;
        mover.setYHeadRot(yaw);
        mover.yRotO = yaw;
        mover.yBodyRotO = yaw;
        mover.yHeadRotO = yaw;
        if (rider != null && rider.isPassenger()) {
            rider.setYRot(yaw);
            rider.yBodyRot = yaw;
            rider.setYHeadRot(yaw);
            rider.yRotO = yaw;
            rider.yBodyRotO = yaw;
            rider.yHeadRotO = yaw;
        }

        Vec3 dm = mover.getDeltaMovement();
        mover.getNavigation().stop();
        stopMovementInput(mover);
        mover.setDeltaMovement(0.0, dm.y, 0.0);
        mover.move(MoverType.SELF, new Vec3(sideX * speed, 0.0, sideZ * speed));
        mover.setDeltaMovement(0.0, dm.y, 0.0);
        mover.hasImpulse = true;
        return true;
    }

    /**
     * 阵型槽位是否站得住：两格净空 + 下方 4 格内有实心地面，且沿途没有危险方块。
     *   首领阵型槽位与玩家军队阵型槽位共用这一份判定。
     */
    public static boolean isSafeStandingSpot(Level level, double x, double y, double z) {
        BlockPos p = BlockPos.containing(x, y, z);

        BlockState here = level.getBlockState(p);
        if (here.isSolid()) return false;
        if (isDangerousBlock(here)) return false;

        BlockState above = level.getBlockState(p.above());
        if (above.isSolid()) return false;
        if (isDangerousBlock(above)) return false;

        for (int dy = 1; dy <= 4; dy++) {
            BlockPos belowPos = p.below(dy);
            BlockState below = level.getBlockState(belowPos);
            if (isDangerousBlock(below)) return false;
            if (below.isSolid()) {
                // 栅栏 / 墙 / 栏杆的碰撞盒高 1.5 格（isSolid() 也是 true），
                //   它们不能当站立面，否则会生成"悬在栅栏上"的槽位，士兵只能贴着栅栏站。
                if (below.getCollisionShape(level, belowPos)
                        .max(Direction.Axis.Y) > 1.05) continue;
                return true;
            }
        }
        return false;
    }

    /**
     * 站位是否"能站而且走得出来"：先过 {@link #isSafeStandingSpot}（两格净空 + 有地面 + 不危险），
     *   再要求水平四个方向里至少两个方向是通的 —— 用来排除"墙里 / 封闭空穴 / 只能卡一格的死角"，
     *   阵型槽位与驻守站点都靠它把关。
     */
    public static boolean isOpenStandingSpot(Level level, double x, double y, double z) {
        if (!isSafeStandingSpot(level, x, y, z)) return false;
        BlockPos pos = BlockPos.containing(x, y, z);
        int open = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (!level.getBlockState(n).isSolid() && !level.getBlockState(n.above()).isSolid()) {
                open++;
            }
        }
        return open >= 2;
    }

    /**
     * 脚所在格是不是窄通道：水平方向最多两个方向能站人（1 格宽走廊 / 门口 / 拐角）。
     *   同阵营分离在窄通道里会把彼此推向两侧的墙，最终互相顶死，需要让行。
     */
    public static boolean isNarrowPassage(Level level, BlockPos feet) {
        int open = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(d);
            if (!level.getBlockState(side).isSolid()
                    && !level.getBlockState(side.above()).isSolid()) {
                open++;
            }
        }
        return open <= 2;
    }

    /** 在 (x,z) 附近找一个站得住的落脚高度（相对 baseY 上下 2 格内）；找不到返回 NaN。 */
    public static double groundNear(Level level, double x, double baseY, double z) {
        int base = (int) Math.floor(baseY);
        for (int dy : new int[]{0, 1, -1, 2, -2}) {
            double y = base + dy;
            if (isOpenStandingSpot(level, x, y, z)) return y;
        }
        return Double.NaN;
    }

    public static boolean hasClearChargeLine(Level level, LivingEntity mount, LivingEntity target) {
        Vec3 dir = target.position().subtract(mount.position());
        double horiz = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (horiz < 0.1) return true;
        double nx = dir.x / horiz;
        double nz = dir.z / horiz;
        double max = Math.min(horiz, 10.0);

        double w = mount.getBbWidth() * 0.5;
        double yFeet = mount.getY();
        double yTop = yFeet + mount.getBbHeight() * 0.9;

        for (double d = 1.0; d <= max; d += 0.5) {
            double px = mount.getX() + nx * d;
            double pz = mount.getZ() + nz * d;
            AABB box = new AABB(px - w, yFeet + 0.1, pz - w,
                    px + w, yTop, pz + w);
            // 只看方块：目标本体 / 友军站在冲锋线上不算"被挡"
            //   （否则近处目标永远判定为冲锋线被挡，骑兵再也不会冲锋）。
            if (!level.noBlockCollision(mount, box)) return false;
        }
        return true;
    }

    /**
     * 马（含骑手）能不能在 (x, y, z) 站下并且"活动得开"：
     *   · 站位本身：马箱（宽 1.4 / 高 1.6）+ 骑手箱（{@link WarHorseEntity#RIDER_HEIGHT}，
     *     头顶约在马脚上方 2.75 格）都无方块碰撞，允许在 ±1 / +2 格内换落脚点；
     *   · 四周空隙：四个正方向里至少 3 个方向也放得下"马 + 骑手"——
     *     1~2 格宽的小坑 / 壁龛里虽然站得下，但骑兵走不动、转不了身，
     *     在这种地方上马只会被地形卡住，所以不算"能骑马"。
     *   · 只判定方块碰撞（不含其他实体），避免被同伴挤一下就被误判成"过不去"。
     *
     *   士兵的自动上下马、首领的坐骑生成 / 卡住下马判定共用这一份逻辑。
     */
    public static boolean mountFitsAt(Level level, LivingEntity rider,
                                      double x, double y, double z) {
        if (Double.isNaN(findMountY(level, rider, x, y, z))) return false;
        int fits = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (!Double.isNaN(findMountYNear(level, rider, x + d.getStepX(), y, z + d.getStepZ()))) {
                fits++;
            }
        }
        return fits >= 3;
    }

    /**
     * 路线节点 / 阵位落点能否容下"马 + 骑手"（只查站位本身，不要求四周活动空间）。
     *   阵位落点、绕路转向点这类校验用这个：要求"活动得开"会把贴着墙的合法站位全否掉。
     *   允许站位高度在原格上下 1 格内浮动（台阶 / 半砖）。
     */
    public static boolean mountStandFits(Level level, LivingEntity rider,
                                         double x, double y, double z) {
        double feetY = findMountY(level, rider, x, y, z);
        return !Double.isNaN(feetY) && Math.abs(feetY - y) <= 1.01;
    }

    /**
     * 在 (x, z) 找一个能站下"马 + 骑手"的落脚高度（方块 y），找不到返回 {@code NaN}。
     *   以脚下这一层为先，其次往下 / 往上 1 格，最后放宽到 +2（站在坑里 / 台阶上）。
     */
    public static double findMountY(Level level, LivingEntity rider,
                                    double x, double y, double z) {
        double base = Math.floor(y);
        for (int dy : new int[]{0, -1, 1, 2}) {
            if (mountFitsAtHeight(level, rider, x, base + dy, z)) return base + dy;
        }
        return Double.NaN;
    }

    /**
     * "上马专用"：站位 + 四周活动空间都够时返回马的落脚 Y，否则返回 {@code NaN}。
     *   生成马 / 骑手就用这个 Y，避免"检查通过了、马却生成在另一个高度被卡住"。
     */
    public static double resolveMountY(Level level, LivingEntity rider,
                                       double x, double y, double z) {
        if (!mountFitsAt(level, rider, x, y, z)) return Double.NaN;
        return findMountY(level, rider, x, y, z);
    }

    /**
     * "四周检查"用的严格版：只认同一层和上下 1 格 ——
     *   +2 格会算到墙顶上的空气，不算真正的活动空间。
     */
    private static double findMountYNear(Level level, LivingEntity rider,
                                         double x, double y, double z) {
        double base = Math.floor(y);
        for (int dy : new int[]{0, -1, 1}) {
            if (mountFitsAtHeight(level, rider, x, base + dy, z)) return base + dy;
        }
        return Double.NaN;
    }

    /** 马箱 + 骑手箱在指定高度 (x, ty, z) 是否都无方块碰撞。 */
    private static boolean mountFitsAtHeight(Level level, LivingEntity rider,
                                             double x, double ty, double z) {
        EntityType<?> horseType = ModEntities.WAR_HORSE.get();
        AABB horseBox = horseType.getDimensions().makeBoundingBox(x, ty, z);
        if (!level.noBlockCollision(rider, horseBox)) return false;
        AABB riderBox = rider.getType().getDimensions()
                .makeBoundingBox(x, ty + WarHorseEntity.RIDER_HEIGHT, z);
        return level.noBlockCollision(rider, riderBox);
    }

    /** 危险方块：站在上面 / 走进去都会出事 */
    public static boolean isDangerousBlock(BlockState state) {
        return state.is(Blocks.LAVA)
                || state.is(Blocks.FIRE)
                || state.is(Blocks.SOUL_FIRE)
                || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.POWDER_SNOW);
    }

    /**
     * 单位能"用手打开"的门类（不限定木头）：
     *   · 门：{@link DoorBlock#type()}.canOpenByHand()（木门 / 铜门 / 其它模组可手动开的门），
     *     或加入了 {@code minecraft:mob_interactable_doors} 标签；铁门 canOpenByHand=false → 不开；
     *   · 活板门：除铁活板门以外都算（木 / 铜 / 模组活板门）；
     *   · 栅栏门：都是可手动开的。
     */
    public static boolean isHandOpenable(BlockState state) {
        if (state.getBlock() instanceof DoorBlock door) {
            return door.type().canOpenByHand() || state.is(BlockTags.MOB_INTERACTABLE_DOORS);
        }
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.is(Blocks.IRON_TRAPDOOR);
        }
        return state.getBlock() instanceof FenceGateBlock;
    }

    /**
     * 打开"够得着、而且确实要过去"的可手动门 / 栅栏门 / 活板门（铁门 / 铁活板门不可开）。
     *
     *   只开"确实挡道"的情况，站着不动 / 只是路过一律不碰：
     *     · 门格子出现在当前寻路路径的正前方 3 格内（马上要从这一格穿过去）→ 提前开，
     *       原版寻路会把"还关着的门"当成一格台阶，提前打开就不会走到门口跳一下；
     *     · 正走进门里：门 / 栅栏门就在正前方 1 格，而且人确实在朝它移动
     *       （手动绕路 / 路径刚好失效时补开一次；不含活板门）；
     *     · 身体顶着门 / 栅栏门、门就在正前方（撞着走不动了，例如栅栏门不进寻路路径）；
     *     · 正在攀爬：梯子 / 脚手架 / 藤蔓列上 ±2 格的活板门（爬梯通道不依赖寻路路径）。
     *   而且都要求：眼睛到门之间没有别的实心方块挡着，
     *   隔着墙 / 隔着一层楼板 / 拐角墙后的门一律不开。
     */
    public static void tryOpenNearbyDoors(Mob mob) {
        Level level = mob.level();
        if (level.isClientSide) return;

        BlockPos base = mob.blockPosition();
        // 前进方向：优先用实际移动方向（走动中），站住时用朝向
        Vec3 dm = mob.getDeltaMovement();
        double mx = dm.x;
        double mz = dm.z;
        double mlen = Math.sqrt(mx * mx + mz * mz);
        boolean moving = mlen >= 0.05;
        // 朝向（站住时判断"顶着哪扇门"用）
        float yawRad = mob.getYRot() * ((float) Math.PI / 180F);
        double fx = -Mth.sin(yawRad);
        double fz = Mth.cos(yawRad);
        if (mlen < 0.01) {
            mx = fx;
            mz = fz;
            mlen = 1.0;
        }
        mx /= mlen;
        mz /= mlen;
        boolean climbing = isOnClimbable(mob);
        Vec3 chest = new Vec3(mob.getX(),
                mob.getY() + Math.min(1.2, mob.getBbHeight() * 0.6), mob.getZ());

        // 提前 2 格开门（只为"正在走过去"的那扇）：原版寻路会把还关着的门当成一格台阶。
        for (int dx = -2; dx <= 2; dx++) {
            // 包含脚下一格：单位起跳 / 站在门框上时，门可能刚好在脚下一格，
            //   只往上看会漏掉它（表现为"站在门前不开门"）。
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    // 门 / 栅栏门 / 可手动开的活板门，且还关着
                    if (!isHandOpenable(state)
                            || !state.hasProperty(DoorBlock.OPEN)
                            || state.getValue(DoorBlock.OPEN)) {
                        continue;
                    }

                    double hx = p.getX() + 0.5 - mob.getX();
                    double hz = p.getZ() + 0.5 - mob.getZ();
                    double hDist = Math.sqrt(hx * hx + hz * hz);
                    if (hDist > 2.2) continue;   // 够不着

                    // 只处理"确实挡道"的门：
                    //   ① 门格子出现在当前寻路路径前方 3 格内（正常走路穿门）；
                    //   ② 正走进门里（门 / 栅栏门在正前方 1 格且人在朝它走）；
                    //   ③ 身体顶着门 / 栅栏门（栅栏门不会进寻路路径，撞上了才开）；
                    //   ④ 正在攀爬时的梯子活板门（活板门关着只挡竖直方向，走路踩过去不用开）。
                    //   站在门边、面朝门发呆、只是路过侧门 / 走过地板活板门 → 一律不开。
                    boolean trapdoor = state.getBlock() instanceof TrapDoorBlock;
                    boolean allowed = trapdoor
                            ? (climbing && isClimbTrapdoor(base, p))
                            : (isDoorAheadOnPath(mob, p)
                                    || (moving && isMovingInto(mob, p, mx, mz))
                                    || isPressingDoor(mob, p, fx, fz));
                    if (!allowed) continue;

                    // 不隔墙：眼睛到门中心没有被别的实心方块挡住
                    if (!hasClearLineToBlock(level, chest, p)) continue;

                    openHandDoor(level, p, state);
                }
            }
        }
    }

    /**
     * 是不是"正走进这扇门"：门 / 栅栏门就在正前方 1 格内，而且人确实在朝它移动。
     *   人的水平速度要够（站着推门时速度会被卡成 0，那种情况靠路径判定提前开门）；
     *   斜对角必须几乎正对着走才算（不会在门口绕路时把侧门也带开）。
     */
    private static boolean isMovingInto(Mob mob, BlockPos door, double mx, double mz) {
        BlockPos base = mob.blockPosition();
        int dx = door.getX() - base.getX();
        int dz = door.getZ() - base.getZ();
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return false;
        if (dx == 0 && dz == 0) return false;
        if (Math.abs(door.getY() - base.getY()) > 1) return false;
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        double dot = (dx * mx + dz * mz) / len;
        return dot >= 0.7;
    }

    /** 攀爬时的活板门：和单位在同一列（±1 格），高度差不超过 2 格（爬到附近才开）。 */
    private static boolean isClimbTrapdoor(BlockPos base, BlockPos door) {
        if (Math.abs(door.getX() - base.getX()) > 1) return false;
        if (Math.abs(door.getZ() - base.getZ()) > 1) return false;
        int dy = door.getY() - base.getY();
        return dy >= -2 && dy <= 2;
    }

    /**
     * 身体正顶着这扇门 / 栅栏门（原版 DoorInteractGoal 的思路）：
     *   水平方向撞着东西、门就在正前方 1 格（按朝向判定）→ 门确实挡着路，开它。
     *   栅栏门不会进寻路路径（原版把它当障碍），靠这条兜底才能过去。
     */
    private static boolean isPressingDoor(Mob mob, BlockPos door, double fx, double fz) {
        if (!mob.horizontalCollision) return false;
        BlockPos base = mob.blockPosition();
        int dx = door.getX() - base.getX();
        int dz = door.getZ() - base.getZ();
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1) return false;
        if (dx == 0 && dz == 0) return false;
        if (Math.abs(door.getY() - base.getY()) > 1) return false;
        double len = Math.sqrt((double) dx * dx + (double) dz * dz);
        double dot = (dx * fx + dz * fz) / len;
        return dot >= 0.7;
    }

    /**
     * 这扇门是不是"马上要走过去的那扇"：在单位 3 格内的当前寻路路径节点里出现。
     *   原版寻路 / 本模组 A* 绕路都会把门格子当可行走节点，所以"要穿门"必然出现在路径上；
     *   只是从门边路过则不会 —— 这样既能提前开门（避免走到门口被当台阶跳一下），
     *   又不会把路过的门全开一遍。
     */
    private static boolean isDoorAheadOnPath(Mob mob, BlockPos door) {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        double mobX = mob.getX();
        double mobZ = mob.getZ();
        for (int i = 0; i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            double ndx = node.x + 0.5 - mobX;
            double ndz = node.z + 0.5 - mobZ;
            if (ndx * ndx + ndz * ndz > 9.0) continue;               // 3 格内的路径段
            // 高度必须和门格子一致：站在地板活板门上（节点在上一格）不算"要穿过去"
            if (node.y != door.getY()) continue;
            if (Math.abs(node.x - door.getX()) <= 1
                    && Math.abs(node.z - door.getZ()) <= 1) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 from 到目标方块中心这条直线上，有没有别的实心方块挡着（目标方块自己不算）。
     *   "隔墙开门"就是靠这一步挡掉的：隔着墙 / 一层地板 / 拐角墙时，连线上会采样到实心方块。
     */
    private static boolean hasClearLineToBlock(Level level, Vec3 from, BlockPos target) {
        Vec3 to = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.25));
        for (int i = 1; i <= steps; i++) {
            Vec3 pt = from.lerp(to, (double) i / steps);
            BlockPos bp = BlockPos.containing(pt);
            if (bp.equals(target)) continue;
            if (level.getBlockState(bp).isSolid()) return false;
        }
        return true;
    }

    /** 按门 / 栅栏门 / 活板门分别播放开门的音效并置为开启状态。 */
    private static void openHandDoor(Level level, BlockPos p, BlockState state) {
        if (state.getBlock() instanceof DoorBlock) {
            level.setBlock(p, state.setValue(DoorBlock.OPEN, true), 10);
            level.playSound(null, p, SoundEvents.WOODEN_DOOR_OPEN,
                    SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.1f + 0.9f);
        } else if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(p, state.setValue(FenceGateBlock.OPEN, true), 10);
            level.playSound(null, p, SoundEvents.FENCE_GATE_OPEN,
                    SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.1f + 0.9f);
        } else if (state.getBlock() instanceof TrapDoorBlock) {
            // 木 / 铜 / 模组活板门都能开（含上下叠放两格）；铁活板门在 isHandOpenable 里已排除
            level.setBlock(p, state.setValue(TrapDoorBlock.OPEN, true), 10);
            level.playSound(null, p, SoundEvents.WOODEN_TRAPDOOR_OPEN,
                    SoundSource.BLOCKS, 1.0f, level.random.nextFloat() * 0.1f + 0.9f);
        }
    }

    /**
     * 两点之间是否有"直线可见"的通道（不穿实心方块）。
     *   脱困传送用：只允许跳到"眼睛能直接看到"的落点，
     *   避免被卡住时直接传送到墙那一边的房间 / 目标点里（表现为穿墙传送）。
     */
    public static boolean hasClearLine(Level level, Vec3 from, Vec3 to) {
        int steps = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.4));
        for (int i = 1; i <= steps; i++) {
            Vec3 p = from.lerp(to, (double) i / steps);
            BlockPos pos = BlockPos.containing(p.x, p.y, p.z);
            if (level.getBlockState(pos).isSolid()) return false;
            if (level.getBlockState(pos.above()).isSolid()) return false;
        }
        return true;
    }

    /**
     * 按"本体宽度"判定的直线通行：中心线 + 左右各偏移一条平行线（偏移量 = 半宽 - 5cm），
     *   三条都通才算"能直线走过去"。
     *
     *   只看中心线会漏掉"擦着墙角"的情形：中心线不碰方块、身体却撞在角上，
     *   单位会被直线进场一直带向墙角（战马 1.4 格宽最明显）。
     *   身体过不去时返回 false，上层就会交给寻路绕过去。
     */
    public static boolean hasClearLineForBody(Level level, Vec3 from, Vec3 to, double halfWidth) {
        long now = level.getGameTime();
        LineKey key = new LineKey(level.dimension().location(), RoutePlanner.worldVersion(level),
                Double.doubleToLongBits(from.x), Double.doubleToLongBits(from.y),
                Double.doubleToLongBits(from.z), Double.doubleToLongBits(to.x),
                Double.doubleToLongBits(to.y), Double.doubleToLongBits(to.z),
                Double.hashCode(halfWidth));
        LineProbe cached = LINE_PROBES.get(key);
        if (cached != null && now - cached.tick <= 2) return cached.result;

        boolean result = hasClearLineForBodyUncached(level, from, to, halfWidth);
        LINE_PROBES.put(key, new LineProbe(now, result));
        if (LINE_PROBES.size() > 512) {
            LINE_PROBES.entrySet().removeIf(e -> now - e.getValue().tick > 20);
        }
        return result;
    }

    private static boolean hasClearLineForBodyUncached(Level level, Vec3 from, Vec3 to, double halfWidth) {
        if (!hasClearLine(level, from, to)) return false;
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-4) return true;
        double off = halfWidth - 0.05;
        if (off <= 1.0E-3) return true;
        double nx = -dz / len * off;
        double nz = dx / len * off;
        return hasClearLine(level, from.add(nx, 0.0, nz), to.add(nx, 0.0, nz))
                && hasClearLine(level, from.subtract(nx, 0.0, nz), to.subtract(nx, 0.0, nz));
    }

    /**
     * 通用"上坎 / 上岸"跳跃：前方（朝向方向）一格是刚度不超过 1 格的实心方块、
     *   上方两格是空的 → 按原版跳跃。
     *   · 卡在台阶 / 坎前时会跳上去；
     *   · 在水里贴着岸边时同样会跳（原版在水里 jumping 会先向上浮，再跳上岸），
     *     避免在"液体与方块交界处"卡住脚。
     */
    public static boolean tryStepUpJump(Mob mob) {
        Level level = mob.level();
        if (level.isClientSide) return false;
        if (mob.isPassenger()) return false;
        // 站在梯子上 / 刚悬在梯顶正上方：交给攀爬逻辑。
        //   这里跳一下会被原版梯子物理变成 +0.2 的向上爬（贴着方块跳 = 爬升），
        //   把下滑顶回去 —— 表现为单位在梯子顶端来回上下、下不去。
        if (isOnClimbable(mob)
                || level.getBlockState(mob.blockPosition().below()).is(BlockTags.CLIMBABLE)) {
            return false;
        }
        if (!mob.horizontalCollision && !mob.isInWater()) return false;

        float yaw = mob.getYRot() * ((float) Math.PI / 180f);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        for (double step : new double[]{0.7, 1.2}) {
            BlockPos ahead = BlockPos.containing(
                    mob.getX() + fx * step, mob.getY(), mob.getZ() + fz * step);
            BlockState state = level.getBlockState(ahead);
            if (!state.isSolid()) continue;
            // 能用手的门 / 栅栏门 / 活板门：交给开门逻辑，不要当成"坎"跳起来。
            if (isHandOpenable(state)) continue;
            double top = ahead.getY()
                    + state.getCollisionShape(level, ahead).max(Direction.Axis.Y);
            double rise = top - mob.getY();
            if (rise > 1.05) continue;   // 太高跳不上去
            // 台阶高度以内的小坎（默认 0.6：台阶 / 半砖等）直接走上去，不用跳。
            double stepHeight = mob.getAttributeValue(
                    Attributes.STEP_HEIGHT);
            if (rise <= stepHeight + 0.05) continue;
            if (level.getBlockState(ahead.above()).isSolid()) continue;
            if (level.getBlockState(ahead.above(2)).isSolid()) continue;
            mob.getJumpControl().jump();
            mob.hasImpulse = true;
            return true;
        }
        return false;
    }

    /** 单位脚下 / 身体所在格是不是可攀爬方块（梯子 / 藤蔓 / 脚手架）。 */
    public static boolean isOnClimbable(Mob mob) {
        // 原版判定优先：它还包含"梯子 + 顶端打开活板门"这种组合，客户端 / 原版物理也认这个。
        if (mob.onClimbable()) return true;
        Level level = mob.level();
        BlockPos pos = mob.blockPosition();
        return level.getBlockState(pos).is(BlockTags.CLIMBABLE)
                || level.getBlockState(pos.above()).is(BlockTags.CLIMBABLE);
    }

    /**
     * 攀爬接近检查（士兵 / 首领每 tick 调用一次）。
     *
     *   上下梯子由寻路路线和梯子移动控制完成，这里只处理两件事：
     *   目标在另一层、附近有通得到目标的梯子且单位没在按路线走时，向目标补发一次寻路；
     *   骑乘单位先下马，让下一次寻路按步兵净空重新规划。
     *   目标高度与单位齐平（{@code |dy| < 1.5}）时直接跳过，不打扰已就位的单位。
     */
    public static void tickClimbApproach(Mob mob, double goalY, @Nullable Vec3 goal) {
        if (mob.level().isClientSide || !mob.isAlive()) return;
        double dy = goalY - mob.getY();
        if (dy > -1.5 && dy < 1.5) return;              // 目标就在这一层：用不上梯子
        if (mob.tickCount % 10 != 0) return;            // 梯子搜索每 10 tick 一次，别每 tick 扫
        PathNavigation nav = mob.getNavigation();
        Path active = nav.getPath();
        // 已经有完整路线在走时不需要再扫梯子；部分路径仍可能进不了目标层，
        // 这时才继续找梯子。
        if (active != null && !active.isDone() && active.canReach()) return;
        if (goal == null) return;
        // 已经在目标点上（驻守 / 就位的单位，目标就是脚下这一格）→ 不要替它重新发路径
        if (goal.distanceToSqr(mob.position()) < 4.0) return;
        BlockPos base = mob.blockPosition();
        LadderProbe probe = LADDER_PROBES.get(mob);
        BlockPos rung;
        if (probe != null && probe.tick > mob.tickCount - 20
                && probe.base.equals(base) && Math.abs(probe.goalY - goalY) < 2.0) {
            rung = probe.rung;
            if (rung != null && !ClimbColumns.isRung(mob.level(), rung)) rung = null;
        } else {
            rung = findClimbColumn(mob.level(), mob, goalY);
            LADDER_PROBES.put(mob, new LadderProbe(mob.tickCount, base, goalY, rung));
        }
        if (rung == null) return;
        if (mob.isPassenger()) {
            dismountForClimb(mob);                      // 坐骑不会爬梯子
            return;
        }
        // 已走完的旧路径会因节点相同被原版拒绝重发（骑乘时这里是坐骑的导航）。
        if (active != null) nav.stop();
        nav.moveTo(goal.x, goal.y, goal.z, 1.0);
    }

    /**
     * 在附近找一节能通往目标层的梯子：上行要求列顶够得着目标，下行要求梯子延伸到脚下。
     *   按环形逐圈扫描所有方向，斜前方或门后的梯子也能找到；找不到返回 null。
     */
    @Nullable
    private static BlockPos findClimbColumn(Level level, Mob mob, double goalY) {
        boolean ascending = goalY > mob.getY();
        BlockPos base = mob.blockPosition();
        for (int ring = 1; ring <= CLIMB_SEARCH_RADIUS; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;   // 只看当前这一圈
                    for (int dy = -2; dy <= 2; dy++) {
                        BlockPos rung = base.offset(dx, dy, dz);
                        if (!ClimbColumns.isRung(level, rung)) continue;
                        if (ascending) {
                            if (ClimbColumns.columnReachesHeight(level, rung, goalY, 3.0)) return rung;
                        } else if (ClimbColumns.columnBottomY(level, rung) <= mob.getY() - 2.0) {
                            return rung;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 单位是否正处于上下梯子这一段：脚在梯子格上，或移动目标点在梯子格上。
     *   上层的"最后几格直线驱动"遇到这种情况要让开，否则会把单位从梯子列里拖出去。
     *   只看脚下与当前移动目标，不看旧路径，避免已站上平台的单位被判成正在爬梯。
     */
    public static boolean isOnClimbRoute(Mob mob) {
        if (mob.level().isClientSide) return false;
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        if (ClimbColumns.isClimbCell(level, feet)) return true;
        MoveControl control = mob.getMoveControl();
        if (control == null) return false;
        return ClimbColumns.isClimbCell(level, BlockPos.containing(
                control.getWantedX(), control.getWantedY(), control.getWantedZ()));
    }

    /**
     * 原版路径是不是"把门 / 栅栏门当成一格台阶"。
     *   下一个路径节点比脚高 ≥0.6 格、且那一带是能用手开的门 → 原版 MoveControl
     *   会触发台阶跳（平地开门无故跳一下）。这种时候上层应该改用方块级 A* 精确路线
     *   （节点高度准确，不会跳）。
     */
    public static boolean pathStepsOverDoor(Mob mob, @Nullable Path path) {
        if (path == null || path.isDone()) return false;
        BlockPos next = path.getNextNodePos();
        if (next.getY() < mob.getY() + 0.6) return false;
        Level level = mob.level();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -2; dy <= 0; dy++) {
                    BlockState state = level.getBlockState(next.offset(dx, dy, dz));
                    if ((state.getBlock() instanceof DoorBlock
                            || state.getBlock() instanceof FenceGateBlock)
                            && isHandOpenable(state)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 平地在门边时压掉原版的"台阶跳"：
     *   原版 MoveControl 会把还没打开的门当成一格台阶，触发跳跃。
     *   若正前方就是能用手开的门、且单位并没有真的站在一个坎前，就把上升速度压掉。
     */
    public static void suppressDoorJump(Mob mob) {
        Vec3 dm = mob.getDeltaMovement();
        if (dm.y <= 0.05) return;
        float yaw = mob.getYRot() * ((float) Math.PI / 180F);
        float fx = -Mth.sin(yaw);
        float fz = Mth.cos(yaw);
        BlockPos ahead = BlockPos.containing(
                mob.getX() + fx * 0.9, mob.getY(), mob.getZ() + fz * 0.9);
        BlockState state = mob.level().getBlockState(ahead);
        boolean doorAhead = (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof FenceGateBlock)
                && isHandOpenable(state);
        if (!doorAhead) return;
        mob.setDeltaMovement(dm.x, 0.0, dm.z);
        mob.fallDistance = 0;
    }

    /**
     * 身边 2 格内有没有"能用手开的门"，优先返回"前方、离得近"的那扇。
     *   用于让单位走到门洞正中通过 —— 开着的门板只有 3/16 格厚的碰撞箱，
     *   沿边缘走会一直撞在门框上导致减速。
     */
    @Nullable
    public static BlockPos handDoorNear(Mob mob) {
        Level level = mob.level();
        BlockPos base = mob.blockPosition();
        DoorProbe cached = DOOR_PROBES.get(mob);
        if (cached != null && cached.tick > mob.tickCount - 3 && cached.base.equals(base)) {
            if (cached.door == null) return null;
            BlockState state = level.getBlockState(cached.door);
            if (state.getBlock() instanceof DoorBlock && isHandOpenable(state)
                    && cached.door.distToCenterSqr(mob.getX(), mob.getY(), mob.getZ()) <= 16.0) {
                return cached.door;
            }
        }
        Vec3 dm = mob.getDeltaMovement();
        double mx = dm.x;
        double mz = dm.z;
        double mlen = Math.sqrt(mx * mx + mz * mz);
        if (mlen < 0.01) {
            float yaw = mob.getYRot() * ((float) Math.PI / 180F);
            mx = -Mth.sin(yaw);
            mz = Mth.cos(yaw);
            mlen = 1.0;
        }
        mx /= mlen;
        mz /= mlen;

        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        Vec3 chest = new Vec3(mob.getX(),
                mob.getY() + Math.min(1.2, mob.getBbHeight() * 0.6), mob.getZ());
        // 只认真正要走过去的那扇门：3 格内、基本在正前方（否则不抢寻路）
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(p);
                    if (!(state.getBlock() instanceof DoorBlock) || !isHandOpenable(state)) {
                        continue;
                    }
                    double tox = p.getX() + 0.5 - mob.getX();
                    double toz = p.getZ() + 0.5 - mob.getZ();
                    double len = Math.sqrt(tox * tox + toz * toz);
                    if (len < 0.05) {
                        DOOR_PROBES.put(mob, new DoorProbe(mob.tickCount, base, p));
                        return p;   // 已经站在门洞里
                    }
                    double dot = (tox * mx + toz * mz) / len;
                    // 背后的门不管（除非已经踩在门洞里，需要继续对齐穿过去）
                    if (dot < 0.1 && len > 0.6) continue;
                    // 稍微远一点的门必须几乎正对，避免把单位从寻路路线上带偏
                    if (len > 1.5 && dot < 0.75) continue;
                    double score = len + (1.0 - dot) * 2.0;
                    // 隔墙不算：墙那一边的门不参与"穿门接管"，否则会把单位往墙上带 / 隔墙开门
                    if (score < bestScore && hasClearLineToBlock(level, chest, p)) {
                        bestScore = score;
                        best = p;
                    }
                }
            }
        }
        DOOR_PROBES.put(mob, new DoorProbe(mob.tickCount, base, best));
        return best;
    }

    /**
     * 门洞里"没有门板碰撞"的那半边中心点：
     *   开着的门板只有 3/16 格厚、贴在某一条边上，0.6 宽的单位走方块正中心仍会蹭到板边减速。
     *   这里直接用门当前的实际碰撞箱算出空出来的那半边中心（世界坐标，高度 = 单位脚高度）。
     */
    public static Vec3 doorPassPoint(Mob mob, BlockPos door) {
        Level level = mob.level();
        BlockState state = level.getBlockState(door);
        double tx = door.getX() + 0.5;
        double tz = door.getZ() + 0.5;
        AABB shape = state.getCollisionShape(level, door).bounds();
        if (shape.maxX - shape.minX > 1.0E-4 || shape.maxZ - shape.minZ > 1.0E-4) {
            double sizeX = shape.maxX - shape.minX;
            double sizeZ = shape.maxZ - shape.minZ;
            // getCollisionShape().bounds() 是方块本地坐标（0..1），不要再减方块坐标
            if (sizeX < 0.5) {
                tx = door.getX() + ((shape.minX + shape.maxX) / 2 < 0.5
                        ? (shape.maxX + 1.0) / 2 : shape.minX / 2);
            }
            if (sizeZ < 0.5) {
                tz = door.getZ() + ((shape.minZ + shape.maxZ) / 2 < 0.5
                        ? (shape.maxZ + 1.0) / 2 : shape.minZ / 2);
            }
        }
        return new Vec3(tx, mob.getY(), tz);
    }

    /** 让本模组的单位为了攀爬下马（其它生物没有坐骑逻辑，忽略即可）。 */
    private static void dismountForClimb(Mob mob) {
        if (mob instanceof AbstractArmySoldier soldier) {
            soldier.dismountForClimb();
        } else if (mob instanceof AbstractArmyCommander commander) {
            commander.dismountForClimb();
        }
    }

    /**
     * 清掉"移动控制器"里残留的想去点。
     *   只 stop 导航不会清移动控制器：它会继续把单位往旧目标（例如砖块里的平台边缘）推，
     *   在梯子上顶着墙 → 触发原版"梯子上贴着墙就往上跳 0.2"的机制，
     *   把下滑 / 迈出梯子的动作顶回去（表现为在梯子上擦着砖块、下不去、来回上下）。
     */
    public static void stopMovementInput(Mob mob) {
        mob.getMoveControl().setWantedPosition(mob.getX(), mob.getY(), mob.getZ(), 0.0);
    }

    /** 停止导航、清除水平惯性并清掉移动控制器的残留目标。 */
    public static void haltMovement(Mob mob) {
        if (mob == null) return;
        mob.getNavigation().stop();
        Vec3 dm = mob.getDeltaMovement();
        mob.setDeltaMovement(0.0, dm.y, 0.0);
        stopMovementInput(mob);
    }

    public static void clearCaches() {
        DOOR_PROBES.clear();
        LADDER_PROBES.clear();
        LINE_PROBES.clear();
    }

    private record DoorProbe(int tick, BlockPos base, @Nullable BlockPos door) {}

    private record LadderProbe(int tick, BlockPos base, double goalY, @Nullable BlockPos rung) {}

    private record LineKey(ResourceLocation dimension, long worldVersion,
                           long fromX, long fromY, long fromZ,
                           long toX, long toY, long toZ, int widthHash) {}

    private record LineProbe(long tick, boolean result) {}
}
