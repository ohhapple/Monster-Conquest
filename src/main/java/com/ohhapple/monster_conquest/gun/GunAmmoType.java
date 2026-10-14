package com.ohhapple.monster_conquest.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 枪械使用的弹药类型。
 * 弹药物品只负责"装填"，发射时统一使用军团子弹 / 火箭弹实体。
 */
public enum GunAmmoType {

    RIFLE_AMMO,
    SHOTGUN_SHELL,
    SNIPER_AMMO,
    ROCKET;

    public Item ammoItem() {
        return switch (this) {
            case RIFLE_AMMO -> Monster_conquest.RIFLE_AMMO.get();
            case SHOTGUN_SHELL -> Monster_conquest.SHOTGUN_SHELL.get();
            case SNIPER_AMMO -> Monster_conquest.SNIPER_AMMO.get();
            case ROCKET -> Monster_conquest.ROCKET_AMMO.get();
        };
    }

    public boolean matches(ItemStack stack) {
        return stack.is(ammoItem());
    }
}
