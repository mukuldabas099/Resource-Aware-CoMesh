package network.message.payload.stateTransfer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class KGroupStateDistributionMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 5530604947123381069L;

    public Map<String, Object> state;
    public Map<String, Integer> seqNos;

    public KGroupStateDistributionMessagePayload(int epochNo, KGroupType kGroupType, Map<String, Object> state,
            Map<String, Integer> seqNos) {
        super(epochNo, kGroupType, new ArrayList<String>(state.keySet()));
        this.state = new HashMap<String, Object>(state);
        if (seqNos != null) {
            this.seqNos = new HashMap<String, Integer>(seqNos);
        }
    }
}
