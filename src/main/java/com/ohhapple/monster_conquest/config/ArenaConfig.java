package com.ohhapple.monster_conquest.config;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** 自走棋玩法的时间、经济与网络转服配置。卡池价格由兵种注册表维护。 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class ArenaConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue PREPARE_SECONDS_DEF;
    public static final ModConfigSpec.IntValue BATTLE_SECONDS_DEF;
    public static final ModConfigSpec.IntValue OVERTIME_SECONDS_DEF;
    public static final ModConfigSpec.IntValue RESULT_SECONDS_DEF;
    public static final ModConfigSpec.IntValue STARTING_GOLD_DEF;
    public static final ModConfigSpec.IntValue XP_COST_DEF;
    public static final ModConfigSpec.IntValue XP_AMOUNT_DEF;
    public static final ModConfigSpec.IntValue REFRESH_COST_DEF;
    public static final ModConfigSpec.IntValue SHOP_SLOTS_DEF;
    public static final ModConfigSpec.BooleanValue EMBEDDED_GATEWAY_ROUTING_DEF;
    public static final ModConfigSpec.ConfigValue<String> PUBLIC_HOST_DEF;
    public static final ModConfigSpec.IntValue GATEWAY_PORT_DEF;
    public static final ModConfigSpec.ConfigValue<String> GATEWAY_LISTEN_HOST_DEF;
    public static final ModConfigSpec.IntValue GATEWAY_MAX_CONNECTIONS_DEF;
    public static final ModConfigSpec.BooleanValue
            GATEWAY_STRICT_SOURCE_BINDING_DEF;
    public static final ModConfigSpec.IntValue ARENA_SERVER_PORT_DEF;
    public static final ModConfigSpec.IntValue ARENA_MAX_PLAYERS_DEF;
    public static final ModConfigSpec.BooleanValue ARENA_SERVER_ENABLED_DEF;
    public static final ModConfigSpec.IntValue IDLE_SHUTDOWN_SECONDS_DEF;
    public static final ModConfigSpec SPEC;

    static {
        BUILDER.comment("准备、战斗与结算阶段的时间。").push("match");
        PREPARE_SECONDS_DEF = BUILDER
                .comment("首次进入选秀大厅和每轮自动准备阶段的持续时间（秒）。")
                .defineInRange("preparationSeconds", 30, 1, 120);
        BATTLE_SECONDS_DEF = BUILDER
                .comment("每场一对一常规战斗的最长时间（秒）。"
                        + "到时仍有双方存活弈子则进入加时阶段。")
                .defineInRange("battleSeconds", 60, 10, 600);
        OVERTIME_SECONDS_DEF = BUILDER
                .comment("常规战斗未分出胜负时的加时阶段持续时间（秒）。")
                .defineInRange("overtimeSeconds", 20, 1, 120);
        RESULT_SECONDS_DEF = BUILDER
                .comment("战斗结算展示时间（秒）。")
                .defineInRange("resultSeconds", 3, 1, 30);
        BUILDER.pop();

        BUILDER.comment("玩家初始资源、升级和商店规则。").push("economy");
        STARTING_GOLD_DEF = BUILDER
                .comment("每名玩家开局金币。")
                .defineInRange("startingGold", 10, 0, 999);
        XP_COST_DEF = BUILDER
                .comment("购买一次经验的金币花费。")
                .defineInRange("xpCost", 4, 0, 99);
        XP_AMOUNT_DEF = BUILDER
                .comment("购买一次获得的经验。")
                .defineInRange("xpAmount", 4, 1, 99);
        REFRESH_COST_DEF = BUILDER
                .comment("手动刷新商店的金币花费。")
                .defineInRange("refreshCost", 2, 0, 99);
        SHOP_SLOTS_DEF = BUILDER
                .comment("商店弈子槽位数量。")
                .defineInRange("shopSlots", 5, 1, 5);
        BUILDER.pop();

        BUILDER.comment("主服、内置网关与竞技子服的连接配置。").push("network");
        EMBEDDED_GATEWAY_ROUTING_DEF = BUILDER
                .comment("启用模组自带的单端口网关。"
                        + "玩家统一连接 gatewayPort，再由网关分流到主服或竞技服。")
                .define("embeddedGatewayRouting", false);
        PUBLIC_HOST_DEF = BUILDER
                .comment("客户端连接主服、网关或竞技子服时使用的主机名或 IP。"
                        + "回环地址会自动尝试选择本机局域网地址；"
                        + "外网部署时填写玩家实际使用的公网地址。")
                .define("publicHost", "127.0.0.1");

        BUILDER.comment("内置网关的公开入口。").push("gateway");
        GATEWAY_PORT_DEF = BUILDER
                .comment("内置网关的唯一公开端口。主服和竞技服使用本机内部端口，"
                        + "只需对外放行此端口。")
                .defineInRange("gatewayPort", 26565, 1, 65535);
        GATEWAY_LISTEN_HOST_DEF = BUILDER
                .comment("内置网关监听地址；对外提供服务通常使用 0.0.0.0。")
                .define("gatewayListenHost", "0.0.0.0");
        GATEWAY_MAX_CONNECTIONS_DEF = BUILDER
                .comment("内置网关允许同时保持的最大连接数。"
                        + "网关使用 Netty 事件循环，连接数不等同于线程数。"
                        + "应按实际经过网关的主服与竞技服在线人数、"
                        + "重连峰值和状态查询流量设置。")
                .defineInRange("gatewayMaxConnections", 256, 8, 4096);
        GATEWAY_STRICT_SOURCE_BINDING_DEF = BUILDER
                .comment("是否要求转服票据的来源 IP 完全一致。"
                        + "默认关闭以兼容 NAT、CGNAT、双栈和代理；"
                        + "在线模式下的 UUID、玩家名和后端认证仍为必需校验。"
                        + "离线模式启用此项可增加约束，但不能从根本上验证身份。")
                .define("strictSourceBinding", false);
        BUILDER.pop();

        BUILDER.comment("竞技子服的实际监听端口。").push("arena");
        ARENA_SERVER_PORT_DEF = BUILDER
                .comment("本地虚空竞技子服监听端口，也是主服转服和竞技服返回时使用的端口。")
                .defineInRange("arenaServerPort", 24565, 1, 65535);
        ARENA_MAX_PLAYERS_DEF = BUILDER
                .comment("竞技子服允许同时在线的最大玩家数。"
                        + "每个房间最多 8 人，子服可同时承载多个房间。")
                .defineInRange("maxPlayers", 128, 8, 1024);
        BUILDER.pop();
        BUILDER.pop();

        BUILDER.comment("竞技子服的自动关闭行为。").push("childServer");
        ARENA_SERVER_ENABLED_DEF = BUILDER
                .comment("是否启用怪物征伐自走棋竞技子服。"
                        + "关闭后主服不会创建子服进程，也不会启动竞技网关路由。")
                .define("enabled", true);
        IDLE_SHUTDOWN_SECONDS_DEF = BUILDER
                .comment("竞技子服没有玩家、待处理房间和活动对局时，"
                        + "自动关闭前的等待秒数。")
                .defineInRange("idleShutdownSeconds", 30, 5, 3600);
        BUILDER.pop();

        SPEC = BUILDER.build();
    }

    public static int prepareTicks = 100;
    public static int battleTicks = 1200;
    public static int overtimeTicks = 400;
    public static int resultTicks = 60;
    public static int startingGold = 10;
    public static int xpCost = 4;
    public static int xpAmount = 4;
    public static int refreshCost = 2;
    public static int shopSlots = 5;
    public static boolean embeddedGatewayRouting = false;
    public static String publicHost = "127.0.0.1";
    public static int gatewayPort = 26565;
    public static String gatewayListenHost = "0.0.0.0";
    public static int gatewayMaxConnections = 256;
    public static boolean gatewayStrictSourceBinding;
    public static int arenaServerPort = 24565;
    public static int arenaMaxPlayers = 128;
    public static boolean arenaServerEnabled = true;
    public static int idleShutdownTicks = 600;

    private ArenaConfig() {
    }

    @SubscribeEvent
    public static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        prepareTicks = PREPARE_SECONDS_DEF.get() * 20;
        battleTicks = BATTLE_SECONDS_DEF.get() * 20;
        overtimeTicks = OVERTIME_SECONDS_DEF.get() * 20;
        resultTicks = RESULT_SECONDS_DEF.get() * 20;
        startingGold = STARTING_GOLD_DEF.get();
        xpCost = XP_COST_DEF.get();
        xpAmount = XP_AMOUNT_DEF.get();
        refreshCost = REFRESH_COST_DEF.get();
        shopSlots = SHOP_SLOTS_DEF.get();
        embeddedGatewayRouting = EMBEDDED_GATEWAY_ROUTING_DEF.get();
        publicHost = PUBLIC_HOST_DEF.get().trim();
        gatewayPort = GATEWAY_PORT_DEF.get();
        gatewayListenHost = GATEWAY_LISTEN_HOST_DEF.get().trim();
        gatewayMaxConnections = GATEWAY_MAX_CONNECTIONS_DEF.get();
        gatewayStrictSourceBinding =
                GATEWAY_STRICT_SOURCE_BINDING_DEF.get();
        arenaServerPort = ARENA_SERVER_PORT_DEF.get();
        arenaMaxPlayers = ARENA_MAX_PLAYERS_DEF.get();
        arenaServerEnabled = ARENA_SERVER_ENABLED_DEF.get();
        idleShutdownTicks = IDLE_SHUTDOWN_SECONDS_DEF.get() * 20;
    }
}
