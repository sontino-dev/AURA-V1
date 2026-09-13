package com.brody.aura.rotation;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * CatLean-inspired attack planner.
 *
 * Reverse-engineered from the CatLean ghost client (Pan4ur/CatLean jar,
 * decompiled with Vineflower). CatLean exposes an addon API class
 * {@code su.catlean.api.addon.feature.AddonAuraRotation} whose shape reveals
 * the internal aura architecture:
 *
 * <pre>
 *   Rotation getRotation(Entity target, Rotation currentRotation, boolean attackTick)
 *   Vec3     getPoint(Entity target)
 *   boolean  needRayCast()
 *   boolean  needExtend()
 * </pre>
 *
 * i.e. the aura (a) picks a randomized point ON the target hitbox and
 * (b) generates the rotation curve with awareness of whether the current tick
 * is the attack tick. Their marketing layer ("WhiskerAura: humanized KillAura
 * with randomized reach and rotation curves that read as legit") confirms the
 * intent: the aim path must look like a human tracing the model.
 *
 * This class implements that architecture with improvements over CatLean:
 *
 * 1. Reach-clamped point picking - every candidate point must sit INSIDE the
 *    per-attack randomized reach (server-side reach checks measure eye->box,
 *    so aiming at a point farther than the attack reach is what flags you).
 *    CatLean randomizes reach but its point picker is not reach-aware.
 * 2. Repick-stability - a point survives N ticks so the GCD-quantized
 *    rotation curve stays clean instead of micro-jittering every tick.
 * 3. Visibility-validated fallback - when nothing passes the wall raycast,
 *    degrade to eye-height center of the visible face (what a human does:
 *    aim at whatever body part is exposed).
 */
public class AttackPlanner {

    private Vec3d pickedPoint = null;
    private Entity pickedFor = null;
    private int pickedAtTick = -999;
    private boolean lastPickVisible = false;
    private float lastPlanYaw;
    private float lastPlanPitch;

    /**
     * CatLean {@code getPoint(target)} equivalent.
     *
     * Returns a randomized, raycast-validated point on the target hitbox,
     * clamped inside {@code reach}. Points are gaussian-biased towards the
     * horizontal center of the box (curves stay short and smooth) with a
     * head/chest height bias (players aim at the upper body).
     */
    public Vec3d pickPoint(MinecraftClient mc, Entity target, double reach) {
        if (mc.player == null || target == null) return null;

        Box box = target.getBoundingBox().expand(0.03);
        Vec3d eye = mc.player.getEyePos();
        Vec3d center = box.getCenter();
        double halfX = box.getLengthX() / 2.0;
        double halfZ = box.getLengthZ() / 2.0;

        Vec3d best = null;
        double bestScore = -Double.MAX_VALUE;
        double safeReach = Math.max(1.5, reach - 0.08);

        for (int i = 0; i < 24; i++) {
            double u = gauss();            // 0..1 gaussian-ish
            double v = gauss();
            double w = gauss();

            // head/chest bias: 55% of candidates forced into the upper half
            double y = v;
            if (Math.random() < 0.55) {
                y = 0.55 + 0.45 * Math.random();
            }

            Vec3d p = new Vec3d(
                    box.minX + MathHelper.clamp(u, 0.05, 0.95) * box.getLengthX(),
                    box.minY + MathHelper.clamp(y, 0.02, 0.98) * box.getLengthY(),
                    box.minZ + MathHelper.clamp(w, 0.05, 0.95) * box.getLengthZ()
            );

            // improvement over CatLean: candidate must be inside attack reach
            if (eye.squaredDistanceTo(p) > safeReach * safeReach) continue;

            // must be hittable (no wall between eye and point)
            float[] rot = GcdUtil.calcAngle(eye, p);
            if (!RaytraceUtil.checkRtx(mc, rot[0], rot[1], reach, 0.0, true)) continue;

            // prefer points near the horizontal center: shorter curves, more
            // tolerance when the target moves between the move packet and the
            // attack packet
            double off = Math.abs(p.x - center.x) / Math.max(0.001, halfX)
                    + Math.abs(p.z - center.z) / Math.max(0.001, halfZ);
            double score = 1.0 - off;

            if (score > bestScore) {
                bestScore = score;
                best = p;
                lastPickVisible = true;
            }
        }

        if (best == null) {
            // fallback: exposed-face center at chest/eye height
            best = new Vec3d(center.x,
                    Math.min(box.maxY - 0.1, box.minY + box.getLengthY() * 0.7),
                    center.z);
            lastPickVisible = mc.player.canSee(target);
        }

        pickedPoint = best;
        pickedFor = target;
        pickedAtTick = mc.player.age;
        return best;
    }

    /**
     * Attack-tick-aware point refresh (CatLean {@code getRotation(..., attackTick)}).
     *
     * While the hit cooldown runs down we keep drifting on a stable point;
     * as the attack tick approaches we re-validate the point against the
     * CURRENT target position (they move!) so the final convergence leg aims
     * at where the hitbox will be, not where it was.
     */
    public Vec3d refreshPoint(MinecraftClient mc, Entity target, double reach, int ticksToAttack, boolean pointPickEnabled) {
        if (!pointPickEnabled) {
            pickedFor = target;
            return null; // caller falls back to engine-default aim point
        }

        int now = mc.player != null ? mc.player.age : 0;
        int repickEvery = Math.max(2, (int) Math.round(KillAuraMod.CONFIG.vulcanRepickTicks));

        boolean stale = pickedPoint == null
                || pickedFor != target
                || now - pickedAtTick >= repickEvery;

        // near attack tick: always re-validate (target motion compensation)
        if (ticksToAttack <= 1) stale = true;

        if (stale) {
            return pickPoint(mc, target, reach);
        }
        return pickedPoint;
    }

    /**
     * Sanity check used before the attack is released: is our current planned
     * rotation actually pointing at the picked point (within attack tolerance)?
     */
    public boolean isPlanAligned(MinecraftClient mc, Entity target, double reach) {
        if (pickedPoint == null || mc.player == null) return false;
        float[] want = GcdUtil.calcAngle(mc.player.getEyePos(), pickedPoint);
        float dy = Math.abs(MathHelper.wrapDegrees(want[0] - lastPlanYaw));
        float dp = Math.abs(want[1] - lastPlanPitch);
        // ~2 blocks lateral tolerance at reach distance; generous on purpose
        return dy < 12.0f && dp < 12.0f;
    }

    public void trackPlanned(float yaw, float pitch) {
        lastPlanYaw = yaw;
        lastPlanPitch = pitch;
    }

    public Vec3d currentPoint() {
        return pickedPoint;
    }

    public boolean wasLastPickVisible() {
        return lastPickVisible;
    }

    public void reset() {
        pickedPoint = null;
        pickedFor = null;
        pickedAtTick = -999;
        lastPickVisible = false;
    }

    /** Sum of 3 uniforms ~ bell curve centered at 0.5. */
    private static double gauss() {
        return (Math.random() + Math.random() + Math.random()) / 3.0;
    }
}
