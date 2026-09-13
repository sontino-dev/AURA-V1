package com.brody.aura.target;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.ShulkerBulletEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * Target acquisition + sorting. Ported from VCore findTarget/skipEntity
 * and ThunderHack's sorter (distance, health, FOV, durability).
 */
public class TargetManager {

    public enum Sort { DISTANCE, HEALTH, DURABILITY, FOV, ANGLE }

    private Entity lockedTarget;

    public Entity updateTarget(MinecraftClient mc, double range, double fov) {
        if (mc.world == null || mc.player == null) {
            lockedTarget = null;
            return null;
        }

        boolean lock = true;
        if (lockedTarget != null && lock && !skipEntity(mc, lockedTarget, range, fov)) {
            return lockedTarget;
        }
        lockedTarget = null;

        Entity candidate = findTarget(mc, range, fov);
        if (candidate == null) return null;
        lockedTarget = candidate;
        return lockedTarget;
    }

    public Entity findTarget(MinecraftClient mc, double range, double fov) {
        double rotateDist = range + KillAuraMod.CONFIG.wallRange;
        double half = rotateDist + 16.0;
        Vec3d eye = mc.player.getEyePos();
        Box searchBox = new Box(eye.x - half, eye.y - half, eye.z - half, eye.x + half, eye.y + half, eye.z + half);

        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity ent : mc.world.getOtherEntities(mc.player, searchBox)) {
            // incoming projectiles priority
            if ((ent instanceof ShulkerBulletEntity || ent instanceof FireballEntity)
                    && ent.isAlive()
                    && KillAuraMod.CONFIG.hvhAntiFireball
                    && mc.player.getEyePos().squaredDistanceTo(ent.getPos()) < range * range) {
                return ent;
            }

            if (skipEntity(mc, ent, range, fov)) continue;
            if (ent instanceof LivingEntity living) {
                candidates.add(living);
            }
        }

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        Sort sort;
        try {
            sort = Sort.valueOf(KillAuraMod.CONFIG.targetSort.toUpperCase());
        } catch (IllegalArgumentException e) {
            sort = Sort.DISTANCE;
        }

        return switch (sort) {
            case DISTANCE -> findMin(candidates, e -> mc.player.squaredDistanceTo(e.getPos()));
            case HEALTH -> findMin(candidates, e -> e.getHealth() + e.getAbsorptionAmount());
            case DURABILITY -> findMin(candidates, TargetManager::computeDurabilityScore);
            case FOV -> findMin(candidates, e -> GcdAngle(mc, e));
            case ANGLE -> findMin(candidates, e -> GcdAngle(mc, e));
        };
    }

    private static float GcdAngle(MinecraftClient mc, LivingEntity e) {
        return com.brody.aura.rotation.GcdUtil.getFovAngle(mc, e);
    }

    private static <T> T findMin(List<T> list, ToDoubleFunction<T> keyFn) {
        T best = null;
        double bestKey = Double.POSITIVE_INFINITY;
        for (T item : list) {
            double key = keyFn.applyAsDouble(item);
            if (key < bestKey) {
                bestKey = key;
                best = item;
            }
        }
        return best;
    }

    private static double computeDurabilityScore(LivingEntity e) {
        float v = 0.0f;
        for (ItemStack armor : e.getArmorItems()) {
            if (armor != null && !armor.isEmpty()) {
                int max = armor.getMaxDamage();
                if (max > 0) {
                    v += (float) (max - armor.getDamage()) / max;
                }
            }
        }
        return v;
    }

    public boolean skipEntity(MinecraftClient mc, Entity entity, double range, double fov) {
        if (entity == null) return true;
        if (!(entity instanceof LivingEntity ent)) return true;
        if (ent.isDead() || !entity.isAlive()) return true;
        if (entity instanceof ArmorStandEntity) return true;
        if (entity instanceof CatEntity) return true;

        if (entity instanceof SlimeEntity && !KillAuraMod.CONFIG.targetMobs) return true;
        if (entity instanceof HostileEntity && !KillAuraMod.CONFIG.targetMobs) return true;
        if (entity instanceof PlayerEntity && !KillAuraMod.CONFIG.targetPlayers) return true;
        if (entity instanceof VillagerEntity && !KillAuraMod.CONFIG.targetMobs) return true;
        if (entity instanceof AnimalEntity && !KillAuraMod.CONFIG.targetMobs) return true;
        if (entity instanceof MobEntity && !KillAuraMod.CONFIG.targetMobs) return true;

        if (KillAuraMod.CONFIG.targetArmorStands && entity instanceof ArmorStandEntity) return false;

        if (com.brody.aura.rotation.GcdUtil.getFovAngle(mc, entity) > fov) return true;

        if (entity instanceof PlayerEntity player) {
            if (player == mc.player) return true;
            if (player.isCreative()) return true;
            if (player.isInvisible() && !KillAuraMod.CONFIG.targetPlayers) return true;
            // friend check hook - extend later with a friend list
        }

        // range check: closest point on hitbox
        Vec3d eye = mc.player.getEyePos();
        Box bb = entity.getBoundingBox();
        Vec3d closest = new Vec3d(
                MathHelper.clamp(eye.x, bb.minX, bb.maxX),
                MathHelper.clamp(eye.y, bb.minY, bb.maxY),
                MathHelper.clamp(eye.z, bb.minZ, bb.maxZ)
        );
        double attackRange = KillAuraMod.CONFIG.reach + KillAuraMod.CONFIG.wallRange;
        if (eye.squaredDistanceTo(closest) > attackRange * attackRange) return true;

        if (!KillAuraMod.CONFIG.attackThroughWalls) {
            if (!mc.player.canSee(entity)) return true;
        }

        return false;
    }

    public Entity getLockedTarget() {
        return lockedTarget;
    }

    public void clear() {
        lockedTarget = null;
    }
}
