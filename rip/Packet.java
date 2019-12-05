package rip;

import java.io.Serializable;
import java.util.ArrayList;

public class Packet implements Comparable<Packet>, Serializable {
    private static final long serialVersionUID = 4458136002143282228L;
    public int src;
    public int dst;
    public ArrayList<Integer> costs;
    public double timestamp;

    public Packet(int src, int dst, ArrayList<Integer> costs) {
        this.src = src;
        this.dst = dst;
        this.costs = costs;
    }

    public Packet(int src, int dst, ArrayList<Integer> costs, double timestamp) {
        this(src, dst, costs);
        this.timestamp = timestamp;
    }

    @Override
    public int compareTo(Packet anotherPacket) {
        if (this.timestamp < anotherPacket.timestamp) {
            return -1;
        }
        if (this.timestamp > anotherPacket.timestamp) {
            return 1;
        }
        return 0;
    }
}