package routine.statement.condition;

import java.time.OffsetTime;

public class TimeCondition extends RoutineCondition {
    public OffsetTime value;

    public TimeCondition(ConditionRelation relation, OffsetTime value) {
        this.relation = relation;
        this.value = value;
    }

    @Override
    public String toString() {
        return "curTime" + relation.toString() + value.toString();
    }
}
