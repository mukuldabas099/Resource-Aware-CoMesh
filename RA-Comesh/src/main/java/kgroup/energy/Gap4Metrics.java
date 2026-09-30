package kgroup.energy;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gap 4: Energy-Aware Coterie Membership -- verification counters.
 *
 * Every node in the simulator keeps its own local KGroupManager and
 * independently recomputes the deterministic hash-based membership for
 * every k-group it knows about (see KGroup.hash / newKGroupMembers). Each
 * of those node-local recomputations is a real, independent application
 * of the energy constraint from that node's point of view, so every one
 * of them is counted here: with N online nodes and a k-group that
 * (re)forms once per epoch, expect roughly N x (#epochs) formation
 * checks in the summary below.
 */
public final class Gap4Metrics {

    private static final Gap4Metrics INSTANCE = new Gap4Metrics();

    private final AtomicInteger formationsChecked = new AtomicInteger(0);
    private final AtomicInteger formationsSwapped = new AtomicInteger(0);
    private final AtomicInteger formationsInfeasible = new AtomicInteger(0);

    private Gap4Metrics() {
    }

    public static Gap4Metrics getInstance() {
        return INSTANCE;
    }

    /** Records the outcome of one node-local coterie (re)formation check. */
    public void recordFormation(boolean swapped, boolean infeasible) {
        formationsChecked.incrementAndGet();
        if (swapped) {
            formationsSwapped.incrementAndGet();
        }
        if (infeasible) {
            formationsInfeasible.incrementAndGet();
        }
    }

    public int getFormationsChecked() {
        return formationsChecked.get();
    }

    public int getFormationsSwapped() {
        return formationsSwapped.get();
    }

    public int getFormationsInfeasible() {
        return formationsInfeasible.get();
    }

    /** Prints the Gap 4 verification summary, matching the format used in verification runs. */
    public void printSummary() {
        System.out.println("\n---- Gap 4: Energy-Aware Coterie Membership summary ----");
        System.out.println("Coterie (re)formations checked: " + formationsChecked.get());
        System.out.println("Formations where locality-only hashing picked an all-low-energy bucket");
        System.out.println(" and the energy constraint swapped a member in: " + formationsSwapped.get());
        System.out.println(
                "Formations where every online candidate was low-energy (constraint infeasible): "
                        + formationsInfeasible.get());
    }

    public static synchronized void reset() {
        INSTANCE.formationsChecked.set(0);
        INSTANCE.formationsSwapped.set(0);
        INSTANCE.formationsInfeasible.set(0);
    }
}
