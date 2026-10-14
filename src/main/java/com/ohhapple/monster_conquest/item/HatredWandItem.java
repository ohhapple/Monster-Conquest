package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.army.relation.ArmyRelations;
import com.ohhapple.monster_conquest.army.relation.FactionHostility;
import com.ohhapple.monster_conquest.army.threat.ThreatDispatcher;
import com.ohhapple.monster_conquest.entity.AbstractArmyCommander;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.util.FactionDisplay;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class HatredWandItem extends Item {

    private static final String KEY_SELECTED_ARMY = "SelectedArmy";
    private static final String KEY_SELECTED_FACTION = "SelectedFaction";
    private static final String KEY_MODE = "Mode";
    public static final int COOLDOWN_TICKS = 10;

    public HatredWandItem(Properties props) { super(props); }

    // ===================== 模式 =====================
    public static boolean isFactionMode(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return "faction".equals(tag.getString(KEY_MODE));
    }

    public static void setFactionMode(ItemStack stack, boolean faction) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(KEY_MODE, faction ? "faction" : "army");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static void toggleMode(Player player, ItemStack stack) {
        boolean now = !isFactionMode(stack);
        setFactionMode(stack, now);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.hatred_wand.mode_switched",
                        Component.translatable(now
                                ? "item.monster_conquest.hatred_wand.mode.faction"
                                : "item.monster_conquest.hatred_wand.mode.army")),
                true);
    }

    // ===================== 中键选择 =====================
    public boolean trySelect(Player player, Entity target, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel sl)) return false;
        if (isFactionMode(stack)) return trySelectFaction(player, target, stack, sl);
        return trySelectArmy(player, target, stack, sl);
    }

    private boolean trySelectArmy(Player player, Entity target, ItemStack stack, ServerLevel sl) {
        UUID army = AbstractArmySoldier.resolveArmyUuid(target, player.level());
        if (army == null) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.hatred_wand.not_in_army"), true);
            return true;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putUUID(KEY_SELECTED_ARMY, army);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.4f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.hatred_wand.army_selected"), true);
        return true;
    }

    private boolean trySelectFaction(Player player, Entity target, ItemStack stack, ServerLevel sl) {
        ResourceLocation faction = resolveFaction(target);
        if (faction == null) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.hatred_wand.not_in_faction"), true);
            return true;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putString(KEY_SELECTED_FACTION, faction.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.6f, 1.4f);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.hatred_wand.faction_selected",
                        FactionDisplay.displayName(faction)),
                true);
        return true;
    }

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
            UUID owner = h.getOwnerUuid();
            if (owner == null) return null;
            if (!(h.level() instanceof ServerLevel sl)) return null;
            Entity o = sl.getEntities().get(owner);
            return o == null ? null : resolveFaction(o);
        }
        return null;
    }

    // ===================== 右键敌对 =====================
    public boolean tryMarkHostile(Player player, Entity target, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel sl)) return false;

        // 和平模式：不建立任何敌意 / 仇恨
        if (com.ohhapple.monster_conquest.util.PeacefulUtil.isPeaceful(player)) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.hatred_wand.peaceful"), true);
            return true;
        }

        if (!isValidTarget(target)) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.hatred_wand.invalid_target"), true);
            return true;
        }

        if (isFactionMode(stack)) {
            ResourceLocation selected = getSelectedFaction(stack);
            if (selected == null) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.hatred_wand.select_faction_first"), true);
                return true;
            }
            ResourceLocation targetFaction = resolveFaction(target);
            if (targetFaction == null) {
                if (target instanceof LivingEntity le
                        && markForFaction(sl, selected, le)) {
                    feedback(player, sl, target,
                            Component.translatable("msg.monster_conquest.hatred_wand.faction_attacks_target",
                                    FactionDisplay.displayName(selected)));
                } else {
                    player.displayClientMessage(
                            Component.translatable("msg.monster_conquest.hatred_wand.no_faction_units"), true);
                }
                return true;
            }
            if (targetFaction.equals(selected)) {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.hatred_wand.self_hostile"), true);
                return true;
            }
            if (player.getCooldowns().isOnCooldown(this)) return true;
            player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            // 游戏内建立的敌对：重置工具 / 和平模式会清掉（GUI 设置的不会）
            FactionHostility.setHostileLearned(selected, targetFaction);
            feedback(player, sl, target,
                    Component.translatable("msg.monster_conquest.hatred_wand.faction_set_hostile",
                            FactionDisplay.displayName(targetFaction)));
            return true;
        }

        UUID selectedArmy = getSelectedArmy(stack);
        if (selectedArmy == null) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.hatred_wand.select_army_first"), true);
            return true;
        }
        UUID targetArmy = AbstractArmySoldier.resolveArmyUuid(target, player.level());
        if (targetArmy == null) {
            if (target instanceof LivingEntity le
                    && markForArmy(sl, selectedArmy, le)) {
                feedback(player, sl, target,
                        Component.translatable("msg.monster_conquest.hatred_wand.army_attacks_target"));
            } else {
                player.displayClientMessage(
                        Component.translatable("msg.monster_conquest.hatred_wand.no_army_units"), true);
            }
            return true;
        }
        if (targetArmy.equals(selectedArmy)) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.hatred_wand.self_hostile"), true);
            return true;
        }
        if (player.getCooldowns().isOnCooldown(this)) return true;
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        ArmyRelations.setHostile(selectedArmy, targetArmy);

        Entity a = sl.getEntities().get(selectedArmy);
        Entity b = sl.getEntities().get(targetArmy);
        if (a instanceof AbstractArmyCommander cmdA && cmdA.isAlive()
                && target instanceof LivingEntity le && cmdA.canAttack(le)) {
            cmdA.markAsHatred(le);
            for (AbstractArmySoldier s : cmdA.getSoldiers()) {
                if (s.canAttack(le)) s.setTarget(le);
            }
        }
        if (b instanceof AbstractArmyCommander cmdB && cmdB.isAlive()
                && a instanceof LivingEntity aLive && cmdB.canAttack(aLive)) {
            cmdB.markAsHatred(aLive);
        }

        feedback(player, sl, target,
                Component.translatable("msg.monster_conquest.hatred_wand.army_set_hostile"));
        return true;
    }

    private static boolean markForFaction(ServerLevel sl, ResourceLocation faction, LivingEntity target) {
        boolean any = false;
        double range = GeneralConfig.armyFollowRange * 2.0;
        AABB box = target.getBoundingBox().inflate(range);

        for (AbstractArmyCommander c : sl.getEntitiesOfClass(AbstractArmyCommander.class, box)) {
            if (!c.isAlive()) continue;
            if (!faction.equals(c.getFactionId())) continue;
            c.markAsHatred(target);
            any = true;
        }
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive()) continue;
            if (!faction.equals(s.getFactionId())) continue;
            s.setTarget(target);
            any = true;
        }
        return any;
    }

    private static boolean markForArmy(ServerLevel sl, UUID army, LivingEntity target) {
        boolean any = false;
        double range = GeneralConfig.armyFollowRange * 2.0;
        AABB box = target.getBoundingBox().inflate(range);

        Entity cmdEntity = sl.getEntities().get(army);
        if (cmdEntity instanceof AbstractArmyCommander c && c.isAlive()) {
            c.markAsHatred(target);
            any = true;
        }
        // 玩家军队：按当前小分队过滤
        var cmdState = com.ohhapple.monster_conquest.player.PlayerArmyManager
                .getIfPresent(army);
        for (AbstractArmySoldier s : sl.getEntitiesOfClass(AbstractArmySoldier.class, box)) {
            if (!s.isAlive()) continue;
            if (!army.equals(s.getCommanderUuid().orElse(null))) continue;
            if (cmdState != null && !cmdState.matchesSquad(s.getSquadId())) continue;
            s.setTarget(target);
            any = true;
        }
        return any;
    }

    /**
     * 清除"游戏内建立"的仇恨关系：
     *   军队级敌对 + 游戏内建立的阵营敌对 + 玩家军队共享仇恨 + 各单位的当前目标 / 仇恨。
     *   N 键阵营 GUI 设置的关系与默认配置不受影响。
     */
    public static void clearRuntimeHatred(ServerLevel sl) {
        // 1. 清军队级敌对关系（反击 / 控制工具 / 仇恨工具 / RTS 命令建立的都是这一类）
        ArmyRelations.clearAll();

        // 2. 只清"游戏内建立"的阵营敌对；保留 N 键 GUI 设置的关系与默认配置
        FactionHostility.clearLearnedHostility();

        // 3. 清所有玩家的共享仇恨列表（含离线玩家的存档状态）
        for (var st : com.ohhapple.monster_conquest.player.PlayerArmyManager.all().values()) {
            st.clearSharedHatred();
            st.clearSharedHatredEntities();
        }

        // 4. 让所有单位清掉当前目标 / 仇恨 / 追击路线
        ThreatDispatcher.requestHatredReset();
    }

    // ===================== 蹲下重置 =====================
    public void resetHostility(Player player, ServerLevel sl) {
        // 1. 清游戏内建立的仇恨 / 敌对（保留 N 键 GUI 设置与默认配置）
        clearRuntimeHatred(sl);

        // 2. 开启停战窗口（不清飞行物）。
        long nowGT = sl.getGameTime();
        ThreatDispatcher.startTruce(nowGT, ThreatDispatcher.TRUCE_DURATION_TICKS);

        // 3. 反馈
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.hatred_wand.reset_done"), true);
        player.displayClientMessage(
                Component.translatable("msg.monster_conquest.hatred_wand.truce",
                        ThreatDispatcher.TRUCE_DURATION_TICKS / 20),
                true);
        sl.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0f, 1.2f);
    }

    private static void feedback(Player player, ServerLevel sl, Entity target, Component msg) {
        sl.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0f, 0.8f);
        sl.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                target.getX(), target.getY() + target.getBbHeight() * 0.8, target.getZ(),
                8, 0.4, 0.4, 0.4, 0.0);
        player.displayClientMessage(msg, true);
    }

    // ===================== 存取 =====================
    @Nullable public static UUID getSelectedArmy(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID(KEY_SELECTED_ARMY) ? tag.getUUID(KEY_SELECTED_ARMY) : null;
    }

    @Nullable public static ResourceLocation getSelectedFaction(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String s = tag.getString(KEY_SELECTED_FACTION);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        boolean faction = isFactionMode(stack);

        tooltip.add(Component.translatable("item.monster_conquest.hatred_wand.tooltip.left_click",
                        Component.translatable(faction
                                ? "item.monster_conquest.hatred_wand.mode.faction"
                                : "item.monster_conquest.hatred_wand.mode.army"))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.monster_conquest.hatred_wand.tooltip.shift_left_click")
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.monster_conquest.hatred_wand.tooltip.middle_click")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.hatred_wand.tooltip.right_click")
                .withStyle(ChatFormatting.GRAY));

        if (faction) {
            ResourceLocation f = getSelectedFaction(stack);
            tooltip.add(Component.translatable("item.monster_conquest.hatred_wand.tooltip.selected_faction",
                            f == null
                                    ? Component.translatable("item.monster_conquest.hatred_wand.none")
                                    : FactionDisplay.displayName(f))
                    .withStyle(ChatFormatting.GOLD));
        } else {
            UUID a = getSelectedArmy(stack);
            tooltip.add(Component.translatable("item.monster_conquest.hatred_wand.tooltip.selected_army",
                            a == null
                                    ? Component.translatable("item.monster_conquest.hatred_wand.none")
                                    : Component.literal(a.toString().substring(0, 8) + "..."))
                    .withStyle(ChatFormatting.GOLD));
        }
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    /**
     * 该实体能否被仇恨权杖右键标记为敌对。
     * 排除盔甲架、箭 / 抛射物、经验球、掉落物、药水云等特殊实体。
     */
    public static boolean isValidTarget(Entity target) {
        if (target instanceof ArmorStand) return false;
        if (target instanceof AbstractArrow) return false;
        if (target instanceof Projectile) return false;
        if (target instanceof ExperienceOrb) return false;
        if (target instanceof ItemEntity) return false;
        if (target instanceof AreaEffectCloud) return false;
        return target instanceof LivingEntity;
    }
}
