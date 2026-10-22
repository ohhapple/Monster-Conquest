package com.ohhapple.monster_conquest.block;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.Monster_conquest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 刷怪点方块实体：保存这个刷怪点的全部配置（开关 / 单位 / 阵营 / 形态 / 等级 / 数量 / 间隔 /
 *   上限 / 模板刷怪蛋）与刷怪状态（已生成计数、从本刷怪点刷出的单位名单），并按间隔驱动刷怪。
 * 初始状态：没选单位、单次刷出 0、间隔无限、开关关闭 —— 什么都不刷。
 *   打开界面（或蹲下右键手持的刷怪点物品）设置好、关掉界面并打开开关之后才会开始刷；
 *   界面打开期间暂停刷怪，避免"还没设置好就开始刷"。
 * 每种数量的语义：
 *   · 单次刷出（batch）—— 每个间隔最多刷几个；
 *   · 最多生成（maxTotal）—— 累计生成上限，-1 = 无限；到顶后停刷，界面里可手动重置计数；
 *   · 同时存在（maxConcurrent）—— 世界上同时存活的本刷怪点单位上限，-1 = 无限；
 *     达到上限就暂缓，等少于上限再继续。
 */
public class SpawnPointBlockEntity extends BlockEntity {

    /** 数值型上限里的"无限"取值。 */
    public static final int UNLIMITED = -1;
    /** 未选择单位。 */
    public static final int NO_UNIT = -1;

    /** 默认单次刷出 0（不刷）；间隔 0 表示无限长（不刷）；开关默认关闭。 */
    public static final int DEFAULT_BATCH = 0;
    public static final int DEFAULT_INTERVAL_SECONDS = 0;

    public static final int MAX_BATCH = 64;
    /** 间隔下限；0 = 无限长（不刷）。 */
    public static final int MIN_INTERVAL_SECONDS = 0;
    public static final int MAX_INTERVAL_SECONDS = 600;
    public static final int MAX_TOTAL_LIMIT = 100000;
    public static final int MAX_CONCURRENT_LIMIT = 256;

    // ===================== 界面状态 =====================

    /** 正常：等待下一个刷怪间隔。 */
    public static final int STATUS_WAITING = 0;
    /** 已达「最多生成」上限，需要手动重置计数。 */
    public static final int STATUS_LIMIT_TOTAL = 1;
    /** 已达「同时存在」上限，等存活数量降下来。 */
    public static final int STATUS_LIMIT_ALIVE = 2;
    /** 还没选要生成的单位。 */
    public static final int STATUS_NO_UNIT = 3;
    /** 使用模板刷怪蛋生成。 */
    public static final int STATUS_EGG = 4;
    /** 这个刷怪点的开关被关掉了。 */
    public static final int STATUS_SWITCHED_OFF = 5;
    /** 被指令全局禁止刷怪。 */
    public static final int STATUS_GLOBAL_DISABLED = 6;
    /** 界面打开中：关掉界面之后才开始刷。 */
    public static final int STATUS_EDITING = 7;
    /** 数量 / 间隔还没设置好（单次 0 或间隔无限）。 */
    public static final int STATUS_NOT_READY = 8;

    // ===================== 配置 =====================

    /** 生成开关（默认关闭：配置好、关掉界面之后再打开才会刷）。 */
    private boolean enabled = false;
    private int unitIndex = NO_UNIT;
    private ResourceLocation faction = FactionIds.UNDEAD;
    private boolean mounted = false;
    private int tier = 0;
    private int batch = DEFAULT_BATCH;
    private int intervalTicks = DEFAULT_INTERVAL_SECONDS * 20;
    private int maxTotal = UNLIMITED;
    private int maxConcurrent = UNLIMITED;
    /** 模板蛋：非空时优先按蛋里的数据生成，蛋本身不消耗。 */
    private ItemStack egg = ItemStack.EMPTY;

    // ===================== 运行状态 =====================

    /** 累计已生成数量（「最多生成」按它判定）。 */
    private int spawnedTotal = 0;
    /** 本刷怪点刷出、还没确认死亡的单位 UUID。 */
    private final List<UUID> tracked = new ArrayList<>();
    /** 缓存的同时存活数量（每 20 tick 或刷怪后刷新，供界面显示）。 */
    private int aliveCount = 0;
    /** 刷怪倒计时（tick）。 */
    private int timer = DEFAULT_INTERVAL_SECONDS * 20;
    /** 存活数量刷新计时。 */
    private int sweepTimer = 0;
    /** 当前打开着这个刷怪点界面的玩家数（界面开着时暂停刷怪，不存档）。 */
    private int openEditors = 0;

    public SpawnPointBlockEntity(BlockPos pos, BlockState state) {
        super(Monster_conquest.SPAWN_POINT_BE.get(), pos, state);
    }

