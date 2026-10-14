package com.ohhapple.monster_conquest.army;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;

/**
 * 阵营长杆旗帜。
 *
 * 外观（长旗杆 + 会飘动的旗面 + 中央十字图案）由客户端渲染器
 * {@code com.ohhapple.monster_conquest.client.renderer.FactionBannerRenderer} 绘制：
 *   · 旗面底色 = {@link FactionColors#bannerCornerColor}
 *   · 十字图案 = {@link FactionColors#bannerCrossColor}
 * 颜色直接取自阵营数据，因此与"身上的阵营旗 / 手里的盾牌图案"始终是同一组颜色。
 */
public final class FactionBanner {

    private FactionBanner() {}

    /** 阵营数据键（与刷怪蛋 / 收纳工具一致）。 */
    public static final String KEY_FACTION = "Faction";

    /** 生成一面带阵营数据的旗帜。 */
    public static ItemStack create(Item item, @Nullable ResourceLocation factionId) {
        ItemStack stack = new ItemStack(item);
        setFaction(stack, factionId);
        return stack;
    }

    public static void setFaction(ItemStack stack, @Nullable ResourceLocation factionId) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (factionId == null) tag.remove(KEY_FACTION);
        else tag.putString(KEY_FACTION, factionId.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Nullable
    public static ResourceLocation factionOf(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String s = tag.getString(KEY_FACTION);
        return s.isEmpty() ? null : ResourceLocation.tryParse(s);
    }
}
