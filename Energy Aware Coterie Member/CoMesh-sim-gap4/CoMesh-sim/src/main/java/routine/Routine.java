package routine;

import java.util.ArrayList;
import java.util.List;

public class Routine {
    protected List<String> touchedDevicesIDs;
    protected int length, seqNo;
    // A routine triggering should have a sequence number.
    // This way, we may support routine triggerings that happen
    // before a specific triggering's execution has finished
    // Specifically, this way, we may know if a lock request is new
    // or old and we may handle it appropriately.

    protected Routine() {
    }

    protected Routine(List<String> touchedDevicesIDs, int length) {
        this.touchedDevicesIDs = new ArrayList<String>(touchedDevicesIDs);
        this.length = length;
        this.seqNo = -1;
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
