package routine.statement;

import routine.statement.condition.RoutineCondition;

public class Statement extends RoutineStatement {
    RoutineCondition condition;

    public Statement(RoutineCondition condition) {
        this.condition = condition;
    }

    @Override
    public RoutineStatement negate() {
        return new Statement(condition.invertRelation());
    }

    public RoutineCondition getCondition() {
        return condition;
    }

    @Override
    public String toString() {
        return condition.toString();
    }
}
