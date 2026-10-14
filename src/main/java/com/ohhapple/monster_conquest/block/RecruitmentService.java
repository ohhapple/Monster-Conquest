package com.ohhapple.monster_conquest.block;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.army.RecruitableSoldiers;
import com.ohhapple.monster_conquest.config.RecruitConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.item.RetrievalToolItem;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 募兵站的服务端逻辑：校验距离与材料 → 扣除 → 在"生成面"紧贴的一侧生成
 *   玩家阵营士兵（带所选等级装备），并默认设为硬驻守（锚点=出生位置）。
 */
public final class RecruitmentService {

    private RecruitmentService() {}

    /** 招募时玩家离方块的最大距离（格）。 */
    private static final double MAX_USE_DIST_SQ = 8.0 * 8.0;

    /** 处理客户端的一次招募请求。 */
    public static void recruit(ServerPlayer player, BlockPos pos, int soldierIndex, int tierIndex) {
        if (soldierIndex < 0 || soldierIndex >= RecruitableSoldiers.ALL.size()) return;
        if (tierIndex < 0 || tierIndex >= EquipmentTier.values().length) return;
        BlockState state = validStation(player, pos);
        if (state == null) return;
        ServerLevel level = player.serverLevel();

        // 军费：绿宝石 + 该等级的金属（每个兵种在配置里单独定价），从背包 + 副手扣。
        RecruitableSoldiers.Entry entry = RecruitableSoldiers.ALL.get(soldierIndex);
        Item emerald = RecruitConfig.EMERALD_CURRENCY;
        int emeraldCount = RecruitConfig.emeraldCost(entry.id(), tierIndex);
        Item metal = RecruitConfig.metalItemFor(tierIndex);
        int metalCount = RecruitConfig.metalCost(entry.id(), tierIndex);
        if (countItem(player, emerald) < emeraldCount
                || countItem(player, metal) < metalCount) {
            Item missing = countItem(player, emerald) < emeraldCount ? emerald : metal;
            int missingCount = missing == emerald ? emeraldCount : metalCount;
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.recruit.not_enough",
                            new ItemStack(missing).getHoverName(), missingCount),
                    true);
            return;
        }
        consumeItem(player, emerald, emeraldCount);
        consumeItem(player, metal, metalCount);

        Direction facing = state.getValue(RecruitmentStationBlock.FACING);
        EquipmentTier tier = EquipmentTier.values()[tierIndex];

        AbstractArmySoldier soldier = spawnRecruit(level, pos, facing, entry, tier,
                PlayerFactionRegistry.factionIdOf(player), player.getUUID());
        if (soldier == null) return;

        // 不用村民叫声：用"穿戴装备"的金属声代替
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, 1.0f, 1.0f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.recruit.success",
                        soldier.getDisplayName(), tierLabel(tier)),
                true);
    }

    /**
     * 处理客户端的一次"升级"请求：
     *   支付低一档的刷怪蛋 ×1 + 目标档招募价格的一半 → 直接召唤升级后的士兵。
     */
    public static void upgrade(ServerPlayer player, BlockPos pos, int soldierIndex, int tierIndex) {
        if (soldierIndex < 0 || soldierIndex >= RecruitableSoldiers.ALL.size()) return;
        if (tierIndex < 1 || tierIndex >= EquipmentTier.values().length) return;
        BlockState state = validStation(player, pos);
        if (state == null) return;
        ServerLevel level = player.serverLevel();

        RecruitableSoldiers.Entry entry = RecruitableSoldiers.ALL.get(soldierIndex);
        Item egg = eggItemFor(entry.id(), tierIndex - 1);
        if (egg == null) return;
        Item emerald = RecruitConfig.EMERALD_CURRENCY;
        Item metal = RecruitConfig.metalItemFor(tierIndex);
        int emeraldCount = halfUp(RecruitConfig.emeraldCost(entry.id(), tierIndex));
        int metalCount = halfUp(RecruitConfig.metalCost(entry.id(), tierIndex));

        if (countItem(player, egg) < 1) {
            notEnough(player, egg, 1);
            return;
        }
        if (countItem(player, emerald) < emeraldCount) {
            notEnough(player, emerald, emeraldCount);
            return;
        }
        if (countItem(player, metal) < metalCount) {
            notEnough(player, metal, metalCount);
            return;
        }
        // 只能升级自己的士兵：蛋里带着阵营数据时，必须是操作者自己的玩家阵营
        ItemStack eggStack = findEggStack(player, egg);
        if (eggStack == null || !isOwnEgg(player, eggStack)) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.recruit.upgrade_not_own"), true);
            return;
        }
        consumeItem(player, egg, 1);
        consumeItem(player, emerald, emeraldCount);
        consumeItem(player, metal, metalCount);

        Direction facing = state.getValue(RecruitmentStationBlock.FACING);
        EquipmentTier tier = EquipmentTier.values()[tierIndex];
        CompoundTag eggTag = eggStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        AbstractArmySoldier soldier = spawnRecruit(level, pos, facing, entry, tier,
                PlayerFactionRegistry.factionIdOf(player), player.getUUID());
        if (soldier == null) return;
        // 升级蛋里保留的信息（手动装备 / 战术装备 / 骑乘形态）一并继承到新单位身上
        soldier.readGearTag(eggTag);
        // 骑乘形态：先只标记成骑兵（待命期间照旧不上马，接到命令 / 待命结束后会自己上马）
        if (eggTag.getBoolean(RetrievalToolItem.KEY_CAVALRY)
                && !soldier.getSoldierType().isCavalry()) {
            soldier.setSoldierType(soldier.getSoldierType().toggleMounted());
        }

        level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 1.2f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.recruit.upgraded",
                        soldier.getDisplayName(), tierLabel(tier)),
                true);
    }

    /** 校验"操作者离方块够近 + 方块还是募兵站"；不合法返回 null。 */
    @Nullable
    private static BlockState validStation(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof RecruitmentStationBlock)) return null;
        if (player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)
                > MAX_USE_DIST_SQ) {
            return null;
        }
        return state;
    }

    /** 士兵刷怪蛋物品（soldier_<兵种>_tier_<n>）；不存在返回 null。 */
    @Nullable
    private static Item eggItemFor(String speciesId, int tier) {
        ResourceLocation rl = ResourceLocation.fromNamespaceAndPath(
                com.ohhapple.monster_conquest.Monster_conquest.MODID,
                speciesId + "_tier_" + tier);
        Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        return (item == null || item == Items.AIR) ? null : item;
    }

    /** 一半价格（向上取整，最少 1）。 */
    private static int halfUp(int value) {
        return Math.max(1, (value + 1) / 2);
    }

    private static void notEnough(ServerPlayer player, Item item, int count) {
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.recruit.not_enough",
                        new ItemStack(item).getHoverName(), count),
                true);
    }

    /**
     * 在生成面紧贴的一侧生成一个玩家阵营士兵（带等级装备 + 默认硬驻守）。
     *   供招募请求使用。
     *
     * @param factionId 该士兵归属的玩家阵营 id
     * @param ownerUuid 该士兵的指挥官（玩家）UUID
     */
    @Nullable
    public static AbstractArmySoldier spawnRecruit(ServerLevel level, BlockPos pos, Direction facing,
                                                    RecruitableSoldiers.Entry entry, EquipmentTier tier,
                                                    ResourceLocation factionId, UUID ownerUuid) {
        if (!(entry.type().get().create(level) instanceof AbstractArmySoldier soldier)) return null;

        float yaw = facing.toYRot();
        BlockPos face = pos.relative(facing);
        BlockPos[] candidates = {face, face.above(), face.below(), face.above(2), pos.above(2)};
        for (BlockPos c : candidates) {
            soldier.moveTo(c.getX() + 0.5, c.getY(), c.getZ() + 0.5, yaw, 0.0f);
            if (level.noCollision(soldier)) break;
        }

        soldier.setSoldierType(entry.form());
        soldier.setTier(tier);
        soldier.applyEquipment(tier);
        soldier.setHealth(soldier.getMaxHealth());
        soldier.setFactionId(factionId);
        FactionRegistry.getOrCreatePlayer(ownerUuid);
        soldier.setCommanderUuid(ownerUuid);
        soldier.setSquadId(0);
        soldier.markPlayerCommanded();
        level.addFreshEntity(soldier);

        // 默认硬驻守：锚点就是它的出生位置（生成面旁）。
        PlayerCommandState st = PlayerArmyManager.get(ownerUuid);
        long now = level.getGameTime();
        PlayerCommandState.stateForCommand(soldier, st)
                .applyMoveOrder(soldier.position(), yaw, now);
        // 待命：编入队伍 / 接到命令前保持步兵（不自动上马）
        PlayerCommandState.stateForCommand(soldier, st)
                .setRideMode(PlayerCommandState.RideMode.FORCED_INFANTRY);
        soldier.markRecruitStandby(soldier.position());
        st.issueOrder(now);
        return soldier;
    }

    /** 等级显示名（沿用兵种蛋的 T0~T4 叫法）。 */
    private static Component tierLabel(EquipmentTier tier) {
        return Component.literal("T" + tier.ordinal());
    }

    /** 统计玩家（背包 36 格 + 副手）里某种物品的数量。 */
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

    /** 找到玩家身上（背包 36 格 / 副手 / 背包物品里）第一颗指定刷怪蛋；没有返回 null。 */
    @Nullable
    private static ItemStack findEggStack(ServerPlayer player, Item item) {
        ItemStack off = player.getInventory().getItem(40);
        if (off.is(item)) return off;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) return stack;
        }
        for (ItemStack backpack : com.ohhapple.monster_conquest.item.BackpackItem.carried(player)) {
            for (ItemStack stack : com.ohhapple.monster_conquest.item.BackpackItem.contents(backpack)) {
                if (stack.is(item)) return stack;
            }
        }
        return null;
    }

    /**
     * 升级用的蛋是否属于该玩家：
     *   · 蛋里带阵营数据（收纳工具收来的 / 其它阵营的）时，必须是操作者自己的玩家阵营；
     *   · 没有阵营数据的普通刷怪蛋（创造物品栏直接拿的）视为可升级。
     */
    private static boolean isOwnEgg(ServerPlayer player, ItemStack eggStack) {
        CompoundTag tag = eggStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String raw = tag.getString(RetrievalToolItem.KEY_FACTION);
        if (raw.isEmpty()) return true;
        ResourceLocation faction = ResourceLocation.tryParse(raw);
        if (faction == null) return true;
        ResourceLocation own = PlayerFactionRegistry.factionIdOf(player);
        return faction.equals(own);
    }

    /** 从背包 36 格 + 副手 + 背包（物品）里扣除指定数量（调用前已确认足够）。 */
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
