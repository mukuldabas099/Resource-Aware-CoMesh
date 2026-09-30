package network.message.payload.election;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class ElectionAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 3917732281659350393L;

    public ElectionAckMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
    }
}
