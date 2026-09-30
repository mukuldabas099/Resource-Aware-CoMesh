package kgroup.priority;

import java.util.Map;
import java.util.SortedMap;

import kgroup.state.LockRequest;

/**
 * Gap 5: Priority-Aware Lock Scheduling.
 *
 * <p>Section V-B of the paper serves a device's lock queue strictly FIFO:
 * whichever routine has waited longest gets the lock next. That does not
 * distinguish a high-importance routine (e.g. a safety/security action)
 * from a low-priority convenience routine, so a long-waiting low-priority
 * routine can delay a newly-arrived but more critical one.
 *
 * <p>This class is the (optional, off-by-default) scheduling policy that
 * replaces "pick queue.firstKey()" with "pick the pending request with
 * the highest <em>effective</em> priority", where effective priority is
 * the request's own declared priority plus an <b>aging bonus</b> that
 * grows the longer a request has been repeatedly passed over. The aging
 * bonus is what keeps the policy deadlock/starvation-free (see the
 * re-examination of Theorem 2 in GAP5_PRIORITY_AWARE_LOCK_SCHEDULING.md):
 * without it, a steady stream of HIGH-priority arrivals could starve an
 * older LOW-priority request forever, which would be a genuine liveness
 * regression versus the original FIFO policy. With it, every pending
 * request's effective priority is non-decreasing over time and strictly
 * increases every AGING_THRESHOLD times it is skipped, so after at most
 * (MAX_PRIORITY - request's own priority) * AGING_THRESHOLD skips any
 * request is guaranteed to reach the maximum effective priority and, from
 * then on, can only be skipped by requests at that same ceiling -- among
 * which ties break by reqSeqNo (oldest first), so it is picked within one
 * more comparison. Bounded wait is therefore preserved for every request,
 * exactly as required by Theorem 2's liveness argument -- only the bound
 * changes (from "queue position" to "queue position AND age").
 *
 * <p>Default behavior (never enabled) is a complete no-op: DeviceLock
 * falls back to the original queue.firstKey()-based FIFO selection,
 * unchanged, byte-for-byte.
 */
public final class PriorityLockScheduler {

    public static final int LOW_PRIORITY = 0;
    public static final int NORMAL_PRIORITY = 1;
    public static final int HIGH_PRIORITY = 2;
    public static final int MAX_PRIORITY = HIGH_PRIORITY;

    // Number of times a pending request must be passed over before its
    // effective priority is bumped by one level. Lower = fights starvation
    // more aggressively (closer to FIFO); higher = favors priority more
    // strictly (closer to strict priority scheduling).
    private static volatile int agingThreshold = 3;

    private static volatile boolean enabled = false;

    private PriorityLockScheduler() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setAgingThreshold(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("agingThreshold must be >= 1");
        }
        agingThreshold = value;
    }

    public static int getAgingThreshold() {
        return agingThreshold;
    }

    public static int parsePriority(String label) {
        if (label == null) {
            return NORMAL_PRIORITY;
        }
        switch (label.trim().toUpperCase()) {
            case "HIGH":
            case "2":
                return HIGH_PRIORITY;
            case "LOW":
            case "0":
                return LOW_PRIORITY;
            case "NORMAL":
            case "1":
            default:
                return NORMAL_PRIORITY;
        }
    }

    /**
     * Pure, read-only: returns the reqSeqNo of the pending request that
     * the current scheduling policy considers the head of the queue,
     * given the queue's current contents and the current overtake counts
     * for every reqSeqNo still pending. Does NOT mutate overtakeCounts --
     * safe to call any number of times (e.g. from multiple accessors,
     * or the same accessor called twice) without changing the schedule.
     *
     * <p>When disabled, always returns queue.firstKey() (plain FIFO),
     * matching the original behavior exactly.
     */
    public static Integer peekHead(SortedMap<Integer, LockRequest> queue, Map<Integer, Integer> overtakeCounts) {
        if (queue.isEmpty()) {
            return null;
        }
        if (!enabled) {
            return queue.firstKey();
        }

        int bestReqSeqNo = -1;
        int bestEffectivePriority = Integer.MIN_VALUE;
        for (Map.Entry<Integer, LockRequest> e : queue.entrySet()) {
            int reqSeqNo = e.getKey();
            int overtakes = overtakeCounts.getOrDefault(reqSeqNo, 0);
            int agingBonus = overtakes / agingThreshold;
            int effectivePriority = e.getValue().getPriority() + agingBonus;
            if (effectivePriority > bestEffectivePriority
                    || (effectivePriority == bestEffectivePriority && reqSeqNo < bestReqSeqNo)) {
                bestEffectivePriority = effectivePriority;
                bestReqSeqNo = reqSeqNo;
            }
        }
        return bestReqSeqNo;
    }

    /**
     * Advances the anti-starvation aging clock by one tick: every
     * pending request other than the current head has its overtake
     * count incremented by one. Called exactly once per real
     * queue-mutating scheduling event (a new request being admitted, or
     * a release opening up the device) -- NOT once per read -- so that
     * the aging bound (see class javadoc) is measured in real events,
     * not in how many times some accessor happens to be queried.
     *
     * <p>Returns the reqSeqNo this tick decided is the head -- callers
     * that need "who's next" right after a tick MUST use this return
     * value rather than peeking again afterwards: because the
     * increments this tick just applied can themselves push a
     * previously-second-place request's aging bonus past the winner's
     * (e.g. exactly crossing an aging-threshold boundary), a fresh peek
     * immediately after could disagree with the decision this same tick
     * just made -- which would let a single scheduling event effectively
     * make two different decisions about who's next. A no-op (returns
     * null) when disabled or the queue is empty.
     */
    public static Integer tick(SortedMap<Integer, LockRequest> queue, Map<Integer, Integer> overtakeCounts) {
        if (!enabled || queue.isEmpty()) {
            return null;
        }
        Integer head = peekHead(queue, overtakeCounts);
        boolean viaAging = overtakeCounts.getOrDefault(head, 0) >= agingThreshold;
        for (Integer reqSeqNo : queue.keySet()) {
            if (reqSeqNo.equals(head)) {
                continue;
            }
            overtakeCounts.merge(reqSeqNo, 1, Integer::sum);
        }
        Gap5Metrics.getInstance().recordHeadComputation(head != queue.firstKey(), viaAging);
        return head;
    }
}
