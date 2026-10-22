package com.ohhapple.monster_conquest.client.rts;

import com.ohhapple.monster_conquest.client.input.ModKeyMappings;
import com.ohhapple.monster_conquest.config.GeneralConfig;
import com.ohhapple.monster_conquest.entity.AbstractArmySoldier;
import com.ohhapple.monster_conquest.entity.WarHorseEntity;
import com.ohhapple.monster_conquest.Monster_conquest;
import com.ohhapple.monster_conquest.network.serverbound.rts.RtsCommandPayload;
import com.ohhapple.monster_conquest.network.serverbound.rts.RtsOrderPayload;
import com.ohhapple.monster_conquest.network.serverbound.rts.RtsSelectPayload;
import com.ohhapple.monster_conquest.network.serverbound.rts.RtsSelectSimilarPayload;
import com.ohhapple.monster_conquest.network.serverbound.rts.RtsSquadClickPayload;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.network.PacketDistributor;

import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

public class RtsScreen extends Screen {

    /** 点击 vs 拖拽的判定阈值（像素平方）。 */
    private static final double CLICK_THRESHOLD_SQR = 16.0;

    /** 双击判定窗口（毫秒）：同一士兵两次点击间隔小于该值算双击。 */
    private static final long DOUBLE_CLICK_MS = 350L;
    private long lastSoldierClickMs = 0L;
    private UUID lastSoldierClickUuid = null;

    private double dragStartX, dragStartY;
    private double dragEndX, dragEndY;
    private boolean dragging = false;

    private double lastMouseX = -1, lastMouseY = -1;
    private boolean hasLastMouse = false;

    /** true = 转视角模式（鼠标隐藏）；false = 固定视角（鼠标显示 + 可框选）。 */
    private boolean lookMode = true;

    public RtsScreen() {
        super(Component.literal("RTS"));
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public boolean shouldCloseOnEsc() { return true; }

    @Override
    protected void init() {
        super.init();
        applyCursorMode();
    }

    private void applyCursorMode() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        int mode = lookMode ? GLFW.GLFW_CURSOR_DISABLED : GLFW.GLFW_CURSOR_NORMAL;
        InputConstants.grabOrReleaseMouse(
                window.getWindow(), mode,
                window.getScreenWidth() / 2.0,
                window.getScreenHeight() / 2.0);
    }

    // ===================== 键盘 =====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        RtsClientState.setKeyPressed(keyCode, true);

