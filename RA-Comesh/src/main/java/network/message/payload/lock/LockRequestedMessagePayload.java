package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockRequestedMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -6501728481760721880L;

    public int routineSeqNo;

    public LockRequestedMessagePayload(int epochNo, List<String> routineID, int routineSeqNo, String deviceID) {
        super(epochNo, KGroupType.ROUTINE, routineID);
        this.routineSeqNo = routineSeqNo;
        this.deviceID = deviceID;
    }
}
