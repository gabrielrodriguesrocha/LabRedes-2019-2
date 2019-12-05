package rip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.SynchronousQueue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import rip.Packet;

/**
 * Abstract representation of a router.
 */
public class Node {
    public int label, numNodes;
    public boolean stopComm;
    public HashMap<Integer, Integer> weights;
    public BlockingQueue<Boolean> dtUpdated;
    public Sender sender;
    public Receiver receiver;
    public ArrayList<ArrayList<Integer>> distanceTable;

    /**
     * The destination table is actually transposed. That means position [i,j]
     * represents known cost to j through i. This is done so that row[label] can be
     * used to store known min costs from this Node to all others. Such
     * representation is particularly handy for sending out min cost distance vector
     * to other nodes, but can be a pain to print out according to specification. As
     * an example, consider the following table for a node labelled 0: D0 | 0 | 1 |
     * 2 -------------- 0 | 0 | 4 | 2 1 | 999 | 5 | 4 2 | 999 | 7 | 2 The first row
     * tells us that the minimum costs to get to nodes 1 and 2 are 4 and 2,
     * respectively. The second row tells us that: - the cost to get to node 1 by
     * direct contact is 5; - the cost to get to node 1 by node 2 is 4; The third
     * row tells us that: - the cost to get to node 2 by node 1 is 7; - the cost to
     * get to node 2 by direct contact is 2; Hence, the minimum cost path from 0 to
     * 1 is 0 -> 1 -> 2, instead of direct contact; Which is exactly why the first
     * row states the cost to 1 is 4 instead of 5.
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
    public Node(final int label, final HashMap<Integer, Integer> costs, final int numNodes) {
        this.label = label;
        this.numNodes = numNodes;
        this.weights = new HashMap<Integer, Integer>(costs);
        this.dtUpdated = new LinkedBlockingQueue<Boolean>(2);

        this.distanceTable = new ArrayList<ArrayList<Integer>>();
        for (int i = 0; i < numNodes; i++) {
            final ArrayList<Integer> tmp = new ArrayList<Integer>();
            final boolean adjacent = costs.containsKey(i);

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

        final ArrayList<Integer> thisNode = this.distanceTable.get(this.label);
        costs.forEach((k, v) -> thisNode.set(k, v));

        this.receiver = new Receiver(this);
        this.sender = new Sender(this);
    }

    /**
     * Starts communication by: - setting dtUpdated to true, hence making the sender
     * thread send packets; - setting stopComm to false; - starting the sender
     * thread.
     */
    public void startCommunication() {
        this.dtUpdated.add(true);
        this.stopComm = false;
        this.sender.start();
        this.receiver.start();
    }

    /**
     * Stops all communication.
     */
    public void stopCommunication() {
        this.stopComm = true;
        this.sender.interrupt();
        this.receiver.interrupt();
    }

    /**
     * Prints current distance table.
     */
    public void printDt() {
        System.out.printf("                via     \n");
        System.out.printf("   D%d |", this.label);
        for (final int n : weights.keySet()) {
            System.out.printf("    %d|", n);
        }
        System.out.println("\n");
        System.out.printf("  ----|-----------------\n");
        for (int j = 0; j < this.numNodes; j++) {
            if (j != this.label) {
                System.out.printf("     %d|", j);
                for (int i = 0; i < this.numNodes; i++) {
                    final int val = this.distanceTable.get(i).get(j);
                    if (val != 999 && i != this.label)
                        System.out.printf("  %3d", val);
                }
                System.out.printf("\n");
            }
        }
    }

    public boolean commStopped() {
        if (!sender.isAlive() || !receiver.isAlive() || stopComm)
            return true;
        return false;
    }
}

/**
 * Sender class, internal to Node.
 */
class Sender extends Thread {
    private final Node node;
    DatagramSocket senderSocket;

