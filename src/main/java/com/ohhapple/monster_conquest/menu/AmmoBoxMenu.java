package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.item.AmmoBoxItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 弹药箱菜单：上面 3 排（27 格）弹药槽，下面玩家背包。
 */
public class AmmoBoxMenu extends AbstractContainerMenu {

    public static final int BOX_SLOTS = AmmoBoxItem.SLOTS;
    private static final int INV_START = BOX_SLOTS;
    private static final int INV_END = INV_START + 36;

    private final Container boxContainer;

    public AmmoBoxMenu(int id, Inventory playerInv, int source, int boxIndex) {
        super(ModMenus.AMMO_BOX.get(), id);
        this.boxContainer = playerInv.player.level().isClientSide
                ? new SimpleContainer(BOX_SLOTS)
                : new AmmoBoxContainer((ServerPlayer) playerInv.player, source, boxIndex);

        for (int row = 0; row < AmmoBoxItem.ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(boxContainer, col + row * 9, 8 + col * 18, 18 + row * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return TacticalEquipmentSlotsData.isAmmo(stack);
                    }
                });
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 88 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 146));
        }
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
        } else if (TacticalEquipmentSlotsData.isAmmo(stack)) {
            if (!moveItemStackTo(stack, 0, BOX_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return boxContainer.stillValid(player);
    }
}
