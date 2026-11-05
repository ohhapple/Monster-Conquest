package com.ohhapple.monster_conquest.network.clientbound.arena;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 一名玩家视角的自走棋操作界面快照。 */
public record ArenaPlayerSnapshotPayload(
        boolean active,
        boolean openGui,
        boolean alive,
        boolean spectator,
        UUID viewOwnerId,
        int viewBoardIndex,
        int roomId,
        int round,
        int phase,
        int phaseTicksLeft,
        int phaseTicksTotal,
        int health,
        int gold,
        int caravanStacks,
        int winStreak,
        int lossStreak,
        int baseIncome,
        int interestIncome,
        int streakIncome,
        int lastBattleOutcome,
        int level,
        int xp,
        int xpToNext,
        int boardCount,
        String opponentName,
        UUID opponentId,
        int lastDamage,
        String championName,
        List<ShopCard> shop,
        List<Integer> equipmentInventory,
        List<Integer> equipmentCounts,
        List<PieceView> pieces,
        List<RoundView> rounds,
        List<PlayerStatus> players) implements CustomPacketPayload {

    public static final int BATTLE_NONE = 0;
    public static final int BATTLE_FIGHTING = 1;
    public static final int BATTLE_WON = 2;
    public static final int BATTLE_LOST = 3;
    public static final int BATTLE_DRAW = 4;

    public static final Type<ArenaPlayerSnapshotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID, "arena_player_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArenaPlayerSnapshotPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ArenaPlayerSnapshotPayload decode(RegistryFriendlyByteBuf buf) {
                    boolean active = buf.readBoolean();
                    boolean openGui = buf.readBoolean();
                    boolean alive = buf.readBoolean();
                    boolean spectator = buf.readBoolean();
                    UUID viewOwnerId = buf.readUUID();
                    int viewBoardIndex = buf.readVarInt();
                    int roomId = buf.readVarInt();
                    int round = buf.readVarInt();
                    int phase = buf.readVarInt();
                    int phaseLeft = buf.readVarInt();
                    int phaseTotal = buf.readVarInt();
                    int health = buf.readVarInt();
                    int gold = buf.readVarInt();
                    int caravanStacks = buf.readVarInt();
                    int winStreak = buf.readVarInt();
                    int lossStreak = buf.readVarInt();
                    int baseIncome = buf.readVarInt();
                    int interestIncome = buf.readVarInt();
                    int streakIncome = buf.readVarInt();
                    int lastBattleOutcome = buf.readVarInt();
                    int level = buf.readVarInt();
                    int xp = buf.readVarInt();
                    int xpNext = buf.readVarInt();
                    int boardCount = buf.readVarInt();
                    String opponent = buf.readUtf(64);
                    UUID opponentId = buf.readUUID();
                    int lastDamage = buf.readVarInt();
                    String championName = buf.readUtf(64);

                    int shopCount = buf.readVarInt();
                    List<ShopCard> shop = new ArrayList<>(shopCount);
                    for (int i = 0; i < shopCount; i++) {
                        shop.add(new ShopCard(
                                buf.readVarInt(),
                                readIntList(buf)));
                    }

                    int equipmentCount = buf.readVarInt();
                    List<Integer> equipmentInventory =
                            new ArrayList<>(equipmentCount);
                    for (int i = 0; i < equipmentCount; i++) {
                        equipmentInventory.add(buf.readVarInt());
                    }
                    List<Integer> equipmentCounts =
                            new ArrayList<>(equipmentCount);
                    for (int i = 0; i < equipmentCount; i++) {
                        equipmentCounts.add(buf.readVarInt());
                    }

                    int pieceCount = buf.readVarInt();
                    List<PieceView> pieces = new ArrayList<>(pieceCount);
                    for (int i = 0; i < pieceCount; i++) {
                        UUID id = buf.readUUID();
                        int unitIndex = buf.readVarInt();
                        int star = buf.readVarInt();
                        int boardSlot = buf.readVarInt();
                        int benchSlot = buf.readVarInt();
                        List<Integer> equipment = readEquipment(buf);
                        LiveStats liveStats = buf.readBoolean()
                                ? readLiveStats(buf) : null;
                        pieces.add(new PieceView(
                                id, unitIndex, star,
                                boardSlot, benchSlot,
                                equipment, liveStats));
                    }

                    int roundCount = buf.readVarInt();
                    List<RoundView> rounds =
                            new ArrayList<>(roundCount);
                    for (int i = 0; i < roundCount; i++) {
                        rounds.add(new RoundView(
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readUUID(),
                                buf.readUtf(64),
                                buf.readBoolean(),
                                buf.readBoolean()));
                    }

                    int playerCount = buf.readVarInt();
                    List<PlayerStatus> players = new ArrayList<>(playerCount);
                    for (int i = 0; i < playerCount; i++) {
                        players.add(new PlayerStatus(
                                buf.readUUID(),
                                buf.readUtf(64),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readBoolean(),
                                buf.readBoolean(),
                                buf.readVarInt()));
                    }
                    return new ArenaPlayerSnapshotPayload(
                            active, openGui, alive, spectator,
                            viewOwnerId, viewBoardIndex,
                            roomId, round, phase, phaseLeft,
                            phaseTotal,
                            health, gold, caravanStacks,
                            winStreak, lossStreak,
                            baseIncome, interestIncome,
                            streakIncome, lastBattleOutcome,
                            level, xp, xpNext, boardCount,
                            opponent, opponentId, lastDamage,
                            championName,
                            shop, equipmentInventory, equipmentCounts,
                            pieces, rounds, players);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ArenaPlayerSnapshotPayload payload) {
                    buf.writeBoolean(payload.active());
                    buf.writeBoolean(payload.openGui());
                    buf.writeBoolean(payload.alive());
                    buf.writeBoolean(payload.spectator());
                    buf.writeUUID(payload.viewOwnerId());
                    buf.writeVarInt(payload.viewBoardIndex());
                    buf.writeVarInt(payload.roomId());
                    buf.writeVarInt(payload.round());
                    buf.writeVarInt(payload.phase());
                    buf.writeVarInt(payload.phaseTicksLeft());
                    buf.writeVarInt(payload.phaseTicksTotal());
                    buf.writeVarInt(payload.health());
                    buf.writeVarInt(payload.gold());
                    buf.writeVarInt(payload.caravanStacks());
                    buf.writeVarInt(payload.winStreak());
                    buf.writeVarInt(payload.lossStreak());
                    buf.writeVarInt(payload.baseIncome());
                    buf.writeVarInt(payload.interestIncome());
                    buf.writeVarInt(payload.streakIncome());
                    buf.writeVarInt(payload.lastBattleOutcome());
                    buf.writeVarInt(payload.level());
                    buf.writeVarInt(payload.xp());
                    buf.writeVarInt(payload.xpToNext());
                    buf.writeVarInt(payload.boardCount());
                    buf.writeUtf(payload.opponentName(), 64);
                    buf.writeUUID(payload.opponentId());
                    buf.writeVarInt(payload.lastDamage());
                    buf.writeUtf(payload.championName(), 64);

                    buf.writeVarInt(payload.shop().size());
                    for (ShopCard card : payload.shop()) {
                        buf.writeVarInt(card.unitIndex());
                        buf.writeVarInt(card.traits().size());
                        for (int trait : card.traits()) {
                            buf.writeVarInt(trait);
                        }
                    }

                    buf.writeVarInt(payload.equipmentInventory().size());
                    for (int equipment : payload.equipmentInventory()) {
                        buf.writeVarInt(equipment);
                    }
                    for (int count : payload.equipmentCounts()) {
                        buf.writeVarInt(count);
                    }

                    buf.writeVarInt(payload.pieces().size());
                    for (PieceView piece : payload.pieces()) {
                        buf.writeUUID(piece.id());
                        buf.writeVarInt(piece.unitIndex());
                        buf.writeVarInt(piece.star());
                        buf.writeVarInt(piece.boardSlot());
                        buf.writeVarInt(piece.benchSlot());
                        buf.writeVarInt(piece.equipment().size());
                        for (int equipment : piece.equipment()) {
                            buf.writeVarInt(equipment);
                        }
                        buf.writeBoolean(piece.liveStats() != null);
                        if (piece.liveStats() != null) {
                            writeLiveStats(buf, piece.liveStats());
                        }
                    }

                    buf.writeVarInt(payload.rounds().size());
                    for (RoundView round : payload.rounds()) {
                        buf.writeVarInt(round.round());
                        buf.writeVarInt(round.result());
                        buf.writeUUID(round.opponentId());
                        buf.writeUtf(round.opponentName(), 64);
                        buf.writeBoolean(round.wild());
                        buf.writeBoolean(round.current());
                    }

                    buf.writeVarInt(payload.players().size());
                    for (PlayerStatus status : payload.players()) {
                        buf.writeUUID(status.id());
                        buf.writeUtf(status.name(), 64);
                        buf.writeVarInt(status.boardIndex());
                        buf.writeVarInt(status.health());
                        buf.writeVarInt(status.level());
                        buf.writeBoolean(status.alive());
                        buf.writeBoolean(status.ai());
                        buf.writeVarInt(status.battleResult());
                    }
                }
            };

    public ArenaPlayerSnapshotPayload {
        shop = List.copyOf(shop);
        equipmentInventory = List.copyOf(equipmentInventory);
        equipmentCounts = List.copyOf(equipmentCounts);
        if (equipmentCounts.size() != equipmentInventory.size()) {
            throw new IllegalArgumentException(
                    "Equipment inventory and count sizes must match");
        }
        pieces = List.copyOf(pieces);
        rounds = List.copyOf(rounds);
        players = List.copyOf(players);
        if (championName == null) championName = "";
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record ShopCard(int unitIndex, List<Integer> traits) {

        public ShopCard {
            traits = List.copyOf(traits);
        }
    }

    public record PieceView(
            UUID id,
            int unitIndex,
            int star,
            int boardSlot,
            int benchSlot,
            List<Integer> equipment,
            LiveStats liveStats) {

        public PieceView {
            equipment = List.copyOf(equipment);
        }
    }

    public record LiveStats(
            double currentHealth,
            double maxHealth,
            double attackDamage,
            double armor,
            double magicResistance,
            double attackSpeed,
            double movementSpeed,
            double magicPower,
            double dodgeChance,
            double healingPower,
            double lifesteal,
            double skillChargeGainPercent,
            double critChance,
            double critDamage,
            double shield,
            int skillCharge,
            int skillRequired) {
    }

    public record RoundView(
            int round,
            int result,
            UUID opponentId,
            String opponentName,
            boolean wild,
            boolean current) {
    }

    public record PlayerStatus(
            UUID id,
            String name,
            int boardIndex,
            int health,
            int level,
            boolean alive,
            boolean ai,
            int battleResult) {
    }

    private static List<Integer> readEquipment(RegistryFriendlyByteBuf buf) {
        return readIntList(buf);
    }

    private static List<Integer> readIntList(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Integer> values = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            values.add(buf.readVarInt());
        }
        return values;
    }

    private static LiveStats readLiveStats(
            RegistryFriendlyByteBuf buf) {
        return new LiveStats(
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readVarInt(),
                buf.readVarInt());
    }

    private static void writeLiveStats(
            RegistryFriendlyByteBuf buf, LiveStats stats) {
        buf.writeDouble(stats.currentHealth());
        buf.writeDouble(stats.maxHealth());
        buf.writeDouble(stats.attackDamage());
        buf.writeDouble(stats.armor());
        buf.writeDouble(stats.magicResistance());
        buf.writeDouble(stats.attackSpeed());
        buf.writeDouble(stats.movementSpeed());
        buf.writeDouble(stats.magicPower());
        buf.writeDouble(stats.dodgeChance());
        buf.writeDouble(stats.healingPower());
        buf.writeDouble(stats.lifesteal());
        buf.writeDouble(stats.skillChargeGainPercent());
        buf.writeDouble(stats.critChance());
        buf.writeDouble(stats.critDamage());
        buf.writeDouble(stats.shield());
        buf.writeVarInt(stats.skillCharge());
        buf.writeVarInt(stats.skillRequired());
    }
}
