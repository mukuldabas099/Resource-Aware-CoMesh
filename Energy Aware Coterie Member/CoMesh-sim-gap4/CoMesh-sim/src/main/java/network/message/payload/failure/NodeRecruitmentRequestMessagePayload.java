package network.message.payload.failure;

import java.util.ArrayList;
import java.util.Map;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class NodeRecruitmentRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -2822918732737107307L;

    public Map<String, Object> state;

    public NodeRecruitmentRequestMessagePayload(int epochNo, KGroupType kGroupType, Map<String, Object> states) {
        super(epochNo, kGroupType, new ArrayList<String>(states.keySet()));
        this.state = states;
    }
}
