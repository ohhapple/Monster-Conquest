package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 仅供竞技 GUI 和头顶装备图标使用的隐藏物品注册表。 */
public final class ArenaEquipmentItems {

    private static final Map<ArenaEquipment, DeferredItem<Item>> ITEMS =
            registerItems();

    private ArenaEquipmentItems() {
    }

    public static void registerAll() {
        // 触发静态初始化，使全部隐藏装备物品先加入共享 ITEMS 注册器。
    }

    private static Map<ArenaEquipment, DeferredItem<Item>> registerItems() {
        EnumMap<ArenaEquipment, DeferredItem<Item>> items =
                new EnumMap<>(ArenaEquipment.class);
        for (ArenaEquipment equipment : ArenaEquipment.values()) {
            DeferredItem<Item> item = Monster_conquest.ITEMS.register(
                    equipment.itemId(),
                    () -> new ArenaEquipmentItem(equipment));
            items.put(equipment, item);
        }
        return Map.copyOf(items);
    }

    public static ItemStack stack(ArenaEquipment equipment) {
        return new ItemStack(ITEMS.get(equipment).get());
    }

    private static final class ArenaEquipmentItem extends Item {
        private final ArenaEquipment equipment;

        private ArenaEquipmentItem(ArenaEquipment equipment) {
            super(new Item.Properties()
                    .stacksTo(1)
                    .rarity(equipment.component()
                            ? Rarity.COMMON : Rarity.RARE)
                    .component(DataComponents.CUSTOM_NAME,
                            Component.translatable(
                                    equipment.translationKey())));
            this.equipment = equipment;
        }

        @Override
        public void appendHoverText(
                ItemStack stack,
                TooltipContext context,
                List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable(
                            equipment.descriptionKey())
                    .withStyle(ChatFormatting.GRAY));
            super.appendHoverText(stack, context, tooltip, flag);
        }
    }
}
