package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import com.ohhapple.monster_conquest.gun.GunAmmoType;
import com.ohhapple.monster_conquest.menu.BackpackMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * 背包（战术装备）：
 *   · 72 格（8 排 × 9 格）容器：右键打开；放到战术装备栏后可按 B 键（按键可改）打开；
 *   · 背包里不能再放背包（套娃）由 {@link BackpackMenu} 的槽位拦截；
 *   · 枪械装填会直接读取"背包里的子弹 + 背包里弹药箱中的子弹"；
 *   · 募兵站 / 交易站等扣材料时也会读取背包里的物品。
 */
public class BackpackItem extends Item {

    /** 总槽位（8 排 × 9 格）。 */
    public static final int SLOTS = 72;
    public static final int ROWS = 8;
    public static final int COLS = 9;

    public BackpackItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
            int slotIndex = hand == InteractionHand.MAIN_HAND
                    ? sp.getInventory().selected
                    : 40;   // 副手槽位
            sp.openMenu(new SimpleMenuProvider(
                            (id, inv, p) -> new BackpackMenu(id, inv,
                                    TacticalEquipmentSlotsData.SOURCE_INVENTORY, slotIndex),
                            Component.translatable("item.monster_conquest.backpack")),
                    buf -> {
                        buf.writeVarInt(TacticalEquipmentSlotsData.SOURCE_INVENTORY);
                        buf.writeVarInt(slotIndex);
                    });
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.monster_conquest.backpack.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.backpack.stored",
                        usedSlots(stack), SLOTS)
                .withStyle(ChatFormatting.GOLD));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    /**
     * 嵌套防护（NeoForge 扩展）：背包不能放进任何"容器类物品"里 ——
     *   潜影盒 / 收纳袋 / 其他模组的容器物品全部拦下（潜影盒槽位、收纳袋、漏斗等都走这个判断），
     *   这样"背包 → 潜影盒 → 背包 → …"的无限套娃链就断了。
     */
    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }

    /** 同上（原版旧入口，仍然覆写一遍保证安全）。 */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /**
     * 背包自己的槽位允许放什么：
     *   · 普通物品：都可以；
     *   · 容器类物品：只允许原版潜影盒 / 原版收纳袋和本模组弹药箱，
     *     其余带容器数据的物品（其它模组的背包 / 容器）一律不放。
     */
    public static boolean slotAccepts(ItemStack stack) {
        if (stack.isEmpty()) return true;
        if (stack.getItem() instanceof BackpackItem) return false;          // 自己的背包：禁止
        boolean vanillaContainer = isShulkerBox(stack) || stack.is(net.minecraft.world.item.Items.BUNDLE);
        if (vanillaContainer) return true;
        if (stack.getItem() instanceof AmmoBoxItem) return true;            // 本模组弹药箱
        // 其它"看起来是容器"的物品（带容器 / 收纳袋数据，例如别的模组的背包）→ 不放
        return !stack.has(DataComponents.CONTAINER)
                && !stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    public static boolean isShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.world.item.BlockItem bi
                && bi.getBlock() instanceof net.minecraft.world.level.block.ShulkerBoxBlock;
    }

    // ===================== 内容读写 =====================

    /**
     * 背包组件里"实际存着的"全部槽位：旧存档槽位数更多时，多出来的内容
     *   靠它做迁移搬运，不能直接当没有。
     */
    public static List<ItemStack> rawContents(ItemStack backpack) {
        List<ItemStack> list = new ArrayList<>();
        if (!(backpack.getItem() instanceof BackpackItem)) return list;
        ItemContainerContents contents = backpack.get(DataComponents.CONTAINER);
        if (contents == null) return list;
        for (int i = 0; i < contents.getSlots(); i++) {
            list.add(contents.getStackInSlot(i));
        }
        return list;
    }

    public static List<ItemStack> contents(ItemStack backpack) {
        List<ItemStack> list = new ArrayList<>(SLOTS);
        for (int i = 0; i < SLOTS; i++) list.add(ItemStack.EMPTY);
        List<ItemStack> stored = rawContents(backpack);
        for (int i = 0; i < Math.min(SLOTS, stored.size()); i++) {
            list.set(i, stored.get(i));
        }
        return list;
    }

    public static void writeContents(ItemStack backpack, List<ItemStack> items) {
        if (!(backpack.getItem() instanceof BackpackItem)) return;
        backpack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    /** 已占用格数（悬浮提示用）。 */
    public static int usedSlots(ItemStack backpack) {
        int n = 0;
        for (ItemStack s : contents(backpack)) {
            if (!s.isEmpty()) n++;
        }
        return n;
    }

    // ===================== 玩家身上携带的背包 =====================

    /**
     * 玩家身上所有背包：战术装备栏里的 + 背包栏里的（返回的 ItemStack 是"就地引用"，
     *   改完内容后调用 {@link #syncAfterChange} 写回）。
     */
    public static List<ItemStack> carried(Player player) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : TacticalEquipmentSlotsData.get(player)) {
            if (s.getItem() instanceof BackpackItem) out.add(s);
        }
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof BackpackItem) out.add(s);
        }
        return out;
    }

    /** 内容被就地修改后：把战术装备栏那份写回附件并同步客户端，背包栏那份标记变更。 */
    public static void syncAfterChange(ServerPlayer player) {
        TacticalEquipmentSlotsData.set(player, TacticalEquipmentSlotsData.get(player));
        TacticalEquipmentSlotsData.sendSync(player);
        player.getInventory().setChanged();
    }

    // ===================== 统计 / 扣除（供募兵站 / 交易站等使用） =====================

    /** 所有背包内容里某种物品的总数。 */
    public static int countItem(Player player, Item item) {
        int n = 0;
        for (ItemStack backpack : carried(player)) {
            for (ItemStack s : contents(backpack)) {
                if (!s.isEmpty() && s.is(item)) n += s.getCount();
            }
        }
        return n;
    }

    /** 从所有背包内容里扣除某种物品，返回实际扣掉的数量。 */
    public static int consumeItem(ServerPlayer player, Item item, int want) {
        int left = want;
        for (ItemStack backpack : carried(player)) {
            if (left <= 0) break;
            List<ItemStack> list = contents(backpack);
            boolean changed = false;
            for (ItemStack s : list) {
                if (left <= 0) break;
                if (s.isEmpty() || !s.is(item)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
                changed = true;
            }
            if (changed) writeContents(backpack, list);
        }
        if (left < want) syncAfterChange(player);
        return want - left;
    }

    // ===================== 备弹统计（枪械读取） =====================

    /** 背包里的子弹 + 背包里弹药箱中的子弹。 */
    public static int countAmmo(Player player, GunAmmoType type) {
        int n = 0;
        for (ItemStack backpack : carried(player)) {
            for (ItemStack s : contents(backpack)) {
                if (type.matches(s)) {
                    n += s.getCount();
                } else if (s.getItem() instanceof AmmoBoxItem) {
                    n += TacticalEquipmentSlotsData.countInBox(s, type);
                }
            }
        }
        return n;
    }

    /** 从背包里的子弹 / 背包里弹药箱中的子弹扣除，返回实际扣掉的数量。 */
    public static int consumeAmmo(ServerPlayer player, GunAmmoType type, int want) {
        int left = want;
        for (ItemStack backpack : carried(player)) {
            if (left <= 0) break;
            List<ItemStack> list = contents(backpack);
            boolean changed = false;
            // 先抠背包里直接放的子弹
            for (ItemStack s : list) {
                if (left <= 0) break;
                if (s.isEmpty() || !type.matches(s)) continue;
                int take = Math.min(left, s.getCount());
                s.shrink(take);
                left -= take;
                changed = true;
            }
            // 再抠背包里弹药箱中的子弹
            for (ItemStack s : list) {
                if (left <= 0) break;
                if (!(s.getItem() instanceof AmmoBoxItem)) continue;
                int got = TacticalEquipmentSlotsData.consumeFromBox(s, type, left);
                if (got > 0) {
                    left -= got;
                    changed = true;
                }
            }
            if (changed) writeContents(backpack, list);
        }
        if (left < want) syncAfterChange(player);
        return want - left;
    }
}
