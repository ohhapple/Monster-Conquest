package com.ohhapple.monster_conquest.ai.nav;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 军队单位的地面寻路：使用带梯子连接的节点评估，并在梯子列里按"真正到格"推进节点。
 *
 *   原版按水平距离推进节点（高度差 1 格内就算到达），挂在梯子上时会出现"人还在这一节、
 *   节点已经跳到上一节"，移动控制随后提前改向，和梯子物理互相顶。这里对梯子节点改为
 *   检查脚部高度，确实到了这一节才推进。
 *
 *   另外处理单格宽走廊的直角卡角：身体抵住内角时两个轴向都会被碰撞挡住，
 *   这里按进度判断，把单位挪回格中心或沿墙横挪一步。
 */
public class ClimbPathNavigation extends GroundPathNavigation {

    /** 贴着方块、位置几乎不动多少 tick 就横挪一步脱困。 */
    private static final int WEDGE_STALL_TICKS = 10;
    /** 朝着方块磨多久检查一次"有没有真的接近下一个节点"。 */
    private static final int WEDGE_PROGRESS_WINDOW = 20;
    /** 一个窗口内接近下一个节点不足这个距离（格）= 只是在原地磨。 */
    private static final double WEDGE_MIN_PROGRESS = 0.25;
    /** 判定"没动"的位移阈值（格）。 */
    private static final double WEDGE_MIN_MOVE = 0.02;
    /** 横挪步长候选（格）：先小后大。 */
    private static final double[] WEDGE_STEPS = {0.18, 0.3, 0.45};

    private int wedgeStallTicks;
    private double wedgeLastX = Double.NaN;
    private double wedgeLastZ = Double.NaN;
    private int wedgeWindowTicks;
    private double wedgeBestNodeDist = Double.MAX_VALUE;
    private BlockPos wedgeLastNode;

    public ClimbPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    /**
     * 重发一条节点相同的新路线时，先丢掉已经走完的旧路径。
     *   原版只用 {@link Path#sameAs(Path)} 判断是否换路，而它只比较节点，
     *   新路线会被当成"同一条路"而不安装：moveTo 返回 false，单位停在原地。
     */
    @Override
    public boolean moveTo(@Nullable Path path, double speedModifier) {
        Path current = this.path;
        if (path != null && current != null && current.isDone() && path.sameAs(current)) {
            this.path = null;
        }
        return super.moveTo(path, speedModifier);
    }

