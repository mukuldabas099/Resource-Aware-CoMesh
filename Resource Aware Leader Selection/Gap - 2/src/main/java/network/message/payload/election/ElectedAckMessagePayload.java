package network.message.payload.election;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class ElectedAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 1845402298249270653L;

    public ElectedAckMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
    }
}