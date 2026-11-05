package com.ohhapple.monster_conquest.arena.game;

import com.ohhapple.monster_conquest.arena.unit.ArenaUnit;

import java.util.UUID;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 玩家拥有的一枚弈子；棋盘和备战席至少有一个位置字段有效。 */
public final class ArenaPiece {

    private final UUID id;
    private final int unitIndex;
    private int star;
    private int poolCopies;
    private int boardSlot = -1;
    private int benchSlot = -1;
    private final List<EquipmentAttachment> equipment = new ArrayList<>();
    private long nextEquipmentOrder;

    public ArenaPiece(int unitIndex, int star) {
        this(UUID.randomUUID(), unitIndex, star);
    }

    public ArenaPiece(UUID id, int unitIndex, int star) {
        this.id = id;
        this.unitIndex = unitIndex;
        this.star = Math.max(1, Math.min(3, star));
        this.poolCopies = baseCopiesForStar(this.star);
    }

    public UUID id() {
        return id;
    }

    public int unitIndex() {
        return unitIndex;
    }

    public ArenaUnit unit() {
        return ArenaUnitCatalog.get(unitIndex);
    }

    public int star() {
        return star;
    }

    public void setStar(int star) {
        this.star = Math.max(1, Math.min(3, star));
    }

    /**
     * 当前弈子中实际来自公共牌池的一星卡数量。复制器生成的卡不计入，
     * 但会参与升星并在合成后把来源数量传给保留的弈子。
     */
    public int poolCopies() {
        return poolCopies;
    }

    void setPoolCopies(int copies) {
        poolCopies = Math.max(0, copies);
    }

    public int boardSlot() {
        return boardSlot;
    }

    public int benchSlot() {
        return benchSlot;
    }

    public boolean onBoard() {
        return boardSlot >= 0;
    }

    public boolean onBench() {
        return benchSlot >= 0;
    }

    public void setBoardSlot(int slot) {
        boardSlot = slot;
        benchSlot = -1;
    }

    public void setBenchSlot(int slot) {
        benchSlot = slot;
        boardSlot = -1;
    }

    public List<ArenaEquipment> equipment() {
        return equipment.stream()
                .sorted(Comparator.comparingLong(
                        EquipmentAttachment::equippedOrder))
                .map(EquipmentAttachment::item)
                .toList();
    }

    /**
     * 影子只用于生成当前战斗实体，不进入玩家持有列表或公共牌池。
     * 保留装备附件原样复制，避免影子因为缺少装备而弱于本体。
     */
    public ArenaPiece copyForShadow() {
        ArenaPiece copy = new ArenaPiece(unitIndex, star);
        if (boardSlot >= 0) copy.setBoardSlot(boardSlot);
        copy.equipment.addAll(equipment);
        copy.nextEquipmentOrder = nextEquipmentOrder;
        copy.poolCopies = 0;
        return copy;
    }

    public boolean canEquip() {
        return equipment.size() < 3;
    }

    public boolean canAccept(ArenaEquipment item) {
        if (item == null || item.tool()) return false;
        if (item.effect() == ArenaEquipment.Effect.SHIELD
                && !canEquipShield()) {
            return false;
        }
        return canEquip() || findCombination(item) >= 0;
    }

    public boolean hasShieldEquipped() {
        return equipment.stream().anyMatch(attachment ->
                attachment.item().effect()
                        == ArenaEquipment.Effect.SHIELD);
    }

    public boolean canEquipShield() {
        return !unit().builtInShield() && !hasShieldEquipped();
    }

    public boolean equip(ArenaEquipment item) {
        if (item == null || item.tool()) return false;
        if (item.effect() == ArenaEquipment.Effect.SHIELD
                && !canEquipShield()) {
            return false;
        }
        int combinationIndex = findCombination(item);
        if (combinationIndex >= 0) {
            ArenaEquipment combined = ArenaEquipment.combine(
                    equipment.get(combinationIndex).item(), item);
            if (combined != null) {
                EquipmentAttachment existing =
                        equipment.remove(combinationIndex);
                equipment.add(new EquipmentAttachment(
                        combined, existing.equippedOrder(), false));
                return true;
            }
        }
        if (!canEquip()) {
            return false;
        }
        equipment.add(new EquipmentAttachment(
                item, nextEquipmentOrder++, false));
        return true;
    }

    /**
     * 临时成装参与本场属性、特效与快照，但不会进入永久装备历史。
     * 每次授予都会追加独立附件，直到弈子的 3 个装备槽全部占用。
     */
    public boolean equipTemporary(ArenaEquipment item) {
        if (item == null || item.component()
                || item.tool()
                || item.effect() == ArenaEquipment.Effect.SHIELD
                || !canEquip()) {
            return false;
        }
        equipment.add(new EquipmentAttachment(
                item, nextEquipmentOrder++, true));
        return true;
    }

    public boolean clearTemporaryEquipment() {
        return equipment.removeIf(EquipmentAttachment::temporary);
    }

    public List<ArenaEquipment> removeEquipment() {
        List<ArenaEquipment> removed = equipment.stream()
                .filter(attachment -> !attachment.temporary())
                .sorted(Comparator.comparingLong(
                        EquipmentAttachment::equippedOrder))
                .map(EquipmentAttachment::item)
                .toList();
        equipment.clear();
        return removed;
    }

    private int findCombination(ArenaEquipment item) {
        if (item == null) return -1;
        for (int i = 0; i < equipment.size(); i++) {
            if (ArenaEquipment.combine(equipment.get(i).item(), item) != null) {
                return i;
            }
        }
        return -1;
    }

    private record EquipmentAttachment(
            ArenaEquipment item, long equippedOrder,
            boolean temporary) {
    }

    private static int baseCopiesForStar(int star) {
        return switch (star) {
            case 2 -> 3;
            case 3 -> 9;
            default -> 1;
        };
    }
}
