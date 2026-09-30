package kgroup;

public enum LeaderElectionPolicy {
    SMALLEST_ID("sID"),
    SMALLEST_HASH("sh"),
    CENTRAL_NODE("cn");

    public String name;

    private LeaderElectionPolicy(String name) {
        this.name = name;
    }
}
