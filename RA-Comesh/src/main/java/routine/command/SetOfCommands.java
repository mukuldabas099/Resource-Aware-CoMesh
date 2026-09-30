package routine.command;

import java.util.List;

public class SetOfCommands extends Subroutine {
    // the commands in this class can be performed in parallel
    private List<Subroutine> commands;

    public SetOfCommands(List<Subroutine> commands) {
        this.commands = commands;
    }

    public List<Subroutine> getCommands() {
        return commands;
    }
}
