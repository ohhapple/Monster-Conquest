package com.ohhapple.monster_conquest.ai.soldier;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;

import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.UUID;

/**
 * 士兵远程/双形态攻击 Goal。
 *
 * 齐射不依赖 getTarget()，所以齐射结束时 getTarget() 可能为 null。
 *   canUse() 在目标为空时主动从玩家军队共享仇恨列表里找最近的活敌人
 *   并立即设为目标，齐射结束后无缝继续射击，不用等索敌 Goal 的扫描周期。
 */
public class SoldierRangedAttackGoal extends Goal {

    private static final float DUAL_LOW_HP_THRESHOLD = 0.5f;
    private static final int REPOSITION_TICKS = 10;
    private static final int SPACING_COMMIT_TICKS = 24;
    private static final int SIDESTEP_FLIP_MIN_TICKS = 24;
    private static final int SIDESTEP_FLIP_RANDOM_TICKS = 12;
    private static final int BOW_DRAW_TICKS = 8;

    /** 回退目标扫描半径（格）。 */
    private static final double FALLBACK_SCAN_RANGE = 16.0;
    /** 回退目标扫描节流（tick）。 */
    private static final int FALLBACK_SCAN_COOLDOWN = 10;

    private final AbstractArmySoldier soldier;
    private int cooldown = 0;
    private int repositionTicks = 0;
    /** 走位寻路节流：目标没变时不必每 tick 重算路径（打不到的目标也一样）。 */
    /** -1 = 还没用过：第一次调用先随机错开，避免整队同一 tick 一起跑寻路。 */
    private int moveCooldown = -1;

    @Nullable private UUID spacingTargetId;
    @Nullable private Vec3 spacingDestination;
    private int spacingMoveTick = -1000;
    private int spacingCommitUntil = 0;
    private int lastSpacingLogTick = -1000;
    private boolean directSpacing;
    private boolean withdrawing;
    private int sidestepDirection = 1;
    private int sidestepFlipTick = 0;
    private int failedWithdrawPlans;
    private int bowDrawTicks;
    @Nullable private UUID bowDrawTargetId;

    /** 走位寻路的节流：有路径在走时 10 tick 重发一次，没路径时 5 tick 重试一次。 */
    private boolean shouldReissueMove(Mob mover) {
        if (moveCooldown < 0) {
            moveCooldown = soldier.getRandom().nextInt(10);
        }
        if (moveCooldown > 0) {
            moveCooldown--;
            return false;
        }
        moveCooldown = mover.getNavigation().isDone() ? 5 : 10;
        return true;
    }

    /** 回退目标扫描冷却。 */
    private int fallbackScanCooldown = 0;

    /** 连续开火失败（被墙 / 掩体挡住）次数：累计到阈值 → 重新规划绕路。 */
    private int failedShots = 0;
    private static final int ROUTE_AFTER_FAILED_SHOTS = 3;
    /** 重新规划路线后保持寻路的时间（tick），期间不被"站位停止"覆盖。 */
    private static final int ROUTE_TICKS = 40;

    public SoldierRangedAttackGoal(AbstractArmySoldier soldier) {
        this.soldier = soldier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        // 紧急后撤每 tick 直接位移一次；隔 tick 执行会把实际逃跑速度砍半并产生顿挫。
        return true;
    }

    @Override
    public boolean canUse() {
        if (soldier.isMoveOrderPriorityActive()) return false;
        if (isVolleyMode()) return true;

        SoldierRole r = soldier.getRole();
        if (r.isMage()) return false;
        if (r.isThrower()) return false;
        if (r.isMelee()) return false;

        LivingEntity target = soldier.getTarget();

        // 齐射刚结束 / 目标刚死 → 从共享仇恨列表里主动挑目标。
        if (target == null || !target.isAlive()) {
            LivingEntity fallback = findFallbackTarget();
            if (fallback == null) return false;
            soldier.setTarget(fallback);
            target = fallback;
        }

        if (r.isDual()) {
            if (isDualLowHealth()) return true;
            return soldier.distanceTo(target) > 5.0;
        }
        return r.isArcher() || r.isGunner();
    }

