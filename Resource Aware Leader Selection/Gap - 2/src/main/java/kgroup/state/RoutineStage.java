package kgroup.state;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Have a different RoutineStage instance for each routine triggering
public class RoutineStage extends KGroupState {
    private static final long serialVersionUID = 3882050344443438437L;

    private String latestLockedDeviceID;
    private RoutineStageType stage;
    private int endTS;
    private List<String> unreleasedDevicesIDs;

    private List<String> latestLockedDeviceIDRecreation;
    private List<RoutineStageType> stageRecreation;

    public RoutineStage() {
        latestLockedDeviceID = null;
        stage = RoutineStageType.ACQUIRING_LOCKS;
        endTS = -1;

        latestLockedDeviceIDRecreation = new ArrayList<String>();
        stageRecreation = new ArrayList<RoutineStageType>();
    }

    public RoutineStage(RoutineStage routineStage) {
        latestLockedDeviceID = routineStage.latestLockedDeviceID;
        stage = routineStage.stage;
        endTS = routineStage.endTS;

        latestLockedDeviceIDRecreation = new ArrayList<String>();
        stageRecreation = new ArrayList<RoutineStageType>();
    }

    public String nextDeviceToLock(List<String> touchedDevicesIDs) {
        Collections.sort(touchedDevicesIDs);
        System.out.println(touchedDevicesIDs + ", last locked: " + latestLockedDeviceID);
        if (latestLockedDeviceID == null) {
            return touchedDevicesIDs.get(0);
        } else if (latestLockedDeviceID == Collections.max(touchedDevicesIDs)) {
            stage = RoutineStageType.ACQUIRED_LOCKS;
            return null;
        } else {
            for (String deviceID : touchedDevicesIDs) {
                if (deviceID.compareTo(latestLockedDeviceID) > 0) {
                    return deviceID;
                }
            }
        }
        return null;
    }

    public boolean acquiredDeviceLock(String deviceID) {
        if (latestLockedDeviceID == null || deviceID.compareTo(latestLockedDeviceID) > 0) {
            latestLockedDeviceID = deviceID;
            return true;
        }
        return false;
    }

    public void acquiredAllDeviceLocks() {
        stage = RoutineStageType.ACQUIRED_LOCKS;
    }

    public void startExecution(int endTS) {
        stage = RoutineStageType.EXECUTING;
        this.endTS = endTS;
    }

    public void startReleasingLocks(List<String> touchedDevicesIDs) {
        stage = RoutineStageType.RELEASING_LOCKS;
        unreleasedDevicesIDs = new ArrayList<>(touchedDevicesIDs);
    }

    public void releasedLock(String deviceID) {
        unreleasedDevicesIDs.remove(deviceID);
    }

    public List<String> getUnreleasedDevicesIDs() {
        return unreleasedDevicesIDs;
    }

    public boolean isAcquiringLocks() {
        return stage == RoutineStageType.ACQUIRING_LOCKS;
    }

    public String getLatestLockedDeviceID() {
        return latestLockedDeviceID;
    }

    public boolean areLocksAcquired() {
        return stage == RoutineStageType.ACQUIRED_LOCKS;
    }

    public boolean isExecuting() {
        return stage == RoutineStageType.EXECUTING;
    }

    public int getEndTS() {
        return endTS;
    }

    public boolean isReleasingLocks() {
        return stage == RoutineStageType.RELEASING_LOCKS;
    }

    public void addLocalState(RoutineStage localState, int f) {
        if (latestLockedDeviceID != localState.latestLockedDeviceID) {
            latestLockedDeviceIDRecreation.add(localState.latestLockedDeviceID);
            if (Collections.frequency(latestLockedDeviceIDRecreation, localState.latestLockedDeviceID) >= 2 * f + 1) {
                latestLockedDeviceID = localState.latestLockedDeviceID;
            }
        }
        if (stage != localState.stage) {
            stageRecreation.add(localState.stage);
            if (Collections.frequency(stageRecreation, localState.stage) >= 2 * f + 1) {
                stage = localState.stage;
            }
        }
    }

    public String toString() {
        return "Stage: " + stage + ", latest locked device ID: " + latestLockedDeviceID;
    }
}
