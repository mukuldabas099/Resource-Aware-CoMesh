package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class RoutineKGroupLeaderInfoMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -1694369111580051543L;

    public String leaderNodeID;

    public RoutineKGroupLeaderInfoMessagePayload(int epochNo, List<String> deviceID, int srcSeqNo,
            String leaderNodeID) {
        super(epochNo, KGroupType.DEVICE, deviceID, srcSeqNo);
        this.leaderNodeID = leaderNodeID;
    }
}
