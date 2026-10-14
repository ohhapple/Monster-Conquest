package com.ohhapple.monster_conquest.config;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 通用设置：统领招募 / 士兵转化与升级 / 形态切换 / 索敌范围。
 * 对应配置文件：config/monster_conquest/general-common.toml
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public class GeneralConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===================== 统领招募 =====================

    public static final ModConfigSpec.IntValue RECRUIT_RADIUS_DEF = BUILDER
            .comment("统领招募士兵的半径（格）")
            .defineInRange("recruitRadius", 64, 4, 128);

    public static final ModConfigSpec.IntValue MAX_ARMY_SIZE_DEF = BUILDER
            .comment("每个统领可指挥的最大士兵数")
            .defineInRange("maxArmySize", 72, 1, 400);

    public static final ModConfigSpec.IntValue THREAT_DURATION_DEF = BUILDER
            .comment("威胁持续时间（tick），默认400=20秒")
            .defineInRange("threatDuration", 400, 20, 6000);

    public static final ModConfigSpec.IntValue RECRUIT_INTERVAL_DEF = BUILDER
            .comment("统领招募扫描间隔（tick），默认10=0.5秒一次")
            .defineInRange("recruitInterval", 10, 1, 200);

    public static final ModConfigSpec.IntValue RECRUIT_PER_SCAN_DEF = BUILDER
            .comment("每次扫描最多转化的士兵数量")
            .defineInRange("recruitPerScan", 5, 1, 100);

    // ===================== 士兵转化与升级 =====================

    // 统一骑兵概率
    public static final ModConfigSpec.DoubleValue CAVALRY_CONVERSION_CHANCE_DEF = BUILDER
            .comment("生物转化时成为骑兵的概率（0.0~1.0）。")
            .comment("  对所有阵营、所有兵种统一生效。")
            .comment("  不命中 → 保持步兵形态。")
            .defineInRange("cavalryConversionChance", 0.45, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue BONUS_TIER_CHANCE_DEF = BUILDER
            .comment("转化时直接获得更高等级装备的概率（0.0~1.0）。")
            .comment("  命中后按递减权重在 TIER_1 ~ TIER_4 之间随机：")
            .comment("    TIER_1 50% / TIER_2 25% / TIER_3 17% / TIER_4 8%")
            .comment("  未命中 → 使用 TIER_0。")
            .defineInRange("bonusTierChance", 0.15, 0.0, 1.0);

    public static final ModConfigSpec.IntValue TIER_UPGRADE_BASE_TICKS_DEF = BUILDER
            .comment("每级升级所需基础 tick 数")
            .defineInRange("tierUpgradeBaseTicks", 600, 20, 200000);

    public static final ModConfigSpec.BooleanValue PLAYER_SOLDIER_TIER_UPGRADE_DEF = BUILDER
            .comment("玩家阵营士兵是否随时间自动升级装备等级（默认关闭：")
            .comment("  募兵站按等级招募，花哪一级的钱就一直是哪一级）。")
            .comment("  关闭后，玩家阵营士兵不会自动升级（NPC 首领的士兵不受此影响）。")
            .define("playerSoldierTierUpgrade", false);

    public static final ModConfigSpec.IntValue FORM_SWITCH_COOLDOWN_DEF = BUILDER
            .comment("士兵在骑兵/步兵之间切换形态的冷却（tick）")
            .defineInRange("formSwitchCooldown", 40, 20, 2000);

    public static final ModConfigSpec.BooleanValue ENABLE_FORM_SWITCH_DEF = BUILDER
            .comment("是否允许士兵在骑兵/步兵之间自动切换形态。关闭后士兵将保持转化时的兵种")
            .define("enableFormSwitch", true);

    // ===================== 索敌 =====================

    public static final ModConfigSpec.DoubleValue ARMY_FOLLOW_RANGE_DEF = BUILDER
            .comment("军团的索敌 / 活动范围上限（格）。招募、阵型刷新、仇恨传播、")
            .comment("  工具扫描等系统仍统一使用这个值；各兵种的索敌半径已细分：")
            .comment("  近战 32 / 双形态·投掷 40 / 弓手 48 / 枪手按枪械射程 / 首领 64，")
            .comment("  但都不会超过这里的设置：调小会整体收紧，调大只放开上限。")
            .defineInRange("armyFollowRange", 128.0, 32.0, 256.0);

    public static final ModConfigSpec.DoubleValue FACTION_SUPPORT_RANGE_DEF = BUILDER
            .comment("同阵营威胁传播的扫描半径（格）。")
            .comment("  当一个 NPC 阵营单位进入战斗 / 被攻击时，")
            .comment("  会向本半径内的所有同阵营单位（首领 / 士兵 / 孤儿士兵）广播威胁。")
            .comment("  独立于 armyFollowRange。调小 → 支援更局部；调大 → 支援更全局。")
            .defineInRange("factionSupportRange", 64.0, 16.0, 256.0);

    // ===================== 和平模式 =====================

    public static final ModConfigSpec.BooleanValue PEACEFUL_REMOVES_UNITS_DEF = BUILDER
            .comment("和平难度下是否像原版怪物一样清除本模组的所有单位（士兵 / 首领 / 巡逻队长）。")
            .comment("  默认关闭：单位保留在世界上，只是不结仇、不索敌、不攻击。")
            .comment("  开启后，切换到和平难度时所有单位（含玩家阵营单位）会被直接清除，")
            .comment("  与和平难度下原版怪物的行为一致。")
            .define("peacefulRemovesUnits", false);

    public static final ModConfigSpec.BooleanValue DEBUG_MOVEMENT_LOG_DEF = BUILDER
            .comment("调试用：每秒输出一行单位行为调用诊断日志（前缀 [MOVE-DIAG]）。")
            .comment("  排查复杂问题。")
            .define("debugMovementLog", false);

    public static final ModConfigSpec.IntValue UNIT_KEEP_LOADED_SECONDS_DEF = BUILDER
            .comment("玩家部队“离队维持加载”秒数：")
            .comment("  士兵还在主人身边时，会不断给所在区块续一张短时的加载票；")
            .comment("  主人突然传送走 / 快速跑远后，部队还会多保持加载这么多秒再卸载 ——")
            .comment("  这段时间足够“跟随传送”把士兵带回主人身边，不会在卸载的瞬间冻在原地。")
            .comment("  0 = 关闭（部队一离开加载范围就立刻卸载）。")
            .defineInRange("unitKeepLoadedSeconds", 5, 0, 60);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===================== 缓存值 =====================
    public static int recruitRadius;
    public static int maxArmySize;
    public static int threatDuration;
    public static int recruitInterval;
    public static int recruitPerScan;
    public static double cavalryConversionChance;
    public static double bonusTierChance;
    public static int tierUpgradeBaseTicks;
    public static boolean playerSoldierTierUpgrade;
    public static int formSwitchCooldown;
    public static boolean enableFormSwitch;

    public static double armyFollowRange;
    public static double factionSupportRange;
    public static boolean peacefulRemovesUnits;
    public static boolean debugMovementLog;
    public static int unitKeepLoadedSeconds;

    // ===================== 加载 =====================
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        recruitRadius = RECRUIT_RADIUS_DEF.get();
        maxArmySize = MAX_ARMY_SIZE_DEF.get();
        threatDuration = THREAT_DURATION_DEF.get();
        recruitInterval = RECRUIT_INTERVAL_DEF.get();
        recruitPerScan = RECRUIT_PER_SCAN_DEF.get();
        cavalryConversionChance = CAVALRY_CONVERSION_CHANCE_DEF.get();
        bonusTierChance = BONUS_TIER_CHANCE_DEF.get();
        tierUpgradeBaseTicks = TIER_UPGRADE_BASE_TICKS_DEF.get();
        playerSoldierTierUpgrade = PLAYER_SOLDIER_TIER_UPGRADE_DEF.get();
        formSwitchCooldown = FORM_SWITCH_COOLDOWN_DEF.get();
        enableFormSwitch = ENABLE_FORM_SWITCH_DEF.get();

        armyFollowRange = ARMY_FOLLOW_RANGE_DEF.get();
        factionSupportRange = FACTION_SUPPORT_RANGE_DEF.get();
        peacefulRemovesUnits = PEACEFUL_REMOVES_UNITS_DEF.get();
        debugMovementLog = DEBUG_MOVEMENT_LOG_DEF.get();
        unitKeepLoadedSeconds = UNIT_KEEP_LOADED_SECONDS_DEF.get();
    }
}
