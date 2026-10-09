package weather.model;

import java.util.Random;

/**
 * CONCEPT: enum with fields, constructor, methods and a static factory.
 * Each constant carries its own unit and physically valid range, so the rest of the
 * program never hard-codes "0..100" the way the original WeatherApp did.
 */
public enum SensorType {
    TEMPERATURE("°C", -50, 60, 40),
    HUMIDITY("%", 0, 100, 80),
    RAINFALL("mm/h", 0, 100, 20),
    PRESSURE("hPa", 900, 1100, 1050),
    WIND("km/h", 0, 200, 90);

    private final String unit;
    private final double min;
    private final double max;
    private final double defaultLimit;

    SensorType(String unit, double min, double max, double defaultLimit) {
        this.unit = unit;
        this.min = min;
        this.max = max;
        this.defaultLimit = defaultLimit;
    }

    public String unit() {
        return unit;
    }

    public double min() {
        return min;
    }

    public double max() {
        return max;
    }

    public double defaultLimit() {
        return defaultLimit;
    }

    public boolean inRange(double v) {
        return v >= min && v <= max;
    }

    /** Clamp a value into the legal range of this type. */
    public double clamp(double v) {
        return Math.max(min, Math.min(max, v));
    }

    /** Uniform random value inside the legal range. */
    public double randomValue(Random rnd) {
        return min + rnd.nextDouble() * (max - min);
    }

    /** Case-insensitive parse that throws a friendlier message than valueOf. */
    public static SensorType parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Sensor type is empty");
        }

        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown sensor type: " + text, e);
        }
    }
}
