package kgroup.resource;

import java.io.FileWriter;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Gap 2: Energy / Resource-Aware Leader and Member Selection
 * ------------------------------------------------------------
 * This is a standalone experiment harness, in the same spirit as
 * {@code routine.pushdown.PredicatePushdownSimulator} (Gap 3): it does not
 * touch the main {@code KGroupManager}/{@code Simulator} event loop, but
 * reuses the exact selection primitive the real simulator uses today
 * ({@code KGroup.hash(nodeID, epochNo, N)} -> {@code Hash(epoch, deviceID,
 * targetID)}, and "lowest hash wins" leader election, see
 * {@code KGroup.chooseLeader(SMALLEST_HASH)}), and adds a resource/battery/
 * CPU hazard model on top of it so the two competing selection policies
 * from the gap analysis can be measured side-by-side over the same
 * randomness.
 *
 * Current State (baseline): {@link Policy#RANDOM_HASH} - coterie membership
 * and leadership are decided purely by {@code Hash(epoch, deviceID,
 * targetID)}; every device is equally likely to become a member/leader
 * regardless of its battery or CPU state.
 *
 * Proposed Direction: {@link Policy#RESOURCE_AWARE} - candidate devices are
 * first filtered to those that are "eligible" (battery {@literal >}
 * batteryThreshold AND cpuLoad {@literal <} cpuThreshold); the hash ranking
 * (member selection + "lowest hash = leader") is then applied only to that
 * eligible pool. If fewer than K devices are eligible, the policy falls
 * back to {@code RANDOM_HASH} for that coterie/epoch (an eligible pool that
 * is too small to safely reason about is treated the same as "no
 * resource information", rather than forcing unhealthy devices in).
 *
 * Mechanisms implemented, mapped to the CoMesh paper / spec:
 *  - hashRanks() / selectCoterie()      : {@link #hashRanks}, {@link #selectCoterie}
 *  - RANDOM_HASH baseline               : {@link Policy#RANDOM_HASH}
 *  - Epoch-based migration (Algorithm 1): epoch-boundary re-selection in
 *                                          {@link #runOnce}, costed via
 *                                          {@code migrationCostPerChangedMember}
 *  - Failure-triggered re-election      : leader-failure handling in
 *                                          {@link #runOnce}, costed via
 *                                          {@code reconstructionCostMult * K}
 *  - RESOURCE_AWARE policy              : {@link #selectCoterie}
 *
 * Resource/hazard model (per spec):
 *  - Battery: passive recharge approximately offsets idle drain; every
 *    epoch a device spends leading adds a net energy deficit.
 *  - CPU load: noisy random walk, plus an upward bump while leading.
 *  - Failure hazard: increases quadratically with battery depletion and
 *    with (effective) CPU load.
 */
public class ResourceAwareLeaderSimulator {

    public enum Policy { RANDOM_HASH, RESOURCE_AWARE }

    /** Tunable experiment parameters (defaults per the Gap 2 spec). */
    public static class Params {
        public int S = 100;              // system size (# devices)
        public int G = 30;               // generations/epochs per run
        public int F = 2;                // tolerated failures
        public int K = 2 * F + 1;        // coterie size, k = 2f + 1
        public double batteryThreshold = 30.0; // % - RESOURCE_AWARE eligibility
        public double cpuThreshold = 70.0;     // % - RESOURCE_AWARE eligibility
        public double healthyRefThreshold = 50.0; // fixed reference used only
        // to *measure* "healthy-device leadership share"; independent of the
        // policy's own eligibility threshold above, so the metric is
        // comparable across the threshold sweep.

        // Resource dynamics
        public double cpuBaseline = 25.0;        // idle CPU load the random walk reverts toward
        public double cpuReversion = 0.15;       // mean-reversion rate per epoch (noisy walk, not unbounded)
        public double cpuNoiseSigma = 4.0;       // CPU random-walk step stddev
        public double leaderCpuBump = 16.0;      // extra CPU load while leading
        public double leaderBatteryDrain = 14.0; // net battery deficit per epoch led (no recharge that epoch)
        public double idleBatteryRecharge = 2.5; // small passive recharge on epochs NOT spent leading
        public double hazardBase = 0.0015;
        public double hazardBatteryCoeff = 0.05; // scales (depletion)^2 term
        public double hazardCpuCoeff = 0.05;     // scales (cpuLoad/100)^2 term
        public int recoveryEpochs = 2;           // epochs a failed device needs to reboot

        // Costs (in abstract "units", same units used consistently for both policies)
        public double migrationCostPerChangedMember = 12.0;
        public double reconstructionCostMult = 40.0; // multiplied by K per re-election
    }

    /** Per-device mutable resource state for one simulation run. */
    private static class DeviceState {
        final String id;
        double battery;
        double cpuLoad;
        boolean failed;
        int recoveryLeft;
        long leaderEpochs;
        long healthyLeaderEpochs;

        DeviceState(String id) {
            this.id = id;
        }
    }

    /** Aggregate metrics produced by one (policy, seed) run. */
    public static class RunResult {
        public long reElections;
        public double overheadUnits;
        public double gini;
        public double healthyLeaderShare;
    }

    /** Mean +/- population stddev across several {@link RunResult}s. */
    public static class Stat {
        public double mean, stdDev;

        static Stat of(double[] xs) {
            Stat s = new Stat();
            double sum = 0;
            for (double x : xs) sum += x;
            s.mean = sum / xs.length;
            double sq = 0;
            for (double x : xs) sq += (x - s.mean) * (x - s.mean);
            s.stdDev = Math.sqrt(sq / xs.length);
            return s;
        }
    }

    // ---- Hash(epoch, deviceID, targetID) -----------------------------------
    // Same construction style as kgroup.KGroup.hash(nodeID, epochNo, N):
    // a SHA-1 digest of the tuple, interpreted as a big integer / hex string
    // for ranking. Here "targetID" is the coterie index this device is being
    // ranked for (mirrors CoMesh's per-entity KGroup: each device/routine
    // "target" gets its own independent coterie/leader selection).
    private static final ThreadLocal<MessageDigest> SHA1 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    });

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String hash(int epoch, String deviceID, int targetID) {
        String value = deviceID + ":" + targetID + ":" + epoch;
        MessageDigest digest = SHA1.get();
        digest.reset();
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        byte[] out = digest.digest();
        char[] hex = new char[out.length * 2];
        for (int i = 0; i < out.length; i++) {
            int v = out[i] & 0xFF;
            hex[i * 2] = HEX[v >>> 4];
            hex[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(hex);
    }

    /** Rank a candidate pool by Hash(epoch, deviceID, targetID), ascending (lowest first). */
    public static List<DeviceState> hashRanks(List<DeviceState> candidates, int epoch, int targetID) {
        List<DeviceState> ranked = new ArrayList<>(candidates);
        ranked.sort(Comparator.comparing(d -> hash(epoch, d.id, targetID)));
        return ranked;
    }

    /**
     * Selects a coterie of size K for the given target/epoch under the given
     * policy. For RESOURCE_AWARE, candidates are first filtered to those
     * that are battery/CPU-eligible; if that pool is smaller than K, it
     * silently falls back to the full (RANDOM_HASH) candidate pool. The
     * lowest-hashed member of the *pool actually used* becomes the leader
     * (see caller), which is what makes the leader "the lowest-hashed
     * eligible device" for RESOURCE_AWARE instead of "the lowest-hashed
     * device overall".
     */
    public static List<DeviceState> selectCoterie(Policy policy, List<DeviceState> alive, int epoch,
            int targetID, Params p) {
        List<DeviceState> pool = alive;
        if (policy == Policy.RESOURCE_AWARE) {
            List<DeviceState> eligible = new ArrayList<>();
            for (DeviceState d : alive) {
                if (d.battery > p.batteryThreshold && d.cpuLoad < p.cpuThreshold) {
                    eligible.add(d);
                }
            }
            if (eligible.size() >= p.K) {
                pool = eligible; // enough eligible candidates: use them exclusively
            } // else: fall back to RANDOM_HASH behavior over the full alive pool
        }
        List<DeviceState> ranked = hashRanks(pool, epoch, targetID);
        return ranked.subList(0, Math.min(p.K, ranked.size()));
    }

    private static double gini(long[] counts) {
        int n = counts.length;
        long[] sorted = counts.clone();
        Arrays.sort(sorted);
        double totalSum = 0;
        for (long c : sorted) totalSum += c;
        if (totalSum == 0) return 0.0;
        double weighted = 0;
        for (int i = 0; i < n; i++) {
            weighted += (double) (2 * (i + 1) - n - 1) * sorted[i];
        }
        return weighted / (n * totalSum);
    }

    /** Runs one full (policy, seed) simulation of G epochs and returns aggregate metrics. */
    public static RunResult runOnce(Policy policy, long seed, Params p) {
        Random rng = new Random(seed);
        List<DeviceState> devices = new ArrayList<>();
        for (int i = 0; i < p.S; i++) {
            DeviceState d = new DeviceState(String.format("dev-%03d", i));
            d.battery = 20 + rng.nextDouble() * 80;   // U(20,100)
            d.cpuLoad = 15 + rng.nextDouble() * 30;   // U(15,45)
            devices.add(d);
        }
        int T = p.S / p.K; // number of independent coteries/targets this run partitions devices' attention across

        @SuppressWarnings("unchecked")
        List<String>[] curMembers = new List[T];
        String[] curLeader = new String[T];

        RunResult result = new RunResult();

        java.util.Map<String, Integer> idxOf = new java.util.HashMap<>();
        for (int i = 0; i < p.S; i++) idxOf.put(devices.get(i).id, i);

        for (int epoch = 1; epoch <= p.G; epoch++) {
            List<DeviceState> alive = new ArrayList<>();
            for (DeviceState d : devices) if (!d.failed) alive.add(d);

            // ---- epoch-boundary migration (Algorithm 1): re-select membership/leader
            // using each device's CURRENT (start-of-epoch) battery/CPU state. This is
            // what "who did the policy pick" actually measures. ----
            int[] leadCount = new int[p.S];

            for (int t = 0; t < T; t++) {
                List<DeviceState> members = selectCoterie(policy, alive, epoch, t, p);
                List<String> newIds = new ArrayList<>();
                for (DeviceState d : members) newIds.add(d.id);

                if (curMembers[t] != null) {
                    Set<String> oldSet = new HashSet<>(curMembers[t]);
                    Set<String> newSet = new HashSet<>(newIds);
                    int changed = 0;
                    for (String id : newSet) if (!oldSet.contains(id)) changed++;
                    for (String id : oldSet) if (!newSet.contains(id)) changed++;
                    result.overheadUnits += changed * p.migrationCostPerChangedMember;
                }
                curMembers[t] = newIds;
                curLeader[t] = newIds.isEmpty() ? null : newIds.get(0); // lowest-hashed (eligible, if filtered) member

                if (curLeader[t] != null) {
                    int li = idxOf.get(curLeader[t]);
                    leadCount[li]++;
                    DeviceState leader = devices.get(li);
                    // Tally leadership "quality" at the moment of selection (before this
                    // epoch's own load/drain is applied to it) - this is what directly
                    // reflects whether the *policy* picked a healthy device.
                    leader.leaderEpochs++;
                    if (leader.battery > p.healthyRefThreshold) leader.healthyLeaderEpochs++;
                }
            }

            // ---- resource dynamics (battery/CPU evolve for this epoch's load) ----
            for (int i = 0; i < p.S; i++) {
                DeviceState d = devices.get(i);
                if (d.failed) {
                    d.recoveryLeft--;
                    if (d.recoveryLeft <= 0) {
                        d.failed = false;
                        d.battery = 70 + rng.nextDouble() * 30; // rebooted/replaced device
                        d.cpuLoad = p.cpuBaseline + rng.nextDouble() * 10;
                    }
                    continue;
                }
                int lc = leadCount[i];
                // CPU: noisy, mean-reverting walk (bounded fluctuation around baseline),
                // plus a persistent upward bump while leading.
                d.cpuLoad += p.cpuReversion * (p.cpuBaseline - d.cpuLoad)
                        + rng.nextGaussian() * p.cpuNoiseSigma
                        + p.leaderCpuBump * lc;
                d.cpuLoad = Math.max(0, Math.min(100, d.cpuLoad));
                // Battery: passive recharge approx. offsets idle drain; leading adds a
                // net energy deficit (no offsetting recharge on an epoch it leads).
                if (lc > 0) {
                    d.battery -= p.leaderBatteryDrain * lc;
                } else {
                    d.battery += p.idleBatteryRecharge;
                }
                d.battery = Math.max(0, Math.min(100, d.battery));

                // Failure hazard: quadratic in battery depletion and in CPU load.
                double depletion = (100 - d.battery) / 100.0;
                double cpuFrac = d.cpuLoad / 100.0;
                double hazard = p.hazardBase
                        + p.hazardBatteryCoeff * depletion * depletion
                        + p.hazardCpuCoeff * cpuFrac * cpuFrac;

                if (lc > 0 && rng.nextDouble() < hazard) {
                    // Leader-failure -> triggers re-election. The device is marked failed;
                    // the *next* epoch-boundary migration naturally excludes it and picks
                    // a fresh leader for every coterie it was leading, so no separate
                    // mid-epoch replacement bookkeeping is needed here.
                    d.failed = true;
                    d.recoveryLeft = p.recoveryEpochs;
                    result.reElections += lc; // one re-election per coterie it was leading
                    result.overheadUnits += p.reconstructionCostMult * p.K * lc;
                }
            }
        }

        long[] leaderCounts = new long[p.S];
        long totalLeaderEpochs = 0, totalHealthyLeaderEpochs = 0;
        for (int i = 0; i < p.S; i++) {
            leaderCounts[i] = devices.get(i).leaderEpochs;
            totalLeaderEpochs += devices.get(i).leaderEpochs;
            totalHealthyLeaderEpochs += devices.get(i).healthyLeaderEpochs;
        }
        result.gini = gini(leaderCounts);
        result.healthyLeaderShare = totalLeaderEpochs == 0 ? 0.0
                : (double) totalHealthyLeaderEpochs / totalLeaderEpochs;
        return result;
    }

    /** Runs N seeded repetitions of a policy and returns per-metric mean/stddev. */
    public static java.util.Map<String, Stat> runMany(Policy policy, int numSeeds, long seedOffset, Params p) {
        double[] reElections = new double[numSeeds];
        double[] overhead = new double[numSeeds];
        double[] giniArr = new double[numSeeds];
        double[] healthyShare = new double[numSeeds];
        for (int s = 0; s < numSeeds; s++) {
            RunResult r = runOnce(policy, seedOffset + s, p);
            reElections[s] = r.reElections;
            overhead[s] = r.overheadUnits;
            giniArr[s] = r.gini;
            healthyShare[s] = r.healthyLeaderShare;
        }
        java.util.Map<String, Stat> out = new java.util.LinkedHashMap<>();
        out.put("reElections", Stat.of(reElections));
        out.put("overhead", Stat.of(overhead));
        out.put("gini", Stat.of(giniArr));
        out.put("healthyShare", Stat.of(healthyShare));
        return out;
    }

    private static String fmt(double mean, double std) {
        return String.format(Locale.US, "%.2f +/- %.2f", mean, std);
    }

    private static double pctChange(double baseline, double proposed, boolean lowerIsBetterUp) {
        // returns "improvement %" — for metrics where lower is better (re-elections,
        // overhead, gini) this is (baseline - proposed) / baseline * 100 (positive = improved).
        // for metrics where higher is better (healthy share) invert the sign convention
        // at the call site instead of here.
        if (baseline == 0) return 0.0;
        return (baseline - proposed) / baseline * 100.0;
    }

    public static void main(String[] args) throws IOException {
        Params headline = new Params();
        headline.batteryThreshold = 30;
        headline.cpuThreshold = 70;

        System.out.println("=== Gap 2: Resource-Aware Leader/Member Selection ===");
        System.out.printf(Locale.US, "S=%d, G=%d, K=2F+1=%d, batteryThreshold=%.0f%%, cpuThreshold=%.0f%%%n%n",
                headline.S, headline.G, headline.K, headline.batteryThreshold, headline.cpuThreshold);

        int headlineSeeds = 30;
        var randHead = runMany(Policy.RANDOM_HASH, headlineSeeds, 1000, headline);
        var resHead = runMany(Policy.RESOURCE_AWARE, headlineSeeds, 1000, headline);

        System.out.println("Headline result table (threshold = 30% battery / 70% CPU):");
        System.out.println("Metric                          RANDOM_HASH          RESOURCE_AWARE");
        System.out.printf(Locale.US, "%-30s  %-20s %-20s%n", "Re-elections (count/run)",
                fmt(randHead.get("reElections").mean, randHead.get("reElections").stdDev),
                fmt(resHead.get("reElections").mean, resHead.get("reElections").stdDev));
        System.out.printf(Locale.US, "%-30s  %-20s %-20s%n", "State-transfer overhead (units)",
                fmt(randHead.get("overhead").mean, randHead.get("overhead").stdDev),
                fmt(resHead.get("overhead").mean, resHead.get("overhead").stdDev));
        System.out.printf(Locale.US, "%-30s  %-20s %-20s%n", "Leadership Gini",
                fmt(randHead.get("gini").mean, randHead.get("gini").stdDev),
                fmt(resHead.get("gini").mean, resHead.get("gini").stdDev));
        System.out.printf(Locale.US, "%-30s  %-20s %-20s%n", "Healthy-device leadership share",
                fmt(randHead.get("healthyShare").mean, randHead.get("healthyShare").stdDev),
                fmt(resHead.get("healthyShare").mean, resHead.get("healthyShare").stdDev));

        System.out.println();
        System.out.println("Threshold sweep table (batteryThreshold 10->70, cpuThreshold fixed at 70%):");
        System.out.printf(Locale.US, "%-10s %-16s %-14s %-16s %-10s%n",
                "Threshold", "Re-election dn%", "Overhead dn%", "Healthy-share up%", "Gini d%");

        int sweepSeeds = 20;
        try (FileWriter csv = new FileWriter("gap2_sweep_results.csv")) {
            csv.write("batteryThreshold,reElections_reduction_pct,overhead_reduction_pct,healthyShare_increase_pct,gini_change_pct\n");
            for (int thr : new int[]{10, 20, 30, 40, 50, 60, 70}) {
                Params sweep = new Params();
                sweep.batteryThreshold = thr;
                sweep.cpuThreshold = 70;
                var rnd = runMany(Policy.RANDOM_HASH, sweepSeeds, 5000, sweep);
                var res = runMany(Policy.RESOURCE_AWARE, sweepSeeds, 5000, sweep);

                double reElecDn = pctChange(rnd.get("reElections").mean, res.get("reElections").mean, true);
                double overheadDn = pctChange(rnd.get("overhead").mean, res.get("overhead").mean, true);
                double healthyUp = rnd.get("healthyShare").mean == 0 ? 0
                        : (res.get("healthyShare").mean - rnd.get("healthyShare").mean) / rnd.get("healthyShare").mean * 100.0;
                double giniChange = rnd.get("gini").mean == 0 ? 0
                        : (res.get("gini").mean - rnd.get("gini").mean) / rnd.get("gini").mean * 100.0;

                System.out.printf(Locale.US, "%-10d %-16.1f %-14.1f %-16.1f %-10.1f%n",
                        thr, reElecDn, overheadDn, healthyUp, giniChange);
                csv.write(String.format(Locale.US, "%d,%.2f,%.2f,%.2f,%.2f%n",
                        thr, reElecDn, overheadDn, healthyUp, giniChange));
            }
        }
        System.out.println("\nWrote per-threshold data to gap2_sweep_results.csv");
    }
}
