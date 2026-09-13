package com.brody.aura.anticheat;

import com.brody.aura.KillAuraMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Anticheat-safe timing layer.
 *
 * Vulcan-focused mitigations researched from the reference clients:
 *
 * 1. Post-swing check  : attack must not swing more than N ticks after move;
 *                        we interleave a move packet right before each attack.
 * 2. Rotation entropy  : GCD-quantized rotations (see GcdUtil) so deltas match
 *                        real mouse movement, plus jitter inside the GCD step.
 * 3. Attack variance   : CPS randomized within band, never metronome.
 * 4. Sprint packets    : drop/return sprint around hits to keep reach checks
 *                        from seeing sprint-boosted attacks.
 * 5. Movement sanity   : never attack while our own movement packets conflict.
 *
 * This is a defensive heuristics layer - no bypass is guaranteed; Vulcan and
 * GrimAC update constantly. The AI backoff (AuraAI.onFlagDetected) is the
 * adaptive part.
 */
public class AnticheatManager {

    private final Deque<Long> recentAttackTimes = new ArrayDeque<>();
    private final Deque<Float> recentRotationDeltas = new ArrayDeque<>();

    /**
     * Called by KillAura right before an attack. Returns false if we should
     * hold the attack this tick (safety gate).
     */
    public boolean gateAttack(MinecraftClient mc) {
        long now = System.currentTimeMillis();

        recentAttackTimes.addLast(now);
        while (recentAttackTimes.size() > 20) recentAttackTimes.removeFirst();

        if (recentAttackTimes.size() >= 20) {
            long window = now - recentAttackTimes.peekFirst();
            double cps = recentAttackTimes.size() / (window / 1000.0);
            if (cps > 20.0) {
                return false;
            }
        }

        if (recentRotationDeltas.size() >= 10) {
            boolean uniform = recentRotationDeltas.stream().distinct().count() == 1;
            if (uniform) return false;
        }

        if (KillAuraMod.CONFIG.vulcanSafe && KillAuraMod.AI.isFlaggedRecently()) {
            return Math.random() < 0.4;
        }

        return true;
    }

    public void recordRotationDelta(float deltaYaw, float deltaPitch) {
        recentRotationDeltas.addLast(Math.abs(deltaYaw) + Math.abs(deltaPitch));
        while (recentRotationDeltas.size() > 10) recentRotationDeltas.removeFirst();
    }

    /**
     * Send a position-sync packet before attacking (Vulcan post mitigation).
     */
    public void sendPreAttackMove(MinecraftClient mc) {
        if (!KillAuraMod.CONFIG.vulcanSafe) return;
        if (mc.player == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                KillAuraMod.ROTATIONS.getRotationYaw(),
                KillAuraMod.ROTATIONS.getRotationPitch(),
                mc.player.isOnGround()
        ));
    }

    /**
     * Send a position-sync packet after attacking to close the post window.
     */
    public void sendPostAttackMove(MinecraftClient mc) {
        if (!KillAuraMod.CONFIG.vulcanSafe) return;
        if (mc.player == null) return;
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.Full(
                mc.player.getX(),
                mc.player.getY(),
                mc.player.getZ(),
                mc.player.getYaw(),
                mc.player.getPitch(),
                mc.player.isOnGround()
        ));
    }

    public double recentCps() {
        if (recentAttackTimes.isEmpty()) return 0;
        long window = System.currentTimeMillis() - recentAttackTimes.peekFirst();
        if (window <= 0) return 0;
        return recentAttackTimes.size() / (window / 1000.0);
    }
}