    Sender(final Node node) {
        this.node = node;
        try {
            senderSocket = new DatagramSocket();
        } catch (final SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private byte[] serialize(final Packet p) {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutput out = null;
        byte[] sendData;
        try {
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(p);
                out.flush();
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            sendData = bos.toByteArray();
        } finally {
            try {
                bos.close();
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        return sendData;
    }

    public void run() {
        Boolean tmp;
        while (true && !this.node.stopComm) {
            /*try {*/
                if (this.node.dtUpdated.poll() != null) {
                    this.node.weights.keySet().forEach(nodeLabel -> {
                        final Packet sendPacket = new Packet(this.node.label, nodeLabel,
                                this.node.distanceTable.get(this.node.label), rip.Emulator.getTime());
                        rip.Emulator.addPacket(sendPacket);
                        final byte[] sendData = serialize(sendPacket);
                        DatagramPacket packet;
                        try {
                            packet = new DatagramPacket(sendData, sendData.length, InetAddress.getByName("localhost"),
                                    1000 + nodeLabel);
                            senderSocket.send(packet);
                        } catch (final UnknownHostException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        } catch (final IOException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    });
                    //this.node.dtUpdated.offer(false);
                }
            /*} catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }*/
        }
    }
}

/**
 * Receiver class, internal to Node.
 */
class Receiver extends Thread {
    private final Node node;
    DatagramSocket receiverSocket;

    Receiver(final Node node) {
        this.node = node;
        try {
            receiverSocket = new DatagramSocket(1000 + this.node.label);
        } catch (final SocketException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private Packet deserialize(final byte[] p) {
        final ByteArrayInputStream bis = new ByteArrayInputStream(p);
        ObjectInput in = null;
        Packet o = null;
        try {
            in = new ObjectInputStream(bis);
            o = (Packet) in.readObject();
        } catch (ClassNotFoundException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (final IOException ex) {
                // ignore close exception
            }
        }
        return o;
    }

    public void run() {
        final byte[] receiveData = new byte[1024];
        try {
            receiverSocket.setSoTimeout(1000);
        } catch (SocketException e2) {
            // TODO Auto-generated catch block
            e2.printStackTrace();
        }
        while (true && !this.node.stopComm) {
            final DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
            try {
                receiverSocket.receive(receivePacket);
            } catch (final IOException e1) {
                // TODO Auto-generated catch block
                this.node.stopComm = true;
                receiverSocket.close();
            }
            final Packet packet = deserialize(receivePacket.getData());
            boolean internalUpdate = false;
            final Integer origin = packet.src;
            final ArrayList<Integer> w = packet.costs;

            if (rip.Emulator.trace > 3) {
                System.out.printf("    NODE %d: received packet from %d, sent at time %.3f\n", this.node.label, origin,
                        packet.timestamp);
                System.out.printf("    NODE %d: current distance table\n", this.node.label);
                this.node.printDt();
            }

            final ArrayList<Integer> thisNode = this.node.distanceTable.get(this.node.label);
            final ArrayList<Integer> originNode = this.node.distanceTable.get(origin);

            // Just in case this node isn't actively communicating
            // if (!this.node.sender.isAlive())
            // this.node.startCommunication();

            for (int i = 0; i < w.size(); i++) {
                if (i == this.node.label)
                    continue;
                try {
                    final int tmpVal = w.get(i) + this.node.weights.get(origin);
                    if (originNode.get(i) > tmpVal) {
                        internalUpdate = true;
                        originNode.set(i, tmpVal);
                    }
                    if (thisNode.get(i) > tmpVal) {
                        internalUpdate = true;
                        thisNode.set(i, tmpVal);
                    }
                } catch (final NullPointerException e) {
                    System.out.println(e);
                }
            }

            if (internalUpdate)
                this.node.dtUpdated.offer(true);

            if (internalUpdate && rip.Emulator.trace > 3) {
                System.out.printf("    NODE %d: distance table was updated\n", this.node.label);
                this.node.printDt();
                System.out.printf("    NODE %d: sending current min costs to nodes ", this.node.label);
                for (final int n : this.node.weights.keySet()) {
                    System.out.printf("%d ", n);
                }
                System.out.printf("\n");
            }
        }
        receiverSocket.close();
    }
}