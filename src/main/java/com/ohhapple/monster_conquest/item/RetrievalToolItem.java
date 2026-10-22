package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.player.PlayerArmyManager;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.registries.DeferredItem;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class RetrievalToolItem extends Item {

    public static final String KEY_FACTION = "FactionId";
    public static final String KEY_CAVALRY = "Cavalry";
    /** 只有收纳工具会设置此标记。 */
    public static final String KEY_FROM_TOOL = "FromTool";

    private static final String KEY_MODE = "Mode";

    public enum Mode {
        SINGLE("item.monster_conquest.retrieval_tool.mode.single"),
        ARMY("item.monster_conquest.retrieval_tool.mode.army");

        private final String translationKey;

        Mode(String translationKey) { this.translationKey = translationKey; }

        public Component getDisplayName() {
            return Component.translatable(translationKey);
        }

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    public record RetrievalPlan(
            List<Entity> targets,
            List<Entity> eggTargets,
            List<AbstractArmySoldier> summonedTargets,
            @Nullable AbstractArmySoldier directSummon) {
    }

    public RetrievalToolItem(Properties props) { super(props); }

    // ===================== 工具蛋判定 =====================

    public static boolean isFromTool(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getBoolean(KEY_FROM_TOOL);
    }

    public static boolean isFromTool(CompoundTag tag) {
        return tag.getBoolean(KEY_FROM_TOOL);
    }

    // ===================== 模式 =====================

    public static Mode getMode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String m = tag.getString(KEY_MODE);
        try { return Mode.valueOf(m); }
        catch (Exception e) { return Mode.SINGLE; }
    }

    public static void setMode(ItemStack stack, Mode mode) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(KEY_MODE, mode.name());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void cycleMode(Player player, ItemStack stack) {
        Mode next = getMode(stack).next();
        setMode(stack, next);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.retrieval_tool.mode_switched",
                        next.getDisplayName()),
                true);
    }

    // ===================== 收纳 =====================
    public boolean tryRetrieve(Player player, Entity target) {
        if (!(player.level() instanceof ServerLevel sl)) return false;

        Mode mode = getMode(player.getMainHandItem());
        RetrievalPlan plan = buildRetrievalPlan(mode, sl, target.getBoundingBox()
                .inflate(GeneralConfig.armyFollowRange * 2), target);
        List<Entity> targets = plan.targets();

        if (!canRetrieve(player, target)) {
            player.displayClientMessage(
                    Component.translatable("item.monster_conquest.retrieval_tool.not_owner"),
                    true);
            return true;
        }

        Map<UUID, UUID> summonRoots = resolveSummonRoots(targets, sl);
        Set<UUID> packedRoots = new HashSet<>();
        int count = 0;
        int failedFull = 0;
        for (Entity t : plan.eggTargets()) {
            switch (retrieveSingle(player, sl, t)) {
                case OK -> {
                    count++;
                    if (t instanceof AbstractArmySoldier soldier) {
                        packedRoots.add(soldier.getUUID());
                    } else if (t instanceof AbstractArmyCommander commander) {
                        packedRoots.add(commander.getUUID());
                    }
                }
                case FULL -> failedFull++;
                case INVALID -> { }
            }
        }

        AbstractArmySoldier directSummon = plan.directSummon();
        if (directSummon != null && !directSummon.isRemoved()) {
            directSummon.discardSummonedInstance();
            count++;
        }

        for (AbstractArmySoldier soldier : plan.summonedTargets()) {
            if (soldier.isRemoved() || soldier == directSummon) continue;
            UUID root = summonRoots.get(soldier.getUUID());
            // 没有可产蛋的非召唤物根说明召唤者已丢失；无主召唤物不再等待根部收纳。
            if (root != null && !packedRoots.contains(root)) continue;
            soldier.discardSummonedInstance();
            count++;
        }

        if (count == 0 && failedFull == 0) return false;

        if (count > 0) {
            sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENDER_CHEST_CLOSE, SoundSource.PLAYERS, 1.0f, 1.3f);
        }

        // 背包放不下的部分：收纳失败（单位留在原地，不会凭空消失）
        if (failedFull > 0 && count > 0) {
            player.displayClientMessage(
                    Component.translatable(
                            "item.monster_conquest.retrieval_tool.inventory_full_partial",
                            count, failedFull), true);
        } else if (failedFull > 0) {
            player.displayClientMessage(
                    Component.translatable("item.monster_conquest.retrieval_tool.inventory_full"),
                    true);
        } else if (count == 1) {
            player.displayClientMessage(
                    Component.translatable("item.monster_conquest.retrieval_tool.collected"), true);
        } else {
            player.displayClientMessage(
                    Component.translatable("item.monster_conquest.retrieval_tool.collected_count", count),
                    true);
        }
        return true;
    }

    /**
     * 将一次收纳拆成“产蛋单位”和“无蛋附属召唤物”。
     *   SINGLE：
     *     · 右键召唤者 → 只产蛋并收纳召唤者，不处理其召唤物；
     *     · 右键召唤物 → 不产蛋，只收纳该召唤物本身。
     *   ARMY：
     *     · 召唤者、召唤物与所在军队组成闭包，非召唤物产蛋，
     *       全部召唤物只作为对应根的附属清除。
     */
    public static RetrievalPlan buildRetrievalPlan(Mode mode, ServerLevel level,
                                                   AABB box, Entity target) {
        List<Entity> targets = new ArrayList<>();
        Entity single = resolveRider(target);
        if (single == null) single = target;
        if (mode == Mode.ARMY) {
            targets.addAll(collectArmyMembers(level, box, single));
        } else {
            targets.add(single);
        }
        if (targets.isEmpty()) targets.add(single);

        List<Entity> eggs = new ArrayList<>();
        List<AbstractArmySoldier> summoned = new ArrayList<>();
        for (Entity entity : targets) {
            if (entity instanceof AbstractArmySoldier soldier && soldier.isSummoned()) {
                summoned.add(soldier);
            } else {
                eggs.add(entity);
            }
        }

        AbstractArmySoldier directSummon = mode == Mode.SINGLE
                && single instanceof AbstractArmySoldier soldier
                && soldier.isSummoned() ? soldier : null;
        return new RetrievalPlan(List.copyOf(targets), List.copyOf(eggs),
                List.copyOf(summoned), directSummon);
    }

    /**
     * 解析每个召唤物最终归属的非召唤物根单位。
     *   在移除任何实体前一次性计算，支持“召唤物又召唤了召唤物”的链式关系。
     */
    private static Map<UUID, UUID> resolveSummonRoots(List<Entity> targets,
                                                      ServerLevel level) {
        Map<UUID, UUID> roots = new HashMap<>();
        for (Entity entity : targets) {
            if (!(entity instanceof AbstractArmySoldier summoned)
                    || !summoned.isSummoned()) continue;

            Set<UUID> visited = new HashSet<>();
            AbstractArmySoldier current = summoned;
            while (current != null && current.isSummoned()
                    && visited.add(current.getUUID())) {
                UUID parentId = current.getSummonerUuid();
                if (parentId == null) break;
                Entity parentEntity = level.getEntities().get(parentId);
                if (!(parentEntity instanceof AbstractArmySoldier parent)) break;
                if (!parent.isSummoned()) {
                    roots.put(summoned.getUUID(), parent.getUUID());
                    break;
                }
                current = parent;
            }
        }
        return roots;
    }

    public static List<Entity> collectArmyMembers(ServerLevel sl, AABB box, Entity target) {
        List<Entity> expanded = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        AbstractArmySoldier rider = resolveRider(target);
        addOnce(expanded, seen, rider != null ? rider : target);

        for (int i = 0; i < expanded.size(); i++) {
            Entity member = expanded.get(i);
            if (member instanceof AbstractArmyCommander commander) {
                addCommanderArmyPeers(expanded, seen, commander, sl, box);
            } else if (member instanceof AbstractArmySoldier soldier) {
                expandSummonRelations(expanded, seen, soldier, sl);
                addArmyPeers(expanded, seen, soldier, sl, box);
            }
        }
        return expanded;
    }

    /**
     * 整军收纳必须包含召唤关系闭包：
     *   召唤物 → 召唤者；召唤者 → 自己登记的全部存活召唤物。
     * 闭包可能继续扩展（召唤者本身也是被召唤单位），直到没有新实体。
     */
    public static List<Entity> collectSummoningClosure(List<Entity> initial, ServerLevel sl) {
        List<Entity> expanded = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : initial) addOnce(expanded, seen, entity);

        for (int i = 0; i < expanded.size(); i++) {
            if (!(expanded.get(i) instanceof AbstractArmySoldier soldier)) continue;
            expandSummonRelations(expanded, seen, soldier, sl);
        }
        return expanded;
    }

    private static void expandSummonRelations(List<Entity> out, Set<UUID> seen,
                                              AbstractArmySoldier soldier,
                                              ServerLevel level) {
        if (soldier.isSummoned()) {
            addOnce(out, seen, soldier.getSummoner());
        }
        for (AbstractArmySoldier summoned : soldier.getLivingSummonedSoldiers(level)) {
            addOnce(out, seen, summoned);
        }
        // 名单是快速路径；若 NPC 名单尚未同步，再按召唤物自身持久化的 UUID 扫附近实体。
        for (AbstractArmySoldier nearby : level.getEntitiesOfClass(
                AbstractArmySoldier.class,
                    soldier.getBoundingBox().inflate(
                            Math.max(GeneralConfig.armyFollowRange, 256.0)),
                candidate -> candidate.isSummoned()
                        && soldier.getUUID().equals(candidate.getSummonerUuid()))) {
            addOnce(out, seen, nearby);
        }
    }

    private static void addArmyPeers(List<Entity> out, Set<UUID> seen,
                                     AbstractArmySoldier anchor, ServerLevel level,
                                     AABB box) {
        UUID army = AbstractArmySoldier.resolveArmyUuid(anchor, level);
        if (army == null) return;

        if (PlayerArmyManager.getIfPresent(army) != null) {
            int squadId = anchor.getSquadId();
            for (AbstractArmySoldier candidate : level.getEntitiesOfClass(
                    AbstractArmySoldier.class, box)) {
                if (candidate.isAlive()
                        && army.equals(candidate.getCommanderUuid().orElse(null))
                        && candidate.getSquadId() == squadId) {
                    addOnce(out, seen, candidate);
                }
            }
            return;
        }

        Entity commander = level.getEntities().get(army);
        if (commander instanceof AbstractArmyCommander c && c.isAlive()) {
            addOnce(out, seen, c);
        }
        for (AbstractArmySoldier candidate : level.getEntitiesOfClass(
                AbstractArmySoldier.class, box)) {
            if (candidate.isAlive()
                    && army.equals(candidate.getCommanderUuid().orElse(null))) {
                addOnce(out, seen, candidate);
            }
        }
    }

    private static void addCommanderArmyPeers(List<Entity> out, Set<UUID> seen,
                                              AbstractArmyCommander commander,
                                              ServerLevel level, AABB box) {
        UUID army = commander.getUUID();
        for (AbstractArmySoldier candidate : level.getEntitiesOfClass(
                AbstractArmySoldier.class, box)) {
            if (candidate.isAlive()
                    && army.equals(candidate.getCommanderUuid().orElse(null))) {
                addOnce(out, seen, candidate);
            }
        }
    }

    private static void addOnce(List<Entity> out, Set<UUID> seen, @Nullable Entity entity) {
        if (entity == null || !entity.isAlive() || entity.isRemoved()) return;
        if (seen.add(entity.getUUID())) out.add(entity);
    }

    @Nullable
    private static AbstractArmySoldier resolveRider(Entity target) {
        if (target instanceof AbstractArmySoldier s) return s;
        if (target instanceof WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            if (rider instanceof AbstractArmySoldier rs) return rs;
        }
        return null;
    }

    /** 收纳单个目标的结果：成功 / 背包放不下 / 目标无效。 */
    public enum RetrieveResult { OK, FULL, INVALID }

    /**
     * 收纳单个单位（单位界面"收纳"按钮等单目标入口使用同一条打包逻辑）：
     *   生成刷怪蛋 → 先确认背包放得下（空槽 / 未满堆叠都行，放不下则单位保留）
     *   → 确认成功后才移除单位与坐骑。
     */
    public static RetrieveResult retrieveUnit(Player player, Entity target) {
        if (!(player.level() instanceof ServerLevel sl)) return RetrieveResult.INVALID;
        RetrievalPlan plan = buildRetrievalPlan(Mode.SINGLE, sl,
                target.getBoundingBox().inflate(GeneralConfig.armyFollowRange * 2), target);
        if (plan.directSummon() != null) {
            plan.directSummon().discardSummonedInstance();
            return RetrieveResult.OK;
        }
        if (plan.eggTargets().isEmpty()) return RetrieveResult.INVALID;
        return retrieveSingle(player, sl, plan.eggTargets().get(0));
    }

    /** 收纳单个单位的反馈（音效 + 提示文字），工具与界面按钮共用。 */
    public static void notifyRetrieveResult(Player player, RetrieveResult result,
                                            double x, double y, double z) {
        switch (result) {
            case OK -> {
                player.level().playSound(null, x, y, z, SoundEvents.ENDER_CHEST_CLOSE,
                        SoundSource.PLAYERS, 1.0f, 1.3f);
                player.displayClientMessage(
                        Component.translatable("item.monster_conquest.retrieval_tool.collected"), true);
            }
            case FULL -> player.displayClientMessage(
                    Component.translatable("item.monster_conquest.retrieval_tool.inventory_full"), true);
            case INVALID -> { }
        }
    }

    /**
     * 收纳单个目标（两阶段）：
     *   1) 先生成刷怪蛋，此时不删除任何实体；
     *   2) 非创造模式：先确认背包放得下（空槽 / 未满的堆叠都可以）——
     *      放不下 → 本次收纳失败（FULL），单位与坐骑都留在原地；
     *      创造模式放不下才掉在原地（保留原有行为）；
     *   3) 确认成功后才移除单位与坐骑。
     */
    private static RetrieveResult retrieveSingle(Player player, ServerLevel sl, Entity target) {
        AbstractArmySoldier soldier = null;
        AbstractArmyCommander commander = null;
        WarHorseEntity horse = null;

        if (target instanceof WarHorseEntity h) {
            Entity rider = h.getFirstPassenger();
            if (rider instanceof AbstractArmySoldier s) {
                soldier = s;
                horse = h;
            } else if (rider instanceof AbstractArmyCommander c) {
                commander = c;
                horse = h;
            } else {
                // 空马：直接移除，不产出刷怪蛋
                h.discard();
                return RetrieveResult.OK;
            }
        } else if (target instanceof AbstractArmySoldier s) {
            soldier = s;
            if (s.getVehicle() instanceof WarHorseEntity h) horse = h;
        } else if (target instanceof AbstractArmyCommander c) {
            commander = c;
            if (c.getVehicle() instanceof WarHorseEntity h) horse = h;
        } else {
            return RetrieveResult.INVALID;
        }

        ItemStack egg = soldier != null
                ? makeEggFromSoldier(soldier, horse)
                : makeCommanderEgg(commander, horse);
        if (egg == null || egg.isEmpty()) return RetrieveResult.INVALID;

        // 先确认能放进背包（add 放不下时不会改动背包）
        if (!player.getInventory().add(egg)) {
            if (!player.isCreative()) {
                return RetrieveResult.FULL;    // 收纳失败：单位保留
            }
            player.drop(egg, false);           // 创造模式：掉在原地
        }

        // 确认收纳成功后才移除单位与坐骑
        if (soldier != null) {
            soldier.stopRiding();
            soldier.discard();
        }
        if (commander != null) {
            commander.stopRiding();
            commander.discard();
        }
        if (horse != null && !horse.isRemoved()) horse.discard();
        return RetrieveResult.OK;
    }

    // ===================== 阵营解析 =====================

    @Nullable
    public static ResourceLocation resolveFaction(Entity target) {
        if (target instanceof AbstractArmyCommander c) return c.getFactionId();
        if (target instanceof AbstractArmySoldier s) {
            ResourceLocation own = s.getFactionId();
            if (own != null) return own;
            AbstractArmyCommander cmd = s.getCommander();
            return cmd == null ? null : cmd.getFactionId();
        }
        if (target instanceof WarHorseEntity h) {
            UUID o = h.getOwnerUuid();
            if (o == null || !(h.level() instanceof ServerLevel sl)) return null;
            return resolveFaction(sl.getEntities().get(o));
        }
        return null;
    }

    /** 非创造模式只限制玩家阵营；NPC 阵营单位允许直接收纳。 */
    public static boolean canRetrieve(Player player, Entity target) {
        if (player.isCreative()) return true;
        ResourceLocation faction = resolveFaction(target);
        if (faction == null) return false;
        if (!FactionIds.isPlayer(faction)) return true;
        return player.getUUID().equals(FactionIds.parsePlayer(faction));
    }

    // ===================== 蛋制作 =====================

    private static ItemStack makeEggFromSoldier(AbstractArmySoldier soldier,
                                                @Nullable WarHorseEntity horse) {
        SoldierSpawnEggItem eggItem = pickEggItem(soldier);
        if (eggItem == null) return ItemStack.EMPTY;
        ItemStack stack = new ItemStack(eggItem);
        if (!eggItem.isSingleLevel()) {
            SoldierSpawnEggItem.writeTierIdxStatic(stack, soldier.getTier().ordinal());
        }

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ResourceLocation faction = soldier.getFactionId();
        if (faction != null) tag.putString(KEY_FACTION, faction.toString());
        tag.putBoolean(KEY_CAVALRY, eggItem.canToggleCavalry()
                && (horse != null || soldier.getSoldierType().isCavalry()));
        tag.putBoolean(KEY_FROM_TOOL, true);
        // 保留单位界面里手动放的装备与战术装备（自带装备不进蛋，避免无意义膨胀）
        soldier.writeGearTag(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack makeCommanderEgg(AbstractArmyCommander commander,
                                              @Nullable WarHorseEntity horse) {
        ResourceLocation faction = commander.getFactionId();
        // 用 instanceof 判断巡逻队长，不依赖基类的任何标记
        boolean patrol = commander instanceof AbstractPatrolCaptain;
        ItemStack stack = eggForFaction(faction, patrol);
        if (stack.isEmpty()) return ItemStack.EMPTY;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (faction != null) tag.putString(KEY_FACTION, faction.toString());
        tag.putBoolean(KEY_CAVALRY, horse != null);
        tag.putBoolean(KEY_FROM_TOOL, true);
        // 保留单位界面里手动放的装备与战术装备（自带装备不进蛋）
        commander.writeGearTag(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static ItemStack eggForFaction(@Nullable ResourceLocation faction, boolean patrol) {
        if (faction == null) return ItemStack.EMPTY;
        DeferredItem<CommanderSpawnEggItem> egg;

        if (patrol) {
            // 巡逻队长蛋
            if (faction.equals(FactionIds.UNDEAD)) {
                egg = Monster_conquest.UNDEAD_PATROL_CAPTAIN_EGG;
            } else if (faction.equals(FactionIds.VILLAGER)) {
                egg = Monster_conquest.VILLAGER_PATROL_CAPTAIN_EGG;
            } else if (faction.equals(FactionIds.ILLAGER)) {
                egg = Monster_conquest.ILLAGER_PATROL_CAPTAIN_EGG;
            } else if (faction.equals(FactionIds.NETHER)) {
                egg = Monster_conquest.NETHER_PATROL_CAPTAIN_EGG;
            } else if (faction.equals(FactionIds.END)) {
                egg = Monster_conquest.END_PATROL_CAPTAIN_EGG;
            } else if (faction.equals(FactionIds.MONSTER)) {
                egg = Monster_conquest.MONSTER_PATROL_CAPTAIN_EGG;
            } else if (faction.equals(FactionIds.NEUTRAL)) {
                egg = Monster_conquest.NEUTRAL_PATROL_CAPTAIN_EGG;
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            // 首领蛋
            if (faction.equals(FactionIds.UNDEAD)) {
                egg = Monster_conquest.UNDEAD_COMMANDER_EGG;
            } else if (faction.equals(FactionIds.VILLAGER)) {
                egg = Monster_conquest.VILLAGER_COMMANDER_EGG;
            } else if (faction.equals(FactionIds.ILLAGER)) {
                egg = Monster_conquest.ILLAGER_COMMANDER_EGG;
            } else if (faction.equals(FactionIds.NETHER)) {
                egg = Monster_conquest.NETHER_COMMANDER_EGG;
            } else if (faction.equals(FactionIds.END)) {
                egg = Monster_conquest.END_COMMANDER_EGG;
            } else if (faction.equals(FactionIds.MONSTER)) {
                egg = Monster_conquest.MONSTER_COMMANDER_EGG;
            } else if (faction.equals(FactionIds.NEUTRAL)) {
                egg = Monster_conquest.NEUTRAL_COMMANDER_EGG;
            } else {
                return ItemStack.EMPTY;
            }
        }
        return new ItemStack(egg.get());
    }

    // ===================== 士兵蛋选择 =====================

    /**
     * 从已注册士兵蛋中按实体类型和等级反查。
     * 不在未知类型上回退到某个默认兵种，避免收纳后静默变成别的单位。
     */
    @Nullable
    private static SoldierSpawnEggItem pickEggItem(AbstractArmySoldier soldier) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (!(item instanceof SoldierSpawnEggItem egg)
                    || !egg.isForEntityType(soldier.getType())) {
                continue;
            }
            if (egg.isSingleLevel() == soldier.hasFixedTier()
                    && (egg.isSingleLevel()
                    || egg.getDefaultTier() == soldier.getTier())) {
                return egg;
            }
        }
        return null;
    }

    // ===================== 提示 =====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        Mode mode = getMode(stack);
        tooltip.add(Component.translatable("item.monster_conquest.retrieval_tool.tooltip.mode",
                        mode.getDisplayName())
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("item.monster_conquest.retrieval_tool.tooltip1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.retrieval_tool.tooltip2")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.retrieval_tool.tooltip3")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.retrieval_tool.tooltip.left_click")
                .withStyle(ChatFormatting.AQUA));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
