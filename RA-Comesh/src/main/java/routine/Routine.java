package routine;

import java.util.ArrayList;
import java.util.List;

import kgroup.priority.PriorityLockScheduler;

public class Routine {
    protected List<String> touchedDevicesIDs;
    protected int length, seqNo;
    // A routine triggering should have a sequence number.
    // This way, we may support routine triggerings that happen
    // before a specific triggering's execution has finished
    // Specifically, this way, we may know if a lock request is new
    // or old and we may handle it appropriately.

    // Gap 5: Priority-Aware Lock Scheduling. Defaults to NORMAL so a
    // routine list with no priority file loaded behaves exactly like
    // before -- every routine at the same priority degenerates the
    // priority-aware scheduler back to plain FIFO (see
    // PriorityLockScheduler / GAP5_PRIORITY_AWARE_LOCK_SCHEDULING.md).
    protected int priority = PriorityLockScheduler.NORMAL_PRIORITY;

    protected Routine() {
    }

    protected Routine(List<String> touchedDevicesIDs, int length) {
        this.touchedDevicesIDs = new ArrayList<String>(touchedDevicesIDs);
        this.length = length;
        this.seqNo = -1;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<String> getTouchedDevicesIDs() {
        return touchedDevicesIDs;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public int getSeqNo() {
        return seqNo;
    }

    public int newTriggering() {
        return ++seqNo;
    }
}
