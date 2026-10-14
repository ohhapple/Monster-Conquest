package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class FollowCommanderGoal extends Goal {

    private static final int STUCK_CHECK_INTERVAL = 20;
    private static final double STUCK_MIN_MOVE_SQ = 0.5 * 0.5;
    private static final double STUCK_DIST_SQ = 1.0;
    private static final double Y_TOLERANCE = 2.5;
    /** 连续多少轮"没走动 + 没有可行路线"才允许传送（单轮可能只是被队友挤 / 正在转身）。 */
    private static final int STUCK_TELEPORT_WINDOWS = 2;

    /** 巡逻队长的士兵用更紧的跟随距离。 */
    private static final float PATROL_STOP_DISTANCE = 2.0f;
    private static final float PATROL_START_DISTANCE = 6.0f;

    private final AbstractArmySoldier soldier;
    private final double speedModifier;
    private final float defaultStopDistance;
    private final float defaultStartDistance;

    private int timeToRecalcPath;
    private int stuckTimer = 0;
    /** 连续"没走动 + 无路"的轮数；一旦走动 / 有路就清零。 */
    private int stuckWindows = 0;
    private double lastCheckX, lastCheckZ;
    private final MarchTrailFollow marchFollow = new MarchTrailFollow();

    public FollowCommanderGoal(AbstractArmySoldier soldier, double speed, float stop, float start) {
        this.soldier = soldier;
        this.speedModifier = speed;
        this.defaultStopDistance = stop;
        this.defaultStartDistance = start;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** 巡逻队长 → 更紧；其他首领 → 用构造参数。 */
    private float effectiveStopDistance() {
        if (soldier.getCommander() instanceof AbstractPatrolCaptain) {
            return PATROL_STOP_DISTANCE;
        }
        return defaultStopDistance;
    }

    private float effectiveStartDistance() {
        if (soldier.getCommander() instanceof AbstractPatrolCaptain) {
            return PATROL_START_DISTANCE;
        }
        return defaultStartDistance;
    }

    @Override
    public boolean canUse() {
        if (shouldSuppressFollow()) return false;
        if (soldier.isAtFormationSlot()) return false;

        LivingEntity leader = soldier.getLeaderEntity();
        if (leader == null || !leader.isAlive()) return false;
        AbstractArmyCommander commander = soldier.getCommander();
        // 首领带着手动行军命令赶路时，即使有仇恨目标也照常跟随
        if (commander != null && commander.getCurrentThreat() != null
                && commander.getManualMarchTarget() == null) return false;
        if (soldier.getTarget() != null && soldier.getTarget().isAlive()) return false;

        float start = effectiveStartDistance();
        double distSq = soldier.distanceToSqr(leader);
        return distSq >= (double) (start * start);
    }

    @Override
    public boolean canContinueToUse() {
        if (shouldSuppressFollow()) return false;
        if (soldier.isAtFormationSlot()) return false;

        LivingEntity leader = soldier.getLeaderEntity();
        if (leader == null || !leader.isAlive()) return false;
        if (soldier.getTarget() != null && soldier.getTarget().isAlive()) return false;

        float stop = effectiveStopDistance();
        return soldier.distanceToSqr(leader) > (double) (stop * stop);
    }

    private boolean shouldSuppressFollow() {
        // 自由移动：不跟随任何人，像原版生物一样自由游走
        if (soldier.isFreeRoaming()) return true;
        if (!soldier.isPlayerArmy()) return false;
        // 刚放出来、还没被玩家指挥过的士兵：保持自然游走，不跟随玩家
        if (!soldier.isPlayerCommanded()) return true;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;
        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(soldier, st);

        if (ss.getMovement()
                == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION) {
            return true;
        }
        if (soldier.isInCombatGrace()) return true;
        return st.getPreciseStrikeTarget(soldier.level().getGameTime()) != null;
    }

    @Override
    public void start() {
        this.timeToRecalcPath = 0;
        this.stuckTimer = 0;
        this.stuckWindows = 0;
        this.lastCheckX = soldier.getX();
        this.lastCheckZ = soldier.getZ();
        this.marchFollow.reset();
    }

    @Override
    public void stop() {
        // 骑乘时移动的是坐骑：停坐骑的导航和惯性（只停骑手会让马继续滑出去，
        //   表现为单位在目标点 / 队长附近来回跑）。
        Mob mover = this.soldier.getVehicle() instanceof Mob m ? m : this.soldier;
        mover.getNavigation().stop();
        Vec3 dm = mover.getDeltaMovement();
        mover.setDeltaMovement(0, dm.y, 0);
        AiNavUtil.stopMovementInput(mover);
        if (mover != this.soldier) {
            Vec3 dmRider = this.soldier.getDeltaMovement();
            this.soldier.setDeltaMovement(0, dmRider.y, 0);
        }
        this.marchFollow.reset();
    }

    @Override
    public void tick() {

        LivingEntity leader = soldier.getLeaderEntity();
        if (leader == null) return;

        double distSq = soldier.distanceToSqr(leader);

        AbstractArmyCommander npcCommander = soldier.getCommander();
        if (npcCommander != null
                && !soldier.getSoldierType().isCavalry()
                && !soldier.getSoldierType().isWitch()
                && !soldier.isPassenger()
                && distSq > 15 * 15
                && soldier.isOpenTerrainCached()) {
            soldier.switchToCavalry();
        }

        if (++stuckTimer >= STUCK_CHECK_INTERVAL) {
            double dx = soldier.getX() - lastCheckX;
            double dz = soldier.getZ() - lastCheckZ;
            double movedSq = dx * dx + dz * dz;

            boolean volleyActive = soldier.isVolleyActiveNow();

            if (!volleyActive
                    && !soldier.isAtFormationSlot()
                    && distSq > STUCK_DIST_SQ && movedSq < STUCK_MIN_MOVE_SQ) {
                // 骑马过不去的窄道 / 低矮通道：先下马步行通过，
                //   不要连人带马一起传送过去（之后地形开阔会自动重新上马）。
                if (soldier.tryDismountForMountTerrain(leader.position())) {
                    lastCheckX = soldier.getX();
                    lastCheckZ = soldier.getZ();
                    stuckTimer = 0;
                    return;
                }

                // 先考虑寻路回来：只要还有可行路线（哪怕要绕路 / 开门），就继续走，不传送；
        //   完全无路可走（被困住 / 被墙地形切断）才传送。
                if (soldier.hasReachablePathTo(leader.position())) {
                    stuckWindows = 0;
                    // 正在下梯子：停掉寻路，交给下滑逻辑（否则会被拉回平台）。
                    if (AiNavUtil.isDescendingOnLadder(soldier, leader.position())) {
                        soldier.getNavigation().stop();
                        soldier.clearMarchRoute();
                        return;
                    }
                    Vec3 navTarget = AiNavUtil.descentAccessPoint(soldier, leader.position());
                    if (navTarget == null) navTarget = leader.position();
                    soldier.getNavigation().moveTo(
                            navTarget.x, navTarget.y, navTarget.z, speedModifier);
                    this.timeToRecalcPath = 10;
                } else if (soldier.isFollowingMarchRoute()
                        || soldier.isMarchRoutePlanDeferred()) {
        // 正在按自研绕路走 / 规划只是被全局节流推迟：都算"还有路"，继续走不传送。
                    stuckWindows = 0;
                    this.timeToRecalcPath = 10;
                } else if (soldier.startMarchRouteAround(leader.position())) {
                    // 自研规划器给出了完整绕路：按路线走，不传送。
                    stuckWindows = 0;
                    this.timeToRecalcPath = 10;
                } else if (++stuckWindows < STUCK_TELEPORT_WINDOWS) {
                    // 单轮"没走动 + 无路"还不能下结论：再确认一轮，期间照常尝试寻路。
                    this.timeToRecalcPath = 10;
                } else {
                    stuckWindows = 0;
                    if (soldier.isPlayerArmy()) {
                        // 玩家士兵：静音传送到玩家 3 格外的安全处
                        soldier.teleportNearSafely(leader, 3.0);
                    } else {
                        // NPC 军队：随机偏移 2~5 格 + 音效
                        double angle = soldier.getRandom().nextDouble() * Math.PI * 2;
                        double offset = 2 + soldier.getRandom().nextDouble() * 3;
                        soldier.teleportWithMount(
                                leader.getX() + Math.cos(angle) * offset,
                                leader.getY(),
                                leader.getZ() + Math.sin(angle) * offset,
                                true);
                    }
                    this.timeToRecalcPath = 0;
                }

                lastCheckX = soldier.getX();
                lastCheckZ = soldier.getZ();
                stuckTimer = 0;
                return;
            }
            stuckWindows = 0;
            lastCheckX = soldier.getX();
            lastCheckZ = soldier.getZ();
            stuckTimer = 0;
        }

        if (--timeToRecalcPath <= 0) {
            timeToRecalcPath = 10;
            // 队长行军：沿队长走过的轨迹排队跟进（队长只算一次寻路），
            //   轨迹走完 / 队长停下后再恢复正常跟随。
            if (npcCommander != null
                    && marchFollow.tick(soldier, npcCommander, speedModifier)) {
                return;
            }
            Vec3 target = computeFollowTarget(leader);
            // 正在下梯子：停掉寻路，交给下滑逻辑。
            if (AiNavUtil.isDescendingOnLadder(soldier, target)) {
                soldier.getNavigation().stop();
                soldier.clearMarchRoute();
                return;
            }
            Vec3 access = AiNavUtil.descentAccessPoint(soldier, target);
            if (access != null) target = access;
            soldier.getNavigation().moveTo(target.x, target.y, target.z, speedModifier);
        }
    }

    private Vec3 computeFollowTarget(LivingEntity leader) {
        Level level = soldier.level();
        double targetY;

        boolean flying = false;
        if (leader instanceof Player p) {
            flying = p.getAbilities().flying || p.isFallFlying();
        }

        if (flying) {
            BlockPos pos = BlockPos.containing(leader.getX(), leader.getY(), leader.getZ());
            targetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    pos.getX(), pos.getZ());
        } else {
            double dy = leader.getY() - soldier.getY();
            if (Math.abs(dy) < Y_TOLERANCE) {
                targetY = soldier.getY();
            } else {
                targetY = leader.getY();
            }
        }
        return new Vec3(leader.getX(), targetY, leader.getZ());
    }
}
