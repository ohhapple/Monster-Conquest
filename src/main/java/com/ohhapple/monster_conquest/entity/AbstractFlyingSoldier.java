package com.ohhapple.monster_conquest.entity;

import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.FlyingUnitMoveControl;
import com.ohhapple.monster_conquest.ai.nav.FlyingUnitPathNavigation;
import com.ohhapple.monster_conquest.ai.soldier.SoldierManualMoveGoal;
import com.ohhapple.monster_conquest.ai.soldier.FlyingCommandGoal;
import com.ohhapple.monster_conquest.ai.soldier.FlyingGroupFollowGoal;
import com.ohhapple.monster_conquest.ai.soldier.FlyingMeleeAttackGoal;
import com.ohhapple.monster_conquest.ai.soldier.FlyingWanderGoal;
import com.ohhapple.monster_conquest.ai.target.AttackConfiguredEnemyGoal;
import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 飞行单位基类。
 *
 * <p>该基类只负责飞行寻路、空中移动、飞行战斗与飞行指挥。
 * 是否为召唤物由 {@link SummonedUnitState} 独立决定，因此飞行单位可以普通生成，
 * 非飞行士兵也可以拥有召唤者绑定。</p>
 */
public abstract class AbstractFlyingSoldier extends AbstractArmySoldier {

    private static final EntityDataAccessor<Boolean> DATA_COMBAT_CHARGING =
            SynchedEntityData.defineId(AbstractFlyingSoldier.class, EntityDataSerializers.BOOLEAN);

    private static final int NAV_REISSUE_TICKS = 10;
    private static final int PATH_STALL_LIMIT_TICKS = 6;
    private static final int FOLLOW_TELEPORT_CHECK_TICKS = 20;
    private static final double FOLLOW_TELEPORT_MIN_DISTANCE = 3.0;
    private static final double PREFERRED_HOVER_HEIGHT = 2.0;
    private static final double DEFAULT_ATTACK_REACH = 1.45;
    private static final double FLYING_INPUT_SCALE = 0.1;
    private static final double MAX_COMBAT_ALTITUDE_GAIN = 100.0;
    private static final int FOLLOW_FORMATION_REFRESH_TICKS = 20;
    private static final int FOLLOW_SLOTS_PER_RING = 10;
    private static final double FOLLOW_RING_RADIUS = 3.4;
    private static final double FOLLOW_RING_SPACING = 2.1;
    private static final double FOLLOW_RING_HEIGHT_STEP = 0.35;
    private static final int FOLLOW_TRAVEL_COLUMNS = 5;
    private static final double FOLLOW_TRAVEL_LATERAL_SPACING = 2.5;
    private static final double FOLLOW_TRAVEL_FIRST_RANK = 2.8;
    private static final double FOLLOW_TRAVEL_RANK_SPACING = 2.4;
    private static final double FOLLOW_SEPARATION_RADIUS = 1.8;
    private static final double FOLLOW_SEPARATION_MAX = 1.2;

    @Nullable private Vec3 airDetourTarget;
    @Nullable private Vec3 airDetourGoal;
    private int airDetourUntil;
    private int navReissueTick;
    @Nullable private Vec3 queuedNavTarget;
    private int pathStallTicks;
    @Nullable private Vec3 lastPathNode;
    private double lastPathNodeDistSq = -1.0;
    @Nullable private UUID combatAltitudeTargetId;
    private double combatAltitudeBaseY;
    private double combatAltitudeCeilingY;
    private int followFormationIndex;
    private int followFormationSize = 1;
    private int followFormationRefreshTick;
    @Nullable private UUID followFormationOwnerId;
    @Nullable private UUID followFormationTargetId;
    private Vec3 followTravelDirection = Vec3.ZERO;
    private int followTravelDirectionUntil;
    private int stationFormationIndex;
    private int stationFormationSize = 1;
    private int stationFormationRefreshTick;
    @Nullable private UUID stationFormationOwnerId;
    private Vec3 stationFormationAnchor = Vec3.ZERO;

