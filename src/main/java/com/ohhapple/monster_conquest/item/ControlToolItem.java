package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.config.PlayerConfig;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.relation.FactionAlly;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.player.PlayerCommandState;
import com.ohhapple.monster_conquest.util.FactionDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 控制工具：
 *  - 中键：选择准星指向的实体（士兵 / 首领 / 战马）
 *  - 左键：切换模式：单个 → 军队 → 阵营 → 单个
 *  - 右键：优先 raycast 敌方实体 → 攻击；否则 raycast 方块 → 前往
 *  - 蹲下中键：循环切换目标小队
 *  - 低头右键：命令当前目标小队跟随
 */
public class ControlToolItem extends Item {

    private static final String KEY_MODE = "Mode";
    private static final String KEY_SELECTED = "SelectedEntity";
    private static final String KEY_SELECTED_FACTION = "SelectedFaction";
    private static final String KEY_SELECTED_SQUAD = "SelectedSquad";

    public enum Mode {
        SINGLE("item.monster_conquest.control_tool.mode.single"),
        ARMY("item.monster_conquest.control_tool.mode.army"),
        FACTION("item.monster_conquest.control_tool.mode.faction");

        private final String translationKey;

        Mode(String translationKey) { this.translationKey = translationKey; }

        public String getTranslationKey() { return translationKey; }

        public Component getDisplayName() {
            return Component.translatable(translationKey);
        }

        public Mode next() {
            Mode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    public ControlToolItem(Properties props) { super(props); }

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
                Component.translatable("msg.monster_conquest.control_tool.mode_switched",
                        next.getDisplayName()),
                true);
    }

    @Nullable
    public static UUID getSelected(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(KEY_SELECTED) ? tag.getUUID(KEY_SELECTED) : null;
    }

