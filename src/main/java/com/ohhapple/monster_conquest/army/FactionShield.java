package com.ohhapple.monster_conquest.army;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import org.jetbrains.annotations.Nullable;

/**
 * 阵营旗帜盾牌。
 *
 * 士兵 / 首领装备的盾牌用所属阵营的颜色作为底色，并印上与"身上旗帜"相同的
 * 十字图案（FactionBannerLayer 的图案就是十字），这样一眼就能从盾牌分辨阵营。
 *
 * 原版盾牌支持 BASE_COLOR + BANNER_PATTERNS 两个组件（与旗帜同款），
 * 所以不需要额外的模型或贴图，物品栏、手持、背上渲染都会自动生效。
 */
public final class FactionShield {

    private FactionShield() {}

    /** 生成一面带阵营底色 + 十字图案的盾牌。 */
    public static ItemStack create(Level level, @Nullable ResourceLocation factionId) {
        ItemStack shield = new ItemStack(Items.SHIELD);
        apply(shield, level, factionId);
        return shield;
    }

    /** 把阵营图案写到已有盾牌上（用于阵营变化后刷新）。 */
    public static void apply(ItemStack shield, Level level, @Nullable ResourceLocation factionId) {
        if (!shield.is(Items.SHIELD)) return;

        // 与身上渲染的阵营旗使用同一组配色：
        //   底色 = 四角色，十字 = 十字色（两者都是原版染料色 → 完全一致）
        DyeColor base = nearestDye(FactionColors.bannerCornerColor(factionId));
        DyeColor mark = nearestDye(FactionColors.bannerCrossColor(factionId));

        shield.set(DataComponents.BASE_COLOR, base);

        BannerPatternLayers layers = BannerPatternLayers.EMPTY;
        var lookup = level.registryAccess().lookup(Registries.BANNER_PATTERN);
        if (lookup.isPresent()) {
            BannerPatternLayers.Builder builder = new BannerPatternLayers.Builder();
            lookup.get().get(BannerPatterns.STRAIGHT_CROSS).ifPresent(h -> builder.add(h, mark));
            layers = builder.build();
        }
        shield.set(DataComponents.BANNER_PATTERNS, layers);
    }

    /** 原版 16 种染料色里挑最接近的作为底色（盾牌只支持染料色）。 */
    public static DyeColor nearestDye(int rgb) {
        int snapped = FactionColors.snapToDye(rgb);
        for (DyeColor dye : DyeColor.values()) {
            if (dye.getTextureDiffuseColor() == snapped) return dye;
        }
        return DyeColor.WHITE;
    }
}
