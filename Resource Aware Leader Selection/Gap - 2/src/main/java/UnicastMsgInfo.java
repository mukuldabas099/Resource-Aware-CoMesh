import kgroup.KGroup;
import network.message.MessageType;
import network.message.payload.MessagePayload;

public class UnicastMsgInfo extends MsgInfo {
    private MessageType type;
    private MessagePayload payload;
    private String dst;
    private KGroup routineKGroup, deviceKGroup;
    private boolean acknowledged;

    public UnicastMsgInfo(MessageType type, MessagePayload payload, KGroup routineKGroup, KGroup deviceKGroup,
            String dst, boolean boundedWait, int routeRTT, int msgTS, int initialEpoch) {
        super(boundedWait, routeRTT, msgTS, initialEpoch);
        this.type = type;
        this.payload = payload;
        this.routineKGroup = routineKGroup;
        this.deviceKGroup = deviceKGroup;
        this.dst = dst;
        acknowledged = false;
    }

    public MessageType getMessageType() {
        return type;
    }

    public MessagePayload getMessagePayload() {
        return payload;
    }

    public String getDestination() {
        return dst;
    }

    public void updateDestination(String dst) {
        this.dst = dst;
    }

    public KGroup getRoutineKGroup() {
        return routineKGroup;
    }

    public KGroup getDeviceKGroup() {
        return deviceKGroup;
    }

    public void setAcknowledged() {
        acknowledged = true;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }
}
