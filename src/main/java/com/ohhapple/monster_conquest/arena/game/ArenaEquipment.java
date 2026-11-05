package com.ohhapple.monster_conquest.arena.game;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 自走棋独立装备表。基础组件参与合成，成装只提供竞技属性和战斗特效；
 * 这些物品不进入主世界配方、战利品或创造模式页。
 */
public enum ArenaEquipment {
    IRON_SWORD(true, "arena_iron_sword", Effect.NONE,
            gearStats().attackPercent(0.25).build()),
    BOW(true, "arena_bow", Effect.NONE,
            gearStats().attackSpeedPercent(0.30).build()),
    CHESTPLATE(true, "arena_chestplate", Effect.NONE,
            gearStats().armor(5).build()),
    TURTLE_SHELL(true, "arena_turtle_shell", Effect.NONE,
            gearStats().magicResistance(25).health(500).build()),
    BOOTS(true, "arena_boots", Effect.NONE,
            gearStats().movementSpeedPercent(0.20).dodgeChance(0.05).build()),
    GOLDEN_APPLE(true, "arena_golden_apple", Effect.NONE,
            gearStats().healingPower(0.10).lifesteal(0.10).build()),
    REDSTONE(true, "arena_redstone", Effect.NONE,
            gearStats().initialSkillCharge(15)
                    .skillChargeGainPercent(0.10).build()),
    BLAZE_ROD(true, "arena_blaze_rod", Effect.NONE,
            gearStats().magicPower(15).critChance(0.20).build()),
    SHIELD(false, "arena_shield", Effect.SHIELD,
            ArenaEquipmentStats.EMPTY),

    GIANT_BLADE(false, "arena_giant_blade", Effect.GIANT_BLADE,
            gearStats().attackPercent(0.50).build()),
    SWIFT_BLADE(false, "arena_swift_blade", Effect.SWIFT_BLADE,
            gearStats().attackPercent(0.25).attackSpeedPercent(0.50).build()),
    WARRIOR_PLATE(false, "arena_warrior_plate", Effect.WARRIOR_PLATE,
            gearStats().attackPercent(0.25).armor(12).build()),
    SPELLBREAKER(false, "arena_spellbreaker", Effect.SPELLBREAKER,
            gearStats().attackPercent(0.45).build()),
    STALKER(false, "arena_stalker", Effect.STALKER,
            gearStats().attackPercent(0.45)
                    .movementSpeedPercent(0.20).build()),
    BLOODTHIRSTER(false, "arena_bloodthirster", Effect.BLOODTHIRSTER,
            gearStats().attackPercent(0.45).lifesteal(0.25).build()),
    CHARGED_BLADE(false, "arena_charged_blade", Effect.CHARGED_BLADE,
            gearStats().attackPercent(0.25)
                    .skillChargeGainPercent(0.30).build()),
    FLAME_BLADE(false, "arena_flame_blade", Effect.FLAME_BLADE,
            gearStats().attackPercent(0.45)
                    .critChance(0.30).build()),

    RAPID_CROSSBOW(false, "arena_rapid_crossbow", Effect.RAPID_CROSSBOW,
            gearStats().attackSpeedPercent(0.80).build()),
    GUERRILLA_ARMOR(false, "arena_guerrilla_armor",
            Effect.GUERRILLA_ARMOR,
            gearStats().attackSpeedPercent(0.50).armor(10).build()),
    FORTRESS_BOW(false, "arena_fortress_bow", Effect.FORTRESS_BOW,
            gearStats().attackSpeedPercent(0.40)
                    .health(800).magicResistance(35).build()),
    GALE_BOW(false, "arena_gale_bow", Effect.GALE_BOW,
            gearStats().attackSpeedPercent(0.60)
                    .movementSpeedPercent(0.20).build()),
    VAMPIRIC_BOW(false, "arena_vampiric_bow", Effect.VAMPIRIC_BOW,
            gearStats().attackSpeedPercent(0.40).lifesteal(0.25).build()),
    PULSE_BOW(false, "arena_pulse_bow", Effect.PULSE_BOW,
            gearStats().attackSpeedPercent(0.50).build()),
    ROCKET_BOW(false, "arena_rocket_bow", Effect.ROCKET_BOW,
            gearStats().attackSpeedPercent(0.60)
                    .critChance(0.30).build()),

