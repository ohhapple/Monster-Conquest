package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.army.EquipmentTier;
import com.ohhapple.monster_conquest.army.FactionIds;
import com.ohhapple.monster_conquest.army.FactionRegistry;
import com.ohhapple.monster_conquest.army.PlayerFactionRegistry;
import com.ohhapple.monster_conquest.army.SoldierRole;
import com.ohhapple.monster_conquest.army.SoldierType;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.registries.DeferredHolder;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class SoldierSpawnEggItem extends Item {

    private static final String TIER_KEY = "Tier";

    private final DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> typeHolder;
    private final SoldierType defaultType;
    private final EquipmentTier defaultTier;
    private final boolean singleLevel;
    private final boolean canToggleCavalry;

    public SoldierSpawnEggItem(
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
            SoldierType defaultType,
            EquipmentTier defaultTier,
            Properties props) {
        this(type, defaultType, defaultTier, false, true, props);
    }

    public SoldierSpawnEggItem(
            DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
            SoldierType defaultType,
            EquipmentTier defaultTier,
            boolean singleLevel,
            boolean canToggleCavalry,
            Properties props) {
        super(props);
        this.typeHolder = type;
        this.defaultType = defaultType;
        this.defaultTier = defaultTier;
        this.singleLevel = singleLevel;
        this.canToggleCavalry = canToggleCavalry;
    }

    /** 蛋自带的等级（蛋里没有 Tier 数据时用）。 */
    public EquipmentTier getDefaultTier() { return defaultTier; }
    public boolean isSingleLevel() { return singleLevel; }
    public boolean canToggleCavalry() { return canToggleCavalry; }
    public boolean isForEntityType(EntityType<?> type) { return typeHolder.get() == type; }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sl)) return InteractionResult.PASS;

        ItemStack stack = ctx.getItemInHand();
        BlockPos clicked = ctx.getClickedPos();
        Direction face = ctx.getClickedFace();
        // 与原生刷怪蛋一致：点到的方块没有碰撞箱（雪片 / 草丛 / 树苗等）时就放在这一格本身，
        //   否则放在点击面外侧一格 —— 不再要求点击面是完整实心面，避免点草丛放不出单位
        BlockState clickedState = sl.getBlockState(clicked);
        BlockPos pos = clickedState.getCollisionShape(sl, clicked).isEmpty()
                ? clicked
                : clicked.relative(face);

        AbstractArmySoldier soldier = createSoldierFromEgg(sl, stack);
        if (soldier == null) return InteractionResult.PASS;

        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        soldier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);

        ResourceLocation faction = getEggFaction(stack);
        if (faction == null && ctx.getPlayer() != null) {
            // 新蛋（无阵营数据）默认就是放置者自己的玩家阵营。
            //   在服务端按放置者实时解析，多人服务器各自独立。
            faction = PlayerFactionRegistry.factionIdOf(ctx.getPlayer());
        }
        if (faction != null) {
            soldier.setFactionId(faction);
        } else {
            soldier.setFactionId(FactionIds.UNDEAD);
        }

        ResourceLocation effective = soldier.getFactionId();
        if (effective != null && FactionIds.isPlayer(effective)) {
            UUID owner = FactionIds.parsePlayer(effective);
            if (owner != null) {
                FactionRegistry.getOrCreatePlayer(owner);
                soldier.setCommanderUuid(owner);
                // 刷怪蛋刷出的玩家阵营士兵默认无小队（0），
                //   需要时用轮盘「编入附近单兵」或 RTS 命令收编成小队。
                soldier.setSquadId(0);
            }
        }

        sl.addFreshEntity(soldier);
        if (soldier.getSoldierType().isCavalry()) soldier.switchToCavalry();

        sl.playSound(null, pos, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL,
                1.0f, (sl.random.nextFloat() - sl.random.nextFloat()) * 0.2f + 1.0f);

        if (ctx.getPlayer() == null || !ctx.getPlayer().getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /**
     * 刷怪蛋 → 士兵实例：形态 / 等级 / 手动装备都按蛋里的数据还原，等级默认取这颗蛋自己的档位。
     *   不含阵营兜底、也不加进世界 —— 交给调用方决定（刷怪蛋放置 / 刷怪点模板都走这里，
     *   保证两者生成的单位完全一致）。
     */
    @Nullable
    public static AbstractArmySoldier createSoldierFromEgg(ServerLevel level, ItemStack stack) {
        if (!(stack.getItem() instanceof SoldierSpawnEggItem egg)) return null;
        if (!(egg.typeHolder.get().create(level) instanceof AbstractArmySoldier soldier)) return null;

        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        boolean cavalry = egg.canToggleCavalry()
                && tag.getBoolean(RetrievalToolItem.KEY_CAVALRY);
        soldier.setSoldierType(cavalry ? egg.defaultType.toggleMounted() : egg.defaultType);

        int tierIndex = egg.singleLevel ? egg.defaultTier.ordinal()
                : readTierIdx(stack, egg.defaultTier.ordinal());
        EquipmentTier tier = EquipmentTier.values()[tierIndex];
        soldier.setTier(tier);
        soldier.applyEquipment(tier);
        // 蛋里带着收纳 / 升级时保留下来的手动装备：恢复并覆盖默认装备
        soldier.readGearTag(tag);
        soldier.setHealth(soldier.getMaxHealth());
        return soldier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        EquipmentTier tier = EquipmentTier.values()[
                singleLevel ? defaultTier.ordinal() : readTierIdx(stack, defaultTier.ordinal())];

        // 收纳 / 升级保留过装备时给个提示
        if (hasStoredGear(tag)) {
            tooltip.add(Component.translatable("item.monster_conquest.egg.gear_kept")
                    .withStyle(ChatFormatting.AQUA));
        }

        SoldierRole role = defaultType.getRole();
        tooltip.add(Component.translatable("tooltip.monster_conquest.role",
                        Component.translatable("tooltip.monster_conquest.role."
                                + role.name().toLowerCase()))
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        if (singleLevel) {
            tooltip.add(Component.translatable("tooltip.monster_conquest.single_level")
                    .withStyle(ChatFormatting.AQUA));
        } else {
            tooltip.add(Component.translatable("tooltip.monster_conquest.tier",
                            Component.translatable("tooltip.monster_conquest.tier_name."
                                    + tier.name().toLowerCase()))
                    .withStyle(ChatFormatting.AQUA));
        }

        String factionStr = tag.getString(RetrievalToolItem.KEY_FACTION);
        ResourceLocation factionRl = factionStr.isEmpty() ? null : ResourceLocation.tryParse(factionStr);
        if (factionRl != null) {
            if (FactionIds.isPlayer(factionRl)) {
                tooltip.add(Component.translatable("tooltip.monster_conquest.player_faction",
                                playerName(factionRl))
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltip.add(Component.translatable("faction.monster_conquest." + factionRl.getPath())
                        .withStyle(ChatFormatting.GOLD));
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                tooltip.add(Component.translatable("tooltip.monster_conquest.player_faction",
                                mc.player.getGameProfile().getName())
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
            } else {
                tooltip.add(Component.translatable("item.monster_conquest.egg.default_faction")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        if (canToggleCavalry) {
            boolean cavalry = tag.getBoolean(RetrievalToolItem.KEY_CAVALRY);
            tooltip.add(Component.translatable("tooltip.monster_conquest.form",
                            Component.translatable("tooltip.monster_conquest.form."
                                    + (cavalry ? "cavalry" : "infantry")))
                    .withStyle(ChatFormatting.YELLOW));
        }

        appendActionHints(tooltip, tag, factionRl, true, canToggleCavalry);

        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    static void appendActionHints(List<Component> tooltip, CompoundTag tag,
                                  @Nullable ResourceLocation factionRl,
                                  boolean allowFactionSwitch) {
        appendActionHints(tooltip, tag, factionRl, allowFactionSwitch, true);
    }

    static void appendActionHints(List<Component> tooltip, CompoundTag tag,
                                  @Nullable ResourceLocation factionRl,
                                  boolean allowFactionSwitch,
                                  boolean allowCavalrySwitch) {
        Minecraft mc = Minecraft.getInstance();
        boolean clientCreative = mc.player != null && mc.player.isCreative();
        boolean fromTool = RetrievalToolItem.isFromTool(tag);

        if (clientCreative) {
            if (allowCavalrySwitch) {
                tooltip.add(Component.translatable("item.monster_conquest.egg.left_click_cavalry")
                        .withStyle(ChatFormatting.GRAY));
            }
            // 首领 / 巡逻队长刷怪蛋不支持中键切换阵营 → 不显示这条提示
            if (allowFactionSwitch) {
                tooltip.add(Component.translatable("item.monster_conquest.egg.middle_click_faction")
                        .withStyle(ChatFormatting.GRAY));
            }
            return;
        }

        if (!fromTool) {
            tooltip.add(Component.translatable("item.monster_conquest.egg.creative_only_tooltip")
                    .withStyle(ChatFormatting.DARK_RED));
            return;
        }

        if (factionRl != null && FactionIds.isPlayer(factionRl)) {
            UUID owner = FactionIds.parsePlayer(factionRl);
            if (mc.player != null && owner != null && !owner.equals(mc.player.getUUID())) {
                tooltip.add(Component.translatable("item.monster_conquest.egg.take_over_hint")
                        .withStyle(ChatFormatting.YELLOW));
                return;
            }
        }

        tooltip.add(Component.translatable("item.monster_conquest.egg.locked_tooltip")
                .withStyle(ChatFormatting.DARK_RED));
    }

    private static String playerName(ResourceLocation factionId) {
        UUID owner = FactionIds.parsePlayer(factionId);
        if (owner == null) return "?";
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.getConnection() != null) {
                PlayerInfo info = mc.getConnection().getPlayerInfo(owner);
                if (info != null && info.getProfile() != null) {
                    return info.getProfile().getName();
                }
            }
        } catch (Throwable ignored) { }
        String s = owner.toString();
        return s.substring(0, 8);
    }

    private static int readTierIdx(ItemStack stack, int fallback) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        int idx = tag.contains(TIER_KEY) ? tag.getInt(TIER_KEY) : fallback;
        if (idx < 0 || idx >= EquipmentTier.values().length) idx = fallback;
        return idx;
    }

    /** 蛋里是否存过手动装备 / 战术装备（收纳工具或升级保留）。 */
    private static boolean hasStoredGear(CompoundTag tag) {
        return !tag.getList("ManualGear", Tag.TAG_COMPOUND).isEmpty()
                || !tag.getList("ManualTactical", Tag.TAG_COMPOUND).isEmpty();
    }

    public static void writeTierIdxStatic(ItemStack stack, int idx) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        tag.putInt(TIER_KEY, idx);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    public static ResourceLocation getEggFaction(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String s = tag.getString(RetrievalToolItem.KEY_FACTION);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }
}
