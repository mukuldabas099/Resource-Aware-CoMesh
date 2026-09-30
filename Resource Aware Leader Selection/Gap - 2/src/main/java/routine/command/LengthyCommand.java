package routine.command;

import java.time.Duration;

public class LengthyCommand extends RoutineCommand {
    private Duration duration;

    public LengthyCommand(String deviceID, String command, Duration duration) {
        super(deviceID, command);
        this.duration = duration;
    }

    public Duration getDuration() {
        return duration;
    }
}
