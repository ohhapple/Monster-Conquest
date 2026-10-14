package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ohhapple.monster_conquest.entity.projectile.ArmyRocket;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * 火箭弹实体渲染：仍使用物品模型，但加上与子弹一致的近距离隐藏保护。
 *
 * 只隐藏"弹体模型"；火箭弹的烟雾 / 火焰拖尾是服务端粒子，
 *   不经过实体渲染器，所以近距离依然能看到烟雾轨迹。
 */
public class ArmyRocketRenderer extends ThrownItemRenderer<ArmyRocket> {

    /** 距摄像机多近就不渲染（格）——与子弹统一为 8 格。 */
    private static final double MIN_CAMERA_DISTANCE_SQ = 8.0 * 8.0;

    public ArmyRocketRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, 0.7f, false);
    }

    @Override
    public void render(ArmyRocket rocket, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int light) {
        // 与子弹相同的纯距离判断（不依赖 tickCount）：8 格内一律不绘制弹体，
        // 避免发射瞬间挡住视野；飞出 8 格后照常显示。
        Vec3 cameraPos = this.entityRenderDispatcher.camera.getPosition();
        double x = Mth.lerp(partialTicks, rocket.xo, rocket.getX());
        double y = Mth.lerp(partialTicks, rocket.yo, rocket.getY());
        double z = Mth.lerp(partialTicks, rocket.zo, rocket.getZ());
        if (cameraPos.distanceToSqr(x, y, z) < MIN_CAMERA_DISTANCE_SQ) {
            return;
        }
        super.render(rocket, entityYaw, partialTicks, poseStack, buffer, light);
    }
}
