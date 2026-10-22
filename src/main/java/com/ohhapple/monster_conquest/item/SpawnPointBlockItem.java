package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.block.SpawnPointBlockEntity;
import com.ohhapple.monster_conquest.block.SpawnPointService;
import com.ohhapple.monster_conquest.menu.SpawnPointMenu;
import com.ohhapple.monster_conquest.util.FactionDisplay;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.SimpleMenuProvider;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 刷怪点方块物品：
 *   · 左键（手持时）：打开界面编辑物品里保存的刷怪点配置，改完直接存回物品；
 *   · 悬浮提示列出当前配置，并说明"左键手持物品 / 放下后右键方块"两种编辑方式。
 */
public class SpawnPointBlockItem extends BlockItem {

    public SpawnPointBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** 打开物品里保存的配置界面（左键触发；只有创造模式玩家能编辑）。 */
    public static void openEditor(ServerPlayer player, InteractionHand hand) {
        // 刷怪点是配置型设施：只有创造模式玩家能编辑配置
        if (!player.isCreative()) {
            player.displayClientMessage(
                    Component.translatable("msg.monster_conquest.spawn_point.creative_only"), true);
            return;
        }
        int mode = hand == InteractionHand.OFF_HAND
                ? SpawnPointMenu.MODE_OFF_HAND
                : SpawnPointMenu.MODE_MAIN_HAND;
        player.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new SpawnPointMenu(id, inventory, mode),
                        Component.translatable("gui.monster_conquest.spawn_point.title")),
                buf -> buf.writeVarInt(mode));
    }

    // ===================== 悬浮提示 =====================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getOrDefault(DataComponents.BLOCK_ENTITY_DATA, CustomData.EMPTY).copyTag();

        if (tag.isEmpty()) {
            tooltip.add(Component.translatable("item.monster_conquest.spawn_point.unconfigured")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            appendConfig(tooltip, tag);
        }

        tooltip.add(Component.translatable("item.monster_conquest.spawn_point.hint_edit")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("item.monster_conquest.spawn_point.hint_place")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    /** 把物品里保存的配置逐项列出来。 */
    private static void appendConfig(List<Component> tooltip, CompoundTag tag) {
        boolean enabled = !tag.contains("Enabled") || tag.getBoolean("Enabled");
        tooltip.add(field("item.monster_conquest.spawn_point.switch",
                        Component.translatable(enabled
                                ? "item.monster_conquest.spawn_point.value.on"
                                : "item.monster_conquest.spawn_point.value.off"))
                .withStyle(ChatFormatting.GOLD));

        int unitIndex = tag.contains("UnitIndex") ? tag.getInt("UnitIndex") : SpawnPointBlockEntity.NO_UNIT;
        ItemStack egg = readEgg(tag);
        if (!egg.isEmpty()) {
            tooltip.add(field("item.monster_conquest.spawn_point.value.egg", egg.getHoverName())
                    .withStyle(ChatFormatting.AQUA));
            return;
        }

        var unit = SpawnPointService.unitAt(unitIndex);
        if (unit == null) {
            tooltip.add(Component.translatable("gui.monster_conquest.spawn_point.status.no_unit")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(field("item.monster_conquest.spawn_point.value.unit",
                            Component.translatable(unit.get().getDescriptionId()))
                    .withStyle(ChatFormatting.AQUA));
        }

        // 阵营（首领 / 巡逻队长用自带阵营）· 形态 · 等级
        ResourceLocation fixed = SpawnPointService.fixedFactionOf(unitIndex);
        ResourceLocation faction = fixed != null ? fixed : parseFaction(tag);
        if (faction != null) {
            MutableComponent stats = field("gui.monster_conquest.spawn_point.faction",
                    FactionDisplay.displayNameOrId(faction));
            stats.append(Component.literal("   ")).append(field(
                    "gui.monster_conquest.spawn_point.form",
                    Component.translatable(tag.getBoolean("Mounted")
                            ? "gui.monster_conquest.spawn_point.form.cavalry"
                            : "gui.monster_conquest.spawn_point.form.infantry")));
            if (!SpawnPointService.isCommanderIndex(unitIndex)) {
                stats.append(Component.literal("   ")).append(field(
                        "gui.monster_conquest.spawn_point.tier",
                        Component.literal("T" + Math.max(0, tag.getInt("Tier")))));
            }
            tooltip.add(stats);
        }

        tooltip.add(join(
                field("gui.monster_conquest.spawn_point.batch",
                        Component.literal(String.valueOf(Math.max(0, tag.getInt("Batch"))))),
                field("gui.monster_conquest.spawn_point.interval",
                        intervalText(tag.getInt("Interval") / 20))));
        tooltip.add(join(
                field("gui.monster_conquest.spawn_point.max_total", limitText(tag.getInt("MaxTotal"))),
                field("gui.monster_conquest.spawn_point.max_alive", limitText(tag.getInt("MaxConcurrent")))));
    }

    /** 一行"标签：值"。 */
    private static MutableComponent field(String labelKey, Component value) {
        return Component.translatable("item.monster_conquest.spawn_point.line",
                Component.translatable(labelKey), value);
    }

    private static MutableComponent join(Component... parts) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) out.append(Component.literal("   "));
            out.append(parts[i]);
        }
        return out;
    }

    /** 间隔：0 = 无限长（不刷）。 */
    private static Component intervalText(int seconds) {
        return seconds <= 0
                ? Component.translatable("gui.monster_conquest.spawn_point.unlimited")
                : Component.translatable("gui.monster_conquest.spawn_point.seconds", seconds);
    }

    private static Component limitText(int value) {
        return value < 0
                ? Component.translatable("gui.monster_conquest.spawn_point.unlimited")
                : Component.literal(String.valueOf(value));
    }

    /** 物品里保存的模板蛋；没有返回空栈。 */
    private static ItemStack readEgg(CompoundTag tag) {
        return tag.contains("Egg")
                ? ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, tag.get("Egg"))
                        .result().orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Nullable
    private static ResourceLocation parseFaction(CompoundTag tag) {
        String raw = tag.getString("Faction");
        return raw.isEmpty() ? null : ResourceLocation.tryParse(raw);
    }
}
