package weather.util;

import java.util.function.Predicate;

/**
 * CONCEPT: custom functional interface with default methods for composition
 * (the same idea as Predicate.and/negate, but carrying an error message).
 */
@FunctionalInterface
public interface Validator<T> {

    /** @return null if valid, otherwise a human readable error message */
    String check(T value);

    default Validator<T> and(Validator<? super T> next) {
        return v -> {
            String first = check(v);

            return first != null ? first : next.check(v);
        };
    }

    static <T> Validator<T> of(Predicate<? super T> ok, String message) {
        return v -> ok.test(v) ? null : message;
    }

    default boolean isValid(T value) {
        return check(value) == null;
    }
}
