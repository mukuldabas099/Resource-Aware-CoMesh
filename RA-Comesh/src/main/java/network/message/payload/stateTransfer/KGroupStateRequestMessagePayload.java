package network.message.payload.stateTransfer;

import java.util.List;

import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import network.message.payload.MessagePayload;

public class KGroupStateRequestMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -4554161628775213371L;

    public LeaderElectionCause cause;

    public KGroupStateRequestMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs,
            LeaderElectionCause cause) {
        super(epochNo, kGroupType, entitiesIDs);
        this.cause = cause;
    }
}
