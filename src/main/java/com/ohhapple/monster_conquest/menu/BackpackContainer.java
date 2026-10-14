package com.ohhapple.monster_conquest.menu;

import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.item.BackpackItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端：把背包物品的容器组件包装成 Container，供菜单槽位读写。
 * 背包既可能在玩家背包里，也可能放在战术装备栏里。
 */
public class BackpackContainer extends SimpleContainer {

    private final ServerPlayer owner;
    private final int source;
    private final int index;
    private boolean loading;

    public BackpackContainer(ServerPlayer owner, int source, int index) {
        super(BackpackItem.SLOTS);
        this.owner = owner;
        this.source = source;
        this.index = index;
        load();
    }

    private ItemStack backpack() {
        return TacticalEquipmentSlotsData.resolveBox(owner, source, index);
    }

    private void load() {
        loading = true;
        List<ItemStack> contents = BackpackItem.contents(backpack());
        for (int i = 0; i < getContainerSize(); i++) {
            setItem(i, contents.get(i));
        }
        loading = false;
        rescueOverflow();
    }

    /**
     * 缩容迁移：存档里可能存有容量更大的旧数据（最多 90 格），读取时截断到当前容量。
     *   多出来的槽位里的东西不能凭空丢掉 —— 能塞进玩家背包就塞，塞不下就丢在脚下，
     *   然后把背包内容按新容量写回，迁移只做一次。
     */
    private void rescueOverflow() {
        ItemStack backpack = backpack();
        List<ItemStack> stored = BackpackItem.rawContents(backpack);
        if (stored.size() <= BackpackItem.SLOTS) return;

        List<ItemStack> keep = new ArrayList<>(stored.subList(0, BackpackItem.SLOTS));
        boolean rescued = false;
        for (int i = BackpackItem.SLOTS; i < stored.size(); i++) {
            ItemStack overflow = stored.get(i);
            if (overflow.isEmpty()) continue;
            rescued = true;
            ItemStack extra = overflow.copy();
            if (!owner.getInventory().add(extra)) {
                owner.drop(extra, false);
            }
        }
        if (!rescued) return;
        BackpackItem.writeContents(backpack, keep);
        TacticalEquipmentSlotsData.storeBox(owner, source, index, backpack);
    }

    @Override
    public void setChanged() {
        if (loading) return;
        List<ItemStack> contents = new ArrayList<>(getContainerSize());
        for (int i = 0; i < getContainerSize(); i++) {
            contents.add(getItem(i));
        }
        ItemStack backpack = backpack();
        BackpackItem.writeContents(backpack, contents);
        TacticalEquipmentSlotsData.storeBox(owner, source, index, backpack);
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && backpack().getItem() instanceof BackpackItem;
    }
}
