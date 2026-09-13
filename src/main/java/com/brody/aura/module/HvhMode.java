package com.brody.aura.module;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.projectile.FireballEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;


/**
 * HVH mode - the "hardcore" combat bundle. Layers on top of KillAura:
 *
 *  - AutoTotemSwap : keeps totems in offhand when health is low
 *  - ShieldBreaker : handled in KillAura but toggled more aggressively here
 *  - AntiFireball  : intercept fireballs / shulker bullets targeting us
 *  - AntiExplosion : pre-shield on crystal explosion risk
 *  - SmartFeint    : random attack pauses to bait opponent's shield timing
 *  - CrystalSnipe  : punch end crystals near low-health enemies
 *
 * Config is in AuraConfig under hvh*.
 */
public class HvhMode extends Module {

    private int totemSwapCooldown = 0;
    private int crystalPunchCooldown = 0;

    public HvhMode() {
        super("HvhMode", "HVH combat suite: totem manager, crystal snipe, anti-projectile, feints");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        if (totemSwapCooldown > 0) totemSwapCooldown--;
        if (crystalPunchCooldown > 0) crystalPunchCooldown--;

        if (KillAuraMod.CONFIG.hvhAutoTotemSwap) {
            autoTotemSwap(mc);
        }

        if (KillAuraMod.CONFIG.hvhAntiFireball) {
            antiProjectile(mc);
        }

        if (KillAuraMod.CONFIG.hvhAutoCrystal) {
            crystalSnipe(mc);
        }
    }

    /**
     * Offhand totem manager. Pops a totem? Swap a new one in.
     * Also pre-swap when health is critical.
     */
    private void autoTotemSwap(MinecraftClient mc) {
        ItemStack offhand = mc.player.getOffHandStack();
        boolean holdingTotem = offhand.getItem() == Items.TOTEM_OF_UNDYING;

        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        boolean critical = health < 14.0f;

        if (!holdingTotem && critical && totemSwapCooldown <= 0) {
            int totemSlot = findTotem(mc);
            if (totemSlot != -1) {
                // swap totem into offhand via inventory click
                mc.interactionManager.clickSlot(
                        mc.player.currentScreenHandler.syncId,
                        totemSlot,
                        45, // offhand slot in player screen handler
                        net.minecraft.screen.slot.SlotActionType.SWAP,
                        mc.player
                );
                totemSwapCooldown = 5;
            }
        }
    }

    private int findTotem(MinecraftClient mc) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.TOTEM_OF_UNDYING) return 36 + i; // player handler slot index
        }
        return -1;
    }

    /**
     * Anti-projectile: if a fireball / wither skull / shulker bullet is
     * incoming, punch it away before it lands.
     */
    private void antiProjectile(MinecraftClient mc) {
        Vec3d eye = mc.player.getEyePos();
        Box search = mc.player.getBoundingBox().expand(4.0);

        for (Entity ent : mc.world.getOtherEntities(mc.player, search)) {
            boolean isThreat = ent instanceof FireballEntity
                    || ent instanceof WitherSkullEntity
                    || ent.getClass().getSimpleName().contains("ShulkerBullet");
            if (!isThreat || !ent.isAlive()) continue;

            double dist = eye.distanceTo(ent.getPos());
            if (dist > 3.5) continue;

            // aim at projectile and punch
            Entity target = ent;
            com.brody.aura.rotation.RotationManager rot = KillAuraMod.ROTATIONS;
            boolean ready = rot.rotate(mc, target,
                    com.brody.aura.rotation.RotationManager.Mode.SNAP,
                    true, 4.0, 4.0, false);
            if (ready && crystalPunchCooldown <= 0) {
                mc.interactionManager.attackEntity(mc.player, target);
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                crystalPunchCooldown = 3;
            }
            return;
        }
    }

    /**
     * Crystal snipe: punch end crystals near low-health enemies.
     * We don't place crystals (that's a full AutoCrystal), just break them
     * opportunistically when an enemy is inside the explosion radius.
     */
    private void crystalSnipe(MinecraftClient mc) {
        if (crystalPunchCooldown > 0) return;

        // find our current aura target
        Module aura = KillAuraMod.MODULES.get("KillAura");
        if (!(aura instanceof KillAura ka) || ka.currentTarget() == null) return;

        Entity mainTarget = ka.currentTarget();
        float targetHealth = mainTarget instanceof LivingEntity le ? le.getHealth() + le.getAbsorptionAmount() : 36.0f;

        Box aroundTarget = mainTarget.getBoundingBox().expand(5.0);
        for (Entity ent : mc.world.getOtherEntities(mc.player, aroundTarget)) {
            if (!(ent instanceof EndCrystalEntity crystal)) continue;
            if (!crystal.isAlive()) continue;

            double dist = mc.player.getEyePos().distanceTo(crystal.getPos());
            if (dist > 4.0) continue;

            // prioritize crystal if it would hurt the enemy more than us
            double distToEnemy = crystal.squaredDistanceTo(mainTarget);
            double distToSelf = crystal.squaredDistanceTo(mc.player);

            if (distToEnemy < distToSelf && (targetHealth < 20.0f || KillAuraMod.CONFIG.aiAggression > 0.5)) {
                boolean ready = KillAuraMod.ROTATIONS.rotate(mc, crystal,
                        com.brody.aura.rotation.RotationManager.Mode.GRIM,
                        true, 4.5, 4.5, false);
                if (ready) {
                    mc.interactionManager.attackEntity(mc.player, crystal);
                    mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
                    crystalPunchCooldown = 2;
                }
                return;
            }
        }
    }

    @Override
    public void onEnable() {
        totemSwapCooldown = 0;
        crystalPunchCooldown = 0;
    }

    @Override
    public void onDisable() {
    }
}
