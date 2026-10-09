package weather.util;

import java.util.Collection;
import java.util.DoubleSummaryStatistics;

/** CONCEPT: record with a static factory built on DoubleSummaryStatistics + derived property. */
public record Statistics(long count, double min, double max, double mean, double stdDev) {

    public static final Statistics EMPTY = new Statistics(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN);

    public static Statistics of(Collection<Double> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }

        DoubleSummaryStatistics s = values.stream().mapToDouble(Double::doubleValue).summaryStatistics();
        double var = values.stream().mapToDouble(v -> (v - s.getAverage()) * (v - s.getAverage())).sum() / s.getCount();

        return new Statistics(s.getCount(), s.getMin(), s.getMax(), s.getAverage(), Math.sqrt(var));
    }

    public double range() {
        return max - min;
    }

    @Override
    public String toString() {
        return count == 0 ? "no data"
                : String.format("n=%d min=%.1f max=%.1f mean=%.2f sd=%.2f", count, min, max, mean, stdDev);
    }
}
