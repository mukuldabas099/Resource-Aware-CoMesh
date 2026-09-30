package kgroup.sleep;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gap 7: Intermittent Device Support -- verification counters.
 *
 * Every node in the simulator independently watches its own local copy
 * of the deterministic node-failure schedule (see KGroupManager's
 * nodeFailureDetected), so each of the counts below is incremented once
 * per node, per event -- exactly like Gap4Metrics/Gap5Metrics count
 * every node-local decision.
 */
public final class Gap7Metrics {

    private static final Gap7Metrics INSTANCE = new Gap7Metrics();

    private final AtomicInteger genuineFailures = new AtomicInteger(0);
    private final AtomicInteger suppressedFalseFailures = new AtomicInteger(0);
    private final AtomicInteger coterieRetentions = new AtomicInteger(0);
    private final AtomicInteger deferredLockRequests = new AtomicInteger(0);
    private final AtomicInteger flushedLockRequests = new AtomicInteger(0);

    private Gap7Metrics() {
    }

    public static Gap7Metrics getInstance() {
        return INSTANCE;
    }

    /** A node absence that was NOT covered by an announced sleep window (handled as before). */
    public void recordGenuineFailure() {
        genuineFailures.incrementAndGet();
    }

    /** A scheduled "failure" event that was recognized as an announced, planned sleep instead. */
    public void recordSuppressedFalseFailure() {
        suppressedFalseFailures.incrementAndGet();
    }

    /** A coterie (re)formation that kept a sleeping-but-incumbent device instead of evicting it. */
    public void recordCoterieRetention() {
        coterieRetentions.incrementAndGet();
    }

    /** A lock request whose delivery was deferred because the target device is asleep. */
    public void recordDeferredLockRequest() {
        deferredLockRequests.incrementAndGet();
    }

    /** A previously-deferred lock request that was (re)sent once the device woke up. */
    public void recordFlushedLockRequest() {
        flushedLockRequests.incrementAndGet();
    }

    public int getGenuineFailures() {
        return genuineFailures.get();
    }

    public int getSuppressedFalseFailures() {
        return suppressedFalseFailures.get();
    }

    public int getCoterieRetentions() {
        return coterieRetentions.get();
    }

    public int getDeferredLockRequests() {
        return deferredLockRequests.get();
    }

    public int getFlushedLockRequests() {
        return flushedLockRequests.get();
    }

    public void printSummary() {
        System.out.println("\n---- Gap 7: Intermittent Device Support summary ----");
        System.out.println("Genuine (unannounced) node-failure events processed as before: " + genuineFailures.get());
        System.out.println("Scheduled absences recognized as announced sleep (false-failure");
        System.out.println(" triggers suppressed): " + suppressedFalseFailures.get());
        System.out.println("Coterie (re)formations that retained a sleeping incumbent instead");
        System.out.println(" of evicting/replacing it: " + coterieRetentions.get());
        System.out.println("Lock requests deferred because their target device was asleep: "
                + deferredLockRequests.get());
        System.out.println("Deferred lock requests flushed once their device woke up: "
                + flushedLockRequests.get());
    }

    public static synchronized void reset() {
        INSTANCE.genuineFailures.set(0);
        INSTANCE.suppressedFalseFailures.set(0);
        INSTANCE.coterieRetentions.set(0);
        INSTANCE.deferredLockRequests.set(0);
        INSTANCE.flushedLockRequests.set(0);
    }
}
