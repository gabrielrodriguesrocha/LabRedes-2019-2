package rip;

import java.util.ArrayList;
import java.util.HashMap;
import rip.Packet;

/**
 * Abstract representation of a router.
 */
public class Node {
    private int label, numNodes;
    private HashMap<Integer, Integer> weights;
    private ArrayList<ArrayList<Integer>> distanceTable;
    /**
     * The destination table is actually transposed. That means position [i,j] represents known cost to j through i.
     * This is done so that row[label] can be used to store known min costs from this Node to all others.
     * Such representation is particularly handy for sending out min cost distance vector to other nodes, but can be a pain to print out according to specification.
     * As an example, consider the following table for a node labelled 0:
     * D0 | 0   | 1 | 2
     * --------------
     *  0 |  0  | 4 | 2
     *  1 | 999 | 5 | 4
     *  2 | 999 | 7 | 2
     * The first row tells us that the minimum costs to get to nodes 1 and 2 are 4 and 2, respectively.
     * The second row tells us that:
     *  - the cost to get to node 1 by direct contact is 5;
     *  - the cost to get to node 1 by node 2 is 2;
     * The third row tells us that:
     *  - the cost to get to node 2 by node 1 is 7;
     *  - the cost to get to node 2 by direct contact is 2;
     * Hence, the minimum cost path from 0 to 1 is 0 -> 1 -> 2, instead of direct contact;
     * Which is exactly why the first row states the cost to 1 is 4 instead of 5.
     */

    /**
     * Constructor for Node - an abstraction of a router.
     * @param label Label for the Node, it's the node number in the given topology
     * @param costs Costs from this Node to all adjacent ones, i.e. edge weights - this doubles as neighbouring nodes information
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
        costs.forEach((k,v) -> thisNode.set(k, v));
    }

    /**
     * Returns Node's label.
     */
    public int getLabel() {
        return this.label;
    }

    /**
     * Sends Node's current cost row to neighbouring Nodes.
     */
    public ArrayList<Packet> sendPacket() {
        ArrayList<Packet> packets = new ArrayList<Packet>(weights.size());
        weights.keySet().forEach(nodeLabel -> {
            packets.add(new Packet(this.label, nodeLabel, this.distanceTable.get(this.label)));
        });
        return packets;
        /*
        neighbours.forEach((nodeLabel,node) ->
            node.receivePkt(this.label, this.distanceTable.get(this.label))
        );*/
    }

    /**
     * Receives a neighbouring Node's cost row and updates own table if need be.
     * @param origin The neighbouring Node's label
     * @param w The neighbouring Node's cost row
     */
    public ArrayList<Packet> receivePacket(Integer origin, ArrayList<Integer> w) {
        boolean updated = false;
        ArrayList<Integer> thisNode = this.distanceTable.get(this.label);
        ArrayList<Integer> originNode = this.distanceTable.get(origin);
        for (int i = 0; i < w.size(); i++) {
            if (i == this.label)
                continue;
            try {
                int tmpVal = w.get(i) + this.weights.get(origin);
                if (originNode.get(i) > tmpVal) {
                    updated = true;
                    originNode.set(i, tmpVal);
                }
                if (thisNode.get(i) > tmpVal) {
                    updated = true;
                    thisNode.set(i, tmpVal);
                }
            } catch (NullPointerException e) {
                System.out.println(e);
            }
        }
        if (updated)
            return this.sendPacket();
        else
            return null;
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