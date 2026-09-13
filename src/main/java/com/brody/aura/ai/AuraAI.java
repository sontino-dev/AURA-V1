package com.brody.aura.ai;

import com.brody.aura.KillAuraMod;
import com.brody.aura.module.Module;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The AI brain. Four cooperating pieces:
 *
 * 1. TargetPredictor - velocity + learned-acceleration extrapolation of the
 *                      target position for silent-lead aiming.
 * 2. HitTrainer      - online UCB1 bandit over CPS and rotation-speed buckets,
 *                      biased towards what actually lands hits on THIS server.
 * 3. FlagDetector    - parses server chat for anticheat flag strings, punishes
 *                      the learner and backs off timings automatically.
 * 4. Brain persistence - learned buckets saved to json, reload on startup.
 */
public class AuraAI {

    public final TargetPredictor predictor = new TargetPredictor();
    public final HitTrainer trainer = new HitTrainer();

    // ---- flag detection ----
    private static final String[] FLAG_PATTERNS = {
            "vulcan", "grimac", "matrix", "intave", "spartan", "karhu", "polar",
            "verus", "vulcan", "purity", "acropolis", "thabsurd"
    };
    private static final String[] FLAG_ACTIONS = {
            "vl", "flag", "kick", "violation", "check", "failed", "setback", "punish"
    };

    private int recentFlags = 0;
    private long lastFlagTime = 0;

    public void tick() {
        // decay flags over time
        if (System.currentTimeMillis() - lastFlagTime > 10000 && recentFlags > 0) {
            recentFlags--;
            lastFlagTime = System.currentTimeMillis();
        }
    }

    public void feedTarget(Entity target) {
        if (target == null) {
            predictor.reset();
        } else {
            predictor.feed(target);
        }
    }

    public void onHitSuccess() {
        trainer.recordHit();
    }

    public void onHitMiss() {
        trainer.recordMiss();
    }

    public void onFlagDetected() {
        recentFlags++;
        lastFlagTime = System.currentTimeMillis();
        trainer.recordFlag();
        // feed the VulcanMode paranoia engine (adaptive cloaking)
        Module vulcan = KillAuraMod.MODULES.get("VulcanMode");
        if (vulcan instanceof com.brody.aura.module.VulcanMode vm) {
            vm.onFlagDetected();
        }
        if (KillAuraMod.CONFIG.debugLog) {
            KillAuraMod.LOGGER.info("[Brody] AI: flag detected, backing off");
        }
    }

    public boolean isFlaggedRecently() {
        return recentFlags > 0 && (System.currentTimeMillis() - lastFlagTime) < 4000;
    }

    public int getRecentFlags() {
        return recentFlags;
    }

    public void onServerChat(String message) {
        if (message == null) return;
        String lower = message.toLowerCase();
        for (String action : FLAG_ACTIONS) {
            if (!lower.contains(action)) continue;
            for (String pattern : FLAG_PATTERNS) {
                if (lower.contains(pattern)) {
                    onFlagDetected();
                    return;
                }
            }
        }
    }

    /**
     * Effective CPS for this attack decision. AI-suggested if enabled, else config range.
     */
    public double effectiveCps() {
        if (KillAuraMod.CONFIG.aiEnabled && KillAuraMod.CONFIG.aiAdaptiveCps) {
            double suggested = trainer.suggestCps();
            double min = KillAuraMod.CONFIG.minCps;
            double max = KillAuraMod.CONFIG.maxCps;
            double clamped = Math.max(min, Math.min(max, suggested));
            // if flagged recently be extra careful
            if (isFlaggedRecently()) clamped = Math.max(3.0, clamped * 0.6);
            return clamped;
        }
        // random within configured band
        double min = KillAuraMod.CONFIG.minCps;
        double max = KillAuraMod.CONFIG.maxCps;
        return min + Math.random() * (max - min);
    }

    /**
     * Effective rotation speed for this tick. AI-suggested if enabled.
     */
    public double effectiveRotSpeed() {
        if (KillAuraMod.CONFIG.aiEnabled) {
            double suggested = trainer.suggestRotSpeed();
            if (isFlaggedRecently()) suggested *= 0.7;
            return Math.max(4.0, Math.min(45.0, suggested));
        }
        return KillAuraMod.CONFIG.rotationSpeed;
    }

    public static Vec3d getPredictedAimPoint(Entity target, Vec3d fallback, int ticks) {
        return fallback;
    }

    // ---- persistence ----

    public void loadBrain() {
        try {
            Path p = brainPath();
            if (Files.exists(p)) {
                String json = Files.readString(p);
                String[] parts = json.trim().split("\\s+");
                if (parts.length >= 4) {
                    int[] cpsS = parseIntArray(parts[0]);
                    int[] cpsA = parseIntArray(parts[1]);
                    int[] rotS = parseIntArray(parts[2]);
                    int[] rotA = parseIntArray(parts[3]);
                    copyInto(cpsS, trainer.cpsSuccessAccess());
                    copyInto(cpsA, trainer.cpsAttemptAccess());
                    copyInto(rotS, trainer.rotSuccessAccess());
                    copyInto(rotA, trainer.rotAttemptAccess());
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void saveBrain() {
        try {
            Path p = brainPath();
            StringBuilder sb = new StringBuilder();
            sb.append(intArrayToString(trainer.cpsSuccessAccess())).append('\n');
            sb.append(intArrayToString(trainer.cpsAttemptAccess())).append('\n');
            sb.append(intArrayToString(trainer.rotSuccessAccess())).append('\n');
            sb.append(intArrayToString(trainer.rotAttemptAccess()));
            Files.createDirectories(p.getParent());
            Files.writeString(p, sb.toString());
        } catch (Exception ignored) {
        }
    }

    private static Path brainPath() {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("brody-aura-brain.json");
    }

    private static int[] parseIntArray(String s) {
        String[] tokens = s.split(",");
        int[] arr = new int[Math.min(8, tokens.length)];
        for (int i = 0; i < arr.length; i++) {
            try {
                arr[i] = Integer.parseInt(tokens[i].trim());
            } catch (NumberFormatException e) {
                arr[i] = 0;
            }
        }
        return arr;
    }

    private static String intArrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            sb.append(arr[i]);
            if (i < arr.length - 1) sb.append(',');
        }
        return sb.toString();
    }

    private static void copyInto(int[] src, int[] dst) {
        for (int i = 0; i < Math.min(src.length, dst.length); i++) dst[i] = src[i];
    }
}
