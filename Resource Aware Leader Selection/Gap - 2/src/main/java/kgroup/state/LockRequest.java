package kgroup.state;

import java.io.Serializable;

public class LockRequest implements Serializable {
    private String routineID;
    private int routineSeqNo;

    public LockRequest(String routineID, int routineSeqNo) {
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
    }

    public LockRequest(LockRequest rq) {
        this.routineID = rq.routineID;
        this.routineSeqNo = rq.routineSeqNo;
    }

    public String getRoutineID() {
        return routineID;
    }

    public int getRoutineSeqNo() {
        return routineSeqNo;
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
