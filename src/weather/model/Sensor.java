package weather.model;

import weather.exception.InvalidSensorException;

import java.io.Serializable;
import java.util.Objects;
import java.util.Random;

/**
 * CONCEPTS: abstraction (abstract class), encapsulation (private fields + validated setter),
 * sealed hierarchy (only the four known sensors may extend), equals/hashCode contract,
 * Comparable, Serializable, template method (describe() calls abstract hook).
 */
public abstract sealed class Sensor implements Comparable<Sensor>, Serializable
        permits TemperatureSensor, HumiditySensor, RainfallSensor, PressureSensor, WindSensor {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final SensorType type;
    private volatile double limit;          // volatile: read by simulator thread, written by UI thread
    private String location;

    protected Sensor(String id, SensorType type, double limit, String location)
            throws InvalidSensorException {
        if (id == null || id.isBlank()) {
            throw new InvalidSensorException("Sensor id is empty");
        }

        if (id.length() > 12) {
            throw new InvalidSensorException("Sensor id too long (max 12)");
        }

        if (!id.trim().matches("[A-Za-z0-9_-]+"))
            throw new InvalidSensorException("Sensor id may only contain letters, digits, '-' and '_'");
        this.id = id.trim();
        this.type = Objects.requireNonNull(type);
        this.location = location == null || location.isBlank() ? "unknown" : location.trim();
        setLimit(limit);
    }

    public String id() {
        return id;
    }

    public SensorType type() {
        return type;
    }

    public double limit() {
        return limit;
    }

    public String location() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    /** Encapsulation: the only way to change the limit goes through validation. */
    public final void setLimit(double newLimit) throws InvalidSensorException {
        if (Double.isNaN(newLimit) || !type.inRange(newLimit)) {
            throw new InvalidSensorException(String.format(
                    "Limit %.1f outside valid range %.0f..%.0f %s for %s",
                    newLimit, type.min(), type.max(), type.unit(), type));
        }

        this.limit = newLimit;
    }

    /**
     * POLYMORPHISM: each subclass produces realistic values for the given hour (0-23).
     * The result is always clamped into the legal range of the type.
     */
    public final double sample(Random rnd, int hourOfDay) {
        return type.clamp(simulate(rnd, hourOfDay));
    }

    /** Hook for subclasses (template-method pattern). */
    protected abstract double simulate(Random rnd, int hourOfDay);

    /** Human readable kind, supplied by subclass. */
    public abstract String describe();

    // ---- Object contract -------------------------------------------------

    /** Two sensors are the same sensor if they have the same id (regardless of subtype). */
    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Sensor other)) return false;     // pattern matching instanceof

        return id.equals(other.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public int compareTo(Sensor o) {
        return id.compareToIgnoreCase(o.id);
    }

    @Override
    public String toString() {
        return String.format("%s[%s @%s limit=%.1f %s]",
                getClass().getSimpleName(), id, location, limit, type.unit());
    }
}
