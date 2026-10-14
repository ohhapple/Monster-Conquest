package com.ohhapple.monster_conquest.client.rts;

import net.minecraft.world.phys.Vec3;

public final class RtsCameraController {

    private RtsCameraController() {}

    private static final double BASE_SPEED_PER_SEC = 20.0;
    private static final double MIN_SPEED_MULT = 0.3;
    private static final double MAX_SPEED_MULT = 4.0;

    private static double camX, camY, camZ;
    private static float yaw, pitch;
    private static double speedMult = 1.0;
    private static boolean initialized = false;

    public static void init(Vec3 playerPos, float playerYaw, float playerPitch) {
        camX = playerPos.x;
        camY = playerPos.y + 1.0;
        camZ = playerPos.z;
        yaw = playerYaw;
        pitch = playerPitch;
        speedMult = 1.0;
        initialized = true;
    }

    public static void reset() { initialized = false; }
    public static boolean isInitialized() { return initialized; }

    public static void move(double fwd, double right, double up, double dt) {
        if (fwd == 0 && right == 0 && up == 0) return;
        float yawRad = (float) Math.toRadians(yaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = -fz;
        double rz = fx;

        double dx = fx * fwd + rx * right;
        double dz = fz * fwd + rz * right;
        double dy = up;

        double len = Math.sqrt(dx * dx + dz * dz + dy * dy);
        if (len < 0.001) return;
        double step = BASE_SPEED_PER_SEC * dt * speedMult;
        camX += dx / len * step;
        camY += dy / len * step;
        camZ += dz / len * step;
    }

    public static void addYaw(float dyaw) {
        yaw += dyaw;
        yaw = ((yaw % 360f) + 360f) % 360f;
    }

    public static void addPitch(float dpitch) {
        pitch += dpitch;
        if (pitch > 90f) pitch = 90f;
        if (pitch < -90f) pitch = -90f;
    }

    public static void addSpeedMult(double delta) {
        speedMult = Math.max(MIN_SPEED_MULT, Math.min(MAX_SPEED_MULT, speedMult + delta));
    }

    public static double getCamX() { return camX; }
    public static double getCamY() { return camY; }
    public static double getCamZ() { return camZ; }
    public static float  getYaw()  { return yaw; }
    public static float  getPitch() { return pitch; }
    public static double getSpeedMult() { return speedMult; }
}