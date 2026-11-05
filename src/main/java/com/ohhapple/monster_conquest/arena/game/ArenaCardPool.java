package com.ohhapple.monster_conquest.arena.game;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 一局游戏内所有真人与 AI 共用的具体弈子牌池。 */
public final class ArenaCardPool {

    private final Map<Integer, Integer> remainingByUnit = new HashMap<>();

    public ArenaCardPool() {
        for (int index = 0; index < ArenaUnitCatalog.ALL.size(); index++) {
            int cost = ArenaUnitCatalog.ALL.get(index).cost();
            remainingByUnit.put(index, copiesForCost(cost));
        }
    }

    public int remaining(int unitIndex) {
        return remainingByUnit.getOrDefault(unitIndex, 0);
    }

    public int remainingForCost(int cost) {
        int total = 0;
        for (Map.Entry<Integer, Integer> entry : remainingByUnit.entrySet()) {
            if (ArenaUnitCatalog.get(entry.getKey()).shopEligible()
                    && ArenaUnitCatalog.get(entry.getKey()).cost() == cost) {
                total += entry.getValue();
            }
        }
        return total;
    }

    public boolean take(int unitIndex) {
        if (unitIndex < 0 || unitIndex >= ArenaUnitCatalog.ALL.size()) {
            return false;
        }
        int remaining = remaining(unitIndex);
        if (remaining <= 0) return false;
        remainingByUnit.put(unitIndex, remaining - 1);
        return true;
    }

    public void returnCards(int unitIndex, int amount) {
        if (unitIndex < 0 || unitIndex >= ArenaUnitCatalog.ALL.size()
                || amount <= 0) {
            return;
        }
        int limit = copiesForCost(ArenaUnitCatalog.get(unitIndex).cost());
        remainingByUnit.put(unitIndex,
                Math.min(limit, remaining(unitIndex) + amount));
    }

    public int rollIndex(int playerLevel, SecureRandom random) {
        int[] weights = ArenaUnitCatalog.costWeights(playerLevel);
        int totalWeight = 0;
        for (int cost = 1; cost <= weights.length; cost++) {
            if (hasCost(cost)) totalWeight += weights[cost - 1];
        }
        if (totalWeight <= 0) return -1;

        int roll = random.nextInt(totalWeight);
        int selectedCost = -1;
        for (int cost = 1; cost <= weights.length; cost++) {
            if (!hasCost(cost)) continue;
            int weight = weights[cost - 1];
            if (roll < weight) {
                selectedCost = cost;
                break;
            }
            roll -= weight;
        }
        if (selectedCost < 0) return -1;

        List<Integer> candidates = new ArrayList<>();
        for (int index = 0; index < ArenaUnitCatalog.ALL.size(); index++) {
            if (ArenaUnitCatalog.ALL.get(index).shopEligible()
                    && ArenaUnitCatalog.ALL.get(index).cost() == selectedCost
                    && remaining(index) > 0) {
                candidates.add(index);
            }
        }
        if (candidates.isEmpty()) return -1;
        return candidates.get(random.nextInt(candidates.size()));
    }

    private boolean hasCost(int cost) {
        for (int index = 0; index < ArenaUnitCatalog.ALL.size(); index++) {
            if (ArenaUnitCatalog.ALL.get(index).shopEligible()
                    && ArenaUnitCatalog.ALL.get(index).cost() == cost
                    && remaining(index) > 0) {
                return true;
            }
        }
        return false;
    }

    private static int copiesForCost(int cost) {
        return switch (cost) {
            case 1 -> 30;
            case 2 -> 25;
            case 3 -> 18;
            case 4 -> 10;
            case 5 -> 9;
            default -> 0;
        };
    }
}
