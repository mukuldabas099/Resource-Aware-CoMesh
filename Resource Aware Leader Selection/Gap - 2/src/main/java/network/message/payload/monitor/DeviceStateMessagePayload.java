package network.message.payload.monitor;

import network.message.payload.ReplyMessagePayload;

public class DeviceStateMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 8128151605419785570L;

    public boolean state;

    public DeviceStateMessagePayload(int srcSeqNo, boolean state) {
        super(srcSeqNo);
        this.state = state;
    }
}
