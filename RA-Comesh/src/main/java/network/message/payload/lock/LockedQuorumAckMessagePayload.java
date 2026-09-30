package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockedQuorumAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 2704514377346524282L;
    public int reqSeqNo, routineSeqNo;

    public LockedQuorumAckMessagePayload(int epochNo, List<String> deviceID, int srcSeqNo, String routineID,
            int routineSeqNo, int reqSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
        this.reqSeqNo = reqSeqNo;
    }
}
