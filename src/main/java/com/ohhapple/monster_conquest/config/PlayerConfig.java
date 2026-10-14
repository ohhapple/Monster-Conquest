package com.ohhapple.monster_conquest.config;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 玩家设置：玩家阵营士兵跟随 / 小分队 / 玩家军队自动敌对。
 * 对应配置文件：config/monster_conquest/player-common.toml
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public class PlayerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===================== 玩家士兵跟随 =====================

    public static final ModConfigSpec.DoubleValue PLAYER_SOLDIER_TELEPORT_DISTANCE_DEF = BUILDER
            .comment("玩家阵营士兵与玩家超过此距离（格）时，自动传送到玩家身边。")
            .defineInRange("playerSoldierTeleportDistance", 32.0, 4.0, 256.0);

    public static final ModConfigSpec.DoubleValue PLAYER_SOLDIER_COMBAT_TELEPORT_DISTANCE_DEF = BUILDER
            .comment("跟随模式下战斗中士兵与玩家的最大距离（格）。超过就传送。")
            .comment("  非战斗时用 playerSoldierTeleportDistance。")
            .defineInRange("playerSoldierCombatTeleportDistance", 64.0, 8.0, 256.0);

    // ===================== 玩家小分队 =====================

    public static final ModConfigSpec.IntValue MAX_PLAYER_SQUADS_DEF = BUILDER
            .comment("玩家可用的最大小分队数量。")
            .defineInRange("maxPlayerSquads", 5, 1, 20);

    public static final ModConfigSpec.DoubleValue ENLIST_NEARBY_RADIUS_DEF = BUILDER
            .comment("R 轮盘「编入附近单兵」的搜索半径（格）。")
            .comment("  把该半径内属于玩家、且还没有编入任何小队的士兵收进当前选中的小队。")
            .defineInRange("enlistNearbyRadius", 64.0, 8.0, 256.0);

    // ===================== 军队自动敌对 =====================

    public static final ModConfigSpec.BooleanValue AUTO_HOSTILITY_BETWEEN_PLAYER_ARMIES_DEF = BUILDER
            .comment("玩家小军队之间是否自动建立敌对关系。")
            .comment("  关闭（默认）：A 玩家的小分队打了 B 玩家的小分队，两边不会互相仇视。")
            .comment("  开启：被打后自动把对方军队（不是阵营）设为敌对，双向生效。")
            .comment("  NPC 阵营之间的军队永久自动敌对，不受此开关控制。")
            .define("autoHostilityBetweenPlayerArmies", false);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ===================== 缓存值 =====================
    public static double playerSoldierTeleportDistance;
    public static double playerSoldierCombatTeleportDistance;
    public static int maxPlayerSquads;
    public static boolean autoHostilityBetweenPlayerArmies;
    public static double enlistNearbyRadius;

    // ===================== 加载 =====================
    @SubscribeEvent
    public static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;

        playerSoldierTeleportDistance = PLAYER_SOLDIER_TELEPORT_DISTANCE_DEF.get();
        playerSoldierCombatTeleportDistance = PLAYER_SOLDIER_COMBAT_TELEPORT_DISTANCE_DEF.get();
        maxPlayerSquads = MAX_PLAYER_SQUADS_DEF.get();
        autoHostilityBetweenPlayerArmies = AUTO_HOSTILITY_BETWEEN_PLAYER_ARMIES_DEF.get();
        enlistNearbyRadius = ENLIST_NEARBY_RADIUS_DEF.get();
    }
}
