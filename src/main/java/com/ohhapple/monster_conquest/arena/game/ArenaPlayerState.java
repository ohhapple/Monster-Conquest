package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.config.ArenaConfig;
import com.ohhapple.monster_conquest.arena.trait.ArenaTrait;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitAnalyzer;
import com.ohhapple.monster_conquest.arena.trait.ArenaTraitCatalog;
import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;
import net.minecraft.util.RandomSource;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** 一名参战者的金币、等级、生命、商店和棋盘阵容。 */
public final class ArenaPlayerState {

    public enum BattleOutcome {
        NONE,
        WIN,
        LOSS,
        DRAW
    }

    public enum ShopPurchaseResult {
        SUCCESS,
        EMPTY_SLOT,
        STALE_SLOT,
        NO_BENCH_SPACE,
        INSUFFICIENT_GOLD
    }

    public static final int BOARD_COLUMNS = 9;
    public static final int BOARD_ROWS = 5;
    public static final int BOARD_SIZE = BOARD_COLUMNS * BOARD_ROWS;
    public static final int BENCH_SIZE = 9;
    public static final int EQUIPMENT_ROWS = 3;
    public static final int EQUIPMENT_SLOTS =
            BOARD_COLUMNS * EQUIPMENT_ROWS;
    public static final int MAX_LEVEL = 10;
    private static final int AI_BENCH_RESERVE = 4;
    private static final int[] AI_CENTER_OUT_COLUMNS =
            {4, 3, 5, 2, 6, 1, 7, 0, 8};

    private static final int[] XP_TO_NEXT = {
            0, 2, 6, 12, 20, 36, 56, 68, 78, 88, 0
    };

    private final UUID id;
    private final String name;
    private final boolean ai;
    private final ArenaCardPool cardPool;
    private final ArenaPiece[] board = new ArenaPiece[BOARD_SIZE];
    private final ArenaPiece[] bench = new ArenaPiece[BENCH_SIZE];
    private final Map<UUID, ArenaPiece> pieces = new LinkedHashMap<>();
    private final int[] shop = new int[5];
    private final List<UUID> combatEntities = new ArrayList<>();
    private final List<@Nullable ArenaEquipment> equipmentInventory =
            new ArrayList<>(EQUIPMENT_SLOTS);
    private final List<ArenaEquipment> pendingEquipmentRewards =
            new ArrayList<>();
    /** 拆卸器在库存中只占一个位置，实际数量由该计数表示。 */
    private int removerCount;

    private int health = 100;
    private int gold;
    private int level;
    private int xp;
    private boolean alive = true;
    private int lastDamage;
    private String opponentName = "";
    @Nullable private UUID opponentId;
    private int winStreak;
    private int lossStreak;
    private int caravanStacks;
    private boolean rosterUpgradeLocked;
    private boolean lastBattleStreakEligible;
    private long preparationSerial;
    private long lastEnderDragonGrantPreparation;
    private BattleOutcome lastBattleOutcome = BattleOutcome.NONE;
    private boolean cardsReturnedToPool;

    public ArenaPlayerState(UUID id, String name, boolean ai,
                            SecureRandom shopRandom) {
        this(id, name, ai, new ArenaCardPool(), shopRandom);
    }