    @Override
    protected PathFinder createPathFinder(int maxPathLength) {
        this.nodeEvaluator = new ClimbNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxPathLength);
    }

    /** 挂在梯子上（既非落地也非水中）时也允许新建 / 更新路线。 */
    @Override
    protected boolean canUpdatePath() {
        if (super.canUpdatePath() || AiNavUtil.isOnClimbable(this.mob)) return true;
        // 正要迈进梯子列 / 从梯子上滑下来的那几 tick 里单位是悬空的：
        //   这里也要算"能推进路线"，否则会走原版的"空中落体推进节点"分支 ——
        //   那条分支按"同一格 + 目标在下方"直接推进节点，会把梯子的下一节提前吃掉。
        return climbNodePending();
    }

    @Override
    protected void followThePath() {
        this.maxDistanceToWaypoint = this.mob.getBbWidth() > 0.75F
                ? this.mob.getBbWidth() / 2.0F
                : 0.75F - this.mob.getBbWidth() / 2.0F;
        Vec3 pos = this.getTempMobPos();
        if (climbNodePending()) {
            Node next = this.path.getNextNode();
            double hx = Math.abs(this.mob.getX() - ((double) next.x + 0.5));
            double hz = Math.abs(this.mob.getZ() - ((double) next.z + 0.5));
            if (hx <= 0.45 && hz <= 0.45
                    && Math.abs(this.mob.getY() - (double) next.y) <= 0.4) {
                this.path.advance();
            }
            this.doStuckDetection(pos);
            return;
        }
        super.followThePath();
        unstuckFromCornerWedge();
    }

    /**
     * 卡墙角脱困：跟着路线走、贴着方块且连续 {@link #WEDGE_STALL_TICKS} 几乎没位移时，
     *   沿墙横挪一小步绕过墙角。候选落点必须先通过无碰撞检查，且尽量朝下一个节点方向挪。
     */
    private void unstuckFromCornerWedge() {
        Mob mob = this.mob;
        if (this.path == null || this.path.isDone()
                || !mob.horizontalCollision || !mob.onGround()
                || AiNavUtil.isOnClimbable(mob) || mob.isPassenger()) {
            resetWedgeTracking();
            return;
        }
        BlockPos node = this.path.getNextNodePos();
        if (!node.equals(wedgeLastNode)) {
            // 路径节点推进了 = 确实在前进，窗口重新计时。
            wedgeLastNode = node;
            wedgeWindowTicks = 0;
            wedgeBestNodeDist = Double.MAX_VALUE;
        }
        double nodeDist = Math.sqrt(mob.distanceToSqr(
                node.getX() + 0.5, mob.getY(), node.getZ() + 0.5));
        // 贴着方块磨蹭时位置会小幅摆动，"每 tick 都没动"判断不出来：
        //   按窗口看"到下一个节点的距离有没有真的缩短"，没缩短就是卡住了。
        boolean wedged;
        if (!Double.isNaN(wedgeLastX)
                && Math.abs(mob.getX() - wedgeLastX) < WEDGE_MIN_MOVE
                && Math.abs(mob.getZ() - wedgeLastZ) < WEDGE_MIN_MOVE) {
            wedgeStallTicks++;
        } else {
            wedgeStallTicks = 0;
        }
        wedgeLastX = mob.getX();
        wedgeLastZ = mob.getZ();
        if (wedgeStallTicks >= WEDGE_STALL_TICKS) {
            wedged = true;
        } else {
            if (nodeDist < wedgeBestNodeDist - WEDGE_MIN_PROGRESS) {
                wedgeWindowTicks = 0;
                wedgeBestNodeDist = nodeDist;
            } else {
                wedgeWindowTicks++;
                wedgeBestNodeDist = Math.min(wedgeBestNodeDist, nodeDist);
            }
            wedged = wedgeWindowTicks >= WEDGE_PROGRESS_WINDOW;
        }
        if (!wedged) return;
        wedgeStallTicks = 0;
        wedgeWindowTicks = 0;
        wedgeBestNodeDist = nodeDist;
        nudgeOffCorner(mob, node);
    }

    private void resetWedgeTracking() {
        wedgeStallTicks = 0;
        wedgeLastX = Double.NaN;
        wedgeLastZ = Double.NaN;
        wedgeWindowTicks = 0;
        wedgeBestNodeDist = Double.MAX_VALUE;
        wedgeLastNode = null;
    }

    /** 卡角后的脱困位移：优先回格中心，其次沿不同角度朝节点方向横挪，只走无碰撞落点。 */
    private void nudgeOffCorner(Mob mob, BlockPos node) {
        // 先试"回本格中心"：贴角蹭墙时身体其实压在方块角上，被碰撞反复顶回，
        //   回到格中心（走廊中心线）再朝节点走就不会挂角。
        BlockPos cell = mob.blockPosition();
        double cx = cell.getX() + 0.5;
        double cz = cell.getZ() + 0.5;
        if (mob.distanceToSqr(cx, mob.getY(), cz) > 0.01
                && this.level.noCollision(mob,
                        mob.getBoundingBox().move(cx - mob.getX(), 0.0, cz - mob.getZ()))) {
            mob.setPos(cx, mob.getY(), cz);
            return;
        }

        double dirX = node.getX() + 0.5 - mob.getX();
        double dirZ = node.getZ() + 0.5 - mob.getZ();
        double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (len < 1.0E-4) {
            dirX = 0.0;
            dirZ = 0.0;
        } else {
            dirX /= len;
            dirZ /= len;
        }
        // 先朝节点方向斜着挪，挪不动再左右绕：内墙角处朝目标直推是被挡死的那个方向。
        double[] angles = {0.0, Math.PI / 6.0, -Math.PI / 6.0, Math.PI / 3.0, -Math.PI / 3.0,
                Math.PI / 2.0, -Math.PI / 2.0, Math.PI};
        for (double step : WEDGE_STEPS) {
            for (double angle : angles) {
                double cos = Math.cos(angle);
                double sin = Math.sin(angle);
                double ox = (dirX * cos - dirZ * sin) * step;
                double oz = (dirX * sin + dirZ * cos) * step;
                double nx = mob.getX() + ox;
                double nz = mob.getZ() + oz;
                if (this.level.noCollision(mob, mob.getBoundingBox().move(ox, 0.0, oz))) {
                    mob.setPos(nx, mob.getY(), nz);
                    return;
                }
            }
        }
    }

    /**
     * 下一个路径节点是梯子格（梯子节 / 梯子活板门通道）时，推进要用更严的判定：
     *   必须真的到了这一格才推进。原版按水平距离推进，会在"人还在平台边、没进梯子列"时
     *   就把节点推到下面那一节 —— 移动控制器随后朝错误的落点驱动，单位从平台边掉到梯子外面。
     */
    private boolean climbNodePending() {
        if (this.path == null || this.path.isDone()) return false;
        Node next = this.path.getNextNode();
        return ClimbColumns.isClimbCell(this.level, new BlockPos(next.x, next.y, next.z));
    }
}
