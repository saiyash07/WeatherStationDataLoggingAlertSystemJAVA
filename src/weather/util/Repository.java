package weather.util;

import java.util.Collection;
import java.util.Optional;

/**
 * CONCEPT: generic interface with two type parameters and a bounded key type.
 * @param <T> entity type
 * @param <K> key type (must be Comparable so implementations can sort by it)
 */
public interface Repository<T, K extends Comparable<K>> extends Iterable<T> {
    void save(T item);
    Optional<T> findById(K key);
    boolean delete(K key);
    Collection<T> findAll();
    int count();

    default boolean exists(K key) {
        return findById(key).isPresent();
    }

    default boolean isEmpty() {
        return count() == 0;
    }
}
