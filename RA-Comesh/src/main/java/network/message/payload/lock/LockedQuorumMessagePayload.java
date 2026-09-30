package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockedQuorumMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -6325764608392709083L;

    public int reqSeqNo, routineSeqNo;

    public LockedQuorumMessagePayload(int epochNo, List<String> deviceID, String routineID, int reqSeqNo,
            int routineSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.reqSeqNo = reqSeqNo;
        this.routineSeqNo = routineSeqNo;
    }
}
