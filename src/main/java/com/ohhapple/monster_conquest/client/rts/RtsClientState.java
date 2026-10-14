package com.ohhapple.monster_conquest.client.rts;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class RtsClientState {

    /** 框选拖动过程中的本地预览集合（尚未提交服务端）。 */
    @Nullable private static volatile Set<UUID> previewSelected = null;

    private RtsClientState() {}

    private static volatile Set<UUID> SELECTED = Collections.emptySet();
    private static volatile boolean rtsMode = false;

    @Nullable private static volatile VirtualCamera camera = null;

    @Nullable private static volatile Vec3 moveMarker = null;

    @Nullable private static volatile Matrix4f lastProjMatrix = null;
    @Nullable private static volatile Matrix4f lastViewMatrix = null;

    private static final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();

    public static boolean isRtsMode() { return rtsMode; }
    public static void setRtsMode(boolean v) { rtsMode = v; }

    @Nullable public static VirtualCamera getCamera() { return camera; }
    public static void setCamera(@Nullable VirtualCamera c) { camera = c; }

    public static Set<UUID> getSelected() { return SELECTED; }

    public static boolean isSelected(UUID uuid) {
        return uuid != null && SELECTED.contains(uuid);
    }

    public static void setSelected(Set<UUID> set) {
        SELECTED = (set == null || set.isEmpty())
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(set));
    }

    @Nullable public static Vec3 getMoveMarker() { return moveMarker; }
    public static void setMoveMarker(@Nullable Vec3 m) { moveMarker = m; }

    public static void setMatrices(Matrix4f proj, Matrix4f view) {
        lastProjMatrix = proj;
        lastViewMatrix = view;
    }

    @Nullable public static Matrix4f getLastProjMatrix() { return lastProjMatrix; }
    @Nullable public static Matrix4f getLastViewMatrix() { return lastViewMatrix; }

    public static void setKeyPressed(int keyCode, boolean pressed) {
        if (pressed) pressedKeys.add(keyCode);
        else pressedKeys.remove(keyCode);
    }

    public static boolean isKeyPressed(int keyCode) {
        return pressedKeys.contains(keyCode);
    }

    public static void clearKeys() {
        pressedKeys.clear();
    }

    public static void setPreviewSelected(@Nullable Set<UUID> s) {
        previewSelected = (s == null || s.isEmpty())
                ? null
                : new LinkedHashSet<>(s);
    }

    @Nullable public static Set<UUID> getPreviewSelected() { return previewSelected; }

    /** 渲染用：服务端 SELECTED + 本地 previewSelected 合并。 */
    public static Set<UUID> getRenderSelected() {
        Set<UUID> p = previewSelected;
        if (p == null) return SELECTED;
        Set<UUID> merged = new LinkedHashSet<>(SELECTED);
        merged.addAll(p);
        return merged;
    }

    public static void clear() {
        SELECTED = Collections.emptySet();
        rtsMode = false;
        camera = null;
        moveMarker = null;
        lastProjMatrix = null;
        lastViewMatrix = null;
        pressedKeys.clear();
        previewSelected = null;
    }
}