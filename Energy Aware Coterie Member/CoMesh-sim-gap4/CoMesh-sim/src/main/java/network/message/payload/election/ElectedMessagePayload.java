package network.message.payload.election;

import java.util.List;

import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import network.message.payload.MessagePayload;

public class ElectedMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 4094035504829395065L;

    public LeaderElectionCause cause;

    public ElectedMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs,
            LeaderElectionCause cause) {
        super(epochNo, kGroupType, entitiesIDs);
        this.cause = cause;
    }
}
