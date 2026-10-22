package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.block.SpawnPointBlock;
import com.ohhapple.monster_conquest.block.SpawnPointBlockEntity;
import com.ohhapple.monster_conquest.block.SpawnPointGlobal;
import com.ohhapple.monster_conquest.block.SpawnPointService;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.item.SoldierSpawnEggItem;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.path.PathRoutes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.SimpleContainer;

import org.jetbrains.annotations.Nullable;

/**
 * 刷怪点界面：1 格模板蛋槽 + 玩家背包，配置数值走数据槽同步（界面按钮只负责发请求）。
 * 两个来源：
 *   · 方块刷怪点 —— 服务端直接读写方块实体；界面开着期间暂停刷怪，关掉界面才开始计时。
 *   · 手持的刷怪点物品（蹲下右键打开）—— 服务端拿一份脱离世界的配置副本，
 *     每次改动立刻写回物品的方块实体数据组件（编辑并保存）。
 */
public class SpawnPointMenu extends AbstractContainerMenu {

    /** 来源：方块刷怪点（数据里会再跟一个方块坐标）。 */
    public static final int MODE_BLOCK = 0;
    /** 来源：主手拿着的刷怪点物品。 */
    public static final int MODE_MAIN_HAND = 1;
    /** 来源：副手拿着的刷怪点物品。 */
    public static final int MODE_OFF_HAND = 2;

    /** 模板蛋槽下标。 */
    public static final int EGG_SLOT = 0;
    private static final int INV_START = EGG_SLOT + 1;
    private static final int INV_END = INV_START + 36;

    // ===================== 数据槽 =====================

    public static final int DATA_UNIT = 0;
    public static final int DATA_FACTION = 1;
    public static final int DATA_MOUNTED = 2;
    public static final int DATA_TIER = 3;
    public static final int DATA_BATCH = 4;
    public static final int DATA_INTERVAL = 5;
    public static final int DATA_MAX_TOTAL = 6;
    public static final int DATA_MAX_CONCURRENT = 7;
    public static final int DATA_SPAWNED = 8;
    public static final int DATA_ALIVE = 9;
    public static final int DATA_STATUS = 10;
    /** 本刷怪点的生成开关。 */
    public static final int DATA_ENABLED = 11;
    /** 是否被指令全局禁止刷怪。 */
    public static final int DATA_GLOBAL_DISABLED = 12;
    /** 刷怪点当前设置的巡逻路径点数量。 */
    public static final int DATA_PATH_POINTS = 13;
    public static final int DATA_COUNT = 14;

    /** 模板蛋槽在界面里的位置（相对界面左上角）。 */
    public static final int EGG_SLOT_X = 9;
    public static final int EGG_SLOT_Y = 160;

    @Nullable private final SpawnPointBlockEntity spawner;
    /** 物品来源：正在编辑的那一份物品（方块来源为 null）。 */
    @Nullable private final ItemStack editedItem;
    /** 开界面玩家的玩家阵营：给"没有阵营数据"的士兵蛋补默认值（与直接放这颗蛋的规则一致）。 */
    @Nullable private final ResourceLocation configuredFaction;
    private final BlockPos pos;
    private final int[] sync = new int[DATA_COUNT];

    /** 服务端：方块来源。 */
    public SpawnPointMenu(int id, Inventory playerInv, BlockPos pos) {
        super(ModMenus.SPAWN_POINT.get(), id);
        this.pos = pos;
        this.editedItem = null;
        Level level = playerInv.player.level();
        this.configuredFaction = level.isClientSide ? null : PlayerFactionRegistry.factionIdOf(playerInv.player);
        SpawnPointBlockEntity be = level.getBlockEntity(pos) instanceof SpawnPointBlockEntity found
                ? found : null;
        this.spawner = be;
        buildSlots(playerInv, be == null
                ? new SimpleContainer(1)
                : new SpawnPointEggContainer(be, this::onEggChanged));
        if (!level.isClientSide && be != null) be.addEditor();
    }

    /** 服务端 / 客户端：手持物品来源（客户端只拿同步数据，不持有真实配置）。 */
    public SpawnPointMenu(int id, Inventory playerInv, int handMode) {
        super(ModMenus.SPAWN_POINT.get(), id);
        this.pos = BlockPos.ZERO;
        Level level = playerInv.player.level();
        if (level.isClientSide) {
            this.editedItem = null;
            this.spawner = null;
            this.configuredFaction = null;
            buildSlots(playerInv, new SimpleContainer(1));
            return;
        }

        InteractionHand hand = handMode == MODE_OFF_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = playerInv.player.getItemInHand(hand);
        this.editedItem = stack;
        this.configuredFaction = PlayerFactionRegistry.factionIdOf(playerInv.player);
        // 脱离世界的配置副本：改它、再写回物品
        SpawnPointBlockEntity copy = new SpawnPointBlockEntity(
                BlockPos.ZERO, Monster_conquest.SPAWN_POINT.get().defaultBlockState());
        copy.loadSettings(stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag());
        this.spawner = copy;
        buildSlots(playerInv, new SpawnPointEggContainer(copy, this::onEggChanged));
    }

