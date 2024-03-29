package rip;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.PriorityQueue;
import rip.Node;
import rip.Packet;

/**
 * Abstract representation of a network communication channel.
 * Could be thought of as analogous to a switch.
 *
 * This emulator is entirely deterministic.
 */
public class Emulator {
    private static Node[] node;
    private static int trace;
    private static PriorityQueue<Packet> eventList;
    private static double time;
    private static final Random rng = new Random();

    public static void main(String[] args) {
        Packet currentEvent;
        init();

        while (true) {
            if ((currentEvent = eventList.poll()) != null) {
                if (trace>1) {
                    System.out.printf("MAIN: rcv event, t=%.3f, at %d", currentEvent.timestamp, currentEvent.dst);
                    System.out.printf(" src:%2d,", currentEvent.src);
                    System.out.printf(" dest:%2d,",currentEvent.dst);
                    System.out.printf(" contents: ");
                    for (int x : currentEvent.costs) {
                        System.out.printf("%3d ", x);
                    }
                    System.out.printf("\n");
                }

                toLayer2(currentEvent);
            }
            if (eventList.isEmpty()) {
                break;
            }
        }
        System.out.printf("\nSimulator terminated at t=%f, no packets in medium\n", time);
        for (Node n : node) {
            n.printDt();
        }
    }

    /**
     * Creates nodes and sets the network topology.
     * Initializes node distance tables.
     * Starts node to node communication and initiates event queueing.
     */
    private static void init() {
        Scanner scan = new Scanner(System.in);
        System.out.println("Enter TRACE:");
        trace = scan.nextInt();
        scan.close();

        // Current topology
        node = new Node[4];
        node[0] = new Node(0, new HashMap<Integer, Integer>(){{ put(1, 1); put(2, 3); put(3, 7); }}, 4);
        node[1] = new Node(1, new HashMap<Integer, Integer>(){{ put(0, 1); put(2, 1); }}, 4);
        node[2] = new Node(2, new HashMap<Integer, Integer>(){{ put(1, 1); put(0, 3); put(3, 2); }}, 4);
        node[3] = new Node(3, new HashMap<Integer, Integer>(){{ put(0, 7); put(2, 2); }}, 4);
        /*
        node[0] = new Node(0, new HashMap<Integer, Integer>(){{ put(1, 3); put(2, 23); }}, 4);
        node[1] = new Node(1, new HashMap<Integer, Integer>(){{ put(0, 3); put(2, 2); }}, 4);
        node[2] = new Node(2, new HashMap<Integer, Integer>(){{ put(0, 23); put(1, 2); put(3, 5); }}, 4);
        node[3] = new Node(3, new HashMap<Integer, Integer>(){{ put(2, 5); }}, 4);
        */
        time = 0.0;
        eventList = new PriorityQueue<Packet>();

        // Node 0 starts communication
        ArrayList<Packet> packets = node[0].sendPacket();
        for (Packet p : packets) {
            time = time + rng.nextDouble();
            p.timestamp = time;
        }
        eventList.addAll(node[0].sendPacket());
    }

    /**
     * Emulates layer 2 switching with a few caveats.
     *
     * @param packet Packet to be processed and sent to its destination.
     */
    private static void toLayer2(Packet packet) {
        if (trace>2)  {
            System.out.printf("    TOLAYER2: source: %d, dest: %d\n              costs:",
                   packet.src, packet.dst);
            for (int x : packet.costs) {
                System.out.printf("%d  ", x);
            }
            System.out.printf("\n");
        }
        ArrayList<Packet> packets = node[packet.dst].receivePacket(packet.src, packet.costs);
        if (packets != null) {
            for (Packet p : packets) {
                time = time + rng.nextDouble();
                p.timestamp = time;
            }
            eventList.addAll(packets);
        }
        return;
    }
}