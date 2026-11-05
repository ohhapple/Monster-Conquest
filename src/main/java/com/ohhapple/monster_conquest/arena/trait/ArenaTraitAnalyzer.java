package com.ohhapple.monster_conquest.arena.trait;

import com.ohhapple.monster_conquest.arena.game.ArenaUnitCatalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 从当前上阵棋子计算羁绊人数和激活阶级，重复弈子只计一次。 */
public final class ArenaTraitAnalyzer {

    public record Status(ArenaTrait trait, int count, int tier) {
    }

    public record State(Map<ArenaTrait, Status> statuses) {

        private static final Comparator<Status> VISIBLE_ORDER =
                (left, right) -> {
                    boolean leftActive = left.tier() > 0;
                    boolean rightActive = right.tier() > 0;
                    if (leftActive != rightActive) {
                        return leftActive ? -1 : 1;
                    }
                    if (leftActive) {
                        int countOrder = Integer.compare(
                                right.count(), left.count());
                        if (countOrder != 0) return countOrder;
                    }
                    int category = left.trait().category()
                            .compareTo(right.trait().category());
                    if (category != 0) return category;
                    return left.trait().compareTo(right.trait());
                };

        public State {
            statuses = Collections.unmodifiableMap(
                    new EnumMap<>(statuses));
        }

        public int count(ArenaTrait trait) {
            Status status = statuses.get(trait);
            return status == null ? 0 : status.count();
        }

        public int tier(ArenaTrait trait) {
            Status status = statuses.get(trait);
            return status == null ? 0 : status.tier();
        }

        public boolean active(ArenaTrait trait, int threshold) {
            return count(trait) >= threshold;
        }

        public List<Status> activeStatuses() {
            return sortedStatuses(true);
        }

        public List<Status> presentStatuses() {
            return sortedStatuses(false);
        }

        private List<Status> sortedStatuses(boolean activeOnly) {
            return statuses.values().stream()
                    .filter(status -> !activeOnly
                            || status.tier() > 0)
                    .filter(status -> activeOnly
                            || status.count() > 0)
                    .sorted(VISIBLE_ORDER)
                    .toList();
        }
    }

    private ArenaTraitAnalyzer() {
    }

    public static State analyze(List<Integer> rosterUnitIndices) {
        Map<ArenaTrait, Set<String>> unitsByTrait =
                new EnumMap<>(ArenaTrait.class);
        for (int unitIndex : rosterUnitIndices) {
            String unitId =
                    ArenaUnitCatalog.get(unitIndex).id();
            for (ArenaTrait trait :
                    ArenaTraitCatalog.traitsFor(unitIndex)) {
                unitsByTrait.computeIfAbsent(
                        trait, ignored -> new HashSet<>()).add(unitId);
            }
        }
        Map<ArenaTrait, Status> result =
                new EnumMap<>(ArenaTrait.class);
        for (ArenaTrait trait : ArenaTrait.values()) {
            int count = unitsByTrait.getOrDefault(
                    trait, Set.of()).size();
            result.put(trait, new Status(
                    trait, count, trait.tierFor(count)));
        }
        return new State(result);
    }
}
