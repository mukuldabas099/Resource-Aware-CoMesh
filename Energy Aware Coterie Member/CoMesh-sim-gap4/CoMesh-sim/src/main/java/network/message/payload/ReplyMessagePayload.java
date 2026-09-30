package network.message.payload;

import java.util.List;

import kgroup.KGroupType;

public class ReplyMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -7456568437786695638L;
    public int srcSeqNo; // this message replies to src's message with ts = srcSeqNo

    public ReplyMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo) {
        super(epochNo, kGroupType, entitiesIDs);
        this.srcSeqNo = srcSeqNo;
    }

    public ReplyMessagePayload(int epochNo, KGroupType kGroupType, String entityID, int srcSeqNo) {
        super(epochNo, kGroupType, entityID);
        this.srcSeqNo = srcSeqNo;
    }

    public ReplyMessagePayload(int srcSeqNo) {
        this.srcSeqNo = srcSeqNo;
    }
}
