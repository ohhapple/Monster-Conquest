package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;

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
    private static long heavyAttemptTick = Long.MIN_VALUE;
    private static int heavyAttemptUsed = 0;
    private static long lastHeavyAttemptTick = Long.MIN_VALUE;
    private static boolean lastHeavyAttemptDeferred = false;

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
                        .max(net.minecraft.core.Direction.Axis.Y) > 1.05) continue;
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
            for (int dy = -2; dy <= 2; dy++) {
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
        net.minecraft.world.level.pathfinder.Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        double mobX = mob.getX();
        double mobZ = mob.getZ();
        for (int i = 0; i < path.getNodeCount(); i++) {
            net.minecraft.world.level.pathfinder.Node node = path.getNode(i);
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

    /** 该格能不能站：脚下有正常支撑、本体与头上一格无实心方块、不危险。 */
    public static boolean canStandOn(Level level, BlockPos pos) {
        BlockPos belowPos = pos.below();
        BlockState below = level.getBlockState(belowPos);
        if (!below.isSolid()) return false;
        if (below.getCollisionShape(level, belowPos).max(Direction.Axis.Y) > 1.05) return false;
        if (isDangerousBlock(below)) return false;
        BlockState here = level.getBlockState(pos);
        if (here.isSolid() || isDangerousBlock(here)) return false;
        BlockState above = level.getBlockState(pos.above());
        if (above.isSolid() || isDangerousBlock(above)) return false;
        return true;
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
                    net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
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
     * 该位置的梯子柱是否"能走"：
     *   上行：不仅上方要还有梯子 / 空位，整根梯子还必须通到目标高度附近（差 ≤2 格）——
     *         半截装饰梯 / 断头梯不能当上楼通道，否则单位爬到顶就悬着卡住。
     *   下行：只要踩得上梯子就行，不做限制。
     */
    private static boolean ladderLeadsToGoal(Level level, BlockPos ladder, double goalY, boolean ascending) {
        if (!ascending) return true;   // 往下走：只要踩得上梯子就行
        if (!level.getBlockState(ladder.above()).is(BlockTags.CLIMBABLE)
                && !level.isEmptyBlock(ladder.above())) return false;
        return goalY <= climbableTopY(level, ladder) + 2.0;
    }

    /** 从这一节可攀爬方块往上数到顶，返回最高那一节的 Y。 */
    private static int climbableTopY(Level level, BlockPos climbable) {
        int top = climbable.getY();
        for (int i = 1; i <= 48; i++) {
            BlockPos up = climbable.above(i);
            if (!level.getBlockState(up).is(BlockTags.CLIMBABLE)) break;
            top = up.getY();
        }
        return top;
    }

    /** 从这一节可攀爬方块往下数到底，返回最低那一节的 Y。 */
    private static int climbableBottomY(Level level, BlockPos climbable) {
        int bottom = climbable.getY();
        for (int i = 1; i <= 48; i++) {
            BlockPos down = climbable.below(i);
            if (!level.getBlockState(down).is(BlockTags.CLIMBABLE)) break;
            bottom = down.getY();
        }
        return bottom;
    }

    /**
     * 爬梯子辅助（士兵 / 首领 / 巡逻队长通用）：
     *   · 已经站在梯子 / 藤蔓上，且目标在上方 → 持续向上爬；
     *   · 站在"能往上走"的梯子旁边（水平 1 格内）且目标明显在上方 → 朝梯子挪一小步准备开爬。
     *
     * @param goalY 当前想去的目标高度（战斗目标 / 首领 / 行军目标）
     * @return 是否正在爬 / 正在靠近梯子（true 时调用方不要再做其它移动干预）
     */
    public static boolean tryClimbLadder(Mob mob, double goalY) {
        Level level = mob.level();
        if (level.isClientSide) return false;

        boolean climbable = isOnClimbable(mob);
        BlockPos feet = mob.blockPosition();
        boolean goalBelow = goalY < mob.getY() - 0.5;   // 目标是明显下方：不要往上翻平台
        boolean deepBelow = goalY < mob.getY() - 1.0;   // 需要顺着梯子往下滑
        boolean ladderBelow = level.getBlockState(feet.below()).is(BlockTags.CLIMBABLE);

        // 平台 / 墙顶边缘的下行梯（最高一节不比脚高）：站在边缘、身边一格内就是这种梯子时
        //   也要接管，把单位推回梯子列里再往下滑 ——
        //   否则单位只在梯顶 / 平台边缘来回挪，永远迈不进梯子列（表现为卡在墙角的梯子边）。
        BlockPos edgeLadder = deepBelow && !climbable && !ladderBelow && mob.onGround()
                ? downLadderBeside(mob) : null;

        // 下行：目标是下方时，站在梯子上 / 悬在梯顶正上方（脚下就是梯子）就往下走。
        //   横向速度朝梯子方块中心修正：寻路 / 跟随的移动输入往往指向"背后的平台"，
        //     不修正的话单位会被移动目标从梯子上拖开（表现为"想远离梯子"），
        //     永远踩不上 / 待不住梯子。
        if (deepBelow && (climbable || ladderBelow || edgeLadder != null)) {
            if (mob.isPassenger()) {
                dismountForClimb(mob);
                return false;
            }
            BlockPos column = climbable ? feet : (ladderBelow ? feet.below() : edgeLadder);
            // "脚还在地上、身体被平台 / 墙顶边缘托着"（正要迈进梯子列，或悬在梯顶但还踩着边）：
            //   目标点取梯子格中心、用固定最小推力 —— 按距离缩放的推力在"目标就在脚边 0.05 格"时
            //   几乎为 0，身体又一直有支撑，会永远停在边缘迈不出去。
            //   已经真的悬空下落（onGround=false）时才用"避开梯子薄板"的开口侧偏移 + 距离缩放。
            boolean stepping = !climbable && mob.onGround();
            double cx = column.getX() + 0.5;
            double cz = column.getZ() + 0.5;
            if (!climbable && !stepping) {
                // 悬在梯顶正上方：把落点定在"梯子开口侧让开 0.25 格"处，
                //   避开梯子那 3/16 厚的碰撞板 —— 往方块中心推会在板边来回顶、下不去。
                BlockState columnState = level.getBlockState(column);
                if (columnState.getBlock() instanceof LadderBlock) {
                    Direction open = columnState.getValue(LadderBlock.FACING);
                    cx += open.getStepX() * 0.25;
                    cz += open.getStepZ() * 0.25;
                }
            }
            double dx = cx - mob.getX();
            double dz = cz - mob.getZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            double sx = 0.0;
            double sz = 0.0;
            if (len > 1.0E-3) {
                double strength;
                if (climbable) {
                    // 还踩着梯子：离中心越远修正越强（上限 = 原版梯子横向速度上限 0.15）。
                    strength = Math.min(0.15, len * 0.6);
                } else if (stepping) {
                    // 迈进梯子列：固定最小推力（否则会停在"目标点就在脚边"的平衡点上）。
                    strength = Math.min(0.30, Math.max(len * 0.8, 0.12));
                } else {
                    // 悬在梯顶：按距离比例推（不做固定大力，否则会越过落点来回抽动）。
                    strength = Math.min(0.30, Math.max(len * 0.8, 0.02));
                }
                sx = dx / len * strength;
                sz = dz / len * strength;
            }
            Vec3 v = mob.getDeltaMovement();
            // 还踩着梯子 → 匀速下滑；刚悬在梯顶上方 → 不再上飘，交给重力把它压进梯子列。
            double vy = climbable ? -0.15 : Math.min(v.y, -0.05);
            mob.setDeltaMovement(sx, vy, sz);
            mob.fallDistance = 0;
            mob.getNavigation().stop();
            stopMovementInput(mob);
            tryOpenNearbyDoors(mob);   // 攀爬时也能开门
            return true;
        }

        // 出梯 / 选层：站在梯子上，且目标高度已经到达（脚上方 0.5 格 ~ 下方 1 格之间）时，
        //   朝旁边能站立的地面迈出去。
        //   · 多层贯通的梯子：在"目标那一层"停住并迈出去，不会一路滑到最底层；
        //   · 梯底：同样在这里退场，不用再靠移动输入和梯子物理硬推。
        // 站在梯子上、目标高度已经到达（脚上方 0.5 格 ~ 下方 1 格之间）时，朝旁边能站的地面迈出去。
        //   但"脚踩在实地上、就站在梯子格里"（刚从旁边走进 / 路过梯子格）不做这个动作：
        //     水平路径可能正好穿过梯子格，这里一推就会和"走向自己的阵位"互相顶，
        //     单位会在梯子格边上反复挪动（表现为卡在梯子 / 墙角来回挪）。这种情况直接走开即可。
        if (climbable && goalY <= mob.getY() + 0.5 && goalY >= mob.getY() - 1.0
                && (goalBelow || !mob.onGround())) {
            Direction support = ladderSupportSide(level, feet);
            Direction open = support != null ? support.getOpposite() : null;
            if (tryStepOffLadder(mob, level, feet, open)) return true;
        }

        // 梯顶翻越：站在梯子 / 藤蔓 / 脚手架上，或者刚翻过最后一级悬在梯顶上方时，
        //   只要目标高度就在头顶 1.5 格内（含齐平、略低），先尝试横移到旁边真正能站的平台。
        //   这一步必须排在"还要继续向上爬（goalY > y + 0.5）"之前：
        //   单位爬到"目标下方 0.5 格"后爬升条件不再成立 → 原版梯子物理开始让它下滑 →
        //   下一 tick 条件又成立继续爬 —— 于是永远卡在最高一节来回上下，上不了平台。
        //   落脚点要同时覆盖"和脚同层"与"在脚上方一格"（梯子最高一节与平台地板同层）两种结构。
        boolean hoveringOverLadderTop = !climbable && ladderBelow;
        // 目标在下方时不做"翻上平台"，否则单位想下梯子时会被反方向推回平台边缘。
        if ((climbable || hoveringOverLadderTop) && !goalBelow && goalY <= mob.getY() + 1.5) {
            if (tryTopOut(mob, level, climbable)) return true;
        }

        boolean climbing = climbable && goalY > mob.getY() + 0.5;
        if (climbing) {
            // 已经踩在梯子上也要确认这根梯子通得到目标：
            //   装饰性断梯爬到顶只会悬着 / 卡住，不能继续往上顶。
            BlockPos rung = level.getBlockState(feet).is(BlockTags.CLIMBABLE) ? feet : feet.above();
            if (level.getBlockState(rung).is(BlockTags.CLIMBABLE)
                    && !ladderLeadsToGoal(level, rung, goalY, true)) {
                climbing = false;
            }
        }
        BlockPos ladder = climbing ? null : findLadderToClimb(level, mob, goalY);
        if (!climbing && ladder == null) return false;

        // 坐骑（战马）无法爬梯子 / 脚手架 / 藤蔓：必须下马才能爬，
        //   攀爬期间持续抑制自动重上马；下一 tick 才会真正开始爬。
        if (mob.isPassenger()) {
            // 只要找到要走的梯子就先下马（搜索半径已放宽，很远也能找到）；
            //   骑在马上没法走梯子，继续骑过去也只会在平台边上打转。
            dismountForClimb(mob);
            return false;
        }

        // 1) 已经踩在梯子上：目标是上方就向上爬
        if (climbing) {
            Vec3 v = mob.getDeltaMovement();
            mob.setDeltaMovement(v.x * 0.4, 0.2, v.z * 0.4);
            mob.fallDistance = 0;
            mob.getNavigation().stop();
            // 脚手架：每一层都是"底部厚板"的碰撞箱，光靠向上速度会被下一层挡住；
            //   玩家也是跳着上脚手架的 —— 这里补一次原版跳跃把身体抬过台阶。
            if (level.getBlockState(feet).is(Blocks.SCAFFOLDING)
                    || level.getBlockState(feet.below()).is(Blocks.SCAFFOLDING)) {
                mob.getJumpControl().jump();
            }
            tryOpenNearbyDoors(mob);   // 攀爬时也能开门（例如梯子顶上的门 / 活板门）
            return true;
        }

        // 2) 目标明显在上方（≥2 格）时，靠近旁边能往上走的梯子。
        //    靠近点取"梯子的开口侧"（背对支撑墙那半边）稍稍偏移：
        //      梯子贴墙的那条薄碰撞箱会挡住 0.7 宽的巡逻队长 / 首领，
        //      走方块正中心会被卡在梯子侧面进不去（实测士兵能进、队长进不去）。
        BlockState ladderState = level.getBlockState(ladder);
        double tx = ladder.getX() + 0.5;
        double tz = ladder.getZ() + 0.5;
        if (ladderState.getBlock() instanceof LadderBlock) {
            Direction open = ladderState.getValue(LadderBlock.FACING);
            tx += open.getStepX() * 0.32;
            tz += open.getStepZ() * 0.32;
        }
        double dx = tx - mob.getX();
        double dz = tz - mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) return false;

        // 离梯子还远：优先让寻路把单位"走到梯子旁边能站的那一格"，
        //   光靠自己给 0.12 的横向速度顶不过寻路（它想去的平台边缘在反方向）。
        //   每 10 tick 重发一次（和移动指令同频），避免每 tick 重算路径造成掉刻。
        BlockPos access = findLadderAccessSpot(level, ladder, feet);
        // 固定每 10 tick 重发一次：不再"没有路径就每 tick 重算"（登梯口走不到时那会非常费）
        if (access != null && mob.tickCount % 10 == 0) {
            if (mob.getNavigation().moveTo(
                    access.getX() + 0.5, access.getY(), access.getZ() + 0.5, 1.0)) {
                mob.fallDistance = 0;
                tryOpenNearbyDoors(mob);
                return true;
            }
        }

        Vec3 v = mob.getDeltaMovement();
        // 目标在下方时用满梯子横向速度上限（0.15）并允许一点下坠：
        //   否则寻路输入（指向平台边缘）会和这点推力互相抵消，单位永远迈不出平台边缘。
        double push = deepBelow ? 0.15 : 0.12;
        double vy = deepBelow ? Math.max(v.y, -0.1) : Math.max(v.y, -0.05);
        mob.setDeltaMovement(dx / len * push, vy, dz / len * push);
        mob.fallDistance = 0;
        mob.getNavigation().stop();
        return true;
    }

    /**
     * 梯顶翻越：在单位横向相邻的 4 格里找一个"真正能站住"的落脚点并持续推过去。
     *   · 平台通常在梯子的支撑墙那一侧 —— LadderBlock.FACING 指向梯子开口，
     *     支撑墙在 FACING.getOpposite()，所以优先朝那个方向试；
     *   · 高度同时考虑两种常见结构：
     *       - 梯子最高一节与平台地板同一层 → 落脚点在脚上方一格（最常见的"上平台"结构）；
     *       - 单位已经翻过最后一级、悬在梯顶上方 → 落脚点与脚齐平。
     *
     * @param onRung true = 此刻还踩在可攀爬方块上；false = 悬在梯顶上方（刚翻过最后一级）
     * @return 是否已找到落脚点并施加了移动速度
     */
    private static boolean tryTopOut(Mob mob, Level level, boolean onRung) {
        if (mob.isPassenger()) return false;
        BlockPos feet = mob.blockPosition();
        Direction supportSide = ladderSupportSide(level, feet);
        Direction[] order = supportSide != null
                ? new Direction[] {
                        supportSide,
                        supportSide.getClockWise(),
                        supportSide.getCounterClockWise(),
                        supportSide.getOpposite() }
                : new Direction[] {
                        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
        for (Direction dir : order) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos spot = feet.relative(dir).above(dy);
                if (!canStandOn(level, spot)) continue;
                if (pushToTopOutSpot(mob, spot, onRung)) return true;
            }
        }
        return false;
    }

    /** 梯子的支撑墙方向（平台通常在这一侧）：FACING 指向开口，支撑墙在反方向；找不到梯子返回 null。 */
    @Nullable
    private static Direction ladderSupportSide(Level level, BlockPos feet) {
        for (BlockPos p : new BlockPos[] { feet, feet.above(), feet.below() }) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof LadderBlock) {
                return state.getValue(LadderBlock.FACING).getOpposite();
            }
        }
        return null;
    }

    /**
     * 梯子的"登梯口"：梯子旁边（高度 ±1）真正能站住、且离单位最近的一格。
     *   离梯子还远时先用原版寻路把单位走到这一格，再交给攀爬 / 下滑逻辑。
     */
    @Nullable
    private static BlockPos findLadderAccessSpot(Level level, BlockPos ladder, BlockPos from) {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = ladder.relative(dir);
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos spot = side.above(dy);
                if (!canStandOn(level, spot)) continue;
                double ddx = spot.getX() - from.getX();
                double ddy = spot.getY() - from.getY();
                double ddz = spot.getZ() - from.getZ();
                double sq = ddx * ddx + ddy * ddy + ddz * ddz;
                if (sq < bestSq) {
                    bestSq = sq;
                    best = spot;
                }
            }
        }
        return best;
    }

    /**
     * 梯底退场：在梯子最下面一节旁边找一个"真正能站住"的地面并迈过去。
     *   优先梯子的开口侧（背对支撑墙那一侧，玩家下梯子也是从这一边走出去）。
     *
     * @param preferred 梯子开口方向；null（藤蔓 / 脚手架）时按固定顺序试 4 个方向
     * @return 是否找到落脚点并施加了移动速度
     */
    private static boolean tryStepOffLadder(Mob mob, Level level, BlockPos feet, @Nullable Direction preferred) {
        BlockPos spot = findStepOffSpot(level, feet, preferred);
        if (spot == null) return false;
        double tx = spot.getX() + 0.5;
        double tz = spot.getZ() + 0.5;
        double dx = tx - mob.getX();
        double dz = tz - mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) return false;
        Vec3 v = mob.getDeltaMovement();
        // 横推 + 允许继续下坠：落地后由目标寻路接管。
        mob.setDeltaMovement(dx / len * 0.15, Math.min(v.y, -0.05), dz / len * 0.15);
        mob.fallDistance = 0;
        mob.hasImpulse = true;
        mob.getNavigation().stop();
        stopMovementInput(mob);
        tryOpenNearbyDoors(mob);
        return true;
    }

    /** 梯子旁边能站的落脚点（优先开口侧）；没有返回 null。 */
    @Nullable
    private static BlockPos findStepOffSpot(Level level, BlockPos feet, @Nullable Direction preferred) {
        Direction[] order = preferred != null
                ? new Direction[] {
                        preferred,
                        preferred.getClockWise(),
                        preferred.getCounterClockWise(),
                        preferred.getOpposite() }
                : new Direction[] {
                        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
        for (Direction dir : order) {
            BlockPos spot = feet.relative(dir);
            if (!canStandOn(level, spot)) continue;
            return spot;
        }
        return null;
    }

    /** 给"梯顶翻越"施加一次横向推力（还踩着梯子时顺带保底向上 / 跳跃）；返回是否成功施加。 */
    private static boolean pushToTopOutSpot(Mob mob, BlockPos spot, boolean onRung) {
        double tx = spot.getX() + 0.5;
        double tz = spot.getZ() + 0.5;
        double dx = tx - mob.getX();
        double dz = tz - mob.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-3) return false;

        Vec3 v = mob.getDeltaMovement();
        double vy;
        if (onRung) {
            // 还踩着梯子：一边继续爬（保底 0.12 向上 + 原版梯子物理），一边横推。
            // 落脚点比脚高一格时按原版跳跃 —— 原版会把"梯子上跳跃"转成 0.2 的额外向上速度，
            // 正好把身体抬过平台边缘，之后横向速度才不会被平台侧面的碰撞箱吃掉。
            if (spot.getY() > mob.getY() + 0.4) mob.getJumpControl().jump();
            vy = Math.max(v.y, 0.12);
        } else {
            // 已经翻过最后一级、悬在梯顶上方：不再往上飘，保持横向推力，让重力把单位放到平台上。
            vy = Math.min(v.y, 0.08);
        }
        // 0.15 = 原版在梯子上的横向速度上限（LivingEntity.handleOnClimbable 的 clamp 值）。
        mob.setDeltaMovement(dx / len * 0.15, vy, dz / len * 0.15);
        mob.fallDistance = 0;
        mob.hasImpulse = true;
        mob.getNavigation().stop();
        stopMovementInput(mob);
        tryOpenNearbyDoors(mob);
        return true;
    }

    /** 目标在上方（≥2 格）或下方（≥1.5 格）时，找身边"能往上/往下走"的可攀爬方块；没有返回 null。 */
    @Nullable
    private static BlockPos findLadderToClimb(Level level, Mob mob, double goalY) {
        boolean ascending = goalY >= mob.getY() + 2.0;
        boolean descending = goalY <= mob.getY() - 1.5;
        if (!ascending && !descending) return null;
        BlockPos base = mob.blockPosition();
        int minDy = descending ? -3 : -1;
        int maxDy = 2;

        if (ascending) {
            // 上行：由近到远扫四条射线 —— "先走到附近的梯子 / 脚手架 / 藤蔓再爬"。
            // 身边有正常路径在走时只扫 2 格（省性能）；没有路径可走（卡在塔下）时才扫到 8 格。
            int maxRadius = mob.getNavigation().isDone() ? 8 : 2;
            for (int radius = 1; radius <= maxRadius; radius++) {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    for (int dy = minDy; dy <= maxDy; dy++) {
                        BlockPos ladder = base.relative(dir, radius).above(dy);
                        if (!level.getBlockState(ladder).is(BlockTags.CLIMBABLE)) continue;
                        if (!ladderLeadsToGoal(level, ladder, goalY, true)) continue;
                        return ladder;
                    }
                }
            }
            return null;
        }

        // 下行：
        //   ① 先在 6 格内"按圈全方向"扫描 —— 实心方块 / 小高台的梯子可能在对角线方向，
        //      只扫正东正西正南正北四条线会永远找不到（单位就只会站在最近的边缘）；
        //   ② 再沿四条射线扫到 24 格（大平台 / 大屋顶的梯子可能离"离目标最近的那条边"很远）。
        //   每一列取"最上面一节"梯节（离脚下走道最近），并优先选旁边有能站的登梯口的梯子 ——
        //   只有找到登梯口，寻路才能把单位送过去。
        BlockPos fallback = null;
        for (int ring = 1; ring <= 6; ring++) {
            int loDy = ring > 4 ? -2 : minDy;
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    BlockPos ladder = scanDownColumn(level, base, dx, dz, loDy, maxDy);
                    if (ladder == null) continue;
                    if (fallback == null) fallback = ladder;
                    if (findLadderAccessSpot(level, ladder, base) != null) return ladder;
                }
            }
        }
        for (int radius = 7; radius <= 24; radius++) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos ladder = scanDownColumn(level, base,
                        dir.getStepX() * radius, dir.getStepZ() * radius, -2, maxDy);
                if (ladder == null) continue;
                if (fallback == null) fallback = ladder;
                if (findLadderAccessSpot(level, ladder, base) != null) return ladder;
            }
        }
        return fallback;
    }

    /** 下行用：在某个水平偏移的竖列里从高到低找最高的一节梯子；没有返回 null。 */
    @Nullable
    private static BlockPos scanDownColumn(Level level, BlockPos base,
                                          int dx, int dz, int loDy, int hiDy) {
        for (int dy = hiDy; dy >= loDy; dy--) {
            BlockPos p = base.offset(dx, dy, dz);
            if (level.getBlockState(p).is(BlockTags.CLIMBABLE)) return p;
        }
        return null;
    }

    /**
     * 供"前往下方"的移动指令使用：目标在下方、且身边能找到可下的梯子时，
     *   返回梯子旁边"登梯口"的坐标 —— 寻路先走到这里，之后由实体自身的攀爬逻辑下梯。
     *   没有梯子 / 已经在梯子上 / 目标不在下方 → null（照常按原目标寻路）。
     */
    @Nullable
    public static Vec3 descentAccessPoint(Mob mob, @Nullable Vec3 goal) {
        if (goal == null || mob.level().isClientSide) return null;
        if (isOnClimbable(mob)) {
            // 已经站在梯子上、且已经下到目标那一层 → 把寻路目标换成"梯子旁边的落脚点"，
            //   否则寻路会继续朝目标（可能是梯子背后的墙里）推，把单位顶在梯子上原地上下。
            //   目标还明显在下方 → null（交给下滑逻辑）；目标在头顶 → null（交给爬升 / 翻越逻辑）。
            if (goal.y < mob.getY() - 1.0 || goal.y > mob.getY() + 0.5) return null;
            BlockPos feet = mob.blockPosition();
            Direction support = ladderSupportSide(mob.level(), feet);
            BlockPos spot = findStepOffSpot(mob.level(), feet,
                    support != null ? support.getOpposite() : null);
            return spot == null ? null
                    : new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        }
        if (goal.y >= mob.getY() - 1.5) return null;
        BlockPos ladder = findLadderToClimb(mob.level(), mob, goal.y);
        if (ladder == null) return null;
        BlockPos access = findLadderAccessSpot(mob.level(), ladder, mob.blockPosition());
        if (access == null) return null;
        return new Vec3(access.getX() + 0.5, access.getY(), access.getZ() + 0.5);
    }

    /**
     * 单位是否"正踩在梯子上（或刚悬在梯顶正上方）并且目标是下方"。
     *   这种状态下上层要停掉自己的寻路：原版只会给一条"爬回平台"的半路路径，
     *   会和下滑互相顶 —— 表现为在梯子上擦着砖块、下滑途中又想往上爬、
     *   落地后还被旧路线往回拉。
     */
    public static boolean isDescendingOnLadder(Mob mob, @Nullable Vec3 goal) {
        if (goal == null || mob.level().isClientSide) return false;
        if (goal.y >= mob.getY() - 0.5) return false;
        if (isOnClimbable(mob)) return true;
        if (mob.level().getBlockState(mob.blockPosition().below()).is(BlockTags.CLIMBABLE)) return true;
        // 站在平台 / 墙顶边缘、脚边一格内就是"往下走的梯子"（最高一节不比脚高）时，
        //   同样算"正在下梯子"：上层不要再发寻路，否则寻路会把单位按在平台上、
        //   和"迈进梯子列往下滑"互相顶 —— 表现为在梯顶 / 平台边缘来回挪，永远下不去。
        return downLadderBeside(mob) != null;
    }

    /**
     * 站在平台 / 墙顶边缘时，身边一格内"往下走的梯子"（梯子最高一节不比脚高）；
     *   找不到返回 null。用于"从这里顺着梯子下去"的判定与接管。
     *   排除"最高一节比脚高"的梯子 —— 那是从这一层往上爬的梯子，不是下行入口。
     */
    @Nullable
    private static BlockPos downLadderBeside(Mob mob) {
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = feet.relative(dir);
            BlockPos top = level.getBlockState(side).is(BlockTags.CLIMBABLE) ? side
                    : (level.getBlockState(side.below()).is(BlockTags.CLIMBABLE) ? side.below() : null);
            if (top == null) continue;
            // "下行入口"的两个条件：最高一节不比脚高（不是往上爬的梯子），
            //   且整根梯子确实往脚下延伸（真的能往下走，而不是和自己齐平的装饰梯）。
            if (climbableTopY(level, top) <= feet.getY()
                    && climbableBottomY(level, top) <= feet.getY() - 2) {
                return top;
            }
        }
        return null;
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
                    if (len < 0.05) return p;   // 已经站在门洞里
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
        net.minecraft.world.phys.AABB shape = state.getCollisionShape(level, door).bounds();
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
        if (mob instanceof com.ohhapple.monster_conquest.entity.AbstractArmySoldier soldier) {
            soldier.dismountForClimb();
        } else if (mob instanceof com.ohhapple.monster_conquest.entity.AbstractArmyCommander commander) {
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
}
