package network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map.Entry;

import org.junit.jupiter.api.Test;

import javafx.util.Pair;
import kgroup.KGroupType;
import kgroup.LeaderElectionCause;
import network.message.Message;
import network.message.MessageType;
import network.message.payload.election.ElectedMessagePayload;

public class NetworkTest {
    static Integer count = 0;

    @Test
    public void shouldMatchExpectedMembership() {
        Network network = new Network("CoMesh/CoMesh/workloads/sample_device_topology.txt", 1, false);
        Set<String> nodes = network.getMembershipList();
        Set<String> expected = new HashSet<String>();
        expected.add("0");
        expected.add("1");
        expected.add("2");
        expected.add("3");
        expected.add("4");
        expected.add("5");
        expected.add("6");
        expected.add("7");
        expected.add("8");
        assertEquals(expected, nodes, "Topology devices are not expected");
    }

    @Test
    public void shouldMatchExpectedNeighbors() {
        Network network = new Network("CoMesh/CoMesh/workloads/device_topology_d27_grid3,3,3.txt", 1,
                false);
        Set<String> neighbors = network.getNeighbors("3");
        Set<String> expected = new HashSet<>();
        expected.add("0");
        expected.add("4");
        expected.add("6");
        expected.add("12");
        assertEquals(expected, neighbors);
    }

