package routine.statement.condition;

public class DeviceStateCondition extends RoutineCondition {
    public String deviceID;
    public boolean value;

    public DeviceStateCondition(String deviceID, ConditionRelation relation, boolean value) {
        this.deviceID = deviceID;
        this.relation = relation;
        this.value = value;
    }

    @Override
    public String toString() {
        String valueStr;
        if (value)
            valueStr = "on";
        else
            valueStr = "off";

        return "dev" + deviceID + relation.toString() + valueStr;
    }
}
