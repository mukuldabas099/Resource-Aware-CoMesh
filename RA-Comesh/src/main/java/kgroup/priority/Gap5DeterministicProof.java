package kgroup.priority;

import kgroup.state.DeviceLock;
import kgroup.state.LockRequest;

/**
 * Gap 5: Priority-Aware Lock Scheduling -- isolated, deterministic proof.
 *
 * Exercises kgroup.state.DeviceLock directly (the exact same class the
 * device k-group leader/replicas use to track a single device's lock
 * queue) through three scenarios, with no Simulator/network involved:
 *
 * 1. FIFO regression: with the scheduler disabled, three same-priority
 *    requests are granted in exactly arrival order -- the original
 *    behavior, unchanged.
 *
 * 2. Priority skip-ahead: with the scheduler enabled, a HIGH-priority
 *    request that arrives *after* two NORMAL-priority requests is
 *    granted the lock before them anyway.
 *
 * 3. Starvation freedom (the Theorem 2 liveness re-examination): a
 *    LOW-priority request is enqueued first and then repeatedly passed
 *    over by a stream of newly-arriving HIGH-priority requests. Without
 *    aging this could starve it forever; with aging enabled, it is
 *    proven to still get the lock within a bounded number of overtakes
 *    (bounded by (MAX_PRIORITY - LOW_PRIORITY) * agingThreshold).
 *
 * Run with (no JUnit / test framework required):
 *   java -cp target/classes kgroup.priority.Gap5DeterministicProof
 */
public class Gap5DeterministicProof {

    public static void main(String[] args) {
        boolean ok = true;
        ok &= scenario1_fifoRegressionWhenDisabled();
        ok &= scenario2_priorityOvertakesFifoOrderWhenEnabled();
        ok &= scenario3_agingPreventsStarvation();

        if (!ok) {
            System.out.println("\nGap 5 deterministic proof FAILED.");
            System.exit(1);
        }
        System.out.println("\nGap 5 deterministic proof: all scenarios passed.");
    }

    private static boolean scenario1_fifoRegressionWhenDisabled() {
        System.out.println("Scenario 1: FIFO regression when priority scheduling is disabled");
        PriorityLockScheduler.setEnabled(false);
        DeviceLock lock = new DeviceLock();

        int r1 = lock.addNewRequest("rtn-A", 0, PriorityLockScheduler.NORMAL_PRIORITY);
        int r2 = lock.addNewRequest("rtn-B", 0, PriorityLockScheduler.HIGH_PRIORITY);
        int r3 = lock.addNewRequest("rtn-C", 0, PriorityLockScheduler.NORMAL_PRIORITY);
        admitAll(lock, r1, r2, r3, "rtn-A", "rtn-B", "rtn-C");

        LockRequest head = lock.getQueueHead();
        boolean pass = head.contains("rtn-A", 0);
        System.out.println("  head (expect rtn-A, arrival order): " + head + " -> " + (pass ? "PASS" : "FAIL"));
        return pass;
    }

    private static boolean scenario2_priorityOvertakesFifoOrderWhenEnabled() {
        System.out.println("Scenario 2: HIGH priority overtakes older NORMAL requests when enabled");
        PriorityLockScheduler.setEnabled(true);
        PriorityLockScheduler.setAgingThreshold(3);
        Gap5Metrics.reset();
        DeviceLock lock = new DeviceLock();

        int r1 = lock.addNewRequest("rtn-A", 0, PriorityLockScheduler.NORMAL_PRIORITY);
        int r2 = lock.addNewRequest("rtn-B", 0, PriorityLockScheduler.NORMAL_PRIORITY);
        admitAll(lock, r1, r2, -1, "rtn-A", "rtn-B", null);
        int r3 = lock.addNewRequest("rtn-C", 0, PriorityLockScheduler.HIGH_PRIORITY);
        lock.requestReplicated(new LockRequest("rtn-C", 0, PriorityLockScheduler.HIGH_PRIORITY), r3);

        LockRequest head = lock.getQueueHead();
        boolean pass = head.contains("rtn-C", 0);
        System.out.println("  head (expect rtn-C despite arriving last): " + head + " -> " + (pass ? "PASS" : "FAIL"));

        // Grant it and release it, then confirm the two older NORMAL
        // requests are still both present and eventually served in their
        // own arrival order (no request was lost by the reordering).
        int headSeqNo = lock.getLockRequestSeqNo("rtn-C", 0);
        lock.lock(head, headSeqNo);
        LockRequest next = lock.release(head, headSeqNo);
        boolean pass2 = next != null && next.contains("rtn-A", 0);
        System.out.println("  next after release (expect rtn-A, the older survivor): " + next + " -> "
                + (pass2 ? "PASS" : "FAIL"));
        int nextSeqNo = lock.getLockRequestSeqNo(next.getRoutineID(), next.getRoutineSeqNo());
        lock.lock(next, nextSeqNo);
        LockRequest last = lock.release(next, nextSeqNo);
        boolean pass3 = last != null && last.contains("rtn-B", 0);
        System.out.println("  next after that release (expect rtn-B): " + last + " -> " + (pass3 ? "PASS" : "FAIL"));

        return pass && pass2 && pass3;
    }

