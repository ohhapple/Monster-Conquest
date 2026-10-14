package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.UnitGearRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 单位装备界面（士兵 / 首领通用）：
 *   · 手动装备 6 格（头盔 / 胸甲 / 护腿 / 靴子 / 主手 / 副手）——默认全空，只显示玩家放进去的那一份；
 *   · 战术装备 18 格——只允许保暖衣 / 防弹衣（背包 / 马哨 / 弹药箱属于玩家装备，不能塞给单位）；
 *   · 玩家背包 36 格；
 *   · 数据槽同步小队号、四套策略（行动 / 攻击 / 阵型 / 骑乘）与"是否单独调过策略"，供界面显示。
 */
public class UnitMenu extends AbstractContainerMenu {

    private static final int GEAR = ManagedUnit.GEAR_SLOTS;
    private static final int TACTICAL = ManagedUnit.TACTICAL_SLOTS;
    private static final int EQUIP_END = GEAR + TACTICAL;
    private static final int INV_END = EQUIP_END + 36;

    /** 数据槽下标：小队号 / 行动 / 攻击 / 阵型 / 骑乘 / 个体化 / 可否调策略。 */
    public static final int DATA_SQUAD = 0;
    public static final int DATA_MOVEMENT = 1;
    public static final int DATA_ATTACK = 2;
    public static final int DATA_FORMATION = 3;
    public static final int DATA_RIDE = 4;
    /** 1 = 这个单位被界面单独调过策略（不再跟随小队 / 全军）。 */
    public static final int DATA_OVERRIDE = 5;
    /** 1 = 这个单位可以单独调策略（玩家阵营士兵；首领与 NPC 单位没有这组按钮）。 */
    public static final int DATA_STRATEGY = 6;
    /**
     * 状态效果同步：最多显示 {@link #EFFECT_SHOWN} 个效果，每个占两个槽
     *   （效果 = ((效果注册表 id + 1) << 8) | 放大等级，剩余秒数 = 秒 / -1 = 永久），
     *   最后一个槽是效果总数（多出来的用 +N 表示）。
     *   客户端实体自己拿不到别的生物的效果列表（原版只同步粒子），必须由服务端同步。
     */
    public static final int EFFECT_SHOWN = 4;
    public static final int DATA_EFFECTS = 7;
    public static final int DATA_EFFECT_DURATION = DATA_EFFECTS + EFFECT_SHOWN;
    public static final int DATA_EFFECT_TOTAL = DATA_EFFECT_DURATION + EFFECT_SHOWN;
    public static final int DATA_COUNT = DATA_EFFECT_TOTAL + 1;

    @Nullable private final ManagedUnit unit;
    private final int unitId;
    private final int[] sync = new int[DATA_COUNT];

    /** 服务端构造：直接绑定实体。 */
    public UnitMenu(int id, Inventory playerInv, ManagedUnit unit) {
        super(ModMenus.UNIT.get(), id);
        this.unit = unit;
        this.unitId = unit.asEntity().getId();
        unit.ensureDefaultGear();          // 老存档兜底：自带装备快照缺失时先补上
        addSlots(playerInv, new UnitEquipmentContainer(unit));
        addSyncSlots();
    }

    /** 客户端构造：只拿到实体 id，实体由客户端世界查回来（可能暂时找不到，界面按只读处理）。 */
    public UnitMenu(int id, Inventory playerInv, int unitId) {
        super(ModMenus.UNIT.get(), id);
        this.unitId = unitId;
        Entity entity = playerInv.player.level().getEntity(unitId);
        this.unit = entity instanceof ManagedUnit managed ? managed : null;
        Container equip = this.unit != null
                ? new UnitEquipmentContainer(this.unit)
                : new SimpleContainer(EQUIP_END);
        addSlots(playerInv, equip);
        addSyncSlots();
    }

    public int getUnitId() { return unitId; }

    @Nullable public ManagedUnit getUnit() { return unit; }

    /** 单位是不是玩家阵营士兵（策略按钮只对它开放）。 */
    public boolean canConfigureStrategy() { return sync[DATA_STRATEGY] == 1; }

    /** 读同步过来的策略 / 小队号。 */
    public int getSync(int index) { return sync[index]; }

