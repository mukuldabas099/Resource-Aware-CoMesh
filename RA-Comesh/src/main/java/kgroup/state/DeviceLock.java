package kgroup.state;

import java.util.*;
import java.util.Map.Entry;

import kgroup.priority.PriorityLockScheduler;

public class DeviceLock extends KGroupState {
    private static final long serialVersionUID = 9018683307565273560L;

    private int reqSeqNo;
    // requests that arrive at a k-group leader
    // go here before they are approved to be put at the actual queue
    private SortedMap<Integer, LockRequest> newLockRequests;
    private SortedMap<Integer, LockRequest> queue;
    private boolean locked;
    private int lastLockedReqSeqNo;
    // Gap 5: Priority-Aware Lock Scheduling.
    // Which reqSeqNo currently holds the lock. Before Gap 5, "the locker"
    // was always queue.firstKey() because requests were only ever granted
    // in strict FIFO/reqSeqNo order. Under priority-aware scheduling the
    // granted request need not be the lowest reqSeqNo still in the queue
    // (an older, lower-priority request can legitimately still be sitting
    // in front of it), so which entry is locked has to be tracked
    // explicitly instead of inferred from queue order. -1 means "none".
    private int lockedReqSeqNo;
    // Gap 5: per-pending-request count of how many scheduling decisions
    // it has been passed over for -- the anti-starvation aging bonus is
    // derived from this (see PriorityLockScheduler). Purely a local
    // scheduling hint, not part of the replicated safety-critical state:
    // if a new leader takes over it starts aging from zero, which only
    // resets the *bound* on worst-case wait, not correctness.
    private transient Map<Integer, Integer> overtakeCounts;

    private List<Integer> reqSeqNoRecreation;
    private SortedMap<Integer, List<LockRequest>> queueRecreation;
    private int lockedRecreation;
    // Gap 5: majority-vote recreation of lockedReqSeqNo on leader failover,
    // exactly mirroring how reqSeqNoRecreation recreates reqSeqNo below.
    private List<Integer> lockedReqSeqNoRecreation;

    public DeviceLock() {
        reqSeqNo = -1;
        newLockRequests = new TreeMap<Integer, LockRequest>();
        queue = new TreeMap<Integer, LockRequest>();
        locked = false;
        lastLockedReqSeqNo = -1;
        lockedReqSeqNo = -1;
        overtakeCounts = new HashMap<Integer, Integer>();

        reqSeqNoRecreation = new ArrayList<Integer>();
        queueRecreation = new TreeMap<Integer, List<LockRequest>>();
        lockedRecreation = 0;
        lockedReqSeqNoRecreation = new ArrayList<Integer>();
    }

    public DeviceLock(DeviceLock deviceLock) {
        reqSeqNo = deviceLock.reqSeqNo;
        lastLockedReqSeqNo = deviceLock.lastLockedReqSeqNo;
        lockedReqSeqNo = deviceLock.lockedReqSeqNo;
        newLockRequests = new TreeMap<Integer, LockRequest>(deviceLock.newLockRequests);
        queue = new TreeMap<Integer, LockRequest>(deviceLock.queue);
        locked = deviceLock.locked;
        overtakeCounts = new HashMap<Integer, Integer>();

        reqSeqNoRecreation = new ArrayList<Integer>();
        queueRecreation = new TreeMap<Integer, List<LockRequest>>();
        lockedRecreation = 0;
        lockedReqSeqNoRecreation = new ArrayList<Integer>();
    }

    // called by leader
    public boolean isLockRequestReceived(String routineID, int routineSeqNo) {
        for (LockRequest rq : newLockRequests.values()) {
            if (rq.contains(routineID, routineSeqNo)) {
                return true;
            }
        }
        for (LockRequest rq : queue.values()) {
            if (rq.contains(routineID, routineSeqNo)) {
                return true;
            }
        }
        return false;
    }

    // called by leader
    public boolean isLockRequestGranted(String routineID, int routineSeqNo) {
        for (LockRequest rq : queue.values()) {
            if (rq.contains(routineID, routineSeqNo)) {
                return true;
            }
        }
        return false;
    }

    // called by leader
    public boolean isAcquired(String routineID, int routineSeqNo) {
        if (isQueueEmpty()) {
            return false;
        }
        if (getLocker() != null && getLocker().contains(routineID, routineSeqNo)) {
            return locked;
        }
        return false;
    }

    // called by leader
    public boolean isAvailable() {
        return isQueueEmpty();
    }

    // called by leader
    public boolean isLocked() {
        if (isQueueEmpty()) {
            locked = false;
            lockedReqSeqNo = -1;
        }
        return locked;
    }

    // called by leader
    public boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    // called by leader
    public boolean isNewLockRequestsEmpty() {
        return newLockRequests.isEmpty();
    }

