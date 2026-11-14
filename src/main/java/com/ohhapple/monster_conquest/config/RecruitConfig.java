package com.ohhapple.monster_conquest.config;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.RecruitableSoldiers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 募兵站配置：
 *   · 所有等级统一使用基础货币（默认紫水晶碎片）；
 *   · T4 招募 / 升级额外消耗下界之星；
 *   · 每名士兵的招募价格 —— 每行一个兵种，"T0..T4" 依次为基础货币数量，
 *     例如 soldier_zombie = "8, 16, 32, 48, 64"。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class RecruitConfig {

    private RecruitConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===================== 招募货币 =====================

    public static final ModConfigSpec.ConfigValue<String> CURRENCY = BUILDER
            .comment("募兵站所有等级使用的基础货币物品 id。")
            .define("currency", "minecraft:amethyst_shard");
    public static final ModConfigSpec.ConfigValue<String> TIER4_EXTRA_CURRENCY = BUILDER
            .comment("T4 招募或升级额外消耗的物品 id。")
            .define("tier4ExtraCurrency", "minecraft:nether_star");
    public static final ModConfigSpec.IntValue TIER4_EXTRA_COST = BUILDER
            .comment("T4 招募或升级额外消耗的数量；设为 0 可关闭。")
            .defineInRange("tier4ExtraCost", 1, 0, 64);

    // ===================== 每名士兵的价格 =====================

    /** 内置默认价格（同时也是配置里的默认值）。 */
    private static final Map<String, String> DEFAULT_PRICES = Map.ofEntries(
            Map.entry("soldier_zombie", "8, 16, 32, 48, 64"),
            Map.entry("soldier_husk", "8, 16, 32, 48, 64"),
            Map.entry("soldier_zombie_villager", "8, 16, 32, 48, 64"),
            Map.entry("soldier_vindicator", "10, 20, 40, 60, 80"),
            Map.entry("soldier_mace", "12, 24, 48, 72, 96"),
            Map.entry("soldier_drowned", "10, 20, 40, 60, 80"),
            Map.entry("soldier_wither_skeleton", "12, 24, 48, 72, 96"),
            Map.entry("soldier_skeleton", "12, 24, 48, 72, 96"),
            Map.entry("soldier_villager_archer", "12, 24, 48, 72, 96"),
            Map.entry("soldier_stray", "14, 28, 56, 84, 112"),
            Map.entry("soldier_bogged", "14, 28, 56, 84, 112"),
            Map.entry("soldier_witch", "16, 32, 64, 96, 128"),
            Map.entry("soldier_villager_priest", "16, 32, 64, 96, 128"),
            Map.entry("soldier_evoker", "20, 40, 80, 120, 160"),
            Map.entry("soldier_piglin_shield", "10, 20, 40, 60, 80"),
            Map.entry("soldier_gunner_rifle", "22, 44, 88, 132, 176"),
            Map.entry("soldier_gunner_shotgun", "24, 48, 96, 144, 192"),
            Map.entry("soldier_gunner_rocket", "24, 48, 96, 144, 192"),
            Map.entry("soldier_gunner_sniper", "26, 52, 104, 156, 208"),
            Map.entry("soldier_vex", "32, 32, 32, 32, 32"),
            Map.entry("soldier_allay", "32, 32, 32, 32, 32")
    );

    /** 每名士兵一个配置项（按兵种表顺序）。 */
    public static final Map<String, ModConfigSpec.ConfigValue<String>> PRICE_DEFS = new LinkedHashMap<>();

    static {
        BUILDER.comment("每名士兵的招募价格（可逐条修改）：");
        BUILDER.comment("  格式：\"T0数量, T1数量, T2数量, T3数量, T4数量\"。");
        BUILDER.comment("  例如 soldier_zombie = \"8, 16, 32, 48, 64\"。");
        BUILDER.push("recruitPrices");
        for (RecruitableSoldiers.Entry entry : RecruitableSoldiers.ALL) {
            String id = entry.id();
            String def = DEFAULT_PRICES.getOrDefault(id, "8, 16, 32, 48, 64");
            PRICE_DEFS.put(id, BUILDER.define(id, def,
                    value -> value instanceof String text && parsePriceOrNull(text) != null));
        }
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===================== 解析后的数据 =====================

    private static Item currencyItem = Items.AMETHYST_SHARD;
    private static Item tier4ExtraItem = Items.NETHER_STAR;
    private static int tier4ExtraCost = 1;

    /** speciesId -> 各等级使用的材料数量。 */
    public static final Map<String, int[]> PRICES = new HashMap<>();

    static {
        for (Map.Entry<String, String> e : DEFAULT_PRICES.entrySet()) {
            int[] parsed = parsePriceOrNull(e.getValue());
            if (parsed != null) PRICES.put(e.getKey(), parsed);
        }
    }

    /** 所有等级共用的基础招募货币。 */
    public static Item currencyItem() {
        return currencyItem;
    }

    /** T4 招募或升级额外消耗的物品。 */
    public static Item tier4ExtraItem() {
        return tier4ExtraItem;
    }

    /** T4 额外物品数量；0 表示关闭。 */
    public static int tier4ExtraCost() {
        return tier4ExtraCost;
    }

    /** 某兵种、某等级需要的招募材料数量。 */
    public static int recruitCost(String speciesId, int tier) {
        int[] prices = PRICES.get(speciesId);
        return prices == null ? 1 : prices[Math.max(0, Math.min(4, tier))];
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        currencyItem = parseItem(CURRENCY.get(), Items.AMETHYST_SHARD);
        tier4ExtraItem = parseItem(TIER4_EXTRA_CURRENCY.get(), Items.NETHER_STAR);
        tier4ExtraCost = TIER4_EXTRA_COST.get();

        boolean correctedPrices = false;
        for (Map.Entry<String, ModConfigSpec.ConfigValue<String>> e : PRICE_DEFS.entrySet()) {
            String id = e.getKey();
            int[] parsed = parsePriceOrNull(e.getValue().get());
            if (parsed == null) {
                String fallback = DEFAULT_PRICES.getOrDefault(id, "8, 16, 32, 48, 64");
                e.getValue().set(fallback);
                parsed = parsePriceOrNull(fallback);
                correctedPrices = true;
            }
            if (parsed != null) PRICES.put(id, parsed);
        }
        if (correctedPrices) SPEC.save();
    }

    private static Item parseItem(String id, Item fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        return (item == null || item == Items.AIR) ? fallback : item;
    }

    /** 解析 "8, 16, 32, 48, 64"；格式不对返回 null。 */
    @Nullable
    private static int[] parsePriceOrNull(@Nullable String raw) {
        if (raw == null) return null;
        String[] tierParts = raw.split(",");
        if (tierParts.length < 5) return null;
        int[] out = new int[5];
        for (int t = 0; t < 5; t++) {
            String value = tierParts[t].trim();
            try {
                out[t] = Math.max(0, Integer.parseInt(value));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return out;
    }
}
