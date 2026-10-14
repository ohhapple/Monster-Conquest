package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

public enum EquipmentTier {

    // 参数：护具材质, 步兵剑, 最小附魔等级, 最大附魔等级, 最大生命值, 攻击力, 达到本级的累计tick
    TIER_0(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
            Items.WOODEN_SWORD, 0, 0, 20.0, 3.0, 0),
    TIER_1(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS,
            Items.STONE_SWORD, 1, 3, 30.0, 4.5, 6000),
    TIER_2(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS,
            Items.IRON_SWORD, 2, 8, 40.0, 6.0, 18000),
    TIER_3(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS,
            Items.DIAMOND_SWORD, 5, 14, 55.0, 8.0, 36000),
    TIER_4(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS,
            Items.NETHERITE_SWORD, 8, 20, 80.0, 11.0, 60000);

    private final Item helmet, chestplate, leggings, boots, weapon;
    private final int minEnchant;
    private final int maxEnchant;
    private final double maxHealth;
    private final double attackDamage;
    private final int upgradeAtTick;

    EquipmentTier(Item helmet, Item chestplate, Item leggings, Item boots, Item weapon,
                  int minEnchant, int maxEnchant, double maxHealth, double attackDamage, int upgradeAtTick) {
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
        this.weapon = weapon;
        this.minEnchant = minEnchant;
        this.maxEnchant = maxEnchant;
        this.maxHealth = maxHealth;
        this.attackDamage = attackDamage;
        this.upgradeAtTick = upgradeAtTick;
    }

    public Item getHelmet()      { return helmet; }
    public Item getChestplate()  { return chestplate; }
    public Item getLeggings()    { return leggings; }
    public Item getBoots()       { return boots; }
    public Item getWeapon()      { return weapon; }
    public int getMinEnchant()   { return minEnchant; }
    public int getMaxEnchant()   { return maxEnchant; }
    public double getMaxHealth() { return maxHealth; }
    public double getAttackDamage() { return attackDamage; }
    public int getUpgradeAtTick(){ return upgradeAtTick; }

    @Nullable
    public EquipmentTier getNext() {
        int next = ordinal() + 1;
        return next < values().length ? values()[next] : null;
    }

    /** 马铠：等级越高品质越好。
     *   TIER_0/TIER_1 → 铁
     *   TIER_2        → 金
     *   TIER_3/TIER_4 → 钻石
     */
    public Item getHorseArmor() {
        return switch (this) {
            case TIER_0, TIER_1 -> Items.IRON_HORSE_ARMOR;
            case TIER_2 -> Items.GOLDEN_HORSE_ARMOR;
            case TIER_3, TIER_4 -> Items.DIAMOND_HORSE_ARMOR;
        };
    }

    /** 近战骑兵骑枪：低阶用铁，中阶用钻石，高阶用下界合金。 */
    public Item getLanceWeapon() {
        return switch (this) {
            case TIER_0, TIER_1 -> Monster_conquest.IRON_LANCE.get();
            case TIER_2, TIER_3 -> Monster_conquest.DIAMOND_LANCE.get();
            case TIER_4 -> Monster_conquest.NETHERITE_LANCE.get();
        };
    }

}