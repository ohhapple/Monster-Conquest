package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.arena.entity.ArenaElderGuardian;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ArenaLaserGuardianRenderer extends
        MobRenderer<ArenaElderGuardian, ArenaLaserGuardianRenderer.Model> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/guardian_elder.png");
    private static final ResourceLocation BEAM_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/guardian_beam.png");
    private static final RenderType BEAM_RENDER_TYPE =
            RenderType.entityCutoutNoCull(BEAM_TEXTURE);

    public ArenaLaserGuardianRenderer(
            EntityRendererProvider.Context context) {
        super(context, new Model(context.bakeLayer(
                ModelLayers.ELDER_GUARDIAN)), 1.2f);
    }

    @Override
    protected void scale(ArenaElderGuardian guardian,
                         PoseStack pose, float partialTick) {
        pose.scale(1.8f, 1.8f, 1.8f);
    }

    @Override
    public boolean shouldRender(ArenaElderGuardian guardian,
                                Frustum frustum,
                                double cameraX, double cameraY,
                                double cameraZ) {
        if (super.shouldRender(guardian, frustum,
                cameraX, cameraY, cameraZ)) {
            return true;
        }
        if (!guardian.hasVisibleLaser()) return false;
        Vec3 start = new Vec3(
                guardian.getX(),
                guardian.getY() + guardian.getEyeHeight(),
                guardian.getZ());
        Vec3 end = guardian.getLaserEnd();
        double padding = guardian.isLargeLaser()
                ? 1.0 : 0.5;
        return frustum.isVisible(
                new AABB(start, end).inflate(padding));
    }

    @Override
    public ResourceLocation getTextureLocation(
            ArenaElderGuardian guardian) {
        return TEXTURE;
    }

    @Override
    public void render(ArenaElderGuardian guardian,
                       float entityYaw, float partialTick,
                       PoseStack pose, MultiBufferSource buffers,
                       int packedLight) {
        super.render(guardian, entityYaw, partialTick,
                pose, buffers, packedLight);
        if (!guardian.hasVisibleLaser()) return;

        double eyeHeight = guardian.getEyeHeight();
        Vec3 start = new Vec3(
                Mth.lerp(partialTick, guardian.xOld,
                        guardian.getX()),
                Mth.lerp(partialTick, guardian.yOld,
                        guardian.getY()) + eyeHeight,
                Mth.lerp(partialTick, guardian.zOld,
                        guardian.getZ()));
        Vec3 delta = guardian.getLaserEnd().subtract(start);
        if (delta.lengthSqr() < 1.0E-6) return;

        pose.pushPose();
        pose.translate(0.0, eyeHeight, 0.0);
        float length = (float) delta.length() + 1.0f;
        Vec3 direction = delta.normalize();
        float verticalAngle = (float) Math.acos(direction.y);
        float horizontalAngle =
                (float) Math.atan2(direction.z, direction.x);
        pose.mulPose(Axis.YP.rotationDegrees(
                ((float) (Math.PI / 2.0) - horizontalAngle)
                        * (180.0f / (float) Math.PI)));
        pose.mulPose(Axis.XP.rotationDegrees(
                verticalAngle * (180.0f / (float) Math.PI)));

        float width = guardian.isLargeLaser()
                ? 0.78f : 0.25f;
        float scroll = (guardian.tickCount + partialTick)
                * 0.05f * -1.5f;
        float nearU = 0.4999f;
        float farU = 0.0f;
        float nearV = -1.0f
                + (guardian.tickCount + partialTick)
                * 0.5f % 1.0f;
        int red = guardian.isLargeLaser()
                ? 96 : 64;
        int green = guardian.isLargeLaser()
                ? 255 : 192;
        int blue = guardian.isLargeLaser()
                ? 255 : 192;
        VertexConsumer vertices = buffers.getBuffer(
                BEAM_RENDER_TYPE);
        PoseStack.Pose last = pose.last();

        float a = Mth.cos(scroll + (float)
                (Math.PI * 3.0 / 4.0)) * width;
        float b = Mth.sin(scroll + (float)
                (Math.PI * 3.0 / 4.0)) * width;
        float c = Mth.cos(scroll + (float)
                (Math.PI / 4.0)) * width;
        float d = Mth.sin(scroll + (float)
                (Math.PI / 4.0)) * width;
        float e = Mth.cos(scroll + (float)
                (Math.PI * 5.0 / 4.0)) * width;
        float f = Mth.sin(scroll + (float)
                (Math.PI * 5.0 / 4.0)) * width;
        float g = Mth.cos(scroll + (float)
                (Math.PI * 7.0 / 4.0)) * width;
        float h = Mth.sin(scroll + (float)
                (Math.PI * 7.0 / 4.0)) * width;
        float inner = width * 0.71f;
        float i = Mth.cos(scroll + (float) Math.PI) * inner;
        float j = Mth.sin(scroll + (float) Math.PI) * inner;
        float k = Mth.cos(scroll) * inner;
        float l = Mth.sin(scroll) * inner;
        float m = Mth.cos(scroll + (float)
                (Math.PI / 2.0)) * inner;
        float n = Mth.sin(scroll + (float)
                (Math.PI / 2.0)) * inner;
        float o = Mth.cos(scroll + (float)
                (Math.PI * 3.0 / 2.0)) * inner;
        float p = Mth.sin(scroll + (float)
                (Math.PI * 3.0 / 2.0)) * inner;
        float farV = length * 2.5f + nearV;

        vertex(vertices, last, i, length, j,
                red, green, blue, nearU, farV);
        vertex(vertices, last, i, 0.0f, j,
                red, green, blue, nearU, nearV);
        vertex(vertices, last, k, 0.0f, l,
                red, green, blue, 0.0f, nearV);
        vertex(vertices, last, k, length, l,
                red, green, blue, 0.0f, farV);
        vertex(vertices, last, m, length, n,
                red, green, blue, nearU, farV);
        vertex(vertices, last, m, 0.0f, n,
                red, green, blue, nearU, nearV);
        vertex(vertices, last, o, 0.0f, p,
                red, green, blue, 0.0f, nearV);
        vertex(vertices, last, o, length, p,
                red, green, blue, 0.0f, farV);

        if (guardian.isLargeLaser()) {
            vertex(vertices, last, a, length, b,
                    red, green, blue, 0.5f, farV);
            vertex(vertices, last, c, length, d,
                    red, green, blue, 1.0f, farV);
            vertex(vertices, last, g, length, h,
                    red, green, blue, 1.0f, nearV);
            vertex(vertices, last, e, length, f,
                    red, green, blue, 0.5f, nearV);
        }
        pose.popPose();
    }

    private static void vertex(
            VertexConsumer vertices, PoseStack.Pose pose,
            float x, float y, float z,
            int red, int green, int blue,
            float u, float v) {
        vertices.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(15728880)
                .setNormal(pose, 0.0f, 1.0f, 0.0f);
    }

    public static final class Model
            extends HierarchicalModel<ArenaElderGuardian> {
        private static final float[] SPIKE_X_ROT =
                {1.75f, 0.25f, 0.0f, 0.0f, 0.5f, 0.5f,
                        0.5f, 0.5f, 1.25f, 0.75f, 0.0f, 0.0f};
        private static final float[] SPIKE_Y_ROT =
                {0.0f, 0.0f, 0.0f, 0.0f, 0.25f, 1.75f,
                        1.25f, 0.75f, 0.0f, 0.0f, 0.0f, 0.0f};
        private static final float[] SPIKE_Z_ROT =
                {0.0f, 0.0f, 0.25f, 1.75f, 0.0f, 0.0f,
                        0.0f, 0.0f, 0.0f, 0.0f, 0.75f, 1.25f};
        private static final float[] SPIKE_X =
                {0.0f, 0.0f, 8.0f, -8.0f, -8.0f, 8.0f,
                        8.0f, -8.0f, 0.0f, 0.0f, 8.0f, -8.0f};
        private static final float[] SPIKE_Y =
                {-8.0f, -8.0f, -8.0f, -8.0f, 0.0f, 0.0f,
                        0.0f, 0.0f, 8.0f, 8.0f, 8.0f, 8.0f};
        private static final float[] SPIKE_Z =
                {8.0f, -8.0f, 0.0f, 0.0f, -8.0f, -8.0f,
                        8.0f, 8.0f, 8.0f, -8.0f, 0.0f, 0.0f};

        private final ModelPart root;
        private final ModelPart head;
        private final ModelPart eye;
        private final ModelPart[] spikes = new ModelPart[12];
        private final ModelPart[] tails = new ModelPart[3];

        private Model(ModelPart root) {
            this.root = root;
            this.head = root.getChild("head");
            this.eye = head.getChild("eye");
            for (int i = 0; i < spikes.length; i++) {
                spikes[i] = head.getChild("spike" + i);
            }
            tails[0] = head.getChild("tail0");
            tails[1] = tails[0].getChild("tail1");
            tails[2] = tails[1].getChild("tail2");
        }

        @Override
        public ModelPart root() {
            return root;
        }

        @Override
        public void setupAnim(ArenaElderGuardian guardian,
                              float limbSwing, float limbSwingAmount,
                              float ageInTicks, float netHeadYaw,
                              float headPitch) {
            head.yRot = netHeadYaw * Mth.DEG_TO_RAD;
            head.xRot = headPitch * Mth.DEG_TO_RAD;
            float spikePhase = ageInTicks * 0.08f;
            float spikePull = guardian.hasVisibleLaser()
                    ? 0.18f : 0.55f;
            float spikeAmount =
                    (1.0f - spikePull) * 0.55f;
            for (int i = 0; i < spikes.length; i++) {
                float offset = spikeOffset(
                        i, spikePhase, spikeAmount);
                spikes[i].x = SPIKE_X[i] * offset;
                spikes[i].y = 16.0f + SPIKE_Y[i] * offset;
                spikes[i].z = SPIKE_Z[i] * offset;
            }
            if (guardian.hasVisibleLaser()) {
                double relativeY = guardian.getLaserEnd().y
                        - (guardian.getY()
                        + guardian.getEyeHeight());
                eye.y = relativeY > 0.0 ? 0.0f : 1.0f;
                eye.x = 0.0f;
            }
            for (int i = 0; i < tails.length; i++) {
                tails[i].yRot = Mth.sin(
                        ageInTicks * 0.067f + i * 0.4f)
                        * (float) Math.PI * (0.05f * (i + 1));
            }
        }

        private static float spikeOffset(
                int index, float phase, float pull) {
            return 1.0f + Mth.cos(
                    phase * 1.5f + index) * 0.01f - pull;
        }
    }
}
