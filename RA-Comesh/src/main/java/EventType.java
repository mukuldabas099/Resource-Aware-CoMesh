public enum EventType {
    ROUTINE_TRIGGERED,
    ROUTINE_EXECUTED,
    NODE_JOINED,
    NODE_FAILED,
    CHECKPOINT,
    // Gap 3: Smart Predicate Push-Down. Drives a device's live sensor
    // reading at a scheduled time (see Simulator's -drf device-reading
    // file / KGroupManager.setReading). Every node's event queue is
    // loaded with the same schedule (same convention as NODE_JOINED /
    // NODE_FAILED above); only the node whose ID matches the affected
    // device actually updates its own reading.
    DEVICE_READING_CHANGED
}
