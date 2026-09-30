package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockReleaseQuorumMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 7862350364653705674L;

    public int reqSeqNo, routineSeqNo;

    public LockReleaseQuorumMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo,
            int reqSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
        this.reqSeqNo = reqSeqNo;
    }
}
