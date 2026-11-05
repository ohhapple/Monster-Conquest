package com.ohhapple.monster_conquest.arena;

import java.util.List;

/**
 * 一名玩家的自走棋生涯快照。对局结算只通过这里暴露的聚合值进入大厅，
 * 避免客户端自行推导胜率或连胜。
 */
public record ArenaCareer(
        int matches,
        int wins,
        int topHalf,
        int currentWinStreak,
        int bestWinStreak,
        List<Match> recentMatches) {

    public ArenaCareer {
        recentMatches = List.copyOf(recentMatches);
    }

    public record Match(
            int roomId,
            int placement,
            int participants,
            long finishedAtEpochMillis) {
    }
}
