package network.message.payload.stateTransfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kgroup.KGroupType;
import network.message.Message;
import network.message.payload.ReplyMessagePayload;

public class KGroupStateMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -6346149567749302839L;

    public Map<String, Object> state;
    public Map<String, Integer> seqNos;
    public List<Message> unprocessedMessages;

    public KGroupStateMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs, int srcSeqNo,
            Map<String, Object> state, Map<String, Integer> seqNos, List<Message> messages) {
        super(epochNo, kGroupType, entitiesIDs, srcSeqNo);
        this.state = new HashMap<String, Object>(state);
        if (seqNos != null) {
            this.seqNos = new HashMap<String, Integer>(seqNos);
        }
        this.unprocessedMessages = new ArrayList<Message>(messages);
    }
}
