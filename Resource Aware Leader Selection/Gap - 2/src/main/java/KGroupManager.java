import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.OffsetTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.Map.Entry;

import kgroup.*;
import kgroup.state.*;
import network.Network;
import network.message.*;
import network.message.payload.*;
import network.message.payload.election.*;
import network.message.payload.failure.*;
import network.message.payload.lock.*;
import network.message.payload.monitor.*;
import network.message.payload.stateTransfer.*;
import network.message.payload.trigger.*;
import routine.*;
import routine.statement.*;
import routine.statement.condition.*;

public class KGroupManager {
    private boolean debug;
    private TestType testType;

    // current node's variables
    private int F, epochLength, routineMonitorPeriod;
    private String myID;
    private Integer epochNo, ts, seqNo;
    private boolean state;
    private float reading;

    private Network network;

    // current node's database of device and routine k-groups, routines,
    // device-to-routine mappings and node membership
    private List<DeviceKGroup> deviceKGroups;
    private List<RoutineKGroup> routineKGroups;
    private Map<String, Routine> routines;
    private Map<String, List<String>> devicesToRoutines;
    private Map<String, Membership> membershipList;
    // devices and routines' IDs the k-groups of which the current node is a member
    // of right now
    private List<String> currentDevicesIDs, currentRoutinesIDs;

    // list of global events per time unit
    private Map<Integer, List<Event>> events;

    // file to write output
    private String outputFilename;

    // mapping of quorums' ts to payload, counts for positive replies and related
    // k-group
    private Map<Integer, QuorumMsgInfo> quorumMsgsInfo;
    // mapping of messages to expected acks
    private Map<Integer, UnicastMsgInfo> unicastMsgsInfo;

    private Map<String, Boolean> deviceStates;
    private Map<String, Float> deviceReadings;

    // For data record
    private NodeMetrics nodeMetric;
    private RoutineMetricSingleton routineMetrics = RoutineMetricSingleton.getInstance();
    private static Map<String, List<String>> rtn_releasing_locks;
    private static int _checkpoint_counter = 0;

    // A k-group manager is maintained in every programmable node of the network
    // myID specifies the current node's ID
    // K is the number of faults a k-group can tolerate
    // epochLength is the length of an epoch in time units
    // RTT is the expected RTT until a message reply is received and is measured in
    // time units
    // nodesIDs is a list of all the programmable nodes' IDs
    // devicesIDs is a list of all the devices's IDs (including programmable nodes)
    // routines are the routine classes for every routineID
    // device/routineKGroupRange specify how many devices/routines will be handled
    // by the same k-group
    // network is the connection among all nodes/k-group managers, simulates the
    // real network
    public KGroupManager(String myID, int F, int K, int epochLength, int routineMonitorPeriod, List<String> nodesIDs,
            List<String> devicesIDs, Map<String, Routine> routines, int deviceKGroupRange, int routineKGroupRange,
            Network network, int initialTS, int endTS, SortedMap<Integer, List<Event>> events, String outputFileName,
            boolean debug, TestType testType, LeaderElectionPolicy lep) {
        this.myID = myID;
        this.F = F;
        this.epochLength = epochLength;
        this.routineMonitorPeriod = routineMonitorPeriod;
        epochNo = 0;
        ts = initialTS - 1;
        seqNo = 0;
        state = false;
        reading = 0;
        this.network = network;
        this.events = new TreeMap<Integer, List<Event>>();
        for (Map.Entry<Integer, List<Event>> e : events.entrySet()) {
            this.events.put(e.getKey(), new ArrayList<Event>());
            this.events.get(e.getKey()).addAll(e.getValue());
        }
        this.outputFilename = outputFileName;
        this.debug = debug;
        this.testType = testType;

        membershipList = new HashMap<String, Membership>();
        for (String nodeID : nodesIDs) {
            membershipList.put(nodeID, Membership.ONLINE);
        }

        // set nodes' membership as offline if they join later
        for (Integer checkTS : events.keySet()) {
            if (checkTS > initialTS && events.get(checkTS) != null) {
                for (Event e : events.get(checkTS)) {
                    if (e.getType() == EventType.NODE_JOINED) {
                        membershipList.replace(e.getAffectedEntity(), Membership.OFFLINE);
                    }
                }
            }
        }

        devicesToRoutines = new HashMap<String, List<String>>();
        for (String deviceID : devicesIDs) {
            devicesToRoutines.put(deviceID, new ArrayList<String>());
        }
        deviceKGroups = new ArrayList<DeviceKGroup>();
        nodeMetric = new NodeMetrics(myID, initialTS);
        currentDevicesIDs = new ArrayList<String>();
        DeviceKGroup newDeviceKGroup;
        for (int counter = 0; counter < devicesIDs.size(); counter += deviceKGroupRange) {
            newDeviceKGroup = new DeviceKGroup(devicesIDs.subList(counter,
                    (counter + deviceKGroupRange < devicesIDs.size()) ? (counter + deviceKGroupRange)
                            : devicesIDs.size()),
                    membershipList, F, K, epochNo, lep);
            if (newDeviceKGroup.isMemberOfKGroup(myID)) {
                currentDevicesIDs.addAll(devicesIDs.subList(counter,
                        (counter + deviceKGroupRange < devicesIDs.size()) ? (counter + deviceKGroupRange)
                                : devicesIDs.size()));
                nodeMetric.startDevRoles(NodeRole.MEMBER, currentDevicesIDs, ts);
                if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                    try {
                        File outputFile = new File(outputFileName);
                        newDeviceKGroup.outputWriter = new FileWriter(outputFile, true);
                    } catch (IOException e) {
                        System.out.println("An error while opening the output file occured.");
                        e.printStackTrace();
                    }
                }
            }
            deviceKGroups.add(newDeviceKGroup);
        }

