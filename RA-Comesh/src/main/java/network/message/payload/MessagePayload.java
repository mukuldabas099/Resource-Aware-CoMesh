package network.message.payload;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import kgroup.KGroupType;

public class MessagePayload implements Serializable {
    private static final long serialVersionUID = 8135931604175469785L;
    public int epochNo;
    // the following two fields specify the k-group this message is addressed to
    public KGroupType kGroupType;
    public List<String> entitiesIDs;
    public String routineID, deviceID;

    public MessagePayload(int epochNo, KGroupType kGroupType, List<String> entitiesIDs) {
        this.epochNo = epochNo;
        this.kGroupType = kGroupType;
        this.entitiesIDs = new ArrayList<String>(entitiesIDs);
    }

    public MessagePayload(int epochNo, KGroupType kGroupType, String entityID) {
        this.epochNo = epochNo;
        this.kGroupType = kGroupType;
        this.entitiesIDs = new ArrayList<String>();
        this.entitiesIDs.add(entityID);
    }

    public MessagePayload() {
    }

    public void changeEpochNo(int newEpoch) {
        this.epochNo = newEpoch;
    }
}
