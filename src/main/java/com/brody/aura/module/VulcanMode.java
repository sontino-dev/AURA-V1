package com.brody.aura.module;

import com.brody.aura.KillAuraMod;
import com.brody.aura.rotation.AttackPlanner;
import com.brody.aura.rotation.RotationManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * VULCAN MODE - dedicated anti-anticheat layer, inspired by the CatLean
 * ghost client and improved on top of it.
 *
 * == Ideas adopted from CatLean (decompiled Pan4ur/CatLean jar) ==
 *
 * 1. "WhiskerAura" humanized combat:
 *    - randomized per-attack reach (their marketing literally says
 *      "randomized reach and rotation curves that read as legit");
 *    - point-picking on the target hitbox instead of constant center aim
 *      (their AddonAuraRotation.getPoint(Entity) API);
 *    - attack-tick-aware rotation curves
 *      (AddonAuraRotation.getRotation(target, currentRotation, attackTick)).
 *
 * 2. "NineLives Bypass" adaptive cloaking:
 *    - per-server/profile behavior switching tuned to the anticheat in front
 *      of you, instead of one fixed behavior.
 *
 * 3. Attribute-level reach (their ReachStateEvent hooks into
 *    blockInteractionRange/entityInteractionRange): vanilla 1.20.5+ moved
 *    interaction range into an attribute, so patching the attribute keeps
 *    crosshair picking / canInteractWithEntity consistent with what we send.
 *    Implemented here via PlayerEntityMixin -> getEntityInteractionRange().
 *
 * == Improvements over CatLean built into this module ==
 *
 * A. Paranoia engine - every detected flag raises paranoia (0..100) which
 *    progressively shrinks reach, tightens the per-tick rotation clamp and
 *    probabilistically throttles attacks. Decays over time. CatLean ships
 *    static profiles; ours self-adjusts mid-fight (and feeds AuraAI which
 *    already punishes flags).
 *
 * B. KB-compliance hold - after we take damage (knockback/explosion), attacks
 *    are held 1..N random ticks so our attack never coincides with a velocity
 *    that the server hasn't seen applied yet (Vulcan Velocity/Simulation
 *    checks are exactly about this window).
 *
 * C. Anti-metronome sprint reset - w-tap on a configurable SHARE of hits
 *    (default 70%), not every hit, so the sprint packet stream never becomes
 *    periodic. Vulcan's heuristics look for deterministic patterns.
 *
 * D. Rotation-delta gate - attacks are held when this tick's rotation step
 *    exceeds the profile clamp (Vulcan KillAura A/C punish big
 *    turn-then-hit-on-same-tick patterns). The turn finishes first, the hit
 *    rides a smaller, legal delta.
 *
 * E. Profiles: LEGIT / BALANCED / RAGE (CatLean-style) - but every knob is
 *    in the JSON config so Jason can tune per server without recompiling.
 */
public class VulcanMode extends Module {

    public enum Profile {
        //           reachTarget, rotClampDeg, cpsMul, resetChance
        LEGIT(       2.85,        16.0,        0.80,   0.60),
        BALANCED(    2.95,        22.0,        1.00,   0.70),
        RAGE(       -1.0,         45.0,        1.15,   0.90); // -1 = raw CONFIG.reach

        public final double reachTarget;
        public final double rotClampDeg;
        public final double cpsMultiplier;
        public final double resetChance;

        Profile(double reachTarget, double rotClampDeg, double cpsMul, double resetChance) {
            this.reachTarget = reachTarget;
            this.rotClampDeg = rotClampDeg;
            this.cpsMultiplier = cpsMul;
            this.resetChance = resetChance;
        }
    }

    private final AttackPlanner planner = new AttackPlanner();

    // paranoia engine
    private double paranoia = 0.0;

    // knockback compliance
    private int prevHurtTime = 0;
    private int kbHoldTicks = 0;

    // rotation delta gate snapshot (pre-rotate, set by onTargetTick)
    private float preRotateYaw;
    private float preRotatePitch;

    // last rolled attack reach
    private double lastAttackReach = 0.0;

    private Profile profile = Profile.BALANCED;

    public VulcanMode() {
        super("VulcanMode", "CatLean-inspired adaptive anti-anticheat layer (Vulcan/Grim aware)");
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            resetState();
            return;
        }

        reloadProfile();

        // paranoia decay
        if (paranoia > 0.0) {
            paranoia = Math.max(0.0, paranoia - KillAuraMod.CONFIG.vulcanParanoiaDecay);
        }

        // knockback detection: hurtTime jumps from 0 -> >0 on a fresh hit
        // (melee KB, projectiles, explosions). Hold attacks while the server
        // expects our velocity to be settling.
        int hurt = mc.player.hurtTime;
        if (prevHurtTime == 0 && hurt > 0) {
            int maxHold = Math.max(1, KillAuraMod.CONFIG.vulcanKbHoldMax);
            kbHoldTicks = 1 + (int) (Math.random() * maxHold);
            if (KillAuraMod.CONFIG.debugLog) {
                KillAuraMod.LOGGER.info("[Brody] VulcanMode: KB compliance hold {}t (paranoia {})", kbHoldTicks, (int) paranoia);
            }
        }
        prevHurtTime = hurt;

