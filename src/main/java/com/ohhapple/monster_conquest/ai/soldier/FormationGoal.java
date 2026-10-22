package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.player.PlayerFormationCalculator;
import com.ohhapple.monster_conquest.player.SquadObservationCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

public class FormationGoal extends Goal {

    /**
     * 到位半径（格²）：一格内算"站在点上"。
     *   容差放到两格时，迷宫 / 绕墙的单位会在离命令点两格的地方（常常还在墙角另一侧）
     *   被判成已到位停下 —— 表现为"永远进不去 / 停在半路"。
     */
    private static final double ARRIVE_HORIZ_SQR = 1.0;
    /** 驻守滞回：已到位后偏离超过这个距离并持续一小段时间才重新起步。 */
    private static final double LEAVE_HORIZ_SQR = 3.5 * 3.5;
    /**
     * 玩家指令（前往 / 驻守 / 跟随）的驻守滞回半径：
     *   只防"在到位边界上被挤一下就走回去"的抖动；比这个远就说明没站到点上，
     *   要放行重新走过去（按 3.5 格滞回会把半路停下的单位锁死在离目标很远的地方）。
     */
    private static final double PLAYER_STATION_HOLD_SQR = 1.5 * 1.5;
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
    /** 目的地附近友军占位检查的节流。 */
    private int slotOccupancyCheckTick = 0;

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
    private static final int PRESS_HOLD_MAX_TICKS = 600;    // 连续顶住时最多歇 30 秒
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
        return soldier.isMoveOrderPriorityActive()
                || target == null || !target.isAlive();
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
        slotOccupancyCheckTick = 0;
        navReissueTick = 0;
        progressCheckTick = 0;
        lastGoalDistSq = -1.0;
        noProgressChecks = 0;
        // 刚接手移动：下一次判定当作"新命令"，确保先把路径对齐到当前命令目标
        lastMoveOrderStamp = Integer.MIN_VALUE;
        moveOrderLegDone = soldier.hasMoveOrderPointReached();
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
        stopMover();
        // 阵型 Goal 停下后不能把旧的指定点路线继续留给战斗 Goal / 路线跟随器。
        if (soldier.isFollowingMarchRoute() || soldier.isMarchRoutePlanning()) {
            soldier.releaseMarchRoute();
        }
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

