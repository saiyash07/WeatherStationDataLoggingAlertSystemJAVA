package weather.util;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * CONCEPT: generic class, functional-interface parameter (key extractor),
 * thread-safe map, custom Iterator that returns a SNAPSHOT (no ConcurrentModificationException).
 */
public class InMemoryRepository<T, K extends Comparable<K>> implements Repository<T, K> {

    private final Map<K, T> store = new ConcurrentHashMap<>();
    private final Function<? super T, ? extends K> keyOf;

    public InMemoryRepository(Function<? super T, ? extends K> keyOf) {
        this.keyOf = Objects.requireNonNull(keyOf);
    }

    @Override
    public void save(T item) {
        store.put(keyOf.apply(item), item);
    }

    @Override
    public Optional<T> findById(K k) {
        return Optional.ofNullable(store.get(k));
    }

    @Override
    public boolean delete(K k) {
        return store.remove(k) != null;
    }

    @Override
    public int count() {
        return store.size();
    }

    /** Returned sorted by key so the UI is deterministic (HashMap order is arbitrary). */
    @Override
    public Collection<T> findAll() {
        List<T> list = new ArrayList<>(store.values());
        list.sort(Comparator.comparing(keyOf));

        return Collections.unmodifiableList(list);
    }

    @Override
    public Iterator<T> iterator() {
        final Iterator<T> snapshot = findAll().iterator();
        // anonymous inner class
        return new Iterator<>() {
            @Override
            public boolean hasNext() {
                return snapshot.hasNext();
            }

            @Override
            public T next() {
                return snapshot.next();
            }
        };
    }

    /** Atomic "insert only if absent" — avoids check-then-act race. */
    public boolean saveIfAbsent(T item) {
        return store.putIfAbsent(keyOf.apply(item), item) == null;
    }

    public void clear() {
        store.clear();
    }
}