    @Override
    public boolean canContinueToUse() {
        if (soldier.isMoveOrderPriorityActive()) return false;
        if (isVolleyMode()) return true;

        SoldierRole r = soldier.getRole();
        if (r.isMage()) return false;
        if (r.isThrower()) return false;
        if (r.isMelee()) return false;
        LivingEntity target = soldier.getTarget();
        if (target == null || !target.isAlive()) return false;

        if (r.isDual()) {
            if (isDualLowHealth()) return true;
            return soldier.distanceTo(target) > 4.0;
        }
        return r.isArcher() || r.isGunner();
    }

    @Override
    public void start() {
        cancelBowDraw();
        this.cooldown = 0;
        this.repositionTicks = 0;
        this.failedShots = 0;
        this.moveCooldown = -1;
        this.spacingTargetId = null;
        this.spacingDestination = null;
        this.spacingMoveTick = -1000;
        this.spacingCommitUntil = 0;
        this.lastSpacingLogTick = -1000;
        this.directSpacing = false;
        this.withdrawing = false;
        this.failedWithdrawPlans = 0;
        this.bowDrawTicks = 0;
        this.bowDrawTargetId = null;
        this.sidestepDirection = soldier.getRandom().nextBoolean() ? 1 : -1;
        this.sidestepFlipTick = soldier.tickCount
                + SIDESTEP_FLIP_MIN_TICKS
                + Math.floorMod(soldier.getId(), SIDESTEP_FLIP_RANDOM_TICKS + 1);
    }

    private boolean isVolleyMode() {
        if (soldier.isMoveOrderPriorityActive()) return false;
        if (!soldier.isVolleyActiveNow()) return false;
        SoldierRole r = soldier.getRole();
        return r.isArcher() || r.isDual() || r.isGunner();
    }

