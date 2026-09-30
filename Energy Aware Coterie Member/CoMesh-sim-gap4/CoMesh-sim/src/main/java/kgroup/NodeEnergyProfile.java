package kgroup;

/**
 * Research-Gap-Analysis Gap 4 ("Energy-Aware Coterie Membership").
 *
 * Holds the resource state of a single physical node that {@link KGroup}
 * consults, on top of the existing pure-hash (LSH-style) bucket selection,
 * before finalizing coterie membership. This is intentionally a small,
 * self-contained value object: the rest of the simulator (Network,
 * KGroupManager message handling, failure detection, locking, etc.) is
 * unaffected by its existence, and code paths that never supply an energy
 * profile map keep behaving exactly as before (see KGroup#newKGroupMembers).
 */
public class NodeEnergyProfile {
    // Remaining battery, expressed as a percentage in [0, 100].
    private double batteryPct;
    // Current CPU load, expressed as a percentage in [0, 100].
    private double cpuLoadPct;

    public NodeEnergyProfile(double batteryPct, double cpuLoadPct) {
        this.batteryPct = clamp(batteryPct);
        this.cpuLoadPct = clamp(cpuLoadPct);
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    public double getBatteryPct() {
        return batteryPct;
    }

    public double getCpuLoadPct() {
        return cpuLoadPct;
    }

    /**
     * Drains battery (e.g. after a period of serving as coterie leader or
     * member) and applies a CPU load delta. Both deltas are expected to be
     * non-negative; call sites decide how much a given role costs.
     */
    public void drain(double batteryDelta, double cpuLoadDelta) {
        batteryPct = clamp(batteryPct - batteryDelta);
        cpuLoadPct = clamp(cpuLoadPct + cpuLoadDelta);
    }

    /**
     * A node is "energy-stable" (Gap 4's term) when it has enough battery
     * headroom and isn't already CPU-saturated -- i.e., it is a candidate
     * that is unlikely to fail/drain out and force a leader re-election or
     * state-transfer shortly after being selected.
     */
    public boolean isEnergyStable(double batteryThresholdPct, double cpuLoadThresholdPct) {
        return batteryPct >= batteryThresholdPct && cpuLoadPct <= cpuLoadThresholdPct;
    }

    @Override
    public String toString() {
        return String.format("battery=%.1f%%, cpuLoad=%.1f%%", batteryPct, cpuLoadPct);
    }
}
