package com.ohhapple.monster_conquest.item;

import net.minecraft.world.item.Item;

/**
 * 阵营长杆旗帜：形状由 {@code models/item/faction_banner.json} 决定，
 * 渲染器把旗面换成随风飘动并按阵营配色的布面（渲染器注册见 ModClientEvents）。
 */
public class FactionBannerItem extends Item {

    public FactionBannerItem(Properties properties) {
        super(properties);
    }
}
