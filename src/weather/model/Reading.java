package weather.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Comparator;

/**
 * CONCEPT: record (immutable data carrier) with a compact constructor for validation,
 * implementing two interfaces, a static factory, and static Comparators.
 *
 * The original code used a mutable class with a boxed {@code Double} and
 * {@code System.currentTimeMillis()} — two readings in the same millisecond collided
 * as TreeMap keys. A record + Instant fixes both problems.
 */
public record Reading(String sensorId, SensorType type, double value, Instant time)
        implements Measurable, Comparable<Reading>, Serializable {

    private static final long serialVersionUID = 1L;

    /** compact canonical constructor = validation point */
    public Reading {
        if (sensorId == null || sensorId.isBlank()) {
            throw new IllegalArgumentException("sensorId required");
        }

        if (type == null) {
            throw new IllegalArgumentException("type required");
        }

        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }

        // reading-range validation: the value must be physically possible for this sensor type
        if (!type.inRange(value)) {
            throw new IllegalArgumentException(String.format(
                    "%s reading %.1f outside valid range %.0f..%.0f %s",
                    type, value, type.min(), type.max(), type.unit()));
        }

        if (time == null) {
            time = Instant.now();
        }

        // timestamp validation: a sensor cannot report from the future (1 minute of clock skew allowed)
        if (time.isAfter(Instant.now().plusSeconds(60))) {
            throw new IllegalArgumentException("timestamp is in the future: " + time);
        }
    }

    public static Reading now(String sensorId, SensorType type, double value) {
        return new Reading(sensorId, type, value, Instant.now());
    }

    /** natural order = chronological */
    @Override
    public int compareTo(Reading o) {
        return time.compareTo(o.time);
    }

    public static final Comparator<Reading> BY_VALUE =
            Comparator.comparingDouble(Reading::value).thenComparing(Reading::time);

    public static final Comparator<Reading> BY_SENSOR_THEN_TIME =
            Comparator.comparing(Reading::sensorId).thenComparing(Reading::time);

    @Override
    public String toString() {
        return sensorId + " = " + formatted();
    }
}
