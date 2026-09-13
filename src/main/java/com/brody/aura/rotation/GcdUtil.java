package com.brody.aura.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * GCD-compliant rotation math, ported from the VCore Grim rotation handler
 * and ThunderHack calcRotations. All rotation deltas must quantize to the
 * mouse-sensitivity GCD or replays flag instantly.
 */
public final class GcdUtil {

    private GcdUtil() {
    }

    /**
     * Classic formula: gcd = (sens * 0.6 + 0.2)^3 * 1.2 * 8 / 8.
     * Used by Track-style smoothing.
     */
    public static double getGcd(MinecraftClient mc) {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        return Math.pow(f, 3.0) * 1.2;
    }

    /**
     * Grim-tuned variant: pow(f, 1.5) * 0.8 * 0.15. Tighter quantization,
     * matches what GrimAC expects from client mouse deltas.
     */
    public static float getGrimGcd(MinecraftClient mc) {
        double sensitivity = mc.options.getMouseSensitivity().getValue();
        double f = sensitivity * 0.6 + 0.2;
        return (float) (Math.pow(f, 1.5) * 0.8 * 0.15);
    }

    public static float applyGcd(float target, float current, double gcd) {
        if (gcd <= 0.0f) return target;
        float delta = wrapDegrees(target - current);
        delta -= delta % gcd;
        return current + delta;
    }

    public static float[] applyGrimQuantize(MinecraftClient mc, float yaw, float pitch) {
        float gcd = getGrimGcd(mc);
        yaw -= yaw % gcd;
        pitch -= pitch % gcd;
        return new float[]{yaw, pitch};
    }

    public static float wrapDegrees(float deg) {
        float d = deg % 360.0f;
        if (d >= 180.0f) d -= 360.0f;
        if (d < -180.0f) d += 360.0f;
        return d;
    }

    /**
     * Compute yaw/pitch needed to look at a point, VCore PlayerManager.calcAngle style.
     */
    public static float[] calcAngle(Vec3d from, Vec3d to) {
        double diffX = to.x - from.x;
        double diffY = to.y - from.y;
        double diffZ = to.z - from.z;
        double dist = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, dist)));
        return new float[]{yaw, MathHelper.clamp(pitch, -90.0f, 90.0f)};
    }

    /**
     * Angle between current view and the vector to the entity (FOV check).
     */
    public static float getFovAngle(MinecraftClient mc, Entity e) {
        double difX = e.getX() - mc.player.getX();
        double difZ = e.getZ() - mc.player.getZ();
        float yaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(difZ, difX)) - 90.0);
        return Math.abs(yaw - MathHelper.wrapDegrees(mc.player.getYaw()));
    }

    /**
     * Interpolate for render (clientLook).
     */
    public static float interpolate(float from, float to, float delta) {
        return from + wrapDegrees(to - from) * delta;
    }
}