        // RTS 快捷指令
        if (ModKeyMappings.RTS_RIDE_CAVALRY.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(0));
            return true;
        }
        if (ModKeyMappings.RTS_RIDE_INFANTRY.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(1));
            return true;
        }
        if (ModKeyMappings.RTS_FORMATION_1.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(10));
            return true;
        }
        if (ModKeyMappings.RTS_FORMATION_2.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(11));
            return true;
        }
        if (ModKeyMappings.RTS_FORMATION_3.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(12));
            return true;
        }
        if (ModKeyMappings.RTS_FORMATION_4.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(13));
            return true;
        }
        if (ModKeyMappings.RTS_FORMATION_5.matches(keyCode, scanCode)) {
            PacketDistributor.sendToServer(new RtsOrderPayload(14));
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        RtsClientState.setKeyPressed(keyCode, false);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    // ===================== 鼠标 =====================

    @Override
    public void mouseMoved(double mx, double my) {
        if (!hasLastMouse) {
            lastMouseX = mx;
            lastMouseY = my;
            hasLastMouse = true;
            return;
        }
        double dx = mx - lastMouseX;
        double dy = my - lastMouseY;
        lastMouseX = mx;
        lastMouseY = my;

        if (!lookMode || dragging) return;

        Minecraft mc = Minecraft.getInstance();
        double scale = (double) mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth();
        double dxPhys = dx * scale;
        double dyPhys = dy * scale;

        double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
        double factor = sens * sens * sens * 8.0 * 0.15;

        RtsCameraController.addYaw((float) (dxPhys * factor));
        RtsCameraController.addPitch((float) (dyPhys * factor));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // 中键：切换视角模式（look ↔ fixed），只在 look 模式下允许切出
        if (button == 2) {
            lookMode = !lookMode;
            applyCursorMode();
            hasLastMouse = false;
            return true;
        }

        // 转视角模式（look）下不响应左键 / 右键 —— 避免误触框选和命令
        if (lookMode) return true;

        if (button == 0) {
            // 固定视角模式：开始可能是一次框选
            dragStartX = dragEndX = mx;
            dragStartY = dragEndY = my;
            dragging = true;
            return true;
        }

        if (button == 1) {
            lastSoldierClickUuid = null;   // 右键命令会打断双击判定
            Entity hit = screenRaycastEntity(mx, my);
            UUID targetUuid = null;
            Vec3 dest;
            if (hit instanceof LivingEntity le) {
                targetUuid = le.getUUID();
                dest = le.position();
            } else {
                dest = screenRaycastGround(mx, my);
            }
            if (dest != null) {
                if (GeneralConfig.debugMovementLog) {
                    Vec3 origin = cameraOrigin();
                    Monster_conquest.LOGGER.info(
                            "[RTS-COMMAND] screen=({},{}) yaw={} pitch={} origin=({},{},{}) dest=({},{},{}) target={}",
                            String.format(Locale.ROOT, "%.1f", mx),
                            String.format(Locale.ROOT, "%.1f", my),
                            String.format(Locale.ROOT, "%.1f",
                                    RtsCameraController.getYaw()),
                            String.format(Locale.ROOT, "%.1f",
                                    RtsCameraController.getPitch()),
                            String.format(Locale.ROOT, "%.2f", origin.x),
                            String.format(Locale.ROOT, "%.2f", origin.y),
                            String.format(Locale.ROOT, "%.2f", origin.z),
                            String.format(Locale.ROOT, "%.2f", dest.x),
                            String.format(Locale.ROOT, "%.2f", dest.y),
                            String.format(Locale.ROOT, "%.2f", dest.z),
                            targetUuid == null ? "-" : targetUuid);
                }
                PacketDistributor.sendToServer(new RtsCommandPayload(
                        dest.x, dest.y, dest.z, targetUuid));
            }
            return true;
        }

        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        // 转视角模式：不处理松手事件（进入 look 模式前若还在 dragging，直接丢弃）
        if (lookMode) {
            if (dragging) {
                dragging = false;
                RtsClientState.setPreviewSelected(null);
            }
            return true;
        }

        if (button == 0 && dragging) {
            dragging = false;
            double dx = mx - dragStartX;
            double dy = my - dragStartY;

            if (dx * dx + dy * dy < CLICK_THRESHOLD_SQR) {
                // —— 视为点击 ——
                Entity hit = screenRaycastEntity(mx, my);
                boolean ctrl = hasControlDown();
                // 点击骑兵战马 → 解析到骑手士兵（不要忽略战马）
                AbstractArmySoldier s = resolveClickedSoldier(hit);
                if (s != null) {
                    long nowMs = System.currentTimeMillis();
                    boolean doubleClick = lastSoldierClickUuid != null
                            && lastSoldierClickUuid.equals(s.getUUID())
                            && nowMs - lastSoldierClickMs <= DOUBLE_CLICK_MS;
                    lastSoldierClickMs = nowMs;
                    lastSoldierClickUuid = s.getUUID();

                    if (doubleClick) {
                        // 双击：全选同种士兵；Ctrl + 双击：全选同兵种士兵
                        PacketDistributor.sendToServer(new RtsSelectSimilarPayload(
                                s.getUUID(), ctrl ? 1 : 0));
                        lastSoldierClickUuid = null;   // 避免三连击重复触发
                    } else {
                        // squadId == 0（无小队）→ 服务端只选中这一名士兵
                        PacketDistributor.sendToServer(
                                new RtsSquadClickPayload(s.getSquadId(), ctrl, s.getUUID()));
                    }
                } else {
                    lastSoldierClickUuid = null;
                    PacketDistributor.sendToServer(
                            new RtsSelectPayload(new ArrayList<>(), false));
                }
            } else {
                // —— 视为框选 ——
                sendBoxSelection(dragStartX, dragStartY, mx, my);
            }
            RtsClientState.setPreviewSelected(null);
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        // 转视角模式：不处理拖动
        if (lookMode) return true;

        if (dragging && button == 0) {
            dragEndX = mx;
            dragEndY = my;
            double ddx = mx - dragStartX;
            double ddy = my - dragStartY;
            if (ddx * ddx + ddy * ddy < CLICK_THRESHOLD_SQR) {
                RtsClientState.setPreviewSelected(null);
            } else {
                RtsClientState.setPreviewSelected(
                        computeBoxSelection(dragStartX, dragStartY, mx, my));
            }
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        RtsCameraController.addSpeedMult(dy * 0.15);
        return true;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 框选矩形
        if (dragging) {
            int x1 = (int) Math.min(dragStartX, dragEndX);
            int y1 = (int) Math.min(dragStartY, dragEndY);
            int x2 = (int) Math.max(dragStartX, dragEndX);
            int y2 = (int) Math.max(dragStartY, dragEndY);
            if (x2 - x1 >= 2 || y2 - y1 >= 2) {
                g.fill(x1, y1, x2, y2, 0x30FFFFFF);
                g.fill(x1, y1, x2, y1 + 1, 0xFFFFFFFF);
                g.fill(x1, y2 - 1, x2, y2, 0xFFFFFFFF);
                g.fill(x1, y1, x1 + 1, y2, 0xFFFFFFFF);
                g.fill(x2 - 1, y1, x2, y2, 0xFFFFFFFF);
            }
        }

        Component exitKey = ModKeyMappings.RTS_TOGGLE.getTranslatedKeyMessage();

        // 顶部：模式 + 速度
        Component modeKey = Component.translatable(lookMode
                ? "gui.monster_conquest.rts.mode.look"
                : "gui.monster_conquest.rts.mode.fixed");
        Component top = Component.translatable(
                "gui.monster_conquest.rts.tip.top",
                modeKey,
                String.format("%.1f", RtsCameraController.getSpeedMult()));
        g.drawString(font, top, 8, 8, 0xFFFFFF, true);

        // 左下角：快捷键
        int lh = font.lineHeight;
        int x = 8;
        int y = this.height - 4 - lh * 7;
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.title"),
                x, y, 0xFFFFC040, true);
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.line1"),
                x, y + lh, 0xC0C0C0, true);
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.line2"),
                x, y + lh * 2, 0xC0C0C0, true);
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.line2b"),
                x, y + lh * 3, 0xC0C0C0, true);
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.line3",
                        ModKeyMappings.RTS_RIDE_CAVALRY.getTranslatedKeyMessage(),
                        ModKeyMappings.RTS_RIDE_INFANTRY.getTranslatedKeyMessage()),
                x, y + lh * 4, 0xC0C0C0, true);
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.line4",
                        ModKeyMappings.RTS_FORMATION_1.getTranslatedKeyMessage(),
                        ModKeyMappings.RTS_FORMATION_2.getTranslatedKeyMessage(),
                        ModKeyMappings.RTS_FORMATION_3.getTranslatedKeyMessage(),
                        ModKeyMappings.RTS_FORMATION_4.getTranslatedKeyMessage(),
                        ModKeyMappings.RTS_FORMATION_5.getTranslatedKeyMessage()),
                x, y + lh * 5, 0xC0C0C0, true);
        g.drawString(font,
                Component.translatable("gui.monster_conquest.rts.keys.line5", exitKey),
                x, y + lh * 6, 0xC0C0C0, true);

        Component feedback = RtsClientState.getFeedback();
        if (feedback != null) {
            int feedbackY = this.height - 58;
            int feedbackWidth = font.width(feedback);
            g.fill(this.width / 2 - feedbackWidth / 2 - 6, feedbackY - 4,
                    this.width / 2 + feedbackWidth / 2 + 6,
                    feedbackY + font.lineHeight + 4, 0x90000000);
            g.drawCenteredString(font, feedback, this.width / 2, feedbackY,
                    0xFFFFD060);
        }
    }

    @Override
    public void removed() {
        super.removed();
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        InputConstants.grabOrReleaseMouse(
                window.getWindow(),
                GLFW.GLFW_CURSOR_NORMAL,
                window.getScreenWidth() / 2.0,
                window.getScreenHeight() / 2.0);
        RtsClientTicker.exitRts(mc);
    }

    // ===================== 框选 =====================

    private void sendBoxSelection(double sx1, double sy1, double sx2, double sy2) {
        if (Math.abs(sx2 - sx1) < 4 && Math.abs(sy2 - sy1) < 4) return;
        Set<UUID> selected = computeBoxSelection(sx1, sy1, sx2, sy2);
        boolean additive = hasControlDown();
        PacketDistributor.sendToServer(
                new RtsSelectPayload(new ArrayList<>(selected), additive));
    }

    /** 计算屏幕矩形内的自己士兵 UUID 集合。 */
    private Set<UUID> computeBoxSelection(double sx1, double sy1,
                                          double sx2, double sy2) {
        Set<UUID> result = new LinkedHashSet<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return result;

        double minX = Math.min(sx1, sx2), maxX = Math.max(sx1, sx2);
        double minY = Math.min(sy1, sy2), maxY = Math.max(sy1, sy2);

        double camX = RtsCameraController.getCamX();
        double camY = RtsCameraController.getCamY();
        double camZ = RtsCameraController.getCamZ();
        AABB searchBox = new AABB(
                camX - 512, camY - 256, camZ - 512,
                camX + 512, camY + 256, camZ + 512);

        for (AbstractArmySoldier s : mc.level.getEntitiesOfClass(
                AbstractArmySoldier.class, searchBox)) {
            if (!s.isAlive()) continue;
            if (!mc.player.getUUID().equals(s.getCommanderUuid().orElse(null))) continue;
            if (isEntityInScreenBox(s, minX, minY, maxX, maxY)) {
                result.add(s.getUUID());
            }
        }
        return result;
    }

    /**
     * 框选使用实体完整包围盒，而不是脚点。
     *   飞行单位模型在脚点上方；只投影 position() 会导致框住身体却选不中。
     */
    private boolean isEntityInScreenBox(Entity entity,
                                        double minX, double minY,
                                        double maxX, double maxY) {
        AABB box = entity.getBoundingBox();
        Vec3 center = box.getCenter();
        if (isWorldPosOnScreen(center, minX, minY, maxX, maxY)) return true;

        for (double x : new double[]{box.minX, center.x, box.maxX}) {
            for (double y : new double[]{box.minY, center.y, box.maxY}) {
                for (double z : new double[]{box.minZ, center.z, box.maxZ}) {
                    if (isWorldPosOnScreen(new Vec3(x, y, z),
                            minX, minY, maxX, maxY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ===================== 相机几何工具 =====================

    /**
     * 输入阶段使用 RTS 相机控制器的当前朝向，而不是上一帧由 VirtualCamera
     *   setup 出来的 Camera 朝向。否则刚转动视角 / 刚切回固定视角时右键，
     *   射线会按旧朝向落点，表现为命令点向转动前的位置偏移。
     */
    private static Vec3[] cameraBasis() {
        float camYaw = RtsCameraController.getYaw();
        float camPitch = RtsCameraController.getPitch();
        Vec3 forward = Vec3.directionFromRotation(camPitch, camYaw);
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = forward.cross(worldUp);
        if (right.lengthSqr() < 1e-6) right = new Vec3(1, 0, 0);
        else right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        return new Vec3[]{forward, right, up};
    }

    /** 优先用本帧渲染时的真实相机位置；首帧尚未记录时退回控制器坐标。 */
    private static Vec3 cameraOrigin() {
        Vec3 snapshot = RtsClientState.getLastCameraPos();
        if (snapshot != null) return snapshot;
        return new Vec3(
                RtsCameraController.getCamX(),
                RtsCameraController.getCamY(),
                RtsCameraController.getCamZ());
    }

    private static double halfTanFovH() {
        Minecraft mc = Minecraft.getInstance();
        double aspect = (double) mc.getWindow().getGuiScaledWidth()
                / (double) mc.getWindow().getGuiScaledHeight();
        return halfTanFovV() * aspect;
    }

    private static double halfTanFovV() {
        // 透视投影矩阵的 m11 = 1 / tan(fovY / 2)。用实际投影矩阵而不是
        //   FOV 设置值，疾跑 / 速度效果 / 其它视角修饰下的边缘落点才一致。
        Matrix4f projection = RtsClientState.getLastProjMatrix();
        if (projection != null) {
            float m11 = Math.abs(projection.m11());
            if (m11 > 1.0E-6f) return 1.0 / m11;
        }
        Minecraft mc = Minecraft.getInstance();
        double fov = mc.options.fov().get();
        return Math.tan(Math.toRadians(fov / 2.0));
    }

    private boolean isWorldPosOnScreen(Vec3 worldPos,
                                       double minX, double minY,
                                       double maxX, double maxY) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 camPos = cameraOrigin();

        Vec3[] basis = cameraBasis();
        Vec3 forward = basis[0], right = basis[1], up = basis[2];

        Vec3 rel = worldPos.subtract(camPos);
        double rz = rel.dot(forward);
        if (rz <= 0.01) return false;

        double rx = rel.dot(right);
        double ry = rel.dot(up);

        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        double halfW = halfTanFovH();
        double halfH = halfTanFovV();

        double ndcX = rx / (rz * halfW);
        double ndcY = ry / (rz * halfH);

        double screenX = (ndcX * 0.5 + 0.5) * w;
        double screenY = (1.0 - (ndcY * 0.5 + 0.5)) * h;

        return screenX >= minX && screenX <= maxX
                && screenY >= minY && screenY <= maxY;
    }

    private Vec3 computeRayDir(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();

        double ndcX = 2 * mx / w - 1;
        double ndcY = 1 - 2 * my / h;

        Vec3[] basis = cameraBasis();
        Vec3 forward = basis[0], right = basis[1], up = basis[2];
        double halfW = halfTanFovH();
        double halfH = halfTanFovV();

        Vec3 dir = forward
                .add(right.scale(ndcX * halfW))
                .add(up.scale(ndcY * halfH));
        return dir.normalize();
    }

    private Vec3 screenRaycastGround(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 dir = computeRayDir(mx, my);
        if (dir == null) return null;

        Vec3 origin = cameraOrigin();
        Vec3 end = origin.add(dir.scale(512.0));

        BlockHitResult hit = mc.level.clip(new ClipContext(
                origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS ? null : hit.getLocation();
    }

    /** 点击实体 → 可指挥士兵：战马解析到骑手士兵。 */
    private static AbstractArmySoldier resolveClickedSoldier(Entity hit) {
        if (hit instanceof AbstractArmySoldier s) return s;
        if (hit instanceof WarHorseEntity horse) {
            Entity rider = horse.getFirstPassenger();
            if (rider instanceof AbstractArmySoldier s) return s;
        }
        return null;
    }

    private Entity screenRaycastEntity(double mx, double my) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 dir = computeRayDir(mx, my);
        if (dir == null) return null;

        Vec3 origin = cameraOrigin();
        Vec3 end = origin.add(dir.scale(256.0));

        AABB search = new AABB(origin, end).inflate(2.0);
        BlockHitResult blockHit = mc.level.clip(new ClipContext(
                origin, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double blockDist = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE : origin.distanceToSqr(blockHit.getLocation());

        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        // 任何活着的生物都可以作为 RTS 命令目标；服务器再区分友方跟随与敌方攻击。
        //   第一个参数传 null：不能排除本地玩家自己，否则"右键自己 = 跟随"永远选不中。
        for (Entity e : mc.level.getEntities((Entity) null, search,
                e -> e instanceof LivingEntity && e.isAlive())) {
            AABB box = e.getBoundingBox().inflate(0.25);
            var hit = box.clip(origin, end);
            if (hit.isEmpty()) continue;
            double d = origin.distanceToSqr(hit.get());
            if (d > blockDist) continue;
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }
}
