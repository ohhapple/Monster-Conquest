package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.ArenaCareer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 指定玩家在当前服务器保存的历史战绩。 */
public record ArenaCareerPayload(
        UUID playerId, String playerName, ArenaCareer career)
        implements CustomPacketPayload {

    private static final int MAX_RECENT_MATCHES = 20;

    public static final Type<ArenaCareerPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_career"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaCareerPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArenaCareerPayload decode(RegistryFriendlyByteBuf buf) {
                    UUID playerId = buf.readUUID();
                    String playerName = buf.readUtf(64);
                    return new ArenaCareerPayload(
                            playerId, playerName, decodeCareer(buf));
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf,
                                   ArenaCareerPayload payload) {
                    buf.writeUUID(payload.playerId());
                    buf.writeUtf(payload.playerName(), 64);
                    encodeCareer(buf, payload.career());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encodeCareer(RegistryFriendlyByteBuf buf,
                                     ArenaCareer career) {
        buf.writeVarInt(career.matches());
        buf.writeVarInt(career.wins());
        buf.writeVarInt(career.topHalf());
        buf.writeVarInt(career.currentWinStreak());
        buf.writeVarInt(career.bestWinStreak());
        buf.writeVarInt(career.recentMatches().size());
        for (ArenaCareer.Match match : career.recentMatches()) {
            buf.writeVarInt(match.roomId());
            buf.writeVarInt(match.placement());
            buf.writeVarInt(match.participants());
            buf.writeLong(match.finishedAtEpochMillis());
        }
    }

    private static ArenaCareer decodeCareer(RegistryFriendlyByteBuf buf) {
        int matches = buf.readVarInt();
        int wins = buf.readVarInt();
        int topHalf = buf.readVarInt();
        int currentWinStreak = buf.readVarInt();
        int bestWinStreak = buf.readVarInt();
        int matchCount = Math.max(0,
                Math.min(MAX_RECENT_MATCHES, buf.readVarInt()));
        List<ArenaCareer.Match> recentMatches =
                new ArrayList<>(matchCount);
        for (int i = 0; i < matchCount; i++) {
            recentMatches.add(new ArenaCareer.Match(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readLong()));
        }
        return new ArenaCareer(matches, wins, topHalf,
                currentWinStreak, bestWinStreak, recentMatches);
    }
}
