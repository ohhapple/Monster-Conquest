package com.ohhapple.monster_conquest.arena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 自走棋战绩的主世界存档容器。对局玩法尚未接入时保持空战绩，
 * 结算侧只需要调用 {@link #recordMatch} 写入权威结果。
 */
public final class ArenaStatsData extends SavedData {

    private static final String DATA_NAME = "monster_conquest_arena_stats";
    private static final int HISTORY_LIMIT = 20;
    private static final ArenaCareer EMPTY_CAREER =
            new ArenaCareer(0, 0, 0, 0, 0, List.of());

    private final Map<UUID, MutableCareer> careers = new HashMap<>();

    public static ArenaStatsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(ArenaStatsData::new, ArenaStatsData::load),
                DATA_NAME);
    }

    public ArenaCareer career(UUID playerId) {
        MutableCareer career = careers.get(playerId);
        return career == null ? EMPTY_CAREER : career.snapshot();
    }

    public boolean clearCareer(UUID playerId) {
        if (careers.remove(playerId) == null) return false;
        setDirty();
        return true;
    }

    public void recordMatch(UUID playerId, int roomId, int placement,
                            int participants, long finishedAtEpochMillis) {
        if (placement <= 0 || participants <= 0 || placement > participants) return;

        MutableCareer career = careers.computeIfAbsent(playerId, ignored -> new MutableCareer());
        career.matches++;
        int cutoff = participants / 2;
        boolean topHalf = cutoff > 0 && placement <= cutoff;
        if (topHalf) career.topHalf++;
        if (placement == 1) {
            career.wins++;
        }
        if (topHalf) {
            career.currentWinStreak++;
            career.bestWinStreak = Math.max(career.bestWinStreak, career.currentWinStreak);
        } else {
            career.currentWinStreak = 0;
        }

        career.recentMatches.add(0, new ArenaCareer.Match(
                roomId, placement, participants, finishedAtEpochMillis));
        while (career.recentMatches.size() > HISTORY_LIMIT) {
            career.recentMatches.remove(career.recentMatches.size() - 1);
        }
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag players = new ListTag();
        for (Map.Entry<UUID, MutableCareer> entry : careers.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", entry.getKey());
            MutableCareer career = entry.getValue();
            playerTag.putInt("Matches", career.matches);
            playerTag.putInt("Wins", career.wins);
            playerTag.putInt("TopHalf", career.topHalf);
            playerTag.putInt("CurrentWinStreak", career.currentWinStreak);
            playerTag.putInt("BestWinStreak", career.bestWinStreak);

            ListTag matches = new ListTag();
            for (ArenaCareer.Match match : career.recentMatches) {
                CompoundTag matchTag = new CompoundTag();
                matchTag.putInt("Room", match.roomId());
                matchTag.putInt("Placement", match.placement());
                matchTag.putInt("Participants", match.participants());
                matchTag.putLong("FinishedAt", match.finishedAtEpochMillis());
                matches.add(matchTag);
            }
            playerTag.put("RecentMatches", matches);
            players.add(playerTag);
        }
        tag.put("Players", players);
        return tag;
    }

    private static ArenaStatsData load(CompoundTag tag, HolderLookup.Provider provider) {
        ArenaStatsData data = new ArenaStatsData();
        ListTag players = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag playerTag = players.getCompound(i);
            if (!playerTag.hasUUID("Player")) continue;

            MutableCareer career = new MutableCareer();
            career.matches = playerTag.getInt("Matches");
            career.wins = playerTag.getInt("Wins");
            career.topHalf = playerTag.getInt("TopHalf");
            career.currentWinStreak = playerTag.getInt("CurrentWinStreak");
            career.bestWinStreak = playerTag.getInt("BestWinStreak");

            ListTag matches = playerTag.getList("RecentMatches", Tag.TAG_COMPOUND);
            for (int j = 0; j < matches.size() && j < HISTORY_LIMIT; j++) {
                CompoundTag matchTag = matches.getCompound(j);
                career.recentMatches.add(new ArenaCareer.Match(
                        matchTag.getInt("Room"),
                        matchTag.getInt("Placement"),
                        matchTag.getInt("Participants"),
                        matchTag.getLong("FinishedAt")));
            }
            data.careers.put(playerTag.getUUID("Player"), career);
        }
        return data;
    }

    private static final class MutableCareer {
        private int matches;
        private int wins;
        private int topHalf;
        private int currentWinStreak;
        private int bestWinStreak;
        private final List<ArenaCareer.Match> recentMatches = new ArrayList<>();

        private ArenaCareer snapshot() {
            return new ArenaCareer(
                    matches,
                    wins,
                    topHalf,
                    currentWinStreak,
                    bestWinStreak,
                    recentMatches);
        }
    }
}
