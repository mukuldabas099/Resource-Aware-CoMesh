package kgroup.resource;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gap 2: Resource-Aware Leader (and Member) Selection -- verification
 * counters for the *live* Simulator/KGroupManager integration.
 *
 * Mirrors kgroup.energy.Gap4Metrics: every node in the simulator
 * independently recomputes chooseLeader(...) for every k-group it knows
 * about, so every one of those node-local recomputations is counted here.
 */
public final class Gap2Metrics {

    private static final Gap2Metrics INSTANCE = new Gap2Metrics();

    private final AtomicInteger electionsChecked = new AtomicInteger(0);
    private final AtomicInteger electionsOverridden = new AtomicInteger(0);
    private final AtomicInteger electionsInfeasible = new AtomicInteger(0);

    private Gap2Metrics() {
    }

    public static Gap2Metrics getInstance() {
        return INSTANCE;
    }

    /**
     * Records the outcome of one node-local SMALLEST_HASH leader-election
     * check under the resource-aware constraint.
     *
     * @param overridden true if the lowest-hashed candidate was NOT
     *                   resource-stable and a lower-priority (higher
     *                   hash) but resource-stable candidate was elected
     *                   in its place.
     * @param infeasible true if no candidate in the k-group was
     *                   resource-stable, so the constraint fell back to
     *                   the original lowest-hash-wins behavior.
     */
    public void recordElection(boolean overridden, boolean infeasible) {
        electionsChecked.incrementAndGet();
        if (overridden) {
            electionsOverridden.incrementAndGet();
        }
        if (infeasible) {
            electionsInfeasible.incrementAndGet();
        }
    }

    public int getElectionsChecked() {
        return electionsChecked.get();
    }

    public int getElectionsOverridden() {
        return electionsOverridden.get();
    }

    public int getElectionsInfeasible() {
        return electionsInfeasible.get();
    }

    /** Prints the Gap 2 verification summary, matching the format used for Gap 4/5/7. */
    public void printSummary() {
        System.out.println("\n---- Gap 2: Resource-Aware Leader Selection summary ----");
        System.out.println("Leader elections checked: " + electionsChecked.get());
        System.out.println("Elections where the lowest-hashed candidate was NOT resource-stable");
        System.out.println(" and a resource-stable candidate was elected instead: " + electionsOverridden.get());
        System.out.println(
                "Elections where every candidate was resource-unstable (constraint infeasible, fell back to lowest hash): "
                        + electionsInfeasible.get());
    }

    public static synchronized void reset() {
        INSTANCE.electionsChecked.set(0);
        INSTANCE.electionsOverridden.set(0);
        INSTANCE.electionsInfeasible.set(0);
    }
}
