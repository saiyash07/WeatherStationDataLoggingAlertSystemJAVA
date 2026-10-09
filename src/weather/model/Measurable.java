package weather.model;

/**
 * CONCEPT: interface with abstract, default and static methods.
 */
public interface Measurable {

    double value();

    SensorType type();

    /** default method: behaviour shared by every implementor. */
    default String formatted() {
        return String.format("%.1f %s", value(), type().unit());
    }

    default boolean exceeds(double limit) {
        return value() > limit;
    }

    /** static interface method: utility that belongs to the abstraction. */
    static double average(Iterable<? extends Measurable> items) {
        double sum = 0;
        int n = 0;

        for (Measurable m : items) {
            sum += m.value();
            n++;
        }

        return n == 0 ? Double.NaN : sum / n;
    }
}
