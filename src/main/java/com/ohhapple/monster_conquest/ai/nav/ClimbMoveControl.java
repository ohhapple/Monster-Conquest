package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 梯子移动控制：在梯子列内以及进出梯子列时直接给出移动速度。
 *   原版只根据"贴着方块"决定是否向上爬，目标在下方或背后时会和下滑互相顶，
 *   单位在梯子上原地抖动。这里按当前移动目标直接设定速度：向上 0.2、向下 -0.15
 *   （原版梯子的上下限），横向不超过 0.15；进出梯子列时朝梯板开口侧靠，
 *   避免被 3/16 厚的薄板挡住。同时清掉移动输入，不再依赖"贴住梯子"这个条件。
 *   移动目标由原版导航每 tick 对齐到当前路径节点，整条"进入 → 上下 → 离开"都按
 *   寻路节点执行；没有路径、由上层直接给点时同样适用。
 */
public class ClimbMoveControl extends MoveControl {

    /** 原版梯子向上速度。 */
    private static final double RISE_SPEED = 0.2;
    /** 原版梯子向下速度上限（handleOnClimbable 的 clamp 值取负）。 */
    private static final double DROP_SPEED = -0.15;
    /** 翻越梯顶 / 平台边缘时的上冲：越过边缘才走得出横向那一步。 */
    private static final double EDGE_RISE_SPEED = 0.3;
    /** 翻越时在落点正上方悬停：刚好抵消重力的竖向速度。 */
    private static final double HOVER_SPEED = 0.08;
    /** 跨平台边缘时继续给上冲 / 横推的 tick 数（离开梯子列之后还要把身体抬过边缘、挪到落点上方）。 */
    private static final int MANTLE_TICKS = 25;
    /**
     * 停在某一层：竖向速度直接给 0。
     *   移动控制每 tick 都会重设速度，所以 0 就是"原地不动"；给正数（哪怕 0.08）会变成
     *   每 tick 稳定上移（重力是在位移之后才结算的，不会在同一 tick 里抵消）。
     */
    private static final double HOLD_SPEED = 0.0;
    /** 攀爬时的横向修正上限 = 原版梯子横向速度上限。 */
    private static final double LATERAL_SPEED = 0.15;
    /** 迈进 / 迈出梯子列时的横向速度（人还不在梯子上，不受梯子横向上限约束）。 */
    private static final double ENTRY_SPEED = 0.22;
    /** 竖向死区：目标高度差在这个范围内算"到这一层了"。 */
    private static final double LEVEL_TOLERANCE = 0.4;
    /** 把目标点当成"进梯子列"的最远水平距离；更远就交给普通寻路先走过去。 */
    private static final double ENTRY_REACH = 1.8;
    /** 进梯子列时朝开口侧让开的距离（避开梯子薄板）。 */
    private static final double PLATE_CLEARANCE = 0.22;

    public ClimbMoveControl(Mob mob) {
        super(mob);
    }

    /** 正在"跨上高一点的平台边缘"的收尾 tick 数。 */
    private int mantleTicks;
    /** 翻越边缘要抬到的脚部高度（由 driveOnColumn 在开始翻越时确定）。 */
    private double mantleClearY;
    /** 从梯板一侧翻越时的梯子格与梯板一侧（身体完全越过梯板后才撒手，避免落在板沿上）。 */
    @Nullable private BlockPos mantlePlateCell;
    @Nullable private Direction mantlePlate;

    @Override
    public void tick() {
        Mob mob = this.mob;
        if (mob.getVehicle() != null) {
            super.tick();
            return;
        }
        if (mob.onGround()) this.mantleTicks = 0;
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        if (isHoldingLadder(level, feet)) {
            if (this.operation == Operation.MOVE_TO && this.speedModifier > 0.0) {
                driveOnColumn(level, feet);
            } else {
                holdOnColumn();
            }
            return;
        }
        if (this.operation != Operation.MOVE_TO || this.speedModifier <= 0.0) {
            super.tick();
            return;
        }
        if (this.mantleTicks > 0 && tryContinueMantle()) return;
        if (tryStepIntoColumn(level)) return;
        super.tick();
    }

    /**
     * 站在梯子格上但没有移动目标（驻守 / 待命 / 刚到位）时按住梯子。
     *   否则单位会顺着梯子慢慢下滑，滑走后上层再下发目标爬回来，来回抖动。
     */
    private void holdOnColumn() {
        Mob mob = this.mob;
        mob.setDeltaMovement(0.0, HOLD_SPEED, 0.0);
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        mob.setJumping(false);
        mob.fallDistance = 0.0F;
    }

