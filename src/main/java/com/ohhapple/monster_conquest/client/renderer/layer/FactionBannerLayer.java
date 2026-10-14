package com.ohhapple.monster_conquest.client.renderer.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ohhapple.monster_conquest.entity.ManagedUnit;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 阵营旗帜 Layer：胸前 / 后背 / 头顶绘制 4x4 像素的阵营十字。
 *   玩家在单位界面里给某个部位换上了自定义装备时，那个部位的阵营图案不再绘制
 *   （自定义装备有自己的外观，再叠一层阵营色只会显脏）。
 * 绘制细节见 {@link FactionMarkPainter}。
 */
public class FactionBannerLayer<T extends LivingEntity, M extends HumanoidModel<T>>
        extends RenderLayer<T, M> {

    /** 身体图案 z 基准（正面 -z / 背面 +z）。 */
    private static final float BODY_Z = 0.200f;
    /** 头顶图案 y（人类模型头顶表面在 -0.5，外层盔甲更外扩）。 */
    private static final float HEAD_TOP_Y = -0.568f;

    public FactionBannerLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffer, int packedLight,
                       T entity, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        int corner = FactionMarkPainter.cornerColor(entity);
        int cross = FactionMarkPainter.crossColor(entity);
        // 头盔 / 胸甲被玩家手动替换 → 对应部位的阵营图案让位
        boolean bodyMark = true;
        boolean headMark = true;
        if (entity instanceof ManagedUnit unit) {
            bodyMark = !unit.hasManualGear(1);
            headMark = !unit.hasManualGear(0);
        }
        if (bodyMark) {
            FactionMarkPainter.renderBody(pose, buffer, packedLight,
                    this.getParentModel().body, corner, cross, -BODY_Z, BODY_Z);
        }
        if (headMark) {
            FactionMarkPainter.renderHead(pose, buffer, packedLight,
                    this.getParentModel().head, corner, cross, HEAD_TOP_Y);
        }
    }
}
