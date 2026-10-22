package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.block.SpawnPointBlock;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.network.clientbound.path.SyncPathRoutePayload;
import com.ohhapple.monster_conquest.path.PathMode;
import com.ohhapple.monster_conquest.path.PathRoute;
import com.ohhapple.monster_conquest.path.PathRoutes;
import com.ohhapple.monster_conquest.util.RaycastUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 路径点工具：
 *   · 左键：切换巡逻模式（走完最后一个点回到第一个点继续循环） / 路线模式（走完最后一个点结束，单位恢复自由活动）；
 *   · 蹲下左键：切换军队模式（点单位选中整支 NPC 军队 / 巡逻队，或玩家小编队） / 单个模式
 *     （点单位只选这一个；如果它在玩家小编队里，会先让它退出小队变成单兵）；
 *   · 中键：选中准星指着的单位（按上面两种模式归类），或刷怪点方块；
 *   · 右键：把点击的位置依次加为巡逻点 / 路线点；蹲下右键：清空当前选中目标的所有路径。
 *
 * 路径只存在世界存档里：挖掉刷怪点、中键复制、方块变成物品、把士兵收纳成蛋都不会带走它。
 */
public class PathToolItem extends Item {

    private static final String KEY_PATH_MODE = "PathMode";
    private static final String KEY_SELECT_MODE = "SelectMode";
    /** 当前选中目标写进物品里（供屏幕右上角 HUD 显示）：0 单位 / 1 军队 / 2 小编队 / 3 刷怪点。 */
    private static final String KEY_SEL_KIND = "SelKind";
    private static final String KEY_SEL_SQUAD = "SelSquad";
    private static final String KEY_SEL_POS = "SelPos";
    private static final String KEY_SEL_ID = "SelId";
    /** 当前选中目标身上的路径点数量（HUD 显示用）。 */
    private static final String KEY_SEL_POINTS = "SelPoints";

    public static final int SEL_UNIT = 0;
    public static final int SEL_ARMY = 1;
    public static final int SEL_SQUAD = 2;
    public static final int SEL_SPAWNER = 3;

    /** 中键选择的范围。 */
    public enum SelectMode {
        /** 单个：只选准星指着的这一个单位。 */
        SINGLE("msg.monster_conquest.path_tool.select.single"),
        /** 军队：NPC 军队 / 巡逻队，或玩家小编队。 */
        ARMY("msg.monster_conquest.path_tool.select.army");

        private final String translationKey;

        SelectMode(String translationKey) { this.translationKey = translationKey; }

        public Component getDisplayName() { return Component.translatable(translationKey); }

        public SelectMode next() {
            SelectMode[] all = values();
            return all[(ordinal() + 1) % all.length];
        }
    }

    public PathToolItem(Properties props) { super(props); }

    // ===================== 模式 =====================

