package rip;

import java.util.ArrayList;

public class Packet implements Comparable<Packet> {
    public int src;
    public int dst;
    public ArrayList<Integer> costs;
    public double timestamp;

    public Packet(int src, int dst, ArrayList<Integer> costs) {
        this.src = src;
        this.dst = dst;
        this.costs = costs;
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