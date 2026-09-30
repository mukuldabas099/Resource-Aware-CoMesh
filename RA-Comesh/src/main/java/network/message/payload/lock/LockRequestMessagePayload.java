package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import kgroup.priority.PriorityLockScheduler;
import network.message.payload.MessagePayload;

public class LockRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 8452728320378949730L;

    public int routineSeqNo;
    // Gap 5: priority of the routine issuing this lock request, carried
    // from the requesting routine k-group leader to the device k-group
    // leader. Defaults to NORMAL_PRIORITY via the 4-arg constructor below
    // so every pre-Gap-5 call site keeps compiling and behaving exactly
    // as before.
    public int priority;

    public LockRequestMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo) {
        this(epochNo, deviceID, routineID, routineSeqNo, PriorityLockScheduler.NORMAL_PRIORITY);
    }

    public LockRequestMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo,
            int priority) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
        this.priority = priority;
    }
}
