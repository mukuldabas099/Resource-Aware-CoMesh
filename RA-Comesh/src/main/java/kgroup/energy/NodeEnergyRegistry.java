package kgroup.energy;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gap 4: Energy-Aware Coterie Membership.
 *
 * Holds the (battery %, CPU load %) profile of every node, loaded from a
 * "node energy file" (one line per node: "nodeID batteryPct cpuLoadPct"),
 * and the global on/off switch for the energy-aware coterie-membership
 * constraint described in the Research Gap Analysis (Gap 4).
 *
 * This is intentionally a small static registry (mirrors the existing
 * RoutineMetricSingleton pattern already used elsewhere in this codebase)
 * rather than a field threaded through every KGroup/KGroupManager
 * constructor: every node in the simulator independently recomputes the
 * same deterministic hash-based membership for every k-group it knows
 * about, so the energy profile of a device is global, read-only
 * information every node needs equal access to -- exactly like the
 * existing static node/membership bookkeeping in KGroupManager.
 *
 * Default behavior (registry never loaded / not enabled) is a complete
 * no-op: isEnabled() returns false and KGroup.newKGroupMembers falls back
 * to the original locality-only hash ranking, unchanged.
 */
public final class NodeEnergyRegistry {

    // Energy-stability thresholds from the Gap 4 verification spec:
    // a device is "energy-stable" iff battery >= BATTERY_THRESHOLD (%)
    // AND cpuLoad <= CPU_THRESHOLD (%).
    public static final double BATTERY_THRESHOLD = 30.0;
    public static final double CPU_THRESHOLD = 70.0;

    private static volatile boolean enabled = false;

    // Gap 2: Resource-Aware Leader (and Member) Selection.
    // Independent on/off switch from the Gap 4 coterie-membership
    // constraint above (`enabled`) -- a run can enable either, both, or
    // neither. Both gaps read the exact same underlying battery/CPU
    // profiles loaded via load(...) / the -nef Simulator flag, since they
    // describe the same physical devices; only the *policy applied on
    // top* (member-swap-in vs. leader-eligibility) differs.
    private static volatile boolean leaderElectionEnabled = false;

    private static final Map<String, double[]> profiles = new HashMap<>();

    private NodeEnergyRegistry() {
    }

    /**
     * Loads a node energy file of the form "nodeID batteryPct cpuLoadPct"
     * (one node per line, whitespace-separated) into the registry.
     * Does NOT enable the constraint by itself -- see setEnabled(true) /
     * the -eam Simulator flag.
     */
    public static synchronized void load(String filename) throws IOException {
        profiles.clear();
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length < 3) {
                    continue;
                }
                String nodeID = parts[0];
                double battery = Double.parseDouble(parts[1]);
                double cpuLoad = Double.parseDouble(parts[2]);
                profiles.put(nodeID, new double[] { battery, cpuLoad });
            }
        }
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Gap 2: enable/query the resource-aware leader-election constraint. */
    public static void setLeaderElectionEnabled(boolean value) {
        leaderElectionEnabled = value;
    }

    public static boolean isLeaderElectionEnabled() {
        return leaderElectionEnabled;
    }

    public static boolean hasProfile(String nodeID) {
        return profiles.containsKey(nodeID);
    }

    public static double getBattery(String nodeID) {
        double[] p = profiles.get(nodeID);
        return p == null ? Double.NaN : p[0];
    }

    public static double getCpuLoad(String nodeID) {
        double[] p = profiles.get(nodeID);
        return p == null ? Double.NaN : p[1];
    }

    /**
     * A node with no profile on record is treated as energy-stable (fail
     * open): the registry only ever restricts membership for nodes it
     * actually has energy data for, so partially-specified energy files
     * or -eam runs without -nef never change behavior for the unknown
     * nodes.
     */
    public static boolean isEnergyStable(String nodeID) {
        double[] p = profiles.get(nodeID);
        if (p == null) {
            return true;
        }
        return p[0] >= BATTERY_THRESHOLD && p[1] <= CPU_THRESHOLD;
    }

    /** Test/rerun hook: clears loaded profiles and disables the constraint. */
    public static synchronized void reset() {
        profiles.clear();
        enabled = false;
        leaderElectionEnabled = false;
    }
}
