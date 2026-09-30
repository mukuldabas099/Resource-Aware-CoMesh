package kgroup;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kgroup.state.DeviceLock;
import kgroup.state.LockRequest;

public class DeviceKGroup extends KGroup {
    public DeviceKGroup(List<String> devicesIDs, Map<String, Membership> membershipList, int F, int K, int epochNo,
            LeaderElectionPolicy lep) {
        super(devicesIDs, membershipList, KGroupType.DEVICE, F, K, epochNo, lep);
        state = new HashMap<String, Object>();
        for (String deviceID : devicesIDs) {
            state.put(deviceID, new DeviceLock());
        }
    }

    // called by leader
    public boolean isLockRequestReceived(String deviceID, String routineID, int routineSeqNo) {
        return ((DeviceLock) state.get(deviceID)).isLockRequestReceived(routineID, routineSeqNo);
    }

    // called by leader
    public boolean isLockAvailable(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).isAvailable();
    }

    // called by leader
    public boolean isLocked(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).isLocked();
    }

    // called by leader
    public int getQueueHeadReqSeqNo(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getQueueHeadReqSeqNo();
    }

    // called by leader
    public LockRequest getQueueHead(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getQueueHead();
    }

    public int getLockQueueSize(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getQueueSize();
    }

    // called by leader
    public int getLockerReqSeqNo(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getLockerReqSeqNo();
    }

    // called by leader
    public Map<Integer, LockRequest> getNewLockRequests(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getNewLockRequests();
    }

    // called by leader
    public LockRequest getLocker(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getLocker();
    }

    // called by leader
    public boolean isLockAcquired(String deviceID, String routineID, int routineSeqNo) {
        return ((DeviceLock) state.get(deviceID)).isAcquired(routineID, routineSeqNo);
    }

    // called by leader
    public boolean isLockQueueEmpty(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).isQueueEmpty();
    }

    // called by leader
    public boolean isNewLockRequestsEmpty(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).isNewLockRequestsEmpty();
    }

    // called by leader
    public boolean isLockRequestGranted(String deviceID, String routineID, int routineSeqNo) {
        return ((DeviceLock) state.get(deviceID)).isLockRequestGranted(routineID, routineSeqNo);
    }

    // called by leader
    public int addNewLockRequest(String deviceID, String routineID, int routineSeqNo) {
        return ((DeviceLock) state.get(deviceID)).addNewRequest(routineID, routineSeqNo);
    }

    // called by leader
    public int getLockRequestSeqNo(String deviceID, String routineID, int routineSeqNo) {
        return ((DeviceLock) state.get(deviceID)).getLockRequestSeqNo(routineID, routineSeqNo);
    }

    // called by leader
    public int getLastLockedReqSeqNo(String deviceID) {
        return ((DeviceLock) state.get(deviceID)).getLastLockedReqSeqNo();
    }

    // called by a non-leader upon request reception from the leader
    public void replicateLockRequest(String deviceID, String routineID, int routineSeqNo, int reqSeqNo) {
        ((DeviceLock) state.get(deviceID)).replicateRequest(new LockRequest(routineID, routineSeqNo), reqSeqNo);
    }

    // called by leader
    public void lockRequestReplicated(String deviceID, String routineID, int routineSeqNo, int reqSeqNo) {
        ((DeviceLock) state.get(deviceID)).requestReplicated(new LockRequest(routineID, routineSeqNo), reqSeqNo);
    }

    // called by non-leader
    public void replicateLockAcquisition(String deviceID, String routineID, int routineSeqNo, int reqSeqNo) {
        ((DeviceLock) state.get(deviceID)).replicateLock(new LockRequest(routineID, routineSeqNo), reqSeqNo);
    }

    // called by leader
    public void lock(String deviceID, String routineID, int routineSeqNo, int reqSeqNo) {
        ((DeviceLock) state.get(deviceID)).lock(new LockRequest(routineID, routineSeqNo), reqSeqNo);
    }

    // called by non-leader
    public void replicateLockRelease(String deviceID, String routineID, int routineSeqNo, int reqSeqNo) {
        ((DeviceLock) state.get(deviceID)).replicateRelease(new LockRequest(routineID, routineSeqNo), reqSeqNo);
    }

    // called by leader
    public LockRequest releaseLock(String deviceID, String routineID, int routineSeqNo, int reqSeqNo) {
        return ((DeviceLock) state.get(deviceID)).release(new LockRequest(routineID, routineSeqNo), reqSeqNo);
    }

    @Override
    public void setState(Map<String, Object> state) {
        this.state = new HashMap<String, Object>();
        for (String deviceID : state.keySet()) {
            this.state.put(deviceID, new DeviceLock((DeviceLock) state.get(deviceID)));
        }
    }
}
