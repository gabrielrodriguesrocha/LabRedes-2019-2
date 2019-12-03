package rip;

import java.util.ArrayList;
import java.util.HashMap;
import rip.Packet;

/**
 * Abstract representation of a router.
 */
public class Node {
    public int label, numNodes;
    public boolean dtUpdated, stopComm;
    public HashMap<Integer, Integer> weights;
    public Sender sender;
    public ArrayList<ArrayList<Integer>> distanceTable;

    /**
     * The destination table is actually transposed. That means position [i,j] represents known cost to j through i.
     * This is done so that row[label] can be used to store known min costs from this Node to all others.
     * Such representation is particularly handy for sending out min cost distance vector to other nodes, but can be a pain to print out according to specification.
     * As an example, consider the following table for a node labelled 0:
     * D0 |  0  | 1 | 2
     * --------------
     *  0 |  0  | 4 | 2
     *  1 | 999 | 5 | 4
     *  2 | 999 | 7 | 2
     * The first row tells us that the minimum costs to get to nodes 1 and 2 are 4 and 2, respectively.
     * The second row tells us that:
     *  - the cost to get to node 1 by direct contact is 5;
     *  - the cost to get to node 1 by node 2 is 4;
     * The third row tells us that:
     *  - the cost to get to node 2 by node 1 is 7;
     *  - the cost to get to node 2 by direct contact is 2;
     * Hence, the minimum cost path from 0 to 1 is 0 -> 1 -> 2, instead of direct contact;
     * Which is exactly why the first row states the cost to 1 is 4 instead of 5.
     */

    /**
     * Constructor for Node - an abstraction of a router.
     *
     * @param label    Label for the Node, it's the node number in the given
     *                 topology
     * @param costs    Costs from this Node to all adjacent ones, i.e. edge weights
     *                 - this doubles as neighbouring nodes information
     * @param numNodes the number of Nodes in the topology
     */
    public Node(int label, HashMap<Integer, Integer> costs, int numNodes) {
        this.label = label;
        this.numNodes = numNodes;
        this.weights = new HashMap<Integer, Integer>(costs);

        this.distanceTable = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < numNodes; i++) {
            ArrayList<Integer> tmp = new ArrayList<Integer>();
            boolean adjacent = costs.containsKey(i);

            for (int j = 0; j < numNodes; j++) {
                if (i == j && adjacent)
                    tmp.add(costs.get(i));
                else if (i == label && j == label) // the cost to myself is null
                    tmp.add(0);
                else // not an adjacent node
                    tmp.add(999);
            }

            this.distanceTable.add(tmp);
        }

        ArrayList<Integer> thisNode = this.distanceTable.get(this.label);
        costs.forEach((k, v) -> thisNode.set(k, v));

        this.sender = new Sender(this);
    }

    /**
     * Starts communication by:
     * - setting dtUpdated to true, hence making the sender thread send packets;
     * - setting stopComm to false;
     * - starting the sender thread.
     */
    public void startCommunication() {
        this.dtUpdated = true;
        this.stopComm = false;
        this.sender.start();
    }

    /**
     * Stops all communication.
     */
    public void stopCommunication() {
        this.stopComm = true;
        try {
            this.sender.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Receives a neighbouring Node's cost row and updates own table if need be.
     * @param packet The packet this Node's received
     */
    public void receivePacket(Packet packet) {
        this.dtUpdated = false;
        Integer origin = packet.src;
        ArrayList<Integer> w = packet.costs;

        if (rip.Emulator.trace>3) {
            System.out.printf("    NODE %d: received packet from %d, sent at time %.3f\n", this.label, origin, packet.timestamp);
            System.out.printf("    NODE %d: current distance table\n", this.label);
            this.printDt();
        }

        ArrayList<Integer> thisNode = this.distanceTable.get(this.label);
        ArrayList<Integer> originNode = this.distanceTable.get(origin);

        // Just in case this node isn't actively communicating
        if (!this.sender.isAlive())
            this.startCommunication();

        for (int i = 0; i < w.size(); i++) {
            if (i == this.label)
                continue;
            try {
                int tmpVal = w.get(i) + this.weights.get(origin);
                if (originNode.get(i) > tmpVal) {
                    this.dtUpdated = true;
                    originNode.set(i, tmpVal);
                }
                if (thisNode.get(i) > tmpVal) {
                    this.dtUpdated = true;
                    thisNode.set(i, tmpVal);
                }
            } catch (NullPointerException e) {
                System.out.println(e);
            }
        }

        if (this.dtUpdated && rip.Emulator.trace>3) {
            System.out.printf("    NODE %d: distance table was updated\n", this.label);
            this.printDt();
            System.out.printf("    NODE %d: sending current min costs to nodes ", this.label);
            for (int n : weights.keySet()) {
                System.out.printf("%d ", n);
            }
            System.out.printf("\n");
        }
    }
    /**
     * Prints current distance table.
     */
    public void printDt() {
        System.out.printf("                via     \n");
        System.out.printf("   D%d |", this.label);
        for (int n : weights.keySet()) {
            System.out.printf("    %d|", n);
        }
        System.out.println("\n");
        System.out.printf("  ----|-----------------\n");
        for (int j = 0; j < this.numNodes; j++) {
            if (j != this.label) {
                System.out.printf("     %d|", j);
                for (int i = 0; i < this.numNodes; i++) {
                    int val = this.distanceTable.get(i).get(j);
                    if (val != 999 && i != this.label)
                        System.out.printf("  %3d", val);
                }
                System.out.printf("\n");
            }
        }
    }
}

/**
 * Sender class, internal to Node.
 */
class Sender extends Thread {
    private Node node;

    Sender(Node node) {
        this.node = node;
    }
    public void run() {
        while(true && !node.stopComm) {
            if (node.dtUpdated) {
                node.weights.keySet().forEach(nodeLabel -> {
                    rip.Emulator.addPacket(new Packet(node.label, nodeLabel, node.distanceTable.get(node.label), rip.Emulator.getTime()));
                });
            }
            node.dtUpdated = false;
        }
    }
}