package com.ohhapple.monster_conquest.config;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 阵营设置：仇视生物 / 敌对阵营 / 结盟阵营 / 转化规则 / 首领召唤。
 * 对应配置文件：config/monster_conquest/factions-common.toml
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public class FactionConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===================== 默认仇视的生物 =====================

    /**
     * 村民 / 玩家阵营默认仇视的原版怪物（僵尸骷髅及变体、苦力怕、末影人、
     *   灾厄系、监守者、末影龙、蜘蛛变体、幻翼、下界与海底怪物等）。
     */
    private static final List<String> VANILLA_MONSTER_ENEMIES = List.of(
            // 僵尸及其变体
            "minecraft:zombie", "minecraft:husk", "minecraft:drowned",
            "minecraft:zombie_villager", "minecraft:zombified_piglin", "minecraft:zombie_horse",
            // 骷髅及其变体
            "minecraft:skeleton", "minecraft:stray", "minecraft:bogged",
            "minecraft:wither_skeleton",
            // 蜘蛛及其变体 / 苦力怕 / 末影人 / 幻翼 / 蠹虫 / 末影螨
            "minecraft:spider", "minecraft:cave_spider",
            "minecraft:creeper", "minecraft:enderman", "minecraft:phantom",
            "minecraft:silverfish", "minecraft:endermite",
            // 灾厄系
            "minecraft:witch", "minecraft:vindicator", "minecraft:pillager",
            "minecraft:ravager", "minecraft:vex", "minecraft:evoker", "minecraft:illusioner",
            // 下界怪物
            "minecraft:blaze", "minecraft:ghast", "minecraft:magma_cube", "minecraft:slime",
            "minecraft:hoglin", "minecraft:zoglin", "minecraft:piglin", "minecraft:piglin_brute",
            // 海底 / 末地 / 深暗 / BOSS
            "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker",
            "minecraft:breeze", "minecraft:warden",
            "minecraft:ender_dragon", "minecraft:wither"
    );

    public static final ModConfigSpec.ConfigValue<List<? extends String>> DEFAULT_ENEMIES_DEF = BUILDER
            .comment("士兵和首领默认仇视并攻击的生物类型 ID 列表（全局兜底，所有阵营共享）。")
            .comment("填写格式为 namespace:path（如 minecraft:player）")
            .defineListAllowEmpty("defaultEnemies",
                    List.of(),
                    FactionConfig::validateEntityName);

    // ===================== 每阵营独立敌对生物 =====================
    // 本列表与全局 defaultEnemies 是两个独立集合，
    //       任意一个命中 → 该生物视为敌人。不是"合并"。

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_UNDEAD_ENEMIES_DEF = BUILDER
            .comment("亡灵阵营额外仇视的生物类型 ID（与全局 defaultEnemies 独立检查，任一命中即视为敌人）")
            .defineListAllowEmpty("factionEnemies.undead",
                    List.of("minecraft:villager", "minecraft:iron_golem", "minecraft:player"),
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_VILLAGER_ENEMIES_DEF = BUILDER
            .comment("村民阵营额外仇视的生物类型 ID（独立检查）")
            .defineListAllowEmpty("factionEnemies.villager",
                    VANILLA_MONSTER_ENEMIES,
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_ILLAGER_ENEMIES_DEF = BUILDER
            .comment("灾厄阵营额外仇视的生物类型 ID（独立检查）")
            .defineListAllowEmpty("factionEnemies.illager",
                    List.of("minecraft:villager", "minecraft:player", "minecraft:iron_golem"),
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_NETHER_ENEMIES_DEF = BUILDER
            .comment("下界阵营额外仇视的生物类型 ID（独立检查）")
            .defineListAllowEmpty("factionEnemies.nether",
                    List.of("minecraft:villager", "minecraft:player", "minecraft:iron_golem"),
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_END_ENEMIES_DEF = BUILDER
            .comment("末地阵营额外仇视的生物类型 ID（默认空：末地不默认仇视任何阵营 / 生物）")
            .defineListAllowEmpty("factionEnemies.end",
                    List.of(),
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_MONSTER_ENEMIES_DEF = BUILDER
            .comment("怪物阵营额外仇视的生物类型 ID（独立检查）")
            .defineListAllowEmpty("factionEnemies.monster",
                    List.of("minecraft:villager", "minecraft:player", "minecraft:iron_golem"),
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_NEUTRAL_ENEMIES_DEF = BUILDER
            .comment("中立阵营额外仇视的生物类型 ID（默认空，不主动攻击）")
            .defineListAllowEmpty("factionEnemies.neutral",
                    List.of(),
                    FactionConfig::validateEntityName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PLAYER_FACTION_ENEMIES_DEF = BUILDER
            .comment("所有玩家阵营额外仇视的生物类型 ID。")
            .comment("  适用于所有玩家（每个玩家的玩家阵营都共享这一份）。")
            .defineListAllowEmpty("playerFactionEnemies",
                    VANILLA_MONSTER_ENEMIES,
                    FactionConfig::validateEntityName);

    // ===================== 每阵营默认敌对阵营 =====================
    // 阵营名单语法：
    //   · "monster_conquest:xxx"          → 仇视该具体 NPC 阵营
    //   · "monster_conquest:player"       → 仇视所有玩家阵营（大玩家阵营）
    //   · "monster_conquest:player/<UUID>" → 只仇视指定 UUID 的玩家阵营
    //   玩家阵营间默认互不仇视（除非显式设置）。

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_UNDEAD_HOSTILE_DEF = BUILDER
            .comment("亡灵阵营默认敌视的其它阵营 ID。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.undead",
                    List.of("monster_conquest:villager",
                            "monster_conquest:player"),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_VILLAGER_HOSTILE_DEF = BUILDER
            .comment("村民阵营默认敌视的其它阵营 ID。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.villager",
                    List.of("monster_conquest:undead",
                            "monster_conquest:illager",
                            "monster_conquest:nether",
                            "monster_conquest:monster"),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_ILLAGER_HOSTILE_DEF = BUILDER
            .comment("灾厄阵营默认敌视的其它阵营 ID。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.illager",
                    List.of("monster_conquest:villager",
                            "monster_conquest:player"),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_NETHER_HOSTILE_DEF = BUILDER
            .comment("下界阵营默认敌视的其它阵营 ID。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.nether",
                    List.of("monster_conquest:villager",
                            "monster_conquest:player"),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_END_HOSTILE_DEF = BUILDER
            .comment("末地阵营默认敌视的其它阵营 ID（默认空：末地不默认仇视其他阵营）。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.end",
                    List.of(),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_MONSTER_HOSTILE_DEF = BUILDER
            .comment("怪物阵营默认敌视的其它阵营 ID。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.monster",
                    List.of("monster_conquest:villager",
                            "monster_conquest:player"),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_NEUTRAL_HOSTILE_DEF = BUILDER
            .comment("中立阵营默认敌视的其它阵营 ID（默认空）。")
            .comment("  可用 monster_conquest:player 表示'仇视所有玩家'。")
            .defineListAllowEmpty("factionHostile.neutral",
                    List.of(),
                    FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> PLAYER_FACTION_HOSTILE_DEF = BUILDER
            .comment("所有玩家阵营默认共同敌视的其它阵营 ID（即'大玩家阵营'的敌对名单）。")
            .comment("  举例：填 monster_conquest:undead → 所有玩家默认仇视亡灵。")
            .defineListAllowEmpty("playerFactionHostile",
                    List.of("monster_conquest:undead",
                            "monster_conquest:nether",
                            "monster_conquest:monster",
                            "monster_conquest:illager"),
                    FactionConfig::validateFactionName);

    // ===================== 每阵营默认结盟阵营（单向） =====================
    // 语法与 factionHostile 相同。单向语义：本方写谁 = 本方不攻击谁。
    //   双方都写 → 完全互不攻击。单方写 → 写的一方不攻击对方，对方仍可攻击写的一方。

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_UNDEAD_ALLY_DEF = BUILDER
            .comment("亡灵阵营默认单向结盟的其它阵营 ID（可以填 monster_conquest:player 表示对所有玩家免伤）")
            .defineListAllowEmpty("factionAlly.undead", List.of(), FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_VILLAGER_ALLY_DEF = BUILDER
            .comment("村民阵营默认单向结盟的其它阵营 ID")
            .defineListAllowEmpty("factionAlly.villager", List.of(), FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_ILLAGER_ALLY_DEF = BUILDER
            .comment("灾厄阵营默认单向结盟的其它阵营 ID")
            .defineListAllowEmpty("factionAlly.illager", List.of(), FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_NETHER_ALLY_DEF = BUILDER
            .comment("下界阵营默认单向结盟的其它阵营 ID")
            .defineListAllowEmpty("factionAlly.nether", List.of(), FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_END_ALLY_DEF = BUILDER
            .comment("末地阵营默认单向结盟的其它阵营 ID")
            .defineListAllowEmpty("factionAlly.end", List.of(), FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_MONSTER_ALLY_DEF = BUILDER
            .comment("怪物阵营默认单向结盟的其它阵营 ID")
            .defineListAllowEmpty("factionAlly.monster", List.of(), FactionConfig::validateFactionName);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_NEUTRAL_ALLY_DEF = BUILDER
            .comment("中立阵营默认单向结盟的其它阵营 ID")
            .defineListAllowEmpty("factionAlly.neutral", List.of(), FactionConfig::validateFactionName);

    // ===================== 每阵营转化规则 =====================
    // 格式："源实体ID->士兵实体ID"
    //   例如 "minecraft:zombie->monster_conquest:soldier_zombie"
    //   首领在招募范围内发现源实体时，会把它转化为对应的士兵。
    //   未列出的生物 → 该阵营首领不会转化。
    //   目标可以写多个，用 | 分隔，转化时随机取一个（例如掠夺者随机变四种枪手）。
    //   每阵营有独立开关；本项只控制"把非本模组的生物转化为士兵"。
    //     巡逻队长 / 首领收编同阵营孤儿士兵是固定开启的，不受这里影响（已解耦）。

    private static final List<String> UNDEAD_CONVERSION_DEFAULTS = List.of(
            "minecraft:zombie->monster_conquest:soldier_zombie",
            "minecraft:husk->monster_conquest:soldier_husk",
            "minecraft:drowned->monster_conquest:soldier_drowned",
            "minecraft:zombie_villager->monster_conquest:soldier_zombie_villager",
            "minecraft:wither_skeleton->monster_conquest:soldier_wither_skeleton",
            "minecraft:skeleton->monster_conquest:soldier_skeleton",
            "minecraft:stray->monster_conquest:soldier_stray",
            "minecraft:bogged->monster_conquest:soldier_bogged",
            "minecraft:witch->monster_conquest:soldier_witch"
    );

    private static final List<String> ILLAGER_CONVERSION_DEFAULTS = List.of(
            "minecraft:vindicator->monster_conquest:soldier_vindicator",
            "minecraft:evoker->monster_conquest:soldier_evoker",
            "minecraft:witch->monster_conquest:soldier_witch",
            // 掠夺者随机转化为四种枪手之一
            "minecraft:pillager->monster_conquest:soldier_gunner_rifle"
                    + "|monster_conquest:soldier_gunner_shotgun"
                    + "|monster_conquest:soldier_gunner_rocket"
                    + "|monster_conquest:soldier_gunner_sniper"
    );

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_UNDEAD_ENABLED_DEF = BUILDER
            .comment("亡灵阵营首领是否可以把非本模组的生物转化为士兵（默认开启）。")
            .comment("  收编同阵营孤儿士兵是固定开启的，不受这里影响。")
            .define("factionConversion.undead.enabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_UNDEAD_DEF = BUILDER
            .comment("亡灵阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .comment("  目标可写多个（用 | 分隔）随机选择；默认包含僵尸 / 尸壳 / 溺尸 / 僵尸村民 /")
            .comment("  凋零骷髅 / 骷髅 / 流浪者 / 沼骸 / 女巫。")
            .defineListAllowEmpty("factionConversion.undead.rules",
                    UNDEAD_CONVERSION_DEFAULTS,
                    FactionConfig::validateConversionRule);

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_VILLAGER_ENABLED_DEF = BUILDER
            .comment("村民阵营首领是否可以把非本模组的生物转化为士兵（默认关闭）。")
            .define("factionConversion.villager.enabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_VILLAGER_DEF = BUILDER
            .comment("村民阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .defineListAllowEmpty("factionConversion.villager.rules",
                    List.of(),
                    FactionConfig::validateConversionRule);

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_ILLAGER_ENABLED_DEF = BUILDER
            .comment("灾厄阵营首领是否可以把非本模组的生物转化为士兵（默认开启）。")
            .comment("  收编同阵营孤儿士兵是固定开启的，不受这里影响。")
            .define("factionConversion.illager.enabled", true);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_ILLAGER_DEF = BUILDER
            .comment("灾厄阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .comment("  目标可写多个（用 | 分隔）随机选择；默认包含卫道士 / 唤魔者 / 女巫，")
            .comment("  掠夺者默认随机转化为四种枪手之一。")
            .defineListAllowEmpty("factionConversion.illager.rules",
                    ILLAGER_CONVERSION_DEFAULTS,
                    FactionConfig::validateConversionRule);

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_NETHER_ENABLED_DEF = BUILDER
            .comment("下界阵营首领是否可以把非本模组的生物转化为士兵（默认关闭）。")
            .define("factionConversion.nether.enabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_NETHER_DEF = BUILDER
            .comment("下界阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .defineListAllowEmpty("factionConversion.nether.rules",
                    List.of(),
                    FactionConfig::validateConversionRule);

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_END_ENABLED_DEF = BUILDER
            .comment("末地阵营首领是否可以把非本模组的生物转化为士兵（默认关闭）。")
            .define("factionConversion.end.enabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_END_DEF = BUILDER
            .comment("末地阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .defineListAllowEmpty("factionConversion.end.rules",
                    List.of(),
                    FactionConfig::validateConversionRule);

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_MONSTER_ENABLED_DEF = BUILDER
            .comment("怪物阵营首领是否可以把非本模组的生物转化为士兵（默认关闭）。")
            .define("factionConversion.monster.enabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_MONSTER_DEF = BUILDER
            .comment("怪物阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .defineListAllowEmpty("factionConversion.monster.rules",
                    List.of(),
                    FactionConfig::validateConversionRule);

    public static final ModConfigSpec.BooleanValue FACTION_CONVERSION_NEUTRAL_ENABLED_DEF = BUILDER
            .comment("中立阵营首领是否可以把非本模组的生物转化为士兵（默认关闭）。")
            .define("factionConversion.neutral.enabled", false);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACTION_CONVERSION_NEUTRAL_DEF = BUILDER
            .comment("中立阵营可转化的怪物列表。格式：源实体ID->士兵实体ID")
            .defineListAllowEmpty("factionConversion.neutral.rules",
                    List.of(),
                    FactionConfig::validateConversionRule);

    // ===================== 首领召唤 =====================

    public static final ModConfigSpec.IntValue SUMMON_INTERVAL_DEF = BUILDER
            .comment("召唤间隔（tick），默认200=10秒。所有阵营共享。")
            .defineInRange("summonInterval", 200, 20, 12000);

    public static final ModConfigSpec.IntValue SUMMON_LOAD_GRACE_DEF = BUILDER
            .comment("首领加载后延迟多少 tick 才允许召唤。默认100=5秒。所有阵营共享。")
            .defineInRange("summonLoadGraceTicks", 100, 0, 1200);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_UNDEAD_ENABLED_DEF = BUILDER
            .comment("亡灵阵营首领是否自动召唤士兵")
            .define("factionSummon.undead.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_UNDEAD_MIN_DEF = BUILDER
            .comment("亡灵阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.undead.minSoldiers", 16, 0, 64);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_VILLAGER_ENABLED_DEF = BUILDER
            .comment("村民阵营首领是否自动召唤士兵")
            .define("factionSummon.villager.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_VILLAGER_MIN_DEF = BUILDER
            .comment("村民阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.villager.minSoldiers", 16, 0, 64);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_ILLAGER_ENABLED_DEF = BUILDER
            .comment("灾厄阵营首领是否自动召唤士兵")
            .define("factionSummon.illager.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_ILLAGER_MIN_DEF = BUILDER
            .comment("灾厄阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.illager.minSoldiers", 16, 0, 64);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_NETHER_ENABLED_DEF = BUILDER
            .comment("下界阵营首领是否自动召唤士兵")
            .define("factionSummon.nether.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_NETHER_MIN_DEF = BUILDER
            .comment("下界阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.nether.minSoldiers", 16, 0, 64);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_END_ENABLED_DEF = BUILDER
            .comment("末地阵营首领是否自动召唤士兵")
            .define("factionSummon.end.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_END_MIN_DEF = BUILDER
            .comment("末地阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.end.minSoldiers", 16, 0, 64);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_MONSTER_ENABLED_DEF = BUILDER
            .comment("怪物阵营首领是否自动召唤士兵")
            .define("factionSummon.monster.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_MONSTER_MIN_DEF = BUILDER
            .comment("怪物阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.monster.minSoldiers", 16, 0, 64);

    public static final ModConfigSpec.BooleanValue FACTION_SUMMON_NEUTRAL_ENABLED_DEF = BUILDER
            .comment("中立阵营首领是否自动召唤士兵")
            .define("factionSummon.neutral.enabled", true);
    public static final ModConfigSpec.IntValue FACTION_SUMMON_NEUTRAL_MIN_DEF = BUILDER
            .comment("中立阵营首领麾下士兵少于此数量时触发召唤（0 = 不召唤）")
            .defineInRange("factionSummon.neutral.minSoldiers", 16, 0, 64);

    // ===================== 村庄巡逻队（村民阵营） =====================
    // 只影响"随村庄自然刷出"的村民阵营单位：
    //   刷怪蛋 / 指令 / 玩家招募 / 首领召唤的单位不受这里的开关与范围限制影响。

    public static final ModConfigSpec.BooleanValue VILLAGER_VILLAGE_SPAWN_ENABLED_DEF = BUILDER
            .comment("村民阵营是否随村庄自然刷出巡逻队（1 名巡逻队长 + 3 名士兵）。")
            .comment("  只以原版村庄判定为准：玩家附近已认领的村庄兴趣点（床 / 工作站）即视为村庄；")
            .comment("  不再要求村庄里必须有村民（原版已用 POI 定义村庄，村民被封住 / 埋住也不影响）。")
            .comment("  队长阵亡后，只要村庄仍未达到队长 / 士兵上限，就会像铁傀儡一样后续自然补刷。")
            .define("villageSpawn.villager.enabled", true);

    public static final ModConfigSpec.IntValue VILLAGER_VILLAGE_MAX_CAPTAINS_DEF = BUILDER
            .comment("每个村庄最多同时存在的村庄巡逻队长数量（每名队长出生带 3 名士兵）。")
            .comment("  只有\"队长数 < 本上限 且 村庄士兵总数 + 3 ≤ 本上限 × 3\"时才会补刷新巡逻队；")
            .comment("  即每次补刷都为新队长要带的 3 名士兵预留位置，士兵总数不会超过上限 × 3。")
            .defineInRange("villageSpawn.villager.maxCaptains", 3, 0, 16);

    public static final ModConfigSpec.DoubleValue VILLAGER_VILLAGE_RADIUS_DEF = BUILDER
            .comment("村庄巡逻队的活动半径（格）。")
            .comment("  巡逻时不会离开村庄范围；战斗与索敌也不会追击超出这个范围的敌人。")
            .defineInRange("villageSpawn.villager.radius", 48.0, 16.0, 128.0);

    public static final ModConfigSpec.IntValue VILLAGER_VILLAGE_CHECK_INTERVAL_DEF = BUILDER
            .comment("村庄巡逻队的自然刷出检测间隔（tick），默认 600 = 30 秒。")
            .defineInRange("villageSpawn.villager.checkIntervalTicks", 600, 100, 24000);

    public static final ModConfigSpec.BooleanValue VILLAGER_VILLAGE_DEFEND_DEF = BUILDER
            .comment("村庄巡逻队是否像铁傀儡一样保护村民：村民被攻击时立即对攻击者建立仇恨。")
            .comment("  只对村庄自然刷出的单位生效。")
            .define("villageSpawn.villager.defendVillagers", true);

    // ===================== 野外巡逻队（自然生成） =====================
    // 像原版掠夺者巡逻队一样在世界上自然出现：随机玩家的 24~48 格外生成 1 支巡逻队
    //   （1 名巡逻队长 + 3 名士兵，队长自带）。
    // 维度与阵营固定对应：主世界 = 亡灵 / 灾厄 / 中立 / 怪物，下界 = 下界，末地 = 末地。
    // 默认只开亡灵与灾厄；生成的队伍不绑锚点，远离玩家后会像普通怪物一样消失。
    // 和平难度、关闭 doPatrolSpawning / doMobSpawning、村庄附近、永不刷巡逻队的生物群系都不会刷。

    public static final ModConfigSpec.IntValue NATURAL_PATROL_CHECK_INTERVAL_DEF = BUILDER
            .comment("野外巡逻队的生成检测间隔（tick），默认 1200 = 1 分钟。")
            .defineInRange("naturalPatrol.checkIntervalTicks", 1200, 200, 72000);

    public static final ModConfigSpec.DoubleValue NATURAL_PATROL_CHANCE_DEF = BUILDER
            .comment("每次检测真正生成的概率（0~1），默认 0.25：平均约 4 分钟一支，频率不会太高。")
            .defineInRange("naturalPatrol.spawnChance", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue NATURAL_PATROL_UNDEAD_DEF = BUILDER
            .comment("主世界是否自然生成亡灵阵营巡逻队（默认开）。")
            .define("naturalPatrol.undead.enabled", true);

    public static final ModConfigSpec.BooleanValue NATURAL_PATROL_ILLAGER_DEF = BUILDER
            .comment("主世界是否自然生成灾厄阵营巡逻队（默认开）。")
            .define("naturalPatrol.illager.enabled", true);

    public static final ModConfigSpec.BooleanValue NATURAL_PATROL_NEUTRAL_DEF = BUILDER
            .comment("主世界是否自然生成中立阵营巡逻队（默认关）。")
            .define("naturalPatrol.neutral.enabled", false);

    public static final ModConfigSpec.BooleanValue NATURAL_PATROL_MONSTER_DEF = BUILDER
            .comment("主世界是否自然生成怪物阵营巡逻队（默认关）。")
            .define("naturalPatrol.monster.enabled", false);

    public static final ModConfigSpec.BooleanValue NATURAL_PATROL_NETHER_DEF = BUILDER
            .comment("下界是否自然生成下界阵营巡逻队（默认关）。")
            .define("naturalPatrol.nether.enabled", false);

    public static final ModConfigSpec.BooleanValue NATURAL_PATROL_END_DEF = BUILDER
            .comment("末地是否自然生成末地阵营巡逻队（默认关）。")
            .comment("  末地阵营目前没有可召唤的士兵，末地巡逻队只有 1 名巡逻队长。")
            .define("naturalPatrol.end.enabled", false);

    // ===================== 袭击增援（灾厄阵营） =====================

    public static final ModConfigSpec.BooleanValue ILLAGER_RAID_SPAWN_ENABLED_DEF = BUILDER
            .comment("灾厄阵营是否在袭击期间向袭击中心派出部队参战。")
            .comment("  派出的部队为 1 名巡逻队长 + 3 名士兵；袭击结束后仍未阵亡的部队会撤离（消失）。")
            .define("raidSpawn.illager.enabled", true);

    public static final ModConfigSpec.IntValue ILLAGER_RAID_MAX_SQUADS_DEF = BUILDER
            .comment("每一波袭击最多刷出的灾厄巡逻队数量（每队 1 名队长 + 3 名士兵）。")
            .comment("  实际数量随袭击波次提高而增加，且不超过本上限。")
            .defineInRange("raidSpawn.illager.maxSquadsPerWave", 5, 1, 10);

    public static final ModConfigSpec.DoubleValue ILLAGER_RAID_HIGH_TIER_CHANCE_DEF = BUILDER
            .comment("袭击增援士兵出现「高于 T0 装备」的概率（默认 0.85；普通召唤为 bonusTierChance，默认 0.15）。")
            .comment("  命中后在 T1 ~ T4 之间随机：T1 20% / T2 20% / T3 30% / T4 30%。")
            .defineInRange("raidSpawn.illager.highTierChance", 0.85, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue ILLAGER_RAID_RADIUS_DEF = BUILDER
            .comment("袭击增援的活动半径（格）：巡逻 / 战斗 / 索敌都不会离开袭击中心太远。")
            .defineInRange("raidSpawn.illager.radius", 64.0, 16.0, 160.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===================== 缓存值 =====================
    public static Set<String> defaultEnemies = new HashSet<>();

    public static Set<String> factionUndeadEnemies = new HashSet<>();
    public static Set<String> factionVillagerEnemies = new HashSet<>();
    public static Set<String> factionIllagerEnemies = new HashSet<>();
    public static Set<String> factionNetherEnemies = new HashSet<>();
    public static Set<String> factionEndEnemies = new HashSet<>();
    public static Set<String> factionMonsterEnemies = new HashSet<>();
    public static Set<String> factionNeutralEnemies = new HashSet<>();
    public static Set<String> playerFactionEnemies = new HashSet<>();

    public static Set<String> factionUndeadHostile = new HashSet<>();
    public static Set<String> factionVillagerHostile = new HashSet<>();
    public static Set<String> factionIllagerHostile = new HashSet<>();
    public static Set<String> factionNetherHostile = new HashSet<>();
    public static Set<String> factionEndHostile = new HashSet<>();
    public static Set<String> factionMonsterHostile = new HashSet<>();
    public static Set<String> factionNeutralHostile = new HashSet<>();
    public static Set<String> playerFactionHostile = new HashSet<>();

    public static Set<String> factionUndeadAlly = new HashSet<>();
    public static Set<String> factionVillagerAlly = new HashSet<>();
    public static Set<String> factionIllagerAlly = new HashSet<>();
    public static Set<String> factionNetherAlly = new HashSet<>();
    public static Set<String> factionEndAlly = new HashSet<>();
    public static Set<String> factionMonsterAlly = new HashSet<>();
    public static Set<String> factionNeutralAlly = new HashSet<>();

    public static int summonInterval;
    public static int summonLoadGraceTicks;

    // 村庄巡逻队（村民阵营）
    public static boolean villagerVillageSpawnEnabled;
    public static int villagerVillageMaxCaptains;
    public static double villagerVillageRadius;
    public static int villagerVillageCheckInterval;
    public static boolean villagerVillageDefend;

    // 野外巡逻队（自然生成）
    public static int naturalPatrolCheckInterval;
    public static double naturalPatrolChance;
    public static boolean naturalPatrolUndeadEnabled;
    public static boolean naturalPatrolIllagerEnabled;
    public static boolean naturalPatrolNeutralEnabled;
    public static boolean naturalPatrolMonsterEnabled;
    public static boolean naturalPatrolNetherEnabled;
    public static boolean naturalPatrolEndEnabled;

    // 袭击增援（灾厄阵营）
    public static boolean illagerRaidSpawnEnabled;
    public static int illagerRaidMaxSquadsPerWave;
    public static double illagerRaidHighTierChance;
    public static double illagerRaidRadius;

    /** 每阵营召唤开关。key = 阵营完整 ID 字符串。 */
    public static final Map<String, Boolean> factionSummonEnabled = new HashMap<>();
    /** 每阵营召唤最少士兵数。key = 阵营完整 ID 字符串。 */
    public static final Map<String, Integer> factionMinSoldier = new HashMap<>();

    /**
     * 每阵营转化规则。
     * key = 阵营完整 ID 字符串（如 "monster_conquest:undead"）。
     * value = Map<源实体ID, 目标士兵实体ID（可能含 | 分隔的多个候选）>。
     */
    public static final Map<String, Map<String, String>> factionConversionRules = new HashMap<>();

    /** 每阵营"把非本模组生物转化为士兵"的开关。key = 阵营完整 ID 字符串。 */
    public static final Map<String, Boolean> factionConversionEnabled = new HashMap<>();

    // ===================== 每阵营查询 =====================

    public static boolean isFactionSummonEnabled(@Nullable ResourceLocation faction) {
        if (faction == null) return true;
        return factionSummonEnabled.getOrDefault(faction.toString(), true);
    }

    public static int getFactionMinSoldier(@Nullable ResourceLocation faction) {
        if (faction == null) return 0;
        return factionMinSoldier.getOrDefault(faction.toString(), 0);
    }

    /** 该阵营能否把 srcEntityId 转化为士兵。返回士兵实体 ID；否则 null。 */
    @Nullable
    public static String getConversionTarget(@Nullable ResourceLocation faction, @Nullable String srcEntityId) {
        if (faction == null || srcEntityId == null) return null;
        if (!isFactionConversionEnabled(faction)) return null;
        Map<String, String> rules = factionConversionRules.get(faction.toString());
        if (rules == null) return null;
        return rules.get(srcEntityId);
    }

    /** 该阵营是否开启了"把非本模组生物转化为士兵"（收编同阵营孤儿士兵不受此影响）。 */
    public static boolean isFactionConversionEnabled(@Nullable ResourceLocation faction) {
        if (faction == null) return false;
        return factionConversionEnabled.getOrDefault(faction.toString(), false);
    }

    // ===================== 校验 =====================

    private static boolean validateEntityName(final Object obj) {
        if (!(obj instanceof String name)) return false;
        if (name.isEmpty()) return false;
        try {
            ResourceLocation rl = ResourceLocation.parse(name);
            return !rl.getPath().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateFactionName(final Object obj) {
        if (!(obj instanceof String name)) return false;
        try {
            ResourceLocation rl = ResourceLocation.parse(name);
            return !rl.getPath().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean validateConversionRule(final Object obj) {
        if (!(obj instanceof String s)) return false;
        int idx = s.indexOf("->");
        if (idx <= 0 || idx >= s.length() - 2) return false;
        try {
            ResourceLocation src = ResourceLocation.parse(s.substring(0, idx).trim());
            if (src.getPath().isEmpty()) return false;
            // 目标可以写多个（用 | 分隔），逐个校验
            for (String part : s.substring(idx + 2).split("\\|")) {
                ResourceLocation dst = ResourceLocation.parse(part.trim());
                if (dst.getPath().isEmpty()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== 加载 =====================
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        defaultEnemies = new HashSet<>(DEFAULT_ENEMIES_DEF.get());

        factionUndeadEnemies = new HashSet<>(FACTION_UNDEAD_ENEMIES_DEF.get());
        factionVillagerEnemies = new HashSet<>(FACTION_VILLAGER_ENEMIES_DEF.get());
        factionIllagerEnemies = new HashSet<>(FACTION_ILLAGER_ENEMIES_DEF.get());
        factionNetherEnemies = new HashSet<>(FACTION_NETHER_ENEMIES_DEF.get());
        factionEndEnemies = new HashSet<>(FACTION_END_ENEMIES_DEF.get());
        factionMonsterEnemies = new HashSet<>(FACTION_MONSTER_ENEMIES_DEF.get());
        factionNeutralEnemies = new HashSet<>(FACTION_NEUTRAL_ENEMIES_DEF.get());
        playerFactionEnemies = new HashSet<>(PLAYER_FACTION_ENEMIES_DEF.get());

        factionUndeadAlly = new HashSet<>(FACTION_UNDEAD_ALLY_DEF.get());
        factionVillagerAlly = new HashSet<>(FACTION_VILLAGER_ALLY_DEF.get());
        factionIllagerAlly = new HashSet<>(FACTION_ILLAGER_ALLY_DEF.get());
        factionNetherAlly = new HashSet<>(FACTION_NETHER_ALLY_DEF.get());
        factionEndAlly = new HashSet<>(FACTION_END_ALLY_DEF.get());
        factionMonsterAlly = new HashSet<>(FACTION_MONSTER_ALLY_DEF.get());
        factionNeutralAlly = new HashSet<>(FACTION_NEUTRAL_ALLY_DEF.get());

        factionUndeadHostile = new HashSet<>(FACTION_UNDEAD_HOSTILE_DEF.get());
        factionVillagerHostile = new HashSet<>(FACTION_VILLAGER_HOSTILE_DEF.get());
        factionIllagerHostile = new HashSet<>(FACTION_ILLAGER_HOSTILE_DEF.get());
        factionNetherHostile = new HashSet<>(FACTION_NETHER_HOSTILE_DEF.get());
        factionEndHostile = new HashSet<>(FACTION_END_HOSTILE_DEF.get());
        factionMonsterHostile = new HashSet<>(FACTION_MONSTER_HOSTILE_DEF.get());
        factionNeutralHostile = new HashSet<>(FACTION_NEUTRAL_HOSTILE_DEF.get());
        playerFactionHostile = new HashSet<>(PLAYER_FACTION_HOSTILE_DEF.get());

        summonInterval = SUMMON_INTERVAL_DEF.get();
        summonLoadGraceTicks = SUMMON_LOAD_GRACE_DEF.get();

        villagerVillageSpawnEnabled = VILLAGER_VILLAGE_SPAWN_ENABLED_DEF.get();
        villagerVillageMaxCaptains = VILLAGER_VILLAGE_MAX_CAPTAINS_DEF.get();
        villagerVillageRadius = VILLAGER_VILLAGE_RADIUS_DEF.get();
        villagerVillageCheckInterval = VILLAGER_VILLAGE_CHECK_INTERVAL_DEF.get();
        villagerVillageDefend = VILLAGER_VILLAGE_DEFEND_DEF.get();

        naturalPatrolCheckInterval = NATURAL_PATROL_CHECK_INTERVAL_DEF.get();
        naturalPatrolChance = NATURAL_PATROL_CHANCE_DEF.get();
        naturalPatrolUndeadEnabled = NATURAL_PATROL_UNDEAD_DEF.get();
        naturalPatrolIllagerEnabled = NATURAL_PATROL_ILLAGER_DEF.get();
        naturalPatrolNeutralEnabled = NATURAL_PATROL_NEUTRAL_DEF.get();
        naturalPatrolMonsterEnabled = NATURAL_PATROL_MONSTER_DEF.get();
        naturalPatrolNetherEnabled = NATURAL_PATROL_NETHER_DEF.get();
        naturalPatrolEndEnabled = NATURAL_PATROL_END_DEF.get();

        illagerRaidSpawnEnabled = ILLAGER_RAID_SPAWN_ENABLED_DEF.get();
        illagerRaidMaxSquadsPerWave = ILLAGER_RAID_MAX_SQUADS_DEF.get();
        illagerRaidHighTierChance = ILLAGER_RAID_HIGH_TIER_CHANCE_DEF.get();
        illagerRaidRadius = ILLAGER_RAID_RADIUS_DEF.get();

        // 每阵营召唤设置
        factionSummonEnabled.clear();
        factionMinSoldier.clear();
        putSummon("monster_conquest:undead",
                FACTION_SUMMON_UNDEAD_ENABLED_DEF.get(), FACTION_SUMMON_UNDEAD_MIN_DEF.get());
        putSummon("monster_conquest:villager",
                FACTION_SUMMON_VILLAGER_ENABLED_DEF.get(), FACTION_SUMMON_VILLAGER_MIN_DEF.get());
        putSummon("monster_conquest:illager",
                FACTION_SUMMON_ILLAGER_ENABLED_DEF.get(), FACTION_SUMMON_ILLAGER_MIN_DEF.get());
        putSummon("monster_conquest:nether",
                FACTION_SUMMON_NETHER_ENABLED_DEF.get(), FACTION_SUMMON_NETHER_MIN_DEF.get());
        putSummon("monster_conquest:end",
                FACTION_SUMMON_END_ENABLED_DEF.get(), FACTION_SUMMON_END_MIN_DEF.get());
        putSummon("monster_conquest:monster",
                FACTION_SUMMON_MONSTER_ENABLED_DEF.get(), FACTION_SUMMON_MONSTER_MIN_DEF.get());
        putSummon("monster_conquest:neutral",
                FACTION_SUMMON_NEUTRAL_ENABLED_DEF.get(), FACTION_SUMMON_NEUTRAL_MIN_DEF.get());

        // 每阵营转化规则
        factionConversionEnabled.clear();
        factionConversionRules.clear();
        putConversion("monster_conquest:undead",
                FACTION_CONVERSION_UNDEAD_ENABLED_DEF.get(), FACTION_CONVERSION_UNDEAD_DEF.get());
        putConversion("monster_conquest:villager",
                FACTION_CONVERSION_VILLAGER_ENABLED_DEF.get(), FACTION_CONVERSION_VILLAGER_DEF.get());
        putConversion("monster_conquest:illager",
                FACTION_CONVERSION_ILLAGER_ENABLED_DEF.get(), FACTION_CONVERSION_ILLAGER_DEF.get());
        putConversion("monster_conquest:nether",
                FACTION_CONVERSION_NETHER_ENABLED_DEF.get(), FACTION_CONVERSION_NETHER_DEF.get());
        putConversion("monster_conquest:end",
                FACTION_CONVERSION_END_ENABLED_DEF.get(), FACTION_CONVERSION_END_DEF.get());
        putConversion("monster_conquest:monster",
                FACTION_CONVERSION_MONSTER_ENABLED_DEF.get(), FACTION_CONVERSION_MONSTER_DEF.get());
        putConversion("monster_conquest:neutral",
                FACTION_CONVERSION_NEUTRAL_ENABLED_DEF.get(), FACTION_CONVERSION_NEUTRAL_DEF.get());
    }

    private static void putSummon(String factionId, boolean enabled, int min) {
        factionSummonEnabled.put(factionId, enabled);
        factionMinSoldier.put(factionId, min);
    }

    private static void putConversion(String factionId, boolean enabled, List<? extends String> raw) {
        factionConversionEnabled.put(factionId, enabled);
        Map<String, String> rules = new LinkedHashMap<>();
        for (String s : raw) {
            if (s == null || s.isEmpty()) continue;
            int idx = s.indexOf("->");
            if (idx <= 0 || idx >= s.length() - 2) continue;
            String src = s.substring(0, idx).trim();
            String dst = s.substring(idx + 2).trim();
            if (src.isEmpty() || dst.isEmpty()) continue;
            rules.put(src, dst);
        }
        factionConversionRules.put(factionId, rules);
    }
}