        this.routines = routines;
        rtn_releasing_locks = new HashMap<>();
        routineKGroups = new ArrayList<RoutineKGroup>();
        List<String> routinesIDs = new ArrayList<String>(routines.keySet());
        currentRoutinesIDs = new ArrayList<String>();
        RoutineKGroup newRoutineKGroup;
        List<String> touchedDevicesIDsPerRoutine;
        for (int counter = 0; counter < routinesIDs.size(); counter += routineKGroupRange) {
            Map<String, List<String>> touchedDevicesIDs = new HashMap<>();
            for (String routineID : routinesIDs.subList(counter,
                    (counter + routineKGroupRange < routinesIDs.size()) ? (counter + routineKGroupRange)
                            : routinesIDs.size())) {
                touchedDevicesIDsPerRoutine = routines.get(routineID).getTouchedDevicesIDs();
                touchedDevicesIDs.put(routineID, touchedDevicesIDsPerRoutine);
                for (String deviceID : touchedDevicesIDsPerRoutine) {
                    devicesToRoutines.get(deviceID).add(routineID);
                }
            }
            newRoutineKGroup = new RoutineKGroup(touchedDevicesIDs, membershipList, F, K, epochNo, lep);
            if (newRoutineKGroup.isMemberOfKGroup(myID)) {
                currentRoutinesIDs.addAll(routinesIDs.subList(counter,
                        (counter + routineKGroupRange < routinesIDs.size()) ? (counter + routineKGroupRange)
                                : routinesIDs.size()));
                nodeMetric.startRtnRoles(NodeRole.MEMBER, currentRoutinesIDs, ts);
                if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                    try {
                        newRoutineKGroup.outputWriter = new FileWriter(this.outputFilename, true);
                    } catch (IOException e) {
                        System.out.println("An error while opening the output file occured.");
                        e.printStackTrace();
                    }
                }
            }
            routineKGroups.add(newRoutineKGroup);
        }

        quorumMsgsInfo = new HashMap<Integer, QuorumMsgInfo>();
        unicastMsgsInfo = new HashMap<Integer, UnicastMsgInfo>();

        deviceStates = new HashMap<String, Boolean>();
        deviceReadings = new HashMap<String, Float>();
        _checkpoint_counter = 0;
    }

    public boolean existUnprocessedEvents() {
        return !events.isEmpty();
    }

    public int incrementTS() {
        int newTS = ++ts;

        // check for new epoch
        if (epochNo == 0 || newTS == epochNo * epochLength) {
            incrementEpoch();
        }

        // check for new event
        List<Integer> toRemoveLists = new ArrayList<>();
        for (Integer checkTS : events.keySet()) {
            if (checkTS > newTS) {
                break;
            }
            if (events.get(checkTS) != null) {
                List<Event> toRemove = new ArrayList<>();
                for (Event e : events.get(checkTS)) {
                    switch (e.getType()) {
                        case NODE_JOINED:
                            nodeJoined(e.getAffectedEntity(), true);
                            toRemove.add(e);
                            break;
                        case NODE_FAILED:
                            nodeFailureDetected(e.getAffectedEntity(), true);
                            toRemove.add(e);
                            break;
                        case ROUTINE_EXECUTED:
                            if (isLeaderOfRoutineKGroup(e.getAffectedEntity())) {
                                releaseDeviceLocksForRoutine(e.getAffectedEntity(), e.getRoutineSeqNo());
                            }
                            toRemove.add(e);
                            break;
                        case ROUTINE_TRIGGERED:
                            break;
                        case CHECKPOINT:
                            if (debug) {
                                System.out.println("Receiving CHECKOUT at node " + myID + " at time " + checkTS);
                            }
                            if (testType == TestType.BANDWIDTH_BG) {
                                msgCheckpoint(checkTS);
                            }
                            toRemove.add(e);
                            break;
                        default:
                            toRemove.add(e);
                            break;
                    }
                }
                events.get(checkTS).removeAll(toRemove);
                if (events.get(checkTS).isEmpty())
                    toRemoveLists.add(checkTS);
            }
        }
        for (Integer removeTS : toRemoveLists) {
            events.remove(removeTS);
        }

        // check for routine triggerings if it's time for that
        if (newTS % routineMonitorPeriod == 0) {
            toRemoveLists.clear();
            for (Integer checkTS : events.keySet()) {
                if (checkTS > newTS) {
                    break;
                }
                if (events.get(checkTS) != null) {
                    List<Event> toRemove = new ArrayList<>();
                    for (Event e : events.get(checkTS)) {
                        if (e.getType() == EventType.ROUTINE_TRIGGERED) {
                            String routine_id = e.getAffectedEntity();
                            RoutineKGroup routineKGroup = routineKGroupInChargeOf(routine_id);
                            if (routineKGroup.getLeader() == myID) {
                                int routine_seq_no = routineKGroup
                                        .setRoutineTriggered(routine_id);
                                routineMetrics.recordTriggerUsrTime(routine_id, routine_seq_no, checkTS);
                                routineMetrics.recordTriggerSysTime(routine_id, routine_seq_no, newTS);
                                gatherQuorumForRoutineTrigger(routine_id, routine_seq_no);
                            }

                            if (!isMemberOfRoutineKGroup(e.getAffectedEntity()) ||
                                    routineKGroup.getLeader() != null) {
                                toRemove.add(e);
                            }
                        }
                    }
                    events.get(checkTS).removeAll(toRemove);
                    if (events.get(checkTS).isEmpty()) {
                        toRemoveLists.add(checkTS);
                    }
                }
            }
            for (Integer removeTS : toRemoveLists) {
                events.remove(removeTS);
            }
        }

        List<Integer> toRemove = new ArrayList<>();
        int msgSeqNo, routeRTT;
        UnicastMsgInfo unicastMsgInfo;
        for (Entry<Integer, UnicastMsgInfo> unicastMsgInfoEntry : unicastMsgsInfo.entrySet()) {
            msgSeqNo = unicastMsgInfoEntry.getKey();
            unicastMsgInfo = unicastMsgInfoEntry.getValue();
            if (unicastMsgInfo.isAcknowledged()) {
                toRemove.add(msgSeqNo);
                continue;
            }
            routeRTT = unicastMsgInfo.getRouteRTT();
            if (routeRTT == 0) {
                routeRTT = 1;
            }
            if (unicastMsgInfo.boundedWait ? (ts < unicastMsgInfo.initialSendTS + routeRTT * 4)
                    : true && epochNo == unicastMsgInfo.initialEpoch) {
                if (ts >= unicastMsgInfo.lastSendTS + routeRTT * 3 && epochNo == unicastMsgInfo.initialEpoch) {
                    if (debug) {
                        System.out.println("Node " + myID + ": Resending " + unicastMsgInfo.getMessageType()
                                + " msg (initialSendTS " + unicastMsgInfo.initialSendTS + ") at time " + ts
                                + ", initial epoch: " + unicastMsgInfo.initialEpoch + " about k-groups: "
                                + unicastMsgInfo.getRoutineKGroup() + ", " + unicastMsgInfo.getDeviceKGroup());
                    }
                    network.sendMsg(
                            new Message(myID, msgSeqNo, unicastMsgInfo.getDestination(), ts + unicastMsgInfo.routeOWD,
                                    unicastMsgInfo.getMessageType(), unicastMsgInfo.getMessagePayload()));
                    unicastMsgInfo.lastSendTS = ts;
                }
            } else {
                // waiting no more for Election msgs; become bully
                if (unicastMsgInfo.getMessageType() == MessageType.ELECTION) {
                    KGroup kgroup = unicastMsgInfo.getDeviceKGroup() != null ? unicastMsgInfo.getDeviceKGroup()
                            : unicastMsgInfo.getRoutineKGroup();
                    if (kgroup.electionStage == LeaderElectionStage.WAITING_ON_ELECTION_ACK) {
                        quorum(kgroup, MessageType.ELECTED,
                                new ElectedMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs,
                                        ((ElectionMessagePayload) unicastMsgInfo.getMessagePayload()).cause),
                                false, true);
                    } else if (kgroup.electionStage == LeaderElectionStage.WAITING_ON_ELECTED) {
                        if (ts > unicastMsgInfo.initialSendTS + routeRTT * 8) {
                            toRemove.add(msgSeqNo);
                            if (debug) {
                                System.out.println("Routine " + kgroup.entitiesIDs + " k-group: node " + myID
                                        + " not waiting on ELECTED anymore");
                            }
                            leaderElection(kgroup, ((ElectionMessagePayload) unicastMsgInfo.getMessagePayload()).cause);
                        }
                    } else {
                        toRemove.add(msgSeqNo);
                    }
                }
                // waiting no more for state transfer from old leader - contact current/old
                // k-group members
                else if (unicastMsgInfo.getMessageType() == MessageType.KGROUP_STATE_REQUEST) {
                    toRemove.add(msgSeqNo);
                    KGroup kgroup = unicastMsgInfo.getDeviceKGroup() != null ? unicastMsgInfo.getDeviceKGroup()
                            : unicastMsgInfo.getRoutineKGroup();
                    quorum(kgroup, MessageType.LOCAL_KGROUP_STATE_REQUEST,
                            new LocalKGroupStateRequestMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs), true,
                            false);
                } else {
                    toRemove.add(msgSeqNo);
                }
            }
        }
        for (Integer _msgSeqNo : toRemove) {
            unicastMsgsInfo.remove(_msgSeqNo);
        }
        toRemove.clear();

        QuorumMsgInfo quorumMsgInfo;
        for (Entry<Integer, QuorumMsgInfo> quorumMsgInfoEntry : quorumMsgsInfo.entrySet()) {
            msgSeqNo = quorumMsgInfoEntry.getKey();
            quorumMsgInfo = quorumMsgInfoEntry.getValue();
            if (quorumMsgInfo.isApproved()) {
                toRemove.add(msgSeqNo);
                continue;
            }

            routeRTT = quorumMsgInfo.getRouteRTT();
            if (routeRTT == 0) {
                routeRTT = 1;
            }
            if (quorumMsgInfo.boundedWait ? (ts < quorumMsgInfo.initialSendTS + routeRTT * 4) : true) {
                if (ts >= quorumMsgInfo.lastSendTS + routeRTT * 2 && epochNo == quorumMsgInfo.initialEpoch) {
                    if (debug) {
                        System.out.println("Node " + myID + ": Resending " + quorumMsgInfo.getMessageType()
                                + " msg (initialSendTS " + quorumMsgInfo.initialSendTS + ") at time " + ts
                                + ", initial epoch: " + quorumMsgInfo.initialEpoch + " about k-group "
                                + quorumMsgInfo.getKGroup());
                    }
                    multicast(quorumMsgInfo.getNoReplyNodesIDs(), quorumMsgInfo.getMessageType(), msgSeqNo,
                            quorumMsgInfo.getMessagePayload());
                    quorumMsgInfo.lastSendTS = ts;
                }
            } else {
                toRemove.add(msgSeqNo);
            }
        }
        for (Integer _msgSeqNo : toRemove) {
            quorumMsgsInfo.remove(_msgSeqNo);
        }

        return newTS;
    }

    public int incrementEpoch() {
        int currentEpoch = ++epochNo;

        // updating all k-group members and moving on to the leader election phase
        currentDevicesIDs.clear();
        nodeMetric.finishDevRoles(ts);
        for (DeviceKGroup kgroup : deviceKGroups) {
            kgroup.updateKGroup(membershipList);
            if (kgroup.isMemberOfKGroup(myID)) {
                currentDevicesIDs.addAll(kgroup.entitiesIDs);
                nodeMetric.startDevRoles(NodeRole.MEMBER, currentDevicesIDs, ts);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
                if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                    try {
                        if (kgroup.outputWriter != null) {
                            kgroup.outputWriter.close();
                        }
                        kgroup.outputWriter = new FileWriter(outputFilename, true);
                    } catch (IOException e) {
                        System.out.println("An error while opening the output file occured.");
                        e.printStackTrace();
                    }
                }
            }
        }
        currentRoutinesIDs.clear();
        nodeMetric.finishRtnRoles(ts);
        for (RoutineKGroup kgroup : routineKGroups) {
            kgroup.updateKGroup(membershipList);
            if (kgroup.isMemberOfKGroup(myID)) {
                currentRoutinesIDs.addAll(kgroup.entitiesIDs);
                nodeMetric.startRtnRoles(NodeRole.MEMBER, currentRoutinesIDs, ts);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
                if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                    try {
                        if (kgroup.outputWriter != null) {
                            kgroup.outputWriter.close();
                        }
                        kgroup.outputWriter = new FileWriter(outputFilename, true);
                    } catch (IOException e) {
                        System.out.println("An error while opening the output file occured.");
                        e.printStackTrace();
                    }
                }
            }
        }

        return currentEpoch;
    }

    public int changeTS(int newTS) {
        int oldTS = ts;
        ts = newTS;
        if (debug) {
            System.out.println("Node" + myID + "-current ts: " + newTS);
        }

        for (int checkTS = oldTS + 1; checkTS <= newTS; checkTS++) {
            if (events.get(checkTS) != null) {
                for (Event e : events.get(checkTS)) {
                    switch (e.getType()) {
                        case NODE_JOINED:
                            nodeJoined(e.getAffectedEntity(), true);
                            break;
                        case NODE_FAILED:
                            nodeFailureDetected(e.getAffectedEntity(), true);
                            break;
                        case ROUTINE_TRIGGERED:
                        default:
                            break;
                    }
                }
            }
        }
        return newTS;
    }

    public int changeEpoch(int newEpochNo) {
        epochNo = newEpochNo;
        if (debug) {
            System.out.println("********************************************************************Node" + myID
                    + ": new epoch " + newEpochNo);
        }
        currentDevicesIDs.clear();
        for (DeviceKGroup kgroup : deviceKGroups) {
            kgroup.updateKGroup(membershipList);
            if (kgroup.isMemberOfKGroup(myID)) {
                currentDevicesIDs.addAll(kgroup.entitiesIDs);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
            }
        }
        currentRoutinesIDs.clear();
        for (RoutineKGroup kgroup : routineKGroups) {
            kgroup.updateKGroup(membershipList);
            if (kgroup.isMemberOfKGroup(myID)) {
                currentRoutinesIDs.addAll(kgroup.entitiesIDs);
                leaderElection(kgroup, LeaderElectionCause.NEW_EPOCH);
            }
        }

        return newEpochNo;
    }

    public void closeOutputWriters() {
        for (KGroup kgroup : deviceKGroups) {
            try {
                if (kgroup.outputWriter != null) {
                    kgroup.outputWriter.close();
                }
            } catch (IOException e) {
                System.out.println("Error occured while closing output writer.");
            }
        }
        for (KGroup kgroup : routineKGroups) {
            try {
                if (kgroup.outputWriter != null) {
                    kgroup.outputWriter.close();
                }
            } catch (IOException e) {
                System.out.println("Error occured while closing output writer.");
            }
        }
    }

    public void processMessage(Message msg, KGroup kgroup) {
        Message replyMsg;
        MessageType replyMsgType = null;
        MessagePayload replyPayload = null;
        UnicastMsgInfo unicastMsgInfo;
        boolean reply = false;

        switch (msg.type) {
            case ELECTION: // remote call / unicast messages
                if (msg.payload.epochNo != epochNo || !kgroup.isMemberOfKGroup(myID))
                    break;

                reply = true;
                replyMsgType = MessageType.ELECTION_ACK;
                replyPayload = new ElectionAckMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, msg.srcSeqNo);

                ElectionMessagePayload electionPayload = (ElectionMessagePayload) msg.payload;
                if (kgroup.electionStage == LeaderElectionStage.NOT_STARTED) {
                    leaderElection(kgroup, electionPayload.cause);
                }

                break;

            case NODE_FAILURE:
                NodeFailureMessagePayload nodeFailurePayload = (NodeFailureMessagePayload) msg.payload;
                handleNodeFailure(nodeFailurePayload.failedNodeID);

                replyMsgType = MessageType.NODE_FAILURE_ACK;
                replyPayload = new NodeFailureAckMessagePayload(msg.srcSeqNo);
                reply = true;

                break;

            case NODE_RECRUITEMENT_REQUEST:
                if (msg.payload.epochNo != epochNo)
                    break;
                NodeRecruitmentRequestMessagePayload nodeRecruitmentRequestMessagePayload = (NodeRecruitmentRequestMessagePayload) msg.payload;
                kgroup.nodeRecruited(myID);
                kgroup.setState(nodeRecruitmentRequestMessagePayload.state);

                replyMsgType = MessageType.NODE_RECRUITED;
                replyPayload = new NodeRecruitedMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, msg.srcSeqNo);
                reply = true;

                break;

            case KGROUP_STATE_REQUEST:
                if (msg.payload.epochNo != epochNo)
                    break;
                kgroup.updateLeader(msg.src);
                replyPayload = new KGroupStateMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, msg.srcSeqNo,
                        kgroup.getState(), kgroup.getSeqNos(), new ArrayList<Message>(kgroup.getWaitingMessageQueue()));
                // when to remove forwarded
                replyMsgType = MessageType.KGROUP_STATE;
                reply = true;
                break;

            case DEVICE_STATE_CHECK:
                replyPayload = new DeviceStateMessagePayload(msg.srcSeqNo, state);
                replyMsgType = MessageType.DEVICE_STATE;
                reply = true;
                break;

            case DEVICE_READING_REQUEST:
                replyPayload = new DeviceReadingMessagePayload(msg.srcSeqNo, reading);
                replyMsgType = MessageType.DEVICE_READING;
                reply = true;
                break;

            case LOCK_REQUEST:
                if (msg.payload.epochNo != epochNo) {
                    // forward message to new device k-group leader
                    break;
                }
                LockRequestMessagePayload lockRequestPayload = (LockRequestMessagePayload) msg.payload;

                // update the routine k-group's leader so that I can safely reply afterwards
                routineKGroupInChargeOf(lockRequestPayload.routineID).updateLeader(msg.src);
                reply = true;
                if (deviceKGroupInChargeOf(lockRequestPayload.entitiesIDs).getLeader() != myID) {
                    List<String> routineID = new ArrayList<>();
                    routineID.add(lockRequestPayload.routineID);
                    replyPayload = new DeviceKGroupLeaderInfoMessagePayload(epochNo, routineID, msg.srcSeqNo,
                            deviceKGroupInChargeOf(lockRequestPayload.entitiesIDs).getLeader());
                    replyMsgType = MessageType.DEVICE_KGROUP_LEADER_INFO;
                } else {
                    String deviceID = lockRequestPayload.entitiesIDs.get(0);
                    if (debug) {
                        System.out.println("Device " + deviceID + " k-group: lock request by routine "
                                + lockRequestPayload.routineID + "-" + lockRequestPayload.routineSeqNo
                                + " received at time " + ts);
                    }
                    List<String> routineID = new ArrayList<>();
                    routineID.add(lockRequestPayload.routineID);
                    replyPayload = new LockRequestAckMessagePayload(epochNo, routineID, msg.srcSeqNo);
                    replyMsgType = MessageType.LOCK_REQUEST_ACK;
                    int reqSeqNo;

                    // if this request has not been received before
                    // meaning if it is not in newLockRequests/queue
                    if (!((DeviceKGroup) kgroup).isLockRequestReceived(deviceID, lockRequestPayload.routineID,
                            lockRequestPayload.routineSeqNo)) {
                        reqSeqNo = ((DeviceKGroup) kgroup).addNewLockRequest(deviceID, lockRequestPayload.routineID,
                                lockRequestPayload.routineSeqNo);
                        if (reqSeqNo == -1) {
                            break;
                        }
                        gatherQuorumForDeviceLockRequest(lockRequestPayload.routineID, lockRequestPayload.routineSeqNo,
                                deviceID, reqSeqNo);
                    }
                    // if this request has already been received before
                    else {
                        reqSeqNo = ((DeviceKGroup) kgroup).getLockRequestSeqNo(deviceID, lockRequestPayload.routineID,
                                lockRequestPayload.routineSeqNo);
                        if (reqSeqNo == -1) {
                            break;
                        }
                        routineID = new ArrayList<>();
                        routineID.add(lockRequestPayload.routineID);
                        // if this lock referred to by this request has already been granted before
                        // meaning if it is the current locker
                        if (((DeviceKGroup) kgroup).isLockAcquired(deviceID, lockRequestPayload.routineID,
                                lockRequestPayload.routineSeqNo)) {
                            remoteCall(routineKGroupInChargeOf(routineID), deviceKGroupInChargeOf(deviceID),
                                    routineKGroupInChargeOf(routineID).getMostProbableLeader(), MessageType.LOCKED,
                                    new LockedMessagePayload(epochNo, routineID, deviceID,
                                            lockRequestPayload.routineSeqNo),
                                    false);
                        }
                        // if lock request has been granted
                        else if (((DeviceKGroup) kgroup).isLockRequestGranted(deviceID, lockRequestPayload.routineID,
                                lockRequestPayload.routineSeqNo)) {
                            remoteCall(routineKGroupInChargeOf(routineID), (DeviceKGroup) kgroup,
                                    routineKGroupInChargeOf(routineID).getMostProbableLeader(),
                                    MessageType.LOCK_REQUESTED, new LockRequestedMessagePayload(epochNo, routineID,
                                            lockRequestPayload.routineSeqNo, deviceID),
                                    false);
                        }
                        // if lock request has been received but not granted
                        else {
                            gatherQuorumForDeviceLockRequest(lockRequestPayload.routineID,
                                    lockRequestPayload.routineSeqNo, deviceID, reqSeqNo);
                        }
                    }
                    if (debug) {
                        System.out.println("Device " + deviceID + " state after: " + kgroup.getState().get(deviceID));
                    }
                }
                break;

            case LOCK_REQUESTED:
                if (msg.payload.epochNo != epochNo)
                    // forward message to new routine k-group leader
                    break;
                LockRequestedMessagePayload lockRequestedPayload = (LockRequestedMessagePayload) msg.payload;
                if (isMemberOfRoutineKGroup(lockRequestedPayload.entitiesIDs)
                        && routineKGroupInChargeOf(lockRequestedPayload.entitiesIDs).getLeader() == myID) {
                    if (debug) {
                        System.out.println("Routine " + lockRequestedPayload.entitiesIDs.get(0) + "-"
                                + lockRequestedPayload.routineSeqNo + " k-group: device "
                                + lockRequestedPayload.deviceID + " lock request granted at time " + ts);
                    }
                    List<String> deviceID = new ArrayList<>();
                    deviceID.add(lockRequestedPayload.deviceID);
                    replyPayload = new LockRequestedAckMessagePayload(epochNo, deviceID, msg.srcSeqNo);
                    replyMsgType = MessageType.LOCK_REQUESTED_ACK;
                    reply = true;
                }
                break;

            case LOCKED:
                if (msg.payload.epochNo != epochNo) {
                    // forward message to new routine k-group leader
                    break;
                }
                LockedMessagePayload lockedPayload = (LockedMessagePayload) msg.payload;
                if (!isLeaderOfRoutineKGroup(lockedPayload.entitiesIDs.get(0))) {
                    break;
                }

                replyPayload = new LockedAckMessagePayload(epochNo, lockedPayload.deviceID, msg.srcSeqNo);
                replyMsgType = MessageType.LOCKED_ACK;
                reply = true;

                if (isMemberOfRoutineKGroup(lockedPayload.entitiesIDs)) {
                    String routineID = lockedPayload.entitiesIDs.get(0);
                    int routineSeqNo = lockedPayload.routineSeqNo;
                    if (!routineKGroupInChargeOf(lockedPayload.entitiesIDs.get(0)).isRoutineAcquiringLocks(routineID,
                            routineSeqNo)) {
                        break;
                    }
                    routineKGroupInChargeOf(lockedPayload.entitiesIDs).acquiredDeviceLockForRoutine(routineID,
                            routineSeqNo, lockedPayload.deviceID, routines.get(routineID).getTouchedDevicesIDs());
                    if (debug) {
                        System.out.println("Routine " + routineID + "-" + routineSeqNo + " k-group: device "
                                + lockedPayload.deviceID + " lock acquired at time " + ts);
                    }
                    if (routineKGroupInChargeOf(lockedPayload.entitiesIDs).isRoutineAcquiringLocks(routineID,
                            routineSeqNo)) {
                        requestDeviceLockForRoutine(lockedPayload.entitiesIDs.get(0), routineSeqNo);
                    } else if (routineKGroupInChargeOf(lockedPayload.entitiesIDs).areRoutineLocksAcquired(routineID,
                            routineSeqNo)) {
                        if (events.get(ts + routines.get(routineID).getLength()) == null) {
                            events.put(ts + routines.get(routineID).getLength(), new ArrayList<Event>());
                        }
                        routineMetrics.recordStartExecutionTime(routineID, routineSeqNo, ts);
                        int routineEndTS = ts + routines.get(routineID).getLength();
                        routineKGroupInChargeOf(lockedPayload.entitiesIDs).startRoutineExecution(routineID,
                                routineSeqNo, routineEndTS);
                        events.get(routineEndTS).add(new Event(routineID, routineSeqNo));
                        List<String> touchedDevicesIDs = routines.get(routineID).getTouchedDevicesIDs();
                        routineMetrics.updateRtnPreRtnLockRelease(routineID, routineSeqNo, touchedDevicesIDs);
                        if (debug) {
                            routineMetrics.printDevLockReleaseMap(touchedDevicesIDs);
                            System.out.println("Routine " + routineID + "-" + routineSeqNo
                                    + " is starting execution at time " + ts + " !!!!");
                        }
                    }

                    if (routineKGroupInChargeOf(lockedPayload.entitiesIDs).isRoutineReleasingLocks(routineID,
                            routineSeqNo)) {
                        releaseDeviceLockForRoutine(routineID, routineSeqNo, lockedPayload.deviceID);
                    }
                }
                break;

            case LOCK_RELEASE_REQUEST:
                if (msg.payload.epochNo != epochNo)
                    // forward to new device k-group leader
                    break;
                LockReleaseRequestMessagePayload lockReleaseRequestPayload = (LockReleaseRequestMessagePayload) msg.payload;
                // update the routine k-group's leader so that I can safely reply afterwards
                routineKGroupInChargeOf(lockReleaseRequestPayload.routineID).updateLeader(msg.src);
                if (deviceKGroupInChargeOf(lockReleaseRequestPayload.entitiesIDs).getLeader() != myID) {
                    List<String> routineID = new ArrayList<>();
                    routineID.add(lockReleaseRequestPayload.routineID);
                    replyPayload = new DeviceKGroupLeaderInfoMessagePayload(epochNo, routineID, msg.srcSeqNo,
                            deviceKGroupInChargeOf(lockReleaseRequestPayload.entitiesIDs).getLeader());
                    replyMsgType = MessageType.DEVICE_KGROUP_LEADER_INFO;
                    reply = true;
                } else {
                    if (debug) {
                        System.out.println("Device " + lockReleaseRequestPayload.entitiesIDs.get(0)
                                + " k-group: lock release requested by routine " + lockReleaseRequestPayload.routineID
                                + "-" + lockReleaseRequestPayload.routineSeqNo + " at time " + ts);
                    }
                    List<String> routineID = new ArrayList<>();
                    routineID.add(lockReleaseRequestPayload.routineID);
                    replyPayload = new LockReleaseRequestAckMessagePayload(epochNo, routineID, msg.srcSeqNo);
                    replyMsgType = MessageType.LOCK_RELEASE_REQUEST_ACK;
                    reply = true;
                    int reqSeqNo = ((DeviceKGroup) kgroup)
                            .getLockerReqSeqNo(lockReleaseRequestPayload.entitiesIDs.get(0));
                    gatherQuorumForDeviceLockRelease(lockReleaseRequestPayload.routineID,
                            lockReleaseRequestPayload.routineSeqNo, lockReleaseRequestPayload.entitiesIDs.get(0),
                            reqSeqNo);
                }
                break;

            case LOCK_RELEASED:
                if (msg.payload.epochNo != epochNo)
                    // forward message to new routine k-group leader
                    break;
                LockReleasedMessagePayload lockReleasedPayload = (LockReleasedMessagePayload) msg.payload;
                if (isMemberOfRoutineKGroup(lockReleasedPayload.entitiesIDs)
                        && routineKGroupInChargeOf(lockReleasedPayload.entitiesIDs).getLeader() == myID) {
                    String routine_id = lockReleasedPayload.entitiesIDs.get(0);
                    int routine_seq_no = lockReleasedPayload.routineSeqNo;
                    if (debug) {
                        System.out.println("Routine " + routine_id + "-" + routine_seq_no + " k-group: device "
                                + lockReleasedPayload.deviceID + " lock released at time " + ts);
                    }

                    List<String> deviceID = new ArrayList<>();
                    deviceID.add(lockReleasedPayload.deviceID);
                    replyPayload = new LockReleasedAckMessagePayload(epochNo, deviceID, routine_id, routine_seq_no,
                            msg.srcSeqNo);
                    replyMsgType = MessageType.LOCK_RELEASED_ACK;
                    reply = true;
                }
                break;

            case ELECTED: // quorum messages
                if (msg.payload.epochNo != epochNo || !kgroup.isMemberOfKGroup(myID)
                        || kgroup.electionStage == LeaderElectionStage.COMPLETE)
                    break;
                ElectedMessagePayload electedPayload = (ElectedMessagePayload) msg.payload;
                if (electedPayload.cause == LeaderElectionCause.LEADER_FAILURE) {
                    membershipList.replace(kgroup.getLeader(), Membership.OFFLINE);
                    kgroup.updateFailedNodes(kgroup.getLeader());
                }
                if (myID != msg.src) {
                    // update leader
                    kgroup.updateLeader(msg.src);
                    kgroup.electionStage = LeaderElectionStage.COMPLETE;
                    kgroup.stateTransferStage = StateTransferStage.COMPLETE;
                    kgroup.openReceivingEnd();
                }

                replyPayload = new ElectedAckMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, msg.srcSeqNo);
                replyMsgType = MessageType.ELECTED_ACK;
                reply = true;
                break;

            case LOCAL_KGROUP_STATE_REQUEST:
                if (msg.payload.epochNo != epochNo)
                    break;
                replyPayload = new LocalKGroupStateMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs,
                        msg.srcSeqNo, kgroup.getState(), kgroup.getSeqNos());
                replyMsgType = MessageType.LOCAL_KGROUP_STATE;
                reply = true;
                break;

            case KGROUP_STATE_DISTRIBUTION:
                if (msg.payload.epochNo != epochNo)
                    break;
                KGroupStateDistributionMessagePayload kGroupStateDistributionPayload = (KGroupStateDistributionMessagePayload) msg.payload;
                if (kgroup.isMemberOfKGroup(myID)) {
                    kgroup.setState(kGroupStateDistributionPayload.state);
                    kgroup.setSeqNos(kGroupStateDistributionPayload.seqNos);
                    kgroup.stateTransferStage = StateTransferStage.COMPLETE;
                    kgroup.openReceivingEnd();
                    replyPayload = new KGroupStateAckMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs,
                            msg.srcSeqNo);
                    replyMsgType = MessageType.KGROUP_STATE_ACK;
                    reply = true;
                }
                break;

            case TRIGGER_QUORUM:
                if (msg.payload.epochNo != epochNo)
                    break;
                TriggerQuorumMessagePayload triggerQuorumPayload = (TriggerQuorumMessagePayload) msg.payload;
                if (kgroup.isMemberOfKGroup(myID)) {
                    if (kgroup.getLeader() != myID) {
                        ((RoutineKGroup) kgroup).setRoutineTriggered(triggerQuorumPayload.entitiesIDs.get(0),
                                triggerQuorumPayload.routineSeqNo);
                    }
                    replyPayload = new TriggerQuorumAckMessagePayload(epochNo, msg.payload.entitiesIDs, msg.srcSeqNo);
                    replyMsgType = MessageType.TRIGGER_QUORUM_ACK;
                    reply = true;
                }
                break;

            case LOCK_REQUEST_QUORUM:
                if (msg.payload.epochNo != epochNo)
                    break;
                LockRequestQuorumMessagePayload lockRequestQuorumPayload = (LockRequestQuorumMessagePayload) msg.payload;
                if (kgroup.isMemberOfKGroup(myID)) {
                    if (kgroup.getLeader() != myID) {
                        ((DeviceKGroup) kgroup).replicateLockRequest(lockRequestQuorumPayload.entitiesIDs.get(0),
                                lockRequestQuorumPayload.routineID, lockRequestQuorumPayload.routineSeqNo,
                                lockRequestQuorumPayload.reqSeqNo);
                    }
                    replyPayload = new LockRequestQuorumAckMessagePayload(epochNo, msg.payload.entitiesIDs,
                            msg.srcSeqNo, lockRequestQuorumPayload.routineID, lockRequestQuorumPayload.reqSeqNo,
                            lockRequestQuorumPayload.routineSeqNo);
                    replyMsgType = MessageType.LOCK_REQUEST_QUORUM_ACK;
                    reply = true;
                }
                break;

            case LOCKED_QUORUM:
                if (msg.payload.epochNo != epochNo)
                    break;
                LockedQuorumMessagePayload lockedQuorumPayload = (LockedQuorumMessagePayload) msg.payload;
                if (kgroup.isMemberOfKGroup(myID)) {
                    if (kgroup.getLeader() != myID) {
                        ((DeviceKGroup) kgroup).replicateLockAcquisition(lockedQuorumPayload.entitiesIDs.get(0),
                                lockedQuorumPayload.routineID, lockedQuorumPayload.routineSeqNo,
                                lockedQuorumPayload.reqSeqNo);
                    }
                    replyPayload = new LockedQuorumAckMessagePayload(epochNo, lockedQuorumPayload.entitiesIDs,
                            msg.srcSeqNo, lockedQuorumPayload.routineID, lockedQuorumPayload.routineSeqNo,
                            lockedQuorumPayload.reqSeqNo);
                    replyMsgType = MessageType.LOCKED_QUORUM_ACK;
                    reply = true;
                }
                break;

            case LOCK_RELEASE_QUORUM:
                if (msg.payload.epochNo != epochNo)
                    break;
                LockReleaseQuorumMessagePayload lockReleaseQuorumPayload = (LockReleaseQuorumMessagePayload) msg.payload;
                if (kgroup.isMemberOfKGroup(myID)) {
                    if (kgroup.getLeader() != myID) {
                        ((DeviceKGroup) kgroup).replicateLockRelease(lockReleaseQuorumPayload.entitiesIDs.get(0),
                                lockReleaseQuorumPayload.routineID, lockReleaseQuorumPayload.routineSeqNo,
                                lockReleaseQuorumPayload.reqSeqNo);
                    }
                    replyPayload = new LockReleaseQuorumAckMessagePayload(epochNo, lockReleaseQuorumPayload.entitiesIDs,
                            msg.srcSeqNo, lockReleaseQuorumPayload.routineID, lockReleaseQuorumPayload.reqSeqNo,
                            lockReleaseQuorumPayload.routineSeqNo);
                    replyMsgType = MessageType.LOCK_RELEASE_QUORUM_ACK;
                    reply = true;
                }
                break;

            case ELECTION_ACK: // remote call acks
                if (!kgroup.isMemberOfKGroup(myID))
                    break;
            case NODE_RECRUITED:
            case KGROUP_STATE:
            case LOCK_REQUEST_ACK:
            case LOCK_REQUESTED_ACK:
            case LOCKED_ACK:
            case LOCK_RELEASE_REQUEST_ACK:
            case LOCK_RELEASED_ACK:
            case NODE_FAILURE_ACK:
            case DEVICE_STATE:
            case DEVICE_READING:
                processRemoteCallReplyMessage(msg, kgroup);
                break;

            case DEVICE_KGROUP_LEADER_INFO:
                DeviceKGroupLeaderInfoMessagePayload deviceKGroupLeaderInfoPayload = (DeviceKGroupLeaderInfoMessagePayload) msg.payload;
                unicastMsgInfo = unicastMsgsInfo.get(deviceKGroupLeaderInfoPayload.srcSeqNo);
                if (unicastMsgInfo != null) {
                    unicastMsgInfo.getDeviceKGroup().updateLeader(deviceKGroupLeaderInfoPayload.leaderNodeID);
                    if (msg.payload.epochNo != epochNo)
                        break;
                    unicastMsgInfo.updateDestination(deviceKGroupLeaderInfoPayload.leaderNodeID);
                }
                break;

            case ROUTINE_KGROUP_LEADER_INFO:
                RoutineKGroupLeaderInfoMessagePayload routineKGroupLeaderInfoPayload = (RoutineKGroupLeaderInfoMessagePayload) msg.payload;
                unicastMsgInfo = unicastMsgsInfo.get(routineKGroupLeaderInfoPayload.srcSeqNo);
                if (unicastMsgInfo != null) {
                    unicastMsgInfo.getRoutineKGroup().updateLeader(routineKGroupLeaderInfoPayload.leaderNodeID);
                    if (msg.payload.epochNo != epochNo)
                        break;
                    unicastMsgInfo.updateDestination(routineKGroupLeaderInfoPayload.leaderNodeID);
                }
                break;

            case ELECTED_ACK: // quorum acks
                if (!kgroup.isMemberOfKGroup(myID) || kgroup.electionStage == LeaderElectionStage.COMPLETE)
                    break;
            case LOCAL_KGROUP_STATE:
            case KGROUP_STATE_ACK:
            case TRIGGER_QUORUM_ACK:
            case LOCK_REQUEST_QUORUM_ACK:
            case LOCKED_QUORUM_ACK:
            case LOCK_RELEASE_QUORUM_ACK:
                if (msg.payload.epochNo != epochNo)
                    break;
                processQuorumAckMessage(msg, kgroup);
                break;

            default:
                break;
        }
        if (reply) {
            seqNo++;
            replyMsg = new Message(myID, seqNo, msg.src, ts + network.getRouteOWD(myID, msg.src), replyMsgType,
                    replyPayload);
            network.sendMsg(replyMsg);
            reply = false;
        }
    }

    public void processRemoteCallReplyMessage(Message msg, KGroup kgroup) {
        ReplyMessagePayload replyPayload = (ReplyMessagePayload) msg.payload;
        if (!kgroup.isMemberOfKGroup(myID)) {
            unicastMsgsInfo.remove(replyPayload.srcSeqNo);
            return;
        }

        UnicastMsgInfo unicastMsgInfo = unicastMsgsInfo.get(replyPayload.srcSeqNo);
        if (unicastMsgInfo != null) {
            switch (msg.type) {
                case ELECTION_ACK:
                    if (kgroup.electionStage == LeaderElectionStage.WAITING_ON_ELECTION_ACK) {
                        kgroup.electionStage = LeaderElectionStage.WAITING_ON_ELECTED;
                    }
                    break;
                case KGROUP_STATE:
                    KGroupStateMessagePayload kGroupStatePayload = (KGroupStateMessagePayload) replyPayload;
                    kgroup.setState(kGroupStatePayload.state);
                    if (debug) {
                        System.out.println(kgroup.type + " " + kgroup.entitiesIDs
                                + " k-group: state received by leader at time " + ts);
                    }
                    quorum(kgroup, MessageType.KGROUP_STATE_DISTRIBUTION, new KGroupStateDistributionMessagePayload(
                            epochNo, kgroup.type, kGroupStatePayload.state, kGroupStatePayload.seqNos), false, false);
                    kgroup.setWaitingMessageQueue(kGroupStatePayload.unprocessedMessages);
                    break;
                case DEVICE_STATE:
                    DeviceStateMessagePayload deviceStatePayload = (DeviceStateMessagePayload) replyPayload;
                    deviceStates.put(msg.src, deviceStatePayload.state);
                    // check whether the corresponding routines are triggered
                    for (String routineID : devicesToRoutines.get(msg.src)) {
                        if (isRoutineTriggered(routineID)) {
                            gatherQuorumForRoutineTrigger(routineID,
                                    routineKGroupInChargeOf(routineID).setRoutineTriggered(routineID));
                        }
                    }
                    break;
                case DEVICE_READING:
                    DeviceReadingMessagePayload deviceReadingPayload = (DeviceReadingMessagePayload) replyPayload;
                    deviceReadings.put(msg.src, deviceReadingPayload.reading);
                    // check whether the corresponding routine is triggered
                    for (String routineID : devicesToRoutines.get(msg.src)) {
                        if (isRoutineTriggered(routineID)) {
                            gatherQuorumForRoutineTrigger(routineID,
                                    routineKGroupInChargeOf(routineID).setRoutineTriggered(routineID));
                        }
                    }
                    break;
                case NODE_RECRUITED:
                    kgroup.nodeRecruited(msg.src);
                    break;
                case NODE_FAILURE_ACK:
                case LOCK_REQUEST_ACK:
                case LOCK_REQUESTED_ACK:
                case LOCKED_ACK:
                case LOCK_RELEASE_REQUEST_ACK:
                    break;
                case LOCK_RELEASED_ACK:
                    LockReleasedAckMessagePayload payload = (LockReleasedAckMessagePayload) msg.payload;
                    String routineID = payload.routineID;
                    String deviceID = msg.payload.entitiesIDs.get(0);
                    if (kgroup.getLeader().equals(myID)) {
                        if (debug) {
                            System.out.println("Node " + myID + " received LOCK_RELEASED_ACK for routine " +
                                    routineID + " device " + deviceID);
                        }
                        rtn_releasing_locks.get(routineID).remove(String.valueOf(deviceID));
                        if (testType == TestType.BANDWIDTH_BG && rtn_releasing_locks.get(routineID).isEmpty()) { // All
                                                                                                                 // locks
                                                                                                                 // has
                                                                                                                 // been
                                                                                                                 // released
                                                                                                                 // for
                                                                                                                 // this
                                                                                                                 // routine.
                            msgCheckpoint(ts);
                        }
                    }
                    break;
                default:
                    break;
            }
            unicastMsgInfo.setAcknowledged();
            unicastMsgsInfo.remove(replyPayload.srcSeqNo);
        }
    }

    public void processQuorumAckMessage(Message msg, KGroup kgroup) {
        ReplyMessagePayload replyPayload = (ReplyMessagePayload) msg.payload;
        if (!kgroup.isMemberOfKGroup(myID)) {
            quorumMsgsInfo.remove(replyPayload.srcSeqNo);
            return;
        }

        QuorumMsgInfo quorumMsgInfo = quorumMsgsInfo.get(replyPayload.srcSeqNo);
        int neededAckCount;
        switch (msg.type) {
            case ELECTED_ACK:
            case TRIGGER_QUORUM_ACK:
            case LOCK_REQUEST_QUORUM_ACK:
            case LOCKED_QUORUM_ACK:
            case LOCK_RELEASE_QUORUM_ACK:
                neededAckCount = kgroup.size() / 2 + F + 1;
                break;
            case LOCAL_KGROUP_STATE:
                LocalKGroupStateMessagePayload localKGroupStatePayload = (LocalKGroupStateMessagePayload) replyPayload;
                kgroup.addLocalState(localKGroupStatePayload.states);
                kgroup.addLocalSeqNos(localKGroupStatePayload.seqNos);
            case KGROUP_STATE_ACK:
                neededAckCount = kgroup.size();
                break;
            default:
                neededAckCount = 0;
                break;
        }
        if (quorumMsgInfo != null && !quorumMsgInfo.isApproved()) {
            quorumMsgInfo.newReply(msg.src, true);
            if (quorumMsgInfo.getPositiveRepliesCount() >= neededAckCount) {
                switch (msg.type) {
                    case ELECTED_ACK:
                        kgroup.updateLeader(myID);
                        kgroup.electionStage = LeaderElectionStage.COMPLETE;
                        if (debug) {
                            System.out.println(kgroup.type + " " + kgroup.entitiesIDs + " k-group: (members "
                                    + kgroup.curNodesIDs + ") elected node " + kgroup.getLeader()
                                    + " as leader for epoch " + epochNo + " at time " + ts);
                        }
                        if (kgroup.type.equals(KGroupType.ROUTINE)) {
                            nodeMetric.startRtnRoles(NodeRole.LEADER, kgroup.entitiesIDs, ts);
                        } else {
                            nodeMetric.startDevRoles(NodeRole.LEADER, kgroup.entitiesIDs, ts);
                        }
                        if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                            try {
                                kgroup.outputWriter.write("\n" + (ts - kgroup.leaderElectionTime));
                            } catch (IOException e) {
                                System.out.println(
                                        "An error occured while writing to the output file about election time.");
                            }
                        }
                        stateTransfer(kgroup, ((ElectedMessagePayload) quorumMsgInfo.getMessagePayload()).cause);
                        break;
                    case KGROUP_STATE_ACK:
                        if (debug) {
                            System.out.println(kgroup.type + " " + kgroup.entitiesIDs
                                    + " k-group: state transfered to all members by time " + ts);
                        }
                        if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                            try {
                                kgroup.outputWriter.write("," + (ts - kgroup.stateTransferTime));
                            } catch (IOException e) {
                                System.out.println(
                                        "An error occured while writing to the output file about state transfer time.");
                            }
                        }
                        kgroup.stateTransferStage = StateTransferStage.COMPLETE;
                        kgroup.openReceivingEnd();
                        switch (kgroup.type) {
                            case DEVICE:
                                for (String deviceID : kgroup.entitiesIDs) {
                                    // if a device's lock is acquired, let the locker routine's k-group know in case
                                    // the device k-group did not succeed before the epoch change
                                    if (deviceKGroupInChargeOf(deviceID).isLocked(deviceID)) {
                                        List<String> routineID = new ArrayList<>();
                                        routineID.add(((DeviceLock) kgroup.getState().get(deviceID)).getLocker()
                                                .getRoutineID());
                                        int routineSeqNo = ((DeviceLock) kgroup.getState().get(deviceID)).getLocker()
                                                .getRoutineSeqNo();
                                        if (debug) {
                                            System.out.println("Device " + deviceID + " k-group: Reminding routine "
                                                    + routineID.get(0) + "-" + routineSeqNo
                                                    + " that it has the device's lock at time " + ts);
                                            if (ts == 1848) {
                                                System.out.println(
                                                        routineKGroupInChargeOf(routineID).getMostProbableLeader());
                                            }
                                        }
                                        remoteCall(routineKGroupInChargeOf(routineID), (DeviceKGroup) kgroup,
                                                routineKGroupInChargeOf(routineID).getMostProbableLeader(),
                                                MessageType.LOCKED,
                                                new LockedMessagePayload(epochNo, routineID, deviceID, routineSeqNo),
                                                false);
                                    }
                                    // if a device is not locked but the queue is not empty either
                                    else if (!deviceKGroupInChargeOf(deviceID).isLocked(deviceID)
                                            && !deviceKGroupInChargeOf(deviceID).isLockQueueEmpty(deviceID)) {
                                        List<String> routineID = new ArrayList<>();
                                        routineID.add(((DeviceLock) kgroup.getState().get(deviceID)).getQueueHead()
                                                .getRoutineID());

                                        gatherQuorumForDeviceLockAcquisition(routineID.get(0),
                                                ((DeviceLock) kgroup.getState().get(deviceID)).getQueueHead()
                                                        .getRoutineSeqNo(),
                                                deviceID);
                                    }

                                    // if there are requests in the newLockRequests queue
                                    if (!deviceKGroupInChargeOf(deviceID).isNewLockRequestsEmpty(deviceID)) {
                                        // resend LOCK_REQUEST_QUORUM to move lock requests to the queue
                                        Map<Integer, LockRequest> newLockRequests = deviceKGroupInChargeOf(deviceID)
                                                .getNewLockRequests(deviceID);
                                        for (Entry<Integer, LockRequest> req : newLockRequests.entrySet()) {
                                            gatherQuorumForDeviceLockRequest(req.getValue().getRoutineID(),
                                                    req.getValue().getRoutineSeqNo(), deviceID, req.getKey());
                                        }
                                    }
                                }
                                break;

                            case ROUTINE:
                                // 1. resend lock request for next device to lock
                                // in case a lock request was lost before
                                // 2. if routine had previously started execution
                                // check to see if it has stopped or not
                                for (String routineID : kgroup.entitiesIDs) {
                                    for (Integer routineSeqNo : routineKGroupInChargeOf(routineID)
                                            .getRoutineSeqNos(routineID)) {
                                        if (routineKGroupInChargeOf(routineID).isRoutineAcquiringLocks(routineID,
                                                routineSeqNo)) {
                                            requestDeviceLockForRoutine(routineID, routineSeqNo);
                                        } else if (routineKGroupInChargeOf(routineID).isRoutineExecuting(routineID,
                                                routineSeqNo)) {
                                            int routineEndTS = routineKGroupInChargeOf(routineID)
                                                    .getRoutineEndTS(routineID, routineSeqNo);
                                            if (routineEndTS <= ts && routineEndTS != -1) {
                                                releaseDeviceLocksForRoutine(routineID, routineSeqNo);
                                            } else if (routineEndTS > ts) {
                                                events.get(routineEndTS).add(new Event(routineID, routineSeqNo));
                                            }
                                        }
                                    }
                                }
                                break;
                            default:
                                break;
                        }
                        break;
                    case TRIGGER_QUORUM_ACK:
                        String routineId = msg.payload.entitiesIDs.get(0);
                        int routineSeqNo = ((TriggerQuorumMessagePayload) quorumMsgInfo
                                .getMessagePayload()).routineSeqNo;
                        requestDeviceLockForRoutine(routineId,
                                routineKGroupInChargeOf(routineId).setRoutineTriggered(routineId, routineSeqNo));
                        routineMetrics.recordTriggerAckTime(routineId, routineSeqNo, ts);
                        if (debug) {
                            System.out.println("Routine " + routineId + " k-group: routine "
                                    + msg.payload.entitiesIDs.get(0) + "-" + routineSeqNo + " triggered at time " + ts);
                        }

                        break;
                    case LOCK_REQUEST_QUORUM_ACK:
                        LockRequestQuorumAckMessagePayload lockRequestQuorumAckMessagePayload = (LockRequestQuorumAckMessagePayload) replyPayload;
                        String deviceID = lockRequestQuorumAckMessagePayload.entitiesIDs.get(0);
                        List<String> routineID = new ArrayList<>();
                        // if the request has been previously acknowledged, do not do it again
                        if (!((DeviceKGroup) kgroup).isLockRequestGranted(deviceID,
                                lockRequestQuorumAckMessagePayload.routineID,
                                lockRequestQuorumAckMessagePayload.routineSeqNo)) {
                            ((DeviceKGroup) kgroup).lockRequestReplicated(deviceID,
                                    lockRequestQuorumAckMessagePayload.routineID,
                                    lockRequestQuorumAckMessagePayload.routineSeqNo,
                                    lockRequestQuorumAckMessagePayload.reqSeqNo);

                            if (debug) {
                                System.out.println("Device " + deviceID + " k-group: lock request granted to routine "
                                        + lockRequestQuorumAckMessagePayload.routineID + "-"
                                        + lockRequestQuorumAckMessagePayload.routineSeqNo + " at time " + ts);
                                System.out.println("Device " + deviceID + " state: " + kgroup.getState().get(deviceID));
                            }

                            routineID.add(lockRequestQuorumAckMessagePayload.routineID);
                            remoteCall(routineKGroupInChargeOf(routineID), (DeviceKGroup) kgroup,
                                    routineKGroupInChargeOf(routineID).getMostProbableLeader(),
                                    MessageType.LOCK_REQUESTED, new LockRequestedMessagePayload(epochNo, routineID,
                                            lockRequestQuorumAckMessagePayload.routineSeqNo, deviceID),
                                    false);

                            // if the device lock is free and this is the only request
                            // in the queue, give it to the new request
                            if (!((DeviceKGroup) kgroup).isLocked(deviceID)
                                    && ((DeviceKGroup) kgroup).getLastLockedReqSeqNo(deviceID)
                                            + 1 == lockRequestQuorumAckMessagePayload.reqSeqNo) {
                                LockRequest nextRoutine = ((DeviceKGroup) kgroup).getQueueHead(deviceID);
                                gatherQuorumForDeviceLockAcquisition(nextRoutine.getRoutineID(),
                                        nextRoutine.getRoutineSeqNo(), deviceID);
                            }
                        }
                        break;
                    case LOCKED_QUORUM_ACK:
                        LockedQuorumAckMessagePayload lockedQuorumAckMessagePayload = (LockedQuorumAckMessagePayload) replyPayload;
                        deviceID = lockedQuorumAckMessagePayload.entitiesIDs.get(0);
                        if (!((DeviceKGroup) kgroup).isLockAcquired(deviceID, lockedQuorumAckMessagePayload.routineID,
                                lockedQuorumAckMessagePayload.routineSeqNo)) {
                            ((DeviceKGroup) kgroup).lock(deviceID, lockedQuorumAckMessagePayload.routineID,
                                    lockedQuorumAckMessagePayload.routineSeqNo, lockedQuorumAckMessagePayload.reqSeqNo);

                            if (debug) {
                                System.out.println("Device " + deviceID + " k-group: lock granted to routine "
                                        + lockedQuorumAckMessagePayload.routineID + "-"
                                        + lockedQuorumAckMessagePayload.routineSeqNo + " at time " + ts);
                                System.out.println("Device " + deviceID + " state: " + kgroup.getState().get(deviceID));
                            }

                            routineID = new ArrayList<>();
                            routineID.add(lockedQuorumAckMessagePayload.routineID);
                            MessagePayload payload = new LockedMessagePayload(epochNo, routineID, deviceID,
                                    lockedQuorumAckMessagePayload.routineSeqNo);
                            remoteCall(routineKGroupInChargeOf(routineID), deviceKGroupInChargeOf(deviceID),
                                    routineKGroupInChargeOf(routineID).getMostProbableLeader(), MessageType.LOCKED,
                                    payload, false);
                        }
                        break;
                    case LOCK_RELEASE_QUORUM_ACK:
                        LockReleaseQuorumAckMessagePayload lockReleaseQuorumAckMessagePayload = (LockReleaseQuorumAckMessagePayload) replyPayload;
                        deviceID = lockReleaseQuorumAckMessagePayload.entitiesIDs.get(0);
                        LockRequest nextRoutine = ((DeviceKGroup) kgroup).releaseLock(deviceID,
                                lockReleaseQuorumAckMessagePayload.routineID,
                                lockReleaseQuorumAckMessagePayload.routineSeqNo,
                                lockReleaseQuorumAckMessagePayload.reqSeqNo);

                        if (debug) {
                            System.out.println("Device " + deviceID + " k-group: lock released for routine "
                                    + lockReleaseQuorumAckMessagePayload.routineID + "-"
                                    + lockReleaseQuorumAckMessagePayload.routineSeqNo + " at time " + ts);
                        }

                        routineID = new ArrayList<>();
                        routineID.add(lockReleaseQuorumAckMessagePayload.routineID);
                        remoteCall(routineKGroupInChargeOf(routineID), deviceKGroupInChargeOf(deviceID),
                                routineKGroupInChargeOf(routineID).getMostProbableLeader(), MessageType.LOCK_RELEASED,
                                new LockReleasedMessagePayload(epochNo, routineID, deviceID,
                                        lockReleaseQuorumAckMessagePayload.routineSeqNo),
                                true);

                        if (nextRoutine != null && !((DeviceKGroup) kgroup).isLockAcquired(deviceID,
                                nextRoutine.getRoutineID(), nextRoutine.getRoutineSeqNo())) {
                            if (debug) {
                                System.out.println("Device " + deviceID
                                        + " k-group: gathering quorum for granting lock to routine " + nextRoutine
                                        + " at time " + ts);
                            }
                            gatherQuorumForDeviceLockAcquisition(nextRoutine.getRoutineID(),
                                    nextRoutine.getRoutineSeqNo(), deviceID);
                        }
                        break;
                    default:
                        break;
                }
                quorumMsgInfo.setApproved(ts);
                if (testType == TestType.INKGROUP_BENCHMARK_WO_FAILURE) {
                    try {
                        kgroup.outputWriter.write("," + quorumMsgInfo.getQuorumTime());
                        if (quorumMsgInfo.getPositiveRepliesCount() == kgroup.size()) {
                            kgroup.outputWriter.flush();
                        }
                    } catch (IOException e) {
                        System.out.println("An error occured while writing to the output file about "
                                + (quorumMsgInfo.getPositiveRepliesCount() == kgroup.size() ? "all k-group " : "")
                                + "quorum time.");
                    }
                }
                quorumMsgsInfo.remove(replyPayload.srcSeqNo);
            }
        }
    }

    public void recvAndProcessMsgs(int curTS) {
        Set<Message> rcvdMsgs;
        Message msg;

        for (KGroup kgroup : deviceKGroups) {
            while (kgroup.isReceivingEndOpen() && !kgroup.getWaitingMessageQueue().isEmpty()) {
                msg = kgroup.getWaitingMessageQueue().remove(0);
                if (!kgroup.isMemberOfKGroup(myID)) {
                    if (kgroup.getOldLeader() == myID) {
                        msg.dst = kgroup.getLeader();
                    } else {
                        msg.dst = kgroup.getMostProbableLeader();
                    }
                    network.sendMsg(msg); // could be remote call
                } else {
                    if (kgroup.getLeader() == myID) {
                        processMessage(msg, kgroup);
                    } else {
                        msg.dst = kgroup.getLeader();
                        network.sendMsg(msg); // could be remote call
                    }
                }
            }
        }
        for (KGroup kgroup : routineKGroups) {
            while (kgroup.isReceivingEndOpen() && !kgroup.getWaitingMessageQueue().isEmpty()) {
                msg = kgroup.getWaitingMessageQueue().remove(0);
                if (!kgroup.isMemberOfKGroup(myID)) {
                    if (kgroup.getOldLeader() == myID) {
                        msg.dst = kgroup.getLeader();
                    } else {
                        msg.dst = kgroup.getMostProbableLeader();
                    }
                    network.sendMsg(msg); // could be remote call
                } else {
                    if (kgroup.getLeader() == myID) {
                        processMessage(msg, kgroup);
                    } else {
                        msg.dst = kgroup.getLeader();
                        network.sendMsg(msg); // could be remote call
                    }
                }
            }
        }

        rcvdMsgs = network.recvPastMsgs(myID, curTS);
        KGroup kgroup;
        for (Message rcvdMsg : rcvdMsgs) {

            switch (rcvdMsg.payload.kGroupType) {
                case DEVICE:
                    kgroup = deviceKGroupInChargeOf(rcvdMsg.payload.entitiesIDs);
                    break;
                case ROUTINE:
                    kgroup = routineKGroupInChargeOf(rcvdMsg.payload.entitiesIDs);
                    break;
                default:
                    kgroup = null;
                    break;
            }

            switch (rcvdMsg.type) {
                case ELECTION:
                case ELECTION_ACK:
                case ELECTED:
                case ELECTED_ACK:
                case KGROUP_STATE_REQUEST:
                case KGROUP_STATE:
                case KGROUP_STATE_DISTRIBUTION:
                case KGROUP_STATE_ACK:
                    if (kgroup == null) {
                        System.err.println("TOP Msg received: referenced k-group in charge of "
                                + rcvdMsg.payload.kGroupType.toString() + " "
                                + rcvdMsg.payload.entitiesIDs + " which does not exist!");
                        continue;
                    }
                case NODE_FAILURE:
                case NODE_FAILURE_ACK:
                case DEVICE_STATE_CHECK:
                case DEVICE_STATE:
                case DEVICE_READING_REQUEST:
                case DEVICE_READING:
                    processMessage(rcvdMsg, kgroup);
                    break;
                default:
                    if (kgroup == null) {
                        System.err.println("DEFAULT Msg " + rcvdMsg.type + " received: referenced k-group in charge of "
                                + rcvdMsg.payload.kGroupType.toString() + " "
                                + rcvdMsg.payload.entitiesIDs + " which does not exist!");
                        continue;
                    }
                    if (kgroup.isReceivingEndOpen()) {
                        processMessage(rcvdMsg, kgroup);
                    } else {
                        kgroup.addToWaitingMessageQueue(rcvdMsg);
                    }
            }
        }
    }

    public boolean isLeaderOfDeviceKGroup(String deviceID) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(deviceID)) {
                return kgroup.isMemberOfKGroup(myID) && kgroup.getLeader() == myID;
            }
        }
        return false;
    }

    public boolean isLeaderOfDeviceKGroup(List<String> devicesIDs) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devicesIDs)) {
                return kgroup.isMemberOfKGroup(myID) && kgroup.getLeader() == myID;
            }
        }
        return false;
    }

    public boolean isLeaderOfRoutineKGroup(String routineID) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(routineID)) {
                return kgroup.isMemberOfKGroup(myID) && kgroup.getLeader() == myID;
            }
        }
        return false;
    }

    public boolean isLeaderOfRoutineKGroup(List<String> routinesIDs) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(routinesIDs)) {
                return kgroup.isMemberOfKGroup(myID) && kgroup.getLeader() == myID;
            }
        }
        return false;
    }

    public boolean isMemberOfDeviceKGroup(String deviceID) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(deviceID)) {
                return kgroup.isMemberOfKGroup(myID);
            }
        }
        return false;
    }

    public boolean isMemberOfDeviceKGroup(List<String> devicesIDs) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devicesIDs)) {
                return kgroup.isMemberOfKGroup(myID);
            }
        }
        return false;
    }

    public boolean isMemberOfRoutineKGroup(String routineID) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(routineID)) {
                return kgroup.isMemberOfKGroup(myID);
            }
        }
        return false;
    }

    public boolean isMemberOfRoutineKGroup(List<String> routinesIDs) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(routinesIDs)) {
                return kgroup.isMemberOfKGroup(myID);
            }
        }
        return false;
    }

    public DeviceKGroup deviceKGroupInChargeOf(String deviceID) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(deviceID)) {
                return kgroup;
            }
        }
        return null;
    }

    public DeviceKGroup deviceKGroupInChargeOf(List<String> devicesIDs) {
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isInChargeOf(devicesIDs)) {
                return kgroup;
            }
        }
        return null;
    }

    public RoutineKGroup routineKGroupInChargeOf(String routineID) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(routineID)) {
                return kgroup;
            }
        }
        return null;
    }

    public RoutineKGroup routineKGroupInChargeOf(List<String> routinesIDs) {
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isInChargeOf(routinesIDs)) {
                return kgroup;
            }
        }
        return null;
    }

    public void unicast(String dst, MessageType type, MessagePayload payload, int msgSeqNo) {
        Message msg = new Message(myID, msgSeqNo, dst, ts + network.getRouteOWD(myID, dst), type, payload);
        network.sendMsg(msg);
    }

    public void multicast(KGroup kgroup, MessageType type, MessagePayload payload, int msgSeqNo, boolean oldKGroup) {
        for (String dst : oldKGroup ? kgroup.oldNodesIDs : kgroup.curNodesIDs) {
            Message msg = new Message(myID, msgSeqNo, dst, ts + network.getRouteOWD(myID, dst), type, payload);
            network.sendMsg(msg);
        }
    }

    public void multicast(List<String> recipients, MessageType type, int msgSeqNo, MessagePayload payload) {
        for (String dst : recipients) {
            Message msg = new Message(myID, msgSeqNo, dst, ts + network.getRouteOWD(myID, dst), type, payload);
            network.sendMsg(msg);
        }
    }

    public void remoteCall(RoutineKGroup routineKGroup, DeviceKGroup deviceKGroup, String dst, MessageType type,
            MessagePayload payload, boolean boundedWait) {
        // calculate the message's RTT based on the hops
        int routeOWD = network.getRouteOWD(myID, dst);
        seqNo++;
        unicastMsgsInfo.put(seqNo, new UnicastMsgInfo(type, payload, routineKGroup, deviceKGroup, dst, boundedWait,
                routeOWD, ts, epochNo));
        Message msg = new Message(myID, seqNo, dst, ts + routeOWD, type, payload);
        network.sendMsg(msg);
    }

    // quorum is currently implemented synchronously - might need to change
    // could be implemented in a thread: when it is done it can set a flag that is
    // periodically checked upon by the manager
    public void quorum(KGroup kgroup, MessageType msgType, MessagePayload payload, boolean oldKGroup,
            boolean boundedWait) {
        int routeRTT, maxRouteRTT = -1;
        for (String dst : (oldKGroup ? kgroup.oldNodesIDs : kgroup.curNodesIDs)) {
            routeRTT = network.getRouteRTT(myID, dst);
            if (maxRouteRTT == -1 || routeRTT > maxRouteRTT) {
                maxRouteRTT = routeRTT;
            }
        }
        seqNo++;
        quorumMsgsInfo.put(seqNo, new QuorumMsgInfo(msgType, payload, kgroup, boundedWait, maxRouteRTT, ts, epochNo));
        multicast(kgroup, msgType, payload, seqNo, oldKGroup);
    }

    public boolean isNodeOnline(String nodeID) {
        return membershipList.get(nodeID) == Membership.ONLINE;
    }

    // called by simulator
    public void nodeJoined(String joinedNodeID, boolean instant) {
        membershipList.put(joinedNodeID, Membership.ONLINE);
        if (instant) {
            return;
        }
    }

    // called by simulator
    public void nodeFailureDetected(String failedNodeID, boolean instant) {
        membershipList.replace(failedNodeID, Membership.OFFLINE);
        if (instant) {
            return;
        }
        MessagePayload payload;
        List<String> recipients = new ArrayList<>();
        String recipient;
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isMemberOfKGroup(failedNodeID)) {
                payload = new NodeFailureMessagePayload(kgroup.type, kgroup.entitiesIDs, failedNodeID);
                if (kgroup.isMemberOfKGroup(myID)) {
                    recipient = kgroup.getLeader();
                } else {
                    recipient = kgroup.getMostProbableLeader();
                }
                if (!recipients.contains(recipient)) {
                    remoteCall((RoutineKGroup) null, kgroup, recipient, MessageType.NODE_FAILURE, payload, false);
                    recipients.add(recipient);
                }
            }
        }
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isMemberOfKGroup(failedNodeID)) {
                payload = new NodeFailureMessagePayload(kgroup.type, kgroup.entitiesIDs, failedNodeID);
                if (kgroup.isMemberOfKGroup(myID)) {
                    recipient = kgroup.getLeader();
                } else {
                    recipient = kgroup.getMostProbableLeader();
                }
                if (!recipients.contains(recipient)) {
                    remoteCall(kgroup, (DeviceKGroup) null, recipient, MessageType.NODE_FAILURE, payload, false);
                    recipients.add(recipient);
                }
            }
        }
    }

    // called by node when it is informed of the failure by a fellow node
    private void handleNodeFailure(String failedNodeID) {
        membershipList.replace(failedNodeID, Membership.OFFLINE);
        for (DeviceKGroup kgroup : deviceKGroups) {
            if (kgroup.isMemberOfKGroup(failedNodeID) && kgroup.isMemberOfKGroup(myID)) {
                kgroup.updateFailedNodes(failedNodeID);
                if (kgroup.getLeader() == myID) {
                    if (kgroup.getCountFailedNodes() >= F) {
                        List<String> replacementNodes = kgroup.replaceFailedNodes(membershipList);
                        replaceKGroupNodes(kgroup, replacementNodes);
                    }
                } else if (kgroup.getLeader() == failedNodeID) {
                    leaderElection(kgroup, LeaderElectionCause.LEADER_FAILURE);
                }
            }
        }
        for (RoutineKGroup kgroup : routineKGroups) {
            if (kgroup.isMemberOfKGroup(failedNodeID) && kgroup.isMemberOfKGroup(myID)) {
                kgroup.updateFailedNodes(failedNodeID);
                if (kgroup.getLeader() == myID) {
                    if (kgroup.getCountFailedNodes() >= F) {
                        List<String> replacementNodes = kgroup.replaceFailedNodes(membershipList);
                        replaceKGroupNodes(kgroup, replacementNodes);
                    }
                } else if (kgroup.getLeader() == failedNodeID) {
                    leaderElection(kgroup, LeaderElectionCause.LEADER_FAILURE);
                }
            }
        }
    }

    public void replaceKGroupNodes(KGroup kgroup, List<String> replacementNodes) {
        NodeRecruitmentRequestMessagePayload payload;
        payload = new NodeRecruitmentRequestMessagePayload(epochNo, kgroup.type, kgroup.getState());

        for (String nodeID : replacementNodes) {
            switch (kgroup.type) {
                case ROUTINE:
                    remoteCall((RoutineKGroup) kgroup, (DeviceKGroup) null, nodeID,
                            MessageType.NODE_RECRUITEMENT_REQUEST, payload, true);
                    break;
                case DEVICE:
                    remoteCall((RoutineKGroup) null, (DeviceKGroup) kgroup, nodeID,
                            MessageType.NODE_RECRUITEMENT_REQUEST, payload, true);
            }

        }
        kgroup.resetCountFailedNodes();
    }

    // Bully Leader Election Algorithm
    public void leaderElection(KGroup kgroup, LeaderElectionCause cause) {
        if ((cause == LeaderElectionCause.NEW_EPOCH && kgroup.getLeader() == null) ||
                (cause == LeaderElectionCause.LEADER_FAILURE && !kgroup.curNodesIDs.contains(kgroup.getLeader())) ||
                (kgroup.electionStage == LeaderElectionStage.NOT_STARTED)) {
            kgroup.leaderElectionTime = ts;
            kgroup.closeReceivingEnd();
            kgroup.electionStage = LeaderElectionStage.ONGOING;

            // current node is the bully
            if (myID == kgroup.getMostProbableLeader()) {
                quorum(kgroup, MessageType.ELECTED,
                        new ElectedMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, cause), false, true);
                if (kgroup.type.equals(KGroupType.ROUTINE)) {
                    nodeMetric.startRtnRoles(NodeRole.LEADER, kgroup.entitiesIDs, ts);
                } else {
                    nodeMetric.startDevRoles(NodeRole.LEADER, kgroup.entitiesIDs, ts);
                }
            } else {
                // initiate an election
                MessagePayload payload = new ElectionMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, cause);
                for (String nodeID : kgroup.curNodesIDs) {
                    // send Election msg to every node in the k-group which is more appropriate for
                    // leader
                    if (kgroup.moreAppr(nodeID, myID)) {
                        switch (kgroup.type) {
                            case ROUTINE:
                                remoteCall((RoutineKGroup) kgroup, (DeviceKGroup) null, nodeID, MessageType.ELECTION,
                                        payload, true);
                                break;
                            case DEVICE:
                                remoteCall((RoutineKGroup) null, (DeviceKGroup) kgroup, nodeID, MessageType.ELECTION,
                                        payload, true);
                        }
                    }
                }
                kgroup.electionStage = LeaderElectionStage.WAITING_ON_ELECTION_ACK;
            }
        }
    }

    public void stateTransfer(KGroup kgroup, LeaderElectionCause cause) {
        // leader election is complete -- moving on to state transfer now
        if (kgroup.getLeader() == myID && epochNo != 1 && kgroup.stateTransferStage != StateTransferStage.COMPLETE) {
            kgroup.stateTransferTime = ts;
            kgroup.stateTransferStage = StateTransferStage.ONGOING;
            if (cause == LeaderElectionCause.NEW_EPOCH) {
                RoutineKGroup routineKGroup = null;
                DeviceKGroup deviceKGroup = null;
                if (kgroup instanceof RoutineKGroup) {
                    routineKGroup = (RoutineKGroup) kgroup;
                } else if (kgroup instanceof DeviceKGroup) {
                    deviceKGroup = (DeviceKGroup) kgroup;
                }
                // only request state transfer if current node was not the leader in the last
                // epoch as well
                if (kgroup.getOldLeader() != myID) {
                    remoteCall(routineKGroup, deviceKGroup, kgroup.getMostProbableOldLeader(),
                            MessageType.KGROUP_STATE_REQUEST,
                            new KGroupStateRequestMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs, cause),
                            true);
                } else {
                    quorum(kgroup, MessageType.KGROUP_STATE_DISTRIBUTION, new KGroupStateDistributionMessagePayload(
                            epochNo, kgroup.type, kgroup.getState(), kgroup.getSeqNos()), false, false);
                }
            } else {
                quorum(kgroup, MessageType.LOCAL_KGROUP_STATE_REQUEST,
                        new LocalKGroupStateRequestMessagePayload(epochNo, kgroup.type, kgroup.entitiesIDs), false,
                        false);
            }
        } else if (epochNo == 1) {
            kgroup.openReceivingEnd();
        }
    }

    // checkOn* methods initiate checks by sending messages to the appropriate nodes

    public void checkOnCondition(String routineID, RoutineCondition condition) {
        MessagePayload payload = null;
        MessageType type = null;
        String deviceID = null;
        if (condition instanceof DeviceStateCondition) {
            deviceID = ((DeviceStateCondition) condition).deviceID;
            payload = new DeviceStateCheckMessagePayload();
            type = MessageType.DEVICE_STATE_CHECK;
        } else if (condition instanceof DeviceReadingCondition) {
            deviceID = ((DeviceReadingCondition) condition).deviceID;
            payload = new DeviceReadingRequestMessagePayload();
            type = MessageType.DEVICE_READING_REQUEST;
        }
        remoteCall(routineKGroupInChargeOf(routineID), null, deviceID, type, payload, false);
    }

    public void checkOnStatement(String routineID, RoutineStatement statement) {
        if (statement instanceof Statement) {
            checkOnStatement(routineID, (Statement) statement);
        } else if (statement instanceof NotStatement) {
            checkOnStatement(routineID, (NotStatement) statement);
        } else if (statement instanceof AndStatement) {
            checkOnStatement(routineID, (AndStatement) statement);
        } else if (statement instanceof OrStatement) {
            checkOnStatement(routineID, (OrStatement) statement);
        }
    }

    public void checkOnStatement(String routineID, Statement statement) {
        checkOnCondition(routineID, statement.getCondition());
    }

    public void checkOnStatement(String routineID, NotStatement statement) {
        checkOnStatement(routineID, statement.getInnerStatement());
    }

    public void checkOnStatement(String routineID, AndStatement statement) {
        for (RoutineStatement innerStatement : statement.getInnerStatements()) {
            checkOnStatement(routineID, innerStatement);
        }
    }

    public void checkOnStatement(String routineID, OrStatement statement) {
        for (RoutineStatement innerStatement : statement.getInnerStatements()) {
            checkOnStatement(routineID, innerStatement);
        }
    }

    public void checkOnRoutine(String routineID) {
        checkOnStatement(routineID, ((DetailedRoutine) routines.get(routineID)).getConditions());
    }

    // is*Satisfied methods check on already received updates to find out whether
    // a routine has been triggered

    public boolean isConditionSatisfied(RoutineCondition condition) {
        if (condition instanceof TimeCondition) {
            return isConditionSatisfied((TimeCondition) condition);
        } else if (condition instanceof DeviceStateCondition) {
            return isConditionSatisfied((DeviceStateCondition) condition);
        } else if (condition instanceof DeviceReadingCondition) {
            return isConditionSatisfied((DeviceReadingCondition) condition);
        } else {
            return false;
        }
    }

    public boolean isConditionSatisfied(TimeCondition condition) {
        switch (condition.getRelation()) {
            case EQUAL:
                return OffsetTime.now().isEqual(condition.value);
            case GREATER:
                return OffsetTime.now().isAfter(condition.value);
            case GREATER_EQUAL:
                return OffsetTime.now().isAfter(condition.value) || OffsetTime.now().isEqual(condition.value);
            case LESS:
                return OffsetTime.now().isBefore(condition.value);
            case LESS_EQUAL:
                return OffsetTime.now().isBefore(condition.value) || OffsetTime.now().isEqual(condition.value);
            case NOT_EQUAL:
                return !OffsetTime.now().isEqual(condition.value);
            default:
                // could throw exception here alternatively
                return false;
        }
    }

    public boolean isConditionSatisfied(DeviceStateCondition condition) {
        if (deviceStates.containsKey(condition.deviceID)) {
            boolean reply = deviceStates.get(condition.deviceID);
            switch (condition.getRelation()) {
                case EQUAL:
                    return reply == condition.value;
                case NOT_EQUAL:
                    return reply != condition.value;
                default:
                    return false;
            }
        }
        return false;
    }

    public boolean isConditionSatisfied(DeviceReadingCondition condition) {
        if (deviceReadings.containsKey(condition.deviceID)) {
            float reply = deviceReadings.get(condition.deviceID);
            switch (condition.getRelation()) {
                case EQUAL:
                    return reply == condition.value;
                case NOT_EQUAL:
                    return reply != condition.value;
                case GREATER:
                    return reply > condition.value;
                case GREATER_EQUAL:
                    return reply >= condition.value;
                case LESS:
                    return reply < condition.value;
                case LESS_EQUAL:
                    return reply <= condition.value;
                default:
                    return false;
            }
        }
        return false;
    }

    public boolean isStatementSatisfied(RoutineStatement statement) {
        if (statement instanceof Statement) {
            return isStatementSatisfied((Statement) statement);
        } else if (statement instanceof NotStatement) {
            return isStatementSatisfied((NotStatement) statement);
        } else if (statement instanceof AndStatement) {
            return isStatementSatisfied((AndStatement) statement);
        } else if (statement instanceof OrStatement) {
            return isStatementSatisfied((OrStatement) statement);
        } else {
            return false;
        }
    }

    public boolean isStatementSatisfied(Statement statement) {
        return isConditionSatisfied(statement.getCondition());
    }

    public boolean isStatementSatisfied(NotStatement statement) {
        return isStatementSatisfied(statement.getInnerStatement());
    }

    public boolean isStatementSatisfied(AndStatement statement) {
        boolean success = true;
        for (RoutineStatement innerStatement : statement.getInnerStatements()) {
            if (!isStatementSatisfied(innerStatement)) {
                success = false;
                break;
            }
        }
        return success;
    }

    public boolean isStatementSatisfied(OrStatement statement) {
        boolean success = false;
        for (RoutineStatement innerStatement : statement.getInnerStatements()) {
            if (isStatementSatisfied(innerStatement)) {
                success = true;
                break;
            }
        }
        return success;
    }

    public boolean isRoutineTriggered(String routineID) {
        if (routines.get(routineID) instanceof DumbRoutine) {
            return ((DumbRoutine) routines.get(routineID)).isTriggered();
        } else {
            return isStatementSatisfied(((DetailedRoutine) routines.get(routineID)).getConditions());
        }
    }

    public void gatherQuorumForRoutineTrigger(String routineID, int routineSeqNo) {
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(routineID);
        quorum(routineKGroupInChargeOf(routineID), MessageType.TRIGGER_QUORUM,
                new TriggerQuorumMessagePayload(epochNo, entitiesIDs, routineSeqNo), false, false);
    }

    public void requestDeviceLockForRoutine(String routineID, int routineSeqNo) {
        String deviceID = routineKGroupInChargeOf(routineID).nextDeviceToLock(routineID, routineSeqNo,
                routines.get(routineID).getTouchedDevicesIDs());
        if (deviceID == null) {
            return;
        }
        if (debug) {
            System.out.println("Next device for routine " + routineID + "-" + routineSeqNo + " to lock: " + deviceID);
        }
        DeviceKGroup deviceKGroup = deviceKGroupInChargeOf(deviceID);
        RoutineKGroup routineKGroup = routineKGroupInChargeOf(routineID);
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(deviceID);
        LockRequestMessagePayload payload;
        payload = new LockRequestMessagePayload(epochNo, entitiesIDs, routineID, routineSeqNo);
        remoteCall(routineKGroup, deviceKGroup, deviceKGroup.getMostProbableLeader(), MessageType.LOCK_REQUEST, payload,
                false);
    }

    public void gatherQuorumForDeviceLockRequest(String routineID, int routineSeqNo, String deviceID, int reqSeqNo) {
        MessagePayload payload;
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(deviceID);
        payload = new LockRequestQuorumMessagePayload(epochNo, entitiesIDs, routineID, routineSeqNo, reqSeqNo);
        quorum(deviceKGroupInChargeOf(deviceID), MessageType.LOCK_REQUEST_QUORUM, payload, false, false);
    }

    public void gatherQuorumForDeviceLockAcquisition(String routineID, int routineSeqNo, String deviceID) {
        MessagePayload payload;
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(deviceID);
        payload = new LockedQuorumMessagePayload(epochNo, entitiesIDs, routineID,
                deviceKGroupInChargeOf(deviceID).getQueueHeadReqSeqNo(deviceID), routineSeqNo);
        quorum(deviceKGroupInChargeOf(deviceID), MessageType.LOCKED_QUORUM, payload, false, false);
    }

    public void releaseDeviceLocksForRoutine(String routineID, int routineSeqNo) {
        List<String> touchedDevicesIDs = routines.get(routineID).getTouchedDevicesIDs();
        rtn_releasing_locks.put(routineID, new ArrayList<>(touchedDevicesIDs));
        if (debug) {
            System.out.println("routine_releasing_locks: " + rtn_releasing_locks);
        }
        routineMetrics.recordRtnLockReleaseTime(routineID, routineSeqNo, ts);
        routineMetrics.recordMultiDevLockReleaseTime(touchedDevicesIDs, ts);
        if (debug) {
            System.out.println("Routine " + routineID + "-" + routineSeqNo + " finished execution, releasing devices "
                    + touchedDevicesIDs + " at time " + ts);
        }
        routineKGroupInChargeOf(routineID).startReleasingRoutineLocks(routineID, routineSeqNo, touchedDevicesIDs);
        for (String deviceID : touchedDevicesIDs) {
            releaseDeviceLockForRoutine(routineID, routineSeqNo, deviceID);
        }
    }

    public void releaseDeviceLockForRoutine(String routineID, int routineSeqNo, String deviceID) {
        DeviceKGroup deviceKGroup = deviceKGroupInChargeOf(deviceID);
        RoutineKGroup routineKGroup = routineKGroupInChargeOf(routineID);
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(deviceID);
        LockReleaseRequestMessagePayload payload;
        payload = new LockReleaseRequestMessagePayload(epochNo, entitiesIDs, routineID, routineSeqNo);
        remoteCall(routineKGroup, deviceKGroup, deviceKGroup.getMostProbableLeader(), MessageType.LOCK_RELEASE_REQUEST,
                payload, false);
    }

    public void gatherQuorumForDeviceLockRelease(String routineID, int routineSeqNo, String deviceID, int reqSeqNo) {
        MessagePayload payload;
        List<String> entitiesIDs = new ArrayList<>();
        entitiesIDs.add(deviceID);
        payload = new LockReleaseQuorumMessagePayload(epochNo, entitiesIDs, routineID, routineSeqNo, reqSeqNo);
        quorum(deviceKGroupInChargeOf(deviceID), MessageType.LOCK_RELEASE_QUORUM, payload, false, false);
    }

    private void msgCheckpoint(int ts) {
        int lastDotIndex = outputFilename.lastIndexOf('.');
        String fname = outputFilename.substring(0, lastDotIndex) +
                "_" + _checkpoint_counter +
                outputFilename.substring(lastDotIndex);
        network.metric.msgCheckpoint(ts, fname);
        _checkpoint_counter += 1;
    }

    public void addEvent(int ts, Event e) {
        if (!events.containsKey(ts)) {
            events.put(ts, new ArrayList<>());
        }
        events.get(ts).add(e);
    }

    public void finalizeRole() {
        finalizeRole(ts);
    }

    public void finalizeRole(int ending_ts) {
        nodeMetric.finishRtnRoles(ending_ts);
        nodeMetric.finishDevRoles(ending_ts);
    }

    public String getRoleTimeInString() {
        return nodeMetric.getRoleTimeInString();
    }

    public int getCurrentTime() {
        return ts;
    }

    public HashMap<Integer, Integer> getRoleCountOverTime(NodeRole role) {
        return nodeMetric.getRoleCountOverTime(role);
    }
}