package network.message;

public enum MessageType {
    // k-Group Leader Election
    ELECTION, // sent by non-bully node to start
    ELECTION_ACK, // sent by other k-group nodes
    ELECTED, // sent by k-group's elected leader
    ELECTED_ACK, // sent by all nodes in a k-group
    // k-Group node replacement
    NODE_FAILURE, // sent by k-group node to leader
    NODE_FAILURE_ACK, // sent by k-group leader to k-group node
    NODE_RECRUITEMENT_REQUEST, // sent by k-group leader to new k-group recruit
    NODE_RECRUITED, // sent by new k-group recruit to k-group leader
    // k-Group State Transfer upon Epoch Change or node failure and replacement
    KGROUP_STATE_REQUEST, // sent by new k-group leader to old k-group leader
    KGROUP_STATE, // sent by old k-group leader to new k-group leader
    KGROUP_STATE_DISTRIBUTION, // sent by new k-group leader to new k-group nodes
    KGROUP_STATE_ACK, // sent by new k-group nodes to new k-group leader
    LOCAL_KGROUP_STATE_REQUEST, // sent by new k-group leader to current/old k-group nodes
    LOCAL_KGROUP_STATE, // sent by current/old k-group nodes to new k-group leader
    // Routine Condition-Monitoring
    DEVICE_STATE_CHECK, // sent by routine k-group's leader to device
    DEVICE_STATE, // sent by device to routine k-group's leade
    DEVICE_READING_REQUEST, // sent by routine k-group's leader to device
    DEVICE_READING, // sent by device to routine k-group's leader
    // Gap 3: Smart Predicate Push-Down
    DEVICE_READING_UPDATE, // baseline: device k-group leader pushes full raw reading to routine
                           // k-group leader on every state update
    DEVICE_PREDICATE_UPDATE, // push-down: device k-group leader locally evaluates its leaf
                              // condition(s) and pushes only the resulting bit, and only when
                              // it flips
    // Routine Triggering
    TRIGGER_QUORUM, // sent by routine k-group's leader to routine k-group's members
    TRIGGER_QUORUM_ACK, // sent by all nodes in a routine k-group to its leader
    // Device k-group leader discovery
    DEVICE_KGROUP_LEADER_INFO, // sent by a device k-group's member to a routine k-group's leader
    ROUTINE_KGROUP_LEADER_INFO, // sent by a routine k-group's member to a device k-group's leader
    // Device Lock Request Queueing
    LOCK_REQUEST, // sent by routine k-group's leader to device k-group's leader
    LOCK_REQUEST_ACK, // sent as an acknowledgement to the routine k-group's leader by device
                      // k-group's leader (in response to LOCK_REQUEST)
    LOCK_REQUEST_QUORUM, // sent by device k-group's leader to all k-group nodes
    LOCK_REQUEST_QUORUM_ACK, // sent by all nodes in a device k-group to leader
    LOCK_REQUESTED, // sent by device k-group's leader to routine k-group's leader
    LOCK_REQUESTED_ACK, // sent as an acknowledgement to the device k-group's leader by routine
                        // k-group's leader (in response to LOCK_REQUEST_REPLY)
    // Device Lock Acquisition
    LOCKED_QUORUM, // sent by device k-group's leader to all k-group nodes
    LOCKED_QUORUM_ACK, // sent by all nodes in a device k-group to leader
    LOCKED, // sent by device k-group's leader to routine k-group's leader
    LOCKED_ACK, // sent by routine k-group's leader to device k-group's leader
    // Routine Excecution
    // Device Lock Release
    LOCK_RELEASE_REQUEST, // sent by routine k-group's leader to device k-group's leader
    LOCK_RELEASE_REQUEST_ACK, // sent as an acknowledgement to the routine k-group's leader by device
                              // k-group's leader (in response to LOCK_RELEASE_REQUEST)
    LOCK_RELEASE_QUORUM, // sent by device k-group's leader to all k-group nodes
    LOCK_RELEASE_QUORUM_ACK, // sent by all nodes in a device k-group to leader
    LOCK_RELEASED, // sent by device k-group's leader to routine k-group's leader (in response to
                   // LOCK_RELEASE_REQUEST)
    LOCK_RELEASED_ACK; // sent as an acknowledgement to the routine k-group's leader by device
                       // k-group's leader (in response to LOCK_RELEASED)
}
