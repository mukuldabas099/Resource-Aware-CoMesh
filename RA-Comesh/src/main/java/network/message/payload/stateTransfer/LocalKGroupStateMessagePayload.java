package network.message.payload.stateTransfer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class LocalKGroupStateMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 1008075774431667084L;

    public Map<String, Object> states;
    public Map<String, Integer> seqNos;

    public LocalKGroupStateMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo,
            Map<String, Object> states, Map<String, Integer> seqNos) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
        this.states = new HashMap<String, Object>(states);
        if (seqNos != null) {
            this.seqNos = new HashMap<String, Integer>(seqNos);
        }
    }
}
