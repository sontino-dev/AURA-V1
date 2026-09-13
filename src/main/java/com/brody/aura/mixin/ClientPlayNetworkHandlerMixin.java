package com.brody.aura.mixin;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Incoming play-phase packet hooks:
 *  - incoming damage status (entity status 2) feeds AI hit confirmation
 *  - incoming chat feeds the AI flag detector
 *
 * (Outgoing sendPacket interception lives in ClientCommonNetworkHandlerMixin,
 * because since 1.20.2 sendPacket is declared on ClientCommonNetworkHandler,
 * not on ClientPlayNetworkHandler.)
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {

    @Inject(method = "onEntityStatus", at = @At("TAIL"))
    public void onEntityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // status 2 = entity hurt animation. If the hurt entity is our current
        // aura target right after we attacked, the hit landed - feed the AI.
        if (packet.getStatus() == 2) {
            Module killAura = KillAuraMod.MODULES.get("KillAura");
            if (killAura instanceof com.brody.aura.module.KillAura ka && ka.currentTarget() != null) {
                var hurt = packet.getEntity(mc.world);
                if (hurt == ka.currentTarget()) {
                    ka.notifyDamageConfirmed();
                }
            }
        }
    }

    @Inject(method = "onGameMessage", at = @At("TAIL"))
    public void onGameMessage(GameMessageS2CPacket packet, CallbackInfo ci) {
        KillAuraMod.AI.onServerChat(packet.content().getString());
    }
}
