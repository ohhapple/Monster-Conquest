package com.ohhapple.monster_conquest.gun;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 玩家可用的枪械。
 *   · 左键        —— 射击（按住连发）
 *   · 右键（狙击）—— 开镜瞄准（望远镜式变焦，散布大幅降低）
 *   · 弹匣打空或一段时间没开枪 —— 自动装填（消耗背包 / 弹药箱里的备弹）
 */
public class GunItem extends Item {

    private final GunSpec spec;

    public GunItem(GunSpec spec, Properties properties) {
        super(properties);
        this.spec = spec;
    }

    public GunSpec spec() {
        return spec;
    }

    /** 是否正在通过右键开镜（仅狙击枪）。 */
    public boolean isScoping(Player player, ItemStack stack) {
        return spec.category() == GunCategory.SNIPER
                && player.isUsingItem()
                && player.getUseItem().is(this);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);

        if (spec.category() == GunCategory.SNIPER) {
            // 右键开镜：必须由物品自己进入"使用中"状态，客户端才会触发变焦与持镜姿势。
            player.startUsingItem(hand);
            if (level instanceof ServerLevel && player instanceof ServerPlayer sp) {
                level.playSound(null, sp.getX(), sp.getEyeY(), sp.getZ(),
                        SoundEvents.SPYGLASS_USE, SoundSource.PLAYERS, 0.9f, 1.0f);
            }
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remainingUseDuration) {
        if (spec.category() != GunCategory.SNIPER) return;
        if (!(living instanceof Player player)) return;
        if (level instanceof ServerLevel) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(),
                    SoundEvents.SPYGLASS_STOP_USING, SoundSource.PLAYERS, 0.8f, 1.0f);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return spec.category() == GunCategory.SNIPER ? 72000 : 0;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // 开镜时不在镜头前渲染物品模型（避免遮挡屏幕）；镜筒界面由 GunScopeHud 绘制。
        return UseAnim.NONE;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(switch (spec.category()) {
            case RIFLE -> "item.monster_conquest.gun.hint.rifle";
            case SHOTGUN -> "item.monster_conquest.gun.hint.shotgun";
            case SNIPER -> "item.monster_conquest.gun.hint.sniper";
            case LAUNCHER -> "item.monster_conquest.gun.hint.launcher";
        }).withStyle(ChatFormatting.GRAY));

        Component ammoName = Component.translatable(spec.ammoType().ammoItem().getDescriptionId());
        tooltip.add(Component.translatable("item.monster_conquest.gun.ammo_line",
                        ammoName, GunCombat.getAmmo(stack, spec), spec.magazineSize())
                .withStyle(ChatFormatting.YELLOW));
        tooltip.add(Component.translatable("item.monster_conquest.gun.stats_line",
                        (int) spec.rangeBlocks(),
                        formatDamage(),
                        Math.round(spec.armorPenetration() * 100.0f))
                .withStyle(ChatFormatting.AQUA));
        if (spec.headshotMultiplier() > 1.0f) {
            tooltip.add(Component.translatable("item.monster_conquest.gun.headshot_line",
                            formatMultiplier(spec.headshotMultiplier()))
                    .withStyle(ChatFormatting.RED));
        }
        if (spec.pellets() > 1) {
            tooltip.add(Component.translatable("item.monster_conquest.gun.pellets_line", spec.pellets())
                    .withStyle(ChatFormatting.AQUA));
        }
        if (spec.hasExplosion()) {
            tooltip.add(Component.translatable("item.monster_conquest.gun.explosion_line",
                            String.format(java.util.Locale.ROOT, "%.1f", spec.explosionRadius()),
                            formatDamage())
                    .withStyle(ChatFormatting.GOLD));
        }
        super.appendHoverText(stack, ctx, tooltip, flag);
    }

    /** 伤害显示：整数不带小数，否则保留一位小数。 */
    private String formatDamage() {
        float damage = spec.damage();
        if (Math.abs(damage - Math.round(damage)) < 0.001f) return String.valueOf(Math.round(damage));
        return String.format(java.util.Locale.ROOT, "%.1f", damage);
    }

    /** 倍率显示：2.0 → "2"，1.5 → "1.5"，1.75 → "1.75"。 */
    private static String formatMultiplier(float multiplier) {
        String text = String.format(java.util.Locale.ROOT, "%.2f", multiplier);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if (text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
