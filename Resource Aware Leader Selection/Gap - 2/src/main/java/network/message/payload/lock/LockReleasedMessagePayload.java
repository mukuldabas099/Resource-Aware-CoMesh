package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockReleasedMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -717256623598162112L;

    public int routineSeqNo;

    public LockReleasedMessagePayload(int epochNo, List<String> routineID, String deviceID, int routineSeqNo) {
        super(epochNo, KGroupType.ROUTINE, routineID);
        this.deviceID = deviceID;
        this.routineSeqNo = routineSeqNo;
    }
}
