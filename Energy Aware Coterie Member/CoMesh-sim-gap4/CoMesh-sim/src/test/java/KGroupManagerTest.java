import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import kgroup.LeaderElectionPolicy;
import network.Network;
import routine.DumbRoutine;
import routine.Routine;

public class KGroupManagerTest {
    @Test
    public void shouldReturnCorrectChargeStatus() {
        int deviceNo = 9, routineNo = 9, deviceKGroupRange = 1, routineKGroupRange = 1, hopOWD = 1;
        Network network = new Network("workloads/sample_node_topology.txt", hopOWD, false);
        List<String> devicesIDs = new ArrayList<>();
        for (int i = 0; i<deviceNo; i++) {
            devicesIDs.add(String.valueOf(i));
        }
        Map<String, Routine> routines = new HashMap<>();
        for (int i=0; i<routineNo; i++) {
            if (i <= (i + deviceNo / routineNo) % deviceNo) {
                routines.put(String.valueOf(i), new DumbRoutine(devicesIDs.subList(i, (i + deviceNo / routineNo) % deviceNo)));
            }
            else {
                List<String> touchedDevicesIDs = new ArrayList<>();
                touchedDevicesIDs.addAll(devicesIDs.subList(0, (i + deviceNo / routineNo) % deviceNo));
                touchedDevicesIDs.addAll(devicesIDs.subList(i, deviceNo));
                routines.put(String.valueOf(i), new DumbRoutine(touchedDevicesIDs));
            }
        }
        KGroupManager kGroupManager = new KGroupManager("0", 1, 5, 50, 10, devicesIDs, devicesIDs, routines, deviceKGroupRange, routineKGroupRange, network, 0, 0, new TreeMap<Integer, List<Event>>(), null, true, null, LeaderElectionPolicy.SMALLEST_HASH);

        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add("5");
        assertEquals(entitiesIDs, kGroupManager.deviceKGroupInChargeOf(entitiesIDs).entitiesIDs);
    }

    @Test
    public void electionTest() {
        int F = 1, K = 4 * F + 1, epochLength = 1 * 15*1000 /*time units*/, routineMonitorPeriod = 5000, deviceNo = 9, routineNo = 9, deviceKGroupRange = 1, routineKGroupRange = 1, hopOWD = 1;
        
        List<String> devicesIDs = new ArrayList<>();
        for (int i = 0; i<deviceNo; i++) {
            devicesIDs.add(String.valueOf(i));
        }
        
        Map<String, Routine> routines = new HashMap<>();
        for (int i=0; i<routineNo; i++) {
            if (i <= (i + deviceNo / routineNo) % deviceNo) {
                routines.put(String.valueOf(i), new DumbRoutine(devicesIDs.subList(i, (i + deviceNo / routineNo) % deviceNo)));
            }
            else {
                List<String> touchedDevicesIDs = new ArrayList<>();
                touchedDevicesIDs.addAll(devicesIDs.subList(0, (i + deviceNo / routineNo) % deviceNo));
                touchedDevicesIDs.addAll(devicesIDs.subList(i, deviceNo));
                routines.put(String.valueOf(i), new DumbRoutine(touchedDevicesIDs));
            }
        }

        Network network = new Network("workloads/sample_node_topology.txt", hopOWD, false);
        
        Map<String, KGroupManager> kGroupManagers = new HashMap<>();
        for (String nodeID: network.getMembershipList()) {
            kGroupManagers.put(nodeID, new KGroupManager(nodeID, F, K, epochLength, routineMonitorPeriod, devicesIDs, devicesIDs, routines, deviceKGroupRange, routineKGroupRange, network, 0, 0, new TreeMap<Integer, List<Event>>(), null, true, null, LeaderElectionPolicy.SMALLEST_HASH));
        }

        try {
            Thread.sleep(10);
        }
        catch (Exception e) {
            System.out.println(e);
        }

        System.out.println("Starting to remove node 4");
        network.nodeFailed("4");
        kGroupManagers.remove("4");
        for (String nodeID: network.getMembershipList()) {
            if (!nodeID.equals("4")) {
                kGroupManagers.get(nodeID).nodeFailureDetected("4", true);
            }
        }
        System.out.println("Removed node 4");

        try {
            Thread.sleep(100);
        }
        catch (Exception e) {
            System.out.println(e);
        }

        while (network.getMembershipList().size() != 0) {}
    }
}
