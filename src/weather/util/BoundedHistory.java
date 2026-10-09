package weather.util;

import java.util.ArrayList;
import java.util.List;

/**
 * CONCEPT: hand-written generic data structure — a fixed-size circular (ring) buffer.
 * The original app kept every reading forever in an ArrayList (unbounded memory leak).
 * This keeps only the newest N and overwrites the oldest in O(1).
 */
public class BoundedHistory<E> {
    private final Object[] data;      // generic arrays cannot be created directly
    private int head = 0;             // next write position
    private int size = 0;

    public BoundedHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be > 0");
        }

        data = new Object[capacity];
    }

    public synchronized void add(E e) {
        data[head] = e;
        head = (head + 1) % data.length;

        if (size < data.length) {
            size++;
        }
    }

    @SuppressWarnings("unchecked")
    public synchronized List<E> toList() {          // oldest -> newest
        List<E> out = new ArrayList<>(size);
        int start = (head - size + data.length) % data.length;

        for (int i = 0; i < size; i++) {
            out.add((E) data[(start + i) % data.length]);
        }

        return out;
    }

    public synchronized int size() {
        return size;
    }

    public int capacity() {
        return data.length;
    }

    public synchronized void clear() {
        java.util.Arrays.fill(data, null);
        head = 0;
        size = 0;
    }
}
