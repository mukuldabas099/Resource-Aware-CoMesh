package network.message.payload.stateTransfer;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class LocalKGroupStateRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -437966124955578173L;

    public KGroupType kGroupType;
    public List<String> entitiesIDs;

    public LocalKGroupStateRequestMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs) {
        super(epochNo, kGroupType, entitiesIDs);
    }
}
