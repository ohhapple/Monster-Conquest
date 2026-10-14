package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.item.AmmoBoxItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端：把弹药箱物品的容器组件包装成 Container，供菜单槽位读写。
 * 弹药箱既可能在玩家背包里，也可能放在战术装备栏里。
 */
public class AmmoBoxContainer extends SimpleContainer {

    private final ServerPlayer owner;
    private final int source;
    private final int index;
    private boolean loading;

    public AmmoBoxContainer(ServerPlayer owner, int source, int index) {
        super(AmmoBoxItem.SLOTS);
        this.owner = owner;
        this.source = source;
        this.index = index;
        load();
    }

    private ItemStack box() {
        return TacticalEquipmentSlotsData.resolveBox(owner, source, index);
    }

    private void load() {
        loading = true;
        List<ItemStack> contents = TacticalEquipmentSlotsData.boxContents(box());
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, contents.get(i));
        }
        loading = false;
    }

    @Override
    public void setChanged() {
        if (loading) return;
        List<ItemStack> contents = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) {
            contents.add(getItem(i));
        }
        ItemStack box = box();
        TacticalEquipmentSlotsData.writeBoxContents(box, contents);
        TacticalEquipmentSlotsData.storeBox(owner, source, index, box);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && box().getItem() instanceof AmmoBoxItem;
    }
}
