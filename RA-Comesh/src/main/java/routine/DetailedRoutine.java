package routine;

import routine.command.Subroutine;
import routine.statement.RoutineStatement;

public class DetailedRoutine extends Routine {
    private RoutineStatement conditions;
    private Subroutine commands;

    public DetailedRoutine(RoutineStatement conditions, Subroutine commands) {
        // if ever we want to support actual commands, set length based on the lengths
        // of the individual commands
        super(commands.getTouchedDevicesIDs(), 0);
        this.conditions = conditions;
        this.commands = commands;
    }

    public RoutineStatement getConditions() {
        return conditions;
    }

    public Subroutine getCommands() {
        return commands;
    }
}