    public ArenaPlayerState(UUID id, String name, boolean ai,
                            ArenaCardPool cardPool,
                            SecureRandom shopRandom) {
        this.id = id;
        this.name = name;
        this.ai = ai;
        this.cardPool = cardPool;
        this.gold = ArenaConfig.startingGold;
        this.level = 1;
        for (int i = 0; i < shop.length; i++) shop[i] = -1;
        for (int i = 0; i < EQUIPMENT_SLOTS; i++) {
            equipmentInventory.add(null);
        }
        rollShop(shopRandom);
        addEquipment(ArenaEquipment.REMOVER);
        int starter = cardPool.rollIndex(level, shopRandom);
        if (starter >= 0) cardPool.take(starter);
        ArenaPiece piece = new ArenaPiece(starter, 1);
        piece.setBoardSlot(20);
        board[20] = piece;
        pieces.put(piece.id(), piece);
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean ai() {
        return ai;
    }

    public int health() {
        return health;
    }

    public void damage(int amount) {
        if (amount <= 0 || !alive) return;
        health = Math.max(0, health - amount);
        if (health == 0) alive = false;
    }

    public boolean alive() {
        return alive;
    }

    public int lastDamage() {
        return lastDamage;
    }

    public void setLastDamage(int lastDamage) {
        this.lastDamage = Math.max(0, lastDamage);
    }

    public String opponentName() {
        return opponentName;
    }

    public void setOpponentName(String opponentName) {
        this.opponentName = opponentName == null ? "" : opponentName;
    }

    @Nullable
    public UUID opponentId() {
        return opponentId;
    }

    public void setOpponent(@Nullable UUID id, String name) {
        opponentId = id;
        setOpponentName(name);
    }

    public void clearOpponent() {
        opponentId = null;
        opponentName = "";
    }

    public int gold() {
        return gold;
    }

    public void addGold(int amount) {
        int normalCap = 999;
        int cap = Math.max(normalCap, gold);
        gold = Math.max(0, Math.min(cap, gold + amount));
    }

    public boolean spendGold(int amount) {
        if (amount < 0 || gold < amount) return false;
        gold -= amount;
        return true;
    }

    public int level() {
        return level;
    }

    public int xp() {
        return xp;
    }

    public int xpToNext() {
        return level >= MAX_LEVEL ? 0 : XP_TO_NEXT[level];
    }

    public boolean buyXp() {
        if (level >= MAX_LEVEL || !spendGold(ArenaConfig.xpCost)) return false;
        grantXp(ArenaConfig.xpAmount);
        return true;
    }

    public void grantXp(int amount) {
        if (amount <= 0 || level >= MAX_LEVEL) return;
        xp += amount;
        while (level < MAX_LEVEL && xp >= XP_TO_NEXT[level]) {
            xp -= XP_TO_NEXT[level];
            level++;
        }
        if (level >= MAX_LEVEL) xp = 0;
    }

    public int winStreak() {
        return winStreak;
    }

    public int lossStreak() {
        return lossStreak;
    }

    public int caravanStacks() {
        return caravanStacks;
    }

    public void setCaravanStacks(int stacks) {
        caravanStacks = Math.max(0, stacks);
    }

    public BattleOutcome lastBattleOutcome() {
        return lastBattleOutcome;
    }

    public void recordBattleOutcome(
            BattleOutcome outcome, boolean countStreak) {
        lastBattleOutcome = outcome;
        lastBattleStreakEligible = countStreak;
        if (!countStreak) return;
        switch (outcome) {
            case WIN -> {
                lossStreak = 0;
                winStreak++;
            }
            case LOSS -> {
                winStreak = 0;
                lossStreak++;
            }
            case DRAW -> {
                winStreak = 0;
                lossStreak = 0;
            }
            case NONE -> {
            }
        }
    }

    public int streakIncome() {
        if (!lastBattleStreakEligible) return 0;
        int streak = lastBattleOutcome == BattleOutcome.WIN
                ? winStreak
                : (lastBattleOutcome == BattleOutcome.LOSS ? lossStreak : 0);
        if (streak >= 6) return 3;
        if (streak >= 5) return 2;
        if (streak >= 2) return 1;
        return 0;
    }

    public void clearBattleOutcome() {
        lastBattleOutcome = BattleOutcome.NONE;
        lastBattleStreakEligible = false;
    }

    public int[] shop() {
        return shop.clone();
    }

    public void rollShop(SecureRandom random) {
        returnShopReservationsToPool();
        for (int i = 0; i < shop.length; i++) {
            int unitIndex = cardPool.rollIndex(level, random);
            shop[i] = unitIndex >= 0 && cardPool.take(unitIndex)
                    ? unitIndex : -1;
        }
    }

    public boolean refreshShop(SecureRandom random) {
        if (!spendGold(ArenaConfig.refreshCost)) return false;
        rollShop(random);
        return true;
    }

    public boolean buyShopSlot(int slot, SecureRandom random) {
        return purchaseShopSlot(slot, random) == ShopPurchaseResult.SUCCESS;
    }

    /**
     * 商店卡在刷新时已经从公共牌池预留。购买失败不能改变商店或金币状态，
     * 成功后才一次性落位并执行升星，避免客户端看到“卡没了但也没买到”。
     */
    public ShopPurchaseResult purchaseShopSlot(
            int slot, SecureRandom random) {
        return purchaseShopSlot(slot, -1, random);
    }

    public ShopPurchaseResult purchaseShopSlot(int slot, int expectedUnit,
                                               SecureRandom random) {
        if (slot < 0 || slot >= shop.length
                || shop[slot] < 0
                || shop[slot] >= ArenaUnitCatalog.ALL.size()) {
            return ShopPurchaseResult.EMPTY_SLOT;
        }
        if (expectedUnit >= 0 && shop[slot] != expectedUnit) {
            return ShopPurchaseResult.STALE_SLOT;
        }
        ArenaUnit unit = ArenaUnitCatalog.get(shop[slot]);
        if (gold < unit.cost()) {
            return ShopPurchaseResult.INSUFFICIENT_GOLD;
        }
        if (!canAcquirePiece(shop[slot], 1)) {
            return ShopPurchaseResult.NO_BENCH_SPACE;
        }
        if (!spendGold(unit.cost())) {
            return ShopPurchaseResult.INSUFFICIENT_GOLD;
        }

        placeAcquiredPiece(shop[slot], 1);
        shop[slot] = -1;
        mergeAll();
        return ShopPurchaseResult.SUCCESS;
    }

    public Collection<ArenaPiece> pieces() {
        return List.copyOf(pieces.values());
    }

    public boolean clearTemporaryEquipment() {
        boolean changed = false;
        for (ArenaPiece piece : pieces.values()) {
            changed |= piece.clearTemporaryEquipment();
        }
        return changed;
    }

    public List<@Nullable ArenaEquipment> equipmentInventory() {
        return Collections.unmodifiableList(equipmentInventory);
    }

    public int equipmentStackCount(int inventoryIndex) {
        if (inventoryIndex < 0
                || inventoryIndex >= equipmentInventory.size()) {
            return 0;
        }
        ArenaEquipment item = equipmentInventory.get(inventoryIndex);
        if (item == null) return 0;
        return item == ArenaEquipment.REMOVER ? removerCount : 1;
    }

    public boolean addEquipment(ArenaEquipment equipment) {
        if (equipment == ArenaEquipment.REMOVER
                && removerCount > 0) {
            removerCount++;
            return true;
        }
        int slot = firstEmptyEquipmentSlot();
        if (slot < 0) return false;
        if (equipment != ArenaEquipment.REMOVER) {
            equipmentInventory.set(slot, equipment);
            return true;
        }
        equipmentInventory.set(slot, ArenaEquipment.REMOVER);
        removerCount = 1;
        return true;
    }

    public void grantEquipmentReward(ArenaEquipment equipment) {
        if (equipment == null) return;
        if (!addEquipment(equipment)) {
            pendingEquipmentRewards.add(equipment);
        }
    }

    public void deliverPendingEquipmentRewards() {
        pendingEquipmentRewards.removeIf(this::addEquipment);
    }

    public ArenaPiece piece(UUID pieceId) {
        return pieces.get(pieceId);
    }

    public ArenaPiece boardPiece(int slot) {
        return slot >= 0 && slot < board.length ? board[slot] : null;
    }

    public ArenaPiece benchPiece(int slot) {
        return slot >= 0 && slot < bench.length ? bench[slot] : null;
    }

    public List<ArenaPiece> benchPieces() {
        List<ArenaPiece> result = new ArrayList<>();
        for (ArenaPiece piece : bench) if (piece != null) result.add(piece);
        return result;
    }

    public int boardCount() {
        int count = 0;
        for (ArenaPiece piece : board) {
            if (piece != null && !piece.unit().special()) count++;
        }
        return count;
    }

    public boolean canDeployAnother() {
        return boardCount() < Math.min(level, BOARD_SIZE);
    }

    public boolean movePiece(UUID pieceId, boolean toBoard, int targetSlot) {
        ArenaPiece piece = pieces.get(pieceId);
        if (piece == null) return false;
        ArenaPiece[] destination = toBoard ? board : bench;
        if (targetSlot < 0 || targetSlot >= destination.length) return false;

        if (toBoard && !piece.onBoard()
                && destination[targetSlot] == null
                && !piece.unit().special()
                && !canDeployAnother()) {
            return false;
        }

        ArenaPiece occupying = destination[targetSlot];
        if (occupying == piece) return true;

        int oldBoardSlot = piece.boardSlot();
        int oldBenchSlot = piece.benchSlot();
        removeAt(piece);
        if (occupying != null) removeAt(occupying);

        if (occupying != null) {
            if (oldBoardSlot >= 0) {
                occupying.setBoardSlot(oldBoardSlot);
                board[oldBoardSlot] = occupying;
            } else if (oldBenchSlot >= 0) {
                occupying.setBenchSlot(oldBenchSlot);
                bench[oldBenchSlot] = occupying;
            }
        }

        if (toBoard) {
            piece.setBoardSlot(targetSlot);
            board[targetSlot] = piece;
        } else {
            piece.setBenchSlot(targetSlot);
            bench[targetSlot] = piece;
        }
        return true;
    }

    public boolean sellPiece(UUID pieceId, boolean allowBoard) {
        ArenaPiece piece = pieces.get(pieceId);
        if (piece == null || (piece.onBoard() && !allowBoard)) {
            return false;
        }
        int multiplier = switch (piece.star()) {
            case 2 -> 3;
            case 3 -> 9;
            default -> 1;
        };
        removeAt(piece);
        pieces.remove(pieceId);
        for (ArenaEquipment item : piece.removeEquipment()) {
            grantEquipmentReward(item);
        }
        cardPool.returnCards(piece.unitIndex(), piece.poolCopies());
        addGold(piece.unit().cost() * multiplier);
        return true;
    }

    public void returnAllCardsToPool() {
        if (cardsReturnedToPool) return;
        cardsReturnedToPool = true;
        returnShopReservationsToPool();
        for (ArenaPiece piece : pieces.values()) {
            int copies = piece.poolCopies();
            piece.setPoolCopies(0);
            cardPool.returnCards(piece.unitIndex(), copies);
        }
    }

    private void returnShopReservationsToPool() {
        for (int i = 0; i < shop.length; i++) {
            if (shop[i] >= 0) cardPool.returnCards(shop[i], 1);
            shop[i] = -1;
        }
    }

    public boolean equipFromInventory(int inventoryIndex,
                                      int expectedEquipmentId,
                                      UUID pieceId) {
        ArenaEquipment expected = ArenaEquipment.byId(expectedEquipmentId);
        int resolvedIndex = resolveInventoryIndex(inventoryIndex, expected);
        if (resolvedIndex < 0) return false;
        ArenaEquipment item = equipmentInventory.get(resolvedIndex);
        ArenaPiece piece = pieces.get(pieceId);
        if (item == null || item.tool() || piece == null
                || !piece.canAccept(item)) {
            return false;
        }
        if (!piece.equip(item)) return false;
        equipmentInventory.set(resolvedIndex, null);
        return true;
    }

    public boolean combineEquipment(int firstIndex, int secondIndex) {
        if (firstIndex < 0 || secondIndex < 0
                || firstIndex >= equipmentInventory.size()
                || secondIndex >= equipmentInventory.size()
                || firstIndex == secondIndex) {
            return false;
        }
        ArenaEquipment first = equipmentInventory.get(firstIndex);
        ArenaEquipment second = equipmentInventory.get(secondIndex);
        if (first == null || second == null) return false;
        ArenaEquipment combined = ArenaEquipment.combine(first, second);
        if (combined == null) return false;
        // 仓库是固定槽位：两次点击只消费组件，成品留在第二件组件的位置。
        equipmentInventory.set(firstIndex, null);
        equipmentInventory.set(secondIndex, combined);
        return true;
    }

    public boolean moveEquipment(int sourceIndex, int targetIndex) {
        if (sourceIndex < 0 || targetIndex < 0
                || sourceIndex >= equipmentInventory.size()
                || targetIndex >= equipmentInventory.size()
                || sourceIndex == targetIndex) {
            return false;
        }
        ArenaEquipment item = equipmentInventory.get(sourceIndex);
        if (item == null || equipmentInventory.get(targetIndex) != null) {
            return false;
        }
        equipmentInventory.set(sourceIndex, null);
        equipmentInventory.set(targetIndex, item);
        return true;
    }

    public boolean swapEquipment(int firstIndex, int secondIndex) {
        if (firstIndex < 0 || secondIndex < 0
                || firstIndex >= equipmentInventory.size()
                || secondIndex >= equipmentInventory.size()
                || firstIndex == secondIndex) {
            return false;
        }
        ArenaEquipment first = equipmentInventory.get(firstIndex);
        ArenaEquipment second = equipmentInventory.get(secondIndex);
        if (first == null || second == null
                || ArenaEquipment.combine(first, second) != null) {
            return false;
        }
        equipmentInventory.set(firstIndex, second);
        equipmentInventory.set(secondIndex, first);
        return true;
    }

    public boolean disassemble(UUID pieceId, int removerIndex,
                               int expectedEquipmentId) {
        ArenaEquipment expected = ArenaEquipment.byId(expectedEquipmentId);
        if (expected != ArenaEquipment.REMOVER) return false;
        int resolvedIndex = resolveInventoryIndex(
                removerIndex, ArenaEquipment.REMOVER);
        if (resolvedIndex < 0) return false;
        ArenaPiece piece = pieces.get(pieceId);
        if (piece == null || piece.equipment().isEmpty()) return false;
        removerCount = Math.max(0, removerCount - 1);
        if (removerCount == 0) {
            equipmentInventory.set(resolvedIndex, null);
        }
        for (ArenaEquipment item : piece.removeEquipment()) {
            grantEquipmentReward(item);
        }
        return true;
    }

    /**
     * 复制器优先领取公共牌池仍存在的同卡；牌池耗尽时才生成不参与返还的复制卡。
     * 复制完成后复用公共三合一流程。
     */
    public boolean duplicatePiece(
            UUID sourcePieceId, int inventoryIndex,
            int expectedEquipmentId) {
        ArenaEquipment item =
                ArenaEquipment.byId(expectedEquipmentId);
        if (!item.duplicator()) return false;
        int resolvedIndex = resolveInventoryIndex(
                inventoryIndex, item);
        if (resolvedIndex < 0) return false;
        ArenaPiece source = pieces.get(sourcePieceId);
        if (source == null) return false;
        if (item == ArenaEquipment.LESSER_DUPLICATOR
                && source.unit().cost() > 3) {
            return false;
        }
        if (!canAcquirePiece(source.unitIndex(), 1)) {
            return false;
        }
        boolean fromPool = cardPool.take(source.unitIndex());
        ArenaPiece copy = placeAcquiredPiece(
                source.unitIndex(), 1);
        copy.setPoolCopies(fromPool ? 1 : 0);
        equipmentInventory.set(resolvedIndex, null);
        mergeAll();
        return true;
    }

    private int firstEmptyEquipmentSlot() {
        for (int i = 0; i < equipmentInventory.size(); i++) {
            if (equipmentInventory.get(i) == null) return i;
        }
        return -1;
    }

    private int resolveInventoryIndex(int preferredIndex,
                                      ArenaEquipment expected) {
        if (preferredIndex >= 0
                && preferredIndex < equipmentInventory.size()
                && equipmentInventory.get(preferredIndex) == expected) {
            return preferredIndex;
        }
        for (int i = 0; i < equipmentInventory.size(); i++) {
            if (equipmentInventory.get(i) == expected) return i;
        }
        return -1;
    }

    /**
     * AI 与玩家共用同一套经济入口：经验、刷新、购买、合成和上阵都通过公开状态方法完成。
     * 这里只决定如何消费，不直接生成弈子或修改等级。
     */
    public void runAiPreparation(
            SecureRandom shopRandom, RandomSource random, int stage) {
        int preparationStage = Math.max(1, stage);
        deployBestBenchUnits();

        int goldFloor = aiSpendingFloor(preparationStage);
        int levelTarget = aiLevelTarget(preparationStage);
        int maxXpPurchases = switch (preparationStage) {
            case 1 -> 1;
            case 2 -> 2;
            default -> 3;
        };
        for (int i = 0; i < maxXpPurchases
                && level < MAX_LEVEL
                && level < levelTarget
                && (health >= 35 || level <= levelTarget - 2)
                && gold() >= ArenaConfig.xpCost + goldFloor; i++) {
            if (!buyXp()) break;
            deployBestBenchUnits();
        }

        int refreshes = 0;
        int maxRefreshes = aiMaxRerolls(preparationStage);
        while (true) {
            int shopSlot = findBestAiShopSlot(random, preparationStage);
            if (shopSlot < 0) {
                if (refreshes < maxRefreshes
                        && shouldAiReroll(preparationStage, random)
                        && gold() >= ArenaConfig.refreshCost + goldFloor
                        && refreshShop(shopRandom)) {
                    refreshes++;
                    continue;
                }
                break;
            }
            int unitIndex = shop[shopSlot];
            if (!canAcquirePiece(unitIndex, 1)
                    && !sellAiBenchForShop(shop[shopSlot])) {
                break;
            }
            if (buyShopSlot(shopSlot, shopRandom)) {
                deployBestBenchUnits();
                optimizeAiBoard();
                continue;
            }
            break;
        }
        deployBestBenchUnits();
        optimizeAiBoard();
        arrangeAiBoardFormation();
        pruneAiBench();
        manageAiDuplicators(random);
        manageAiEquipment();
    }

    /**
     * AI 在阶段推进时逐步追求更高利息，但低生命或早期不会为了存钱放弃阵容。
     */
    private int aiSpendingFloor(int stage) {
        int floor = switch (Math.max(1, stage)) {
            case 1 -> 0;
            case 2 -> 10;
            case 3 -> 20;
            default -> Math.min(50, stage * 10);
        };
        if (health < 60) floor = Math.min(floor, 10);
        if (health < 35) floor = 0;
        return floor;
    }

    private int aiLevelTarget(int stage) {
        return switch (Math.max(1, stage)) {
            case 1 -> 3;
            case 2 -> 5;
            case 3 -> 6;
            case 4 -> 7;
            case 5 -> 8;
            case 6 -> 9;
            default -> 10;
        };
    }

    private int aiMaxRerolls(int stage) {
        if (stage <= 1) return 0;
        if (stage <= 2) return 1;
        if (stage <= 4) return 2;
        return 3;
    }

    private boolean shouldAiReroll(
            int stage, RandomSource random) {
        if (health < 35) return true;
        return switch (Math.max(1, stage)) {
            case 1, 2 -> random.nextFloat() < 0.20f;
            case 3, 4 -> random.nextFloat() < 0.45f;
            default -> random.nextFloat() < 0.65f;
        };
    }

    private void manageAiDuplicators(RandomSource random) {
        boolean duplicated = true;
        while (duplicated) {
            duplicated = false;
            for (int index = 0;
                 index < equipmentInventory.size(); index++) {
                ArenaEquipment item = equipmentInventory.get(index);
                if (item == null || !item.duplicator()) continue;
                ArenaPiece target = bestAiDuplicationTarget(item);
                if (target == null) continue;
                if (duplicatePiece(target.id(), index,
                        item.ordinal())) {
                    duplicated = true;
                    break;
                }
            }
        }
    }

    @Nullable
    private ArenaPiece bestAiDuplicationTarget(
            ArenaEquipment duplicator) {
        ArenaPiece best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ArenaPiece piece : pieces.values()) {
            if (duplicator == ArenaEquipment.LESSER_DUPLICATOR
                    && piece.unit().cost() > 3) {
                continue;
            }
            int oneStarCopies = sameStarCopies(
                    piece.unitIndex(), 1);
            int pairBonus = oneStarCopies >= 2
                    ? 2_000_000
                    : (oneStarCopies == 1 ? 150_000 : 0);
            int score = pairBonus
                    + piece.unit().cost() * 100_000
                    + piece.star() * 20_000
                    + (piece.onBoard() ? 10_000 : 0);
            if (score > bestScore) {
                bestScore = score;
                best = piece;
            }
        }
        return best;
    }

