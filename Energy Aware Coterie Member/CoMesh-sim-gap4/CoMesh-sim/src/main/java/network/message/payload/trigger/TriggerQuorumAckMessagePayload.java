package network.message.payload.trigger;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class TriggerQuorumAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 4548420034890318714L;

    public TriggerQuorumAckMessagePayload(int epochNo, List<String> routineID, int srcSeqNo) {
        super(epochNo, KGroupType.ROUTINE, routineID, srcSeqNo);
    }
}
