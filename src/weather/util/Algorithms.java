package weather.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CONCEPT: generic methods, bounded wildcards (PECS), recursion, and the classic
 * search / sort algorithms a case-study marker expects to see implemented by hand.
 */
public final class Algorithms {
    private Algorithms() {}                  // utility class: no instances

    /** Stable O(n log n) merge sort. {@code ? super T} on the comparator = Consumer-Super. */
    public static <T> List<T> mergeSort(List<? extends T> input, Comparator<? super T> cmp) {
        if (input.size() <= 1) {
            return new ArrayList<>(input);
        }

        int mid = input.size() / 2;
        List<T> left  = mergeSort(input.subList(0, mid), cmp);
        List<T> right = mergeSort(input.subList(mid, input.size()), cmp);
        List<T> merged = new ArrayList<>(input.size());
        int i = 0, j = 0;

        while (i < left.size() && j < right.size()) {
            // <= keeps equal elements in original order => stable
            merged.add(cmp.compare(left.get(i), right.get(j)) <= 0 ? left.get(i++) : right.get(j++));
        }

        while (i < left.size()) {
            merged.add(left.get(i++));
        }

        while (j < right.size()) {
            merged.add(right.get(j++));
        }

        return merged;
    }

    /** Overload using natural ordering: T must be mutually Comparable. */
    public static <T extends Comparable<? super T>> List<T> mergeSort(List<? extends T> input) {
        return mergeSort(input, Comparator.<T>naturalOrder());
    }

    /** Iterative binary search on a list sorted by {@code cmp}. @return index, or -(insertion point)-1 */
    public static <T> int binarySearch(List<? extends T> sorted, T key, Comparator<? super T> cmp) {
        int lo = 0, hi = sorted.size() - 1;

        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;          // unsigned shift avoids int overflow
            int c = cmp.compare(sorted.get(mid), key);

            if (c < 0) {
                lo = mid + 1;
            } else if (c > 0) hi = mid - 1;
            else return mid;
        }

        return -(lo + 1);
    }

    /** Linear search with a predicate — what the original search() did, but reusable. */
    public static <T> List<T> filter(Iterable<? extends T> items, java.util.function.Predicate<? super T> p) {
        List<T> out = new ArrayList<>();

        for (T t : items) {
            if (p.test(t)) out.add(t);
        }

        return out;
    }

    /** varargs: max of any number of comparables. */
    @SafeVarargs
    public static <T extends Comparable<? super T>> T max(T first, T... rest) {
        T best = first;

        for (T t : rest) {
            if (t.compareTo(best) > 0) best = t;
        }

        return best;
    }
}
