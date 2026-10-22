package com.ohhapple.monster_conquest.event;

import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.army.ShieldGuardController;
import com.ohhapple.monster_conquest.army.ShieldGuardUser;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * 单位盾牌的单一伤害结算入口。
 *
 * <p>单位不通过原版举盾物品状态触发伤害减免，而是在标准
 * {@link LivingShieldBlockEvent} 中按当前姿态、概率和单位属性决定是否挡伤。
 * 盾牌只作为识别条件，不修改实际耐久。</p>
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class ShieldGuardEvents {

    private ShieldGuardEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof ShieldGuardUser guard)) return;
        if (event.getEntity().level().isClientSide) return;

        // 单位盾牌只承担伤害判定，不消耗物品耐久；这条规则也覆盖第三方
        // 通过原版举盾状态触发的格挡。
        event.setShieldDamage(0.0F);

        DamageSource source = event.getDamageSource();
        if (!ShieldGuardController.canBlock(guard, source)) return;

        // 其他模组或原版已经判定为格挡时，不再叠加本模组的概率减伤；
        // 耐久已经在上面统一清零。
        if (event.getBlocked()) {
            return;
        }

        double chance = guard.getShieldGuardChance();
        if (chance <= 0.0D || event.getEntity().getRandom().nextDouble() >= chance) {
            return;
        }

        float incoming = event.getOriginalBlockedDamage();
        double reduction = guard.getShieldGuardReduction();
        double limit = guard.getShieldGuardLimit();
        float blocked = (float) Math.min(incoming * reduction, limit);
        if (blocked <= 0.0F) return;

        event.setBlocked(true);
        event.setBlockedDamage(blocked);
        ShieldGuardController.onBlocked(guard, source);
    }
}
