package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import kgroup.priority.PriorityLockScheduler;
import network.message.payload.MessagePayload;

public class LockRequestQuorumMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -9127445782736958486L;

    public int reqSeqNo, routineSeqNo;
    // Gap 5: see LockRequestMessagePayload.priority -- this is the same
    // priority value, replicated to the device k-group's other members
    // alongside the reqSeqNo so every replica's local DeviceLock stores
    // the correct priority for the request too.
    public int priority;

    public LockRequestQuorumMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo,
            int reqSeqNo) {
        this(epochNo, deviceID, routineID, routineSeqNo, reqSeqNo, PriorityLockScheduler.NORMAL_PRIORITY);
    }

    public LockRequestQuorumMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo,
            int reqSeqNo, int priority) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.reqSeqNo = reqSeqNo;
        this.routineSeqNo = routineSeqNo;
        this.priority = priority;
    }
}
