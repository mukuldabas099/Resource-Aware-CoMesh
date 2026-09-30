package network.message.payload.monitor;

import network.message.payload.ReplyMessagePayload;

public class DeviceReadingMessagePayload extends ReplyMessagePayload {
    private static final long serialVersionUID = 8883248266329096745L;

    public float reading;

    public DeviceReadingMessagePayload(int srcSeqNo, float reading) {
        super(srcSeqNo);
        this.reading = reading;
    }
}