    private void addSlots(Inventory playerInv, Container equip) {
        final Player owner = playerInv.player;
        // 手动装备：护甲 4 格 + 主手 / 副手各一格（主手 / 副手按兵种收武器）
        for (int i = 0; i < GEAR; i++) {
            final int index = i;
            EquipmentSlot target = ManagedUnit.gearSlot(i);
            int[] pos = gearSlotPos(i);
            addSlot(new Slot(equip, index, pos[0], pos[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (index <= 3) return UnitGearRules.fitsArmorSlot(owner, stack, target);
                    Entity bound = owner.level().getEntity(UnitMenu.this.unitId);
                    if (bound instanceof AbstractArmySoldier soldier) {
                        return index == 4
                                ? UnitGearRules.mayPlaceMainHand(soldier, stack)
                                : UnitGearRules.mayPlaceOffHand(soldier, stack);
                    }
                    if (bound instanceof AbstractArmyCommander commander) {
                        return index == 4
                                ? UnitGearRules.mayPlaceMainHand(commander, stack)
                                : UnitGearRules.mayPlaceOffHand(commander, stack);
                    }
                    return true;
                }
            });
        }
        // 战术装备：只允许保暖衣 / 防弹衣，同类只能一件
        for (int i = 0; i < TACTICAL; i++) {
            final int index = GEAR + i;
            int[] pos = tacticalSlotPos(i);
            addSlot(new Slot(equip, index, pos[0], pos[1]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    if (!stack.is(Monster_conquest.WARM_CLOTHES.get())
                            && !stack.is(Monster_conquest.BULLETPROOF_VEST.get())) {
                        return false;
                    }
                    for (int other = GEAR; other < EQUIP_END; other++) {
                        if (other == index) continue;
                        ItemStack existing = equip.getItem(other);
                        if (!existing.isEmpty() && existing.is(stack.getItem())) return false;
                    }
                    return true;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 191 + col * 18, 149 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 191 + col * 18, 211));
        }
    }

    /** 手动装备栏位置（相对界面左上角；界面底图里画在槽位左上角 -1 处）。 */
    private static int[] gearSlotPos(int index) {
        return new int[]{191 + index * 26, 25};
    }

    /** 战术装备栏位置（6 列 × 3 行 = 18 格）。 */
    private static int[] tacticalSlotPos(int index) {
        int col = index % 6;
        int row = index / 6;
        return new int[]{191 + col * 20, 73 + row * 20};
    }

    private void addSyncSlots() {
        addDataSlots(new ContainerData() {
            @Override public int get(int index) { return sync[index]; }
            @Override public void set(int index, int value) { sync[index] = value; }
            @Override public int getCount() { return DATA_COUNT; }
        });
    }

    @Override
    public void broadcastChanges() {
        sync[DATA_STRATEGY] = 0;
        if (unit instanceof AbstractArmySoldier soldier
                && soldier.level() instanceof ServerLevel) {
            sync[DATA_SQUAD] = soldier.getSquadId();
            if (soldier.getCommanderPlayer() instanceof ServerPlayer owner) {
                PlayerCommandState state = PlayerArmyManager.getIfPresent(owner.getUUID());
                if (state != null) {
                    PlayerCommandState.SquadState ss =
                            PlayerCommandState.stateForCommand(soldier, state);
                    sync[DATA_MOVEMENT] = ss.getMovement().getId();
                    sync[DATA_ATTACK] = ss.getAttack().getId();
                    sync[DATA_FORMATION] = ss.getFormation().getId();
                    sync[DATA_RIDE] = ss.getRideMode().getId();
                    sync[DATA_OVERRIDE] = soldier.hasStrategyOverride() ? 1 : 0;
                    sync[DATA_STRATEGY] = 1;
                }
            }
        }
        if (unit != null && !unit.asEntity().level().isClientSide) {
            syncEffects();
        }
        super.broadcastChanges();
    }

    /** 服务端：把单位的状态效果写进数据槽（客户端实体自己看不到别的生物的效果）。 */
    private void syncEffects() {
        int shown = 0;
        int total = 0;
        if (unit != null) {
            var effects = unit.asEntity().getActiveEffects();
            total = effects.size();
            for (var effect : effects) {
                if (shown >= EFFECT_SHOWN) break;
                int id = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT
                        .getId(effect.getEffect().value());
                if (id < 0) continue;
                sync[DATA_EFFECTS + shown] = ((id + 1) << 8) | (effect.getAmplifier() & 0xFF);
                sync[DATA_EFFECT_DURATION + shown] = effect.isInfiniteDuration()
                        ? -1
                        : Math.min(3599, effect.getDuration() / 20);
                shown++;
            }
        }
        for (int i = shown; i < EFFECT_SHOWN; i++) {
            sync[DATA_EFFECTS + i] = 0;
            sync[DATA_EFFECT_DURATION + i] = 0;
        }
        sync[DATA_EFFECT_TOTAL] = total;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack result = stack.copy();

        if (index < EQUIP_END) {
            if (!moveItemStackTo(stack, EQUIP_END, INV_END, true)) return ItemStack.EMPTY;
        } else {
            // 背包 → 优先塞进装备栏，放不下再留在原处
            if (!moveItemStackTo(stack, 0, EQUIP_END, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        // 客户端实例只用于渲染，实体暂未同步到时不做关闭判定
        if (unit == null) return player.isAlive();
        return player.isAlive() && unit.asEntity().isAlive()
                && player.distanceToSqr(unit.asEntity()) <= 64.0;
    }
}
