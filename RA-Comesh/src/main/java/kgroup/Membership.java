package kgroup;

public enum Membership {
    ONLINE,
    OFFLINE,
    // Gap 7: Intermittent Device Support. A device that is unreachable
    // because it is inside one of its own announced sleep windows (see
    // kgroup.sleep.DeviceSleepRegistry) -- NOT because it failed. Treated
    // like OFFLINE for the purpose of picking *new* coterie members (a
    // sleeping device can't do useful new work right now), but an
    // *incumbent* coterie member is allowed to stay a member through a
    // SLEEPING window instead of being evicted/replaced the way a genuine
    // OFFLINE failure would (see KGroup.newKGroupMembers).
    SLEEPING
}