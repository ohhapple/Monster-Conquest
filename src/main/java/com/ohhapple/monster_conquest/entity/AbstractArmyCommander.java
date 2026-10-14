package com.ohhapple.monster_conquest.entity;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.config.FactionConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.ai.commander.CommanderCombatGoal;
import com.ohhapple.monster_conquest.ai.commander.CommanderMarchGoal;
import com.ohhapple.monster_conquest.ai.nav.AiNavUtil;
import com.ohhapple.monster_conquest.ai.nav.BallisticAim;
import com.ohhapple.monster_conquest.ai.nav.LancePierce;
import com.ohhapple.monster_conquest.ai.nav.RoutePlanner;
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
import com.ohhapple.monster_conquest.item.CommanderBlade;
import com.ohhapple.monster_conquest.item.LanceItem;
import com.ohhapple.monster_conquest.util.PeacefulUtil;
import com.ohhapple.monster_conquest.world.ArmySpawnUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

public abstract class AbstractArmyCommander extends PathfinderMob
        implements RangedAttackMob, ArmyAnchor.AnchoredUnit, ManagedUnit {

    public enum WeaponMode { BOW, LANCE, BLADE }
    public enum Formation { LINE, WEDGE, SHIELD_WALL, SKIRMISH }

    protected static final EntityDataAccessor<String> DATA_FACTION =
            SynchedEntityData.defineId(AbstractArmyCommander.class, EntityDataSerializers.STRING);

    /** 手动装备掩码（按位对应 6 个手动装备栏）：客户端渲染阵营图案时要知道哪些部位被玩家装备盖住了。 */
    protected static final EntityDataAccessor<Byte> DATA_MANUAL_GEAR =
            SynchedEntityData.defineId(AbstractArmyCommander.class, EntityDataSerializers.BYTE);

    /**
     * 单位锚点：随村庄刷出的巡逻队长 / 随袭击刷出的增援才带锚点，
     *   锚点限制巡逻、战斗与索敌的活动范围；普通单位（刷怪蛋 / 指令 / 召唤）无锚点。
     */
    private final ArmyAnchor anchor = new ArmyAnchor();
    /** 锚点拉回动作的冷却（tick），避免每 tick 重复寻路。 */
    private int anchorLeashCooldown = 0;
    /** 野外自然刷出的单位：不绑锚点，远离玩家后像普通怪物一样消失。 */
    private boolean wild;

    public boolean isWild() { return wild; }

    public void setWild(boolean wild) { this.wild = wild; }
    /** 锚点保护传送的卡住门槛：连续多轮没走动（约 10 秒）才允许直接传送回锚点。 */
    private static final int ANCHOR_LEASH_TELEPORT_TICKS = 200;

    @Override
    public ArmyAnchor getAnchor() { return anchor; }

    /** 锚点位置（无锚点时为 null）。 */
    @Nullable public BlockPos getAnchorPos() { return anchor.getPos(); }
    /** 是否是随村庄刷出的村庄巡逻队长。 */
    public boolean isVillageBound() { return anchor.isVillage(); }

    private static final double LOW_HP_THRESHOLD = 0.3;
    private static final float CAVALRY_MAX_TURN_PER_TICK = 12f;

    private final Map<UUID, HatredEntry> hatredMap = new HashMap<>();

    private static final class HatredEntry {
        final LivingEntity entity;
        final int expiryTick;
        HatredEntry(LivingEntity entity, int expiryTick) {
            this.entity = entity;
            this.expiryTick = expiryTick;
        }
    }
    private boolean equipmentInitialized = false;
    private int lastHurtTick = -10000;

    private boolean cachedOpenTerrain = false;
    private int openTerrainCacheTick = -1000;
    private static final int OPEN_TERRAIN_CACHE_TICKS = 100;

    private int summonTimer = -1;

    private List<AbstractArmySoldier> soldierCache = new ArrayList<>();
    private int soldierCacheTick = -1;
    private static final int SOLDIER_CACHE_REFRESH_TICKS = 20;

    /**
     * 编制名单：士兵每 20 tick 自己登记一次（uuid → 最后登记的 gameTime）。
     *   周围扫描（armyFollowRange）之外的掉队兵靠它留在编制里；否则长纵队 /
     *   绕路掉队的士兵拿不到阵型槽位，队伍停下后只会散在首领身边。
     */
    private final Map<UUID, Long> soldierRoster = new HashMap<>();
    /** 连续多久没登记（tick）就从编制名单里剔除（换编 / 死亡 / 区块卸载）。 */
    private static final long SOLDIER_ROSTER_STALE_TICKS = 300L;

    private WeaponMode weaponMode = WeaponMode.BLADE;
    private WeaponMode appliedMode = null;
    private double lastChargeSpeed = 0;
    private boolean retreating = false;

    /** 骑枪穿刺的每目标冷却（与近战士兵共用 LancePierce，独立计时）。 */
    private final java.util.Map<java.util.UUID, Integer> lanceHitCd = new java.util.HashMap<>();

    @Nullable private Vec3 manualMarchTarget = null;

    /** 行军轨迹：无战斗目标行进时记录的路点（索引 0 最旧），士兵沿它排队跟随。 */
    private final java.util.ArrayList<Vec3> marchTrail = new java.util.ArrayList<>();
    private int marchTrailExpireTick = 0;
    private boolean marching = false;
    /** 轨迹最多保留的路点数（约 64 格）。 */
    private static final int MARCH_TRAIL_MAX_POINTS = 64;
    /** 轨迹点最小间距平方（每走 1 格记一个点）。 */
    private static final double MARCH_TRAIL_STEP_SQR = 1.0;
    /** 首领停下后轨迹保留时间，供落后的士兵走完最后一段。 */
    private static final int MARCH_TRAIL_LINGER_TICKS = 400;

    private Formation formation = Formation.LINE;
    private int formationEvalTimer = 0;
    /**
     * 阵型朝向：指挥官最近一次「行进方向」。
     *
     *   士兵到达阵型槽位时锁定这个方向（而不是指挥官当下的 yRot），
     *   这样指挥官站定后可以自然转头（自由游走 / 待机环顾），
     *   同时整支军队的阵型朝向保持统一。
     */
    private float formationFacingYaw = 0f;
    private boolean formationFacingInit = false;

    public float getFormationFacing() {
        if (!formationFacingInit) {
            formationFacingYaw = this.getYRot();
            formationFacingInit = true;
        }
        return formationFacingYaw;
    }

    private int stuckCheckTimer = 0;
    private double lastStuckX, lastStuckY, lastStuckZ;
    private int stuckAccumTicks = 0;
    /** "在动但没接近目标"（绕路 / 目标移动 / 被挤）的累计时间：只用于重新规划，不触发传送。 */
    private int wanderAccumTicks = 0;
    /** "到目标完全没有可行路线"的累计时间：即使一直在动（例如陷在坑里）也会累计，用于脱困传送。 */
    private int noPathAccumTicks = 0;
    /** 上次探测"到目标有没有路"的 tick（限流，避免频繁 A* 规划拖慢服务器）。 */
    private int lastPathProbeTick = -10000;
    /** "寻路不通"被暂时拉黑的目标。 */
    @Nullable private java.util.UUID unreachableTargetId = null;
    private int unreachableTargetUntil = 0;
    /** 完全无路可走时触发传送脱困的累计时间（tick）。10 秒。 */
    private static final int NO_PATH_TELEPORT_TICKS = 200;
    /** "到目标有没有路"的探测间隔（tick）。4 秒一次。 */
    private static final int PATH_PROBE_INTERVAL_TICKS = 80;
    /** "寻路不通"目标的拉黑时长（tick）。30 秒。 */
    private static final int UNREACHABLE_BLACKLIST_TICKS = 600;
    /** 允许"传送到敌人附近"的最大高度差（格）：超过说明敌人在墙头 / 塔楼，不传送过去。 */
    private static final double MAX_ENEMY_TELEPORT_Y_DIFF = 2.0;
    private double lastTargetDistSq = -1;
    /** 上一次处理过的仇恨重置标记（重置工具 / 和平模式会清目标与仇恨）。 */
    private long lastHatredResetStamp = 0L;
    /** 上一次卡住检测时到手动行军目标的距离平方。 */
    private double lastMarchDistSq = -1;

    // 绕路规划 / 路线跟随状态（与士兵同一套逻辑）
    private final java.util.ArrayDeque<Vec3> routePoints = new java.util.ArrayDeque<>();
    @Nullable private UUID routeTargetId = null;
    private int routeExpireTick = 0;
    private int routeIssueTick = 0;
    private int lastRoutePlanTick = -10000;
    private int routeWaypointTicks = 0;
    private int routeStallTicks = 0;
    private double routeLastDistSq = -1.0;
    private boolean routeReachesGoal = false;
    private java.util.List<BlockPos> lastRoutePoints = java.util.List.of();
    /** 手动行军目标的"方块级 A* 绕路"跟随器（迷宫 / 隔墙，与士兵共用）。 */
    private final com.ohhapple.monster_conquest.ai.nav.MarchRouteFollower marchRoute =
            new com.ohhapple.monster_conquest.ai.nav.MarchRouteFollower();
    /** 因地形主动下马后的免重上马冷却（tick）。 */
    private int terrainDismountCooldown = 0;
    /** 崎岖地形连续确认计数（避免边缘地形反复上马 / 下马）。 */
    private int roughTerrainChecks = 0;
    private static final int ROUGH_TERRAIN_DISMOUNT_CHECKS = 3;

    private ItemStack bowStack = ItemStack.EMPTY;
    private ItemStack lanceStack = ItemStack.EMPTY;
    private ItemStack bladeStack = ItemStack.EMPTY;
    /** 阵营长杆旗帜：非战斗时手持（颜色随阵营，换阵营后刷新）。 */
    private ItemStack bannerStack = ItemStack.EMPTY;

    private final Map<UUID, Vec3> formationSlots = new HashMap<>();
    private int formationCacheTick = Integer.MIN_VALUE;

    private enum SummonCategory { MELEE, RANGED, DUAL, WITCH, MAGE }

    protected AbstractArmyCommander(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.xpReward = getCommanderXpReward();
        // 构造末尾立即应用子类覆盖的属性。
        //   必须放在这里而不是 finalizeSpawn —— 刷怪蛋 / addFreshEntity 都不触发 finalizeSpawn。
        //   所有子类的 getCommanderXxx() 都是常量（不依赖子类构造字段），虚方法调用安全。
        applyCommanderAttributes();
        this.setHealth((float) getCommanderMaxHealth());
    }

    // ===================== 子类必须实现 =====================
    public abstract ResourceLocation getDefaultFactionId();

    // ===================== 数值（子类可覆盖） =====================

    /** 首领最大生命值。 */
    public double getCommanderMaxHealth() { return 100.0D; }

    /** 首领近战攻击伤害。 */
    public double getCommanderAttackDamage() { return 8.0D; }

    /** 首领护甲值。 */
    public double getCommanderArmor() { return 6.0D; }

    /** 首领击退抗性（0~1）。 */
    public double getCommanderKnockbackResistance() { return 0.5D; }

    /** 首领远程（弓）基础伤害。 */
    public float getCommanderRangedDamage() { return (float) CombatConfig.arrowBaseDamage; }

    /** 首领移动速度（格/tick）。 */
    public double getCommanderMovementSpeed() { return 0.33D; }

    /** 首领骑乘时马的移动速度（格/tick）。默认 0.4，子类覆盖。 */
    /** 骑兵马速：各首领 / 巡逻队长在自己的类里直接写数值（不乘任何倍率）。 */
    public double getCommanderHorseSpeed() { return 0.4; }

    /** 首领骑乘冲锋时的水平速度（格/tick）。默认 0.5，子类覆盖。 */
    public double getCommanderChargeSpeed() { return 0.5; }
    /**
     * 首领 HP 低于撤退阈值（30%）时"撤离战场"的导航速度倍率。
     *   子类可覆盖。
     */
    public double getRetreatSpeedModifier() { return 1.0; }

    /** 首领马的最大生命值。默认 = 首领最大生命值。 */
    public double getCommanderHorseMaxHealth() { return getCommanderMaxHealth(); }

    /** 首领马铠。默认下界合金。 */
    public Item getCommanderHorseArmor() { return Items.DIAMOND_HORSE_ARMOR; }
    /** 马铠之外的额外护甲值。默认 0，子类覆盖。 */
    public double getCommanderHorseArmorBonus() { return 0.0; }

    /**
     * 首领索敌半径：64 格，仍受 GeneralConfig.armyFollowRange 上限约束。
     *   统一用 128 格索敌扫描开销偏大、也容易隔墙锁敌。
     */
    public double getCommanderFollowRange() {
        return Math.min(64.0, GeneralConfig.armyFollowRange);
    }

    /** 首领经验值。 */
    public int getCommanderXpReward() { return 50; }

    // ===================== 阵营 =====================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_FACTION, "");
        builder.define(DATA_MANUAL_GEAR, (byte) 0);
    }

    public final ResourceLocation getFactionId() {
        String s = this.entityData.get(DATA_FACTION);
        if (s == null || s.isEmpty()) return getDefaultFactionId();
        ResourceLocation rl = ResourceLocation.tryParse(s);
        return rl != null ? rl : getDefaultFactionId();
    }

    public void setFactionId(@Nullable ResourceLocation id) {
        this.entityData.set(DATA_FACTION, id == null ? "" : id.toString());
        refreshFactionShield();
    }

    /** 阵营变化后刷新盾牌上的阵营旗图案（装备已生成时才处理）。 */
    private void refreshFactionShield() {
        if (level().isClientSide) return;
        ItemStack off = this.getOffhandItem();
        // 副手被玩家手动替换（自定义盾牌等）→ 不动玩家放的那件
        if (off.is(Items.SHIELD) && !hasManualGear(5)) {
            FactionShield.apply(off, level(), getFactionId());
        }
        // 长杆阵营旗也随阵营刷新（颜色图案与身上 / 盾牌一致）
        bannerStack = FactionBanner.create(Monster_conquest.FACTION_BANNER.get(), getFactionId());
        if (!hasManualMainHand()
                && this.getMainHandItem().is(Monster_conquest.FACTION_BANNER.get())) {
            this.setItemSlot(EquipmentSlot.MAINHAND, bannerStack.copy());
        }
    }

    // ===================== 属性 =====================
    public static AttributeSupplier.Builder createAttributes() {
        // 首领同样不再继承 Monster（避免被别的模组当"敌对生物"默认锁定）。
        return Mob.createMobAttributes().add(Attributes.ATTACK_DAMAGE)
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.27D)
                .add(Attributes.FOLLOW_RANGE, 128.0D)
                .add(Attributes.ARMOR, 3.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5D);
    }

    public void setWeaponMode(WeaponMode mode) { this.weaponMode = mode; }
    public WeaponMode getWeaponMode() { return weaponMode; }
    public Formation getFormation() { return formation; }

    public void setFormation(Formation f) {
        this.formation = f;
        this.formationCacheTick = Integer.MIN_VALUE;
    }

    // ===================== 控制工具：手动行军目标 =====================
    @Nullable public Vec3 getManualMarchTarget() { return manualMarchTarget; }

    public void setManualMarchTarget(@Nullable Vec3 target) {
        this.manualMarchTarget = target;
    }

    /** 是否正在无战斗目标行军（士兵沿行军轨迹排队跟随，不再各自排阵型）。 */
    public boolean isMarching() { return marching; }

    /**
     * 行军轨迹点（只读，索引 0 = 最旧，末尾最接近首领当前位置）。
     *   首领停下后保留一小段时间，让落后的士兵走完最后一段再展开阵型。
     */
    public java.util.List<Vec3> getMarchTrail() { return marchTrail; }

    // ===================== AI =====================
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new CommanderCombatGoal(this));
        this.goalSelector.addGoal(7, new CommanderMarchGoal(this));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // 同士兵：去掉 HurtByTargetGoal，避免首领被夹击时来回换目标。
        this.targetSelector.addGoal(1, new AttackConfiguredEnemyGoal(this));
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        // 和平模式：不索敌、不反击
        if (PeacefulUtil.isPeaceful(this)) return false;
        // 锚点保护：不攻击锚点活动范围之外的目标（村庄巡逻 / 袭击增援）
        if (!ArmyAnchor.targetAllowed(this, target)) return false;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (target == this) return false;
        if (target instanceof WarHorseEntity) return false;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不索敌。
        if (NativePeace.isPeacefulPair(this, target)) return false;

        if (target instanceof AbstractArmySoldier
                || target instanceof AbstractArmyCommander
                || target instanceof Player) {
            UUID targetArmy = AbstractArmySoldier.resolveArmyUuid(target, level());
            if (targetArmy != null && targetArmy.equals(this.getUUID())) return false;

            ResourceLocation myFac = getFactionId();
            ResourceLocation targetFac = factionOf(target);
            if (myFac != null && targetFac != null
                    && FactionAlly
                    .isAlly(myFac, targetFac)) {
                return false;
            }

            if (getLastHurtByMob() == target) {
                ResourceLocation myF = getFactionId();
                ResourceLocation theirF = factionOf(target);
                if (myF == null || theirF == null || !myF.equals(theirF)) return true;
            }

            if (targetArmy != null && ArmyRelations.isHostile(this.getUUID(), targetArmy)) return true;

            ResourceLocation myFaction = this.getFactionId();
            ResourceLocation targetFaction = factionOf(target);

            // 玩家阵营互打：受 Config 控制
            //   - autoHostilityBetweenPlayerArmies = true  → 允许主动敌对
            //   - false（默认）→ 不主动敌对，但被打了仍会走上面的 getLastHurtByMob 反击
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

    @Override
    public PathNavigation getNavigation() {
        if (this.getVehicle() instanceof Mob horse) return horse.getNavigation();
        return super.getNavigation();
    }

    // ===================== 掉落 =====================

    /** 首领与巡逻队长死亡不掉落任何物品与经验。 */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
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

    // ===================== Tick =====================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        // 爬梯子：目标是上方、身边 / 脚下有梯子时自动攀爬
        AiNavUtil.tryClimbLadder(this, resolveClimbGoalY());
        // 移动控制器"串线"保护：坐骑的移动目标点离自己超过 6 格（上马前 / 旧路径残留）
        //   → 清掉，否则快速坐骑会掉头朝旧点走（到目标前原地转圈）。
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
        // 通用上坎 / 上岸跳跃
        if (!AiNavUtil.isOnClimbable(this)) {
            AiNavUtil.tryStepUpJump(this);
        }
        // 每 5 tick 主动开一次附近的木门 / 栅栏门 / 木活板门（避免站在门前等一会儿才开）
        if (tickCount % 5 == 0) {
            AiNavUtil.tryOpenNearbyDoors(this);
        }

        // 和平模式：单位保留，但清掉目标 / 仇恨（不结仇、不攻击）
        if (PeacefulUtil.isPeaceful(this)) {
            if (super.getTarget() != null) super.setTarget(null);
            if (!hatredMap.isEmpty()) hatredMap.clear();
            if (isFollowingRoute()) clearRoute();
        }

        // 仇恨重置（重置工具 / 和平模式）：清掉目标、仇恨与追击路线
        long resetStamp = ThreatDispatcher.hatredResetStamp();
        if (resetStamp != lastHatredResetStamp) {
            lastHatredResetStamp = resetStamp;
            if (super.getTarget() != null) super.setTarget(null);
            if (!hatredMap.isEmpty()) hatredMap.clear();
            if (isFollowingRoute()) clearRoute();
            setLastHurtByMob(null);
        }

        if (getNavigation() instanceof GroundPathNavigation gpn) gpn.setCanOpenDoors(true);

        applyWaterFloat();
        if (!equipmentInitialized) {
            equipCommander();
            equipmentInitialized = true;
            this.lastStuckX = getX();
            this.lastStuckY = getY();
            this.lastStuckZ = getZ();
        }

        updateChargeSpeed();
        updateRetreatState();
        if (terrainDismountCooldown > 0) terrainDismountCooldown--;

        if (weaponMode == WeaponMode.LANCE && !this.isPassenger()) weaponMode = WeaponMode.BLADE;

        tickHatred();
        if (getTarget() instanceof Player p2 && (p2.isCreative() || p2.isSpectator())) setTarget(null);

        if (tickCount % Math.max(1, GeneralConfig.recruitInterval) == 0) tryRecruit();
        if (tickCount % 40 == 0) tryRecruitStrays();

        trySummonIfFew();
        controlMount();
        tickLancePierce();
        syncWeapon();
        tickStuckCheck();
        tickRoute();
        tickMarchRoute();
        tickTerrainDismountCheck();
        enforceAnchorLeash();

        if (tickCount % 20 == 0 && (getTarget() == null || !getTarget().isAlive())) {
            LivingEntity pick = pickOwnThreat();
            if (pick != null) setTarget(pick);
        }

        if (++formationEvalTimer >= 100) {
            formationEvalTimer = 0;
            evaluateFormation();
        }

        // 骑乘时用坐骑的导航 / 速度判断是否在移动（骑手自身速度恒为 0）
        Mob moveVehicle = this.getVehicle() instanceof Mob m ? m : this;
        boolean moving = !moveVehicle.getNavigation().isDone()
                || moveVehicle.getDeltaMovement().horizontalDistanceSqr() > 0.001;

        tickMarchTrail(moveVehicle, moving);

        if (moving) {
            // 只在行进中刷新阵型朝向：取最近一段行军路线的方向（比瞬时朝向稳定，
            //   拐弯收尾时整片阵型不会被带偏）；没有路线时退回坐骑 / 自身朝向。
            //   站定后本值冻结，首领可以自然环顾而不影响阵型。
            Float routeYaw = trailFacingYaw();
            formationFacingYaw = routeYaw != null ? routeYaw : moveVehicle.getYRot();
            formationFacingInit = true;
        }

        tickRegen();
    }

    /**
     * 锚点保护（村庄巡逻队 / 袭击增援）：
     *   · 目标超出锚点活动半径 → 放弃目标（不追出村庄 / 袭击范围）；
     *   · 自己超出活动半径 → 取消行军目标与追击路线后往回走，离得太远直接传送回锚点附近；
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

        if (isMarching()) setManualMarchTarget(null);
        if (isFollowingRoute()) clearRoute();

        if (--anchorLeashCooldown > 0) return;
        anchorLeashCooldown = 20;

        double hard = r + 16.0;
        if (d2 > hard * hard && stuckAccumTicks >= ANCHOR_LEASH_TELEPORT_TICKS) {
            // 只有"确实卡住走不动"才传送回锚点：
            //   追击敌人 / 绕路时离锚点远属于正常机动，能走就自己走回去，不传送。
            Vec3 back = anchor.randomPointNear(getRandom(), 0.4);
            this.randomTeleport(back.x, back.y, back.z, false);
        } else {
            Vec3 back = anchor.randomPointNear(getRandom(), 0.7);
            this.getNavigation().moveTo(back.x, back.y, back.z, 1.0);
        }
    }

    /**
     * 行军轨迹：无战斗目标行进时每约 1 格记一个路点，士兵沿这条轨迹排队跟随；
     *   首领停下后轨迹保留一段时间，让落后的士兵走完最后一段再展开阵型。
     */
    private void tickMarchTrail(Mob mover, boolean moving) {
        boolean marchNow = moving && !retreating && getCombatTarget() == null;
        if (marchNow) {
            Vec3 pos = mover.position();
            if (marchTrail.isEmpty()
                    || marchTrail.get(marchTrail.size() - 1).distanceToSqr(pos) > MARCH_TRAIL_STEP_SQR) {
                marchTrail.add(pos);
                while (marchTrail.size() > MARCH_TRAIL_MAX_POINTS) marchTrail.remove(0);
            }
            marchTrailExpireTick = tickCount + MARCH_TRAIL_LINGER_TICKS;
        } else if (!marchTrail.isEmpty() && tickCount > marchTrailExpireTick) {
            marchTrail.clear();
        }
        marching = marchNow;
    }

    /** 阵型朝向：最近一段行军路线（约 6 格）的方向；路线太短时返回 null。 */
    @Nullable
    private Float trailFacingYaw() {
        int n = marchTrail.size();
        if (n < 2) return null;
        Vec3 last = marchTrail.get(n - 1);
        Vec3 back = marchTrail.get(0);
        for (int i = n - 2; i >= 0; i--) {
            Vec3 p = marchTrail.get(i);
            if (p.distanceToSqr(last) >= 36.0) {
                back = p;
                break;
            }
        }
        double dx = last.x - back.x;
        double dz = last.z - back.z;
        if (dx * dx + dz * dz < 1.0) return null;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private void tickStuckCheck() {
        if (++stuckCheckTimer < 40) return;
        stuckCheckTimer = 0;

        Entity mover = getVehicle();
        if (mover == null) mover = this;

        double dx = mover.getX() - lastStuckX;
        double dy = mover.getY() - lastStuckY;
        double dz = mover.getZ() - lastStuckZ;
        double movedSq = dx * dx + dy * dy + dz * dz;

        double eyeY = (mover instanceof LivingEntity le) ? le.getEyeY() : mover.getY() + 1.5;
        BlockPos feet = mover.blockPosition();
        BlockPos head = BlockPos.containing(mover.getX(), eyeY, mover.getZ());
        BlockPos middle = BlockPos.containing(mover.getX(),
                mover.getY() + mover.getBbHeight() * 0.5, mover.getZ());
        boolean suffocating =
                level().getBlockState(feet).isSuffocating(level(), feet)
                        || level().getBlockState(middle).isSuffocating(level(), middle)
                        || level().getBlockState(head).isSuffocating(level(), head);

        if (suffocating) {
            teleportToSafeSpot();
            stuckAccumTicks = 0;
            lastStuckX = mover.getX();
            lastStuckY = mover.getY();
            lastStuckZ = mover.getZ();
            lastTargetDistSq = -1;
            lastMarchDistSq = -1;
            return;
        }

        // 战斗中的卡住判定：已经打得到目标 → 原地输出是正常状态，
        //   不累积卡住时间、不传送。只有"一段时间没缩短距离 + 打不到敌人"
        //   才会走到下面的传送；非战斗（行军 / 归队）照常累积。
        //   例外：带着手动行军命令时必须继续赶路，站着不动一律按卡住处理。
        LivingEntity engaged = getCombatTarget();
        if (engaged != null && engaged.isAlive() && canCurrentlyAttack(engaged)
                && getManualMarchTarget() == null) {
            stuckAccumTicks = 0;
            lastStuckX = mover.getX();
            lastStuckY = mover.getY();
            lastStuckZ = mover.getZ();
            lastTargetDistSq = -1;
            lastMarchDistSq = -1;
            return;
        }

        boolean pathActive;
        if (getVehicle() instanceof Mob mount) {
            pathActive = !mount.getNavigation().isDone();
            if (!pathActive && mount.getDeltaMovement().horizontalDistanceSqr() > 0.001) pathActive = true;
        } else {
            pathActive = !getNavigation().isDone();
        }

        LivingEntity target = getCombatTarget();
        double currentDistSq = -1;
        boolean shouldMoveToTarget = false;
        if (target != null && target.isAlive()) {
            currentDistSq = mover.distanceToSqr(target);
            shouldMoveToTarget = currentDistSq > 4.0;
        }
        // 手动行军：把"没朝目标点缩短距离"也算作卡住（顶着墙原地推 / 来回抽搐）
        Vec3 marchTarget = getManualMarchTarget();
        double marchDistSq = marchTarget == null ? -1 : mover.position().distanceToSqr(marchTarget);
        // 行军目标还没到也算"应该在动"：否则卡住时原版导航先 stop，这里会误判成"无事可做"而不累计卡住
        boolean shouldMove = pathActive || shouldMoveToTarget || marchDistSq > 4.0;

        // 区分"真的没动（撞墙 / 卡地形）"和"在动但没接近目标（绕路 / 目标在移动 / 被挤）"：
        //   只有前者会累积并触发传送；后者只做重新规划，避免"没卡住却瞬移到目标旁边"。
        boolean stuck = false;
        boolean slowProgress = false;
        if (shouldMove) {
            if (movedSq < 0.04) {
                stuck = true;
            } else if (marchDistSq > 0 && lastMarchDistSq > 0
                    && marchDistSq > lastMarchDistSq - 0.5) {
                slowProgress = true;
            } else if (currentDistSq > 0 && lastTargetDistSq > 0
                    && currentDistSq > lastTargetDistSq - 0.5) {
                slowProgress = true;
            }
        }

        // 绝对距离 + 路径距离结合判定（限流：只在出现卡住 / 徘徊迹象时，且最多每 4 秒探测一次；
        //   路径规划开销大，不能每个首领每 2 秒都跑一次，否则服务器会掉刻）：
        //   · 完全没有可行路线（例如陷在坑里 / 被墙围死）→ 累计约 10 秒后脱困传送；
        //     战斗中寻路不通时不会传送到敌人附近，只把目标暂时拉黑；
        //   · 有路线（哪怕要绕很远）→ 视为绕路，不传送。
        // 只认"真的没动"（movedSq 极小）：在动但没接近目标（战斗中追人 / 绕路 /
        //   目标在移动）属于正常机动，不探测"无路"、不拉黑目标、更不传送。
        boolean mayBeStuck = stuck;
        if (!mayBeStuck) {
            noPathAccumTicks = 0;
        } else if (tickCount - lastPathProbeTick >= PATH_PROBE_INTERVAL_TICKS) {
            lastPathProbeTick = tickCount;

            LivingEntity pathTarget = (target != null && target.isAlive()) ? target : null;
            Vec3 objective = pathTarget != null ? pathTarget.position() : marchTarget;
            if (objective != null) {
                boolean pathExists = pathTarget != null
                        ? hasPathToTarget(pathTarget)
                        : hasReachablePathTo(objective);
                // 正在按方块级 A* 绕路赶路：这条路线本身就是"路"，不算寻路不通
                if (pathExists || isFollowingMarchRoute()) {
                    noPathAccumTicks = 0;
                } else {
                    noPathAccumTicks += PATH_PROBE_INTERVAL_TICKS;
                    if (noPathAccumTicks >= NO_PATH_TELEPORT_TICKS) {
                        // 战斗中寻路不通：不传送到敌人附近（攻城战不会直接上城墙），
                        //   只把目标暂时拉黑，然后就地脱困。
                        if (pathTarget != null) {
                            markTargetUnreachable(pathTarget);
                            setTarget(null);
                        }
                        teleportToSafeSpot();
                        noPathAccumTicks = 0;
                        stuckAccumTicks = 0;
                        wanderAccumTicks = 0;
                        lastTargetDistSq = -1;
                        lastMarchDistSq = -1;
                        return;
                    }
                }
            } else {
                noPathAccumTicks = 0;
            }
        }

        if (stuck) {
            wanderAccumTicks = 0;
            stuckAccumTicks += 40;
            AiNavUtil.tryOpenNearbyDoors(this);

            if (stuckAccumTicks >= 100 && getVehicle() instanceof WarHorseEntity) {
                // 窄缝（左右都是墙）或前方 1.5 / 2.5 格"马 + 骑手"站不下
                //   （2 格高的低矮通道等）→ 下马步行通过。
                if (isHorseFacingNarrowGap() || mountCannotPassAhead()) {
                    // 保留卡住计时：否则"下马 → 重上马 → 再下马"会一直循环，
                    //   永远累计不到传送阈值（首领在拐角原地抽搐）。
                    int keep = stuckAccumTicks;
                    dismountHorseForTerrain();
                    stuckAccumTicks = keep;
                    return;
                }
            }

            // 战斗中目标"完全不可达"（连绕路都规划不出来）→ 直接传送脱困；
            //   能规划出完整绕路就开始按路线前进，不再继续累计卡住。
            if (stuckAccumTicks >= 100) {
                LivingEntity combat = getCombatTarget();
                if (combat != null && combat.isAlive()) {
                    // 除"原地卡住 5 秒"外，还要求"连续多轮探测都找不到路"，
                    //   避免某一次探测被限流 / 瞬时失败就把首领传走（战斗中尤其明显）。
                    if (!hasPathToTarget(combat)
                            && noPathAccumTicks >= NO_PATH_TELEPORT_TICKS) {
                        // 寻路不通：不传送到敌人附近（防止攻城战直接上城墙），
                        //   只把目标暂时拉黑，然后就地脱困。
                        markTargetUnreachable(combat);
                        setTarget(null);
                        teleportToSafeSpot();
                    }
                    stuckAccumTicks = 0;
                    lastStuckX = getX();
                    lastStuckY = getY();
                    lastStuckZ = getZ();
                    lastTargetDistSq = -1;
                    lastMarchDistSq = -1;
                    return;
                }
                // 行军顶住地形：先丢掉旧路径重新规划（下一 tick 行军 goal 会重新下发），
                //   再拖到 200 tick 才传送。
                if (marchTarget != null) {
                    this.getNavigation().stop();
                    clearRoute();
                }
            }

            if (stuckAccumTicks >= 200) {
                teleportToSafeSpot();
                stuckAccumTicks = 0;
            }
        } else {
            stuckAccumTicks = 0;
            if (slowProgress) {
                wanderAccumTicks += 40;
                // 长时间"在动但没接近目标"：清掉旧路线重新规划，不传送。
                if (wanderAccumTicks >= 1200) {
                    wanderAccumTicks = 0;
                    clearRoute();
                    this.getNavigation().stop();
                }
            } else {
                wanderAccumTicks = 0;
            }
        }

        lastStuckX = mover.getX();
        lastStuckY = mover.getY();
        lastStuckZ = mover.getZ();
        lastTargetDistSq = currentDistSq;
        lastMarchDistSq = marchDistSq;
    }

    /**
     * 朝当前战斗目标 1.5 / 2.5 格处，马（含骑手）是否站不下：
     *   判定为"骑马过不去的低矮 / 狭窄路段"，配合卡住检测主动下马。
     */
    private boolean mountCannotPassAhead() {
        LivingEntity target = getCombatTarget();
        if (target == null || !target.isAlive()) return false;
        double dx = target.getX() - getX();
        double dz = target.getZ() - getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) return false;
        double nx = dx / len;
        double nz = dz / len;
        return !AiNavUtil.mountFitsAt(level(), this,
                        getX() + nx * 1.5, getY(), getZ() + nz * 1.5)
                || !AiNavUtil.mountFitsAt(level(), this,
                        getX() + nx * 2.5, getY(), getZ() + nz * 2.5);
    }

    /**
     * 当前是否已经打得到目标（只用于卡住判定，不影响真正的攻击逻辑）。
     *
     *   与 {@link CommanderCombatGoal} 的武器切换保持一致：
     *     · 未骑乘：近战 3.5 格内（带 1 格余量）能直接砍 → 算打得到；
     *     · 骑乘：8 格内准备骑枪冲锋（卡住冲不动 = 打不到），
     *       8~32 格才会拉弓射箭 → 算打得到。
     *
     *   能打得到 → 原地输出属于正常战斗状态，不应该被判为卡住而传送。
     */
    public boolean canCurrentlyAttack(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        double dx = getX() - target.getX();
        double dz = getZ() - target.getZ();
        double horizSq = dx * dx + dz * dz;

        if (!isPassenger()) {
            double melee = CommanderCombatGoal.MELEE_RANGE + 1.0;
            // 高度差受限：墙头 / 高台上的目标不算"打得到"
            if (horizSq <= melee * melee
                    && Math.abs(getY() - target.getY()) <= CommanderCombatGoal.MAX_MELEE_Y_DIFF) {
                return true;
            }
        }

        double rangedMin = CommanderCombatGoal.RANGED_THRESHOLD;
        double rangedMax = CommanderCombatGoal.RANGED_MAX_RANGE + 1.0;
        return horizSq > rangedMin * rangedMin && horizSq < rangedMax * rangedMax;
    }

    private void applyWaterFloat() {
        if (!this.isInWater()) return;
        if (this.isPassenger()) return;
        BlockPos footPos = this.blockPosition();
        if (!this.level().getBlockState(footPos).getFluidState().is(FluidTags.WATER)) return;
        Vec3 dm = this.getDeltaMovement();
        if (dm.y < 0.03) {
            this.setDeltaMovement(dm.x, dm.y + 0.05, dm.z);
            this.hasImpulse = true;
            this.fallDistance = 0;
        }
    }

    private boolean isHorseFacingNarrowGap() {
        if (!(getVehicle() instanceof LivingEntity horse)) return false;
        float yaw = horse.getYRot() * ((float) Math.PI / 180f);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx =  Math.cos(yaw);
        double rz =  Math.sin(yaw);
        int baseY = (int) Math.floor(horse.getY());
        for (int step = 1; step <= 2; step++) {
            double cx = horse.getX() + fx * step;
            double cz = horse.getZ() + fz * step;
            boolean leftBlocked = true;
            boolean rightBlocked = true;
            for (int dy = 0; dy < 2; dy++) {
                BlockPos lp = BlockPos.containing(cx - rx * 1.0, baseY + dy, cz - rz * 1.0);
                BlockPos rp = BlockPos.containing(cx + rx * 1.0, baseY + dy, cz + rz * 1.0);
                if (!level().getBlockState(lp).isSolid()) leftBlocked = false;
                if (!level().getBlockState(rp).isSolid()) rightBlocked = false;
            }
            if (leftBlocked && rightBlocked) return true;
        }
        return false;
    }

    /**
     * 爬梯子 / 脚手架 / 藤蔓前必须下马（坐骑无法攀爬），并在攀爬期间保持下马。
     *   由 AiNavUtil.tryClimbLadder 在需要攀爬时调用（可重复调用，安全）。
     */
    public void dismountForClimb() {
        if (getVehicle() instanceof WarHorseEntity) {
            dismountHorse();
            clearRoute();
            stuckAccumTicks = 0;
            wanderAccumTicks = 0;
        }
        // 攀爬期间抑制自动重上马（与"窄道主动下马"共用同一个冷却）
        terrainDismountCooldown = Math.max(terrainDismountCooldown, 120);
    }

    private void dismountHorse() {
        Entity vehicle = getVehicle();
        if (vehicle == null) return;
        stopRiding();
        vehicle.discard();
        if (weaponMode == WeaponMode.LANCE) weaponMode = WeaponMode.BLADE;
    }

    // ===================== 绕路规划 / 路线跟随（与士兵同一套逻辑） =====================

    /** 是否正在按规划路线绕路前进。 */
    public boolean isFollowingRoute() { return !routePoints.isEmpty(); }

    /** 是否正在按"行军绕路"（方块级 A* 路线）前往手动指定的行军目标。 */
    public boolean isFollowingMarchRoute() { return marchRoute.isFollowing(); }

    /** 当前绕路路线是否正通向这个指定点（中途改命令时用来判断是否要重新规划）。 */
    public boolean isFollowingMarchTowards(Vec3 target) {
        return marchRoute.isFollowingTowards(target);
    }

    public void clearMarchRoute() { marchRoute.clear(); }

    /**
     * 手动行军目标的方块级 A* 绕路：原版寻路在迷宫 / 长绕路里只能给"半路路径"
     *   （甚至没有路），单位会顶着墙原地打转。这里规划一条真正的绕路并按路径点前进。
     *
     * @return true = 已开始按绕路路线前进
     */
    public boolean startMarchRouteAround(Vec3 target) {
        if (target == null || level().isClientSide) return false;
        // 不再一律下马：先按"马 + 骑手"净空规划，骑马过不去（窄道 / 迷宫）时才下马步行。
        Mob mount = getVehicle() instanceof Mob m ? m : null;
        boolean mounted = getVehicle() instanceof WarHorseEntity;
        Mob mover = mounted && mount != null ? mount : this;
        double extraHeight = mounted ? WarHorseEntity.RIDER_HEIGHT : 0.0;
        // 偏好：自己刚走过的行军轨迹（重规划时不会在迷宫岔路之间来回换）
        java.util.List<BlockPos> favored = null;
        if (!marchTrail.isEmpty()) {
            java.util.List<BlockPos> list = new java.util.ArrayList<>(marchTrail.size());
            for (Vec3 p : marchTrail) list.add(BlockPos.containing(p));
            favored = list;
        }
        return marchRoute.start(this, mounted ? mount : null, target, tickCount, extraHeight,
                favored, this::dismountHorseForTerrain);
    }

    /** 跟随行军绕路路线：在 AI goal 之后执行，保证导航不被覆盖。 */
    private void tickMarchRoute() {
        if (!marchRoute.isFollowing()) return;
        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        LivingEntity combat = getCombatTarget();
        marchRoute.tick(mover, tickCount, combat != null && combat.isAlive());
    }

    /** 是否有"能真正到达该目标"的路线在跟随中（部分路线不算）。 */
    public boolean hasReachingRoute(LivingEntity target) {
        return target != null && !routePoints.isEmpty() && routeReachesGoal
                && routeTargetId != null && routeTargetId.equals(target.getUUID())
                && tickCount <= routeExpireTick;
    }

    /** 到指定坐标是否存在可行路线（绝对距离 vs 路径距离判定用）。 */
    public boolean hasReachablePathTo(@Nullable Vec3 target) {
        if (target == null) return false;
        net.minecraft.world.level.pathfinder.Path path =
                getNavigation().createPath(BlockPos.containing(target), 1);
        return path != null && path.canReach();
    }

    /** 到目标有没有可行路线（限流调用：先看已有路线 → 原版寻路 → 自研 A* 完整规划）。 */
    public boolean hasPathToTarget(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) return false;
        if (hasReachingRoute(target)) return true;
        if (hasReachablePathTo(target.position())) return true;
        return replanRouteTo(target, true);
    }

    /** 该目标是否在"寻路不通"黑名单里（索敌时跳过）。 */
    public boolean isTargetBlacklisted(@Nullable LivingEntity target) {
        return target != null && unreachableTargetId != null
                && unreachableTargetId.equals(target.getUUID())
                && tickCount < unreachableTargetUntil;
    }

    /** 当前想去的目标高度（战斗目标 / 行军目标 / 自己），用于爬梯子判断。 */
    private double resolveClimbGoalY() {
        LivingEntity t = getCombatTarget();
        if (t != null && t.isAlive()) return t.getY();
        Vec3 march = getManualMarchTarget();
        if (march != null) return march.y;
        return getY();
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
        net.minecraft.util.RandomSource random = getRandom();
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double dist = 1.0 + random.nextDouble() * 9.0;
            int x = baseX + (int) Math.round(Math.cos(angle) * dist);
            int z = baseZ + (int) Math.round(Math.sin(angle) * dist);
            for (int y = baseY + 16; y > baseY; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (!isSafeOpenSpot(pos)) continue;
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

    public boolean replanRouteTo(LivingEntity target) { return replanRouteTo(target, false); }

    /**
     * 重新规划到达目标的路线：先完整寻路，必要时放宽寻路半径，
     *   最后退回方块级 A* 绕路；骑乘时若马走不通则按步行净空重规划并下马。
     *
     * @param onlyIfReachable true = 只承认"真正到达目标"的路线（用于仇恨 / 卡住判定）
     */
    public boolean replanRouteTo(LivingEntity target, boolean onlyIfReachable) {
        if (target == null || !target.isAlive()) return false;

        if (!routePoints.isEmpty() && routeTargetId != null
                && routeTargetId.equals(target.getUUID()) && tickCount <= routeExpireTick) {
            return true;
        }

        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        PathNavigation nav = mover.getNavigation();
        nav.stop();
        double speed = 1.0D;

        if (nav.moveTo(target, speed) && pathReaches(nav)) return true;

        // 昂贵尝试的全局闸门（放宽半径、多次原版寻路、自研 A*）：
        //   同一 tick 只放行一个单位，多个单位同时锁定走不到的目标时不会一起硬算。
        if (!AiNavUtil.acquireHeavyRouteAttempt(level().getGameTime())) {
            AiNavUtil.markHeavyAttemptDeferred(true);
            return false;
        }
        AiNavUtil.markHeavyAttemptDeferred(false);

        net.minecraft.world.entity.ai.attributes.AttributeInstance followAttr =
                mover.getAttribute(Attributes.FOLLOW_RANGE);
        double originalFollow = followAttr != null ? followAttr.getBaseValue() : 0.0D;
        try {
            if (followAttr != null) {
                followAttr.setBaseValue(Math.max(originalFollow, GeneralConfig.armyFollowRange));
            }
            // 不再放大节点预算（同士兵：复杂地形下单次寻路几十毫秒，长绕路交给自研 A*）
            if (nav.moveTo(target, speed) && pathReaches(nav)) return true;

            // 目标周围最多试 4 个落脚点：每个点都是一次完整寻路，全试完太贵
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

            if (onlyIfReachable || tickCount - lastRoutePlanTick >= 40) {
                lastRoutePlanTick = tickCount;
                double goalRadius = routeGoalRadius();
                double planRadius = Math.min(96.0, Math.sqrt(distanceToSqr(target)) + 24.0);
                boolean mounted = getVehicle() instanceof WarHorseEntity;
                double extraHeight = mounted ? WarHorseEntity.RIDER_HEIGHT : 0.0;
                java.util.List<BlockPos> favored = routeTargetId != null
                        && routeTargetId.equals(target.getUUID()) ? lastRoutePoints : null;

                RoutePlanner.Result planned = RoutePlanner.plan(level(), mover, target.position(),
                        goalRadius, planRadius, 4200, extraHeight, favored);

                if (mounted && (planned == null || !planned.reachedGoal())) {
                    RoutePlanner.Result onFoot = RoutePlanner.plan(level(), this, target.position(),
                            goalRadius, planRadius, 4200, 0.0, favored);
                    if (onFoot != null && onFoot.points().size() >= 2
                            && (planned == null || onFoot.reachedGoal()
                            || routeEndDistance(onFoot, target)
                                    < routeEndDistance(planned, target) - 1.0)) {
                        dismountHorseForTerrain();
                        planned = onFoot;
                        mover = this;
                        nav = getNavigation();
                    }
                }

                if (planned != null && planned.points().size() >= 2
                        && (!onlyIfReachable || planned.reachedGoal())) {
                    startRoute(planned.points(), target, planned.reachedGoal());
                    return true;
                }
            }

            if (onlyIfReachable) return false;
            return nav.moveTo(target, speed);
        } finally {
            if (followAttr != null) followAttr.setBaseValue(originalFollow);
            nav.resetMaxVisitedNodesMultiplier();
        }
    }

    /** 绕路规划的目标半径：抵达近战距离附近即可。 */
    private double routeGoalRadius() { return CommanderCombatGoal.MELEE_RANGE + 0.5; }

    private static boolean pathReaches(PathNavigation nav) {
        net.minecraft.world.level.pathfinder.Path path = nav.getPath();
        return path != null && path.canReach();
    }

    /** 规划路线终点到目标的距离（比较骑乘 / 步行两条部分路线谁更接近目标）。 */
    private static double routeEndDistance(RoutePlanner.Result route, LivingEntity target) {
        if (route == null || route.points().isEmpty()) return Double.MAX_VALUE;
        BlockPos end = route.points().get(route.points().size() - 1);
        return Math.sqrt(end.distToCenterSqr(target.getX(), target.getY(), target.getZ()));
    }

    private void startRoute(List<BlockPos> points, LivingEntity target, boolean reachesGoal) {
        routePoints.clear();
        for (int i = 1; i < points.size(); i++) {
            BlockPos p = points.get(i);
            routePoints.add(new Vec3(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
        }
        lastRoutePoints = java.util.List.copyOf(points);
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

    /** 跟随绕路路线：走完一个路径点切下一个；停滞 3 秒丢弃路线；能打到目标即交回战斗 AI。 */
    private void tickRoute() {
        if (routePoints.isEmpty()) return;

        LivingEntity target = getCombatTarget();
        if (target == null || !target.isAlive()
                || routeTargetId == null || !routeTargetId.equals(target.getUUID())
                || tickCount > routeExpireTick) {
            clearRoute();
            return;
        }
        if (canCurrentlyAttack(target)) {
            clearRoute();
            return;
        }

        Mob mover = getVehicle() instanceof Mob mount ? mount : this;
        PathNavigation nav = mover.getNavigation();
        Vec3 waypoint = routePoints.peekFirst();
        if (waypoint == null) { clearRoute(); return; }
        double distSqr = mover.position().distanceToSqr(waypoint);

        if (distSqr < 4.0 || ++routeWaypointTicks > 80) {
            routePoints.pollFirst();
            routeWaypointTicks = 0;
            routeStallTicks = 0;
            routeLastDistSq = -1.0;
            waypoint = routePoints.peekFirst();
            if (waypoint == null) { clearRoute(); return; }
            distSqr = mover.position().distanceToSqr(waypoint);
        }

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

    /** 朝参考点方向 1.5 / 2.5 格处"马 + 骑手"站不下 → 立即下马步行。 */
    public boolean tryDismountForMountTerrain(Vec3 reference) {
        if (!(getVehicle() instanceof WarHorseEntity)) return false;
        if (reference == null) return false;

        double dx = reference.x - getX();
        double dz = reference.z - getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.5) return false;
        double nx = dx / len;
        double nz = dz / len;
        if (AiNavUtil.mountFitsAt(level(), this, getX() + nx * 1.5, getY(), getZ() + nz * 1.5)
                && AiNavUtil.mountFitsAt(level(), this, getX() + nx * 2.5, getY(), getZ() + nz * 2.5)) {
            return false;
        }
        dismountHorseForTerrain();
        return true;
    }

    /** 地形下马：下马 + 清掉骑兵残留的移动 / 路线状态 + 6 秒免重上马。 */
    private void dismountHorseForTerrain() {
        if (!(getVehicle() instanceof WarHorseEntity)) return;
        dismountHorse();
        this.getNavigation().stop();
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0;
        this.lastChargeSpeed = 0;
        this.hasImpulse = true;
        clearRoute();
        clearMarchRoute();
        lastRoutePlanTick = -10000;
        stuckAccumTicks = 0;
        lastStuckX = getX();
        lastStuckY = getY();
        lastStuckZ = getZ();
        lastTargetDistSq = -1;
        lastMarchDistSq = -1;
        terrainDismountCooldown = 120;
    }

    /** 每 10 tick 检查：马走不动且前方过不去 → 立即下马步行。 */
    private void tickTerrainDismountCheck() {
        if (!(getVehicle() instanceof WarHorseEntity horse)) return;
        if (tickCount % 10 != 0) return;

        Vec3 ref = null;
        LivingEntity target = getCombatTarget();
        if (target != null && target.isAlive()) ref = target.position();
        else if (getManualMarchTarget() != null) ref = getManualMarchTarget();
        if (ref == null) return;

        if (!horse.getNavigation().isDone() && stuckAccumTicks <= 0) return;
        tryDismountForMountTerrain(ref);
    }

    private void teleportToSafeSpot() {
        LivingEntity target = getCombatTarget();
        // 战斗中只有"能真正到达"的目标才允许传送到敌人附近：
        //   寻路不通（例如攻城时敌人在城墙上）→ 拉黑目标，改走安全脱困，
        //   不会被传送到城墙 / 墙头上。
        if (target != null && target.isAlive()
                && (!hasPathToTarget(target)
                    || Math.abs(target.getY() - getY()) > MAX_ENEMY_TELEPORT_Y_DIFF)) {
            markTargetUnreachable(target);
            setTarget(null);
            target = null;
        }
        // 锚定单位（村庄巡逻 / 袭击增援）：目标在自己活动范围内时才允许传送到敌人附近，
        //   否则回退到行军 / 原地安全点，避免脱困传送把人带离村庄 / 袭击范围。
        boolean chase = !retreating && target != null && target.isAlive()
                && CombatConfig.enableStuckTeleportNearEnemy
                && getAnchor().contains(target.position());
        Vec3 safe;
        if (chase) {
            safe = findSafeOpenSpotAround(target.getX(), target.getY(), target.getZ(), 16);
        } else {
            // 不在原地空传，也不朝行军目标方向跳（那是穿墙传送）；
            //   只在附近找一个开阔落脚点脱困（掉坑 / 掉沟时优先往上找开口）。
            safe = findEscapeSpot();
            if (safe == null) safe = findSafeOpenSpotAround(getX(), getY(), getZ(), 16);
        }
        teleportWithMount(safe.x, safe.y, safe.z);
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, safe.x, safe.y, safe.z,
                    SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 1.0f);
        }
    }

    private void teleportWithMount(double x, double y, double z) {
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
        this.lastTargetDistSq = -1;
        this.lastMarchDistSq = -1;
        // 传送会把轨迹拉断 → 清掉，士兵下一次直接重新接上首领
        marchTrail.clear();
        marching = false;
    }

    private Vec3 findSafeOpenSpotAround(double cx, double cy, double cz, int maxRadius) {
        int baseX = (int) Math.floor(cx);
        int baseY = (int) Math.floor(cy);
        int baseZ = (int) Math.floor(cz);

        for (int radius = 0; radius <= maxRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) continue;
                    int x = baseX + dx;
                    int z = baseZ + dz;
                    for (int y = baseY + 5; y >= baseY - 5; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (!isSafeOpenSpot(pos)) continue;
                        // 只能跳到"直线可见"的落点，避免脱困传送穿墙
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
        return new Vec3(cx, cy, cz);
    }

    private boolean isSafeOpenSpot(BlockPos pos) {
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

        int solid = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockState s = level().getBlockState(pos.offset(dx, 0, dz));
                if (AiNavUtil.isDangerousBlock(s)) return false;
                if (s.isSolid()) solid++;
            }
        }
        return solid < 8;
    }

    private boolean isOpenTerrain() {
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

    private void evaluateFormation() {
        LivingEntity t = getCombatTarget();
        if (t == null || !t.isAlive()) { setFormation(Formation.LINE); return; }
        double hpRatio = getHealth() / getMaxHealth();
        List<AbstractArmySoldier> soldiers = getSoldiers();
        int melee = 0, cavalry = 0;
        for (AbstractArmySoldier s : soldiers) {
            if (!s.isAlive()) continue;
            if (s.getSoldierType() == SoldierType.MELEE_CAVALRY) cavalry++;
            else if (s.getSoldierType() == SoldierType.MELEE_INFANTRY) melee++;
        }

        Formation want;
        if (hpRatio < 0.4) want = Formation.SHIELD_WALL;
        else if (cavalry >= 4) want = Formation.WEDGE;
        else if (melee >= 6) want = Formation.SHIELD_WALL;
        else want = Formation.LINE;

        if (want != formation) setFormation(want);
    }

    private void updateChargeSpeed() {
        if (this.isPassenger() && this.getVehicle() != null) {
            Vec3 m = this.getVehicle().getDeltaMovement();
            double s = Math.sqrt(m.x * m.x + m.z * m.z);
            if (s > lastChargeSpeed) lastChargeSpeed = s;
            else lastChargeSpeed *= 0.92;
            if (lastChargeSpeed < 0.05) lastChargeSpeed = 0;
        } else {
            lastChargeSpeed = 0;
        }
    }

    private void syncWeapon() {
        // 非战斗时手持长杆阵营旗（行军 / 仪仗），进入战斗再换回武器：
        //   既保留"举旗首领"的外观，又不影响弓 / 骑枪 / 近战表现
        //   （骑枪穿刺要求主手是骑枪）。
        // 玩家在单位界面里给主手放了装备 → 一直拿那件，武器形态切换与行军旗都让位。
        if (hasManualMainHand()) return;
        boolean inCombat = getTarget() != null && getTarget().isAlive();
        if (!inCombat) {
            if (bannerStack.isEmpty()) {
                bannerStack = FactionBanner.create(
                        Monster_conquest.FACTION_BANNER.get(), getFactionId());
            }
            ItemStack held = this.getMainHandItem();
            if (!held.is(Monster_conquest.FACTION_BANNER.get())
                    || !java.util.Objects.equals(
                            FactionBanner.factionOf(held), getFactionId())) {
                this.setItemSlot(EquipmentSlot.MAINHAND, bannerStack.copy());
            }
            appliedMode = null;   // 下次进入战斗时重新同步武器
            return;
        }

        if (appliedMode == weaponMode) return;
        ItemStack stack = switch (weaponMode) {
            case BOW -> bowStack;
            case LANCE -> lanceStack;
            case BLADE -> bladeStack;
        };
        if (stack.isEmpty()) return;
        this.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
        appliedMode = weaponMode;
    }

    public boolean isRetreating() { return retreating; }

    private void updateRetreatState() {
        float hpRatio = getHealth() / getMaxHealth();
        if (!retreating && hpRatio < (float) LOW_HP_THRESHOLD) retreating = true;
        else if (retreating && hpRatio > 0.5f) retreating = false;
    }

    private void tickRegen() {
        if (!CombatConfig.enableOutOfCombatRegen) return;
        if (this.getHealth() >= this.getMaxHealth()) return;
        if (this.tickCount - lastHurtTick < CombatConfig.outOfCombatRegenDelay) return;

        if (retreating) {
            if (this.tickCount % CombatConfig.outOfCombatRegenInterval == 0) {
                this.heal(Math.max(1.0f, this.getMaxHealth() * 0.05f));
            }
            return;
        }

        LivingEntity target = getTarget();
        if (target != null && target.isAlive()) return;
        if (getCombatTarget() != null) return;

        if (this.tickCount % CombatConfig.outOfCombatRegenInterval == 0) {
            this.heal(Math.max(1.0f, this.getMaxHealth() * 0.05f));
        }
    }

    // ===================== 方阵槽位 =====================
    @Nullable
    public Vec3 getFormationSlot(AbstractArmySoldier soldier) {
        if (formationCacheTick == Integer.MIN_VALUE
                || tickCount - formationCacheTick > 20) {
            rebuildFormationSlots();
            formationCacheTick = tickCount;
        }
        return formationSlots.get(soldier.getUUID());
    }

    private void rebuildFormationSlots() {
        formationSlots.clear();
        List<AbstractArmySoldier> all = new ArrayList<>(getSoldiers());
        if (all.isEmpty()) return;
        all.sort(Comparator.comparing(e -> e.getUUID().toString()));

        // 地形适配：先按当前朝向测出横向能放几列，再按列数收缩阵型。
        //   开阔地 limit = 24（不改变原有阵型）；窄口 / 走廊会降为好几列，
        //   1~2 列时直接整队收缩成单列纵队。
        int limit = terrainColumnLimit(FORMATION_CELL, MAX_FORMATION_COLS);
        if (limit <= 2) {
            layoutColumn(all);
            return;
        }

        switch (formation) {
            case LINE -> layoutLine(all, limit);
            case WEDGE -> layoutWedge(all, limit);
            case SHIELD_WALL -> layoutShieldWall(all, limit);
            case SKIRMISH -> layoutSkirmish(all, limit);
        }
    }

    /** 阵型横向最多列数（开阔地）。 */
    private static final int MAX_FORMATION_COLS = 24;
    /** 用于地形宽度探测的横向间距（与步兵列距一致）。 */
    private static final double FORMATION_CELL = 1.5;

    /**
     * 地形适配：测出当前朝向下，阵型横向能站下多少列。
     *
     * 从最大列数往下试：取该列数的最左 / 最右两列，在队伍前、中、后三排
     * 各取一个采样点，全部能站人才算这一列数可行。窄通道里会一路收缩到 1 列。
     */
    private int terrainColumnLimit(double cell, int maxCols) {
        for (int cols = maxCols; cols >= 2; cols--) {
            if (terrainColumnsFit(cols, cell)) return cols;
        }
        return 1;
    }

    private boolean terrainColumnsFit(int cols, double cell) {
        double half = (cols - 1) / 2.0;
        // 用"阵型朝向"（最近一次行进方向）而不是当下身体朝向：
        //   首领站定后东张西望会转身体，用当下朝向会让整片阵型跟着转 → 士兵反复挪窝。
        float yaw = getFormationFacing() * ((float) Math.PI / 180f);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        for (double offX : new double[]{-half * cell, half * cell}) {
            for (double offZ : new double[]{-2.0, 2.0, 6.0}) {
                double wx = getX() + offX * cos - offZ * sin;
                double wz = getZ() + offX * sin + offZ * cos;
                double wy = findGroundY(wx, getY(), wz);
                if (!isSafeFormationPos(wx, wy, wz)) return false;
            }
        }
        return true;
    }

    /**
     * 单列纵队：极窄地形（1~2 列）时整队排队通过。
     *   仍然保持"近战在前、远程在后"的兵种顺序。
     */
    private void layoutColumn(List<AbstractArmySoldier> all) {
        final double STEP = 2.0;
        // 单列纵队排在首领身后：窄道 / 迷宫由首领带头，士兵排队跟进，
        //   不会出现士兵要挤到首领前面去站位。
        double z = -2.0;
        SoldierType[] order = {
                SoldierType.MELEE_INFANTRY, SoldierType.MELEE_CAVALRY,
                SoldierType.DUAL_CAVALRY, SoldierType.DUAL_INFANTRY,
                SoldierType.THROWER_CAVALRY, SoldierType.THROWER_INFANTRY,
                SoldierType.GUNNER_CAVALRY, SoldierType.GUNNER_INFANTRY,
                SoldierType.ARCHER_CAVALRY, SoldierType.ARCHER_INFANTRY,
                SoldierType.MAGE_CAVALRY, SoldierType.MAGE_INFANTRY,
        };
        for (SoldierType type : order) {
            for (AbstractArmySoldier s : filterByType(all, type)) {
                placeSlot(s, 0, z);
                // 往首领身后排：offZ 为正 = 阵型朝向（行进方向）前方，
                //   单列纵队必须递减，否则整个队列会排到首领前面去。
                z -= STEP;
            }
        }
    }

    private void layoutLine(List<AbstractArmySoldier> all, int terrainLimit) {
        final double INF_CELL = 1.5;
        final double CAV_CELL = 2.4;
        final double INF_ROW  = 2.0;
        final double CAV_ROW  = 3.2;
        final double GAP = 6.0;
        final int MAX_PER_ROW = 24;
        final int maxPerRow = Math.max(1, Math.min(MAX_PER_ROW, terrainLimit));

        double zFront = INF_ROW;
        SoldierType[] frontOrder = { SoldierType.MELEE_INFANTRY, SoldierType.MELEE_CAVALRY };
        for (SoldierType type : frontOrder) {
            List<AbstractArmySoldier> g = filterByType(all, type);
            if (g.isEmpty()) continue;
            boolean cav = type.isCavalry();
            double cell = cav ? CAV_CELL : INF_CELL;
            double rowSpacing = cav ? CAV_ROW : INF_ROW;
            zFront = placeTypedRow(g, zFront, cell, rowSpacing, GAP, maxPerRow, +1);
        }

        double zBack = -INF_ROW;
        SoldierType[] backOrder = {
                SoldierType.DUAL_CAVALRY, SoldierType.DUAL_INFANTRY,
                SoldierType.THROWER_CAVALRY, SoldierType.THROWER_INFANTRY,
                SoldierType.GUNNER_CAVALRY, SoldierType.GUNNER_INFANTRY,
                SoldierType.ARCHER_CAVALRY, SoldierType.ARCHER_INFANTRY,
                SoldierType.MAGE_CAVALRY, SoldierType.MAGE_INFANTRY,
        };
        for (SoldierType type : backOrder) {
            List<AbstractArmySoldier> g = filterByType(all, type);
            if (g.isEmpty()) continue;
            boolean cav = type.isCavalry();
            double cell = cav ? CAV_CELL : INF_CELL;
            double rowSpacing = cav ? CAV_ROW : INF_ROW;
            zBack = placeTypedRow(g, zBack, cell, rowSpacing, GAP, maxPerRow, -1);
        }
    }

    private void layoutWedge(List<AbstractArmySoldier> all, int terrainLimit) {
        final double CELL = 1.5;
        final double ROW = 1.8;
        // 每侧最多 8 个骑兵 → 超过换到下一段楔形
        final int WEDGE_HALF_WIDTH = 8;
        // 后排每排最多 24 个 → 超过换到下一行
        final int BACK_MAX_PER_ROW = 24;

        // 地形适配：窄地形下楔形变窄（多分段）、后排列数变少
        final int backMax = Math.max(1, Math.min(BACK_MAX_PER_ROW, terrainLimit));
        final int wedgeHalf = Math.max(1,
                Math.min(WEDGE_HALF_WIDTH, Math.max(1, (terrainLimit - 1) / 2)));

        SoldierType[] order = {
                SoldierType.MELEE_CAVALRY, SoldierType.MELEE_INFANTRY,
                SoldierType.DUAL_CAVALRY, SoldierType.DUAL_INFANTRY,
                SoldierType.THROWER_CAVALRY, SoldierType.THROWER_INFANTRY,
                SoldierType.GUNNER_CAVALRY, SoldierType.GUNNER_INFANTRY,
                SoldierType.ARCHER_CAVALRY, SoldierType.ARCHER_INFANTRY,
                SoldierType.MAGE_CAVALRY, SoldierType.MAGE_INFANTRY,
        };

        double z = 2.0;
        int prevWidth = 1;

        for (SoldierType type : order) {
            List<AbstractArmySoldier> g = filterByType(all, type);
            if (g.isEmpty()) continue;

            if (type == SoldierType.MELEE_CAVALRY) {
                int segSize = wedgeHalf * 2;
                for (int i = 0; i < g.size(); i++) {
                    int seg = i / segSize;
                    int within = i % segSize;
                    int row = within / 2 + 1;
                    int side = (within % 2 == 0) ? -1 : 1;
                    double segZ = z + seg * (wedgeHalf + 2) * ROW;
                    placeSlot(g.get(i), side * row * 1.6, segZ + row * ROW);
                }
                int segs = (g.size() + segSize - 1) / segSize;
                z += segs * (wedgeHalf + 2) * ROW + ROW;
                prevWidth = Math.max(prevWidth, segSize);
            } else {
                int naturalWidth = Math.max(1, (int) Math.ceil(Math.sqrt(g.size())));
                int width = Math.max(naturalWidth, prevWidth + 1);
                if (width > backMax) width = backMax;
                prevWidth = width;

                for (int i = 0; i < g.size(); i++) {
                    int row = i / width;
                    int col = i % width;
                    placeSlot(g.get(i),
                            (col - (width - 1) / 2.0) * CELL,
                            z + row * ROW);
                }
                int rows = (g.size() + width - 1) / width;
                z += (rows + 1) * ROW;
            }
        }
    }

    private void layoutShieldWall(List<AbstractArmySoldier> all, int terrainLimit) {
        final double CELL = 1.3;
        final double ROW = 1.8;
        final int COLS = 12;
        // 两翼骑兵每侧每排最多 4 个
        final int SIDE_COLS = 4;

        // 地形适配：窄地形下盾墙变窄；太窄时两翼骑兵并到后排（不再挂到两侧）
        final int cols = Math.max(1, Math.min(COLS, terrainLimit));
        final boolean foldWings = terrainLimit < COLS;

        List<AbstractArmySoldier> meleeInf = filterByType(all, SoldierType.MELEE_INFANTRY);
        List<AbstractArmySoldier> meleeCav = filterByType(all, SoldierType.MELEE_CAVALRY);

        for (int i = 0; i < meleeInf.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            double off = (col - (cols - 1) / 2.0) * CELL;
            if (row % 2 == 1) off += CELL * 0.5;
            placeSlot(meleeInf.get(i), off, 3 + row * ROW);
        }
        int mainRows = (meleeInf.size() + cols - 1) / cols;

        SoldierType[] backTypes = {
                SoldierType.DUAL_CAVALRY, SoldierType.DUAL_INFANTRY,
                SoldierType.THROWER_CAVALRY, SoldierType.THROWER_INFANTRY,
                SoldierType.GUNNER_CAVALRY, SoldierType.GUNNER_INFANTRY,
                SoldierType.ARCHER_CAVALRY, SoldierType.ARCHER_INFANTRY,
                SoldierType.MAGE_CAVALRY, SoldierType.MAGE_INFANTRY,
        };
        double z = 3 + (mainRows + 1) * ROW;
        for (SoldierType type : backTypes) {
            List<AbstractArmySoldier> g = filterByType(all, type);
            if (g.isEmpty()) continue;
            int width = Math.max(1, Math.min(terrainLimit,
                    Math.max(4, (int) Math.ceil(Math.sqrt(g.size())))));
            for (int i = 0; i < g.size(); i++) {
                int row = i / width;
                int col = i % width;
                placeSlot(g.get(i),
                        (col - (width - 1) / 2.0) * CELL,
                        z + row * ROW);
            }
            int rows = (g.size() + width - 1) / width;
            z += (rows + 1) * ROW;
        }

        if (foldWings && !meleeCav.isEmpty()) {
            // 窄地形：两翼骑兵并到后排，按受地形限制的宽度排队
            int width = Math.max(1, Math.min(terrainLimit, 4));
            for (int i = 0; i < meleeCav.size(); i++) {
                int row = i / width;
                int col = i % width;
                placeSlot(meleeCav.get(i),
                        (col - (width - 1) / 2.0) * CELL,
                        z + row * ROW);
            }
            return;
        }

        int half = (meleeCav.size() + 1) / 2;
        for (int i = 0; i < meleeCav.size(); i++) {
            boolean left = i < half;
            int idx = left ? i : i - half;
            int row = idx / SIDE_COLS;
            int col = idx % SIDE_COLS;
            placeSlot(meleeCav.get(i),
                    (left ? -1 : 1) * (9 + col * 1.5),
                    2 + row * ROW);
        }
    }

    private void layoutSkirmish(List<AbstractArmySoldier> all, int terrainLimit) {
        SoldierType[] rangedTypes = {
                SoldierType.DUAL_CAVALRY, SoldierType.DUAL_INFANTRY,
                SoldierType.THROWER_CAVALRY, SoldierType.THROWER_INFANTRY,
                SoldierType.GUNNER_CAVALRY, SoldierType.GUNNER_INFANTRY,
                SoldierType.ARCHER_CAVALRY, SoldierType.ARCHER_INFANTRY,
                SoldierType.MAGE_CAVALRY, SoldierType.MAGE_INFANTRY,
        };

        // 散兵线：每环一个兵种，都按多行网格换行
        final int RING_COLS = 10;
        final double RING_CELL = 1.8;
        final double RING_ROW_GAP = 2.4;
        final int ringCols = Math.max(1, Math.min(RING_COLS, terrainLimit));

        int ringIdx = 0;
        for (SoldierType type : rangedTypes) {
            List<AbstractArmySoldier> g = filterByType(all, type);
            if (g.isEmpty()) { ringIdx++; continue; }

            double baseZ = 2.0 + ringIdx * 4.0;
            for (int i = 0; i < g.size(); i++) {
                int row = i / ringCols;
                int col = i % ringCols;
                double x = (col - (ringCols - 1) / 2.0) * RING_CELL;
                double z = baseZ + row * RING_ROW_GAP;
                placeSlot(g.get(i), x, z);
            }
            int rows = (g.size() + ringCols - 1) / ringCols;
            ringIdx++;
            // 预留空间给下一个环
            ringIdx += Math.max(0, rows - 1);
        }

        List<AbstractArmySoldier> meleeInf = filterByType(all, SoldierType.MELEE_INFANTRY);
        List<AbstractArmySoldier> meleeCav = filterByType(all, SoldierType.MELEE_CAVALRY);
        // 地形适配：窄地形把两翼骑兵并进近战方阵，宽度也跟着收窄
        final boolean foldWings = terrainLimit < RING_COLS;
        int meleeTotal = meleeInf.size() + (foldWings ? meleeCav.size() : 0);
        int mWidth = Math.max(1, Math.min(terrainLimit,
                Math.max(4, (int) Math.ceil(Math.sqrt(Math.max(1, meleeTotal))))));
        for (int i = 0; i < meleeTotal; i++) {
            AbstractArmySoldier s = i < meleeInf.size()
                    ? meleeInf.get(i) : meleeCav.get(i - meleeInf.size());
            int row = i / mWidth;
            int col = i % mWidth;
            placeSlot(s,
                    (col - (mWidth - 1) / 2.0) * 1.5,
                    2 + (4 + row) * 1.8);
        }

        if (foldWings) return;

        // 两翼骑兵每侧每排最多 4 个
        final int SKIRMISH_SIDE_COLS = 4;
        int half = (meleeCav.size() + 1) / 2;
        for (int i = 0; i < meleeCav.size(); i++) {
            boolean left = i < half;
            int idx = left ? i : i - half;
            int row = idx / SKIRMISH_SIDE_COLS;
            int col = idx % SKIRMISH_SIDE_COLS;
            placeSlot(meleeCav.get(i),
                    (left ? -1 : 1) * (10 + col * 1.5),
                    2 + row * 1.8);
        }
    }

    private static List<AbstractArmySoldier> filterByType(
            List<AbstractArmySoldier> all, SoldierType type) {
        List<AbstractArmySoldier> result = new ArrayList<>();
        for (AbstractArmySoldier s : all) {
            if (s.getSoldierType() == type) result.add(s);
        }
        return result;
    }

    private double placeTypedRow(List<AbstractArmySoldier> group, double startZ,
                                 double cell, double rowSpacing, double gap,
                                 int maxPerRow, int direction) {
        int idx = 0;
        int rowIdx = 0;
        double lastZ = startZ;

        while (idx < group.size()) {
            int n = Math.min(group.size() - idx, maxPerRow);
            double z = startZ + direction * rowIdx * rowSpacing;
            lastZ = z;

            int leftCount = (n + 1) / 2;
            int rightCount = n - leftCount;

            for (int i = 0; i < leftCount; i++) {
                double x = -gap / 2.0 - (leftCount - 1 - i) * cell;
                placeSlot(group.get(idx + i), x, z);
            }
            for (int i = 0; i < rightCount; i++) {
                double x = gap / 2.0 + i * cell;
                placeSlot(group.get(idx + leftCount + i), x, z);
            }
            idx += n;
            rowIdx++;
        }

        return lastZ + direction * rowSpacing;
    }

    private void placeSlot(AbstractArmySoldier s, double offX, double offZ) {
        // 同 terrainColumnsFit：站位按"阵型朝向"摆放，首领站定后转身不会带动整片阵型漂移
        float yaw = getFormationFacing() * ((float) Math.PI / 180f);
        double cos = Math.cos(yaw);
        double sin = Math.sin(yaw);
        double rx = offX * cos - offZ * sin;
        double rz = offX * sin + offZ * cos;
        double wx = getX() + rx;
        double wz = getZ() + rz;

        double wy = findGroundY(wx, getY(), wz);

        if (!isSafeFormationPos(wx, wy, wz)) {
            double cx = getX();
            double cz = getZ();
            double cy = findGroundY(cx, getY(), cz);
            boolean found = false;
            for (int step = 1; step <= 8; step++) {
                double t = step / 8.0;
                double tx = wx + (cx - wx) * t;
                double tz = wz + (cz - wz) * t;
                double ty = findGroundY(tx, getY(), tz);
                if (isSafeFormationPos(tx, ty, tz)) {
                    wx = tx;
                    wz = tz;
                    wy = ty;
                    found = true;
                    break;
                }
            }
            if (!found) { wx = cx; wz = cz; wy = cy; }
        }

        formationSlots.put(s.getUUID(), new Vec3(wx, wy, wz));
    }

    private double findGroundY(double x, double baseY, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(baseY);
        int bz = (int) Math.floor(z);
        for (int y = by + 2; y >= by - 5; y--) {
            BlockPos p = new BlockPos(bx, y, bz);
            BlockPos below = p.below();
            BlockState here = level().getBlockState(p);
            BlockState under = level().getBlockState(below);
            if (here.isAir() && under.isSolid()) {
                return y;
            }
        }
        return baseY;
    }

    private boolean isSafeFormationPos(double x, double y, double z) {
        // 站得住 + 至少两个方向通气：排除墙里 / 封闭空穴 / 只能卡一格的死角
        return AiNavUtil.isOpenStandingSpot(level(), x, y, z);
    }

    // ===================== 首领装备 =====================

    /**
     * 首领默认装备等级（子类可覆盖）。
     *   想让某个首领用低等级装备 / 自己改数值时覆盖它。
     */
    public EquipmentTier getCommanderTier() { return EquipmentTier.TIER_4; }

    private void equipCommander() {
        EquipmentTier tier = getCommanderTier();
        this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(tier.getHelmet()));
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(tier.getChestplate()));
        this.setItemSlot(EquipmentSlot.LEGS, new ItemStack(tier.getLeggings()));
        this.setItemSlot(EquipmentSlot.FEET, new ItemStack(tier.getBoots()));
        // 盾牌带阵营旗图案（底色 + 十字，与身上渲染的阵营旗一致）
        this.setItemSlot(EquipmentSlot.OFFHAND, FactionShield.create(level(), getFactionId()));

        for (EquipmentSlot slot : EquipmentSlot.values()) this.setDropChance(slot, 0.0f);

        if (!(level() instanceof ServerLevel sl)) {
            refreshDefaultGear();
            return;
        }

        // 先把武器栈准备出来（不依赖附魔注册表）
        bowStack = new ItemStack(Items.BOW);
        lanceStack = new ItemStack(Monster_conquest.NETHERITE_LANCE.get());
        bladeStack = new ItemStack(Monster_conquest.COMMANDER_BLADE.get());
        bannerStack = FactionBanner.create(Monster_conquest.FACTION_BANNER.get(), getFactionId());
        this.setItemSlot(EquipmentSlot.MAINHAND, bladeStack.copy());
        this.appliedMode = WeaponMode.BLADE;

        // 用 try-catch 包裹整个附魔流程。
        //   原因：原代码直接 getOrThrow(Enchantments.POWER) —— 如果某个模组
        //   （或未来 MC 版本）改变了 ENCHANTMENT 注册表结构，会抛
        //   IllegalStateException 让首领生成失败。
        //   失败时跳过附魔，装备本身仍然正确。
        try {
            var enchants = sl.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            var rand = this.getRandom();

            enchantIfPresent(bowStack, enchants, Enchantments.POWER, 5);
            enchantIfPresent(bowStack, enchants, Enchantments.FLAME, 1);
            enchantIfPresent(bowStack, enchants, Enchantments.PUNCH, 2);

            enchantIfPresent(lanceStack, enchants, Enchantments.SHARPNESS, 5);

            enchantIfPresent(bladeStack, enchants, Enchantments.SHARPNESS, 5);
            if (rand.nextBoolean()) enchantIfPresent(bladeStack, enchants, Enchantments.FIRE_ASPECT, 2);
            enchantIfPresent(bladeStack, enchants, Enchantments.KNOCKBACK, 2);

            this.setItemSlot(EquipmentSlot.MAINHAND, bladeStack.copy());

            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                    EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                enchantIfPresent(this.getItemBySlot(slot), enchants, Enchantments.PROTECTION, 4);
            }
            enchantIfPresent(this.getItemBySlot(EquipmentSlot.FEET),
                    enchants, Enchantments.FEATHER_FALLING, 4);
        } catch (Throwable t) {
            Monster_conquest.LOGGER.warn(
                    "[MonsterConquest] Commander enchant skipped: {}", t.toString());
        }
        // 记录自带装备快照，并把玩家手动装备盖回身上
        refreshDefaultGear();
    }

    /**
     * 用 Optional 取附魔 Holder，缺失时跳过而不是抛异常。
     *   等价于 getOrThrow 的软失败版本。
     */
    private static void enchantIfPresent(ItemStack stack,
                                         HolderLookup.RegistryLookup<Enchantment> enchants,
                                         net.minecraft.resources.ResourceKey<Enchantment> key,
                                         int level) {
        if (stack.isEmpty()) return;
        var opt = enchants.get(key);
        opt.ifPresent(h -> stack.enchant(h, level));
    }

    // ===================== 手动装备 / 战术装备（单位界面） =====================
    // 与士兵一致：界面里只显示玩家手动放进去的那一份，自带装备不显示、默认栏位为空；
    //   放入即覆盖身上自带的那件，取出立刻还原成自带装备。

    private final ItemStack[] manualGear = emptyStacks(GEAR_SLOTS);
    private final ItemStack[] defaultGear = emptyStacks(GEAR_SLOTS);
    private final ItemStack[] manualTactical = emptyStacks(TACTICAL_SLOTS);

    @Override
    public LivingEntity asEntity() { return this; }

    /** 手动装备栏下标 → 实体装备槽。 */
    private static EquipmentSlot gearSlot(int index) {
        return ManagedUnit.gearSlot(index);
    }

    @Override
    public ItemStack getManualGear(int index) { return manualGear[index]; }

    /**
     * 写入手动装备：非空覆盖自带装备，空则立刻还原自带装备。
     *   还原一律以"自带装备快照"为准：快照为空就是空手，不再"保留身上那件"。
     */
    @Override
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
            // 自带装备本来就是空的：取下手动装备 = 空手
            want = ItemStack.EMPTY;
        }
        this.setItemSlot(gearSlot(index), want.copy());
        refreshManualGearMask();
    }

    /**
     * 手动装备"穿在身上"的那一份：界面里的那份保持原样，
     *   自定义装备自带附魔就用它自己的，不带就继承这个部位自带装备的附魔。
     */
    private ItemStack wornManualStack(int index) {
        ItemStack worn = manualGear[index].copy();
        if (worn.isEmpty()) return worn;
        if (worn.isEnchanted()) return worn;
        ItemStack def = defaultGear[index];
        if (def.isEmpty()) return worn;
        if (!def.getEnchantments().isEmpty()) {
            worn.set(net.minecraft.core.component.DataComponents.ENCHANTMENTS,
                    def.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS));
        }
        return worn;
    }

    @Override
    public boolean hasManualGear(int index) {
        return (this.entityData.get(DATA_MANUAL_GEAR) & (1 << index)) != 0;
    }

    /** 主手是否被玩家手动指定：指定后武器形态切换 / 行军旗让位，一直拿着玩家放的那件。 */
    public boolean hasManualMainHand() { return !manualGear[4].isEmpty(); }

    private void refreshManualGearMask() {
        int mask = 0;
        for (int i = 0; i < GEAR_SLOTS; i++) {
            if (!manualGear[i].isEmpty()) mask |= 1 << i;
        }
        this.entityData.set(DATA_MANUAL_GEAR, (byte) mask);
    }

    @Override
    public void ensureDefaultGear() {
        for (int i = 0; i < GEAR_SLOTS; i++) {
            if (defaultGear[i].isEmpty() && manualGear[i].isEmpty()) {
                defaultGear[i] = this.getItemBySlot(gearSlot(i)).copy();
            }
        }
    }

    @Override
    public ItemStack getManualTactical(int index) { return manualTactical[index]; }

    @Override
    public void setManualTactical(int index, ItemStack stack) {
        manualTactical[index] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    @Override
    public boolean hasTactical(Predicate<ItemStack> test) {
        for (ItemStack stack : manualTactical) if (test.test(stack)) return true;
        return false;
    }

    @Override
    public int findTactical(Predicate<ItemStack> test) {
        for (int i = 0; i < TACTICAL_SLOTS; i++) if (test.test(manualTactical[i])) return i;
        return -1;
    }

    @Override
    public void clearTactical(int index) {
        if (index >= 0 && index < TACTICAL_SLOTS) manualTactical[index] = ItemStack.EMPTY;
    }

    @Override
    public void clearManualEquipment() {
        ensureDefaultGear();
        for (int i = 0; i < GEAR_SLOTS; i++) setManualGear(i, ItemStack.EMPTY);
        for (int i = 0; i < TACTICAL_SLOTS; i++) manualTactical[i] = ItemStack.EMPTY;
        refreshManualGearMask();
    }

    /**
     * 记录自带装备快照，并把玩家手动装备重新盖回去。
     *   调用时机：身上 6 个槽位里放的必须是"这次刚写好的自带装备"
     *   （equipCommander 写完全部默认装备与附魔之后），这样快照才能带上附魔。
     */
    public void refreshDefaultGear() {
        for (int i = 0; i < GEAR_SLOTS; i++) {
            EquipmentSlot slot = gearSlot(i);
            defaultGear[i] = this.getItemBySlot(slot).copy();
            if (!manualGear[i].isEmpty()) this.setItemSlot(slot, wornManualStack(i));
        }
        refreshManualGearMask();
    }

    /** 把手动装备 / 战术装备写进标签（做蛋时用；自带装备不写）。 */
    public void writeGearTag(CompoundTag tag) {
        tag.put("ManualGear", stackListTag(manualGear));
        tag.put("ManualTactical", stackListTag(manualTactical));
    }

    /** 从标签恢复手动装备；后续 equipCommander 会刷新自带装备快照并把手动装备盖回身上。 */
    public void readGearTag(CompoundTag tag) {
        readStackList(tag.getList("ManualGear", Tag.TAG_COMPOUND), manualGear);
        readStackList(tag.getList("ManualTactical", Tag.TAG_COMPOUND), manualTactical);
        refreshManualGearMask();
    }

    private static ItemStack[] emptyStacks(int count) {
        ItemStack[] arr = new ItemStack[count];
        for (int i = 0; i < count; i++) arr[i] = ItemStack.EMPTY;
        return arr;
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

    // ===================== 招募 =====================
    protected void tryRecruit() {
        int radius = GeneralConfig.recruitRadius;
        int maxSize = GeneralConfig.maxArmySize;
        int slots = maxSize - getSoldiers().size();
        if (slots <= 0) return;

        List<Monster> candidates = level().getEntitiesOfClass(Monster.class,
                getBoundingBox().inflate(radius), this::isConvertible);
        if (candidates.isEmpty()) return;

        int toConvert = Math.min(Math.min(slots, candidates.size()), GeneralConfig.recruitPerScan);
        for (int i = 0; i < toConvert; i++) convert(candidates.get(i));
    }

    /** 可收编孤儿士兵的编制上限（巡逻队长覆盖为固定 3 人）。 */
    protected int recruitStrayCapacity() {
        return GeneralConfig.maxArmySize;
    }

    protected void tryRecruitStrays() {
        int cap = Math.min(GeneralConfig.maxArmySize, Math.max(0, recruitStrayCapacity()));
        if (getSoldiers().size() >= cap) return;
        if (!(level() instanceof ServerLevel sl)) return;

        List<AbstractArmySoldier> strays = level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                getBoundingBox().inflate(GeneralConfig.recruitRadius),
                s -> s.isAlive() && !s.isRemoved() && isRecruitableStray(s));

        if (strays.isEmpty()) return;

        int slots = cap - getSoldiers().size();
        int count = Math.min(strays.size(), slots);
        for (int i = 0; i < count; i++) {
            AbstractArmySoldier s = strays.get(i);

            UUID oldCmd = s.getCommanderUuid().orElse(null);
            if (oldCmd != null && !oldCmd.equals(this.getUUID())) {
                ArmyRelations.clear(oldCmd);
            }
            LivingEntity t = s.getTarget();
            if (t instanceof AbstractArmySoldier || t instanceof AbstractArmyCommander
                    || t instanceof WarHorseEntity) {
                s.setTarget(null);
            }

            // 收编后的落点同样走安全检查（避开房顶 / 沟底 / 危险方块 / 墙里）
            Vec3 dest = ArmySpawnUtil.findSafeSpawnNear(
                    sl, this.position(), 3.0, 7.0, 2, this.getRandom());
            if (dest != null) {
                s.teleportWithMount(dest.x, dest.y, dest.z, true);
            } else {
                double angle = this.getRandom().nextDouble() * Math.PI * 2;
                double dist = 3 + this.getRandom().nextDouble() * 4;
                s.teleportWithMount(this.getX() + Math.cos(angle) * dist, this.getY(),
                        this.getZ() + Math.sin(angle) * dist, true);
            }

            s.setCommanderUuid(this.getUUID());
            s.setFactionId(this.getFactionId());
            inheritAnchor(s);
            s.setWild(this.wild);                 // 野外巡逻队收编的孤儿士兵同样会在远离玩家后消失
            for (LivingEntity candidate : getHatredList()) {
                if (candidate.isAlive() && s.canAttack(candidate)) {
                    s.setTarget(candidate);
                    break;
                }
            }
        }
        if (count > 0) soldierCacheTick = -1;
    }

    /** 把首领的村庄 / 袭击锚点传给新加入的士兵（普通首领无锚点，不处理）。 */
    private void inheritAnchor(AbstractArmySoldier soldier) {
        if (!anchor.isBound()) return;
        soldier.getAnchor().bind(anchor.getType(), anchor.getPos());
    }

    private boolean isRecruitableStray(AbstractArmySoldier s) {
        UUID id = s.getCommanderUuid().orElse(null);
        if (id == null) return sameFaction(s);
        if (id.equals(this.getUUID())) return false;
        if (!(level() instanceof ServerLevel sl)) return false;

        Entity old = sl.getEntities().get(id);
        if (old instanceof AbstractArmyCommander c && c.isAlive()) return false;

        var p = sl.getServer().getPlayerList().getPlayer(id);
        if (p != null) return false;

        var profileCache = sl.getServer().getProfileCache();
        if (profileCache != null && profileCache.get(id).isPresent()) return false;

        return sameFaction(s);
    }

    private boolean sameFaction(AbstractArmySoldier s) {
        ResourceLocation a = s.getFactionId();
        ResourceLocation b = this.getFactionId();
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    protected void trySummonIfFew() {
        ResourceLocation faction = getFactionId();
        if (!FactionConfig.isFactionSummonEnabled(faction)) return;

        int min = FactionConfig.getFactionMinSoldier(faction);
        if (min <= 0) return;

        if (summonTimer < 0) {
            summonTimer = FactionConfig.summonInterval + FactionConfig.summonLoadGraceTicks;
            return;
        }

        if (summonTimer > 0) { summonTimer--; return; }

        if (!areSoldierChunksLoaded()) {
            summonTimer = FactionConfig.summonInterval;
            return;
        }

        if (getSoldiers().size() >= min) {
            summonTimer = FactionConfig.summonInterval;
            return;
        }

        summonTimer = FactionConfig.summonInterval;
        summonRandomSoldier();
    }

    private boolean areSoldierChunksLoaded() {
        if (!(level() instanceof ServerLevel sl)) return false;
        int wanted = ((int) GeneralConfig.armyFollowRange >> 4) + 1;
        int simDist = sl.getServer().getPlayerList().getSimulationDistance();
        int chunkRadius = Math.min(wanted, simDist);
        int cx = this.getBlockX() >> 4;
        int cz = this.getBlockZ() >> 4;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                if (!sl.hasChunk(cx + dx, cz + dz)) return false;
            }
        }
        return true;
    }

    private static SummonCategory getSummonCategory(AbstractArmySoldier s) {
        SoldierType t = s.getSoldierType();
        if (t.isMage()) return SummonCategory.MAGE;
        if (t.isThrower()) return SummonCategory.WITCH;
        if (t.isDual()) return SummonCategory.DUAL;
        if (t.isArcher() || t.isGunner()) return SummonCategory.RANGED;
        return SummonCategory.MELEE;
    }

    protected void summonRandomSoldier() {
        if (!(level() instanceof ServerLevel sl)) return;

        Faction faction = FactionRegistry.get(getFactionId());
        if (faction == null) return;
        var soldierTypes = faction.getSoldierTypes();
        if (soldierTypes.isEmpty()) return;

        List<EntityType<? extends AbstractArmySoldier>> meleePool = new ArrayList<>();
        List<EntityType<? extends AbstractArmySoldier>> rangedPool = new ArrayList<>();
        List<EntityType<? extends AbstractArmySoldier>> dualPool = new ArrayList<>();
        List<EntityType<? extends AbstractArmySoldier>> witchPool = new ArrayList<>();
        List<EntityType<? extends AbstractArmySoldier>> magePool = new ArrayList<>();

        for (EntityType<? extends AbstractArmySoldier> type : soldierTypes) {
            Entity mob = type.create(sl);
            if (!(mob instanceof AbstractArmySoldier soldier)) continue;
            SoldierType st = soldier.getSoldierType();
            if (st.isMage()) magePool.add(type);
            else if (st.isThrower()) witchPool.add(type);
            else if (st.isDual()) dualPool.add(type);
            else if (st.isArcher() || st.isGunner()) rangedPool.add(type);
            else meleePool.add(type);
            mob.discard();
        }

        Map<SummonCategory, List<EntityType<? extends AbstractArmySoldier>>> pools = new LinkedHashMap<>();
        Map<SummonCategory, Integer> counts = new LinkedHashMap<>();

        if (!meleePool.isEmpty()) { pools.put(SummonCategory.MELEE, meleePool); counts.put(SummonCategory.MELEE, 0); }
        if (!rangedPool.isEmpty()) { pools.put(SummonCategory.RANGED, rangedPool); counts.put(SummonCategory.RANGED, 0); }
        if (!dualPool.isEmpty()) { pools.put(SummonCategory.DUAL, dualPool); counts.put(SummonCategory.DUAL, 0); }
        if (!witchPool.isEmpty()) { pools.put(SummonCategory.WITCH, witchPool); counts.put(SummonCategory.WITCH, 0); }
        if (!magePool.isEmpty()) { pools.put(SummonCategory.MAGE, magePool); counts.put(SummonCategory.MAGE, 0); }
        if (pools.isEmpty()) return;

        for (AbstractArmySoldier s : getSoldiers()) {
            if (!s.isAlive() || s.isRemoved()) continue;
            SummonCategory cat = getSummonCategory(s);
            Integer cur = counts.get(cat);
            if (cur != null) counts.put(cat, cur + 1);
        }

        int minCount = Integer.MAX_VALUE;
        for (int v : counts.values()) if (v < minCount) minCount = v;

        List<SummonCategory> candidates = new ArrayList<>();
        for (var e : counts.entrySet()) {
            if (e.getValue() == minCount) candidates.add(e.getKey());
        }
        SummonCategory chosen = candidates.get(this.getRandom().nextInt(candidates.size()));

        List<EntityType<? extends AbstractArmySoldier>> pool = pools.get(chosen);
        if (pool == null || pool.isEmpty()) return;

        EntityType<? extends AbstractArmySoldier> pickedType =
                pool.get(this.getRandom().nextInt(pool.size()));
        Entity newMob = pickedType.create(sl);
        if (!(newMob instanceof AbstractArmySoldier soldier)) return;

        // 生成位置检查：在首领附近找一个安全可站立的位置
        //   （同高度优先，避开房顶 / 沟底 / 危险方块 / 墙里），找不到就用首领脚下兜底。
        Vec3 spawnPos = ArmySpawnUtil.findSafeSpawnNear(
                sl, this.position(), 2.0, 6.0, 2, this.getRandom());
        if (spawnPos == null) spawnPos = this.position();
        soldier.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, this.getYRot(), 0);
        soldier.setCommanderUuid(this.getUUID());
        soldier.setFactionId(this.getFactionId());
        inheritAnchor(soldier);
        soldier.setWild(this.wild);               // 野外巡逻队的随行士兵随队长一起消失

        SoldierType baseType = soldier.getDefaultSoldierType();
        boolean wantCavalry = this.getRandom().nextDouble() < GeneralConfig.cavalryConversionChance;
        if (wantCavalry) {
            soldier.setSoldierType(baseType.toggleMounted());
        } else {
            soldier.setSoldierType(baseType);
        }

        EquipmentTier tier = rollRecruitTier();
        soldier.setTier(tier);
        soldier.applyEquipment(tier);
        soldier.setHealth(soldier.getMaxHealth());

        sl.addFreshEntity(soldier);
        if (soldier.getSoldierType().isCavalry()) soldier.switchToCavalry();
        soldierCacheTick = -1;
    }

    private boolean isConvertible(Monster monster) {
        // 单位不继承 Monster：这份扫描天然只包含原版怪物（不会扫到自己人）。
        ResourceLocation key = EntityType.getKey(monster.getType());
        if (key == null) return false;
        return FactionConfig.getConversionTarget(getFactionId(), key.toString()) != null;
    }

    /** 转化目标支持多个候选（用 | 分隔）：随机取一个；只有一个时原样返回。 */
    @Nullable
    private String pickConversionTarget(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return null;
        if (raw.indexOf('|') < 0) return raw;

        List<String> valid = new ArrayList<>();
        for (String part : raw.split("\\|")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) valid.add(trimmed);
        }
        if (valid.isEmpty()) return null;
        return valid.get(this.getRandom().nextInt(valid.size()));
    }

    protected EquipmentTier rollRecruitTier() {
        // 袭击增援：更高级士兵的概率大幅提高（默认 85% 高于 T0，T3 / T4 权重也更高）
        if (anchor.isRaid()) {
            if (this.getRandom().nextDouble() >= FactionConfig.illagerRaidHighTierChance) {
                return EquipmentTier.TIER_0;
            }
            int r = this.getRandom().nextInt(100);
            if (r < 20) return EquipmentTier.TIER_1;
            if (r < 40) return EquipmentTier.TIER_2;
            if (r < 70) return EquipmentTier.TIER_3;
            return EquipmentTier.TIER_4;
        }

        if (this.getRandom().nextDouble() >= GeneralConfig.bonusTierChance) return EquipmentTier.TIER_0;
        int r = this.getRandom().nextInt(100);
        if (r < 50) return EquipmentTier.TIER_1;
        if (r < 75) return EquipmentTier.TIER_2;
        if (r < 92) return EquipmentTier.TIER_3;
        return EquipmentTier.TIER_4;
    }

    private void convert(Monster original) {
        if (!(level() instanceof ServerLevel sl)) return;

        ResourceLocation srcKey = EntityType.getKey(original.getType());
        if (srcKey == null) { original.discard(); return; }

        String soldierId = FactionConfig.getConversionTarget(getFactionId(), srcKey.toString());
        if (soldierId == null) { original.discard(); return; }

        // 目标支持多个候选（用 | 分隔）：随机取一个（例如掠夺者随机变成四种枪手之一）
        String chosen = pickConversionTarget(soldierId);
        if (chosen == null) { original.discard(); return; }

        ResourceLocation soldierRl = ResourceLocation.tryParse(chosen);
        if (soldierRl == null) { original.discard(); return; }

        var optType = BuiltInRegistries.ENTITY_TYPE.getOptional(soldierRl);
        if (optType.isEmpty()) { original.discard(); return; }

        Entity newEntity = optType.get().create(sl);
        if (!(newEntity instanceof AbstractArmySoldier soldier)) { original.discard(); return; }

        double angle = this.getRandom().nextDouble() * Math.PI * 2;
        double dist = 3 + this.getRandom().nextDouble() * 4;
        double nx = this.getX() + Math.cos(angle) * dist;
        double nz = this.getZ() + Math.sin(angle) * dist;
        soldier.moveTo(nx, this.getY(), nz, original.getYRot(), 0);
        soldier.setCommanderUuid(this.getUUID());
        soldier.setFactionId(this.getFactionId());
        inheritAnchor(soldier);

        EquipmentTier startTier = rollRecruitTier();
        SoldierType type = decideSoldierType(original, soldier);

        soldier.setSoldierType(type);
        soldier.setTier(startTier);
        soldier.applyEquipment(startTier);
        soldier.setHealth(soldier.getMaxHealth());

        sl.addFreshEntity(soldier);
        if (type.isCavalry()) soldier.switchToCavalry();
        soldierCacheTick = -1;
        original.discard();
    }

    private SoldierType decideSoldierType(Monster original, AbstractArmySoldier soldier) {
        SoldierType base = soldier.getDefaultSoldierType();
        boolean cavalry = this.getRandom().nextDouble() < GeneralConfig.cavalryConversionChance;
        return cavalry ? base.toggleMounted() : base;
    }

    // ===================== 坐骑 =====================
    private void controlMount() {
        LivingEntity threat = getCombatTarget();

        if (!(getVehicle() instanceof WarHorseEntity horse)) {
            boolean shouldMount = false;
            // 站在梯子 / 脚手架 / 藤蔓上时不自动上马（坐骑没法攀爬，也会卡在梯子上）
            if (terrainDismountCooldown <= 0 && !AiNavUtil.isOnClimbable(this)) {
                if (threat != null && threat.isAlive()) shouldMount = true;
                else if (tickCount % 40 == 0 && isOpenTerrainCached()) shouldMount = true;
            }
            if (shouldMount) mountCommanderHorse();
            return;
        }

        if (horse.isPassingThrough()) return;

        if (threat == null || !threat.isAlive()) {
            horse.setCharging(false);
            // 崎岖地形要连续确认几次才下马：边缘地形上反复"下马 → 上马"
            //   表现为原地抽搐，整队也跟着走走停停。
            if (tickCount % 40 == 0) {
                if (retreating || isOpenTerrain()) {
                    roughTerrainChecks = 0;
                } else if (++roughTerrainChecks >= ROUGH_TERRAIN_DISMOUNT_CHECKS) {
                    roughTerrainChecks = 0;
                    dismountHorseForTerrain();
                }
            }
            return;
        }

        if (retreating) { horse.setCharging(false); return; }

        Vec3 toTarget = threat.position().subtract(horse.position());
        double hLen = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);

        double dirX, dirZ;
        if (hLen > 0.1) { dirX = toTarget.x / hLen; dirZ = toTarget.z / hLen; }
        else {
            float yaw = horse.getYRot() * ((float) Math.PI / 180f);
            dirX = -Math.sin(yaw);
            dirZ = Math.cos(yaw);
        }

        // 与近战士兵完全一致：靠近到 2.5 格 → 触发穿越（冲过敌人身体），
        //   否则骑马近战时会和敌人重叠在一起，穿不过去。
        // 冲锋前先确认冲锋线上没有被方块挡住（贴脸也一样）：
        //   被墙 / 地形挡住 → 放弃冲锋，改走寻路绕路（绕墙、穿门）。
        if (!AiNavUtil.hasClearChargeLine(level(), horse, threat)) {
            horse.setCharging(false);
            // 冲锋线被挡 → 走完整绕路规划（绕墙 / 穿门），而不是直接冲向墙
            replanRouteTo(threat);
            horse.getLookControl().setLookAt(threat, 30.0f, 30.0f);
            return;
        }

        if (hLen < 2.5) {
            horse.startPassThrough(new Vec3(dirX, 0, dirZ));
            horse.setCharging(true);
            double charge = Math.max(getCommanderChargeSpeed(), 0.6);
            Vec3 dm = horse.getDeltaMovement();
            horse.setDeltaMovement(dirX * charge, dm.y, dirZ * charge);
            horse.hasImpulse = true;
            horse.getLookControl().setLookAt(threat, 30.0f, 30.0f);
            return;
        }

        double chargeSpeed = getCommanderChargeSpeed();
        Vec3 current = horse.getDeltaMovement();
        horse.setDeltaMovement(dirX * chargeSpeed, current.y, dirZ * chargeSpeed);
        horse.hasImpulse = true;
        horse.setCharging(true);

        float desiredYaw = (float) Math.toDegrees(Math.atan2(-dirX, dirZ));
        applyLimitedYaw(horse, desiredYaw, CAVALRY_MAX_TURN_PER_TICK);

        horse.getLookControl().setLookAt(threat, 30.0f, 30.0f);
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

    private boolean isOpenTerrainCached() {
        if (tickCount - openTerrainCacheTick > OPEN_TERRAIN_CACHE_TICKS) {
            cachedOpenTerrain = isOpenTerrain();
            openTerrainCacheTick = tickCount;
        }
        return cachedOpenTerrain;
    }

    public void spawnAndMountHorse() { mountCommanderHorse(); }

    public void mountCommanderHorse() {
        if (!this.isAlive()) return;
        if (!(level() instanceof ServerLevel sl)) return;
        if (this.isPassenger()) return;
        // 马（含骑手）在当前位置站不下 / 周围活动不开 → 不生成马
        double mountY = AiNavUtil.resolveMountY(level(), this, getX(), getY(), getZ());
        if (Double.isNaN(mountY)) return;

        WarHorseEntity horse = ModEntities.WAR_HORSE.get().create(sl);
        if (horse == null) return;
        horse.moveTo(getX(), mountY, getZ(), getYRot(), 0);
        horse.setOwner(this);
        horse.getInventory().setItem(0, new ItemStack(Items.SADDLE));
        // 马铠
        horse.setItemSlot(EquipmentSlot.BODY, new ItemStack(getCommanderHorseArmor()));

        // 马移动速度
        var spd = horse.getAttribute(Attributes.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(getCommanderHorseSpeed());

        // 马生命值
        double horseMaxHp = getCommanderHorseMaxHealth();
        var hp = horse.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(horseMaxHp);
            horse.setHealth((float) horseMaxHp);
        }

        // 马铠之外的额外护甲
        var horseArmor = horse.getAttribute(Attributes.ARMOR);
        if (horseArmor != null) {
            horseArmor.setBaseValue(getCommanderHorseArmorBonus());
        }

        sl.addFreshEntity(horse);
        startRiding(horse, true);
        // 形态变了（步行 → 骑乘）：旧的绕路路线按步行净空算的，作废让上层重新规划
        clearMarchRoute();
    }

    // ===================== 近战 =====================
    /** 骑枪穿刺：与近战士兵共用同一份逻辑（LancePierce），
     *   骑马冲锋时冲过敌人身体并刺伤前方扇形内的目标，不再和敌人重叠。 */
    private void tickLancePierce() {
        LancePierce.tick(this, getCombatTarget(), lastChargeSpeed, tickCount, lanceHitCd, this::canAttack);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // 近战高度差限制：站在墙头 / 高台上的目标砍不到（骑枪允许略大一点的高度差）
        if (target instanceof LivingEntity livingTarget) {
            double maxDy = this.getMainHandItem().getItem() instanceof LanceItem ? 2.0 : 1.5;
            if (Math.abs(this.getY() - livingTarget.getY()) > maxDy) return false;
        }

        // 不穿墙：近战 / 骑枪都要求身体中心之间没有方块遮挡
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
                    double dx = target.getX() - this.getX();
                    double dz = target.getZ() - this.getZ();
                    double len = Math.max(0.001, Math.sqrt(dx * dx + dz * dz));
                    living.knockback(0.8, -dx / len, -dz / len);

                    if (this.getVehicle() instanceof WarHorseEntity horse) {
                        // 命中即穿越：与近战士兵保持一致，不再掷概率
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
        if (hit && target instanceof LivingEntity living && !level().isClientSide) {
            ThreatDispatcher.propagate(living, this);
            // 首领 NPC 用首领之刃近战：和玩家挥刀一样给目标挂碎甲
            if (this.getMainHandItem().getItem() instanceof CommanderBlade) {
                CommanderBlade.applyArmorBreak(living, this);
            }
        }
        return hit;
    }

    // ===================== 远程 =====================
    /** 身体视线：身体中心 → 身体中心之间没有被方块挡住（近战 / 骑枪共用）。 */
    private boolean hasBodyLineOfSight(LivingEntity target) {
        Vec3 from = position().add(0, getBbHeight() * 0.5, 0);
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0);
        return level().clip(new net.minecraft.world.level.ClipContext(from, to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, this))
                .getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        if (!(level() instanceof ServerLevel)) return;

        double baseSpeed = CombatConfig.arrowSpeed;
        double speedJitter = 0.85 + this.getRandom().nextDouble() * 0.30;

        ResourceLocation myFaction = getFactionId();
        BallisticAim.Solution sol = BallisticAim.solve(
                level(), this, target,
                baseSpeed * speedJitter,
                e -> {
                    if (!(e instanceof LivingEntity)) return false;
                    ResourceLocation f = ArmyThreatUtil.factionOf(e);
                    return f != null && f.equals(myFaction);
                });

        if (sol == null) return;

        ArmyArrow arrow = new ArmyArrow(this.level(), this, new ItemStack(Items.ARROW), null);

        arrow.shoot(sol.dirX(), sol.dirY(), sol.dirZ(), (float) sol.speed(), (float) CombatConfig.arrowInaccuracy);
        // 用子类可覆盖的首领远程伤害
        arrow.setBaseDamage(getCommanderRangedDamage());

        this.playSound(net.minecraft.sounds.SoundEvents.SKELETON_SHOOT, 1.0F,
                1.0F / (this.getRandom().nextFloat() * 0.4F + 0.8F));
        this.level().addFreshEntity(arrow);
    }

    // ===================== 军团查询 =====================
    /** 士兵每 20 tick 登记一次存在（见 AbstractArmySoldier.tick 的编制登记）。 */
    public void noteSoldierPresence(AbstractArmySoldier soldier) {
        if (soldier == null || level().isClientSide) return;
        soldierRoster.put(soldier.getUUID(), level().getGameTime());
    }

    public List<AbstractArmySoldier> getSoldiers() {
        if (soldierCacheTick < 0
                || tickCount - soldierCacheTick > SOLDIER_CACHE_REFRESH_TICKS) {
            refreshSoldierCache();
        }
        return soldierCache;
    }

    private void refreshSoldierCache() {
        double r = GeneralConfig.armyFollowRange;
        long now = level().getGameTime();
        soldierRoster.entrySet().removeIf(e -> now - e.getValue() > SOLDIER_ROSTER_STALE_TICKS);

        Map<UUID, AbstractArmySoldier> found = new LinkedHashMap<>();
        for (AbstractArmySoldier s : level().getEntitiesOfClass(
                AbstractArmySoldier.class,
                getBoundingBox().inflate(r),
                s -> s.isAlive()
                        && !s.isRemoved()
                        && this.getUUID().equals(s.getCommanderUuid().orElse(null)))) {
            found.put(s.getUUID(), s);
        }

        // 编制名单里的掉队兵：即使跑出扫描半径（长纵队 / 绕路）也保留编制，
        //   阵型槽位才发得出去；只解析已加载的实体。
        if (!soldierRoster.isEmpty() && level() instanceof ServerLevel sl) {
            for (UUID id : soldierRoster.keySet()) {
                if (found.containsKey(id)) continue;
                Entity e = sl.getEntities().get(id);
                if (e instanceof AbstractArmySoldier s
                        && s.isAlive() && !s.isRemoved()
                        && this.getUUID().equals(s.getCommanderUuid().orElse(null))) {
                    found.put(id, s);
                }
            }
        }

        this.soldierCache = List.copyOf(found.values());
        this.soldierCacheTick = tickCount;
    }

    // ===================== 威胁 =====================
    @Nullable
    public LivingEntity getCurrentThreat() {
        if (PeacefulUtil.isPeaceful(this)) return null;
        return pickOwnThreat();
    }

    // ===================== 仇恨列表 =====================
    private void tickHatred() {
        // 和平模式：不结仇，并清掉已有仇恨
        if (PeacefulUtil.isPeaceful(this)) {
            if (!hatredMap.isEmpty()) hatredMap.clear();
            return;
        }
        if (tickCount % 20 != 0) return;
        double maxDist = GeneralConfig.armyFollowRange * 1.5;
        List<UUID> refresh = new ArrayList<>();
        hatredMap.entrySet().removeIf(e -> {
            HatredEntry h = e.getValue();
            if (h.entity == null || !h.entity.isAlive()) return true;
            if (h.entity instanceof Player p && (p.isCreative() || p.isSpectator())) return true;
            if (h.entity.distanceToSqr(this) > maxDist * maxDist) return true;
            // 仇恨过期：只有路线完全无法到达时才移除；还能到达就续期继续追击
            if (h.expiryTick < tickCount) {
                if (canStillReach(h.entity)) {
                    refresh.add(e.getKey());
                    return false;
                }
                return true;
            }
            return false;
        });
        for (UUID id : refresh) {
            HatredEntry old = hatredMap.get(id);
            if (old != null) {
                hatredMap.put(id, new HatredEntry(old.entity, tickCount + GeneralConfig.threatDuration));
            }
        }
    }

    /** 仇恨目标是否仍能通过寻路到达（用于仇恨过期判定）。 */
    private boolean canStillReach(LivingEntity target) {
        net.minecraft.world.entity.ai.navigation.PathNavigation nav = getNavigation();
        net.minecraft.world.level.pathfinder.Path path = nav.createPath(target, 1);
        return path != null && path.canReach();
    }

    public List<LivingEntity> getHatredList() {
        List<LivingEntity> out = new ArrayList<>();
        for (HatredEntry h : hatredMap.values()) {
            if (h.entity != null && h.entity.isAlive()) out.add(h.entity);
        }
        return out;
    }

    @Nullable
    private LivingEntity pickOwnThreat() {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (HatredEntry h : hatredMap.values()) {
            if (h.entity == null || !h.entity.isAlive()) continue;
            if (!canAttack(h.entity)) continue;
            double d = h.entity.distanceToSqr(this);
            if (d < bestD) { bestD = d; best = h.entity; }
        }
        return best;
    }

    public void markAsHatred(LivingEntity target) {
        if (target == null || !target.isAlive()) return;
        if (PeacefulUtil.isPeaceful(this)) return;
        if (target instanceof WarHorseEntity) return;
        if (target instanceof Player p && (p.isCreative() || p.isSpectator())) return;

        int expire = GeneralConfig.threatDuration;
        hatredMap.put(target.getUUID(), new HatredEntry(target, tickCount + expire));

        if (getTarget() == null || !getTarget().isAlive()) {
            setTarget(target);
        }
    }

    @Nullable
    public LivingEntity getCombatTarget() {
        if (PeacefulUtil.isPeaceful(this)) return null;
        LivingEntity t = getTarget();
        if (t != null && t.isAlive()) return t;
        return pickOwnThreat();
    }

    // ===================== 盾牌 / 受伤 =====================
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

        float blocked = computeCommanderShieldBlock(source, amount);
        if (blocked > 0f) playShieldBlockSound();
        float remaining = amount - blocked;
        if (remaining <= 0f) return false;

        boolean hurt = super.hurt(source, remaining);
        if (hurt) lastHurtTick = this.tickCount;
        return hurt;
    }

    private void playShieldBlockSound() {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.SHIELD_BLOCK, this.getSoundSource(),
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

        UUID myArmy = this.getUUID();
        UUID theirArmy = AbstractArmySoldier.resolveArmyUuid(livingAttacker, level());
        if (theirArmy != null && myArmy.equals(theirArmy)) return true;

        ResourceLocation myFac = getFactionId();
        ResourceLocation theirFac = factionOf(livingAttacker);
        if (myFac != null && theirFac != null
                && FactionAlly
                .isAlly(theirFac, myFac)) return true;

        // 同族和平：灾厄阵营 ↔ 原版灾厄生物、村民阵营 ↔ 原版村民 / 铁傀儡，互不伤害。
        return NativePeace.isPeacefulPair(this, livingAttacker);
    }

    private float computeCommanderShieldBlock(DamageSource source, float amount) {
        // 自定义 / 模组盾牌同样能格挡
        if (!CombatRules.isShieldItem(this.getOffhandItem())) return 0f;
        // 魔法伤害 / 破盾伤害一律无法被盾牌格挡。
        if (CombatRules.bypassesShieldBlock(source)) return 0f;
        double chance = CombatConfig.shieldBlockChance;
        if (chance <= 0.0) return 0f;
        if (this.getRandom().nextDouble() >= chance) return 0f;

        double maxAbsorb = CombatConfig.shieldBlockMaxAbsorb
                + getCommanderTier().ordinal() * CombatConfig.shieldBlockMaxAbsorbPerTier;
        return (float) Math.min(amount, maxAbsorb);
    }

    /**
     * 应用子类覆盖的首领属性基础值。
     *   调用时机：
     *     1) 构造函数（覆盖刷怪蛋 / 自然生成 / summon 所有创建路径）
     *     2) finalizeSpawn（防御性，无副作用）
     *     3) readAdditionalSaveData（存档重载，MC 不保存 attribute baseValue）
     */
    protected void applyCommanderAttributes() {
        var hp = this.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) hp.setBaseValue(getCommanderMaxHealth());
        var atk = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(getCommanderAttackDamage());
        var armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(getCommanderArmor());
        var kbr = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbr != null) kbr.setBaseValue(getCommanderKnockbackResistance());
        var mspd = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (mspd != null) mspd.setBaseValue(getCommanderMovementSpeed());
        var follow = this.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) follow.setBaseValue(getCommanderFollowRange());
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
                                        MobSpawnType reason, @Nullable SpawnGroupData data) {
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data);

        // 应用子类覆盖的属性
        applyCommanderAttributes();
        this.setHealth((float) getCommanderMaxHealth());

        this.lastStuckX = getX();
        this.lastStuckY = getY();
        this.lastStuckZ = getZ();
        return result;
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide) {
            ArmyRelations.clear(this.getUUID());
            for (AbstractArmySoldier s : getSoldiers()) {
                LivingEntity t = s.getTarget();
                if (t instanceof AbstractArmySoldier || t instanceof AbstractArmyCommander
                        || t instanceof WarHorseEntity) {
                    s.setTarget(null);
                }
            }
        }
        super.die(source);
    }

    // ===================== 存档 =====================
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Faction", getFactionId().toString());
        // MC 不保存 attribute baseValue，MAX_HEALTH 会在 load 时被重置为默认值，
        //   原版 Health 字段随之被 clamp 到 100。用自定义 key 保存原始血量。
        tag.putFloat("MC_Health", getHealth());
        // 村庄 / 袭击锚点（普通单位无锚点，不写入）
        anchor.save(tag, "Anchor");
        // 野外巡逻队标记（远离玩家后消失）
        if (wild) tag.putBoolean("MC_Wild", true);
        // 手动装备 / 自带装备快照 / 战术装备
        tag.put("ManualGear", stackListTag(manualGear));
        tag.put("DefaultGear", stackListTag(defaultGear));
        tag.put("ManualTactical", stackListTag(manualTactical));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        ResourceLocation rl = ResourceLocation.tryParse(tag.getString("Faction"));
        if (rl != null) setFactionId(rl);
        this.wild = tag.getBoolean("MC_Wild");

        // 重新应用子类覆盖的属性（MC 不保存 attribute baseValue）
        applyCommanderAttributes();

        // 恢复存档血量（子类覆盖的 MAX_HEALTH 之后才生效，此处 setHealth 不会再被截断）
        if (tag.contains("MC_Health")) {
            this.setHealth(tag.getFloat("MC_Health"));
        }
        anchor.load(tag, "Anchor");
        readStackList(tag.getList("ManualGear", Tag.TAG_COMPOUND), manualGear);
        readStackList(tag.getList("DefaultGear", Tag.TAG_COMPOUND), defaultGear);
        readStackList(tag.getList("ManualTactical", Tag.TAG_COMPOUND), manualTactical);
        refreshManualGearMask();
    }
}
