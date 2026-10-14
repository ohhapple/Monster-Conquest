package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.menu.AmmoBoxMenu;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * 弹药箱：右键打开三排（27 格）弹药容器，枪械可直接从中读取备弹装填。
 */
public class AmmoBoxItem extends Item {

    /** 弹药格数量（3 排 × 9 格）。 */
    public static final int SLOTS = 27;
    public static final int ROWS = 3;

    /** 单格最大堆叠（用于物品条显示"装了多少"）。 */
    public static final int SLOT_CAPACITY = 64;

    public AmmoBoxItem(Properties properties) {
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
                            (id, inv, p) -> new AmmoBoxMenu(id, inv,
                                    TacticalEquipmentSlotsData.SOURCE_INVENTORY, slotIndex),
                            Component.translatable("item.monster_conquest.ammo_box")),
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
        tooltip.add(Component.translatable("item.monster_conquest.ammo_box.hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.ammo_box.stored",
                        ammoCount(stack), SLOTS * SLOT_CAPACITY)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.monster_conquest.ammo_box.tactical_slots_hint")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    // ===================== 存量条（类似耐久条，但只由内容物决定） =====================

    /** 箱内总弹药数。 */
    public static int ammoCount(ItemStack stack) {
        int total = 0;
        for (ItemStack s : TacticalEquipmentSlotsData.boxContents(stack)) {
            total += s.getCount();
        }
        return total;
    }

    /** 装满比例：总弹药 ÷ (27 格 × 64)。 */
    public static float fillRatio(ItemStack stack) {
        int capacity = SLOTS * SLOT_CAPACITY;
        return Mth.clamp(ammoCount(stack) / (float) capacity, 0.0f, 1.0f);
    }

    /**
     * 存量条（原版"收纳袋"同款机制）：
     *   完全不使用原版耐久度组件，所以不会被耐久修补 / 经验修补 /
     *   无法破坏 / 属性修改等外部机制影响 —— 只由箱内装了多少弹药决定。
     */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return ammoCount(stack) > 0;   // 空箱不显示条
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        float ratio = fillRatio(stack);
        if (ratio <= 0.0f) return 0;
        return Math.max(1, Math.min(13, Math.round(13.0f * ratio)));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        // 与原版耐久条同一套配色：空 → 红，满 → 绿
        return Mth.hsvToRgb(fillRatio(stack) / 3.0f, 1.0f, 1.0f);
    }
}