    @Test
    public void shouldMatchExpectedRoutingTable() {
        Network network = new Network("CoMesh/CoMesh/workloads/device_topology_d27_grid3,3,3.txt", 1,
                false);
        boolean allGood = true;

        for (Entry<String, Pair<String, Integer>> routing : network.getRoutingTable("3").entrySet()) {
            switch (routing.getKey()) {
                case "0":
                    if (!routing.getValue().getKey().equals("0")) {
                        System.out.println("0: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "1":
                    if (!routing.getValue().getKey().equals("4") && !routing.getValue().getKey().equals("0")) {
                        System.out.println("1: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "2":
                    if (!routing.getValue().getKey().equals("0") && !routing.getValue().getKey().equals("4")) {
                        System.out.println("2: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "3":
                    if (routing.getValue() != null) {
                        System.out.println("3: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "4":
                    if (!routing.getValue().getKey().equals("4")) {
                        System.out.println("4: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "5":
                    if (!routing.getValue().getKey().equals("4")) {
                        System.out.println("5: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "6":
                    if (!routing.getValue().getKey().equals("6")) {
                        System.out.println("6: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "7":
                    if (!routing.getValue().getKey().equals("4") && !routing.getValue().getKey().equals("6")) {
                        System.out.println("7: " + routing.getValue());
                        allGood = false;
                    }
                    break;
                case "8":
                    if (!routing.getValue().getKey().equals("4") && !routing.getValue().getKey().equals("6")) {
                        System.out.println("8: " + routing.getValue());
                        allGood = false;
                    }
                    break;
            }
        }
        assertTrue(allGood, "Routing table is incorrect!");
    }

    @Test
    public void sendAndRecvOneMessage() {
        Network network = new Network("CoMesh/CoMesh/workloads/sample_device_topology.txt", 1, false);
        Message msg = new Message("0", 1, "8", network.getRouteOWD("0", "8"), MessageType.ELECTED,
                new ElectedMessagePayload(1, KGroupType.DEVICE, new ArrayList<String>(),
                        LeaderElectionCause.NEW_EPOCH));

        Thread thread = new Thread(new Runnable() {
            public void run() {
                Message rcvdMsg = (Message) network.recvAllMsgs("8").toArray()[0];
                assertEquals(msg, rcvdMsg);
                assertEquals(msg.src, "0");
            }
        }, "rcv test");

        network.sendMsg(msg);
        thread.start();
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void sendAndRecvSeveralMessages() {
        Network network = new Network("CoMesh/CoMesh/workloads/sample_device_topology.txt", 1, false);
        Thread[] threads = new Thread[9];
        Message[][] msgs = new Message[9][9];
        int i;

        System.out.println("Sending 81 messages in the whole network");

        for (i = 0; i < 9; i++) {
            threads[i] = new Thread(new Runnable() {
                String myID;

                public Runnable init(String i) {
                    myID = i;
                    return this;
                }

                public void run() {
                    Set<Message> rcvdMsgs = network.recvAllMsgs(myID);
                    for (Message rcvdMsg : rcvdMsgs) {
                        if (msgs[Integer.parseInt(rcvdMsg.src)][Integer.parseInt(rcvdMsg.dst)].equals(rcvdMsg)) {
                            // System.out.println(rcvdMsg.src + " -> " + rcvdMsg.dst + " done");
                            synchronized (count) {
                                count++;
                            }
                        }
                    }
                }
            }.init(String.valueOf(i)), "rcv test thread at node " + i);
            for (int j = 0; j < 9; j++) {
                msgs[i][j] = new Message(String.valueOf(i), j, String.valueOf(j),
                        j + network.getRouteOWD(String.valueOf(i), String.valueOf(j)), MessageType.ELECTED,
                        new ElectedMessagePayload(1, KGroupType.DEVICE, new ArrayList<String>(),
                                LeaderElectionCause.NEW_EPOCH));
                network.sendMsg(msgs[i][j]);
            }
        }
        for (i = 0; i < 9; i++) {
            threads[i].start();
        }

        try {
            for (i = 0; i < 9; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Overall messages received: " + count);
        assertEquals(81, count);
    }

    @Test
    public void nodeJoinTest() {
        Network network = new Network("CoMesh/CoMesh/workloads/sample_device_topology.txt", 1, true);

        List<String> newNodeNeighbors = new ArrayList<>();
        newNodeNeighbors.add("6");
        network.nodeJoined("9", newNodeNeighbors);

        Thread[] threads = new Thread[10];
        Message[][] msgs = new Message[10][10];
        int i;

        System.out.println("Sending 100 messages in the whole network");

        for (i = 0; i < 10; i++) {
            threads[i] = new Thread(new Runnable() {
                String myID;

                public Runnable init(String i) {
                    myID = i;
                    return this;
                }

                public void run() {
                    Set<Message> rcvdMsgs = network.recvAllMsgs(myID);
                    for (Message rcvdMsg : rcvdMsgs) {
                        if (msgs[Integer.parseInt(rcvdMsg.src)][Integer.parseInt(rcvdMsg.dst)].equals(rcvdMsg)) {
                            // System.out.println(rcvdMsg.src + " -> " + rcvdMsg.dst + " done");
                            synchronized (count) {
                                count++;
                            }
                        }
                    }
                }
            }.init(String.valueOf(i)), "rcv test thread at node " + i);
            for (int j = 0; j < 10; j++) {
                msgs[i][j] = new Message(String.valueOf(i), j, String.valueOf(j),
                        j + network.getRouteOWD(String.valueOf(i), String.valueOf(j)), MessageType.ELECTED,
                        new ElectedMessagePayload(1, KGroupType.DEVICE, new ArrayList<String>(),
                                LeaderElectionCause.NEW_EPOCH));
                network.sendMsg(msgs[i][j]);
            }
        }
        for (i = 0; i < 10; i++) {
            threads[i].start();
        }

        try {
            for (i = 0; i < 10; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Overall messages received: " + count);
        assertEquals(100, count);
    }

    @Test
    public void nodeFailureTest() {
        count = 0;
        Network network = new Network("CoMesh/CoMesh/workloads/sample_device_topology.txt", 1, true);
        network.nodeFailed("4");

        Thread[] threads = new Thread[9];
        Message[][] msgs = new Message[9][9];
        int i;

        System.out.println("Sending 64 messages in the whole network");

        for (i = 0; i < 9; i++) {
            if (i != 4) {
                threads[i] = new Thread(new Runnable() {
                    String myID;

                    public Runnable init(String i) {
                        myID = i;
                        return this;
                    }

                    public void run() {
                        Set<Message> rcvdMsgs = network.recvAllMsgs(myID);
                        for (Message rcvdMsg : rcvdMsgs) {
                            if (msgs[Integer.parseInt(rcvdMsg.src)][Integer.parseInt(rcvdMsg.dst)].equals(rcvdMsg)) {
                                // System.out.println(rcvdMsg.src + " -> " + rcvdMsg.dst + " done");
                                synchronized (count) {
                                    count++;
                                }
                            }
                        }
                    }
                }.init(String.valueOf(i)), "rcv test thread at node " + i);
                for (int j = 0; j < 9; j++) {
                    if (j != 4) {
                        msgs[i][j] = new Message(String.valueOf(i), j, String.valueOf(j),
                                j + network.getRouteOWD(String.valueOf(i), String.valueOf(j)), MessageType.ELECTED,
                                new ElectedMessagePayload(1, KGroupType.DEVICE, new ArrayList<String>(),
                                        LeaderElectionCause.NEW_EPOCH));
                        network.sendMsg(msgs[i][j]);
                    }
                }
            }
        }
        for (i = 0; i < 9; i++) {
            if (i != 4)
                threads[i].start();
        }

        try {
            for (i = 0; i < 9; i++) {
                if (i != 4)
                    threads[i].join();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Overall messages received: " + count);
        assertEquals(64, count);
    }
}
