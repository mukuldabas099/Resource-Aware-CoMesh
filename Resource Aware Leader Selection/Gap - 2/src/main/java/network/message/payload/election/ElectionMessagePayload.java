package network.message.payload.election;

import java.util.List;

import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import network.message.payload.MessagePayload;

public class ElectionMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -5070340407181689772L;

    public LeaderElectionCause cause;

    public ElectionMessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs,
            LeaderElectionCause cause) {
        super(epochNo, kGroupType, entitiesIDs);
        this.cause = cause;
    }
}
