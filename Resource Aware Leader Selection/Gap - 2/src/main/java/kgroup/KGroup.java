package kgroup;

import java.io.FileWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import kgroup.state.*;
import network.message.Message;

public class KGroup {
    // entitiesIDs refers to the ID(s) of the entities (device(s) or routine(s))
    // this k-group is responsible for
    // membershipList the IDs of all the nodes in the network with an Online/Offline
    // status
    // oldNodesIDs and curNodesIDs refer to the IDs of the nodes that are members of
    // the old and current k-group respectively
    // failedNodesIDs consists of the IDs of nodes that have been detected as failed
    // type refers to whether the k-group is in charge of device(s) or routine(s)
    // K is the minimum number of nodes that are members of the k-group
    // N is the number of all nodes in the network

    public List<String> entitiesIDs, oldNodesIDs, curNodesIDs, failedNodesIDs;
    protected String curKGroupLeaderNodeID, oldKGroupLeaderNodeID;
    public KGroupType type;
    protected List<Message> waitingMessageQueue;
    protected Map<String, Object> state;
    protected int F, K, N, countReceivedLocalStates, countFailedNodes, epochNo;
    public int myID;
    protected boolean receivingEndOpen;
    protected LeaderElectionPolicy electionPolicy;
    public LeaderElectionStage electionStage;
    public StateTransferStage stateTransferStage;
    public int leaderElectionTime, stateTransferTime;
    public FileWriter outputWriter;
    protected static int id = 0;

    public KGroup(List<String> entitiesIDs, Map<String, Membership> membershipList, KGroupType type, int F, int K,
            int epochNo, LeaderElectionPolicy lep) {
        this.entitiesIDs = new ArrayList<String>(entitiesIDs);
        N = membershipList.size();
        this.type = type;
        this.F = F;
        if (K < 4 * F + 1) {
            System.out.println("K cannot be less than 4 * F + 1!");
            System.exit(-1);
        }
        this.K = K;
        this.epochNo = epochNo;

        oldNodesIDs = null;
        oldKGroupLeaderNodeID = null;
        curNodesIDs = new CopyOnWriteArrayList<String>();
        curKGroupLeaderNodeID = null;
        failedNodesIDs = new CopyOnWriteArrayList<String>();
        receivingEndOpen = false;
        electionPolicy = lep;
        electionStage = LeaderElectionStage.NOT_STARTED;
        stateTransferStage = StateTransferStage.NOT_STARTED;
        countReceivedLocalStates = 0;
        waitingMessageQueue = new ArrayList<Message>();
        countFailedNodes = 0;
        myID = id++;
    }

    public String hash(String nodeID, int epochNo, int N) {
        String value = nodeID + type.toString() + entitiesIDs.toString() + String.valueOf(epochNo);
        String sha1 = "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.reset();
            digest.update(value.getBytes("utf8"));
            sha1 = String.format("%040x", new BigInteger(1, digest.digest()));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return sha1;
    }

    // returns the nodes that should be added to the k-group based on the
    // recruitement strategy
    public List<String> newKGroupMembers(Map<String, Membership> membershipList, int desired) {
        int N = 0;
        for (Membership membership : membershipList.values()) {
            if (membership == Membership.ONLINE) {
                N++;
            }
        }
        SortedMap<String, String> hashes = new TreeMap<>();
        for (String nodeID : membershipList.keySet()) {
            if (membershipList.get(nodeID) == Membership.ONLINE
                    && (desired >= K || (curNodesIDs != null && !curNodesIDs.contains(nodeID)))) {
                hashes.put(hash(nodeID, epochNo, N), nodeID);
            }
        }
        if (desired > hashes.size()) {
            System.out.println("\nNode hashes collide!!!!!!!\n");
        }
        int c = 0;
        List<String> newMembers = new ArrayList<>();
        for (String nodeID : hashes.values()) {
            if (c < desired) {
                newMembers.add(nodeID);
                c++;
            } else {
                break;
            }
        }
        return newMembers;
    }

    public void updateKGroup(Map<String, Membership> membershipList) {
        epochNo++;
        oldNodesIDs = curNodesIDs;
        oldKGroupLeaderNodeID = curKGroupLeaderNodeID;
        curNodesIDs = new CopyOnWriteArrayList<>(newKGroupMembers(membershipList, K));
        curKGroupLeaderNodeID = null;
        electionStage = LeaderElectionStage.NOT_STARTED;
        stateTransferStage = StateTransferStage.NOT_STARTED;
    }

    public List<String> getDifferentNodes() {
        List<String> result = new ArrayList<>();
        for (String nodeID : curNodesIDs) {
            if (!oldNodesIDs.contains(nodeID)) {
                result.add(nodeID);
            }
        }

        return result;
    }

    public void updateFailedNodes(String failedNodeID) {
        if (curNodesIDs.contains(failedNodeID)) {
            curNodesIDs.remove(failedNodeID);
            countFailedNodes++;
            if (curKGroupLeaderNodeID.equals(failedNodeID)) {
                oldKGroupLeaderNodeID = failedNodeID;
                curKGroupLeaderNodeID = null;
                electionStage = LeaderElectionStage.NOT_STARTED;
            }
        }
    }

