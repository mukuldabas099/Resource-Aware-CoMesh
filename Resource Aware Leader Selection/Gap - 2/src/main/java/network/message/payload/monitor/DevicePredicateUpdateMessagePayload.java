package network.message.payload.monitor;

import network.message.payload.MessagePayload;

/**
 * Gap 3 (Smart Predicate Push-Down) - push-down behavior.
 *
 * Unsolicited ("pushed") message sent by a device coterie's leader to a
 * routine coterie's leader. Instead of shipping the raw sensor reading (as
 * {@link DeviceReadingUpdateMessagePayload} does), the device coterie
 * locally evaluates the leaf condition(s) of the routine's predicate that
 * concern this device (e.g. "reading > threshold") and sends only the
 * resulting boolean.
 *
 * Crucially, this message is only sent when {@code satisfied} actually
 * flips relative to the last value sent for this device - i.e. only state
 * changes that can affect the predicate's outcome are forwarded, which is
 * exactly the proposed direction for closing Gap 3.
 */
public class DevicePredicateUpdateMessagePayload extends MessagePayload {
    private static final long serialVersionUID = 4471028839213456702L;

    public boolean satisfied;

    public DevicePredicateUpdateMessagePayload(String deviceID, boolean satisfied) {
        this.deviceID = deviceID;
        this.satisfied = satisfied;
    }
}
