package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockRequestQuorumAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 8234550092862487011L;

    public int reqSeqNo, routineSeqNo;

    public LockRequestQuorumAckMessagePayload(int epochNo, List<String> deviceID, int srcSeqNo, String routineID,
            int reqSeqNo, int routineSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
        this.routineID = routineID;
        this.reqSeqNo = reqSeqNo;
        this.routineSeqNo = routineSeqNo;
    }
}
