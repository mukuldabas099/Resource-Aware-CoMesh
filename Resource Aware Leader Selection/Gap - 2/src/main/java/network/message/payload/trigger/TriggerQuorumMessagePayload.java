package network.message.payload.trigger;

import java.util.List;

import kgroup.KGroupType;
import network.message.payload.MessagePayload;

public class TriggerQuorumMessagePayload extends MessagePayload {
    private static final long serialVersionUID = -710012838803253143L;
    public int routineSeqNo;

    public TriggerQuorumMessagePayload(int epochNo, List<String> routineID, int routineSeqNo) {
        super(epochNo, KGroupType.ROUTINE, routineID);
        this.routineSeqNo = routineSeqNo;
    }
}
