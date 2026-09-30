package kgroup.sleep;

/**
 * Gap 7: Intermittent Device Support.
 *
 * A (routineID, routineSeqNo) lock request whose delivery to a currently
 * SLEEPING device was deferred instead of being sent into the void; kept
 * around so it can be resent once the device wakes up (see
 * KGroupManager.requestDeviceLockForRoutine / nodeJoined).
 */
public final class DeferredLockRequest {
    public final String routineID;
    public final int routineSeqNo;

    public DeferredLockRequest(String routineID, int routineSeqNo) {
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
    }
}
