import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class NodeMetrics {
    private String _id;      // node id;
    private boolean _debug = false;

    private class RoleTime {
        public String group_id;    // kgroup id (routine / device id)
        public NodeRole role;      // the latest role of the node.
        public int role_start_ts;  // Timestamp of when the role is assigned.

        public RoleTime(String gid, NodeRole r, int ts) {
            group_id = gid;
            role = r;
            role_start_ts = ts;
        }

        @Override
        public String toString() {
            return "Group: " + group_id + " role " + role.name() + " start at " + role_start_ts;
        }

        public NodeRole getRole() { return role; }
    }

    private RoleTime _singular_role;
    private HashMap<NodeRole, Integer> _singular_acc_time = new HashMap<>();


    // key: routine id / device id. value: current role and its role starting time.
    private HashMap<String, RoleTime> _runtime_rtn_roles = new HashMap<>();
    private HashMap<String, RoleTime> _runtime_dev_roles = new HashMap<>();

    // Accumulative time that node _id acts as different NodeRole
    private HashMap<NodeRole, Integer> _accumulated_time = new HashMap<>();


    // Data structure used to record number of concurrent roles over time.
    private class RoleCount {
        private NodeRole _role = NodeRole.NON_IDLE;
        public int num_role = 0;
        public int start_ts = 0;
        // Accumulative time that a ndoe acts a _role in
        // **k** kgroup simultaneously.
        //   key: num_role
        //   value: accumulated time
        private final HashMap<Integer, Integer> _concur_role_time = new HashMap<>();

        public RoleCount() {}
        public RoleCount(NodeRole role) { _role = role; }

        private void record(int ts) {
            int pre_time = _concur_role_time.getOrDefault(num_role, 0);
            int role_itv = ts - start_ts;
            _concur_role_time.put(num_role, pre_time + role_itv);
        }

        public void incRole(int ts) { incRoleMulti(1, ts); }
        public void incRoleMulti(int delta, int ts) {
            if (delta == 0) return;
            record(ts);
            num_role += delta;
            start_ts = ts;
        }

        public void decRole(int ts) { decRoleMulti(1, ts); }
        private void decRoleMulti(int delta, int ts) {
            if (delta == 0) return;
            if (delta > num_role) {
                System.out.println("[ERROR] #RCDM Invalid role decreasing for " + _role.name() +
                        " current num_role: " + num_role + " to decrease: " + delta);
                System.exit(0);
            };
            record(ts);
            num_role -= delta;
            start_ts = ts;
        }

        public HashMap<Integer, Integer> getCountOverTime() { return _concur_role_time; }
    }
    RoleCount role_count = new RoleCount(NodeRole.NON_IDLE);
    RoleCount leader_count = new RoleCount(NodeRole.LEADER);
    RoleCount member_count = new RoleCount(NodeRole.MEMBER);


    public NodeMetrics(String id) {
        _id = id;
        _singular_role = new RoleTime("singular", NodeRole.IDLE, 0);
    }
    public NodeMetrics(String id, int ts) {
        _id = id;
        _singular_role = new RoleTime("singular", NodeRole.IDLE, ts);
    }

    private void accumulateRoleTime(NodeRole role, int interval) {
        int pre_acc_time = _accumulated_time.getOrDefault(role, 0);
        _accumulated_time.put(role, pre_acc_time + interval);
    }

    private void accSingularRoleTime(NodeRole new_role, int ts) {
        NodeRole pre_role = _singular_role.role;
        int pre_acc_time = _singular_acc_time.getOrDefault(pre_role, 0);
        int interval = ts - _singular_role.role_start_ts;
        _singular_acc_time.put(pre_role, pre_acc_time + interval);
        _singular_role.role = new_role;
        _singular_role.role_start_ts = ts;
    }

    public void startRtnRoles(NodeRole new_role, List<String> ids, int ts) {
        startKGroupRoles(new_role, ids, ts, _runtime_rtn_roles);
    }

    public void startDevRoles(NodeRole new_role, List<String> ids, int ts) {
        startKGroupRoles(new_role, ids, ts, _runtime_dev_roles);
    }

    private void startKGroupRoles(NodeRole new_role, List<String> ids, int ts, HashMap<String, RoleTime> runtime_roles) {
        if (_debug) { System.out.println("[NodeMetric] Node " + _id + " updating to " + new_role + " to " + ids); }
        for (String id: ids) {
            if (runtime_roles.containsKey(id)) {
                // Node _id is already in the device kgroup id, but is changing roles
                RoleTime roleTime = runtime_roles.get(id);
                if (roleTime.role.equals(new_role)) { continue; }
                int role_itv = ts - roleTime.role_start_ts;
                logRoles(roleTime.role, ts, false);
                accumulateRoleTime(roleTime.role, role_itv);
                // Record the new role
                runtime_roles.get(id).role = new_role;
                runtime_roles.get(id).role_start_ts = ts;
            } else {  // Start a new_role for a new kgroup
                runtime_roles.put(id, new RoleTime(id, new_role, ts));
            }
            logRoles(new_role, ts, true);
            accSingularRoleTime(new_role, ts);
        }
        if (_debug) {
            System.out.println("   -- RoleCount LEADER: " + leader_count.num_role + " MEMBER: " +
                    member_count.num_role + " NON_IDLE: " + role_count.num_role);
            System.out.println("   -- runtime roles: " + runtime_roles);
        }
    }

    public void finishRtnRoles(int ts) {
        finishRoles(ts, _runtime_rtn_roles);
        checkIdle(ts);
    }

    public void finishDevRoles(int ts) {
        finishRoles(ts, _runtime_dev_roles);
        checkIdle(ts);
    }

    private void assertRoleCountIdle() {
        // Check the correctness of role count.
        if (leader_count.num_role != 0 || member_count.num_role != 0 || role_count.num_role != 0) {
            System.out.println("[ERROR] #RCER Assertion failed. Role count should be " +
                    "all zero, but with LEADER: " + leader_count.num_role + " MEMBER: " +
                    member_count.num_role + " NON_IDLE: " + role_count.num_role);
            System.exit(0);
        }

    }

    private void checkIdle(int ts) {
        if (_runtime_dev_roles.isEmpty() && _runtime_rtn_roles.isEmpty()) {
            accSingularRoleTime(NodeRole.IDLE, ts);
        }
    }

    private void finishRoles(int ts, HashMap<String, RoleTime> runtime_roles) {
        // Log to concurrent role event.
        List<NodeRole> roles = runtime_roles.values().stream()
                .map(RoleTime::getRole)
                .collect(Collectors.toList());
        logRoles(roles, ts, false);
        // Log to accumulated roles
        for (String id: runtime_roles.keySet()) {
            RoleTime roleTime = runtime_roles.get(id);
            int role_itv = ts - roleTime.role_start_ts;
            accumulateRoleTime(roleTime.role, role_itv);
        }
        runtime_roles.clear();
    }

    private void logRoles(NodeRole role, int ts, boolean is_start) {
        if (is_start) {
            if (role.equals(NodeRole.LEADER)) leader_count.incRoleMulti(1, ts);
            if (role.equals(NodeRole.MEMBER)) member_count.incRoleMulti(1, ts);
            role_count.incRoleMulti(1, ts);
        } else {
            if (role.equals(NodeRole.LEADER)) leader_count.decRoleMulti(1, ts);
            if (role.equals(NodeRole.MEMBER)) member_count.decRoleMulti(1, ts);
            role_count.decRoleMulti(1, ts);
        }
    }

    private void logRoles(List<NodeRole> roles, int ts, boolean is_start) {
        int num_leader = Collections.frequency(roles, NodeRole.LEADER);
        int num_member = Collections.frequency(roles, NodeRole.MEMBER);
        if (is_start) {
            leader_count.incRoleMulti(num_leader, ts);
            member_count.incRoleMulti(num_member, ts);
            role_count.incRoleMulti(num_leader + num_member, ts);
        } else {
            leader_count.decRoleMulti(num_leader, ts);
            member_count.decRoleMulti(num_member, ts);
            role_count.decRoleMulti(num_leader + num_member, ts);
        }
    }

    /**
     * Return csv style string of accumulative times from different roles.
     *   Role sequence: LEADER, MEMBER, IDLE
     * */
    public String getRoleTimeInString() {
        StringBuilder result = new StringBuilder();
        result.append(_id).append(",");
        for (NodeRole role: List.of(NodeRole.LEADER, NodeRole.MEMBER)) {
            result.append(_accumulated_time.getOrDefault(role, 0)).append(",");
        }
        result.append(_accumulated_time.getOrDefault(NodeRole.LEADER, 0) +
                      _accumulated_time.getOrDefault(NodeRole.MEMBER, 0))
              .append(",");
        List<NodeRole> role_seq = List.of(NodeRole.LEADER, NodeRole.MEMBER, NodeRole.IDLE);
        for (NodeRole role: role_seq) {
            result.append(_singular_acc_time.getOrDefault(role, 0)).append(",");
        }
        result.setLength(Math.max(result.length() - 1, 0));  // Remove the extra last comma.
        return String.valueOf(result);
    }

    public HashMap<Integer, Integer> getRoleCountOverTime(NodeRole role) {
        if (role.equals(NodeRole.LEADER)) {
            return leader_count.getCountOverTime();
        } else if (role.equals(NodeRole.MEMBER)) {
            return member_count.getCountOverTime();
        } else if (role.equals(NodeRole.NON_IDLE)) {
            return role_count.getCountOverTime();
        } else {
            System.out.println("Not supported NodeRole type for RoleCount request,");
            return new HashMap<>();
        }
    }

    public void debugModeOn() { _debug = true; }
    public void debugModeOff() { _debug = false; }
}
