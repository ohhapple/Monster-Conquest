package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.item.BackpackItem;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 背包菜单：上面 8 排 × 9 格（72 格），下面玩家背包。
 *   背包槽位里不能再放背包（防套娃）。
 */
public class BackpackMenu extends AbstractContainerMenu {

    public static final int BAG_SLOTS = BackpackItem.SLOTS;
    private static final int INV_START = BAG_SLOTS;
    private static final int INV_END = INV_START + 36;

    private final Container bagContainer;

    public BackpackMenu(int id, Inventory playerInv, int source, int bagIndex) {
        super(ModMenus.BACKPACK.get(), id);
        this.bagContainer = playerInv.player.level().isClientSide
                ? new SimpleContainer(BAG_SLOTS)
                : new BackpackContainer((ServerPlayer) playerInv.player, source, bagIndex);

        for (int row = 0; row < BackpackItem.ROWS; row++) {
            for (int col = 0; col < BackpackItem.COLS; col++) {
                addSlot(new Slot(bagContainer, col + row * BackpackItem.COLS,
                        8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        // 套娃防护：只允许 普通物品 / 原版潜影盒 / 原版收纳袋 / 本模组弹药箱
                        return BackpackItem.slotAccepts(stack);
                    }
                });
            }
        }

        int invY = 18 + BackpackItem.ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, invY + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, invY + 3 * 18 + 4));
        }
    }

    /** 从战术装备栏里找到第一个背包并打开（B 键 / 快捷入口）。 */
    public static void openEquipped(ServerPlayer player) {
        int index = findEquippedIndex(player);
        if (index < 0) return;   // 没装备背包时按 B：静默，什么都不做
        player.openMenu(new SimpleMenuProvider(
                        (id, inv, p) -> new BackpackMenu(id, inv,
                                TacticalEquipmentSlotsData.SOURCE_TACTICAL_SLOTS, index),
                        Component.translatable("item.monster_conquest.backpack")),
                buf -> {
                    buf.writeVarInt(TacticalEquipmentSlotsData.SOURCE_TACTICAL_SLOTS);
                    buf.writeVarInt(index);
                });
    }

    /** 战术装备栏里第一个背包的槽位下标；没有返回 -1。 */
    public static int findEquippedIndex(Player player) {
        List<ItemStack> equipment = TacticalEquipmentSlotsData.get(player);
        for (int i = 0; i < equipment.size(); i++) {
            if (equipment.get(i).getItem() instanceof BackpackItem) return i;
        }
        return -1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        result = stack.copy();

        if (index < INV_START) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else {
            if (!BackpackItem.slotAccepts(stack)) return ItemStack.EMPTY;   // 防套娃
            if (!moveItemStackTo(stack, 0, BAG_SLOTS, false)) return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return bagContainer.stillValid(player);
    }
}
