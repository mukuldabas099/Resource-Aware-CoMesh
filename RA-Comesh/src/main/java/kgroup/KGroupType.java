package kgroup;

public enum KGroupType {
    DEVICE("device(s)"),
    ROUTINE("routine(s)");

    private String type;

    private KGroupType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return type;
    }
}
