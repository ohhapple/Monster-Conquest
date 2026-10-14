package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 战术装备栏菜单：18 个槽位（同类型装备最多装一个，弹药箱除外）+ 玩家背包。
 */
public class TacticalEquipmentSlotsMenu extends AbstractContainerMenu {

    private static final int EQUIP_SLOTS = TacticalEquipmentSlotsData.SLOT_COUNT;
    private static final int INV_START = EQUIP_SLOTS;
    private static final int INV_END = INV_START + 36;

    private final Container equipmentContainer;

    public TacticalEquipmentSlotsMenu(int id, Inventory playerInv) {
        super(ModMenus.TACTICAL_EQUIPMENT_SLOTS.get(), id);
        this.equipmentContainer = playerInv.player.level().isClientSide
                ? new SimpleContainer(EQUIP_SLOTS)
                : new TacticalEquipmentSlotsContainer((ServerPlayer) playerInv.player);

        for (int i = 0; i < EQUIP_SLOTS; i++) {
            int col = i % 6;
            int row = i / 6;
            addSlot(new Slot(equipmentContainer, i, 26 + col * 20, 18 + row * 20) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // 允许：弹药箱、马哨、保暖衣、防弹衣、背包
                    if (!stack.is(Monster_conquest.AMMO_BOX.get())
                            && !stack.is(Monster_conquest.HORSE_WHISTLE.get())
                            && !stack.is(Monster_conquest.WARM_CLOTHES.get())
                            && !stack.is(Monster_conquest.BULLETPROOF_VEST.get())
                            && !stack.is(Monster_conquest.BACKPACK.get())) {
                        return false;
                    }
                    // 同类型装备只能装一个（弹药箱除外：不同口径的箱子可以各带一个）
                    if (stack.is(Monster_conquest.AMMO_BOX.get())) return true;
                    for (int other = 0; other < EQUIP_SLOTS; other++) {
                        if (other == this.getContainerSlot()) continue;
                        ItemStack existing = equipmentContainer.getItem(other);
                        if (!existing.isEmpty() && existing.is(stack.getItem())) return false;
                    }
                    return true;
                }
            });
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
        } else if (stack.is(Monster_conquest.AMMO_BOX.get())
                || stack.is(Monster_conquest.HORSE_WHISTLE.get())
                || stack.is(Monster_conquest.WARM_CLOTHES.get())
                || stack.is(Monster_conquest.BULLETPROOF_VEST.get())
                || stack.is(Monster_conquest.BACKPACK.get())) {
            if (!moveItemStackTo(stack, 0, EQUIP_SLOTS, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return equipmentContainer.stillValid(player);
    }
}
