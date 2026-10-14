package com.ohhapple.monster_conquest.entity;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.BallisticAim;
import com.ohhapple.monster_conquest.ai.nav.GunAim;
import com.ohhapple.monster_conquest.ai.nav.LancePierce;
import com.ohhapple.monster_conquest.ai.nav.RoutePlanner;
import com.ohhapple.monster_conquest.ai.soldier.*;
import com.ohhapple.monster_conquest.ai.target.AttackConfiguredEnemyGoal;
import com.ohhapple.monster_conquest.army.*;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.relation.NativePeace;
import com.ohhapple.monster_conquest.army.FactionShield;
import com.ohhapple.monster_conquest.army.threat.ArmyThreatUtil;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.entity.projectile.ArmyArrow;
import com.ohhapple.monster_conquest.entity.soldiers.EvokerSoldier;
import com.ohhapple.monster_conquest.gun.GunSpec;
import com.ohhapple.monster_conquest.item.LanceItem;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.PeacefulUtil;
import com.ohhapple.monster_conquest.world.ArmyChunkLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class AbstractArmySoldier extends PathfinderMob
        implements RangedAttackMob, ArmyAnchor.AnchoredUnit, ManagedUnit {

    protected static final EntityDataAccessor<Optional<UUID>> DATA_COMMANDER =
            SynchedEntityData.defineId(AbstractArmySoldier.class, EntityDataSerializers.OPTIONAL_UUID);
    protected static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(AbstractArmySoldier.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<Integer> DATA_SOLDIER_TYPE =
            SynchedEntityData.defineId(AbstractArmySoldier.class, EntityDataSerializers.INT);
    protected static final EntityDataAccessor<String> DATA_FACTION =
            SynchedEntityData.defineId(AbstractArmySoldier.class, EntityDataSerializers.STRING);

    protected static final EntityDataAccessor<Integer> DATA_SQUAD_ID =
            SynchedEntityData.defineId(AbstractArmySoldier.class, EntityDataSerializers.INT);

    /** 手动装备掩码（按位对应 6 个手动装备栏）：客户端渲染阵营图案时要知道哪些部位被玩家装备盖住了。 */
    protected static final EntityDataAccessor<Byte> DATA_MANUAL_GEAR =
            SynchedEntityData.defineId(AbstractArmySoldier.class, EntityDataSerializers.BYTE);

    /**
     * 单位锚点：随村庄刷出的巡逻队 / 随袭击刷出的增援才带锚点，
     *   锚点限制巡逻、战斗与索敌的活动范围；普通单位（刷怪蛋 / 指令 / 招募）无锚点。
     */
    private final ArmyAnchor anchor = new ArmyAnchor();
    /** 锚点拉回动作的冷却（tick），避免每 tick 重复寻路。 */
    private int anchorLeashCooldown = 0;

    @Override
    public ArmyAnchor getAnchor() { return anchor; }

    protected int armyAge = 0;
    protected int formCheckCooldown = 0;
    protected int lastHurtTick = -10000;
    protected double lastChargeSpeed = 0;

    private int stuckCheckTimer = 0;
    private double lastStuckX, lastStuckY, lastStuckZ;
    private int stuckAccumTicks = 0;
    /** "在动但没接近目标"（绕路 / 来回徘徊 / 被同伴挤开）的累计时间。 */
    private int wanderAccumTicks = 0;
    /** "到目标完全没有可行路线"的累计时间：即使一直在动（例如掉坑里左右徘徊）也会累计。 */
    private int noPathAccumTicks = 0;
    /** 上次探测"到目标有没有路"的 tick（限流，避免频繁 A* 规划拖慢服务器）。 */
    private int lastPathProbeTick = -10000;
    /** "寻路不通"被暂时拉黑的目标。 */
    @Nullable private java.util.UUID unreachableTargetId = null;
    private int unreachableTargetUntil = 0;
    /** 可达性探测缓存：同一目标最近确认过"完全不可达"时，这段时间内不再重复跑 A*。 */
    @Nullable private java.util.UUID reachProbeMemoId = null;
    private int reachProbeMemoUntil = 0;
    /** 同一目标的战斗追击重规划节流：失败按次数指数退避，避免对着打不到的敌人反复狂算。 */
    @Nullable private java.util.UUID lastReplanTargetId = null;
    private int lastReplanTick = -10000;
    private int replanRetryTicks = REPLAN_MIN_RETRY_TICKS;
    /** 掉队回巢的完整规划节流（跟随首领时用；原版说没路才轮到自研 A*）。 */
    @Nullable private java.util.UUID lastLeashRouteTargetId = null;
    private int lastLeashRouteTick = -10000;
    /** 本检查窗口（40 tick）内实际走过的水平距离（每 tick 累加，忽略传送级跳变）。 */
    private double traveledSinceCheck = 0;
    private double lastTravelX, lastTravelZ;

    /**
     * 因地形（窄道 / 低矮通道）主动下马后的冷却（tick）。
     *   冷却期内不自动重新上马，避免站在窄道口"下马→上马→再下马"来回循环。
     */
    private int terrainDismountCooldown = 0;

    /**
     * 40 tick 窗口内实际移动少于该距离（格）→ 判定为"真的卡住没动"（撞墙 / 被地形卡住）。
     *   移动超过该值但净进展不足 → 归类为"在动但没接近"（绕路 / 来回徘徊）。
     */
    private static final double STUCK_TRAVEL_EPSILON = 2.0;
    /** "在动但没接近目标"的传送兜底时间（tick）。60 秒。 */
    private static final int WANDER_TELEPORT_TICKS = 1200;
    /** 完全无路可走时触发传送脱困的累计时间（tick）。10 秒。 */
    private static final int NO_PATH_TELEPORT_TICKS = 200;
    /** "脱离首领 / 玩家范围"类传送的卡住门槛：连续多轮没走动（约 10 秒）才算卡住。 */
    private static final int STUCK_TELEPORT_MIN_TICKS = 200;
    /** "到目标有没有路"的探测间隔（tick）。4 秒一次，避免拖慢服务器。 */
    private static final int PATH_PROBE_INTERVAL_TICKS = 80;
    /** "寻路不通"目标的拉黑时长（tick）。30 秒。 */
    private static final int UNREACHABLE_BLACKLIST_TICKS = 600;
    /** 可达性探测缓存时长（tick）：确认"完全不可达"后这段时间内不再重复跑 A*。 */
    private static final int REACH_PROBE_MEMO_TICKS = 200;
    /** 同一目标战斗追击重规划的最短间隔 / 退避上限（tick）。 */
    private static final int REPLAN_MIN_RETRY_TICKS = 8;
    private static final int REPLAN_MAX_RETRY_TICKS = 120;
    /** 允许"传送到敌人附近"的最大高度差（格）：超过说明敌人在墙头 / 塔楼，不传送过去。 */
    private static final double MAX_ENEMY_TELEPORT_Y_DIFF = 2.0;

    /** 最近一次成功攻击（近战命中 / 成功开火 / 穿刺命中）的 tick。 */
    private int lastAttackSuccessTick = -10000;

    /** 自研规划器给出的绕路路径点（含路径终点），按顺序逐个走。 */
    private final java.util.ArrayDeque<Vec3> routePoints = new java.util.ArrayDeque<>();
    @Nullable private java.util.UUID routeTargetId = null;
    private int routeExpireTick = 0;
    private int routeIssueTick = 0;
    private int lastRoutePlanTick = -10000;
    /** 当前路径点已尝试的 tick 数、连续无进展 tick 数、到该路径点的上次距离²。 */
    private int routeWaypointTicks = 0;
    private int routeStallTicks = 0;
    private double routeLastDistSq = -1.0;
    /** 上一次规划的路线：作为下次重规划的代价偏好，避免在两条绕路之间来回切换。 */
    private java.util.List<net.minecraft.core.BlockPos> lastRoutePoints = java.util.List.of();
    /** 当前路线是否真正能到达目标（部分路线 = false，用于仇恨过期判定）。 */
    private boolean routeReachesGoal = false;

    // 行军绕路路线：普通导航只能给"半路路径"（迷宫 / 绕墙）时，改用方块级 A* 绕路
    //   （与首领共用同一套跟随器，见 ai/nav/MarchRouteFollower）
    private final com.ohhapple.monster_conquest.ai.nav.MarchRouteFollower marchRoute =
            new com.ohhapple.monster_conquest.ai.nav.MarchRouteFollower();

    private double lastTargetDistSq = -1;
    private int lastCombatTick = -10000;
    private static final int COMBAT_GRACE_TICKS = 100;
    /** 上一次处理过的仇恨重置标记（重置工具 / 和平模式会清目标与仇恨）。 */
    private long lastHatredResetStamp = 0L;

    private final Map<UUID, Integer> lanceHitCd = new HashMap<>();

    private static final float CAVALRY_MAX_TURN_PER_TICK = 12f;

    private ItemStack dualBowStack = ItemStack.EMPTY;
    private ItemStack dualMeleeSwordStack = ItemStack.EMPTY;
    private ItemStack dualMeleeLanceStack = ItemStack.EMPTY;

    private boolean atFormationSlot = false;
    private boolean cachedOpenTerrain = false;
    private int openTerrainCacheTick = -1000;
    private static final int OPEN_TERRAIN_CACHE_TICKS = 100;

    @Nullable private Vec3 manualMoveTarget = null;
    @Nullable private Vec3 manualFacingAnchor = null;
    /** 缓存玩家 ride mode ordinal，用于检测变化（上下马立即生效，不等冷却）。 */
    private int lastRideModeOrdinal = -1;
    @Nullable private transient Player cachedPlayer = null;
    private int cachedPlayerTick = -1;

    private long spawnGameTime = -1L;
    /** 上次向同阵营广播威胁的 tickCount。 */
    private int lastThreatBroadcastTick = -10000;
    /** 广播间隔（tick）。100 tick = 5 秒。 */
    private static final int THREAT_BROADCAST_INTERVAL_TICKS = 100;
    private long lastProcessedOrderGen = -1L;
    private boolean playerOrderAccepted = false;
    /** 出生后是否收到过玩家的专门命令（个人命令 / 编队等），决定是否进入"已归属玩家指挥"状态。 */
    private boolean playerEverOrdered = false;

    protected AbstractArmySoldier(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = getXpReward();
        // 兜底：构造时按当前 Tier（默认 TIER_0）应用属性。
        //   装备槽位 / 附魔走 applyEquipment，在 finalizeSpawn 或刷怪蛋里另行处理。
        applyAttributesForTier(getTier());
    }

    // ===================== 子类必须实现 =====================


    public abstract SoldierType getDefaultSoldierType();
    public abstract int getRenderIndex();

    public SoldierRole getRole() { return getSoldierType().getRole(); }
    public boolean isDualWeapon() { return getRole() == SoldierRole.DUAL; }

    /** 是否溺尸士兵：直接按实体类型判断。 */
    public boolean isDrowned() {
        return this.getType() == ModEntities.DROWNED_SOLDIER.get();
    }

    protected void applyArrowEffects(ArmyArrow arrow) { }
    protected void applyMeleeEffects(LivingEntity victim) { }
    protected boolean usesTridentForDual() { return false; }

    /**
     * 近战武器（按装备等级）。默认使用 {@link EquipmentTier#getWeapon()}（剑）。
     *   卫道士士兵覆盖为斧子（不同等级品质不同）。
     */
    public Item getMeleeWeaponForTier(EquipmentTier tier) { return tier.getWeapon(); }

    /**
     * 骑兵形态是否改用骑枪。默认 true（普通近战兵骑马拿骑枪）。
     *   卫道士士兵覆盖为 false：骑兵形态仍然只拿斧子 + 盾牌。
     */
    public boolean usesLanceInCavalry() { return true; }

    /**
     * 近战 / 骑兵冲锋使用的伤害来源。默认 {@code mobAttack}。
     *   卫道士士兵覆盖为"破盾"伤害（无视一切盾牌格挡）。
     */
    protected DamageSource meleeDamageSource() {
        return this.damageSources().mobAttack(this);
    }

    @Nullable
    protected Projectile createProjectile() {
        ArmyArrow arrow = new ArmyArrow(this.level(), this, new ItemStack(Items.ARROW), null);
        applyArrowEffects(arrow);
        return arrow;
    }

    protected double getProjectileSpeed() { return CombatConfig.arrowSpeed; }
    protected SoundEvent getShootSound() { return SoundEvents.SKELETON_SHOOT; }

    /**
     * 是否使用枪械直瞄弹道（弹速极快、几乎无下坠、可穿过友军）。
     * 枪手子类返回 true；弓手 / 三叉戟保持 false。
     */
    protected boolean usesDirectFire() { return false; }

    /** 枪械弹丸重力（仅直瞄弹道使用）。 */
    protected double getProjectileGravity() { return 0.05; }

    /** 远程攻击冷却（tick）。 */
    public int getRangedAttackCooldownTicks() { return 25; }

    /** 近战攻击间隔（tick）。默认 10；重锤兵等重型武器可覆盖为更慢。 */
    public int getMeleeAttackIntervalTicks() { return 10; }

    /** 最小射击距离：目标近于该距离时拉开身位。 */
    public double getMinAttackRange(boolean mounted) { return mounted ? 18.0 : 14.0; }

    /** 最大射击距离：目标远于该距离时向前靠近。 */
    public double getMaxAttackRange(boolean mounted) { return mounted ? 40.0 : 32.0; }

    /**
     * 远程兵的"理想站位距离"：目标比这更远时会主动靠近。
     *   默认 = 最大射程（在射程边缘输出）；霰弹枪兵覆盖为近身距离。
     *   只影响走位，不影响开火判定（开火仍看 {@link #getMaxAttackRange}）。
     */
    public double getPreferredAttackRange(boolean mounted) { return getMaxAttackRange(mounted); }

    /** 每次射击发射的弹丸数量（霰弹枪为多发）。 */
    protected int getProjectilesPerShot() { return 1; }

    /** 霰弹枪扇形散布角度（度）。 */
    protected double getShotSpreadDegrees() { return 0.0; }

    /** 枪械射击散布（越小越准）。狙击枪等精英枪手可覆盖为 0。 */
    protected double getShotInaccuracy() { return CombatConfig.arrowInaccuracy; }

    /** 射击音效音高倍率。默认 1.0；狙击枪等可覆盖为更低沉的音色。 */
    protected float getShootSoundPitch() { return 1.0f; }

    /** 枪手手持的枪械物品（用于装备槽位 / 手持渲染）。 */
    @Nullable
    public Item getGunItem() { return null; }

    /** 枪手使用的枪械数值（与玩家一致）；非枪手返回 null。 */
    @Nullable
    public GunSpec getGunSpec() { return null; }

    // ===================== 每级数值（子类可覆盖） =====================

    public double getMaxHealthForTier(EquipmentTier tier) { return tier.getMaxHealth(); }
    public double getMeleeDamageForTier(EquipmentTier tier) { return tier.getAttackDamage(); }
    public float getRangedDamageForTier(EquipmentTier tier) { return (float) CombatConfig.arrowBaseDamage; }

    public double getArmorForTier(EquipmentTier tier) {
        return switch (tier) {
            case TIER_0 -> 2.0;
            case TIER_1 -> 4.0;
            case TIER_2 -> 6.0;
            case TIER_3 -> 8.0;
            case TIER_4 -> 10.0;
        };
    }

    public double getMovementSpeed() { return 0.33; }
    /** 骑兵马速：各兵种在自己的类里直接写数值（写多少就是多少，不乘任何倍率）。 */
    public double getCavalryHorseSpeed() { return 0.4; }
    public double getCavalryChargeSpeed() { return 0.5; }

    /**
     * 骑马时的实际常规马速（非冲锋）。
     *   与马速一致，不再额外乘倍率：逃跑 / 追击时和坐骑本身的速度相同。
     */
    public double getMountedRideSpeed() {
        return getCavalryHorseSpeed();
    }

    /**
     * 骑兵形态的近战攻击距离（格）。
     *   非骑枪骑兵骑马时马身会把目标挡开，追击逃跑的远程兵时 2.8 格容易够不着，
     *   所以给更远的距离；骑枪骑兵不用这个值（走冲锋穿刺）。
     */
    public double getMountedMeleeReach() {
        return usesLanceInCavalry() ? 2.4 : 3.4;
    }

    public double getCavalryHorseMaxHealth() { return getMaxHealth(); }
    public Item getCavalryHorseArmor() { return getTier().getHorseArmor(); }
    public double getCavalryHorseArmorBonus() { return 0.0; }
    /**
     * 自然索敌半径：近战 32 / 投掷 40 / 弓手·双形态·枪手 64 格，
     *   以 GeneralConfig.armyFollowRange 为全局上限；玩家强制目标 / 威胁传播 / 共享仇恨
     *   仍按 GeneralConfig.armyFollowRange 获取。
     */
    public double getFollowRange() {
        double base = switch (getRole()) {
            case MELEE -> 32.0;
            case THROWER -> 40.0;
            case DUAL, ARCHER -> 64.0;
            case GUNNER -> 64.0;
            case MAGE -> 40.0;
        };
        return Math.min(base, GeneralConfig.armyFollowRange);
    }
    public int getXpReward() { return 5; }
    /**
     * 远程兵被近身（目标 < 自身最小射程）时"拉距离"的导航速度倍率。
     *   子类可覆盖。
     *
     *   这是路径导航的 speedModifier，实际格/tick = MOVEMENT_SPEED × 此值。
     *   本项目统一为 1.0：士兵移动速度只由各兵种的 MOVEMENT_SPEED / 马速属性决定。
     */
    public double getRangedRetreatSpeedModifier() { return 1.0; }

    // ===================== 控制工具：手动移动 =====================
    @Nullable public Vec3 getManualMoveTarget() { return manualMoveTarget; }
    public void setManualMoveTarget(@Nullable Vec3 target) { this.manualMoveTarget = target; }

    /** 已服从的"移动命令代号"：代号变了 = 玩家刚下了"前往 / 驻守"命令。 */
    private int obeyedMoveOrderStamp = Integer.MIN_VALUE;

    /** 收到攻击命令时调用：放弃手动移动目标（攻击命令覆盖旧的移动命令）。 */
    public void cancelManualMoveOrder() {
        manualMoveTarget = null;
        manualFacingAnchor = null;
    }

    /**
     * "前往指定点"命令是否仍在执行：命令期间不主动索敌（命令优先于自主锁定）。
     *   单兵手动移动 → 一直优先到到达 / 被新命令取消；
     *   小队"前往"命令 → 由小队状态里的命令目标判断（到达目标点附近或超时后恢复自主索敌）。
     */
    public boolean isMoveOrderPriorityActive() {
        if (manualMoveTarget != null) return true;
        if (!isPlayerArmy()) return false;
        Player owner = getCommanderPlayer();
        if (owner == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;
        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st);
        return ss.isMoveOrderPriorityActive(getX(), getZ(), level().getGameTime());
    }

    /** 小队 / 单兵当前有没有"玩家点名的攻击目标"（强制目标 / 精确打击）：有则攻击命令优先。 */
    public boolean hasPlayerOrderedTarget() {
        if (!isPlayerArmy()) return false;
        Player owner = getCommanderPlayer();
        if (owner == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;
        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st);
        if (ss.getForcedTarget() != null) return true;
        return st.getPreciseStrikeTarget(level().getGameTime()) != null
                && st.isSquadInPreciseStrike(getSquadId());
    }

    /** 这个目标是不是"玩家点名"的（强制目标 / 精确打击）：点名目标才能打断手动移动。 */
    public boolean isPlayerOrderedTarget(@Nullable LivingEntity target) {
        if (target == null || !isPlayerArmy()) return false;
        Player owner = getCommanderPlayer();
        if (owner == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;
        java.util.UUID id = target.getUUID();
        if (com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st).isForcedTarget(id)) return true;
        java.util.UUID precise = st.getPreciseStrikeTarget(level().getGameTime());
        return precise != null && precise.equals(id);
    }

    /**
     * 跟随玩家的移动命令代号：发现玩家刚下了新的"前往 / 驻守"命令时，
     *   立刻清掉当前战斗目标与追击路线 —— 移动命令优先，士兵先去命令点。
     *   （玩家点名攻击（强制目标 / 精确打击）不受影响：那条命令会重新锁定目标。）
     */
    private void syncPlayerMoveOrder() {
        if (!isPlayerArmy()) return;
        Player owner = getCommanderPlayer();
        if (owner == null) return;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return;
        int stamp = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st).getMoveOrderStamp();
        if (obeyedMoveOrderStamp == Integer.MIN_VALUE) {
            obeyedMoveOrderStamp = stamp;      // 首次同步只记录，不打断已有指令
            return;
        }
        if (stamp == obeyedMoveOrderStamp) return;
        obeyedMoveOrderStamp = stamp;
        setTarget(null);
        setLastHurtByMob(null);
        clearRoute();
        releaseMarchRoute();
        clearReachProbeMemo();
    }

    public int getSquadId() { return this.entityData.get(DATA_SQUAD_ID); }
    /** 0 = 无小队（不参与任何小队命令，可被"编入附近单兵"重新编队）。 */
    public void setSquadId(int id) { this.entityData.set(DATA_SQUAD_ID, Math.max(0, id)); }

    // ===================== 手动装备 / 战术装备（单位界面） =====================
    // 单位界面里显示的永远是"玩家手动放进去的那一份"：自带装备不上界面、默认栏位为空；
    //   放入手动装备即覆盖身上自带的那件，取出立刻还原成自带装备（还原的是身上穿的，
    //   界面栏位本身保持为空，与玩家的直觉一致）。
    // 装备数据（手动装备 + 战术装备）会写进收纳工具做出来的蛋、并在升级时保留。

    /** 手动装备栏下标：0..3 = 头盔 / 胸甲 / 护腿 / 靴子，4 = 主手，5 = 副手。 */
    public static final int GEAR_SLOTS = ManagedUnit.GEAR_SLOTS;
    /** 战术装备栏格数（与玩家一致；单位只允许保暖衣 / 防弹衣，背包 / 马哨 / 弹药箱不可放入）。 */
    public static final int TACTICAL_SLOTS = ManagedUnit.TACTICAL_SLOTS;

    private final ItemStack[] manualGear = emptyStacks(GEAR_SLOTS);
    private final ItemStack[] defaultGear = emptyStacks(GEAR_SLOTS);
    private final ItemStack[] manualTactical = emptyStacks(TACTICAL_SLOTS);

    // 单位界面里单独调过的策略：只影响这一个单位，不影响它所在小队 / 全军。
    //   null = 跟随小队（或单兵自己的）指令状态。
    private PlayerCommandState.MovementMode movementOverride;
    private PlayerCommandState.AttackMode attackOverride;
    private PlayerCommandState.RideMode rideOverride;
    private PlayerCommandState.FormationMode formationOverride;

    public PlayerCommandState.MovementMode getMovementOverride() { return movementOverride; }
    public PlayerCommandState.AttackMode getAttackOverride() { return attackOverride; }
    public PlayerCommandState.RideMode getRideOverride() { return rideOverride; }

    public void setMovementOverride(@Nullable PlayerCommandState.MovementMode mode) {
        this.movementOverride = mode;
    }

    public void setAttackOverride(@Nullable PlayerCommandState.AttackMode mode) {
        this.attackOverride = mode;
    }

    public void setRideOverride(@Nullable PlayerCommandState.RideMode mode) {
        this.rideOverride = mode;
    }

    public PlayerCommandState.FormationMode getFormationOverride() { return formationOverride; }

    public void setFormationOverride(@Nullable PlayerCommandState.FormationMode mode) {
        this.formationOverride = mode;
    }

    /** 是否被界面单独调过策略：调过的单位走自己的状态，不再同步小队 / 全军的策略。 */
    public boolean hasStrategyOverride() {
        return movementOverride != null || attackOverride != null
                || rideOverride != null || formationOverride != null;
    }

    /** 首次个体化：把当前生效的小队策略复制到自己的个体状态，避免其它字段留着旧值。 */
    public void adoptStrategyFrom(PlayerCommandState.SquadState from) {
        PlayerCommandState.SquadState mine = loneState();
        // 个体状态可能残留上一次单兵时期的目标 / 锚点：先清干净再抄（单兵自己就是 from 时不能清）
        if (mine != from) mine.clear();
        mine.setMovement(from.getMovement());
        mine.setAttackMode(from.getAttack());
        mine.setFormation(from.getFormation());
        mine.setRideMode(from.getRideMode());
    }

    /** 解除个体策略：此后重新跟随小队 / 全军指令。 */
    public void clearStrategyOverrides() {
        movementOverride = null;
        attackOverride = null;
        rideOverride = null;
        formationOverride = null;
    }

    private static ItemStack[] emptyStacks(int count) {
        ItemStack[] arr = new ItemStack[count];
        for (int i = 0; i < count; i++) arr[i] = ItemStack.EMPTY;
        return arr;
    }

    /** 手动装备栏下标 → 实体装备槽。 */
    public static EquipmentSlot gearSlot(int index) {
        return ManagedUnit.gearSlot(index);
    }

    @Override
    public LivingEntity asEntity() { return this; }

    /** 读取手动装备（空 = 使用自带装备）。 */
    public ItemStack getManualGear(int index) { return manualGear[index]; }

    /**
     * 写入手动装备：非空覆盖自带装备，空则立刻还原自带装备。
     *   还原一律以"自带装备快照"为准：快照为空（枪手 / 双形态 / 弓手的副手等）就是空手，
     *   不再"保留身上那件"——否则取下旗帜之类的物品后会一直卡在手上。
     */
    public void setManualGear(int index, ItemStack stack) {
        ItemStack previous = manualGear[index];
        manualGear[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        ItemStack worn = this.getItemBySlot(gearSlot(index));
        ItemStack want;
        if (!manualGear[index].isEmpty()) {
            want = wornManualStack(index);
        } else if (!defaultGear[index].isEmpty()) {
            want = defaultGear[index];
        } else if (!previous.isEmpty() && !ItemStack.isSameItem(worn, previous)) {
            // 快照缺失（老存档）、身上穿的已经不是刚取下的那件：当成自带装备保留
            want = worn;
        } else {
            // 自带装备本来就是空的（枪手 / 双形态 / 弓手的副手等）：取下手动装备 = 空手
            want = ItemStack.EMPTY;
        }
        this.setItemSlot(gearSlot(index), want.copy());
        // 双形态：副手（远程形态）被取走时，主手可能正拿着从它换过去的复制品 → 放回主手默认装备
        if (index == 5 && !previous.isEmpty() && isDualWeapon() && !usesTridentForDual()
                && ItemStack.isSameItem(this.getMainHandItem(), previous)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, defaultGear[4].copy());
        }
        refreshManualGearMask();
    }

    /**
     * 手动装备"穿在身上"的那一份：界面里的那份保持原样，装备的效果按下面两条走：
     *   · 自定义装备自己带附魔 → 用自定义的附魔；
     *   · 自定义装备不带附魔 → 继承这个部位自带装备的附魔（不让玩家一换装备就丢附魔效果）。
     */
    private ItemStack wornManualStack(int index) {
        ItemStack worn = manualGear[index].copy();
        if (worn.isEmpty()) return worn;
        if (worn.isEnchanted()) return worn;
        // 手持槽只在放的是武器时继承附魔（盾牌等非武器不蹭武器附魔）
        if (index >= 4
                && !com.ohhapple.monster_conquest.util.UnitGearRules.isMeleeWeapon(worn)
                && !com.ohhapple.monster_conquest.util.UnitGearRules.isBow(worn)) {
            return worn;
        }
        ItemStack def = enchantSourceFor(index);
        if (def.isEmpty()) return worn;
        if (!def.getEnchantments().isEmpty()) {
            worn.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    def.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS));
        }
        return worn;
    }

    /**
     * 手动装备继承附魔的来源装备：
     *   双形态兵种的两个手分别对应"近战形态 / 远程形态"，附魔也从对应形态的默认武器继承；
     *   其它单位就是该槽位的自带装备。
     */
    private ItemStack enchantSourceFor(int index) {
        if (isDualWeapon() && !usesTridentForDual()) {
            if (index == 4) return dualMeleeSwordStack;
            if (index == 5) return dualBowStack;
        }
        return defaultGear[index];
    }

    /** 某个装备栏是否被玩家手动覆盖（客户端靠同步的掩码判断，界面显示与阵营图案都看它）。 */
    @Override
    public boolean hasManualGear(int index) {
        return (this.entityData.get(DATA_MANUAL_GEAR) & (1 << index)) != 0;
    }

    private void refreshManualGearMask() {
        int mask = 0;
        for (int i = 0; i < GEAR_SLOTS; i++) {
            if (!manualGear[i].isEmpty()) mask |= 1 << i;
        }
        this.entityData.set(DATA_MANUAL_GEAR, (byte) mask);
    }

    /** 兼容老存档：没有自带装备快照时，把当前身上（未被手动覆盖的）装备记为自带装备。 */
    public void ensureDefaultGear() {
        for (int i = 0; i < GEAR_SLOTS; i++) {
            if (defaultGear[i].isEmpty() && manualGear[i].isEmpty()) {
                defaultGear[i] = this.getItemBySlot(gearSlot(i)).copy();
            }
        }
    }

    /** 主手是否被玩家手动指定（手动指定时双持自动换武器让位）。 */
    public boolean hasManualMainHand() { return !manualGear[4].isEmpty(); }

    /**
     * 这个兵种是否允许玩家在单位界面里换武器。
     *   部分兵种的武器与专属攻击逻辑绑定（重锤兵的重锤冲击等），由子类关闭换武器。
     */
    public boolean allowsCustomWeapons() { return true; }

    public ItemStack getManualTactical(int index) { return manualTactical[index]; }

    public void setManualTactical(int index, ItemStack stack) {
        manualTactical[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /** 战术装备栏里是否有满足条件的物品。 */
    public boolean hasTactical(java.util.function.Predicate<ItemStack> test) {
        for (ItemStack stack : manualTactical) if (test.test(stack)) return true;
        return false;
    }

    /** 找到满足条件的战术装备槽位；没有返回 -1。 */
    public int findTactical(java.util.function.Predicate<ItemStack> test) {
        for (int i = 0; i < TACTICAL_SLOTS; i++) if (test.test(manualTactical[i])) return i;
        return -1;
    }

    /** 清空某个战术装备槽（防弹衣打坏时用）。 */
    public void clearTactical(int index) {
        if (index >= 0 && index < TACTICAL_SLOTS) manualTactical[index] = ItemStack.EMPTY;
    }

    /** 清空全部手动装备与战术装备，并把自带装备还原到身上。 */
    public void clearManualEquipment() {
        ensureDefaultGear();
        for (int i = 0; i < GEAR_SLOTS; i++) setManualGear(i, ItemStack.EMPTY);
        for (int i = 0; i < TACTICAL_SLOTS; i++) manualTactical[i] = ItemStack.EMPTY;
        refreshManualGearMask();
    }

    /**
     * 记录自带装备快照，并把玩家手动装备重新盖回去。
     *   调用时机：身上 6 个槽位里放的必须是"这次刚写好的自带装备"
     *   （即 applyEquipment / equipCommander 写完全部默认装备与附魔之后），
     *   这样快照才能带上附魔；随后手动装备原样盖回去。
     */
    public void refreshDefaultGear() {
        for (int i = 0; i < GEAR_SLOTS; i++) {
            EquipmentSlot slot = gearSlot(i);
            defaultGear[i] = this.getItemBySlot(slot).copy();
            if (!manualGear[i].isEmpty()) this.setItemSlot(slot, wornManualStack(i));
        }
        refreshManualGearMask();
    }

    /** 把手动装备 / 战术装备写进标签（做蛋时用；自带装备不写，避免无意义膨胀）。 */
    public void writeGearTag(CompoundTag tag) {
        tag.put("ManualGear", stackListTag(manualGear));
        tag.put("ManualTactical", stackListTag(manualTactical));
    }

    /** 从标签恢复手动装备；必须在 applyEquipment 之后调用，手动装备会覆盖自带装备。 */
    public void readGearTag(CompoundTag tag) {
        readStackList(tag.getList("ManualGear", Tag.TAG_COMPOUND), manualGear);
        readStackList(tag.getList("ManualTactical", Tag.TAG_COMPOUND), manualTactical);
        for (int i = 0; i < GEAR_SLOTS; i++) {
            ItemStack want = manualGear[i].isEmpty() ? defaultGear[i] : wornManualStack(i);
            this.setItemSlot(gearSlot(i), want.copy());
        }
        refreshManualGearMask();
    }

    private static ListTag stackListTag(ItemStack[] stacks) {
        ListTag list = new ListTag();
        for (ItemStack stack : stacks) {
            list.add(ItemStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, stack)
                    .result().orElseGet(CompoundTag::new));
        }
        return list;
    }

    private static void readStackList(ListTag list, ItemStack[] out) {
        for (int i = 0; i < out.length; i++) {
            if (i >= list.size()) {
                out[i] = ItemStack.EMPTY;
                continue;
            }
            out[i] = ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, list.get(i))
                    .result().orElse(ItemStack.EMPTY);
        }
    }

    // ===================== 无小队单兵的个人指令状态 =====================

    /**
     * 无小队士兵的个人 SquadState：和小队命令共用同一套逻辑
     * （移动锚点 / 驻守 / 软驻守），只是不占用小队号。
     */
    @Nullable private com.ohhapple.monster_conquest.player.PlayerCommandState.SquadState loneState = null;

    public com.ohhapple.monster_conquest.player.PlayerCommandState.SquadState loneState() {
        if (loneState == null) {
            loneState = new com.ohhapple.monster_conquest.player.PlayerCommandState.SquadState();
            // 单兵默认自由移动：刷怪蛋 / 指令召唤出来的单兵默认像原版生物一样自由游走，
            //   而不是原地硬驻守。玩家下达跟随 / 驻守 / 自由移动等命令后按命令执行。
            loneState.setMovement(
                    com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FREE);
        }
        return loneState;
    }

    public void clearLoneState() {
        if (loneState == null) return;
        loneState.clear();
        // 与新建单兵一致：解散 / 脱离小队后的单兵默认自由移动
        loneState.setMovement(
                com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FREE);
    }

    // ===================== 募兵站"待命"状态 =====================
    /**
     * 募兵站召出来的士兵：在编入队伍或接到指令之前保持"待命"——
     *   强制步兵（不自动上马），免得刚招出来就骑着马往募兵站 / 窄处里钻。
     *   编入队伍 / 接到命令后自动解除，恢复正常自动判断上下马。
     */
    private boolean recruitStandby = false;
    @Nullable private Vec3 recruitStandbyAnchor = null;

    /** 登记"募兵站待命"（锚点 = 出生位置）。 */
    public void markRecruitStandby(Vec3 anchor) {
        this.recruitStandby = true;
        this.recruitStandbyAnchor = anchor;
    }

    public boolean isRecruitStandby() { return recruitStandby; }

    private void tickRecruitStandby() {
        if (!recruitStandby) return;
        if (tickCount % 10 != 0) return;
        // 编入队伍 / 指挥工具点地 / 接到攻击命令 → 解除待命
        if (getSquadId() > 0 || getManualMoveTarget() != null || getTarget() != null) {
            releaseRecruitStandby();
            return;
        }
        com.ohhapple.monster_conquest.player.PlayerCommandState.SquadState ss = loneState;
        if (ss == null) {
            releaseRecruitStandby();
            return;
        }
        // 玩家手动改了骑乘模式 / 行动模式 / 锚点挪了地方（说明收到了命令）→ 解除待命
        if (ss.getRideMode()
                    != com.ohhapple.monster_conquest.player.PlayerCommandState.RideMode.FORCED_INFANTRY
                || ss.getMovement()
                    != com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION
                || ss.getFormationAnchor() == null
                || (recruitStandbyAnchor != null
                    && ss.getFormationAnchor().distanceToSqr(recruitStandbyAnchor) > 4.0)) {
            releaseRecruitStandby();
        }
    }

    private void releaseRecruitStandby() {
        recruitStandby = false;
        recruitStandbyAnchor = null;
        if (loneState != null
                && loneState.getRideMode()
                    == com.ohhapple.monster_conquest.player.PlayerCommandState.RideMode.FORCED_INFANTRY) {
            loneState.setRideMode(com.ohhapple.monster_conquest.player.PlayerCommandState.RideMode.AUTO);
        }
    }

    @Nullable public Vec3 getManualFacingAnchor() { return manualFacingAnchor; }
    public void setManualFacingAnchor(@Nullable Vec3 v) { this.manualFacingAnchor = v; }

    public boolean isAttackStopped() {
        if (!isPlayerArmy()) return false;
        Player p = getCommanderPlayer();
        if (p == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(p.getUUID());
        if (st == null) return false;
        // 无小队单兵读个人状态（与小队共用同一套状态机）
        return com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st).getAttack()
                == com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.STOP;
    }

    /**
     * 当前攻击策略是不是"无差别攻击"（轮盘 / 单位界面都能设）。
     *   这是一条玩家明确下的攻击命令，索敌时不受"是否接受过命令 / 移动命令优先"的限制。
     */
    public boolean isFreeAggressiveStance() {
        if (!isPlayerArmy()) return false;
        Player p = getCommanderPlayer();
        if (p == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(p.getUUID());
        if (st == null) return false;
        return com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st).getAttack()
                == com.ohhapple.monster_conquest.player.PlayerCommandState.AttackMode.FREE_AGGRESSIVE;
    }

    // ===================== 阵营 =====================
    @Nullable
    public ResourceLocation getFactionId() {
        String s = this.entityData.get(DATA_FACTION);
        if (s != null && !s.isEmpty()) {
            ResourceLocation rl = ResourceLocation.tryParse(s);
            if (rl != null) return rl;
        }
        UUID cmdUuid = getCommanderUuid().orElse(null);
        if (cmdUuid == null) return null;
        AbstractArmyCommander cmd = getCommander();
        if (cmd != null) return cmd.getFactionId();
        Player p = getCommanderPlayer();
        if (p != null) return FactionIds.player(p.getUUID());
        if (level() instanceof ServerLevel sl) {
            var cache = sl.getServer().getProfileCache();
            if (cache != null && cache.get(cmdUuid).isPresent()) {
                return FactionIds.player(cmdUuid);
            }
        }
        return null;
    }

    public void setFactionId(@Nullable ResourceLocation id) {
        this.entityData.set(DATA_FACTION, id == null ? "" : id.toString());
        if (id != null && FactionIds.isPlayer(id)) {
            UUID owner = FactionIds.parsePlayer(id);
            FactionRegistry.getOrCreatePlayer(owner);
        }
        refreshFactionShield();
    }

    /** 阵营变化后刷新盾牌上的阵营旗图案（装备已生成时才处理）。 */
    private void refreshFactionShield() {
        if (level().isClientSide) return;
        // 副手被玩家手动替换（自定义盾牌等）→ 不动玩家放的那件
        if (hasManualGear(5)) return;
        ItemStack off = this.getOffhandItem();
        if (!off.is(Items.SHIELD)) return;
        FactionShield.apply(off, level(), getFactionId());
    }

    // ===================== 首领/玩家 =====================
    @Nullable
    public AbstractArmyCommander getCommander() {
        Optional<UUID> uuid = this.entityData.get(DATA_COMMANDER);
        if (uuid.isEmpty()) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        var entity = sl.getEntities().get(uuid.get());
        return entity instanceof AbstractArmyCommander c ? c : null;
    }

    @Nullable
    public Player getCommanderPlayer() {
        UUID uuid = getCommanderUuid().orElse(null);
        if (uuid == null) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        if (cachedPlayerTick == tickCount && cachedPlayer != null) return cachedPlayer;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(uuid);
        cachedPlayer = p;
        cachedPlayerTick = tickCount;
        return p;
    }

    @Nullable
    public LivingEntity getLeaderEntity() {
        AbstractArmyCommander cmd = getCommander();
        if (cmd != null && cmd.isAlive()) return cmd;
        Player p = getCommanderPlayer();
        if (p != null && p.isAlive()) return p;
        return null;
    }

    // ===================== 玩家命令响应 =====================
    private void refreshPlayerOrder() {
        if (spawnGameTime < 0) {
            spawnGameTime = level().getGameTime();
            if (!isPlayerArmy()) {
                playerOrderAccepted = false;
                return;
            }
            Player owner = getCommanderPlayer();
            if (owner == null) {
                playerOrderAccepted = false;
                return;
            }
            var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                    .getIfPresent(owner.getUUID());
            if (state == null) {
                playerOrderAccepted = false;
                return;
            }
            long lastIssued = state.getLastOrderIssuedGT();
            playerOrderAccepted = lastIssued < 0 || spawnGameTime < lastIssued;
            lastProcessedOrderGen = state.getCommandGen();
            return;
        }

        if (!isPlayerArmy()) {
            playerOrderAccepted = false;
            return;
        }
        Player owner = getCommanderPlayer();
        if (owner == null) return;
        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (state == null) {
            playerOrderAccepted = false;
            return;
        }
        long gen = state.getCommandGen();
        if (gen == lastProcessedOrderGen) return;
        lastProcessedOrderGen = gen;
        long lastIssued = state.getLastOrderIssuedGT();
        playerOrderAccepted = lastIssued < 0 || spawnGameTime < lastIssued;
    }

    public boolean isPlayerOrderAccepted() { return playerOrderAccepted; }

    /** 标记该士兵已被玩家指挥过（编入小队 / 收到个人命令时调用）。 */
    public void markPlayerCommanded() {
        this.playerEverOrdered = true;
    }

    /**
     * 该玩家阵营士兵是否已经"归属玩家指挥"：
     *   · 已编入小队（含隐藏小队）；或
     *   · 出生后收到过玩家的专门命令（个人命令 / 编队等）。
     *
     *   刚用刷怪蛋等方式放出来的士兵：没有小队、也没收到过命令 → false。
     *   此时保持原版式自然游走，不跟随玩家、也不会被传送到玩家身边，
     *   直到玩家把它编入小队或给它下达命令。
     */
    public boolean isPlayerCommanded() {
        if (!isPlayerArmy()) return false;
        return getSquadId() > 0 || playerEverOrdered;
    }

    public boolean isVolleyActiveNow() {
        if (!isPlayerArmy()) return false;
        Player p = getCommanderPlayer();
        if (p == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(p.getUUID());
        if (st == null) return false;
        return com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st)
                .isVolleyActive(level().getGameTime());
    }

    public boolean isPlayerArmy() {
        UUID cmdUuid = getCommanderUuid().orElse(null);
        if (cmdUuid == null) {
            ResourceLocation f = getFactionId();
            return f != null && FactionIds.isPlayer(f);
        }
        if (getCommander() != null) return false;
        return getCommanderPlayer() != null
                || (getFactionId() != null && FactionIds.isPlayer(getFactionId()));
    }

    /**
     * 硬驻守：玩家军队 + STATION + 非软驻守。
     *   硬驻守时：
     *     - 远程兵在 SoldierRangedAttackGoal 里禁止移动
     *     - 远程兵在 AttackConfiguredEnemyGoal 里索敌范围翻倍
     */
    public boolean isHardStationed() {
        if (!isPlayerArmy()) return false;
        Player p = getCommanderPlayer();
        if (p == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(p.getUUID());
        if (st == null) return false;
        // 无小队士兵读自己的个人状态（与小队共用同一套软驻守逻辑）
        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st);
        return ss.getMovement()
                == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION
                && !ss.isSoftStation();
    }

    /**
     * 自由移动（自由游走）：玩家把小队的行动策略设为"自由移动"。
     *   此时士兵不跟随、不驻守、不列阵，由原版游走 AI
     *   （WaterAvoidingRandomStrollGoal）像原版生物一样自由游走。
     */
    public boolean isFreeRoaming() {
        if (!isPlayerArmy()) return false;
        Player owner = getCommanderPlayer();
        if (owner == null) return false;
        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (state == null) return false;
        return com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, state).getMovement()
                == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FREE;
    }

    public Optional<UUID> getCommanderUuid() { return this.entityData.get(DATA_COMMANDER); }

    public void setCommanderUuid(UUID uuid) {
        this.entityData.set(DATA_COMMANDER, Optional.of(uuid));
        this.cachedPlayer = null;
        this.cachedPlayerTick = -1;
    }

    public void clearCommanderUuid() {
        this.entityData.set(DATA_COMMANDER, Optional.empty());
        this.cachedPlayer = null;
        this.cachedPlayerTick = -1;
    }

    // ===================== 军队解析 =====================
    @Nullable
    public static UUID resolveArmyUuid(Entity entity, Level level) {
        if (entity instanceof AbstractArmyCommander c) return c.getUUID();
        if (entity instanceof AbstractArmySoldier s) {
            Optional<UUID> cmd = s.getCommanderUuid();
            if (cmd.isPresent()) return cmd.get();
            return s.getUUID();
        }
        if (entity instanceof Player p) return p.getUUID();
        if (entity instanceof WarHorseEntity h) {
            UUID ownerUuid = h.getOwnerUuid();
            if (ownerUuid == null) return null;
            if (!(level instanceof ServerLevel sl)) return null;
            Entity owner = sl.getEntities().get(ownerUuid);
            if (owner == null) return null;
            return resolveArmyUuid(owner, level);
        }
        return null;
    }

    // ===================== 属性 =====================
    public static AttributeSupplier.Builder createAttributes() {
        // 单位不再继承 Monster（避免被别的模组当"敌对生物"默认锁定），
        //   属性基线与原版怪物一致：Mob 基础 + 攻击力。
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE)
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COMMANDER, Optional.empty());
        builder.define(DATA_TIER, 0);
        builder.define(DATA_SOLDIER_TYPE, getDefaultSoldierType().ordinal());
        builder.define(DATA_FACTION, "");
        // 默认无小队（0）：刷怪蛋 / 指令召唤出来的己方士兵不会自动占用 1 分队，
        //   由玩家用轮盘"编入附近单兵"或 RTS 命令收编；存档中的队号仍按 NBT 恢复。
        builder.define(DATA_SQUAD_ID, 0);
        builder.define(DATA_MANUAL_GEAR, (byte) 0);
    }

    /** 野外自然刷出的单位：随队长一起在远离玩家后消失（刷怪蛋 / 指令 / 玩家招募的士兵不受影响）。 */
    private boolean wild;

    public boolean isWild() { return wild; }

    public void setWild(boolean wild) { this.wild = wild; }

    @Override public boolean removeWhenFarAway(double distance) { return wild; }

    @Override
    public void remove(RemovalReason reason) {
        // 野外巡逻队的骑兵在远处消失时连人带马一起移除，避免留下无主的战马
        //   （下马状态也要处理：战马不会自然消失，被丢在野外会越积越多）
        if (wild && reason == RemovalReason.DISCARDED && level() instanceof ServerLevel sl) {
            if (getVehicle() instanceof WarHorseEntity horse && !horse.isRemoved()) {
                horse.discard();
            } else {
                for (WarHorseEntity horse : sl.getEntitiesOfClass(WarHorseEntity.class,
                        getBoundingBox().inflate(16.0),
                        h -> getUUID().equals(h.getOwnerUuid()))) {
                    horse.discard();
                }
            }
        }
        super.remove(reason);
    }

    // ===================== 掉落 =====================

    /** 士兵死亡不掉落任何物品与经验。 */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Override
    public boolean fireImmune() {
        return getTier().ordinal() >= EquipmentTier.TIER_3.ordinal() || super.fireImmune();
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SoldierManualMoveGoal(this));
        this.goalSelector.addGoal(2, new SoldierMeleeAttackGoal(this));
        this.goalSelector.addGoal(3, new SoldierRangedAttackGoal(this));
        // 唤魔者士兵：召唤尖牙（只有魔法师角色会启用）
        if (this instanceof EvokerSoldier evoker) {
            this.goalSelector.addGoal(4, new EvokerSpellGoal(evoker));
        }
        this.goalSelector.addGoal(5, new WitchSupportGoal(this));
        this.goalSelector.addGoal(6, new FormationGoal(this));
        this.goalSelector.addGoal(7, new FollowCommanderGoal(this, 1.0D, 4.0f, 24.0f));
        this.goalSelector.addGoal(8, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 12.0f));
        this.goalSelector.addGoal(10, new RandomLookAroundGoal(this));

        // 去掉 HurtByTargetGoal：
        //   混战中敌人轮流攻击 → lastHurtByMobTimestamp 每次变化 → HurtByTargetGoal
        //   无条件 setTarget(lastHurtByMob) → 士兵在左右两侧敌人之间反复切换目标
        //   → 来回跑，谁都打不到。
        //   反击逻辑已由 AttackConfiguredEnemyGoal.isEnemyOf 里的
        //   "getLastHurtByMob() == entity → return true" 分支涵盖，
        //   且 ThreatDispatcher 在被打时会即时 setTarget，反应速度不变。
        this.targetSelector.addGoal(0, new AttackConfiguredEnemyGoal(this));
    }

    @Override
    public PathNavigation getNavigation() {
        if (this.getVehicle() instanceof Mob horse) return horse.getNavigation();
        return super.getNavigation();
    }

    // ===================== 和平模式 =====================

    /**
     * 和平难度下是否清除单位。
     *   默认不清除（保留单位，只是不结仇、不索敌、不攻击）；
     *   把 general-common.toml 的 peacefulRemovesUnits 设为 true 后，
     *   单位会像原版怪物一样在和平难度下被清除。
     */
    @Override
    protected boolean shouldDespawnInPeaceful() { return GeneralConfig.peacefulRemovesUnits; }

    /** 和平模式下对外表现为"没有目标"（战斗 / 索敌 goal 全部失效）。 */
    @Override
    @Nullable
    public LivingEntity getTarget() {
        if (PeacefulUtil.isPeaceful(this)) return null;
        return super.getTarget();
    }

    /** 和平模式下不接受新目标。 */
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        if (target != null && PeacefulUtil.isPeaceful(this)) return;
        super.setTarget(target);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        // 和平模式：不索敌、不反击
        if (PeacefulUtil.isPeaceful(this)) return false;
        // 锚点保护：不攻击锚点活动范围之外的目标（村庄巡逻 / 袭击增援）
        if (!ArmyAnchor.targetAllowed(this, target)) return false;
        if (isAttackStopped()) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (target == this) return false;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不索敌。
        if (NativePeace.isPeacefulPair(this, target)) return false;

        Player commanderPlayer = getCommanderPlayer();
        if (commanderPlayer != null && target == commanderPlayer) return false;

        if (target instanceof WarHorseEntity) return false;

        if (target instanceof AbstractArmySoldier
                || target instanceof AbstractArmyCommander
                || target instanceof Player) {
            UUID myArmy = resolveArmyUuid(this, level());
            UUID targetArmy = resolveArmyUuid(target, level());

            ResourceLocation myFac = getFactionId();
            ResourceLocation targetFac = factionOf(target);
            if (myFac != null && targetFac != null
                    && FactionAlly
                    .isAlly(myFac, targetFac)) {
                return false;
            }

            if (myArmy != null && myArmy.equals(targetArmy)) return false;

            if (getLastHurtByMob() == target) {
                ResourceLocation myF = getFactionId();
                ResourceLocation theirF = factionOf(target);
                if (myF == null || theirF == null || !myF.equals(theirF)) {
                    return true;
                }
            }

            if (myArmy != null && targetArmy != null
                    && ArmyRelations.isHostile(myArmy, targetArmy)) return true;

            ResourceLocation myFaction = getFactionId();
            ResourceLocation targetFaction = factionOf(target);

            // 玩家阵营互打：受 Config 控制（与 AbstractArmyCommander 保持一致）
            if (myFaction != null && targetFaction != null
                    && !myFaction.equals(targetFaction)
                    && FactionIds.isPlayer(myFaction)
                    && FactionIds.isPlayer(targetFaction)) {
                return PlayerConfig.autoHostilityBetweenPlayerArmies;
            }

            if (myFaction != null && targetFaction != null
                    && !myFaction.equals(targetFaction)
                    && FactionHostility.isHostile(myFaction, targetFaction)) return true;

            return false;
        }

        return super.canAttack(target);
    }

    @Nullable
    private static ResourceLocation factionOf(LivingEntity e) {
        if (e instanceof AbstractArmyCommander c) return c.getFactionId();
        if (e instanceof AbstractArmySoldier s) return s.getFactionId();
        if (e instanceof Player p) return FactionIds.player(p.getUUID());
        if (e instanceof WarHorseEntity h) {
            UUID o = h.getOwnerUuid();
            if (o == null || !(h.level() instanceof ServerLevel sl)) return null;
            var owner = sl.getEntities().get(o);
            if (owner instanceof LivingEntity le) return factionOf(le);
        }
        return null;
    }

    public boolean isSameArmy(LivingEntity other) {
        UUID mine = resolveArmyUuid(this, level());
        if (mine == null) return false;
        UUID otherArmy = resolveArmyUuid(other, level());
        return mine.equals(otherArmy);
    }

    public void teleportWithMount(double x, double y, double z, boolean playSound) {
        this.getNavigation().stop();
        Entity vehicle = getVehicle();
        if (vehicle instanceof Mob mobVehicle) {
            mobVehicle.getNavigation().stop();
        }

        if (vehicle != null) {
            vehicle.setPos(x, y, z);
            vehicle.setDeltaMovement(Vec3.ZERO);
            vehicle.fallDistance = 0;
            vehicle.hurtMarked = true;
        }
        this.setPos(x, y, z);
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
        this.hurtMarked = true;

        this.lastStuckX = x;
        this.lastStuckY = y;
        this.lastStuckZ = z;
        // 传送不算"走过的距离"，同时避免传送后的位置跳变污染绕路/徘徊判定。
        this.traveledSinceCheck = 0;
        this.lastTravelX = x;
        this.lastTravelZ = z;
        this.lastTargetDistSq = -1;
        clearRoute();
        clearMarchRoute();
        // 清掉移动控制器里的旧目标：否则传送后还会掉头朝传送前那个旧点走
        AiNavUtil.stopMovementInput(this);
        if (vehicle instanceof Mob mobVehicle) {
            AiNavUtil.stopMovementInput(mobVehicle);
        }

        if (playSound && level() instanceof ServerLevel sl) {
            sl.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT,
                    SoundSource.HOSTILE, 1.0f, 1.0f + (this.getRandom().nextFloat() - 0.5f) * 0.3f);
        }
    }

    /**
     * 跟随传送（玩家士兵专用）：静音 + 落在 around 单位
     *   "水平距离 > minDistance 格"的安全落脚点，避免和玩家重叠。
     *   找不到安全点 → 不传送（返回 false），下一 tick 再试。
     */
    public boolean teleportNearSafely(LivingEntity around, double minDistance) {
        Vec3 safe = findSafeSpotNear(
                around.getX(), around.getY(), around.getZ(), minDistance);
        if (safe == null) return false;
        teleportWithMount(safe.x, safe.y, safe.z, false);
        return true;
    }

    @Override
    public void tick() {

        super.tick();

        if (getVehicle() instanceof WarHorseEntity horse) {
            Float locked = horse.getLockedYaw();
            float yaw = (locked != null) ? locked : horse.getYRot();
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.yBodyRot = yaw;
            this.yBodyRotO = yaw;
        }

        if (level().isClientSide) return;
        if (!this.isAlive()) return;

        // 玩家刚下了新的"前往 / 驻守"命令 → 立刻停战让路（移动命令优先）
        syncPlayerMoveOrder();

        // 爬梯子：目标是上方、身边 / 脚下有梯子时自动攀爬（攻城时也能顺着梯子上去）
        AiNavUtil.tryClimbLadder(this, resolveClimbGoalY());
        // 调试诊断（general-common.toml 的 debugMovementLog）：无论当前由哪个 Goal 驱动，
        //   每秒记录一次移动状态，用于排查"上马后原地转圈 / 迷宫半路停下"。
        logMovementDiag();
        // 移动控制器"串线"保护：骑乘时坐骑的移动目标点离自己超过 6 格，
        //   说明是上马前 / 旧路径的残留（原版路径节点都是相邻格，不可能这么远）。
        //   不清掉的话，快速坐骑会掉头朝那个旧点走 —— 表现为到目标前原地转圈。
        if (getVehicle() instanceof Mob mount) {
            double wx = mount.getMoveControl().getWantedX() - mount.getX();
            double wz = mount.getMoveControl().getWantedZ() - mount.getZ();
            if (wx * wx + wz * wz > 36.0) {
                AiNavUtil.stopMovementInput(mount);
            }
            // 骑手自己的移动控制器同样可能残留旧点（下马后会被带着跑）→ 一并清掉
            double rx = getMoveControl().getWantedX() - getX();
            double rz = getMoveControl().getWantedZ() - getZ();
            if (rx * rx + rz * rz > 36.0) {
                AiNavUtil.stopMovementInput(this);
            }
        }
        // 平地在门边被原版"台阶跳"顶起来 → 压掉（门不是坎）
        AiNavUtil.suppressDoorJump(this);
        // 通用上坎 / 上岸跳跃：卡在台阶 / 坎前、或在水里贴岸时按原版跳跃
        if (!AiNavUtil.isOnClimbable(this)) {
            AiNavUtil.tryStepUpJump(this);
        }
        // 贴角脱困：顶着方块、几乎不动的状态持续一会儿 → 朝有空间的侧面侧移一小步 + 重新规划
        tickCornerUnstick();
        // 每 5 tick 主动开一次附近的木门 / 栅栏门 / 木活板门：
        //   只等卡住判定才开的话，单位会在门前先顶一会儿。
        if (tickCount % 5 == 0) {
            AiNavUtil.tryOpenNearbyDoors(this);
        }

        // 和平模式：单位保留，但清掉目标与绕路（不结仇、不攻击）
        if (PeacefulUtil.isPeaceful(this)) {
            if (super.getTarget() != null) super.setTarget(null);
            if (isFollowingRoute()) clearRoute();
            if (isFollowingMarchRoute()) clearMarchRoute();
        }

        // 仇恨重置（重置工具 / 和平模式）：清掉目标、仇恨与追击路线
        long resetStamp = ThreatDispatcher.hatredResetStamp();
        if (resetStamp != lastHatredResetStamp) {
            lastHatredResetStamp = resetStamp;
            if (super.getTarget() != null) super.setTarget(null);
            if (isFollowingRoute()) clearRoute();
            if (isFollowingMarchRoute()) clearMarchRoute();
            setLastHurtByMob(null);
        }

        refreshPlayerOrder();

        // 玩家部队“离队维持加载”：还在主人附近时续一张短时加载票 ——
        //   主人突然传送 / 快速跑远后，部队还能多撑几秒（unitKeepLoadedSeconds），
        //   20 tick 一次的“跟随传送”检查来得及把士兵带回主人身边。
        tickKeepLoaded();

        if (isAttackStopped() && getTarget() != null) setTarget(null);

        // 硬驻守的近战 / 投掷兵：目标超出锚点 48 格 → 立即清除。
        //   每 tick 执行（getTarget() == null 时立即返回，成本可忽略）。
        enforceHardStationAnchor();

        // 锚点保护（村庄巡逻队 / 袭击增援）：目标与自身都不能离开锚点范围太远。
        enforceAnchorLeash();

        if (getNavigation() instanceof GroundPathNavigation gpn) gpn.setCanOpenDoors(true);
        applyWaterFloat();
        if (armyAge % 4 == 0) applySeparation();

        if (isDrowned()) this.setAirSupply(this.getMaxAirSupply());

        if (isDrowned() && this.isPassenger()) {
            Entity v = this.getVehicle();
            if (this.isInWater() || (v != null && v.isInWater())) switchToInfantry();
        }

        final SoldierType type = getSoldierType();
        armyAge++;
        updateChargeSpeed();

        if (type.isCavalry() && !this.isPassenger()) {
            switchToInfantry();
        }

        // 玩家切换骑乘模式（上马 / 下马 / 默认）→ 立即触发形态评估，
        //   不再等 GeneralConfig.formSwitchCooldown 冷却（默认 40 tick = 2 秒）。
        if (isPlayerArmy()) {
            Player rideOwner = getCommanderPlayer();
            if (rideOwner != null) {
                var rideState = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(rideOwner.getUUID());
                if (rideState != null) {
                    int curRide = com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(this, rideState)
                            .getRideMode().ordinal();
                    if (curRide != lastRideModeOrdinal) {
                        lastRideModeOrdinal = curRide;
                        formCheckCooldown = 0;
                    }
                }
            }
        }

        // 募兵站"待命"：编入队伍 / 接到命令前强制步兵（不自动上马）
        tickRecruitStandby();

        if (--formCheckCooldown <= 0) {
            formCheckCooldown = GeneralConfig.formSwitchCooldown;
            evaluateFormSwitch();
        }

        if (terrainDismountCooldown > 0) terrainDismountCooldown--;

        controlMount();
        tickDualWeapon();
        tickLancePierce();
        tickStuckCheck();
        tickRoute();
        tickMarchRoute();
        tickTerrainDismountCheck();

        LivingEntity curTarget = getTarget();
        if (curTarget instanceof Player cp && (cp.isCreative() || cp.isSpectator())) {
            setTarget(null);
        } else if (curTarget != null && curTarget.isAlive()) {
            lastCombatTick = this.tickCount;
        }

        if (armyAge % 20 == 0) {
            LivingEntity myTarget = getTarget();

            if (myTarget != null && myTarget.isAlive()
                    && !isPlayerArmy()
                    && !isSameArmy(myTarget)
                    && tickCount - lastThreatBroadcastTick
                    >= THREAT_BROADCAST_INTERVAL_TICKS) {
                lastThreatBroadcastTick = tickCount;
                ThreatDispatcher.propagate(this, myTarget);
            }

            AbstractArmyCommander commander = getCommander();
            if (commander != null) {
                // 编制登记：掉队跑出兵编制半径后，首领仍能继续给自己发阵型槽位
                commander.noteSoldierPresence(this);
                if (myTarget != null && myTarget.isAlive() && !isSameArmy(myTarget)) {
                    commander.markAsHatred(myTarget);
                }
                LivingEntity cur = getTarget();
                if (cur == null || !cur.isAlive()) {
                    LivingEntity pick = pickHatredTarget(commander);
                    if (pick != null && canAttack(pick)) setTarget(pick);
                }
            }
        }

        if (isPlayerArmy() && armyAge % 20 == 0) {
            Player owner = getCommanderPlayer();
            // 只有"已经归属玩家指挥"的士兵才会跟随 / 被传送回玩家身边；
            //   刚放出来、没编队也没下过命令的士兵保持自然游走。
            if (owner != null && owner.isAlive() && isPlayerCommanded()) {
                var cmdState = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                var ss = cmdState != null
                        ? com.ohhapple.monster_conquest.player.PlayerCommandState
                                .stateForCommand(this, cmdState)
                        : null;

                boolean accepted = playerOrderAccepted && ss != null;

                boolean isStation = ss != null
                        && ss.getMovement()
                        == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION;
                boolean isSoftFollow = ss != null && accepted
                        && ss.getMovement()
                        == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.SOFT_FOLLOW;
                boolean isFollow = !isStation && !isSoftFollow && (ss == null
                        || !accepted
                        || ss.getMovement()
                        == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FOLLOW);

                boolean volleyActive = ss != null
                        && ss.isVolleyActive(level().getGameTime());

                boolean inCombat = curTarget != null && curTarget.isAlive();

                if (!volleyActive) {
                    if (isSoftFollow) {
                        // 软跟随：有战斗 / 有共享仇恨 → 不传送
                        long nowGT = level().getGameTime();
                        if (cmdState != null) {
                            cmdState.tickSharedHatred(nowGT);
                            cmdState.tickSharedHatredEntities(nowGT);
                        }
                        boolean hasSharedHatred = cmdState != null
                                && (!cmdState.getSharedHatredArmies().isEmpty()
                                || !cmdState.getSharedHatredEntities().isEmpty());
                        if (!inCombat && !hasSharedHatred) {
                            double distSq = distanceToSqr(owner);
                            double teleportDist = PlayerConfig.playerSoldierTeleportDistance;
                            if (distSq > teleportDist * teleportDist) {
                                // 静音 + 玩家 3 格外的安全处
                                teleportNearSafely(owner, 3.0);
                            }
                        }
                    } else if (isFollow) {
                        double distSq = distanceToSqr(owner);
                        boolean combatNow = inCombat;
                        double teleportDist = combatNow
                                ? PlayerConfig.playerSoldierCombatTeleportDistance
                                : PlayerConfig.playerSoldierTeleportDistance;
                        // 战斗中只有"确实卡住走不动"才允许传送：追击敌人 / 绕路超距都属于正常机动，
                        //   不能因为离玩家远就瞬移回来（非战斗的归队传送维持原规则）。
                        if (distSq > teleportDist * teleportDist
                                && (!combatNow || isStuckEnoughToTeleport())) {
                            // 静音 + 玩家 3 格外的安全处
                            teleportNearSafely(owner, 3.0);
                        }
                    } else if (isStation && !ss.isSoftStation()
                            && ss.getFormationAnchor() != null
                            && !inCombat) {
                        long changedGT = ss.getAnchorChangedGameTime();
                        long nowGT = level().getGameTime();
                        boolean recentChange = changedGT < 0 || (nowGT - changedGT) < 100;
                        // 还挂着"前往指定点"命令时不传送：命令点就是锚点（目的地），
                        //   "拉回锚点"等于把单位瞬移到目的地；只有命令已被后续命令覆盖、
                        //   且真的卡住走不动（约 10 秒没进展）才允许把走失的单位传送回驻守点。
                        if (!recentChange
                                && !ss.hasMoveOrder()
                                && isStuckEnoughToTeleport()) {
                            Vec3 anchor = ss.getFormationAnchor();
                            double distSq = position().distanceToSqr(anchor);
                            double maxD = 128.0;
                            if (distSq > maxD * maxD) {
                                Vec3 safe = findSafeSpotNear(anchor.x, anchor.y, anchor.z);
                                teleportWithMount(safe.x, safe.y, safe.z, true);
                            }
                        }
                    }
                }
            }
        }

        if (armyAge % 20 == 0) {
            LivingEntity tgt = getTarget();
            if (tgt != null && tgt.isAlive()) {
                AbstractArmyCommander cmd = getCommander();
                if (cmd != null && cmd.isAlive()) {
                    double maxD = CombatConfig.maxCombatDistance;
                    if (distanceToSqr(cmd) > maxD * maxD) {
                        // 超出跟随范围：先寻路回来（能绕墙 / 开门就走路回去）。
                        //   只有"确实卡住走不动"（连续多轮没走动）才允许传送 ——
                        //   追击敌人跑远了不算卡住；规划被全局节流推迟也不算没有路。
                        if (!routeBackTo(cmd) && isStuckEnoughToTeleport()) {
                            double angle = getRandom().nextDouble() * Math.PI * 2;
                            double offset = 2 + getRandom().nextDouble() * 3;
                            teleportWithMount(cmd.getX() + Math.cos(angle) * offset,
                                    cmd.getY(), cmd.getZ() + Math.sin(angle) * offset, true);
                        }
                    }
                }
            }
        }

        EquipmentTier next = getTier().getNext();
        if (next != null && armyAge >= tierUpgradeTicks(next) && canAutoUpgrade()) {
            upgradeTo(next);
        }

        tickRegen();
    }

    @Nullable
    private LivingEntity pickHatredTarget(AbstractArmyCommander commander) {
        List<LivingEntity> hatred = commander.getHatredList();
        if (hatred.isEmpty()) return null;
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : hatred) {
            if (e == this || !e.isAlive()) continue;
            if (!canAttack(e)) continue;
            double d = e.distanceToSqr(this);
            d *= 0.9 + getRandom().nextDouble() * 0.2;
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }


    /**
     * 硬驻守锚点保护：每 tick 清除超出锚点允许范围的目标，
     *   判定统一走 {@code PlayerCommandState.allowsHardStationTarget}。
     */
    private void enforceHardStationAnchor() {
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return;

        Player owner = getCommanderPlayer();
        if (owner == null) return;

        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return;

        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st);

        if (!com.ohhapple.monster_conquest.player.PlayerCommandState
                .allowsHardStationTarget(this, ss.getFormationAnchor(), t.position())) {
            setTarget(null);
        }
    }

    /**
     * 锚点保护（村庄巡逻队 / 袭击增援）：
     *   · 目标超出锚点活动半径 → 放弃目标（战斗 / 索敌留在村庄 / 袭击范围内）；
     *   · 自己超出活动半径 → 停止追击路线往回走，离得太远则直接传送回锚点附近；
     *   · 袭击增援：袭击结束后自动撤离（消失）。
     */
    private void enforceAnchorLeash() {
        if (!anchor.isBound()) return;

        // 袭击增援：袭击结束后撤离（每 5 秒检查一次，开销可忽略）
        if (anchor.isRaid() && this.tickCount % 100 == 0
                && level() instanceof ServerLevel sl
                && sl.getRaidAt(anchor.getPos()) == null) {
            this.discard();
            return;
        }

        if (PeacefulUtil.isPeaceful(this)) return;

        LivingEntity t = getTarget();
        if (t != null && !anchor.contains(t.position())) {
            setTarget(null);
        }

        double r = anchor.radius();
        double d2 = anchor.distanceSqr(this);
        if (d2 <= r * r) return;

        if (isFollowingRoute()) clearRoute();
        if (isFollowingMarchRoute()) clearMarchRoute();

        if (--anchorLeashCooldown > 0) return;
        anchorLeashCooldown = 20;

        double hard = r + 16.0;
        if (d2 > hard * hard && isStuckEnoughToTeleport()) {
            // 只有"确实卡住走不动"才传送回锚点：
            //   追击敌人 / 绕路时离锚点远属于正常机动，能走就自己走回去，不传送。
            Vec3 back = anchor.randomPointNear(getRandom(), 0.4);
            this.randomTeleport(back.x, back.y, back.z, false);
            // 传送回锚点后清掉移动控制器里的旧目标，避免又朝传送前的旧点走
            AiNavUtil.stopMovementInput(this);
        } else {
            Vec3 back = anchor.randomPointNear(getRandom(), 0.7);
            this.getNavigation().moveTo(back.x, back.y, back.z, 1.0);
        }
    }

    private void applySeparation() {

        if (isPassenger()) return;

        final double RADIUS = 1.0;
        AABB box = getBoundingBox().inflate(RADIUS, 0.5, RADIUS);
        List<AbstractArmySoldier> nearby = level().getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s != this && s.isAlive() && isSameArmy(s) && !s.isPassenger());
        if (nearby.isEmpty()) return;

        double pushX = 0, pushZ = 0;
        int count = 0;
        for (AbstractArmySoldier other : nearby) {
            double dx = getX() - other.getX();
            double dz = getZ() - other.getZ();
            double dSqr = dx * dx + dz * dz;
            if (dSqr < 1.0e-6) {
                double a = getRandom().nextDouble() * Math.PI * 2;
                dx = Math.cos(a) * 0.01;
                dz = Math.sin(a) * 0.01;
                dSqr = 1.0e-4;
            }
            if (dSqr >= RADIUS * RADIUS) continue;
            double d = Math.sqrt(dSqr);
            double strength = (RADIUS - d) / RADIUS;
            pushX += dx / d * strength;
            pushZ += dz / d * strength;
            count++;
        }
        if (count == 0) return;
        pushX /= count;
        pushZ /= count;

        double speed = 0.06;
        Vec3 dm = getDeltaMovement();
        setDeltaMovement(dm.x + pushX * speed, dm.y, dm.z + pushZ * speed);
        hasImpulse = true;
    }

    private boolean canAutoUpgrade() {
        if (isPlayerArmy()) return GeneralConfig.playerSoldierTierUpgrade;
        return true;
    }

    private boolean suffocating(LivingEntity e) {
        BlockPos feet = e.blockPosition();
        BlockPos middle = BlockPos.containing(e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ());
        BlockPos head = BlockPos.containing(e.getX(), e.getEyeY(), e.getZ());
        return level().getBlockState(feet).isSuffocating(level(), feet)
                || level().getBlockState(middle).isSuffocating(level(), middle)
                || level().getBlockState(head).isSuffocating(level(), head);
    }

    private void resetStuckTracking() {
        stuckAccumTicks = 0;
        wanderAccumTicks = 0;
        noPathAccumTicks = 0;
        traveledSinceCheck = 0;
        lastStuckX = this.getX();
        lastStuckY = this.getY();
        lastStuckZ = this.getZ();
        lastTravelX = this.getX();
        lastTravelZ = this.getZ();
        lastTargetDistSq = -1;
    }

    /**
     * 玩家部队“离队维持加载”（每 10 tick 一次）：只给“还在主人身边”的部队续加载票。
     *   主人传送 / 快速跑远后不再续票，最后一张票在 unitKeepLoadedSeconds 秒后过期，
     *   区块才真正卸载 —— 这段时间足够“跟随传送”把士兵带回主人身边。
     */
    private void tickKeepLoaded() {
        if (tickCount % 10 != 0) return;
        if (!ArmyChunkLoader.isEnabled()) return;
        if (!isPlayerArmy() || !isPlayerCommanded()) return;
        Player owner = getCommanderPlayer();
        if (owner == null || !owner.isAlive()) return;
        if (owner.level() != level()) return;
        // “还在身边”的范围取服务器的模拟距离再放宽一点：
        //   这段距离内本来就靠玩家的加载票保持加载，这里的票保证主人一走，
        //   部队手里至少有一张“最多 10 tick 前刚续过”的票。
        int simBlocks = owner.getServer().getPlayerList().getSimulationDistance() * 16;
        double range = simBlocks + 48.0;
        if (owner.distanceToSqr(this) > range * range) return;
        ArmyChunkLoader.keepLoaded((ServerLevel) level(), chunkPosition());
    }

    private void tickStuckCheck() {

        // 每 tick 累积"实际走过的水平距离"（骑乘时按坐骑位置算）。
        //   用途：区分"真的卡住没动"和"在动但没接近目标（绕路 / 来回徘徊 / 被挤）"。
        //   单 tick 位移 ≥4 格视为传送，不计入。
        Entity traveller = getVehicle() != null ? getVehicle() : this;
        double stepX = traveller.getX() - lastTravelX;
        double stepZ = traveller.getZ() - lastTravelZ;
        double stepDist = Math.sqrt(stepX * stepX + stepZ * stepZ);
        if (stepDist < 4.0) traveledSinceCheck += stepDist;
        lastTravelX = traveller.getX();
        lastTravelZ = traveller.getZ();

        if (++stuckCheckTimer < 40) return;
        stuckCheckTimer = 0;

        if (isVolleyActiveNow()) { resetStuckTracking(); return; }
        if (atFormationSlot) { resetStuckTracking(); return; }
        // 自由游走的士兵不参与卡住判定（否则会被误判并传送回玩家 / 敌人身边）
        if (isFreeRoaming()) { resetStuckTracking(); return; }

        // 硬驻守的远程兵（弓手 / 双形态 / 枪手）：本来就不该移动，不参与卡住判定，
        //   避免被误判为 stuck 后 teleportToSafety 把士兵传送到敌人处。
        if (isHardStationed()
                && (getRole().isArcher() || getRole().isDual() || getRole().isGunner())) {
            resetStuckTracking();
            return;
        }

        boolean suff = suffocating(this)
                || (getVehicle() instanceof LivingEntity mount && suffocating(mount));
        if (suff) { teleportToSafety(); resetStuckTracking(); return; }

        // 战斗中的卡住判定（新）：
        //   已经"打得到"目标时（弓手 / 枪手站桩输出、近战贴脸挥砍），原地不动是
        //   正常战斗状态 → 不累积卡住时间、不侧移、不传送。
        //   只有"一段时间没缩短距离 + 打不到敌人"才会走到下面的卡住累积与传送。
        //   非战斗（归队 / 跟随）不受影响，仍然按"没缩短距离"触发传送。
        LivingEntity combatTarget = getTarget();
        if (combatTarget != null && combatTarget.isAlive()
                && canCurrentlyAttack(combatTarget)) {
            resetStuckTracking();
            return;
        }

        Vec3 goal = resolveCurrentGoal();
        double currentDistSq = -1;
        boolean shouldMove = false;
        if (goal != null) {
            double gx = getX() - goal.x;
            double gz = getZ() - goal.z;
            currentDistSq = gx * gx + gz * gz;
            shouldMove = currentDistSq > 4.0;
        } else {
            LivingEntity leader = getLeaderEntity();
            if (leader != null) {
                double lx = getX() - leader.getX();
                double lz = getZ() - leader.getZ();
                currentDistSq = lx * lx + lz * lz;
                shouldMove = currentDistSq > 25.0;
            }
        }

        boolean pathActive;
        if (getVehicle() instanceof Mob mount) pathActive = !mount.getNavigation().isDone();
        else pathActive = !getNavigation().isDone();

        if (!pathActive && !shouldMove) { resetStuckTracking(); return; }

        double rdx = this.getX() - lastStuckX;
        double rdy = this.getY() - lastStuckY;
        double rdz = this.getZ() - lastStuckZ;
        double riderMovedSq = rdx * rdx + rdy * rdy + rdz * rdz;
        double mountMovedSq = 0;
        if (getVehicle() != null) {
            Entity m = getVehicle();
            double mdx = m.getX() - lastStuckX;
            double mdy = m.getY() - lastStuckY;
            double mdz = m.getZ() - lastStuckZ;
            mountMovedSq = mdx * mdx + mdy * mdy + mdz * mdz;
        }
        double movedSq = Math.max(riderMovedSq, mountMovedSq);

        boolean stuck = false;
        if (currentDistSq > 0 && lastTargetDistSq > 0) {
            double lastDist = Math.sqrt(lastTargetDistSq);
            double curDist = Math.sqrt(currentDistSq);
            double progress = lastDist - curDist;
            if (progress < 1.0) stuck = true;
        } else if (lastTargetDistSq > 0 && movedSq < 0.16) {
            stuck = true;
        }

        // 这 40 tick 里到底走没走动：只有"真的没动"才允许累计"寻路不通"并脱困传送；
        //   在动（战斗中追人 / 绕路 / 被同伴挤着挪）时"没接近目标"是正常现象，
        //   不拉黑目标、不传送。
        boolean reallyStuck = traveledSinceCheck < STUCK_TRAVEL_EPSILON;

        // 绝对距离 + 路径距离结合判定（限流）：
        //   只在"出现卡住 / 徘徊迹象"时才探测"到目标还有没有路"，且最多每 4 秒探测一次
        //   （路径规划开销大，不能每个士兵每 2 秒都跑一次，否则服务器会掉刻）。
        //   · 完全没有路线（例如掉进坑里爬不出来）：累计约 10 秒后脱困传送；
        //     战斗中寻路不通时不会传送到敌人附近，只把目标暂时拉黑；
        //   · 有路线（哪怕要绕很远）：属于绕路，不传送。
        boolean slowProgress = !stuck && currentDistSq > 0 && lastTargetDistSq > 0
                && Math.sqrt(lastTargetDistSq) - Math.sqrt(currentDistSq) < 1.0;
        if (!reallyStuck) {
            // 在动的单位不算"寻路不通"：清掉累计，避免"跑动中攒了计数、停下就立刻被传走"
            noPathAccumTicks = 0;
        } else if ((stuck || slowProgress)
                && tickCount - lastPathProbeTick >= PATH_PROBE_INTERVAL_TICKS) {
            lastPathProbeTick = tickCount;

            LivingEntity pathTarget = getTarget();
            boolean pathKnown = false;
            boolean pathExists = false;
            if (pathTarget != null && pathTarget.isAlive()) {
                pathExists = hasPathToTarget(pathTarget);
                pathKnown = true;
            } else if (goal != null) {
                pathExists = hasReachablePathTo(goal);
                pathKnown = true;
            } else {
                LivingEntity leaderForPath = getLeaderEntity();
                if (leaderForPath != null) {
                    pathExists = hasReachablePathTo(leaderForPath.position());
                    pathKnown = true;
                }
            }
            // 正在按方块级 A* 绕路走向指定点：这条路线本身就是"路"，
            //   不算"寻路不通"，也不该触发脱困传送（否则走到一半被传上墙顶）。
            if (isFollowingMarchRoute()) {
                noPathAccumTicks = 0;
            } else if (pathKnown && !pathExists) {
                noPathAccumTicks += PATH_PROBE_INTERVAL_TICKS;
                if (noPathAccumTicks >= NO_PATH_TELEPORT_TICKS) {
                    // 战斗中寻路不通：不传送到敌人附近（攻城战不会直接上城墙），
                    //   只把目标暂时拉黑，然后就地脱困（掉坑 / 掉沟会往上找开口）。
                    if (pathTarget != null && pathTarget.isAlive()) {
                        markTargetUnreachable(pathTarget);
                        setTarget(null);
                    }
                    teleportToSafety();
                    resetStuckTracking();
                    return;
                }
            } else {
                noPathAccumTicks = 0;
            }
        }

        if (stuck) {
            AiNavUtil.tryOpenNearbyDoors(this);

            // 骑兵专属：卡住 + 前方 1.5 / 2.5 格处"马 + 骑手"站不下
            //   → 判定为骑马过不去的窄道 / 低矮通道，主动下马步行
            //   （下马后 6 秒内不自动上马，走完这段路地形开阔会自动再骑上）。
            if (getVehicle() instanceof WarHorseEntity) {
                Vec3 ref = resolveCurrentGoal();
                if (ref == null) {
                    LivingEntity leader = getLeaderEntity();
                    if (leader != null) ref = leader.position();
                }
                if (tryDismountForMountTerrain(ref)) return;
            }

            // 再按"这个窗口里到底走了多远"细分，避免短距离来回徘徊被当成卡住：
            //   · 实际移动 < 2 格 → 真·卡住（撞墙 / 卡在地形里）→ 维持 20 秒传送；
            //   · 实际移动 ≥ 2 格但净进展 < 1 格 → 绕路 / 来回徘徊 / 被同伴挤开 →
            //     不计入"真卡住"计时，改用 60 秒兜底（期间只尝试清路线重新规划脱困）。
            if (reallyStuck) {
                stuckAccumTicks += 40;
            } else {
                wanderAccumTicks += 40;
            }

            // 只有"真的没动"才侧移脱困：在动（战斗中追人 / 绕路）时侧移只会打断追击。
            if (stuckAccumTicks >= 60 && stuckAccumTicks < 300) trySideStep();

            // 战斗中目标"完全不可达"（连绕路都规划不出来）→ 卡住 10 秒后直接传送脱困；
            //   能规划出完整绕路就开始按路线走，不再累计卡住时间。
            LivingEntity combat = getTarget();
            // 只有"真的卡住没动"（stuckAccumTicks）才允许走传送分支；
            //   "在动但没接近目标"（绕路 / 徘徊 / 被挡在墙外）不传送。
            //   还要求"连续多轮路径探测都找不到路"（noPathAccumTicks），
            //     避免某一次探测被限流 / 瞬时失败就把单位传走。
            if (stuckAccumTicks >= 200 && combat != null && combat.isAlive()
                    && noPathAccumTicks >= NO_PATH_TELEPORT_TICKS) {
                if (hasPathToTarget(combat)) {
                    // 有路：继续按路线走，不传送
                    resetStuckTracking();
                    return;
                }
                // 寻路不通：不传送到敌人附近（防止攻城战直接上城墙），
                //   把目标暂时拉黑后就地脱困。
                markTargetUnreachable(combat);
                setTarget(null);
                teleportToSafety();
                resetStuckTracking();
                return;
            }

            // 真的卡住 20 秒 → 传送脱困（战斗中按配置决定是否落到敌人附近）
            if (stuckAccumTicks >= 400) {
                teleportToSafety();
                resetStuckTracking();
                return;
            }

            // 长时间"在动但没接近目标"：清掉当前绕路路线，重新规划一次——
            //   不再传送到敌人身边，避免"明明没卡住却瞬移到目标旁边"。
            if (wanderAccumTicks >= WANDER_TELEPORT_TICKS) {
                clearRoute();
                wanderAccumTicks = 0;
            }
        } else {
            if (currentDistSq > 0 && lastTargetDistSq > 0) {
                double lastDist = Math.sqrt(lastTargetDistSq);
                double curDist = Math.sqrt(currentDistSq);
                if (lastDist - curDist >= 1.0) {
                    stuckAccumTicks = 0;
                    wanderAccumTicks = 0;
                }
            } else if (movedSq > 1.0) {
                stuckAccumTicks = 0;
                wanderAccumTicks = 0;
            }
        }

        lastStuckX = this.getX();
        lastStuckY = this.getY();
        lastStuckZ = this.getZ();
        lastTargetDistSq = currentDistSq;
        traveledSinceCheck = 0;
    }

    /**
     * 当前是否打得到目标（只用于卡住判定）：最近攻击过直接算能打；
     *   否则按兵种判定距离与视线（近战身体视线、远程射程 + 射界、投掷 / 施法距离）。
     */
    public boolean canCurrentlyAttack(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        // 最近 5 秒内确实成功攻击过（近战命中 / 成功开火 / 穿刺命中）
        //   → 说明"打得到"，原地输出是正常战斗状态（弓手抛射越障也算）。
        if (tickCount - lastAttackSuccessTick <= 100) return true;
        return canAttackSpatially(target);
    }

    /** 空间上能否打到目标（距离 / 视线判定，不含"最近攻击过"）。 */
    private boolean canAttackSpatially(LivingEntity target) {
        double distSq = distanceToSqr(target);
        boolean mounted = getSoldierType().isCavalry();
        SoldierRole role = getRole();

        // —— 近战 / 双形态：够得着 + 身体视线（与新的防穿墙规则一致）——
        if (role.isMelee() || role.isDual()) {
            double reach = isPassenger() ? getMountedMeleeReach()
                    : SoldierMeleeAttackGoal.MELEE_REACH;
            double withMargin = reach + 1.5;
            // 高度差同样受限：墙头 / 高台上的目标不算"打得到"
            if (distSq <= withMargin * withMargin
                    && Math.abs(getY() - target.getY()) <= SoldierMeleeAttackGoal.MAX_MELEE_Y_DIFF
                    && hasBodyLineOfSight(target)) return true;
            if (role.isMelee()) return false;
        }

        // —— 弓 / 枪 / 双形态远程：射程内且有射界 ——
        if (role.isArcher() || role.isGunner() || role.isDual()) {
            double max = getMaxAttackRange(mounted) + 4.0;
            return distSq <= max * max && hasClearShot(target);
        }

        // —— 投掷（女巫 / 牧师）：投掷药水的出手距离 ——
        if (role.isThrower()) {
            double max = WitchSupportGoal.MAX_THROW_RANGE + 2.0;
            return distSq <= max * max;
        }

        // —— 魔法师（唤魔者）：尖牙施法距离 ——
        if (role.isMage()) {
            double max = EvokerSoldier.FANG_CAST_RANGE + 3.0;
            return distSq <= max * max && hasBodyLineOfSight(target);
        }
        return false;
    }

    /** 记录一次成功攻击（近战命中 / 成功开火 / 穿刺命中）。 */
    public void markAttackSuccess() {
        this.lastAttackSuccessTick = this.tickCount;
    }

    /**
     * 重新规划到达目标的路线：先清掉局部移动，再完整寻路到目标本体（绕墙 / 穿门），
     *   必要时放宽寻路半径，最后退回方块级 A* 绕路规划。
     *
     * @return 是否成功规划出路线
     */
    public boolean replanRouteTo(LivingEntity target) {
        return replanRouteTo(target, false);
    }

    /**
     * 重新规划到达目标的路线（先清掉局部移动再完整寻路，必要时放宽半径，最后退回 A* 绕路）。
     *
     * @param onlyIfReachable true = 只在能规划出"真正到达目标"的路线时才算成功（用于仇恨过期判定）
     * @return 是否成功规划出路线
     */
    public boolean replanRouteTo(LivingEntity target, boolean onlyIfReachable) {
        if (target == null || !target.isAlive()) return false;

        // 已有有效路线 → 继续沿路线走，不重复规划
        if (!routePoints.isEmpty() && routeTargetId != null
                && routeTargetId.equals(target.getUUID())
                && tickCount <= routeExpireTick) {
            return true;
        }

        // 战斗追击这类"每 tick 反复喊"的调用要限流：同一个目标短时间内只真正规划一次，
        //   失败后按次数指数退避（最多 6 秒一次）——对着墙后 / 封闭空穴里的敌人反复狂算
        //   是服务器运算卡顿的主要来源。
        //   可达性探测（onlyIfReachable）由调用方自己限流（探测缓存），不在这里挡。
        if (!onlyIfReachable) {
            boolean sameTarget = target.getUUID().equals(lastReplanTargetId);
            if (sameTarget && tickCount - lastReplanTick < replanRetryTicks) return false;
            if (!sameTarget) replanRetryTicks = REPLAN_MIN_RETRY_TICKS;
            boolean ok = tryReplanRouteTo(target, onlyIfReachable);
            boolean deferred = AiNavUtil.wasLastHeavyAttemptDeferred();
            lastReplanTargetId = target.getUUID();
            lastReplanTick = tickCount;
            if (ok) {
                replanRetryTicks = REPLAN_MIN_RETRY_TICKS;
            } else if (!deferred) {
                // 真的规划失败才退避；"本 tick 名额被占满"只是稍后重试
                replanRetryTicks = Math.min(REPLAN_MAX_RETRY_TICKS,
                        Math.max(REPLAN_MIN_RETRY_TICKS, replanRetryTicks * 2));
            }
            return ok;
        }
        return tryReplanRouteTo(target, onlyIfReachable);
    }

    /** 真正的重规划实现：见 {@link #replanRouteTo(LivingEntity, boolean)}。 */
    private boolean tryReplanRouteTo(LivingEntity target, boolean onlyIfReachable) {

        if (target == null || !target.isAlive()) return false;

        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        PathNavigation nav = mover.getNavigation();

        // 普通重规划也有全局名额（每 tick 3 个）：整队同一 tick 一起喊"去追谁"时，
        //   不会每个单位都跑一遍完整寻路，而是摊到接下来几个 tick。
        if (!AiNavUtil.acquireQuickRouteAttempt(level().getGameTime())) {
            AiNavUtil.markHeavyAttemptDeferred(true);
            return false;
        }
        AiNavUtil.markHeavyAttemptDeferred(false);
        nav.stop();

        double speed = 1.0D;

        // 1) 先用兵种自己的寻路半径规划一次：能走到目标本体就直接用。
        if (nav.moveTo(target, speed) && pathReaches(nav)) return true;

        // 昂贵尝试的全局闸门（放宽半径、最多十次原版寻路、自研 A*）：
        //   同一 tick 只放行一个单位，整队同时锁定走不到的目标时不会一起硬算。
        if (!AiNavUtil.acquireHeavyRouteAttempt(level().getGameTime())) {
            AiNavUtil.markHeavyAttemptDeferred(true);
            return false;
        }
        AiNavUtil.markHeavyAttemptDeferred(false);

        // 2) 只走到"最接近目标的墙边"（半路路径）→ 说明绕路距离超过了
        //    兵种寻路半径（近战 32 / 弓手 64 …）。这里临时把寻路半径放宽到
        //    GeneralConfig.armyFollowRange、搜索预算 ×4，再完整规划一次——
        //   这次能找到"绕墙 / 穿门"的真实路线。
        // 用 mover 的属性：骑乘时寻路是坐骑在跑（马有自己的 FOLLOW_RANGE）
        AttributeInstance followAttr = mover.getAttribute(Attributes.FOLLOW_RANGE);
        double originalFollow = followAttr != null ? followAttr.getBaseValue() : 0.0D;
        try {
            if (followAttr != null) {
                followAttr.setBaseValue(Math.max(originalFollow, GeneralConfig.armyFollowRange));
            }
            // 不再放大节点预算：复杂地形下单次原版寻路会从 ~10ms 涨到 ~40-100ms，
            //   那是服务器运算卡顿的主要来源；长绕路交给自研 A*（有节点/时间预算）。
            if (nav.moveTo(target, speed) && pathReaches(nav)) return true;

            // 3) 目标本体脚下站不下（敌人紧贴墙 / 站在方块边缘）：
            //    改用目标周围 8 个相邻点作为终点再试。
            //    先确认落点站得住（墙里 / 悬空 / 封闭空穴直接跳过）：对着封闭盒子里的
            //    敌人时这能省掉 8 次注定失败的大范围寻路。
            //    最多只试 4 个点：每个点都是一次完整寻路，全试完太贵。
            int tried = 0;
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2.0 * i / 8.0;
                double x = target.getX() + Math.cos(angle) * 1.5;
                double z = target.getZ() + Math.sin(angle) * 1.5;
                double y = AiNavUtil.groundNear(level(), x, target.getY(), z);
                if (Double.isNaN(y)) continue;
                if (++tried > 2) break;
                if (nav.moveTo(x, y, z, speed) && pathReaches(nav)) return true;
            }

            // 4) 原版只能给出"最接近目标"的半路路径 → 用方块级 A* 单独算一条绕路路线
            //   （onlyIfReachable 的可达性判定不走节流，保证结论准确）
            if (onlyIfReachable || tickCount - lastRoutePlanTick >= 40) {
                lastRoutePlanTick = tickCount;
                double goalRadius = routeGoalRadius();
                double planRadius = Math.min(96.0, Math.sqrt(distanceToSqr(target)) + 24.0);
                boolean mounted = getVehicle() instanceof WarHorseEntity;
                double extraHeight = mounted ? WarHorseEntity.RIDER_HEIGHT : 0.0;
                java.util.List<net.minecraft.core.BlockPos> favored =
                        routeTargetId != null && routeTargetId.equals(target.getUUID())
                                ? lastRoutePoints : null;
                RoutePlanner.Result planned = RoutePlanner.plan(
                        level(), mover, target.position(), goalRadius, planRadius, 4200,
                        extraHeight, favored);

                // 骑兵路线走不通（或只能到半路）→ 按步兵净空再规划一次：
                //   骑兵过不去的窄道 / 低矮通道只要步兵能走，就立即下马步行前往。
                if (mounted && !isForcedCavalry()
                        && (planned == null || !planned.reachedGoal())) {
                    RoutePlanner.Result onFoot = RoutePlanner.plan(
                            level(), this, target.position(), goalRadius, planRadius, 4200,
                            0.0, favored);
                    if (onFoot != null && onFoot.points().size() >= 2
                            && (planned == null || onFoot.reachedGoal()
                            || routeEndDistance(onFoot, target)
                                    < routeEndDistance(planned, target) - 1.0)) {
                        switchToInfantry();
                        terrainDismountCooldown = 120;
                        planned = onFoot;
                        mover = this;
                        nav = getNavigation();
                    }
                }

                if (planned != null && planned.points().size() >= 2) {
                    if (!onlyIfReachable || planned.reachedGoal()) {
                        startRoute(planned.points(), target, planned.reachedGoal());
                        return true;
                    }
                }
            }

            if (onlyIfReachable) return false;

            // 5) 还是到不了 → 退回"最接近目标"的半路路径（继续朝目标推进，
            //    后续卡住检测 / 下马 / 传送会接管）。
            return nav.moveTo(target, speed);
        } finally {
            if (followAttr != null) followAttr.setBaseValue(originalFollow);
            nav.resetMaxVisitedNodesMultiplier();
        }
    }

    /** 规划路线终点到目标的距离（用于比较骑兵 / 步兵两条部分路线谁更接近目标）。 */
    private static double routeEndDistance(RoutePlanner.Result route, LivingEntity target) {
        if (route == null || route.points().isEmpty()) return Double.MAX_VALUE;
        net.minecraft.core.BlockPos end = route.points().get(route.points().size() - 1);
        return Math.sqrt(end.distToCenterSqr(target.getX(), target.getY(), target.getZ()));
    }

    /** 绕路规划的目标半径：近战进入攻击距离，远程接近到能看见目标。 */
    private double routeGoalRadius() {
        SoldierRole role = getRole();
        if (role.isMelee() || role.isDual()) {
            double reach = isPassenger() ? getMountedMeleeReach()
                    : SoldierMeleeAttackGoal.MELEE_REACH;
            return reach + 0.5;
        }
        if (role.isMage()) return 6.0;
        return 10.0;
    }

    /** 是否正在按规划路线绕路前进。 */
    public boolean isFollowingRoute() { return !routePoints.isEmpty(); }

    /** 是否有"能真正到达该目标"的路线在跟随中（部分路线不算）。 */
    public boolean hasReachingRoute(LivingEntity target) {
        return target != null
                && !routePoints.isEmpty()
                && routeReachesGoal
                && routeTargetId != null
                && routeTargetId.equals(target.getUUID())
                && tickCount <= routeExpireTick;
    }

    /**
     * 到指定坐标是否存在可行路线（跟随 / 卡住判定用：绝对距离 vs 路径距离）。
     *   骑乘时用坐骑的导航（马在跑）。
     */
    public boolean hasReachablePathTo(@Nullable Vec3 target) {
        if (target == null) return false;
        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        net.minecraft.world.level.pathfinder.Path path =
                mover.getNavigation().createPath(BlockPos.containing(target), 1);
        return path != null && path.canReach();
    }

    /**
     * 到目标有没有可行路线：先看已有的自研路线，再用原版寻路探一次，
     *   最后才动用代价较高的自研 A* 完整规划（能绕墙 / 开门）。
     *   同一目标最近确认过"完全不可达"时直接沿用结论（探测缓存），
     *   避免对着墙后的敌人反复空跑大范围规划；
     *   已在索敌黑名单里的目标同样直接按不可达处理。
     *   调用方需要自己做限流（见 tickStuckCheck 的 PATH_PROBE_INTERVAL_TICKS）。
     */
    public boolean hasPathToTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (hasReachingRoute(target)) {
            clearReachProbeMemo();
            return true;
        }
        // 最近已经确认过这个目标不可达：窗口内直接返回，避免每次探测都空跑一次 A*
        if (isTargetBlacklisted(target)) return false;
        if (reachProbeMemoId != null && reachProbeMemoId.equals(target.getUUID())
                && tickCount < reachProbeMemoUntil) {
            return false;
        }
        if (hasReachablePathTo(target.position())) {
            clearReachProbeMemo();
            return true;
        }
        boolean planned = replanRouteTo(target, true);
        // 真的规划不出来（不是"本 tick 预算被占满"）→ 记下结论，这段时间内不再重复空算。
        if (!planned && !marchRoute.isStartDeferred()
                && !RoutePlanner.wasLastCallDeferred()
                && !AiNavUtil.wasLastHeavyAttemptDeferred()) {
            reachProbeMemoId = target.getUUID();
            reachProbeMemoUntil = tickCount + REACH_PROBE_MEMO_TICKS;
        }
        return planned;
    }

    /** 清掉"完全不可达"探测缓存（探测到可行路线时）。 */
    private void clearReachProbeMemo() {
        reachProbeMemoId = null;
        reachProbeMemoUntil = 0;
    }

    /** 该目标是否在"寻路不通"黑名单里（索敌时跳过，避免反复顶着城墙跑）。 */
    public boolean isTargetBlacklisted(@Nullable LivingEntity target) {
        return target != null && unreachableTargetId != null
                && unreachableTargetId.equals(target.getUUID())
                && tickCount < unreachableTargetUntil;
    }

    /** 当前绕路路线是否正通向这个指定点（中途改命令时用来判断是否要重新规划）。 */
    public boolean isFollowingMarchTowards(Vec3 target) {
        return marchRoute.isFollowingTowards(target);
    }

    /** 当前想去的目标高度（战斗目标 / 首领 / 自己），用于爬梯子判断。 */
    private double resolveClimbGoalY() {
        LivingEntity t = getTarget();
        if (t != null && t.isAlive()) return t.getY();
        // 手动指令指定的点：目标在梯子 / 脚手架 / 藤蔓上方时也要能爬上去
        if (manualMoveTarget != null) return manualMoveTarget.y;
        // 玩家军队指定驻守点（RTS / 控制工具的小队命令）
        if (isPlayerArmy()) {
            Player owner = getCommanderPlayer();
            if (owner != null) {
                var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (state != null) {
                    var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(this, state);
                    if (ss.getMovement()
                            == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION
                            && ss.getFormationAnchor() != null) {
                        // 优先用"自己该站的阵位"高度：命令点落在墙上 / 半空时，
                        //   用锚点高度会让路过的士兵莫名其妙去扒梯子（扒到一半悬着不动）。
                        Vec3 slot = ss.getFormationSlots().get(getUUID());
                        return slot != null ? slot.y : ss.getFormationAnchor().y;
                    }
                    // 自由游走：不追主人的高度 —— 否则"自然游走"的单位会莫名其妙往梯子跑、
                    //   爬到主人所在的那一层。梯子只服务真有移动目标的情况（驻守 / 跟随 / 行军）。
                    if (ss.getMovement()
                            == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FREE) {
                        return getY();
                    }
                    LivingEntity follow = getLeaderEntity();
                    if (follow != null && follow.isAlive()) return follow.getY();
                }
            }
        }
        LivingEntity leader = getLeaderEntity();
        if (leader != null && leader.isAlive()) return leader.getY();
        return getY();
    }

    /**
     * 调试诊断：每秒一行 [MOVE-DIAG]，记录这个士兵的完整移动状态。
     *   general-common.toml 的 debugMovementLog 打开时生效，默认关闭。
     */
    private void logMovementDiag() {
        if (!GeneralConfig.debugMovementLog) return;
        if (tickCount % 20 != 0) return;
        if (!isPlayerArmy() && getManualMoveTarget() == null) return;
        Mob mover = getVehicle() instanceof Mob m ? m : this;
        var nav = mover.getNavigation();
        Vec3 dm = mover.getDeltaMovement();
        String pathTarget = nav.getPath() == null ? "null" : nav.getPath().getTarget().toString();
        String nextNode = nav.getPath() == null || nav.getPath().isDone() ? "-"
                : nav.getPath().getNextNodePos().toString();
        String locked = getVehicle() instanceof WarHorseEntity horse
                && horse.getLockedYaw() != null
                ? String.format(java.util.Locale.ROOT, "%.1f", horse.getLockedYaw()) : "-";
        LivingEntity combat = getTarget();
        Vec3 manual = getManualMoveTarget();
        // 当前在跑哪些 AI 目标 + 小队的行动模式：排查"到底是谁在驱动这个单位"用
        String goals = this.goalSelector.getAvailableGoals().stream()
                .filter(net.minecraft.world.entity.ai.goal.WrappedGoal::isRunning)
                .map(g -> g.getGoal().getClass().getSimpleName())
                .collect(java.util.stream.Collectors.joining(","));
        String squadMove = "-";
        Player owner = getCommanderPlayer();
        if (owner != null) {
            var ownerState = com.ohhapple.monster_conquest.player.PlayerArmyManager
                    .getIfPresent(owner.getUUID());
            if (ownerState != null) {
                squadMove = com.ohhapple.monster_conquest.player.PlayerCommandState
                        .stateForCommand(this, ownerState).getMovement().name();
            }
        }
        com.ohhapple.monster_conquest.Monster_conquest.LOGGER.info(
                "[MOVE-DIAG] t={} unit={} pos=({},{},{}) rot={} mounted={} commanded={} free={} atSlot={} combatTarget={} manual={} route={} navDone={} path={} next={} wanted=({},{},{}) riderWanted=({},{},{}) delta=({},{},{}) lockedYaw={} ground={} noAi={} squadMove={} goals=[{}]",
                tickCount, getType().toShortString(),
                fmtDiag(getX()), fmtDiag(getY()), fmtDiag(getZ()), fmtDiag(getYRot()),
                isPassenger(), isPlayerCommanded(), isFreeRoaming(), isAtFormationSlot(),
                combat == null ? "-" : combat.getType().toShortString(),
                manual == null ? "-" : "(" + fmtDiag(manual.x) + "," + fmtDiag(manual.y) + "," + fmtDiag(manual.z) + ")",
                isFollowingMarchRoute(), nav.isDone(), pathTarget, nextNode,
                fmtDiag(mover.getMoveControl().getWantedX()),
                fmtDiag(mover.getMoveControl().getWantedY()),
                fmtDiag(mover.getMoveControl().getWantedZ()),
                fmtDiag(getMoveControl().getWantedX()),
                fmtDiag(getMoveControl().getWantedY()),
                fmtDiag(getMoveControl().getWantedZ()),
                fmtDiag(dm.x), fmtDiag(dm.y), fmtDiag(dm.z), locked,
                mover.onGround(), mover.isNoAi(), squadMove, goals);
    }

    private static String fmtDiag(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    /** 标记目标"寻路不通"：暂时不再把它当目标，也不会被传送到它身边。 */
    public void markTargetUnreachable(LivingEntity target) {
        if (target == null) return;
        this.unreachableTargetId = target.getUUID();
        this.unreachableTargetUntil = tickCount + UNREACHABLE_BLACKLIST_TICKS;
    }

    /**
     * 真正的脱困兜底：优先找一个"上方 / 附近的开阔可站点"（掉进深坑 / 沟里时能爬出去），
     *   找不到再退回原地附近的普通安全点。
     */
    @Nullable
    private Vec3 findEscapeSpot() {
        if (!(level() instanceof ServerLevel sl)) return null;
        int baseX = (int) Math.floor(getX());
        int baseY = (int) Math.floor(getY());
        int baseZ = (int) Math.floor(getZ());
        RandomSource random = getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 1.0 + random.nextDouble() * 9.0;
            int x = baseX + (int) Math.round(Math.cos(angle) * dist);
            int z = baseZ + (int) Math.round(Math.sin(angle) * dist);
            // 优先往上找（能爬出坑 / 沟）；同层的安全点由下面的普通搜索兜底
            for (int y = baseY + 16; y > baseY; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isSafeStandingSpot(pos)) continue;
                boolean sky = true;
                for (int i = 1; i <= 5; i++) {
                    if (!level().isEmptyBlock(pos.above(i))) { sky = false; break; }
                }
                if (!sky) continue;
                // 只能跳到"直线可见"的落点，避免脱困传送穿墙
                Vec3 candidate = new Vec3(x + 0.5, y, z + 0.5);
                if (!AiNavUtil.hasClearLine(level(),
                        position().add(0, getBbHeight() * 0.5, 0), candidate)) {
                    continue;
                }
                return candidate;
            }
        }
        return null;
    }

    /**
     * 掉队回巢：超出首领跟随范围时，先尝试寻路回到首领身边。
     *   有可行路线（含绕墙 / 开门）→ 下发行走目标并返回 true（不传送）；
     *   完全无路可走 → 返回 false（调用方按"是否真的卡住"决定要不要传送）；
     *   被全局节流推迟 → 也返回 true（下个周期再试，不算无路）。
     */
    private boolean routeBackTo(AbstractArmyCommander commander) {
        if (commander == null) return false;
        // 先丢掉正在追的路线，避免继续朝敌人方向跑（保留规划节流，避免每次都重算大范围 A*）
        if (isFollowingRoute()) clearRoute();
        if (isFollowingMarchRoute()) releaseMarchRoute();

        if (hasReachablePathTo(commander.position())) {
            Mob mover = getVehicle() instanceof Mob mount ? mount : this;
            mover.getNavigation().moveTo(commander, 1.0);
            return true;
        }
        // 原版说没路 → 下面的自研规划很贵（多次完整寻路）：同一个首领每 40 tick 最多试一次，
        //   避免大混战里一堆掉队单位每个检查周期都跑一遍大范围搜索。
        boolean sameLeader = commander.getUUID().equals(lastLeashRouteTargetId);
        if (sameLeader && tickCount - lastLeashRouteTick < 40) return false;
        lastLeashRouteTargetId = commander.getUUID();
        lastLeashRouteTick = tickCount;
        // 再让自研规划器完整规划一次（能绕墙 / 穿门）
        boolean planned = replanRouteTo(commander, true);
        // 被节流（本 tick 名额 / 预算用满、还在冷却、大预算没轮到）≠ 没有路：
        //   按"已经在回去的路上"处理，下个检查周期再试，不会因此把人传送走。
        if (!planned && (marchRoute.isStartDeferred()
                || RoutePlanner.wasLastCallDeferred()
                || AiNavUtil.wasLastHeavyAttemptDeferred())) return true;
        return planned;
    }

    /**
     * 够不够格"传送"：只有连续多轮真的没走动（约 10 秒）才算卡住。
     *   在动（追敌 / 绕路 / 被同伴挤着挪）时一律不算，也就不允许传送。
     */
    private boolean isStuckEnoughToTeleport() {
        return stuckAccumTicks >= STUCK_TELEPORT_MIN_TICKS;
    }

    /** 刷新对目标的仇恨：重建军队敌对关系与共享仇恨，避免赶路途中仇恨过期。 */
    public void refreshHatredOf(LivingEntity target) {
        if (target == null || !target.isAlive() || level().isClientSide) return;
        ThreatDispatcher.propagate(this, target);
        AbstractArmyCommander commander = getCommander();
        if (commander != null && commander.isAlive()) {
            commander.markAsHatred(target);
        }
    }

    /**
     * 骑兵提前下马：每隔 10 tick 检查朝当前目标（或首领）方向 1.5 / 2.5 格处
     *   "马 + 骑手"是否站得下，站不下（窄道 / 低矮通道）立即下马步行。
     */
    private void tickTerrainDismountCheck() {
        if (!GeneralConfig.enableFormSwitch) return;
        if (!(getVehicle() instanceof WarHorseEntity horse)) return;
        if (armyAge % 10 != 0) return;
        if (isForcedCavalry()) return;

        Vec3 ref = resolveCurrentGoal();
        if (ref == null) {
            LivingEntity leader = getLeaderEntity();
            if (leader == null) return;
            ref = leader.position();
        }

        // 只在"马已经走不动"时才提前下马：导航无路可走，或最近一个窗口没有进展。
        //   正常沿路径赶路（哪怕正前方隔着墙、路径在绕行）不打扰，避免频繁上下马。
        //   例外：目标就在附近（24 格内）而马的路径到不了（只有半路路径 / 无路）——
        //     说明前方有马过不去的窄道 / 低矮通道（单格迷宫入口等），也要检查下马。
        PathNavigation horseNav = horse.getNavigation();
        boolean nearGoal = position().distanceToSqr(ref) <= 24.0 * 24.0;
        if (!horseNav.isDone() && stuckAccumTicks <= 0
                && !(nearGoal && !pathReaches(horseNav))) {
            return;
        }

        tryDismountForMountTerrain(ref);
    }

    private void startRoute(List<net.minecraft.core.BlockPos> points, LivingEntity target,
                            boolean reachesGoal) {
        routePoints.clear();
        for (int i = 1; i < points.size(); i++) {
            net.minecraft.core.BlockPos p = points.get(i);
            routePoints.add(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
        }
        lastRoutePoints = List.copyOf(points);
        routeReachesGoal = reachesGoal;
        routeTargetId = target.getUUID();
        routeExpireTick = tickCount + 200;
        routeIssueTick = 0;
        routeWaypointTicks = 0;
        routeStallTicks = 0;
        routeLastDistSq = -1.0;
    }

    private void clearRoute() {
        routePoints.clear();
        routeReachesGoal = false;
        routeTargetId = null;
        routeExpireTick = 0;
        routeIssueTick = 0;
        routeWaypointTicks = 0;
        routeStallTicks = 0;
        routeLastDistSq = -1.0;
    }

    /** 是否正在按"行军绕路"（方块级 A* 路线）前进。 */
    public boolean isFollowingMarchRoute() { return marchRoute.isFollowing(); }

    public void clearMarchRoute() {
        marchRoute.clear();
    }

    /**
     * 丢下当前绕路路线但保留规划节流（回巢 / 改目标时用）。
     *   与 {@link #clearMarchRoute()} 的区别：不会把冷却 / 退避清零，
     *   避免多个单位同时改目标时每 tick 都重算一次大范围 A*（掉刻）。
     */
    public void releaseMarchRoute() {
        marchRoute.release();
    }

    /** 上一次行军绕路规划是否只是被全局节流推迟（不是真的规划不出路）。 */
    public boolean isMarchRoutePlanDeferred() {
        return marchRoute.isStartDeferred();
    }

    /**
     * 行军绕路：普通导航只能给"半路路径"或没有路时（迷宫 / 隔墙）用方块级 A* 算一条真正的绕路，
     *   并以首领的行军轨迹作为代价偏好，尽量沿首领走过的路线走。
     *
     * @return true = 已开始按绕路路线前进
     */
    public boolean startMarchRouteAround(Vec3 target) {
        if (target == null || level().isClientSide) return false;

        // 规划永远按"马 + 骑手"的净空来算（宽 1.4 的马不能照 0.6 宽的步兵路线走，
        //   否则它的转向点会落在墙里 → 顶着墙角抽搐）；
        //   强制骑兵只决定"骑马方案走不通时能不能回调下马"，不改变规划实体。
        Mob mount = getVehicle() instanceof Mob m ? m : null;
        double extraHeight = mount != null ? WarHorseEntity.RIDER_HEIGHT : 0.0;
        boolean allowDismount = mount instanceof WarHorseEntity && !isForcedCavalry();

        // 偏好：首领的行军轨迹（走同一条路，避免在迷宫岔路上乱窜）
        java.util.List<net.minecraft.core.BlockPos> favored = null;
        AbstractArmyCommander commander = getCommander();
        if (commander != null) {
            java.util.List<Vec3> trail = commander.getMarchTrail();
            if (!trail.isEmpty()) {
                java.util.List<net.minecraft.core.BlockPos> list = new java.util.ArrayList<>(trail.size());
                for (Vec3 p : trail) list.add(net.minecraft.core.BlockPos.containing(p));
                favored = list;
            }
        }

        return marchRoute.start(this, mount, target, tickCount, extraHeight, favored,
                allowDismount ? () -> {
                    switchToInfantry();
                    terrainDismountCooldown = 120;
                } : null);
    }

    /** 跟随行军绕路路线：走完一个路径点切下一个；到了目标附近 / 停滞 3 秒 / 出现战斗目标 → 交回上层。 */
    private void tickMarchRoute() {
        if (!marchRoute.isFollowing()) return;
        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        LivingEntity target = getTarget();
        marchRoute.tick(mover, tickCount, target != null && target.isAlive());
    }

    /**
     * 跟随绕路路线：在 AI goal 之后执行，保证路径不被 goal 的导航覆盖；
     *   走完一个路径点切下一个；能打到目标 / 目标变化 / 超时 → 交回战斗 AI。
     */
    private void tickRoute() {
        if (routePoints.isEmpty()) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()
                || routeTargetId == null || !routeTargetId.equals(target.getUUID())
                || tickCount > routeExpireTick) {
            clearRoute();
            return;
        }
        if (canAttackSpatially(target)) {
            clearRoute();
            return;
        }

        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        PathNavigation nav = mover.getNavigation();
        Vec3 waypoint = routePoints.peekFirst();
        if (waypoint == null) { clearRoute(); return; }
        double distSqr = mover.position().distanceToSqr(waypoint);

        // 到达（2 格内）或单个路径点反复走不到（4 秒）→ 切下一个路径点，
        //   避免卡在一个走不到的点上原地僵住。
        if (distSqr < 4.0 || ++routeWaypointTicks > 80) {
            routePoints.pollFirst();
            routeWaypointTicks = 0;
            routeStallTicks = 0;
            routeLastDistSq = -1.0;
            waypoint = routePoints.peekFirst();
            if (waypoint == null) { clearRoute(); return; }
            distSqr = mover.position().distanceToSqr(waypoint);
        }

        // 连续 3 秒没有向当前路径点靠近 → 丢弃这条路线，让上层重新规划
        if (routeLastDistSq > 0 && distSqr > routeLastDistSq - 0.05) {
            if (++routeStallTicks > 60) { clearRoute(); return; }
        } else {
            routeStallTicks = 0;
        }
        routeLastDistSq = distSqr;

        // 没有路径时 5 tick 重试一次，有路径在走时每 20 tick 重发一次 —— 不每 tick 重算，
        //   否则路径点走不到时每个 tick 都会跑一次完整寻路。
        if (tickCount >= routeIssueTick) {
            routeIssueTick = tickCount + (nav.isDone() ? 5 : 20);
            nav.moveTo(waypoint.x, waypoint.y, waypoint.z, 1.0D);
        }
    }

    /** 当前导航路径是否真的能到达终点（false = 只找到"最接近目标"的半路路径）。 */
    private static boolean pathReaches(PathNavigation nav) {
        net.minecraft.world.level.pathfinder.Path path = nav.getPath();
        return path != null && path.canReach();
    }

    @Nullable
    private Vec3 resolveCurrentGoal() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) return target.position();
        if (manualMoveTarget != null) return manualMoveTarget;

        if (isPlayerArmy()) {
            Player owner = getCommanderPlayer();
            if (owner != null) {
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (st != null) {
                    var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(this, st);
                    if (ss.getMovement()
                            == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION
                            && ss.getFormationAnchor() != null) {
                        return ss.getFormationAnchor();
                    }
                }
            }
        }
        return null;
    }

    private void trySideStep() {
        if (!this.onGround()) return;
        if (this.tickCount % 80 != 0) return;
        double angle = getRandom().nextDouble() * Math.PI * 2;
        double vx = Math.cos(angle) * 0.18;
        double vz = Math.sin(angle) * 0.18;
        Vec3 dm = getDeltaMovement();
        this.setDeltaMovement(vx, dm.y, vz);
        this.hasImpulse = true;
    }

    /** 贴角脱困的连续"顶着方块不动"计数与冷却。 */
    private int cornerWedgeTicks = 0;
    private int cornerWedgeNextTick = 0;
    private double cornerWedgeLastX = Double.NaN;
    private double cornerWedgeLastZ = Double.NaN;
    /** 连续在同一处触发脱困的次数（用来把"蹭一下"升级成"持续侧滑一小段"）。 */
    private int cornerWedgeAttempts = 0;
    private int cornerWedgeAttemptTick = Integer.MIN_VALUE;
    private double cornerWedgeAttemptX = Double.NaN;
    private double cornerWedgeAttemptZ = Double.NaN;
    /** 强制侧滑窗口：这段时间不听寻路的，直接沿"绕行方向"滑一小段，真正绕过墙角。 */
    private int cornerSlideUntil = 0;
    private double cornerSlideX = 0.0;
    private double cornerSlideZ = 0.0;

    /**
     * 贴角脱困（凸墙角专用的"轻推一把"）：
     *   有移动目标、却连续顶着方块几乎不动时（典型：原版寻路把单位带到凸墙角，
     *   目标点在墙角斜后方，两个方向都被墙挡住），朝"侧面有空间"的一边侧移一小步，
     *   并停掉当前路径让上层重新规划 —— 只在真的顶住不动时触发。
     *   同一处反复卡时改用一段持续侧滑，把单位带过墙角再交给寻路。
     */
    private void tickCornerUnstick() {
        Mob mover = getVehicle() instanceof Mob m ? m : this;
        // 侧滑窗口：强制沿侧面走一小段（不听寻路），绕过墙角后再由上层重新规划。
        if (tickCount < cornerSlideUntil) {
            mover.getNavigation().stop();
            steerCornerSlide(mover);
            return;
        }
        boolean wantsToMove = !mover.getNavigation().isDone()
                || isFollowingMarchRoute() || getManualMoveTarget() != null;
        double stepX = mover.getX() - cornerWedgeLastX;
        double stepZ = mover.getZ() - cornerWedgeLastZ;
        double moved = Double.isNaN(cornerWedgeLastX) ? 1.0
                : Math.sqrt(stepX * stepX + stepZ * stepZ);
        cornerWedgeLastX = mover.getX();
        cornerWedgeLastZ = mover.getZ();
        if (!wantsToMove || AiNavUtil.isOnClimbable(mover) || !mover.horizontalCollision) {
            cornerWedgeTicks = 0;
            return;
        }
        if (moved > 0.012) {
            cornerWedgeTicks = 0;
            return;
        }
        if (++cornerWedgeTicks < 12 || tickCount < cornerWedgeNextTick) return;
        cornerWedgeNextTick = tickCount + 15;
        cornerWedgeTicks = 0;

        // 侧移方向取"想去方向"的左右两侧（没有想去方向就用当前朝向）；
        //   两侧都能站时，选"更接近目标（绕过去该走的那边）"的一侧。
        BlockPos goalPos = cornerGoalPos(mover);
        double gx = goalPos == null ? 0.0 : goalPos.getX() + 0.5 - mover.getX();
        double gz = goalPos == null ? 0.0 : goalPos.getZ() + 0.5 - mover.getZ();
        double fx = mover.getMoveControl().getWantedX() - mover.getX();
        double fz = mover.getMoveControl().getWantedZ() - mover.getZ();
        double len = Math.sqrt(fx * fx + fz * fz);
        if (len < 0.05) {
            float yaw = mover.getYRot() * ((float) Math.PI / 180F);
            fx = -Mth.sin(yaw);
            fz = Mth.cos(yaw);
            len = 1.0;
        }
        fx /= len;
        fz /= len;
        int pick = -1;
        double bestDot = -Double.MAX_VALUE;
        for (int i = 0; i < 2; i++) {
            double lx = (i == 0) ? fz : -fz;
            double lz = (i == 0) ? -fx : fx;
            BlockPos side = BlockPos.containing(
                    mover.getX() + lx * 0.8, mover.getY(), mover.getZ() + lz * 0.8);
            if (mover.level().getBlockState(side).isSolid()
                    || mover.level().getBlockState(side.above()).isSolid()) {
                continue;
            }
            double dot = lx * gx + lz * gz;
            if (dot > bestDot) {
                bestDot = dot;
                pick = i;
            }
        }
        if (pick < 0) return;
        double lx = (pick == 0) ? fz : -fz;
        double lz = (pick == 0) ? -fx : fx;

        // 同一地点反复触发 → 轻推没用（寻路会立刻把单位拉回墙角），升级成"持续侧滑一小段"。
        boolean sameSpot = !Double.isNaN(cornerWedgeAttemptX)
                && Math.hypot(mover.getX() - cornerWedgeAttemptX,
                mover.getZ() - cornerWedgeAttemptZ) < 1.5
                && tickCount - cornerWedgeAttemptTick < 160;
        cornerWedgeAttempts = sameSpot ? cornerWedgeAttempts + 1 : 1;
        cornerWedgeAttemptTick = tickCount;
        cornerWedgeAttemptX = mover.getX();
        cornerWedgeAttemptZ = mover.getZ();

        if (com.ohhapple.monster_conquest.config.GeneralConfig.debugMovementLog) {
            com.ohhapple.monster_conquest.Monster_conquest.LOGGER.info(
                    "[MOVE-UNSTICK] t={} pos=({},{},{}) side=({},{}) attempts={} escalate={}",
                    tickCount, fmtDiag(getX()), fmtDiag(getY()), fmtDiag(getZ()),
                    fmtDiag(lx), fmtDiag(lz), cornerWedgeAttempts, cornerWedgeAttempts >= 2);
        }
        // 骑马跟着绕路路线却一直顶着墙角：转向点连"马 + 骑手"都站不下（宽体不会
        //   按步兵节点走）。继续轻推只会被寻路立刻拉回墙角（原地抽搐），
        //   直接把这条路线作废，让上层按坐骑净空重新规划。
        if (cornerWedgeAttempts >= 2 && mover instanceof WarHorseEntity
                && isFollowingMarchRoute() && !moveTargetFitsMount(mover)) {
            if (com.ohhapple.monster_conquest.config.GeneralConfig.debugMovementLog) {
                com.ohhapple.monster_conquest.Monster_conquest.LOGGER.info(
                        "[MOVE-UNSTICK] t={} pos=({},{},{}) drop-route target=({},{},{})",
                        tickCount, fmtDiag(getX()), fmtDiag(getY()), fmtDiag(getZ()),
                        fmtDiag(mover.getMoveControl().getWantedX()),
                        fmtDiag(mover.getMoveControl().getWantedY()),
                        fmtDiag(mover.getMoveControl().getWantedZ()));
            }
            releaseMarchRoute();
            AiNavUtil.stopMovementInput(mover);
            resetStuckTracking();
            return;
        }
        if (cornerWedgeAttempts >= 2) {
            // 同一处连续卡住：停掉当前寻路，强制侧滑一小段绕过墙角
            mover.getNavigation().stop();
            AiNavUtil.stopMovementInput(mover);
            cornerSlideX = lx;
            cornerSlideZ = lz;
            cornerSlideUntil = tickCount + 18;
            steerCornerSlide(mover);
        } else {
            // 轻推即可：保留寻路，避免每次轻推都触发一轮重新规划（部队一多就是卡顿来源）
            Vec3 dm = mover.getDeltaMovement();
            mover.setDeltaMovement(lx * 0.2, dm.y, lz * 0.2);
            mover.hasImpulse = true;
            if (mover != this) {
                Vec3 dmRider = getDeltaMovement();
                setDeltaMovement(lx * 0.2, dmRider.y, lz * 0.2);
                hasImpulse = true;
            }
        }
        resetStuckTracking();
    }

    /** 移动控制器里的转向点能否容下"马 + 骑手"（脱困判定用），只看站位本身。 */
    private boolean moveTargetFitsMount(Mob mover) {
        return AiNavUtil.mountStandFits(level(), this,
                mover.getMoveControl().getWantedX(),
                mover.getMoveControl().getWantedY(),
                mover.getMoveControl().getWantedZ());
    }

    /**
     * 把"侧滑目标点"同时写进坐骑与骑手的移动控制器。
     *   骑马单位的实际操控入口并不总是坐骑自己的移动控制器，只写马这一侧会出现"脱困完全不动"。
     */
    private void steerCornerSlide(Mob mover) {
        double tx = mover.getX() + cornerSlideX * 1.6;
        double tz = mover.getZ() + cornerSlideZ * 1.6;
        mover.getMoveControl().setWantedPosition(tx, mover.getY(), tz, 1.0D);
        if (mover != this) {
            getMoveControl().setWantedPosition(tx, mover.getY(), tz, 1.0D);
        }
    }

    /** 脱困用的"目标点"：优先用当前路径终点（代表绕行方向），其次用追击 / 手动 / 锚点目标。 */
    @Nullable
    private BlockPos cornerGoalPos(Mob mover) {
        Path path = mover.getNavigation().getPath();
        if (path != null && !path.isDone()) return path.getTarget();
        Vec3 goal = resolveCurrentGoal();
        return goal == null ? null : BlockPos.containing(goal);
    }

    protected void applyWaterFloat() {
        if (!this.isInWater()) return;
        if (this.isPassenger()) return;
        if (isDrowned()) return;

        BlockPos footPos = this.blockPosition();
        if (!this.level().getBlockState(footPos).getFluidState().is(FluidTags.WATER)) return;

        Vec3 dm = this.getDeltaMovement();
        if (dm.y < 0.03) {
            this.setDeltaMovement(dm.x, dm.y + 0.05, dm.z);
            this.hasImpulse = true;
            this.fallDistance = 0;
        }
    }

    @Override
    public void travel(Vec3 input) {
        if (!level().isClientSide && isDrowned() && !this.isPassenger()) {
            BlockPos footPos = this.blockPosition();
            boolean footInWater = level().getBlockState(footPos).getFluidState().is(FluidTags.WATER);
            // 脚已经离开水格、但正在翻越岸边（drownedClimbTicks > 0）时也保持控制：
            //   否则浮到水面那一瞬间就被交回原版物理、带着零横向速度掉回水里 ——
            //   表现为"贴着岸卡住、永远上不了岸"。
            if (footInWater || drownedClimbTicks > 0) { travelDrowned(); return; }
        }
        super.travel(input);
    }

    /** 找水面时最多往上看几格（很深的水里不会每 tick 扫描太多方块）。 */
    private static final int DROWNED_SURFACE_SCAN_LIMIT = 8;

    /** 判断岸壁高低时最多往上看几格（更高的墙一律视为"爬不上去"）。 */
    private static final int DROWNED_CLIMB_SCAN_LIMIT = 8;

    /** 贴岸爬升计时（tick）：贴着岸壁时不断刷新；>0 时即使脚离开水格也继续由水里移动控制。 */
    private int drownedClimbTicks = 0;
    /** 翻过岸顶后的"平推"计时（tick）：短暂不下沉，把身体送上岸再交回原版物理。 */
    private int drownedHoverTicks = 0;
    /** 空闲（无目标无首领）时自己找的岸边落点，以及它的有效期 / 下次找落点的冷却。 */
    @Nullable private Vec3 drownedWanderGoal = null;
    /** 上次走了很久也没到（多半是爬不上去的高岸）的落点：下次找落点时避开那一片。 */
    @Nullable private Vec3 drownedWanderFail = null;
    private int drownedWanderTicks = 0;
    private int drownedWanderCooldown = 0;
    /** "想上岸却卡住"的计时（水里、目标在岸上、连续没前进）→ 触发"换最近能爬的岸"。 */
    private int drownedStuckTicks = 0;
    /** 累计卡住时间：换目标也一直走不动（被卡死）→ 用通用脱困传送送到安全点。 */
    private int drownedStuckTotalTicks = 0;
    private double drownedStuckLastDist = -1.0;

    /**
     * 当前水柱"水面之上第一格空气"的 Y（巡航深度、爬岸高度都以它为基准）：
     *   脚下那格是水 → 直接往上数水柱；
     *   脚已经浮在水面之上（爬岸途中）→ 先往下找水柱顶，再往上数。
     *   水太深看不到水面（超过扫描上限）或附近没有水 → 返回"当前高度上面一点"。
     */
    private double drownedSurfaceAirY() {
        BlockPos feet = blockPosition();
        BlockPos waterTop = level().getBlockState(feet).getFluidState().is(FluidTags.WATER) ? feet : null;
        if (waterTop == null) {
            for (int down = 1; down <= 4; down++) {
                BlockPos p = feet.below(down);
                if (level().getBlockState(p).getFluidState().is(FluidTags.WATER)) {
                    waterTop = p;
                    break;
                }
            }
        }
        if (waterTop == null) return getY() + 3.0;

        int up = 1;
        while (up <= DROWNED_SURFACE_SCAN_LIMIT
                && level().getBlockState(waterTop.above(up)).getFluidState().is(FluidTags.WATER)) {
            up++;
        }
        if (up > DROWNED_SURFACE_SCAN_LIMIT) return getY() + 3.0;
        return waterTop.getY() + up;
    }

    /**
     * 溺尸的"水下巡航高度"（脚的目标 Y）：
     *   水面下方 (眼高 + 0.4) 格 —— 让眼睛没入水面下一点、整个人潜在水里，
     *   而不是被托在水面上"行走"。
     */
    private double drownedCruiseY() {
        return drownedSurfaceAirY() - (getEyeHeight() + 0.4);
    }

    /** 水面漂浮高度（脚的目标 Y）：眼睛刚好露出水面 —— 原版生物"浮在水面"的样子。 */
    private double drownedFloatY() {
        return drownedSurfaceAirY() - getEyeHeight();
    }

    /**
     * 陆地 Goal 当前写给"移动控制器"的目标点（阵型站位 / 水里直线接近都会写这里）。
     *   太近（≈ 自己脚下，说明已经停下 / 已就位）或太远（旧的残留点）都视为"没有目标"。
     */
    @Nullable
    private Vec3 liveMoveControlTarget() {
        double wx = getMoveControl().getWantedX();
        double wy = getMoveControl().getWantedY();
        double wz = getMoveControl().getWantedZ();
        // 移动控制器从未被写过时是 (0,0,0)（世界原点）—— 那不是"目标点"，
        //   是按"没目标"处理；否则刚出生、没有任何命令的溺尸会朝世界原点游。
        if (wx == 0.0 && wy == 0.0 && wz == 0.0) return null;
        double dx = wx - getX();
        double dz = wz - getZ();
        double d2 = dx * dx + dz * dz;
        if (d2 < 0.64 || d2 > 48.0 * 48.0) return null;
        return new Vec3(wx, wy, wz);
    }

    /**
     * 当前的"阵型站位点"（就是陆地 Goal 此刻在用的同一个点）：
     *   · 首领军队 → 首领给这个士兵排的槽位；
     *   · 玩家军队 → 跟随对象（默认主人）的位置 —— 跟随 / 软跟随时它就代表"该站的地方"；
     *   · 拿不到（没有槽位 / 没有首领）→ null，交给首领 / 空闲逻辑。
     */
    @Nullable
    private Vec3 drownedStationPoint() {
        AbstractArmyCommander commander = getCommander();
        if (commander != null) return commander.getFormationSlot(this);
        if (isPlayerArmy()) {
            Player owner = getCommanderPlayer();
            if (owner != null) {
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (st != null) {
                    var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(this, st);
                    var movement = ss.getMovement();
                    // 原地驻守（没有锚点）= 待在原地；自由移动 = 像原版那样自己游荡。
                    //   这两种都不拿玩家当站位参考，否则水里会又去追玩家。
                    if (movement == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FREE
                            || (movement
                                    == com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.STATION
                                    && ss.getFormationAnchor() == null)) {
                        return null;
                    }
                }
            }
            LivingEntity ref = drownedFollowRef();
            if (ref != null) return ref.position();
            LivingEntity leader = getLeaderEntity();
            return leader == null ? null : leader.position();
        }
        return null;
    }

    /**
     * 玩家军队"跟随 / 软跟随"时的实际跟随对象（主人，或专门指定的被跟随单位）。
     *   用来做"深度参照"：横向目标可能只是贴着水面的阵位点，
     *   但纵向要跟着被跟随的人走 —— 对方下潜时一起下潜，而不是一直漂在水面。
     *   其它情况（不是玩家军队 / 不是跟随模式）返回 null。
     */
    @Nullable
    private LivingEntity drownedFollowRef() {
        if (!isPlayerArmy()) return null;
        Player owner = getCommanderPlayer();
        if (owner == null) return null;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return null;
        var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st);
        var movement = ss.getMovement();
        if (movement != com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FOLLOW
                && movement != com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.SOFT_FOLLOW) {
            return null;
        }
        java.util.UUID uuid = ss.getFollowTargetUuid();
        if (uuid != null && !uuid.equals(owner.getUUID())) {
            if (level() instanceof ServerLevel sl) {
                net.minecraft.world.entity.Entity e = sl.getEntity(uuid);
                if (e instanceof LivingEntity le && le.isAlive()) return le;
            }
            return null;
        }
        return owner;
    }

    /**
     * 某个目标点（站位点 / 命令点 / 路线节点 / 跟随对象位置）是不是"泡在水里"。
     *   水里"已就位"与"纵向跟深"判定都用它：
     *     · 目标点在水里、而且明显比人深 → 跟着潜下去；
     *     · 目标点不沾水（岸上 / 平台上）而人还在水里 → 不算到位，要继续游上岸。
     */
    private boolean drownedPointWet(Vec3 point) {
        return level().getFluidState(
                BlockPos.containing(point.x, point.y + 0.1, point.z)).is(FluidTags.WATER);
    }

    /**
     * 空闲时自己找一个"岸边落点"（没有敌人、没有路线、也没有首领时用）：
     *   原版溺尸在水里也会偶尔游上岸溜达，这里给它们同样的行为 —— 找不到就继续浮在水面。
     *   只挑"脚下实心、自身和头上是空气、完全不沾水"的干岸落点。
     */
    @Nullable
    private Vec3 drownedIdleLandGoal() {
        if (drownedWanderGoal != null) {
            double dx = drownedWanderGoal.x - getX();
            double dz = drownedWanderGoal.z - getZ();
            // 只有"真的出了水"才算到岸：贴着岸边还在水里时不能算到达，
            //   否则目标一清掉就没有方向，会浮在岸边不上岸。
            if (dx * dx + dz * dz < 4.0 && !this.isInWater()) {
                drownedWanderGoal = null;
                drownedWanderFail = null;
            } else if (--drownedWanderTicks <= 0) {
                // 走了很久都没到（多半是爬不上去的高岸）→ 记住这里，下次换一边找
                drownedWanderFail = drownedWanderGoal;
                drownedWanderGoal = null;
            } else {
                return drownedWanderGoal;
            }
        }
        if (--drownedWanderCooldown > 0) return null;
        drownedWanderCooldown = 160 + getRandom().nextInt(160);

        // 从近到远一圈圈找最近的干岸落点（找到就立刻用最近的，不走随机取样）
        BlockPos origin = blockPosition();
        double surfaceAirY = drownedSurfaceAirY();
        int budget = 2000;   // 扫描上限：大海中央找不到岸也不会一直扫（限流）
        search:
        for (int radius = 1; radius <= 14; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    for (int dy = 3; dy >= -2; dy--) {
                        if (--budget <= 0) break search;
                        BlockPos p = origin.offset(dx, dy, dz);
                        if (drownedWanderFail != null
                                && drownedWanderFail.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 9.0) {
                            continue;   // 上次到不了的区域 → 跳过
                        }
                        if (p.getY() > surfaceAirY + 1.0) {
                            continue;   // 只找"能爬上去"的岸：岸顶最多比水面高 1 格
                        }
                        if (!level().getBlockState(p).isAir()) continue;
                        if (!level().getBlockState(p.above()).isAir()) continue;
                        if (!level().getBlockState(p.below()).isSolid()) continue;
                        if (!level().getFluidState(p).isEmpty()) continue;
                        if (!level().getFluidState(p.below()).isEmpty()) continue;
                        drownedWanderGoal = new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
                        drownedWanderTicks = 240;
                        return drownedWanderGoal;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 水里"跟着寻路路线游"的目标点（导航路径的下一个节点）：
     *   跟随 / 行军 / 漫游 / 移动命令都会先规划路线，在水里应该沿着它游，
     *   而不是无视路线直冲目的地（那样容易顶着岸边卡住）。
     *   原版路径推进要求"接近节点高度"，潜水游不会触发 ——
     *   这里按水平距离自己推进路径，否则会永远卡在第一个节点上。
     */
    @Nullable
    private Vec3 drownedRoutePoint() {
        PathNavigation nav = getNavigation();
        Path path = nav.getPath();
        if (path == null || nav.isDone()) return null;
        for (int guard = 0; guard < 16 && !path.isDone(); guard++) {
            Vec3i node = path.getNextNodePos();
            double dx = (double) node.getX() + 0.5 - getX();
            double dz = (double) node.getZ() + 0.5 - getZ();
            if (dx * dx + dz * dz > 1.44) break;
            path.advance();
        }
        if (path.isDone()) return null;
        Vec3i node = path.getNextNodePos();
        return new Vec3((double) node.getX() + 0.5, node.getY(), (double) node.getZ() + 0.5);
    }

    /**
     * 溺尸水里移动（只有溺尸兵种走这里）：
     *   · 速度 = 该溺尸自己的士兵移动速度（getMovementSpeed()，写多少是多少，全程不做任何倍率）；
     *   · 横向：朝目标直线游（战斗追敌人，没敌人跟首领），不经过寻路；
     *   · 纵向：潜到水面下方巡航；目标 / 首领也在水里时跟随对方更深的高度；
     *   · 前方是岸壁 → 一边继续朝目标推进、一边向上浮（贴岸爬升），脚越过岸顶后
     *     短暂"平推"把身体送上岸，站到陆地后再交回原版物理。
     */
    private void travelDrowned() {
        if (drownedClimbTicks > 0) drownedClimbTicks--;

        final double speed = getMovementSpeed();

        // 玩家军队"跟随"时实际跟随的对象（用于纵向跟深：对方下潜就一起下潜）。
        //   横向目标可能只是贴着水面的阵位点 / 路线节点，但深度要跟着人走。
        LivingEntity followRef = drownedFollowRef();

        // 水平目标点：水里完全沿用陆地那套指挥逻辑：
        //   战斗目标 > 指挥工具指令 > 寻路路线（行军 / 跟随规划） > 陆地 Goal 直接写的移动目标
        //   （阵型站位 / 水里直线接近） > 驻守锚点 > "已就位（原地待命）" > 首领 / 野生上岸。
        Vec3 steer = null;
        boolean combatSteer = false;
        boolean routeSteer = false;
        boolean manualSteer = false;
        boolean orderSteer = false;
        boolean idleSteer = false;
        boolean holdPosition = false;
        boolean steerInWater = false;

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) {
            steer = target.position();
            combatSteer = true;
            steerInWater = target.isInWater();
        } else {
            // 第 1 优先：指挥工具下的"前往某地"命令（水里同样生效）。
            Vec3 manual = getManualMoveTarget();
            if (manual != null) {
                steer = manual;
                manualSteer = true;
            } else {
                // 第 2 优先：陆地 Goal 规划出的导航路线（行军 / 绕路 / 跟随都走这里）。
                steer = drownedRoutePoint();
                if (steer != null) {
                    routeSteer = true;
                } else {
                    // 第 3 优先：陆地 Goal 直接写给移动控制器的目标点
                    //   （阵型站位、水里"直线接近"分支都写在这里 —— 和陆地完全是同一份目标）。
                    steer = liveMoveControlTarget();
                    if (steer != null) {
                        orderSteer = true;
                    } else {
                        // 第 4 优先：驻守锚点（和陆地脱困逻辑用的同一个目标）。
                        steer = resolveCurrentGoal();
                        if (steer != null) {
                            orderSteer = true;
                        } else if (isAtFormationSlot() && !isFreeRoaming()) {
                            // 第 5 优先：已经在阵位 / 就位。
                            //   但水里的"就位"要比陆地严格：陆地的到达判定只看水平距离
                            //   （差半格有台阶 / 跳跃兜底），水里差一格就表现成
                            //   "泡在水面下、贴着岸边永远上不去"。
                            //   站位点是干燥陆地（岸上 / 平台上）而人还泡在水里 → 不算到位：
                            //   继续朝站位游，让爬岸逻辑把人送上岸；
                            //   站位点本身在水里时再分两种：比人深（跟随对象下潜了）→ 跟着潜下去；
                            //   和人在差不多深 → 原地浮着待命。
                            Vec3 station = drownedStationPoint();
                            if (station == null) {
                                holdPosition = true;
                            } else if (drownedPointWet(station)) {
                                if (station.y < getY() - 1.5) {
                                    steer = station;
                                    orderSteer = true;
                                    steerInWater = true;
                                } else {
                                    holdPosition = true;
                                }
                            } else {
                                steer = station;
                                orderSteer = true;
                            }
                        }
                    }
                }
                if (steer == null && !holdPosition) {
                    LivingEntity leader = getLeaderEntity();
                    // 自由移动 / 没被指挥过的士兵：像原版溺尸那样自己游荡（自己找岸）。
                    boolean freeRoam = isFreeRoaming() || (isPlayerArmy() && !isPlayerCommanded());
                    if (leader == null || freeRoam) {
                        // 野生溺尸（没首领 / 玩家还没下过命令）：自己找个干岸落点游过去上岸 ——
                        //   原版溺尸也会自己上岸溜达。
                        steer = drownedIdleLandGoal();
                        idleSteer = steer != null;
                    } else {
                        steer = leader.position();
                        steerInWater = leader.isInWater();
                    }
                }
            }
        }

        double vx = 0, vy = 0, vz = 0;

        if (steer != null) {
            // 目标点本身泡在水里（命令点在水下 / 路线节点在水里）→ 纵向也跟着它走，
            //   而不是一直按"贴着水面巡航"处理。
            if (!steerInWater && drownedPointWet(steer)) steerInWater = true;

            double dx = steer.x - getX();
            double dz = steer.z - getZ();
            double horiz = Math.sqrt(dx * dx + dz * dz);

            // "想上岸却卡住"的判定：目标不在水里、还有点距离、连续 ~2 秒没能拉近 1 格
            //   （典型：顶着"高出水面 1 格以上、爬不上去"的高岸干推；小幅左右蹭不算接近）。
            //   进度按"到最终目标（敌人 / 首领）的距离"算，不能按路径节点 ——
            //   路径节点可能就在脚边，会把"一直没动"误判成"已经到位"。
            //   "有没有在接近目标"要用当前真正的目标算：战斗目标 > 手动命令点 > 首领。
            //   （用首领算会把"正按命令往别处走"误判成卡住，从而把命令顶掉。）
            double goalDist;
            if (target != null && target.isAlive()) {
                goalDist = Math.sqrt(target.distanceToSqr(this));
            } else if (manualSteer || orderSteer) {
                goalDist = steer != null ? Math.sqrt(steer.distanceToSqr(getX(), getY(), getZ())) : horiz;
            } else {
                LivingEntity leader = getLeaderEntity();
                goalDist = leader != null ? Math.sqrt(leader.distanceToSqr(this)) : horiz;
            }
            if (!steerInWater && goalDist > 3.0) {
                if (drownedStuckLastDist < 0.0 || goalDist < drownedStuckLastDist - 1.0) {
                    drownedStuckLastDist = goalDist;
                    drownedStuckTicks = 0;
                    drownedStuckTotalTicks = 0;
                } else {
                    if (++drownedStuckTicks > 40) {
                        drownedStuckTicks = 0;
                        // 先"退一步"：卡在方块里的话，任何移动都会被碰撞顶回 0，先把自己挪出来
                        // （只在退一步的位置没有碰撞时才挪，避免挪进别的方块）。
                        double backX = getX() - (horiz > 0.01 ? dx / horiz : 0.0) * 0.45;
                        double backZ = getZ() - (horiz > 0.01 ? dz / horiz : 0.0) * 0.45;
                        AABB backBox = getBoundingBox().move(backX - getX(), 0.0, backZ - getZ());
                        if (level().noCollision(this, backBox)) {
                            this.setPos(backX, getY(), backZ);
                        }
                        // 换目标：去最近"能爬上去"的干岸落点（找不到就继续按原目标游）。
                        // 卡住恢复要立刻找落点，不受"空闲游走"的冷却影响。
                        drownedWanderCooldown = 0;
                        Vec3 shore = drownedIdleLandGoal();
                        if (shore != null) {
                            steer = shore;
                            combatSteer = false;
                            routeSteer = false;
                            idleSteer = true;
                            steerInWater = false;
                            dx = steer.x - getX();
                            dz = steer.z - getZ();
                            horiz = Math.sqrt(dx * dx + dz * dz);
                        }
                    }
                    // 换目标后还是走不动（确实卡住了）→ 用通用脱困传送直接送到安全点：
                    //   玩家部队送到玩家身边，其它单位送到附近的安全落点。
                    if (++drownedStuckTotalTicks > 180) {
                        drownedStuckTotalTicks = 0;
                        drownedStuckLastDist = -1.0;
                        drownedStuckTicks = 0;
                        // 确实卡住了：直接送到刚才找到的那个"能爬的岸"落点；没有就退回通用脱困传送。
                        Vec3 shore = drownedIdleLandGoal();
                        if (shore != null) {
                            teleportWithMount(shore.x, shore.y, shore.z, true);
                        } else {
                            teleportToSafety();
                        }
                    }
                }
            } else {
                drownedStuckTicks = 0;
                drownedStuckTotalTicks = 0;
                drownedStuckLastDist = -1.0;
            }

            // 战斗追人 0.5 格内就够；跟路线 / 去岸边落点是逐个节点走；跟首领 2 格内算到位
            boolean moving = combatSteer ? horiz > 0.5
                    : (routeSteer || idleSteer || manualSteer || orderSteer) ? horiz > 0.3
                    : horiz > 2.0;
            double dirX = 0, dirZ = 0;
            if (horiz > 0.01) {
                dirX = dx / horiz;
                dirZ = dz / horiz;
            }

            // 岸壁检测：朝目标方向 1 格内有实心方块 → 再看它是不是"能爬的岸"。
            //   贴墙时也检测（不看 moving），所以目标站在岸边 / 岸顶时同样会往上爬。
            //   爬升高度限制：岸顶最多比水面高 1 格；更高的墙 / 悬崖不爬（不会贴着悬崖无限往上爬）。
            boolean wallAhead = false;
            boolean wallClimbable = false;
            int wallTopBlockY = -1;
            double wallSurfaceAirY = 0;
            if (horiz > 0.01) {
                double surfaceAirY = drownedSurfaceAirY();
                wallSurfaceAirY = surfaceAirY;
                // 先朝目标方向探；目标方向没探到、但身体正顶着东西（斜着顶住平台 / 围着平台绕）时，
                // 再补探四个正方向 —— 哪个方向紧挨着能爬的岸，就朝哪个方向爬。
                List<double[]> probes = new ArrayList<>();
                probes.add(new double[]{dirX, dirZ});
                if (horizontalCollision) {
                    probes.add(new double[]{0, 1});
                    probes.add(new double[]{0, -1});
                    probes.add(new double[]{1, 0});
                    probes.add(new double[]{-1, 0});
                }
                // 竖直方向"整段扫描"：从脚下扫到眼睛上方一格（漂浮时身体会有一点点上下浮动，
                //   固定只看"脚 / 眼睛"两个高度的话，方块正好卡在两个高度之间就会漏检 ——
                //   表现为：同高的浮动平台（或差一点没对准的岸）上不去、还会从平台底下钻过去）。
                int scanLowY = Mth.floor(getY());
                int scanHighY = Mth.floor(getEyeY()) + 1;
                probeLoop:
                for (double[] probe : probes) {
                    double px = probe[0], pz = probe[1];
                    if (Math.abs(px) + Math.abs(pz) < 0.01) continue;
                    for (double step = 0.4; step <= 1.0; step += 0.3) {
                        int wallX = Mth.floor(getX() + px * step);
                        int wallZ = Mth.floor(getZ() + pz * step);
                        for (int y = scanLowY; y <= scanHighY; y++) {
                            if (!level().getBlockState(new BlockPos(wallX, y, wallZ)).isSolid()) continue;
                            // 这一列有墙 → 量墙高（从脚的高度往上找最后一块实心方块）
                            int baseY = Mth.floor(getY());
                            int topBlockY = -1;
                            for (int dy = 0; dy <= DROWNED_CLIMB_SCAN_LIMIT; dy++) {
                                if (level().getBlockState(new BlockPos(wallX, baseY + dy, wallZ)).isSolid()) {
                                    topBlockY = baseY + dy;
                                }
                            }
                            // 能爬的墙：朝这个方向爬（斜着顶住时方向和目标方向不一定一致）
                            if (topBlockY >= 0 && topBlockY <= surfaceAirY) {
                                wallAhead = true;
                                wallClimbable = true;
                                wallTopBlockY = topBlockY;
                                dirX = px;
                                dirZ = pz;
                                break probeLoop;
                            }
                            // 高墙：先记着；若四个方向都没有能爬的岸，就按"有墙但爬不了"处理
                            if (!wallAhead) {
                                wallAhead = true;
                                wallTopBlockY = topBlockY;
                            }
                            break;   // 这个方向爬不上去，换下一个方向继续看
                        }
                    }
                }
            }

            // 水里上岸诊断日志（general-common.toml 的 debugMovementLog 打开时每 20 tick 记一行）：
            //   用于排查"某个地形上不来" —— 一眼能看出是没检测到岸、岸被判定太高、还是根本没在往岸走。
            if (GeneralConfig.debugMovementLog && tickCount % 20 == 0) {
                com.ohhapple.monster_conquest.Monster_conquest.LOGGER.info(
                        "[SWIM-DIAG] t={} pos=({},{},{}) steer=({},{},{}) combat={} route={} manual={} idle={} horiz={} goalDist={} stuck={} stuckTotal={} wallAhead={} climbable={} wallTopBlockY={} surfaceAirY={} climbTicks={} hover={} atSlot={} hold={} feetBlock={} inWater={} navPath={}",
                        tickCount, fmtDiag(getX()), fmtDiag(getY()), fmtDiag(getZ()),
                        fmtDiag(steer.x), fmtDiag(steer.y), fmtDiag(steer.z),
                        combatSteer, routeSteer, manualSteer, idleSteer, fmtDiag(horiz), fmtDiag(goalDist),
                        drownedStuckTicks, drownedStuckTotalTicks,
                        wallAhead, wallClimbable, wallTopBlockY, fmtDiag(wallSurfaceAirY),
                        drownedClimbTicks, drownedHoverTicks,
                        isAtFormationSlot(), holdPosition,
                        level().getBlockState(blockPosition()).getBlock().getName().getString(),
                        this.isInWater(),
                        getNavigation().getPath() == null ? "null" : getNavigation().getPath().getTarget().toString());
            }

            if (wallAhead && wallClimbable) {
                // 贴着岸壁：保持朝目标的推进 + 每 tick 上浮（爬升），脚越过岸顶后会被下面的
                //   "平推"分支送出水面。
                vx = dirX * speed;
                vz = dirZ * speed;
                vy = speed;
                drownedClimbTicks = 24;
                drownedHoverTicks = 6;
            } else if (drownedHoverTicks > 0) {
                // 刚翻过岸顶：短暂"平推"（不下沉），把身体送到岸上；脚下已经是实心陆地就收工。
                drownedHoverTicks--;
                vx = dirX * speed;
                vz = dirZ * speed;
                vy = 0;
                if (!this.isInWater() && level().getBlockState(blockPosition().below()).isSolid()) {
                    drownedClimbTicks = 0;
                    drownedHoverTicks = 0;
                }
            } else {
                // 普通游泳（包括"前方是爬不上去的高墙"：照常朝目标游 / 潜到巡航深度）
                if (moving) {
                    vx = dirX * speed;
                    vz = dirZ * speed;
                }
                // 纵向：正在游（或目标本身在水里）→ 潜到水下巡航 / 跟随目标深度；
                //   没在游（已经有路线 / 首领在身边待命）→ 浮在水面，头露出水。
                //   跟随对象泡在水里时，深度一律以它为准 —— 对方下潜，自己也跟着下潜。
                boolean followDive = followRef != null && followRef.isInWater();
                double aimY = (moving || steerInWater || followDive) ? drownedCruiseY() : drownedFloatY();
                if (steerInWater || followDive) {
                    double refY = steer.y;
                    if (followDive) refY = Math.min(refY, followRef.getY());
                    aimY = Math.min(aimY, refY + 0.5);
                }
                vy = Mth.clamp(aimY - getY(), -speed, speed);
            }

            if (horiz > 0.01) {
                float yaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.setYHeadRot(yaw);
            }
        } else {
            // 没敌人、没路线、也没首领：浮在水面（原地踩水），不再沉到水下。
            vy = Mth.clamp(drownedFloatY() - getY(), -speed, speed);
        }

        if (vy < 0) {
            BlockPos below = blockPosition().below();
            if (level().getBlockState(below).isSolid()) vy = 0;
        }

        this.setDeltaMovement(vx, vy, vz);
        this.move(MoverType.SELF, this.getDeltaMovement());
        this.fallDistance = 0;
    }

    private void teleportToSafety() {
        LivingEntity target = getTarget();
        // 战斗中只有"能真正到达"的目标才允许传送到敌人附近：
        //   寻路不通（例如攻城时敌人在城墙上）→ 把目标暂时拉黑，改走安全脱困，
        //   不会被传送到城墙 / 墙头上。
        if (target != null && target.isAlive()
                && (!hasPathToTarget(target)
                    || Math.abs(target.getY() - getY()) > MAX_ENEMY_TELEPORT_Y_DIFF)) {
            markTargetUnreachable(target);
            setTarget(null);
            target = null;
        }
        LivingEntity leader = getLeaderEntity();
        boolean inCombat = target != null && target.isAlive();

        // ===================== 玩家军队：软驻守 / 硬驻守 / 跟随 分派 =====================
        if (isPlayerArmy()) {
            Player owner = getCommanderPlayer();
            if (owner != null) {
                var cmdState = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (cmdState != null) {
                    var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(this, cmdState);

                    // —— 软驻守：不传送（等 FormationGoal 处理）——
                    if (ss.isSoftStation()) {
                        this.lastTargetDistSq = -1;
                        return;
                    }

                    // —— 硬驻守 ——
                    if (ss.getMovement()
                            == com.ohhapple.monster_conquest.player.PlayerCommandState
                            .MovementMode.STATION) {
                        if (ss.getFormationAnchor() != null) {
                            if (ss.hasMoveOrder()) {
                                // 还挂着"前往指定点"命令：锚点就是目的地，
                                //   不传送到锚点（等于瞬移到目的地），就地脱困让它自己走过去。
                                Vec3 escape = findEscapeSpot();
                                if (escape == null) {
                                    escape = findSafeSpotNear(getX(), getY(), getZ());
                                }
                                teleportWithMount(escape.x, escape.y, escape.z, true);
                            } else {
                                // 驻守点（命令已被后续命令覆盖）：拉回锚点附近，
                                //   落点必须"直线可见"，不会穿墙。
                                Vec3 anchor = ss.getFormationAnchor();
                                Vec3 safe = findSafeSpotNear(anchor.x, anchor.y, anchor.z);
                                teleportWithMount(safe.x, safe.y, safe.z, true);
                            }
                        }
                        this.lastTargetDistSq = -1;
                        return;
                    }

                    // —— FOLLOW / SOFT_FOLLOW 非战斗：传送到玩家 ——
                    if (!inCombat) {
                        if (isPlayerCommanded()) {
                            // 静音 + 玩家 3 格外的安全处
                            teleportNearSafely(owner, 3.0);
                        } else {
                            // 还没被玩家指挥过的士兵：只在原地附近找安全点，
                            //   不会跑到玩家身边（保持自然游走状态）
                            Vec3 safe = findSafeSpotNear(getX(), getY(), getZ());
                            teleportWithMount(safe.x, safe.y, safe.z, false);
                        }
                        this.lastTargetDistSq = -1;
                        return;
                    }

                    // —— FOLLOW 战斗中：落到下方通用逻辑（可传送到敌人附近）——
                }
            }
        }

        // ===================== NPC 军队 / 玩家军队 FOLLOW：通用脱困处理 =====================
        double cx, cy, cz;
        // 锚定单位（村庄巡逻 / 袭击增援）：只有目标在自己活动范围内时才允许传送到敌人附近，
        //   否则回到队长 / 自身附近，保证不会因为脱困传送而离开村庄 / 袭击范围。
        boolean nearEnemy = target != null && target.isAlive()
                && CombatConfig.enableStuckTeleportNearEnemy
                && getAnchor().contains(target.position());
        if (nearEnemy) {
            cx = target.getX(); cy = target.getY(); cz = target.getZ();
        } else if (leader != null) {
            cx = leader.getX(); cy = leader.getY(); cz = leader.getZ();
        } else {
            // 没有目标也没有首领：真正的脱困兜底（掉坑 / 掉沟）——
            //   优先往上找一个开阔可站点爬出来，找不到再退回原地附近的普通安全点。
            Vec3 escape = findEscapeSpot();
            if (escape != null) {
                teleportWithMount(escape.x, escape.y, escape.z, true);
            } else {
                Vec3 local = findSafeSpotNear(getX(), getY(), getZ());
                teleportWithMount(local.x, local.y, local.z, true);
            }
            this.lastTargetDistSq = -1;
            return;
        }

        Vec3 safe = findSafeSpotNear(cx, cy, cz);
        teleportWithMount(safe.x, safe.y, safe.z, true);
        this.lastTargetDistSq = -1;
    }

    private Vec3 findSafeSpotNear(double cx, double cy, double cz) {
        Vec3 safe = findSafeSpotNear(cx, cy, cz, 0.0);
        if (safe != null) return safe;
        // 目标点附近找不到"直线可见"的落点（例如目标在隔着墙的房间里）→
        //   退回到自己附近可见的安全点，不直接落在目标点上（那等于穿墙传送）。
        Vec3 local = findSafeSpotNear(getX(), getY(), getZ(), 0.0);
        return local != null ? local : position();
    }

    /**
     * 在 (cx,cy,cz) 附近找安全落脚点。
     *   minDistance > 0 时：只接受水平距离"严格大于 minDistance"的位置
     *   （跟随传送用，保证不会和玩家重叠）。
     *
     * @return 安全点；minDistance > 0 且找不到时返回 null（调用方跳过本次传送）
     */
    @Nullable
    private Vec3 findSafeSpotNear(double cx, double cy, double cz, double minDistance) {
        int baseX = (int) Math.floor(cx);
        int baseY = (int) Math.floor(cy);
        int baseZ = (int) Math.floor(cz);

        double minSq = minDistance * minDistance;
        int maxRadius = (minDistance > 0) ? 12 : 8;
        for (int radius = 1; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    if ((double) dx * dx + (double) dz * dz <= minSq) continue;
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    for (int y = baseY + 3; y >= baseY - 3; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!isSafeStandingSpot(pos)) continue;
                        // 只能跳到"直线可见"的落点，避免脱困传送穿墙跳进隔壁房间 / 目标点
                        Vec3 candidate = new Vec3(x + 0.5, y, z + 0.5);
                        if (!AiNavUtil.hasClearLine(level(),
                                position().add(0, getBbHeight() * 0.5, 0), candidate)) {
                            continue;
                        }
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private boolean isSafeStandingSpot(BlockPos pos) {
        BlockState below = level().getBlockState(pos.below());
        if (!below.isSolid()) return false;
        if (AiNavUtil.isDangerousBlock(below)) return false;

        BlockState here = level().getBlockState(pos);
        if (!here.isAir()) return false;
        if (!here.getFluidState().isEmpty()) return false;
        if (AiNavUtil.isDangerousBlock(here)) return false;

        for (int i = 1; i <= 2; i++) {
            BlockState s = level().getBlockState(pos.above(i));
            if (s.isSolid()) return false;
            if (!s.getFluidState().isEmpty()) return false;
            if (AiNavUtil.isDangerousBlock(s)) return false;
        }
        return true;
    }

    /**
     * 骑枪穿刺：与首领 / 巡逻队长共用 LancePierce，
     *   保证两者的骑兵近战（冲锋、穿刺、伤害）完全一致。
     */
    private void tickLancePierce() {
        // 只有"骑兵形态使用骑枪"的兵种才有穿刺伤：
        //   非骑枪骑兵（卫道士 / 溺尸 / 重锤兵）骑马时只做普通近战单体攻击，
        //   不会像骑枪那样对冲锋路径上的所有敌人造成伤害。
        if (!usesLanceInCavalry()) return;
        if (LancePierce.tick(this, getTarget(), lastChargeSpeed, armyAge,
                lanceHitCd, this::canAttack) > 0) {
            markAttackSuccess();
        }
    }

    private void tickDualWeapon() {
        if (!isDualWeapon()) return;
        if (usesTridentForDual()) return;

        LivingEntity target = getTarget();
        boolean lowHp = getHealth() < getMaxHealth() * 0.5f;
        boolean nearTarget = !lowHp && target != null && target.isAlive()
                && distanceTo(target) <= 5.0;

        // 双形态的换武器完全由形态模板驱动（界面不收自定义武器，避免打乱这套逻辑）
        ItemStack desired;
        if (nearTarget) desired = this.isPassenger() ? dualMeleeLanceStack : dualMeleeSwordStack;
        else desired = dualBowStack;
        if (desired.isEmpty()) return;

        ItemStack current = getMainHandItem();
        if (!ItemStack.isSameItem(current, desired)) {
            this.setItemSlot(EquipmentSlot.MAINHAND, desired.copy());
        }
    }

    private void updateChargeSpeed() {
        if (this.isPassenger() && this.getVehicle() != null) {
            Vec3 m = this.getVehicle().getDeltaMovement();
            double s = Math.sqrt(m.x * m.x + m.z * m.z);
            if (s > lastChargeSpeed) lastChargeSpeed = s;
            else lastChargeSpeed *= 0.92;
            if (lastChargeSpeed < 0.05) lastChargeSpeed = 0;
        } else lastChargeSpeed = 0;
    }

    private void tickRegen() {
        if (!CombatConfig.enableOutOfCombatRegen) return;
        if (this.getHealth() >= this.getMaxHealth()) return;
        if (this.tickCount - lastHurtTick < CombatConfig.outOfCombatRegenDelay) return;

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) return;

        AbstractArmyCommander commander = getCommander();
        if (commander != null && commander.getCurrentThreat() != null) return;

        if (this.tickCount % CombatConfig.outOfCombatRegenInterval == 0) {
            this.heal(Math.max(1.0f, this.getMaxHealth() * 0.05f));
        }
    }

    private int tierUpgradeTicks(EquipmentTier tier) {
        int idx = tier.ordinal();
        return idx <= 0 ? 0 : GeneralConfig.tierUpgradeBaseTicks * idx;
    }

    private void upgradeTo(EquipmentTier tier) {
        setTier(tier);
        applyEquipment(tier);
        this.heal(this.getMaxHealth());
        if (this.getVehicle() instanceof WarHorseEntity horse) {
            horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(getCavalryHorseArmor()));
            var hp = horse.getAttribute(Attributes.MAX_HEALTH);
            if (hp != null) {
                double hm = getCavalryHorseMaxHealth();
                hp.setBaseValue(hm);
                horse.setHealth((float) hm);
            }
        }
    }

    /** 马（含骑手）能否站在 (x, y, z)：详见 {@link AiNavUtil#mountFitsAt}。 */
    private boolean mountFitsAt(double x, double y, double z) {
        return AiNavUtil.mountFitsAt(level(), this, x, y, z);
    }

    /**
     * 卡住时使用：朝 reference 方向 1.5 / 2.5 格处马（含骑手）都站不下
     *   → 判定"骑马过不去的窄道 / 低矮通道"，主动下马步行，并进入免重上马冷却。
     *   非骑兵 / 玩家强制骑兵 / 没有参照点时不处理。
     *
     * @return 是否执行了下马
     */
    public boolean tryDismountForMountTerrain(Vec3 reference) {
        if (!(getVehicle() instanceof WarHorseEntity)) return false;
        if (isForcedCavalry()) return false;
        if (reference == null) return false;

        double dx = reference.x - getX();
        double dz = reference.z - getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) return false;

        double nx = dx / len;
        double nz = dz / len;
        boolean fitNear = mountFitsAt(getX() + nx * 1.5, getY(), getZ() + nz * 1.5);
        boolean fitFar = mountFitsAt(getX() + nx * 2.5, getY(), getZ() + nz * 2.5);
        if (fitNear && fitFar) return false;

        switchToInfantry();
        terrainDismountCooldown = 120;   // 6 秒内不自动重上马，先走完这段路
        resetStuckTracking();
        return true;
    }

    /** 玩家是否用轮盘强制该士兵保持骑兵模式（强制模式下不做地形自动下马）。 */
    private boolean isForcedCavalry() {
        if (!isPlayerArmy()) return false;
        Player owner = getCommanderPlayer();
        if (owner == null) return false;
        var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(owner.getUUID());
        if (st == null) return false;
        return com.ohhapple.monster_conquest.player.PlayerCommandState
                .stateForCommand(this, st)
                .getRideMode()
                == com.ohhapple.monster_conquest.player.PlayerCommandState.RideMode.FORCED_CAVALRY;
    }

    private void evaluateFormSwitch() {
        // 站在梯子 / 脚手架 / 藤蔓上时不做任何形态切换：
        //   坐骑没法攀爬，而且在梯子上凭空生成战马很怪（玩家强制骑兵会在离开梯子后自动恢复）。
        if (AiNavUtil.isOnClimbable(this)) return;
        // 换形态（上马 / 下马）时由 switchToCavalry / switchToInfantry 清掉旧绕路路线，
        //   由上层按新形态重新规划，所以这里不再禁用"开阔地形自动上马"。

        if (isPlayerArmy()) {
            Player owner = getCommanderPlayer();
            if (owner != null) {
                var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                        .getIfPresent(owner.getUUID());
                if (st != null) {
                    var ss = com.ohhapple.monster_conquest.player.PlayerCommandState
                            .stateForCommand(this, st);
                    var ride = ss.getRideMode();
                    if (ride == com.ohhapple.monster_conquest.player.PlayerCommandState.RideMode.FORCED_CAVALRY) {
                        if (isDrowned() && this.isInWater()) return;
                        // 玩家强制骑兵 → 立即响应，清掉地形下马的免重上马冷却
                        terrainDismountCooldown = 0;
                        if (!this.isPassenger()) switchToCavalry();
                        return;
                    }
                    if (ride == com.ohhapple.monster_conquest.player.PlayerCommandState.RideMode.FORCED_INFANTRY) {
                        if (this.isPassenger()) switchToInfantry();
                        return;
                    }
                }
            }
        }

        if (!GeneralConfig.enableFormSwitch) return;
        if (isDrowned() && this.isInWater()) return;

        // 因地形主动下马后的冷却：先步行通过窄道 / 低矮路段，冷却结束再自动上马，
        //   否则会站在窄道口"下马 → 上马 → 再下马"来回循环。
        if (terrainDismountCooldown > 0) return;

        // 上马 / 保持骑兵都要先确认"马 + 骑手"在当前位置站得下：
        //   1 格宽的缺口 / 门、2 格高的低矮通道（骑手坐在马背上头高约 2.75 格）
        //   都会被挡住 → 不开阔 → 不会上马 / 在马上则下马。
        boolean open = isOpenTerrainCached() && mountFitsAt(getX(), getY(), getZ());
        boolean mounted = this.isPassenger();
        LivingEntity target = getTarget();
        boolean hasTarget = target != null && target.isAlive();

        if (!hasTarget) {
            if (open && !mounted) switchToCavalry();
            else if (!open && mounted) {
                // 地形不让骑马 → 下马后先步行一段时间再考虑重上马，
                //   否则会在窄道 / 迷宫入口反复"上马 → 下马"（表现为骑着马来回跑）
                switchToInfantry();
                terrainDismountCooldown = 120;
            }
            return;
        }

        // 远程兵不再有独立的 50% 上马概率：与其他士兵完全一致 ——
        //   地形开阔就上马，地形不开阔（窄道 / 低矮通道）就下马。
        if (!mounted && open) switchToCavalry();
        else if (mounted && !open) {
            switchToInfantry();
            terrainDismountCooldown = 120;
        }
    }

    public boolean isOpenTerrainCached() {
        if (tickCount - openTerrainCacheTick > OPEN_TERRAIN_CACHE_TICKS) {
            cachedOpenTerrain = isOpenTerrain();
            openTerrainCacheTick = tickCount;
        }
        return cachedOpenTerrain;
    }

    public boolean isOpenTerrain() {
        BlockPos center = blockPosition();
        int solidCount = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                if (level().getBlockState(center.offset(dx, 0, dz)).isSolid()) solidCount++;
            }
        }
        boolean hasCeiling = false;
        for (int dy = 1; dy <= 3; dy++) {
            if (level().getBlockState(center.above(dy)).isSolid()) { hasCeiling = true; break; }
        }
        return !hasCeiling && solidCount < 6;
    }

    public void switchToCavalry() {
        if (!this.isAlive()) return;
        if (this.isPassenger()) return;
        // 募兵站待命（未编队 / 未接命令）期间不自动上马
        if (recruitStandby) return;
        // 溺尸在水里不骑马：水下必须是步兵形态（这里是所有上马入口的统一兜底）
        if (isDrowned() && this.isInWater()) return;
        if (!(level() instanceof ServerLevel sl)) return;
        // 因地形主动下马后的冷却期内不响应自动上马（强制骑兵会先清掉冷却）
        if (terrainDismountCooldown > 0) return;
        // 马（含骑手）在当前位置站不下 / 周围活动不开（窄道 / 低矮通道 / 小坑壁龛）→ 不生成马
        double mountY = AiNavUtil.resolveMountY(level(), this, getX(), getY(), getZ());
        if (Double.isNaN(mountY)) return;

        WarHorseEntity horse = ModEntities.WAR_HORSE.get().create(sl);
        if (horse == null) return;

        horse.moveTo(getX(), mountY, getZ(), getYRot(), 0);
        horse.setOwner(this);
        horse.getInventory().setItem(0, new ItemStack(Items.SADDLE));
        horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(getCavalryHorseArmor()));

        var spd = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(getMountedRideSpeed());

        double horseMaxHp = getCavalryHorseMaxHealth();
        var hp = horse.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(horseMaxHp);
            horse.setHealth((float) horseMaxHp);
        }
        var horseArmor = horse.getAttribute(Attributes.ARMOR);
        if (horseArmor != null) {
            horseArmor.setBaseValue(getCavalryHorseArmorBonus());
        }

        sl.addFreshEntity(horse);
        startRiding(horse, true);

        if (!getSoldierType().isCavalry()) setSoldierType(getSoldierType().toggleMounted());
        applyEquipment(getTier());
        // 形态变了（步兵 → 骑乘）：旧的绕路路线是按步兵净空算的，作废让上层按骑乘重新规划
        clearMarchRoute();
    }

    /**
     * 爬梯子 / 脚手架 / 藤蔓前必须下马（坐骑无法攀爬），并在攀爬期间保持下马。
     *   由 AiNavUtil.tryClimbLadder 在需要攀爬时调用（可重复调用，安全）。
     */
    public void dismountForClimb() {
        if (getVehicle() instanceof WarHorseEntity) {
            switchToInfantry();
            resetStuckTracking();
        }
        // 攀爬期间抑制自动重上马（与"窄道主动下马"共用同一个冷却）
        terrainDismountCooldown = Math.max(terrainDismountCooldown, 120);
    }

    public void switchToInfantry() {
        boolean wasMounted = this.isPassenger();
        Entity vehicle = getVehicle();
        if (wasMounted) {
            stopRiding();
            if (vehicle != null) vehicle.discard();
        }
        if (getSoldierType().isCavalry()) setSoldierType(getSoldierType().toggleMounted());
        applyEquipment(getTier());
        // 形态变了（骑乘 → 步兵）：作废按坐骑净空算的绕路路线
        clearMarchRoute();

        // 清掉骑兵残留的移动 / 寻路状态：
        //   立即停止坐骑留下的路径与速度、丢弃按"马 + 骑手"净空规划的绕路路线，
        //   让士兵下一 tick 以纯步兵状态重新寻路（否则会沿用马匹的旧路线，显得迟钝）。
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
        this.lastChargeSpeed = 0;
        this.hasImpulse = true;
        clearRoute();
        // 骑兵路线是按"马 + 骑手"净空算的，下马后作废，重新按步兵净空寻路
        clearMarchRoute();
        lastRoutePlanTick = -10000;
        resetStuckTracking();
    }

    private void controlMount() {
        if (!(getVehicle() instanceof WarHorseEntity horse)) return;
        if (horse.isPassingThrough()) return;

        LivingEntity target = getTarget();
        if (target == null || !target.isAlive()) {
            horse.setCharging(false);
            return;
        }

        SoldierRole role = getRole();
        // 纯远程（弓手 / 枪手 / 投掷 / 法师）不冲锋。
        //   双形态（凋零骷髅）骑兵：拿弓时保持距离射击，切到骑枪（近战模式，目标 5 格内）后才冲锋穿刺，
        //   这样上下马 / 弓枪切换都不会丢掉冲锋。
        if (role.isRanged() && !role.isDual()) {
            horse.setCharging(false);
            return;
        }
        if (role.isDual() && !(getMainHandItem().getItem() instanceof LanceItem)) {
            horse.setCharging(false);
            return;
        }

        // 非骑枪骑兵（卫道士 / 重锤兵）：不做"穿身冲锋 + 穿刺"，
        //   由 SoldierMeleeAttackGoal 的马背分支正常靠近 + 挥砍（单体，攻速与步兵一致）。
        if (!usesLanceInCavalry()) {
            horse.setCharging(false);
            return;
        }

        Vec3 toTarget = target.position().subtract(horse.position());
        double horizDist = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);

        // 冲锋方向：直指敌人本体。不做任何偏移。
        double dirX, dirZ;
        if (horizDist < 0.1) {
            float yaw = horse.getYRot() * (float) Math.PI / 180f;
            dirX = -Math.sin(yaw);
            dirZ =  Math.cos(yaw);
        } else {
            dirX = toTarget.x / horizDist;
            dirZ = toTarget.z / horizDist;
        }

        // 冲锋前先确认冲锋线上没有被方块挡住（贴脸也一样）：
        //   被墙 / 地形挡住 → 放弃冲锋，改走寻路绕路（绕墙、穿门），
        //   否则骑兵会顶着墙"锁人"不动。
        if (!AiNavUtil.hasClearChargeLine(level(), horse, target)) {
            horse.setCharging(false);
            horse.getNavigation().moveTo(target, 1.0D);
            return;
        }

        // 靠近到 2.5 格 → 触发穿越（冲过敌人身体）
        if (horizDist < 2.5) {
            horse.startPassThrough(new Vec3(dirX, 0, dirZ));
            horse.setCharging(true);
            double charge = Math.max(getCavalryChargeSpeed(), 0.6);
            Vec3 dm = horse.getDeltaMovement();
            horse.setDeltaMovement(dirX * charge, dm.y, dirZ * charge);
            horse.hasImpulse = true;
            return;
        }

        double charge = getCavalryChargeSpeed();
        Vec3 current = horse.getDeltaMovement();
        horse.setDeltaMovement(dirX * charge, current.y, dirZ * charge);
        horse.hasImpulse = true;
        horse.setCharging(true);

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        applyLimitedYaw(horse, desiredYaw, CAVALRY_MAX_TURN_PER_TICK);

        this.setYRot(horse.getYRot());
        this.yBodyRot = horse.getYRot();
        this.setYHeadRot(horse.getYRot());
    }


    private static void applyLimitedYaw(WarHorseEntity horse, float desiredYaw, float maxDelta) {
        float current = horse.getYRot();
        float delta = Mth.wrapDegrees(desiredYaw - current);
        if (delta > maxDelta) delta = maxDelta;
        if (delta < -maxDelta) delta = -maxDelta;
        float result = current + delta;
        horse.setYRot(result);
        horse.yBodyRot = result;
        horse.setYHeadRot(result);
        horse.yRotO = result;
        horse.yBodyRotO = result;
        horse.yHeadRotO = result;
    }

    // ===================== 装备 =====================
    public void applyEquipment(EquipmentTier tier) {
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(tier.getHelmet()));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(tier.getChestplate()));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(tier.getLeggings()));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(tier.getBoots()));

        for (EquipmentSlot slot : EquipmentSlot.values()) this.setDropChance(slot, 0.0f);

        // 属性基础值独立方法（存档重载时也复用）
        applyAttributesForTier(tier);

        if (!(level() instanceof ServerLevel sl)) {
            applyWeaponWithoutEnchant(tier);
            refreshDefaultGear();
            return;
        }

        HolderLookup.RegistryLookup<Enchantment> enchants =
                sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        RandomSource rand = this.getRandom();

        applyWeaponWithoutEnchant(tier);

        if (tier.getMinEnchant() > 0) {
            if (isDualWeapon()) {
                if (usesTridentForDual()) {
                    ItemStack trident = new ItemStack(Items.TRIDENT);
                    int min = tier.getMinEnchant();
                    int max = tier.getMaxEnchant();
                    if (min > 0) {
                        int sharpLvl = Math.min(min + rand.nextInt(Math.max(1, max - min + 1)), 5);
                        trident.enchant(enchants.getOrThrow(Enchantments.SHARPNESS), Math.max(1, sharpLvl));
                        if (tier.ordinal() >= 2) {
                            int impaling = Math.min(tier.ordinal(), 5);
                            trident.enchant(enchants.getOrThrow(Enchantments.IMPALING), impaling);
                        }
                    }
                    dualBowStack = trident;
                    dualMeleeSwordStack = trident;
                    dualMeleeLanceStack = trident;
                    setItemSlot(EquipmentSlot.MAINHAND, trident.copy());
                } else {
                    dualBowStack = buildEnchantedBow(tier, enchants, rand);
                    dualMeleeSwordStack = buildEnchantedMelee(new ItemStack(tier.getWeapon()), tier, enchants, rand);
                    dualMeleeLanceStack = buildEnchantedMelee(new ItemStack(tier.getLanceWeapon()), tier, enchants, rand);
                    setItemSlot(EquipmentSlot.MAINHAND, dualBowStack.copy());
                }
            } else if (getRole().isThrower()) {
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            } else if (getRole().isMage()) {
                // 魔法师（唤魔者士兵）：徒手施法，手持施法媒介"木棍"。
                setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            } else if (getRole().isArcher()) {
                setItemSlot(EquipmentSlot.MAINHAND, buildEnchantedBow(tier, enchants, rand));
            } else if (getRole().isGunner()) {
                Item gun = getGunItem();
                setItemSlot(EquipmentSlot.MAINHAND,
                        new ItemStack(gun != null ? gun : Items.CROSSBOW));
            } else {
                boolean useLance = getSoldierType().isCavalry() && this.isPassenger()
                        && usesLanceInCavalry();
                ItemStack base = new ItemStack(useLance
                        ? tier.getLanceWeapon() : getMeleeWeaponForTier(tier));
                setItemSlot(EquipmentSlot.MAINHAND,
                        buildMeleeWeapon(base, tier, enchants, rand));
            }

            enchantArmorPiece(EquipmentSlot.HEAD, enchants, tier.getMinEnchant(), tier.getMaxEnchant(), rand, Enchantments.PROTECTION, 4);
            enchantArmorPiece(EquipmentSlot.CHEST, enchants, tier.getMinEnchant(), tier.getMaxEnchant(), rand, Enchantments.PROTECTION, 4);
            enchantArmorPiece(EquipmentSlot.LEGS, enchants, tier.getMinEnchant(), tier.getMaxEnchant(), rand, Enchantments.PROTECTION, 4);
            enchantArmorPiece(EquipmentSlot.FEET, enchants, tier.getMinEnchant(), tier.getMaxEnchant(), rand, Enchantments.PROTECTION, 4);
            enchantArmorPiece(EquipmentSlot.FEET, enchants, tier.getMinEnchant(), tier.getMaxEnchant(), rand, Enchantments.FEATHER_FALLING, 4);
        }
        // 附魔写完之后再记自带装备快照，并把玩家手动装备盖回身上（否则快照记的是没附魔的那一份）
        refreshDefaultGear();
    }

    /**
     * 只设置属性基础值，不动装备槽位与附魔。
     *   applyEquipment 首次生成时调用；readAdditionalSaveData 存档重载时调用。
     */
    private void applyAttributesForTier(EquipmentTier tier) {
        var maxHealth = this.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) maxHealth.setBaseValue(getMaxHealthForTier(tier));
        var atk = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(getMeleeDamageForTier(tier));
        var speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(getMovementSpeed());
        var armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(getArmorForTier(tier));
    }

    private void applyWeaponWithoutEnchant(EquipmentTier tier) {
        SoldierRole role = getRole();

        if (isDualWeapon()) {
            if (usesTridentForDual()) {
                ItemStack trident = new ItemStack(Items.TRIDENT);
                dualBowStack = trident;
                dualMeleeSwordStack = trident;
                dualMeleeLanceStack = trident;
                this.setItemSlot(EquipmentSlot.MAINHAND, trident.copy());
                this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AIR));
                return;
            }
            dualBowStack = new ItemStack(Items.BOW);
            dualMeleeSwordStack = new ItemStack(tier.getWeapon());
            dualMeleeLanceStack = new ItemStack(tier.getLanceWeapon());
            this.setItemSlot(EquipmentSlot.MAINHAND, dualBowStack.copy());
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AIR));
            return;
        }
        if (role.isThrower()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AIR));
            return;
        }
        if (role.isMage()) {
            // 魔法师（唤魔者士兵）：副手空手，徒手施法。
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STICK));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AIR));
            return;
        }
        if (role.isGunner()) {
            Item gun = getGunItem();
            this.setItemSlot(EquipmentSlot.MAINHAND,
                    new ItemStack(gun != null ? gun : Items.CROSSBOW));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AIR));
        } else if (role.isArcher()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
            this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.AIR));
        } else {
            boolean useLance = getSoldierType().isCavalry() && this.isPassenger()
                    && usesLanceInCavalry();
            Item weaponItem = useLance ? tier.getLanceWeapon() : getMeleeWeaponForTier(tier);
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weaponItem));
            // 盾牌带阵营旗图案（底色 + 十字，与身上渲染的阵营旗一致）
            this.setItemSlot(EquipmentSlot.OFFHAND, FactionShield.create(level(), getFactionId()));
        }
    }

    /**
     * 近战武器附魔。默认走 {@link #buildEnchantedMelee}（锋利等）；
     *   子类可覆盖：例如村民重锤兵给重锤附上原版"破甲"。
     */
    protected ItemStack buildMeleeWeapon(ItemStack base, EquipmentTier tier,
                                         HolderLookup.RegistryLookup<Enchantment> enchants,
                                         RandomSource rand) {
        return buildEnchantedMelee(base, tier, enchants, rand);
    }

    private ItemStack buildEnchantedBow(EquipmentTier tier,
                                        HolderLookup.RegistryLookup<Enchantment> enchants,
                                        RandomSource rand) {
        ItemStack bow = new ItemStack(Items.BOW);
        int min = tier.getMinEnchant();
        int max = tier.getMaxEnchant();
        int tierIdx = tier.ordinal();
        if (min <= 0) return bow;

        int powerLvl = Math.min(Math.max(1, min + tierIdx), 5);
        bow.enchant(enchants.getOrThrow(Enchantments.POWER), powerLvl);
        if (tierIdx >= 3) bow.enchant(enchants.getOrThrow(Enchantments.FLAME), 1);
        else if (tierIdx >= 2 && rand.nextFloat() < 0.5f) bow.enchant(enchants.getOrThrow(Enchantments.FLAME), 1);
        if (tierIdx >= 2 && rand.nextFloat() < 0.5f)
            bow.enchant(enchants.getOrThrow(Enchantments.PUNCH), 1 + rand.nextInt(2));
        return bow;
    }

    private ItemStack buildEnchantedMelee(ItemStack base, EquipmentTier tier,
                                          HolderLookup.RegistryLookup<Enchantment> enchants,
                                          RandomSource rand) {
        int min = tier.getMinEnchant();
        int max = tier.getMaxEnchant();
        int tierIdx = tier.ordinal();
        if (min <= 0) return base;
        int sharpLvl = Math.min(min + rand.nextInt(Math.max(1, max - min + 1)), 5);
        base.enchant(enchants.getOrThrow(Enchantments.SHARPNESS), Math.max(1, sharpLvl));
        if (rand.nextFloat() < 0.2f * tierIdx)
            base.enchant(enchants.getOrThrow(Enchantments.FIRE_ASPECT), 1 + rand.nextInt(2));
        if (rand.nextFloat() < 0.15f * tierIdx)
            base.enchant(enchants.getOrThrow(Enchantments.KNOCKBACK), 1 + rand.nextInt(2));
        return base;
    }

    private void enchantArmorPiece(EquipmentSlot slot,
                                   HolderLookup.RegistryLookup<Enchantment> enchants,
                                   int min, int max, RandomSource rand,
                                   ResourceKey<Enchantment> key, int cap) {
        ItemStack stack = this.getItemBySlot(slot);
        if (stack.isEmpty()) return;
        int lvl = Math.max(1, Math.min(min + rand.nextInt(Math.max(1, max - min + 1)), cap));
        stack.enchant(enchants.getOrThrow(key), lvl);
    }

    // ===================== 近战 =====================
    @Override
    public boolean doHurtTarget(Entity target) {
        if (isAttackStopped()) return false;

        // 近战高度差限制：站在墙头 / 高台上的目标砍不到（骑枪允许略大一点的高度差）
        if (target instanceof LivingEntity livingTarget) {
            double maxDy = this.getMainHandItem().getItem() instanceof LanceItem ? 2.0 : 1.5;
            if (Math.abs(this.getY() - livingTarget.getY()) > maxDy) return false;
        }

        // 不穿墙：近战 / 骑枪 / 挥锤都要求身体中心之间没有方块遮挡
        if (target instanceof LivingEntity livingTarget
                && !hasBodyLineOfSight(livingTarget)) {
            return false;
        }

        ItemStack weapon = this.getMainHandItem();
        if (weapon.getItem() instanceof LanceItem lance && this.isPassenger()
                && target instanceof LivingEntity living) {
            double speed = lastChargeSpeed;
            if (speed >= 0.05) {
                float multiplier = 1.0f + (float) ((speed - 0.05) * 5.0);
                float dmg = lance.getBaseDamage() * multiplier;
                boolean hit = living.hurt(this.damageSources().mobAttack(this), dmg);
                if (hit) {
                    markAttackSuccess();
                    double dx = target.getX() - this.getX();
                    double dz = target.getZ() - this.getZ();
                    double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                    living.knockback(0.8, -dx / len, -dz / len);
                    if (this.getVehicle() instanceof WarHorseEntity horse) {
                        // 命中即穿越：与"近身触发穿越"保持一致，不再掷概率
                        horse.startPassThrough(new Vec3(dx / len, 0, dz / len));
                    }
                    if (!level().isClientSide) {
                        ThreatDispatcher.propagate(living, this);
                    }
                }
                return hit;
            }
        }

        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity living) {
            markAttackSuccess();
            applyMeleeEffects(living);
            if (!level().isClientSide) {
                ThreatDispatcher.propagate(living, this);
            }
        }
        return hit;
    }

    // ===================== 远程 =====================
    /**
     * 身体视线：身体中心 → 身体中心之间没有被方块挡住。
     *   近战挥砍 / 骑枪穿刺 / 唤魔者施法共用（比"眼睛到目标中心"更严格，
     *   1 格高的矮墙也会挡住，符合"不能隔墙打人"的要求）。
     */
    public boolean hasBodyLineOfSight(LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        return level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    /** 直射射界：眼睛到目标中心之间没有被方块挡住。 */
    public boolean hasClearShot(LivingEntity target) {
        Vec3 from = getEyePosition();
        Vec3 to = target.getBoundingBox().getCenter();
        return level().clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
                .getType() == HitResult.Type.MISS;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        tryPerformRangedAttack(target);
    }

    public boolean tryPerformRangedAttack(LivingEntity target) {
        if (!(level() instanceof ServerLevel)) return false;

        // 枪械弹道几乎无下坠，不会像弓箭那样自然失效 → 显式限制射程。
        if (usesDirectFire()) {
            double maxRange = getMaxAttackRange(getSoldierType().isCavalry());
            if (target.distanceTo(this) > maxRange + 2.0) return false;

            // 射界检查：直射武器前方被方块（掩体 / 墙 / 地形）挡住时不开火，
            //   tryPerformRangedAttack 返回 false 后由远程 Goal 的重定位逻辑挪位。
            //   弓手保留抛物线越障能力，不做这个限制。
            if (!hasClearShot(target)) return false;
        }

        double baseSpeed = getProjectileSpeed();
        double speedJitter = 0.85 + this.getRandom().nextDouble() * 0.30;

        Vec3 dir;
        double speed;
        double targetDist = target.distanceTo(this);
        if (usesDirectFire() && targetDist <= 3.0) {
            // 贴脸（3 格内）直接瞄向目标：高速弹几乎无下坠，
            //   避免弹道解算在极近距离/高低差下解不出正确仰角而打空。
            Vec3 eye = getEyePosition();
            // 瞄身体中心（60% 身高）而不是眼睛，避免 AI 每一发都判定成爆头
            Vec3 aimPoint = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
            Vec3 delta = aimPoint.subtract(eye);
            if (delta.lengthSqr() < 1.0E-6) return false;
            dir = delta.normalize();
            speed = baseSpeed * speedJitter;
        } else if (usesDirectFire()) {
            GunAim.Solution sol = GunAim.solve(
                    level(), this, target,
                    baseSpeed * speedJitter, getProjectileGravity());
            if (sol == null) return false;
            dir = new Vec3(sol.dirX(), sol.dirY(), sol.dirZ());
            speed = sol.speed();
        } else {
            ResourceLocation myFaction = getFactionId();
            BallisticAim.Solution sol = BallisticAim.solve(
                    level(), this, target,
                    baseSpeed * speedJitter,
                    e -> {
                        if (!(e instanceof LivingEntity)) return false;
                        ResourceLocation f = ArmyThreatUtil.factionOf(e);
                        return f != null && f.equals(myFaction);
                    });
            if (sol == null) return false;
            dir = new Vec3(sol.dirX(), sol.dirY(), sol.dirZ());
            speed = sol.speed();
        }

        int shots = Math.max(1, getProjectilesPerShot());
        double spread = getShotSpreadDegrees();
        boolean fired = false;

        for (int i = 0; i < shots; i++) {
            Projectile proj = createProjectile();
            if (proj == null) continue;

            Vec3 shotDir = dir;
            if (shots > 1 && spread > 0.0) {
                shotDir = perturbDirection(dir, spread, this.getRandom());
            }

            proj.shoot(shotDir.x, shotDir.y, shotDir.z,
                    (float) speed, (float) getShotInaccuracy());
            applyProjectileDamage(proj);
            this.level().addFreshEntity(proj);
            fired = true;
        }

        if (fired) {
            markAttackSuccess();
            SoundEvent sound = getShootSound();
            if (sound != null) {
                // 绑定实体播放：客户端声音跟随士兵实体，移动中也不会偏位
                this.level().playSound(null, this, sound, this.getSoundSource(), 1.0F,
                        1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F)
                                * getShootSoundPitch());
            }
        }
        return fired;
    }

    public void performVolleyShot(Vec3 targetPos) {
        if (!(level() instanceof ServerLevel)) return;

        double sx = getX();
        double sy = getEyeY() - 0.1;
        double sz = getZ();
        double dx = targetPos.x - sx;
        double dy = targetPos.y - sy;
        double dz = targetPos.z - sz;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 0.01) return;

        double dirX = dx / horiz;
        double dirZ = dz / horiz;

        int shots = Math.max(1, getProjectilesPerShot());
        double spread = getShotSpreadDegrees();
        boolean fired = false;

        if (usesDirectFire()) {
            // 枪械：直瞄目标点，几乎无下坠。
            double baseSpeed = getProjectileSpeed();
            double speedJitter = 0.99 + this.getRandom().nextDouble() * 0.02;
            double speed = baseSpeed * speedJitter;
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            Vec3 baseDir = new Vec3(dx / len, dy / len, dz / len);

            for (int i = 0; i < shots; i++) {
                Projectile proj = createProjectile();
                if (proj == null) continue;
                Vec3 shotDir = (shots > 1 && spread > 0.0)
                        ? perturbDirection(baseDir, spread, this.getRandom())
                        : baseDir;
                proj.shoot(shotDir.x, shotDir.y, shotDir.z, (float) speed, 0.1f);
                applyProjectileDamage(proj);
                this.level().addFreshEntity(proj);
                fired = true;
            }
        } else {
            double baseSpeed = getProjectileSpeed();
            double speedJitter = 0.99 + this.getRandom().nextDouble() * 0.02;
            double speed = baseSpeed * speedJitter;

            Double pitch = solveVolleyPitch(dy, horiz, speed);
            double pitchRad = pitch != null ? pitch : Math.toRadians(30.0);

            double cosP = Math.cos(pitchRad);
            double finalDirX = dirX * cosP;
            double finalDirY = Math.sin(pitchRad);
            double finalDirZ = dirZ * cosP;

            Projectile proj = createProjectile();
            if (proj != null) {
                proj.shoot(finalDirX, finalDirY, finalDirZ, (float) speed, 0.1f);
                applyProjectileDamage(proj);
                this.level().addFreshEntity(proj);
                fired = true;
            }
        }

        if (fired) {
            SoundEvent sound = getShootSound();
            if (sound != null) {
                this.level().playSound(null, this, sound, this.getSoundSource(), 1.0F,
                        1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F)
                                * getShootSoundPitch());
            }
        }
        // 枪械不开火不播放攻击动画
        if (!getRole().isGunner()) {
            this.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        }
    }

    /**
     * 按士兵当前的远程伤害给抛射物设置基础伤害。
     * 弓（AbstractArrow）与枪械弹丸（ArmyGunProjectile）走各自接口。
     */
    private void applyProjectileDamage(Projectile proj) {
        GunSpec spec = getGunSpec();
        if (proj instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(spec != null ? spec.damage() : getRangedDamageForTier(getTier()));
        } else if (proj instanceof com.ohhapple.monster_conquest.entity.projectile.ArmyGunProjectile gun) {
            gun.setBaseDamage(spec != null ? spec.damage() : getRangedDamageForTier(getTier()));
            if (spec != null) {
                // 穿甲 / 爆头 / 最大飞行距离与玩家枪械一致
                gun.setArmorPenetration(spec.armorPenetration());
                gun.setHeadshotMultiplier(spec.headshotMultiplier());
                gun.setMaxRange(spec.rangeBlocks() * 1.2);
            }
        }
    }

    /** 在基准方向上叠加一个圆锥内随机偏移（霰弹枪 / 玩家枪械散布共用）。 */
    public static Vec3 perturbDirection(Vec3 dir, double maxDegrees, RandomSource random) {
        if (maxDegrees <= 0.0) return dir;
        double maxRad = Math.toRadians(maxDegrees);
        double angle = maxRad * Math.sqrt(random.nextDouble());
        double around = random.nextDouble() * Math.PI * 2.0;

        Vec3 axis = dir.normalize();
        Vec3 ref = Math.abs(axis.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = axis.cross(ref).normalize();
        Vec3 v = axis.cross(u).normalize();

        double sinAngle = Math.sin(angle);
        return axis.scale(Math.cos(angle))
                .add(u.scale(sinAngle * Math.cos(around)))
                .add(v.scale(sinAngle * Math.sin(around)));
    }

    private static Double solveVolleyPitch(double dy, double horiz, double speed) {
        for (double deg = -20.0; deg <= 75.0; deg += 1.0) {
            double pitch = Math.toRadians(deg);
            double[] r = simulateY(pitch, horiz, speed);
            if (r == null) continue;
            if (Math.abs(r[0] - dy) < 0.2) return pitch;
        }
        double best = Double.NaN;
        double bestErr = Double.MAX_VALUE;
        for (double deg = -20.0; deg <= 75.0; deg += 1.0) {
            double pitch = Math.toRadians(deg);
            double[] r = simulateY(pitch, horiz, speed);
            if (r == null) continue;
            double err = Math.abs(r[0] - dy);
            if (err < bestErr) { bestErr = err; best = pitch; }
        }
        return Double.isNaN(best) ? null : best;
    }

    private static double[] simulateY(double pitch, double horiz, double speed) {
        double vh = Math.cos(pitch) * speed;
        double vy = Math.sin(pitch) * speed;
        if (vh <= 0.0001) return null;
        double x = 0, y = 0;
        double prevX = 0, prevY = 0;

        for (int t = 0; t < 600; t++) {
            prevX = x;
            prevY = y;
            x += vh;
            y += vy;
            if (x >= horiz) {
                double frac = (x - prevX) < 0.0001 ? 0 : (horiz - prevX) / (x - prevX);
                double exactY = prevY + (y - prevY) * frac;
                double exactTicks = t + frac;
                return new double[]{exactY, exactTicks};
            }
            vh *= 0.99;
            vy = vy * 0.99 - 0.05;
        }
        return null;
    }

    // ===================== 伤害 / 盾牌 =====================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFriendlyFire(source)) return false;

        if (!level().isClientSide) {
            Entity atk = source.getEntity();
            if (atk == null) atk = source.getDirectEntity();
            if (atk instanceof Projectile proj) atk = proj.getOwner();
            if (atk instanceof LivingEntity attacker && attacker != this) {
                ThreatDispatcher.propagate(this, attacker);
            }
        }

        float blocked = computeShieldBlock(source, amount);
        if (blocked > 0f) playShieldBlockSound();
        float remaining = amount - blocked;
        if (remaining <= 0f) return false;

        boolean hurt = super.hurt(source, remaining);
        if (hurt) lastHurtTick = this.tickCount;
        return hurt;
    }

    private float computeShieldBlock(DamageSource source, float amount) {
        // 自定义 / 模组盾牌同样能格挡
        if (!CombatRules.isShieldItem(this.getOffhandItem())) return 0f;
        // 魔法伤害 / 破盾伤害一律无法被盾牌格挡。
        if (CombatRules.bypassesShieldBlock(source)) return 0f;
        double chance = CombatConfig.shieldBlockChance;
        if (chance <= 0.0) return 0f;
        if (this.getRandom().nextDouble() >= chance) return 0f;
        double maxAbsorb = CombatConfig.shieldBlockMaxAbsorb
                + getTier().ordinal() * CombatConfig.shieldBlockMaxAbsorbPerTier;
        return (float) Math.min(amount, maxAbsorb);
    }

    private void playShieldBlockSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                SoundEvents.SHIELD_BLOCK, this.getSoundSource(),
                1.0f, 0.8f + this.getRandom().nextFloat() * 0.4f);
    }

    private boolean isFriendlyFire(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) attacker = source.getDirectEntity();
        if (attacker instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner instanceof LivingEntity living) attacker = living;
        }
        if (!(attacker instanceof LivingEntity livingAttacker)) return false;
        if (livingAttacker == this) return true;

        UUID myArmy = resolveArmyUuid(this, level());
        UUID theirArmy = resolveArmyUuid(livingAttacker, level());
        if (myArmy != null && myArmy.equals(theirArmy)) return true;

        ResourceLocation myFac = getFactionId();
        ResourceLocation theirFac = factionOf(livingAttacker);
        if (myFac != null && theirFac != null
                && FactionAlly
                .isAlly(theirFac, myFac)) return true;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不伤害。
        return NativePeace.isPeacefulPair(this, livingAttacker);
    }

    // ===================== 等级 / 兵种 =====================
    public EquipmentTier getTier() {
        int idx = this.entityData.get(DATA_TIER);
        EquipmentTier[] all = EquipmentTier.values();
        return all[Math.max(0, Math.min(idx, all.length - 1))];
    }

    public void setTier(EquipmentTier tier) { this.entityData.set(DATA_TIER, tier.ordinal()); }

    public SoldierType getSoldierType() {
        int idx = this.entityData.get(DATA_SOLDIER_TYPE);
        SoldierType[] all = SoldierType.values();
        return all[Math.max(0, Math.min(idx, all.length - 1))];
    }

    public void setSoldierType(SoldierType type) {
        this.entityData.set(DATA_SOLDIER_TYPE, type.ordinal());
        applyFollowRange();
    }

    public int getArmyAge() { return armyAge; }
    public void setArmyAge(int age) { this.armyAge = Math.max(0, age); }

    private void applyFollowRange() {
        var attr = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (attr == null) return;
        attr.setBaseValue(getFollowRange());
    }

    public boolean isAtFormationSlot() { return atFormationSlot; }

    public boolean isInCombatGrace() {
        // 有活目标才走宽限时间窗；无活目标立即结束 grace，士兵马上归队
        LivingEntity t = getTarget();
        if (t == null || !t.isAlive()) return false;
        return this.tickCount - lastCombatTick < COMBAT_GRACE_TICKS;
    }
    public void setAtFormationSlot(boolean v) { this.atFormationSlot = v; }

    // ===================== 存档 =====================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.entityData.get(DATA_COMMANDER).ifPresent(uuid -> tag.putUUID("Commander", uuid));
        tag.putInt("Tier", getTier().ordinal());
        tag.putInt("SoldierType", getSoldierType().ordinal());
        tag.putInt("ArmyAge", armyAge);
        String faction = this.entityData.get(DATA_FACTION);
        if (faction != null && !faction.isEmpty()) tag.putString("Faction", faction);
        tag.putLong("SpawnGT", spawnGameTime);
        tag.putLong("LastOrderGen", lastProcessedOrderGen);
        tag.putBoolean("OrderAccepted", playerOrderAccepted);
        tag.putBoolean("EverOrdered", playerEverOrdered);
        tag.putInt("SquadId", getSquadId());
        if (wild) tag.putBoolean("MC_Wild", true);
        // 手动装备 / 自带装备快照 / 战术装备
        tag.put("ManualGear", stackListTag(manualGear));
        tag.put("DefaultGear", stackListTag(defaultGear));
        tag.put("ManualTactical", stackListTag(manualTactical));
        if (movementOverride != null) tag.putInt("MoveOverride", movementOverride.getId());
        if (attackOverride != null) tag.putInt("AttackOverride", attackOverride.getId());
        if (rideOverride != null) tag.putInt("RideOverride", rideOverride.getId());
        if (formationOverride != null) tag.putInt("FormationOverride", formationOverride.getId());
        // 募兵站"待命"状态（编入队伍 / 接命令前强制步兵）
        tag.putBoolean("RecruitStandby", recruitStandby);
        if (recruitStandbyAnchor != null) {
            tag.putDouble("StandbyX", recruitStandbyAnchor.x);
            tag.putDouble("StandbyY", recruitStandbyAnchor.y);
            tag.putDouble("StandbyZ", recruitStandbyAnchor.z);
        }
        // 无小队单兵的个人指令状态（软驻守 / 锚点）也写进存档
        if (loneState != null) tag.put("LoneState", loneState.save());
        // MC 不保存 attribute baseValue，Tier 决定的最大生命值会在 load 时回退默认。
        //   用自定义 key 保存原始血量。
        tag.putFloat("MC_Health", getHealth());
        // 村庄 / 袭击锚点（普通单位无锚点，不写入）
        anchor.save(tag, "Anchor");
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Commander")) setCommanderUuid(tag.getUUID("Commander"));
        setTier(EquipmentTier.values()[tag.getInt("Tier")]);
        this.entityData.set(DATA_SOLDIER_TYPE, tag.getInt("SoldierType"));
        armyAge = tag.getInt("ArmyAge");
        this.wild = tag.getBoolean("MC_Wild");
        readStackList(tag.getList("ManualGear", Tag.TAG_COMPOUND), manualGear);
        readStackList(tag.getList("DefaultGear", Tag.TAG_COMPOUND), defaultGear);
        readStackList(tag.getList("ManualTactical", Tag.TAG_COMPOUND), manualTactical);
        movementOverride = tag.contains("MoveOverride")
                ? PlayerCommandState.MovementMode.byId(tag.getInt("MoveOverride")) : null;
        attackOverride = tag.contains("AttackOverride")
                ? PlayerCommandState.AttackMode.byId(tag.getInt("AttackOverride")) : null;
        rideOverride = tag.contains("RideOverride")
                ? PlayerCommandState.RideMode.byId(tag.getInt("RideOverride")) : null;
        formationOverride = tag.contains("FormationOverride")
                ? PlayerCommandState.FormationMode.byId(tag.getInt("FormationOverride")) : null;
        // 老存档没有自带装备快照：用身上现有的补一份（否则取下手动装备会把单位扒空）
        ensureDefaultGear();
        refreshManualGearMask();
        this.entityData.set(DATA_FACTION, tag.getString("Faction"));

        // 重新应用 Tier 属性基础值（MC 不保存 attribute baseValue）
        applyAttributesForTier(getTier());
        applyFollowRange();

        this.lastStuckX = getX();
        this.lastStuckY = getY();
        this.lastStuckZ = getZ();

        spawnGameTime = tag.getLong("SpawnGT");
        lastProcessedOrderGen = tag.getLong("LastOrderGen");
        playerOrderAccepted = tag.getBoolean("OrderAccepted");
        playerEverOrdered = tag.getBoolean("EverOrdered");
        setSquadId(tag.getInt("SquadId"));
        if (tag.contains("LoneState")) {
            loneState().load(tag.getCompound("LoneState"));
        }
        if (tag.getBoolean("RecruitStandby")) {
            recruitStandby = true;
            recruitStandbyAnchor = new Vec3(
                    tag.getDouble("StandbyX"), tag.getDouble("StandbyY"), tag.getDouble("StandbyZ"));
        }

        // 恢复存档血量（Tier 属性已生效，setHealth 不会被截断）
        if (tag.contains("MC_Health")) {
            this.setHealth(tag.getFloat("MC_Health"));
        }
        anchor.load(tag, "Anchor");
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);
        applyEquipment(getTier());
        applyFollowRange();
        this.lastStuckX = getX();
        this.lastStuckY = getY();
        this.lastStuckZ = getZ();
        return result;
    }
}
