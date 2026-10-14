package com.ohhapple.monster_conquest.equipment;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.List;

/**
 * 战术装备栏里的"经验修补"：
 *   · 原版经验修补只认穿在身上的装备（护甲 / 双手），放在战术装备栏里的防弹衣等
 *     物品拿不到经验 —— 这里补上；
 *   · 顺序与原版一致：先让原版把身上的装备修完，剩下的经验再补给战术装备栏
 *     （原版结算完经验后会在同一个调用链里发 XpChange，在该处接手）；
 *   · 修补公式与原版 ExperienceOrb 完全一致（每点经验修多少耐久、剩余经验怎么算都照抄），
 *     消耗的经验从这次到手的经验里扣，不会凭空多给经验；
 *   · 只认"带经验修补 + 已损伤"的物品；战术装备栏里没有可修的东西时一动不动，
 *     原版流程照旧。
 */
public final class TacticalMending {

    private TacticalMending() {}

    /** 正在等经验结算的玩家：拾取经验球时打标记，同一个 tick 的 XpChange 里取走。 */
    private static ServerPlayer pendingOrbPlayer;
    private static int pendingOrbTick = -1;

    /** 拾取经验球（PlayerXpEvent.PickupXp）时调用：等原版结算完，再来补修战术装备栏。 */
    public static void noteOrbPickup(ServerPlayer player) {
        pendingOrbPlayer = player;
        pendingOrbTick = player.tickCount;
    }

    /** 取出标记：只有"同一个玩家、同一个 tick"才算这次经验球的经验（同一调用链内）。 */
    public static boolean consumeOrbPickup(ServerPlayer player) {
        boolean fromOrbPickup = pendingOrbPlayer == player && pendingOrbTick == player.tickCount;
        pendingOrbPlayer = null;
        pendingOrbTick = -1;
        return fromOrbPickup;
    }

    /** 玩家下线：把可能残留的标记清掉，不抱着玩家对象不放。 */
    public static void clear(ServerPlayer player) {
        if (pendingOrbPlayer == player) {
            pendingOrbPlayer = null;
            pendingOrbTick = -1;
        }
    }

    /**
     * 用经验修补战术装备栏里的物品（可修多件，经验按原版规则依次分摊）。
     *
     * @param xp 本次可用的经验值
     * @return 实际消耗掉的经验值（0 = 战术装备栏里没有可修的东西）
     */
    public static int repair(ServerPlayer player, int xp) {
        if (xp <= 0) return 0;

        List<ItemStack> equipment = new ArrayList<>(TacticalEquipmentSlotsData.get(player));
        int remaining = xp;
        boolean changed = false;

        while (remaining > 0) {
            int index = randomDamagedMendingSlot(player, equipment);
            if (index < 0) break;
            ItemStack stack = equipment.get(index);

            // 与原版 ExperienceOrb#repairPlayerItems 相同的算法
            int repairAmount = EnchantmentHelper.modifyDurabilityToRepairFromXp(
                    player.serverLevel(), stack, (int) (remaining * stack.getXpRepairRatio()));
            int repaired = Math.min(repairAmount, stack.getDamageValue());
            if (repaired <= 0) break;

            stack.setDamageValue(stack.getDamageValue() - repaired);
            changed = true;

            int leftover = remaining - repaired * remaining / repairAmount;
            if (leftover >= remaining) break;   // 经验一点没消耗（极端取整）：停手，避免死循环
            remaining = leftover;
        }

        if (changed) {
            TacticalEquipmentSlotsData.set(player, equipment);
            TacticalEquipmentSlotsData.sendSync(player);
        }
        return xp - remaining;
    }

    /** 在"带经验修补 + 已损伤"的槽位里随机挑一个（与原版一样随机）；没有可修的返回 -1。 */
    private static int randomDamagedMendingSlot(ServerPlayer player, List<ItemStack> equipment) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < equipment.size(); i++) {
            if (isDamagedMending(equipment.get(i))) candidates.add(i);
        }
        if (candidates.isEmpty()) return -1;
        return candidates.get(player.getRandom().nextInt(candidates.size()));
    }

    private static boolean isDamagedMending(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamaged()) return false;
        ItemEnchantments enchants = stack.getOrDefault(
                DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> holder : enchants.keySet()) {
            if (holder.is(Enchantments.MENDING)) return true;
        }
        return false;
    }
}
