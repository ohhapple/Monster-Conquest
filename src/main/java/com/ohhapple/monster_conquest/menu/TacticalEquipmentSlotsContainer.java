package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端：战术装备栏内容（玩家数据附件）包装成 Container，供菜单槽位读写。
 */
public class TacticalEquipmentSlotsContainer extends SimpleContainer {

    private final ServerPlayer owner;
    private boolean loading;

    public TacticalEquipmentSlotsContainer(ServerPlayer owner) {
        super(TacticalEquipmentSlotsData.SLOT_COUNT);
        this.owner = owner;
        load();
    }

    private void load() {
        loading = true;
        List<ItemStack> list = TacticalEquipmentSlotsData.get(owner);
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, i < list.size() ? list.get(i) : ItemStack.EMPTY);
        }
        loading = false;
    }

    @Override
    public void setChanged() {
        if (loading) return;
        List<ItemStack> list = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) {
            list.add(getItem(i));
        }
        TacticalEquipmentSlotsData.set(owner, list);
        TacticalEquipmentSlotsData.sendSync(owner);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && player.isAlive();
    }
}
