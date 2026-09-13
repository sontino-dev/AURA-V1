package com.brody.aura.ai;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/**
 * Velocity extrapolator. Combines current velocity with observed historical
 * jitter to guess where the target will be in N ticks. Feeds the rotation engine.
 */
public class TargetPredictor {

    private double lastX, lastY, lastZ;
    private boolean first = true;

    // observed acceleration correction (learned from position deltas)
    private double ax, ay, az;
    private int samples;

    public void feed(Entity target) {
        if (first) {
            lastX = target.getX();
            lastY = target.getY();
            lastZ = target.getZ();
            first = false;
            return;
        }
        double vx = target.getX() - lastX;
        double vy = target.getY() - lastY;
        double vz = target.getZ() - lastZ;

        // exponential moving average of acceleration
        ax = 0.7 * ax + 0.3 * (vx - target.getVelocity().x);
        ay = 0.7 * ay + 0.3 * (vy - target.getVelocity().y);
        az = 0.7 * az + 0.3 * (vz - target.getVelocity().z);

        samples++;
        lastX = target.getX();
        lastY = target.getY();
        lastZ = target.getZ();
    }

    public void reset() {
        first = true;
        ax = ay = az = 0;
        samples = 0;
    }

    /**
     * Predict position N ticks ahead with velocity + learned acceleration correction.
     */
    public Vec3d predict(Entity target, int ticks) {
        double px = target.getX();
        double py = target.getY();
        double pz = target.getZ();
        double vx = target.getVelocity().x + ax * 0.5;
        double vy = target.getVelocity().y + ay * 0.5;
        double vz = target.getVelocity().z + az * 0.5;

        for (int i = 0; i < ticks; i++) {
            vx *= 0.91;
            vz *= 0.91;
            vy = (vy - 0.08) * 0.98;
            px += vx;
            py += vy;
            pz += vz;
        }
        return new Vec3d(px, py, pz);
    }

    /**
     * Confidence 0..1 based on sample count - don't trust prediction on a fresh target.
     */
    public double confidence() {
        return Math.min(1.0, samples / 20.0);
    }

    /**
     * Is target strafing hard right now? Useful for feint logic.
     */
    public boolean isStrafingHard(Entity target) {
        double v = Math.sqrt(target.getVelocity().x * target.getVelocity().x + target.getVelocity().z * target.getVelocity().z);
        return v > 0.22;
    }
}
