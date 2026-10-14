package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.config.FactionConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 单位锚点：标记一个单位属于某个村庄巡逻队 / 某次袭击增援。
 * 锚点同时决定该单位的活动范围（巡逻、战斗、索敌都受它限制）与生命周期。
 * 只有"随村庄刷出 / 随袭击刷出"的单位带锚点；
 *   刷怪蛋、指令、玩家招募、首领自然召唤的单位没有锚点，行为保持原样。
 */
public final class ArmyAnchor {

    public enum Type { NONE, VILLAGE, RAID }

    private Type type = Type.NONE;
    @Nullable private BlockPos pos;

    public Type getType() { return type; }
    public boolean isBound() { return type != Type.NONE && pos != null; }
    public boolean isVillage() { return type == Type.VILLAGE && pos != null; }
    public boolean isRaid() { return type == Type.RAID && pos != null; }

    @Nullable
    public BlockPos getPos() { return pos; }

    public void bind(Type type, BlockPos pos) {
        this.type = type;
        this.pos = pos.immutable();
    }

    public void clear() {
        this.type = Type.NONE;
        this.pos = null;
    }

    /** 活动半径（格）：村庄 / 袭击各自读配置。 */
    public double radius() {
        return switch (type) {
            case VILLAGE -> FactionConfig.villagerVillageRadius;
            case RAID -> FactionConfig.illagerRaidRadius;
            case NONE -> 0.0;
        };
    }

    /** 锚点中心（方块中心）。 */
    public Vec3 center() {
        if (pos == null) return Vec3.ZERO;
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public double distanceSqr(Entity entity) {
        return isBound() ? entity.distanceToSqr(center()) : 0.0;
    }

    public boolean contains(Entity entity) {
        if (!isBound()) return true;
        double r = radius();
        return distanceSqr(entity) <= r * r;
    }

    public boolean contains(Vec3 point) {
        if (!isBound()) return true;
        double r = radius();
        return point.distanceToSqr(center()) <= r * r;
    }

    /** 在锚点附近取一个随机点（factor = 相对活动半径的比例，0.05~1.0）。 */
    public Vec3 randomPointNear(RandomSource random, double factor) {
        double r = Math.max(2.0, radius() * Mth.clamp(factor, 0.05, 1.0));
        double angle = random.nextDouble() * Math.PI * 2.0;
        double dist = Math.sqrt(random.nextDouble()) * r;
        Vec3 c = center();
        return new Vec3(c.x + Math.cos(angle) * dist, c.y, c.z + Math.sin(angle) * dist);
    }

    // ===================== 存档 =====================

    public void save(CompoundTag tag, String prefix) {
        if (!isBound()) return;
        tag.putString(prefix + "Type", type.name());
        tag.putLong(prefix + "Pos", pos.asLong());
    }

    public void load(CompoundTag tag, String prefix) {
        String raw = tag.getString(prefix + "Type");
        if (raw.isEmpty()) {
            clear();
            return;
        }
        try {
            Type parsed = Type.valueOf(raw);
            BlockPos p = BlockPos.of(tag.getLong(prefix + "Pos"));
            if (parsed == Type.NONE) clear();
            else bind(parsed, p);
        } catch (Exception e) {
            clear();
        }
    }

    // ===================== 通用查询 =====================

    /** 带锚点的单位（村庄巡逻 / 袭击增援）。 */
    public interface AnchoredUnit {
        ArmyAnchor getAnchor();
    }

    @Nullable
    public static ArmyAnchor anchorOf(@Nullable Entity entity) {
        return entity instanceof AnchoredUnit unit ? unit.getAnchor() : null;
    }

    /**
     * 目标是否在攻击者的锚点活动范围内。
     * 没有锚点的单位不受限制（返回 true）。
     * 村庄巡逻队特例：袭击期间可以锁定任意距离的袭击单位——
     *   仇恨不会因为目标跑远 / 上楼而失效；实际移动仍受村庄范围约束。
     */
    public static boolean targetAllowed(@Nullable Entity attacker, @Nullable Entity target) {
        if (attacker == null || target == null) return true;
        ArmyAnchor anchor = anchorOf(attacker);
        if (anchor == null || !anchor.isBound()) return true;
        if (anchor.contains(target.position())) return true;
        return anchor.isVillage() && isRaidUnit(target);
    }

    /** 目标是否是正在进行的袭击的参战单位（原版掠夺者 / 本模组袭击部队）。 */
    private static boolean isRaidUnit(Entity target) {
        ArmyAnchor targetAnchor = anchorOf(target);
        if (targetAnchor != null && targetAnchor.isRaid()) return true;
        if (target instanceof net.minecraft.world.entity.raid.Raider raider) {
            return raider.hasActiveRaid();
        }
        return false;
    }
}