        // 正在按方块级 A* 路线绕路 → 到达 / 停顿 / 阵位滞回都交给路线控制器判断。
        //   前置判定会在骑马段终点或窄道前误判“已经到位”，随后清掉下一段步行路线。
        if (soldier.isFollowingMarchRoute()) {
            resetSettle();
            return;
        }

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
        } else if (arrivedAt(slot, horizSqr)) {
            resetSettle();
            // 锁定"最近一次行进方向"（阵型朝向），而不是指挥官当下的朝向：
            //   指挥官站定后会自然环顾，阵型朝向不受影响。
            arrive(commander.getFormationFacing());
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
        // 上下梯子这一段例外：直线驱动会把单位从梯子列里拖出去（从平台边掉下去）。
        if (!AiNavUtil.isOnClimbRoute(approachMover)
                && (horizSqr < 16.0 || (watery && horizSqr < 576.0))
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
        // 旧路径可能已经走完但还留在导航里：节点相同的路径会被原版 Path.sameAs 判成
        //   "同一条路"而拒绝，单位会原地站着不动。重发前先清掉旧路径。
        nav.stop();
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
                    //   目标在另一层（命令点在平台上、人还在下面）时水平接近不算到位：
                    //   必须让 A* 再找一次路（会经过梯子），否则单位会停在平台下面不动。
                    boolean sameLevel = atTargetLevel(slot.y);
                    boolean planned = (!sameLevel || soldier.position().distanceToSqr(fallback) > 16.0)
                            && soldier.startMarchRouteAround(fallback);
                    if (soldier.isMarchRoutePlanning()) return;
                    if (!planned && sameLevel && horizSqr <= 9.0) {
                        if (acceptMountedNearSlot(slot, horizSqr, commander.getFormationFacing())) {
                            return;
                        }
                        // 走不到就停住等（不标"已到位"）：路打通后会自己再走过去。
                        resetSettle();
                        giveUpAtUnreachable();
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

        // 新命令识别：SquadState 的"移动指令代号"变了 = 玩家刚给这个小队 / 单兵下了新命令。
        //   旧路径 / 旧绕路必须当场作废，否则单位会先沿旧路线走到一半才听新命令
        //   （表现为"明明下了新命令，它还朝旧地点走一小会儿"）。
        boolean orderChanged = ss.getMoveOrderStamp() != lastMoveOrderStamp;
        if (orderChanged) {
            lastMoveOrderStamp = ss.getMoveOrderStamp();
            // 新命令会在 sync 阶段清掉这个标记；同一条命令期间 Goal 重启时
            //   保持"已到命令点"，不要又折返回锚点。
            moveOrderLegDone = soldier.hasMoveOrderPointReached();
            // 新指令不能继承上一条路线失败留下的规划退避；否则个别单位会
            //   在队友已经开始走时仍停着，直到脱困传送清掉状态才重新规划。
            soldier.clearMarchRoute();
            navReissueTick = 0;
            smartRouteCheckTick = 0;
            slotOccupancyCheckTick = 0;
            progressCheckTick = 0;
            lastGoalDistSq = -1.0;
            noProgressChecks = 0;
            lastSlotTarget = null;
            pressStallWindows = 0;
            pressNoProgressWindows = 0;
            pressBestDistSq = Double.MAX_VALUE;
            pressHoldUntil = 0;
            pressHoldTicks = 0;
            pressWatchTick = 0;
            pressTravel = 0.0;
            pressLastX = Double.NaN;
            pressLastZ = Double.NaN;
            soldier.tryMountForOpenTravel();
        }
        // "前往指定点"命令执行期间：先走到命令点本身 ——
        //   原版寻路走不通时由方块级 A* 绕路，到了命令点附近再按阵型展开。
        //   否则阵型排布会把站位放在锚点后方（线列阵第一排就在锚点后 5 格、后排兵种还更远），
        //   目标点在墙 / 障碍另一侧时站位会落在障碍这一侧 —— 表现为"被地形挡住、不会绕路"。
        //
        // 每名士兵的"去命令点"这一段是一次性的（moveOrderLegDone）：
        //   到达后只切一次阵位，避免两翼士兵在命令点和自己的阵位之间反复折返。
        boolean headingToOrderPoint = false;
        if (movement == PlayerCommandState.MovementMode.STATION
                && ss.hasMoveOrder() && !moveOrderLegDone) {
            double orderDx = soldier.getX() - anchor.x;
            double orderDz = soldier.getZ() - anchor.z;
            // 直线距离小于 6 格不等于到达：一格厚的迷宫墙另一侧、上层平台边缘
            // 都可能满足这个距离。只有真的走到命令点这一层、最后几格没有遮挡，
            // 才结束“去命令点”阶段；否则单位会提前切阵位，围着外墙站住。
            if (arrivedAt(anchor, orderDx * orderDx + orderDz * orderDz)) {
                moveOrderLegDone = true;
                soldier.markMoveOrderPointReached();
            } else {
                headingToOrderPoint = true;
            }
        }
        Vec3 moveTarget;
        Vec3 formationSlot = null;
        if (headingToOrderPoint) {
            // 执行"前往"命令时只认命令点。阵位要等真的进入目标区域后再排，
            // 否则迷宫 / 平台边缘会在墙外提前生成一批不可达阵位。
            moveTarget = anchor;
        } else {
            // 指定点命令（前往 / 驻守）→ 阵型以命令点为中心展开；跟随 → 阵型排在主人身后
            boolean stationLike = movement == PlayerCommandState.MovementMode.STATION;
            Vec3 slot = PlayerFormationCalculator.getSlot(
                    player, soldier, ss, mode, anchor, yaw, stationLike);
            if (slot == null) {
                if (movement == PlayerCommandState.MovementMode.STATION) {
                    stopMover();
                    return;
                }
                defaultFollow(resolveFollowTarget(player, ss));
                return;
            }
            formationSlot = slot;
            moveTarget = slot;
        }
        if (formationSlot != null
                && soldier.tickCount >= slotOccupancyCheckTick
                && soldier.position().distanceToSqr(formationSlot) <= 64.0) {
            slotOccupancyCheckTick = soldier.tickCount + 20;
            Vec3 adjusted = PlayerFormationCalculator.resolveOccupiedSlot(
                    player, soldier, ss, formationSlot, anchor);
            if (!adjusted.equals(formationSlot)) {
                moveTarget = adjusted;
                resetSettle();
                if (GeneralConfig.debugMovementLog) {
                    Monster_conquest.LOGGER.info(
                            "[SLOT-YIELD] t={} id={} old={} new={}",
                            soldier.tickCount,
                            soldier.getUUID().toString().substring(0, 8),
                            formationSlot, adjusted);
                }
            }
        }

        double dx = soldier.getX() - moveTarget.x;
        double dz = soldier.getZ() - moveTarget.z;
        double horizSqr = dx * dx + dz * dz;
        logDiag(null, moveTarget, horizSqr, ss.isSoftStation());

        // 路线执行期间由路线控制器负责到达判定，避免骑乘前缀终点被阵位判定提前接管。
        if (soldier.isFollowingMarchRoute()) {
            if (!orderChanged) {
                resetSettle();
                return;
            }
            soldier.clearMarchRoute();
            soldier.getNavigation().stop();
            AiNavUtil.stopMovementInput(soldier.getVehicle() instanceof Mob m ? m : soldier);
        }

        // 已在阵位：小范围偏移（被队友挤开 / 命令点微动）不重新起步，与 NPC 驻守同一套滞回。
        //   没有这一层时，单位在到位边界上一被挤出去就立刻走回来 —— 表现为原地小幅度蹭。
        if (soldier.isAtFormationSlot() && !orderChanged) {
            if (horizSqr <= PLAYER_STATION_HOLD_SQR && atTargetLevel(moveTarget.y)) {
                leaveTicks = 0;
                resetSettle();
                stopMover();
                return;
            }
            if (++leaveTicks <= LEAVE_DELAY_TICKS) {
                resetSettle();
                stopMover();
                return;
            }
            leaveTicks = 0;
            leaveSlot();
        }

        if (arrivedAt(moveTarget, horizSqr)) {
            resetSettle();
            if (headingToOrderPoint) {
                // 到达命令点只是"前往"这一段完成，还不是个人阵位。
                //   若在这里标成已到阵位，下一步会先停 10 tick 再展开队形，
                //   期间还会恢复索敌（表现为到了目的地却迟一拍才动）。
                moveOrderLegDone = true;
                soldier.markMoveOrderPointReached();
                leaveSlot();
                stopMover();
            } else {
                arrive(yaw);
            }
            return;
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
        if (!AiNavUtil.isOnClimbRoute(approachMover)
                && (horizSqr < 16.0 || (watery && horizSqr < 576.0))
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
        // 同上：清掉可能残留的旧（已走完）路径，避免新路径因节点相同被拒。
        soldier.getNavigation().stop();
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
                    //   目标在另一层（命令点在平台上、人还在下面）时水平接近不算到位：
                    //   必须让 A* 再找一次路（会经过梯子），否则单位会停在平台下面不动。
                    boolean sameLevel = atTargetLevel(moveTarget.y);
                    boolean needsRoute = !sameLevel
                            || soldier.position().distanceToSqr(routeTarget) > 16.0;
                    boolean planned = needsRoute && soldier.startMarchRouteAround(routeTarget);
                    if (soldier.isMarchRoutePlanning()) return;
                    if (!planned && sameLevel && horizSqr <= 9.0) {
                        if (acceptMountedNearSlot(moveTarget, horizSqr, yaw)) {
                            return;
                        }
                        // 走不到就停住等（不标"已到位"）：路打通后会自己再走过去。
                        resetSettle();
                        giveUpAtUnreachable();
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
        return SquadObservationCache.centroid(player, soldier, squadId);
    }

    private boolean anySquadMateInCombat(Player player, int squadId) {
        return SquadObservationCache.anyInCombat(player, squadId);
    }

    /**
     * 软驻守期间：队友在战斗中时，士兵主动接近最近正在战斗的队友。
     *   走到队友 6 格内就停下，等其他 Goal（MeleeGoal / RangedGoal）
     *   在 AttackConfiguredEnemyGoal 分配目标后接管。
     *
     *   找不到正在战斗的队友 → 原地停下（下一个 tick 会重新检查）。
     */
    private void approachNearestFightingMate(Player player, int squadId) {
        AbstractArmySoldier nearestMate = SquadObservationCache.nearestFightingMate(
                player, soldier, squadId);

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
        soldier.getNavigation().stop();
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
        soldier.getNavigation().stop();
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
        UUID id = ss.getFollowTargetUuid();
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
        soldier.getNavigation().stop();
        soldier.getNavigation().moveTo(navGoal.x, navGoal.y, navGoal.z, 1.0D);
    }

    // ===================== 辅助 =====================

    /** 寻路目标：梯子的上下由带梯子的寻路 + 梯子感知移动控制器负责，这里原样返回。 */
    private Vec3 navGoal(double x, double y, double z) {
        return new Vec3(x, y, z);
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
        Path path = soldier.getNavigation().getPath();
        if (path == null && !allowMissingPath) return false;
        // 已经贴着绕路目标：交给到达判定，不再重复规划
        if (soldier.position().distanceToSqr(routeTarget) <= 2.25) return false;
        boolean cannotReach = path == null
                || !path.canReach()
                || AiNavUtil.pathStepsOverDoor(soldier, path);
        if (!cannotReach) return false;
        smartRouteCheckTick = soldier.tickCount + SMART_ROUTE_CHECK_INTERVAL;
        boolean planned = soldier.startMarchRouteAround(routeTarget);
        if (!planned && soldier.isMarchRoutePlanning()) {
            // 规划在后台继续，当前原版路径照常前进；路线完成时会接到单位当前位置。
            return false;
        }
        return planned;
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
                ? String.format(Locale.ROOT, "%.1f", horse.getLockedYaw()) : "-";
        boolean mountFit = AiNavUtil.mountFitsAt(soldier.level(), soldier,
                mover.getX(), mover.getY(), mover.getZ());
        Monster_conquest.LOGGER.info(
                "[MOVE-DIAG] t={} unit={} id={} side={} mover={} pos=({},{},{}) rot={} mounted={} open={} mountFit={} dismountCd={} soft={} route={} planning={} navDone={} path={} next={} wanted=({},{},{}) delta=({},{},{}) lockedYaw={} slot={} slotDist={}",
                soldier.tickCount,
                soldier.getType().toShortString(),
                soldier.getUUID().toString().substring(0, 8),
                commander != null ? "NPC" : "PLAYER",
                mover.getType().toShortString(),
                fmt(mover.getX()), fmt(mover.getY()), fmt(mover.getZ()), fmt(mover.getYRot()),
                soldier.isPassenger(), soldier.isOpenTerrainCached(), mountFit,
                soldier.getTerrainDismountCooldown(), soft, soldier.isFollowingMarchRoute(),
                soldier.isMarchRoutePlanning(),
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
        return String.format(Locale.ROOT, "%.2f", value);
    }

    /**
     * 是否已经站在目标那一层：脚下有实地 + 高度差在容差内。
     *   挂在梯子半路时不算到位 —— 脚没落地，否则单位会被判成"已到位"停在梯子中间下不来。
     *   骑乘时看的是坐骑（真正在走的实体）：骑手本人永远不 onGround，
     *   按骑手判定会让骑兵永远"到不了位"，阵位目标一直重发 —— 马原地转圈。
     */
    private boolean atTargetLevel(double targetY) {
        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        return mover.onGround()
                && Math.abs(mover.getY() - targetY) <= ARRIVE_Y_TOLERANCE;
    }

    /**
     * 骑乘单位长时间停在阵位边缘时，接受一个安全且看得见目标的落脚点。
     *   马体宽、转向慢，原版导航可能无法生成最后几格路径；继续重试只会让整队
     *   在同一个位置反复停走。只在目标 3 格内、同层、马能站下且直线无墙时生效。
     */
    private boolean acceptMountedNearSlot(Vec3 slot, double horizSqr, float yaw) {
        if (!(soldier.getVehicle() instanceof WarHorseEntity)) return false;
        if (horizSqr > 9.0 || !atTargetLevel(slot.y)) return false;
        WarHorseEntity horse = (WarHorseEntity) soldier.getVehicle();
        if (!AiNavUtil.mountFitsAt(soldier.level(), soldier,
                horse.getX(), horse.getY(), horse.getZ())) return false;
        if (!AiNavUtil.hasClearLineForBody(soldier.level(),
                horse.position().add(0.0, 0.5, 0.0),
                slot.add(0.0, 0.5, 0.0),
                horse.getBbWidth() * 0.5 + 0.05)) return false;
        arrive(yaw);
        return true;
    }

    /**
     * 是否算"走到位"：水平一格内、和目标同层，且不是"半路假到位"。
     *   · 还踩着一条通向目标的未走完路径 → 先把最后一段走完（避免在拐角处被拦住）；
     *   · 目标与当前位置之间隔着方块（栅栏 / 墙角）→ 不算到位，交给寻路绕过去。
     *   距离已经贴脸（0.6 格内）时直接算到位，避免在最后一点点上反复起步。
     */
    private boolean arrivedAt(Vec3 target, double horizSqr) {
        if (horizSqr > ARRIVE_HORIZ_SQR || !atTargetLevel(target.y)) return false;
        if (horizSqr <= 0.36) return true;
        Mob mover = soldier.getVehicle() instanceof Mob m ? m : soldier;
        Path path = mover.getNavigation().getPath();
        if (path != null && !path.isDone() && path.canReach()
                && path.getTarget().distToCenterSqr(target.x, target.y, target.z) < 4.0) {
            return false;
        }
        return AiNavUtil.hasClearLineForBody(soldier.level(),
                mover.position().add(0.0, 0.5, 0.0), target.add(0.0, 0.5, 0.0),
                mover.getBbWidth() * 0.5 + 0.05);
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
            AiNavUtil.stopMovementInput(soldier);
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

        Path path = mover.getNavigation().getPath();
        boolean hasFullPath = path != null && path.canReach();
        if (hasFullPath || soldier.isMarchRoutePlanDeferred()) {
            // 目标有完整路径（只是挤在人群 / 门口）/ 绕路规划只是被节流推迟 → 继续走
            pressStallWindows = 0;
            pressNoProgressWindows = 0;
            pressBestDistSq = Math.min(pressBestDistSq, horizSqr);
            return false;
        }
        // 玩家单位去远处的迷宫 / 单格宽通道：原版常常只能给半路路径，而方块级 A*
        //   的规划还可能被全局预算排队。这里一旦按“推不动”进入长按停，单位会错过
        //   后续规划，最后只能靠脱困传送；这种情况下保持继续尝试，交给 A* / 重发路径。
        if (soldier.isPlayerArmy()
                && (horizSqr > 9.0
                    || AiNavUtil.isNarrowPassage(soldier.level(), mover.blockPosition()))) {
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
        } else if (soldier.isFollowingMarchRoute() && !stalled) {
            // 正在按绕路路线移动：绕墙 / 进迷宫时直线距离会先变远，这不是"没进展"。
            //   照常累计的话，长绕路走到一半就会被这里的"放弃"判定叫停（表现为蹭一段停一下）。
            pressNoProgressWindows = 0;
        } else {
            pressNoProgressWindows++;
        }

        boolean targetAbove = target.y >= mover.getY() + 2.0;
        int need = targetAbove ? PRESS_ABOVE_WINDOWS
                : (horizSqr <= 100.0 ? PRESS_NEAR_WINDOWS : PRESS_FAR_WINDOWS);
        // "一直不接近目标"在跟随绕路路线时也要计入（原地推墙的那几秒）：
        //   绕路路线会被反复丢弃又重建，只统计"没有路线"的窗口会导致永远不触发。
        boolean giveUp = pressStallWindows >= need
                || pressNoProgressWindows >= PRESS_NO_PROGRESS_WINDOWS;
        if (!giveUp) return false;

        pressStallWindows = 0;
        pressNoProgressWindows = 0;
        pressBestDistSq = horizSqr;
        giveUpAtUnreachable();
        if (GeneralConfig.debugMovementLog) {
            Monster_conquest.LOGGER.info(
                    "[PRESS-HOLD] t={} pos=({},{},{}) target=({},{},{}) hold={}"
                            + " following={} pathCanReach={} narrow={}",
                    soldier.tickCount,
                    fmt(mover.getX()), fmt(mover.getY()), fmt(mover.getZ()),
                    fmt(target.x), fmt(target.y), fmt(target.z), pressHoldTicks,
                    soldier.isFollowingMarchRoute(),
                    hasFullPath,
                    AiNavUtil.isNarrowPassage(soldier.level(), mover.blockPosition()));
        }
        return true;
    }

    /**
     * 目标确实走不到（隔着墙 / 封闭空间 / 门没开）：就地停住，并逐步拉长重试间隔。
     *   这里不能标"已到位"、也不能锁朝向 —— 那会让单位看起来"隔着墙就算到了"，
     *   而且路后来打通（开门、拆墙、绕路可行）时它也不会再尝试走过去。
     */
    private void giveUpAtUnreachable() {
        pressHoldTicks = pressHoldTicks <= 0
                ? PRESS_HOLD_TICKS
                : Math.min(pressHoldTicks * 2, PRESS_HOLD_MAX_TICKS);
        pressHoldUntil = soldier.tickCount + pressHoldTicks;
        stopMover();
    }

    /** 身边一格内（同一层 / 上下 1 格）有没有梯子、脚手架、藤蔓 —— 有就交给攀爬逻辑，不当推墙处理。 */
    private boolean nearClimbable(Mob mover) {
        BlockPos base = mover.blockPosition();
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = base.relative(dir);
            for (int dy = -1; dy <= 1; dy++) {
                if (mover.level().getBlockState(side.above(dy)).is(BlockTags.CLIMBABLE)) {
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
        // 命令点在平台 / 屋顶上，而底下那格地面被上方的方块盖住（在平台底下、被墙围住）：
        //   吸到地面等于把单位指到进不去的地方，只会让它绕着墙怼 —— 保持命令高度。
        if (fallbackY > gy + 1.0 && isCoveredAbove(gx, gy, gz)) return fallbackY;
        return gy;
    }

    /** 这个落脚点头顶不远（2~5 格内）有没有实心方块盖着（在平台 / 屋顶下面）。 */
    private boolean isCoveredAbove(int x, int y, int z) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy <= 5; dy++) {
            if (soldier.level().getBlockState(pos.set(x, y + dy, z)).isSolid()) return true;
        }
        return false;
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
