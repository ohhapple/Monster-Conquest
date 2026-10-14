package com.ohhapple.monster_conquest.army;

import org.jetbrains.annotations.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;

import java.util.UUID;

/**
 * 阵营配色（服务端 / 客户端共用）。
 *
 * 士兵与首领身上渲染的阵营旗（FactionBannerLayer）和手里盾牌的底色
 * 都从这里取色，保证"身上旗子"和"盾牌图案"完全一致。
 */
public final class FactionColors {

    private FactionColors() {}

    // ===================== 阵营旗帜配色（四角底色 / 十字色） =====================
    //
    // 全部直接使用原版染料色作为取值：
    //   · 盾牌只支持染料色（BASE_COLOR / BANNER_PATTERNS），
    //   · 身上渲染的阵营旗也用同一组数值，
    // 这样"盾牌图案"与"身上渲染"的颜色能够做到完全一致。
    //
    // 指定配色：
    //   亡灵 = 绿四角 + 白十字    村民 = 棕 + 白     中立 = 蓝 + 白
    //   下界 = 红 + 白            怪物 = 黄 + 白     灾厄 = 灰 + 白
    //   末地 = 紫 + 白            玩家 = UUID 前半段四角 / 后半段十字（各自吸附到最近的染料色）

    /** 阵营旗帜的四角底色。 */
    public static int bannerCornerColor(@Nullable ResourceLocation id) {
        // 无阵营：中灰（配白十字看得清）
        if (id == null) return DyeColor.GRAY.getTextureDiffuseColor();
        if (id.equals(FactionIds.UNDEAD))   return DyeColor.GREEN.getTextureDiffuseColor();
        if (id.equals(FactionIds.VILLAGER)) return DyeColor.BROWN.getTextureDiffuseColor();
        if (id.equals(FactionIds.NEUTRAL))  return DyeColor.BLUE.getTextureDiffuseColor();
        if (id.equals(FactionIds.NETHER))   return DyeColor.RED.getTextureDiffuseColor();
        if (id.equals(FactionIds.MONSTER))  return DyeColor.YELLOW.getTextureDiffuseColor();
        if (id.equals(FactionIds.ILLAGER))  return DyeColor.GRAY.getTextureDiffuseColor();
        if (id.equals(FactionIds.END))      return DyeColor.PURPLE.getTextureDiffuseColor();
        UUID owner = FactionIds.parsePlayer(id);
        if (owner != null) {
            // 玩家阵营：UUID 前半段生成四角颜色
            return snapToDye(hashedColor(owner.getMostSignificantBits()));
        }
        int fallback = snapToDye(id.hashCode() & 0xFFFFFF);
        // 未知阵营若刚好吸附到白色，会和白色十字撞色 → 换中灰
        return fallback == DyeColor.WHITE.getTextureDiffuseColor()
                ? DyeColor.GRAY.getTextureDiffuseColor() : fallback;
    }

    /** 阵营旗帜的十字颜色。 */
    public static int bannerCrossColor(@Nullable ResourceLocation id) {
        UUID owner = (id == null) ? null : FactionIds.parsePlayer(id);
        if (owner != null) {
            // 玩家阵营：UUID 后半段生成十字颜色；与四角撞色时退到黑/白保证看得见
            int cross = snapToDye(hashedColor(owner.getLeastSignificantBits()));
            int corner = snapToDye(hashedColor(owner.getMostSignificantBits()));
            if (cross == corner) {
                cross = (cross == DyeColor.WHITE.getTextureDiffuseColor())
                        ? DyeColor.BLACK.getTextureDiffuseColor()
                        : DyeColor.WHITE.getTextureDiffuseColor();
            }
            return cross;
        }
        // NPC 阵营统一白色十字
        return DyeColor.WHITE.getTextureDiffuseColor();
    }

    /** 由 64 位数据（UUID 的一半）映射到一个稳定的高饱和颜色。 */
    private static int hashedColor(long bits) {
        return hsvToRgb(Math.floorMod(bits, 360L), 0.85f, 0.95f);
    }

    /** 把任意 RGB 吸附到最近的染料色（盾牌与身上渲染都用这个值 → 完全一致）。 */
    public static int snapToDye(int rgb) {
        int best = DyeColor.WHITE.getTextureDiffuseColor();
        double bestDist = Double.MAX_VALUE;
        int r0 = (rgb >> 16) & 0xFF;
        int g0 = (rgb >> 8) & 0xFF;
        int b0 = rgb & 0xFF;
        for (DyeColor dye : DyeColor.values()) {
            int c = dye.getTextureDiffuseColor();
            double dr = r0 - ((c >> 16) & 0xFF);
            double dg = g0 - ((c >> 8) & 0xFF);
            double db = b0 - (c & 0xFF);
            double d = dr * dr + dg * dg + db * db;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    /** 阵营主色（旗帜底色、盾牌底色）。 */
    public static int bannerColor(@Nullable ResourceLocation id) {
        return bannerCornerColor(id);
    }

    /** 图案对比色：亮底用深色，暗底用白色。 */
    public static int contrastColor(int c) {
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        int lum = (r * 299 + g * 587 + b * 114) / 1000;
        return lum > 140 ? 0x202020 : 0xFFFFFF;
    }

    /** 玩家阵营：由 UUID 稳定映射到一个色相。 */
    public static int playerColor(UUID uuid) {
        long h = uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits();
        float hue = Math.floorMod(h, 360L);
        return hsvToRgb(hue, 0.85f, 0.95f);
    }

    private static int hsvToRgb(float h, float s, float v) {
        float c = v * s;
        float hh = ((h % 360f) + 360f) % 360f / 60f;
        float x = c * (1f - Math.abs(hh % 2f - 1f));
        float rr, gg, bb;
        if      (hh < 1) { rr = c; gg = x; bb = 0; }
        else if (hh < 2) { rr = x; gg = c; bb = 0; }
        else if (hh < 3) { rr = 0; gg = c; bb = x; }
        else if (hh < 4) { rr = 0; gg = x; bb = c; }
        else if (hh < 5) { rr = x; gg = 0; bb = c; }
        else             { rr = c; gg = 0; bb = x; }
        float m = v - c;
        int ri = Math.round((rr + m) * 255f);
        int gi = Math.round((gg + m) * 255f);
        int bi = Math.round((bb + m) * 255f);
        return (ri << 16) | (gi << 8) | bi;
    }
}