    private void manageAiEquipment() {
        combineAiComponents();
        for (int priority = 0; priority <= 2; priority++) {
            boolean equipped = true;
            while (equipped) {
                equipped = false;
                for (int index = 0;
                     index < equipmentInventory.size(); index++) {
                    ArenaEquipment item = equipmentInventory.get(index);
                    if (item == null) continue;
                    if (aiEquipmentPriority(item) != priority) continue;
                    ArenaPiece carrier = bestAiEquipmentCarrier(item);
                    if (carrier == null) continue;
                    if (equipFromInventory(index, item.ordinal(),
                            carrier.id())) {
                        equipped = true;
                        break;
                    }
                }
            }
        }
    }

    private void combineAiComponents() {
        boolean combined = true;
        while (combined) {
            combined = false;
            for (int first = 0; first < equipmentInventory.size(); first++) {
                ArenaEquipment firstItem =
                        equipmentInventory.get(first);
                if (firstItem == null || !firstItem.component()) continue;
                for (int second = first + 1;
                     second < equipmentInventory.size(); second++) {
                    ArenaEquipment secondItem =
                            equipmentInventory.get(second);
                    if (secondItem != null
                            && ArenaEquipment.combine(
                            firstItem, secondItem) != null) {
                        combined = combineEquipment(first, second);
                        break;
                    }
                }
                if (combined) break;
            }
        }
    }

