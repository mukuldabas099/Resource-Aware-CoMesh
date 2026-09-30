package kgroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kgroup.state.RoutineStage;

public class RoutineKGroup extends KGroup {
    private Map<String, Integer> seqNos;

    public RoutineKGroup(Map<String, List<String>> touchedDevicesIDs, Map<String, Membership> membershipList, int F,
            int K, int epochNo, LeaderElectionPolicy lep) {
        super((List<String>) new ArrayList<String>(touchedDevicesIDs.keySet()), membershipList, KGroupType.ROUTINE, F,
                K, epochNo, lep);
        state = new HashMap<String, Object>();
        seqNos = new HashMap<String, Integer>();
        for (String routineID : touchedDevicesIDs.keySet()) {
            state.put(routineID, new HashMap<Integer, RoutineStage>());
            seqNos.put(routineID, 0);
        }
    }

    // called by routine k-group leader
    public Set<Integer> getRoutineSeqNos(String routineID) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).keySet();

    }

    // called by routine k-group leader
    @Override
    public Map<String, Integer> getSeqNos() {
        return seqNos;

    }

    // called by routine k-group leader
    @Override
    public void setSeqNos(Map<String, Integer> seqNos) {
        seqNos = new HashMap<String, Integer>();
        seqNos.putAll(seqNos);
    }

    // called by routine k-group leader
    @Override
    public void addLocalSeqNos(Map<String, Integer> localSeqNos) {
        for (String routineID : entitiesIDs) {
            if (localSeqNos.get(routineID) > seqNos.get(routineID)) {
                seqNos.put(routineID, localSeqNos.get(routineID));
            }
        }
    }

    // called by routine k-group leader
    public int setRoutineTriggered(String routineID) {
        // assign a sequance number to the new routine triggering, and return it later
        seqNos.put(routineID, seqNos.get(routineID) + 1);
        // create a new RoutineStage instance for the new triggering
        ((HashMap<Integer, RoutineStage>) state.get(routineID)).put(seqNos.get(routineID), new RoutineStage());
        return seqNos.get(routineID);
    }

    // called by non-leader routine k-group member
    public int setRoutineTriggered(String routineID, int routineSeqNo) {
        // update the routine's sequence number
        seqNos.put(routineID, routineSeqNo);
        // create a new RoutineStage instance for the new triggering
        ((HashMap<Integer, RoutineStage>) state.get(routineID)).put(routineSeqNo, new RoutineStage());
        // System.out.println("Routine " + routineID + ": triggered, seqNo: " +
        // seqNos.get(routineID));
        return seqNos.get(routineID);
    }

    public String nextDeviceToLock(String routineID, int routineSeqNo, List<String> touchedDevicesIDs) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo)
                .nextDeviceToLock(touchedDevicesIDs);
    }

    public boolean acquiredDeviceLockForRoutine(String routineID, int routineSeqNo, String deviceID,
            List<String> touchedDevicesIDs) {
        boolean rv = ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo)
                .acquiredDeviceLock(deviceID);

        if (rv && deviceID.equals(Collections.max(touchedDevicesIDs))) {
            ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).acquiredAllDeviceLocks();
        }

        return rv;
    }

    public void startRoutineExecution(String routineID, int routineSeqNo, int endTS) {
        ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).startExecution(endTS);
    }

    public void startReleasingRoutineLocks(String routineID, int routineSeqNo, List<String> touchedDevicesIDs) {
        ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo)
                .startReleasingLocks(touchedDevicesIDs);
    }

    public boolean isRoutineAcquiringLocks(String routineID, int routineSeqNo) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).isAcquiringLocks();
    }

    public boolean areRoutineLocksAcquired(String routineID, int routineSeqNo) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).areLocksAcquired();
    }

    public boolean isRoutineExecuting(String routineID, int routineSeqNo) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).isExecuting();
    }

    public int getRoutineEndTS(String routineID, int routineSeqNo) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).getEndTS();
    }

    public boolean isRoutineReleasingLocks(String routineID, int routineSeqNo) {
        return ((HashMap<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo).isReleasingLocks();
    }

    @Override
    public void setState(Map<String, Object> state) {
        this.state = new HashMap<String, Object>();
        for (String routineID : state.keySet()) {
            this.state.put(routineID, new HashMap<Integer, RoutineStage>());
            for (Integer routineSeqNo : ((Map<Integer, RoutineStage>) state.get(routineID)).keySet()) {
                ((Map<Integer, RoutineStage>) this.state.get(routineID)).put(routineSeqNo, new RoutineStage(
                        (RoutineStage) ((Map<Integer, RoutineStage>) state.get(routineID)).get(routineSeqNo)));
            }
        }
    }
}
