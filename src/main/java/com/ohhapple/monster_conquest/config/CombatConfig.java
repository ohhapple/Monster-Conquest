package com.ohhapple.monster_conquest.config;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 战斗设置：盾牌格挡 / 脱战回血 / 箭矢 / 枪械 / 首领行军 / 战斗距离。
 * 对应配置文件：config/monster_conquest/combat-common.toml
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public class CombatConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===================== 盾牌格挡 =====================

    public static final ModConfigSpec.DoubleValue SHIELD_BLOCK_CHANCE_DEF = BUILDER
            .comment("持盾士兵/首领被攻击时免疫伤害的概率（0.0~1.0）。触发时播放盾牌格挡音效")
            .defineInRange("shieldBlockChance", 0.5, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue SHIELD_BLOCK_MAX_ABSORB_DEF = BUILDER
            .comment("盾牌单次格挡能吸收的最大伤害（基础值，TIER_0）。超过部分正常结算")
            .defineInRange("shieldBlockMaxAbsorb", 20.0, 0.0, 1000.0);

    public static final ModConfigSpec.DoubleValue SHIELD_BLOCK_MAX_ABSORB_PER_TIER_DEF = BUILDER
            .comment("盾牌每提升一级装备等级额外增加的最大吸收伤害")
            .defineInRange("shieldBlockMaxAbsorbPerTier", 10.0, 0.0, 1000.0);

    // ===================== 脱战回血 =====================

    public static final ModConfigSpec.BooleanValue ENABLE_OUT_OF_COMBAT_REGEN_DEF = BUILDER
            .comment("士兵、首领、战马脱战后是否自动回血")
            .define("enableOutOfCombatRegen", true);

    public static final ModConfigSpec.IntValue OUT_OF_COMBAT_REGEN_DELAY_DEF = BUILDER
            .comment("脱战后多久开始回血（tick）。默认100=5秒")
            .defineInRange("outOfCombatRegenDelay", 100, 20, 6000);

    public static final ModConfigSpec.IntValue OUT_OF_COMBAT_REGEN_INTERVAL_DEF = BUILDER
            .comment("回血间隔（tick）。默认40=2秒回复一次")
            .defineInRange("outOfCombatRegenInterval", 40, 10, 600);

    // ===================== 箭矢 =====================

    public static final ModConfigSpec.DoubleValue ARROW_SPEED_DEF = BUILDER
            .comment("军团射手箭矢的基础初速。实际发射速度会在此基础上乘以 0.85~1.15 的随机系数，")
            .comment("并会根据抛物线解算自动调整角度。不会固定于一个值。")
            .defineInRange("arrowSpeed", 3.0, 1.0, 20.0);

    public static final ModConfigSpec.DoubleValue ARROW_INACCURACY_DEF = BUILDER
            .comment("箭矢散布（越小越准）")
            .defineInRange("arrowInaccuracy", 0.3, 0.0, 10.0);

    public static final ModConfigSpec.DoubleValue ARROW_BASE_DAMAGE_DEF = BUILDER
            .comment("箭矢基础伤害")
            .defineInRange("arrowBaseDamage", 1.6, 0.0, 200.0);

    public static final ModConfigSpec.IntValue ARROW_LINGER_TICKS_DEF = BUILDER
            .comment("军团抛射物落地后存活的时间（tick），默认 60 = 3 秒")
            .defineInRange("arrowLingerTicks", 60, 0, 600);

    public static final ModConfigSpec.IntValue ARROW_FREEZE_BASE_SECONDS_DEF = BUILDER
            .comment("村民弓箭手箭矢造成冻结的基础时长（秒），默认 4 秒。")
            .defineInRange("arrowFreezeBaseSeconds", 4, 0, 30);

    public static final ModConfigSpec.IntValue ARROW_FREEZE_SECONDS_PER_TIER_DEF = BUILDER
            .comment("村民弓箭手箭矢冻结时长每提升一级装备等级的加成（秒）。")
            .comment("  默认 3 秒：T0 = 4 秒，T4 = 4 + 3×4 = 16 秒。")
            .defineInRange("arrowFreezeSecondsPerTier", 3, 0, 30);

    // ===================== 玩家枪械 =====================

    public static final ModConfigSpec.BooleanValue GUN_REQUIRE_AMMO_DEF = BUILDER
            .comment("玩家使用枪械（步枪 / 霰弹枪 / 狙击枪 / 火箭筒）时是否需要消耗弹药。")
            .comment("  开启：弹匣打空后自动装填，从背包消耗子弹 / 火箭弹。")
            .comment("  关闭：无限弹药，无需装填。")
            .define("gunRequireAmmo", true);

    public static final ModConfigSpec.BooleanValue GUN_HEADSHOT_DEF = BUILDER
            .comment("玩家子弹命中头部时是否造成额外伤害（不同枪械倍率不同）。")
            .define("gunHeadshot", true);

    // ===================== 首领行军 =====================

    public static final ModConfigSpec.BooleanValue ENABLE_MARCH_DEF = BUILDER
            .comment("首领在无战斗目标时是否自然游走：像原版生物那样随机找附近的位置散步。")
            .comment("开启后不强行拉直队伍朝向；")
            .comment("关闭（默认）时首领原地待命。控制工具手动指定的行军目标不受这个开关影响。")
            .define("enableMarch", false);

    // 卡住时是否传送到敌方附近
    public static final ModConfigSpec.BooleanValue ENABLE_STUCK_TELEPORT_NEAR_ENEMY_DEF = BUILDER
            .comment("军队战斗中真的卡住不动（撞墙 / 卡地形）、并且打不到敌人时，")
            .comment("  是否传送到敌方附近：")
            .comment("  · 只有「到敌人有可行路线」时才会传送到敌人附近；")
            .comment("    寻路不通（例如攻城时敌人站在城墙上）不会传送过去，只把该目标暂时拉黑（30 秒）。")
            .comment("  · 只是绕路 / 徘徊（一直在动但没接近目标）不会触发传送。")
            .comment("  · 能打到敌人（站桩射击 / 近战挥砍）时即使没有位移也不会传送。")
            .comment("  锚定单位（村庄巡逻 / 袭击增援）只在目标位于自己活动范围内时才会传送到敌人附近。")
            .comment("  关闭 → 卡住时只在原地附近找一个安全点传送，不会瞬移到敌人面前。")
            .comment("  开启 → 卡住且打不到时直接传送到目标周围（默认，便于快速接战）。")
            .define("enableStuckTeleportNearEnemy", true);

    // ===================== 战斗距离约束 =====================

    public static final ModConfigSpec.IntValue MAX_COMBAT_DISTANCE_DEF = BUILDER
            .comment("战斗中士兵与首领的最大距离（格）。超过会被传送回首身边。")
            .defineInRange("maxCombatDistance", 128, 64, 512);

    // 硬驻守索敌半径
    public static final ModConfigSpec.DoubleValue HARD_STATION_RANGE_DEF = BUILDER
            .comment("硬驻守时近战 / 投掷兵的最大索敌半径（格）。")
            .comment("  目标相对驻守锚点超出此半径 → 不被视为敌人。")
            .comment("  弓手 / 双形态不受限（不移动，由 BallisticAim 判断能否命中）。")
            .defineInRange("hardStationRange", 48.0, 8.0, 256.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===================== 缓存值 =====================
    public static double shieldBlockChance;
    public static double shieldBlockMaxAbsorb;
    public static double shieldBlockMaxAbsorbPerTier;

    public static boolean enableOutOfCombatRegen;
    public static int outOfCombatRegenDelay;
    public static int outOfCombatRegenInterval;

    public static double arrowSpeed;
    public static double arrowInaccuracy;
    public static double arrowBaseDamage;
    public static int arrowLingerTicks;
    public static int arrowFreezeBaseSeconds;
    public static int arrowFreezeSecondsPerTier;

    public static boolean gunRequireAmmo;
    public static boolean gunHeadshot;

    public static boolean enableMarch;
    public static boolean enableStuckTeleportNearEnemy;

    public static int maxCombatDistance;
    public static double hardStationRange;

    // ===================== 加载 =====================
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        shieldBlockChance = SHIELD_BLOCK_CHANCE_DEF.get();
        shieldBlockMaxAbsorb = SHIELD_BLOCK_MAX_ABSORB_DEF.get();
        shieldBlockMaxAbsorbPerTier = SHIELD_BLOCK_MAX_ABSORB_PER_TIER_DEF.get();

        enableOutOfCombatRegen = ENABLE_OUT_OF_COMBAT_REGEN_DEF.get();
        outOfCombatRegenDelay = OUT_OF_COMBAT_REGEN_DELAY_DEF.get();
        outOfCombatRegenInterval = OUT_OF_COMBAT_REGEN_INTERVAL_DEF.get();

        arrowSpeed = ARROW_SPEED_DEF.get();
        arrowInaccuracy = ARROW_INACCURACY_DEF.get();
        arrowBaseDamage = ARROW_BASE_DAMAGE_DEF.get();
        arrowLingerTicks = ARROW_LINGER_TICKS_DEF.get();
        arrowFreezeBaseSeconds = ARROW_FREEZE_BASE_SECONDS_DEF.get();
        arrowFreezeSecondsPerTier = ARROW_FREEZE_SECONDS_PER_TIER_DEF.get();

        gunRequireAmmo = GUN_REQUIRE_AMMO_DEF.get();
        gunHeadshot = GUN_HEADSHOT_DEF.get();

        enableMarch = ENABLE_MARCH_DEF.get();
        enableStuckTeleportNearEnemy = ENABLE_STUCK_TELEPORT_NEAR_ENEMY_DEF.get();

        maxCombatDistance = MAX_COMBAT_DISTANCE_DEF.get();
        hardStationRange = HARD_STATION_RANGE_DEF.get();
    }
}
