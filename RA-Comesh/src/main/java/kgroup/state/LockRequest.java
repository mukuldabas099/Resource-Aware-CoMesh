package kgroup.state;

import java.io.Serializable;

import kgroup.priority.PriorityLockScheduler;

public class LockRequest implements Serializable {
    private String routineID;
    private int routineSeqNo;

    // Gap 5: Priority-Aware Lock Scheduling.
    // Declared priority of the routine that issued this request (higher =
    // more urgent). Defaults to PriorityLockScheduler.NORMAL_PRIORITY so
    // that every existing call site/constructor that does not know about
    // priorities keeps behaving exactly as before: with every request at
    // the same priority, the priority-aware ordering degenerates back to
    // plain arrival (reqSeqNo) order, i.e. the original FIFO policy.
    private int priority;

    public LockRequest(String routineID, int routineSeqNo) {
        this(routineID, routineSeqNo, PriorityLockScheduler.NORMAL_PRIORITY);
    }

    public LockRequest(String routineID, int routineSeqNo, int priority) {
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
        this.priority = priority;
    }

    public LockRequest(LockRequest rq) {
        this.routineID = rq.routineID;
        this.routineSeqNo = rq.routineSeqNo;
        this.priority = rq.priority;
    }

    public String getRoutineID() {
        return routineID;
    }

    public int getRoutineSeqNo() {
        return routineSeqNo;
    }

    public int getPriority() {
        return priority;
    }

    public boolean contains(String routineID, int routineSeqNo) {
        if (this.routineID.equals(routineID) && this.routineSeqNo == routineSeqNo) {
            return true;
        }
        return false;
    }

    public boolean equals(LockRequest rq) {
        if (this.routineID.equals(rq.routineID) && this.routineSeqNo == rq.routineSeqNo) {
            return true;
        }
        return false;
    }

    public String toString() {
        return routineID + "-" + routineSeqNo;
    }
}
