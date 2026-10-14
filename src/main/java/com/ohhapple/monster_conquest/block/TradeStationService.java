package com.ohhapple.monster_conquest.block;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * 交易站的服务端逻辑：固定的出售清单（价格与合成表一致）+ 批量购买。
 *   火箭弹合成配方受 3×3 工作台限制，用 5 火药 + 4 铜锭；
 *   交易价格固定为 5 火药 + 5 铜锭。
 */
public final class TradeStationService {

    private TradeStationService() {}

    /** 一种支付材料。 */
    public record Price(Item item, int count) {}

    /** 一个出售项：物品 + 单件价格。 */
    public record Offer(Item item, int count, List<Price> price) {}

    private static Price p(Item item, int count) { return new Price(item, count); }

    /** 出售清单（顺序 = GUI 顺序）。 */
    public static final List<Offer> OFFERS = List.of(
            new Offer(Monster_conquest.RIFLE_AMMO.get(), 1,
                    List.of(p(Items.COPPER_INGOT, 1), p(Items.GUNPOWDER, 1))),
            new Offer(Monster_conquest.SHOTGUN_SHELL.get(), 1,
                    List.of(p(Items.COPPER_INGOT, 1), p(Items.GUNPOWDER, 1), p(Items.IRON_INGOT, 1))),
            new Offer(Monster_conquest.SNIPER_AMMO.get(), 1,
                    List.of(p(Items.COPPER_INGOT, 3), p(Items.GUNPOWDER, 2))),
            new Offer(Monster_conquest.ROCKET_AMMO.get(), 1,
                    List.of(p(Items.GUNPOWDER, 5), p(Items.COPPER_INGOT, 4))),

            new Offer(Monster_conquest.RIFLE.get(), 1, List.of(p(Items.NETHERITE_INGOT, 9))),
            new Offer(Monster_conquest.SHOTGUN.get(), 1, List.of(p(Items.NETHERITE_INGOT, 9))),
            new Offer(Monster_conquest.SNIPER_RIFLE.get(), 1, List.of(p(Items.NETHERITE_INGOT, 9))),
            new Offer(Monster_conquest.ROCKET_LAUNCHER.get(), 1, List.of(p(Items.NETHERITE_INGOT, 9))),

            new Offer(Monster_conquest.RETRIEVAL_TOOL.get(), 1,
                    List.of(p(Items.STICK, 1), p(Items.BLUE_DYE, 1))),
            new Offer(Monster_conquest.CONTROL_TOOL.get(), 1,
                    List.of(p(Items.STICK, 1), p(Items.YELLOW_DYE, 1))),
            new Offer(Monster_conquest.GUIDE_BOOK.get(), 1, List.of(p(Items.BOOK, 1))),
            new Offer(Monster_conquest.AMMO_BOX.get(), 1, List.of(p(Items.IRON_INGOT, 8))),
            new Offer(Monster_conquest.HORSE_WHISTLE.get(), 1, List.of(p(Items.SADDLE, 1))),
            new Offer(Monster_conquest.WARM_CLOTHES.get(), 1,
                    List.of(p(Items.NETHERITE_INGOT, 8), p(Items.BLAZE_ROD, 1))),
            new Offer(Monster_conquest.BULLETPROOF_VEST.get(), 1, List.of(p(Items.IRON_INGOT, 8))),
            new Offer(Monster_conquest.BACKPACK.get(), 1,
                    List.of(p(Items.LEATHER, 6), p(Items.CHEST, 1)))
    );

    private static final double MAX_USE_DIST_SQ = 8.0 * 8.0;

    /** 处理客户端的一次购买请求。 */
    public static void buy(ServerPlayer player, BlockPos pos, int offerIndex, int quantity) {
        if (offerIndex < 0 || offerIndex >= OFFERS.size()) return;
        if (quantity != 1 && quantity != 8 && quantity != 64) return;

        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof TradeStationBlock)) return;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_USE_DIST_SQ) {
            return;
        }

        Offer offer = OFFERS.get(offerIndex);
        for (Price price : offer.price()) {
            int need = price.count() * quantity;
            if (countItem(player, price.item()) < need) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.trade.not_enough",
                                new ItemStack(price.item()).getHoverName(), need),
                        true);
                return;
            }
        }
        for (Price price : offer.price()) {
            consumeItem(player, price.item(), price.count() * quantity);
        }
        give(player, offer, quantity);

        // 不用村民叫声：用"拾取经验"的清脆声代替购买音效
        level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.9f, 1.1f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.trade.bought",
                        new ItemStack(offer.item()).getHoverName(), quantity),
                true);
    }

    private static void give(ServerPlayer player, Offer offer, int quantity) {
        int remaining = offer.count() * quantity;
        int max = new ItemStack(offer.item()).getMaxStackSize();
        while (remaining > 0) {
            int n = Math.min(remaining, max);
            ItemStack stack = new ItemStack(offer.item(), n);
            if (!player.getInventory().add(stack)) player.drop(stack, false);
            remaining -= n;
        }
    }

    private static int countItem(ServerPlayer player, Item item) {
        int n = countIn(player.getInventory().getItem(40), item);   // 副手
        for (int i = 0; i < 36; i++) {
            n += countIn(player.getInventory().getItem(i), item);
        }
        // 背包（物品）里的物品也算
        n += com.ohhapple.monster_conquest.item.BackpackItem.countItem(player, item);
        return n;
    }

    private static int countIn(ItemStack stack, Item item) {
        return stack.is(item) ? stack.getCount() : 0;
    }

    private static void consumeItem(ServerPlayer player, Item item, int count) {
        int[] slots = new int[37];
        for (int i = 0; i < 36; i++) slots[i] = i;
        slots[36] = 40;
        int left = count;
        for (int slot : slots) {
            if (left <= 0) break;
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
        }
        player.getInventory().setChanged();
        if (left > 0) {
            com.ohhapple.monster_conquest.item.BackpackItem.consumeItem(player, item, left);
        }
    }
}
