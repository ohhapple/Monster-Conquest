package com.ohhapple.monster_conquest.equipment;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.gun.GunAmmoType;
import com.ohhapple.monster_conquest.item.AmmoBoxItem;
import com.ohhapple.monster_conquest.network.clientbound.gun.SyncEquipmentPayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 战术装备栏数据读写与备弹统计。
 *
 * 服务端：战术装备栏内容存放在玩家数据附件里；客户端：由同步包缓存一份，
 * 供 HUD 统计备弹使用（背包内容客户端本来就有）。
 */
public final class TacticalEquipmentSlotsData {

    private TacticalEquipmentSlotsData() {}

    public static final int SLOT_COUNT = 18;

    /** 弹药箱来源：玩家背包槽 / 战术装备栏槽。 */
    public static final int SOURCE_INVENTORY = 0;
    public static final int SOURCE_TACTICAL_SLOTS = 1;

    private static List<ItemStack> clientTacticalSlots = newTacticalSlotsList();

    public static List<ItemStack> newTacticalSlotsList() {
        List<ItemStack> list = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) list.add(ItemStack.EMPTY);
        return list;
    }

    // ===================== 战术装备栏 =====================

    public static List<ItemStack> get(Player player) {
        if (player.level().isClientSide) return clientTacticalSlots;
        List<ItemStack> list = player.getData(ModAttachments.TACTICAL_EQUIPMENT_SLOTS.get());
        if (list.size() == SLOT_COUNT) return list;
        // 老存档槽位数不同（例如扩栏前是 6 格）：保留原有内容，缺的补空，不丢装备
        List<ItemStack> migrated = newTacticalSlotsList();
        for (int i = 0; i < Math.min(list.size(), SLOT_COUNT); i++) migrated.set(i, list.get(i));
        player.setData(ModAttachments.TACTICAL_EQUIPMENT_SLOTS.get(), migrated);
        return migrated;
    }

    public static void set(ServerPlayer player, List<ItemStack> list) {
        player.setData(ModAttachments.TACTICAL_EQUIPMENT_SLOTS.get(), List.copyOf(list));
    }

    public static void setClientTacticalSlots(List<ItemStack> list) {
        clientTacticalSlots = List.copyOf(list);
    }

    public static void sendSync(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncEquipmentPayload(get(player)));
    }

    /** 读取指定来源的弹药箱物品。 */
    public static ItemStack resolveBox(Player player, int source, int index) {
        if (source == SOURCE_TACTICAL_SLOTS) {
            List<ItemStack> list = get(player);
            return index >= 0 && index < list.size() ? list.get(index) : ItemStack.EMPTY;
        }
        return player.getInventory().getItem(index);
    }

    /** 把修改后的弹药箱写回指定来源。 */
    public static void storeBox(ServerPlayer player, int source, int index, ItemStack box) {
        if (source == SOURCE_TACTICAL_SLOTS) {
            List<ItemStack> list = new ArrayList<>(get(player));
            if (index >= 0 && index < list.size()) {
                list.set(index, box);
                set(player, list);
            }
        }
        sendSync(player);
    }

    // ===================== 弹药判定 =====================

    public static boolean isAmmo(ItemStack stack) {
        return stack.is(Monster_conquest.RIFLE_AMMO.get())
                || stack.is(Monster_conquest.SHOTGUN_SHELL.get())
                || stack.is(Monster_conquest.SNIPER_AMMO.get())
                || stack.is(Monster_conquest.ROCKET_AMMO.get());
    }

    // ===================== 弹药箱内容 =====================

    public static List<ItemStack> boxContents(ItemStack box) {
        List<ItemStack> list = new ArrayList<>(AmmoBoxItem.SLOTS);
        for (int i = 0; i < AmmoBoxItem.SLOTS; i++) list.add(ItemStack.EMPTY);
        if (!(box.getItem() instanceof AmmoBoxItem)) return list;

        ItemContainerContents contents = box.get(DataComponents.CONTAINER);
        if (contents == null) return list;
        for (int i = 0; i < Math.min(AmmoBoxItem.SLOTS, contents.getSlots()); i++) {
            list.set(i, contents.getStackInSlot(i));
        }
        return list;
    }

    public static void writeBoxContents(ItemStack box, List<ItemStack> items) {
        if (!(box.getItem() instanceof AmmoBoxItem)) return;
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    public static int countInBox(ItemStack box, GunAmmoType type) {
        int count = 0;
        for (ItemStack s : boxContents(box)) {
            if (type.matches(s)) count += s.getCount();
        }
        return count;
    }

    /** 从弹药箱里扣除弹药，返回实际扣掉的数量。 */
    public static int consumeFromBox(ItemStack box, GunAmmoType type, int want) {
        if (!(box.getItem() instanceof AmmoBoxItem) || want <= 0) return 0;
        List<ItemStack> items = boxContents(box);
        int taken = 0;
        for (ItemStack s : items) {
            if (taken >= want) break;
            if (!type.matches(s)) continue;
            int take = Math.min(want - taken, s.getCount());
            s.shrink(take);
            taken += take;
        }
        if (taken > 0) writeBoxContents(box, items);
        return taken;
    }

    // ===================== 备弹统计 / 消耗 =====================

    /** 备弹 = 背包栏里的弹药 + 战术装备栏里所有弹药箱中的弹药 + 背包（物品）里的弹药。 */
    public static int countReserve(Player player, GunAmmoType type) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (type.matches(s)) count += s.getCount();
        }
        for (ItemStack box : get(player)) {
            if (box.getItem() instanceof AmmoBoxItem) count += countInBox(box, type);
        }
        // 背包（物品）里的子弹 + 背包里弹药箱中的子弹
        count += com.ohhapple.monster_conquest.item.BackpackItem.countAmmo(player, type);
        return count;
    }

    /** 优先从战术装备栏的弹药箱扣，再从背包扣。 */
    public static int consumeReserve(ServerPlayer player, GunAmmoType type, int want) {
        int taken = 0;

        List<ItemStack> equipment = new ArrayList<>(get(player));
        boolean changed = false;
        for (ItemStack box : equipment) {
            if (taken >= want) break;
            if (!(box.getItem() instanceof AmmoBoxItem)) continue;
            int got = consumeFromBox(box, type, want - taken);
            if (got > 0) {
                taken += got;
                changed = true;
            }
        }
        if (changed) set(player, equipment);
        if (changed) sendSync(player);

        // 再从背包（物品）里扣（背包里的散装子弹 + 背包里弹药箱中的子弹）
        if (taken < want) {
            taken += com.ohhapple.monster_conquest.item.BackpackItem
                    .consumeAmmo(player, type, want - taken);
        }

        if (taken < want) {
            for (int i = 0; i < player.getInventory().getContainerSize() && taken < want; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (!type.matches(s)) continue;
                int take = Math.min(want - taken, s.getCount());
                s.shrink(take);
                taken += take;
            }
        }
        return taken;
    }
}
