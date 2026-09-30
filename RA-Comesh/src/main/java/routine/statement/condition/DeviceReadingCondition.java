package routine.statement.condition;

public class DeviceReadingCondition extends RoutineCondition {
    public String deviceID;
    public float value;

    public DeviceReadingCondition(String deviceID, ConditionRelation relation, float value) {
        this.deviceID = deviceID;
        this.relation = relation;
        this.value = value;
    }

    @Override
    public String toString() {
        return "dev" + deviceID + relation.toString() + Float.toString(value);
    }
}
