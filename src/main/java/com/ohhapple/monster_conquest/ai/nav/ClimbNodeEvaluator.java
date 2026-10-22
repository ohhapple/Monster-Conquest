package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.jetbrains.annotations.Nullable;

/**
 * 带梯子连接的步行节点评估。
 *   原版把梯子格当作"没有落脚点的空格"，路线既进不了梯子列，也没法沿梯子上下。
 *   这里只补三种显式连接，并给它们加上比平地高的代价，保证能走平地就不绕梯子：
 *   同列逐节上下、梯子格与旁边可站平台的互通、沿列直接滑到下方第一个落点。
 *   梯子格本身仍不当作普通可行走格 —— 否则寻路会把它当平地，规划出横穿梯子列掉下去的
 *   路线。路线只负责"到哪一节"，真正的上下动作由 {@link ClimbMoveControl} 执行。
 */
public class ClimbNodeEvaluator extends WalkNodeEvaluator {

    /** 梯子节的代价：比平地贵一点。 */
    private static final float RUNG_MALUS = 1.0F;
    /** 进出平台的代价：进梯子是绕路手段，平地优先。 */
    private static final float PLATFORM_MALUS = 1.6F;
    /** 顺着梯子直接下滑的代价。 */
    private static final float SLIDE_MALUS = 1.2F;
    /** 翻上高一格的平台边缘（梯子最高一节比平台顶低一格）的代价。 */
    private static final float LEDGE_MALUS = 2.0F;
    /** 一次最多顺着梯子滑多少格（再长就逐节走，避免路径节点跨度过大）。 */
    private static final int SLIDE_LIMIT = 24;

    @Override
    public int getNeighbors(Node[] out, Node node) {
        int count = super.getNeighbors(out, node);
        BlockPos pos = new BlockPos(node.x, node.y, node.z);
        // 离梯子远的地方原版邻居就够：梯子判定只在梯子附近做，别给全图加开销。
        if (!ladderNearby(pos)) return count;
        int height = this.entityHeight;
        BlockGetter level = this.currentContext.level();

        if (ClimbColumns.isClimbCell(level, pos)) {
            count = addColumnNodes(out, count, node, pos, height);
            count = addLandingNodesAround(out, count, node, pos, height);
        }
        count = addRungNodesAround(out, count, node, pos, height);
        return count;
    }

    /**
     * 门的上半格不作为可走节点。
     *   原版会把门的上下两半都当成门格，路径偶尔给出"脚下一格、门上半格高一格"的节点；
     *   单位跟随时触发原版移动控制的"目标高一格 → 起跳"，表现为过门无故跳一下。
     *   只保留门下半格，跳跃自然消失。
     */
    @Override
    public PathType getPathType(PathfindingContext context, int x, int y, int z) {
        BlockState state = context.level().getBlockState(new BlockPos(x, y, z));
        if (state.getBlock() instanceof DoorBlock
                && state.hasProperty(DoorBlock.HALF)
                && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return PathType.BLOCKED;
        }
        return super.getPathType(context, x, y, z);
    }

    /**
     * 梯子格在原版里给不出起点（没有落脚点）。
     *   站在梯子上时用脚这一格或上下相邻格兜底，保证从梯子上也能重新规划。
     */
    @Override
    @Nullable
    public Node getStart() {
        BlockGetter level = this.currentContext.level();
        BlockPos feet = this.mob.blockPosition();
        if (this.mob.onClimbable()
                || ClimbColumns.isClimbCell(level, feet)
                || ClimbColumns.isClimbCell(level, feet.below())) {
            for (BlockPos cell : new BlockPos[] { feet, feet.above(), feet.below() }) {
                if (!this.canStartAt(cell)) continue;
                Node node = this.getStartNode(cell);
                if (ClimbColumns.isClimbCell(level, cell)) {
                    node.type = PathType.OPEN;
                    node.costMalus = RUNG_MALUS;
                }
                return node;
            }
        }
        return super.getStart();
    }

    /** 允许把梯子格（以及原版认可的普通起点）作为寻路起点。 */
    @Override
    protected boolean canStartAt(BlockPos pos) {
        if (ClimbColumns.isClimbCell(this.currentContext.level(), pos)) return true;
        return super.canStartAt(pos);
    }