    @Override
    public void tick() {
        // 移动命令优先：Goal 可能是在命令生效前启动的，本 tick 先停手，
        //   避免齐射 / 走位继续占用 MOVE 旗标。
        if (soldier.isMoveOrderPriorityActive()) return;

        if (soldier.isArenaUnit()) {
            int interval = soldier.getArenaRangedAttackCooldownTicks();
            if (cooldown > interval) cooldown = interval;
        }
        if (cooldown > 0) cooldown--;
        if (repositionTicks > 0) repositionTicks--;

        // ==================== 齐射 ====================
        if (isVolleyMode()) {
            Player owner = soldier.getCommanderPlayer();
            var cmdState = owner != null
                    ? PlayerArmyManager.getIfPresent(owner.getUUID()) : null;
            Vec3 vt = cmdState != null
                    ? PlayerCommandState
                            .stateForCommand(soldier, cmdState).getVolleyTarget()
                    : null;
            if (vt != null) {
                Mob mover = soldier.getVehicle() instanceof Mob h ? h : soldier;
                mover.getNavigation().stop();

                double dx = vt.x - soldier.getX();
                double dy = vt.y - soldier.getEyeY();
                double dz = vt.z - soldier.getZ();
                double horiz = Math.sqrt(dx * dx + dz * dz);
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

                soldier.setYRot(yaw);
                soldier.yRotO = yaw;
                soldier.yBodyRot = yaw;
                soldier.yBodyRotO = yaw;
                soldier.setYHeadRot(yaw);
                soldier.yHeadRotO = yaw;
                soldier.setXRot(pitch);
                soldier.xRotO = pitch;

                if (cooldown <= 0) {
                    if (holdBowDraw(null)) return;
                    soldier.performVolleyShot(vt);
                    playShotFinishAnimation();
                    cooldown = soldier.getArenaRangedAttackCooldownTicks();
                }
                return;
            }
        }

        // ==================== 普通射击 ====================
        LivingEntity target = soldier.getTarget();
        if (target == null) {
            cancelBowDraw();
            return;
        }
        boolean mounted = soldier.getSoldierType().isCavalry();
        Mob mover = soldier.getVehicle() instanceof Mob h ? h : soldier;
        boolean closeThreat = soldier.distanceTo(target) < soldier.getMinAttackRange(mounted);
        if (soldier.isFollowingMarchRoute() || soldier.isMarchRoutePlanning()) {
            if (!closeThreat) {
                cancelBowDraw();
                return;
            }
            soldier.clearMarchRoute();
            AiNavUtil.haltMovement(mover);
            if (mover != soldier) AiNavUtil.stopMovementInput(soldier);
        }

        if (withdrawing && soldier.getVehicle() instanceof WarHorseEntity horse) {
            syncRiderFacingToMount(horse);
        } else {
            soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
            if (soldier.getVehicle() instanceof Mob horse) {
                horse.getLookControl().setLookAt(target, 30.0f, 30.0f);
            }
        }

        double min = soldier.getMinAttackRange(mounted);
        double max = soldier.getArenaEffectiveMaxAttackRange(mounted);
        // 理想站位距离（默认 = 最大射程；霰弹枪兵更近）
        double hold = soldier.getPreferredAttackRange(mounted);
        double dist = soldier.distanceTo(target);
        if (bowDrawTicks > 0 && dist > max + 1.0) cancelBowDraw();

        // 硬驻守：不主动追击，但敌人贴身时允许紧急让开。
        //   能否命中交给 tryPerformRangedAttack 判断
        //   （弓箭走 BallisticAim，枪械走 GunAim + 显式射程闸门）。
        if (soldier.isHardStationed()) {
            if (dist < min || withdrawing) {
                // 驻守只是不主动追击；敌人贴身后仍要做一次有限的自保后撤。
                maintainSkirmishSpacing(mover, target, min, hold, dist, false);
            } else {
                clearSkirmishSpacing(mover, true);
            }
            if (cooldown <= 0) {
                if (holdBowDraw(target.getUUID())) return;
                boolean fired = soldier.tryPerformRangedAttack(target);
                if (fired) {
                    playShotFinishAnimation();
                    cooldown = soldier.getArenaRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                }
            }
            return;
        }

        boolean isDual = soldier.getRole().isDual();
        boolean highHpDual = isDual && !isDualLowHealth();
        boolean routeOwnsMovement = soldier.isFollowingRoute();
        if (routeOwnsMovement && closeThreat) {
            soldier.clearRoute();
            AiNavUtil.haltMovement(mover);
            if (mover != soldier) AiNavUtil.stopMovementInput(soldier);
            routeOwnsMovement = false;
        }

        if (highHpDual) {
            if (repositionTicks > 0 || routeOwnsMovement) {
                // 保持导航路径
            } else if (dist > 4.5) {
                // 主动靠近：原值 1.0；节流重发，避免每 tick 重算路径
                if (shouldReissueMove(mover)) mover.getNavigation().moveTo(target, 1.0D);
            } else {
                mover.getNavigation().stop();
            }

            if (cooldown <= 0 && dist > 4.5) {
                if (holdBowDraw(target.getUUID())) return;
                boolean fired = soldier.tryPerformRangedAttack(target);
                if (fired) {
                    failedShots = 0;
                    playShotFinishAnimation();
                    cooldown = soldier.getArenaRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                    onShotFailed(mover, target);
                }
            }
        } else {
            if (repositionTicks > 0 || routeOwnsMovement) {
                // reposition 期间不覆盖 navigation
            } else {
                maintainSkirmishSpacing(mover, target, min, hold, dist, true);
            }

            // 目标超出最大射程时只靠近、不开火：
            //   否则每次失败都会用 1 格微移覆盖整段寻路，导致原地反复重算路径
            if (cooldown <= 0 && dist <= max + 1.0) {
                if (holdBowDraw(target.getUUID())) return;
                boolean fired = soldier.tryPerformRangedAttack(target);
                if (fired) {
                    failedShots = 0;
                    playShotFinishAnimation();
                    cooldown = soldier.getArenaRangedAttackCooldownTicks();
                } else {
                    cooldown = 5;
                    onShotFailed(mover, target);
                }
            }
        }
    }

