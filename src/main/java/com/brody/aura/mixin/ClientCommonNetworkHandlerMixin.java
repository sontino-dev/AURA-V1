package com.brody.aura.mixin;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.Module;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Silent rotation packet patch.
 *
 * Since 1.20.2 the sendPacket entry point lives on ClientCommonNetworkHandler
 * (parent of ClientPlayNetworkHandler / ClientConfigurationNetworkHandler).
 * ClientPlayerEntity.sendMovementPackets() routes every move packet through
 * ClientPlayNetworkHandler.sendPacket -> inherited ClientCommonNetworkHandler
 * .sendPacket, so injecting here at HEAD sees (and can rewrite) the packet
 * object before it reaches the connection.
 */
@Mixin(ClientCommonNetworkHandler.class)
public abstract class ClientCommonNetworkHandlerMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    public void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return;

        // Silent rotation injection: patch yaw/pitch into movement packets
        if (packet instanceof PlayerMoveC2SPacket move) {
            Module killAura = KillAuraMod.MODULES.get("KillAura");
            if (killAura != null
                    && killAura.isEnabled()
                    && KillAuraMod.CONFIG.rotateSilent
                    && hasRotation(move)) {
                float yaw = KillAuraMod.ROTATIONS.getRotationYaw();
                float pitch = KillAuraMod.ROTATIONS.getRotationPitch();
                // packet object itself carries the fields, accessor mixin patches it
                ((PlayerMoveC2SPacketAccessor) (Object) move).setYaw(yaw);
                ((PlayerMoveC2SPacketAccessor) (Object) move).setPitch(pitch);
            }
        }
    }

    private static boolean hasRotation(PlayerMoveC2SPacket packet) {
        return packet instanceof PlayerMoveC2SPacket.Full || packet instanceof PlayerMoveC2SPacket.LookAndOnGround;
    }
}