    private static boolean scenario3_agingPreventsStarvation() {
        System.out.println("Scenario 3: anti-starvation aging bounds a LOW request's wait");
        PriorityLockScheduler.setEnabled(true);
        int agingThreshold = 3;
        PriorityLockScheduler.setAgingThreshold(agingThreshold);
        DeviceLock lock = new DeviceLock();

        int rLow = lock.addNewRequest("rtn-low", 0, PriorityLockScheduler.LOW_PRIORITY);
        lock.requestReplicated(new LockRequest("rtn-low", 0, PriorityLockScheduler.LOW_PRIORITY), rLow);

        // Worst-case bound: (MAX_PRIORITY - LOW_PRIORITY) * agingThreshold
        // scheduling rounds, each one adding a brand-new HIGH-priority
        // competitor that (absent aging) would always win.
        int bound = (PriorityLockScheduler.MAX_PRIORITY - PriorityLockScheduler.LOW_PRIORITY) * agingThreshold;
        int round;
        boolean lowWon = false;
        for (round = 0; round < bound + 5; round++) {
            int rHigh = lock.addNewRequest("rtn-high-" + round, 0, PriorityLockScheduler.HIGH_PRIORITY);
            lock.requestReplicated(new LockRequest("rtn-high-" + round, 0, PriorityLockScheduler.HIGH_PRIORITY),
                    rHigh);
            LockRequest head = lock.getQueueHead();
            // Immediately grant-and-release every HIGH winner so the
            // queue keeps draining except for rtn-low, simulating a
            // steady stream of higher-priority arrivals.
            if (head.contains("rtn-low", 0)) {
                lowWon = true;
                break;
            }
            lock.lock(head, lock.getQueueHeadReqSeqNo());
            lock.release(head, lock.getQueueHeadReqSeqNo());
        }

        boolean pass = lowWon && round <= bound;
        System.out.println("  rtn-low won the lock after " + round + " competing HIGH arrivals (bound=" + bound
                + ") -> " + (pass ? "PASS" : "FAIL"));
        return pass;
    }

    // Helper: moves newly-added requests from newLockRequests into the
    // replicated queue, exactly like replicateRequest()/requestReplicated()
    // do for real messages, skipping any reqSeqNo of -1 (not admitted).
    private static void admitAll(DeviceLock lock, int r1, int r2, int r3, String id1, String id2, String id3) {
        if (r1 >= 0) {
            lock.requestReplicated(peekPriorityAware(lock, id1, r1), r1);
        }
        if (r2 >= 0) {
            lock.requestReplicated(peekPriorityAware(lock, id2, r2), r2);
        }
        if (r3 >= 0 && id3 != null) {
            lock.requestReplicated(peekPriorityAware(lock, id3, r3), r3);
        }
    }

    private static LockRequest peekPriorityAware(DeviceLock lock, String routineID, int reqSeqNo) {
        for (LockRequest rq : lock.getNewLockRequests().values()) {
            if (rq.contains(routineID, 0)) {
                return rq;
            }
        }
        return new LockRequest(routineID, 0);
    }
}