    // called by leader
    // Gap 5: returns the reqSeqNo of whichever pending request the current
    // scheduling policy considers "next in line". Under the original FIFO
    // policy (PriorityLockScheduler disabled) this is exactly
    // queue.firstKey(), unchanged. Under priority-aware scheduling it may
    // be a higher-(effective-)priority request further back in the queue.
    public int getQueueHeadReqSeqNo() {
        Integer head = PriorityLockScheduler.peekHead(queue, overtakeCounts);
        if (head == null) {
            // mirror TreeMap#firstKey()'s contract on an empty map, since
            // that is exactly what every existing caller already expects
            // (callers only invoke this when they know the queue isn't
            // empty; see isQueueEmpty() guards throughout).
            return queue.firstKey();
        }
        return head;
    }

    // called by leader
    public LockRequest getQueueHead() {
        if (!isQueueEmpty()) {
            return queue.get(getQueueHeadReqSeqNo());
        } else {
            return null;
        }
    }

    public int getQueueSize() {
        return queue.size();
    }

    // called by leader
    public Map<Integer, LockRequest> getNewLockRequests() {
        return newLockRequests;
    }

    // called by leader
    public int getLockerReqSeqNo() {
        if (!isQueueEmpty() && locked) {
            return lockedReqSeqNo;
        }
        return -1;
    }

    // called by leader
    public LockRequest getLocker() {
        if (isQueueEmpty()) {
            locked = false;
            lockedReqSeqNo = -1;
        }
        if (locked) {
            return queue.get(lockedReqSeqNo);
        }
        return null;
    }

    // called by leader
    public int addNewRequest(String routineID, int routineSeqNo) {
        return addNewRequest(routineID, routineSeqNo, PriorityLockScheduler.NORMAL_PRIORITY);
    }

    // called by leader
    // Gap 5: overload carrying the requesting routine's priority through
    // to the LockRequest stored in newLockRequests.
    public int addNewRequest(String routineID, int routineSeqNo, int priority) {
        for (LockRequest rq : newLockRequests.values()) {
            if (rq.contains(routineID, routineSeqNo)) {
                return -1;
            }
        }
        reqSeqNo++; // sequence number for queue recreation on leader failure
        newLockRequests.put(reqSeqNo, new LockRequest(routineID, routineSeqNo, priority));
        return reqSeqNo;
    }

    // called by leader
    public int getLockRequestSeqNo(String routineID, int routineSeqNo) {
        for (Map.Entry<Integer, LockRequest> e : newLockRequests.entrySet()) {
            if (e.getValue().contains(routineID, routineSeqNo)) {
                return e.getKey();
            }
        }
        for (Map.Entry<Integer, LockRequest> e : queue.entrySet()) {
            if (e.getValue().contains(routineID, routineSeqNo)) {
                return e.getKey();
            }
        }
        return -1;
    }

    public int getLastLockedReqSeqNo() {
        return lastLockedReqSeqNo;
    }

    // this is called by a non-leader k-group member
    // upon request reception from the leader
    public void replicateRequest(LockRequest rq, int reqSeqNo) {
        queue.put(reqSeqNo, new LockRequest(rq));

        // check the newLockRequests
        newLockRequests.remove(reqSeqNo);
    }

    // called by leader as soon as the request has been replicated by the rest of
    // the k-group members
    // Gap 5: returns the reqSeqNo this admission's aging tick decided is
    // now the head (null if priority scheduling is disabled) -- callers
    // that need "is my new request now the head" should use this return
    // value directly rather than peeking again afterwards (see
    // PriorityLockScheduler.tick's javadoc for why a fresh peek right
    // after a tick is not guaranteed to agree with what the tick itself
    // just decided).
    public Integer requestReplicated(LockRequest rq, int reqSeqNo) {
        // System.out.println("replicated request " + rq + "!!!!!!!!!!");
        // Gap 5: newLockRequests already holds this request with its
        // real, originally-declared priority (set back when the LOCK_REQUEST
        // first arrived -- see addNewRequest). Some callers build `rq`
        // fresh with only (routineID, routineSeqNo) and no priority
        // information available to them at that point, which would
        // otherwise silently downgrade every request to NORMAL right as
        // it's admitted into the queue. Prefer the stored entry; fall
        // back to the passed-in rq only if it's for some reason missing.
        LockRequest stored = newLockRequests.get(reqSeqNo);
        LockRequest toStore = (stored != null) ? stored : rq;
        queue.put(reqSeqNo, new LockRequest(toStore));
        newLockRequests.remove(reqSeqNo);
        // Gap 5: a new request just became pending -- one real
        // scheduling event, so advance the anti-starvation aging clock
        // exactly once for it (see PriorityLockScheduler.tick).
        return PriorityLockScheduler.tick(queue, overtakeCounts);
    }

    // called by leader
    public boolean lock(LockRequest rq, int reqSeqNo) {
        boolean isCurrentHead = getQueueHead().equals(rq) && getQueueHeadReqSeqNo() == reqSeqNo;
        // Gap 5: under strict FIFO, a request could only ever be granted
        // if it was exactly the next expected reqSeqNo (lastLockedReqSeqNo
        // + 1), since nothing could ever be granted out of arrival order.
        // Under priority-aware scheduling a request can legitimately be
        // granted while older (lower-reqSeqNo), lower-priority requests
        // are still waiting behind it, so the guard only needs to ensure
        // this exact reqSeqNo has never been granted before (monotonic,
        // no double-grants) -- not that it is the very next integer.
        boolean seqNoOk = PriorityLockScheduler.isEnabled()
                ? reqSeqNo > lastLockedReqSeqNo
                : reqSeqNo == lastLockedReqSeqNo + 1;
        if (isCurrentHead && seqNoOk) {
            locked = true;
            lockedReqSeqNo = reqSeqNo;
            lastLockedReqSeqNo = reqSeqNo;
        }
        return locked;
    }

