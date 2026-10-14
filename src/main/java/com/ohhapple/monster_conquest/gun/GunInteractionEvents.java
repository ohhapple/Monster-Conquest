package com.ohhapple.monster_conquest.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * 手持枪械时屏蔽近战攻击与挖掘：
 *   · 左键不对实体造成近战伤害
 *   · 左键不开始 / 继续破坏方块
 * （客户端另有 GunAttackSuppressor 屏蔽挥臂动画。）
 */
@EventBusSubscriber(modid = Monster_conquest.MODID)
public final class GunInteractionEvents {

    private GunInteractionEvents() {}

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof GunItem) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().getMainHandItem().getItem() instanceof GunItem) {
            event.setCanceled(true);
        }
    }
}