    HEAVY_ARMOR(false, "arena_heavy_armor", Effect.HEAVY_ARMOR,
            gearStats().armor(35).health(500).build()),
    MAGEBANE_ARMOR(false, "arena_magebane_armor",
            Effect.MAGEBANE_ARMOR,
            gearStats().health(500).armor(12).magicResistance(45).build()),
    SCOUT_ARMOR(false, "arena_scout_armor", Effect.SCOUT_ARMOR,
            gearStats().armor(10).dodgeChance(0.25).build()),
    REGENERATION_ARMOR(false, "arena_regeneration_armor",
            Effect.REGENERATION_ARMOR,
            gearStats().armor(10).health(500).build()),
    THORNS_ARMOR(false, "arena_thorns_armor", Effect.THORNS_ARMOR,
            gearStats().armor(20).health(500).build()),
    MOLTEN_ARMOR(false, "arena_molten_armor", Effect.MOLTEN_ARMOR,
            gearStats().armor(18).health(500)
                    .critChance(0.30).build()),

    SHELL_BULWARK(false, "arena_shell_bulwark",
            Effect.SHELL_BULWARK,
            gearStats().health(1400).magicResistance(50).build()),
    GUARDIAN_BOOTS(false, "arena_guardian_boots", Effect.NONE,
            gearStats().health(1200).magicResistance(35)
                    .dodgeChance(0.25).build()),
    HOLY_SHELL(false, "arena_holy_shell", Effect.HOLY_SHELL,
            gearStats().health(1200).magicResistance(35).build()),
    ENERGY_SHELL(false, "arena_energy_shell", Effect.ENERGY_SHELL,
            gearStats().health(1200).magicResistance(35).build()),
    MOLTEN_SHELL(false, "arena_molten_shell", Effect.MOLTEN_SHELL,
            gearStats().health(1200).magicResistance(35)
                    .critChance(0.30).build()),

    SWIFT_BOOTS(false, "arena_swift_boots", Effect.NONE,
            gearStats().movementSpeedPercent(0.40)
                    .dodgeChance(0.35).build()),
    NIMBLE_BOOTS(false, "arena_nimble_boots", Effect.NIMBLE_BOOTS,
            gearStats().movementSpeedPercent(0.30).build()),
    PULSE_BOOTS(false, "arena_pulse_boots", Effect.NONE,
            gearStats().movementSpeedPercent(0.30)
                    .skillChargeGainPercent(0.30).build()),
    FLAME_TREADS(false, "arena_flame_treads", Effect.FLAME_TREADS,
            gearStats().movementSpeedPercent(0.30)
                    .critChance(0.30).build()),

    ENCHANTED_GOLDEN_APPLE(false, "arena_enchanted_golden_apple",
            Effect.ENCHANTED_GOLDEN_APPLE,
            gearStats().health(1600).healingPower(0.40).build()),
    HEALING_CORE(false, "arena_healing_core", Effect.HEALING_CORE,
            gearStats().health(1000).healingPower(0.30)
                    .skillChargeGainPercent(0.25).build()),
    BURNING_CHARM(false, "arena_burning_charm", Effect.BURNING_CHARM,
            gearStats().healingPower(0.25).magicPower(30)
                    .critChance(0.30).build()),

    PULSE_CORE(false, "arena_pulse_core", Effect.NONE,
            gearStats().initialSkillCharge(40)
                    .skillChargeGainPercent(0.60).build()),
    ARCANE_CORE(false, "arena_arcane_core", Effect.NONE,
            gearStats().magicPower(30)
                    .skillChargeGainPercent(0.30)
                    .critChance(0.30).build()),
    FLAME_STAFF(false, "arena_flame_staff", Effect.FLAME_STAFF,
            gearStats().magicPower(50).critChance(0.50)
                    .critDamage(0.70).skillCritEnabled(true).build()),

