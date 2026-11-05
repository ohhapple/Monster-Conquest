package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.ArenaLobbyManager;
import com.ohhapple.monster_conquest.arena.external.ArenaBridgeResult;
import com.ohhapple.monster_conquest.arena.external.ArenaRuntime;
import com.ohhapple.monster_conquest.arena.external.ExternalArenaServerRuntime;
import com.ohhapple.monster_conquest.arena.entity.ArenaLootDrop;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitCatalog;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitEconomy;
import com.ohhapple.monster_conquest.config.ArenaConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaGameNoticePayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaCombatStatsPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.LiveStats;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.PieceView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.PlayerStatus;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.RoundView;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload.ShopCard;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSpectatorSnapshotPayload;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSpectatorSnapshotPayload.SpectatorPiece;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSpectatorSnapshotPayload.SpectatorPlayerStatus;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaSoundPayload;
import com.ohhapple.monster_conquest.network.serverbound.arena.ArenaIntentPayload;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * 自走棋对局的服务端状态机。逻辑阵容与物理棋盘分离，只有战斗阶段才生成临时士兵实体。
 */
public final class ArenaMatchManager {

    private static final String HP_OBJECTIVE = "mca_hp";
    private static final int HOME_BOARD_COUNT = 8;
    private static final double VANILLA_MAX_HEALTH_LIMIT = 1024.0;
    private static final List<Integer> WILD_ONE_COST_UNITS =
            IntStream.range(0, ArenaUnitCatalog.ALL.size())
                    .filter(index -> ArenaUnitCatalog.get(index)
                            .cost() == 1)
                    .boxed()
                    .toList();
    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();
    private static final ArenaRequestLimiter INTENT_LIMITER =
            new ArenaRequestLimiter(20.0, 30.0);

    private static final Map<Integer, Match> MATCHES_BY_ROOM = new HashMap<>();
    private static final Map<UUID, Match> MATCHES_BY_PLAYER = new HashMap<>();
    private static final Map<UUID, SpectatorView> SPECTATOR_VIEWS =
            new HashMap<>();
    private static final Set<UUID> VIEWERS = new HashSet<>();
    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new HashMap<>();

    @Nullable private static MinecraftServer server;
    @Nullable private static Objective healthObjective;

    private ArenaMatchManager() {
    }

