package com.ohhapple.monster_conquest.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.arena.game.ArenaEquipment;
import com.ohhapple.monster_conquest.arena.game.ArenaStatusFlags;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaGameClientState;
import com.ohhapple.monster_conquest.client.gui.arena.ArenaStarColors;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.network.clientbound.arena.ArenaPlayerSnapshotPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** 竞技单位头顶的星级、生命条和已穿戴装备；准备阶段也显示。 */
@EventBusSubscriber(modid = Monster_conquest.MODID, value = Dist.CLIENT)
public final class ArenaUnitHealthRenderer {

    private static final float BAR_HALF_WIDTH = 30.0f;
    private static final float BAR_HEIGHT = 4.0f;
    private static final float SKILL_TOP = BAR_HEIGHT + 2.0f;
    private static final float SKILL_HEIGHT = 3.0f;
    private static final float HEALTH_TICK_INTERVAL = 500.0f;
    private static final float EQUIPMENT_SIZE = 12.0f;
    private static final float EQUIPMENT_GAP = 2.0f;
    private static final ResourceLocation STUN_ICON =
            statusIcon("stun");
    private static final ResourceLocation FREEZE_ICON =
            statusIcon("freeze");
    private static final ResourceLocation MAGIC_RESIST_SHRED_ICON =
            statusIcon("magic_resist_shred");
    private static final ResourceLocation BURN_ICON =
            statusIcon("burn");
    private static final float SURFACE_BACKGROUND = 0.0f;
    private static final float SURFACE_SKILL_BACKGROUND = 0.01f;
    private static final float SURFACE_FILL = 0.02f;
    private static final float SURFACE_SHIELD = 0.03f;
    private static final float SURFACE_TICK = 0.04f;
    private static final float SURFACE_GLYPH = 0.06f;
    private static final float EQUIPMENT_TOP =
            SKILL_TOP + SKILL_HEIGHT + 3.0f;
    private static final RenderType HEALTH_BAR_RENDER_TYPE = RenderType.create(
            "monster_conquest_arena_health_bar",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(
                            RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private ArenaUnitHealthRenderer() {
    }

    private static ResourceLocation statusIcon(String name) {
        return ResourceLocation.fromNamespaceAndPath(
                Monster_conquest.MODID,
                "textures/gui/arena/status/" + name + ".png");
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof AbstractArmySoldier soldier)) return;
        renderOverlay(soldier, event.getPoseStack(),
                event.getMultiBufferSource());
    }