    /**
     * 维持远程交战距离。后撤采用迟滞，并在正后方受阻时转为斜后方；
     * 距离合适后只在近距机动带内短暂侧移，不把整段最大射程都当作风筝区。
     */
    private void maintainSkirmishSpacing(Mob mover, LivingEntity target,
                                         double minRange, double holdRange,
                                         double distance, boolean allowSidestep) {
        int now = soldier.tickCount;
        if (spacingTargetId == null || !spacingTargetId.equals(target.getUUID())) {
            spacingTargetId = target.getUUID();
            spacingDestination = null;
            spacingMoveTick = -1000;
            spacingCommitUntil = 0;
            directSpacing = false;
            withdrawing = false;
            failedWithdrawPlans = 0;
            sidestepDirection = soldier.getRandom().nextBoolean() ? 1 : -1;
            sidestepFlipTick = now + SIDESTEP_FLIP_MIN_TICKS;
        }

        double releaseDistance = minRange
                + Math.min(4.0, Math.max(2.0, minRange * 0.18));
        if (withdrawing && distance >= releaseDistance) {
            AiNavUtil.haltMovement(mover);
            withdrawing = false;
            spacingDestination = null;
            directSpacing = false;
            failedWithdrawPlans = 0;
            return;
        }

        if (distance < minRange) {
            if (!withdrawing && GeneralConfig.debugMovementLog) {
                Monster_conquest.LOGGER.info(
                        "[RANGED-KITE] unit={} phase=withdraw start={} min={} target={}",
                        soldier.getUUID().toString().substring(0, 8),
                        String.format(java.util.Locale.ROOT, "%.2f", distance),
                        String.format(java.util.Locale.ROOT, "%.2f", minRange),
                        target.getUUID().toString().substring(0, 8));
            }
            withdrawing = true;
        }
        if (withdrawing) {
            boolean routedWithdraw = !directSpacing && spacingDestination != null;
            Vec3 frameMovement = AiNavUtil.observedHorizontalMovement(mover);
            boolean routeStalled = routedWithdraw
                    && (mover.horizontalCollision || frameMovement.lengthSqr() < 1.0E-4);
            // 主世界的紧急后撤是固定 1.2 格/tick 的直接位移，不读取实体移速。
            // 竞技单位必须走导航/移动控制器，否则远程兵会永久快于近战追击者。
            boolean allowDirectEscape = !soldier.isArenaUnit()
                    && (!routedWithdraw || routeStalled);
            if (allowDirectEscape && AiNavUtil.applyFullSpeedBackstep(
                    mover, soldier.isPassenger() ? soldier : null, target)) {
                spacingDestination = null;
                directSpacing = false;
                spacingCommitUntil = 0;
                failedWithdrawPlans = 0;
                return;
            }
            if (allowDirectEscape && trySideStepRetreat(mover, target)) {
                spacingDestination = null;
                directSpacing = false;
                spacingCommitUntil = 0;
                failedWithdrawPlans = 0;
                return;
            }
            boolean stuck = mover.getNavigation().isStuck();
            boolean finished = mover.getNavigation().isDone();
            boolean directReached = directSpacing && spacingDestination != null
                    && mover.position().distanceToSqr(spacingDestination) <= 1.0;
            boolean needPlan = spacingDestination == null
                    || now >= spacingCommitUntil
                    || directReached
                    || (!directSpacing && (finished || stuck) && now - spacingMoveTick >= 6);
            if (needPlan) {
                LivingEntity rider = soldier.isPassenger() ? soldier : null;
                mover.getNavigation().stop();
                spacingDestination = AiNavUtil.findFallbackSpacingPoint(
                        mover, rider, target, releaseDistance);
                directSpacing = false;
                spacingMoveTick = -1000;
                if (spacingDestination == null) {
                    spacingDestination = AiNavUtil.startRoutedRetreat(
                            mover, rider, target, releaseDistance);
                    if (spacingDestination != null) spacingMoveTick = now;
                }
                if (spacingDestination == null) {
                    spacingDestination = AiNavUtil.findImmediateRetreatPoint(
                            mover, rider, target);
                    directSpacing = spacingDestination != null;
                }
                if (spacingDestination == null) {
                    spacingDestination = AiNavUtil.findStraightBackstepPoint(
                            mover, rider, target);
                    directSpacing = spacingDestination != null;
                }
                if (spacingDestination != null) {
                    failedWithdrawPlans = 0;
                } else {
                    failedWithdrawPlans++;
                    if (GeneralConfig.enableFormSwitch
                            && soldier.getVehicle() instanceof WarHorseEntity
                            && failedWithdrawPlans >= 3 && mover.horizontalCollision) {
                        AiNavUtil.haltMovement(mover);
                        soldier.dismountForTerrain();
                        spacingDestination = null;
                        directSpacing = false;
                        failedWithdrawPlans = 0;
                        return;
                    }
                }
                if (GeneralConfig.debugMovementLog
                        && (spacingDestination != null || now - lastSpacingLogTick >= 20)) {
                    lastSpacingLogTick = now;
                    Monster_conquest.LOGGER.info(
                            "[RANGED-KITE] unit={} phase=plan release={} direct={} destination={}",
                            soldier.getUUID().toString().substring(0, 8),
                            String.format(java.util.Locale.ROOT, "%.2f", releaseDistance),
                            directSpacing,
                            spacingDestination == null ? "none"
                                    : String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f",
                                            spacingDestination.x,
                                            spacingDestination.y,
                                            spacingDestination.z));
                }
                spacingCommitUntil = now + SPACING_COMMIT_TICKS;
            }
            if (spacingDestination != null && directSpacing) {
                mover.getNavigation().stop();
                mover.getMoveControl().setWantedPosition(
                        spacingDestination.x, spacingDestination.y,
                        spacingDestination.z, 1.0D);
                spacingMoveTick = now;
            } else if (spacingDestination != null
                    && (now - spacingMoveTick >= 10
                    || mover.getNavigation().isDone()
                    || mover.getNavigation().isStuck())) {
                if (mover.getNavigation().moveTo(
                        spacingDestination.x, spacingDestination.y, spacingDestination.z, 1.0D)) {
                    spacingMoveTick = now;
                } else {
                    spacingDestination = null;
                }
            }
            return;
        }