    @Nullable
    public static ArenaPlayerState combatOwner(
            AbstractArmySoldier combatEntity) {
        UUID matchId = combatEntity.getArenaMatchId();
        if (matchId == null) return null;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (!match.id.equals(matchId)) continue;
            for (ArenaPlayerState state : match.players.values()) {
                if (state.combatEntities().contains(
                        combatEntity.getUUID())) {
                    return state;
                }
            }
        }
        return null;
    }

    @Nullable
    public static ArenaPiece combatPieceFor(
            AbstractArmySoldier combatEntity) {
        CombatSide side = combatSideFor(combatEntity);
        if (side == null) return null;
        for (Map.Entry<UUID, AbstractArmySoldier> entry
                : side.pieceEntities.entrySet()) {
            if (entry.getValue() == combatEntity) {
                if (side.actual != null) {
                    return side.actual.piece(entry.getKey());
                }
                for (ArenaPiece piece : side.pieces) {
                    if (piece.id().equals(entry.getKey())) {
                        return piece;
                    }
                }
                return null;
            }
        }
        return null;
    }

    public static void recordArenaDamage(
            @Nullable UUID sourcePieceId,
            AbstractArmySoldier victim,
            float healthDamage, float shieldDamage,
            ArenaUnitPerformance.DamageKind kind) {
        Match match = matchFor(victim);
        if (match == null) return;
        UUID victimPieceId = victim.getArenaSourcePieceId();
        if (victimPieceId != null
                && (healthDamage > 0.0f || shieldDamage > 0.0f)) {
            match.performance(victimPieceId)
                    .addDamageTaken(kind,
                            healthDamage + shieldDamage);
        }
        double dealt = healthDamage + shieldDamage;
        if (sourcePieceId != null && dealt > 0.0f) {
            match.performance(sourcePieceId)
                    .addDamageDealt(kind, dealt);
        }
    }

    public static void recordArenaHealing(
            @Nullable UUID sourcePieceId,
            AbstractArmySoldier healer,
            float healing) {
        if (sourcePieceId == null || healing <= 0.0f) return;
        Match match = matchFor(healer);
        if (match == null) return;
        match.performance(sourcePieceId)
                .addHealing(healing);
    }

    public static void recordArenaShieldGain(
            @Nullable UUID sourcePieceId,
            AbstractArmySoldier recipient,
            float shieldGained) {
        if (sourcePieceId == null || shieldGained <= 0.0f) return;
        Match match = matchFor(recipient);
        if (match == null) return;
        match.performance(sourcePieceId)
                .addShieldHealing(shieldGained);
    }

    @Nullable
    private static Match matchFor(
            AbstractArmySoldier combatEntity) {
        UUID matchId = combatEntity.getArenaMatchId();
        if (matchId == null) return null;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (match.id.equals(matchId)) return match;
        }
        return null;
    }

    public static boolean claimArenaLoot(
            UUID matchId, UUID ownerId,
            int rewardType, int rewardPayload) {
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (match.id.equals(matchId)) {
                return match.claimLoot(
                        ownerId, rewardType, rewardPayload);
            }
        }
        return false;
    }

    /**
     * 临时成装写入实际棋子当前战斗的装备栏，使属性和装备特效立即生效；
     * 战斗结束进入结算阶段前会统一清除，不能进入战后装备或仓库。
     */
    public static boolean grantTemporaryCompletedArenaItem(
            AbstractArmySoldier combatEntity, ArenaEquipment item) {
        if (item == null || item.component()
                || item == ArenaEquipment.REMOVER
                || item.effect() == ArenaEquipment.Effect.SHIELD) {
            return false;
        }
        CombatSide side = combatSideFor(combatEntity);
        Match match = matchForCombatEntity(combatEntity);
        if (side == null || match == null) return false;
        ArenaPiece piece = combatPieceFor(combatEntity);
        if (piece == null || !piece.equipTemporary(item)) {
            return false;
        }
        match.applyArenaAppearance(piece, combatEntity);
        match.applyArenaStats(side, piece, combatEntity, false);
        match.requestSnapshots(false);
        return true;
    }

    @Nullable
    private static CombatSide combatSideFor(
            AbstractArmySoldier combatEntity) {
        UUID matchId = combatEntity.getArenaMatchId();
        if (matchId == null) return null;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (!match.id.equals(matchId)) continue;
            for (Duel duel : match.duels) {
                if (duel.left.entities.contains(combatEntity)) {
                    return duel.left;
                }
                if (duel.right.entities.contains(combatEntity)) {
                    return duel.right;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Match matchForCombatEntity(
            AbstractArmySoldier combatEntity) {
        UUID matchId = combatEntity.getArenaMatchId();
        if (matchId == null) return null;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (match.id.equals(matchId)) return match;
        }
        return null;
    }

    /**
     * 技能分裂等运行时产生的正式战斗实体需要进入当前 CombatSide 的存活
     * 名单，但不占棋盘人口，也不生成额外卡片或刷怪蛋。
     */
    public static boolean registerArenaCombatEntity(
            AbstractArmySoldier combatEntity) {
        UUID matchId = combatEntity.getArenaMatchId();
        UUID ownerId = combatEntity.getCommanderUuid().orElse(null);
        if (matchId == null || ownerId == null) return false;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (!match.id.equals(matchId)) continue;
            for (Duel duel : match.duels) {
                for (CombatSide side : List.of(
                        duel.left, duel.right)) {
                    if (side.side != combatEntity.getArenaSide()
                            || !side.ownerId.equals(ownerId)) {
                        continue;
                    }
                    if (!side.entities.contains(combatEntity)) {
                        side.entities.add(combatEntity);
                    }
                    if (side.actual != null
                            && !side.actual.combatEntities()
                            .contains(combatEntity.getUUID())) {
                        side.actual.combatEntities().add(
                                combatEntity.getUUID());
                    }
                    acquireInitialArenaTarget(combatEntity);
                    return true;
                }
            }
        }
        return false;
    }

    public static void acquireInitialArenaTarget(
            AbstractArmySoldier combatEntity) {
        if (!combatEntity.isAlive() || combatEntity.isRemoved()
                || !combatEntity.isArenaUnit()) {
            return;
        }
        LivingEntity current = combatEntity.getTarget();
        if (current != null && current.isAlive()
                && combatEntity.canAttack(current)
                && combatEntity.isArenaEnemy(current)) {
            return;
        }
        List<AbstractArmySoldier> candidates =
                combatEntity.level().getEntitiesOfClass(
                        AbstractArmySoldier.class,
                        combatEntity.getBoundingBox().inflate(64.0),
                        other -> other.isAlive()
                                && !other.isRemoved()
                                && other.isArenaUnit()
                                && combatEntity.isArenaEnemy(other));
        candidates.sort(Comparator.comparingDouble(
                combatEntity::distanceToSqr));
        if (!candidates.isEmpty()) {
            combatEntity.setTarget(candidates.get(0));
        }
    }

    /**
     * 三星嗅探兽的效果由对决生命周期持有，即使施放者死亡也继续生效，
     * 并在对决结算后停止发放金币。
     */
    public static boolean activateSnifferDominance(
            AbstractArmySoldier source) {
        UUID matchId = source.getArenaMatchId();
        if (matchId == null) return false;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (!match.id.equals(matchId)) continue;
            for (Duel duel : match.duels) {
                if (duel.resolved) continue;
                for (CombatSide side : List.of(
                        duel.left, duel.right)) {
                    if (side.entities.contains(source)) {
                        return side.activateSnifferDominance(
                                source.getUUID(),
                                match.level.getGameTime());
                    }
                }
            }
        }
        return false;
    }

    public static boolean isArenaCombatActive(
            AbstractArmySoldier combatEntity) {
        UUID matchId = combatEntity.getArenaMatchId();
        if (matchId == null) return false;
        for (Match match : MATCHES_BY_ROOM.values()) {
            if (!match.id.equals(matchId)
                    || (match.phase != ArenaPhase.BATTLE
                    && match.phase != ArenaPhase.OVERTIME)) {
                continue;
            }
            for (Duel duel : match.duels) {
                if (duel.resolved) continue;
                if (duel.left.entities.contains(combatEntity)
                        || duel.right.entities.contains(combatEntity)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void setServer(@Nullable MinecraftServer newServer) {
        if (server != null && server != newServer) shutdown();
        server = newServer;
        healthObjective = null;
    }

    public static boolean startRoom(MinecraftServer runningServer, int roomId,
                                    List<ArenaParticipant> participants, boolean autoFillAi) {
        if (MATCHES_BY_ROOM.containsKey(roomId) || participants.isEmpty()) return false;
        ServerLevel level = ArenaDimensions.get(runningServer);
        if (level == null) {
            Monster_conquest.LOGGER.error("Arena level {} is not loaded",
                    ArenaDimensions.ARENA_LEVEL.location());
            return false;
        }
        server = runningServer;
        RandomSource random = secureRandom();
        SecureRandom shopRandom = new SecureRandom();
        ArenaCardPool cardPool = new ArenaCardPool();
        LinkedHashMap<UUID, ArenaPlayerState> states = new LinkedHashMap<>();
        Map<UUID, Integer> seatIndices = new HashMap<>();
        boolean[] occupiedSeats = new boolean[HOME_BOARD_COUNT];
        for (ArenaParticipant participant : participants) {
            if (states.containsKey(participant.id())) return false;
            int seat = participant.seatIndex();
            if (occupiedSeats[seat]) seat = firstFreeSeat(occupiedSeats);
            if (seat < 0) return false;
            occupiedSeats[seat] = true;
            ArenaPlayerState state = new ArenaPlayerState(
                    participant.id(), participant.name(), false, cardPool,
                    shopRandom);
            states.put(participant.id(), state);
            seatIndices.put(participant.id(), seat);
        }

        int aiIndex = 1;
        if (autoFillAi) {
            for (int seat = 0; seat < occupiedSeats.length; seat++) {
                if (occupiedSeats[seat]) continue;
                addAiParticipant(states, seatIndices, occupiedSeats,
                        roomId, seat, aiIndex++, cardPool, shopRandom);
            }
        } else if (states.size() < 2) {
            int seat = firstFreeSeat(occupiedSeats);
            if (seat < 0) return false;
            addAiParticipant(states, seatIndices, occupiedSeats,
                    roomId, seat, aiIndex, cardPool, shopRandom);
        }

        Match match = new Match(
                level, roomId, states, seatIndices, shopRandom);
        MATCHES_BY_ROOM.put(roomId, match);
        for (ArenaPlayerState state : states.values()) {
            if (!state.ai()) MATCHES_BY_PLAYER.put(state.id(), match);
        }

        for (ArenaPlayerState state : states.values()) {
            ServerPlayer player = player(state.id());
            if (player == null) continue;
            if (!ArenaRuntime.isArenaServer()) {
                rememberReturnPoint(player);
                PlayerArmyManager.get(player.getUUID()).setRtsActive(false);
            }
            player.setGameMode(GameType.SPECTATOR);
        }
        match.enterSelection(true);
        Monster_conquest.LOGGER.info(
                "[ARENA-MATCH] start room={} participants={} aiFill={} boards={}",
                roomId, participants.size(), autoFillAi, seatIndices);
        return true;
    }

    private static int firstFreeSeat(boolean[] occupiedSeats) {
        for (int seat = 0; seat < occupiedSeats.length; seat++) {
            if (!occupiedSeats[seat]) return seat;
        }
        return -1;
    }

    private static void addAiParticipant(
            LinkedHashMap<UUID, ArenaPlayerState> states,
            Map<UUID, Integer> seatIndices,
            boolean[] occupiedSeats, int roomId, int seat,
            int aiIndex, ArenaCardPool cardPool,
            SecureRandom shopRandom) {
        occupiedSeats[seat] = true;
        UUID id = UUID.nameUUIDFromBytes(
                ("monster_conquest_ai/" + roomId + "/seat/" + seat).getBytes());
        while (states.containsKey(id)) {
            id = UUID.randomUUID();
        }
        states.put(id, new ArenaPlayerState(
                id, "AI-" + aiIndex, true, cardPool, shopRandom));
        seatIndices.put(id, seat);
    }

    public static void tick(MinecraftServer runningServer) {
        if (server == null) server = runningServer;
        if (MATCHES_BY_ROOM.isEmpty()) return;

        for (Match match : new ArrayList<>(MATCHES_BY_ROOM.values())) {
            match.tick();
            if (match.finished) {
                MATCHES_BY_ROOM.remove(match.roomId);
                for (ArenaPlayerState state : match.players.values()) {
                    if (!state.ai()) MATCHES_BY_PLAYER.remove(state.id());
                }
                if (!ArenaRuntime.isArenaServer()) {
                    ArenaLobbyManager.onMatchFinished(match.roomId);
                }
            }
        }
    }

    public static boolean isPlayerInArenaMatch(UUID playerId) {
        return MATCHES_BY_PLAYER.containsKey(playerId);
    }

    public static boolean enterSpectator(ServerPlayer player, int roomId) {
        Match match = MATCHES_BY_ROOM.get(roomId);
        if (match == null) return false;
        ArenaPlayerState target = match.enterSpectator(player);
        if (target == null) return false;
        SPECTATOR_VIEWS.put(player.getUUID(),
                new SpectatorView(match, target.id()));
        return true;
    }

    public static boolean hasActiveMatches() {
        return !MATCHES_BY_ROOM.isEmpty();
    }

    public static void handleIntent(ServerPlayer player, ArenaIntentPayload payload) {
        if (payload.action() == ArenaIntentPayload.CLOSE) {
            VIEWERS.remove(player.getUUID());
            return;
        }
        if (!INTENT_LIMITER.allow(player.getUUID())) return;

        Match match = MATCHES_BY_PLAYER.get(player.getUUID());
        if (match == null) {
            handleSpectatorIntent(player, payload);
            return;
        }
        if (match.finishPending || match.finished) {
            return;
        }
        ArenaPlayerState state = match.players.get(player.getUUID());
        if (state == null) return;

        if (payload.action() == ArenaIntentPayload.OPEN) {
            VIEWERS.add(player.getUUID());
            sendSnapshot(player, state, match, true);
            return;
        }

        if (payload.action() == ArenaIntentPayload.SPECTATE) {
            ArenaPlayerState target = match.players.get(payload.pieceId());
            if (target == null || !match.teleportToBoardView(player, target)) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.spectate_unavailable");
            }
            return;
        }
        if (payload.action() == ArenaIntentPayload.RETURN_TO_BOARD) {
            if (!match.teleportToBoardView(player, state)) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.board_unavailable");
            }
            return;
        }
        if (payload.action() == ArenaIntentPayload.EXIT_MATCH) {
            if (state.alive()) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.exit_after_elimination");
                return;
            }
            VIEWERS.remove(player.getUUID());
            match.removePreview(state.id());
            match.removeBossBar(state.id());
            MATCHES_BY_PLAYER.remove(player.getUUID());
            if (!ExternalArenaServerRuntime.exitPlayer(player)) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.exit_unavailable");
            } else if (!match.hasHumanControllers()) {
                match.finish();
            }
            return;
        }
        if (payload.action() == ArenaIntentPayload.SURRENDER) {
            if (!state.alive()) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.already_eliminated");
                return;
            }
            match.surrender(state);
            match.requestSnapshots(false);
            return;
        }

        if (payload.action() == ArenaIntentPayload.EQUIP
                || payload.action() == ArenaIntentPayload.COMBINE_EQUIPMENT
                || payload.action() == ArenaIntentPayload.MOVE_EQUIPMENT
                || payload.action() == ArenaIntentPayload.SWAP_EQUIPMENT
                || payload.action() == ArenaIntentPayload.DISASSEMBLE
                || payload.action() == ArenaIntentPayload.DUPLICATE) {
            if (!state.alive() || match.phase == ArenaPhase.FINISHED) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.eliminated_spectator");
                return;
            }
            ArenaPiece equipmentTarget =
                    state.piece(payload.pieceId());
            if (payload.action() == ArenaIntentPayload.DISASSEMBLE
                    && isCombatEquipmentLocked(match.phase)
                    && equipmentTarget != null
                    && equipmentTarget.onBoard()) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.equipment_locked");
                return;
            }
            boolean changed = switch (payload.action()) {
                case ArenaIntentPayload.EQUIP ->
                        state.equipFromInventory(
                                payload.targetIndex(), payload.targetType(),
                                payload.pieceId());
                case ArenaIntentPayload.COMBINE_EQUIPMENT ->
                        // 界面把第一次点击放在 targetType，第二次放在
                        // targetIndex；顺序决定成品保留在哪一格。
                        state.combineEquipment(
                                payload.targetType(), payload.targetIndex());
                case ArenaIntentPayload.MOVE_EQUIPMENT ->
                        state.moveEquipment(
                                payload.targetType(), payload.targetIndex());
                case ArenaIntentPayload.SWAP_EQUIPMENT ->
                        state.swapEquipment(
                                payload.targetType(), payload.targetIndex());
                case ArenaIntentPayload.DISASSEMBLE ->
                        state.disassemble(
                                payload.pieceId(), payload.targetIndex(),
                                payload.targetType());
                case ArenaIntentPayload.DUPLICATE ->
                        state.duplicatePiece(
                                payload.pieceId(),
                                payload.targetIndex(),
                                payload.targetType());
                default -> false;
            };
            if (!changed) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.equipment_failed");
                return;
            } else {
                state.grantEnderDragonReward();
                if (payload.action() == ArenaIntentPayload.EQUIP
                        || payload.action()
                        == ArenaIntentPayload.DUPLICATE) {
                    sendSound(player,
                            ArenaSoundPayload.EQUIPMENT_SUCCESS);
                } else if (payload.action()
                        == ArenaIntentPayload.COMBINE_EQUIPMENT) {
                    sendSound(player,
                            ArenaSoundPayload.EQUIPMENT_COMBINE_SUCCESS);
                } else if (payload.action()
                        == ArenaIntentPayload.MOVE_EQUIPMENT) {
                    sendSound(player,
                            ArenaSoundPayload.EQUIPMENT_MOVE_SUCCESS);
                } else if (payload.action()
                        == ArenaIntentPayload.SWAP_EQUIPMENT) {
                    sendSound(player,
                            ArenaSoundPayload.EQUIPMENT_MOVE_SUCCESS);
                }
                if (payload.action()
                        != ArenaIntentPayload.DUPLICATE) {
                    match.refreshCombatEquipment(
                            state, payload.pieceId());
                }
                if (match.phase == ArenaPhase.SELECTION) {
                    match.refreshPreview(state);
                } else {
                    match.refreshBenchDisplays(state);
                    match.refreshCombatPieces(state);
                }
            }
            match.requestSnapshots(false);
            return;
        }

        if (!state.alive()) {
            notice(player,
                    "gui.monster_conquest.arena.game.notice.eliminated_spectator");
            return;
        }

        ArenaPiece moving = state.piece(payload.pieceId());
        boolean benchOnlyPhase = match.phase == ArenaPhase.BATTLE
                || match.phase == ArenaPhase.OVERTIME
                || match.phase == ArenaPhase.RESULT;
        ArenaPlayerState.ShopPurchaseResult shopPurchase = null;
        boolean changed = switch (payload.action()) {
            case ArenaIntentPayload.BUY -> {
                if (!match.canUseEconomy()) yield false;
                shopPurchase = state.purchaseShopSlot(
                        payload.targetIndex(), payload.targetType(),
                        match.shopRandom);
                Monster_conquest.LOGGER.info(
                        "[ARENA-SHOP] room={} player={} slot={} "
                                + "expectedUnit={} result={} gold={} level={}",
                        match.roomId, state.name(), payload.targetIndex(),
                        payload.targetType(), shopPurchase,
                        state.gold(), state.level());
                yield shopPurchase
                        == ArenaPlayerState.ShopPurchaseResult.SUCCESS;
            }
            case ArenaIntentPayload.REFRESH -> match.canUseEconomy()
                    &&
                    state.refreshShop(match.shopRandom);
            case ArenaIntentPayload.BUY_XP ->
                    match.canUseEconomy() && state.buyXp();
            case ArenaIntentPayload.MOVE ->
                    match.canUseEconomy()
                            && (!benchOnlyPhase
                            || (payload.targetType() == ArenaIntentPayload.BENCH
                            && moving != null && moving.onBench()))
                            && state.movePiece(
                            payload.pieceId(),
                            payload.targetType() == ArenaIntentPayload.BOARD,
                            payload.targetIndex());
            case ArenaIntentPayload.SELL ->
                    match.canUseEconomy()
                            && state.sellPiece(
                            payload.pieceId(),
                            match.phase == ArenaPhase.SELECTION);
            default -> false;
        };

        if (changed
                && payload.action() == ArenaIntentPayload.BUY) {
            sendSound(player, ArenaSoundPayload.PURCHASE_SUCCESS);
        } else if (changed
                && payload.action() == ArenaIntentPayload.SELL) {
            sendSound(player, ArenaSoundPayload.SELL_SUCCESS);
        } else if (changed
                && payload.action() == ArenaIntentPayload.MOVE) {
            sendSound(player, ArenaSoundPayload.UNIT_MOVE_SUCCESS);
        }
        if (changed) {
            state.grantEnderDragonReward();
        }
        if (!changed) {
            notice(player, shopPurchase == null
                    ? "gui.monster_conquest.arena.game.notice.action_failed"
                    : switch (shopPurchase) {
                        case EMPTY_SLOT ->
                                "gui.monster_conquest.arena.game.notice.shop_sold";
                        case STALE_SLOT ->
                                "gui.monster_conquest.arena.game.notice.shop_refreshed";
                        case NO_BENCH_SPACE ->
                                "gui.monster_conquest.arena.game.notice.shop_no_bench";
                        case INSUFFICIENT_GOLD ->
                                "gui.monster_conquest.arena.game.notice.shop_no_gold";
                        case SUCCESS ->
                                "gui.monster_conquest.arena.game.notice.action_failed";
                    });
            return;
        } else if ((payload.action() == ArenaIntentPayload.BUY
                || payload.action() == ArenaIntentPayload.MOVE
                || payload.action() == ArenaIntentPayload.SELL)
                && (match.phase == ArenaPhase.BATTLE
                || match.phase == ArenaPhase.OVERTIME
                || match.phase == ArenaPhase.RESULT)) {
            match.refreshBenchDisplays(state);
            if (payload.action() == ArenaIntentPayload.BUY) {
                match.refreshCombatPieces(state);
            }
        } else if ((payload.action() == ArenaIntentPayload.BUY
                || payload.action() == ArenaIntentPayload.MOVE
                || payload.action() == ArenaIntentPayload.SELL)
                && match.phase == ArenaPhase.SELECTION) {
            match.refreshPreview(state);
        }
        match.requestSnapshots(false);
    }

    private static boolean isCombatEquipmentLocked(ArenaPhase phase) {
        return phase == ArenaPhase.BATTLE
                || phase == ArenaPhase.OVERTIME
                || phase == ArenaPhase.RESULT;
    }

    private static void handleSpectatorIntent(
            ServerPlayer player, ArenaIntentPayload payload) {
        SpectatorView view = SPECTATOR_VIEWS.get(player.getUUID());
        if (view == null) {
            if (payload.action() == ArenaIntentPayload.OPEN) {
                sendInactive(player);
            }
            return;
        }
        Match match = view.match();
        ArenaPlayerState target = match.spectatorTarget(view.targetId());
        if (payload.action() == ArenaIntentPayload.OPEN) {
            if (target == null) {
                sendInactive(player);
                return;
            }
            VIEWERS.add(player.getUUID());
            sendSpectatorSnapshot(player, target, match, true);
            return;
        }
        if (payload.action() == ArenaIntentPayload.SPECTATE) {
            ArenaPlayerState requested = match.players.get(payload.pieceId());
            if (requested == null
                    || !match.teleportToBoardView(player, requested)) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.spectate_unavailable");
                return;
            }
            SPECTATOR_VIEWS.put(player.getUUID(),
                    new SpectatorView(match, requested.id()));
            return;
        }
        if (payload.action() == ArenaIntentPayload.RETURN_TO_BOARD) {
            if (target == null
                    || !match.teleportToBoardView(player, target)) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.board_unavailable");
            }
            return;
        }
        if (payload.action() == ArenaIntentPayload.EXIT_MATCH) {
            if (!ExternalArenaServerRuntime.exitPlayer(player)) {
                notice(player,
                        "gui.monster_conquest.arena.game.notice.exit_unavailable");
                return;
            }
            VIEWERS.remove(player.getUUID());
            SPECTATOR_VIEWS.remove(player.getUUID());
            return;
        }
        notice(player,
                "gui.monster_conquest.arena.game.notice.observer_read_only");
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        INTENT_LIMITER.remove(player.getUUID());
        VIEWERS.remove(player.getUUID());
        SPECTATOR_VIEWS.remove(player.getUUID());
        Match match = MATCHES_BY_PLAYER.remove(player.getUUID());
        if (match != null) {
            if (ArenaRuntime.isArenaServer()) {
                match.surrenderOnDisconnect(player.getUUID());
            } else {
                match.removePlayer(player.getUUID());
                teleportHome(player);
            }
            RETURN_POINTS.remove(player.getUUID());
        }
    }

    public static void onPlayerLogin(ServerPlayer player) {
        if (ArenaRuntime.isArenaServer()) return;
        if (!ArenaDimensions.isArena(player)
                || MATCHES_BY_PLAYER.containsKey(player.getUUID())
                || server == null) {
            return;
        }
        player.setGameMode(server.getDefaultGameType());
        var overworld = server.overworld();
        var spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld,
                spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5,
                overworld.getSharedSpawnAngle(), 0.0f);
    }

    public static void shutdown() {
        INTENT_LIMITER.clear();
        for (Match match : new ArrayList<>(MATCHES_BY_ROOM.values())) {
            match.clearBossBars();
            match.flushLootDrops(true);
            match.cleanupCombat();
            ArenaPlotLayout.release(match.level, match.roomId);
            for (ArenaPlayerState state : match.players.values()) {
                if (!state.ai()) {
                    ServerPlayer player = player(state.id());
                    if (player != null) {
                        sendInactive(player);
                        if (!ArenaRuntime.isArenaServer()) teleportHome(player);
                    }
                }
            }
        }
        MATCHES_BY_ROOM.clear();
        MATCHES_BY_PLAYER.clear();
        SPECTATOR_VIEWS.clear();
        VIEWERS.clear();
        RETURN_POINTS.clear();
        server = null;
        healthObjective = null;
    }

    private static void rememberReturnPoint(ServerPlayer player) {
        RETURN_POINTS.putIfAbsent(player.getUUID(), new ReturnPoint(
                player.level().dimension(),
                player.position(),
                player.getYRot(),
                player.getXRot(),
                player.gameMode.getGameModeForPlayer(),
                player.getVehicle() == null ? null : player.getVehicle().getUUID()));
    }

    private static void teleportHome(ServerPlayer player) {
        ReturnPoint point = RETURN_POINTS.remove(player.getUUID());
        if (point == null || server == null) return;
        ServerLevel target = server.getLevel(point.dimension);
        if (target == null) target = server.overworld();
        player.setGameMode(point.gameType);
        player.teleportTo(target,
                point.position.x, point.position.y, point.position.z,
                point.yaw, point.pitch);
        if (point.vehicleId != null) {
            var vehicle = target.getEntities().get(point.vehicleId);
            if (vehicle != null && vehicle.isAlive()) {
                player.startRiding(vehicle, true);
            }
        }
    }

    private static void sendSnapshot(ServerPlayer player, ArenaPlayerState state,
                                     Match match, boolean openGui) {
        List<PlayerStatus> statuses = playerStatuses(match);

        List<ShopCard> shop = new ArrayList<>();
        for (int unit : state.shop()) {
            List<Integer> traits = unit < 0
                    ? List.of()
                    : ArenaTraitCatalog.traitsFor(unit).stream()
                    .map(Enum::ordinal).toList();
            shop.add(new ShopCard(unit, traits));
        }

        List<Integer> equipmentInventory = new ArrayList<>();
        List<Integer> equipmentCounts = new ArrayList<>();
        List<ArenaEquipment> inventory = state.equipmentInventory();
        for (int i = 0; i < inventory.size(); i++) {
            ArenaEquipment item = inventory.get(i);
            equipmentInventory.add(item == null ? -1 : item.ordinal());
            equipmentCounts.add(state.equipmentStackCount(i));
        }

        List<PieceView> pieces = new ArrayList<>();
        for (ArenaPiece piece : state.pieces()) {
            AbstractArmySoldier liveEntity =
                    livePieceEntity(match, state, piece.id());
            pieces.add(new PieceView(
                    piece.id(), piece.unitIndex(), piece.star(),
                    piece.boardSlot(), piece.benchSlot(),
                    piece.equipment().stream()
                            .map(ArenaEquipment::ordinal).toList(),
                    liveEntity == null
                            ? null : liveStats(liveEntity)));
        }
        boolean victoryIncome =
                state.lastBattleOutcome()
                        == ArenaPlayerState.BattleOutcome.WIN;
        int baseIncome = match.fixedIncomeForRound(
                match.round + 1) + (victoryIncome ? 1 : 0);
        int interestIncome = Math.min(5, state.gold() / 10);
        int streakIncome = state.streakIncome();

        PacketDistributor.sendToPlayer(player, new ArenaPlayerSnapshotPayload(
                true,
                openGui,
                state.alive(),
                false,
                state.id(),
                viewBoardIndex(match, state.id()),
                match.roomId,
                match.displayRound(),
                match.phase.ordinal(),
                match.phaseTicks,
                match.phaseTicksTotal,
                state.health(),
                state.gold(),
                state.caravanStacks(),
                state.winStreak(),
                state.lossStreak(),
                baseIncome,
                interestIncome,
                streakIncome,
                state.lastBattleOutcome().ordinal(),
                state.level(),
                state.xp(),
                state.xpToNext(),
                state.boardCount(),
                state.opponentName(),
                state.opponentId() == null
                        ? new UUID(0L, 0L) : state.opponentId(),
                state.lastDamage(),
                match.championName,
                shop,
                equipmentInventory,
                equipmentCounts,
                pieces,
                match.roundViews(state.id()),
                statuses));
    }

    private static void sendSpectatorSnapshot(
            ServerPlayer player, ArenaPlayerState state,
            Match match, boolean openGui) {
        List<SpectatorPiece> boardPieces = new ArrayList<>();
        for (ArenaPiece piece : state.pieces()) {
            if (piece.boardSlot() < 0) continue;
            boardPieces.add(new SpectatorPiece(
                    piece.id(),
                    piece.unitIndex(),
                    piece.star(),
                    piece.boardSlot(),
                    piece.equipment().stream()
                            .map(ArenaEquipment::ordinal)
                            .toList()));
        }

        PacketDistributor.sendToPlayer(
                player, new ArenaSpectatorSnapshotPayload(
                        true,
                        openGui,
                        match.roomId,
                        match.displayRound(),
                        match.phase.ordinal(),
                        match.phaseTicks,
                        match.phaseTicksTotal,
                        state.id(),
                        state.name(),
                        state.health(),
                        state.level(),
                        state.alive(),
                        viewBoardIndex(match, state.id()),
                        state.opponentName(),
                        state.opponentId() == null
                                ? new UUID(0L, 0L) : state.opponentId(),
                        state.lastDamage(),
                        match.championName,
                        boardPieces,
                        spectatorPlayerStatuses(match)));
    }

    private static List<SpectatorPlayerStatus> spectatorPlayerStatuses(
            Match match) {
        return playerStatuses(match).stream()
                .map(status -> new SpectatorPlayerStatus(
                        status.id(),
                        status.name(),
                        status.boardIndex(),
                        status.health(),
                        status.level(),
                        status.alive(),
                        status.ai(),
                        status.battleResult()))
                .toList();
    }

    private static List<PlayerStatus> playerStatuses(Match match) {
        List<PlayerStatus> statuses = new ArrayList<>();
        for (ArenaPlayerState other : match.players.values()) {
            BoardView home = match.homeBoardViews.get(other.id());
            statuses.add(new PlayerStatus(
                    other.id(), other.name(),
                    home == null
                            ? Integer.MAX_VALUE : home.boardIndex(),
                    other.health(),
                    other.level(), other.alive(), other.ai(),
                    match.battleResult(other.id())));
        }
        statuses.sort(Comparator.comparingInt(PlayerStatus::health)
                .reversed()
                .thenComparingInt(PlayerStatus::boardIndex)
                .thenComparing(PlayerStatus::name));
        return statuses;
    }

    @Nullable
    private static AbstractArmySoldier livePieceEntity(
            Match match, ArenaPlayerState state, UUID pieceId) {
        for (Duel duel : match.duels) {
            if (duel.left.actual == state) {
                AbstractArmySoldier soldier =
                        duel.left.pieceEntities.get(pieceId);
                if (soldier != null) return soldier;
            }
            if (duel.right.actual == state) {
                AbstractArmySoldier soldier =
                        duel.right.pieceEntities.get(pieceId);
                if (soldier != null) return soldier;
            }
        }
        return null;
    }

    private static LiveStats liveStats(
            AbstractArmySoldier soldier) {
        return new LiveStats(
                soldier.getArenaCurrentHealth(),
                soldier.getArenaDisplayMaxHealth(),
                soldier.getAttributeValue(Attributes.ATTACK_DAMAGE),
                soldier.getAttributeValue(Attributes.ARMOR),
                soldier.getArenaEffectiveMagicResistance(),
                soldier.getArenaEffectiveAttackSpeed(),
                soldier.getAttributeValue(Attributes.MOVEMENT_SPEED),
                soldier.getArenaMagicPower(),
                soldier.arenaEquipment().dodgeChance(),
                soldier.arenaEquipment().healingPowerBonus(),
                soldier.arenaEquipment().lifestealRatio(),
                soldier.arenaEquipment().skillChargeGainBonus(),
                soldier.arenaEquipment().criticalChance(),
                soldier.arenaEquipment().criticalDamageMultiplier()
                        - 1.0,
                soldier.getArenaShield(),
                soldier.getArenaSkillCharge(),
                soldier.getArenaSkillRequired());
    }

    private static void sendInactive(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ArenaPlayerSnapshotPayload(
                false, false, false, false,
                new UUID(0L, 0L), -1,
                0, 0, ArenaPhase.FINISHED.ordinal(),
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, "",
                new UUID(0L, 0L), 0,
                "",
                List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of()));
        PacketDistributor.sendToPlayer(player,
                new ArenaCombatStatsPayload(
                        false, 0, 0, List.of()));
    }

    private static int viewBoardIndex(Match match, UUID playerId) {
        Map<UUID, BoardView> views = match.phase == ArenaPhase.SELECTION
                ? match.homeBoardViews : match.lastBoardViews;
        BoardView view = views.get(playerId);
        return view == null ? -1 : view.boardIndex();
    }

    private static void notice(ServerPlayer player, String key) {
        PacketDistributor.sendToPlayer(player, new ArenaGameNoticePayload(key));
    }

    private static void sendSound(ServerPlayer player, int cue) {
        PacketDistributor.sendToPlayer(
                player, new ArenaSoundPayload(cue));
    }

    private static String phaseKey(ArenaPhase phase) {
        return switch (phase) {
            case SELECTION ->
                    "gui.monster_conquest.arena.game.phase.selection";
            case BATTLE ->
                    "gui.monster_conquest.arena.game.phase.battle";
            case OVERTIME ->
                    "gui.monster_conquest.arena.game.phase.overtime";
            case RESULT ->
                    "gui.monster_conquest.arena.game.phase.result";
            case FINISHED ->
                    "gui.monster_conquest.arena.game.phase.finished";
        };
    }

    private static String resultKey(ArenaPlayerState.BattleOutcome outcome) {
        return switch (outcome) {
            case WIN -> "gui.monster_conquest.arena.game.result.win";
            case LOSS -> "gui.monster_conquest.arena.game.result.loss";
            case DRAW -> "gui.monster_conquest.arena.game.result.draw";
            case NONE -> "gui.monster_conquest.arena.game.opponent_tbd";
        };
    }

    private static ChatFormatting phaseColor(ArenaPhase phase) {
        return switch (phase) {
            case SELECTION -> ChatFormatting.AQUA;
            case BATTLE -> ChatFormatting.RED;
            case OVERTIME -> ChatFormatting.GOLD;
            case RESULT -> ChatFormatting.YELLOW;
            case FINISHED -> ChatFormatting.GRAY;
        };
    }

    private static ChatFormatting resultColor(
            ArenaPlayerState.BattleOutcome outcome) {
        return switch (outcome) {
            case WIN -> ChatFormatting.GREEN;
            case LOSS -> ChatFormatting.RED;
            case DRAW -> ChatFormatting.YELLOW;
            case NONE -> ChatFormatting.GRAY;
        };
    }

    @Nullable
    private static ServerPlayer player(UUID id) {
        return server == null ? null : server.getPlayerList().getPlayer(id);
    }

    private static Objective healthObjective() {
        if (healthObjective != null) return healthObjective;
        var scoreboard = server.getScoreboard();
        healthObjective = scoreboard.getObjective(HP_OBJECTIVE);
        if (healthObjective == null) {
            healthObjective = scoreboard.addObjective(
                    HP_OBJECTIVE,
                    ObjectiveCriteria.DUMMY,
                    Component.translatable("gui.monster_conquest.arena.game.health_objective"),
                    ObjectiveCriteria.RenderType.INTEGER,
                    false,
                    null);
        }
        return healthObjective;
    }

    private static void updateHealthScore(ArenaPlayerState state) {
        if (server == null || state.ai()) return;
        ServerPlayer player = player(state.id());
        if (player == null) return;
        server.getScoreboard().getOrCreatePlayerScore(player, healthObjective())
                .set(state.health());
    }

    private record ReturnPoint(
            ResourceKey<Level> dimension,
            Vec3 position,
            float yaw,
            float pitch,
            GameType gameType,
            @Nullable UUID vehicleId) {
    }

    private record Pairing(ArenaPlayerState first, ArenaPlayerState second) {
    }

    private record PairingPlan(List<Pairing> pairings, long cost) {
    }

    private record ShadowPlan(
            @Nullable ArenaPlayerState shadowHost,
            @Nullable ArenaPlayerState shadowSource,
            List<Pairing> pairings,
            long cost) {
    }

    private record CaravanPresentation(
            UUID ownerId, int gold, List<ArenaEquipment> equipment) {
    }

    private record PendingCaravanSettlement(
            UUID ownerId,
            ArenaTraitEconomy.CaravanSettlement settlement) {
    }

    private record BoardView(int boardIndex, int side) {
    }

    private record SpectatorView(Match match, UUID targetId) {
    }

    private record RoundRecord(
            UUID opponentId,
            String opponentName,
            boolean wild,
            int result) {
    }

    private static final class Duel {
        private final CombatSide left;
        private final CombatSide right;
        private final int boardIndex;
        private final UUID homeOwnerId;
        private final boolean wildEncounter;
        private final boolean shadowEncounter;
        private boolean resolved;

        private Duel(CombatSide left, CombatSide right,
                     int boardIndex, UUID homeOwnerId,
                     boolean wildEncounter) {
            this(left, right, boardIndex, homeOwnerId,
                    wildEncounter, false);
        }

        private Duel(CombatSide left, CombatSide right,
                     int boardIndex, UUID homeOwnerId,
                     boolean wildEncounter,
                     boolean shadowEncounter) {
            this.left = left;
            this.right = right;
            this.boardIndex = boardIndex;
            this.homeOwnerId = homeOwnerId;
            this.wildEncounter = wildEncounter;
            this.shadowEncounter = shadowEncounter;
        }
    }

    private static final class SnifferDominance {
        private long nextGoldTick;

        private SnifferDominance(long nextGoldTick) {
            this.nextGoldTick = nextGoldTick;
        }
    }

    private static final class CombatSide {
        @Nullable private final ArenaPlayerState actual;
        private final UUID ownerId;
        private final int side;
        private final int level;
        private final String displayName;
        private final List<ArenaPiece> pieces;
        @Nullable private final ArenaWildRules.StatScaling wildScaling;
        private final List<AbstractArmySoldier> entities = new ArrayList<>();
        private final Map<UUID, AbstractArmySoldier> pieceEntities =
                new HashMap<>();
        private final List<AbstractArmySoldier> benchDisplayEntities =
                new ArrayList<>();
        private final Map<UUID, SnifferDominance> snifferDominance =
                new HashMap<>();

        private CombatSide(@Nullable ArenaPlayerState actual,
                           UUID ownerId,
                           int side,
                           int level,
                           String displayName,
                           List<ArenaPiece> pieces,
                           @Nullable ArenaWildRules.StatScaling wildScaling) {
            this.actual = actual;
            this.ownerId = ownerId;
            this.side = side;
            this.level = level;
            this.displayName = displayName;
            this.pieces = List.copyOf(pieces);
            this.wildScaling = wildScaling;
        }

        private static CombatSide actual(ArenaPlayerState state, int side) {
            return new CombatSide(
                    state,
                    state.id(),
                    side,
                    state.level(),
                    state.name(),
                    state.deployedPieces(),
                    null);
        }

        private static CombatSide shadow(ArenaPlayerState source) {
            return new CombatSide(
                    null,
                    source.id(),
                    1,
                    source.level(),
                    Component.translatable(
                            "gui.monster_conquest.arena.game.shadow_opponent",
                            source.name()).getString(),
                    source.cloneForShadow(),
                    null);
        }

        private static CombatSide wild(
                ArenaPlayerState source, int stage,
                UUID wildOwnerId, RandomSource random) {
            int count = ArenaWildRules.wildCount(stage);
            List<ArenaPiece> pieces = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                int unitIndex = WILD_ONE_COST_UNITS.get(
                        random.nextInt(WILD_ONE_COST_UNITS.size()));
                ArenaPiece piece = new ArenaPiece(unitIndex, 1);
                piece.setBoardSlot(2 + index);
                pieces.add(piece);
            }
            return new CombatSide(
                    null,
                    wildOwnerId,
                    1,
                    stage,
                    Component.translatable(
                            "gui.monster_conquest.arena.game.wild_opponent",
                            stage).getString(),
                    pieces,
                    ArenaWildRules.scalingForStage(stage));
        }

        private int livingCount() {
            int count = 0;
            for (AbstractArmySoldier entity : entities) {
                if (entity.isAlive() && !entity.isRemoved()) count++;
            }
            return count;
        }

        private boolean activateSnifferDominance(
                UUID sourceId, long now) {
            if (snifferDominance.containsKey(sourceId)) {
                return false;
            }
            snifferDominance.put(sourceId, new SnifferDominance(
                    now + ArenaSkillBalance
                            .SNIFFER_DOMINANCE_GOLD_INTERVAL_TICKS));
            return true;
        }

        private void tickSnifferDominance(ServerLevel level) {
            if (snifferDominance.isEmpty()) return;
            long now = level.getGameTime();
            for (Map.Entry<UUID, SnifferDominance> entry
                    : snifferDominance.entrySet()) {
                UUID sourceId = entry.getKey();
                ResourceLocation auraId =
                        ResourceLocation.fromNamespaceAndPath(
                                Monster_conquest.MODID,
                                "arena_sniffer_dominance/"
                                        + sourceId);
                for (AbstractArmySoldier ally : entities) {
                    if (!ally.isAlive() || ally.isRemoved()
                            || !ally.isArenaUnit()
                            || ally.getArenaSide() != side) {
                        continue;
                    }
                    ally.arenaSkills()
                            .applySnifferDominance(auraId);
                }
                SnifferDominance aura = entry.getValue();
                if (now < aura.nextGoldTick) continue;
                if (actual != null && actual.alive()) {
                    actual.addGold(ArenaSkillBalance
                            .SNIFFER_DOMINANCE_GOLD_PER_SECOND);
                }
                aura.nextGoldTick = now + ArenaSkillBalance
                        .SNIFFER_DOMINANCE_GOLD_INTERVAL_TICKS;
            }
        }

        private void discard() {
            if (actual != null) {
                actual.clearTemporaryEquipment();
            }
            for (AbstractArmySoldier entity : entities) {
                if (!entity.isRemoved()) entity.discard();
            }
            entities.clear();
            pieceEntities.clear();
            snifferDominance.clear();
            discardBenchDisplays();
            if (actual != null) actual.clearCombatEntities();
        }

        private void discardBenchDisplays() {
            for (AbstractArmySoldier entity : benchDisplayEntities) {
                if (!entity.isRemoved()) entity.discard();
            }
            benchDisplayEntities.clear();
        }

    }

    private static final class Match {
        private final ServerLevel level;
        private final int roomId;
        private final UUID id = UUID.randomUUID();
        private final LinkedHashMap<UUID, ArenaPlayerState> players;
        private final RandomSource random;
        private final SecureRandom shopRandom;
        private final Map<UUID, Map<UUID, Integer>> encounters = new HashMap<>();
        private final Map<UUID, UUID> lastOpponents = new HashMap<>();
        private final Map<UUID, Set<UUID>> stageOpponents = new HashMap<>();
        private final List<Duel> duels = new ArrayList<>();
        private final List<UUID> eliminationOrder = new ArrayList<>();
        private final Map<UUID, Integer> eliminationRounds =
                new HashMap<>();
        private final Map<UUID, BoardView> homeBoardViews = new HashMap<>();
        private final Map<UUID, BoardView> lastBoardViews = new HashMap<>();
        private final Map<UUID, List<AbstractArmySoldier>> previewEntities =
                new HashMap<>();
        private final Map<UUID, Integer> battleResults = new HashMap<>();
        private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
        private final List<ArenaLootDrop> lootDrops = new ArrayList<>();
        private final List<PendingCaravanSettlement>
                pendingCaravanSettlements = new ArrayList<>();
        private final List<CaravanPresentation>
                pendingCaravanPresentations = new ArrayList<>();
        private final Map<UUID, List<ArenaEquipment>>
                pendingLootEquipment = new HashMap<>();
        private final Map<UUID, Map<Integer, RoundRecord>>
                roundHistory = new HashMap<>();
        private final Map<UUID, ArenaUnitPerformance>
                unitPerformance = new HashMap<>();

        private ArenaPhase phase = ArenaPhase.SELECTION;
        private int round;
        private long preparationSerial;
        private int phaseTicks;
        private int phaseTicksTotal;
        private int snapshotCooldown;
        private int combatStatsCooldown;
        private int stageOpponentStage = -1;
        private boolean finished;
        private boolean finishPending;
        private int finishDelayTicks;
        private String championName = "";
        private List<ArenaBridgeResult.PlayerPlacement> finalPlacements =
                List.of();
        private boolean awaitingArrival;
        private int arrivalTimeout;
        private int lastPreparationBellSecond = -1;
        private boolean snapshotDirty;
        private boolean snapshotDirtyOpenGui;

        private Match(ServerLevel level, int roomId,
                      LinkedHashMap<UUID, ArenaPlayerState> players,
                      Map<UUID, Integer> seatIndices,
                      SecureRandom shopRandom) {
            this.level = level;
            this.roomId = roomId;
            this.players = players;
            this.random = secureRandom();
            this.shopRandom = shopRandom;
            boolean[] occupiedBoards = new boolean[HOME_BOARD_COUNT];
            for (UUID playerId : players.keySet()) {
                int boardIndex = seatIndices.getOrDefault(playerId, -1);
                if (boardIndex < 0
                        || boardIndex >= HOME_BOARD_COUNT
                        || occupiedBoards[boardIndex]) {
                    boardIndex = firstFreeSeat(occupiedBoards);
                }
                if (boardIndex < 0) boardIndex = 0;
                occupiedBoards[boardIndex] = true;
                BoardView home = new BoardView(boardIndex, 0);
                homeBoardViews.put(playerId, home);
                lastBoardViews.put(playerId, home);
            }
        }

        private void tick() {
            if (finished) return;
            if (finishPending) {
                finishDelayTicks--;
                phaseTicks = Math.max(0, finishDelayTicks);
                if (finishDelayTicks <= 0) {
                    completeFinish();
                } else if (finishDelayTicks % 20 == 0) {
                    sendSnapshots(false);
                }
                return;
            }
            if (snapshotDirty) {
                boolean openGui = snapshotDirtyOpenGui;
                snapshotDirty = false;
                snapshotDirtyOpenGui = false;
                sendSnapshots(openGui);
            }
            lootDrops.removeIf(ArenaLootDrop::isRemoved);
            deliverPendingEquipment();
            for (ArenaPlayerState state : players.values()) {
                state.deliverPendingEquipmentRewards();
            }
            if (phase == ArenaPhase.SELECTION && awaitingArrival) {
                if (allHumansArrived() || --arrivalTimeout <= 0) {
                    awaitingArrival = false;
                } else {
                    if (--snapshotCooldown <= 0) {
                        sendSnapshots(false);
                        snapshotCooldown = 10;
                    }
                    updateBossBars();
                    return;
                }
            }
            if (phaseTicks > 0) phaseTicks--;
            tickPreparationBell();
            snapshotCooldown--;
            if (snapshotCooldown <= 0) {
                sendSnapshots(false);
                snapshotCooldown = 10;
            }
            combatStatsCooldown--;
            if (combatStatsCooldown <= 0) {
                sendCombatStatsSnapshots();
                combatStatsCooldown = 5;
            }
            if (phase == ArenaPhase.BATTLE
                    || phase == ArenaPhase.OVERTIME
                    || phase == ArenaPhase.RESULT) {
                confineCombatantsToZone();
            }

            switch (phase) {
                case SELECTION -> {
                    if (phaseTicks <= 0) beginBattle();
                }
                case BATTLE -> {
                    checkDuels();
                    if (phase == ArenaPhase.BATTLE
                            && phaseTicks <= 0) {
                        beginOvertime();
                    }
                    if (phase == ArenaPhase.BATTLE
                            && !duels.isEmpty()
                            && duels.stream().allMatch(duel -> duel.resolved)) {
                        enterResult();
                    }
                }
                case OVERTIME -> {
                    applyOvertimeState();
                    checkDuels();
                    if (phase == ArenaPhase.OVERTIME
                            && !duels.isEmpty()
                            && duels.stream().allMatch(
                            duel -> duel.resolved)) {
                        enterResult();
                    } else if (phase == ArenaPhase.OVERTIME
                            && phaseTicks <= 0) {
                        resolveTimeout();
                    }
                }
                case RESULT -> {
                    if (phaseTicks <= 0) {
                        cleanupCombat();
                        if (alivePlayers().size() <= 1) {
                            finish();
                        } else {
                            enterSelection(false);
                        }
                    }
                }
                case FINISHED -> {
                }
            }
            updateBossBars();
        }

        private void confineCombatantsToZone() {
            AABB search = ArenaPlotLayout.combatSearchBounds(roomId);
            for (AbstractArmySoldier soldier
                    : level.getEntitiesOfClass(
                    AbstractArmySoldier.class, search)) {
                int boardIndex = soldier.getArenaBoardIndex();
                if (!soldier.isArenaUnit()
                        || soldier.isRemoved()
                        || !id.equals(soldier.getArenaMatchId())
                        || boardIndex < 0
                        || boardIndex >= HOME_BOARD_COUNT) {
                    continue;
                }
                ArenaPlotLayout.confineToCombatZone(
                        soldier, roomId, boardIndex);
            }
        }

        private void enterSelection(boolean first) {
            phase = ArenaPhase.SELECTION;
            // 准备阶段继续展示刚结束战斗的统计，下一场战斗开始时再清零。
            combatStatsCooldown = 5;
            phaseTicks = ArenaConfig.prepareTicks;
            phaseTicksTotal = phaseTicks;
            preparationSerial++;
            lastPreparationBellSecond = -1;
            snapshotCooldown = 0;
            awaitingArrival = first;
            arrivalTimeout = 30 * 20;
            applyPendingCaravanSettlements();
            for (ArenaPlayerState state : players.values()) {
                state.resolveDeferredRosterUpgrades();
                if (state.alive()) {
                    state.beginPreparation(preparationSerial);
                }
                if (!first && state.alive()) {
                    awardRoundIncome(state);
                    state.grantXp(2);
                    state.rollShop(shopRandom);
                    state.clearOpponent();
                }
                state.clearCombatEntities();
                updateHealthScore(state);
            }
            for (ArenaPlayerState state : players.values()) {
                if (state.ai() && state.alive()) {
                    int preparationStage =
                            (Math.max(1, displayRound()) - 1) / 7 + 1;
                    state.runAiPreparation(
                            shopRandom, random, preparationStage);
                    Monster_conquest.LOGGER.info(
                            "[ARENA-MATCH] ai-prep room={} round={} player={} "
                                    + "level={} board={} gold={} highestStar={}",
                            roomId, roundLabel(displayRound()), state.name(),
                            state.level(), state.boardCount(), state.gold(),
                            state.highestStar());
                }
            }
            for (ArenaPlayerState state : players.values()) {
                if (state.alive()) {
                    state.grantEnderDragonReward();
                }
            }
            teleportAlivePlayersHome();
            spawnPendingCaravanPresentations(false);
            spawnAllPreviews();
            sendSnapshots(first);
            sendCombatStatsSnapshots();
            updateBossBars();
        }

        private void teleportAlivePlayersHome() {
            for (ArenaPlayerState state : players.values()) {
                if (state.ai() || !state.alive()) continue;
                ServerPlayer player = player(state.id());
                BoardView home = homeBoardViews.get(state.id());
                if (player == null || home == null) continue;
                Vec3 point = ArenaPlotLayout.playerViewPoint(
                        roomId, home.boardIndex(), home.side());
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level,
                        point.x, point.y, point.z,
                        ArenaPlotLayout.viewYaw(home.side()),
                        ArenaPlotLayout.viewPitch());
            }
        }

        private boolean allHumansArrived() {
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player == null || !ArenaDimensions.isArena(player)) {
                    return false;
                }
            }
            return true;
        }

        private void beginBattle() {
            cleanupCombat();
            ArenaPlotLayout.clearRoomEntities(level, roomId);
            battleResults.clear();
            // 常规战斗和加时共用同一轮统计，只在下一场战斗开始时重置。
            unitPerformance.clear();
            combatStatsCooldown = 5;
            round++;
            for (ArenaPlayerState state : players.values()) {
                if (state.alive()) {
                    state.lockRosterUpgrades();
                    state.setLastDamage(0);
                    state.grantEnderDragonReward();
                    for (ArenaPiece piece : state.deployedPieces()) {
                        performance(piece.id());
                    }
                }
            }
            List<ArenaPlayerState> alive = alivePlayers();
            if (alive.size() <= 1) {
                finish();
                return;
            }

            if (ArenaWildRules.isWildRound(round)) {
                scheduleWildRound(alive);
            } else {
                schedulePvpRound(alive);
            }

            for (Duel duel : duels) spawnDuel(duel);
            for (Duel duel : duels) {
                markFighting(duel.left);
                markFighting(duel.right);
            }
            phase = ArenaPhase.BATTLE;
            phaseTicks = ArenaConfig.battleTicks;
            phaseTicksTotal = phaseTicks;
            teleportToDuels();
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] battle room={} round={} duels={}",
                    roomId, round, duels.size());
            sendSnapshots(false);
            sendCombatStatsSnapshots();
        }

        private ArenaUnitPerformance performance(UUID pieceId) {
            return unitPerformance.computeIfAbsent(
                    pieceId, ignored -> new ArenaUnitPerformance());
        }

        private void beginOvertime() {
            if (duels.isEmpty()
                    || duels.stream().allMatch(duel -> duel.resolved)) {
                return;
            }
            // 加时仍属于当前战斗回合，不能清理实体、投射物或地面效果。
            phase = ArenaPhase.OVERTIME;
            phaseTicks = Math.max(1, ArenaConfig.overtimeTicks);
            phaseTicksTotal = phaseTicks;
            applyOvertimeState();
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] overtime room={} round={} ticks={}",
                    roomId, round, phaseTicksTotal);
        }

        private void applyOvertimeState() {
            int elapsedSeconds = Math.min(
                    phaseTicksTotal / 20,
                    Math.max(0, phaseTicksTotal - phaseTicks) / 20);
            for (Duel duel : duels) {
                if (duel.resolved) continue;
                applyOvertimeState(
                        duel.left, elapsedSeconds);
                applyOvertimeState(
                        duel.right, elapsedSeconds);
            }
        }

        private void applyOvertimeState(
                CombatSide side, int seconds) {
            for (AbstractArmySoldier soldier : side.entities) {
                if (!soldier.isAlive() || soldier.isRemoved()) continue;
                soldier.arenaSkills().applyArenaOvertime(seconds);
                soldier.arenaSkills()
                        .applyArenaOvertimeToSummons(seconds);
            }
        }

        private void clearOvertimeState() {
            for (Duel duel : duels) {
                clearOvertimeState(duel.left);
                clearOvertimeState(duel.right);
            }
        }

        private void clearOvertimeState(CombatSide side) {
            for (AbstractArmySoldier soldier : side.entities) {
                soldier.arenaSkills().clearArenaOvertime();
                soldier.arenaSkills()
                        .clearArenaOvertimeFromSummons();
            }
        }

        private void scheduleWildRound(
                List<ArenaPlayerState> alive) {
            int stage = ArenaWildRules.stageForRound(round);
            for (ArenaPlayerState state : alive) {
                BoardView home = homeBoardViews.get(state.id());
                if (home == null) continue;
                UUID wildOwnerId = UUID.nameUUIDFromBytes(
                        (id + ":wild:" + round + ":" + state.id())
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                CombatSide wild = CombatSide.wild(
                        state, stage, wildOwnerId, random);
                duels.add(new Duel(
                        CombatSide.actual(state, 0), wild,
                        home.boardIndex(), state.id(), true));
                state.setOpponent(null, wild.displayName);
                recordRoundStarted(state.id(), round,
                        new UUID(0L, 0L), wild.displayName, true);
                Monster_conquest.LOGGER.info(
                        "[ARENA-MATCH] wild room={} round={} player={} "
                                + "stage={} count={} board={}",
                        roomId, round, state.name(), stage,
                        wild.pieces.size(), home.boardIndex());
            }
        }

        private void schedulePvpRound(
                List<ArenaPlayerState> alive) {
            resetStageOpponentsIfNeeded();
            List<ArenaPlayerState> candidates =
                    new ArrayList<>(alive);
            Collections.shuffle(
                    candidates, new java.util.Random(random.nextLong()));
            ShadowPlan plan;
            if ((candidates.size() & 1) == 1) {
                plan = bestShadowPlan(candidates);
            } else {
                PairingPlan pairingPlan = bestPairingPlan(candidates);
                plan = pairingPlan == null ? null : new ShadowPlan(
                        null, null, pairingPlan.pairings(),
                        pairingPlan.cost());
            }
            if (plan == null) return;

            for (Pairing pairing : plan.pairings()) {
                ArenaPlayerState venueOwner = random.nextBoolean()
                        ? pairing.first : pairing.second;
                int venueBoard = homeBoardViews.get(
                        venueOwner.id()).boardIndex();
                boolean firstAtHome =
                        venueOwner == pairing.first;
                Duel duel = new Duel(
                        CombatSide.actual(pairing.first,
                                firstAtHome ? 0 : 1),
                        CombatSide.actual(pairing.second,
                                firstAtHome ? 1 : 0),
                        venueBoard, venueOwner.id(), false);
                duels.add(duel);
                Monster_conquest.LOGGER.info(
                        "[ARENA-MATCH] venue room={} round={} board={} "
                                + "home={} homeSide=0 away={} awaySide=1",
                        roomId, round, venueBoard,
                        venueOwner.name(),
                        firstAtHome ? pairing.second.name()
                                : pairing.first.name());
                recordEncounter(
                        pairing.first.id(), pairing.second.id());
                recordStageOpponent(
                        pairing.first.id(), pairing.second.id());
                pairing.first.setOpponent(
                        pairing.second.id(), pairing.second.name());
                pairing.second.setOpponent(
                        pairing.first.id(), pairing.first.name());
                recordRoundStarted(pairing.first.id(), round,
                        pairing.second.id(), pairing.second.name(), false);
                recordRoundStarted(pairing.second.id(), round,
                        pairing.first.id(), pairing.first.name(), false);
            }

            ArenaPlayerState shadowHost = plan.shadowHost();
            ArenaPlayerState source = plan.shadowSource();
            if (shadowHost == null || source == null) return;
            duels.add(new Duel(
                    CombatSide.actual(shadowHost, 0),
                    CombatSide.shadow(source),
                    homeBoardViews.get(shadowHost.id()).boardIndex(),
                    shadowHost.id(), false, true));
            shadowHost.setOpponent(source.id(), Component.translatable(
                    "gui.monster_conquest.arena.game.shadow_opponent",
                    source.name()).getString());
            recordRoundStarted(shadowHost.id(), round, source.id(),
                    shadowHost.opponentName(), false);
            recordStageOpponent(shadowHost.id(), source.id());
        }

        private void recordRoundStarted(
                UUID playerId, int absoluteRound,
                UUID opponentId, String opponentName,
                boolean wild) {
            roundHistory.computeIfAbsent(
                            playerId, ignored -> new HashMap<>())
                    .put(absoluteRound, new RoundRecord(
                            opponentId,
                            opponentName == null ? "" : opponentName,
                            wild,
                            ArenaPlayerSnapshotPayload
                                    .BATTLE_FIGHTING));
        }

        private void completeRound(
                UUID playerId, int result) {
            Map<Integer, RoundRecord> history =
                    roundHistory.get(playerId);
            if (history == null) return;
            RoundRecord record = history.get(round);
            if (record == null) return;
            history.put(round, new RoundRecord(
                    record.opponentId(),
                    record.opponentName(),
                    record.wild(),
                    result));
        }

        @Nullable
        private PairingPlan bestPairingPlan(
                List<ArenaPlayerState> list) {
            Search search = new Search();
            search.visit(list, new ArrayList<>(), 0L);
            return search.bestPairs == null ? null
                    : new PairingPlan(
                    search.bestPairs, search.bestCost);
        }

        @Nullable
        private ShadowPlan bestShadowPlan(
                List<ArenaPlayerState> alive) {
            List<ShadowPlan> bestPlans = new ArrayList<>();
            long bestCost = Long.MAX_VALUE;
            for (ArenaPlayerState host : alive) {
                List<ArenaPlayerState> remaining =
                        new ArrayList<>(alive);
                remaining.remove(host);
                PairingPlan pairingPlan =
                        bestPairingPlan(remaining);
                if (pairingPlan == null) continue;
                for (ArenaPlayerState source : remaining) {
                    long cost = pairingPlan.cost()
                            + shadowRelationCost(host, source);
                    if (cost < bestCost) {
                        bestCost = cost;
                        bestPlans.clear();
                    }
                    if (cost == bestCost) {
                        bestPlans.add(new ShadowPlan(
                                host, source,
                                pairingPlan.pairings(), cost));
                    }
                }
            }
            if (bestPlans.isEmpty()) return null;
            return bestPlans.get(
                    random.nextInt(bestPlans.size()));
        }

        private long pairingCost(
                ArenaPlayerState first,
                ArenaPlayerState second) {
            // 真实配对重复的代价远高于影子来源重复，避免为了优化影子而牺牲
            // 本可避免的真实重复对局。
            return relationCost(first, second, 1_000_000_000L);
        }

        private long shadowRelationCost(
                ArenaPlayerState first,
                ArenaPlayerState second) {
            return relationCost(first, second, 10_000_000L);
        }

        private long relationCost(
                ArenaPlayerState first,
                ArenaPlayerState second,
                long stageRepeatPenalty) {
            int met = encounters.getOrDefault(first.id(), Map.of())
                    .getOrDefault(second.id(), 0);
            long cost = met * 100L;
            if (stageOpponents.getOrDefault(
                    first.id(), Set.of()).contains(second.id())) {
                cost += stageRepeatPenalty;
            }
            UUID last = lastOpponents.get(first.id());
            if (second.id().equals(last)
                    || first.id().equals(
                    lastOpponents.get(second.id()))) {
                cost += 10_000L;
            }
            return cost;
        }

        private final class Search {
            private long bestCost = Long.MAX_VALUE;
            private List<Pairing> bestPairs;

            private void visit(List<ArenaPlayerState> remaining,
                               List<Pairing> current, long cost) {
                if (cost >= bestCost) return;
                if (remaining.isEmpty()) {
                    bestCost = cost;
                    bestPairs = List.copyOf(current);
                    return;
                }
                ArenaPlayerState first = remaining.get(0);
                List<ArenaPlayerState> rest = new ArrayList<>(remaining.subList(1, remaining.size()));
                Collections.shuffle(rest, new java.util.Random(random.nextLong()));
                for (ArenaPlayerState second : rest) {
                    long pairCost = pairingCost(first, second);
                    List<ArenaPlayerState> next = new ArrayList<>(rest);
                    next.remove(second);
                    current.add(new Pairing(first, second));
                    visit(next, current, cost + pairCost);
                    current.remove(current.size() - 1);
                }
            }
        }

        private void recordEncounter(UUID first, UUID second) {
            encounters.computeIfAbsent(first, ignored -> new HashMap<>())
                    .merge(second, 1, Integer::sum);
            encounters.computeIfAbsent(second, ignored -> new HashMap<>())
                    .merge(first, 1, Integer::sum);
            lastOpponents.put(first, second);
            lastOpponents.put(second, first);
        }

        private void resetStageOpponentsIfNeeded() {
            int stage = ArenaWildRules.stageForRound(round);
            if (stageOpponentStage == stage) return;
            stageOpponentStage = stage;
            stageOpponents.clear();
        }

        private void recordStageOpponent(UUID first, UUID second) {
            stageOpponents.computeIfAbsent(
                    first, ignored -> new HashSet<>()).add(second);
            stageOpponents.computeIfAbsent(
                    second, ignored -> new HashSet<>()).add(first);
        }

        private void spawnDuel(Duel duel) {
            spawnSide(duel.left, duel.boardIndex);
            spawnSide(duel.right, duel.boardIndex);
        }

        private void spawnSide(CombatSide side, int boardIndex) {
            List<ArenaPiece> pieces = side.pieces;
            for (int index = 0; index < pieces.size(); index++) {
                ArenaPiece piece = pieces.get(index);
                int boardSlot = piece.boardSlot();
                int column;
                int row;
                if (boardSlot >= 0) {
                    column = boardSlot % ArenaPlayerState.BOARD_COLUMNS;
                    row = boardSlot / ArenaPlayerState.BOARD_COLUMNS;
                } else {
                    column = index % ArenaPlayerState.BOARD_COLUMNS;
                    row = Math.min(ArenaPlayerState.BOARD_ROWS - 1,
                            index / ArenaPlayerState.BOARD_COLUMNS);
                }
                Vec3 point = ArenaPlotLayout.unitPoint(
                        roomId, boardIndex, side.side, column, row);
                AbstractArmySoldier soldier = spawnSoldier(
                        side, piece, point, boardIndex);
                if (soldier != null) side.entities.add(soldier);
            }
            spawnBenchDisplays(side, boardIndex);
        }

        private void spawnBenchDisplays(CombatSide side, int boardIndex) {
            side.discardBenchDisplays();
            if (side.actual == null) return;
            for (ArenaPiece piece : side.actual.benchPieces()) {
                int benchSlot = piece.benchSlot();
                if (benchSlot < 0) continue;
                Vec3 point = ArenaPlotLayout.benchPoint(
                        roomId, boardIndex, side.side, benchSlot);
                AbstractArmySoldier soldier = spawnBenchSoldier(
                        side, piece, point, boardIndex);
                if (soldier != null) side.benchDisplayEntities.add(soldier);
            }
        }

        private void spawnAllPreviews() {
            clearAllPreviews();
            for (ArenaPlayerState state : players.values()) {
                if (state.alive()) spawnPreview(state);
            }
            int units = previewEntities.values().stream()
                    .mapToInt(List::size).sum();
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] preview room={} round={} players={} units={}",
                    roomId, round, previewEntities.size(), units);
        }

        private void spawnPreview(ArenaPlayerState state) {
            BoardView view = homeBoardViews.get(state.id());
            if (view == null) return;
            List<AbstractArmySoldier> entities = new ArrayList<>();
            List<ArenaPiece> roster = state.deployedPieces();
            for (ArenaPiece piece : state.deployedPieces()) {
                int slot = piece.boardSlot();
                int column = slot % ArenaPlayerState.BOARD_COLUMNS;
                int row = slot / ArenaPlayerState.BOARD_COLUMNS;
                Vec3 point = ArenaPlotLayout.unitPoint(
                        roomId, view.boardIndex(), view.side(), column, row);
                AbstractArmySoldier soldier = spawnPreviewSoldier(
                        state.id(), piece, point, view.side(),
                        view.boardIndex(), roster);
                if (soldier != null) entities.add(soldier);
            }
            for (ArenaPiece piece : state.benchPieces()) {
                Vec3 point = ArenaPlotLayout.benchPoint(
                        roomId, view.boardIndex(), view.side(), piece.benchSlot());
                AbstractArmySoldier soldier = spawnPreviewSoldier(
                        state.id(), piece, point, view.side(),
                        view.boardIndex(), roster);
                if (soldier != null) entities.add(soldier);
            }
            if (!entities.isEmpty()) {
                previewEntities.put(state.id(), entities);
            }
        }

        @Nullable
        private AbstractArmySoldier spawnPreviewSoldier(
                UUID ownerId, ArenaPiece piece, Vec3 point, int side,
                int boardIndex, List<ArenaPiece> roster) {
            EntityType<? extends Mob> type = piece.unit().entityType().get();
            if (!(type.create(level) instanceof AbstractArmySoldier soldier)) {
                return null;
            }
            soldier.moveTo(point.x, point.y, point.z,
                    ArenaPlotLayout.viewYaw(side), 0.0f);
            soldier.setSoldierType(piece.unit().form());
            soldier.setCommanderUuid(ownerId);
            soldier.enterArenaPresentationMode(side, boardIndex);
            applyArenaAppearance(piece, soldier);
            applyArenaStats(roster, piece, soldier, true);
            faceArenaForward(soldier, side);
            soldier.setInvulnerable(true);
            soldier.setNoGravity(true);
            soldier.setSilent(true);
            soldier.setPersistenceRequired();
            soldier.addTag("monster_conquest_arena_preview");
            level.addFreshEntity(soldier);
            return soldier;
        }

        private void refreshPreview(ArenaPlayerState state) {
            removePreview(state.id());
            if (phase == ArenaPhase.SELECTION && state.alive()) {
                spawnPreview(state);
            }
        }

        private void removePreview(UUID playerId) {
            List<AbstractArmySoldier> entities = previewEntities.remove(playerId);
            if (entities == null) return;
            for (AbstractArmySoldier entity : entities) {
                if (!entity.isRemoved()) entity.discard();
            }
        }

        private void clearAllPreviews() {
            for (UUID playerId : new ArrayList<>(previewEntities.keySet())) {
                removePreview(playerId);
            }
        }

        private void updateBossBars() {
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player == null) continue;

                ServerBossEvent bar = bossBars.computeIfAbsent(
                        state.id(), ignored -> new ServerBossEvent(
                                Component.empty(),
                                BossBarColor.BLUE,
                                BossBarOverlay.PROGRESS));
                if (!bar.getPlayers().contains(player)) {
                    bar.addPlayer(player);
                }
                String opponent = state.opponentName().isBlank()
                        ? Component.translatable(
                        "gui.monster_conquest.arena.game.opponent_tbd").getString()
                        : state.opponentName();
                ArenaPlayerState opponentState = state.opponentId() == null
                        ? null : players.get(state.opponentId());
                String opponentLevel = opponentState == null
                        ? "-" : Integer.toString(opponentState.level());
                String opponentGold = opponentState == null
                        ? "-" : Integer.toString(opponentState.gold());
                String opponentHealth = opponentState == null
                        ? "-" : Integer.toString(opponentState.health());
                Component selfLevel = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar.level",
                        state.level()).withStyle(ChatFormatting.GREEN);
                Component selfXp = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar.xp",
                        state.xp(), state.xpToNext())
                        .withStyle(ChatFormatting.GREEN);
                Component selfGold = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar.gold",
                        state.gold()).withStyle(ChatFormatting.GOLD);
                Component selfHealth = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar.health",
                        state.health()).withStyle(ChatFormatting.GREEN);
                Component opponentName = Component.literal(opponent)
                        .withStyle(opponentState == null
                                ? ChatFormatting.GRAY
                                : ChatFormatting.RED);
                Component opponentLevelComponent = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar.level",
                        opponentLevel).withStyle(ChatFormatting.GREEN);
                Component opponentGoldComponent = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar.gold",
                        opponentGold).withStyle(ChatFormatting.GOLD);
                Component opponentHealthComponent =
                        Component.translatable(
                                "gui.monster_conquest.arena.game.boss_bar.health",
                                opponentHealth)
                                .withStyle(ChatFormatting.RED);
                Component phaseName = Component.translatable(phaseKey(phase))
                        .withStyle(phaseColor(phase));
                Component roundName = Component.literal(
                                roundLabel(displayRound()))
                        .withStyle(ChatFormatting.WHITE);
                Component baseName = Component.translatable(
                        "gui.monster_conquest.arena.game.boss_bar",
                        selfLevel,
                        selfXp,
                        selfGold,
                        selfHealth,
                        opponentName,
                        opponentLevelComponent,
                        opponentGoldComponent,
                        opponentHealthComponent,
                        phaseName,
                        roundName);
                if (phase == ArenaPhase.RESULT
                        && state.lastBattleOutcome()
                        != ArenaPlayerState.BattleOutcome.NONE) {
                    bar.setName(Component.translatable(
                            "gui.monster_conquest.arena.game.boss_bar_result",
                            baseName,
                            Component.translatable(resultKey(
                                    state.lastBattleOutcome()))
                                    .withStyle(resultColor(
                                            state.lastBattleOutcome()))));
                } else {
                    bar.setName(baseName);
                }
                float progress = phaseTicksTotal <= 0
                        ? 0.0f
                        : Math.max(0.0f, Math.min(1.0f,
                        phaseTicks / (float) phaseTicksTotal));
                bar.setProgress(progress);
                bar.setColor(switch (phase) {
                    case SELECTION -> BossBarColor.BLUE;
                    case BATTLE -> BossBarColor.RED;
                    case OVERTIME -> BossBarColor.RED;
                    case RESULT -> BossBarColor.YELLOW;
                    case FINISHED -> BossBarColor.WHITE;
                });
            }
        }

        private void removeBossBar(UUID playerId) {
            ServerBossEvent bar = bossBars.remove(playerId);
            if (bar != null) {
                bar.removeAllPlayers();
            }
        }

        private void clearBossBars() {
            for (ServerBossEvent bar : bossBars.values()) {
                bar.removeAllPlayers();
            }
            bossBars.clear();
        }

        @Nullable
        private AbstractArmySoldier spawnBenchSoldier(
                CombatSide side, ArenaPiece piece, Vec3 point,
                int boardIndex) {
            EntityType<? extends Mob> type = piece.unit().entityType().get();
            if (!(type.create(level) instanceof AbstractArmySoldier soldier)) {
                return null;
            }
            soldier.moveTo(point.x, point.y, point.z,
                    ArenaPlotLayout.viewYaw(side.side), 0.0f);
            soldier.setSoldierType(piece.unit().form());
            soldier.setCommanderUuid(side.ownerId);
            soldier.enterArenaPresentationMode(side.side, boardIndex);
            applyArenaAppearance(piece, soldier);
            applyArenaStats(side.pieces, piece, soldier, true);
            faceArenaForward(soldier, side.side);
            soldier.setInvulnerable(true);
            soldier.setNoGravity(true);
            soldier.setSilent(true);
            soldier.setPersistenceRequired();
            soldier.addTag("monster_conquest_arena_bench");
            level.addFreshEntity(soldier);
            return soldier;
        }

        private void refreshBenchDisplays(ArenaPlayerState state) {
            for (Duel duel : duels) {
                if (duel.left.actual == state) {
                    spawnBenchDisplays(duel.left, duel.boardIndex);
                    return;
                }
                if (duel.right.actual == state) {
                    spawnBenchDisplays(duel.right, duel.boardIndex);
                    return;
                }
            }
        }

        private void refreshCombatEquipment(ArenaPlayerState state,
                                            UUID pieceId) {
            if (pieceId.equals(new UUID(0L, 0L))) return;
            ArenaPiece piece = state.piece(pieceId);
            if (piece == null) return;
            for (Duel duel : duels) {
                CombatSide side = null;
                if (duel.left.actual == state) side = duel.left;
                else if (duel.right.actual == state) side = duel.right;
                if (side == null) continue;
                AbstractArmySoldier soldier = side.pieceEntities.get(pieceId);
                if (soldier != null && !soldier.isRemoved()) {
                    applyArenaAppearance(piece, soldier);
                    applyArenaStats(side, piece, soldier, false);
                }
                return;
            }
        }

        private void refreshCombatPieces(ArenaPlayerState state) {
            for (Duel duel : duels) {
                CombatSide side = null;
                if (duel.left.actual == state) side = duel.left;
                else if (duel.right.actual == state) side = duel.right;
                if (side == null) continue;
                for (ArenaPiece piece : state.deployedPieces()) {
                    AbstractArmySoldier soldier =
                            side.pieceEntities.get(piece.id());
                    if (soldier != null && !soldier.isRemoved()) {
                        applyArenaAppearance(piece, soldier);
                        applyArenaStats(side, piece, soldier, false);
                    }
                }
                return;
            }
        }

        @Nullable
        private AbstractArmySoldier spawnSoldier(CombatSide side,
                                                 ArenaPiece piece,
                                                 Vec3 point,
                                                 int boardIndex) {
            EntityType<? extends Mob> type = piece.unit().entityType().get();
            if (!(type.create(level) instanceof AbstractArmySoldier soldier)) return null;

            soldier.moveTo(point.x, point.y, point.z,
                    ArenaPlotLayout.viewYaw(side.side), 0.0f);
            soldier.setSoldierType(piece.unit().form());
            soldier.setFactionId(ResourceLocation.fromNamespaceAndPath(
                    Monster_conquest.MODID,
                    "arena/" + id.toString().replace("-", "")
                            + "/" + side.side));
            soldier.setCommanderUuid(side.ownerId);
            soldier.setSquadId(0);
            soldier.enterArenaMode(id, side.side, boardIndex);
            soldier.setArenaSourcePieceId(
                    side.actual == null ? null : piece.id());
            soldier.setArenaWild(side.wildScaling != null);
            faceArenaForward(soldier, side.side);
            applyArenaAppearance(piece, soldier);
            applyArenaStats(side, piece, soldier, true);
            soldier.addArenaSkillChargeRaw(
                    soldier.arenaEquipment().initialSkillChargeBonus());
            soldier.addTag("monster_conquest_arena");
            level.addFreshEntity(soldier);
            side.pieceEntities.put(piece.id(), soldier);
            if (side.actual != null) {
                side.actual.combatEntities().add(soldier.getUUID());
            }
            return soldier;
        }

        private void faceArenaForward(AbstractArmySoldier soldier, int side) {
            float yaw = ArenaPlotLayout.viewYaw(side);
            soldier.setYRot(yaw);
            soldier.yRotO = yaw;
            soldier.setYHeadRot(yaw);
            soldier.yHeadRotO = yaw;
            soldier.setYBodyRot(yaw);
            soldier.yBodyRotO = yaw;
        }

        private void applyArenaAppearance(ArenaPiece piece,
                                          AbstractArmySoldier soldier) {
            net.minecraft.world.item.Item helmet = null;
            net.minecraft.world.item.Item chestplate = null;
            net.minecraft.world.item.Item leggings = null;
            net.minecraft.world.item.Item boots = null;
            boolean shield = piece.unit().builtInShield();
            for (ArenaEquipment equipment : piece.equipment()) {
                if (equipment.helmetVisual() != null) {
                    helmet = equipment.helmetVisual();
                }
                if (equipment.chestVisual() != null) {
                    chestplate = equipment.chestVisual();
                }
                if (equipment.leggingsVisual() != null) {
                    leggings = equipment.leggingsVisual();
                }
                if (equipment.bootsVisual() != null) {
                    boots = equipment.bootsVisual();
                }
                if (equipment.effect() == ArenaEquipment.Effect.SHIELD) {
                    shield = true;
                }
            }
            soldier.setArenaStar(piece.star());
            soldier.setArenaEquipmentIds(piece.equipment().stream()
                    .map(ArenaEquipment::ordinal).toList());
            soldier.applyArenaEquipmentAppearance(
                    helmet, chestplate, leggings, boots, shield);
        }

        private void applyArenaStats(CombatSide side, ArenaPiece piece,
                                     AbstractArmySoldier soldier,
                                     boolean healToFull) {
            applyArenaStats(side.pieces, piece, soldier,
                    healToFull, side.wildScaling);
        }

        private void applyArenaStats(List<ArenaPiece> roster, ArenaPiece piece,
                                     AbstractArmySoldier soldier,
                                     boolean healToFull) {
            applyArenaStats(
                    roster, piece, soldier, healToFull, null);
        }

        private void applyArenaStats(
                List<ArenaPiece> roster, ArenaPiece piece,
                AbstractArmySoldier soldier, boolean healToFull,
                @Nullable ArenaWildRules.StatScaling wildScaling) {
            ArenaStatCalculator.Stats stats =
                    ArenaStatCalculator.calculate(piece, roster);
            if (wildScaling != null) {
                stats = wildScaling.apply(stats);
            }
            soldier.arenaTraits().configure(
                    piece.unitIndex(),
                    roster.stream().map(ArenaPiece::unitIndex).toList());
            soldier.configureArenaSkill(
                    piece.unit().skillType(),
                    piece.unit().skillChargeRequired());
            double oldMaxHealth = soldier.getMaxHealth();
            double previousArenaMax = soldier.getArenaDisplayMaxHealth();
            double healthRatio;
            if (previousArenaMax > 0.0) {
                healthRatio = soldier.getArenaHealthRatio();
                oldMaxHealth = previousArenaMax;
            } else {
                healthRatio = oldMaxHealth <= 0.0
                        ? 1.0
                        : soldier.getHealth() / oldMaxHealth;
            }
            var healthAttribute = soldier.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttribute != null) {
                double targetHealth = Math.max(1.0, stats.maxHealth());
                double baseHealth = Math.min(
                        VANILLA_MAX_HEALTH_LIMIT, targetHealth);
                healthAttribute.setBaseValue(baseHealth);
            }
            var damageAttribute = soldier.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damageAttribute != null) {
                damageAttribute.setBaseValue(stats.attackDamage());
            }
            var armorAttribute = soldier.getAttribute(Attributes.ARMOR);
            if (armorAttribute != null) {
                armorAttribute.setBaseValue(stats.armor());
            }
            var speedAttribute = soldier.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speedAttribute != null) {
                speedAttribute.setBaseValue(stats.movementSpeed());
            }
            var attackSpeedAttribute = soldier.getAttribute(
                    Attributes.ATTACK_SPEED);
            if (attackSpeedAttribute != null) {
                attackSpeedAttribute.setBaseValue(stats.attackSpeed());
            }
            soldier.setArenaRangedDamage(stats.attackDamage());
            soldier.setArenaAttackSpeed(stats.attackSpeed());
            soldier.setArenaTraitAttackRangeBonus(
                    stats.attackRangeBonus());
            soldier.setArenaBaseAttackSpeed(
                    piece.unit().attackSpeed());
            soldier.setArenaMagicResistance(stats.magicResistance());
            soldier.setArenaMagicPower(stats.magicPower());
            soldier.setArenaDisplayMaxHealth(stats.maxHealth());
            soldier.setArenaCurrentHealth(healToFull
                    ? stats.maxHealth()
                    : Math.max(1.0, stats.maxHealth()
                    * Math.min(1.0, healthRatio)));
            soldier.arenaSkills().reapplyArenaStatBonuses(
                    healToFull ? 1.0 : healthRatio);
            soldier.arenaTraits().syncRuntimeStats();
            soldier.syncArenaHealthMirror();
            if (soldier.isArenaUnit()) {
                soldier.arenaTraits().initialize();
            }
        }

        private void checkDuels() {
            for (Duel duel : duels) {
                if (duel.resolved) continue;
                duel.left.tickSnifferDominance(level);
                duel.right.tickSnifferDominance(level);
                int left = duel.left.livingCount();
                int right = duel.right.livingCount();
                if (left > 0 && right > 0) continue;
                if (left == 0 && right == 0) {
                    resolveDraw(duel);
                } else if (left == 0) {
                    resolveElimination(duel, duel.right, duel.left);
                } else {
                    resolveElimination(duel, duel.left, duel.right);
                }
            }
        }

        private void resolveElimination(Duel duel, CombatSide winner, CombatSide loser) {
            int survivorCount = winner.livingCount();
            int damage = Math.min(survivorCount, winner.level);
            applyDamage(loser, damage);
            markWinner(winner);
            markLoser(loser);
            settleCaravan(duel, winner, loser);
            if (duel.wildEncounter
                    && winner.actual != null
                    && loser.actual == null) {
                spawnWildRewards(duel, winner, loser);
            }
            duel.resolved = true;
        }

        private void resolveTimeout() {
            for (Duel duel : duels) {
                if (duel.resolved) continue;
                int left = duel.left.livingCount();
                int right = duel.right.livingCount();
                if (left == right) {
                    resolveDraw(duel);
                    continue;
                }
                CombatSide winner = left > right ? duel.left : duel.right;
                CombatSide loser = left > right ? duel.right : duel.left;
                applyDamage(loser, Math.abs(left - right));
                markWinner(winner);
                markLoser(loser);
                settleCaravan(duel, winner, loser);
                duel.resolved = true;
            }
            if (!duels.isEmpty() && duels.stream().allMatch(duel -> duel.resolved)) {
                enterResult();
            }
        }

        private void applyDamage(CombatSide loser, int damage) {
            if (loser.actual == null || damage <= 0
                    || !loser.actual.alive()) {
                return;
            }
            int stage = Math.max(1, round - 1) / 7 + 1;
            // 阶段递增必须提高容错成本，否则后期可以长期保留残血玩家。
            int totalDamage = Math.min(15, damage + stage);
            boolean wasAlive = loser.actual.alive();
            loser.actual.setLastDamage(totalDamage);
            loser.actual.damage(totalDamage);
            if (wasAlive && !loser.actual.alive()) {
                recordElimination(loser.actual);
            }
            updateHealthScore(loser.actual);
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] damage room={} round={} player={} "
                            + "base={} stage={} total={} health={}",
                    roomId, round, loser.actual.name(), damage, stage,
                    totalDamage, loser.actual.health());
        }

        private void settleCaravan(
                Duel duel, CombatSide winner, CombatSide loser) {
            if (duel.wildEncounter) return;
            if (!duel.shadowEncounter
                    && (winner.actual == null
                    || loser.actual == null)) {
                return;
            }
            if (winner.actual != null) {
                settleCaravanSide(
                        winner.actual, winner.pieces, true);
            }
            if (loser.actual != null) {
                settleCaravanSide(
                        loser.actual, loser.pieces, false);
            }
        }

        private void settleCaravanSide(
                ArenaPlayerState owner,
                List<ArenaPiece> pieces,
                boolean won) {
            var settlement = ArenaTraitEconomy.settleCaravan(
                    owner, pieces, won, random);
            pendingCaravanSettlements.add(
                    new PendingCaravanSettlement(
                            owner.id(), settlement));
            Monster_conquest.LOGGER.info(
                    "[ARENA-TRAIT] caravan room={} round={} player={} "
                            + "won={} tier={} stacks={} gold={} "
                            + "lesserDuplicators={} components={} "
                            + "specials={} duplicators={}",
                    roomId, round, owner.name(), won,
                    settlement.tier(), settlement.stacks(),
                    settlement.gold(),
                    settlement.lesserDuplicators(),
                    settlement.components().size(),
                    settlement.specialEquipment(),
                    settlement.duplicators());
        }

        private void applyPendingCaravanSettlements() {
            if (pendingCaravanSettlements.isEmpty()) return;
            for (PendingCaravanSettlement pending
                    : pendingCaravanSettlements) {
                ArenaPlayerState owner = players.get(
                        pending.ownerId());
                if (owner == null) continue;
                ArenaTraitEconomy.applyCaravanSettlement(
                        owner, pending.settlement());
                var settlement = pending.settlement();
                if (settlement.gold() > 0
                        || settlement.lesserDuplicators() > 0
                        || !settlement.components().isEmpty()
                        || !settlement.specialEquipment().isEmpty()
                        || settlement.duplicators() > 0) {
                    queueCaravanPresentation(
                            owner.id(), settlement);
                }
                Monster_conquest.LOGGER.info(
                        "[ARENA-TRAIT] caravan apply room={} round={} "
                                + "player={} tier={} stacks={} gold={}",
                        roomId, round, owner.name(),
                        settlement.tier(), settlement.stacks(),
                        settlement.gold());
            }
            pendingCaravanSettlements.clear();
        }

        /**
         * 商队经济与展示物共用同一份已锁定结算快照，并延迟到玩家
         * 回到自己的准备阶段主场后一起生效。展示物不能再次结算。
         */
        private void queueCaravanPresentation(
                UUID ownerId,
                ArenaTraitEconomy.CaravanSettlement reward) {
            List<ArenaEquipment> equipment = new ArrayList<>();
            for (int i = 0; i < reward.lesserDuplicators(); i++) {
                equipment.add(ArenaEquipment.LESSER_DUPLICATOR);
            }
            equipment.addAll(reward.components());
            equipment.addAll(reward.specialEquipment());
            for (int i = 0; i < reward.duplicators(); i++) {
                equipment.add(ArenaEquipment.DUPLICATOR);
            }
            if (reward.gold() <= 0 && equipment.isEmpty()) return;
            pendingCaravanPresentations.add(new CaravanPresentation(
                    ownerId, reward.gold(), List.copyOf(equipment)));
        }

        private void spawnPendingCaravanPresentations(
                boolean useBattleView) {
            if (pendingCaravanPresentations.isEmpty()) return;
            for (CaravanPresentation presentation
                    : pendingCaravanPresentations) {
                ArenaPlayerState owner = players.get(
                        presentation.ownerId());
                BoardView view = (useBattleView
                        ? lastBoardViews : homeBoardViews)
                        .get(presentation.ownerId());
                if (owner == null || !owner.alive() || view == null) {
                    continue;
                }
                Vec3 center = ArenaPlotLayout.unitPoint(
                        roomId, view.boardIndex(), view.side(), 4, 2);
                int visualIndex = 0;
                if (presentation.gold() > 0) {
                    spawnLootDrop(owner.id(),
                            ArenaLootDrop.TYPE_GOLD, presentation.gold(),
                            lootDropPoint(center, visualIndex++), true);
                }
                for (ArenaEquipment item : presentation.equipment()) {
                    spawnLootDrop(owner.id(),
                            ArenaLootDrop.TYPE_EQUIPMENT,
                            item.ordinal(),
                            lootDropPoint(center, visualIndex++), true);
                }
                Monster_conquest.LOGGER.info(
                        "[ARENA-TRAIT] caravan visual room={} round={} "
                                + "player={} board={} side={} gold={} "
                                + "equipment={}",
                        roomId, round, owner.name(),
                        view.boardIndex(), view.side(),
                        presentation.gold(), presentation.equipment());
            }
            pendingCaravanPresentations.clear();
        }

        private Vec3 lootDropPoint(Vec3 center, int index) {
            if (index == 0) return center;
            double angle = (index - 1) * Math.PI / 3.0;
            double radius = 0.8 + 0.28 * ((index - 1) / 6);
            return center.add(
                    Math.cos(angle) * radius, 0.0,
                    Math.sin(angle) * radius);
        }

        private void spawnWildRewards(
                Duel duel, CombatSide winner,
                CombatSide defeatedWilds) {
            int stage = ArenaWildRules.stageForRound(round);
            int gold = ArenaWildRules.goldReward(stage);
            Vec3 point = wildDropPoint(
                    defeatedWilds, duel.boardIndex);
            spawnLootDrop(winner.actual.id(),
                    ArenaLootDrop.TYPE_GOLD, gold, point);
            // 固定奖励按整波结算一次；野怪数量只影响下面的独立 50% 判定。
            ArenaEquipment remover = ArenaEquipment.REMOVER;
            ArenaEquipment component =
                    ArenaWildRules.randomBasicComponent(random);
            spawnLootDrop(winner.actual.id(),
                    ArenaLootDrop.TYPE_EQUIPMENT,
                    remover.ordinal(),
                    point.add(0.9, 0.0, 0.0));
            spawnLootDrop(winner.actual.id(),
                    ArenaLootDrop.TYPE_EQUIPMENT,
                    component.ordinal(),
                    point.add(1.8, 0.0, 0.0));
            int bonusDrops = 0;
            for (int i = 0; i < defeatedWilds.pieces.size(); i++) {
                ArenaEquipment bonus =
                        ArenaWildRules.rollMonsterBonusReward(random);
                if (bonus == null) continue;
                spawnLootDrop(winner.actual.id(),
                        ArenaLootDrop.TYPE_EQUIPMENT,
                        bonus.ordinal(),
                        point.add(2.7 + bonusDrops * 0.9,
                                0.0, 0.0));
                bonusDrops++;
            }
            Monster_conquest.LOGGER.info(
                    "[ARENA-WILD] reward room={} round={} player={} "
                            + "stage={} gold={} remover={} component={} "
                            + "monsterBonusDrops={}",
                    roomId, round, winner.actual.name(),
                    stage, gold, remover, component,
                    bonusDrops);
        }

        private Vec3 wildDropPoint(
                CombatSide side, int boardIndex) {
            if (!side.entities.isEmpty()) {
                Vec3 total = Vec3.ZERO;
                for (AbstractArmySoldier entity : side.entities) {
                    total = total.add(entity.position());
                }
                return total.scale(1.0 / side.entities.size());
            }
            return ArenaPlotLayout.unitPoint(
                    roomId, boardIndex, 1, 4, 0);
        }

        private void spawnLootDrop(
                UUID ownerId, int type, int payload,
                Vec3 point) {
            spawnLootDrop(ownerId, type, payload, point, false);
        }

        private void spawnLootDrop(
                UUID ownerId, int type, int payload,
                Vec3 point, boolean rewardSettled) {
            ArenaLootDrop drop = ModEntities.ARENA_LOOT_DROP
                    .get().create(level);
            if (drop == null) return;
            drop.moveTo(point.x, point.y, point.z,
                    random.nextFloat() * 360.0f, 0.0f);
            drop.configure(id, ownerId, type, payload, rewardSettled);
            if (level.addFreshEntity(drop)) {
                lootDrops.add(drop);
            }
        }

        private boolean claimLoot(
                UUID ownerId, int type, int payload) {
            ArenaPlayerState owner = players.get(ownerId);
            if (owner == null) {
                Monster_conquest.LOGGER.warn(
                        "[ARENA-WILD] missing loot owner room={} match={} "
                                + "owner={} type={} payload={}",
                        roomId, id, ownerId, type, payload);
                return false;
            }
            if (type == ArenaLootDrop.TYPE_GOLD) {
                ArenaWildRules.applyReward(owner, type, payload);
                Monster_conquest.LOGGER.info(
                        "[ARENA-WILD] claim room={} player={} gold={}",
                        roomId, owner.name(), payload);
                return true;
            }
            if (!ArenaWildRules.validEquipmentReward(payload)) {
                return false;
            }
            ArenaEquipment item =
                    ArenaEquipment.byId(payload);
            if (ArenaWildRules.applyReward(
                    owner, type, payload)) {
                Monster_conquest.LOGGER.info(
                        "[ARENA-WILD] claim room={} player={} item={}",
                        roomId, owner.name(), item);
                return true;
            }
            pendingLootEquipment.computeIfAbsent(
                    ownerId, ignored -> new ArrayList<>())
                    .add(item);
            Monster_conquest.LOGGER.info(
                    "[ARENA-WILD] pending room={} player={} item={}",
                    roomId, owner.name(), item);
            return true;
        }

        private void deliverPendingEquipment() {
            if (pendingLootEquipment.isEmpty()) return;
            var iterator = pendingLootEquipment.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, List<ArenaEquipment>> entry =
                        iterator.next();
                ArenaPlayerState owner = players.get(entry.getKey());
                if (owner == null) {
                    iterator.remove();
                    continue;
                }
                entry.getValue().removeIf(owner::addEquipment);
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }

        private void flushLootDrops(boolean discardSettledVisuals) {
            for (ArenaLootDrop drop : List.copyOf(lootDrops)) {
                if (drop.isRemoved()) continue;
                if (discardSettledVisuals) {
                    drop.expireNow();
                } else if (!drop.rewardSettled()) {
                    drop.expireNow();
                }
            }
            lootDrops.removeIf(ArenaLootDrop::isRemoved);
            deliverPendingEquipment();
        }

        private void resolveDraw(Duel duel) {
            applyDamage(duel.left, 1);
            applyDamage(duel.right, 1);
            markDraw(duel.left);
            markDraw(duel.right);
            duel.resolved = true;
        }

        private void teleportToDuels() {
            for (Duel duel : duels) {
                if (duel.left.actual != null) {
                    lastBoardViews.put(duel.left.actual.id(),
                            new BoardView(duel.boardIndex, duel.left.side));
                }
                if (duel.right.actual != null) {
                    lastBoardViews.put(duel.right.actual.id(),
                            new BoardView(duel.boardIndex, duel.right.side));
                }
                teleportBattleSide(duel, duel.left);
                teleportBattleSide(duel, duel.right);
            }
        }

        private void teleportBattleSide(
                Duel duel, CombatSide side) {
            // 战斗开始时双方都重新落位，避免准备阶段移动视角后仍停留在旧位置。
            teleportSide(side, duel.boardIndex);
        }

        private boolean teleportToBoardView(ServerPlayer player,
                                            ArenaPlayerState target) {
            BoardView view = phase == ArenaPhase.SELECTION
                    ? homeBoardViews.get(target.id())
                    : lastBoardViews.get(target.id());
            if (view == null) return false;
            Vec3 point = ArenaPlotLayout.playerViewPoint(
                    roomId, view.boardIndex(), view.side());
            player.setGameMode(GameType.SPECTATOR);
            player.teleportTo(level,
                    point.x, point.y, point.z,
                    ArenaPlotLayout.viewYaw(view.side()),
                    ArenaPlotLayout.viewPitch());
            return true;
        }

        @Nullable
        private ArenaPlayerState enterSpectator(ServerPlayer player) {
            ArenaPlayerState target = spectatorTarget(null);
            if (target == null
                    || !teleportToBoardView(player, target)) {
                return null;
            }
            return target;
        }

        @Nullable
        private ArenaPlayerState spectatorTarget(@Nullable UUID preferredId) {
            if (preferredId != null) {
                ArenaPlayerState preferred = players.get(preferredId);
                if (preferred != null) return preferred;
            }
            for (ArenaPlayerState state : players.values()) {
                if (!state.ai() && state.alive()) return state;
            }
            for (ArenaPlayerState state : players.values()) {
                if (!state.ai()) return state;
            }
            return null;
        }

        private boolean canUseEconomy() {
            return phase == ArenaPhase.SELECTION
                    || phase == ArenaPhase.BATTLE
                    || phase == ArenaPhase.OVERTIME
                    || phase == ArenaPhase.RESULT;
        }

        private void enterResult() {
            // 只在常规战斗或加时赛真正结束后清理战斗残留。
            clearOvertimeState();
            phase = ArenaPhase.RESULT;
            phaseTicks = ArenaConfig.resultTicks;
            phaseTicksTotal = phaseTicks;
            flushLootDrops(false);
            if (alivePlayers().size() <= 1) {
                applyPendingCaravanSettlements();
                spawnPendingCaravanPresentations(true);
            }
            sendBattleResultSounds();
            battleResults.clear();
            cleanupArenaGroundEffects();
            for (ArenaPlayerState state : players.values()) {
                state.clearTemporaryEquipment();
            }
            sendSnapshots(false);
        }

        private void sendBattleResultSounds() {
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player == null) continue;
                int result = battleResults.getOrDefault(
                        state.id(),
                        ArenaPlayerSnapshotPayload.BATTLE_NONE);
                if (result == ArenaPlayerSnapshotPayload.BATTLE_WON) {
                    sendSound(player,
                            ArenaSoundPayload.BATTLE_VICTORY);
                } else if (result
                        == ArenaPlayerSnapshotPayload.BATTLE_LOST) {
                    sendSound(player,
                            ArenaSoundPayload.BATTLE_DEFEAT);
                }
            }
        }

        private void markFighting(CombatSide side) {
            if (side.actual != null) {
                battleResults.put(
                        side.actual.id(),
                        ArenaPlayerSnapshotPayload.BATTLE_FIGHTING);
            }
        }

        private void markWinner(CombatSide side) {
            if (side.actual == null) return;
            battleResults.put(
                    side.actual.id(),
                    ArenaPlayerSnapshotPayload.BATTLE_WON);
            completeRound(side.actual.id(),
                    ArenaPlayerSnapshotPayload.BATTLE_WON);
            side.actual.recordBattleOutcome(
                    ArenaPlayerState.BattleOutcome.WIN,
                    !ArenaWildRules.isWildRound(round));
        }

        private void markLoser(CombatSide side) {
            if (side.actual == null) return;
            battleResults.put(
                    side.actual.id(),
                    ArenaPlayerSnapshotPayload.BATTLE_LOST);
            completeRound(side.actual.id(),
                    ArenaPlayerSnapshotPayload.BATTLE_LOST);
            side.actual.recordBattleOutcome(
                    ArenaPlayerState.BattleOutcome.LOSS,
                    !ArenaWildRules.isWildRound(round));
        }

        private void markDraw(CombatSide side) {
            if (side.actual == null) return;
            battleResults.put(
                    side.actual.id(),
                    ArenaPlayerSnapshotPayload.BATTLE_DRAW);
            completeRound(side.actual.id(),
                    ArenaPlayerSnapshotPayload.BATTLE_DRAW);
            side.actual.recordBattleOutcome(
                    ArenaPlayerState.BattleOutcome.DRAW,
                    !ArenaWildRules.isWildRound(round));
        }

        private int battleResult(UUID playerId) {
            return battleResults.getOrDefault(
                    playerId, ArenaPlayerSnapshotPayload.BATTLE_NONE);
        }

        private void awardRoundIncome(ArenaPlayerState state) {
            int nextRound = round + 1;
            int fixed = fixedIncomeForRound(nextRound);
            int interest = Math.min(5, state.gold() / 10);
            int victory = state.lastBattleOutcome()
                    == ArenaPlayerState.BattleOutcome.WIN ? 1 : 0;
            int streak = state.streakIncome();
            int total = fixed + interest + victory + streak;
            state.addGold(total);
            state.clearBattleOutcome();
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] income room={} player={} round={} total={} "
                            + "fixed={} interest={} victory={} streak={}",
                    roomId, state.name(), roundLabel(nextRound),
                    total, fixed, interest, victory, streak);
        }

        private void tickPreparationBell() {
            if (phase != ArenaPhase.SELECTION
                    || phaseTicks <= 0 || phaseTicks > 3 * 20) {
                return;
            }
            int secondsLeft = (phaseTicks + 19) / 20;
            if (secondsLeft == lastPreparationBellSecond) return;
            lastPreparationBellSecond = secondsLeft;
            int cue = switch (secondsLeft) {
                case 3 -> ArenaSoundPayload.COUNTDOWN_BELL_THREE;
                case 2 -> ArenaSoundPayload.COUNTDOWN_BELL_TWO;
                default -> ArenaSoundPayload.COUNTDOWN_BELL_ONE;
            };
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player != null) {
                    sendSound(player, cue);
                }
            }
        }

        private int fixedIncomeForRound(int absoluteRound) {
            int stage = (absoluteRound - 1) / 7 + 1;
            int step = (absoluteRound - 1) % 7 + 1;
            if (stage == 1) {
                if (step == 1) return 0;
                if (step <= 3) return 2;
                return 3;
            }
            if (stage == 2 && step == 1) return 4;
            return 5;
        }

        private List<RoundView> roundViews(UUID playerId) {
            int currentRound = displayRound();
            int stageStart = (currentRound - 1)
                    / ArenaWildRules.ROUNDS_PER_STAGE
                    * ArenaWildRules.ROUNDS_PER_STAGE + 1;
            Map<Integer, RoundRecord> history =
                    roundHistory.getOrDefault(playerId, Map.of());
            List<RoundView> views = new ArrayList<>(
                    ArenaWildRules.ROUNDS_PER_STAGE);
            for (int index = 0;
                 index < ArenaWildRules.ROUNDS_PER_STAGE;
                 index++) {
                int absoluteRound = stageStart + index;
                RoundRecord record = history.get(absoluteRound);
                boolean wild = record != null
                        ? record.wild()
                        : ArenaWildRules.isWildRound(absoluteRound);
                views.add(new RoundView(
                        absoluteRound,
                        record == null
                                ? ArenaPlayerSnapshotPayload.BATTLE_NONE
                                : record.result(),
                        record == null
                                ? new UUID(0L, 0L)
                                : record.opponentId(),
                        record == null ? "" : record.opponentName(),
                        wild,
                        absoluteRound == currentRound));
            }
            return views;
        }

        private String roundLabel(int absoluteRound) {
            int safeRound = Math.max(1, absoluteRound);
            int stage = (safeRound - 1) / 7 + 1;
            int step = (safeRound - 1) % 7 + 1;
            return stage + "-" + step;
        }

        private int displayRound() {
            return phase == ArenaPhase.SELECTION
                    ? Math.max(1, round + 1)
                    : Math.max(1, round);
        }

        private void surrender(ArenaPlayerState state) {
            if (!state.alive()) return;
            int remainingHealth = state.health();
            state.setLastDamage(remainingHealth);
            state.damage(remainingHealth);
            recordElimination(state);
            for (Duel duel : duels) {
                if (duel.left.actual == state) {
                    if (!duel.resolved) {
                        markLoser(duel.left);
                        markWinner(duel.right);
                        duel.resolved = true;
                    }
                    duel.left.discard();
                }
                if (duel.right.actual == state) {
                    if (!duel.resolved) {
                        markLoser(duel.right);
                        markWinner(duel.left);
                        duel.resolved = true;
                    }
                    duel.right.discard();
                }
            }
            removePreview(state.id());
            updateHealthScore(state);
            ExternalArenaServerRuntime.reportParticipantDeparted(
                    roomId, state.id());
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] surrender room={} round={} player={}",
                    roomId, round, state.name());
            if (phase == ArenaPhase.SELECTION
                    && alivePlayers().size() <= 1) {
                finish();
            }
        }

        private void surrenderOnDisconnect(UUID playerId) {
            if (finished) return;
            ArenaPlayerState state = players.get(playerId);
            if (state == null) return;
            removeBossBar(playerId);
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] disconnect-surrender room={} round={} player={}",
                    roomId, round, state.name());
            surrender(state);
            if (!finished && !hasHumanControllers()) {
                finish();
            } else if (!finished) {
                sendSnapshots(false);
            }
        }

        private boolean hasHumanControllers() {
            for (ArenaPlayerState state : players.values()) {
                if (!state.ai()
                        && MATCHES_BY_PLAYER.get(state.id()) == this) {
                    return true;
                }
            }
            return false;
        }

        private void teleportSide(CombatSide side, int boardIndex) {
            if (side.actual == null || side.actual.ai()) return;
            ServerPlayer player = player(side.actual.id());
            if (player == null) return;
            player.setGameMode(GameType.SPECTATOR);
            Vec3 point = ArenaPlotLayout.playerViewPoint(roomId, boardIndex, side.side);
            player.teleportTo(level,
                    point.x, point.y, point.z,
                    ArenaPlotLayout.viewYaw(side.side),
                    ArenaPlotLayout.viewPitch());
        }

        private void cleanupCombat() {
            clearOvertimeState();
            cleanupArenaGroundEffects();
            for (Duel duel : duels) {
                duel.left.discard();
                duel.right.discard();
            }
            duels.clear();
            clearAllPreviews();
        }

        private void cleanupArenaGroundEffects() {
            ArenaPlotLayout.clearCombatResiduals(level, roomId);
        }

        private List<ArenaPlayerState> alivePlayers() {
            List<ArenaPlayerState> result = new ArrayList<>();
            for (ArenaPlayerState state : players.values()) {
                if (state.alive()) result.add(state);
            }
            return result;
        }

        private void recordElimination(ArenaPlayerState state) {
            if (!eliminationOrder.contains(state.id())) {
                eliminationOrder.add(state.id());
            }
            eliminationRounds.putIfAbsent(
                    state.id(), Math.max(1, round));
            state.clearOpponent();
            for (ArenaPlayerState other : players.values()) {
                if (state.id().equals(other.opponentId())) {
                    other.clearOpponent();
                }
            }
            lastBoardViews.remove(state.id());
            state.returnAllCardsToPool();
        }

        private List<ArenaPlayerState> humanPlayers() {
            List<ArenaPlayerState> result = new ArrayList<>();
            for (ArenaPlayerState state : players.values()) {
                if (!state.ai()) result.add(state);
            }
            return result;
        }

        private void removePlayer(UUID playerId) {
            ArenaPlayerState removed = players.remove(playerId);
            if (removed != null) {
                removed.damage(1000);
                recordElimination(removed);
            }
            removePreview(playerId);
            removeBossBar(playerId);
            for (Duel duel : duels) {
                if (duel.left.actual == removed) duel.left.discard();
                if (duel.right.actual == removed) duel.right.discard();
            }
            if (humanPlayers().isEmpty()) {
                finish();
            }
        }

        private void finish() {
            if (finished || finishPending) return;
            finishPending = true;
            // 延迟完成期间保持 Match 注册，才能继续向客户端发送冠军快照。
            finishDelayTicks = 5 * 20;
            phase = ArenaPhase.FINISHED;
            phaseTicks = finishDelayTicks;
            phaseTicksTotal = finishDelayTicks;
            championName = determineChampionName();
            finalPlacements = buildPlacements();
            Monster_conquest.LOGGER.info(
                    "[ARENA-MATCH] finish-pending room={} round={} "
                            + "champion={} delayTicks={}",
                    roomId, round, championName, finishDelayTicks);
            clearBossBars();
            applyPendingCaravanSettlements();
            flushLootDrops(true);
            sendSnapshots(true);
        }

        private String determineChampionName() {
            return firstPlaceStates().stream()
                    .map(ArenaPlayerState::name)
                    .collect(java.util.stream.Collectors.joining(" & "));
        }

        private List<ArenaPlayerState> firstPlaceStates() {
            List<ArenaPlayerState> alive = alivePlayers();
            if (!alive.isEmpty()) {
                alive.sort(Comparator.comparingInt(
                        ArenaPlayerState::health).reversed());
                return List.of(alive.get(0));
            }

            int finalRound = Math.max(1, round);
            List<ArenaPlayerState> tied = new ArrayList<>();
            for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
                ArenaPlayerState state = players.get(
                        eliminationOrder.get(i));
                if (state != null
                        && eliminationRounds.getOrDefault(
                        state.id(), -1) == finalRound
                        && !tied.contains(state)) {
                    tied.add(state);
                }
            }
            if (tied.isEmpty() && !eliminationOrder.isEmpty()) {
                ArenaPlayerState last = players.get(
                        eliminationOrder.get(
                                eliminationOrder.size() - 1));
                if (last != null) tied.add(last);
            }
            return tied;
        }

        private void completeFinish() {
            if (finished) return;
            finished = true;
            finishPending = false;
            cleanupCombat();
            ArenaPlotLayout.release(level, roomId);
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player == null) continue;
                sendInactive(player);
            }
            if (ArenaRuntime.isArenaServer()) {
                ExternalArenaServerRuntime.finishMatch(
                        roomId, finalPlacements);
            } else {
                for (ArenaPlayerState state : players.values()) {
                    if (state.ai()) continue;
                    ServerPlayer player = player(state.id());
                    if (player != null) teleportHome(player);
                }
            }
        }

        private List<ArenaBridgeResult.PlayerPlacement> buildPlacements() {
            List<ArenaPlayerState> ranking = new ArrayList<>();
            List<ArenaPlayerState> alive = new ArrayList<>();
            for (ArenaPlayerState state : players.values()) {
                if (state.alive()) alive.add(state);
            }
            alive.sort(Comparator.comparingInt(ArenaPlayerState::health)
                    .reversed());
            ranking.addAll(alive);
            // 已淘汰玩家只按淘汰顺序反向排列：最早淘汰者名次最低。
            for (int i = eliminationOrder.size() - 1; i >= 0; i--) {
                ArenaPlayerState eliminated = players.get(
                        eliminationOrder.get(i));
                if (eliminated != null && !ranking.contains(eliminated)) {
                    ranking.add(eliminated);
                }
            }
            for (ArenaPlayerState state : players.values()) {
                if (!ranking.contains(state)) ranking.add(state);
            }

            int participants = ranking.size();
            List<ArenaPlayerState> tiedFirst = alive.isEmpty()
                    ? firstPlaceStates() : List.of();
            Set<UUID> tiedFirstIds = new HashSet<>();
            for (ArenaPlayerState state : tiedFirst) {
                tiedFirstIds.add(state.id());
            }
            int nextPlacement = tiedFirst.isEmpty()
                    ? 1 : tiedFirst.size() + 1;
            Map<UUID, Integer> placementByPlayer =
                    new HashMap<>();
            for (ArenaPlayerState state : ranking) {
                placementByPlayer.put(
                        state.id(),
                        tiedFirstIds.contains(state.id())
                                ? 1 : nextPlacement++);
            }
            List<ArenaBridgeResult.PlayerPlacement> placements =
                    new ArrayList<>();
            for (ArenaPlayerState state : ranking) {
                if (state.ai()) continue;
                placements.add(new ArenaBridgeResult.PlayerPlacement(
                        state.id(),
                        placementByPlayer.get(state.id()),
                        participants));
            }
            return placements;
        }

        private void sendSnapshots(boolean openGui) {
            snapshotDirty = false;
            snapshotDirtyOpenGui = false;
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player == null) continue;
                sendSnapshot(player, state, this, openGui);
            }
            for (Map.Entry<UUID, SpectatorView> entry
                    : new ArrayList<>(SPECTATOR_VIEWS.entrySet())) {
                UUID playerId = entry.getKey();
                SpectatorView view = entry.getValue();
                // 终局与普通快照都只发送给当前这间 Match 的观战者。
                if (view.match() != this
                        || !VIEWERS.contains(playerId)) {
                    continue;
                }
                ServerPlayer player = player(playerId);
                if (player == null) continue;
                ArenaPlayerState target =
                        spectatorTarget(view.targetId());
                if (target == null) continue;
                sendSpectatorSnapshot(player, target, this, openGui);
            }
        }

        private void sendCombatStatsSnapshots() {
            for (ArenaPlayerState state : players.values()) {
                if (state.ai()) continue;
                ServerPlayer player = player(state.id());
                if (player == null) continue;
                List<ArenaCombatStatsPayload.UnitStats> units =
                        new ArrayList<>();
                for (ArenaPiece piece : state.pieces()) {
                    ArenaUnitPerformance performance =
                            unitPerformance.get(piece.id());
                    if (performance == null) continue;
                    ArenaUnitPerformance.Snapshot stats =
                            performance.snapshot(piece.id());
                    units.add(new ArenaCombatStatsPayload.UnitStats(
                            stats.pieceId(),
                            stats.physicalDealt(),
                            stats.magicDealt(),
                            stats.trueDealt(),
                            stats.healing(),
                            stats.shieldHealing(),
                            stats.physicalTaken(),
                            stats.magicTaken(),
                            stats.trueTaken()));
                }
                PacketDistributor.sendToPlayer(player,
                        new ArenaCombatStatsPayload(
                                true, roomId,
                                displayRound(), units));
            }
        }

        private void requestSnapshots(boolean openGui) {
            snapshotDirty = true;
            snapshotDirtyOpenGui |= openGui;
        }

    }

    private static RandomSource secureRandom() {
        // 房间号和对局 tick 都可被客户端推测，不能作为商店与匹配随机源。
        return RandomSource.create(SECURE_RANDOM.nextLong());
    }
}