    /** 梯子列里的竖直连接：逐节上下 + 顺着列直接滑到下方落点。 */
    private int addColumnNodes(Node[] out, int count, Node center, BlockPos pos, int height) {
        BlockGetter level = this.currentContext.level();
        for (int dy : new int[] { 1, -1 }) {
            BlockPos next = pos.above(dy);
            if (!ClimbColumns.isClimbCell(level, next)) continue;
            count = addNode(out, count, next, PathType.OPEN, RUNG_MALUS, height);
        }
        BlockPos landing = ClimbColumns.findSlideLanding(level, pos, height, SLIDE_LIMIT);
        if (landing != null) {
            count = addNode(out, count, landing, PathType.WALKABLE, SLIDE_MALUS, height);
        }
        return count;
    }

    /** 旁边 1 格内（含上下 1 格）的梯子节：从地面 / 平台走进梯子列。 */
    private int addRungNodesAround(Node[] out, int count, Node center, BlockPos pos, int height) {
        count = addRungNode(out, count, pos, height);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            // 往下多扫一格：从平台边缘走下来踩到低两格的梯子节上（落两格由梯子接住）
            for (int dy = -2; dy <= 1; dy++) {
                count = addRungNode(out, count, side.above(dy), height);
            }
        }
        return count;
    }

    private int addRungNode(Node[] out, int count, BlockPos cell, int height) {
        if (!ClimbColumns.isClimbCell(this.currentContext.level(), cell)) return count;
        return addNode(out, count, cell, PathType.OPEN, RUNG_MALUS, height);
    }

    /**
     * 梯子节旁边的落脚点：同层 / 低一层的平台，以及"旁一格是地板方块、站它上面一格"
     *   （梯子最高一节与平台地板同层时就是这种结构）。
     */
    private int addLandingNodesAround(Node[] out, int count, Node center, BlockPos pos, int height) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            count = addLandingNode(out, count, side, height);
            count = addLandingNode(out, count, side.above(), height);
            count = addLandingNode(out, count, side.below(), height);
            // 梯子最高一节比平台顶低一格：中间那一格是平台边缘方块，落脚点在它上面一格，
            //   攀爬时靠一次跳跨上去（见 ClimbMoveControl）。
            BlockPos ledge = side.above();
            BlockPos landing = side.above(2);
            if (this.currentContext.level().getBlockState(ledge).isSolid()
                    && ClimbColumns.isStandable(this.currentContext.level(), landing, height)) {
                count = addNode(out, count, landing, PathType.WALKABLE, LEDGE_MALUS, height);
            }
        }
        return count;
    }

    private int addLandingNode(Node[] out, int count, BlockPos cell, int height) {
        if (!ClimbColumns.isStandable(this.currentContext.level(), cell, height)) return count;
        return addNode(out, count, cell, PathType.WALKABLE, PLATFORM_MALUS, height);
    }

    /**
     * 登记一个额外邻居。节点对象按格子唯一，重复添加直接跳过；
     *   梯子格在原版里是"不可走"（代价 -1），这里补成可走代价。
     */
    private int addNode(Node[] out, int count, BlockPos cell, PathType type, float malus, int height) {
        if (count >= out.length) return count;
        if (!ClimbColumns.hasClearance(this.currentContext.level(), cell, height)) return count;
        Node node = this.getNode(cell.getX(), cell.getY(), cell.getZ());
        if (node == null || node.closed) return count;
        for (int i = 0; i < count; i++) {
            if (out[i] == node) return count;
        }
        if (node.costMalus < 0.0F) node.costMalus = malus;
        node.type = type;
        out[count++] = node;
        return count;
    }

    /** 身边 1 格内有没有跟梯子有关的格子：没有就不做任何梯子判定（省开销）。 */
    private boolean ladderNearby(BlockPos pos) {
        BlockGetter level = this.currentContext.level();
        if (ClimbColumns.isClimbCell(level, pos) || ClimbColumns.isClimbCell(level, pos.above())) {
            return true;
        }
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            if (ClimbColumns.isClimbCell(level, side) || ClimbColumns.isRung(level, side.above())) {
                return true;
            }
        }
        return false;
    }

}
