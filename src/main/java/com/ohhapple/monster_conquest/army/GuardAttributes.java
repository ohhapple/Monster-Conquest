package com.ohhapple.monster_conquest.army;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 单位盾牌守卫的共享属性。
 *
 * <p>这些值按单位生成，可被等级、状态效果、装备和模组修改器分别调整；
 * 配置里的数值只是生成单位时写入的基础值。</p>
 */
public final class GuardAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(Registries.ATTRIBUTE, Monster_conquest.MODID);

    /**
     * 盾牌默认基线。配置定义、属性注册和实体属性供应商共用这一组值；
     * 运行期实际值仍以配置文件为准，并在单位生成或读档时写入属性基础值。
     */
    public static final double DEFAULT_GUARD_CHANCE = 0.8D;
    public static final double DEFAULT_GUARD_REDUCTION = 1.0D;
    public static final double DEFAULT_GUARD_LIMIT = 20.0D;
    public static final double DEFAULT_GUARD_LIMIT_PER_TIER = 10.0D;

    /** 举盾状态下的随机格挡概率。 */
    public static final DeferredHolder<Attribute, Attribute> SHIELD_GUARD_CHANCE =
            ATTRIBUTES.register("shield_guard_chance",
                    () -> new RangedAttribute(
                            "attribute.monster_conquest.shield_guard_chance",
                            DEFAULT_GUARD_CHANCE, 0.0D, 1.0D));

    /** 格挡成功时对本次伤害应用的减伤比例。1.0 表示按吸收上限全部吸收。 */
    public static final DeferredHolder<Attribute, Attribute> SHIELD_GUARD_REDUCTION =
            ATTRIBUTES.register("shield_guard_reduction",
                    () -> new RangedAttribute(
                            "attribute.monster_conquest.shield_guard_reduction",
                            DEFAULT_GUARD_REDUCTION, 0.0D, 1.0D));

    /** 单次格挡最多吸收的伤害值。 */
    public static final DeferredHolder<Attribute, Attribute> SHIELD_GUARD_LIMIT =
            ATTRIBUTES.register("shield_guard_limit",
                    () -> new RangedAttribute(
                            "attribute.monster_conquest.shield_guard_limit",
                            DEFAULT_GUARD_LIMIT, 0.0D, 1000.0D));

    private GuardAttributes() {}
}