    // ===================== 配置读写 =====================

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean value) {
        if (value == enabled) return;
        enabled = value;
        setChanged();
    }

    public int getUnitIndex() { return unitIndex; }

    public void setUnitIndex(int value) {
        if (value == unitIndex) return;
        unitIndex = value;
        setChanged();
    }

    public ResourceLocation getFaction() { return faction; }

    public void setFaction(@Nullable ResourceLocation value) {
        ResourceLocation next = value == null ? FactionIds.UNDEAD : value;
        if (next.equals(faction)) return;
        faction = next;
        setChanged();
    }

    public boolean isMounted() { return mounted; }

    public void setMounted(boolean value) {
        if (value == mounted) return;
        mounted = value;
        setChanged();
    }

    public int getTier() { return tier; }

    public void setTier(int value) {
        int clamped = Math.max(0, value);
        if (clamped == tier) return;
        tier = clamped;
        setChanged();
    }

    public int getBatch() { return batch; }

    public void setBatch(int value) {
        int clamped = Math.max(0, Math.min(MAX_BATCH, value));
        if (clamped == batch) return;
        batch = clamped;
        setChanged();
    }

    public int getIntervalTicks() { return intervalTicks; }

    /** 刷怪间隔（秒）；0 = 无限长（不刷）。 */
    public int getIntervalSeconds() { return Math.max(0, intervalTicks / 20); }

    public void setIntervalSeconds(int seconds) {
        int clamped = Math.max(MIN_INTERVAL_SECONDS, Math.min(MAX_INTERVAL_SECONDS, seconds));
        int ticks = clamped * 20;
        if (ticks == intervalTicks) return;
        intervalTicks = ticks;
        // 调快间隔时立刻生效，不等旧的长倒计时走完
        timer = Math.min(timer, intervalTicks);
        setChanged();
    }

    public int getMaxTotal() { return maxTotal; }

    public void setMaxTotal(int value) {
        int clamped = value < 0 ? UNLIMITED : Math.max(1, Math.min(MAX_TOTAL_LIMIT, value));
        if (clamped == maxTotal) return;
        maxTotal = clamped;
        setChanged();
    }

    public int getMaxConcurrent() { return maxConcurrent; }

    public void setMaxConcurrent(int value) {
        int clamped = value < 0 ? UNLIMITED : Math.max(1, Math.min(MAX_CONCURRENT_LIMIT, value));
        if (clamped == maxConcurrent) return;
        maxConcurrent = clamped;
        setChanged();
    }

    public ItemStack getEgg() { return egg; }

    public void setEgg(ItemStack stack) {
        ItemStack next = stack == null ? ItemStack.EMPTY : stack.copy();
        if (ItemStack.matches(next, egg)) return;
        egg = next;
        // 放了模板蛋就改用它生成：手动选的单位让位（取出模板蛋后才能重新手选）
        if (!egg.isEmpty()) unitIndex = NO_UNIT;
        setChanged();
    }

    /** 世界是否已配好可以刷（有模板蛋，或选了单位 + 数量 / 间隔都设置过）。 */
    public boolean hasSpawnSource() {
        if (!egg.isEmpty()) return true;
        return SpawnPointService.unitAt(unitIndex) != null;
    }

    /** 单次刷出数量与间隔是否都设置过（0 / 无限都不刷）。 */
    public boolean hasSpawnTiming() {
        return batch > 0 && intervalTicks > 0;
    }

    // ===================== 界面占用 =====================

    /** 有玩家打开了这个刷怪点的界面：暂停刷怪，避免一边改一边刷。 */
    public boolean isBeingEdited() { return openEditors > 0; }

    public void addEditor() {
        openEditors++;
    }

    public void removeEditor() {
        if (openEditors > 0) openEditors--;
        // 界面关掉后从这个完整间隔开始计时，不会一关上就立刻刷
        if (openEditors == 0) timer = Math.max(1, intervalTicks);
    }

    // ===================== 运行状态 =====================

    public int getSpawnedTotal() { return spawnedTotal; }

    /** 手动重置累计生成计数，让到顶的刷怪点重新开始刷。 */
    public void resetSpawnedTotal() {
        if (spawnedTotal == 0) return;
        spawnedTotal = 0;
        setChanged();
    }

    public void addSpawned(int count) {
        if (count <= 0) return;
        spawnedTotal += count;
        setChanged();
    }

    public int getAliveCount() { return aliveCount; }

    public void setAliveCount(int value) {
        aliveCount = Math.max(0, value);
    }

    public List<UUID> getTracked() { return tracked; }

    public int getStatus() {
        if (SpawnPointGlobal.isDisabled()) return STATUS_GLOBAL_DISABLED;
        if (!enabled) return STATUS_SWITCHED_OFF;
        if (isBeingEdited()) return STATUS_EDITING;
        if (!hasSpawnSource()) return STATUS_NO_UNIT;
        if (!hasSpawnTiming()) return STATUS_NOT_READY;
        if (maxTotal != UNLIMITED && spawnedTotal >= maxTotal) return STATUS_LIMIT_TOTAL;
        if (maxConcurrent != UNLIMITED && aliveCount >= maxConcurrent) return STATUS_LIMIT_ALIVE;
        if (!egg.isEmpty()) return STATUS_EGG;
        return STATUS_WAITING;
    }

    // ===================== 刷怪 =====================

    public static void serverTick(Level level, BlockPos pos, BlockState state, SpawnPointBlockEntity be) {
        // 间隔无限长（0）：不刷，也不用跑倒计时
        if (be.intervalTicks > 0) {
            if (be.timer > 0) be.timer--;
            if (be.timer <= 0) {
                be.timer = Math.max(1, be.intervalTicks);
                if (level instanceof ServerLevel serverLevel) {
                    SpawnPointService.trySpawn(serverLevel, pos, state, be);
                }
            }
        }
        // 存活数量定期刷新：达到「同时存在」上限后靠它发现数量掉下来了
        if (++be.sweepTimer >= 20) {
            be.sweepTimer = 0;
            if (level instanceof ServerLevel serverLevel) {
                be.aliveCount = SpawnPointService.countAlive(serverLevel, be);
            }
        }
    }

    // ===================== 存档 / 物品数据 =====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.merge(saveSettings());
        tag.putInt("SpawnedTotal", spawnedTotal);
        ListTag list = new ListTag();
        for (UUID id : tracked) list.add(NbtUtils.createUUID(id));
        tag.put("Tracked", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadSettings(tag);
        spawnedTotal = Math.max(0, tag.getInt("SpawnedTotal"));
        tracked.clear();
        ListTag list = tag.getList("Tracked", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < list.size(); i++) {
            tracked.add(NbtUtils.loadUUID(list.get(i)));
        }
        timer = intervalTicks;
    }

    /** 保存配置（含模板蛋）—— 中键复制用。 */
    public CompoundTag saveSettings() {
        return saveSettings(true);
    }

    /**
     * 保存配置。
     *
     * @param withEgg 是否带上模板蛋：中键复制带上（完整复制）；挖掉的掉落不带
     *                （模板蛋会单独掉出来，同一颗蛋不会变成两份）
     */
    public CompoundTag saveSettings(boolean withEgg) {
        CompoundTag tag = new CompoundTag();
        // 物品组件里的方块实体数据必须带 id（原版编码校验要求）
        ResourceLocation typeId = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(getType());
        if (typeId != null) tag.putString("id", typeId.toString());
        tag.putBoolean("Enabled", enabled);
        tag.putInt("UnitIndex", unitIndex);
        tag.putString("Faction", faction.toString());
        tag.putBoolean("Mounted", mounted);
        tag.putInt("Tier", tier);
        tag.putInt("Batch", batch);
        tag.putInt("Interval", intervalTicks);
        tag.putInt("MaxTotal", maxTotal);
        tag.putInt("MaxConcurrent", maxConcurrent);
        if (withEgg && !egg.isEmpty()) {
            tag.put("Egg", ItemStack.OPTIONAL_CODEC.encodeStart(NbtOps.INSTANCE, egg)
                    .result().orElseGet(CompoundTag::new));
        }
        return tag;
    }

    /** 读配置（与 {@link #saveSettings(boolean)} 对称）；缺字段用默认值。 */
    public void loadSettings(CompoundTag tag) {
        // 缺字段 = 默认关闭（新放下的刷怪点要先在界面里配置、打开开关才会刷）
        enabled = tag.getBoolean("Enabled");
        unitIndex = tag.contains("UnitIndex") ? Math.max(NO_UNIT, tag.getInt("UnitIndex")) : NO_UNIT;
        ResourceLocation saved = ResourceLocation.tryParse(tag.getString("Faction"));
        faction = saved == null ? FactionIds.UNDEAD : saved;
        mounted = tag.getBoolean("Mounted");
        tier = Math.max(0, tag.getInt("Tier"));
        batch = tag.contains("Batch") ? Math.max(0, Math.min(MAX_BATCH, tag.getInt("Batch"))) : DEFAULT_BATCH;
        intervalTicks = tag.contains("Interval")
                ? Math.max(0, Math.min(MAX_INTERVAL_SECONDS * 20, tag.getInt("Interval")))
                : DEFAULT_INTERVAL_SECONDS * 20;
        maxTotal = tag.contains("MaxTotal") ? tag.getInt("MaxTotal") : UNLIMITED;
        maxConcurrent = tag.contains("MaxConcurrent") ? tag.getInt("MaxConcurrent") : UNLIMITED;
        egg = tag.contains("Egg")
                ? ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, tag.get("Egg"))
                        .result().orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        if (!egg.isEmpty()) unitIndex = NO_UNIT;
    }

    /**
     * 把自己的配置同步给客户端：创造模式的中键复制要在客户端本地生成带设置的方块物品。
     *   （界面里的数值另有菜单数据槽同步，这里只为了让中键复制拿到真实配置。）
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveSettings();
    }
}