    /**
     * 竞技专属代理渲染器不一定继承 LivingEntityRenderer，不会触发
     * RenderLivingEvent；它们必须在实体模型恢复原始 PoseStack 后显式调用此入口。
     */
    public static void renderOverlay(
            AbstractArmySoldier soldier, PoseStack pose,
            MultiBufferSource buffer) {
        if (!soldier.usesArenaPresentationRules()
                || !soldier.isAlive()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || soldier.distanceToSqr(minecraft.player) > 64.0 * 64.0) {
            return;
        }

        float maxHealth = soldier.getArenaDisplayMaxHealth() > 0.0f
                ? soldier.getArenaDisplayMaxHealth()
                : soldier.getMaxHealth();
        if (maxHealth <= 0.0f) return;
        float health = Math.max(0.0f,
                soldier.getArenaDisplayMaxHealth() > 0.0f
                        ? soldier.getArenaCurrentHealth()
                        : soldier.getHealth());
        float shield = Math.max(0.0f, soldier.getArenaShield());
        float scaleTotal = Math.max(maxHealth, health + shield);
        float healthWidth = scaleTotal <= 0.0f
                ? 0.0f : BAR_HALF_WIDTH * 2.0f * health / scaleTotal;
        float shieldWidth = scaleTotal <= 0.0f
                ? 0.0f : BAR_HALF_WIDTH * 2.0f * shield / scaleTotal;
        ArenaPlayerSnapshotPayload snapshot =
                ArenaGameClientState.snapshot();
        UUID viewOwnerId = snapshot == null
                ? new UUID(0L, 0L) : snapshot.viewOwnerId();
        UUID opponentId = snapshot == null
                ? new UUID(0L, 0L) : snapshot.opponentId();
        boolean viewedBoard = snapshot != null
                && snapshot.viewBoardIndex() >= 0
                && soldier.getArenaBoardIndex()
                == snapshot.viewBoardIndex();
        UUID commanderId = soldier.getCommanderUuid().orElse(null);
        int healthColor;
        if (soldier.isArenaWildUnit()) {
            healthColor = 0xE6F48FB1;
        } else if (viewedBoard
                && viewOwnerId.equals(commanderId)) {
            healthColor = 0xE64CD966;
        } else if (viewedBoard
                && !new UUID(0L, 0L).equals(opponentId)
                && opponentId.equals(commanderId)) {
            healthColor = 0xE6E64B4B;
        } else if (soldier.getArenaSide() == 0) {
            healthColor = 0xE69B59F5;
        } else if (soldier.getArenaSide() == 1) {
            healthColor = 0xE6F2C94C;
        } else {
            healthColor = 0xE6AAB4BE;
        }

        pose.pushPose();
        pose.translate(0.0, soldier.getBbHeight() + 0.62, 0.0);
        pose.mulPose(minecraft.getEntityRenderDispatcher()
                .cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);

        Matrix4f matrix = pose.last().pose();
        VertexConsumer vertices = buffer.getBuffer(HEALTH_BAR_RENDER_TYPE);
        float healthEndX = -BAR_HALF_WIDTH + healthWidth;
        float shieldEndX = healthEndX + shieldWidth;
        if (shieldEndX < BAR_HALF_WIDTH) {
            quad(vertices, matrix,
                    shieldEndX, 0.0f,
                    BAR_HALF_WIDTH, BAR_HEIGHT,
                    SURFACE_BACKGROUND, 0xFF090B0D);
        }
        quad(vertices, matrix,
                -BAR_HALF_WIDTH, 0.0f,
                healthEndX,
                BAR_HEIGHT,
                SURFACE_FILL, healthColor);
        if (shieldWidth > 0.0f) {
            quad(vertices, matrix,
                    healthEndX, 0.0f,
                    shieldEndX,
                    BAR_HEIGHT, SURFACE_SHIELD, 0xF2FFFFFF);
        }
        for (float tick = HEALTH_TICK_INTERVAL;
             tick < scaleTotal;
             tick += HEALTH_TICK_INTERVAL) {
            float tickX = -BAR_HALF_WIDTH
                    + BAR_HALF_WIDTH * 2.0f * (tick / scaleTotal);
            quad(vertices, matrix,
                    tickX - 0.45f, 0.0f,
                    tickX + 0.45f, BAR_HEIGHT,
                    SURFACE_TICK, 0xFF101010);
        }

        int skillRequired = Math.max(1, soldier.getArenaSkillRequired());
        float skillRatio = Math.max(0.0f, Math.min(1.0f,
                soldier.getArenaSkillCharge() / (float) skillRequired));
        float skillLeft = -BAR_HALF_WIDTH;
        float skillRight = BAR_HALF_WIDTH;
        float skillBottom = SKILL_TOP + SKILL_HEIGHT;
        int skillColor = 0xFF3B82F6;
        // 外框拆成四条边，避免整块底色与黑色空槽共面后发生深度闪烁。
        quad(vertices, matrix,
                skillLeft - 1.0f, SKILL_TOP - 1.0f,
                skillRight + 1.0f, SKILL_TOP,
                SURFACE_BACKGROUND, skillColor);
        quad(vertices, matrix,
                skillLeft - 1.0f, skillBottom,
                skillRight + 1.0f, skillBottom + 1.0f,
                SURFACE_BACKGROUND, skillColor);
        quad(vertices, matrix,
                skillLeft - 1.0f, SKILL_TOP,
                skillLeft, skillBottom,
                SURFACE_BACKGROUND, skillColor);
        quad(vertices, matrix,
                skillRight, SKILL_TOP,
                skillRight + 1.0f, skillBottom,
                SURFACE_BACKGROUND, skillColor);
        float skillEndX = skillLeft
                + BAR_HALF_WIDTH * 2.0f * skillRatio;
        if (skillEndX < skillRight) {
            quad(vertices, matrix,
                    skillEndX, SKILL_TOP,
                    skillRight, skillBottom,
                    SURFACE_SKILL_BACKGROUND, 0xFF090B0D);
        }
        quad(vertices, matrix,
                skillLeft, SKILL_TOP,
                skillEndX,
                skillBottom,
                SURFACE_FILL, skillColor);

        drawStarMarks(vertices, matrix, soldier.getArenaStar());

        // 文字和物品模型都会刷新批次；所有自定义几何必须在此之前完成。
        drawStatusIcons(pose, buffer, matrix, minecraft, soldier);

        Component label = Component.literal(
                Math.max(0, (int) Math.ceil(health))
                        + " / "
                        + Math.max(0, (int) Math.ceil(maxHealth)));
        float textX = -minecraft.font.width(label) / 2.0f;
        drawText(minecraft, buffer, matrix, label, textX,
                -BAR_HEIGHT - 10.0f, 0xFFFFFFFF);

        List<Integer> equipmentIds = soldier.getArenaEquipmentIds();
        if (!equipmentIds.isEmpty()) {
            float totalWidth = equipmentIds.size() * EQUIPMENT_SIZE
                    + Math.max(0, equipmentIds.size() - 1) * EQUIPMENT_GAP;
            float centerY = EQUIPMENT_TOP + EQUIPMENT_SIZE / 2.0f;
            for (int i = 0; i < equipmentIds.size(); i++) {
                float centerX = -totalWidth / 2.0f + EQUIPMENT_SIZE / 2.0f
                        + i * (EQUIPMENT_SIZE + EQUIPMENT_GAP);
                ArenaEquipment equipment =
                        ArenaEquipment.byId(equipmentIds.get(i));
                pose.pushPose();
                pose.translate(centerX, centerY, 0.2f);
                pose.scale(EQUIPMENT_SIZE, -EQUIPMENT_SIZE,
                        EQUIPMENT_SIZE);
                minecraft.getItemRenderer().renderStatic(
                        equipment.iconStack(),
                        ItemDisplayContext.GUI,
                        0xF000F0,
                        OverlayTexture.NO_OVERLAY,
                        pose,
                        buffer,
                        minecraft.level,
                        equipment.ordinal() * 31 + i);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    private static void drawStatusIcons(
            PoseStack pose, MultiBufferSource buffer,
            Matrix4f matrix, Minecraft minecraft,
            AbstractArmySoldier soldier) {
        List<net.minecraft.world.item.Item> items = new java.util.ArrayList<>();
        int flags = soldier.getArenaStatusFlags();
        boolean stunned = (flags & ArenaStatusFlags.STUN) != 0;
        boolean frozen = soldier.getTicksFrozen() > 0;
        boolean magicResistShredded =
                (flags & ArenaStatusFlags.MAGIC_RESIST_SHRED) != 0;
        boolean burning = (flags & ArenaStatusFlags.BURN) != 0;
        Set<Holder<MobEffect>> statusEffects = new LinkedHashSet<>();
        String encodedEffects = soldier.getArenaVisibleEffects();
        if (!encodedEffects.isBlank()) {
            for (String token : encodedEffects.split(",")) {
                try {
                    int packed = Integer.parseInt(token);
                    BuiltInRegistries.MOB_EFFECT.getHolder(
                                    (packed >>> 8) - 1)
                            .ifPresent(statusEffects::add);
                } catch (NumberFormatException ignored) {
                    // Registry data is server-authored; skip malformed entries.
                }
            }
        }
        if ((flags & ArenaStatusFlags.TAUNT) != 0) items.add(Items.SHIELD);
        if ((flags & ArenaStatusFlags.STRENGTH) != 0) items.add(Items.IRON_SWORD);
        if ((flags & ArenaStatusFlags.RAPID_FIRE) != 0) items.add(Items.ARROW);
        if ((flags & ArenaStatusFlags.ARCANE_SHOTS) != 0) {
            items.add(Items.ENCHANTED_BOOK);
        }
        if ((flags & ArenaStatusFlags.EMPOWERED_ATTACK) != 0) {
            items.add(Items.IRON_AXE);
        }
        if ((flags & ArenaStatusFlags.WITHER_DRAIN) != 0) {
            items.add(Items.WITHER_SKELETON_SKULL);
        }
        if ((flags & ArenaStatusFlags.SNIPER_READY) != 0) {
            items.add(Items.CROSSBOW);
        }
        if ((flags & ArenaStatusFlags.DROWNED_READY) != 0) {
            items.add(Items.TRIDENT);
        }
        if ((flags & ArenaStatusFlags.STUN_READY) != 0) {
            items.add(Items.BELL);
        }
        if ((flags & ArenaStatusFlags.BATTLE_GROWTH) != 0) {
            items.add(Items.GOLDEN_APPLE);
        }
        if ((flags & ArenaStatusFlags.GOAT_CHARGE) != 0) {
            items.add(Items.GOAT_HORN);
        }
        if ((flags & ArenaStatusFlags.POLAR_FORM) != 0) {
            items.add(Items.POLAR_BEAR_SPAWN_EGG);
        }
        if ((flags & ArenaStatusFlags.HOGLIN_CHARGE) != 0) {
            items.add(Items.HOGLIN_SPAWN_EGG);
        }
        if ((flags & ArenaStatusFlags.ARMADILLO_ROLL) != 0) {
            items.add(Items.ARMADILLO_SCUTE);
        }
        if ((flags & ArenaStatusFlags.SILENCE) != 0) {
            items.add(Items.BARRIER);
        }
        if ((flags & ArenaStatusFlags.PANDA_ROLL) != 0) {
            items.add(Items.BAMBOO);
        }
        if ((flags & ArenaStatusFlags.ENDERMAN_LOCK) != 0) {
            items.add(Items.ENDER_EYE);
        }
        if ((flags & ArenaStatusFlags.WARDEN_GROWTH) != 0) {
            items.add(Items.ECHO_SHARD);
        }
        if ((flags & ArenaStatusFlags.WITHER_ARMOR) != 0) {
            items.add(Items.NETHERITE_CHESTPLATE);
        }
        if ((flags & ArenaStatusFlags.GIANT_ARMADILLO_MOMENTUM) != 0) {
            items.add(Items.RABBIT_FOOT);
        }

        int special = (stunned ? 1 : 0) + (frozen ? 1 : 0)
                + (magicResistShredded ? 1 : 0) + (burning ? 1 : 0);
        int total = special + statusEffects.size() + items.size();
        if (total <= 0) return;
        int slot = 0;
        if (stunned) {
            drawStatusTexture(buffer, matrix, STUN_ICON,
                    statusX(slot, total), statusY(slot));
            slot++;
        }
        if (frozen && slot < total) {
            drawStatusTexture(buffer, matrix, FREEZE_ICON,
                    statusX(slot, total), statusY(slot));
            slot++;
        }
        if (magicResistShredded && slot < total) {
            drawStatusTexture(
                    buffer, matrix, MAGIC_RESIST_SHRED_ICON,
                    statusX(slot, total), statusY(slot));
            slot++;
        }
        if (burning && slot < total) {
            drawStatusTexture(buffer, matrix, BURN_ICON,
                    statusX(slot, total), statusY(slot));
            slot++;
        }
        for (int i = 0; i < items.size() && slot < total; i++, slot++) {
            pose.pushPose();
            pose.translate(statusX(slot, total),
                    statusY(slot), 0.2f);
            pose.scale(10.0f, -10.0f, 10.0f);
            minecraft.getItemRenderer().renderStatic(
                    new ItemStack(items.get(i)),
                    ItemDisplayContext.GUI,
                    0xF000F0,
                    OverlayTexture.NO_OVERLAY,
                    pose,
                    buffer,
                    minecraft.level,
                    i * 31 + soldier.getId());
            pose.popPose();
        }
        for (Holder<MobEffect> effect : statusEffects) {
            if (slot >= total) break;
            drawMobEffectIcon(buffer, matrix,
                    minecraft.getMobEffectTextures().get(effect),
                    statusX(slot, total), statusY(slot));
            slot++;
        }
    }

    private static float statusX(int slot, int total) {
        return ArenaStatusIconLayout.x(slot, total);
    }

    private static float statusY(int slot) {
        return ArenaStatusIconLayout.y(slot);
    }

    private static void drawMobEffectIcon(
            MultiBufferSource buffer, Matrix4f matrix,
            TextureAtlasSprite sprite, float centerX, float centerY) {
        ResourceLocation atlas = sprite.atlasLocation();
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(atlas));
        float half = 4.0f;
        iconVertex(vertices, matrix,
                centerX - half, centerY - half,
                sprite.getU0(), sprite.getV0());
        iconVertex(vertices, matrix,
                centerX - half, centerY + half,
                sprite.getU0(), sprite.getV1());
        iconVertex(vertices, matrix,
                centerX + half, centerY + half,
                sprite.getU1(), sprite.getV1());
        iconVertex(vertices, matrix,
                centerX + half, centerY - half,
                sprite.getU1(), sprite.getV0());
    }

    private static void drawStatusTexture(
            MultiBufferSource buffer, Matrix4f matrix,
            ResourceLocation texture, float centerX, float centerY) {
        VertexConsumer vertices = buffer.getBuffer(
                RenderType.entityCutoutNoCull(texture));
        float half = 4.0f;
        iconVertex(vertices, matrix,
                centerX - half, centerY - half,
                0.0f, 0.0f);
        iconVertex(vertices, matrix,
                centerX - half, centerY + half,
                0.0f, 1.0f);
        iconVertex(vertices, matrix,
                centerX + half, centerY + half,
                1.0f, 1.0f);
        iconVertex(vertices, matrix,
                centerX + half, centerY - half,
                1.0f, 0.0f);
    }

    private static void iconVertex(VertexConsumer vertices,
                                   Matrix4f matrix,
                                   float x, float y, float u, float v) {
        vertices.addVertex(matrix, x, y, SURFACE_GLYPH)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(0xF000F0)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    private static void drawText(Minecraft minecraft, MultiBufferSource buffer,
                                 Matrix4f matrix, Component text,
                                 float x, float y, int color) {
        minecraft.font.drawInBatch(
                text,
                x,
                y,
                color,
                false,
                matrix,
                buffer,
                Font.DisplayMode.SEE_THROUGH,
                0x66000000,
                0xF000F0);
    }

    private static void drawStarMarks(VertexConsumer vertices,
                                      Matrix4f matrix, int star) {
        int count = Math.max(1, Math.min(3, star));
        int color = ArenaStarColors.color(count);
        float right = -BAR_HALF_WIDTH - 4.0f;
        quad(vertices, matrix,
                right - count * 7.0f - 1.0f, -2.5f,
                right + 0.5f, BAR_HEIGHT + 2.5f,
                0xD9000000);
        for (int i = 0; i < count; i++) {
            float center = right - 3.0f - i * 7.0f;
            stroke(vertices, matrix,
                    center + 2.0f, -1.5f,
                    center - 2.0f, BAR_HEIGHT / 2.0f,
                    0.8f, color);
            stroke(vertices, matrix,
                    center - 2.0f, BAR_HEIGHT / 2.0f,
                    center + 2.0f, BAR_HEIGHT + 1.5f,
                    0.8f, color);
        }
    }

    private static void stroke(VertexConsumer vertices, Matrix4f matrix,
                               float startX, float startY,
                               float endX, float endY,
                               float halfWidth, int color) {
        float deltaX = endX - startX;
        float deltaY = endY - startY;
        float length = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
        if (length <= 0.0f) return;
        float normalX = -deltaY / length * halfWidth;
        float normalY = deltaX / length * halfWidth;
        vertices.addVertex(matrix, startX + normalX, startY + normalY,
                        SURFACE_GLYPH)
                .setColor(color);
        vertices.addVertex(matrix, startX - normalX, startY - normalY,
                        SURFACE_GLYPH)
                .setColor(color);
        vertices.addVertex(matrix, endX - normalX, endY - normalY,
                        SURFACE_GLYPH)
                .setColor(color);
        vertices.addVertex(matrix, endX + normalX, endY + normalY,
                        SURFACE_GLYPH)
                .setColor(color);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix,
                             float left, float top,
                             float right, float bottom,
                             int color) {
        quad(vertices, matrix, left, top, right, bottom,
                SURFACE_BACKGROUND, color);
    }

    private static void quad(VertexConsumer vertices, Matrix4f matrix,
                             float left, float top,
                             float right, float bottom,
                             float z, int color) {
        vertices.addVertex(matrix, left, top, z).setColor(color);
        vertices.addVertex(matrix, left, bottom, z).setColor(color);
        vertices.addVertex(matrix, right, bottom, z).setColor(color);
        vertices.addVertex(matrix, right, top, z).setColor(color);
    }
}