    protected AbstractFlyingSoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingUnitMoveControl(this, 20);
        this.setNoGravity(true);
        this.setCanPickUpLoot(false);
        this.setSoldierType(SoldierType.FLYING);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COMBAT_CHARGING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new SoldierManualMoveGoal(this));
        this.goalSelector.addGoal(2, new FlyingCommandGoal(this));
        this.goalSelector.addGoal(3, new FlyingMeleeAttackGoal(this));
        this.goalSelector.addGoal(6, new FlyingWanderGoal(this));
        this.goalSelector.addGoal(7, new FlyingGroupFollowGoal(this));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(0, new AttackConfiguredEnemyGoal(this));
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingUnitPathNavigation navigation =
                new FlyingUnitPathNavigation(this, level);
        navigation.setCanOpenDoors(false);
        navigation.setCanPassDoors(true);
        navigation.setCanFloat(true);
        return navigation;
    }

    @Override
    public boolean isFlyingUnit() {
        return true;
    }

    /**
     * 非玩家控制的飞行 Mob 默认固定使用 0.02 的飞行输入，
     *   会把 MoveControl 读取的 MOVEMENT_SPEED 完全忽略。
     *   本模组的飞行单位直接以自身 MOVEMENT_SPEED 作为飞行输入速度。
     */
    @Override
    protected float getFlyingSpeed() {
        return (float) getFlyingTravelSpeed();
    }

    public double getAppliedFlyingSpeed() {
        return getFlyingTravelSpeed();
    }

    /**
     * 与玩家飞行一致，把自身的 MOVEMENT_SPEED 乘以 0.1 转成飞行输入。
     * MoveControl 只提供方向；最终位移速度统一由这里决定，避免重复套用属性。
     */
    public double getFlyingTravelSpeed() {
        return Math.max(0.0, getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                * FLYING_INPUT_SCALE);
    }

    @Override
    protected int decreaseAirSupply(int airSupply) {
        return airSupply;
    }

    @Override
    public boolean canRide() {
        return false;
    }

    @Override
    protected boolean usesGroundArmyMovement() {
        return false;
    }

    /**
     * 飞行兵种可以覆盖装备规则；当前恼鬼、悦灵固定裸装并使用固定主手武器。
     */
    @Override
    public void applyEquipment(EquipmentTier tier) {
        super.applyEquipment(tier);
        if (usesArmyEquipment()) return;
        ItemStack weapon = createFlyingMainHandItem();
        this.setItemSlot(EquipmentSlot.MAINHAND,
                weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy());
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
        refreshDefaultGear();
    }

    /** 子兵种明确声明固定主手武器。 */
    protected ItemStack createFlyingMainHandItem() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canPickUpLoot() {
        return false;
    }

    /** 恼鬼覆盖为 true；悦灵保持 false，移动仍受方块碰撞限制。 */
    public boolean canPassThroughWalls() {
        return false;
    }

    public boolean isCombatCharging() {
        return this.entityData.get(DATA_COMBAT_CHARGING);
    }

    private void setCombatCharging(boolean charging) {
        if (!level().isClientSide) this.entityData.set(DATA_COMBAT_CHARGING, charging);
    }

    public void stopFlyingMovement() {
        airDetourTarget = null;
        airDetourGoal = null;
        queuedNavTarget = null;
        pathStallTicks = 0;
        lastPathNode = null;
        lastPathNodeDistSq = -1.0;
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        AiNavUtil.stopMovementInput(this);
    }

    /**
     * 以固定导航参数 1.0 将飞行单位送往目标点。
     *   有碰撞的悦灵在有视线时直飞，隔墙时交给飞行寻路绕行；
     *   可穿墙的恼鬼始终直接飞向目标。
     */
    public void moveTowardPoint(Vec3 target) {
        if (target == null) return;
        target = resolveOpenAirTarget(target);
        if (canPassThroughWalls()) {
            airDetourTarget = null;
            airDetourGoal = null;
            queuedNavTarget = null;
            this.getNavigation().stop();
            this.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.0D);
            return;
        }

        boolean direct = AiNavUtil.hasClearLineForBody(level(),
                position().add(0.0, getBbHeight() * 0.5, 0.0),
                target, getBbWidth() * 0.5 + 0.12);
        if (direct) {
            airDetourTarget = null;
            airDetourGoal = null;
            queuedNavTarget = null;
            this.getNavigation().stop();
            this.getMoveControl().setWantedPosition(target.x, target.y, target.z, 1.0D);
            return;
        }

        Vec3 pendingDetour = reusableAirDetour(target);
        if (pendingDetour != null) {
            this.getNavigation().stop();
            driveAirStep(pendingDetour);
            return;
        }

        PathNavigation navigation = this.getNavigation();
        boolean followingPath = navigation.getPath() != null && !navigation.isDone();
        if (followingPath) {
            Vec3 pathNode = navigation.getPath().getNextEntityPos(this);
            boolean pathNodeClear = AiNavUtil.hasClearLineForBody(
                    level(),
                    position().add(0.0, getBbHeight() * 0.5, 0.0),
                    pathNode,
                    getBbWidth() * 0.5 + 0.12);
            if (!pathNodeClear) {
                Vec3 cornerDetour = resolveAirCornerDetour(target, pathNode);
                if (cornerDetour != null) {
                    navigation.stop();
                    queuedNavTarget = null;
                    pathStallTicks = 0;
                    driveAirStep(cornerDetour);
                    return;
                }
            }
            if (lastPathNode == null || lastPathNode.distanceToSqr(pathNode) > 1.0) {
                lastPathNode = pathNode;
                lastPathNodeDistSq = position().distanceToSqr(pathNode);
                pathStallTicks = 0;
            }
            double nodeDistSq = position().distanceToSqr(pathNode);
            if (lastPathNodeDistSq < 0.0
                    || nodeDistSq < lastPathNodeDistSq - 0.0025) {
                pathStallTicks = 0;
            } else {
                pathStallTicks++;
            }
            lastPathNodeDistSq = nodeDistSq;
            if (horizontalCollision) pathStallTicks += 2;

            if (pathStallTicks >= PATH_STALL_LIMIT_TICKS) {
                Vec3 cornerDetour = resolveAirCornerDetour(target, pathNode);
                if (cornerDetour != null) {
                    navigation.stop();
                    queuedNavTarget = null;
                    pathStallTicks = 0;
                    driveAirStep(cornerDetour);
                    return;
                }
            }

            // 路线一旦安装就由导航持续拥有移动权。移动目标发生明显变化时才节流重发，
            // 不能每 tick 用墙角侧移把刚规划出的迷宫路线取消。
            if (queuedNavTarget != null
                    && queuedNavTarget.distanceToSqr(target) > 16.0
                    && tickCount >= navReissueTick) {
                issueAirPath(target);
            }
            return;
        }

        // 完整飞行寻路优先于局部侧移；它才能穿过多段墙和迷宫。
        if (issueAirPath(target)) return;

        Vec3 detour = resolveAirDetour(target);
        if (detour != null) {
            this.getNavigation().stop();
            driveAirStep(detour);
            return;
        }
        if (tickCount < navReissueTick) return;
        navReissueTick = tickCount + NAV_REISSUE_TICKS;
        this.getMoveControl().setWantedPosition(getX(), getY(), getZ(), 1.0D);
        navigation.moveTo(target.x, target.y, target.z, 1.0D);
    }

    private boolean issueAirPath(Vec3 target) {
        if (tickCount < navReissueTick) return false;
        PathNavigation navigation = this.getNavigation();
        navigation.stop();
        boolean issued = navigation.moveTo(target.x, target.y, target.z, 1.0D);
        if (!issued || navigation.getPath() == null) return false;
        queuedNavTarget = target;
        navReissueTick = tickCount + NAV_REISSUE_TICKS;
        pathStallTicks = 0;
        lastPathNode = null;
        lastPathNodeDistSq = -1.0;
        airDetourTarget = null;
        airDetourGoal = null;
        return true;
    }

    /** 战斗追击使用固定的初始高度包络，目标双方互相索敌时不会无限抬升。 */
    public void moveTowardCombatTarget(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        ensureCombatAltitude(target);

        double targetAimY = target.getY() + target.getBbHeight() * 0.5;
        double supportY = Math.max(
                supportYBelow(getX(), getZ(), Math.max(getY(), target.getY()) + 1.0),
                supportYBelow(target.getX(), target.getZ(),
                        Math.max(getY(), target.getY()) + 1.0));
        double minimumY = supportY + 0.08;
        double ceilingY = Math.max(combatAltitudeCeilingY, minimumY);
        double aimY = Math.max(minimumY, Math.min(targetAimY, ceilingY));
        moveTowardPoint(new Vec3(target.getX(), aimY, target.getZ()));
    }

    public void beginCombatWith(LivingEntity target) {
        combatAltitudeTargetId = null;
        ensureCombatAltitude(target);
    }

    public void clearCombatAltitude() {
        combatAltitudeTargetId = null;
        combatAltitudeBaseY = 0.0;
        combatAltitudeCeilingY = 0.0;
    }

    private void ensureCombatAltitude(LivingEntity target) {
        if (target == null) return;
        if (target.getUUID().equals(combatAltitudeTargetId)) return;
        combatAltitudeTargetId = target.getUUID();
        combatAltitudeBaseY = Math.max(getY(), target.getY());
        combatAltitudeCeilingY = combatAltitudeBaseY + MAX_COMBAT_ALTITUDE_GAIN;
    }

    private double supportYBelow(double x, double z, double fromY) {
        Vec3 from = new Vec3(x, fromY, z);
        Vec3 to = new Vec3(x, level().getMinBuildHeight(), z);
        BlockHitResult hit = level().clip(new ClipContext(
                from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.MISS
                ? level().getMinBuildHeight() : hit.getLocation().y;
    }

    /**
     * 命令点落进方块时，向上寻找最近的可容纳完整碰撞盒的空中格。
     *   恼鬼可以穿墙，但不能把“进入方块内部”当成正常停靠点。
     */
    private Vec3 resolveOpenAirTarget(Vec3 target) {
        if (isSafeTargetPoint(target)) return target;
        int baseX = (int) Math.floor(target.x);
        int baseY = (int) Math.floor(target.y);
        int baseZ = (int) Math.floor(target.z);
        for (int dy = 0; dy <= 12; dy++) {
            Vec3 candidate = new Vec3(baseX + 0.5, baseY + dy, baseZ + 0.5);
            if (isSafeTargetPoint(candidate)) return candidate;
        }
        return target;
    }

    /**
     * 跟随地面单位时的低空悬停点：优先在目标脚上方约 2 格，
     *   低顶环境下逐级降低，不会为了找空气一路飞到屋顶上方。
     */
    public Vec3 getHoverPointAbove(Vec3 anchor) {
        for (double height : new double[]{PREFERRED_HOVER_HEIGHT, 1.5, 2.5, 1.0, 3.0}) {
            Vec3 candidate = anchor.add(0.0, height, 0.0);
            if (isSafeTargetPoint(candidate)) return candidate;
        }
        return anchor.add(0.0, PREFERRED_HOVER_HEIGHT, 0.0);
    }

    /**
     * 玩家跟随落点使用稳定的空中分层阵位。
     *   同一跟随目标下的飞行单位按 UUID 排序占位，避免所有人都把“目标上方”
     *   当成同一点；每层最多十位，更多单位向外扩环并略微抬高。
     */
    public Vec3 getPlayerFollowPoint(LivingEntity target, @Nullable UUID followTargetId) {
        UUID ownerId = getCommanderUuid().orElse(null);
        UUID effectiveTargetId = followTargetId != null
                ? followTargetId : target.getUUID();
        refreshFollowFormation(target, ownerId, effectiveTargetId);
        Vec3 travelDirection = updateFollowTravelDirection(target);

        if (followFormationSize <= 1) {
            if (travelDirection.lengthSqr() < 0.5) {
                return getHoverPointAbove(target.position());
            }
            Vec3 center = target.position().subtract(
                    travelDirection.scale(FOLLOW_TRAVEL_FIRST_RANK));
            center = center.add(followSeparation(travelDirection));
            Vec3 safe = safeFollowPoint(center, PREFERRED_HOVER_HEIGHT);
            return safe != null ? safe : center.add(
                    0.0, PREFERRED_HOVER_HEIGHT, 0.0);
        }

        if (travelDirection.lengthSqr() >= 0.5) {
            int columns = Math.min(FOLLOW_TRAVEL_COLUMNS, followFormationSize);
            int rank = followFormationIndex / columns;
            int column = followFormationIndex % columns;
            int unitsInRank = Math.min(
                    columns, followFormationSize - rank * columns);
            Vec3 side = new Vec3(-travelDirection.z, 0.0, travelDirection.x);
            double lateral = (column - (unitsInRank - 1) * 0.5)
                    * FOLLOW_TRAVEL_LATERAL_SPACING;
            double behind = FOLLOW_TRAVEL_FIRST_RANK
                    + rank * FOLLOW_TRAVEL_RANK_SPACING;
            Vec3 center = target.position()
                    .add(side.scale(lateral))
                    .subtract(travelDirection.scale(behind))
                    .add(followSeparation(travelDirection));
            double height = PREFERRED_HOVER_HEIGHT
                    + Math.min(1.0, rank * 0.12);
            return safeFollowPoint(center, height);
        }

        int ring = followFormationIndex / FOLLOW_SLOTS_PER_RING;
        int column = followFormationIndex % FOLLOW_SLOTS_PER_RING;
        int slotsInRing = Math.min(
                FOLLOW_SLOTS_PER_RING,
                followFormationSize - ring * FOLLOW_SLOTS_PER_RING);
        // 世界坐标环不随玩家视角旋转。跟随对象转身时整圈阵位若一起旋转，
        // 两侧单位会交叉穿过中心，移动中最终收成一团。
        double angle = Math.PI * 0.5
                + Math.PI * 2.0 * column / slotsInRing;
        double radius = FOLLOW_RING_RADIUS + ring * FOLLOW_RING_SPACING;
        double height = PREFERRED_HOVER_HEIGHT + ring * FOLLOW_RING_HEIGHT_STEP;
        Vec3 center = target.position();

        Vec3 ideal = new Vec3(
                center.x + Math.cos(angle) * radius,
                center.y,
                center.z + Math.sin(angle) * radius);
        Vec3 safe = safeFollowPoint(ideal, height);
        if (safe != null) return safe;
        return new Vec3(
                center.x + Math.cos(angle) * radius,
                center.y + height,
                center.z + Math.sin(angle) * radius);
    }

    /** 测试 / 诊断：当前跟随编队序号与移动方向。 */
    public int getFollowFormationIndex() {
        return followFormationIndex;
    }

    public int getFollowFormationSize() {
        return followFormationSize;
    }

    public Vec3 getFollowTravelDirection() {
        return followTravelDirection;
    }

    /**
     * 玩家军队驻守时，同一编队围绕共同锚点使用空中阵位，而不是所有单位瞄准同一个点。
     */
    public Vec3 getStationFormationPoint(Vec3 anchor) {
        UUID ownerId = getCommanderUuid().orElse(null);
        refreshStationFormation(anchor, ownerId);
        if (stationFormationSize <= 1) {
            Vec3 safe = safeFollowPoint(anchor, 0.0);
            return safe != null ? safe : anchor;
        }

        int ring = stationFormationIndex / FOLLOW_SLOTS_PER_RING;
        int column = stationFormationIndex % FOLLOW_SLOTS_PER_RING;
        int slotsInRing = Math.min(
                FOLLOW_SLOTS_PER_RING,
                stationFormationSize - ring * FOLLOW_SLOTS_PER_RING);
        double angle = Math.PI * 0.5
                + Math.PI * 2.0 * column / slotsInRing;
        double radius = FOLLOW_RING_RADIUS + ring * FOLLOW_RING_SPACING;
        double height = PREFERRED_HOVER_HEIGHT + ring * FOLLOW_RING_HEIGHT_STEP;
        Vec3 ideal = new Vec3(
                anchor.x + Math.cos(angle) * radius,
                anchor.y,
                anchor.z + Math.sin(angle) * radius);
        Vec3 safe = safeFollowPoint(ideal, height);
        return safe != null ? safe : ideal.add(0.0, height, 0.0);
    }

    private void refreshStationFormation(Vec3 anchor, @Nullable UUID ownerId) {
        boolean commandChanged = (ownerId == null) != (stationFormationOwnerId == null)
                || (ownerId != null && !ownerId.equals(stationFormationOwnerId))
                || stationFormationAnchor.distanceToSqr(anchor) > 16.0;
        if (!commandChanged && tickCount < stationFormationRefreshTick) return;

        stationFormationOwnerId = ownerId;
        stationFormationAnchor = anchor;
        stationFormationRefreshTick = tickCount + FOLLOW_FORMATION_REFRESH_TICKS;
        stationFormationIndex = 0;
        stationFormationSize = 1;
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return;

        PlayerCommandState ownerState = PlayerArmyManager.getIfPresent(ownerId);
        if (ownerState == null) return;
        double scanRadius = Math.max(64.0, GeneralConfig.armyFollowRange * 2.0);
        List<AbstractFlyingSoldier> members = new ArrayList<>();
        collectStationFormationMembers(
                serverLevel, new AABB(
                        anchor.x - scanRadius, anchor.y - 32.0, anchor.z - scanRadius,
                        anchor.x + scanRadius, anchor.y + 32.0, anchor.z + scanRadius),
                ownerId, anchor, ownerState, members);
        collectStationFormationMembers(
                serverLevel, getBoundingBox().inflate(scanRadius),
                ownerId, anchor, ownerState, members);
        if (!members.contains(this)) members.add(this);
        members.sort(Comparator.comparing(unit -> unit.getUUID().toString()));

        stationFormationSize = members.size();
        int index = members.indexOf(this);
        stationFormationIndex = index < 0 ? 0 : index;
    }

    private static void collectStationFormationMembers(
            ServerLevel serverLevel, AABB scanBox, UUID ownerId, Vec3 anchor,
            PlayerCommandState ownerState,
            List<AbstractFlyingSoldier> members) {
        for (AbstractFlyingSoldier other : serverLevel.getEntitiesOfClass(
                AbstractFlyingSoldier.class, scanBox,
                candidate -> isStationedWith(
                        candidate, ownerId, anchor, ownerState))) {
            if (!members.contains(other)) members.add(other);
        }
    }

    private static boolean isStationedWith(
            AbstractFlyingSoldier other, UUID ownerId, Vec3 anchor,
            PlayerCommandState ownerState) {
        if (!other.isAlive() || other.isRemoved()
                || !ownerId.equals(other.getCommanderUuid().orElse(null))) {
            return false;
        }
        PlayerCommandState.SquadState command =
                PlayerCommandState.stateForCommand(other, ownerState);
        return command.getMovement() == PlayerCommandState.MovementMode.STATION
                && command.getFormationAnchor() != null
                && command.getFormationAnchor().distanceToSqr(anchor) <= 16.0;
    }

    private Vec3 updateFollowTravelDirection(LivingEntity target) {
        Vec3 movement = AiNavUtil.observedHorizontalMovement(target);
        if (movement.lengthSqr() > 4.0E-4) {
            Vec3 direction = movement.normalize();
            double dot = followTravelDirection.x * direction.x
                    + followTravelDirection.z * direction.z;
            if (followTravelDirection.lengthSqr() < 0.5 || dot < 0.0) {
                followTravelDirection = direction;
            } else {
                followTravelDirection = followTravelDirection.scale(0.8)
                        .add(direction.scale(0.2))
                        .normalize();
            }
            followTravelDirectionUntil = tickCount + 2;
        } else if (tickCount > followTravelDirectionUntil) {
            followTravelDirection = Vec3.ZERO;
        }
        return followTravelDirection;
    }

    /**
     * 移动编队只约束理想槽位还不够：速度饱和时多个单位会沿同一条追赶线重叠。
     *   对当前实际位置过近的同指挥官飞行单位施加有界水平排斥，保持槽位身份不变，
     *   同时让落后单位自然分散。
     */
    private Vec3 followSeparation(Vec3 travelDirection) {
        UUID ownerId = getCommanderUuid().orElse(null);
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) {
            return Vec3.ZERO;
        }
        Vec3 separation = Vec3.ZERO;
        double radiusSqr = FOLLOW_SEPARATION_RADIUS * FOLLOW_SEPARATION_RADIUS;
        for (AbstractFlyingSoldier other : serverLevel.getEntitiesOfClass(
                AbstractFlyingSoldier.class,
                getBoundingBox().inflate(FOLLOW_SEPARATION_RADIUS),
                candidate -> candidate != this
                        && candidate.isAlive()
                        && !candidate.isRemoved()
                        && ownerId.equals(candidate.getCommanderUuid().orElse(null)))) {
            Vec3 away = position().subtract(other.position());
            away = new Vec3(away.x, 0.0, away.z);
            double distanceSqr = away.lengthSqr();
            if (distanceSqr >= radiusSqr) continue;
            if (distanceSqr < 1.0E-6) {
                Vec3 side = new Vec3(-travelDirection.z, 0.0, travelDirection.x);
                away = side.scale(getId() > other.getId() ? 1.0 : -1.0);
                distanceSqr = away.lengthSqr();
            }
            double distance = Math.sqrt(distanceSqr);
            double strength = (FOLLOW_SEPARATION_RADIUS - distance)
                    / FOLLOW_SEPARATION_RADIUS;
            separation = separation.add(away.scale(strength / distance));
        }
        if (separation.lengthSqr() > FOLLOW_SEPARATION_MAX * FOLLOW_SEPARATION_MAX) {
            separation = separation.normalize().scale(FOLLOW_SEPARATION_MAX);
        }
        return separation;
    }

    @Nullable
    private Vec3 safeFollowPoint(Vec3 horizontalCenter, double height) {
        // 低顶空间优先降低悬停高度；仍不安全时交给 moveTowardPoint 做完整碰撞盒修正。
        for (double candidateHeight : new double[]{
                height, 1.5, 1.0, height + 0.5, 3.0}) {
            Vec3 candidate = new Vec3(
                    horizontalCenter.x,
                    horizontalCenter.y + candidateHeight,
                    horizontalCenter.z);
            if (isSafeTargetPoint(candidate)) return candidate;
        }
        return null;
    }

    private void refreshFollowFormation(LivingEntity target, @Nullable UUID ownerId,
                                        UUID targetId) {
        boolean commandChanged = !targetId.equals(followFormationTargetId)
                || (ownerId == null) != (followFormationOwnerId == null)
                || (ownerId != null && !ownerId.equals(followFormationOwnerId));
        if (!commandChanged && tickCount < followFormationRefreshTick) return;

        if (commandChanged) {
            followTravelDirection = Vec3.ZERO;
            followTravelDirectionUntil = 0;
        }
        followFormationOwnerId = ownerId;
        followFormationTargetId = targetId;
        followFormationRefreshTick = tickCount + FOLLOW_FORMATION_REFRESH_TICKS;
        followFormationIndex = 0;
        followFormationSize = 1;
        if (ownerId == null || !(level() instanceof ServerLevel serverLevel)) return;

        PlayerCommandState ownerState = PlayerArmyManager.getIfPresent(ownerId);
        if (ownerState == null) return;
        double scanRadius = Math.max(64.0, GeneralConfig.armyFollowRange * 2.0);
        List<AbstractFlyingSoldier> members = new ArrayList<>();
        collectFollowFormationMembers(
                serverLevel, target.getBoundingBox().inflate(scanRadius),
                ownerId, targetId, ownerState, members);
        collectFollowFormationMembers(
                serverLevel, getBoundingBox().inflate(scanRadius),
                ownerId, targetId, ownerState, members);
        if (!members.contains(this)) members.add(this);
        members.sort(Comparator.comparing(unit -> unit.getUUID().toString()));

        followFormationSize = members.size();
        int index = members.indexOf(this);
        followFormationIndex = index < 0 ? 0 : index;
    }

    private void collectFollowFormationMembers(ServerLevel serverLevel, AABB scanBox,
                                               UUID ownerId, UUID targetId,
                                               PlayerCommandState ownerState,
                                               List<AbstractFlyingSoldier> members) {
        for (AbstractFlyingSoldier other : serverLevel.getEntitiesOfClass(
                AbstractFlyingSoldier.class, scanBox,
                candidate -> isFollowingSameTarget(
                        candidate, ownerId, targetId, ownerState))) {
            if (!members.contains(other)) members.add(other);
        }
    }

    private static boolean isFollowingSameTarget(
            AbstractFlyingSoldier other, UUID ownerId, UUID targetId,
            PlayerCommandState ownerState) {
        if (!other.isAlive() || other.isRemoved()
                || !ownerId.equals(other.getCommanderUuid().orElse(null))) {
            return false;
        }
        PlayerCommandState.SquadState command =
                PlayerCommandState.stateForCommand(other, ownerState);
        PlayerCommandState.MovementMode movement = command.getMovement();
        if (movement != PlayerCommandState.MovementMode.FOLLOW
                && movement != PlayerCommandState.MovementMode.SOFT_FOLLOW) {
            return false;
        }
        UUID commandedTarget = command.getFollowTargetUuid();
        return targetId.equals(commandedTarget == null ? ownerId : commandedTarget);
    }

    /** 子兵种可覆盖近战挥砍中心距离。 */
    public double getFlyingAttackReach() {
        return DEFAULT_ATTACK_REACH;
    }

    public double getFlyingAttackStopDistance() {
        return getFlyingAttackReach() * 0.7;
    }

    private boolean isSafeTargetPoint(Vec3 target) {
        BlockPos pos = BlockPos.containing(target);
        if (!level().isLoaded(pos)) return false;
        AABB moved = getBoundingBox().move(
                target.x - getX(), target.y - getY(), target.z - getZ());
        if (!level().noCollision(this, moved)) return false;
        return level().getBlockStates(moved).noneMatch(state ->
                !state.getFluidState().isEmpty() || AiNavUtil.isDangerousBlock(state));
    }

    /**
     * 飞行节点的中心线会擦进墙角，实际碰撞盒却过不去。
     *   直线受阻时优先找一条“先沿墙面横向离开，再继续目标方向”的空中落脚点；
     *   候选必须整盒无碰撞且到当前位置的身体宽度直线通畅。
     */
    @Nullable
    private Vec3 resolveAirDetour(Vec3 target) {
        if (canPassThroughWalls()) return null;
        Vec3 delta = target.subtract(position());
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontal < 0.5) return null;

        Vec3 reusable = reusableAirDetour(target);
        if (reusable != null) return reusable;

        double dirX = delta.x / horizontal;
        double dirZ = delta.z / horizontal;
        double sideX = -dirZ;
        double sideZ = dirX;
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;

        for (double side : new double[]{3.0, 5.0, 7.0, -3.0, -5.0, -7.0}) {
            double x = getX() + sideX * side;
            double z = getZ() + sideZ * side;
                for (double dy : new double[]{0.0, 2.0, 3.5, 5.0, -1.5, 7.0}) {
                Vec3 candidate = new Vec3(x, getY() + dy, z);
                BlockPos pos = BlockPos.containing(candidate);
                if (!level().isLoaded(pos) || !level().isEmptyBlock(pos)) continue;
                AABB moved = getBoundingBox().move(
                        candidate.x - getX(), candidate.y - getY(), candidate.z - getZ());
                if (!level().noCollision(this, moved)) continue;
                if (!AiNavUtil.hasClearLineForBody(level(),
                        position().add(0.0, getBbHeight() * 0.5, 0.0),
                        candidate, getBbWidth() * 0.5 + 0.12)) {
                    continue;
                }
                double score = candidate.distanceToSqr(target) + Math.abs(side) * 0.5;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        }

        if (best != null) {
            airDetourTarget = best;
            airDetourGoal = target;
            airDetourUntil = tickCount + 60;
        }
        return best;
    }

    @Nullable
    private Vec3 reusableAirDetour(Vec3 target) {
        if (airDetourTarget == null) return null;
        boolean expired = tickCount >= airDetourUntil;
        boolean targetChanged = airDetourGoal == null
                || airDetourGoal.distanceToSqr(target) > 16.0;
        double detourDistanceSqr = position().distanceToSqr(airDetourTarget);
        boolean stillClear = AiNavUtil.hasClearLineForBody(level(),
                position().add(0.0, getBbHeight() * 0.5, 0.0),
                airDetourTarget, getBbWidth() * 0.5 + 0.12);
        if (!expired && !targetChanged && detourDistanceSqr > 0.5 * 0.5 && stillClear) {
            return airDetourTarget;
        }
        airDetourTarget = null;
        airDetourGoal = null;
        return null;
    }

    /**
     * 原版飞行节点以格中心连接，可能让宽碰撞盒斜切墙角。
     *   路径连续无法产生水平位移时，在目标方向两侧尝试轴对齐的空中落脚点，
     *   先离开墙角再重新寻路。
     */
    @Nullable
    private Vec3 resolveAirCornerDetour(Vec3 finalTarget, Vec3 pathNode) {
        Vec3 origin = position();
        Vec3 fromCenter = origin.add(0.0, getBbHeight() * 0.5, 0.0);
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        double[] verticalOffsets = {0.0, 0.8, -0.8, 1.5, -1.5, 2.5, 4.0};
        double[] radii = {2.0, 3.0, 4.5, 6.0};
        int[][] directions = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (double radius : radii) {
            for (int[] dir : directions) {
                double x = getX() + dir[0] * radius;
                double z = getZ() + dir[1] * radius;
                for (double dy : verticalOffsets) {
                    Vec3 candidate = new Vec3(x, pathNode.y + dy, z);
                    if (!isSafeTargetPoint(candidate)) continue;
                    if (!AiNavUtil.hasClearLineForBody(level(), fromCenter, candidate,
                            getBbWidth() * 0.5 + 0.12)) {
                        continue;
                    }
                    double score = candidate.distanceToSqr(pathNode)
                            + candidate.distanceToSqr(finalTarget) * 0.15
                            + Math.abs(dy) * 0.5;
                    if (score < bestScore) {
                        bestScore = score;
                        best = candidate;
                    }
                }
            }
        }

        if (best != null) {
            airDetourTarget = best;
            airDetourGoal = finalTarget;
            airDetourUntil = tickCount + 80;
        }
        return best;
    }

    private void driveAirStep(Vec3 target) {
        Vec3 delta = target.subtract(position());
        double length = delta.length();
        if (length < 1.0E-4) return;
        double speed = getFlyingTravelSpeed();
        Vec3 step = delta.scale(Math.min(speed, length) / length);
        AiNavUtil.stopMovementInput(this);
        float yaw = (float) Math.toDegrees(Math.atan2(-step.x, step.z));
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.setYHeadRot(yaw);
        this.setDeltaMovement(Vec3.ZERO);
        this.move(MoverType.SELF, step);
        this.setDeltaMovement(Vec3.ZERO);
        this.hasImpulse = true;
    }

    /** 选择一个锚点附近、不会钻进方块或地面的悬停点。 */
    @Nullable
    public Vec3 chooseWanderPoint(Vec3 anchor) {
        for (int attempt = 0; attempt < 28; attempt++) {
            double angle = getRandom().nextDouble() * Math.PI * 2.0;
            double radius = 2.0 + getRandom().nextDouble() * 4.0;
            double x = anchor.x + Math.cos(angle) * radius;
            double z = anchor.z + Math.sin(angle) * radius;
            double y = anchor.y + 0.15 + getRandom().nextDouble() * 2.85;
            y = Math.max(y, level().getMinBuildHeight() + 1.0);
            y = Math.max(y, getY() - 1.5);

            BlockPos pos = BlockPos.containing(x, y, z);
            if (!level().isLoaded(pos) || !level().isEmptyBlock(pos)) continue;
            AABB movedBox = getBoundingBox().move(
                    x - getX(), y - getY(), z - getZ());
            if (!level().noCollision(this, movedBox)) continue;
            return new Vec3(x, y, z);
        }
        return null;
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            setCombatCharging(getTarget() != null && getTarget().isAlive());
            if (!isSummoned() && !hasSummonRoamAnchor()) setSummonRoamAnchor(position());
        }

        setNoGravity(true);
        super.tick();
        setNoGravity(true);
        this.fallDistance = 0.0f;
        if (!level().isClientSide) tickPlayerFollowTeleport();
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        this.fallDistance = 0.0f;
        return false;
    }

    /**
     * 飞行编队和召唤物会在移动 / 传送时短暂共享空间。
     *   原版挤压伤害按附近可推实体数量结算，不会调用同阵营跳过的 {@link #push(Entity)}；
     *   这里保留实际推挤，但移除仅针对飞行单位的 cramming 伤害。
     */
    @Override
    protected void pushEntities() {
        if (level().isClientSide) {
            super.pushEntities();
            return;
        }
        if (isMovingFormationActive()) {
            double separationRadius = 1.4;
            AABB separationBox = new AABB(
                    getX() - separationRadius, getY() - 1.5, getZ() - separationRadius,
                    getX() + separationRadius, getY() + 1.5, getZ() + separationRadius);
            for (AbstractFlyingSoldier other : level().getEntitiesOfClass(
                    AbstractFlyingSoldier.class, separationBox,
                    candidate -> candidate != this
                            && candidate.isAlive()
                            && !candidate.isRemoved()
                            && isSameFlyingCommander(candidate))) {
                pushFlyingApart(other);
            }
        }
        for (Entity entity : level().getEntities(
                this, getBoundingBox(), EntitySelector.pushableBy(this))) {
            doPush(entity);
        }
    }

    /**
     * 同阵营地面单位保持可穿行，但同一指挥官的飞行编队需要物理分离兜底。
     *   当导航速度饱和、多个飞行单位追同一条路线时，仅有不同的目标点仍可能重叠。
     */
    @Override
    public void push(Entity entity) {
        if (entity instanceof AbstractFlyingSoldier other
                && isMovingFormationActive()
                && isSameFlyingCommander(other)) {
            pushFlyingApart(other);
            return;
        }
        super.push(entity);
    }

    private boolean isMovingFormationActive() {
        return followTravelDirection.lengthSqr() >= 0.5;
    }

    private boolean isSameFlyingCommander(AbstractFlyingSoldier other) {
        UUID owner = getCommanderUuid().orElse(null);
        return owner != null && owner.equals(other.getCommanderUuid().orElse(null));
    }

    private void pushFlyingApart(AbstractFlyingSoldier other) {
        if (isPassengerOfSameVehicle(other)) return;
        double dx = other.getX() - getX();
        double dz = other.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.01) {
            double direction = getId() > other.getId() ? 1.0 : -1.0;
            dx = direction;
            dz = 0.0;
            distance = 1.0;
        }
        double dirX = dx / distance;
        double dirZ = dz / distance;
        if (!isVehicle()) push(-dirX * 0.06, 0.0, -dirZ * 0.06);
        if (!other.isVehicle()) other.push(dirX * 0.06, 0.0, dirZ * 0.06);

        double minimum = 0.9;
        if (distance >= minimum) return;
        double correction = Math.min(0.12, (minimum - distance) * 0.3) * 0.5;
        move(MoverType.SELF, new Vec3(-dirX * correction, 0.0, -dirZ * correction));
        other.move(MoverType.SELF, new Vec3(dirX * correction, 0.0, dirZ * correction));
    }

    /**
     * 硬跟随使用与普通玩家士兵相同的距离阈值；软跟随仍会在战斗或共享仇恨期间让位。
     *   飞行落点必须是完整碰撞盒可容纳的无液体空气，避免悦灵嵌墙或恼鬼遁地。
     */
    private void tickPlayerFollowTeleport() {
        if (!isPlayerArmy() || !isPlayerCommanded()
                || armyAge % FOLLOW_TELEPORT_CHECK_TICKS != 0) {
            return;
        }

        UUID ownerUuid = getCommanderUuid().orElse(null);
        if (ownerUuid == null) return;
        PlayerCommandState state = PlayerArmyManager.getIfPresent(ownerUuid);
        if (state == null) return;

        PlayerCommandState.SquadState command =
                PlayerCommandState.stateForCommand(this, state);
        PlayerCommandState.MovementMode movement = command.getMovement();
        if (movement != PlayerCommandState.MovementMode.FOLLOW
                && movement != PlayerCommandState.MovementMode.SOFT_FOLLOW) {
            return;
        }

        LivingEntity followTarget = resolveCommandFollowTarget(command);
        if (followTarget == null || followTarget.level() != level()) return;

        boolean inCombat = getTarget() != null && getTarget().isAlive();
        if (movement == PlayerCommandState.MovementMode.SOFT_FOLLOW) {
            state.tickSharedHatred(level().getGameTime());
            state.tickSharedHatredEntities(level().getGameTime());
            boolean hasSharedHatred = !state.getSharedHatredArmies().isEmpty()
                    || !state.getSharedHatredEntities().isEmpty();
            if (inCombat || hasSharedHatred) return;
            if (distanceToSqr(followTarget)
                    > PlayerConfig.playerSoldierTeleportDistance
                    * PlayerConfig.playerSoldierTeleportDistance) {
                teleportNearSafelyInAir(
                        followTarget, FOLLOW_TELEPORT_MIN_DISTANCE,
                        getPlayerFollowPoint(followTarget, followTarget.getUUID()));
            }
            return;
        }

        double teleportDistance = inCombat
                ? PlayerConfig.playerSoldierCombatTeleportDistance
                : PlayerConfig.playerSoldierTeleportDistance;
        if (distanceToSqr(followTarget) <= teleportDistance * teleportDistance) return;
        if (inCombat && !isStuckEnoughToTeleport()) return;
        teleportNearSafelyInAir(
                followTarget, FOLLOW_TELEPORT_MIN_DISTANCE,
                getPlayerFollowPoint(followTarget, followTarget.getUUID()));
    }

    @Nullable
    private LivingEntity resolveCommandFollowTarget(
            PlayerCommandState.SquadState command) {
        if (!(level() instanceof ServerLevel serverLevel)) return null;

        UUID followUuid = command.getFollowTargetUuid();
        if (followUuid != null) {
            Entity entity = serverLevel.getEntities().get(followUuid);
            if (entity instanceof LivingEntity living
                    && living != this && living.isAlive()) {
                return living;
            }
        }

        UUID ownerUuid = getCommanderUuid().orElse(null);
        return ownerUuid == null ? null
                : serverLevel.getServer().getPlayerList().getPlayer(ownerUuid);
    }

    /**
     * 在跟随目标同一空气空间中找完整碰撞盒可容纳的悬停点。
     *   候选必须与目标互相可见，避免跨墙传送到隔壁房间；找不到就等待下一次检查。
     */
    private boolean teleportNearSafelyInAir(LivingEntity around, double minDistance,
                                            Vec3 preferred) {
        Vec3 safe = findSafeAirSpotNear(around, minDistance, preferred);
        if (safe == null) return false;
        teleportWithMount(safe.x, safe.y, safe.z, false);
        return true;
    }

    @Nullable
    private Vec3 findSafeAirSpotNear(LivingEntity around, double minDistance,
                                     @Nullable Vec3 preferred) {
        int baseX = (int) Math.floor(around.getX());
        int baseY = (int) Math.floor(around.getY());
        int baseZ = (int) Math.floor(around.getZ());
        Vec3 targetCenter = around.position().add(
                0.0, around.getBbHeight() * 0.5, 0.0);
        double minSqr = minDistance * minDistance;
        if (preferred != null
                && isSafeAirSpot(preferred)
                && AiNavUtil.hasClearLineForBody(level(),
                preferred.add(0.0, getBbHeight() * 0.5, 0.0),
                targetCenter, getBbWidth() * 0.5 + 0.12)) {
            return preferred;
        }

        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int radius = 2; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    if ((double) dx * dx + (double) dz * dz <= minSqr) continue;
                    for (int dy : new int[]{2, 3, 1, 4, 0, -1}) {
                        Vec3 candidate = new Vec3(
                                baseX + dx + 0.5, baseY + dy, baseZ + dz + 0.5);
                        if (!isSafeAirSpot(candidate)) continue;
                        Vec3 candidateCenter = candidate.add(
                                0.0, getBbHeight() * 0.5, 0.0);
                        if (!AiNavUtil.hasClearLineForBody(level(),
                                candidateCenter, targetCenter,
                                getBbWidth() * 0.5 + 0.12)) {
                            continue;
                        }
                        double score = preferred != null
                                ? candidate.distanceToSqr(preferred)
                                : candidate.distanceToSqr(around.position());
                        if (score < bestScore) {
                            best = candidate;
                            bestScore = score;
                        }
                    }
                }
            }
        }
        return best;
    }

    private boolean isSafeAirSpot(Vec3 candidate) {
        BlockPos pos = BlockPos.containing(candidate);
        if (!level().isLoaded(pos)) return false;
        if (candidate.y <= level().getMinBuildHeight()
                || candidate.y + getBbHeight() >= level().getMaxBuildHeight()) {
            return false;
        }

        AABB moved = getBoundingBox().move(
                candidate.x - getX(), candidate.y - getY(), candidate.z - getZ());
        if (!level().noCollision(this, moved)) return false;
        return level().getBlockStates(moved).noneMatch(state ->
                !state.getFluidState().isEmpty() || AiNavUtil.isDangerousBlock(state));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (isAttackStopped() || !(target instanceof LivingEntity living)) return false;
        double reach = getFlyingAttackReach();
        if (distanceToSqr(living) > reach * reach) return false;
        double maxDy = canPassThroughWalls() ? 3.0 : 1.5;
        if (Math.abs(getY() - living.getY()) > maxDy) return false;
        if (!canPassThroughWalls() && !hasMeleeLineOfSight(living)) return false;

        DamageSource source = meleeDamageSource();
        boolean hit = living.hurt(source, (float) getAttributeValue(
                net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
        if (!hit) return false;

        markAttackSuccess();
        setLastHurtMob(living);
        applyMeleeEffects(living);
        if (!level().isClientSide) ThreatDispatcher.propagate(living, this);
        return true;
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        // 固定装备模板由当前兵种重新写入，避免 NBT 直接生成的飞行单位短暂空手。
        if (!usesArmyEquipment()) applyEquipment(getTier());
    }

}
