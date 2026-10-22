package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.FlyNodeEvaluator;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;

/**
 * 三维空中导航。
 *
 * <p>原版 FlyingPathNavigation 仍要求终点“像地面一样可站立”，因此命令点位于
 * 半空时会被判成不可达。飞行单位需要把无碰撞、无液体的空中格视为稳定终点，
 * 同时继续使用 FlyNodeEvaluator 和实体碰撞盒，保证悦灵不能穿墙。</p>
 */
public class FlyingUnitPathNavigation extends FlyingPathNavigation {

    public FlyingUnitPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new FullBodyFlyNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    @Override
    public boolean isStableDestination(BlockPos pos) {
        BlockState state = this.level.getBlockState(pos);
        if (!state.getCollisionShape(this.level, pos).isEmpty()
                || !state.getFluidState().isEmpty()
                || AiNavUtil.isDangerousBlock(state)) {
            return false;
        }
        AABB moved = this.mob.getBoundingBox().move(
                pos.getX() + 0.5 - this.mob.getX(),
                pos.getY() - this.mob.getY(),
                pos.getZ() + 0.5 - this.mob.getZ());
        return this.level.noCollision(this.mob, moved)
                && this.level.getBlockStates(moved).noneMatch(candidate ->
                !candidate.getFluidState().isEmpty() || AiNavUtil.isDangerousBlock(candidate));
    }

    /**
     * 原版飞行节点可能只按格中心 / 方块形状判定通行，宽碰撞盒会擦进墙角或地板。
     *   本模组所有有碰撞飞行导航都以实体完整碰撞盒作为节点可通行条件。
     */
    private final class FullBodyFlyNodeEvaluator extends FlyNodeEvaluator {
        @Override
        protected PathType getCachedPathType(int x, int y, int z) {
            PathType base = super.getCachedPathType(x, y, z);
            if (base == PathType.BLOCKED) return base;
            AABB moved = mob.getBoundingBox().move(
                    x + 0.5 - mob.getX(),
                    y - mob.getY(),
                    z + 0.5 - mob.getZ());
            return level.noCollision(mob, moved) ? base : PathType.BLOCKED;
        }
    }
}
