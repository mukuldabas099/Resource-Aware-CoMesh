package routine.command;

public class RoutineCommand {
    private String deviceID;
    private String command;

    public RoutineCommand(String deviceID, String command) {
        this.deviceID = deviceID;
        this.command = command;
    }

    public String getDeviceID() {
        return deviceID;
    }

    public String getCommand() {
        return command;
    }
}
