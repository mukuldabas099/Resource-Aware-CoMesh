package network.message.payload.lock;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.ReplyMessagePayload;

public class DeviceKGroupLeaderInfoMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = -1694369111580051543L;

    public String leaderNodeID;

    public DeviceKGroupLeaderInfoMessagePayload(int epochNo, List<String> routineID, int srcSeqNo,
            String leaderNodeID) {
        super(epochNo, KGroupType.ROUTINE, routineID, srcSeqNo);
        this.leaderNodeID = leaderNodeID;
    }
}