        if (distance > holdRange) {
            spacingDestination = null;
            if (shouldReissueMove(mover)) mover.getNavigation().moveTo(target, 1.0D);
            return;
        }

        double skirmishLimit = minRange
                + Math.max(5.0, Math.min(8.0, minRange * 0.3));
        if (distance > skirmishLimit) {
            if (AiNavUtil.isMovingHorizontally(target)) {
                if (shouldReissueMove(mover)) {
                    mover.getNavigation().moveTo(target, 1.0D);
                }
            } else {
                mover.getNavigation().stop();
            }
            return;
        }
        if (!allowSidestep) {
            AiNavUtil.haltMovement(mover);
            return;
        }

        if (now >= sidestepFlipTick) {
            sidestepDirection = soldier.getRandom().nextBoolean() ? 1 : -1;
            sidestepFlipTick = now + SIDESTEP_FLIP_MIN_TICKS
                    + soldier.getRandom().nextInt(SIDESTEP_FLIP_RANDOM_TICKS + 1);
        }
        if (soldier.isArenaUnit()) {
            AiNavUtil.haltMovement(mover);
            return;
        }
        LivingEntity rider = soldier.isPassenger() ? soldier : null;
        if (!AiNavUtil.applyFullSpeedSidestep(mover, rider, target, sidestepDirection)) {
            sidestepDirection = -sidestepDirection;
            if (!AiNavUtil.applyFullSpeedSidestep(mover, rider, target, sidestepDirection)) {
                AiNavUtil.haltMovement(mover);
            }
        }
    }

    private void clearSkirmishSpacing(Mob mover, boolean stopMovement) {
        spacingTargetId = null;
        spacingDestination = null;
        spacingMoveTick = -1000;
        spacingCommitUntil = 0;
        directSpacing = false;
        withdrawing = false;
        failedWithdrawPlans = 0;
        if (stopMovement) {
            AiNavUtil.haltMovement(mover);
        }
    }

    private boolean trySideStepRetreat(Mob mover, LivingEntity target) {
        LivingEntity rider = soldier.isPassenger() ? soldier : null;
        if (AiNavUtil.applyFullSpeedSidestep(mover, rider, target, sidestepDirection)) {
            sidestepFlipTick = Math.max(sidestepFlipTick, soldier.tickCount + 30);
            return true;
        }
        sidestepDirection = -sidestepDirection;
        if (AiNavUtil.applyFullSpeedSidestep(mover, rider, target, sidestepDirection)) {
            sidestepFlipTick = Math.max(sidestepFlipTick, soldier.tickCount + 30);
            return true;
        }
        return false;
    }

    private void syncRiderFacingToMount(WarHorseEntity horse) {
        float yaw = horse.getYRot();
        soldier.setYRot(yaw);
        soldier.yBodyRot = yaw;
        soldier.setYHeadRot(yaw);
        soldier.yRotO = yaw;
        soldier.yBodyRotO = yaw;
        soldier.yHeadRotO = yaw;
    }

    /**
     * 弓类武器先进入原版使用物品状态，让客户端模型播放拉弓；
     * 非弓远程武器仍使用普通挥臂动画。
     *
     * @return true 表示本 tick 仍在拉弓或需要等待下一次重新瞄准
     */
    private boolean holdBowDraw(@Nullable UUID targetId) {
        if (soldier.getMainHandItem().getUseAnimation() != UseAnim.BOW) {
            cancelBowDraw();
            return false;
        }
        if (bowDrawTicks > 0) {
            if (targetId != null && !targetId.equals(bowDrawTargetId)) {
                cancelBowDraw();
                return true;
            }
            if (--bowDrawTicks > 0) return true;
            cancelBowDraw();
            return false;
        }
        soldier.startUsingItem(InteractionHand.MAIN_HAND);
        bowDrawTicks = BOW_DRAW_TICKS;
        bowDrawTargetId = targetId;
        return true;
    }

    private void cancelBowDraw() {
        if (soldier.isUsingItem() && soldier.getUsedItemHand() == InteractionHand.MAIN_HAND
                && soldier.getUseItem().getUseAnimation() == UseAnim.BOW) {
            soldier.stopUsingItem();
        }
        bowDrawTicks = 0;
        bowDrawTargetId = null;
    }

    /** 弓由使用物品姿态驱动；其它远程武器使用挥臂。 */
    private void playShotFinishAnimation() {
        if (soldier.getMainHandItem().getUseAnimation() == UseAnim.BOW) return;
        if (!soldier.getRole().isGunner()) {
            soldier.swing(InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * 开火失败处理：直线被挡（墙 / 掩体）时立即重新规划完整绕路路线；
     *   否则先小幅挪位继续尝试，连续失败同样转入绕路。绕路期间保持路线不被打断。
     */
    private void onShotFailed(Mob mover, LivingEntity target) {
        if (!soldier.hasClearShot(target) || ++failedShots >= ROUTE_AFTER_FAILED_SHOTS) {
            failedShots = 0;
            // 只有真的规划出路线才进入"保持路线"状态；否则退回小幅挪位，避免站着不动
            if (soldier.replanRouteTo(target)) {
                repositionTicks = ROUTE_TICKS;
            } else {
                repositionTicks = REPOSITION_TICKS;
                tryReposition(mover);
            }
        } else {
            repositionTicks = REPOSITION_TICKS;
            tryReposition(mover);
        }
    }

    private void tryReposition(Mob mover) {
        LivingEntity target = soldier.getTarget();
        if (target == null) return;
        // 小幅挪位同样要节流：每次最多 3 次寻路，不能每 tick 都来一遍
        if (!shouldReissueMove(mover)) return;

        if (mover instanceof WarHorseEntity horse) {
            horse.setCharging(false);
            horse.setPassThroughTicks(0);
        }

        double dx = target.getX() - soldier.getX();
        double dz = target.getZ() - soldier.getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.5) return;

        // 挪位一小步：原值 1.0
        double nx = soldier.getX() + dx / horiz * 1.0;
        double nz = soldier.getZ() + dz / horiz * 1.0;
        if (mover.getNavigation().moveTo(nx, soldier.getY(), nz, 1.0D)) return;

        double perpX = -dz / horiz;
        double perpZ = dx / horiz;
        for (int sign : new int[]{1, -1}) {
            double sx = soldier.getX() + perpX * sign * 1.0;
            double sz = soldier.getZ() + perpZ * sign * 1.0;
            if (mover.getNavigation().moveTo(sx, soldier.getY(), sz, 1.0D)) return;
        }
    }

    private boolean isDualLowHealth() {
        if (soldier.isArenaUnit()
                && soldier.getArenaDisplayMaxHealth() > 0.0f) {
            return soldier.getArenaHealthRatio() < DUAL_LOW_HP_THRESHOLD;
        }
        return soldier.getHealth() < soldier.getMaxHealth() * DUAL_LOW_HP_THRESHOLD;
    }

    @Override
    public void stop() {
        cancelBowDraw();
        Mob mover = soldier.getVehicle() instanceof Mob mount ? mount : soldier;
        AiNavUtil.haltMovement(mover);
        if (mover != soldier) {
            AiNavUtil.stopMovementInput(soldier);
        }
        repositionTicks = 0;
        clearSkirmishSpacing(mover, false);
    }

    // ===================== 回退目标搜索 =====================

    /**
     * 从玩家军队共享仇恨列表里找最近的活敌人。
     *
     *   · 只对玩家军队士兵生效（NPC 军队走 AttackConfiguredEnemyGoal + 首领 hatredMap）
     *   · 10 tick 节流 + 16 格扫描半径 → 开销可忽略
     *   · 只查 sharedHatredArmies / sharedHatredEntities（玩家军队当前仇恨的敌人）
     */
    @Nullable
    private LivingEntity findFallbackTarget() {
        if (soldier.isMoveOrderPriorityActive()) return null;
        if (fallbackScanCooldown > 0) {
            fallbackScanCooldown--;
            return null;
        }
        fallbackScanCooldown = FALLBACK_SCAN_COOLDOWN;

        if (!soldier.isPlayerArmy()) return null;
        Player owner = soldier.getCommanderPlayer();
        if (owner == null) return null;
        var st = PlayerArmyManager.getIfPresent(owner.getUUID());
        if (st == null) return null;

        long nowGT = soldier.level().getGameTime();

        LivingEntity best = null;
        double bestDistSqr = Double.MAX_VALUE;

        for (LivingEntity e : soldier.level().getEntitiesOfClass(
                LivingEntity.class,
                soldier.getBoundingBox().inflate(FALLBACK_SCAN_RANGE),
                e -> e.isAlive() && e != soldier && e.canBeHitByProjectile())) {

            if (!soldier.canAttack(e)) continue;

            UUID theirArmy = AbstractArmySoldier.resolveArmyUuid(e, soldier.level());
            boolean isEnemy = false;
            if (theirArmy != null && st.isSharedHatredArmy(theirArmy, nowGT)) {
                isEnemy = true;
            } else if (st.isSharedHatredEntity(e.getUUID(), nowGT)) {
                isEnemy = true;
            }
            if (!isEnemy) continue;

            double d = soldier.distanceToSqr(e);
            if (d < bestDistSqr) {
                bestDistSqr = d;
                best = e;
            }
        }
        return best;
    }
}
