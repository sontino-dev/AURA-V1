package com.brody.aura.rotation;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Raytrace utility. Ported from VCore Managers.PLAYER.checkRtx and
 * ThunderHack Managers.PLAYER.checkRtx. Verifies that a rotation actually
 * lands a line of sight on the target hitbox within reach.
 */
public final class RaytraceUtil {

    private RaytraceUtil() {
    }

    /**
     * True if rotating to (yaw, pitch) gives a clear ray to the target within range.
     */
    public static boolean checkRtx(MinecraftClient mc, float yaw, float pitch, double range, double wallRange, boolean raytrace) {
        if (!raytrace) return true;
        Vec3d eye = mc.player.getEyePos();
        Vec3d rotation = getRotationVector(yaw, pitch);
        Vec3d end = eye.add(rotation.multiply(range));

        BlockHitResult blockHit = mc.world.raycast(new net.minecraft.world.RaycastContext(
                eye, end,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE,
                mc.player
        ));

        boolean blockedByWall = blockHit != null && blockHit.getType() != HitResult.Type.MISS;
        if (blockedByWall) {
            // If wall range allows it, we can still hit through the wall
            if (wallRange <= 0) return false;
            // continue past the wall up to wallRange
            Vec3d wallEnd = eye.add(rotation.multiply(wallRange));
            BlockHitResult deeper = mc.world.raycast(new net.minecraft.world.RaycastContext(
                    blockHit.getPos().add(rotation.multiply(0.1)),
                    wallEnd,
                    net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                    net.minecraft.world.RaycastContext.FluidHandling.NONE,
                    mc.player
            ));
            return deeper == null || deeper.getType() == HitResult.Type.MISS;
        }
        return true;
    }

    /**
     * Full scan over the target hitbox to find any visible point within range.
     * Same idea as ThunderHack isInRange - grid sample the box.
     */
    public static boolean isBoxReachable(MinecraftClient mc, Entity target, double range, double wallRange, boolean raytrace) {
        if (target == null || mc.player == null) return false;

        net.minecraft.util.math.Box bb = target.getBoundingBox();
        Vec3d eye = mc.player.getEyePos();

        // fast reject: closest point distance
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, bb.minX, bb.maxX),
                MathHelper.clamp(eye.y, bb.minY, bb.maxY),
                MathHelper.clamp(eye.z, bb.minZ, bb.maxZ)
        );
        if (eye.squaredDistanceTo(closest) > range * range) return false;

        double halfBox = bb.getLengthX() / 2.0;
        double lengthY = bb.getLengthY();

        for (float x1 = -(float) halfBox; x1 <= halfBox; x1 += 0.15f) {
            for (float z1 = -(float) halfBox; z1 <= halfBox; z1 += 0.15f) {
                for (float y1 = 0.05f; y1 <= lengthY; y1 += 0.25f) {
                    Vec3d point = new Vec3d(target.getX() + x1, target.getY() + y1, target.getZ() + z1);
                    if (eye.squaredDistanceTo(point) > range * range) continue;

                    float[] rot = GcdUtil.calcAngle(eye, point);
                    if (checkRtx(mc, rot[0], rot[1], range, wallRange, raytrace)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static Vec3d getRotationVector(float yaw, float pitch) {
        float yawRad = yaw * ((float) Math.PI / 180f);
        float pitchRad = pitch * ((float) Math.PI / 180f);
        float cosPitch = MathHelper.cos(pitchRad);
        return new Vec3d(
                (-MathHelper.sin(yawRad) * cosPitch),
                (-MathHelper.sin(pitchRad)),
                (MathHelper.cos(yawRad) * cosPitch)
        );
    }
}
