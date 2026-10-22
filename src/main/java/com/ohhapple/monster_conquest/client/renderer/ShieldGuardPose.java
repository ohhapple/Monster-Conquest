package com.ohhapple.monster_conquest.client.renderer;

import com.ohhapple.monster_conquest.army.ShieldGuardUser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/** 把单位同步的举盾姿态映射到人形模型的持盾手臂。 */
public final class ShieldGuardPose {

    private ShieldGuardPose() {}

    public static void apply(HumanoidModel<?> model, LivingEntity entity) {
        if (!(entity instanceof ShieldGuardUser guard) || !guard.isShieldGuardPoseVisible()) {
            return;
        }

        HumanoidArm shieldArm = entity.getMainArm().getOpposite();
        if (shieldArm == HumanoidArm.RIGHT) {
            model.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        } else {
            model.leftArmPose = HumanoidModel.ArmPose.BLOCK;
        }
    }
}
