package com.brody.aura.rotation;

import com.brody.aura.KillAuraMod;
import com.brody.aura.ai.AuraAI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Rotation engine. Three modes ported from the reference clients:
 *  - TRACK : smoothed yaw/pitch steps, DVD-logo random aim point inside hitbox
 *  - GRIM  : direct aim + grim gcd quantize (fast, packet-accurate)
 *  - SNAP  : instant snap on attack tick
 * Silent rotations are applied via mixin right before the move packet goes out.
 */
public class RotationManager {

    public enum Mode { TRACK, GRIM, SNAP, NONE }

    public float rotationYaw;
    public float rotationPitch;
    public float pitchAcceleration = 1.0f;
    public boolean lookingAtHitbox = false;

    /**
     * VulcanMode / AttackPlanner aim override (CatLean getPoint idea).
     * When non-null, rotate() aims at this point instead of the mode-default
     * point. Set/cleared by VulcanMode.onTargetTick / onAttacked.
     */
    public Vec3d aimOverride = null;

    // overshoot -> corrective step pairing (human re-centering behavior)
    private float pendingCorrection = 0.0f;

    // DVD-logo style aim point drift inside the target hitbox
    private Vec3d rotationPoint = Vec3d.ZERO;
    private Vec3d rotationMotion = Vec3d.ZERO;

    private int snapTicksLeft = 0;

