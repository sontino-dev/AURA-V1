package com.brody.aura.mixin;

import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Mutable access to move packet rotation fields so the silent rotation
 * engine can patch yaw/pitch right before the packet hits the wire.
 *
 * Verified against 1.21.1 yarn: yaw/pitch are "protected final float" on the
 * PlayerMoveC2SPacket base class, so the setters need @Mutable (Mixin strips
 * the final modifier from the target field) or every write would fail.
 */
@Mixin(PlayerMoveC2SPacket.class)
public interface PlayerMoveC2SPacketAccessor {

    @Mutable
    @Accessor("yaw")
    void setYaw(float yaw);

    @Mutable
    @Accessor("pitch")
    void setPitch(float pitch);
}
