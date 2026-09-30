package kgroup.priority;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gap 5: Priority-Aware Lock Scheduling -- verification counters.
 *
 * Every device k-group leader (one per device, recomputed independently
 * by whichever node currently holds that role) makes its own local
 * "who's next" decision every time a lock is granted or released. Each
 * of those decisions is one head computation, counted here exactly like
 * Gap4Metrics counts every node-local coterie-formation check.
 */
public final class Gap5Metrics {

    private static final Gap5Metrics INSTANCE = new Gap5Metrics();

    private final AtomicInteger headComputations = new AtomicInteger(0);
    private final AtomicInteger priorityOvertakes = new AtomicInteger(0);
    private final AtomicInteger agingPromotions = new AtomicInteger(0);

    private Gap5Metrics() {
    }

    public static Gap5Metrics getInstance() {
        return INSTANCE;
    }

    /**
     * @param overtookFifoOrder true iff the request picked as head was NOT
     *                          the oldest (lowest reqSeqNo) pending request,
     *                          i.e. a higher (effective) priority request
     *                          skipped ahead of at least one older one.
     * @param viaAging          true iff the winning request's effective
     *                          priority included a non-zero aging bonus,
     *                          i.e. it won (at least partly) because it had
     *                          been waiting/skipped, not on declared
     *                          priority alone.
     */
    public void recordHeadComputation(boolean overtookFifoOrder, boolean viaAging) {
        headComputations.incrementAndGet();
        if (overtookFifoOrder) {
            priorityOvertakes.incrementAndGet();
        }
        if (viaAging) {
            agingPromotions.incrementAndGet();
        }
    }

    public int getHeadComputations() {
        return headComputations.get();
    }

    public int getPriorityOvertakes() {
        return priorityOvertakes.get();
    }

    public int getAgingPromotions() {
        return agingPromotions.get();
    }

    public void printSummary() {
        System.out.println("\n---- Gap 5: Priority-Aware Lock Scheduling summary ----");
        System.out.println("Lock-queue head computations: " + headComputations.get());
        System.out.println("Computations where a higher-(effective-)priority request skipped ahead");
        System.out.println(" of at least one older pending request: " + priorityOvertakes.get());
        System.out.println("Computations where the winner needed its anti-starvation aging bonus");
        System.out.println(" to win (waited long enough to be promoted): " + agingPromotions.get());
    }

    public static synchronized void reset() {
        INSTANCE.headComputations.set(0);
        INSTANCE.priorityOvertakes.set(0);
        INSTANCE.agingPromotions.set(0);
    }
}
