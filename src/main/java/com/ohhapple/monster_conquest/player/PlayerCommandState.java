package com.ohhapple.monster_conquest.player;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerCommandState {

    public enum FormationMode {
        DEFAULT(0), LINE(1), WEDGE(2), SHIELD_WALL(3), SKIRMISH(4), FREE(5);
        private final int id;
        FormationMode(int id) { this.id = id; }
        public int getId() { return id; }
        public static FormationMode byId(int id) {
            for (FormationMode m : values()) if (m.id == id) return m;
            return DEFAULT;
        }
    }

    public enum MovementMode {
        FOLLOW(0), STATION(1), SOFT_FOLLOW(2),
        /** 自由移动：不跟随、不驻守、不列阵，像原版生物一样自由游走。 */
        FREE(3);
        private final int id;
        MovementMode(int id) { this.id = id; }
        public int getId() { return id; }
        public static MovementMode byId(int id) {
            for (MovementMode m : values()) if (m.id == id) return m;
            return FOLLOW;
        }
    }

    public enum AttackMode {
        AUTONOMOUS(0), COMMANDED(1), HOLD_GROUND(2), FREE_AGGRESSIVE(3), STOP(4);
        private final int id;
        AttackMode(int id) { this.id = id; }
        public int getId() { return id; }
        public static AttackMode byId(int id) {
            for (AttackMode m : values()) if (m.id == id) return m;
            return AUTONOMOUS;
        }
    }

    public enum RideMode {
        AUTO(0), FORCED_CAVALRY(1), FORCED_INFANTRY(2);
        private final int id;
        RideMode(int id) { this.id = id; }
        public int getId() { return id; }
        public static RideMode byId(int id) {
            for (RideMode m : values()) if (m.id == id) return m;
            return AUTO;
        }
    }

    public static class SquadState {
        FormationMode formation = FormationMode.DEFAULT;
        MovementMode movement = MovementMode.STATION;
        AttackMode attack = AttackMode.AUTONOMOUS;
        RideMode rideMode = RideMode.AUTO;
        @Nullable Vec3 formationAnchor;
        float formationAnchorYaw = 0f;
        long anchorChangedGameTime = -1L;
        @Nullable UUID forcedTarget;
        /**
         * 强制目标组：命令指定的目标属于军队 / 巡逻小队时，
         *   整支军队（指挥官 + 全部士兵）都会进入这里，一起被锁定。
         */
        final Set<UUID> forcedTargetGroup = new HashSet<>();
        @Nullable Vec3 volleyTarget;
        long volleyEndGameTime = 0L;

        boolean softStation = false;
        MovementMode softStationOrigin = MovementMode.STATION;
        long softStationLastCheckedGT = 0L;

        /**
         * FOLLOW / SOFT_FOLLOW 的跟随对象（任意存活单位 UUID：玩家 / 士兵 / 首领 / 战马…）。
         *   null = 跟随默认主人（指挥官自己）；非 null = 右键指定的那个单位。
         */
        @Nullable UUID followTargetUuid = null;

        final Map<UUID, Vec3> formationSlots = new HashMap<>();
        int formationCacheTick = Integer.MIN_VALUE;
        FormationMode cachedMode = null;
        @Nullable Vec3 cachedAnchor = null;
        float cachedYaw = Float.NaN;

        /**
         * 可达落点缓存（阵型重建用）：从指挥官脚下"能走过去"的落点集合。
         *   命令点 / 指挥官位置没明显变化时直接复用 —— 否则每次重建都跑一遍有界 BFS 太费。
         */
        @Nullable Set<Long> reachCache = null;
        double reachCacheAnchorX = Double.NaN;
        double reachCacheAnchorZ = Double.NaN;
        double reachCacheAnchorY = Double.NaN;
        double reachCacheRadius = -1.0;
        /** 骑乘单位专用的可达性缓存（按坐骑净空搜索）；null = 没算过 / 当前没有骑乘单位。 */
        @Nullable Set<Long> reachCacheMount = null;
        double reachCacheMountRadius = -1.0;
        double reachCacheMountWidth = -1.0;
        double reachCacheMountExtra = -1.0;

        /**
         * 移动指令代号：每次"去哪"的命令（前往 / 跟随 / 自由移动 / 驻守）下发时自增。
         *   士兵的移动 Goal 会记住上一次见到的代号；一旦变了就说明玩家刚下了新命令，
         *   旧路径 / 旧绕路必须当场作废、立刻按新目标重新规划 ——
         *   否则单位会先沿旧路线走到一半才听新命令（表现为"还朝旧地点走一小会儿"）。
         */
        int moveOrderStamp = 0;
        /** "前往指定点"命令的目标点与截止刻：命令执行期间士兵不主动索敌（命令优先）。 */
        @Nullable Vec3 moveOrderDest = null;
        long moveOrderUntilGT = 0L;

        public int getMoveOrderStamp() { return moveOrderStamp; }

        /**
         * 最近一条"前往指定点"命令是否还没被后续命令（攻击 / 驻守 / 跟随 / 自由移动）覆盖。
         *   命令点就是这次命令的目的地：还挂着它时任何"拉回锚点 / 传送到锚点"的行为
         *   都等于把单位瞬移到目的地，必须禁止。
         */
        public boolean hasMoveOrder() { return moveOrderDest != null; }

        public FormationMode getFormation() { return formation; }
        public MovementMode getMovement() { return movement; }
        public AttackMode getAttack() { return attack; }
        public RideMode getRideMode() { return rideMode; }
        public void setRideMode(RideMode m) { this.rideMode = m; }

        /** 直接设置这一份状态的阵型（单位界面按小队 / 单兵状态改阵型时用）。 */
        public void setFormation(FormationMode f) {
            if (this.formation != f) {
                this.formation = f;
                invalidateFormationCache();
            }
        }

        public void setMovement(MovementMode m) {
            if (this.movement != m) {
                this.movement = m;
                invalidateFormationCache();
            }
        }
        @Nullable public Vec3 getFormationAnchor() { return formationAnchor; }
        public float getFormationAnchorYaw() { return formationAnchorYaw; }
        public long getAnchorChangedGameTime() { return anchorChangedGameTime; }

        public void setFormationAnchor(@Nullable Vec3 anchor) {
            setFormationAnchor(anchor, this.formationAnchorYaw, -1L);
        }

        public void setFormationAnchor(@Nullable Vec3 anchor, float yaw) {
            setFormationAnchor(anchor, yaw, -1L);
        }

        public void setFormationAnchor(@Nullable Vec3 anchor, float yaw, long gameTime) {
            boolean changed = (this.formationAnchor == null) != (anchor == null)
                    || (this.formationAnchor != null && anchor != null
                    && this.formationAnchor.distanceToSqr(anchor) > 4.0);
            if (changed && gameTime >= 0) this.anchorChangedGameTime = gameTime;
            this.formationAnchor = anchor;
            this.formationAnchorYaw = yaw;
            invalidateFormationCache();
        }
        @Nullable public UUID getForcedTarget() { return forcedTarget; }

        // ===================== 指令下发（小队 / 无小队单兵共用同一实现） =====================

        /**
         * 移动命令：设置阵型锚点 + 进入驻守、退出软驻守。
         *   指挥小队和无小队单兵都调用这里。
         */
        public void applyMoveOrder(Vec3 dest, float yaw, long gameTime) {
            moveOrderStamp++;
            setFormationAnchor(dest, yaw, gameTime);
            setMovement(MovementMode.STATION);
            setSoftStation(false);
            // 移动命令优先：到达前不主动索敌（见 isMoveOrderPriorityActive）。
            moveOrderDest = dest;
            moveOrderUntilGT = gameTime + MOVE_ORDER_PRIORITY_TICKS;
            // 移动命令覆盖旧的攻击命令：清掉"点名攻击"的强制目标
            forcedTarget = null;
            forcedTargetGroup.clear();
        }

        /**
         * 驻守命令：以指定点为锚点原地驻守。
         *   与"前往指定点"不同，驻守不设"移动命令优先"窗口 —— 部队在驻守点就位前后都要正常索敌，
         *   否则隔着几十秒谁打到家门口都不还手（只有被攻击的反击目标能穿过）。
         */
        public void applyHoldOrder(Vec3 anchor, float yaw, long gameTime) {
            moveOrderStamp++;
            setFormationAnchor(anchor, yaw, gameTime);
            setMovement(MovementMode.STATION);
            setSoftStation(false);
            // 原地驻守没有"走到命令点"的阶段：清掉前往命令，驻守期间照常索敌
            moveOrderDest = null;
            moveOrderUntilGT = 0L;
            // 驻守命令覆盖旧的攻击命令：清掉"点名攻击"的强制目标
            forcedTarget = null;
            forcedTargetGroup.clear();
        }

        /**
         * "前往指定点"命令是否仍在执行：命令期间士兵不主动索敌（命令优先于自主锁定）。
         *   判断条件:还有命令目标、没超时、且这个士兵还没走到目标点附近。
         */
        public boolean isMoveOrderPriorityActive(double x, double z, long nowGT) {
            if (moveOrderDest == null) return false;
            if (nowGT >= moveOrderUntilGT) return false;
            double dx = x - moveOrderDest.x;
            double dz = z - moveOrderDest.z;
            return dx * dx + dz * dz > MOVE_ORDER_ARRIVE_SQR;
        }

        /**
         * 攻击命令：记录原移动模式 → 进入软驻守（脱战后自动恢复）。
         *   指挥小队和无小队单兵都调用这里。
         */
        public void applyAttackOrder(long gameTime) {
            if (!isSoftStation()) setSoftStationOrigin(getMovement());
            setMovement(MovementMode.STATION);
            setSoftStation(true);
            setSoftStationLastCheckedGT(gameTime);
            // 攻击命令覆盖移动命令：取消"前往期间不索敌"
            moveOrderDest = null;
            moveOrderUntilGT = 0L;
        }

        /** 跟随默认主人（指挥官自己）。 */
        public void applyFollowOrder(MovementMode m) {
            applyFollowOrder(m, null);
        }

        /**
         * 跟随命令：进入 FOLLOW / SOFT_FOLLOW（清锚点、退出软驻守）。
         *   小队和无小队单兵共用；followUuid = 跟随对象（null = 跟随主人）。
         */
        public void applyFollowOrder(MovementMode m, @Nullable UUID followUuid) {
            moveOrderStamp++;
            boolean changed = this.movement != m
                    || !java.util.Objects.equals(this.followTargetUuid, followUuid);
            this.movement = m;
            this.followTargetUuid = followUuid;
            this.formationAnchor = null;
            this.softStation = false;
            this.softStationOrigin = MovementMode.STATION;
            // 跟随命令覆盖"前往"命令：清掉命令点，避免残留的"命令优先"影响到这次命令
            this.moveOrderDest = null;
            this.moveOrderUntilGT = 0L;
            if (changed) invalidateFormationCache();
        }

        /**
         * 自由移动命令：清跟随对象 / 阵型锚点，退出软驻守。
         *   之后 FormationGoal / FollowCommanderGoal 全部让位，
         *   由原版游走 AI 接管（像原版生物一样自由游走）。
         */
        public void applyFreeMoveOrder() {
            moveOrderStamp++;
            boolean changed = this.movement != MovementMode.FREE;
            this.movement = MovementMode.FREE;
            this.followTargetUuid = null;
            this.formationAnchor = null;
            this.softStation = false;
            this.softStationOrigin = MovementMode.STATION;
            // 自由移动命令同样覆盖"前往"命令：清掉命令点
            this.moveOrderDest = null;
            this.moveOrderUntilGT = 0L;
            if (changed) invalidateFormationCache();
        }

        @Nullable public UUID getFollowTargetUuid() { return followTargetUuid; }

        public Set<UUID> getForcedTargetGroup() { return forcedTargetGroup; }

        /** 该 UUID 是否属于本队的强制目标（单体或整队）。 */
        public boolean isForcedTarget(UUID id) {
            if (id == null) return false;
            return id.equals(forcedTarget) || forcedTargetGroup.contains(id);
        }

        public void setForcedTargetGroup(@Nullable Collection<UUID> group) {
            forcedTargetGroup.clear();
            if (group != null) forcedTargetGroup.addAll(group);
        }

        public void setAttackMode(AttackMode m) { this.attack = m; }
        public void setForcedTargetId(@Nullable UUID t) { this.forcedTarget = t; }

        @Nullable public Vec3 getVolleyTarget() { return volleyTarget; }
        public long getVolleyEndGameTime() { return volleyEndGameTime; }

        public boolean isSoftStation() { return softStation; }
        public void setSoftStation(boolean v) { this.softStation = v; }

        public MovementMode getSoftStationOrigin() { return softStationOrigin; }
        public void setSoftStationOrigin(MovementMode m) {
            this.softStationOrigin = (m == null) ? MovementMode.STATION : m;
        }

        public long getSoftStationLastCheckedGT() { return softStationLastCheckedGT; }
        public void setSoftStationLastCheckedGT(long gt) { this.softStationLastCheckedGT = gt; }

        public boolean isVolleyActive(long nowGameTime) {
            return volleyTarget != null && nowGameTime < volleyEndGameTime;
        }

        public Map<UUID, Vec3> getFormationSlots() { return formationSlots; }

        public boolean shouldRebuildFormation(int nowTick, FormationMode mode,
                                              @Nullable Vec3 anchor, float yaw) {
            if (formationCacheTick == Integer.MIN_VALUE) return true;
            if (nowTick - formationCacheTick > 20) return true;
            if (cachedMode != mode) return true;
            if (cachedAnchor == null || anchor == null
                    || cachedAnchor.distanceToSqr(anchor) > 4.0) return true;
            if (Float.isNaN(cachedYaw) || Math.abs(
                    net.minecraft.util.Mth.wrapDegrees(cachedYaw - yaw)) > 15f) return true;
            return false;
        }

        public void commitFormationCache(int nowTick, FormationMode mode,
                                         @Nullable Vec3 anchor, float yaw) {
            formationCacheTick = nowTick;
            cachedMode = mode;
            cachedAnchor = anchor;
            cachedYaw = yaw;
        }

        public void invalidateFormationCache() {
            formationCacheTick = Integer.MIN_VALUE;
            cachedMode = null;
            cachedAnchor = null;
            cachedYaw = Float.NaN;
            formationSlots.clear();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Formation", formation.getId());
            tag.putInt("Movement", movement.getId());
            tag.putInt("Attack", attack.getId());
            tag.putInt("RideMode", rideMode.getId());
            tag.putBoolean("SoftStation", softStation);
            tag.putInt("SoftStationOrigin", softStationOrigin.getId());
            if (followTargetUuid != null) tag.putUUID("FollowTarget", followTargetUuid);
            if (formationAnchor != null) {
                tag.putDouble("AnchorX", formationAnchor.x);
                tag.putDouble("AnchorY", formationAnchor.y);
                tag.putDouble("AnchorZ", formationAnchor.z);
                tag.putFloat("AnchorYaw", formationAnchorYaw);
                tag.putLong("AnchorChangedGT", anchorChangedGameTime);
            }
            if (forcedTarget != null) tag.putUUID("ForcedTarget", forcedTarget);
            if (!forcedTargetGroup.isEmpty()) {
                ListTag group = new ListTag();
                for (UUID id : forcedTargetGroup) group.add(NbtUtils.createUUID(id));
                tag.put("ForcedTargetGroup", group);
            }
            return tag;
        }

        public void load(CompoundTag tag) {
            formation = FormationMode.byId(tag.getInt("Formation"));
            movement = MovementMode.byId(tag.getInt("Movement"));
            attack = AttackMode.byId(tag.getInt("Attack"));
            rideMode = RideMode.byId(tag.getInt("RideMode"));
            softStation = tag.getBoolean("SoftStation");
            softStationOrigin = tag.contains("SoftStationOrigin")
                    ? MovementMode.byId(tag.getInt("SoftStationOrigin"))
                    : MovementMode.STATION;
            followTargetUuid = tag.hasUUID("FollowTarget")
                    ? tag.getUUID("FollowTarget") : null;
            if (tag.contains("AnchorX")) {
                formationAnchor = new Vec3(
                        tag.getDouble("AnchorX"),
                        tag.getDouble("AnchorY"),
                        tag.getDouble("AnchorZ"));
                formationAnchorYaw = tag.getFloat("AnchorYaw");
                anchorChangedGameTime = tag.getLong("AnchorChangedGT");
            } else {
                formationAnchor = null;
                formationAnchorYaw = 0f;
                anchorChangedGameTime = -1L;
            }
            if (tag.hasUUID("ForcedTarget")) forcedTarget = tag.getUUID("ForcedTarget");
            else forcedTarget = null;
            forcedTargetGroup.clear();
            ListTag group = tag.getList("ForcedTargetGroup", Tag.TAG_INT_ARRAY);
            for (int i = 0; i < group.size(); i++) {
                UUID id = NbtUtils.loadUUID(group.get(i));
                if (id != null) forcedTargetGroup.add(id);
            }
            volleyTarget = null;
            volleyEndGameTime = 0L;
            softStationLastCheckedGT = 0L;
            invalidateFormationCache();
        }

        public void clear() {
            formation = FormationMode.DEFAULT;
            movement = MovementMode.STATION;
            attack = AttackMode.AUTONOMOUS;
            rideMode = RideMode.AUTO;
            formationAnchor = null;
            formationAnchorYaw = 0f;
            anchorChangedGameTime = -1L;
            forcedTarget = null;
            forcedTargetGroup.clear();
            volleyTarget = null;
            volleyEndGameTime = 0L;
            softStation = false;
            softStationOrigin = MovementMode.STATION;
            softStationLastCheckedGT = 0L;
            followTargetUuid = null;
            invalidateFormationCache();
        }
    }

    private final UUID ownerUuid;

    private final Map<Integer, SquadState> squads = new HashMap<>();

    // 默认 1 分队（不是全选）。全选只在当次 session 内有效，不持久化。
    private int selectedSquad = 1;
    private int lastConcreteSquad = 1;

    private long commandGen = 0L;
    private long lastOrderIssuedGT = -1L;

    private UUID controlToolSelectedArmy = null;
    private ResourceLocation controlToolSelectedFaction = null;
    // 控制工具默认也是 1 分队。
    private int controlToolSelectedSquad = 1;

    /**
     * 控制工具"单兵选择"标记（controlToolSelectedSquad = -1）：
     *   中键选中的是无小队士兵（或他骑的战马）时使用 —— 命令只作用于这一个士兵，
     *   与"全选（0）"彻底区分开。
     */
    public static final int CONTROL_TOOL_LONE = -1;

    /** "前往指定点"命令期间不主动索敌的最长时间（tick）：60 秒内到不了就恢复自主索敌。 */
    public static final int MOVE_ORDER_PRIORITY_TICKS = 1200;
    /** 离命令目标点多近算"到达"（格²）：到达后恢复自主索敌（就地防御）。 */
    public static final double MOVE_ORDER_ARRIVE_SQR = 6.0 * 6.0;

    private UUID preciseStrikeTarget = null;
    /** 精确打击目标组：目标属于军队 / 巡逻小队时，整队一起被锁定。 */
    private final Set<UUID> preciseStrikeGroup = new HashSet<>();
    private final Set<Integer> preciseStrikeSquads = new HashSet<>();
    private long preciseStrikeEndGT = 0L;

    private final Set<Integer> occupiedSquads = new HashSet<>();

    // ===================== RTS：个体选中 =====================

    /** RTS：当前选中的士兵 UUID（个体选择，不是队号）。 */
    private final Set<UUID> rtsSelectedSoldiers = new LinkedHashSet<>();
    /** 已释放的隐藏小队号池。 */
    private final Set<Integer> freeHiddenSquadIds = new TreeSet<>();
    /** 下一个待分配的隐藏小队号。 */
    private int nextHiddenSquadId = -1;
    /** RTS 模式开关。 */
    private boolean rtsActive = false;
    /** RTS 移动标记。 */
    @Nullable private Vec3 rtsMoveMarker = null;
    private long rtsMoveMarkerGT = 0L;
    public static final long RTS_MARKER_LIFETIME = 300L;

    private final Map<UUID, Long> sharedHatredArmies = new HashMap<>();
    private final Map<UUID, Long> sharedHatredEntities = new HashMap<>();

    public PlayerCommandState(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public UUID getOwnerUuid() { return ownerUuid; }

    public SquadState squadState(int squadId) {
        return squads.computeIfAbsent(squadId, k -> new SquadState());
    }

    public SquadState stateForSoldier(int squadId) {
        SquadState s = squads.get(squadId);
        if (s != null) return s;
        return squads.computeIfAbsent(squadId, k -> new SquadState());
    }

    public SquadState current() {
        return squadState(selectedSquad);
    }

    public int getSelectedSquad() { return selectedSquad; }
    public int getLastConcreteSquad() { return lastConcreteSquad; }

    /**
     * 设置当前选中的分队。
     *   0 = 全选（只作用于 1..maxPlayerSquads）；>0 = 指定队号（含隐藏队号 6+）。
     *
     *   这里不做 maxPlayerSquads 钳制 —— 否则"控制工具中键选中隐藏队（6+）"
     *   会被压回 5，隐藏队的指挥就落到 5 分队头上了。
     */
    public void setSelectedSquad(int s) {
        if (s < 0) s = 0;
        this.selectedSquad = s;
        if (s > 0) this.lastConcreteSquad = s;
    }

    /**
     * 该士兵的队号是否匹配当前选中。
     *   - selectedSquad == 0（全选）→ 只匹配 1..maxPlayerSquads，隐藏队不参与全选
     *   - selectedSquad > 0 → 精确匹配（可以是隐藏队号）
     */
    public boolean matchesSquad(int soldierSquad) {
        if (selectedSquad == 0) {
            int max = Math.max(1, PlayerConfig.maxPlayerSquads);
            return soldierSquad >= 1 && soldierSquad <= max;
        }
        return soldierSquad == selectedSquad;
    }

    public int squadForNewSoldier() {
        return selectedSquad == 0 ? lastConcreteSquad : selectedSquad;
    }

    public long getCommandGen() { return commandGen; }
    public long getLastOrderIssuedGT() { return lastOrderIssuedGT; }

    public void issueOrder(long nowGT) {
        this.commandGen++;
        this.lastOrderIssuedGT = nowGT;
        endPreciseStrike();
    }

    @Nullable public UUID getControlToolSelectedArmy() {
        return controlToolSelectedArmy;
    }
    public void setControlToolSelectedArmy(@Nullable UUID army) {
        this.controlToolSelectedArmy = army;
    }

    @Nullable public ResourceLocation getControlToolSelectedFaction() {
        return controlToolSelectedFaction;
    }
    public void setControlToolSelectedFaction(@Nullable ResourceLocation faction) {
        this.controlToolSelectedFaction = faction;
    }

    public int getControlToolSelectedSquad() { return controlToolSelectedSquad; }
    public void setControlToolSelectedSquad(int s) {
        // -1 = 单兵选择（无小队士兵）；0 = 全选；>0 = 指定队号（含隐藏队号）
        this.controlToolSelectedSquad = Math.max(CONTROL_TOOL_LONE, s);
    }

    public void setOccupiedSquads(Collection<Integer> squads) {
        this.occupiedSquads.clear();
        this.occupiedSquads.addAll(squads);
    }

    public boolean isSquadOccupied(int squadId) {
        return occupiedSquads.contains(squadId);
    }

    // ===================== 共享仇恨 =====================

    public void markSharedHatredArmy(UUID army, long nowGT) {
        if (army == null) return;
        sharedHatredArmies.put(army, nowGT + GeneralConfig.threatDuration);
    }

    public boolean isSharedHatredArmy(UUID army, long nowGT) {
        if (army == null) return false;
        Long expiry = sharedHatredArmies.get(army);
        if (expiry == null) return false;
        if (nowGT > expiry) {
            sharedHatredArmies.remove(army);
            return false;
        }
        return true;
    }

    public void tickSharedHatred(long nowGT) {
        if (sharedHatredArmies.isEmpty()) return;
        sharedHatredArmies.entrySet().removeIf(e -> nowGT > e.getValue());
    }

    public void clearSharedHatred() { sharedHatredArmies.clear(); }
    public Map<UUID, Long> getSharedHatredArmies() { return sharedHatredArmies; }

    public void markSharedHatredEntity(UUID entityUuid, long nowGT) {
        if (entityUuid == null) return;
        sharedHatredEntities.put(entityUuid, nowGT + GeneralConfig.threatDuration);
    }

    public boolean isSharedHatredEntity(UUID entityUuid, long nowGT) {
        if (entityUuid == null) return false;
        Long expiry = sharedHatredEntities.get(entityUuid);
        if (expiry == null) return false;
        if (nowGT > expiry) {
            sharedHatredEntities.remove(entityUuid);
            return false;
        }
        return true;
    }

    public void tickSharedHatredEntities(long nowGT) {
        if (sharedHatredEntities.isEmpty()) return;
        sharedHatredEntities.entrySet().removeIf(e -> nowGT > e.getValue());
    }

    public void clearSharedHatredEntities() { sharedHatredEntities.clear(); }
    public Map<UUID, Long> getSharedHatredEntities() { return sharedHatredEntities; }

    public void startPreciseStrike(UUID target,
                                   Collection<Integer> squads,
                                   long nowGT, long durationTicks) {
        startPreciseStrike(target, null, squads, nowGT, durationTicks);
    }

    /**
     * 精确打击（可带"整队目标组"）：
     *   目标是军队 / 巡逻小队成员时，把整支军队都加入目标组。
     */
    public void startPreciseStrike(UUID target,
                                   @Nullable Collection<UUID> group,
                                   Collection<Integer> squads,
                                   long nowGT, long durationTicks) {
        this.preciseStrikeTarget = target;
        this.preciseStrikeGroup.clear();
        this.preciseStrikeGroup.add(target);
        if (group != null) this.preciseStrikeGroup.addAll(group);
        this.preciseStrikeSquads.clear();
        this.preciseStrikeSquads.addAll(squads);
        this.preciseStrikeEndGT = nowGT + durationTicks;
    }

    public void endPreciseStrike() {
        this.preciseStrikeTarget = null;
        this.preciseStrikeGroup.clear();
        this.preciseStrikeSquads.clear();
        this.preciseStrikeEndGT = 0L;
    }

    /** 精确打击目标组（含目标本身）。 */
    public Set<UUID> getPreciseStrikeGroup() { return preciseStrikeGroup; }

    /** 该 UUID 是否属于本次精确打击的目标组。 */
    public boolean isPreciseStrikeTarget(UUID id) {
        if (id == null) return false;
        return preciseStrikeGroup.contains(id)
                || (preciseStrikeTarget != null && preciseStrikeTarget.equals(id));
    }

    public UUID getPreciseStrikeTarget(long nowGT) {
        if (preciseStrikeTarget == null) return null;
        if (nowGT > preciseStrikeEndGT) {
            endPreciseStrike();
            return null;
        }
        return preciseStrikeTarget;
    }

    public boolean isSquadInPreciseStrike(int squadId) {
        return !preciseStrikeSquads.isEmpty()
                && preciseStrikeSquads.contains(squadId);
    }

    /**
     * "全选"作用的队号集合：只包含 1..maxPlayerSquads。
     *   隐藏队号（>max）不参与全选，只由指定队号命令控制。
     */
    private Iterable<Integer> activeSquadIds() {
        int max = Math.max(1, PlayerConfig.maxPlayerSquads);
        Set<Integer> ids = new LinkedHashSet<>();
        for (int id : occupiedSquads) {
            if (id >= 1 && id <= max) ids.add(id);
        }
        for (Integer id : squads.keySet()) {
            if (id != null && id >= 1 && id <= max) ids.add(id);
        }
        return ids;
    }

    /**
     * 硬驻守索敌判定唯一入口：非硬驻守 / 弓手 / 双形态不限制；
     *   枪手要求目标在射程内；其余要求目标在锚点（无锚点用当前位置）
     *   {@code CombatConfig.hardStationRange} 范围内，超出返回 false。
     */
    public static boolean allowsHardStationTarget(
            AbstractArmySoldier soldier,
            @Nullable Vec3 anchor,
            Vec3 targetPos) {
        if (soldier == null || targetPos == null) return true;
        if (!soldier.isHardStationed()) return true;

        var role = soldier.getRole();
        // 弓手 / 双形态不移动，由 BallisticAim 判断能否命中。
        if (role.isArcher() || role.isDual()) return true;

        // 枪手有明确的最大射程：硬驻守时只把射程内的敌人当作合法目标。
        //   否则枪手会一直仇恨锁定远处打不到的敌人，无视之后靠近的敌人。
        if (role.isGunner()) {
            double max = soldier.getMaxAttackRange(soldier.getSoldierType().isCavalry()) + 2.0;
            return soldier.position().distanceToSqr(targetPos) <= max * max;
        }

        Vec3 a = (anchor != null) ? anchor : soldier.position();
        double maxSqr = CombatConfig.hardStationRange * CombatConfig.hardStationRange;
        return a.distanceToSqr(targetPos) <= maxSqr;
    }

    public FormationMode getFormation() { return current().formation; }

    public void setFormation(FormationMode f) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) applyFormation(s, f);
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            applyFormation(s, f);
        }
    }

    private static void applyFormation(SquadState s, FormationMode f) {
        if (s.formation != f) {
            s.formation = f;
            s.invalidateFormationCache();
        }
    }

    public MovementMode getMovement() { return current().movement; }

    public void setMovement(MovementMode m) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) applyMovement(s, m);
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            applyMovement(s, m);
        }
    }

    private static void applyMovement(SquadState s, MovementMode m) {
        // 自由移动：清锚点 / 清跟随对象 / 退出软驻守
        if (m == MovementMode.FREE) {
            s.applyFreeMoveOrder();
            return;
        }
        // FOLLOW / SOFT_FOLLOW 走共用实现（清锚点、退出软驻守）
        if (m == MovementMode.FOLLOW || m == MovementMode.SOFT_FOLLOW) {
            s.applyFollowOrder(m);
            return;
        }
        if (s.movement != m) {
            // 行动模式换了（例如"跟随 → 驻守"）也是新命令：立刻改走新目标
            s.moveOrderStamp++;
            s.movement = m;
            s.invalidateFormationCache();
        }
        s.softStation = false;
        s.softStationOrigin = MovementMode.STATION;
    }

    public AttackMode getAttack() { return current().attack; }

    public void setAttack(AttackMode a) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) s.attack = a;
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            s.attack = a;
        }
    }

    public RideMode getRideMode() { return current().rideMode; }

    public void setRideMode(RideMode m) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) s.rideMode = m;
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            s.rideMode = m;
        }
    }

    @Nullable public Vec3 getFormationAnchor() { return current().formationAnchor; }
    public float getFormationAnchorYaw() { return current().formationAnchorYaw; }
    public long getAnchorChangedGameTime() { return current().anchorChangedGameTime; }

    public void setFormationAnchor(@Nullable Vec3 anchor) {
        setFormationAnchor(anchor, current().formationAnchorYaw, -1L);
    }

    public void setFormationAnchor(@Nullable Vec3 anchor, float yaw) {
        setFormationAnchor(anchor, yaw, -1L);
    }

    public void setFormationAnchor(@Nullable Vec3 anchor, float yaw, long gameTime) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) applyAnchor(s, anchor, yaw, gameTime);
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            applyAnchor(s, anchor, yaw, gameTime);
        }
    }

    private static void applyAnchor(SquadState s, @Nullable Vec3 anchor,
                                    float yaw, long gameTime) {
        boolean changed = (s.formationAnchor == null) != (anchor == null)
                || (s.formationAnchor != null && anchor != null
                && s.formationAnchor.distanceToSqr(anchor) > 4.0);
        if (changed && gameTime >= 0) s.anchorChangedGameTime = gameTime;
        s.formationAnchor = anchor;
        s.formationAnchorYaw = yaw;
        s.invalidateFormationCache();
    }

    @Nullable public UUID getForcedTarget() { return current().forcedTarget; }

    public void setForcedTarget(@Nullable UUID target) {
        setForcedTarget(target, null);
    }

    /**
     * 设置强制目标（可带"整队目标组"）。
     *   目标是军队 / 巡逻小队成员时，把整支军队都加入组里一起锁定。
     */
    public void setForcedTarget(@Nullable UUID target, @Nullable Collection<UUID> group) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) {
                    s.forcedTarget = target;
                    s.setForcedTargetGroup(group);
                }
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            s.forcedTarget = target;
            s.setForcedTargetGroup(group);
        }
    }

    // ===================== 命令下发统一入口（小队 / 无小队单兵共用同一实现） =====================

    /**
     * 指挥状态解析：无小队（squadId == 0）士兵 → 个人状态；有队 → 玩家小队状态。
     *
     *   命令下发、软驻守、索敌、驻守判定……所有读取都必须走这里，
     *   保证"单兵"和"小队"永远共用同一套 SquadState 实现，不存在第二套逻辑。
     */
    public static SquadState stateForCommand(AbstractArmySoldier soldier,
                                             PlayerCommandState state) {
        // 界面里单独调过策略的单位：走它自己的那份状态，只影响这一个单位，不再吃小队 / 全军指令
        if (soldier.hasStrategyOverride()) return soldier.loneState();
        if (soldier.getSquadId() == 0) return soldier.loneState();
        return state.stateForSoldier(soldier.getSquadId());
    }

    /**
     * 移动命令：小队（0 = 1..maxPlayerSquads 全选；>0 = 指定队号，含隐藏队号）。
     *   与无小队单兵的 orderLoneMove 调用同一个 SquadState.applyMoveOrder。
     */
    public void orderMove(int squadId, Vec3 dest, float yaw, long nowGT) {
        if (squadId == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) s.applyMoveOrder(dest, yaw, nowGT);
            }
        } else {
            squadState(squadId).applyMoveOrder(dest, yaw, nowGT);
        }
        clearPreciseStrike(squadId);
        issueOrder(nowGT);
    }

    /** 移动命令覆盖旧的攻击命令：受影响的精确打击目标一并清掉（全选直接清，指定小队则把它移出打击名单）。 */
    private void clearPreciseStrike(int squadId) {
        if (preciseStrikeTarget == null) return;
        if (squadId == 0) {
            endPreciseStrike();
            return;
        }
        preciseStrikeSquads.remove(squadId);
        if (preciseStrikeSquads.isEmpty()) endPreciseStrike();
    }

    /**
     * 原地驻守：选中的小队（或全选时的各小队）立刻以"当前所在地"为锚点硬驻守，
     *   而不是走到玩家脚下驻守。
     *   各小队分别取自己的质心 → 分散的部队就地驻守，不会长途跋涉。
     */
    public void orderHoldInPlace(Player player, ServerLevel sl, long nowGT) {
        if (selectedSquad == 0) {
            // 全选：所有"有状态"的小队 + 附近实际有士兵的小队，各自就地驻守
            Set<Integer> ids = new LinkedHashSet<>();
            for (int id : activeSquadIds()) ids.add(id);
            double range = GeneralConfig.armyFollowRange * 4;
            for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class,
                    player.getBoundingBox().inflate(range))) {
                if (!s.isAlive()) continue;
                if (!player.getUUID().equals(s.getCommanderUuid().orElse(null))) continue;
                if (s.getSquadId() >= 1) ids.add(s.getSquadId());
            }
            for (int id : ids) {
                Vec3 center = squadCentroid(sl, player, id);
                if (center == null) center = player.position();
                squadState(id).applyHoldOrder(center, player.getYRot(), nowGT);
            }
        } else if (selectedSquad > 0) {
            Vec3 center = squadCentroid(sl, player, selectedSquad);
            if (center == null) center = player.position();
            squadState(selectedSquad).applyHoldOrder(center, player.getYRot(), nowGT);
        }
        clearPreciseStrike(selectedSquad);
        issueOrder(nowGT);
    }

    /** 小队当前质心（只统计该指挥官麾下、该小队的活士兵）；没有士兵时返回 null。 */
    @Nullable
    private static Vec3 squadCentroid(ServerLevel sl, Player player, int squadId) {
        if (squadId <= 0) return null;
        double range = GeneralConfig.armyFollowRange * 4;
        AABB box = player.getBoundingBox().inflate(range);
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive()) continue;
            if (!player.getUUID().equals(s.getCommanderUuid().orElse(null))) continue;
            if (s.getSquadId() != squadId) continue;
            sx += s.getX();
            sy += s.getY();
            sz += s.getZ();
            n++;
        }
        return n == 0 ? null : new Vec3(sx / n, sy / n, sz / n);
    }

    /**
     * 移动命令：无小队单兵 —— 同一个 applyMoveOrder（锚点 + 驻守 + 退出软驻守）。
     *   没有独立的"单兵移动令"，也没有"到达 / 超时失效"计时。
     */
    public void orderLoneMove(Player player, ServerLevel sl, AbstractArmySoldier lone,
                              Vec3 dest, long nowGT) {
        detachToLone(player, sl, lone);
        lone.markPlayerCommanded();

        double dx = dest.x - lone.getX();
        double dz = dest.z - lone.getZ();
        float yaw = player.getYRot();
        if (dx * dx + dz * dz > 0.01) {
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }

        stateForCommand(lone, this).applyMoveOrder(dest, yaw, nowGT);
        lone.setTarget(null);
        issueOrder(nowGT);
    }

    /**
     * 攻击命令：一支或多支小队进入软驻守（与无小队单兵共用 applyAttackOrder）。
     */
    public void orderAttack(Collection<Integer> squadIds, long nowGT) {
        for (int id : squadIds) squadState(id).applyAttackOrder(nowGT);
        issueOrder(nowGT);
    }

    /**
     * 跟随命令：指定小队（>0）或全选（0）跟随某个单位（玩家 / 士兵 / 首领 / 战马…）。
     *   followUuid = null → 跟随命令发出者本人（默认行为）。
     */
    public void orderFollow(int squadId, @Nullable UUID followUuid, long nowGT) {
        if (squadId == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) s.applyFollowOrder(MovementMode.FOLLOW, followUuid);
            }
        } else {
            squadState(squadId).applyFollowOrder(MovementMode.FOLLOW, followUuid);
        }
        issueOrder(nowGT);
    }

    /**
     * 攻击命令：无小队单兵 —— 与"指挥小队攻击"完全同一套状态机制：
     *   脱队 → 记录强制目标（整支敌军一起锁定）→ 软驻守 → 直接锁敌。
     *   目标死亡后由公共的强制目标清理逻辑自动恢复，没有独立的单兵攻击状态。
     */
    public void orderLoneAttack(Player player, ServerLevel sl, AbstractArmySoldier lone,
                                LivingEntity target, long nowGT) {
        detachToLone(player, sl, lone);
        lone.markPlayerCommanded();

        UUID targetArmy = AbstractArmySoldier.resolveArmyUuid(target, sl);
        if (targetArmy != null && !targetArmy.equals(player.getUUID())) {
            ArmyRelations.setHostile(player.getUUID(), targetArmy);
            markSharedHatredArmy(targetArmy, nowGT);
        } else if (targetArmy == null) {
            markSharedHatredEntity(target.getUUID(), nowGT);
        }

        SquadState ls = stateForCommand(lone, this);
        ls.setForcedTargetId(target.getUUID());
        ls.setForcedTargetGroup(expandArmyTargets(sl, target));
        ls.setAttackMode(AttackMode.COMMANDED);
        ls.applyAttackOrder(nowGT);
        lone.setTarget(target);
        issueOrder(nowGT);
    }

    /**
     * 把目标扩展成"整支军队 / 整个巡逻小队"。
     *
     *   目标是某个指挥官麾下的士兵，或指挥官本人 → 返回该指挥官 + 全部士兵的 UUID；
     *   目标是玩家 → 返回该玩家 + 附近属于他的士兵；
     *   其它目标（普通生物 / 孤儿士兵）→ 只返回目标自己。
     */
    public static Set<UUID> expandArmyTargets(Level level, @Nullable LivingEntity target) {
        Set<UUID> out = new LinkedHashSet<>();
        if (target == null) return out;
        if (!(level instanceof ServerLevel sl)) return out;

        // 目标是骑兵战马 → 等价于选中它的骑手（骑手再按军队展开）。
        //   士兵 AI 默认不把战马当攻击目标（伤害本来就会转嫁给骑手），
        //   所以命令打马 = 打骑手 + 打他所属的整支军队。
        if (target instanceof com.ohhapple.monster_conquest.entity.WarHorseEntity horse) {
            out.add(horse.getUUID());
            UUID ownerUuid = horse.getOwnerUuid();
            if (ownerUuid != null) {
                Entity owner = sl.getEntities().get(ownerUuid);
                if (owner instanceof LivingEntity le) {
                    out.addAll(expandArmyTargets(level, le));
                }
            }
            return out;
        }

        out.add(target.getUUID());
        UUID army = AbstractArmySoldier.resolveArmyUuid(target, level);
        if (army == null) return out;

        Entity leader = sl.getEntities().get(army);
        if (leader instanceof AbstractArmyCommander cmd) {
            out.add(cmd.getUUID());
            for (AbstractArmySoldier s : cmd.getSoldiers()) {
                if (s != null && s.isAlive()) out.add(s.getUUID());
            }
        } else if (leader instanceof Player p) {
            out.add(p.getUUID());
            for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class,
                    p.getBoundingBox().inflate(64.0),
                    s -> p.getUUID().equals(s.getCommanderUuid().orElse(null)))) {
                if (s.isAlive()) out.add(s.getUUID());
            }
        }
        return out;
    }

    public boolean isVolleyActive(long nowGameTime) {
        return current().isVolleyActive(nowGameTime);
    }

    public void startVolley(Vec3 target, long nowGameTime, long durationTicks) {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) applyVolley(s, target, nowGameTime, durationTicks);
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            applyVolley(s, target, nowGameTime, durationTicks);
        }
    }

    private static void applyVolley(SquadState s, Vec3 target,
                                    long nowGameTime, long durationTicks) {
        s.volleyTarget = target;
        s.volleyEndGameTime = nowGameTime + durationTicks;
    }

    public void stopVolley() {
        if (selectedSquad == 0) {
            for (int id : activeSquadIds()) {
                SquadState s = squads.get(id);
                if (s != null) {
                    s.volleyTarget = null;
                    s.volleyEndGameTime = 0L;
                }
            }
        } else {
            SquadState s = squads.computeIfAbsent(selectedSquad, k -> new SquadState());
            s.volleyTarget = null;
            s.volleyEndGameTime = 0L;
        }
    }

    @Nullable public Vec3 getVolleyTarget() { return current().volleyTarget; }
    public long getVolleyEndGameTime() { return current().volleyEndGameTime; }

    public Map<UUID, Vec3> getFormationSlots() { return current().formationSlots; }

    public boolean shouldRebuildFormation(int nowTick, FormationMode mode,
                                          @Nullable Vec3 anchor, float yaw) {
        return current().shouldRebuildFormation(nowTick, mode, anchor, yaw);
    }

    public void commitFormationCache(int nowTick, FormationMode mode,
                                     @Nullable Vec3 anchor, float yaw) {
        current().commitFormationCache(nowTick, mode, anchor, yaw);
    }

    public void invalidateFormationCache() { current().invalidateFormationCache(); }

    public void clear() {
        for (SquadState s : squads.values()) s.clear();
        // 重置为 1 分队（不是全选）
        selectedSquad = 1;
        lastConcreteSquad = 1;
        endPreciseStrike();
    }

    public void clearSquad(int squadId) {
        SquadState s = squads.get(squadId);
        if (s != null) s.clear();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("SelectedSquad", selectedSquad);
        tag.putInt("LastConcreteSquad", lastConcreteSquad);
        tag.putLong("CommandGen", commandGen);
        tag.putLong("LastOrderGT", lastOrderIssuedGT);
        tag.putInt("ControlToolSquad", controlToolSelectedSquad);
        if (controlToolSelectedArmy != null) {
            tag.putUUID("ControlToolArmy", controlToolSelectedArmy);
        }
        if (controlToolSelectedFaction != null) {
            tag.putString("ControlToolFaction", controlToolSelectedFaction.toString());
        }

        // 持久化隐藏小队号分配状态
        tag.putInt("NextHiddenSquadId", nextHiddenSquadId);
        ListTag freeIds = new ListTag();
        for (int id : freeHiddenSquadIds) {
            CompoundTag ct = new CompoundTag();
            ct.putInt("Id", id);
            freeIds.add(ct);
        }
        tag.put("FreeHiddenSquadIds", freeIds);

        ListTag list = new ListTag();
        for (Map.Entry<Integer, SquadState> e : squads.entrySet()) {
            if (e.getKey() == 0) continue;
            CompoundTag ct = e.getValue().save();
            ct.putInt("SquadId", e.getKey());
            list.add(ct);
        }
        tag.put("Squads", list);

        ListTag hatredList = new ListTag();
        for (Map.Entry<UUID, Long> e : sharedHatredArmies.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID("Army", e.getKey());
            ct.putLong("Expiry", e.getValue());
            hatredList.add(ct);
        }
        ListTag entityHatredList = new ListTag();
        for (Map.Entry<UUID, Long> e : sharedHatredEntities.entrySet()) {
            CompoundTag ct = new CompoundTag();
            ct.putUUID("Entity", e.getKey());
            ct.putLong("Expiry", e.getValue());
            entityHatredList.add(ct);
        }
        tag.put("SharedHatredEntities", entityHatredList);
        tag.put("SharedHatred", hatredList);
        return tag;
    }

    public void load(CompoundTag tag) {
        // 只保留正值队号；读到 0（全选）或缺失 → 回到 1 分队。
        //   隐藏队号（6+）会被保留。
        int savedSel = tag.getInt("SelectedSquad");
        selectedSquad = (savedSel > 0) ? savedSel : 1;

        lastConcreteSquad = tag.getInt("LastConcreteSquad");
        if (lastConcreteSquad <= 0) lastConcreteSquad = 1;

        commandGen = tag.getLong("CommandGen");
        lastOrderIssuedGT = tag.getLong("LastOrderGT");

        // 控制工具同上：正值保留（含隐藏队号），-1（单兵选择）保留，其余回 1。
        int savedCtrl = tag.getInt("ControlToolSquad");
        controlToolSelectedSquad = (savedCtrl == CONTROL_TOOL_LONE)
                ? CONTROL_TOOL_LONE
                : ((savedCtrl > 0) ? savedCtrl : 1);

        controlToolSelectedArmy = tag.hasUUID("ControlToolArmy")
                ? tag.getUUID("ControlToolArmy") : null;
        controlToolSelectedFaction = ResourceLocation.tryParse(
                tag.getString("ControlToolFaction"));

        // 恢复隐藏小队号分配状态
        nextHiddenSquadId = tag.contains("NextHiddenSquadId")
                ? tag.getInt("NextHiddenSquadId") : -1;
        freeHiddenSquadIds.clear();
        ListTag freeIds = tag.getList("FreeHiddenSquadIds", Tag.TAG_COMPOUND);
        for (int i = 0; i < freeIds.size(); i++) {
            int id = freeIds.getCompound(i).getInt("Id");
            if (id > 0) freeHiddenSquadIds.add(id);
        }

        squads.clear();
        ListTag list = tag.getList("Squads", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag ct = list.getCompound(i);
            int id = ct.getInt("SquadId");
            SquadState s = new SquadState();
            s.load(ct);
            squads.put(id, s);
        }

        occupiedSquads.clear();

        sharedHatredArmies.clear();
        ListTag hatredList = tag.getList("SharedHatred", Tag.TAG_COMPOUND);
        for (int i = 0; i < hatredList.size(); i++) {
            CompoundTag ct = hatredList.getCompound(i);
            if (ct.hasUUID("Army")) {
                sharedHatredArmies.put(ct.getUUID("Army"), ct.getLong("Expiry"));
            }
        }
        sharedHatredEntities.clear();
        ListTag entityHatredList = tag.getList("SharedHatredEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entityHatredList.size(); i++) {
            CompoundTag ct = entityHatredList.getCompound(i);
            if (ct.hasUUID("Entity")) {
                sharedHatredEntities.put(ct.getUUID("Entity"), ct.getLong("Expiry"));
            }
        }
    }

    // ===================== RTS =====================

    public boolean isRtsActive() { return rtsActive; }
    public void setRtsActive(boolean v) { this.rtsActive = v; }

    /**
     * 分配隐藏小队号：优先复用空闲号，否则从 maxVisible + 1 起找未占用号；
     *   必须跳过读档残留与附近士兵正在使用的队号，避免撞号导致整队被误指挥。
     */
    public int allocateHiddenSquadId(ServerPlayer player, ServerLevel sl) {
        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);

        // 收集所有被占用的队号（squads map + 附近活士兵的 squadId）
        Set<Integer> used = new HashSet<>(squads.keySet());
        used.remove(Integer.valueOf(0));   // 0 = "全选"占位，不算真实队号

        if (player != null && sl != null) {
            double r = 512.0;
            AABB box = new AABB(
                    player.getX() - r, player.getY() - r, player.getZ() - r,
                    player.getX() + r, player.getY() + r, player.getZ() + r);
            for (AbstractArmySoldier s : sl.getEntitiesOfClass(
                    AbstractArmySoldier.class, box,
                    s -> s.isAlive()
                            && player.getUUID().equals(s.getCommanderUuid().orElse(null)))) {
                int id = s.getSquadId();
                if (id > 0) used.add(id);
            }
        }

        // 优先复用已释放的号（前提：没被占用）
        Iterator<Integer> it = freeHiddenSquadIds.iterator();
        while (it.hasNext()) {
            int id = it.next();
            if (!used.contains(id)) {
                it.remove();
                return id;
            }
        }

        // 找一个未占用的新号
        if (nextHiddenSquadId <= maxVisible) nextHiddenSquadId = maxVisible + 1;
        while (used.contains(nextHiddenSquadId)) {
            nextHiddenSquadId++;
        }
        return nextHiddenSquadId++;
    }

    public void freeHiddenSquadId(int id) {
        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
        if (id <= maxVisible) return;
        squads.remove(id);
        freeHiddenSquadIds.add(id);
    }

    // ===================== 单兵 / 解散 =====================

    /**
     * 隐藏小队（队号 > maxPlayerSquads）只剩 1 个活士兵、且该小队处于空闲状态时，
     *   把这个士兵变为"无小队"（0）并释放队号 —— 避免单兵长期占用隐藏队号。
     *
     *   正在执行命令（还在赶路 / 有攻击目标 / 齐射中 / 精确打击中 / 软驻守）时跳过，
     *   等空闲后再解散，不会打断当前行动。
     *
     * @return 本次解散的隐藏小队数量
     */
    public int normalizeLoneHiddenSquads(ServerPlayer player, ServerLevel sl, long nowGT) {
        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
        AABB box = searchBoxAll(player);
        List<AbstractArmySoldier> mine = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null)));

        Map<Integer, List<AbstractArmySoldier>> bySquad = new HashMap<>();
        for (AbstractArmySoldier s : mine) {
            int id = s.getSquadId();
            if (id <= maxVisible) continue;
            bySquad.computeIfAbsent(id, k -> new ArrayList<>()).add(s);
        }

        int dissolved = 0;
        for (Map.Entry<Integer, List<AbstractArmySoldier>> e : bySquad.entrySet()) {
            int id = e.getKey();
            List<AbstractArmySoldier> units = e.getValue();
            if (units.size() != 1) continue;

            AbstractArmySoldier lone = units.get(0);
            if (isSquadBusy(id, lone, nowGT)) continue;

            lone.setSquadId(0);
            freeHiddenSquadId(id);
            if (selectedSquad == id) {
                setSelectedSquad(Math.max(1, Math.min(maxVisible, lastConcreteSquad)));
            }
            dissolved++;
        }
        return dissolved;
    }

    /** 该小队是否正在执行命令（用于判断能不能解散单兵隐藏队）。 */
    private boolean isSquadBusy(int squadId, AbstractArmySoldier lone, long nowGT) {
        SquadState ss = squads.get(squadId);
        if (ss != null) {
            if (ss.getForcedTarget() != null) return true;
            if (ss.getVolleyTarget() != null && nowGT < ss.getVolleyEndGameTime()) return true;
            if (ss.isSoftStation()) return true;
            Vec3 anchor = ss.getFormationAnchor();
            if (ss.getMovement() == MovementMode.STATION && anchor != null
                    && lone.position().distanceToSqr(anchor) > 16.0) {
                return true;   // 还在赶往驻守点
            }
            if (preciseStrikeSquads.contains(squadId)) return true;
        }
        LivingEntity target = lone.getTarget();
        return target != null && target.isAlive();
    }

    /**
     * 编入附近单兵：把玩家附近"无小队"（squadId = 0）的自己人编入当前命令小队
     *   （r 轮盘左侧选中的小队；全选时用最近一次选中的具体小队）。
     *
     * @return 编入的士兵数量
     */
    public int enlistNearbyLoneSoldiers(ServerPlayer player, double radius) {
        if (!(player.level() instanceof ServerLevel sl)) return 0;
        int targetSquad = squadForNewSoldier();
        if (targetSquad <= 0) targetSquad = 1;

        AABB box = player.getBoundingBox().inflate(radius);
        List<AbstractArmySoldier> lone = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == 0);
        for (AbstractArmySoldier s : lone) {
            assignSquad(s, targetSquad);
        }
        if (!lone.isEmpty()) {
            squadState(targetSquad);
        }
        return lone.size();
    }

    /**
     * 解散当前小队：把当前选中的小队里的士兵全部变为无小队（0），
     *   并清掉该小队的状态（隐藏队号释放回号池）。
     *
     * @return 解散的士兵数；-1 = 当前左侧选的是"全选"，需要先选一个具体小队
     */
    public int dissolveCurrentSquad(ServerPlayer player, ServerLevel sl) {
        if (selectedSquad == 0) return -1;
        int squadId = selectedSquad;

        AABB box = searchBoxAll(player);
        List<AbstractArmySoldier> units = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId);
        for (AbstractArmySoldier s : units) {
            s.setSquadId(0);
            s.clearLoneState();
        }

        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
        if (squadId > maxVisible) {
            freeHiddenSquadId(squadId);
            setSelectedSquad(Math.max(1, Math.min(maxVisible, lastConcreteSquad)));
        } else {
            squads.remove(squadId);
        }
        return units.size();
    }

    public Set<Integer> getFreeHiddenSquadIds() { return freeHiddenSquadIds; }

    /**
     * RTS 框选：只记录选中的士兵 UUID，不改队号、不选整队。
     */
    public int applyRtsBoxSelection(ServerPlayer player,
                                    List<UUID> soldierUuids,
                                    boolean additive) {
        if (soldierUuids == null || soldierUuids.isEmpty()) {
            if (!additive) rtsSelectedSoldiers.clear();
            return 0;
        }
        if (!(player.level() instanceof ServerLevel sl)) return 0;

        List<AbstractArmySoldier> units = new ArrayList<>();
        for (UUID uuid : soldierUuids) {
            Entity e = sl.getEntities().get(uuid);
            if (e instanceof AbstractArmySoldier s && s.isAlive()
                    && player.getUUID().equals(s.getCommanderUuid().orElse(null))) {
                units.add(s);
            }
        }
        if (units.isEmpty()) {
            if (!additive) rtsSelectedSoldiers.clear();
            return 0;
        }

        if (!additive) rtsSelectedSoldiers.clear();
        for (AbstractArmySoldier s : units) rtsSelectedSoldiers.add(s.getUUID());
        return units.size();
    }

    /**
     * 点击士兵：
     *   · 有小队（squadId > 0）→ 选中该队全部活士兵（"点选整队"语义）；
     *   · 无小队（squadId == 0）→ 只选中被点击的这名士兵（soldierUuid）。
     *   骑马时点击战马同样解析到骑手士兵后走这里（不要忽略战马）。
     */
    public int applyRtsSquadClick(ServerPlayer player, int squadId, boolean additive,
                                  @Nullable UUID soldierUuid) {
        if (!(player.level() instanceof ServerLevel sl)) return 0;

        // 以"被点击的士兵"为准（客户端传来的队号可能已过期）：
        //   不是自己的士兵 → 无操作（避免误选到自己同号的队）
        if (soldierUuid != null) {
            Entity e = sl.getEntities().get(soldierUuid);
            if (!(e instanceof AbstractArmySoldier s) || !s.isAlive()
                    || !player.getUUID().equals(s.getCommanderUuid().orElse(null))) {
                return 0;
            }
            // 无小队 → 只选中这一名士兵；有队 → 选中整队
            if (s.getSquadId() <= 0) {
                if (!additive) rtsSelectedSoldiers.clear();
                rtsSelectedSoldiers.add(s.getUUID());
                return 1;
            }
            squadId = s.getSquadId();
        }
        if (squadId <= 0) return 0;

        final int filterSquad = squadId;
        AABB box = searchBoxAll(player);
        List<AbstractArmySoldier> units = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == filterSquad);
        if (units.isEmpty()) return 0;
        if (!additive) rtsSelectedSoldiers.clear();
        for (AbstractArmySoldier s : units) rtsSelectedSoldiers.add(s.getUUID());
        return units.size();
    }

    /**
     * RTS 双击全选同类士兵。
     *
     *   mode 0 = 同种：同一实体种类（例如所有僵尸士兵，不分等级 / 队号）；
     *   mode 1 = 同兵种：同一兵种类别（近战 / 远程 / 双形态 / 投掷 / 枪手，
     *                    枪手再按枪械种类细分）。
     *
     * @return 选中的士兵数量
     */
    public int applyRtsSelectSimilar(ServerPlayer player, UUID clickedUuid, int mode) {
        if (!(player.level() instanceof ServerLevel sl)) return 0;
        Entity e = sl.getEntities().get(clickedUuid);
        if (!(e instanceof AbstractArmySoldier clicked) || !clicked.isAlive()) return 0;
        if (!player.getUUID().equals(clicked.getCommanderUuid().orElse(null))) return 0;

        AABB box = searchBoxAll(player);
        List<AbstractArmySoldier> units = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && isSimilarSoldier(clicked, s, mode));

        rtsSelectedSoldiers.clear();
        for (AbstractArmySoldier s : units) rtsSelectedSoldiers.add(s.getUUID());
        return units.size();
    }

    /** 是否属于"同种 / 同兵种"。 */
    private static boolean isSimilarSoldier(AbstractArmySoldier clicked,
                                            AbstractArmySoldier other, int mode) {
        if (mode == 1) {
            if (other.getRole() != clicked.getRole()) return false;
            // 枪手按枪械种类细分：步枪 / 霰弹 / 狙击 / 火箭各自一档
            if (clicked.getRole().isGunner() && other.getType() != clicked.getType()) return false;
            return true;
        }
        return other.getType() == clicked.getType();
    }

    /** AABB —— 覆盖整个已加载区域（RTS 相机可能远离玩家实体）。 */
    private static AABB searchBoxAll(Entity player) {
        double r = 512.0;
        return new AABB(
                player.getX() - r, player.getY() - r, player.getZ() - r,
                player.getX() + r, player.getY() + r, player.getZ() + r);
    }

    public int countLivingSoldiersInSquad(ServerPlayer player, ServerLevel sl, int squadId) {
        AABB box = searchBoxAll(player);
        return sl.getEntitiesOfClass(AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId).size();
    }

    /** 收集当前选中的活士兵 UUID（发客户端渲染描边）。 */
    public Set<UUID> collectRtsSelectedSoldierUuids(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel sl)) return Set.of();
        if (rtsSelectedSoldiers.isEmpty()) return Set.of();
        Set<UUID> out = new LinkedHashSet<>();
        for (UUID uuid : rtsSelectedSoldiers) {
            Entity e = sl.getEntities().get(uuid);
            if (e instanceof AbstractArmySoldier s && s.isAlive()) out.add(uuid);
        }
        rtsSelectedSoldiers.retainAll(out);
        return out;
    }

    /**
     * 友方判定（RTS 右键"跟随 / 攻击"分流用）：
     *   · 自己军队的单位（士兵 / 首领 / 自己的战马）；
     *   · 同阵营或"我方阵营的结盟阵营"的单位（FactionAlly 为单向：以我方视角判定，
     *     与士兵 AI 的友军判定方向一致）；
     *   · 玩家 → 玩家阵营；战马 → 骑手 / 主人的阵营。
     */
    private static boolean isFriendlyTarget(ServerPlayer player, LivingEntity target,
                                            ServerLevel sl) {
        UUID myArmy = player.getUUID();
        UUID targetArmy = AbstractArmySoldier.resolveArmyUuid(target, sl);
        if (targetArmy != null && targetArmy.equals(myArmy)) return true;

        ResourceLocation myFac = FactionIds.player(player.getUUID());
        ResourceLocation theirFac = resolveTargetFaction(target, sl);
        if (myFac == null || theirFac == null) return false;
        return myFac.equals(theirFac) || FactionAlly.isAlly(myFac, theirFac);
    }

    /** 目标阵营：首领 / 士兵取自己的；玩家取玩家阵营；战马 → 骑手 / 主人的阵营。 */
    @Nullable
    private static ResourceLocation resolveTargetFaction(LivingEntity target, ServerLevel sl) {
        if (target instanceof AbstractArmyCommander c) return c.getFactionId();
        if (target instanceof AbstractArmySoldier s) return s.getFactionId();
        if (target instanceof Player p) return FactionIds.player(p.getUUID());
        if (target instanceof WarHorseEntity horse) {
            LivingEntity controller = horseController(horse, sl);
            return controller == null ? null : resolveTargetFaction(controller, sl);
        }
        return null;
    }

    /** 战马的控制者：优先骑手，其次 owner（主人可能是玩家，也可能是骑它的士兵）。 */
    @Nullable
    private static LivingEntity horseController(WarHorseEntity horse, ServerLevel sl) {
        Entity rider = horse.getFirstPassenger();
        if (rider instanceof LivingEntity le && le.isAlive()) return le;
        UUID owner = horse.getOwnerUuid();
        if (owner == null) return null;
        Entity e = sl.getEntities().get(owner);
        return (e instanceof LivingEntity le && le.isAlive()) ? le : null;
    }

    /**
     * RTS 右键命令。
     *
     * 1. 收集选中活士兵
     * 2. 右键友方单位 → 跟随；其余 → 攻击 / 移动
     * 3. 决定命令目标 squadId（**此时才分组建队**）：
     *      · 唯一完整小队 → 用那队号
     *      · ≥2 完整小队 → 合并成 1 队
     *      · 部分抽取 → 新建隐藏队
     * 4. 对目标小队下令
     */
    public int applyRtsCommand(ServerPlayer player,
                               @Nullable LivingEntity target,
                               Vec3 dest) {
        if (rtsSelectedSoldiers.isEmpty()) return 0;
        if (!(player.level() instanceof ServerLevel sl)) return 0;
        long nowGT = sl.getGameTime();

        List<AbstractArmySoldier> units = new ArrayList<>();
        for (UUID uuid : rtsSelectedSoldiers) {
            Entity e = sl.getEntities().get(uuid);
            if (e instanceof AbstractArmySoldier s && s.isAlive()
                    && player.getUUID().equals(s.getCommanderUuid().orElse(null))) {
                units.add(s);
            }
        }
        if (units.isEmpty()) return 0;

        // 和平模式：不能下达攻击命令（不能有敌意 / 仇恨）
        if (target != null && !isFriendlyTarget(player, target, sl)
                && com.ohhapple.monster_conquest.util.PeacefulUtil.isPeaceful(player)) {
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                            "msg.monster_conquest.peaceful_no_attack"), true);
            return 0;
        }

        // 右键友方单位 → 跟随它（自己 / 结盟阵营的玩家、士兵、首领、战马……）。
        //   判定与士兵 AI 的友军判定同一方向：同军队 / 同阵营 / 我方阵营的结盟
        //   （FactionAlly.isAlly(我的阵营, 对方阵营)）。
        //   非友方（含普通生物、敌对玩家）：不跟随，交给下面的攻击逻辑。
        if (target != null && isFriendlyTarget(player, target, sl)) {
            // 点战马 → 跟随其骑手 / 主人（战马可能被销毁，跟随单位更稳）
            LivingEntity followUnit = (target instanceof WarHorseEntity horse)
                    ? horseController(horse, sl) : target;
            if (followUnit == null) followUnit = target;

            UUID followUuid = followUnit.getUUID().equals(player.getUUID())
                    ? null : followUnit.getUUID();
            if (units.size() == 1) {
                // 单个士兵：与移动 / 攻击完全一致 —— 脱队后用个人状态
                AbstractArmySoldier lone = units.get(0);
                detachToLone(player, sl, lone);
                lone.markPlayerCommanded();
                stateForCommand(lone, this)
                        .applyFollowOrder(MovementMode.FOLLOW, followUuid);
                issueOrder(nowGT);
            } else {
                int sid = resolveCommandSquadId(player, sl, units);
                // 与"跟随我"共用同一套 SquadState.applyFollowOrder
                orderFollow(sid, followUuid, nowGT);
            }
            rtsMoveMarker = null;
            rtsMoveMarkerGT = 0L;
            return units.size();
        }

        // 单个士兵的命令：不占用小队号 —— 脱离原小队，用"个人状态"执行
        //   （和小队完全同一套入口：orderLoneMove / orderLoneAttack）。
        if (units.size() == 1) {
            AbstractArmySoldier lone = units.get(0);

            if (target != null && target.isAlive()) {
                orderLoneAttack(player, sl, lone, target, nowGT);
                rtsMoveMarker = null;
                rtsMoveMarkerGT = 0L;
                return 1;
            }

            orderLoneMove(player, sl, lone, dest, nowGT);
            rtsMoveMarker = dest;
            rtsMoveMarkerGT = nowGT;
            return 1;
        }

        int targetSquadId = resolveCommandSquadId(player, sl, units);

        if (target != null && target.isAlive()) {
            UUID targetArmy = AbstractArmySoldier.resolveArmyUuid(target, sl);
            if (targetArmy != null && !targetArmy.equals(player.getUUID())) {
                ArmyRelations
                        .setHostile(player.getUUID(), targetArmy);
                markSharedHatredArmy(targetArmy, nowGT);
            } else if (targetArmy == null) {
                markSharedHatredEntity(target.getUUID(), nowGT);
            }

            int count = 0;
            for (AbstractArmySoldier s : units) {
                if (s.isAttackStopped()) continue;
                s.cancelManualMoveOrder();   // 攻击命令覆盖旧的移动命令
                s.setTarget(target);
                count++;
            }
            if (count > 0) {
                Set<Integer> affected = new HashSet<>();
                affected.add(targetSquadId);
                orderAttack(affected, nowGT);
                startPreciseStrike(target.getUUID(),
                        expandArmyTargets(sl, target),
                        affected, nowGT, 36000L);
            }
            rtsMoveMarker = null;
            rtsMoveMarkerGT = 0L;
            return count;
        }

        Vec3 origin = player.position();
        double dx = dest.x - origin.x;
        double dz = dest.z - origin.z;
        float yaw = player.getYRot();
        if (dx * dx + dz * dz > 0.01) {
            yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        }
        orderMove(targetSquadId, dest, yaw, nowGT);
        rtsMoveMarker = dest;
        rtsMoveMarkerGT = nowGT;
        return units.size();
    }

    /** 根据当前选中决定命令目标 squadId（部分抽取时新建队）。 */
    private int resolveCommandSquadId(ServerPlayer player, ServerLevel sl,
                                      List<AbstractArmySoldier> units) {
        Map<Integer, List<AbstractArmySoldier>> bySquad = new LinkedHashMap<>();
        for (AbstractArmySoldier s : units) {
            bySquad.computeIfAbsent(s.getSquadId(), k -> new ArrayList<>()).add(s);
        }

        // 是否每支被碰到的小队都完整选中
        boolean allFull = true;
        for (var entry : bySquad.entrySet()) {
            int total = countLivingSoldiersInSquad(player, sl, entry.getKey());
            if (total != entry.getValue().size()) { allFull = false; break; }
        }

        if (allFull && bySquad.size() == 1) {
            // 唯一完整小队 → 用那队号
            int id = bySquad.keySet().iterator().next();
            if (id != 0) return id;
            // 全是无小队士兵（单兵命令已在 applyRtsCommand 里提前处理）：
            //   这里只可能是 ≥2 人 → 走下面的"新建隐藏小队"分支
        }
        if (allFull && bySquad.size() >= 2) {
            // ≥2 完整小队 → 合并
            Integer reuse = null;
            int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
            for (int id : bySquad.keySet()) {
                if (id > maxVisible) { reuse = id; break; }
            }
            int target = (reuse != null)
                    ? reuse
                    : allocateHiddenSquadId(player, sl);   // 带参版本
            for (AbstractArmySoldier s : units) assignSquad(s, target);
            for (int oldId : bySquad.keySet()) {
                if (oldId == target) continue;
                if (countLivingSoldiersInSquad(player, sl, oldId) == 0) {
                    freeHiddenSquadId(oldId);
                }
            }
            return target;
        }
        // 部分抽取（含只选 1 个）→ 新建隐藏队
        int target = allocateHiddenSquadId(player, sl);        // 带参版本
        for (AbstractArmySoldier s : units) assignSquad(s, target);
        for (int oldId : bySquad.keySet()) {
            if (oldId == target) continue;
            if (countLivingSoldiersInSquad(player, sl, oldId) == 0) {
                freeHiddenSquadId(oldId);
            }
        }
        return target;
    }

    /** 把士兵编入小队，同时清掉它的个人指令状态。 */
    private static void assignSquad(AbstractArmySoldier s, int squadId) {
        s.setSquadId(squadId);
        s.clearLoneState();
        // 编入小队 = 已归属玩家指挥：之后即使脱离小队也保持"已被指挥"状态
        s.markPlayerCommanded();
    }

    /**
     * 把士兵脱离原小队变成"无小队单兵"（个人命令用，不占用小队号）。
     *   若原小队是隐藏队且因此空了 → 释放队号。
     */
    public void detachToLone(net.minecraft.world.entity.player.Player player,
                             ServerLevel sl, AbstractArmySoldier soldier) {
        int old = soldier.getSquadId();
        if (old == 0) return;
        soldier.setSquadId(0);
        soldier.clearLoneState();
        // 在玩家小队里服役过 → 保持"已归属玩家指挥"
        soldier.markPlayerCommanded();

        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
        if (old <= maxVisible) return;

        boolean hasOther = !sl.getEntitiesOfClass(AbstractArmySoldier.class, searchBoxAll(player),
                s -> s.isAlive() && s != soldier
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == old).isEmpty();
        if (!hasOther) {
            freeHiddenSquadId(old);
        }
    }

    /**
     * 轮盘"选中当前小队"：把准星所指实体所属小队并入当前选中分队；
     *   目标队无活士兵时直接迁移，有活士兵时两队互换队号与 SquadState。
     *
     * @return 迁移的士兵数；-1 = 目标无效；0 = 无变化
     */
    public int pullSquadIntoSelected(ServerPlayer player, Entity target, ServerLevel sl) {
        int targetSquad = (selectedSquad == 0) ? 1 : selectedSquad;
        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
        if (targetSquad < 1 || targetSquad > maxVisible) return -1;

        int sourceSquad = resolveSquadIdFromEntity(target);
        if (sourceSquad <= 0) return -1;
        if (sourceSquad == targetSquad) return 0;

        AABB box = searchBoxAll(player);

        List<AbstractArmySoldier> srcUnits = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == sourceSquad);
        if (srcUnits.isEmpty()) return -1;

        List<AbstractArmySoldier> tgtUnits = sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && player.getUUID().equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == targetSquad);

        if (tgtUnits.isEmpty()) {
            // —— 目标队为空：直接迁移 ——
            for (AbstractArmySoldier s : srcUnits) s.setSquadId(targetSquad);

            SquadState srcState = squads.remove(sourceSquad);
            squads.put(targetSquad, srcState != null ? srcState : new SquadState());

            // 源队若为隐藏队 → 释放号
            if (sourceSquad > maxVisible) {
                freeHiddenSquadId(sourceSquad);
            }
            return srcUnits.size();
        }

        // —— 目标队有士兵：两队互换队号 + SquadState ——
        for (AbstractArmySoldier s : srcUnits) s.setSquadId(targetSquad);
        for (AbstractArmySoldier s : tgtUnits) s.setSquadId(sourceSquad);

        SquadState srcState = squads.get(sourceSquad);
        SquadState tgtState = squads.get(targetSquad);
        squads.put(sourceSquad, tgtState != null ? tgtState : new SquadState());
        squads.put(targetSquad, srcState != null ? srcState : new SquadState());

        // 互换后源队仍有士兵（原目标队的人）→ 隐藏队号继续保留
        return srcUnits.size();
    }

    /** 从实体解析所属 squadId（战马 → 取骑手）。 */
    private static int resolveSquadIdFromEntity(Entity e) {
        if (e instanceof AbstractArmySoldier s) return s.getSquadId();
        if (e instanceof com.ohhapple.monster_conquest.entity.WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            if (rider instanceof AbstractArmySoldier rs) return rs.getSquadId();
        }
        return 0;
    }

    /** RTS 按键指令：对选中士兵所在的每个小队应用动作。 */
    public int applyRtsOrder(ServerPlayer player, int action) {
        if (rtsSelectedSoldiers.isEmpty()) return 0;
        if (!(player.level() instanceof ServerLevel sl)) return 0;

        Set<Integer> squads = new HashSet<>();
        List<AbstractArmySoldier> loneUnits = new ArrayList<>();
        for (UUID uuid : rtsSelectedSoldiers) {
            Entity e = sl.getEntities().get(uuid);
            if (e instanceof AbstractArmySoldier s && s.isAlive()) {
                // 无小队单兵写自己的个人状态（与小队共用同一套状态机）
                if (s.getSquadId() == 0) loneUnits.add(s);
                else squads.add(s.getSquadId());
            }
        }
        if (squads.isEmpty() && loneUnits.isEmpty()) return 0;

        for (int sid : squads) {
            applyRtsOrderTo(squadState(sid), action);
        }
        for (AbstractArmySoldier s : loneUnits) {
            applyRtsOrderTo(s.loneState(), action);
        }
        return squads.size() + loneUnits.size();
    }

    /** 单个 SquadState 上的 RTS 策略（骑乘模式 / 阵型）。 */
    private static void applyRtsOrderTo(SquadState ss, int action) {
        switch (action) {
            case 0 -> ss.setRideMode(RideMode.FORCED_CAVALRY);
            case 1 -> ss.setRideMode(RideMode.FORCED_INFANTRY);
            case 2 -> ss.setRideMode(RideMode.AUTO);
            case 10 -> applyFormation(ss, FormationMode.LINE);
            case 11 -> applyFormation(ss, FormationMode.SHIELD_WALL);
            case 12 -> applyFormation(ss, FormationMode.WEDGE);
            case 13 -> applyFormation(ss, FormationMode.SKIRMISH);
            case 14 -> applyFormation(ss, FormationMode.FREE);
        }
    }

    public void cleanupEmptyHiddenSquads(ServerPlayer player) {
        int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
        if (squads.size() <= maxVisible && freeHiddenSquadIds.isEmpty()) return;
        if (!(player.level() instanceof ServerLevel sl)) return;

        Set<Integer> hidden = new HashSet<>();
        for (int id : squads.keySet()) if (id > maxVisible) hidden.add(id);
        hidden.addAll(freeHiddenSquadIds);

        for (int id : hidden) {
            if (countLivingSoldiersInSquad(player, sl, id) == 0) {
                freeHiddenSquadId(id);
            }
        }
    }

    @Nullable public Vec3 getRtsMoveMarker() { return rtsMoveMarker; }

    /** 清空 RTS 移动标记（黄框）。 */
    public void clearRtsMoveMarker() {
        rtsMoveMarker = null;
        rtsMoveMarkerGT = 0L;
    }

    /** 清理过期的移动标记（黄框）；返回本次是否刚好清掉，供上层给客户端补一次同步。 */
    public boolean tickRtsMarker(long nowGT) {
        if (rtsMoveMarker == null) return false;
        if (nowGT - rtsMoveMarkerGT > RTS_MARKER_LIFETIME) {
            rtsMoveMarker = null;
            rtsMoveMarkerGT = 0L;
            return true;
        }
        return false;
    }

    public void settleSquadCentroidIfIdle(ServerLevel sl, UUID owner,
                                          int squadId, Vec3 around) {
        SquadState ss = squadState(squadId);
        if (ss.getMovement() != MovementMode.STATION) return;
        if (ss.getFormationAnchor() != null) return;

        double range = GeneralConfig.armyFollowRange * 4;
        AABB box = new AABB(
                around.x - range, around.y - range, around.z - range,
                around.x + range, around.y + range, around.z + range);

        double sx = around.x, sy = around.y, sz = around.z;
        int count = 1;
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(
                AbstractArmySoldier.class, box,
                s -> s.isAlive()
                        && owner.equals(s.getCommanderUuid().orElse(null))
                        && s.getSquadId() == squadId)) {
            sx += s.getX(); sy += s.getY(); sz += s.getZ();
            count++;
        }
        Vec3 centroid = new Vec3(sx / count, sy / count, sz / count);
        ss.setFormationAnchor(centroid, 0f, sl.getGameTime());
    }
}