    private void buildSlots(Inventory playerInv, Container eggContainer) {
        addSlot(new Slot(eggContainer, 0, EGG_SLOT_X, EGG_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return SpawnPointService.isUnitEgg(stack);
            }

            /** 蛋只当模板用，一格放一颗就够。 */
            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 109 + col * 18, 206 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 109 + col * 18, 264));
        }

        addDataSlots(new ContainerData() {
            @Override public int get(int index) { return sync[index]; }
            @Override public void set(int index, int value) { sync[index] = value; }
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    /** 读同步过来的配置 / 计数。 */
    public int getSync(int index) { return sync[index]; }

    /** 服务端：处理界面的一次配置请求（校验后写进配置，物品来源立刻保存）。 */
    public void applyAction(int action, int value) {
        if (spawner == null) return;
        if (action == SpawnPointService.ACTION_CLEAR_PATH) {
            // 巡逻路径只挂在方块刷怪点上（手持物品的副本没有路径这回事）
            if (spawner.getLevel() instanceof ServerLevel level) {
                PathRoutes.get(level)
                        .clear(PathRoutes.spawnerKey(pos));
            }
            return;
        }
        SpawnPointService.applyAction(spawner, action, value);
        flush();
    }

    /** 物品来源：把配置副本写回物品（方块来源直接改的就是方块实体，不需要回写）。 */
    private void flush() {
        if (editedItem == null || spawner == null) return;
        editedItem.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(spawner.saveSettings()));
    }

    /**
     * 蛋槽内容变化（服务端）：没有阵营数据的士兵蛋补上配置者的玩家阵营
     *   —— 与"直接放这颗蛋"一致（新蛋默认就是放置者的玩家阵营），
     *   补完立刻存回方块实体 / 物品，界面里的阵营显示也随之同步。
     */
    private void onEggChanged() {
        ItemStack egg = getSlot(EGG_SLOT).getItem();
        if (!egg.isEmpty() && configuredFaction != null
                && egg.getItem() instanceof SoldierSpawnEggItem) {
            CompoundTag tag = egg.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
            if (!tag.contains(RetrievalToolItem.KEY_FACTION)) {
                tag.putString(RetrievalToolItem.KEY_FACTION, configuredFaction.toString());
                egg.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                // 走一遍槽位写回：容器会把补好的蛋存进刷怪点（并同步给客户端）
                getSlot(EGG_SLOT).setChanged();
            }
        }
        flush();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        // 界面关掉：方块来源解除"编辑中"的暂停
        if (spawner != null && editedItem == null) spawner.removeEditor();
    }

    @Override
    public void broadcastChanges() {
        if (spawner != null) {
            sync[DATA_UNIT] = spawner.getUnitIndex();
            int factionIndex = SpawnPointService.FACTIONS.indexOf(spawner.getFaction());
            sync[DATA_FACTION] = factionIndex < 0 ? 0 : factionIndex;
            sync[DATA_MOUNTED] = spawner.isMounted() ? 1 : 0;
            sync[DATA_TIER] = spawner.getTier();
            sync[DATA_BATCH] = spawner.getBatch();
            sync[DATA_INTERVAL] = spawner.getIntervalSeconds();
            sync[DATA_MAX_TOTAL] = spawner.getMaxTotal();
            sync[DATA_MAX_CONCURRENT] = spawner.getMaxConcurrent();
            sync[DATA_SPAWNED] = spawner.getSpawnedTotal();
            sync[DATA_ALIVE] = spawner.getAliveCount();
            sync[DATA_STATUS] = spawner.getStatus();
            sync[DATA_ENABLED] = spawner.isEnabled() ? 1 : 0;
            sync[DATA_GLOBAL_DISABLED] = SpawnPointGlobal.isDisabled() ? 1 : 0;
            sync[DATA_PATH_POINTS] = spawner.getLevel() instanceof ServerLevel level
                    ? PathRoutes.get(level)
                            .pointCount(PathRoutes.spawnerKey(pos))
                    : 0;
        }
        super.broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index == EGG_SLOT) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else if (SpawnPointService.isUnitEgg(stack)) {
            if (!moveItemStackTo(stack, EGG_SLOT, EGG_SLOT + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        // 配置界面只对创造模式开放：中途切回生存也会自动关掉
        if (!player.isCreative()) return false;
        if (editedItem != null) return player.isAlive() && !editedItem.isEmpty();
        if (spawner == null) return player.isAlive();
        return player.isAlive()
                && player.level().getBlockState(pos).getBlock() instanceof SpawnPointBlock
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