    public static PathMode getPathMode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        try {
            return PathMode.valueOf(tag.getString(KEY_PATH_MODE));
        } catch (Exception e) {
            return PathMode.PATROL;
        }
    }

    public static SelectMode getSelectMode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        try {
            return SelectMode.valueOf(tag.getString(KEY_SELECT_MODE));
        } catch (Exception e) {
            return SelectMode.ARMY;
        }
    }

    private static void put(ItemStack stack, String key, String value) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(key, value);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    // ===================== 选中信息（写进物品，HUD 用） =====================

    private static void writeSelection(ItemStack stack, int kind, int squad, long pos, String id) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(KEY_SEL_KIND, kind);
        tag.putInt(KEY_SEL_SQUAD, squad);
        tag.putLong(KEY_SEL_POS, pos);
        tag.putString(KEY_SEL_ID, id == null ? "" : id);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void writePointCount(ItemStack stack, int count) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(KEY_SEL_POINTS, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static int getSelKind(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(KEY_SEL_KIND);
    }

    public static int getSelSquad(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(KEY_SEL_SQUAD);
    }

    public static long getSelPos(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getLong(KEY_SEL_POS);
    }

    public static String getSelId(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getString(KEY_SEL_ID);
    }

    public static int getSelPoints(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getInt(KEY_SEL_POINTS);
    }

    /** 左键：巡逻 / 路线切换。 */
    public static void cyclePathMode(ServerPlayer player, ItemStack stack) {
        PathMode next = getPathMode(stack).next();
        put(stack, KEY_PATH_MODE, next.name());
        if (player.level() instanceof ServerLevel level) {
            PathRoutes routes = PathRoutes.get(level);
            PathRoutes.Selection selection = PathRoutes.selectionOf(player.getUUID());
            if (selection != null) {
                routes.setRouteMode(selection.key(), next);
                pushPreview(player, routes, selection.key());
            }
        }
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.path_tool.mode_switched",
                        next.getDisplayName()), true);
    }

    /** 蹲下左键：军队 / 单个切换。 */
    public static void cycleSelectMode(ServerPlayer player, ItemStack stack) {
        SelectMode next = getSelectMode(stack).next();
        put(stack, KEY_SELECT_MODE, next.name());
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.path_tool.select_switched",
                        next.getDisplayName()), true);
    }

    // ===================== 中键选择 =====================

    public static boolean isUnit(Entity entity) {
        return entity instanceof AbstractArmySoldier
                || entity instanceof AbstractArmyCommander
                || (entity instanceof WarHorseEntity horse
                        && horse.getFirstPassenger() instanceof AbstractArmySoldier);
    }

    /** 中键：选中准星指着的单位 / 军队 / 小编队，或刷怪点方块。 */
    public static void select(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) return;
        PathRoutes routes = PathRoutes.get(level);
        boolean creative = player.isCreative();

        Entity target = RaycastUtil.raycastEntity(player, RaycastUtil.DEFAULT_MAX_DISTANCE, PathToolItem::isUnit);
        if (target != null) {
            // 骑兵：准星指到战马时选骑手 —— 战马会随下马被清除，不能作为选中对象。
            if (target instanceof WarHorseEntity horse
                    && horse.getFirstPassenger() instanceof AbstractArmySoldier rider) {
                target = rider;
            }
            // 非创造模式：只能操作自己的单位 / 小编队
            if (!creative && !isOwnUnit(player, target)) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.path_tool.not_yours"), true);
                pushPreview(player, routes, null);
                return;
            }
            PathRoutes.Selection selection = selectUnit(player, stack, target);
            if (selection != null) {
                PathRoutes.setSelection(player.getUUID(), selection);
                int count = routes.pointCount(selection.key());
                writeSelection(stack,
                        selection.kind(), selection.squad(),
                        selection.pos(), target.getUUID().toString());
                writePointCount(stack, count);
                player.inventoryMenu.broadcastChanges();
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.path_tool.selected",
                                selection.label(), count), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.6f, 1.6f);
                pushPreview(player, routes, selection.key());
            }
            return;
        }

        // 再看方块：刷怪点
        HitResult hit = player.pick(RaycastUtil.DEFAULT_MAX_DISTANCE, 0.0f, false);
        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) hit).getBlockPos();
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof SpawnPointBlock) {
                if (!creative) {
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.path_tool.spawner_creative"), true);
                    pushPreview(player, routes, null);
                    return;
                }
                PathRoutes.Selection selection = new PathRoutes.Selection(
                        PathRoutes.spawnerKey(pos),
                        Component.translatable("msg.monster_conquest.path_tool.spawner"),
                        SEL_SPAWNER, 0, pos.asLong(), null);
                PathRoutes.setSelection(player.getUUID(), selection);
                writeSelection(stack, SEL_SPAWNER, 0, pos.asLong(), "");
                writePointCount(stack, routes.pointCount(selection.key()));
                player.inventoryMenu.broadcastChanges();
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.path_tool.selected",
                                selection.label(), routes.pointCount(selection.key())), true);
                pushPreview(player, routes, selection.key());
                return;
            }
        }

        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.path_tool.nothing"), true);
        pushPreview(player, routes, null);
    }

    @Nullable
    private static PathRoutes.Selection selectUnit(ServerPlayer player, ItemStack stack, Entity target) {
        UUID owner = ownFactionOwner(target);
        if (getSelectMode(stack) == SelectMode.SINGLE) {
            // 单个模式：在玩家小编队里的士兵先退出小队，变成单兵
            if (target instanceof AbstractArmySoldier soldier
                    && soldier.isPlayerArmy() && soldier.getSquadId() > 0) {
                soldier.setSquadId(0);
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.path_tool.left_squad",
                                target.getDisplayName()), true);
            }
            return new PathRoutes.Selection(PathRoutes.unitKey(target.getUUID()), target.getDisplayName(),
                    SEL_UNIT, 0, 0L, owner);
        }

        // 军队模式：NPC 军队 / 巡逻队（走首领），玩家小编队；没有归属就退回单个
        if (target instanceof AbstractArmySoldier soldier) {
            UUID commander = soldier.getCommanderUuid().orElse(null);
            if (commander == null) {
                return new PathRoutes.Selection(PathRoutes.unitKey(target.getUUID()), target.getDisplayName(),
                        SEL_UNIT, 0, 0L, owner);
            }
            if (soldier.isPlayerArmy()) {
                int squad = soldier.getSquadId();
                if (squad <= 0) {
                    return new PathRoutes.Selection(PathRoutes.unitKey(target.getUUID()), target.getDisplayName(),
                            SEL_UNIT, 0, 0L, owner);
                }
                return new PathRoutes.Selection(PathRoutes.squadKey(commander, squad),
                        Component.translatable("msg.monster_conquest.path_tool.player_squad", squad),
                        SEL_SQUAD, squad, 0L, commander);
            }
            return new PathRoutes.Selection(PathRoutes.armyKey(commander),
                    Component.translatable("msg.monster_conquest.path_tool.army"), SEL_ARMY, 0, 0L, owner);
        }
        if (target instanceof AbstractArmyCommander commander) {
            return new PathRoutes.Selection(PathRoutes.armyKey(commander.getUUID()),
                    Component.translatable("msg.monster_conquest.path_tool.army"), SEL_ARMY, 0, 0L, owner);
        }
        return null;
    }

    /** 该单位是不是玩家阵营的单位；是的话返回归属玩家。 */
    @Nullable
    private static UUID ownFactionOwner(Entity entity) {
        ResourceLocation faction = entity instanceof AbstractArmySoldier soldier ? soldier.getFactionId()
                : entity instanceof AbstractArmyCommander commander ? commander.getFactionId() : null;
        return FactionIds.parsePlayer(faction);
    }

    /** 非创造模式：只允许操作自己的单位 / 小编队。 */
    private static boolean isOwnUnit(ServerPlayer player, Entity entity) {
        UUID owner = ownFactionOwner(entity);
        return owner != null && owner.equals(player.getUUID());
    }

    // ===================== 右键加点 / 蹲下右键清空（可隔空） =====================

    /**
     * 右键（与其他工具一样走准星射线，最远 256 格）：
     *   · 普通右键：把准星指到的方块上方那一格依次加为路径点；
     *   · 蹲下右键：清空当前选中目标的全部路径点。
     */
    public static void handleRightClick(ServerPlayer player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) return;
        PathRoutes routes = PathRoutes.get(level);
        PathRoutes.Selection selection = PathRoutes.selectionOf(player.getUUID());
        if (selection == null) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.path_tool.no_selection"), true);
            pushPreview(player, routes, null);
            return;
        }
        // 非创造模式：选中的必须是自己的单位 / 小编队（在创造模式选好再切回生存也会被拦下）
        if (!player.isCreative()
                && (selection.owner() == null || !selection.owner().equals(player.getUUID()))) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.path_tool.not_yours"), true);
            pushPreview(player, PathRoutes.get(level), null);
            return;
        }

        if (player.isSecondaryUseActive()) {
            routes.clear(selection.key());
            writePointCount(stack, 0);
            player.inventoryMenu.broadcastChanges();
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.path_tool.cleared", selection.label()), true);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 0.7f);
            pushPreview(player, routes, selection.key());
            return;
        }

        // 准星射线（256 格）；没瞄到方块（对着天空 / 远处地形之外）时用准星前方 16 格兜底
        BlockPos hit = RaycastUtil.raycastBlock(player, RaycastUtil.DEFAULT_MAX_DISTANCE);
        Vec3 point = hit != null
                ? new Vec3(hit.getX() + 0.5, hit.getY() + 1.0, hit.getZ() + 0.5)
                : player.getEyePosition().add(player.getLookAngle().scale(16.0));

        PathRoute route = routes.addPoint(selection.key(), getPathMode(stack), point);
        writePointCount(stack, route.size());
        player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.path_tool.added",
                        route.size(), selection.label(), route.getMode().getDisplayName()), true);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.4f);
        pushPreview(player, routes, selection.key());
    }

    /**
     * 把某个 key 的路径同步给客户端（手持路径点工具时在世界里画点与箭头）；key 为 null 或没有路径时清空预览。
     */
    private static void pushPreview(ServerPlayer player, PathRoutes routes, @Nullable String key) {
        PathRoute route = routes.route(key);
        if (route == null || route.size() == 0) {
            PacketDistributor.sendToPlayer(player,
                    new SyncPathRoutePayload(PathMode.PATROL.ordinal(), List.of()));
            return;
        }
        PacketDistributor.sendToPlayer(player,
                new SyncPathRoutePayload(route.getMode().ordinal(), List.copyOf(route.getPoints())));
    }

    /** 其它入口改了路径（例如刷怪点界面里的「清空」按钮）后，把玩家当前选中目标重新同步给客户端。 */
    public static void syncPreview(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) return;
        PathRoutes.Selection selection = PathRoutes.selectionOf(player.getUUID());
        pushPreview(player, PathRoutes.get(level), selection == null ? null : selection.key());
    }

    // ===================== 提示 =====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("msg.monster_conquest.path_tool.mode_switched",
                        getPathMode(stack).getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("msg.monster_conquest.path_tool.select_switched",
                        getSelectMode(stack).getDisplayName())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.monster_conquest.path_tool.tip1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.path_tool.tip2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.path_tool.tip3")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.path_tool.tip4")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }
}
