package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.ohhapple.monster_conquest.arena.entity.ArenaCow;
import com.ohhapple.monster_conquest.arena.entity.ArenaCreeper;
import com.ohhapple.monster_conquest.arena.entity.ArenaDonkey;
import com.ohhapple.monster_conquest.arena.entity.ArenaFox;
import com.ohhapple.monster_conquest.arena.entity.ArenaFrog;
import com.ohhapple.monster_conquest.arena.entity.ArenaPig;
import com.ohhapple.monster_conquest.arena.entity.ArenaPanda;
import com.ohhapple.monster_conquest.arena.entity.ArenaRabbit;
import com.ohhapple.monster_conquest.arena.entity.ArenaSheep;
import com.ohhapple.monster_conquest.arena.entity.ArenaZombifiedPiglin;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.arena.game.ArenaStatusFlags;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.CowRenderer;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.ChestedHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FoxRenderer;
import net.minecraft.client.renderer.entity.FrogRenderer;
import net.minecraft.client.renderer.entity.PigRenderer;
import net.minecraft.client.renderer.entity.PiglinRenderer;
import net.minecraft.client.renderer.entity.RabbitRenderer;
import net.minecraft.client.renderer.entity.SheepRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.horse.Donkey;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Panda;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ZombifiedPiglin;

import java.util.Map;
import java.util.WeakHashMap;

/** Reuses vanilla animal renderers without making arena units vanilla mobs. */
public final class ArenaAnimalRenderers {

    private static final ResourceLocation RABBIT_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/rabbit/brown.png");
    private static final ResourceLocation DONKEY_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/horse/donkey.png");
    private static final ResourceLocation SHEEP_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/sheep/sheep.png");
    private static final ResourceLocation COW_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/cow/cow.png");
    private static final ResourceLocation PIG_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/pig/pig.png");
    private static final ResourceLocation PANDA_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/panda/panda.png");
    private static final ResourceLocation ZOMBIFIED_PIGLIN_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/piglin/zombified_piglin.png");
    private static final ResourceLocation FROG_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/frog/temperate_frog.png");
    private static final ResourceLocation FOX_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/fox/fox.png");
    private static final ResourceLocation CREEPER_TEXTURE =
            ResourceLocation.withDefaultNamespace(
                    "textures/entity/creeper/creeper.png");

    private ArenaAnimalRenderers() {
    }

    private abstract static class DelegatingRenderer<
            A extends AbstractArmySoldier,
            V extends Mob> extends EntityRenderer<A> {
        private final EntityType<V> dummyType;
        private final EntityRenderer<? super V> delegate;
        private final ResourceLocation texture;
        private final Map<A, V> dummies = new WeakHashMap<>();

        private DelegatingRenderer(
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

        protected V dummy(A entity) {
            V dummy = dummies.get(entity);
            if (dummy == null || dummy.level() != entity.level()) {
                dummy = dummyType.create(entity.level());
                if (dummy != null) dummies.put(entity, dummy);
            }
            if (dummy == null) {
                throw new IllegalStateException(
                        "Could not create renderer proxy "
                                + dummyType);
            }
            syncCommon(entity, dummy);
            return dummy;
        }

        protected void applyScale(
                A entity, PoseStack pose, float partialTick) {
        }

        protected void applyAnimationTransform(
                A entity, PoseStack pose, float partialTick) {
        }

        @Override
        public void render(A entity, float entityYaw,
                           float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            V proxy = dummy(entity);
            pose.pushPose();
            applyScale(entity, pose, partialTick);
            applyAnimationTransform(entity, pose, partialTick);
            delegate.render(proxy, entityYaw, partialTick,
                    pose, buffers, light);
            pose.popPose();
            ArenaUnitHealthRenderer.renderOverlay(
                    entity, pose, buffers);
        }

        @Override
        public ResourceLocation getTextureLocation(A entity) {
            return texture;
        }
    }

    public static final class RabbitRenderer
            extends DelegatingRenderer<ArenaRabbit, Rabbit> {
        public RabbitRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.RABBIT,
                    new net.minecraft.client.renderer.entity
                            .RabbitRenderer(context),
                    RABBIT_TEXTURE, 0.4f);
        }

