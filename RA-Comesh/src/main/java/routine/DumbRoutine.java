package routine;

import java.util.List;

public class DumbRoutine extends Routine {
    private boolean triggered;

    public DumbRoutine(List<String> touchedDevicesIDs, int length) {
        super(touchedDevicesIDs, length);
        triggered = false;
    }

    public DumbRoutine(List<String> touchedDevicesIDs) {
        this.touchedDevicesIDs = touchedDevicesIDs;
        triggered = false;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public void setTriggered() {
        triggered = true;
    }

    public void resetTriggered() {
        triggered = false;
    }
}
