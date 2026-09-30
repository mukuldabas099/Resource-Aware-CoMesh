import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import kgroup.LeaderElectionPolicy;
import network.Network;
import routine.DetailedRoutine;
import routine.Routine;
import routine.command.Command;
import routine.command.InstantCommand;
import routine.statement.Statement;
import routine.statement.condition.ConditionRelation;
import routine.statement.condition.DeviceReadingCondition;
import routine.pushdown.Gap3Metrics;
import routine.pushdown.PredicatePushdownRegistry;

public class Gap3Harness {

    static void check(String label, boolean cond) {
        System.out.println((cond ? "PASS" : "FAIL") + " - " + label);
        if (!cond) {
            System.exit(1);
        }
    }

    static int curTS = -1;

    // Mirrors Simulator.java's main loop body exactly: every node
    // advances one tick and drains its inbox for that tick, in lockstep.
    static void pump(Map<String, KGroupManager> managers, int ticks) {
        for (int i = 0; i < ticks; i++) {
            curTS++;
            for (KGroupManager m : managers.values()) {
                m.incrementTS();
                m.recvAndProcessMsgs(curTS);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        int F = 1, K = 4 * F + 1, epochLength = 15_000, routineMonitorPeriod = 5000;
        int deviceKGroupRange = 1, routineKGroupRange = 1, hopOWD = 1;

        List<String> devicesIDs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            devicesIDs.add(String.valueOf(i));
        }

        // routine "R" has a single leaf condition on device "0": reading > 50
        DeviceReadingCondition cond = new DeviceReadingCondition("0", ConditionRelation.GREATER, 50f);
        Statement statement = new Statement(cond);
        Command command = new Command(new InstantCommand("0", "noop"));
        Routine detailedRoutine = new DetailedRoutine(statement, command);

        Map<String, Routine> routines = new HashMap<>();
        routines.put("R", detailedRoutine);

        Network network = new Network("/home/claude/harness/topology.txt", hopOWD, false);

        // Note: myID must be the *same String object* as the element in
        // the nodesIDs list passed to every KGroupManager (the codebase's
        // leaderElection() bully self-check uses `==`, not .equals(), on
        // myID vs. getMostProbableLeader() -- exactly mirroring how
        // Simulator.java itself iterates its own shared nodesIDs list
        // when constructing each node's KGroupManager).
        List<String> nodeIDs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            nodeIDs.add(String.valueOf(i));
        }

        Map<String, KGroupManager> managers = new HashMap<>();
        for (String nodeID : nodeIDs) {
            managers.put(nodeID, new KGroupManager(nodeID, F, K, epochLength, routineMonitorPeriod,
                    nodeIDs, devicesIDs, routines, deviceKGroupRange, routineKGroupRange, network, 0, 0,
                    new TreeMap<Integer, List<Event>>(), null, true, null, LeaderElectionPolicy.SMALLEST_HASH));
        }

        // Let epoch 0's initial leader election run to completion.
        pump(managers, 30);

        String deviceLeaderID = null, routineLeaderID = null;
        for (KGroupManager m : managers.values()) {
            String l = m.deviceKGroupInChargeOf("0").getLeader();
            if (l != null) {
                deviceLeaderID = l;
            }
            l = m.routineKGroupInChargeOf("R").getLeader();
            if (l != null) {
                routineLeaderID = l;
            }
        }
        System.out.println("device \"0\" leader = " + deviceLeaderID + ", routine \"R\" leader = " + routineLeaderID);
        check("device and routine leaders got elected", deviceLeaderID != null && routineLeaderID != null);

        KGroupManager deviceLeader = managers.get("0");
        KGroupManager routineLeader = managers.get(routineLeaderID);

        // ---- Scenario A: baseline (PredicatePushdownRegistry disabled) ----
        PredicatePushdownRegistry.setEnabled(false);
        Gap3Metrics.reset();

        deviceLeader.setReading(40f);
        pump(managers, 5);
        System.out.println("[debug] baseline sent=" + Gap3Metrics.getInstance().getBaselineMessagesSent());
        check("baseline: reading=40 (<=50) -> condition NOT satisfied",
                !routineLeader.isConditionSatisfied(cond));
        check("baseline: one raw-reading message sent", Gap3Metrics.getInstance().getBaselineMessagesSent() == 1);

        deviceLeader.setReading(60f);
        pump(managers, 5);
        check("baseline: reading=60 (>50) -> condition satisfied",
                routineLeader.isConditionSatisfied(cond));
        check("baseline: two raw-reading messages sent total",
                Gap3Metrics.getInstance().getBaselineMessagesSent() == 2);
        check("baseline: no push-down messages sent",
                Gap3Metrics.getInstance().getPushdownMessagesSent() == 0
                        && Gap3Metrics.getInstance().getPushdownMessagesSuppressed() == 0);

        // ---- Scenario B: push-down enabled ----
        PredicatePushdownRegistry.setEnabled(true);
        Gap3Metrics.reset();

        deviceLeader.setReading(70f); // bit=true (70>50), never sent before under pushdown -> must send
        pump(managers, 5);
        check("pushdown: reading=70 -> condition satisfied", routineLeader.isConditionSatisfied(cond));
        check("pushdown: first push sent (bit changed vs. no prior pushdown state)",
                Gap3Metrics.getInstance().getPushdownMessagesSent() == 1);
        check("pushdown: nothing suppressed yet",
                Gap3Metrics.getInstance().getPushdownMessagesSuppressed() == 0);

        deviceLeader.setReading(80f); // bit still true -> must be suppressed
        pump(managers, 5);
        check("pushdown: reading=80 (bit unchanged) -> condition still satisfied",
                routineLeader.isConditionSatisfied(cond));
        check("pushdown: still only 1 message sent (second push suppressed)",
                Gap3Metrics.getInstance().getPushdownMessagesSent() == 1);
        check("pushdown: exactly 1 suppression recorded",
                Gap3Metrics.getInstance().getPushdownMessagesSuppressed() == 1);

        deviceLeader.setReading(20f); // bit flips to false -> must send again
        pump(managers, 5);
        check("pushdown: reading=20 -> condition NOT satisfied", !routineLeader.isConditionSatisfied(cond));
        check("pushdown: second message sent after bit flip",
                Gap3Metrics.getInstance().getPushdownMessagesSent() == 2);

        System.out.println("\nAll Gap 3 live-integration checks passed.");
        System.exit(0);
    }
}
