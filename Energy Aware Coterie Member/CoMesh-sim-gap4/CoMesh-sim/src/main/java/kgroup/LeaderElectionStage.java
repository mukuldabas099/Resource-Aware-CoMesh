package kgroup;

public enum LeaderElectionStage {
    NOT_STARTED,
    ONGOING,
    WAITING_ON_ELECTION_ACK,
    WAITING_ON_ELECTED,
    COMPLETE;
}