    @Nullable
    public static ResourceLocation getSelectedFaction(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String s = tag.getString(KEY_SELECTED_FACTION);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    /** 工具记录的小分队号（0 = 首领/未指定 = 全选）。 */
    public static int getSelectedSquad(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.getInt(KEY_SELECTED_SQUAD);
    }

    // ===================== 中键：选择 =====================

    public boolean trySelect(Player player, Entity target, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel sl)) return false;
        if (!(target instanceof LivingEntity)) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.not_controllable"), true);
            return false;
        }

        ResourceLocation faction = resolveFaction(target);
        UUID armyUuid = AbstractArmySoldier.resolveArmyUuid(target, player.level());

        if (faction == null && armyUuid == null) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.not_in_army"), true);
            return false;
        }

        if (!player.isCreative()) {
            ResourceLocation myFaction = FactionIds.player(player.getUUID());
            if (!myFaction.equals(faction)) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.control_tool.no_permission"), true);
                return false;
            }
        }

        int selectedSquad = resolveSquadFromTarget(target);

        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .get(player.getUUID());
        state.setControlToolSelectedArmy(armyUuid);
        state.setControlToolSelectedFaction(faction);
        state.setControlToolSelectedSquad(selectedSquad);

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(KEY_SELECTED, target.getUUID());
        if (faction != null) tag.putString(KEY_SELECTED_FACTION, faction.toString());
        tag.putInt(KEY_SELECTED_SQUAD, selectedSquad);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.4f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.control_tool.selected",
                        describeTarget(target)), true);
        return true;
    }

    /**
     * 从目标解析控制工具的"命令目标"：
     *   · 无小队士兵 / 无小队骑手 → CONTROL_TOOL_LONE（-1，单兵选择，命令只作用于它）；
     *   · 有队士兵 / 有队骑手     → 队号；
     *   · 首领 / 其它            → 0（全选）。
     */
    private static int resolveSquadFromTarget(Entity target) {
        if (target instanceof AbstractArmySoldier s) {
            return (s.getSquadId() <= 0)
                    ? com.ohhapple.monster_conquest.player.PlayerCommandState.CONTROL_TOOL_LONE
                    : s.getSquadId();
        }
        if (target instanceof WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            if (rider instanceof AbstractArmySoldier rs) {
                return (rs.getSquadId() <= 0)
                        ? com.ohhapple.monster_conquest.player.PlayerCommandState.CONTROL_TOOL_LONE
                        : rs.getSquadId();
            }
        }
        return 0;
    }

    // ===================== 右键：前往 =====================
    public boolean goTo(Player player, BlockPos pos, ItemStack stack) {
        return goTo(player,
                new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), stack);
    }

    /** 前往指定落点：方块点击与"准星没瞄到方块时的前方兜底落点"共用同一条命令流程。 */
    public boolean goTo(Player player, Vec3 dest, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel sl)) return false;

        Mode mode = getMode(stack);
        List<LivingEntity> targets = collectTargets(player, sl, mode, stack);
        if (targets.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.no_targets"), true);
            return false;
        }

        // 命令提示里显示落点所在的方块坐标（方块点击时与点击的那个方块一致）
        BlockPos pos = BlockPos.containing(dest.x, dest.y - 1, dest.z);

        boolean allOwn = true;
        for (LivingEntity t : targets) {
            if (!isOwnSoldier(player, t)) {
                allOwn = false;
                break;
            }
        }

        // ==================== 玩家自己的士兵 ====================
        if (allOwn) {
            var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                    .get(player.getUUID());

            refreshOccupiedSquads(player, sl);

            int selSquad = state.getControlToolSelectedSquad();
            // ARMY 模式 + 单兵选择（-1）：命令只作用于中键选中的那一名士兵
            boolean loneSelected = mode == Mode.SINGLE
                    || (mode == Mode.ARMY
                        && selSquad == com.ohhapple.monster_conquest.player
                                .PlayerCommandState.CONTROL_TOOL_LONE);

            // 单个士兵的命令：不占用小队号 —— 脱离小队，用个人状态执行
            //   （与小队"前往"完全同一套入口 orderLoneMove / orderMove）
            int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);
            int targetSquad = (mode == Mode.ARMY) ? selSquad : 0;
            boolean squadRedirect = false;

            if (loneSelected && targets.size() == 1
                    && targets.get(0) instanceof AbstractArmySoldier lone) {
                int sid = lone.getSquadId();
                if (sid >= 1 && sid <= maxVisible) {
                    // 已在 1-5 可见小队的士兵不能被拆成单兵：
                    //   对它所在的小队下令（保留小队编制）。
                    targetSquad = sid;
                    squadRedirect = true;
                } else {
                    // 无小队 / 隐藏小队：沿用个人状态命令
                    state.orderLoneMove(player, sl, lone, dest,
                            player.level().getGameTime());
                    sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.6f);
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.control_tool.ordered",
                                    1,
                                    pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                            true);
                    return true;
                }
            }

            // 起点 = 上一次驻守点；无锚点则退回玩家位置。
            //   朝向 = 起点 → 目标点方向（士兵实际行进方向），而不是玩家当前朝向。
            Vec3 origin = resolveCurrentAnchor(state, targetSquad);
            if (origin == null) origin = player.position();

            double dx = dest.x - origin.x;
            double dz = dest.z - origin.z;
            float yaw = player.getYRot();
            if (dx * dx + dz * dz > 0.01) {
                yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            }

            // 与无小队单兵共用同一套移动命令实现
            state.orderMove(targetSquad, dest, yaw, player.level().getGameTime());

            int count = 0;
            if (squadRedirect) {
                count = countSquadSoldiers(player, sl, targetSquad);
            } else {
                for (LivingEntity t : targets) {
                    if (t instanceof AbstractArmySoldier) count++;
                }
            }
            if (count == 0) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.control_tool.no_units"), true);
                return false;
            }

            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.6f);
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.ordered",
                            count,
                            pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                    true);
            return true;
        }

        // ==================== NPC 军队 ====================
        int count = 0;
        for (LivingEntity t : targets) {
            if (t instanceof AbstractArmyCommander c) {
                c.setManualMarchTarget(dest);
                count++;
            } else if (t instanceof AbstractArmySoldier s) {
                s.setManualFacingAnchor(s.position());
                s.setManualMoveTarget(dest);
                count++;
            }
        }

        if (count == 0) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.no_units"), true);
            return false;
        }

        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.6f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.control_tool.ordered",
                        count, pos.getX() + " " + pos.getY() + " " + pos.getZ()),
                true);
        return true;
    }

    /**
     * 找"上一次驻守点"。
     *   targetSquad > 0 → 直接取该 squad 的锚点；
     *   targetSquad == 0（全选）→ 从 occupied squads 里任取一个锚点
     *                            （全选时各小队的锚点应一致）。
     */
    @Nullable
    private static Vec3 resolveCurrentAnchor(
            com.ohhapple.monster_conquest.player.PlayerCommandState state,
            int targetSquad) {
        if (targetSquad < 0) return null;   // 单兵选择：不涉及小队锚点
        if (targetSquad > 0) {
            return state.stateForSoldier(targetSquad).getFormationAnchor();
        }
        int max = Math.max(1, PlayerConfig.maxPlayerSquads);
        for (int id = 1; id <= max; id++) {
            if (!state.isSquadOccupied(id)) continue;
            Vec3 a = state.stateForSoldier(id).getFormationAnchor();
            if (a != null) return a;
        }
        return null;
    }

    /** 统计某支小队（同一指挥官）当前存活的士兵数量（用于命令提示）。 */
    private static int countSquadSoldiers(Player player, ServerLevel sl, int squadId) {
        if (squadId <= 0) return 0;
        int n = 0;
        double range = GeneralConfig.armyFollowRange * 2;
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class,
                player.getBoundingBox().inflate(range))) {
            if (!s.isAlive()) continue;
            if (!player.getUUID().equals(s.getCommanderUuid().orElse(null))) continue;
            if (s.getSquadId() != squadId) continue;
            n++;
        }
        return n;
    }

    // ===================== 右键：攻击目标 =====================
    // ===================== 右键：攻击目标 =====================
    public boolean attackTarget(Player player, LivingEntity target, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel sl)) return false;
        if (target == player) return false;
        // 目标是自己正骑着的战马 → 不是攻击命令（骑在马上右键脚下应当是"跟随我"）
        if (target == player.getVehicle()) return false;

        // 和平模式：不能下达攻击命令（不能有敌意 / 仇恨）
        if (com.ohhapple.monster_conquest.util.PeacefulUtil.isPeaceful(player)) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.peaceful_no_attack"), true);
            return true;
        }

        Mode mode = getMode(stack);
        List<LivingEntity> units = collectTargets(player, sl, mode, stack);
        if (units.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.no_targets"), true);
            return false;
        }

        if (units.size() == 1) {
            LivingEntity only = units.get(0);
            if (only == target || only.getVehicle() == target) {
                return false;
            }
        }

        ResourceLocation targetFaction = resolveFaction(target);
        UUID targetArmy = AbstractArmySoldier.resolveArmyUuid(target, sl);

        // 单个士兵的命令：不占用小队号 —— 脱离小队，进入与"小队攻击"一致的软驻守
        //   例外：已在 1-5 可见小队的士兵不拆队，交给下面的小队攻击流程（整队执行）。
        int maxVisibleSquads = Math.max(1, PlayerConfig.maxPlayerSquads);
        if (units.size() == 1 && units.get(0) instanceof AbstractArmySoldier lone
                && player.getUUID().equals(lone.getCommanderUuid().orElse(null))
                && (lone.getSquadId() < 1 || lone.getSquadId() > maxVisibleSquads)) {
            ResourceLocation myFaction = lone.getFactionId();
            if (isFriendlyFaction(myFaction, targetFaction)) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.control_tool.same_faction"), true);
                return false;
            }
            var st = com.ohhapple.monster_conquest.player.PlayerArmyManager
                    .get(player.getUUID());
            long nowGT = player.level().getGameTime();
            st.orderLoneAttack(player, sl, lone, target, nowGT);

            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f);
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.control_tool.attack_ordered",
                            1, target.getName().getString()),
                    true);
            return true;
        }

        Set<Integer> affectedSquads = new HashSet<>();

        int count = 0;
        int blockedByFaction = 0;

        for (LivingEntity t : units) {
            if (t == target) continue;

            if (t instanceof AbstractArmySoldier s) {
                if (s.isAttackStopped()) continue;

                ResourceLocation myFaction = s.getFactionId();
                if (isFriendlyFaction(myFaction, targetFaction)) {
                    blockedByFaction++;
                    continue;
                }

                UUID myArmy = AbstractArmySoldier.resolveArmyUuid(s, sl);
                if (myArmy != null && targetArmy != null) {
                    ArmyRelations
                            .setHostile(myArmy, targetArmy);
                }
                s.cancelManualMoveOrder();   // 攻击命令覆盖旧的移动命令
                s.setTarget(target);
                if (player.getUUID().equals(s.getCommanderUuid().orElse(null))) {
                    if (s.getSquadId() == 0) {
                        // 无小队单兵（ARMY 模式顺带选中）：同样进入软驻守
                        PlayerCommandState
                                .stateForCommand(s,
                                        com.ohhapple.monster_conquest.player.PlayerArmyManager
                                                .get(player.getUUID()))
                                .applyAttackOrder(player.level().getGameTime());
                    } else {
                        affectedSquads.add(s.getSquadId());
                    }
                }
                count++;
            } else if (t instanceof AbstractArmyCommander c) {
                ResourceLocation myFaction = c.getFactionId();
                if (isFriendlyFaction(myFaction, targetFaction)) {
                    blockedByFaction++;
                    continue;
                }

                UUID myArmy = c.getUUID();
                if (targetArmy != null) {
                    ArmyRelations
                            .setHostile(myArmy, targetArmy);
                }
                c.setTarget(target);
                for (AbstractArmySoldier s : c.getSoldiers()) {
                    if (s.isAttackStopped()) continue;
                    s.cancelManualMoveOrder();   // 攻击命令覆盖旧的移动命令
                    s.setTarget(target);
                }
                count++;
            }
        }

        if (count == 0) {
            if (blockedByFaction > 0) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.control_tool.same_faction"), true);
            } else {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.control_tool.no_units"), true);
            }
            return false;
        }

        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(player.getUUID());
        long now = player.level().getGameTime();

        if (state != null) {
            if (targetArmy != null) {
                state.markSharedHatredArmy(targetArmy, now);
            } else {
                state.markSharedHatredEntity(target.getUUID(), now);
            }
        }

        if (state != null && !affectedSquads.isEmpty()) {
            refreshOccupiedSquads(player, sl);
            // 与无小队单兵共用同一套软驻守实现
            state.orderAttack(affectedSquads, now);
        }

        if (state != null) {
            if ((mode == Mode.SINGLE || mode == Mode.ARMY) && !affectedSquads.isEmpty()) {
                state.startPreciseStrike(
                        target.getUUID(),
                        PlayerCommandState.expandArmyTargets(sl, target),
                        affectedSquads,
                        now,
                        36000L);
            } else if (mode == Mode.FACTION) {
                state.endPreciseStrike();
            }
        }

        // 单体选中但属于可见小队 → 实际是整队执行，提示按小队人数显示
        int reportCount = count;
        if (units.size() == 1 && affectedSquads.size() == 1) {
            reportCount = Math.max(count,
                    countSquadSoldiers(player, sl, affectedSquads.iterator().next()));
        }

        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.control_tool.attack_ordered",
                        reportCount, target.getName().getString()),
                true);
        return true;
    }

    // ===================== 低头：跟随 =====================

    /** 命令控制工具当前选中的小队跟随玩家（复用 controlToolSelectedSquad）。 */
    public boolean commandFollowHere(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel sl)) return false;

        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(player.getUUID());
        if (state == null) return false;

        // 刷新"有士兵的小队"
        refreshOccupiedSquads(player, sl);

        int targetSquad = state.getControlToolSelectedSquad();

        // 单兵选择（-1）：命令这名无小队士兵跟随（个人状态，同一套 MovementMode）
        if (targetSquad == com.ohhapple.monster_conquest.player
                .PlayerCommandState.CONTROL_TOOL_LONE) {
            AbstractArmySoldier lone = resolveSelectedSoldier(sl, stack);
            if (lone == null || !lone.isAlive()) return false;

            com.ohhapple.monster_conquest.player.PlayerCommandState
                    .stateForCommand(lone, state)
                    .applyFollowOrder(com.ohhapple.monster_conquest.player
                            .PlayerCommandState.MovementMode.FOLLOW);

            sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.4f);
            player.displayClientMessage(
                    Component.translatable(
                            "msg.monster_conquest.control_tool.follow_lone_ordered",
                            lone.getName()),
                    true);
            return true;
        }

        int oldSelected = state.getSelectedSquad();
        state.setSelectedSquad(targetSquad);
        try {
            state.setMovement(
                    com.ohhapple.monster_conquest.player.PlayerCommandState.MovementMode.FOLLOW);
        } finally {
            state.setSelectedSquad(oldSelected);
        }

        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8f, 1.4f);

        Component msg = (targetSquad == 0)
                ? Component.translatable("msg.monster_conquest.control_tool.follow_all_ordered")
                : Component.translatable("msg.monster_conquest.control_tool.follow_squad_ordered",
                targetSquad);
        player.displayClientMessage(msg, true);
        return true;
    }

    /** 扫描附近属于该玩家的士兵，把"有士兵的小队"写回 state（带 5 tick 节流）。 */
    private static void refreshOccupiedSquads(Player player, ServerLevel sl) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.ohhapple.monster_conquest.player.PlayerArmyManager
                    .refreshOccupiedSquads(sp);
        }
    }

    // ===================== 辅助 =====================

    private static boolean isOwnSoldier(Player player, LivingEntity t) {
        if (t instanceof AbstractArmySoldier s) {
            return player.getUUID().equals(s.getCommanderUuid().orElse(null));
        }
        return false;
    }

    /**
     * 友方阵营判定（攻击命令的前置检查）：
     *   同阵营，或"我方阵营的结盟阵营"（FactionAlly 单向，与士兵 AI 的友军判定一致）。
     */
    private static boolean isFriendlyFaction(@Nullable ResourceLocation mine,
                                             @Nullable ResourceLocation target) {
        if (mine == null || target == null) return false;
        return mine.equals(target) || FactionAlly.isAlly(mine, target);
    }

    /**
     * 控制工具当前中键选中的士兵：选中的是战马时解析到骑手士兵。
     *   用于"单兵选择（-1）"的命令目标解析。
     */
    @Nullable
    private static AbstractArmySoldier resolveSelectedSoldier(ServerLevel sl, ItemStack stack) {
        UUID sel = getSelected(stack);
        if (sel == null) return null;
        Entity e = sl.getEntities().get(sel);
        if (e instanceof WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            return (rider instanceof AbstractArmySoldier s) ? s : null;
        }
        return (e instanceof AbstractArmySoldier s) ? s : null;
    }

    private List<LivingEntity> collectTargets(Player player, ServerLevel sl,
                                              Mode mode, ItemStack stack) {
        List<LivingEntity> out = new ArrayList<>();
        var state = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(player.getUUID());
        if (state == null) return out;

        switch (mode) {
            case SINGLE -> {
                UUID sel = getSelected(stack);
                if (sel != null) {
                    Entity e = sl.getEntities().get(sel);
                    // 选中的是战马 → 取骑手
                    if (e instanceof WarHorseEntity horse) {
                        Entity rider = horse.getFirstPassenger();
                        if (rider instanceof LivingEntity le && le.isAlive()) {
                            out.add(le);
                        }
                    } else if (e instanceof LivingEntity le && le.isAlive()) {
                        out.add(le);
                    }
                }
            }
            case ARMY -> {
                UUID armyUuid = state.getControlToolSelectedArmy();
                if (armyUuid == null) {
                    armyUuid = player.getUUID();
                }

                Entity cmd = sl.getEntities().get(armyUuid);
                if (cmd instanceof AbstractArmyCommander c && c.isAlive()) {
                    out.add(c);
                    return out;
                }

                int selSquad = state.getControlToolSelectedSquad();
                int maxVisible = Math.max(1, PlayerConfig.maxPlayerSquads);

                // 单兵选择（-1）：只命令中键选中的那一个（战马 → 骑手），
                //   与"全选（0）"完全区分开。
                if (selSquad < 0) {
                    AbstractArmySoldier lone = resolveSelectedSoldier(sl, stack);
                    if (lone != null && lone.isAlive()) out.add(lone);
                    return out;
                }

                double range = GeneralConfig.armyFollowRange * 2;
                AABB box = player.getBoundingBox().inflate(range);
                for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
                    if (!s.isAlive()) continue;
                    UUID su = s.getCommanderUuid().orElse(null);
                    if (!armyUuid.equals(su)) continue;
                    int sid = s.getSquadId();
                    if (selSquad > 0) {
                        // 指定队号（可以是隐藏队号 6+）—— 精确匹配
                        if (sid != selSquad) continue;
                    } else {
                        // 全选：只包含 1..maxPlayerSquads，隐藏队不参与
                        if (sid < 1 || sid > maxVisible) continue;
                    }
                    out.add(s);
                }
            }
            case FACTION -> {
                ResourceLocation faction = state.getControlToolSelectedFaction();
                if (faction == null) return out;

                double range = GeneralConfig.armyFollowRange * 2;
                AABB box = player.getBoundingBox().inflate(range);

                for (AbstractArmyCommander c : sl.getEntitiesOfClass(AbstractArmyCommander.class, box)) {
                    if (!c.isAlive()) continue;
                    if (!faction.equals(c.getFactionId())) continue;
                    out.add(c);
                }

                if (out.isEmpty()) {
                    for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
                        if (!s.isAlive()) continue;
                        if (!faction.equals(s.getFactionId())) continue;
                        out.add(s);
                    }
                }
            }
        }
        return out;
    }

    private static Component describeTarget(Entity target) {
        if (target instanceof AbstractArmyCommander c) {
            return Component.translatable("msg.monster_conquest.control_tool.desc.commander",
                    FactionDisplay.displayName(c.getFactionId()));
        }
        if (target instanceof AbstractArmySoldier s) {
            UUID cmd = s.getCommanderUuid().orElse(null);
            String shortId = cmd == null ? "-" : cmd.toString().substring(0, 8);
            return Component.translatable("msg.monster_conquest.control_tool.desc.soldier",
                    s.getSoldierType().name(), shortId, squadLabel(s.getSquadId()));
        }
        if (target instanceof WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            if (rider instanceof AbstractArmySoldier rs) {
                UUID cmd = rs.getCommanderUuid().orElse(null);
                String shortId = cmd == null ? "-" : cmd.toString().substring(0, 8);
                return Component.translatable(
                        "msg.monster_conquest.control_tool.desc.horse_with_squad",
                        rs.getSoldierType().name(), shortId, squadLabel(rs.getSquadId()));
            }
            return Component.translatable("msg.monster_conquest.control_tool.desc.horse");
        }
        return target.getName();
    }

    /** 队号显示：0 / -1 → "无小队"，其余显示数字。 */
    private static Component squadLabel(int squadId) {
        return squadId <= 0
                ? Component.translatable("msg.monster_conquest.control_tool.squad.lone")
                : Component.literal(String.valueOf(squadId));
    }

    @Nullable
    private static ResourceLocation resolveFaction(Entity target) {
        if (target instanceof AbstractArmyCommander c) return c.getFactionId();
        if (target instanceof AbstractArmySoldier s) return s.getFactionId();
        if (target instanceof Player p) return FactionIds.player(p.getUUID());
        if (target instanceof WarHorseEntity h) {
            // 骑手优先：玩家正骑着的马按玩家的阵营算 ——
            //   "打自己骑的马 / 拿自己骑的马当目标"会走同阵营拦截，不会变成攻击命令。
            Entity rider = h.getFirstPassenger();
            if (rider instanceof LivingEntity le) {
                ResourceLocation fac = resolveFaction(le);
                if (fac != null) return fac;
            }
            UUID o = h.getOwnerUuid();
            if (o == null || !(h.level() instanceof ServerLevel sl)) return null;
            Entity owner = sl.getEntities().get(o);
            return owner == null ? null : resolveFaction(owner);
        }
        return null;
    }

    // ===================== Tooltip =====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        Mode mode = getMode(stack);

        tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.select")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.mode",
                        mode.getDisplayName())
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.goto")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.order")
                .withStyle(ChatFormatting.DARK_GRAY));

        UUID sel = getSelected(stack);
        if (sel != null) {
            tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.selected",
                            sel.toString().substring(0, 8))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.not_selected")
                    .withStyle(ChatFormatting.GRAY));
        }

        ResourceLocation f = getSelectedFaction(stack);
        if (f != null) {
            tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.faction",
                            FactionDisplay.displayName(f))
                    .withStyle(ChatFormatting.GOLD));
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !mc.player.isCreative()) {
            tooltip.add(Component.translatable("item.monster_conquest.control_tool.tooltip.creative_only")
                    .withStyle(ChatFormatting.DARK_RED));
        }

        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
