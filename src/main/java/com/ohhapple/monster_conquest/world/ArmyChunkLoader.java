package com.ohhapple.monster_conquest.world;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * 玩家部队“离队维持加载”：给单位所在的区块续一张短时的加载票。
 *
 *   单位还在主人身边时，由单位自己不断续票（{@link #keepLoaded}）；
 *   主人突然传送走 / 快速跑远后不再续票，最后一张票还能多撑
 *   general-common.toml 里 unitKeepLoadedSeconds 秒再过期 ——
 *   这段时间里单位照常参与服务端逻辑（20 tick 一次的“跟随传送”检查等），
 *   不会因为区块被卸载而瞬间冻在原地，跟随指令下能正常回到主人身边。
 *
 *   加载票过期由原版机制自动回收，不需要手动清理。
 */
public final class ArmyChunkLoader {

    private ArmyChunkLoader() {}

    /** 加载票类型（按配置创建；重复 addTicket 会刷新它的计时）。 */
    @Nullable
    private static TicketType<ChunkPos> ticketType;
    /** 创建当前票类型时用的配置秒数：配置热重载后用来判断要不要重建。 */
    private static int ticketTypeSeconds = Integer.MIN_VALUE;

    private static TicketType<ChunkPos> ticketType() {
        int seconds = Math.max(0, GeneralConfig.unitKeepLoadedSeconds);
        TicketType<ChunkPos> t = ticketType;
        // 配置改了（热重载）就重建：否则票的有效期会一直沿用旧值，直到重启游戏。
        if (t == null || ticketTypeSeconds != seconds) {
            t = TicketType.create("monster_conquest_unit",
                    Comparator.comparingLong(ChunkPos::toLong), Math.max(1, seconds * 20));
            ticketType = t;
            ticketTypeSeconds = seconds;
        }
        return t;
    }

    /** 配置里关闭（0 秒）时返回 false，调用方可以整个跳过。 */
    public static boolean isEnabled() {
        return GeneralConfig.unitKeepLoadedSeconds > 0;
    }

    /**
     * 让这个区块在加载票到期前保持“实体可 tick”状态（distance 2 → 区块自身可 tick 实体）。
     *   重复调用会自动续期（原版 addTicket 每次都会刷新创建时间）。
     */
    public static void keepLoaded(ServerLevel level, ChunkPos pos) {
        if (!isEnabled()) return;
        level.getChunkSource().addRegionTicket(ticketType(), pos, 2, pos);
    }
}
