package com.ohhapple.monster_conquest.arena.trait;

import java.util.Arrays;
import java.util.Locale;

/** 自走棋羁绊定义；阈值只描述激活断点，不包含弈子或战斗实现。 */
public enum ArenaTrait {
    UNDEAD(Category.ORIGIN, 2, 4, 6, 8, 10),
    BEAST(Category.ORIGIN, 2, 4, 6, 8),
    NETHER(Category.ORIGIN, 2, 4, 6, 8),
    OCEAN(Category.ORIGIN, 2, 3),
    END(Category.ORIGIN, 2),
    ARTHROPOD(Category.ORIGIN, 2, 3),
    CONSTRUCT(Category.ORIGIN, 2, 3),
    VILLAGER(Category.ORIGIN, 2, 4),
    ILLAGER(Category.ORIGIN, 2, 4),
    HERD(Category.ORIGIN, 2, 4, 6, 8, 10),
    GUNNER(Category.TACTICAL, 2, 3),
    SUMMON(Category.TACTICAL, 2, 4),
    GUARDIAN(Category.TACTICAL, 2, 4, 6),
    ARCANE(Category.TACTICAL, 2, 4, 6, 9),
    RAIDER(Category.TACTICAL, 2, 4, 6),
    BULWARK(Category.TACTICAL, 2, 4, 6, 10),
    AMBUSH(Category.TACTICAL, 2, 4, 6, 8, 10),
    CARAVAN(Category.ECONOMY, 2, 3, 4, 6);

    public enum Category {
        ORIGIN,
        TACTICAL,
        ECONOMY
    }

    private final Category category;
    private final int[] thresholds;

    ArenaTrait(Category category, int... thresholds) {
        this.category = category;
        this.thresholds = thresholds.clone();
    }

    public Category category() {
        return category;
    }

    public int[] thresholds() {
        return thresholds.clone();
    }

    public int tierFor(int count) {
        int tier = 0;
        for (int i = 0; i < thresholds.length; i++) {
            if (count >= thresholds[i]) tier = i + 1;
        }
        return tier;
    }

    public int threshold(int tier) {
        if (tier <= 0) return 0;
        return thresholds[Math.min(tier, thresholds.length) - 1];
    }

    public int maxTier() {
        return thresholds.length;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "gui.monster_conquest.arena.game.trait." + id();
    }

    public String descriptionKey(int tier) {
        return translationKey() + ".tier_" + Math.max(1, tier);
    }

    @Override
    public String toString() {
        return id() + Arrays.toString(thresholds);
    }
}