    /** 脚所在格能不能扒住梯子（与原版 onClimbable 同一套判定，外加梯子活板门通道）。 */
    private boolean isHoldingLadder(Level level, BlockPos feet) {
        return this.mob.onClimbable() || ClimbColumns.isClimbCell(level, feet);
    }

    /** 在梯子列里按目标高度差决定上爬 / 下滑 / 停住，并做小幅横向修正。 */
    private void driveOnColumn(Level level, BlockPos feet) {
        Mob mob = this.mob;
        BlockPos column = ClimbColumns.isClimbCell(level, feet) ? feet : feet.below();

        double dy = this.wantedY - mob.getY();
        boolean sameColumn = Mth.floor(this.wantedX) == column.getX()
                && Mth.floor(this.wantedZ) == column.getZ();
        // 目标在本列时对齐列中心，并朝开口侧让开一点，避免正对薄板被顶住。
        Direction open = ClimbColumns.openSide(level, column);
        double targetX = sameColumn ? column.getX() + 0.5 : this.wantedX;
        double targetZ = sameColumn ? column.getZ() + 0.5 : this.wantedZ;
        if (sameColumn && open != null) {
            targetX += open.getStepX() * PLATE_CLEARANCE * 0.5;
            targetZ += open.getStepZ() * PLATE_CLEARANCE * 0.5;
        }
        double dx = targetX - mob.getX();
        double dz = targetZ - mob.getZ();
        double hdist = Math.sqrt(dx * dx + dz * dz);

        double vy;
        // 目标在列外、这一层或更高：从顶端迈出去，需要把身体抬过梯板顶面再横挪。
        boolean exiting = !sameColumn && this.wantedY >= column.getY() - 0.4;
        double riseThreshold = sameColumn ? LEVEL_TOLERANCE : 0.05;
        if (exiting) {
            double clearY = this.wantedY + 0.25;
            // 迈出的方向正对梯板一侧时（梯子贴墙，出口在墙那一侧）：
            //   梯板一直挡到本格顶面，必须抬过它再横挪。
            this.mantlePlateCell = null;
            this.mantlePlate = null;
            Direction openSide = ClimbColumns.openSide(level, column);
            if (openSide != null) {
                Direction plate = openSide.getOpposite();
                double toX = this.wantedX - (column.getX() + 0.5);
                double toZ = this.wantedZ - (column.getZ() + 0.5);
                Direction exitDir = Math.abs(toX) >= Math.abs(toZ)
                        ? (toX >= 0 ? Direction.EAST : Direction.WEST)
                        : (toZ >= 0 ? Direction.SOUTH : Direction.NORTH);
                if (exitDir == plate) {
                    // 抬到"梯板顶面 + 一点点"就够：再高就是在空中多升一段（视觉上像凭空上浮）。
                    clearY = Math.max(clearY, column.getY() + 1.02);
                    this.mantlePlateCell = column;
                    this.mantlePlate = plate;
                    if (tryStepOverPlate(level, column, plate)) return;
                }
            }
            this.mantleClearY = clearY;
            this.mantleTicks = MANTLE_TICKS;
            // 接近目标高度时减速，避免最后一下冲过头、在平台面上方多浮一截。
            double remaining = this.mantleClearY - mob.getY();
            vy = remaining <= 0.0 ? HOLD_SPEED
                    : (remaining < 0.25 ? Math.max(0.05, remaining + 0.08) : EDGE_RISE_SPEED);
        } else if (dy > riseThreshold) {
            vy = RISE_SPEED;
        } else if (dy < -LEVEL_TOLERANCE) {
            vy = DROP_SPEED;
        } else {
            vy = HOLD_SPEED;
        }

        double sx = 0.0;
        double sz = 0.0;
        // 只是"停在某一层"（目标就在本列、高度也到了）时不做横推：
        //   到位 / 驻守的单位不该再挪窝，站在哪就是哪。
        boolean holdAtLevel = sameColumn && Math.abs(dy) <= LEVEL_TOLERANCE;
        if (hdist > 1.0E-3 && !holdAtLevel) {
            double lateral = Math.min(LATERAL_SPEED, Math.max(0.05, hdist * 0.35));
            // 要到旁边平台上去（含跨高一格的边缘）：横向给满（梯子横向上限），
            //   否则贴着梯子一点点蹭、迈不出去
            if (!sameColumn && Math.abs(dy) <= 2.6) lateral = LATERAL_SPEED;
            sx = dx / hdist * lateral;
            sz = dz / hdist * lateral;
        }
        applyDrive(sx, vy, sz, dx, dz, hdist);
    }

