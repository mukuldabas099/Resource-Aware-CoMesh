package routine.pushdown;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gap 3: Smart Predicate Push-Down -- verification counters for the
 * *live* KGroupManager.setReading(...) integration (see
 * DEVICE_READING_UPDATE / DEVICE_PREDICATE_UPDATE handling in
 * KGroupManager). Mirrors kgroup.energy.Gap4Metrics / kgroup.priority.Gap5Metrics.
 */
public final class Gap3Metrics {

    private static final Gap3Metrics INSTANCE = new Gap3Metrics();

    // Baseline ("Current State"): every raw reading change on a device
    // leader is forwarded in full to every interested routine leader.
    private final AtomicInteger baselineMessagesSent = new AtomicInteger(0);

    // Push-down ("Proposed Direction"): the device leader evaluates the
    // leaf condition locally; a message is only sent when the resulting
    // bit differs from the last one sent for that (device, routine) pair.
    private final AtomicInteger pushdownMessagesSent = new AtomicInteger(0);
    private final AtomicInteger pushdownMessagesSuppressed = new AtomicInteger(0);
    private final AtomicLong pushdownBytesSaved = new AtomicLong(0);

    private Gap3Metrics() {
    }

    public static Gap3Metrics getInstance() {
        return INSTANCE;
    }

    public void recordBaselineSent() {
        baselineMessagesSent.incrementAndGet();
    }

    /** @param suppressed true if the bit hadn't changed and the forward was skipped. */
    public void recordPushdownCheck(boolean suppressed) {
        if (suppressed) {
            pushdownMessagesSuppressed.incrementAndGet();
        } else {
            pushdownMessagesSent.incrementAndGet();
        }
    }

    public void addPushdownBytesSaved(long bytes) {
        pushdownBytesSaved.addAndGet(bytes);
    }

    public int getBaselineMessagesSent() {
        return baselineMessagesSent.get();
    }

    public int getPushdownMessagesSent() {
        return pushdownMessagesSent.get();
    }

    public int getPushdownMessagesSuppressed() {
        return pushdownMessagesSuppressed.get();
    }

    /** Prints the Gap 3 verification summary, matching the format used for Gap 4/5/7. */
    public void printSummary() {
        System.out.println("\n---- Gap 3: Smart Predicate Push-Down summary ----");
        System.out.println("Baseline (raw-reading) forwards sent: " + baselineMessagesSent.get());
        System.out.println("Push-down (evaluated-bit) forwards sent: " + pushdownMessagesSent.get());
        System.out.println("Push-down forwards suppressed (bit unchanged, no message sent): "
                + pushdownMessagesSuppressed.get());
        int totalChecks = pushdownMessagesSent.get() + pushdownMessagesSuppressed.get();
        if (totalChecks > 0) {
            double suppressedPct = 100.0 * pushdownMessagesSuppressed.get() / totalChecks;
            System.out.printf("Suppressed rate: %.1f%%%n", suppressedPct);
        }
    }

    public static synchronized void reset() {
        INSTANCE.baselineMessagesSent.set(0);
        INSTANCE.pushdownMessagesSent.set(0);
        INSTANCE.pushdownMessagesSuppressed.set(0);
        INSTANCE.pushdownBytesSaved.set(0);
    }
}