    private int aiEquipmentPriority(ArenaEquipment item) {
        if (item.tool()) return -1;
        if (item.effect() == ArenaEquipment.Effect.SHIELD) return 0;
        return item.component() ? 2 : 1;
    }

    @Nullable
    private ArenaPiece bestAiEquipmentCarrier(ArenaEquipment item) {
        ArenaPiece best = null;
        int bestScore = Integer.MIN_VALUE;
        for (ArenaPiece piece : pieces.values()) {
            if (!piece.canEquip()) continue;
            if (item.effect() == ArenaEquipment.Effect.SHIELD
                    && !piece.canEquipShield()) {
                continue;
            }
            int score = (piece.onBoard() ? 10_000 : 0)
                    + piece.star() * 1_000
                    + piece.unit().cost() * 100;
            if (score > bestScore) {
                bestScore = score;
                best = piece;
            }
        }
        return best;
    }

    private int findBestAiShopSlot(
            RandomSource random, int stage) {
        int bestSlot = -1;
        int bestScore = -1;
        int goldFloor = aiSpendingFloor(stage);
        for (int slot = 0; slot < shop.length; slot++) {
            if (shop[slot] < 0) continue;
            ArenaUnit unit = ArenaUnitCatalog.get(shop[slot]);
            if (gold() < unit.cost()) continue;

            int mergeCopies = sameStarCopies(shop[slot], 1);
            boolean urgent = mergeCopies >= 2
                    || (health < 35 && unit.cost() <= 3);
            if (!urgent && gold() - unit.cost() < goldFloor) {
                continue;
            }
            int score = aiShopCandidatePower(shop[slot])
                    * 100
                    + random.nextInt(8);
            if (score > bestScore) {
                bestScore = score;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int aiPiecePower(ArenaPiece piece) {
        ArenaUnit unit = piece.unit();
        int sameStarCopies = sameStarCopies(
                piece.unitIndex(), piece.star());
        int pairBonus = sameStarCopies >= 2 ? 250_000 : 0;
        return piece.star() * piece.star() * 1_000_000
                + unit.cost() * 100_000
                + (int) (unit.baseHealth() / 10.0)
                + (int) (unit.baseDamage() * 20.0)
                + (int) (unit.armor() * 400.0)
                + (int) (unit.magicResistance() * 200.0)
                + (int) (unit.attackSpeed() * 10_000.0)
                + pairBonus
                + piece.equipment().size() * 5_000;
    }

    private int aiShopCandidatePower(int unitIndex) {
        ArenaUnit unit = ArenaUnitCatalog.get(unitIndex);
        int mergeCopies = sameStarCopies(unitIndex, 1);
        int mergeBonus = switch (mergeCopies) {
            case 2 -> 900_000;
            case 1 -> 20_000;
            default -> 0;
        };
        int ownedThreeStarPenalty =
                highestOwnedStar(unitIndex) >= 3
                        ? -700_000 : 0;
        return 1_000_000
                + unit.cost() * 100_000
                + (int) (unit.baseHealth() / 10.0)
                + (int) (unit.baseDamage() * 20.0)
                + (int) (unit.armor() * 400.0)
                + (int) (unit.magicResistance() * 200.0)
                + (int) (unit.attackSpeed() * 10_000.0)
                + mergeBonus
                + aiTraitCandidateBonus(unitIndex)
                + ownedThreeStarPenalty;
    }

    private int highestOwnedStar(int unitIndex) {
        int highest = 0;
        for (ArenaPiece piece : pieces.values()) {
            if (piece.unitIndex() == unitIndex) {
                highest = Math.max(highest, piece.star());
            }
        }
        return highest;
    }

    private int sameStarCopies(int unitIndex, int star) {
        int copies = 0;
        for (ArenaPiece piece : pieces.values()) {
            if (piece.unitIndex() == unitIndex && piece.star() == star) {
                copies++;
            }
        }
        return copies;
    }

    private boolean sellAiBenchForShop(int unitIndex) {
        ArenaPiece weakest = weakestAiBenchPiece(true);
        if (weakest == null) weakest = weakestAiBenchPiece(false);
        if (weakest == null
                || aiShopCandidatePower(unitIndex)
                <= aiPiecePower(weakest)) {
            return false;
        }
        return sellPiece(weakest.id(), false);
    }

    @Nullable
    private ArenaPiece weakestAiBenchPiece(boolean avoidPairs) {
        ArenaPiece weakest = null;
        int weakestPower = Integer.MAX_VALUE;
        for (ArenaPiece piece : benchPieces()) {
            if (avoidPairs && sameStarCopies(
                    piece.unitIndex(), piece.star()) >= 2) {
                continue;
            }
            int power = aiPiecePower(piece);
            if (power < weakestPower) {
                weakestPower = power;
                weakest = piece;
            }
        }
        return weakest;
    }

    private void optimizeAiBoard() {
        while (true) {
            List<ArenaPiece> currentBoard = deployedPieces();
            int currentScore = aiBoardStrength(currentBoard);
            ArenaPiece replaceBoard = null;
            ArenaPiece replaceBench = null;
            int bestScore = currentScore + 20_000;
            for (ArenaPiece boardPiece : currentBoard) {
                if (boardPiece.unit().special()) continue;
                for (ArenaPiece benchPiece : benchPieces()) {
                    if (benchPiece.unit().special()) continue;
                    List<ArenaPiece> projected =
                            new ArrayList<>(currentBoard);
                    projected.remove(boardPiece);
                    projected.add(benchPiece);
                    int score = aiBoardStrength(projected);
                    if (score > bestScore) {
                        bestScore = score;
                        replaceBoard = boardPiece;
                        replaceBench = benchPiece;
                    }
                }
            }
            if (replaceBoard == null || replaceBench == null
                    || !movePiece(replaceBench.id(), true,
                    replaceBoard.boardSlot())) {
                return;
            }
        }
    }

    private int aiBoardStrength(List<ArenaPiece> roster) {
        int score = 0;
        int frontline = 0;
        int backline = 0;
        for (ArenaPiece piece : roster) {
            score += aiPiecePower(piece);
            boolean ranged = piece.unit().form().isRanged();
            if (ranged) {
                backline++;
            }
            if (!ranged || piece.unit().form().isDual()) {
                frontline++;
            }
        }
        score += aiTraitScore(roster);
        if (roster.size() >= 4 && frontline == 0) score -= 80_000;
        if (roster.size() >= 6 && backline == 0) score -= 50_000;
        score += Math.min(3, Math.min(frontline, backline)) * 6_000;
        return score;
    }

    private int aiTraitScore(List<ArenaPiece> roster) {
        if (roster.isEmpty()) return 0;
        List<Integer> unitIndices = new ArrayList<>(roster.size());
        for (ArenaPiece piece : roster) {
            unitIndices.add(piece.unitIndex());
        }
        ArenaTraitAnalyzer.State state =
                ArenaTraitAnalyzer.analyze(unitIndices);
        int score = 0;
        for (ArenaTrait trait : ArenaTrait.values()) {
            int count = state.count(trait);
            if (count <= 0) continue;
            int tier = state.tier(trait);
            score += count * 2_000 + tier * 20_000;
            if (trait.category() == ArenaTrait.Category.ECONOMY) {
                score += tier * 4_000;
            }
        }
        return score;
    }

    private int aiTraitCandidateBonus(int unitIndex) {
        List<ArenaPiece> boardNow = deployedPieces();
        for (ArenaPiece piece : boardNow) {
            if (piece.unitIndex() == unitIndex) return 0;
        }
        List<Integer> unitIndices = new ArrayList<>(boardNow.size());
        for (ArenaPiece piece : boardNow) {
            unitIndices.add(piece.unitIndex());
        }
        ArenaTraitAnalyzer.State state =
                ArenaTraitAnalyzer.analyze(unitIndices);
        int bonus = 0;
        for (ArenaTrait trait :
                ArenaTraitCatalog.traitsFor(unitIndex)) {
            int currentCount = state.count(trait);
            int currentTier = state.tier(trait);
            int nextTier = trait.tierFor(currentCount + 1);
            if (nextTier > currentTier) {
                bonus += nextTier * 120_000;
            } else if (currentCount > 0) {
                bonus += 5_000;
            }
        }
        return bonus;
    }

    private void arrangeAiBoardFormation() {
        List<ArenaPiece> deployed = deployedPieces();
        deployed.sort(Comparator.comparingInt(this::aiFormationPriority));
        for (ArenaPiece piece : deployed) {
            int preferred = preferredAiFormationSlot(piece);
            if (preferred >= 0 && preferred != piece.boardSlot()) {
                movePiece(piece.id(), true, preferred);
            }
        }
    }

    private int aiFormationPriority(ArenaPiece piece) {
        if (piece.unit().form().isRanged()) return 0;
        if (piece.unit().form().isDual()) return 1;
        return 2;
    }

    /**
     * GUI 第 0 行是靠近对手的前排，第 4 行是己方后排。
     */
    private int preferredAiFormationSlot(ArenaPiece piece) {
        int[] rows;
        if (piece.unit().form().isRanged()) {
            rows = new int[]{4, 3, 2, 1, 0};
        } else if (piece.unit().form().isDual()) {
            rows = new int[]{2, 1, 3, 0, 4};
        } else {
            rows = new int[]{0, 1, 2, 3, 4};
        }
        for (int row : rows) {
            for (int column : AI_CENTER_OUT_COLUMNS) {
                int slot = row * BOARD_COLUMNS + column;
                if (board[slot] == null || board[slot] == piece) {
                    return slot;
                }
            }
        }
        for (int slot = 0; slot < board.length; slot++) {
            if (board[slot] == null || board[slot] == piece) return slot;
        }
        return -1;
    }

    private void pruneAiBench() {
        while (benchPieces().size() > AI_BENCH_RESERVE) {
            ArenaPiece weakest = weakestAiBenchPiece(true);
            if (weakest == null) weakest = weakestAiBenchPiece(false);
            if (weakest == null
                    || !sellPiece(weakest.id(), false)) {
                return;
            }
        }
    }

    private void deployBestBenchUnits() {
        while (true) {
            ArenaPiece candidate = null;
            int bestScore = Integer.MIN_VALUE;
            List<ArenaPiece> currentBoard = deployedPieces();
            for (ArenaPiece piece : benchPieces()) {
                if (!piece.unit().special() && !canDeployAnother()) {
                    continue;
                }
                List<ArenaPiece> projected =
                        new ArrayList<>(currentBoard);
                projected.add(piece);
                int score = aiBoardStrength(projected);
                if (score > bestScore) {
                    bestScore = score;
                    candidate = piece;
                }
            }
            int free = firstEmptyBoard();
            if (candidate == null || free < 0
                    || !movePiece(candidate.id(), true, free)) {
                break;
            }
        }
    }

    public List<ArenaPiece> deployedPieces() {
        List<ArenaPiece> result = new ArrayList<>();
        for (ArenaPiece piece : board) if (piece != null) result.add(piece);
        result.sort(Comparator.comparing(ArenaPiece::id));
        return result;
    }

    public int highestStar() {
        int highest = 0;
        for (ArenaPiece piece : pieces.values()) {
            highest = Math.max(highest, piece.star());
        }
        return highest;
    }

    public List<ArenaPiece> cloneForShadow() {
        List<ArenaPiece> result = new ArrayList<>();
        for (ArenaPiece piece : deployedPieces()) {
            result.add(piece.copyForShadow());
        }
        return result;
    }

    public void beginPreparation(long serial) {
        preparationSerial = Math.max(preparationSerial, serial);
    }

    public void lockRosterUpgrades() {
        rosterUpgradeLocked = true;
    }

    public void resolveDeferredRosterUpgrades() {
        rosterUpgradeLocked = false;
        mergeAll();
    }

    /**
     * 亡灵十人口每个准备阶段从共享牌库领取一张末影龙。
     * 备战席没有空位时不提前取牌，后续有空位时仍可在同一阶段领取。
     */
    public boolean grantEnderDragonReward() {
        if (preparationSerial <= 0L
                || lastEnderDragonGrantPreparation == preparationSerial) {
            return false;
        }
        List<Integer> roster = deployedPieces().stream()
                .map(ArenaPiece::unitIndex)
                .toList();
        if (!ArenaTraitAnalyzer.analyze(roster)
                .active(ArenaTrait.UNDEAD, 10)) {
            return false;
        }
        int dragonIndex = ArenaUnitCatalog.enderDragonIndex();
        if (dragonIndex < 0
                || !canAcquirePiece(dragonIndex, 1)
                || !cardPool.take(dragonIndex)) {
            return false;
        }
        placeAcquiredPiece(dragonIndex, 1);
        lastEnderDragonGrantPreparation = preparationSerial;
        mergeAll();
        return true;
    }

    public List<UUID> combatEntities() {
        return combatEntities;
    }

    public void clearCombatEntities() {
        combatEntities.clear();
    }

    private void mergeAll() {
        boolean merged = true;
        while (merged) {
            merged = false;
            for (int star = 1; star <= 2; star++) {
                Map<Integer, List<ArenaPiece>> groups = new HashMap<>();
                for (ArenaPiece piece : pieces.values()) {
                    if (piece.star() == star) {
                        groups.computeIfAbsent(piece.unitIndex(), ignored -> new ArrayList<>())
                                .add(piece);
                    }
                }
                for (Map.Entry<Integer, List<ArenaPiece>> entry : groups.entrySet()) {
                    if (entry.getValue().size() < 3) continue;
                    List<ArenaPiece> group = entry.getValue();
                    if (rosterUpgradeLocked
                            && group.stream().anyMatch(
                            ArenaPiece::onBoard)) {
                        continue;
                    }
                    group.sort(Comparator.comparing(piece -> !piece.onBoard()));
                    ArenaPiece kept = group.get(0);
                    int keptBoard = kept.boardSlot();
                    int keptBench = kept.benchSlot();
                    int poolCopies = 0;
                    for (int i = 0; i < 3; i++) {
                        ArenaPiece removed = group.get(i);
                        if (i > 0) {
                            for (ArenaEquipment item
                                    : removed.removeEquipment()) {
                                grantEquipmentReward(item);
                            }
                        }
                        poolCopies += removed.poolCopies();
                        removeAt(removed);
                        pieces.remove(removed.id());
                    }
                    kept.setStar(star + 1);
                    kept.setPoolCopies(poolCopies);
                    if (keptBoard >= 0) {
                        kept.setBoardSlot(keptBoard);
                        board[keptBoard] = kept;
                    } else if (keptBench >= 0) {
                        kept.setBenchSlot(keptBench);
                        bench[keptBench] = kept;
                    }
                    pieces.put(kept.id(), kept);
                    merged = true;
                    break;
                }
                if (merged) break;
            }
        }
    }

    private void removeAt(ArenaPiece piece) {
        if (piece.onBoard() && board[piece.boardSlot()] == piece) {
            board[piece.boardSlot()] = null;
        }
        if (piece.onBench() && bench[piece.benchSlot()] == piece) {
            bench[piece.benchSlot()] = null;
        }
    }

    private int firstEmptyBench() {
        for (int i = 0; i < bench.length; i++) if (bench[i] == null) return i;
        return -1;
    }

    /**
     * 满备战席仍可获得的条件是新增弈子无需落位就能立即完成三合一。
     * 棋盘和备战席上的同名同星弈子都属于同一个合成组；战斗中锁定棋盘升星时，
     * 只有不涉及棋盘弈子的备战席合成可以立即完成。
     */
    private boolean canCompleteImmediateMerge(int unitIndex, int star) {
        int copies = 0;
        boolean hasBoardCopy = false;
        for (ArenaPiece piece : pieces.values()) {
            if (piece.unitIndex() != unitIndex
                    || piece.star() != star) {
                continue;
            }
            copies++;
            hasBoardCopy |= piece.onBoard();
        }
        if (copies < 2) return false;
        return !rosterUpgradeLocked || !hasBoardCopy;
    }

    /**
     * 弈子发放入口按调用顺序逐个检查当前这一项，不从后续奖励中跳项补空位。
     */
    private boolean canAcquirePiece(int unitIndex, int star) {
        return firstEmptyBench() >= 0
                || canCompleteImmediateMerge(unitIndex, star);
    }

    private ArenaPiece placeAcquiredPiece(int unitIndex, int star) {
        ArenaPiece piece = new ArenaPiece(unitIndex, star);
        int benchSlot = firstEmptyBench();
        if (benchSlot >= 0) {
            piece.setBenchSlot(benchSlot);
            bench[benchSlot] = piece;
        }
        pieces.put(piece.id(), piece);
        return piece;
    }

    private int firstEmptyBoard() {
        for (int i = 0; i < board.length; i++) if (board[i] == null) return i;
        return -1;
    }
}
