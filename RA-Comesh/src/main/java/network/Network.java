package network;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import javafx.util.Pair;

import dijkstra.*;
import network.message.*;

public class Network {
    private boolean debug;
    private int hopOWD;
    // for every node
    private Map<String, Node> neighbors = new ConcurrentHashMap<>();
    private Map<String, Map<String, Pair<String, Integer>>> routingTable = new ConcurrentHashMap<>();
    private Map<String, Map<Integer, Set<Message>>> msgBuffers = new ConcurrentHashMap<>(); // {rcver: {ts: [msg]}}
    private int msgNo;
    private long totalBytes;

    public NetworkMetric metric;

    public Network(String topologyFilename, int hopOWD, boolean debug) {
        this.debug = debug;
        this.hopOWD = hopOWD;
        // create membership list, neighbors and msg listener for each node in the
        // network
        // based on topology-specifying file
        File topologyFile = new File(topologyFilename);
        Graph topology = new Graph();
        String curNode;
        try (Scanner sc = new Scanner(topologyFile)) {
            // create every node in the network
            while (sc.hasNextLine()) {
                curNode = sc.nextLine().split(" ")[0];
                neighbors.put(curNode, new Node(curNode));
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Topology file " + topologyFilename + " does not exist");
            System.exit(-1);
        }

        try (Scanner sc = new Scanner(topologyFile)) {
            // add every node's neighbors
            String[] line = {};
            while (sc.hasNextLine()) {
                line = sc.nextLine().split(" ");
                curNode = line[0];
                for (int i = 1; i < line.length; i++) {
                    neighbors.get(curNode).addDestination(neighbors.get(line[i]), 1);
                }
                msgBuffers.put(curNode, new ConcurrentHashMap<Integer, Set<Message>>());
            }
        } catch (FileNotFoundException ex) {
            System.out.println("Topology file " + topologyFilename + " does not exist");
            System.exit(-1);
        }

        // add every node to the graph
        topology.setNodes(neighbors.values());

        createRoutingTable(topology);

        msgNo = 0;
        totalBytes = 0;

        metric = new NetworkMetric();
    }

    public void printTopology() {
        System.out.println("printTopology:");
        for (String node : neighbors.keySet()) {
            Node value = neighbors.get(node);
            if (value != null) {
                System.out.println(node.toString() + ": " + value.toString());
            }
        }
    }

    public Set<String> getMembershipList() {
        return neighbors.keySet();
    }

    public Set<String> getNeighbors(String curNode) {
        Node node = neighbors.get(curNode);
        if (node != null) {
            return node.getAdjacentNodesIDs();
        } else {
            return null;
        }
    }

    public void createRoutingTable(Graph topology) {
        Graph tempTopology, spanningTree;
        synchronized (routingTable) {
            routingTable.clear();
            for (String srcNodeID : neighbors.keySet()) {
                tempTopology = new Graph(topology);
                if (debug) {
                    tempTopology.print();
                }
                spanningTree = Dijkstra.calculateShortestPathFromSource(tempTopology,
                        tempTopology.getNodeByID(srcNodeID));
                if (debug) {
                    System.out.println(srcNodeID + ": " + spanningTree);
                }

                routingTable.put(srcNodeID, new HashMap<String, Pair<String, Integer>>());
                Map<String, Pair<String, Integer>> singleNodeRoutingTable = routingTable.get(srcNodeID);
                for (Node destNode : spanningTree.getNodes()) {
                    if (destNode.getShortestPath().size() > 1) {
                        singleNodeRoutingTable.put(destNode.getID(), new Pair<String, Integer>(
                                destNode.getShortestPath().get(1).getID(), destNode.getDistance()));
                    } else if (destNode.getShortestPath().size() == 1) {
                        singleNodeRoutingTable.put(destNode.getID(),
                                new Pair<String, Integer>(destNode.getID(), destNode.getDistance()));
                    } else {
                        singleNodeRoutingTable.put(destNode.getID(), null);
                    }
                }
            }
        }
        if (debug) {
            printRoutingTable();
        }
    }

    public void printRoutingTable(String nodeID) {
        System.out.println("Routing table\nsource\tdestinations");
        System.out.print("\t");

        for (String node : neighbors.keySet()) {
            System.out.print(node + "\t");
        }

        synchronized (routingTable) {
            for (String src : neighbors.keySet()) {
                if (src.equals(nodeID)) {
                    System.out.print("\n" + src);
                    Map<String, Pair<String, Integer>> singleNodeRoutingTable = routingTable.get(src);
                    if (singleNodeRoutingTable != null) {
                        for (String dst : singleNodeRoutingTable.keySet()) {
                            System.out.print("\t");
                            Pair<String, Integer> neighbor = singleNodeRoutingTable.get(dst);
                            if (!src.equals(dst) && neighbor != null)
                                System.out.print(neighbor.getKey());
                        }
                        break;
                    }
                }
            }
        }
        System.out.println();
    }

    public void printRoutingTable() {
        System.out.println("Routing table\nsource\tdestinations");
        System.out.print("\t");

        for (String node : neighbors.keySet()) {
            System.out.print(node + "\t");
        }

        synchronized (routingTable) {
            for (String src : neighbors.keySet()) {
                System.out.print("\n" + src);
                Map<String, Pair<String, Integer>> singleNodeRoutingTable = routingTable.get(src);
                if (singleNodeRoutingTable != null) {
                    for (String dst : singleNodeRoutingTable.keySet()) {
                        System.out.print("\t");
                        Pair<String, Integer> neighbor = singleNodeRoutingTable.get(dst);
                        if (!src.equals(dst) && neighbor != null)
                            System.out.print(neighbor.getKey());
                    }
                }
            }
        }
        System.out.println();
    }

    public Map<String, Map<String, Pair<String, Integer>>> getRoutingTable() {
        return routingTable;
    }

    public Map<String, Pair<String, Integer>> getRoutingTable(String nodeID) {
        return routingTable.get(nodeID);
    }

    public int getRouteOWD(String src, String dst) {
        int routeOWD;
        if (src.equals(dst)) {
            routeOWD = 0;
        } else if (getRoutingTable(src).get(dst) == null) {
            System.out.println("from " + src + " to " + dst + ": no routing entry?");
            routeOWD = 0;
        } else {
            routeOWD = getRoutingTable(src).get(dst).getValue() * hopOWD;
        }
        return routeOWD;
    }

    public int getRouteRTT(String src, String dst) {
        return getRouteOWD(src, dst) * 2;
    }

    public void nodeJoined(String newNodeID, List<String> newNodeNeighbors) {
        msgBuffers.put(newNodeID, new ConcurrentHashMap<Integer, Set<Message>>());

        neighbors.put(newNodeID, new Node(newNodeID));
        for (String nodeID : neighbors.keySet()) {
            if (newNodeNeighbors.contains(nodeID)) {
                neighbors.get(newNodeID).addDestination(neighbors.get(nodeID), 1);
                neighbors.get(nodeID).addDestination(neighbors.get(newNodeID), 1);
            }
        }

        Graph topology = new Graph();
        topology.setNodes(neighbors.values());
        createRoutingTable(topology);
    }

    public void nodeFailed(String failedNodeID) {
        msgBuffers.remove(failedNodeID);

        Node failedNode = neighbors.get(failedNodeID);
        for (String nodeID : neighbors.keySet()) {
            if (neighbors.get(nodeID).getAdjacentNodesIDs().contains(failedNodeID)) {
                neighbors.get(nodeID).removeDestination(failedNode);
            }
        }
        neighbors.remove(failedNodeID);

        Graph topology = new Graph();
        topology.setNodes(neighbors.values());
        createRoutingTable(topology);
    }

    public void sendMsg(Message msg) {
        fwdMsg(msg, msg.src);
    }

    private void fwdMsg(final Message msg, final String curr) {
        if (curr.equals(msg.dst)) {
            if (msgBuffers.get(msg.dst) == null) {
                msgBuffers.put(msg.dst, new ConcurrentHashMap<Integer, Set<Message>>());
            }
            if (msgBuffers.get(msg.dst).get(msg.dstTS) == null) {
                msgBuffers.get(msg.dst).put(msg.dstTS, new HashSet<Message>());
            }
            msgBuffers.get(msg.dst).get(msg.dstTS).add(msg);
            metric.recordE2EMsg(msg.src, msg.dst, msg.type);
        } else {
            msgNo++;
            // turn msg to byte array and get its length
            totalBytes += msg.getByteSize();
            Map<String, Pair<String, Integer>> singleNodeRoutingTable = routingTable.get(curr);
            if (singleNodeRoutingTable != null) {
                String nodeID = singleNodeRoutingTable.get(msg.dst).getKey();
                if (nodeID != null) {
                    fwdMsg(msg, nodeID);
                    metric.recordH2HMsg(curr, nodeID, msg.type);
                } else {
                    System.out.println(msg + " failed at node " + curr);
                }
            } else {
                System.out.println(msg + " failed at node " + curr);
            }
        }
    }

    public boolean existUnreadMsgs() {
        for (String recipient : msgBuffers.keySet()) {
            for (Entry<Integer, Set<Message>> e : msgBuffers.get(recipient).entrySet()) {
                if (e.getValue() != null) {
                    if (!e.getValue().isEmpty()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public Set<Message> recvCurMsgs(String recipient, int ts) {
        if (msgBuffers.get(recipient) != null) {
            Set<Message> msgSet = (Set<Message>) ((HashSet<Message>) msgBuffers.get(recipient).get(ts)).clone();
            msgBuffers.get(recipient).get(ts).clear();
            return msgSet;
        } else {
            return null;
        }
    }

    public Set<Message> recvPastMsgs(String recipient, int ts) {
        Set<Message> msgSet = new HashSet<>();
        for (Entry<Integer, Set<Message>> e : msgBuffers.get(recipient).entrySet()) {
            if (e.getKey() <= ts && e.getValue() != null) {
                msgSet.addAll(recvCurMsgs(recipient, e.getKey()));
            }
        }
        return msgSet;
    }

    public Set<Message> recvAllMsgs(String recipient) {
        Set<Message> msgSet = new HashSet<>();
        for (Entry<Integer, Set<Message>> e : msgBuffers.get(recipient).entrySet()) {
            if (e.getValue() != null) {
                msgSet.addAll(recvCurMsgs(recipient, e.getKey()));
            }
        }
        return msgSet;
    }

    public int totalMessages() {
        return msgNo;
    }

    public double averageBandwidth(int timeUnitsPassed) {
        return totalBytes / (double) timeUnitsPassed;
    }
}