package routine.command;

import java.util.ArrayList;
import java.util.List;

public abstract class Subroutine {
    public List<String> getTouchedDevicesIDs() {
        List<String> touchedDevicesIDs = new ArrayList<>();

        if (this instanceof Command) {
            touchedDevicesIDs.add(((Command) this).getCommand().getDeviceID());
        } else if (this instanceof SequenceOfCommands) {
            for (Subroutine sub : ((SequenceOfCommands) this).getCommands()) {
                touchedDevicesIDs.addAll(sub.getTouchedDevicesIDs());
            }
        } else if (this instanceof SetOfCommands) {
            for (Subroutine sub : ((SetOfCommands) this).getCommands()) {
                touchedDevicesIDs.addAll(sub.getTouchedDevicesIDs());
            }
        }

        return touchedDevicesIDs;
    }
}
