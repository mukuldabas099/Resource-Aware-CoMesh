package network.message.payload.failure;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class NodeFailureMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -5307849874262842593L;

    public String failedNodeID;

    public NodeFailureMessagePayload(KGroupType kGroupType, List<String> entitiesIDs, String failedNodeID) {
        super(-1, kGroupType, entitiesIDs);
        this.failedNodeID = failedNodeID;
    }
}
