package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ohhapple.monster_conquest.arena.entity.ArenaEnderman;
import com.ohhapple.monster_conquest.arena.entity.ArenaWarden;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import net.minecraft.client.model.EndermanModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EnderEyesLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.Map;
import java.util.WeakHashMap;

public final class ArenaGreaterMobRenderers {

    private static final ResourceLocation ENDERMAN_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/enderman/enderman.png");
    private static final ResourceLocation WARDEN_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/warden/warden.png");

    private ArenaGreaterMobRenderers() {
    }

    public static final class ArenaEndermanRenderer
            extends MobRenderer<ArenaEnderman,
            EndermanModel<ArenaEnderman>> {
        public ArenaEndermanRenderer(
                EntityRendererProvider.Context context) {
            super(context, new EndermanModel<>(
                    context.bakeLayer(ModelLayers.ENDERMAN)),
                    0.5f);
            addLayer(new EnderEyesLayer<>(this));
        }

        @Override
        public ResourceLocation getTextureLocation(
                ArenaEnderman entity) {
            return ENDERMAN_TEXTURE;
        }
    }

    public static final class ArenaWardenRenderer
            extends ProxyRenderer<ArenaWarden, Warden> {
        private final Map<ArenaWarden, Integer> attackStarts =
                new WeakHashMap<>();
        private final Map<ArenaWarden, Integer> sonicStarts =
                new WeakHashMap<>();

        public ArenaWardenRenderer(
                EntityRendererProvider.Context context) {
            super(context, EntityType.WARDEN,
                    new net.minecraft.client.renderer.entity
                            .WardenRenderer(context),
                    WARDEN_TEXTURE, 0.9f);
        }

        @Override
        protected void prepareDelegate(
                ArenaWarden entity, Warden dummy,
                float partialTick) {
            int attackStart =
                    entity.attackAnimationStartTick();
            if (entity.isAttackAnimationActive()) {
                if (!attackStarts.containsKey(entity)
                        || attackStarts.get(entity) != attackStart) {
                    attackStarts.put(entity, attackStart);
                    dummy.attackAnimationState.start(
                            attackStart);
                }
            } else {
                dummy.attackAnimationState.stop();
                attackStarts.remove(entity);
            }
            int sonicStart =
                    entity.sonicAnimationStartTick();
            if (entity.isSonicAnimationActive()) {
                if (!sonicStarts.containsKey(entity)
                        || sonicStarts.get(entity) != sonicStart) {
                    sonicStarts.put(entity, sonicStart);
                    dummy.sonicBoomAnimationState.start(
                            sonicStart);
                }
            } else {
                dummy.sonicBoomAnimationState.stop();
                sonicStarts.remove(entity);
            }
        }
    }

    private abstract static class ProxyRenderer<
            A extends AbstractArmySoldier,
            V extends Mob> extends EntityRenderer<A> {
        private final EntityType<V> dummyType;
        private final EntityRenderer<? super V> delegate;
        private final ResourceLocation texture;
        private final Map<A, V> dummies = new WeakHashMap<>();

        private ProxyRenderer(
                EntityRendererProvider.Context context,
                EntityType<V> dummyType,
                EntityRenderer<? super V> delegate,
                ResourceLocation texture,
                float shadowRadius) {
            super(context);
            this.dummyType = dummyType;
            this.delegate = delegate;
            this.texture = texture;
            this.shadowRadius = shadowRadius;
        }

        @Override
        public void render(A entity, float entityYaw,
                           float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            V dummy = dummies.get(entity);
            if (dummy == null
                    || dummy.level() != entity.level()) {
                dummy = dummyType.create(entity.level());
                if (dummy != null) dummies.put(entity, dummy);
            }
            if (dummy == null) return;
            syncCommon(entity, dummy);
            prepareDelegate(entity, dummy, partialTick);
            delegate.render(dummy, entityYaw, partialTick,
                    pose, buffers, light);
            ArenaUnitHealthRenderer.renderOverlay(
                    entity, pose, buffers);
        }

        @Override
        public ResourceLocation getTextureLocation(A entity) {
            return texture;
        }

        protected void prepareDelegate(
                A entity, V dummy, float partialTick) {
        }
    }

    private static void syncCommon(Mob source, Mob proxy) {
        proxy.moveTo(source.getX(), source.getY(), source.getZ(),
                source.getYRot(), source.getXRot());
        proxy.setOldPosAndRot();
        int elapsedTicks = source.tickCount - proxy.tickCount;
        if (elapsedTicks > 0) {
            float targetSpeed = source.walkAnimation.speed();
            int steps = Math.min(elapsedTicks, 4);
            for (int i = 0; i < steps; i++) {
                proxy.walkAnimation.update(targetSpeed, 1.0f);
            }
        } else {
            proxy.walkAnimation.setSpeed(
                    source.walkAnimation.speed());
        }
        proxy.tickCount = source.tickCount;
        proxy.setYHeadRot(source.getYHeadRot());
        proxy.yHeadRotO = source.yHeadRotO;
        proxy.yBodyRot = source.yBodyRot;
        proxy.yBodyRotO = source.yBodyRotO;
        proxy.setDeltaMovement(source.getDeltaMovement());
        proxy.setOnGround(source.onGround());
        proxy.setShiftKeyDown(source.isShiftKeyDown());
        proxy.swingTime = source.swingTime;
        proxy.swinging = source.swinging;
        proxy.swingingArm = source.swingingArm;
        proxy.attackAnim = source.attackAnim;
        proxy.oAttackAnim = source.oAttackAnim;
        proxy.hurtTime = source.hurtTime;
        proxy.hurtDuration = source.hurtDuration;
        proxy.xxa = source.xxa;
        proxy.yya = source.yya;
        proxy.zza = source.zza;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            proxy.setItemSlot(slot, source.getItemBySlot(slot));
        }
    }
}
