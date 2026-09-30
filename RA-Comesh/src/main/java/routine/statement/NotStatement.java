package routine.statement;

public class NotStatement extends RoutineStatement {
    RoutineStatement innerStatement;

    public NotStatement(RoutineStatement statement) {
        this.innerStatement = statement.negate();
    }

    @Override
    public RoutineStatement negate() {
        return innerStatement.negate();
    }

    public RoutineStatement getInnerStatement() {
        return innerStatement;
    }

    @Override
    public String toString() {
        return innerStatement.toString();
    }
}
