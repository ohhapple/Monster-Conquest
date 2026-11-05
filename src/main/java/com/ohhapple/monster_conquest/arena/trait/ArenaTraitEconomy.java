package com.ohhapple.monster_conquest.arena.trait;

import com.ohhapple.monster_conquest.arena.game.ArenaEquipment;
import com.ohhapple.monster_conquest.arena.game.ArenaPiece;
import com.ohhapple.monster_conquest.arena.game.ArenaPlayerState;
import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.List;

/** 只对拥有实际玩家状态的参战者结算的经济类羁绊。 */
public final class ArenaTraitEconomy {

    public record CaravanSettlement(
            int tier, int previousStacks, int stacks,
            int gold, int lesserDuplicators,
            List<ArenaEquipment> components,
            List<ArenaEquipment> specialEquipment,
            int duplicators) {

        public CaravanSettlement {
            components = List.copyOf(components);
            specialEquipment = List.copyOf(specialEquipment);
        }
    }

    private static final List<ArenaEquipment> BASIC_COMPONENTS =
            Arrays.stream(ArenaEquipment.values())
                    .filter(ArenaEquipment::component)
                    .toList();
    private static final List<ArenaEquipment> SPECIAL_EQUIPMENT =
            Arrays.stream(ArenaEquipment.values())
                    .filter(item -> item.category()
                            == ArenaEquipment.Category.SPECIAL)
                    .toList();

    private record TierRules(
            int cap, int goldPerStack, int bonusGold,
            int lesserThreshold, int lesserCount,
            int componentThreshold, int componentCount,
            int specialThreshold, int specialCount,
            int duplicatorThreshold, int duplicatorBase,
            boolean duplicatorPerExtraStack) {
    }

    private ArenaTraitEconomy() {
    }

    public static CaravanSettlement settleCaravan(
            ArenaPlayerState player, List<ArenaPiece> roster,
            boolean won, RandomSource random) {
        int tier = ArenaTraitAnalyzer.analyze(
                        roster.stream()
                                .map(ArenaPiece::unitIndex)
                                .toList())
                .tier(ArenaTrait.CARAVAN);
        int previous = player.caravanStacks();
        if (tier <= 0) {
            return new CaravanSettlement(
                    tier, previous, previous, 0,
                    0, List.of(), List.of(), 0);
        }
        if (!won) {
            int stacks = Math.min(rulesFor(tier).cap(),
                    previous + 1);
            return new CaravanSettlement(
                    tier, previous, stacks, 0,
                    0, List.of(), List.of(), 0);
        }
        if (previous <= 0) {
            return new CaravanSettlement(
                    tier, previous, 0, 0,
                    0, List.of(), List.of(), 0);
        }

        TierRules rules = rulesFor(tier);
        int gold = previous * rules.goldPerStack()
                + rules.bonusGold();
        int lesserDuplicators = rewardCount(
                previous, rules.lesserThreshold(),
                rules.lesserCount(), false);
        int componentCount = rewardCount(
                previous, rules.componentThreshold(),
                rules.componentCount(), false);
        int specialCount = rewardCount(
                previous, rules.specialThreshold(),
                rules.specialCount(), false);
        int duplicators = rewardCount(
                previous, rules.duplicatorThreshold(),
                rules.duplicatorBase(),
                rules.duplicatorPerExtraStack());
        return new CaravanSettlement(
                tier, previous, 0, gold,
                lesserDuplicators,
                randomEquipment(BASIC_COMPONENTS,
                        componentCount, random),
                randomEquipment(SPECIAL_EQUIPMENT,
                        specialCount, random),
                duplicators);
    }

    /**
     * 将已计算好的商队结果一次性写入玩家状态。调用时机固定为
     * 下一准备阶段开始；经济与展示物因此使用同一份结算快照。
     */
    public static void applyCaravanSettlement(
            ArenaPlayerState player,
            CaravanSettlement settlement) {
        if (player == null || settlement == null) return;
        player.setCaravanStacks(settlement.stacks());
        if (settlement.gold() > 0) {
            player.addGold(settlement.gold());
        }
        int lesserDuplicators = settlement.lesserDuplicators();
        for (int i = 0; i < lesserDuplicators; i++) {
            player.grantEquipmentReward(
                    ArenaEquipment.LESSER_DUPLICATOR);
        }
        for (ArenaEquipment component : settlement.components()) {
            player.grantEquipmentReward(
                    component);
        }
        for (ArenaEquipment special
                : settlement.specialEquipment()) {
            player.grantEquipmentReward(
                    special);
        }
        for (int i = 0; i < settlement.duplicators(); i++) {
            player.grantEquipmentReward(
                    ArenaEquipment.DUPLICATOR);
        }
    }

    private static TierRules rulesFor(int tier) {
        return switch (tier) {
            case 1 -> new TierRules(
                    6, 2, 0,
                    3, 1, 5, 1, 6, 1,
                    Integer.MAX_VALUE, 0, false);
            case 2 -> new TierRules(
                    18, 3, 1,
                    3, 2, 5, 2, 7, 2,
                    8, 2, false);
            case 3 -> new TierRules(
                    24, 4, 0,
                    2, 3, 4, 3, 6, 3,
                    8, 1, true);
            case 4 -> new TierRules(
                    999, 5, 3,
                    2, 4, 3, 4, 5, 4,
                    7, 2, true);
            default -> throw new IllegalArgumentException(
                    "Unsupported Caravan tier: " + tier);
        };
    }

    private static int rewardCount(
            int stacks, int threshold, int baseCount,
            boolean perExtraStack) {
        if (threshold == Integer.MAX_VALUE
                || baseCount <= 0 || stacks < threshold) {
            return 0;
        }
        return baseCount
                + (perExtraStack ? stacks - threshold : 0);
    }

    private static List<ArenaEquipment> randomEquipment(
            List<ArenaEquipment> source, int count,
            RandomSource random) {
        if (count <= 0 || source.isEmpty()) return List.of();
        List<ArenaEquipment> rewards =
                new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rewards.add(source.get(
                    random.nextInt(source.size())));
        }
        return List.copyOf(rewards);
    }
}
