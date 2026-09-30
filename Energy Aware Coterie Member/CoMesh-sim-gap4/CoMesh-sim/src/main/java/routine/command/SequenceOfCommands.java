package routine.command;

import java.util.List;

public class SequenceOfCommands extends Subroutine {
    // the commands in this class must be performed sequentially
    private List<Subroutine> commands;

    public SequenceOfCommands(List<Subroutine> commands) {
        this.commands = commands;
    }

    public List<Subroutine> getCommands() {
        return commands;
    }
}
