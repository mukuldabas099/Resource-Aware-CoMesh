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

import kgroup.energy.Gap4Metrics;
import kgroup.energy.NodeEnergyRegistry;
import kgroup.resource.Gap2Metrics;
import kgroup.sleep.DeviceSleepRegistry;
import kgroup.sleep.Gap7Metrics;
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
            boolean eligible;
            Membership membership = membershipList.get(nodeID);
            if (membership == Membership.ONLINE) {
                eligible = (desired >= K || (curNodesIDs != null && !curNodesIDs.contains(nodeID)));
            } else if (DeviceSleepRegistry.isEnabled() && membership == Membership.SLEEPING
                    && curNodesIDs != null && curNodesIDs.contains(nodeID)) {
                // Gap 7: an announced (planned) sleep is not a failure. As
                // long as this node is already an incumbent member of this
                // k-group, keep it eligible for re-selection this epoch
                // instead of evicting it purely because it is temporarily
                // unreachable -- it is expected to wake on its own before
                // its next scheduled duty. (A SLEEPING node that is NOT
                // already a member is still ineligible: it can't usefully
                // take on brand-new coterie duty while asleep.)
                eligible = true;
                Gap7Metrics.getInstance().recordCoterieRetention();
            } else {
                eligible = false;
            }
            if (eligible) {
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

        if (NodeEnergyRegistry.isEnabled() && !newMembers.isEmpty()) {
            applyEnergyConstraint(newMembers, hashes, desired);
        }

        return newMembers;
    }

    /**
     * Gap 4: Energy-Aware Coterie Membership.
     *
     * The locality-sensitive hash ranking above ("hashes") optimizes for
     * locality only, so it can, by chance, place an entirely low-energy
     * bucket into a coterie. This adds an energy constraint on top of that
     * locality-only selection: if the bucket it picked contains zero
     * energy-stable devices, the lowest-hashed (best-locality) *stable*
     * candidate outside the bucket is swapped in for the worst-hashed
     * member already selected, guaranteeing at least one energy-stable
     * device is present -- without otherwise disturbing the
     * locality-driven choice of every other member.
     *
     * "hashes" is already ascending by hash value (TreeMap natural
     * ordering of the SHA-1 hex strings), so "newMembers" is exactly the
     * lowest-hashed "desired" candidates and the last element is the
     * worst-hashed (highest-hash) member actually selected.
     */
    private void applyEnergyConstraint(List<String> newMembers, SortedMap<String, String> hashes, int desired) {
        boolean alreadyStable = false;
        for (String nodeID : newMembers) {
            if (NodeEnergyRegistry.isEnergyStable(nodeID)) {
                alreadyStable = true;
                break;
            }
        }
        if (alreadyStable) {
            Gap4Metrics.getInstance().recordFormation(false, false);
            return;
        }

        String replacement = null;
        for (String candidateNodeID : hashes.values()) {
            if (newMembers.contains(candidateNodeID)) {
                continue;
            }
            if (NodeEnergyRegistry.isEnergyStable(candidateNodeID)) {
                replacement = candidateNodeID;
                break;
            }
        }

        if (replacement == null) {
            // Every online candidate for this k-group is low-energy: the
            // energy constraint is infeasible for this formation, leave
            // the locality-only bucket untouched.
            Gap4Metrics.getInstance().recordFormation(false, true);
            return;
        }

        newMembers.remove(newMembers.size() - 1);
        newMembers.add(replacement);
        Gap4Metrics.getInstance().recordFormation(true, false);
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
                if (NodeEnergyRegistry.isLeaderElectionEnabled()) {
                    return chooseResourceAwareLeader(hashes);
                }
                return hashes.get(hashes.firstKey());
            case SMALLEST_ID:
                return Collections.min(nodesIDs);
            case CENTRAL_NODE:
            default:
                return null;
        }
    }

    /**
     * Gap 2: Resource-Aware Leader (and Member) Selection.
     *
     * "hashes" is ascending by hash value (TreeMap natural ordering of
     * the SHA-1 hex strings), so this walks candidates from lowest-hashed
     * to highest-hashed and elects the first one that is resource-stable
     * (per NodeEnergyRegistry.isEnergyStable, using the exact same
     * battery/CPU profiles as Gap 4's coterie-membership constraint) --
     * i.e. "the lowest-hashed *eligible* device becomes leader, rather
     * than the lowest-hashed device overall" (Gap 2's Proposed
     * Direction). If none of the candidates in this k-group have a
     * resource-stable profile, the constraint is infeasible for this
     * election and this falls back, unchanged, to the original
     * lowest-hash-wins behavior (fail open, same convention as Gap 4).
     */
    private String chooseResourceAwareLeader(SortedMap<String, String> hashes) {
        String lowestHashNode = hashes.get(hashes.firstKey());
        for (String nodeID : hashes.values()) {
            if (NodeEnergyRegistry.isEnergyStable(nodeID)) {
                Gap2Metrics.getInstance().recordElection(!nodeID.equals(lowestHashNode), false);
                return nodeID;
            }
        }
        Gap2Metrics.getInstance().recordElection(false, true);
        return lowestHashNode;
    }

    public boolean moreAppr(String nodeID1, String nodeID2) {
        switch (electionPolicy) {
            case SMALLEST_HASH:
                // Gap 2: keep the bully-election "who's more appropriate to
                // lead" comparison consistent with chooseResourceAwareLeader
                // above -- otherwise the bully protocol (triggered on
                // leader failure) could elect a different node than
                // chooseLeader()/getMostProbableLeader() would have picked
                // at the next epoch boundary. A resource-stable node is
                // always more appropriate than an unstable one; among two
                // nodes with the same stability, fall back to the original
                // lowest-hash-wins comparison unchanged.
                if (NodeEnergyRegistry.isLeaderElectionEnabled()) {
                    boolean stable1 = NodeEnergyRegistry.isEnergyStable(nodeID1);
                    boolean stable2 = NodeEnergyRegistry.isEnergyStable(nodeID2);
                    if (stable1 != stable2) {
                        return stable1;
                    }
                }
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