    /**
     * 梯顶与平台面齐平时的直接迈出：脚已到平台面高度且终点无碰撞时，横挪过薄梯板，
     *   不必升到梯板顶面之上。挪不过去返回 false，调用方退回抬升翻越。
     */
    private boolean tryStepOverPlate(Level level, BlockPos column, Direction plate) {
        Mob mob = this.mob;
        if (mob.getY() < this.wantedY - 0.05) return false;
        double reach = mob.getBbWidth() * 0.5 + 0.06;
        double nx = mob.getX();
        double nz = mob.getZ();
        switch (plate) {
            case SOUTH -> nz = column.getZ() + 1.0 + reach;
            case NORTH -> nz = column.getZ() - reach;
            case EAST -> nx = column.getX() + 1.0 + reach;
            case WEST -> nx = column.getX() - reach;
            default -> {
                return false;
            }
        }
        if (nx == mob.getX() && nz == mob.getZ()) return false;
        int bodyHeight = Math.max(1, Mth.floor(mob.getBbHeight() + 1.0F));
        if (!ClimbColumns.isStandable(level, BlockPos.containing(nx, mob.getY(), nz), bodyHeight)) {
            return false;
        }
        if (!level.noCollision(mob,
                mob.getBoundingBox().move(nx - mob.getX(), 0.0, nz - mob.getZ()))) {
            return false;
        }
        mob.setPos(nx, mob.getY(), nz);
        mob.setDeltaMovement(0.0, 0.0, 0.0);
        this.mantleTicks = 0;
        return true;
    }

    /**
     * 跨平台边缘的收尾：人已离开梯子列，但目标在旁边的落点上。
     *   继续小幅上冲并横移，把身体带过边缘；持续固定 tick 数后交回原版。
     */
    private boolean tryContinueMantle() {
        Mob mob = this.mob;
        double dx = this.wantedX - mob.getX();
        double dz = this.wantedZ - mob.getZ();
        double hdist = Math.sqrt(dx * dx + dz * dz);
        // 翻越途中不落地、也不被拖太远；目标格必须能站，避免朝空处推。
        BlockPos cell = BlockPos.containing(this.wantedX, this.wantedY, this.wantedZ);
        int height = Math.max(1, Mth.floor(mob.getBbHeight() + 1.0F));
        BlockPos mobCell = mob.blockPosition();
        boolean inTargetCell = mobCell.getX() == cell.getX()
                && mobCell.getY() == cell.getY() && mobCell.getZ() == cell.getZ();
        if ((mob.onGround() && inTargetCell) || hdist > 4.0
                || !ClimbColumns.isStandable(mob.level(), cell, height)) {
            this.mantleTicks = 0;
            return false;
        }
        this.mantleTicks--;
        if (this.mantleTicks <= 0 || (hdist <= 0.2 && clearOfPlate(mob))) {
            // 已经挪到落点正上方（或收尾超时）→ 交回原版：人自然落到平台上。
            //   撒手前横向速度清零：带着惯性飘出去的话，下坠途中容易被梯板顶边接住。
            mob.setDeltaMovement(0.0, mob.getDeltaMovement().y, 0.0);
            return false;
        }
        // 抬够高度后保持高度横移到落点正上方；蹭到梯板顶边时也要继续抬，不能停在板沿上。
        // 超出目标高度的部分直接夹回，避免"到顶后凭空继续升高"。
        if (!mob.onGround() && !AiNavUtil.isOnClimbable(mob)
                && mob.getY() > this.mantleClearY + 0.01) {
            mob.setPos(mob.getX(), this.mantleClearY, mob.getZ());
        }
        double vy = mob.getY() < this.mantleClearY || (mob.onGround() && !inTargetCell)
                ? EDGE_RISE_SPEED
                : HOVER_SPEED;
        double lateral = Math.max(0.05, hdist * 0.35);
        if (hdist > 1.0E-3) {
            lateral = Math.min(LATERAL_SPEED, Math.max(lateral, LATERAL_SPEED * 0.8));
            applyDrive(dx / hdist * lateral, vy, dz / hdist * lateral, dx, dz, hdist);
            return true;
        }
        applyDrive(0.0, vy, 0.0, 0.0, 0.0, 0.0);
        return true;
    }