    public int getCountFailedNodes() {
        return countFailedNodes;
    }

    public List<String> replaceFailedNodes(Map<String, Membership> membershipList) {
        failedNodesIDs.clear();
        return newKGroupMembers(membershipList, F);
    }

    public void nodeRecruited(String newMemberNodeID) {
        curNodesIDs.add(newMemberNodeID);
    }

    public void resetCountFailedNodes() {
        countFailedNodes = 0;
    }

    public String chooseLeader(boolean oldKGroup) {
        List<String> nodesIDs = oldKGroup ? oldNodesIDs : curNodesIDs;
        int epoch = epochNo - (oldKGroup ? 1 : 0);
        switch (electionPolicy) {
            case SMALLEST_HASH:
                SortedMap<String, String> hashes = new TreeMap<>();
                for (String nodeID : nodesIDs) {
                    hashes.put(hash(nodeID, epoch, N), nodeID);
                }
                return hashes.get(hashes.firstKey());
            case SMALLEST_ID:
                return Collections.min(nodesIDs);
            case CENTRAL_NODE:
            default:
                return null;
        }
    }

    public boolean moreAppr(String nodeID1, String nodeID2) {
        switch (electionPolicy) {
            case SMALLEST_HASH:
                return hash(nodeID1, epochNo, N).compareTo(hash(nodeID2, epochNo, N)) < 0;
            case SMALLEST_ID:
                return nodeID1.compareTo(nodeID2) < 0;
            case CENTRAL_NODE:
            default:
                return false;
        }
    }

    public void updateLeader(String newLeaderNodeID) {
        curKGroupLeaderNodeID = newLeaderNodeID;
    }

    public String getLeader() {
        return curKGroupLeaderNodeID;
    }

    public String getOldLeader() {
        return oldKGroupLeaderNodeID;
    }

    public String getMostProbableLeader() {
        if (curKGroupLeaderNodeID != null) {
            return curKGroupLeaderNodeID;
        }
        return chooseLeader(false);
    }

    public String getMostProbableOldLeader() {
        if (oldKGroupLeaderNodeID != null) {
            return oldKGroupLeaderNodeID;
        }
        return chooseLeader(true);
    }

    public int ascNodeOrder(String nodeID) {
        Collections.sort(curNodesIDs);
        int order = 0;
        for (String member : curNodesIDs) {
            if (member.equals(nodeID)) {
                return order;
            }
            order++;
        }
        return -1;
    }

    public List<String> nodesBefore(String nodeID) {
        Collections.sort(curNodesIDs);
        List<String> lowerNodesIDs = new ArrayList<>();
        for (String curNodeID : curNodesIDs) {
            if (curNodeID.compareTo(nodeID) >= 0) {
                break;
            }
            lowerNodesIDs.add(curNodeID);
        }
        return lowerNodesIDs;
    }

    public Map<String, Object> getState() {
        return state;
    }

    public void setState(Map<String, Object> state) {
        this.state = state;
    }

    // called by new leader
    public void addLocalState(Map<String, Object> localState) {
        countReceivedLocalStates++;
        for (String entityID : entitiesIDs) {
            switch (type) {
                case DEVICE:
                    ((DeviceLock) state.get(entityID)).addLocalState(((DeviceLock) localState.get(entityID)), F);
                    break;

                case ROUTINE:
                    for (Integer routineSeqNo : ((Map<Integer, RoutineStage>) localState.get(entityID)).keySet()) {
                        ((RoutineStage) ((Map<Integer, RoutineStage>) state.get(entityID)).get(routineSeqNo))
                                .addLocalState(((RoutineStage) localState.get(entityID)), F);
                    }
            }
        }
    }

    public void addLocalSeqNos(Map<String, Integer> localSeqNos) {
    }

    public int getCountReceivedLocalStates() {
        return countReceivedLocalStates;
    }

    public boolean isReceivingEndOpen() {
        return receivingEndOpen;
    }

    public void openReceivingEnd() {
        receivingEndOpen = true;
    }

    public void closeReceivingEnd() {
        receivingEndOpen = false;
    }

    public void addToWaitingMessageQueue(Message msg) {
        waitingMessageQueue.add(msg);
    }

    public List<Message> getWaitingMessageQueue() {
        return waitingMessageQueue;
    }

    public void setWaitingMessageQueue(List<Message> unprocessedMessages) {
        waitingMessageQueue = unprocessedMessages;
    }

    public void setSeqNos(Map<String, Integer> seqNos) {
    }

    public Map<String, Integer> getSeqNos() {
        return null;
    }

    public int size() {
        return K;
    }

    public boolean isMemberOfKGroup(String nodeID) {
        return curNodesIDs.contains(nodeID);
    }

    public boolean isInChargeOf(String entityID) {
        return entitiesIDs.contains(entityID);
    }

    public boolean isInChargeOf(List<String> entitiesIDs) {
        return this.entitiesIDs.containsAll(entitiesIDs);
    }

    public String toString() {
        return type + " k-group in charge of " + entitiesIDs + " with members " + curNodesIDs + " and leader "
                + curKGroupLeaderNodeID;
    }
}