    // called by non-leader
    public void replicateLock(LockRequest rq, int reqSeqNo) {
        locked = true;
        lockedReqSeqNo = reqSeqNo;
        // Lock queue repair: safe ONLY under strict FIFO granting order,
        // where "reqSeqNo was just granted" implies every lower reqSeqNo
        // must already have been granted-and-released (so it's safe to
        // treat them as stale and drop them). Gap 5 breaks that
        // implication (an older, lower-priority request can still be
        // legitimately pending), so this fast-path pruning is skipped
        // under priority-aware scheduling; correctness there instead
        // relies on explicit per-reqSeqNo removal (release/replicateRelease)
        // plus the existing quorum-based state-transfer reconciliation
        // (KGroup.addLocalState) as the safety net for repair.
        if (!PriorityLockScheduler.isEnabled()) {
            while (!queue.isEmpty() && queue.firstKey() < reqSeqNo) {
                queue.remove(queue.firstKey());
            }
        }
        queue.put(reqSeqNo, new LockRequest(rq));
    }

    // called by leader
    // returns next routine's ID/seqNo to be locked
    public LockRequest release(LockRequest rq, int reqSeqNo) {
        // unlock device and delete last granted request
        if (locked && getLocker().equals(rq) && getLockerReqSeqNo() == reqSeqNo) {
            locked = false;
            lockedReqSeqNo = -1;
        }
        queue.remove(reqSeqNo);
        overtakeCounts.remove(reqSeqNo);

        if (PriorityLockScheduler.isEnabled()) {
            // Gap 5: a slot just opened up -- one real scheduling event.
            // Use the reqSeqNo tick() itself decided on (see its javadoc
            // for why a fresh peek afterwards could disagree).
            Integer next = PriorityLockScheduler.tick(queue, overtakeCounts);
            return next != null ? queue.get(next) : null;
        }
        // original FIFO behavior, unchanged
        if (!queue.isEmpty()) {
            return queue.get(queue.firstKey());
        }
        return null;
    }

    // called by non-leader
    public void replicateRelease(LockRequest rq, int reqSeqNo) {
        if (locked && getLocker().equals(rq) && getLockerReqSeqNo() == reqSeqNo) {
            locked = false;
            lockedReqSeqNo = -1;
        }
        queue.remove(reqSeqNo);
        overtakeCounts.remove(reqSeqNo);

        // lock queue repair -- see the identical guard/comment in
        // replicateLock() above.
        if (!PriorityLockScheduler.isEnabled()) {
            while (!queue.isEmpty() && queue.firstKey() < reqSeqNo) {
                queue.remove(queue.firstKey());
            }
        }
    }

    // called by leader
    public void addLocalState(DeviceLock localState, int f) {
        for (Entry<Integer, LockRequest> e : localState.queue.entrySet()) {
            if (!queueRecreation.containsKey(e.getKey())) {
                queueRecreation.put(e.getKey(), new ArrayList<LockRequest>());
            }
            LockRequest rq = e.getValue();
            queueRecreation.get(e.getKey()).add(rq);
            int freq = 0;
            for (LockRequest rqi : queueRecreation.get(e.getKey())) {
                if (rqi.equals(rq)) {
                    freq++;
                }
            }
            if (freq >= f + 1) {
                queue.put(e.getKey(), new LockRequest(rq)); // check!!!!!!
            }
        }

        if (localState.locked) {
            lockedRecreation++;
            if (lockedRecreation >= f + 1) {
                locked = true;
            }
            // Gap 5: recreate which reqSeqNo is locked the same way the
            // rest of this method recreates reqSeqNo/locked -- via a
            // simple majority vote across replicas' local states.
            lockedReqSeqNoRecreation.add(localState.lockedReqSeqNo);
            int lockedSeqFreq = 0;
            for (Integer candidate : lockedReqSeqNoRecreation) {
                if (localState.lockedReqSeqNo == candidate) {
                    lockedSeqFreq++;
                }
                if (lockedSeqFreq >= f + 1) {
                    lockedReqSeqNo = candidate;
                }
            }
        }

        reqSeqNoRecreation.add(localState.reqSeqNo);
        int freq = 0;
        for (Integer reqSeqNoi : reqSeqNoRecreation) {
            if (localState.reqSeqNo == reqSeqNoi) {
                freq++;
            }
            if (freq >= f + 1) {
                reqSeqNo = reqSeqNoi;
            }
        }
    }

    public String toString() {
        String str = (locked ? "L" : "Not l") + "ocked";
        str += ", Current queue: " + queue;
        str += ", New requests: " + newLockRequests;

        return str;
    }
}