    /**
     * 身体是否已经完全越过梯板那一侧的面。
     *   差一点就撒手的话，下坠途中身体会蹭到梯板顶边，落在板沿上。
     */
    private boolean clearOfPlate(Mob mob) {
        if (this.mantlePlate == null || this.mantlePlateCell == null) return true;
        double reach = mob.getBbWidth() * 0.5 + 0.05;
        return switch (this.mantlePlate) {
            case EAST -> mob.getX() - reach > this.mantlePlateCell.getX() + 1.0;
            case WEST -> mob.getX() + reach < this.mantlePlateCell.getX();
            case SOUTH -> mob.getZ() - reach > this.mantlePlateCell.getZ() + 1.0;
            case NORTH -> mob.getZ() + reach < this.mantlePlateCell.getZ();
            default -> true;
        };
    }

    /**
     * 移动目标就在旁边的梯子上、人还没进梯子列时：朝梯子开口侧迈过去。
     *   梯底走进去（同层）、梯顶迈下去（低一层）都走这一条；
     *   梯子最低一节在脚上方时按原版跳一下，不跳扒不住。
     */
    private boolean tryStepIntoColumn(Level level) {
        Mob mob = this.mob;
        BlockPos cell = BlockPos.containing(this.wantedX, this.wantedY, this.wantedZ);
        if (!ClimbColumns.isClimbCell(level, cell)) return false;
        double hdx = this.wantedX - mob.getX();
        double hdz = this.wantedZ - mob.getZ();
        double hdist = Math.sqrt(hdx * hdx + hdz * hdz);
        if (hdist > ENTRY_REACH) return false;

        BlockPos rung = ClimbColumns.isRung(level, cell) ? cell : cell.below();
        // 移动目标可能是"顺着这根梯子滑到下面的落点"：进梯子列要挑自己这一层的那一节，
        //   直接朝下面那一节的坐标走过去会从平台边掉到梯子外面。
        int entryY = Mth.clamp(Mth.floor(mob.getY()),
                ClimbColumns.columnBottomY(level, rung),
                ClimbColumns.columnTopY(level, rung));
        BlockPos entry = new BlockPos(rung.getX(), entryY, rung.getZ());
        double dy = (double) entryY - mob.getY();
        double targetX = entry.getX() + 0.5;
        double targetZ = entry.getZ() + 0.5;
        Direction open = ClimbColumns.openSide(level, entry);
        if (open != null) {
            // 梯子薄板贴在支撑墙一侧：从开口侧靠过去，别正对薄板（会骑在板上进不去）
            targetX += open.getStepX() * PLATE_CLEARANCE;
            targetZ += open.getStepZ() * PLATE_CLEARANCE;
        }
        double dx = targetX - mob.getX();
        double dz = targetZ - mob.getZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist < 1.0E-3) return false;

        double push = Math.min(ENTRY_SPEED, Math.max(dist * 0.5, 0.1));
        // 站在地上时不给竖向速度：同层走进去 / 从平台边迈下去都交给重力与梯子物理
        double vy = mob.onGround() ? 0.0 : Math.min(mob.getDeltaMovement().y, -0.02);
        if (dy > 0.5 && mob.onGround()) {
            mob.getJumpControl().jump();
            vy = mob.getDeltaMovement().y;   // 跳跃速度由原版物理给出，这里不要压掉
        }
        applyDrive(dx / dist * push, vy, dz / dist * push, dx, dz, dist);
        return true;
    }

    /** 施加一次驱动：直接给速度、清移动输入、朝向移动方向。 */
    private void applyDrive(double sx, double vy, double sz, double dx, double dz, double hdist) {
        Mob mob = this.mob;
        mob.setDeltaMovement(sx, vy, sz);
        mob.fallDistance = 0.0F;
        mob.hasImpulse = true;
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        mob.setSpeed((float) (this.speedModifier * mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        if (hdist > 1.0E-3) {
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
            mob.setYRot(yaw);
            mob.yBodyRot = yaw;
            mob.yHeadRot = yaw;
        }
        // 本 tick 的目标已消费；导航下一 tick 会再下发（跟随路径节点 / 上层直接给点都行）
        this.operation = Operation.WAIT;
    }
}
