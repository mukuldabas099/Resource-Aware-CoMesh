package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockReleaseRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 3631481029651092528L;

    public int routineSeqNo;

    public LockReleaseRequestMessagePayload(int epochNo, List<String> deviceID, String routineID, int routineSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID);
        this.routineID = routineID;
        this.routineSeqNo = routineSeqNo;
    }
}
