import java.util.ArrayList;
import java.util.List;

import kgroup.KGroup;
import network.message.MessageType;
import network.message.payload.MessagePayload;

public class QuorumMsgInfo extends MsgInfo {
    private MessageType type;
    private MessagePayload payload;
    private KGroup kgroup;
    private int positiveReplies, quorumTime;
    private List<String> replied;
    private boolean approved;

    public QuorumMsgInfo(MessageType type, MessagePayload payload, KGroup kgroup, boolean boundedWait, int routeRTT,
            int msgTS, int initialEpoch) {
        super(boundedWait, routeRTT, msgTS, initialEpoch);
        this.type = type;
        this.payload = payload;
        this.kgroup = kgroup;
        positiveReplies = 0;
        replied = new ArrayList<String>();
        approved = false;
        quorumTime = msgTS;
    }

    public MessageType getMessageType() {
        return type;
    }

    public MessagePayload getMessagePayload() {
        return payload;
    }

    public void newReply(String nodeID, boolean positive) {
        if (!replied.contains(nodeID)) {
            replied.add(nodeID);
            if (positive)
                positiveReplies++;
        }
    }

    public int getPositiveRepliesCount() {
        return positiveReplies;
    }

    public KGroup getKGroup() {
        return kgroup;
    }

    public List<String> getRepliedNodesIDs() {
        return replied;
    }

    public List<String> getNoReplyNodesIDs() {
        List<String> kgroupRecipients;
        kgroupRecipients = new ArrayList<>(kgroup.curNodesIDs);
        kgroupRecipients.removeAll(replied);
        return kgroupRecipients;
    }

    public void setApproved(int ts) {
        approved = true;
        quorumTime = ts - quorumTime;
    }

    public boolean isApproved() {
        return approved;
    }

    public int getQuorumTime() {
        return quorumTime;
    }
}
