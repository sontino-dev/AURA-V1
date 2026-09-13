package com.brody.aura.module;

import net.minecraft.client.MinecraftClient;

/**
 * VelocityFix placeholder - reduces knockback perception client-side.
 * NOTE: real KB reduction requires server-trusted packets which is out of scope;
 * this module smooths the camera shake after being hit.
 */
public class VelocityFix extends Module {

    public VelocityFix() {
        super("VelocityFix", "Smooths knockback camera shake (visual)");
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;
        // anti-kb camera smoothing: dampen pitch kick
        if (mc.player.hurtTime > 0 && mc.player.hurtTime < 5) {
            // keep pitch stable
            if (mc.player.getPitch() > 15) {
                mc.player.setPitch(mc.player.getPitch() - 0.5f);
            }
        }
    }
}
