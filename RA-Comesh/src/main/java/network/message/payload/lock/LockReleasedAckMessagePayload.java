package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockReleasedAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -6501728481760721880L;
    public int routineSeqNo;

    public LockReleasedAckMessagePayload(
            int epochNo,
            List<String> deviceID,
            String routineID,
            int routineSeqNo,
            int srcSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
    }
}