    TOTEM_OF_UNDYING(false, "arena_totem_of_undying",
            Effect.TOTEM_OF_UNDYING, ArenaEquipmentStats.EMPTY),
    CONDUIT(false, "arena_conduit", Effect.CONDUIT,
            ArenaEquipmentStats.EMPTY),

    DUPLICATOR(false, "arena_duplicator", Effect.NONE,
            ArenaEquipmentStats.EMPTY),
    LESSER_DUPLICATOR(false, "arena_lesser_duplicator",
            Effect.NONE, ArenaEquipmentStats.EMPTY),
    REMOVER(false, "arena_equipment_remover", Effect.NONE,
            ArenaEquipmentStats.EMPTY);

    public enum Category {
        BASIC,
        COMPLETED,
        SPECIAL,
        TOOL
    }

    public enum Effect {
        NONE,
        SHIELD,
        GIANT_BLADE,
        SWIFT_BLADE,
        WARRIOR_PLATE,
        SPELLBREAKER,
        STALKER,
        BLOODTHIRSTER,
        CHARGED_BLADE,
        FLAME_BLADE,
        RAPID_CROSSBOW,
        GUERRILLA_ARMOR,
        FORTRESS_BOW,
        GALE_BOW,
        VAMPIRIC_BOW,
        PULSE_BOW,
        ROCKET_BOW,
        HEAVY_ARMOR,
        MAGEBANE_ARMOR,
        SCOUT_ARMOR,
        REGENERATION_ARMOR,
        THORNS_ARMOR,
        MOLTEN_ARMOR,
        SHELL_BULWARK,
        HOLY_SHELL,
        ENERGY_SHELL,
        MOLTEN_SHELL,
        NIMBLE_BOOTS,
        FLAME_TREADS,
        ENCHANTED_GOLDEN_APPLE,
        HEALING_CORE,
        BURNING_CHARM,
        FLAME_STAFF,
        TOTEM_OF_UNDYING,
        CONDUIT
    }

    private static final Map<Long, ArenaEquipment> RECIPES = buildRecipes();
    private static final List<ArenaEquipment> CRAFTABLE_COMPLETED =
            Arrays.stream(values())
                    .filter(item -> !item.component()
                            && RECIPES.containsValue(item))
                    .toList();
    private static final List<ArenaEquipment> BASIC_COMPONENTS =
            Arrays.stream(values())
                    .filter(ArenaEquipment::component)
                    .toList();

    private final boolean component;
    private final String itemId;
    private final Effect effect;
    private final ArenaEquipmentStats stats;

    ArenaEquipment(boolean component, String itemId,
                   Effect effect, ArenaEquipmentStats stats) {
        this.component = component;
        this.itemId = itemId;
        this.effect = effect;
        this.stats = stats;
    }

    public boolean component() {
        return component;
    }

    public Category category() {
        if (component) return Category.BASIC;
        if (this == DUPLICATOR || this == LESSER_DUPLICATOR
                || this == REMOVER) {
            return Category.TOOL;
        }
        if (this == SHIELD || this == TOTEM_OF_UNDYING
                || this == CONDUIT) {
            return Category.SPECIAL;
        }
        return Category.COMPLETED;
    }

    public boolean tool() {
        return category() == Category.TOOL;
    }

    public boolean duplicator() {
        return this == DUPLICATOR || this == LESSER_DUPLICATOR;
    }

    public String categoryKey() {
        return "gui.monster_conquest.arena.equipment.category."
                + category().name().toLowerCase(Locale.ROOT);
    }

    public String itemId() {
        return itemId;
    }

    public Effect effect() {
        return effect;
    }

    public ArenaEquipmentStats stats() {
        return stats;
    }

    /**
     * 八件基础组件通过正式配方可以产出的成装；技能随机成装不得绕过
     * 该集合发放盾牌、拆卸器或未来新增的特殊装备。
     */
    public static List<ArenaEquipment> craftableCompleted() {
        return CRAFTABLE_COMPLETED;
    }

