package com.ohhapple.monster_conquest.client.gun;

import com.ohhapple.monster_conquest.Monster_conquest;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/** 玩家手持枪械时第三人称采用"举弩"姿势。 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class GunClientExtensions {

    private GunClientExtensions() {}

    @SubscribeEvent
    public static void register(RegisterClientExtensionsEvent event) {
        IClientItemExtensions gunHold = new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        };
        event.registerItem(gunHold,
                Monster_conquest.RIFLE.get(),
                Monster_conquest.SHOTGUN.get(),
                Monster_conquest.SNIPER_RIFLE.get(),
                Monster_conquest.ROCKET_LAUNCHER.get());
    }
}
