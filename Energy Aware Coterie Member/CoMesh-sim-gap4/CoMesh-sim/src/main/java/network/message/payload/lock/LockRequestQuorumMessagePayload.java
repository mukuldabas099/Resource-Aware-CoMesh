package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockRequestQuorumMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -9127445782736958486L;

    public int reqSeqNo, routineSeqNo;

    public LockRequestQuorumMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo,
            int reqSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.reqSeqNo = reqSeqNo;
        this.routineSeqNo = routineSeqNo;
    }
}
