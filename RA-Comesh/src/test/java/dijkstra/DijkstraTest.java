package dijkstra;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class DijkstraTest {
    @Test
    public void shouldReturnExpectedSpanningTree() {
        Node node1 = new Node("D1");
        Node node2 = new Node("D2");
        Node node3 = new Node("D3");
        Node node4 = new Node("D4"); 
        Node node5 = new Node("D5");
        Node node6 = new Node("D6");

        node1.addDestination(node2, 10);
        node1.addDestination(node3, 15);

        node2.addDestination(node4, 12);
        node2.addDestination(node6, 15);

        node3.addDestination(node5, 10);

        node4.addDestination(node5, 2);
        node4.addDestination(node6, 1);

        node6.addDestination(node5, 5);

        Graph graph = new Graph();

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);
        graph.addNode(node4);
        graph.addNode(node5);
        graph.addNode(node6);

        graph = Dijkstra.calculateShortestPathFromSource(graph, node1);

        String[] spanningTree = {"D1-0 -> D2-10 -> D4-22 -> D5-24", "D1-0 -> D2-10", "D1-0 -> D3-15", "D1-0 -> D2-10 -> D4-22", "D1-0 -> D2-10 -> D4-22 -> D6-23"};

        for (Node node: graph.getNodes()) {
            for (String path: spanningTree) {
                if (node.toString().equals(path)) {
                    assertEquals(path, node.toString(), "Dijkstra test failed");
                    System.out.println(node.toString() + " = " + path);
                    break;
                }
            }
        }
    }
}