    public static List<ArenaEquipment> basicComponents() {
        return BASIC_COMPONENTS;
    }

    public List<Combination> combinations() {
        if (!component) return List.of();
        List<Combination> result = new ArrayList<>();
        for (ArenaEquipment other : values()) {
            if (!other.component) continue;
            ArenaEquipment crafted = combine(this, other);
            if (crafted != null) {
                result.add(new Combination(other, crafted));
            }
        }
        return List.copyOf(result);
    }

    public ItemStack iconStack() {
        return switch (this) {
            case SHIELD -> new ItemStack(Items.SHIELD);
            case TOTEM_OF_UNDYING ->
                    new ItemStack(Items.TOTEM_OF_UNDYING);
            case CONDUIT -> new ItemStack(Items.CONDUIT);
            case DUPLICATOR ->
                    new ItemStack(Items.ORANGE_SHULKER_BOX);
            case LESSER_DUPLICATOR ->
                    new ItemStack(Items.BLUE_SHULKER_BOX);
            default -> ArenaEquipmentItems.stack(this);
        };
    }

    /** 地面掉落物使用独立视觉，避免所有装备都显示为铁砧。 */
    public ItemStack dropStack() {
        return switch (this) {
            case DUPLICATOR -> new ItemStack(Items.RED_SHULKER_BOX);
            case LESSER_DUPLICATOR ->
                    new ItemStack(Items.BLUE_SHULKER_BOX);
            case REMOVER -> new ItemStack(Items.SHEARS);
            default -> iconStack();
        };
    }

    @Nullable
    public Item chestVisual() {
        return switch (this) {
            case CHESTPLATE, WARRIOR_PLATE -> Items.IRON_CHESTPLATE;
            case GUERRILLA_ARMOR, SCOUT_ARMOR -> Items.LEATHER_CHESTPLATE;
            case MAGEBANE_ARMOR -> Items.DIAMOND_CHESTPLATE;
            case REGENERATION_ARMOR -> Items.GOLDEN_CHESTPLATE;
            case HEAVY_ARMOR, THORNS_ARMOR, MOLTEN_ARMOR ->
                    Items.NETHERITE_CHESTPLATE;
            default -> null;
        };
    }

    @Nullable
    public Item helmetVisual() {
        return switch (this) {
            case TURTLE_SHELL, SHELL_BULWARK, HOLY_SHELL,
                    ENERGY_SHELL, MOLTEN_SHELL -> Items.TURTLE_HELMET;
            default -> null;
        };
    }

    @Nullable
    public Item leggingsVisual() {
        return null;
    }

    @Nullable
    public Item bootsVisual() {
        return switch (this) {
            case BOOTS, SWIFT_BOOTS, NIMBLE_BOOTS,
                    PULSE_BOOTS, FLAME_TREADS -> Items.IRON_BOOTS;
            case GUARDIAN_BOOTS -> Items.DIAMOND_BOOTS;
            default -> null;
        };
    }

    public String translationKey() {
        return "gui.monster_conquest.arena.equipment."
                + name().toLowerCase(Locale.ROOT);
    }

    public String descriptionKey() {
        return translationKey() + ".description";
    }

    public String itemTranslationKey() {
        return "item.monster_conquest." + itemId;
    }

    @Nullable
    public static ArenaEquipment combine(ArenaEquipment first,
                                         ArenaEquipment second) {
        if (first == null || second == null
                || !first.component || !second.component) {
            return null;
        }
        return RECIPES.get(recipeKey(first, second));
    }

    public static ArenaEquipment byId(int id) {
        ArenaEquipment[] values = values();
        return values[Math.floorMod(id, values.length)];
    }

    private static ArenaEquipmentStats.Builder gearStats() {
        return ArenaEquipmentStats.builder();
    }