        if (kbHoldTicks > 0) kbHoldTicks--;
    }

    // ------------------------------------------------------- KillAura hooks

    /**
     * Called by KillAura EVERY tick while a target exists, BEFORE
     * RotationManager.rotate(). Sets the silent-aim override point
     * (CatLean getPoint) and snapshots rotation for the delta gate.
     */
    public void onTargetTick(MinecraftClient mc, Entity target) {
        RotationManager rot = KillAuraMod.ROTATIONS;
        if (rot == null) return;

        preRotateYaw = rot.getRotationYaw();
        preRotatePitch = rot.getRotationPitch();

        int eta = 99;
        Module ka = KillAuraMod.MODULES.get("KillAura");
        if (ka instanceof KillAura killAura) {
            eta = killAura.ticksUntilNextAttack();
        }

        double planningReach = lastAttackReach > 0 ? lastAttackReach : effectiveReachTarget();
        Vec3d point = planner.refreshPoint(mc, target, planningReach, eta, KillAuraMod.CONFIG.vulcanPointPick);
        rot.aimOverride = point; // null -> engine default aim point
    }

    /**
     * Attack gate. KillAura calls this INSTEAD of AnticheatManager.gateAttack
     * while VulcanMode is enabled (we delegate into it at the end).
     */
    public boolean gateAttack(MinecraftClient mc, Entity target) {
        // B. KB-compliance hold
        if (kbHoldTicks > 0) return false;

        RotationManager rot = KillAuraMod.ROTATIONS;
        if (rot != null) {
            // D. rotation-delta gate: never attack on the same tick as a big turn
            float dy = Math.abs(MathHelper.wrapDegrees(rot.getRotationYaw() - preRotateYaw));
            float dp = Math.abs(rot.getRotationPitch() - preRotatePitch);
            float delta = Math.max(dy, dp);
            double clamp = profile.rotClampDeg * (1.0 - (paranoia / 100.0) * 0.4);
            if (delta > clamp && Math.random() < 0.9) {
                return false; // finish the turn first, hit on a legal small delta
            }

            planner.trackPlanned(rot.getRotationYaw(), rot.getRotationPitch());

            // soft alignment gate: if aim is far from the picked point,
            // occasionally hold (human tracing behavior)
            if (KillAuraMod.CONFIG.vulcanPointPick && target != null
                    && !planner.isPlanAligned(mc, target, effectiveReachTarget())
                    && Math.random() < 0.25) {
                return false;
            }
        }

        // A. paranoia throttle
        if (paranoia > 0.0 && Math.random() < (paranoia / 100.0) * 0.5) {
            return false;
        }

        return KillAuraMod.ANTICHEAT.gateAttack(mc);
    }

    /**
     * CatLean "randomized reach": gaussian jitter around the profile reach
     * target, hard-capped at maxReachSoft (LEGIT/BALANCED) so the server
     * reach check never sees >3.0. RAGE releases the cap by user choice.
     */
    public double rollAttackReach(MinecraftClient mc) {
        double target = effectiveReachTarget();

        if (profile != Profile.RAGE && paranoia > 0.0) {
            target *= 1.0 - (paranoia / 100.0) * 0.25; // up to -25% under paranoia
        }

        double sigma = KillAuraMod.CONFIG.vulcanReachJitter;
        double jitter = (Math.random() + Math.random() + Math.random() - 1.5) / 1.5 * sigma;

        double cap = profile == Profile.RAGE
                ? Math.max(KillAuraMod.CONFIG.reach, KillAuraMod.CONFIG.maxReachSoft)
                : KillAuraMod.CONFIG.maxReachSoft;

        lastAttackReach = MathHelper.clamp(target + jitter, KillAuraMod.CONFIG.vulcanReachMin, cap);
        return lastAttackReach;
    }

    /** C. anti-metronome w-tap: reset sprint only on a share of hits. */
    public boolean rollSprintReset() {
        return Math.random() < profile.resetChance;
    }

    /** AI-informed CPS scaling for the active profile. */
    public double cpsMultiplier() {
        return profile.cpsMultiplier;
    }

    /** Called after a successful attack (KillAura). */
    public void onAttacked() {
        // fresh point per attack - the next curve starts from a new picked
        // position, exactly like a human re-acquiring the model between hits
        planner.reset();
        if (KillAuraMod.ROTATIONS != null) {
            KillAuraMod.ROTATIONS.aimOverride = null;
        }
    }

    /** Called by AuraAI when a server flag/setback/kick message is detected. */
    public void onFlagDetected() {
        paranoia = Math.min(100.0, paranoia + 35.0);
        planner.reset();
        if (KillAuraMod.CONFIG.debugLog) {
            KillAuraMod.LOGGER.info("[Brody] VulcanMode: paranoia -> {} (adaptive cloaking)", (int) paranoia);
        }
    }

    /**
     * Attribute-level reach (CatLean ReachStateEvent idea, via mixin).
     * Only ever RAISES the vanilla 3.0 (needed when RAGE wants >3.0); never
     * lowers it, so manual play stays unaffected.
     */
    public double effectiveClientRange() {
        return Math.max(3.0, KillAuraMod.CONFIG.reach);
    }

    public double getParanoia() {
        return paranoia;
    }

    public Profile currentProfile() {
        return profile;
    }

    public AttackPlanner getPlanner() {
        return planner;
    }

    // ----------------------------------------------------------------- misc

    private double effectiveReachTarget() {
        return profile.reachTarget > 0
                ? profile.reachTarget
                : KillAuraMod.CONFIG.reach;
    }

    private void reloadProfile() {
        try {
            profile = Profile.valueOf(KillAuraMod.CONFIG.vulcanProfile.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            profile = Profile.BALANCED;
        }
    }

    private void resetState() {
        paranoia = 0.0;
        kbHoldTicks = 0;
        prevHurtTime = 0;
        lastAttackReach = 0.0;
        planner.reset();
        if (KillAuraMod.ROTATIONS != null) {
            KillAuraMod.ROTATIONS.aimOverride = null;
        }
    }

    @Override
    public void onEnable() {
        paranoia = 0.0;
        kbHoldTicks = 0;
        prevHurtTime = 0;
        reloadProfile();
    }

    @Override
    public void onDisable() {
        resetState();
    }
}
