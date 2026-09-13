package com.brody.aura.ai;


/**
 * Learns which CPS + rotation speed buckets land hits on the current server.
 * Online, no dataset needed. Simple bandit over success ratios.
 */
public class HitTrainer {

    private static final int BUCKETS = 8;

    private final int[] cpsSuccess = new int[BUCKETS];
    private final int[] cpsAttempt = new int[BUCKETS];
    private final int[] rotSuccess = new int[BUCKETS];
    private final int[] rotAttempt = new int[BUCKETS];

    private double lastCps;
    private double lastRot;

    public void snapshot(double cps, double rotSpeed) {
        lastCps = cps;
        lastRot = rotSpeed;
    }

    public void recordHit() {
        cpsSuccess[idx(lastCps, 20)]++;
        rotSuccess[idx(lastRot, 40)]++;
    }

    public void recordMiss() {
        cpsAttempt[idx(lastCps, 20)]++;
        rotAttempt[idx(lastRot, 40)]++;
    }

    public void recordFlag() {
        // flag: punish current bucket hard
        cpsAttempt[idx(lastCps, 20)] += 3;
        rotAttempt[idx(lastRot, 40)] += 3;
    }

    public double suggestCps() {
        return suggest(cpsSuccess, cpsAttempt, 20.0, 6.0);
    }

    public double suggestRotSpeed() {
        return suggest(rotSuccess, rotAttempt, 40.0, 8.0);
    }

    private double suggest(int[] success, int[] attempt, double scale, double fallback) {
        double bestScore = -1;
        int bestIdx = -1;
        for (int i = 0; i < BUCKETS; i++) {
            int total = success[i] + attempt[i];
            if (total < 3) continue;
            double rate = success[i] / (double) total;
            // UCB1-ish: bonus for exploring
            double ucb = rate + Math.sqrt(2.0 * Math.log(total) / Math.max(1, attempt[i] + 1));
            if (ucb > bestScore) {
                bestScore = ucb;
                bestIdx = i;
            }
        }
        if (bestIdx < 0) return fallback;
        return (bestIdx + 0.5) * (scale / BUCKETS);
    }

    private static int idx(double value, double scale) {
        int i = (int) (value / (scale / BUCKETS));
        return Math.max(0, Math.min(BUCKETS - 1, i));
    }

    public String stats() {
        StringBuilder sb = new StringBuilder("cps:[");
        for (int i = 0; i < BUCKETS; i++) sb.append(String.format("%.2f/%d ", cpsAttempt[i] > 0 ? cpsSuccess[i] / (double)(cpsSuccess[i]+cpsAttempt[i]) : 0.0, cpsSuccess[i] + cpsAttempt[i]));
        sb.append("] rot:[");
        for (int i = 0; i < BUCKETS; i++) sb.append(String.format("%.2f/%d ", rotAttempt[i] > 0 ? rotSuccess[i] / (double)(rotSuccess[i]+rotAttempt[i]) : 0.0, rotSuccess[i] + rotAttempt[i]));
        sb.append("]");
        return sb.toString();
    }

    // persistence accessors
    public int[] cpsSuccessAccess() { return cpsSuccess; }
    public int[] cpsAttemptAccess() { return cpsAttempt; }
    public int[] rotSuccessAccess() { return rotSuccess; }
    public int[] rotAttemptAccess() { return rotAttempt; }
}
