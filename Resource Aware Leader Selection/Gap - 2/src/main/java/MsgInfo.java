public class MsgInfo {
    public boolean boundedWait;
    public int routeOWD, initialSendTS, lastSendTS, initialEpoch;

    public MsgInfo(boolean boundedWait, int routeOWD, int initialSendTS, int initialEpoch) {
        this.boundedWait = boundedWait;
        this.routeOWD = routeOWD;
        this.initialSendTS = this.lastSendTS = initialSendTS;
        this.initialEpoch = initialEpoch;
    }

    public int getRouteRTT() {
        return routeOWD * 2;
    }
}