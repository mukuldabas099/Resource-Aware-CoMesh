package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockRequestedAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -6501728481760721880L;

    public LockRequestedAckMessagePayload(int epochNo, List<String> deviceID, int srcSeqNo) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
    }
}
