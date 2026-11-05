package com.ohhapple.monster_conquest.arena.unit;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.SoldierType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * 自走棋弈子的内容定义。每个具体弈子继承本类，并在自己的类中声明卡费和
 * 基础属性；实体类型既可以是复用兵种，也可以是自走棋专属实体。同星级下，
 * 生命、攻击和防御的基础预算先由费用阶梯决定；技能条与技能收益只用于在
 * 该阶梯内形成角色差异，不能以低费技能回填跨费用的基础属性倒挂。
 */
public abstract class ArenaUnit {

    private final String id;
    private final DeferredHolder<EntityType<?>,
            ? extends EntityType<? extends Mob>> entityType;
    private final SoldierType form;
    private final int cost;
    private final ArenaSkillType skillType;
    private final int skillChargeRequired;
    private final double baseHealth;
    private final double baseDamage;
    private final double armor;
    private final double magicResistance;
    private final double attackSpeed;
    private final boolean builtInShield;

    protected ArenaUnit(String id,
                        DeferredHolder<EntityType<?>,
                                ? extends EntityType<? extends Mob>> entityType,
                        SoldierType form, int cost, ArenaSkillType skillType,
                        int skillChargeRequired, double baseHealth,
                        double baseDamage, double armor,
                        double magicResistance, double attackSpeed) {
        this(id, entityType, form, cost, skillType, skillChargeRequired,
                baseHealth, baseDamage, armor, magicResistance,
                attackSpeed, false);
    }

    protected ArenaUnit(String id,
                        DeferredHolder<EntityType<?>,
                                ? extends EntityType<? extends Mob>> entityType,
                        SoldierType form, int cost, ArenaSkillType skillType,
                        int skillChargeRequired, double baseHealth,
                        double baseDamage, double armor,
                        double magicResistance, double attackSpeed,
                        boolean builtInShield) {
        this.id = id;
        this.entityType = entityType;
        this.form = form;
        this.cost = cost;
        this.skillType = skillType;
        this.skillChargeRequired = Math.max(1, skillChargeRequired);
        this.baseHealth = costAdjustedHealth(cost, baseHealth);
        this.baseDamage = costAdjustedAttack(cost, baseDamage);
        this.armor = armor;
        this.magicResistance = magicResistance;
        this.attackSpeed = attackSpeed;
        this.builtInShield = builtInShield;
    }

    public final String id() {
        return id;
    }

    public final DeferredHolder<EntityType<?>,
            ? extends EntityType<? extends Mob>> entityType() {
        return entityType;
    }

    public final SoldierType form() {
        return form;
    }

    public final int cost() {
        return cost;
    }

    public final ArenaSkillType skillType() {
        return skillType;
    }

    public final int skillChargeRequired() {
        return skillChargeRequired;
    }

    public final double baseHealth() {
        return baseHealth;
    }

    public final double baseDamage() {
        return baseDamage;
    }

    /**
     * 费用只负责建立可辨识的基础战斗预算。高费卡仍保持更高生命和攻击，
     * 但差距需要给低费卡的技能、装备与羁绊留下持续发挥作用的空间。
     */
    static double costAdjustedHealth(int cost, double health) {
        double safeHealth = Math.max(1.0, health);
        double multiplier = switch (cost) {
            case 1 -> 1.05;
            case 2 -> 0.95;
            case 3 -> 0.82;
            case 4 -> 0.70;
            case 5 -> 0.64;
            default -> 1.0;
        };
        return Math.round(safeHealth * multiplier);
    }

    /**
     * 所有读取路径统一使用调整后的卡面攻击，攻击型技能也会自然同步。
     */
    static double costAdjustedAttack(int cost, double attack) {
        double safeAttack = Math.max(0.0, attack);
        double multiplier = switch (cost) {
            case 1 -> 1.35;
            case 2 -> 1.18;
            case 4 -> 0.84;
            case 5 -> 0.76;
            default -> 1.0;
        };
        return Math.round(safeAttack * multiplier);
    }

    public final double armor() {
        return armor;
    }

    public final double magicResistance() {
        return magicResistance;
    }

    public final double attackSpeed() {
        return attackSpeed;
    }

    public final boolean builtInShield() {
        return builtInShield;
    }

    public double movementSpeed() {
        return switch (form.getRole()) {
            case MELEE -> 0.30;
            case DUAL -> 0.27;
            default -> 0.24;
        };
    }

    /** 特殊奖励弈子不进入商店刷新候选。 */
    public boolean special() {
        return false;
    }

    public final boolean shopEligible() {
        return !special();
    }

    public final String translationKey() {
        return "entity.monster_conquest." + id;
    }

    public ResourceLocation icon() {
        return customIcon(id + "_tier_0");
    }

    protected final ResourceLocation customIcon(String textureName) {
        return ResourceLocation.fromNamespaceAndPath(
                Monster_conquest.MODID,
                "textures/item/egg/soldier/" + textureName + ".png");
    }
}