    private static Map<Long, ArenaEquipment> buildRecipes() {
        Map<Long, ArenaEquipment> recipes = new HashMap<>();
        recipe(recipes, IRON_SWORD, IRON_SWORD, GIANT_BLADE);
        recipe(recipes, IRON_SWORD, BOW, SWIFT_BLADE);
        recipe(recipes, IRON_SWORD, CHESTPLATE, WARRIOR_PLATE);
        recipe(recipes, IRON_SWORD, TURTLE_SHELL, SPELLBREAKER);
        recipe(recipes, IRON_SWORD, BOOTS, STALKER);
        recipe(recipes, IRON_SWORD, GOLDEN_APPLE, BLOODTHIRSTER);
        recipe(recipes, IRON_SWORD, REDSTONE, CHARGED_BLADE);
        recipe(recipes, IRON_SWORD, BLAZE_ROD, FLAME_BLADE);

        recipe(recipes, BOW, BOW, RAPID_CROSSBOW);
        recipe(recipes, BOW, CHESTPLATE, GUERRILLA_ARMOR);
        recipe(recipes, BOW, TURTLE_SHELL, FORTRESS_BOW);
        recipe(recipes, BOW, BOOTS, GALE_BOW);
        recipe(recipes, BOW, GOLDEN_APPLE, VAMPIRIC_BOW);
        recipe(recipes, BOW, REDSTONE, PULSE_BOW);
        recipe(recipes, BOW, BLAZE_ROD, ROCKET_BOW);

        recipe(recipes, CHESTPLATE, CHESTPLATE, HEAVY_ARMOR);
        recipe(recipes, CHESTPLATE, TURTLE_SHELL, MAGEBANE_ARMOR);
        recipe(recipes, CHESTPLATE, BOOTS, SCOUT_ARMOR);
        recipe(recipes, CHESTPLATE, GOLDEN_APPLE, REGENERATION_ARMOR);
        recipe(recipes, CHESTPLATE, REDSTONE, THORNS_ARMOR);
        recipe(recipes, CHESTPLATE, BLAZE_ROD, MOLTEN_ARMOR);

        recipe(recipes, TURTLE_SHELL, TURTLE_SHELL, SHELL_BULWARK);
        recipe(recipes, TURTLE_SHELL, BOOTS, GUARDIAN_BOOTS);
        recipe(recipes, TURTLE_SHELL, GOLDEN_APPLE, HOLY_SHELL);
        recipe(recipes, TURTLE_SHELL, REDSTONE, ENERGY_SHELL);
        recipe(recipes, TURTLE_SHELL, BLAZE_ROD, MOLTEN_SHELL);

        recipe(recipes, BOOTS, BOOTS, SWIFT_BOOTS);
        recipe(recipes, BOOTS, GOLDEN_APPLE, NIMBLE_BOOTS);
        recipe(recipes, BOOTS, REDSTONE, PULSE_BOOTS);
        recipe(recipes, BOOTS, BLAZE_ROD, FLAME_TREADS);

        recipe(recipes, GOLDEN_APPLE, GOLDEN_APPLE,
                ENCHANTED_GOLDEN_APPLE);
        recipe(recipes, GOLDEN_APPLE, REDSTONE, HEALING_CORE);
        recipe(recipes, GOLDEN_APPLE, BLAZE_ROD, BURNING_CHARM);

        recipe(recipes, REDSTONE, REDSTONE, PULSE_CORE);
        recipe(recipes, REDSTONE, BLAZE_ROD, ARCANE_CORE);
        recipe(recipes, BLAZE_ROD, BLAZE_ROD, FLAME_STAFF);
        return Map.copyOf(recipes);
    }

    private static void recipe(Map<Long, ArenaEquipment> recipes,
                               ArenaEquipment first,
                               ArenaEquipment second,
                               ArenaEquipment result) {
        recipes.put(recipeKey(first, second), result);
    }

    private static long recipeKey(ArenaEquipment first,
                                  ArenaEquipment second) {
        int low = Math.min(first.ordinal(), second.ordinal());
        int high = Math.max(first.ordinal(), second.ordinal());
        return ((long) low << 32) | (high & 0xFFFFFFFFL);
    }

    public record Combination(
            ArenaEquipment ingredient,
            ArenaEquipment result) {
    }
}
