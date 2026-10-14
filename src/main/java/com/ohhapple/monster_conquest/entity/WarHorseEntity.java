package com.ohhapple.monster_conquest.entity;

import com.ohhapple.monster_conquest.config.CombatConfig;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class WarHorseEntity extends AbstractHorse {

    /** 非玩家骑手（士兵 / 首领 / 巡逻队长）的骑乘高度。上下马空间判定也用它。 */
    public static final double RIDER_HEIGHT = 0.8;
    private static final double GRAVITY = 0.08;

    @Nullable private UUID ownerUuid;
    /** 玩家用马哨召唤的战马：无敌、无伤害传导、下马立即销毁。 */
    private boolean playerMount = false;
    private int noRiderTicks = 0;
    private boolean charging = false;
    private int passThroughTicks = 0;
    private Vec3 passThroughDir = Vec3.ZERO;

    @Nullable private Float lockedYaw = null;

    private int lastHurtTick = -10000;
    /** NPC 骑兵时，战马受到的伤害按此比例转嫁给骑手。 */
    private static final float RIDER_DAMAGE_TRANSFER = 0.3f;

    public void setLockedYaw(@Nullable Float yaw) { this.lockedYaw = yaw; }
    @Nullable public Float getLockedYaw() { return lockedYaw; }

    public WarHorseEntity(EntityType<? extends AbstractHorse> type, Level level) {
        super(type, level);
        this.setTamed(true);
        this.setPersistenceRequired();
        if (getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation gpn) {
            gpn.setCanOpenDoors(true);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return AbstractHorse.createBaseHorseAttributes()
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.4D)
                .add(Attributes.JUMP_STRENGTH, 0.7D)
                .add(Attributes.ARMOR, 0.0D);
    }

    public void setOwner(LivingEntity owner) {
        this.ownerUuid = owner.getUUID();
        this.setTamed(true);
        this.setOwnerUUID(owner.getUUID());
    }

    public boolean isPlayerMount() { return playerMount; }
    public void setPlayerMount(boolean value) { this.playerMount = value; }

    public void setCharging(boolean charging) { this.charging = charging; }
    public boolean isCharging() { return charging; }
    public boolean isPassingThrough() { return passThroughTicks > 0; }

    /** 骑兵穿越敌人时保持无碰撞的默认时长（tick）。 */
    public static final int DEFAULT_PASS_THROUGH_TICKS = 5;

    /** 使用默认时长开始穿越。 */
    public void startPassThrough(Vec3 direction) {
        startPassThrough(direction, DEFAULT_PASS_THROUGH_TICKS);
    }

    public void startPassThrough(Vec3 direction, int ticks) {
        if (direction.lengthSqr() < 0.001) return;
        this.passThroughDir = direction.normalize();
        this.passThroughTicks = Math.max(ticks, this.passThroughTicks);
    }

    public void setPassThroughTicks(int ticks) { this.passThroughTicks = ticks; }
    public int getPassThroughTicks() { return passThroughTicks; }

    /** 玩家骑乘时不限制转头角度；NPC 骑手限制 12°。 */
    private boolean isPlayerRidden() {
        return playerMount && this.getFirstPassenger() instanceof Player;
    }

    @Override public int getMaxHeadYRot() { return isPlayerRidden() ? 360 : 12; }
    @Override public int getMaxHeadXRot() { return isPlayerRidden() ? 90 : 12; }
    @Override public int getInventoryColumns() { return 0; }

    /** 骑乘高度：玩家用实体类型登记的挂点，其他骑手用 {@link #RIDER_HEIGHT}。 */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dims, float scale) {
        if (passenger instanceof Player) {
            return super.getPassengerAttachmentPoint(passenger, dims, scale);
        }
        return new Vec3(0.0, RIDER_HEIGHT, 0.0);
    }

    // 玩家骑乘的转向交给 AbstractHorse.tickRidden（保留朝向插值）

    // ===================== 受伤动画抑制 =====================

    /** 忽略受伤事件（ID 2），不播放抬头/受伤动画。 */
    @Override
    public void handleEntityEvent(byte id) {
        if (id == 2) return; // 屏蔽受伤动画
        super.handleEntityEvent(id);
    }

    /** 没有受伤音效。 */
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return null; }

    /** 没有死亡音效（可选，也抑制）。 */
    @Override
    protected SoundEvent getDeathSound() { return null; }

    // ===================== travel =====================
    @Override
    public void travel(Vec3 input) {
        if (this.level().isClientSide) { super.travel(input); return; }

        if (this.isInWater()) {
            if (charging) charging = false;
            if (passThroughTicks > 0) passThroughTicks = 0;

            super.travel(input);

            BlockPos footPos = this.blockPosition();
            boolean footInWater = this.level().getBlockState(footPos)
                    .getFluidState().is(FluidTags.WATER);
            if (footInWater) {
                Vec3 dm = this.getDeltaMovement();
                if (dm.y < 0.03) {
                    this.setDeltaMovement(dm.x, dm.y + 0.05, dm.z);
                    this.hasImpulse = true;
                    this.fallDistance = 0;
                }
            }

            if (this.getFirstPassenger() instanceof LivingEntity rider) {
                // 骑手还有没走完的移动指令（编队 / 指定点等）→ 水里也交给导航驱动，
                //   不能再"直接游向玩家 / 首领"：两边目标不同会让马原地转圈。
                boolean riderHasOrder = rider instanceof Mob riderMob
                        && riderMob.getNavigation() != null
                        && !riderMob.getNavigation().isDone();
                if (riderHasOrder) {
                    return;
                }
                Vec3 goal = null;
                double stopSqr = 9.0;

                if (rider instanceof AbstractArmySoldier s) {
                    LivingEntity t = s.getTarget();
                    if (t != null && t.isAlive()) { goal = t.position(); stopSqr = 9.0; }
                    else {
                        LivingEntity leader = s.getLeaderEntity();
                        if (leader != null && leader.isAlive() && s.distanceToSqr(leader) > 16.0) {
                            goal = leader.position();
                            stopSqr = 4.0;
                        }
                    }
                } else if (rider instanceof AbstractArmyCommander c) {
                    LivingEntity t = c.getCombatTarget();
                    if (t != null && t.isAlive()) { goal = t.position(); stopSqr = 9.0; }
                }

                if (goal != null) {
                    double gdx = goal.x - this.getX();
                    double gdz = goal.z - this.getZ();
                    double hSqr = gdx * gdx + gdz * gdz;
                    if (hSqr > stopSqr) {
                        double h = Math.sqrt(hSqr);
                        double step = 0.08;
                        this.move(MoverType.SELF, new Vec3(gdx / h * step, 0, gdz / h * step));
                        float yaw = (float) Math.toDegrees(Math.atan2(-gdx / h, gdz / h));
                        this.setYRot(yaw);
                        this.yBodyRot = yaw;
                        this.setYHeadRot(yaw);
                    }
                }
            }
            return;
        }

        // 冲锋 / 穿越：手动驱动方向 + 速度
        if (passThroughTicks > 0 || charging) {
            Vec3 dm = this.getDeltaMovement();
            double speed = Math.max(dm.horizontalDistance(), 0.35);
            double vy = dm.y - GRAVITY;
            if (this.onGround() && vy < 0) vy = 0;

            if (dm.horizontalDistanceSqr() > 0.0025) {
                float yaw = (float) (Math.atan2(dm.z, dm.x) * 180.0 / Math.PI) - 90.0f;
                this.setYRot(yaw);
                this.yBodyRot = yaw;
                this.yHeadRot = yaw;
            }

            if (passThroughTicks > 0) {
                this.setDeltaMovement(passThroughDir.x * speed, vy, passThroughDir.z * speed);
                pushAwayOverlappingEnemiesContinuous();
            } else {
                this.setDeltaMovement(dm.x, vy, dm.z);
            }
            this.move(MoverType.SELF, this.getDeltaMovement());
            return;
        }

        // 非冲锋：完全走原版。
        //   速度 = MOVEMENT_SPEED 属性（= 骑手类里的马速数值，写多少就是多少）
        //           × navigation.speedModifier（统一 1.0）
        //   归队 / 跟随 / 行军全部走这里。
        super.travel(input);
    }


    // ===================== tick =====================
    @Override
    public void tick() {
        super.tick();

        // 每 tick 清零受伤时间，避免受伤动画残留
        this.hurtTime = 0;
        this.hurtDuration = 0;
        // 不要把 yHeadRotO 设成 yHeadRot：
        //   渲染时头部朝向是按 (yHeadRotO → yHeadRot) 插值的，
        //   每 tick 把两者对齐等于取消插值 → 玩家骑马转头时马头一卡一卡。
        //   （受伤动画已由上面两行抑制，不需要动头部朝向历史值。）

        if (level().isClientSide) return;

        // 只在非冲锋、非穿越时执行马之间分离。
        //   冲锋时马之间的防重叠由 controlMount 的虚拟瞄准点负责，
        //   这里的 deltaMovement 推挤会被下一 tick 的 controlMount 覆盖，
        //   反而在未覆盖时（掉头 / 站桩）残留成横向速度 → 视觉上"横滑"。
        if (tickCount % 4 == 0 && !isPassingThrough() && !charging) {
            applyHorseSeparation();
        }

        // 每 40 tick 同步一次坐骑速度：以骑手（士兵 / 首领）类里的马速数值为准。
        if (this.tickCount % 40 == 0) {
            double target = resolveOwnerHorseSpeed();
            var spd = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (spd != null && Math.abs(spd.getBaseValue() - target) > 1.0e-4) {
                spd.setBaseValue(target);
            }
        }

        if (passThroughTicks > 0) {
            passThroughTicks--;
            if (passThroughTicks == 0) {
                pushAwayOverlappingEnemiesContinuous();
                Vec3 forward = passThroughDir;
                if (forward.lengthSqr() < 0.001) {
                    float yaw = this.getYRot() * ((float) Math.PI / 180f);
                    forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                }
                forward = forward.normalize();
                this.move(MoverType.SELF, new Vec3(forward.x * 0.5, 0, forward.z * 0.5));
            }
        }

        if (ownerUuid != null) {
            Entity owner = null;
            if (level() instanceof ServerLevel sl) owner = sl.getEntities().get(ownerUuid);
            if (owner == null || !owner.isAlive()) { this.discard(); return; }
            if (this.getControllingPassenger() == null && this.getFirstPassenger() == null) {
                // 玩家的战马：下马立即销毁
                if (playerMount) { this.discard(); return; }
                noRiderTicks++;
                if (noRiderTicks > 100) this.discard();
            } else {
                noRiderTicks = 0;
            }
        }

        if (lockedYaw != null) {
            float yaw = lockedYaw;
            this.setYRot(yaw);
            this.yRotO = yaw;
            this.yBodyRot = yaw;
            this.yBodyRotO = yaw;
            this.setYHeadRot(yaw);
            this.yHeadRotO = yaw;
        }

        tickRegen();
    }

    private void tickRegen() {
        if (!CombatConfig.enableOutOfCombatRegen) return;
        if (this.getHealth() >= this.getMaxHealth()) return;
        if (this.tickCount - lastHurtTick < CombatConfig.outOfCombatRegenDelay) return;

        LivingEntity rider = null;
        Entity p = getControllingPassenger();
        if (!(p instanceof LivingEntity)) p = getFirstPassenger();
        if (p instanceof LivingEntity le) rider = le;

        if (rider != null) {
            if (rider instanceof AbstractArmySoldier s) {
                if (s.getTarget() != null && s.getTarget().isAlive()) return;
            } else if (rider instanceof AbstractArmyCommander c) {
                if (c.getCombatTarget() != null) return;
            }
        }

        if (this.tickCount % CombatConfig.outOfCombatRegenInterval == 0) {
            this.heal(Math.max(1.0f, this.getMaxHealth() * 0.05f));
        }
    }

    /**
     * 马之间的软分离。
     *   马 isPushable=false，原版不会互推 → 骑兵会重叠。
     *   半径按 (selfW + otherW)/2 + 0.1 动态计算，两匹马至少要间隔 1.5 格。
     */
    private void applyHorseSeparation() {
        AABB box = getBoundingBox().inflate(1.5, 0.5, 1.5);
        List<WarHorseEntity> nearby = level().getEntitiesOfClass(
                WarHorseEntity.class, box,
                h -> h != this && h.isAlive() && isSameArmyHorse(h));
        if (nearby.isEmpty()) return;

        double selfW = getBbWidth();
        double pushX = 0, pushZ = 0;
        int count = 0;
        for (WarHorseEntity other : nearby) {
            double minDist = (selfW + other.getBbWidth()) * 0.5 + 0.1;
            double dx = getX() - other.getX();
            double dz = getZ() - other.getZ();
            double dSqr = dx * dx + dz * dz;
            if (dSqr < 1.0e-6) {
                double a = getRandom().nextDouble() * Math.PI * 2;
                dx = Math.cos(a) * 0.01;
                dz = Math.sin(a) * 0.01;
                dSqr = 1.0e-4;
            }
            double d = Math.sqrt(dSqr);
            if (d >= minDist) continue;
            double strength = (minDist - d) / minDist;
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

    private boolean isSameArmyHorse(WarHorseEntity other) {
        if (ownerUuid == null || other.ownerUuid == null) return false;
        if (ownerUuid.equals(other.ownerUuid)) return true;
        UUID mine = resolveOwnerCommander();
        UUID theirs = other.resolveOwnerCommander();
        return mine != null && mine.equals(theirs);
    }

    // ===================== 穿越推开 =====================
    private void pushAwayOverlappingEnemiesContinuous() {
        AABB box = this.getBoundingBox().inflate(1.5, 0.8, 1.5);

        // 只推"非马类"敌人。
        //   原因：马对马之间没有碰撞（isPushable=false + move 不检测实体碰撞），
        //   穿越时把另一匹马 setPos 侧移 1 格 → 每 5 tick 一次 →
        //   视觉上就是"对方马匹快速持续平移"。
        //   马之间的分离由 AbstractArmySoldier.controlMount 的虚拟瞄准点负责，
        //   这里不需要也不应该再推。
        List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive()
                        && e != getFirstPassenger()
                        && !(e instanceof WarHorseEntity)   // 马不推马
                        && !isFriendly(e));
        if (nearby.isEmpty()) return;

        Vec3 forward = passThroughDir;
        if (forward.lengthSqr() < 0.001) {
            Vec3 dm = this.getDeltaMovement();
            forward = new Vec3(dm.x, 0, dm.z);
        }
        if (forward.lengthSqr() < 0.001) {
            float yaw = this.getYRot() * ((float) Math.PI / 180f);
            forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        }
        forward = forward.normalize();

        Vec3 left = new Vec3(-forward.z, 0, forward.x);
        Vec3 right = new Vec3(forward.z, 0, -forward.x);

        for (LivingEntity e : nearby) {
            Vec3 toEnemy = e.position().subtract(this.position());
            double d = Math.sqrt(toEnemy.x * toEnemy.x + toEnemy.z * toEnemy.z);
            double minDist = (this.getBbWidth() + e.getBbWidth()) * 0.6;
            if (d >= minDist) continue;

            double leftDot = toEnemy.dot(left);
            Vec3 lateral = (leftDot >= 0 ? left : right);
            double push = minDist - d + 0.25;
            Vec3 pushVec = lateral.scale(push);
            Vec3 forwardPush = forward.scale(0.15);

            e.setPos(e.getX() + pushVec.x + forwardPush.x,
                    e.getY(),
                    e.getZ() + pushVec.z + forwardPush.z);
            e.hurtMarked = true;

            Vec3 ev = e.getDeltaMovement();
            double fdot = ev.dot(forward);
            if (fdot < 0) {
                e.setDeltaMovement(ev.x - forward.x * fdot, ev.y, ev.z - forward.z * fdot);
            }
        }
    }

    private boolean isFriendly(Entity other) {
        UUID myCommander = resolveOwnerCommander();
        if (myCommander == null) return false;

        if (other instanceof AbstractArmySoldier s) {
            return myCommander.equals(s.getCommanderUuid().orElse(null));
        }
        if (other instanceof AbstractArmyCommander c) {
            return myCommander.equals(c.getUUID());
        }
        if (other instanceof Player p) {
            return myCommander.equals(p.getUUID());
        }
        if (other instanceof WarHorseEntity otherHorse) {
            UUID otherCommander = null;
            if (level() instanceof ServerLevel sl && otherHorse.getOwnerUuid() != null) {
                Entity owner = sl.getEntities().get(otherHorse.getOwnerUuid());
                if (owner instanceof AbstractArmySoldier s) otherCommander = s.getCommanderUuid().orElse(null);
                else if (owner instanceof AbstractArmyCommander c) otherCommander = c.getUUID();
                else if (owner instanceof Player p) otherCommander = p.getUUID();
            }
            return myCommander.equals(otherCommander);
        }
        return false;
    }

    @Nullable
    private UUID resolveOwnerCommander() {
        if (!(level() instanceof ServerLevel sl) || ownerUuid == null) return null;
        Entity owner = sl.getEntities().get(ownerUuid);
        if (owner instanceof AbstractArmySoldier s) return s.getCommanderUuid().orElse(null);
        if (owner instanceof AbstractArmyCommander c) return c.getUUID();
        if (owner instanceof Player p) return p.getUUID();
        return null;
    }

    /**
     * 从 owner 读取该马的期望移动速度：
     *   - owner 是士兵 → 士兵的 getCavalryHorseSpeed()
     *   - owner 是首领 → 首领的 getCommanderHorseSpeed()
     *   - 其它（玩家 / 无 owner）→ 0.4
     */
    private double resolveOwnerHorseSpeed() {
        if (!(level() instanceof ServerLevel sl) || ownerUuid == null) return 0.4;
        Entity owner = sl.getEntities().get(ownerUuid);
        if (owner instanceof AbstractArmySoldier s) return s.getCavalryHorseSpeed();
        if (owner instanceof AbstractArmyCommander c) return c.getCommanderHorseSpeed();
        return 0.4;
    }

    // ===================== hurt =====================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isFriendlyFire(source)) return false;

        // 无论是否免疫，都把"战马被打"反映给骑手/主人的军团
        Entity atk = source.getEntity();
        if (atk == null) atk = source.getDirectEntity();
        if (atk instanceof Projectile proj) atk = proj.getOwner();
        if (atk instanceof LivingEntity le && !level().isClientSide) {
            ThreatDispatcher.propagate(this, le);
        }

        // 玩家的战马：无敌 + 无伤害传导。
        //   仇恨已经在上面照常传递（玩家军队会把这名攻击者记为共享仇恨）。
        if (playerMount) {
            return false;
        }

        // 找出骑手（可能是控制乘客，也可能是第一个乘客）
        LivingEntity rider = null;
        Entity p = getControllingPassenger();
        if (!(p instanceof LivingEntity)) p = getFirstPassenger();
        if (p instanceof LivingEntity le) rider = le;

        // NPC 骑兵：马自身不受伤，将部分伤害通过骑手的 hurt() 转嫁
        //   走骑手的 hurt()，所以护甲 / 护盾 / 无敌帧 等原版机制都会照常生效。
        //   玩家骑乘的战马不转移，正常受伤。
        if (rider instanceof AbstractArmySoldier || rider instanceof AbstractArmyCommander) {
            if (!level().isClientSide && rider.isAlive()) {
                float transferred = amount * RIDER_DAMAGE_TRANSFER;
                if (transferred > 0f) {
                    // 只有"攻击类伤害"（有攻击者 / 弹射物）才清掉骑手无敌帧（齐射时每一发都要结算）；
                    //   仙人掌 / 岩浆 / 火烧 / 挤压窒息等环境伤害没有攻击者，必须照常吃无敌帧 ——
                    //   否则骑乘的士兵会被每 tick 连续扣血，被地形挤压或烧死。
                    if (source.getEntity() != null || source.getDirectEntity() != null) {
                        rider.invulnerableTime = 0;
                    }
                    rider.hurt(source, transferred);
                }
            }
            // 马自身不掉血
            return false;
        }

        // 玩家骑乘 / 无人骑乘 → 正常受伤
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) {
            lastHurtTick = this.tickCount;
        }
        return hurt;
    }

    private boolean isFriendlyFire(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker == null) attacker = source.getDirectEntity();
        if (attacker instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner != null) attacker = owner;
        }
        if (!(attacker instanceof LivingEntity living)) return false;

        UUID horseOwnerCommander = resolveOwnerCommander();
        if (horseOwnerCommander == null) return false;

        if (living instanceof AbstractArmyCommander c
                && c.getUUID().equals(horseOwnerCommander)) return true;
        if (living instanceof AbstractArmySoldier s) {
            var uc = s.getCommanderUuid().orElse(null);
            if (uc != null && uc.equals(horseOwnerCommander)) return true;
        }
        if (living instanceof Player p && p.getUUID().equals(horseOwnerCommander)) return true;
        return false;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
    }

    @Override public boolean isFood(ItemStack stack) { return false; }
    @Override public boolean isPushable() { return false; }

    /** 玩家战马只允许主人骑乘。 */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        if (playerMount && ownerUuid != null
                && !passenger.getUUID().equals(ownerUuid)) {
            return false;
        }
        return super.canAddPassenger(passenger);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (ownerUuid != null) tag.putUUID("Owner", ownerUuid);
        tag.putBoolean("PlayerMount", playerMount);
        tag.putInt("LastHurtTick", lastHurtTick);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner");
        playerMount = tag.getBoolean("PlayerMount");
        lastHurtTick = tag.getInt("LastHurtTick");
    }

    // ===================== 掉落 =====================

    /** 战马死亡不掉落任何物品与经验（鞍、马甲、箱内物品一并清除）。 */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (this.getInventory() != null) this.getInventory().clearContent();
    }

    @Override
    public boolean shouldDropExperience() {
        return false;
    }

    @Nullable
    public UUID getOwnerUuid() { return ownerUuid; }
}
