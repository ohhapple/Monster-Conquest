package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import com.ohhapple.monster_conquest.equipment.TacticalEquipmentSlotsData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

/**
 * 防弹衣（战术装备）：
 *   · 放入战术装备栏后生效 —— 被子弹 / 炮弹 / 其它弹射物击中时伤害 -70%；
 *   · 每命中一次，按"减免掉的伤害值"扣防弹衣耐久（初始 500），耐久耗尽即损坏消失；
 *   · 只对玩家有效（战术装备栏只有玩家有）。
 */
public class BulletproofVestItem extends Item {

    /** 初始耐久。 */
    public static final int MAX_DURABILITY = 3000;
    /** 弹射物伤害减免比例（70%）。 */
    public static final float PROJECTILE_REDUCTION = 0.7f;

    public BulletproofVestItem(Properties properties) {
        super(properties);
    }

    /** 战术装备栏里的防弹衣槽位下标；没穿返回 -1。 */
    public static int findEquippedSlot(LivingEntity entity) {
        if (!(entity instanceof Player player)) return -1;
        List<ItemStack> list = TacticalEquipmentSlotsData.get(player);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).is(Monster_conquest.BULLETPROOF_VEST.get())) return i;
        }
        return -1;
    }

    /**
     * 处理一次弹射物命中：按 70% 减免，并按减免量扣防弹衣耐久。
     *
     * @return 减免后的伤害（没穿防弹衣时原样返回）
     */
    public static float applyProjectileReduction(ServerPlayer player, float amount) {
        int slot = findEquippedSlot(player);
        if (slot < 0 || amount <= 0f) return amount;

        float reduced = amount * PROJECTILE_REDUCTION;
        float remaining = amount - reduced;
        int loss = Math.max(1, (int) Math.ceil(reduced));

        List<ItemStack> list = new ArrayList<>(TacticalEquipmentSlotsData.get(player));
        ItemStack vest = list.get(slot);
        if (vest.isEmpty()) return amount;

        ItemStack updated = vest.copy();
        int newDamage = updated.getDamageValue() + loss;
        if (newDamage >= updated.getMaxDamage()) {
            // 打坏了：清空该槽并给出提示音
            list.set(slot, ItemStack.EMPTY);
            TacticalEquipmentSlotsData.set(player, list);
            TacticalEquipmentSlotsData.sendSync(player);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.9f, 1.0f);
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.bulletproof_vest.broken"), true);
        } else {
            updated.setDamageValue(newDamage);
            list.set(slot, updated);
            TacticalEquipmentSlotsData.set(player, list);
            TacticalEquipmentSlotsData.sendSync(player);
        }
        return remaining;
    }

    /**
     * 单位版：从单位（士兵 / 首领）的战术装备栏里找防弹衣，按减免量扣耐久；打坏就清掉那一格。
     *   单位没有玩家那种提示音 / 提示文字，安静地坏掉即可。
     */
    public static float applyProjectileReduction(ManagedUnit unit, float amount) {
        int slot = unit.findTactical(stack -> stack.is(Monster_conquest.BULLETPROOF_VEST.get()));
        if (slot < 0 || amount <= 0f) return amount;

        ItemStack vest = unit.getManualTactical(slot);
        if (vest.isEmpty()) return amount;

        float reduced = amount * PROJECTILE_REDUCTION;
        float remaining = amount - reduced;
        ItemStack updated = vest.copy();
        int newDamage = updated.getDamageValue() + Math.max(1, (int) Math.ceil(reduced));
        if (newDamage >= updated.getMaxDamage()) {
            unit.clearTactical(slot);
        } else {
            updated.setDamageValue(newDamage);
            unit.setManualTactical(slot, updated);
        }
        return remaining;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.monster_conquest.bulletproof_vest.hint")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
