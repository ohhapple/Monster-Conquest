package com.ohhapple.monster_conquest.arena.trait;

import com.ohhapple.monster_conquest.arena.game.ArenaUnitCatalog;
import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;

import java.util.List;
import java.util.Map;

/** 弈子稳定 ID 到羁绊的独立映射；更换弈子类不会影响羁绊表。 */
public final class ArenaTraitCatalog {

    private static final Map<String, List<ArenaTrait>> BY_UNIT_ID = Map.ofEntries(
            e("soldier_zombie", ArenaTrait.UNDEAD, ArenaTrait.GUARDIAN),
            e("soldier_skeleton", ArenaTrait.UNDEAD),
            e("soldier_zombie_villager", ArenaTrait.UNDEAD, ArenaTrait.GUARDIAN),
            e("arena_snow_golem", ArenaTrait.CONSTRUCT, ArenaTrait.ARCANE),
            e("arena_spider", ArenaTrait.ARTHROPOD, ArenaTrait.AMBUSH),
            e("arena_goat", ArenaTrait.BEAST, ArenaTrait.RAIDER),
            e("arena_armadillo", ArenaTrait.HERD, ArenaTrait.BULWARK),
            e("arena_endermite", ArenaTrait.END, ArenaTrait.ARTHROPOD, ArenaTrait.AMBUSH),
            e("arena_rabbit", ArenaTrait.BEAST, ArenaTrait.AMBUSH),
            e("arena_donkey", ArenaTrait.HERD, ArenaTrait.CARAVAN),
            e("arena_sheep", ArenaTrait.HERD, ArenaTrait.CARAVAN),
            e("arena_cow", ArenaTrait.HERD, ArenaTrait.BULWARK,
                    ArenaTrait.CARAVAN),
            e("arena_pig", ArenaTrait.NETHER),

            e("soldier_drowned", ArenaTrait.UNDEAD, ArenaTrait.OCEAN),
            e("soldier_stray", ArenaTrait.UNDEAD, ArenaTrait.ARCANE),
            e("soldier_villager_archer", ArenaTrait.VILLAGER, ArenaTrait.CARAVAN),
            e("soldier_vindicator", ArenaTrait.ILLAGER, ArenaTrait.RAIDER),
            e("soldier_husk", ArenaTrait.UNDEAD, ArenaTrait.GUARDIAN),
            e("arena_hoglin", ArenaTrait.NETHER),
            e("arena_cave_spider", ArenaTrait.ARTHROPOD, ArenaTrait.AMBUSH),
            e("arena_zombified_piglin", ArenaTrait.NETHER),
            e("arena_fox", ArenaTrait.HERD, ArenaTrait.AMBUSH),
            e("arena_creeper", ArenaTrait.HERD, ArenaTrait.AMBUSH),
            e("arena_panda", ArenaTrait.BEAST, ArenaTrait.BULWARK),

            e("soldier_piglin_shield", ArenaTrait.NETHER, ArenaTrait.GUARDIAN),
            e("soldier_gunner_rifle", ArenaTrait.GUNNER),
            e("arena_wolf", ArenaTrait.BEAST, ArenaTrait.SUMMON,
                    ArenaTrait.RAIDER),
            e("arena_polar_bear", ArenaTrait.BEAST, ArenaTrait.BULWARK),
            e("arena_iron_golem", ArenaTrait.CONSTRUCT, ArenaTrait.VILLAGER,
                    ArenaTrait.GUARDIAN, ArenaTrait.BULWARK),
            e("arena_blaze", ArenaTrait.NETHER, ArenaTrait.ARCANE),
            e("arena_frog", ArenaTrait.HERD, ArenaTrait.AMBUSH),
            e("arena_magma_cube", ArenaTrait.NETHER, ArenaTrait.SUMMON),
            e("arena_turtle", ArenaTrait.HERD, ArenaTrait.OCEAN,
                    ArenaTrait.BULWARK),
            e("arena_skeleton_horse", ArenaTrait.UNDEAD, ArenaTrait.GUARDIAN),

            e("soldier_mace", ArenaTrait.VILLAGER, ArenaTrait.CARAVAN, ArenaTrait.RAIDER),
            e("soldier_bogged", ArenaTrait.UNDEAD, ArenaTrait.ARCANE),
            e("soldier_witch", ArenaTrait.ILLAGER, ArenaTrait.ARCANE),
            e("arena_ravager", ArenaTrait.ILLAGER, ArenaTrait.BEAST,
                    ArenaTrait.BULWARK),
            e("arena_elder_guardian", ArenaTrait.OCEAN, ArenaTrait.ARCANE,
                    ArenaTrait.BULWARK),
            e("arena_enderman", ArenaTrait.END, ArenaTrait.AMBUSH),
            e("arena_wandering_trader", ArenaTrait.VILLAGER, ArenaTrait.CARAVAN),
            e("soldier_gunner_shotgun", ArenaTrait.GUNNER, ArenaTrait.RAIDER),
            e("arena_giant_armadillo", ArenaTrait.HERD, ArenaTrait.BULWARK,
                    ArenaTrait.AMBUSH),

            e("soldier_wither_skeleton", ArenaTrait.UNDEAD, ArenaTrait.NETHER),
            e("soldier_gunner_sniper", ArenaTrait.GUNNER, ArenaTrait.RAIDER),
            e("soldier_evoker", ArenaTrait.ILLAGER, ArenaTrait.SUMMON, ArenaTrait.ARCANE),
            e("arena_breeze", ArenaTrait.ARCANE, ArenaTrait.AMBUSH),
            e("arena_sniffer", ArenaTrait.BEAST, ArenaTrait.HERD),
            e("arena_giant_slime", ArenaTrait.BEAST, ArenaTrait.SUMMON),
            e("arena_warden", ArenaTrait.CONSTRUCT, ArenaTrait.BULWARK),
            e("arena_wither", ArenaTrait.UNDEAD, ArenaTrait.NETHER, ArenaTrait.ARCANE)
    );

    private ArenaTraitCatalog() {
    }

    public static List<ArenaTrait> traitsFor(int unitIndex) {
        if (unitIndex < 0) return List.of();
        return traitsFor(ArenaUnitCatalog.get(unitIndex));
    }

    public static List<ArenaTrait> traitsFor(ArenaUnit unit) {
        return BY_UNIT_ID.getOrDefault(unit.id(), List.of());
    }

    public static boolean hasTrait(int unitIndex, ArenaTrait trait) {
        if (unitIndex < 0) return false;
        return traitsFor(unitIndex).contains(trait);
    }

    public static Map<String, List<ArenaTrait>> snapshot() {
        return Map.copyOf(BY_UNIT_ID);
    }

    private static Map.Entry<String, List<ArenaTrait>> e(
            String unitId, ArenaTrait... traits) {
        return Map.entry(unitId, List.of(traits));
    }
}
