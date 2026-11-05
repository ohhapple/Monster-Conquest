package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredItem;

/** 竞技场掉落物仅用于实体渲染，不进入生存背包或创造物品栏。 */
public final class ArenaDropItems {

    public static final DeferredItem<Item> GOLD_COIN =
            Monster_conquest.ITEMS.register(
                    "arena_gold_coin",
                    () -> new Item(new Item.Properties()
                            .component(DataComponents.CUSTOM_NAME,
                                    Component.translatableWithFallback(
                                            "item.monster_conquest.arena_gold_coin",
                                            "Arena Gold"))));

    private ArenaDropItems() {
    }

    public static void registerAll() {
        // 触发静态注册。
    }

    public static ItemStack goldCoin() {
        return new ItemStack(GOLD_COIN.get());
    }
}
