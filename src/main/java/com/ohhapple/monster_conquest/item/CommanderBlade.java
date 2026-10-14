package com.ohhapple.monster_conquest.item;

import com.ohhapple.monster_conquest.effect.ModEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;

/**
 * 首领专属武器。。
 * 命中会削减目标护甲。
 */
public class CommanderBlade extends SwordItem {

    /** 命中附加的碎甲等级（amplifier 2 = 3 级）与持续时间（8 秒）。 */
    private static final int ARMOR_BREAK_AMPLIFIER = 2;
    private static final int ARMOR_BREAK_DURATION = 160;

    public CommanderBlade(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        applyArmorBreak(target, attacker);
    }

    /** 命中附加效果的统一入口：玩家走物品回调，首领 NPC 的近战逻辑直接调用。 */
    public static void applyArmorBreak(LivingEntity target, Entity source) {
        if (!(target.level() instanceof ServerLevel)) return;
        if (!target.isAlive()) return;
        target.addEffect(new MobEffectInstance(
                ModEffects.ARMOR_BREAK, ARMOR_BREAK_DURATION, ARMOR_BREAK_AMPLIFIER), source);
    }
}
