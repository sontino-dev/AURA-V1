package com.brody.aura.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Flat JSON config. Every module reads its knobs from here.
 * Keeps values human-editable so Jason can tweak without recompiling.
 */
public class AuraConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir()
            .resolve("brody-killaura.json");

    // ---- KillAura core ----
    public double reach = 3.0;
    public double wallRange = 3.0;
    public double minCps = 6.0;
    public double maxCps = 13.0;
    public double fov = 360.0;
    public boolean attackThroughWalls = false;
    public boolean requireSight = true;
    public boolean autoSprint = true;
    public boolean autoJumpCrit = true;
    public boolean targetPlayers = true;
    public boolean targetMobs = false;
    public boolean targetArmorStands = false;
    public String targetSort = "DISTANCE"; // DISTANCE, HEALTH, ANGLE, FOV

    // ---- Rotations ----
    public boolean rotateSilent = true;
    public boolean rotateSmooth = true;
    public double rotationSpeed = 12.0;
    public double rotationJitter = 0.8;
    public boolean gcdCompliant = true;
    public double maxRotationStepDeg = 28.0;

    // ---- AI ----
    public boolean aiEnabled = true;
    public double aiLearningRate = 0.05;
    public double aiAggression = 0.65;
    public boolean aiAdaptiveCps = true;
    public boolean aiPredictVelocity = true;
    public int aiPredictionTicks = 2;
    public double aiRewardHit = 1.0;
    public double aiPunishMiss = -0.8;
    public double aiPunishFlag = -2.5;

    // ---- HVH ----
    public boolean hvhAutoTotemSwap = true;
    public boolean hvhShieldBreaker = true;
    public boolean hvhAutoCrystal = false;
    public boolean hvhSmartFeint = true;
    public double hvhFeintChance = 0.18;
    public boolean hvhAntiFireball = true;
    public boolean hvhAntiExplosion = true;

    // ---- Anti-anticheat ----
    public boolean vulcanSafe = true;
    public boolean sprintReset = true;
    public boolean randomPostDelay = true;
    public int minPostDelayTicks = 1;
    public int maxPostDelayTicks = 4;
    public boolean humanizedAim = true;
    public double aimOvershootDeg = 1.5;
    public boolean fakeSneakOnHit = false;
    public double maxReachSoft = 2.95;

    // ---- VulcanMode (CatLean-inspired adaptive layer) ----
    public String vulcanProfile = "BALANCED";     // LEGIT, BALANCED, RAGE
    public boolean vulcanPointPick = true;        // CatLean getPoint: randomized validated aim points
    public double vulcanRepickTicks = 4.0;        // keep a picked point stable for N ticks
    public double vulcanReachMin = 2.35;          // randomized per-attack reach floor
    public double vulcanReachJitter = 0.22;       // gaussian sigma around profile reach target
    public double vulcanSprintResetChance = 0.7;  // share of hits that w-tap (anti-metronome)
    public int vulcanKbHoldMax = 3;               // max attack-hold ticks after receiving knockback
    public double vulcanParanoiaDecay = 1.2;      // paranoia points decayed per tick
    public boolean vulcanOvershootCorrect = true; // overshoot -> corrective step pairs
    public double vulcanRotationClampDeg = 22.0;  // (gate uses profile clamp; this is the fallback cap)

    // ---- UI (ClickGUI + HUD) ----
    public int guiBind = 344;      // open ClickGUI (default Right Shift, GLFW 344; -1 disables)
    public int guiX = 40;          // ClickGUI panel position
    public int guiY = 30;
    public boolean hudEnabled = true;
    public int hudX = 6;           // HUD overlay position
    public int hudY = 6;

    // ---- Misc ----
    public boolean debugLog = false;
    public int maxTargets = 3;
    public double blockBreakingAbortRange = 4.5;

    private final Map<String, Double> extra = new HashMap<>();

    public void load() {
        try {
            if (Files.exists(CONFIG_PATH)) {
                String json = Files.readString(CONFIG_PATH);
                AuraConfig loaded = GSON.fromJson(json, AuraConfig.class);
                if (loaded != null) {
                    copyFrom(loaded);
                }
            } else {
                save();
            }
        } catch (IOException e) {
            // keep defaults
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.writeString(CONFIG_PATH, GSON.toJson(this));
        } catch (IOException ignored) {
        }
    }

    private void copyFrom(AuraConfig other) {
        reach = other.reach;
        wallRange = other.wallRange;
        minCps = other.minCps;
        maxCps = other.maxCps;
        fov = other.fov;
        attackThroughWalls = other.attackThroughWalls;
        requireSight = other.requireSight;
        autoSprint = other.autoSprint;
        autoJumpCrit = other.autoJumpCrit;
        targetPlayers = other.targetPlayers;
        targetMobs = other.targetMobs;
        targetArmorStands = other.targetArmorStands;
        targetSort = other.targetSort;
        rotateSilent = other.rotateSilent;
        rotateSmooth = other.rotateSmooth;
        rotationSpeed = other.rotationSpeed;
        rotationJitter = other.rotationJitter;
        gcdCompliant = other.gcdCompliant;
        maxRotationStepDeg = other.maxRotationStepDeg;
        aiEnabled = other.aiEnabled;
        aiLearningRate = other.aiLearningRate;
        aiAggression = other.aiAggression;
        aiAdaptiveCps = other.aiAdaptiveCps;
        aiPredictVelocity = other.aiPredictVelocity;
        aiPredictionTicks = other.aiPredictionTicks;
        aiRewardHit = other.aiRewardHit;
        aiPunishMiss = other.aiPunishMiss;
        aiPunishFlag = other.aiPunishFlag;
        hvhAutoTotemSwap = other.hvhAutoTotemSwap;
        hvhShieldBreaker = other.hvhShieldBreaker;
        hvhAutoCrystal = other.hvhAutoCrystal;
        hvhSmartFeint = other.hvhSmartFeint;
        hvhFeintChance = other.hvhFeintChance;
        hvhAntiFireball = other.hvhAntiFireball;
        hvhAntiExplosion = other.hvhAntiExplosion;
        vulcanSafe = other.vulcanSafe;
        sprintReset = other.sprintReset;
        randomPostDelay = other.randomPostDelay;
        minPostDelayTicks = other.minPostDelayTicks;
        maxPostDelayTicks = other.maxPostDelayTicks;
        humanizedAim = other.humanizedAim;
        aimOvershootDeg = other.aimOvershootDeg;
        fakeSneakOnHit = other.fakeSneakOnHit;
        maxReachSoft = other.maxReachSoft;
        vulcanProfile = other.vulcanProfile;
        vulcanPointPick = other.vulcanPointPick;
        vulcanRepickTicks = other.vulcanRepickTicks;
        vulcanReachMin = other.vulcanReachMin;
        vulcanReachJitter = other.vulcanReachJitter;
        vulcanSprintResetChance = other.vulcanSprintResetChance;
        vulcanKbHoldMax = other.vulcanKbHoldMax;
        vulcanParanoiaDecay = other.vulcanParanoiaDecay;
        vulcanOvershootCorrect = other.vulcanOvershootCorrect;
        vulcanRotationClampDeg = other.vulcanRotationClampDeg;
        guiBind = other.guiBind;
        guiX = other.guiX;
        guiY = other.guiY;
        hudEnabled = other.hudEnabled;
        hudX = other.hudX;
        hudY = other.hudY;
        debugLog = other.debugLog;
        maxTargets = other.maxTargets;
        blockBreakingAbortRange = other.blockBreakingAbortRange;
    }

    public double getExtra(String key, double def) {
        return extra.getOrDefault(key, def);
    }

    public void setExtra(String key, double value) {
        extra.put(key, value);
    }
}
