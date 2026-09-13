package com.brody.aura.mixin;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook attackEntity to log attack range for debugging and AI feedback.
 *
 * Verified against 1.21.1 yarn: the only signature is
 * attackEntity(PlayerEntity, Entity) - no Hand parameter.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("TAIL"))
    public void onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || target == null) return;

        double dist = mc.player.getEyePos().distanceTo(target.getBoundingBox().getCenter());
        KillAuraMod.CONFIG.setExtra("lastAttackDistance", dist);

        if (KillAuraMod.CONFIG.debugLog) {
            KillAuraMod.LOGGER.info("[Brody] attack {} dist={}", target.getType().getName().getString(), String.format("%.2f", dist));
        }
    }
}
