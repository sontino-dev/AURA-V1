package com.brody.aura.mixin;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.VulcanMode;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * CatLean-style attribute-level reach.
 *
 * Since 1.20.5, interaction range lives in the entity_interaction_range
 * attribute and everything downstream (GameRenderer crosshair picking,
 * ClientPlayerInteractionManager, canInteractWithEntity) reads it from
 * {@link PlayerEntity#getEntityInteractionRange()}. CatLean hooks exactly
 * this via its ReachStateEvent -> blockInteractionRange/entityInteractionRange
 * mixin so the client-side raycast stays consistent with the reach the client
 * actually attacks at.
 *
 * We only ever RAISE the value above vanilla 3.0 (needed when VulcanMode RAGE
 * is configured with reach > 3.0). At LEGIT/BALANCED the value stays at
 * vanilla 3.0 and this hook is a no-op in practice.
 */
@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {

    @Inject(method = "getEntityInteractionRange", at = @At("HEAD"), cancellable = true)
    public void brody$entityInteractionRange(CallbackInfoReturnable<Double> cir) {
        if (KillAuraMod.MODULES.get("VulcanMode") instanceof VulcanMode vm && vm.isEnabled()) {
            cir.setReturnValue(vm.effectiveClientRange());
        }
    }
}
