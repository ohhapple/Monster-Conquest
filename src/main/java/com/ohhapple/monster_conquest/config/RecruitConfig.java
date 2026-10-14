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
 *   · 每个等级的"金属"货币（默认：T0~T2 铁锭 / T3 钻石 / T4 下界合金锭）；
 *   · 每名士兵的招募价格 —— 每行一个兵种，"T0..T4" 依次为 "绿宝石/金属"，
 *     例如 soldier_zombie = "12/4, 18/8, 24/12, 30/4, 40/2"。
 *   最终消耗 = 绿宝石 × 第一个数 + 该等级的金属 × 第二个数。
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class RecruitConfig {

    private RecruitConfig() {}

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===================== 每级的金属货币 =====================

    public static final ModConfigSpec.ConfigValue<String> TIER0_METAL = BUILDER
            .comment("募兵站：一阶（T0）士兵的金属货币物品 id（另外还要绿宝石）。")
            .define("tier0Metal", "minecraft:iron_ingot");
    public static final ModConfigSpec.ConfigValue<String> TIER1_METAL = BUILDER
            .comment("二阶（T1）金属货币。")
            .define("tier1Metal", "minecraft:iron_ingot");
    public static final ModConfigSpec.ConfigValue<String> TIER2_METAL = BUILDER
            .comment("三阶（T2）金属货币。")
            .define("tier2Metal", "minecraft:iron_ingot");
    public static final ModConfigSpec.ConfigValue<String> TIER3_METAL = BUILDER
            .comment("四阶（T3）金属货币。")
            .define("tier3Metal", "minecraft:diamond");
    public static final ModConfigSpec.ConfigValue<String> TIER4_METAL = BUILDER
            .comment("五阶（T4）金属货币。")
            .define("tier4Metal", "minecraft:netherite_ingot");

    // ===================== 每名士兵的价格 =====================

    /** 内置默认价格（同时也是配置里的默认值）。 */
    private static final Map<String, String> DEFAULT_PRICES = Map.ofEntries(
            Map.entry("soldier_zombie", "12/4, 18/8, 24/12, 30/4, 40/2"),
            Map.entry("soldier_husk", "12/4, 18/8, 24/12, 30/4, 40/2"),
            Map.entry("soldier_zombie_villager", "12/4, 18/8, 24/12, 30/4, 40/2"),
            Map.entry("soldier_vindicator", "16/5, 24/10, 32/16, 40/5, 52/3"),
            Map.entry("soldier_mace", "18/6, 27/12, 36/18, 45/6, 58/3"),
            Map.entry("soldier_drowned", "16/5, 24/10, 32/16, 40/5, 52/3"),
            Map.entry("soldier_wither_skeleton", "18/6, 27/12, 36/18, 45/6, 58/3"),
            Map.entry("soldier_skeleton", "18/6, 27/12, 36/18, 45/6, 58/3"),
            Map.entry("soldier_villager_archer", "18/6, 27/12, 36/18, 45/6, 58/3"),
            Map.entry("soldier_stray", "22/7, 33/14, 44/20, 55/7, 70/4"),
            Map.entry("soldier_bogged", "22/7, 33/14, 44/20, 55/7, 70/4"),
            Map.entry("soldier_witch", "26/8, 39/16, 52/24, 65/8, 82/4"),
            Map.entry("soldier_villager_priest", "26/8, 39/16, 52/24, 65/8, 82/4"),
            Map.entry("soldier_evoker", "30/10, 45/20, 60/30, 75/10, 94/5"),
            Map.entry("soldier_gunner_rifle", "34/11, 51/22, 68/33, 85/11, 106/5"),
            Map.entry("soldier_gunner_shotgun", "38/12, 57/24, 76/36, 95/12, 118/6"),
            Map.entry("soldier_gunner_rocket", "38/12, 57/24, 76/36, 95/12, 118/6"),
            Map.entry("soldier_gunner_sniper", "42/13, 63/26, 84/39, 105/13, 130/6")
    );

    /** 每名士兵一个配置项（按兵种表顺序）。 */
    public static final Map<String, ModConfigSpec.ConfigValue<String>> PRICE_DEFS = new LinkedHashMap<>();

    static {
        BUILDER.comment("每名士兵的招募价格（可逐条修改）：");
        BUILDER.comment("  格式：\"T0绿宝石/T0金属, T1绿宝石/T1金属, ... T4绿宝石/T4金属\"。");
        BUILDER.comment("  例如 soldier_zombie = \"12/4, 18/8, 24/12, 30/4, 40/2\"。");
        BUILDER.push("prices");
        for (RecruitableSoldiers.Entry entry : RecruitableSoldiers.ALL) {
            String id = entry.id();
            String def = DEFAULT_PRICES.getOrDefault(id, "12/4, 18/8, 24/12, 30/4, 40/2");
            PRICE_DEFS.put(id, BUILDER.define(id, def));
        }
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===================== 解析后的数据 =====================

    public static final Item EMERALD_CURRENCY = Items.EMERALD;

    public static final Item[] metalItem = new Item[5];
    /** speciesId -> [tier][0=绿宝石数, 1=金属数] */
    public static final Map<String, int[][]> PRICES = new HashMap<>();

    static {
        for (int i = 0; i < 5; i++) metalItem[i] = Items.IRON_INGOT;
        for (Map.Entry<String, String> e : DEFAULT_PRICES.entrySet()) {
            int[][] parsed = parsePriceOrNull(e.getValue());
            if (parsed != null) PRICES.put(e.getKey(), parsed);
        }
    }

    /** 第 tier 档使用的金属货币。 */
    public static Item metalItemFor(int tier) {
        return (tier >= 0 && tier < 5) ? metalItem[tier] : Items.IRON_INGOT;
    }

    /** 某兵种、某等级的绿宝石数量。 */
    public static int emeraldCost(String speciesId, int tier) {
        int[][] p = PRICES.get(speciesId);
        return p == null ? 1 : p[Math.max(0, Math.min(4, tier))][0];
    }

    /** 某兵种、某等级的金属数量。 */
    public static int metalCost(String speciesId, int tier) {
        int[][] p = PRICES.get(speciesId);
        return p == null ? 1 : p[Math.max(0, Math.min(4, tier))][1];
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        metalItem[0] = parseItem(TIER0_METAL.get(), Items.IRON_INGOT);
        metalItem[1] = parseItem(TIER1_METAL.get(), Items.IRON_INGOT);
        metalItem[2] = parseItem(TIER2_METAL.get(), Items.IRON_INGOT);
        metalItem[3] = parseItem(TIER3_METAL.get(), Items.DIAMOND);
        metalItem[4] = parseItem(TIER4_METAL.get(), Items.NETHERITE_INGOT);

        for (Map.Entry<String, ModConfigSpec.ConfigValue<String>> e : PRICE_DEFS.entrySet()) {
            String id = e.getKey();
            int[][] parsed = parsePriceOrNull(e.getValue().get());
            if (parsed == null) parsed = parsePriceOrNull(DEFAULT_PRICES.get(id));
            if (parsed != null) PRICES.put(id, parsed);
        }
    }

    private static Item parseItem(String id, Item fallback) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        return (item == null || item == Items.AIR) ? fallback : item;
    }

    /** 解析 "12/4, 18/8, 24/12, 30/4, 40/2"；格式不对返回 null。 */
    @Nullable
    private static int[][] parsePriceOrNull(@Nullable String raw) {
        if (raw == null) return null;
        String[] tierParts = raw.split(",");
        if (tierParts.length < 5) return null;
        int[][] out = new int[5][2];
        for (int t = 0; t < 5; t++) {
            String[] em = tierParts[t].trim().split("/");
            if (em.length < 2) return null;
            try {
                out[t][0] = Math.max(0, Integer.parseInt(em[0].trim()));
                out[t][1] = Math.max(0, Integer.parseInt(em[1].trim()));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return out;
    }
}
