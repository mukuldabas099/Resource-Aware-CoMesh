package routine.statement.condition;

public enum ConditionRelation {
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    LESS("<"),
    LESS_EQUAL("<=");

    private String strRepr;

    private ConditionRelation(String strRepr) {
        this.strRepr = strRepr;
    }

    @Override
    public String toString() {
        return strRepr;
    }
}
