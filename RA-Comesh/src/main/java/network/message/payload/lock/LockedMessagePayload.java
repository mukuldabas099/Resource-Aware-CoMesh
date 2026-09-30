package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LockedMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 8889721375113625608L;

    public int routineSeqNo;

    public LockedMessagePayload(int epochNo, List<String> routineID, String deviceID, int routineSeqNo) {
        super(epochNo, KGroupType.ROUTINE, routineID);
        this.deviceID = deviceID;
        this.routineSeqNo = routineSeqNo;
    }
}
