package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.player.PlayerFormationCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class FormationGoal extends Goal {

    private static final double ARRIVE_HORIZ_SQR = 2 * 2;
    /** 驻守滞回：已到位后偏离超过这个距离并持续一小段时间才重新起步。 */
    private static final double LEAVE_HORIZ_SQR = 3.5 * 3.5;
    /**
     * 到位判定的高度容差：站在墙顶 / 平台上时，水平方向压在阵位上不算到位 ——
     *   否则站在 10 格高的墙顶、水平距离够了就被判成"已到位"再也不下来。
     *   容差覆盖骑乘时骑手比马高约 0.8 格、以及跳跃 / 小坡的偏移。
     */
    private static final double ARRIVE_Y_TOLERANCE = 1.6;
    private static final int LEAVE_DELAY_TICKS = 10;
    /** 槽位寻路的最短重发间隔（tick）。 */
    private static final int SLOT_REISSUE_INTERVAL = 10;
    /** 行军跟随时沿轨迹移动的速度。 */
    private static final double MARCH_FOLLOW_SPEED = 1.0D;
    private static final int SETTLE_TICKS = 60;

    /**
     * 自由站位舒适半径平方：10 格。
     *   在此半径内 → 停下，由 separation 自然分布，不锁朝向。
     *   超出此半径 → 向中心靠近。
     */
    private static final double FREE_COMFORT_SQR = 10.0 * 10.0;

    private final AbstractArmySoldier soldier;
    private final MarchTrailFollow marchFollow = new MarchTrailFollow();

    private double bestDistSqr = Double.MAX_VALUE;
    private int settleTimer = 0;
    private int leaveTicks = 0;
    @Nullable private Vec3 lastSlotTarget = null;
    private int slotReissueTick = 0;
    /** 卡住检查：连续多个窗口没有更接近当前目标才启用方块级 A* 绕路。 */
    private int progressCheckTick = 0;
    private double lastGoalDistSq = -1.0;
    private int noProgressChecks = 0;
    private static final int PROGRESS_CHECK_INTERVAL = 20;
    private static final int NO_PROGRESS_LIMIT = 3;

    /** 智能绕路检查节流（tick）：原版路径够不到目标时改用方块级 A* 绕路。 */
    private int smartRouteCheckTick = 0;
    private static final int SMART_ROUTE_CHECK_INTERVAL = 10;

    /** 寻路重发节流：没有路径时 5 tick 重试一次，有路径在走时 10 tick 重发一次。 */
    private int navReissueTick = 0;

    /**
     * "顶着方块走不动"监视：
     *   目标上不去 / 进不去时（命令点在墙顶、盒子里等），士兵会一直贴着方块推。
     *   这里按 40 tick 一个窗口统计实际位移：连续多窗口几乎没动、且没有完整路径时，
     *   就近处站住或歇几秒再试，不再持续推墙。
     */
    private static final int PRESS_WINDOW_TICKS = 40;
    private static final double PRESS_TRAVEL_EPSILON = 1.0;
    private static final int PRESS_ABOVE_WINDOWS = 3;   // ~6 秒：目标在头顶上方且没梯子，多半上不去
    private static final int PRESS_NEAR_WINDOWS = 6;    // ~12 秒（目标就在旁边）
    private static final int PRESS_FAR_WINDOWS = 10;    // ~20 秒（目标还很远，多给点时间绕路）
    private static final int PRESS_NO_PROGRESS_WINDOWS = 5;  // ~10 秒没接近过目标
    private static final int PRESS_HOLD_TICKS = 100;    // 远的就歇 5 秒再重试
    private static final int PRESS_HOLD_MAX_TICKS = 1200;   // 连续顶住时最多歇 60 秒
    private int pressWatchTick = 0;
    private double pressLastX = Double.NaN;
    private double pressLastZ = Double.NaN;
    private double pressTravel = 0.0;
    private int pressStallWindows = 0;
    private int pressHoldUntil = 0;
    private int pressHoldTicks = 0;
    private double pressBestDistSq = Double.MAX_VALUE;
    private int pressNoProgressWindows = 0;

    /**
     * 能不能重发寻路：走位目标走不到时（导航里没有路径）不允许每 tick 重算，
     *   否则几十个单位一起打墙后 / 封闭盒子里的敌人时会把服务器 tick 占满。
     */
    private boolean canReissueNav() {
        if (soldier.tickCount < navReissueTick) return false;
        navReissueTick = soldier.tickCount
                + (soldier.getNavigation().isDone() ? 5 : 10);
        return true;
    }

    /**
     * 上一次见到的"移动指令代号"（{@link PlayerCommandState.SquadState#getMoveOrderStamp()}）。
     *   变了 = 玩家刚下了新的"去哪"命令 → 旧路径 / 旧绕路当场作废，立刻按新目标重新规划。
     */
    private int lastMoveOrderStamp = Integer.MIN_VALUE;

    /**
     * "去命令点"这一段路是否已经走完（每名士兵各记各的）。
     *   走完 = 到过命令点附近（6 格内），或命令本身已超时。
     *   走完之后就再也不回头去命令点，只奔自己的阵位 ——
     *   否则阵位离命令点超过 6 格的士兵会在"奔命令点"和"奔阵位"之间无限来回跑
     *   （宽阵型的两翼最明显：命令下达后围着命令点跑满整段命令时间）。
     */
    private boolean moveOrderLegDone = false;

    public FormationGoal(AbstractArmySoldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 自由移动：完全不参与阵型 / 跟随，交给原版游走 AI
        if (soldier.isFreeRoaming()) return false;
        // 刚放出来、还没被玩家指挥过的士兵：保持自然游走，不参与玩家阵型
        if (soldier.isPlayerArmy() && !soldier.isPlayerCommanded()) return false;
        LivingEntity leader = soldier.getLeaderEntity();
        if (leader == null || !leader.isAlive()) return false;
        AbstractArmyCommander commander = soldier.getCommander();
        // 巡逻队长的士兵不进阵型系统，走 FollowCommanderGoal 松散跟随
        if (commander instanceof AbstractPatrolCaptain) return false;
        // 首领没给这个兵排站位（例如阵型里没排到的兵种）→ 交给 FollowCommanderGoal 跟着走，
        //   否则这里会一直占着 MOVE 旗标，士兵既不站位也不跟随。
        if (commander != null && commander.getFormationSlot(soldier) == null) return false;
        // 首领有仇恨目标时正常不排阵型；但首领带着"手动行军"命令赶路时，
        //   没有自己目标的士兵仍然要跟着走，否则整队会原地不动。
        if (commander != null && commander.getCurrentThreat() != null
                && commander.getManualMarchTarget() == null) return false;
        LivingEntity target = soldier.getTarget();
        return target == null || !target.isAlive();
    }

    @Override
    public boolean canContinueToUse() { return canUse(); }

    @Override
    public void start() {
        bestDistSqr = Double.MAX_VALUE;
        settleTimer = 0;
        leaveTicks = 0;
        lastSlotTarget = null;
        slotReissueTick = 0;
        marchFollow.reset();
        smartRouteCheckTick = 0;
        // 刚接手移动：下一次判定当作"新命令"，确保先把路径对齐到当前命令目标
        lastMoveOrderStamp = Integer.MIN_VALUE;
        moveOrderLegDone = false;
        pressWatchTick = 0;
        pressLastX = Double.NaN;
        pressLastZ = Double.NaN;
        pressTravel = 0.0;
        pressStallWindows = 0;
        pressHoldUntil = 0;
        pressHoldTicks = 0;
        pressBestDistSq = Double.MAX_VALUE;
        pressNoProgressWindows = 0;
    }

    @Override
    public void stop() {
        soldier.getNavigation().stop();
        soldier.setAtFormationSlot(false);
        if (soldier.getVehicle() instanceof WarHorseEntity horse) {
            horse.setLockedYaw(null);
        }
        bestDistSqr = Double.MAX_VALUE;
        settleTimer = 0;
        leaveTicks = 0;
        lastSlotTarget = null;
        marchFollow.reset();
    }

    @Override
    public void tick() {

        AbstractArmyCommander commander = soldier.getCommander();
        if (commander != null) {
            tickForCommander(commander);
            return;
        }
        Player playerLeader = soldier.getCommanderPlayer();
        if (playerLeader != null) {
            tickForPlayer(playerLeader);
        }
    }

    // ===================== NPC 首领 =====================

    private void tickForCommander(AbstractArmyCommander commander) {
        // 行军：士兵沿首领走过的轨迹排队跟进（首领只算一次寻路）。
        //   首领停下后，附近（16 格内）的士兵立刻回到自己的槽位展开阵型；
        //   只有远在 16 格外的掉队兵才继续沿轨迹走到队伍附近。
        boolean useTrail = commander.isMarching()
                || (!soldier.isAtFormationSlot()
                    && soldier.position().distanceToSqr(commander.position()) > 16.0 * 16.0);
        if (useTrail) {
            if (marchFollow.tick(soldier, commander, MARCH_FOLLOW_SPEED)) {
                leaveSlot();
                resetSettle();
                return;
            }
        }

        Vec3 slot = commander.getFormationSlot(soldier);
        if (slot == null) {
            bestDistSqr = Double.MAX_VALUE;
            settleTimer = 0;
            return;
        }

        double dx = soldier.getX() - slot.x;
        double dz = soldier.getZ() - slot.z;
        double horizSqr = dx * dx + dz * dz;
        logDiag(commander, slot, horizSqr, false);

        if (soldier.isAtFormationSlot()) {
            // 驻守滞回：槽位随首领小幅移动 / 被队友挤开时不重新起步，
            //   偏离超过阈值并持续一小段时间才离开。
            if (horizSqr <= LEAVE_HORIZ_SQR) {
                leaveTicks = 0;
                resetSettle();
                arrive(commander.getFormationFacing());
                return;
            }
            if (++leaveTicks <= LEAVE_DELAY_TICKS) {
                resetSettle();
                arrive(commander.getFormationFacing());
                return;
            }
            leaveTicks = 0;
            leaveSlot();
        } else if (horizSqr <= ARRIVE_HORIZ_SQR
                && Math.abs(soldier.getY() - slot.y) <= ARRIVE_Y_TOLERANCE) {
            resetSettle();
            // 锁定"最近一次行进方向"（阵型朝向），而不是指挥官当下的朝向：
            //   指挥官站定后会自然环顾，阵型朝向不受影响。
            arrive(commander.getFormationFacing());
            return;
        }

        // 正在按方块级 A* 路线绕路 → 不做"磨蹭就停下"的判定（迷宫半路不许停）。
        if (soldier.isFollowingMarchRoute()) {
            resetSettle();
            return;
        }

        double targetY = groundY(slot.x, slot.z, slot.y);
        leaveSlot();
        if (pressWatch(slot, horizSqr)) return;

        // 最后 4 格：直线进场，避免快马绕着目标点打转进不了判定圈。
        Mob approachMover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        //   水里（原版地面导航在液体里默认不驱动）放宽到直线 24 格，直接游过去。
        boolean watery = approachMover.isInWater();
        // 要按"本体宽度"判定：宽体坐骑（战马 1.4 格）擦着墙角走时，中心线是通的、
        //   身体却会撞在角上 —— 只查中心线会把它一直朝墙角里带（单位贴着墙角推的来源之一）。
        if ((horizSqr < 16.0 || (watery && horizSqr < 576.0))
                && AiNavUtil.hasClearLineForBody(soldier.level(),
                approachMover.position().add(0, 0.5, 0), slot.add(0, 0.5, 0),
                approachMover.getBbWidth() * 0.5 + 0.05)) {
            soldier.getNavigation().stop();
            approachMover.getMoveControl().setWantedPosition(slot.x, slot.y, slot.z, 1.0D);
            return;
        }

        // 正前方有门 → 接管移动：从没有门板的那半边直线穿过（不跳、不蹭门框减速）
        if (tryDoorPass()) return;

        // 路径还在走且目标点没怎么变 → 不重发，避免每 tick 重算路径来回摆；
        //   路径走完 / 目标点明显移动 / 间隔到才重新规划。
        PathNavigation nav = soldier.getNavigation();
        if (!nav.isDone()) {
            if (lastSlotTarget != null && lastSlotTarget.distanceToSqr(slot) < 0.75 * 0.75) return;
        } else {
            // 导航刚走完：立刻清掉移动控制器里残留的旧目标点。
            //   否则马会继续朝"上一次的目标点"走（快速坐骑表现为掉头 / 来回拉锯）。
            AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        }
        if (nav.isDone()) {
            if (soldier.tickCount < slotReissueTick) {
                return;
            }
        }
        slotReissueTick = soldier.tickCount + SLOT_REISSUE_INTERVAL;
        lastSlotTarget = slot;
        Vec3 navGoal = navGoal(slot.x, targetY, slot.z);
        if (navGoal == null) return;
        nav.moveTo(navGoal.x, navGoal.y, navGoal.z, 1.0D);
        // 只有"确实推进不了"（连续 60 tick 没更接近槽位）才用方块级 A* 绕路；
        //   槽位落在墙里 / 迷宫外够不到时，先按"首领本人"的位置走。
        if (soldier.tickCount >= progressCheckTick) {
            progressCheckTick = soldier.tickCount + PROGRESS_CHECK_INTERVAL;
            double d = soldier.position().distanceToSqr(slot);
            if (lastGoalDistSq > 0 && d > lastGoalDistSq - 1.0) {
                if (++noProgressChecks >= NO_PROGRESS_LIMIT) {
                    noProgressChecks = 0;
                    Vec3 fallback = commander != null
                            ? commander.position()
                            : new Vec3(slot.x, targetY, slot.z);
                    // 先让 A* 规划绕路；A* 也没路且已经在目标附近（6 格内）才允许停下。
                    boolean planned = soldier.position().distanceToSqr(fallback) > 16.0
                            && soldier.startMarchRouteAround(fallback);
                    if (!planned && horizSqr <= 9.0) {
                        resetSettle();
                        arrive(commander.getFormationFacing());
                        return;
                    }
                }
            } else {
                noProgressChecks = 0;
            }
            lastGoalDistSq = d;
        }
    }

    private void tickForPlayer(Player player) {
        PlayerCommandState state = PlayerArmyManager.get(player.getUUID());

        // 无小队士兵用个人状态（同一套 SquadState 逻辑），只是不占用队号
        boolean lone = soldier.getSquadId() == 0;
        PlayerCommandState.SquadState ss = PlayerCommandState
                .stateForCommand(soldier, state);

        // ==================== 软驻守 ====================
        if (ss.isSoftStation()) {
            long now = player.level().getGameTime();

            boolean preciseActive = !lone
                    && state.getPreciseStrikeTarget(now) != null
                    && state.isSquadInPreciseStrike(soldier.getSquadId());

            // 无小队单兵：以自身是否有交战目标判断"是否在战斗中"；
            //   有队士兵沿用"精确打击 / 队友交战中"判断。
            boolean squadInCombat = preciseActive || (lone
                    ? (soldier.getTarget() != null && soldier.getTarget().isAlive())
                    : anySquadMateInCombat(player, soldier.getSquadId()));

            if (squadInCombat) {
                ss.setSoftStationLastCheckedGT(now);
                if (!lone) approachNearestFightingMate(player, soldier.getSquadId());
                return;
            }

            long since = ss.getSoftStationLastCheckedGT();
            if (since == 0L) {
                ss.setSoftStationLastCheckedGT(now);
                stopMover();
                return;
            }

            if (now - since >= 60L) {
                // 冷却结束 → 按 softStationOrigin 恢复：
                //    FOLLOW      → 恢复 FOLLOW
                //    SOFT_FOLLOW → 恢复 SOFT_FOLLOW
                //    FREE        → 恢复自由移动（战后继续自由游走）
                //    STATION     → 质心 STATION（按质心驻守）
                ss.setSoftStation(false);
                PlayerCommandState.MovementMode origin = ss.getSoftStationOrigin();
                ss.setSoftStationOrigin(PlayerCommandState.MovementMode.STATION);

                switch (origin) {
                    case FOLLOW -> {
                        ss.setMovement(PlayerCommandState.MovementMode.FOLLOW);
                        ss.setFormationAnchor(null);
                    }
                    case SOFT_FOLLOW -> {
                        ss.setMovement(PlayerCommandState.MovementMode.SOFT_FOLLOW);
                        ss.setFormationAnchor(null);
                    }
                    case FREE -> ss.applyFreeMoveOrder();
                    case STATION -> {
                        // 无小队单兵：战后在自己当前位置驻守
                        Vec3 center = lone
                                ? soldier.position()
                                : computeSquadCentroid(player, soldier.getSquadId());
                        ss.setFormationAnchor(center, player.getYRot(), now);
                    }
                }
                ss.setSoftStationLastCheckedGT(now);
            } else {
                stopMover();
                return;
            }
        }

        // ==================== 战斗保持 ====================
        if (soldier.isInCombatGrace()
                && ss.getMovement() == PlayerCommandState.MovementMode.FOLLOW) {
            stopMover();
            return;
        }

        PlayerCommandState.FormationMode mode = ss.getFormation();
        PlayerCommandState.MovementMode movement = ss.getMovement();

        // ==================== 自由站位 ====================
        if (mode == PlayerCommandState.FormationMode.FREE) {
            tickFreeRoam(player, ss);
            return;
        }

        // STATION 但锚点为 null → 原地待命
        if (movement == PlayerCommandState.MovementMode.STATION
                && ss.getFormationAnchor() == null) {
            stopMover();
            return;
        }

        Vec3 anchor;
        float yaw;
        if (movement == PlayerCommandState.MovementMode.STATION) {
            anchor = ss.getFormationAnchor();
            yaw = ss.getFormationAnchorYaw();
        } else {
            // FOLLOW / SOFT_FOLLOW：锚点 = 跟随对象当前位置
            // （默认指挥官自己；右键指定了跟随对象时用那个单位）
            LivingEntity follow = resolveFollowTarget(player, ss);
            anchor = new Vec3(follow.getX(), follow.getY(), follow.getZ());
            yaw = follow.getYRot();
        }

        // 指定点命令（前往 / 驻守）→ 阵型以命令点为中心展开；跟随 → 阵型排在主人身后
        boolean stationLike = movement == PlayerCommandState.MovementMode.STATION;
        Vec3 slot = PlayerFormationCalculator.getSlot(player, soldier, ss, mode, anchor, yaw, stationLike);
        if (slot == null) {
            if (movement == PlayerCommandState.MovementMode.STATION) {
                stopMover();
                return;
            }
            defaultFollow(resolveFollowTarget(player, ss));
            return;
        }

        // 新命令识别：SquadState 的"移动指令代号"变了 = 玩家刚给这个小队 / 单兵下了新命令。
        //   旧路径 / 旧绕路必须当场作废，否则单位会先沿旧路线走到一半才听新命令
        //   （表现为"明明下了新命令，它还朝旧地点走一小会儿"）。
        boolean orderChanged = ss.getMoveOrderStamp() != lastMoveOrderStamp;
        if (orderChanged) {
            lastMoveOrderStamp = ss.getMoveOrderStamp();
            moveOrderLegDone = false;
            pressStallWindows = 0;
            pressNoProgressWindows = 0;
            pressBestDistSq = Double.MAX_VALUE;
            pressHoldUntil = 0;
            pressHoldTicks = 0;
        }

        // "前往指定点"命令执行期间：先走到命令点本身 ——
        //   原版寻路走不通时由方块级 A* 绕路，到了命令点附近再按阵型展开。
        //   否则阵型排布会把站位放在锚点后方（线列阵第一排就在锚点后 5 格、后排兵种还更远），
        //   目标点在墙 / 障碍另一侧时站位会落在障碍这一侧 —— 表现为"被地形挡住、不会绕路"。
        //
        // 每名士兵的"去命令点"这一段是一次性的（moveOrderLegDone）：
        //   判断条件本身是"离命令点还有 6 格以上"，而宽阵型两翼的阵位就在 6 格以外，
        //   如果不记住"已经到过命令点"，两翼士兵会在两个目标之间无限折返。
        boolean headingToOrderPoint = false;
        if (movement == PlayerCommandState.MovementMode.STATION && !moveOrderLegDone) {
            if (ss.isMoveOrderPriorityActive(soldier.getX(), soldier.getZ(),
                    player.level().getGameTime())) {
                headingToOrderPoint = true;
            } else {
                // 到过命令点附近（或命令超时）→ 这一段结束，之后只奔自己的阵位
                moveOrderLegDone = true;
            }
        }
        Vec3 moveTarget = headingToOrderPoint ? anchor : slot;

        double dx = soldier.getX() - moveTarget.x;
        double dz = soldier.getZ() - moveTarget.z;
        double horizSqr = dx * dx + dz * dz;
        logDiag(null, moveTarget, horizSqr, ss.isSoftStation());

        if (horizSqr <= ARRIVE_HORIZ_SQR
                && Math.abs(soldier.getY() - moveTarget.y) <= ARRIVE_Y_TOLERANCE) {
            resetSettle();
            arrive(yaw);
            return;
        }

        // 正在按方块级 A* 路线绕路（迷宫 / 绕墙）→ 这就是"正在前进"：
        //   不做"磨蹭就停下"的判定，否则直线距离不缩短时会在半路停下 / 走走停停。
        if (soldier.isFollowingMarchRoute()) {
            // 同一条命令 → 继续按绕路走完（绕路的目标就是命令点，不能因为阵位偏移就丢掉它）
            if (!orderChanged) {
                resetSettle();
                return;
            }
            // 新命令：旧绕路作废，落到下面按新目标重新规划
            soldier.clearMarchRoute();
            soldier.getNavigation().stop();
            AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        }

        leaveSlot();

        double targetY = groundY(moveTarget.x, moveTarget.z, moveTarget.y);
        if (pressWatch(moveTarget, horizSqr)) return;
        // 方块级 A* 绕路的目标 = 这名士兵真正要去的地方（前往 = 命令点；就位后 = 自己的站位）。
        //   若一律用"命令锚点"，已经站在自己站位上的士兵会被拉回锚点来回跑。
        Vec3 routeTarget = new Vec3(moveTarget.x, targetY, moveTarget.z);
        // 最后 4 格：直线进场，避免快马绕着目标点打转进不了判定圈。
        Mob approachMover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        boolean watery = approachMover.isInWater();
        // 同上：按本体宽度判定（宽体坐骑擦墙角时交给寻路绕行，不做直线冲刺）。
        if ((horizSqr < 16.0 || (watery && horizSqr < 576.0))
                && AiNavUtil.hasClearLineForBody(soldier.level(),
                approachMover.position().add(0, 0.5, 0), moveTarget.add(0, 0.5, 0),
                approachMover.getBbWidth() * 0.5 + 0.05)) {
            soldier.getNavigation().stop();
            approachMover.getMoveControl().setWantedPosition(moveTarget.x, moveTarget.y, moveTarget.z, 1.0D);
            return;
        }
        // 正前方有门 → 接管移动：从没有门板的那半边直线穿过
        if (tryDoorPass()) return;
        // 正在走的原版路径够不到目标（迷宫 / 长墙只会给"半路路径"）→ 立刻改走方块级 A* 绕路。
        //   与控制工具指定点 / 首领手动行军共用同一套判定，不需要先顶着墙干等。
        if (trySmartRoute(routeTarget, false)) return;
        // 路径走到一半时目的地变了（新命令 / 换跟随对象）：不再等旧路径走完，
        //   直接按新目标重新规划（否则单位会先把旧路走到头才转向新目标）。
        // 原版只给"半路路径"（目标在墙顶 / 盒子里面等够不到的地方）时不能一直跟着走，
        //   否则就是顶着墙角不动的表现；要放行到下面的重规划与兜底判定。
        PathNavigation navNow = soldier.getNavigation();
        boolean partialPath = !navNow.isDone() && navNow.getPath() != null
                && !navNow.getPath().canReach();
        if (!navNow.isDone() && !orderChanged && !partialPath) return;
        // 没有路径时的重试要节流（目标走不到时不能每 tick 重算一次寻路）
        if (!orderChanged && !canReissueNav()) return;
        // 导航刚走完 / 刚换新命令：清掉移动控制器残留的旧目标点（否则快速坐骑会掉头走向旧点）。
        AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        Vec3 navGoal = navGoal(moveTarget.x, targetY, moveTarget.z);
        if (navGoal == null) return;
        soldier.getNavigation().moveTo(navGoal.x, navGoal.y, navGoal.z, 1.0D);
        // 原版刚给的路径同样够不到目标（连路都没有 / 半路路径 / 门被当成台阶）
        //   → 直接改走方块级 A* 绕路（迷宫入口就靠这一步）。
        if (trySmartRoute(routeTarget, true)) return;
        // 兜底：原版路径"看着能到"、但确实推进不了（连续 60 tick 没更接近目标）
        //   → 同样交给方块级 A* 绕路；A* 也规划不出路、且人已经在目标附近（6 格内）才允许就地停下。
        if (soldier.tickCount >= progressCheckTick) {
            progressCheckTick = soldier.tickCount + PROGRESS_CHECK_INTERVAL;
            double d = soldier.position().distanceToSqr(routeTarget);
            if (lastGoalDistSq > 0 && d > lastGoalDistSq - 1.0) {
                if (++noProgressChecks >= NO_PROGRESS_LIMIT) {
                    noProgressChecks = 0;
                    // 先让方块级 A* 去算真正的绕路（迷宫入口就靠这一步）。
                    //   只有 A* 也规划不出路、而且已经在目标附近（6 格内）时，
                    //   才允许"就地停下" —— 顺序反了会导致单位在迷宫墙外停下、
                    //   永远轮不到 A* 找入口。
                    boolean planned = soldier.position().distanceToSqr(routeTarget) > 16.0
                            && soldier.startMarchRouteAround(routeTarget);
                    if (!planned && horizSqr <= 9.0) {
                        resetSettle();
                        arrive(yaw);
                        return;
                    }
                }
            } else {
                noProgressChecks = 0;
            }
            lastGoalDistSq = d;
        }
    }

    private Vec3 computeSquadCentroid(Player player, int squadId) {
        double sx = soldier.getX(), sy = soldier.getY(), sz = soldier.getZ();
        double count = 1;
        double range = GeneralConfig.armyFollowRange;
        for (AbstractArmySoldier other : player.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                soldier.getBoundingBox().inflate(range),
                s -> s != soldier
                        && s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId)) {
            sx += other.getX();
            sy += other.getY();
            sz += other.getZ();
            count++;
        }
        return new Vec3(sx / count, sy / count, sz / count);
    }

    private boolean anySquadMateInCombat(Player player, int squadId) {
        for (AbstractArmySoldier other : player.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                player.getBoundingBox().inflate(GeneralConfig.armyFollowRange),
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId)) {
            var t = other.getTarget();
            if (t != null && t.isAlive()) return true;
        }
        return false;
    }

    /**
     * 软驻守期间：队友在战斗中时，士兵主动接近最近正在战斗的队友。
     *   走到队友 6 格内就停下，等其他 Goal（MeleeGoal / RangedGoal）
     *   在 AttackConfiguredEnemyGoal 分配目标后接管。
     *
     *   找不到正在战斗的队友 → 原地停下（下一个 tick 会重新检查）。
     */
    private void approachNearestFightingMate(Player player, int squadId) {
        AbstractArmySoldier nearestMate = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (AbstractArmySoldier other : player.level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                player.getBoundingBox().inflate(GeneralConfig.armyFollowRange),
                s -> s != soldier
                        && s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId)) {
            var t = other.getTarget();
            if (t == null || !t.isAlive()) continue;
            double d = soldier.distanceToSqr(other);
            if (d < nearestDistSq) {
                nearestDistSq = d;
                nearestMate = other;
            }
        }

        if (nearestMate == null) {
            // 没有正在战斗的队友 → 原地停下，下一个 tick 再判定
            stopMover();
            return;
        }

        Vec3 matePos = nearestMate.position();
        double dx = soldier.getX() - matePos.x;
        double dz = soldier.getZ() - matePos.z;
        double horizSqr = dx * dx + dz * dz;

        // 已在队友附近（6 格内）→ 停下。
        // 进入攻击范围后 AttackConfiguredEnemyGoal 会给士兵分配目标，
        // MeleeGoal / RangedGoal 会立刻接管 MOVE flag。
        if (horizSqr <= 6.0 * 6.0) {
            stopMover();
            return;
        }

        if (!soldier.getNavigation().isDone()) return;
        if (!canReissueNav()) return;
        // 导航刚走完：清掉移动控制器残留的旧目标点（否则快速坐骑会掉头走向旧点）。
        AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        double ty = groundY(matePos.x, matePos.z, matePos.y);
        Vec3 navGoal = navGoal(matePos.x, ty, matePos.z);
        if (navGoal == null) return;
        soldier.getNavigation().moveTo(navGoal.x, navGoal.y, navGoal.z, 1.0D);
    }

    // ===================== 自由站位（无固定槽位、不锁朝向） =====================
    private void tickFreeRoam(Player player, PlayerCommandState.SquadState ss) {
        if (soldier.getVehicle() instanceof WarHorseEntity horse) {
            horse.setLockedYaw(null);
        }

        boolean station = ss.getMovement()
                == PlayerCommandState.MovementMode.STATION;

        Vec3 center;
        if (station && ss.getFormationAnchor() != null) {
            center = ss.getFormationAnchor();
        } else {
            center = player.position();
        }

        double dx = soldier.getX() - center.x;
        double dz = soldier.getZ() - center.z;
        double distSqr = dx * dx + dz * dz;

        double comfortSqr = station ? 3.0 * 3.0 : FREE_COMFORT_SQR;

        if (distSqr <= comfortSqr) {
            if (!soldier.isAtFormationSlot()) soldier.setAtFormationSlot(true);
            stopMover();
            return;
        }

        if (soldier.isAtFormationSlot()) soldier.setAtFormationSlot(false);
        if (tryDoorPass()) return;

        Vec3 routeTarget = new Vec3(center.x, groundY(center.x, center.z, center.y), center.z);
        // 自由站位也要防"顶着障碍推"：目标够不到时原版只会给半路路径，
        //   这里与阵型移动一致 —— 够不到交给 A* 绕路，推不动太久就歇手。
        if (pressWatch(routeTarget, distSqr)) return;
        if (soldier.isFollowingMarchRoute()) {
            if (soldier.isFollowingMarchTowards(routeTarget)) return;
            soldier.clearMarchRoute();
        }
        if (trySmartRoute(routeTarget, true)) return;
        if (!soldier.getNavigation().isDone()) return;
        if (!canReissueNav()) return;
        // 导航刚走完：清掉移动控制器残留的旧目标点（否则快速坐骑会掉头走向旧点）。
        AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        Vec3 navGoal = navGoal(routeTarget.x, routeTarget.y, routeTarget.z);
        if (navGoal == null) return;
        soldier.getNavigation().moveTo(navGoal.x, navGoal.y, navGoal.z, 1.0D);
        // 原版刚给的路径仍然够不到目标 → 同样交给方块级 A* 绕路。
        if (trySmartRoute(routeTarget, false)) return;
    }

    // ===================== 默认跟随 =====================

    /**
     * 当前跟随对象：默认指挥官本人；
     *   指定了跟随单位（右键友方单位下达的跟随命令）则用那个单位，
     *   对方不存在 / 已死亡 → 回退跟随指挥官。
     */
    private static LivingEntity resolveFollowTarget(Player owner,
                                                    PlayerCommandState.SquadState ss) {
        java.util.UUID id = ss.getFollowTargetUuid();
        if (id == null || id.equals(owner.getUUID())) return owner;
        if (!(owner.level() instanceof ServerLevel sl)) return owner;
        Entity e = sl.getEntities().get(id);
        return (e instanceof LivingEntity le && le.isAlive()) ? le : owner;
    }

    private void defaultFollow(LivingEntity player) {
        double dx = soldier.getX() - player.getX();
        double dz = soldier.getZ() - player.getZ();
        double horizSqr = dx * dx + dz * dz;

        if (horizSqr <= ARRIVE_HORIZ_SQR) {
            resetSettle();
            arrive(player.getYRot());
            return;
        }

        if (soldier.isFollowingMarchRoute()) {
            resetSettle();
            return;
        }

        if (horizSqr < bestDistSqr - 0.25) {
            bestDistSqr = horizSqr;
            settleTimer = 0;
        } else {
            settleTimer++;
            if (settleTimer > SETTLE_TICKS && horizSqr <= 9.0) {
                resetSettle();
                arrive(player.getYRot());
                return;
            }
        }

        leaveSlot();

        double targetY = groundY(player.getX(), player.getZ(), player.getY());
        if (tryDoorPass()) return;
        if (!soldier.getNavigation().isDone()) return;
        if (!canReissueNav()) return;
        // 导航刚走完：清掉移动控制器残留的旧目标点（否则快速坐骑会掉头走向旧点）。
        AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        Vec3 navGoal = navGoal(player.getX(), targetY, player.getZ());
        if (navGoal == null) return;
        soldier.getNavigation().moveTo(navGoal.x, navGoal.y, navGoal.z, 1.0D);
    }

    // ===================== 辅助 =====================

    /**
     * 目标在下方、身边能找到可下的梯子时，把寻路目标换成"登梯口"（梯子旁能站的一格）；
     *   否则照常按原目标寻路。目标在下方时原版只会给一条"走到平台边缘就停"的半路路径，
     *   单位会一直站在边缘下不去。
     */
    private Vec3 navGoal(double x, double y, double z) {
        Vec3 target = new Vec3(x, y, z);
        // 正在下梯子：交给实体自身的下滑逻辑，上层不要发寻路（否则会往平台 / 砖块方向顶）。
        if (AiNavUtil.isDescendingOnLadder(soldier, target)) {
            soldier.getNavigation().stop();
            soldier.clearMarchRoute();
            return null;
        }
        Vec3 access = AiNavUtil.descentAccessPoint(soldier, target);
        return access != null ? access : target;
    }

    /**
     * 智能绕路（迷宫 / 长墙）：原版寻路只会给"最接近目标的半路路径"，
     *   单位于是顶着墙不动。这里每 10 tick 检查一次当前原版路径，够不到目标就改用
     *   方块级 A* 精确绕路 —— 与"控制工具指定点"（{@link SoldierManualMoveGoal}）
     *   和首领手动行军用的是同一套判定。
     *
     * @param routeTarget      绕路目标点
     * @param allowMissingPath true = "连原版路径都没有"也算够不到（刚尝试寻路失败时用）
     * @return true = 已开始按绕路前进（本 tick 交给绕路，上层不要再抢移动）
     */
    private boolean trySmartRoute(Vec3 routeTarget, boolean allowMissingPath) {
        if (soldier.tickCount < smartRouteCheckTick) return false;   // 节流：每 10 tick 最多查一次
        net.minecraft.world.level.pathfinder.Path path = soldier.getNavigation().getPath();
        if (path == null && !allowMissingPath) return false;
        // 已经贴着绕路目标：交给到达判定，不再重复规划
        if (soldier.position().distanceToSqr(routeTarget) <= 2.25) return false;
        boolean cannotReach = path == null
                || !path.canReach()
                || AiNavUtil.pathStepsOverDoor(soldier, path);
        if (!cannotReach) return false;
        smartRouteCheckTick = soldier.tickCount + SMART_ROUTE_CHECK_INTERVAL;
        return soldier.startMarchRouteAround(routeTarget);
    }

    /**
     * 穿门接管：身边（4 格内、基本在正前方）有能用手开的门时，停掉寻路，
     *   直接让"真正在走的实体"从没有门板的那半边直线穿过：
     *   · 避免原版把门当成一格台阶触发跳跃；
     *   · 避免 0.6 宽的身体蹭到开着的门板（3/16 格厚）导致减速。
     *
     * @return true = 本 tick 已接管移动
     */
    private boolean tryDoorPass() {
        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        BlockPos door = AiNavUtil.handDoorNear(mover);
        if (door == null) return false;
        Vec3 pass = AiNavUtil.doorPassPoint(mover, door);
        double dirX = 0.0;
        double dirZ = 0.0;
        Vec3 delta = mover.getDeltaMovement();
        double mvLen = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (mvLen > 0.02) {
            dirX = delta.x / mvLen;
            dirZ = delta.z / mvLen;
        } else {
            double toX = pass.x - mover.getX();
            double toZ = pass.z - mover.getZ();
            double toLen = Math.sqrt(toX * toX + toZ * toZ);
            if (toLen > 1.0E-3) {
                dirX = toX / toLen;
                dirZ = toZ / toLen;
            }
        }
        if (dirX == 0.0 && dirZ == 0.0) return false;
        mover.getNavigation().stop();
        soldier.clearMarchRoute();
        double aheadX = mover.getX() + dirX * 2.5;
        double aheadZ = mover.getZ() + dirZ * 2.5;
        mover.getMoveControl().setWantedPosition(
                aheadX + (pass.x - aheadX) * 0.3, mover.getY(),
                aheadZ + (pass.z - aheadZ) * 0.3, 1.0D);
        return true;
    }

    private void resetSettle() {
        bestDistSqr = Double.MAX_VALUE;
        settleTimer = 0;
    }

    /**
     * 调试诊断：每秒输出一行单位移动状态（general-common.toml 的 debugMovementLog 打开时）。
     *   用于排查"迷宫半路停下 / 上马后原地转圈"等问题：能直接看到导航目标、移动控制器目标、
     *   惯性、锁定的朝向、编队槽位距离等。
     */
    private void logDiag(@Nullable AbstractArmyCommander commander, @Nullable Vec3 slot,
                         double slotDistSqr, boolean soft) {
        if (!GeneralConfig.debugMovementLog) return;
        if (soldier.tickCount % 20 != 0) return;
        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        PathNavigation nav = mover.getNavigation();
        Vec3 dm = mover.getDeltaMovement();
        String pathTarget = nav.getPath() == null ? "null" : nav.getPath().getTarget().toString();
        String nextNode = nav.getPath() == null || nav.getPath().isDone() ? "-"
                : nav.getPath().getNextNodePos().toString();
        String locked = soldier.getVehicle() instanceof WarHorseEntity horse
                && horse.getLockedYaw() != null
                ? String.format(java.util.Locale.ROOT, "%.1f", horse.getLockedYaw()) : "-";
        com.ohhapple.monster_conquest.Monster_conquest.LOGGER.info(
                "[MOVE-DIAG] t={} side={} mover={} pos=({},{},{}) rot={} mounted={} soft={} route={} navDone={} path={} next={} wanted=({},{},{}) delta=({},{},{}) lockedYaw={} slot={} slotDist={}",
                soldier.tickCount,
                commander != null ? "NPC" : "PLAYER",
                mover.getType().toShortString(),
                fmt(mover.getX()), fmt(mover.getY()), fmt(mover.getZ()), fmt(mover.getYRot()),
                soldier.isPassenger(), soft, soldier.isFollowingMarchRoute(),
                nav.isDone(), pathTarget, nextNode,
                fmt(mover.getMoveControl().getWantedX()),
                fmt(mover.getMoveControl().getWantedY()),
                fmt(mover.getMoveControl().getWantedZ()),
                fmt(dm.x), fmt(dm.y), fmt(dm.z), locked,
                slot == null ? "null"
                        : "(" + fmt(slot.x) + "," + fmt(slot.y) + "," + fmt(slot.z) + ")",
                slotDistSqr < 0 ? "-" : fmt(Math.sqrt(slotDistSqr)));
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private void arrive(float yaw) {
        boolean firstArrive = !soldier.isAtFormationSlot();
        if (firstArrive) {
            soldier.setAtFormationSlot(true);
            lockYaw(soldier, yaw);
        }
        // 到位 / 到锚点后不要再跟绕路路线（否则会在终点附近被继续拉着走）
        soldier.clearMarchRoute();
        stopMover();
    }

    /**
     * 停下"真正在走的实体"：骑乘时移动的是坐骑（战马）——
     *   只停骑手自己的导航 / 惯性，马会带着人继续滑出去，
     *   表现为单位在目标点 / 集合点附近来回跑。
     */
    private void stopMover() {
        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        mover.getNavigation().stop();
        Vec3 dm = mover.getDeltaMovement();
        mover.setDeltaMovement(0, dm.y, 0);
        // 清掉移动控制器里残留的目标点：否则马会继续朝旧目标原地打转 / 重新起步。
        AiNavUtil.stopMovementInput(mover);
        if (mover != soldier) {
            Vec3 dmRider = soldier.getDeltaMovement();
            soldier.setDeltaMovement(0, dmRider.y, 0);
        }
    }

    private void leaveSlot() {
        if (soldier.isAtFormationSlot()) soldier.setAtFormationSlot(false);
        if (soldier.getVehicle() instanceof WarHorseEntity horse) {
            horse.setLockedYaw(null);
        }
    }

    /**
     * 推墙保护：按 40 tick 窗口统计"真正在走的实体"的位移。
     *   连续多窗口几乎没动、且当前目标没有完整原版路径（上不去 / 进不去）时：
     *     · 人已经在目标旁边（10 格内）→ 就地站住（不再推墙，等新命令 / 队伍移动再走）；
     *     · 目标还很远 → 歇几秒再重试（给绕路逻辑重新规划的机会）。
     * @return true = 本 tick 已接管（上层不要再驱动移动）
     */
    private boolean pressWatch(Vec3 target, double horizSqr) {
        if (pressHoldUntil > 0 && soldier.tickCount < pressHoldUntil) {
            stopMover();
            return true;
        }
        pressHoldUntil = 0;

        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        double x = mover.getX();
        double z = mover.getZ();
        if (!Double.isNaN(pressLastX)) {
            double dx = x - pressLastX;
            double dz = z - pressLastZ;
            pressTravel += Math.sqrt(dx * dx + dz * dz);
        }
        pressLastX = x;
        pressLastZ = z;
        if (soldier.tickCount < pressWatchTick) return false;
        pressWatchTick = soldier.tickCount + PRESS_WINDOW_TICKS;

        boolean stalled = pressTravel < PRESS_TRAVEL_EPSILON;
        pressTravel = 0.0;
        if (pressBestDistSq == Double.MAX_VALUE) pressBestDistSq = horizSqr;

        // 身边有梯子 / 正踩在梯子上：那是真在上楼，不要在这里判"推墙"（爬梯由攀爬逻辑负责）。
        if (AiNavUtil.isOnClimbable(mover) || nearClimbable(mover)) {
            pressStallWindows = 0;
            pressNoProgressWindows = 0;
            pressBestDistSq = horizSqr;
            return false;
        }

        net.minecraft.world.level.pathfinder.Path path = mover.getNavigation().getPath();
        boolean hasFullPath = path != null && path.canReach();
        if (hasFullPath || soldier.isMarchRoutePlanDeferred()) {
            // 目标有完整路径（只是挤在人群 / 门口）/ 绕路规划只是被节流推迟 → 继续走
            pressStallWindows = 0;
            pressNoProgressWindows = 0;
            pressBestDistSq = Math.min(pressBestDistSq, horizSqr);
            return false;
        }

        if (stalled && !soldier.isFollowingMarchRoute()) {
            pressStallWindows++;
        } else {
            pressStallWindows = 0;
        }
        if (horizSqr < pressBestDistSq - 1.0) {
            pressBestDistSq = horizSqr;
            pressNoProgressWindows = 0;
            pressHoldTicks = 0;
        } else {
            pressNoProgressWindows++;
        }

        boolean targetAbove = target.y >= mover.getY() + 2.0;
        int need = targetAbove ? PRESS_ABOVE_WINDOWS
                : (horizSqr <= 100.0 ? PRESS_NEAR_WINDOWS : PRESS_FAR_WINDOWS);
        // "一直不接近目标"在跟随绕路路线时同样要计入：贴着墙原地推的时候，
        //   绕路路线会被反复丢弃又重建，只统计"没有路线"的窗口会导致永远不触发。
        boolean giveUp = pressStallWindows >= need
                || pressNoProgressWindows >= PRESS_NO_PROGRESS_WINDOWS;
        if (!giveUp) return false;

        pressStallWindows = 0;
        pressNoProgressWindows = 0;
        pressBestDistSq = horizSqr;
        if (horizSqr <= 100.0) {
            // 目标就在旁边但上不去 / 进不去 → 就地站住
            resetSettle();
            arrive(soldier.getYRot());
        } else {
            // 还远：歇一会儿再试；连续触发时等待时间翻倍，避免"推一下、被拉回、再推"的循环
            pressHoldTicks = pressHoldTicks <= 0
                    ? PRESS_HOLD_TICKS
                    : Math.min(pressHoldTicks * 2, PRESS_HOLD_MAX_TICKS);
            pressHoldUntil = soldier.tickCount + pressHoldTicks;
            stopMover();
        }
        return true;
    }

    /** 身边一格内（同一层 / 上下 1 格）有没有梯子、脚手架、藤蔓 —— 有就交给攀爬逻辑，不当推墙处理。 */
    private boolean nearClimbable(Mob mover) {
        net.minecraft.core.BlockPos base = mover.blockPosition();
        for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
            net.minecraft.core.BlockPos side = base.relative(dir);
            for (int dy = -1; dy <= 1; dy++) {
                if (mover.level().getBlockState(side.above(dy)).is(net.minecraft.tags.BlockTags.CLIMBABLE)) {
                    return true;
                }
            }
        }
        return false;
    }

    private double groundY(double x, double z, double fallbackY) {
        int gx = (int) Math.floor(x);
        int gz = (int) Math.floor(z);
        int gy = soldier.level().getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, gx, gz);
        if (Math.abs(gy - fallbackY) > 6.0) return fallbackY;
        return gy;
    }

    private static void lockYaw(AbstractArmySoldier soldier, float yaw) {
        soldier.setYRot(yaw);
        soldier.yRotO = yaw;
        soldier.yBodyRot = yaw;
        soldier.yBodyRotO = yaw;
        soldier.setYHeadRot(yaw);
        soldier.yHeadRotO = yaw;
        if (soldier.getVehicle() instanceof WarHorseEntity horse) {
            horse.setLockedYaw(yaw);
        }
    }
}
