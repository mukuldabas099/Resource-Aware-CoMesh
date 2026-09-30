package network.message.payload.monitor;

import network.message.payload.MessagePayload;

/**
 * Gap 3 (Smart Predicate Push-Down) - baseline behavior.
 *
 * Unsolicited ("pushed") message sent by a device coterie's leader to a
 * routine coterie's leader every time the device's raw sensor reading
 * changes. This models the paper's "Current State": the device leader
 * forwards every state update in full (the raw reading) to the routine
 * leader, which is then responsible for evaluating the (possibly
 * multi-device) predicate itself.
 *
 * Contrast with {@link DevicePredicateUpdateMessagePayload}, which only
 * carries a single evaluated bit and is only sent when that bit changes.
 */
public class DeviceReadingUpdateMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 4471028839213456701L;

    public float reading;

    public DeviceReadingUpdateMessagePayload(String deviceID, float reading) {
        this.deviceID = deviceID;
        this.reading = reading;
    }
}
