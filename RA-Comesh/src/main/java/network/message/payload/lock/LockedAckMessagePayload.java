package network.message.payload.lock;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockedAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -9066647417477343542L;

    public LockedAckMessagePayload(int epochNo, String deviceID, int srcSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
    }
}
