package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 梯子列的公共判定：可攀爬方块、可站立落点、列高度与开口方向的统一规则。
 *
 *   坐标一律使用脚所在的方块格，与原版寻路节点一致；梯子顶端的活板门打开时，
 *   那一格也算一节通道。节点评估（{@link ClimbNodeEvaluator}）、移动控制
 *   （{@link ClimbMoveControl}）和绕路规划（RoutePlanner）共用这里的标准，
 *   避免各自判断不一致。
 */
public final class ClimbColumns {

    /** 沿梯子列上下最多数多少节（断梯 / 超长列兜底）。 */
    public static final int MAX_COLUMN_SCAN = 96;

    private ClimbColumns() {}

    /** 这一格是不是可攀爬方块（梯子 / 脚手架 / 藤蔓）。 */
    public static boolean isRung(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.CLIMBABLE);
    }

    /** 身体高度以内有没有实心方块挡着。 */
    public static boolean hasClearance(BlockGetter level, BlockPos pos, int height) {
        for (int dy = 0; dy < Math.max(1, height); dy++) {
            if (level.getBlockState(pos.above(dy)).isSolid()) return false;
        }
        return true;
    }

    /** 梯子顶端的活板门通道：打开着的活板门，紧挨着梯子 —— 原版把这种格子当梯子用。 */
    public static boolean isClimbPassage(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TrapDoorBlock)
                || !state.hasProperty(TrapDoorBlock.OPEN)
                || !state.getValue(TrapDoorBlock.OPEN)) {
            return false;
        }
        return isRung(level, pos.below()) || isRung(level, pos.above());
    }

    /** 脚踩在这一格能不能扒住梯子（路径节点与移动控制器共用这一条判定）。 */
    public static boolean isClimbCell(BlockGetter level, BlockPos pos) {
        return isRung(level, pos) || isClimbPassage(level, pos);
    }

    /** 这一格能不能站：脚下有结实顶面、身体净空、不碰危险方块。 */
    public static boolean isStandable(BlockGetter level, BlockPos pos, int height) {
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (!belowState.isFaceSturdy(level, below, Direction.UP)) return false;
        if (belowState.getCollisionShape(level, below).max(Direction.Axis.Y) > 1.05) return false;
        if (AiNavUtil.isDangerousBlock(belowState)
                || AiNavUtil.isDangerousBlock(level.getBlockState(pos))
                || AiNavUtil.isDangerousBlock(level.getBlockState(pos.above()))) {
            return false;
        }
        return hasClearance(level, pos, height);
    }

    /**
     * 顺着梯子列往下滑时"能落脚"的第一格：
     *   中间每一格都得通过，落点上面那一格还在梯子列里（也就是确实顺着梯子滑下来的，
     *   不是半空撒手）。找不到返回 null。
     */
    @Nullable
    public static BlockPos findSlideLanding(BlockGetter level, BlockPos from, int height, int maxDrop) {
        int limit = Math.max(1, Math.min(maxDrop, MAX_COLUMN_SCAN));
        for (int i = 1; i <= limit; i++) {
            BlockPos pos = from.below(i);
            if (!hasClearance(level, pos, height)) return null;
            if (isStandable(level, pos, height)) {
                return isClimbCell(level, pos) || isClimbCell(level, pos.above()) ? pos : null;
            }
        }
        return null;
    }

    /** 从 from 能不能顺着梯子列直接滑到 to（中间每一格都在梯子列里且能通过）。 */
    public static boolean canSlideDownDirectly(BlockGetter level, BlockPos from, BlockPos to, int height) {
        if (to.getY() >= from.getY()) return false;
        if (from.getX() != to.getX() || from.getZ() != to.getZ()) return false;
        for (int y = from.getY() - 1; y > to.getY(); y--) {
            BlockPos pos = new BlockPos(from.getX(), y, from.getZ());
            if (!hasClearance(level, pos, height) || !isClimbCell(level, pos)) return false;
        }
        return true;
    }

    /** 这一节梯子往上最高数到哪一节（连续的可攀爬方块才算）。 */
    public static int columnTopY(BlockGetter level, BlockPos rung) {
        int top = rung.getY();
        for (int i = 1; i <= MAX_COLUMN_SCAN; i++) {
            if (!isRung(level, rung.above(i))) break;
            top = rung.getY() + i;
        }
        return top;
    }

    /** 这一节梯子往下最低数到哪一节。 */
    public static int columnBottomY(BlockGetter level, BlockPos rung) {
        int bottom = rung.getY();
        for (int i = 1; i <= MAX_COLUMN_SCAN; i++) {
            if (!isRung(level, rung.below(i))) break;
            bottom = rung.getY() - i;
        }
        return bottom;
    }

    /** 梯子这一列的顶端够不够得着目标高度（半截装饰梯不算上楼通道）。 */
    public static boolean columnReachesHeight(BlockGetter level, BlockPos rung, double goalY, double tolerance) {
        return goalY <= columnTopY(level, rung) + tolerance;
    }

    /** 单位所扒这一节梯子的开口方向（离开梯子薄板的那一侧）；不是梯子方块返回 null。 */
    @Nullable
    public static Direction openSide(BlockGetter level, BlockPos climbCell) {
        for (BlockPos pos : new BlockPos[] { climbCell, climbCell.below() }) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof LadderBlock) {
                return state.getValue(LadderBlock.FACING);
            }
        }
        return null;
    }
}
