package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LockReleaseRequestAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -6501728481760721880L;

    public LockReleaseRequestAckMessagePayload(int epochNo, List<String> routineID, int srcSeqNo) {
        super(epochNo, KGroupType.ROUTINE, routineID, srcSeqNo);
    }
}