    public void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        // rotation captured by mixin on packet send
    }

    /**
     * Rotate towards the target. Returns true when rotation is "ready" (close enough to attack).
     */
    public boolean rotate(MinecraftClient mc, Entity target, Mode mode, boolean readyForCrit, double range, double wallRange, boolean raytrace) {
        if (target == null || mc.player == null) {
            lookingAtHitbox = false;
            return false;
        }

        if (Float.isNaN(rotationYaw)) rotationYaw = mc.player.getYaw();
        if (Float.isNaN(rotationPitch)) rotationPitch = mc.player.getPitch();

        // Snap countdown
        if (readyForCrit && mode == Mode.SNAP) {
            snapTicksLeft = 2;
        } else if (snapTicksLeft > 0) {
            snapTicksLeft--;
        }

        Vec3d aimPoint = aimOverride != null
                ? aimOverride
                : switch (mode) {
            case GRIM -> computeGrimAimPoint(target);
            case SNAP -> target.getEyePos();
            default -> computeTrackAimPoint(target, mc, range, wallRange, raytrace);
        };

        if (KillAuraMod.CONFIG.aiEnabled && KillAuraMod.CONFIG.aiPredictVelocity) {
            aimPoint = AuraAI.getPredictedAimPoint(target, aimPoint, KillAuraMod.CONFIG.aiPredictionTicks);
        }

        float[] targetRot = GcdUtil.calcAngle(mc.player.getEyePos(), aimPoint);

        // delta yaw/pitch
        float deltaYawRaw = MathHelper.wrapDegrees(MathHelper.wrapDegrees(targetRot[0]) - rotationYaw);
        float deltaPitch = (float) (targetRot[1] - rotationPitch);

        float yawStep;
        float pitchStep;

        switch (mode) {
            case TRACK -> {
                // maxRotationStepDeg acts as a hard per-tick cap: large turns
                // get SPLIT across ticks (Vulcan KillAura A/C punish
                // turn-then-hit-on-same-tick patterns)
                double speed = Math.min(
                        KillAuraMod.CONFIG.rotationSpeed + (Math.random() * KillAuraMod.CONFIG.rotationJitter),
                        KillAuraMod.CONFIG.maxRotationStepDeg > 0 ? KillAuraMod.CONFIG.maxRotationStepDeg : 360.0
                );
                yawStep = (float) MathHelper.clamp(speed, 1.0, 180.0);
                // pitch acceleration like VCore Track
                boolean aimed = RaytraceUtil.checkRtx(mc, rotationYaw, rotationPitch, range, wallRange, raytrace);
                pitchAcceleration = aimed
                        ? 1.5f
                        : (pitchAcceleration < 16.0f ? pitchAcceleration * 1.65f : 16.0f);
                pitchStep = pitchAcceleration + (float) (Math.random() - 0.5);
            }
            case GRIM -> {
                yawStep = 360.0f;
                pitchStep = 180.0f;
                // small random jitter to avoid pixel-perfect detection
                deltaYawRaw += (float) (Math.random() * 2.0 - 1.0);
                deltaPitch += (float) (Math.random() * 2.0 - 1.0);
            }
            case SNAP -> {
                yawStep = 360.0f;
                pitchStep = 180.0f;
            }
            default -> {
                lookingAtHitbox = false;
                return false;
            }
        }

        if (readyForCrit) {
            yawStep = 180.0f;
            pitchStep = 90.0f;
        }

        float clampedYaw = MathHelper.clamp(MathHelper.abs(deltaYawRaw), -yawStep, yawStep);
        float clampedPitch = MathHelper.clamp(deltaPitch, -pitchStep, pitchStep);

        float newYaw = rotationYaw + (deltaYawRaw > 0 ? clampedYaw : -clampedYaw);
        float newPitch = MathHelper.clamp(rotationPitch + clampedPitch, -90.0f, 90.0f);

        // GCD compliance
        if (KillAuraMod.CONFIG.gcdCompliant) {
            if (mode == Mode.GRIM) {
                float[] corrected = GcdUtil.applyGrimQuantize(mc, newYaw, newPitch);
                rotationYaw = corrected[0];
                rotationPitch = corrected[1];
            } else {
                double gcd = GcdUtil.getGcd(mc);
                rotationYaw = (float) (newYaw - (newYaw - rotationYaw) % gcd);
                rotationPitch = (float) (newPitch - (newPitch - rotationPitch) % gcd);
            }
        } else {
            rotationYaw = newYaw;
            rotationPitch = newPitch;
        }

        // humanization: overshoot with corrective re-centering pair
        if (KillAuraMod.CONFIG.humanizedAim) {
            if (pendingCorrection != 0.0f) {
                // humans who overshoot visibly pull back on the next tick
                rotationYaw = GcdUtil.applyGcd(rotationYaw + pendingCorrection, rotationYaw, GcdUtil.getGcd(mc));
                pendingCorrection = 0.0f;
            } else {
                float overshoot = (float) ((Math.random() - 0.5) * KillAuraMod.CONFIG.aimOvershootDeg);
                rotationYaw = GcdUtil.applyGcd(rotationYaw + overshoot, rotationYaw, GcdUtil.getGcd(mc));
                if (KillAuraMod.CONFIG.vulcanOvershootCorrect && Math.abs(overshoot) > 0.05f) {
                    pendingCorrection = -overshoot * (0.6f + (float) Math.random() * 0.5f);
                }
            }
        }

        // hitbox check
        lookingAtHitbox = RaytraceUtil.checkRtx(mc, rotationYaw, rotationPitch, range, wallRange, raytrace)
                && isAimOnHitbox(mc, target);

        return lookingAtHitbox || mode == Mode.SNAP && snapTicksLeft > 0;
    }

    private boolean isAimOnHitbox(MinecraftClient mc, Entity target) {
        Vec3d eye = mc.player.getEyePos();
        Vec3d rot = RaytraceUtil.getRotationVector(rotationYaw, rotationPitch);
        Vec3d end = eye.add(rot.multiply(KillAuraMod.CONFIG.reach + 1.0));

        net.minecraft.util.math.Box bb = target.getBoundingBox().expand(0.05);
        net.minecraft.util.hit.EntityHitResult hit = net.minecraft.entity.projectile.ProjectileUtil.raycast(
                mc.player, eye, end, bb, e -> e == target, 6.0
        );
        return hit != null && hit.getEntity() == target;
    }

    private Vec3d computeGrimAimPoint(Entity target) {
        return target.getBoundingBox().getCenter();
    }

    /**
     * DVD-logo drifting aim point inside the target hitbox.
     * This is the ThunderHack "legit look" technique - the crosshair wanders
     * around the hitbox like a human tracing the model, not snapping to center.
     */
    private Vec3d computeTrackAimPoint(Entity target, MinecraftClient mc, double range, double wallRange, boolean raytrace) {
        float minMotionXZ = 0.003f;
        float maxMotionXZ = 0.03f;
        float minMotionY = 0.001f;
        float maxMotionY = 0.03f;

        double lengthX = target.getBoundingBox().getLengthX();
        double lengthY = target.getBoundingBox().getLengthY();
        double lengthZ = target.getBoundingBox().getLengthZ();

        if (rotationMotion.equals(Vec3d.ZERO)) {
            rotationMotion = new Vec3d(
                    rnd(-0.05, 0.05), rnd(-0.05, 0.05), rnd(-0.05, 0.05)
            );
        }

        rotationPoint = rotationPoint.add(rotationMotion);

        // bounce inside hitbox
        if (rotationPoint.x >= (lengthX - 0.05) / 2.0) {
            rotationMotion = new Vec3d(-rnd(minMotionXZ, maxMotionXZ), rotationMotion.y, rotationMotion.z);
        }
        if (rotationPoint.y >= lengthY) {
            rotationMotion = new Vec3d(rotationMotion.x, -rnd(minMotionY, maxMotionY), rotationMotion.z);
        }
        if (rotationPoint.z >= (lengthZ - 0.05) / 2.0) {
            rotationMotion = new Vec3d(rotationMotion.x, rotationMotion.y, -rnd(minMotionXZ, maxMotionXZ));
        }
        if (rotationPoint.x <= -(lengthX - 0.05) / 2.0) {
            rotationMotion = new Vec3d(rnd(minMotionXZ, maxMotionXZ), rotationMotion.y, rotationMotion.z);
        }
        if (rotationPoint.y <= 0.05) {
            rotationMotion = new Vec3d(rotationMotion.x, rnd(minMotionY, maxMotionY), rotationMotion.z);
        }
        if (rotationPoint.z <= -(lengthZ - 0.05) / 2.0) {
            rotationMotion = new Vec3d(rotationMotion.x, rotationMotion.y, rnd(minMotionXZ, maxMotionXZ));
        }

        // jitter
        rotationPoint.add(rnd(-0.03, 0.03), 0.0, rnd(-0.03, 0.03));

        boolean canSee = mc.player.canSee(target);

        // If raytrace says we can't hit from here, scan for a visible point
        if (!canSee || !RaytraceUtil.checkRtx(mc, rotationYaw, rotationPitch, range, wallRange, raytrace)) {
            Vec3d centerOffset = new Vec3d(0, target.getEyeHeight(target.getPose()) / 2.0f, 0);
            Vec3d centerPos = target.getPos().add(centerOffset);
            float[] centerRot = GcdUtil.calcAngle(mc.player.getEyePos(), centerPos);
            boolean centerVisible = RaytraceUtil.checkRtx(mc, centerRot[0], centerRot[1], range, 0, raytrace);

            if (centerVisible) {
                rotationPoint = new Vec3d(rnd(-0.1, 0.1), target.getEyeHeight(target.getPose()) / rnd(1.8, 2.5), rnd(-0.1, 0.1));
            } else {
                double halfBox = lengthX / 2.0;
                outer:
                for (float x1 = -(float) halfBox; x1 <= halfBox; x1 += 0.05f) {
                    for (float z1 = -(float) halfBox; z1 <= halfBox; z1 += 0.05f) {
                        for (float y1 = 0.05f; y1 <= lengthY; y1 += 0.15f) {
                            Vec3d v = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
                            if (mc.player.getEyePos().squaredDistanceTo(v) > range * range) continue;
                            float[] r = GcdUtil.calcAngle(mc.player.getEyePos(), v);
                            if (RaytraceUtil.checkRtx(mc, r[0], r[1], range, 0, raytrace)) {
                                rotationPoint = new Vec3d(x1, y1, z1);
                                break outer;
                            }
                        }
                    }
                }
            }
        }

        return target.getPos().add(rotationPoint);
    }

    /**
     * Current rotation to apply to packets (silent).
     */
    public float getRotationYaw() {
        return rotationYaw;
    }

    public float getRotationPitch() {
        return rotationPitch;
    }

    public void syncFromPlayer(MinecraftClient mc) {
        if (mc.player != null) {
            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
        }
    }

    public void reset(MinecraftClient mc) {
        rotationPoint = Vec3d.ZERO;
        rotationMotion = Vec3d.ZERO;
        pitchAcceleration = 1.0f;
        snapTicksLeft = 0;
        lookingAtHitbox = false;
        aimOverride = null;
        pendingCorrection = 0.0f;
        if (mc.player != null) {
            rotationYaw = mc.player.getYaw();
            rotationPitch = mc.player.getPitch();
        }
    }

    private static double rnd(double min, double max) {
        return min + Math.random() * (max - min);
    }
}