        @Override
        protected void applyScale(
                ArenaRabbit entity, PoseStack pose,
                float partialTick) {
            pose.scale(1.55f, 1.55f, 1.55f);
        }
    }

    public static final class DonkeyRenderer
            extends DelegatingRenderer<ArenaDonkey, Donkey> {
        public DonkeyRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.DONKEY,
                    new ChestedHorseRenderer<Donkey>(
                            context, 1.0f, ModelLayers.DONKEY),
                    DONKEY_TEXTURE, 0.5f);
        }
    }

    public static final class SheepRenderer
            extends DelegatingRenderer<ArenaSheep, Sheep> {
        public SheepRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.SHEEP,
                    new net.minecraft.client.renderer.entity
                            .SheepRenderer(context),
                    SHEEP_TEXTURE, 0.7f);
        }
    }

    public static final class CowRenderer
            extends DelegatingRenderer<ArenaCow, Cow> {
        public CowRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.COW,
                    new net.minecraft.client.renderer.entity
                            .CowRenderer(context),
                    COW_TEXTURE, 0.7f);
        }
    }

    public static final class ZombifiedPiglinRenderer
            extends DelegatingRenderer<
            ArenaZombifiedPiglin, ZombifiedPiglin> {
        public ZombifiedPiglinRenderer(
                EntityRendererProvider.Context context) {
            super(context, EntityType.ZOMBIFIED_PIGLIN,
                    new PiglinRenderer(context,
                            ModelLayers.ZOMBIFIED_PIGLIN,
                            ModelLayers.ZOMBIFIED_PIGLIN_INNER_ARMOR,
                            ModelLayers.ZOMBIFIED_PIGLIN_OUTER_ARMOR,
                            false),
                    ZOMBIFIED_PIGLIN_TEXTURE, 0.5f);
        }
    }

    public static final class FrogRenderer
            extends DelegatingRenderer<ArenaFrog, Frog> {
        public FrogRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.FROG,
                    new net.minecraft.client.renderer.entity
                            .FrogRenderer(context),
                    FROG_TEXTURE, 0.3f);
        }

        @Override
        protected void applyScale(
                ArenaFrog entity, PoseStack pose,
                float partialTick) {
            pose.scale(1.8f, 1.8f, 1.8f);
        }
    }

    public static final class FoxRenderer
            extends DelegatingRenderer<ArenaFox, Fox> {
        public FoxRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.FOX,
                    new net.minecraft.client.renderer.entity
                            .FoxRenderer(context),
                    FOX_TEXTURE, 0.4f);
        }
    }

    public static final class CreeperRenderer
            extends DelegatingRenderer<ArenaCreeper, Creeper> {
        public CreeperRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.CREEPER,
                    new net.minecraft.client.renderer.entity
                            .CreeperRenderer(context),
                    CREEPER_TEXTURE, 0.5f);
        }
    }

    public static final class PigRenderer
            extends EntityRenderer<ArenaPig> {
        private final net.minecraft.client.renderer.entity.PigRenderer
                normalRenderer;
        private final PiglinRenderer piglinRenderer;
        private final Map<ArenaPig, Pig> normalDummies =
                new WeakHashMap<>();
        private final Map<ArenaPig, ZombifiedPiglin> piglinDummies =
                new WeakHashMap<>();

        public PigRenderer(EntityRendererProvider.Context context) {
            super(context);
            normalRenderer = new net.minecraft.client.renderer.entity
                    .PigRenderer(context);
            piglinRenderer = new PiglinRenderer(context,
                    ModelLayers.ZOMBIFIED_PIGLIN,
                    ModelLayers.ZOMBIFIED_PIGLIN_INNER_ARMOR,
                    ModelLayers.ZOMBIFIED_PIGLIN_OUTER_ARMOR,
                    false);
            shadowRadius = 0.5f;
        }

        @Override
        public void render(ArenaPig entity, float entityYaw,
                           float partialTick, PoseStack pose,
                           MultiBufferSource buffers, int light) {
            if (entity.isZombified()) {
                ZombifiedPiglin piglinDummy =
                        piglinDummies.get(entity);
                if (piglinDummy == null
                        || piglinDummy.level() != entity.level()) {
                    piglinDummy = EntityType.ZOMBIFIED_PIGLIN
                            .create(entity.level());
                    if (piglinDummy != null) {
                        piglinDummies.put(entity, piglinDummy);
                    }
                }
                if (piglinDummy == null) return;
                syncCommon(entity, piglinDummy);
                piglinRenderer.render(piglinDummy, entityYaw,
                        partialTick, pose, buffers, light);
            } else {
                Pig normalDummy = normalDummies.get(entity);
                if (normalDummy == null
                        || normalDummy.level() != entity.level()) {
                    normalDummy = EntityType.PIG
                            .create(entity.level());
                    if (normalDummy != null) {
                        normalDummies.put(entity, normalDummy);
                    }
                }
                if (normalDummy == null) return;
                syncCommon(entity, normalDummy);
                normalRenderer.render(normalDummy, entityYaw,
                        partialTick, pose, buffers, light);
            }
            ArenaUnitHealthRenderer.renderOverlay(
                    entity, pose, buffers);
        }

        @Override
        public ResourceLocation getTextureLocation(ArenaPig entity) {
            return entity.isZombified()
                    ? ZOMBIFIED_PIGLIN_TEXTURE : PIG_TEXTURE;
        }
    }

    public static final class PandaRenderer
            extends DelegatingRenderer<ArenaPanda, Panda> {
        public PandaRenderer(EntityRendererProvider.Context context) {
            super(context, EntityType.PANDA,
                    new net.minecraft.client.renderer.entity
                            .PandaRenderer(context),
                    PANDA_TEXTURE, 0.7f);
        }

        @Override
        protected void applyAnimationTransform(
                ArenaPanda entity, PoseStack pose,
                float partialTick) {
            if ((entity.getArenaStatusFlags()
                    & ArenaStatusFlags.PANDA_ROLL) == 0) {
                return;
            }
            float centerY = entity.getBbHeight() * 0.5f;
            pose.translate(0.0f, centerY, 0.0f);
            pose.mulPose(Axis.XP.rotationDegrees(
                    (entity.tickCount + partialTick) * 34.0f));
            pose.translate(0.0f, -centerY, 0.0f);
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
