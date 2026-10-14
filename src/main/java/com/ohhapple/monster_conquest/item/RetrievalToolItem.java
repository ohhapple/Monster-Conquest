package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.ModEntities;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.entity.commanders.patrol.AbstractPatrolCaptain;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.registries.DeferredItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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

        List<Entity> targets = new ArrayList<>();
        if (mode == Mode.ARMY) {
            targets.addAll(collectArmyMembers(player, sl, target));
        }
        if (targets.isEmpty()) targets.add(target);

        if (!player.isCreative()) {
            ResourceLocation f = resolveFaction(targets.get(0));
            if (f == null || !FactionIds.isPlayer(f)
                    || !player.getUUID().equals(FactionIds.parsePlayer(f))) {
                player.displayClientMessage(
                        Component.translatable("item.monster_conquest.retrieval_tool.not_owner"),
                        true);
                return true;
            }
        }

        int count = 0;
        int failedFull = 0;
        for (Entity t : targets) {
            switch (retrieveSingle(player, sl, t)) {
                case OK -> count++;
                case FULL -> failedFull++;
                case INVALID -> { }
            }
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

    private static List<Entity> collectArmyMembers(Player player, ServerLevel sl, Entity target) {
        List<Entity> out = new ArrayList<>();
        UUID army = AbstractArmySoldier.resolveArmyUuid(target, sl);
        if (army == null) return out;

        double range = GeneralConfig.armyFollowRange * 2;
        AABB box = player.getBoundingBox().inflate(range);

        boolean isPlayerArmy = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(army) != null;

        if (isPlayerArmy) {
            AbstractArmySoldier anchor = resolveRider(target);
            if (anchor == null) return out;

            int targetSquad = anchor.getSquadId();
            for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
                if (!s.isAlive()) continue;
                UUID su = s.getCommanderUuid().orElse(null);
                if (!army.equals(su)) continue;
                if (s.getSquadId() != targetSquad) continue;
                out.add(s);
            }
            return out;
        }

        // NPC 军队：收整军
        Entity cmd = sl.getEntities().get(army);
        if (cmd instanceof AbstractArmyCommander c && c.isAlive()) {
            out.add(c);
        }
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive()) continue;
            UUID su = s.getCommanderUuid().orElse(null);
            if (!army.equals(su)) continue;
            out.add(s);
        }
        return out;
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
     * 收纳单个单位（收纳工具右键 / 单位界面"收纳"按钮共用同一条打包逻辑）：
     *   生成刷怪蛋 → 先确认背包放得下（空槽 / 未满堆叠都行，放不下则单位保留）
     *   → 确认成功后才移除单位与坐骑。
     */
    public static RetrieveResult retrieveUnit(Player player, Entity target) {
        if (!(player.level() instanceof ServerLevel sl)) return RetrieveResult.INVALID;
        return retrieveSingle(player, sl, target);
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

    // ===================== 蛋制作 =====================

    private static ItemStack makeEggFromSoldier(AbstractArmySoldier soldier,
                                                @Nullable WarHorseEntity horse) {
        DeferredItem<SoldierSpawnEggItem> eggItem = pickEggItem(soldier);
        ItemStack stack = new ItemStack(eggItem.get());
        SoldierSpawnEggItem.writeTierIdxStatic(stack, soldier.getTier().ordinal());

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        ResourceLocation faction = soldier.getFactionId();
        if (faction != null) tag.putString(KEY_FACTION, faction.toString());
        tag.putBoolean(KEY_CAVALRY, horse != null || soldier.getSoldierType().isCavalry());
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
        DeferredItem<CommanderSpawnEggItem> egg;

        if (patrol) {
            // 巡逻队长蛋
            if (faction == null) {
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
                egg = Monster_conquest.UNDEAD_PATROL_CAPTAIN_EGG;
            }
        } else {
            // 首领蛋
            if (faction == null) {
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
                egg = Monster_conquest.UNDEAD_COMMANDER_EGG;
            }
        }
        return new ItemStack(egg.get());
    }

    // ===================== 士兵蛋选择 =====================

    /**
     * 按士兵自身的实体类型选蛋。
     */
    private static DeferredItem<SoldierSpawnEggItem> pickEggItem(AbstractArmySoldier soldier) {
        int t = soldier.getTier().ordinal();
        EntityType<?> type = soldier.getType();

        if (type == ModEntities.HUSK_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_HUSK_T0, Monster_conquest.EGG_HUSK_T1,
                    Monster_conquest.EGG_HUSK_T2, Monster_conquest.EGG_HUSK_T3,
                    Monster_conquest.EGG_HUSK_T4);
        }
        if (type == ModEntities.DROWNED_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_DROWNED_T0, Monster_conquest.EGG_DROWNED_T1,
                    Monster_conquest.EGG_DROWNED_T2, Monster_conquest.EGG_DROWNED_T3,
                    Monster_conquest.EGG_DROWNED_T4);
        }
        if (type == ModEntities.ZOMBIE_VILLAGER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_ZOMBIE_VILLAGER_T0, Monster_conquest.EGG_ZOMBIE_VILLAGER_T1,
                    Monster_conquest.EGG_ZOMBIE_VILLAGER_T2, Monster_conquest.EGG_ZOMBIE_VILLAGER_T3,
                    Monster_conquest.EGG_ZOMBIE_VILLAGER_T4);
        }
        if (type == ModEntities.SKELETON_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_SKELETON_T0, Monster_conquest.EGG_SKELETON_T1,
                    Monster_conquest.EGG_SKELETON_T2, Monster_conquest.EGG_SKELETON_T3,
                    Monster_conquest.EGG_SKELETON_T4);
        }
        if (type == ModEntities.STRAY_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_STRAY_T0, Monster_conquest.EGG_STRAY_T1,
                    Monster_conquest.EGG_STRAY_T2, Monster_conquest.EGG_STRAY_T3,
                    Monster_conquest.EGG_STRAY_T4);
        }
        if (type == ModEntities.BOGGED_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_BOGGED_T0, Monster_conquest.EGG_BOGGED_T1,
                    Monster_conquest.EGG_BOGGED_T2, Monster_conquest.EGG_BOGGED_T3,
                    Monster_conquest.EGG_BOGGED_T4);
        }
        if (type == ModEntities.WITHER_SKELETON_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_WITHER_SKELETON_T0, Monster_conquest.EGG_WITHER_SKELETON_T1,
                    Monster_conquest.EGG_WITHER_SKELETON_T2, Monster_conquest.EGG_WITHER_SKELETON_T3,
                    Monster_conquest.EGG_WITHER_SKELETON_T4);
        }
        if (type == ModEntities.WITCH_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_WITCH_T0, Monster_conquest.EGG_WITCH_T1,
                    Monster_conquest.EGG_WITCH_T2, Monster_conquest.EGG_WITCH_T3,
                    Monster_conquest.EGG_WITCH_T4);
        }
        if (type == ModEntities.RIFLE_GUNNER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_GUNNER_RIFLE_T0, Monster_conquest.EGG_GUNNER_RIFLE_T1,
                    Monster_conquest.EGG_GUNNER_RIFLE_T2, Monster_conquest.EGG_GUNNER_RIFLE_T3,
                    Monster_conquest.EGG_GUNNER_RIFLE_T4);
        }
        if (type == ModEntities.SHOTGUN_GUNNER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_GUNNER_SHOTGUN_T0, Monster_conquest.EGG_GUNNER_SHOTGUN_T1,
                    Monster_conquest.EGG_GUNNER_SHOTGUN_T2, Monster_conquest.EGG_GUNNER_SHOTGUN_T3,
                    Monster_conquest.EGG_GUNNER_SHOTGUN_T4);
        }
        if (type == ModEntities.ROCKET_GUNNER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_GUNNER_ROCKET_T0, Monster_conquest.EGG_GUNNER_ROCKET_T1,
                    Monster_conquest.EGG_GUNNER_ROCKET_T2, Monster_conquest.EGG_GUNNER_ROCKET_T3,
                    Monster_conquest.EGG_GUNNER_ROCKET_T4);
        }
        if (type == ModEntities.SNIPER_GUNNER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_GUNNER_SNIPER_T0, Monster_conquest.EGG_GUNNER_SNIPER_T1,
                    Monster_conquest.EGG_GUNNER_SNIPER_T2, Monster_conquest.EGG_GUNNER_SNIPER_T3,
                    Monster_conquest.EGG_GUNNER_SNIPER_T4);
        }
        if (type == ModEntities.VINDICATOR_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_VINDICATOR_T0, Monster_conquest.EGG_VINDICATOR_T1,
                    Monster_conquest.EGG_VINDICATOR_T2, Monster_conquest.EGG_VINDICATOR_T3,
                    Monster_conquest.EGG_VINDICATOR_T4);
        }
        if (type == ModEntities.EVOKER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_EVOKER_T0, Monster_conquest.EGG_EVOKER_T1,
                    Monster_conquest.EGG_EVOKER_T2, Monster_conquest.EGG_EVOKER_T3,
                    Monster_conquest.EGG_EVOKER_T4);
        }
        if (type == ModEntities.VILLAGER_MACE_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_MACE_T0, Monster_conquest.EGG_MACE_T1,
                    Monster_conquest.EGG_MACE_T2, Monster_conquest.EGG_MACE_T3,
                    Monster_conquest.EGG_MACE_T4);
        }
        if (type == ModEntities.VILLAGER_ARCHER_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_VILLAGER_ARCHER_T0, Monster_conquest.EGG_VILLAGER_ARCHER_T1,
                    Monster_conquest.EGG_VILLAGER_ARCHER_T2, Monster_conquest.EGG_VILLAGER_ARCHER_T3,
                    Monster_conquest.EGG_VILLAGER_ARCHER_T4);
        }
        if (type == ModEntities.VILLAGER_PRIEST_SOLDIER.get()) {
            return pickTier(t,
                    Monster_conquest.EGG_VILLAGER_PRIEST_T0, Monster_conquest.EGG_VILLAGER_PRIEST_T1,
                    Monster_conquest.EGG_VILLAGER_PRIEST_T2, Monster_conquest.EGG_VILLAGER_PRIEST_T3,
                    Monster_conquest.EGG_VILLAGER_PRIEST_T4);
        }
        // 默认：僵尸士兵
        return pickTier(t,
                Monster_conquest.EGG_ZOMBIE_T0, Monster_conquest.EGG_ZOMBIE_T1,
                Monster_conquest.EGG_ZOMBIE_T2, Monster_conquest.EGG_ZOMBIE_T3,
                Monster_conquest.EGG_ZOMBIE_T4);
    }

    private static DeferredItem<SoldierSpawnEggItem> pickTier(
            int tier,
            DeferredItem<SoldierSpawnEggItem> t0,
            DeferredItem<SoldierSpawnEggItem> t1,
            DeferredItem<SoldierSpawnEggItem> t2,
            DeferredItem<SoldierSpawnEggItem> t3,
            DeferredItem<SoldierSpawnEggItem> t4) {
        return switch (tier) {
            case 4 -> t4;
            case 3 -> t3;
            case 2 -> t2;
            case 1 -> t1;
            default -> t0;
        };
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
