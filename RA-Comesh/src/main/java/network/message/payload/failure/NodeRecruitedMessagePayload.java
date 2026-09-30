package network.message.payload.failure;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class NodeRecruitedMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -5798654073094211382L;

    public NodeRecruitedMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
    }
}
