package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockReleaseQuorumAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -5205396067179785877L;

    public int reqSeqNo, routineSeqNo;

    public LockReleaseQuorumAckMessagePayload(int epochNo, List<String> deviceID, int srcSeqNo, String routineID,
            int reqSeqNo, int routineSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
        this.routineID = routineID;
        this.reqSeqNo = reqSeqNo;
        this.routineSeqNo = routineSeqNo;
    }
}
