package com.brody.aura.mixin;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Movement fix: when silent-rotating, movement input is relative to the real
 * camera but the server sees our spoofed yaw. Fix strafe direction so movement
 * stays in the intended direction relative to the spoofed rotation.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "tickMovement", at = @At("HEAD"))
    public void beforeTickMovement(CallbackInfo ci) {
        // input remap happens in tickNewAi via moveFix; hooking here for state sync
        MinecraftClient mc = MinecraftClient.getInstance();
        Module killAura = KillAuraMod.MODULES.get("KillAura");
        if (killAura != null && killAura.isEnabled() && KillAuraMod.ROTATIONS != null) {
            // keep rotation manager synced with real player yaw when no target
            if (ka_noTarget()) {
                KillAuraMod.ROTATIONS.syncFromPlayer(mc);
            }
        }
    }

    private static boolean ka_noTarget() {
        Module killAura = KillAuraMod.MODULES.get("KillAura");
        if (killAura instanceof com.brody.aura.module.KillAura ka) {
            return ka.currentTarget() == null;
        }
        return true;
    }
}
