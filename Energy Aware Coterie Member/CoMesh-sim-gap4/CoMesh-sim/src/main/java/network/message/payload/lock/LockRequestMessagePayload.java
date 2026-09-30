package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 8452728320378949730L;

    public int routineSeqNo;

    public LockRequestMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
    }
}
