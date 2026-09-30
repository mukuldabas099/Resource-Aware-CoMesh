package routine.command;

public class Command extends Subroutine {
    // this class contains a single command
    private RoutineCommand command;

    public Command(RoutineCommand command) {
        this.command = command;
    }

    public RoutineCommand getCommand() {
        return command;
    }
}
