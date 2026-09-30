package network.message.payload.stateTransfer;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class KGroupStateAckMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -5768899016281379791L;

    public KGroupStateAckMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
    }
}
