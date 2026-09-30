package dijkstra;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

public class Graph {
    private List<Node> nodes = new ArrayList<>();

    public Graph() {
    }

    public Graph(Graph graph) {
        for (Node node : graph.getNodes()) {
            Node newNode = new Node(node.getID());
            nodes.add(newNode);
        }
        Node newNode, newAdjacentNode;
        for (Node node : graph.getNodes()) {
            newNode = getNodeByID(node.getID());
            for (Entry<Node, Integer> adjacentNode : node.getAdjacentNodes().entrySet()) {
                newAdjacentNode = getNodeByID(adjacentNode.getKey().getID());
                newNode.addDestination(newAdjacentNode, adjacentNode.getValue());
            }
        }
    }

    public void addNode(Node nodeA) {
        nodes.add(nodeA);
    }

    public Node getNodeByID(String nodeID) {
        for (Node node : nodes) {
            if (node.getID().equals(nodeID)) {
                return node;
            }
        }
        return null;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<String> getNodesIDs() {
        List<String> IDs = new ArrayList<>();
        for (Node node : nodes) {
            IDs.add(node.getID());
        }
        return IDs;
    }

    public void setNodes(Collection<Node> nodes) {
        this.nodes = new ArrayList<Node>(nodes);
    }

    public void print() {
        System.out.println("topology.print():");
        for (Node node : nodes) {
            System.out.println(node.getID() + ": " + node.getAdjacentNodes());
            System.out.println("distance: " + node.getDistance() + ", shortest path: " + node.getShortestPath());
        }
    }

    @Override
    public String toString() {
        String str = "";
        for (Node node : nodes) {
            str += node.toString() + " | ";
        }
        return str.substring(0, str.length() - 3);
    }
}
