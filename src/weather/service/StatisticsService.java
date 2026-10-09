package weather.service;

import weather.model.Alert;
import weather.model.AlertLevel;
import weather.model.Reading;
import weather.model.Sensor;
import weather.model.SensorType;
import weather.util.Statistics;

import java.util.*;
import java.util.stream.Collectors;

/** CONCEPT: Streams API — groupingBy, mapping, collectingAndThen, sorted, limit, joining, text blocks. */
public final class StatisticsService {
    private StatisticsService() {}

    public static Map<String, Statistics> bySensor(Collection<Reading> readings) {
        return readings.stream().collect(Collectors.groupingBy(
                Reading::sensorId,
                TreeMap::new,
                Collectors.collectingAndThen(
                        Collectors.mapping(Reading::value, Collectors.toList()),
                        Statistics::of)));
    }

    public static Map<SensorType, Statistics> byType(Collection<Reading> readings) {
        return readings.stream().collect(Collectors.groupingBy(
                Reading::type,
                () -> new EnumMap<>(SensorType.class),
                Collectors.collectingAndThen(
                        Collectors.mapping(Reading::value, Collectors.toList()),
                        Statistics::of)));
    }

    public static List<Reading> topN(Collection<Reading> readings, int n) {
        return readings.stream()
                .sorted(Reading.BY_VALUE.reversed())
                .limit(n)
                .toList();
    }

    public static Optional<Reading> hottest(Collection<Reading> readings) {
        return readings.stream()
                .filter(r -> r.type() == SensorType.TEMPERATURE)
                .max(Comparator.comparingDouble(Reading::value));
    }

    public static long countAbove(Collection<Reading> readings, Map<String, Double> limits) {
        return readings.stream()
                .filter(r -> r.value() > limits.getOrDefault(r.sensorId(), Double.MAX_VALUE))
                .count();
    }

    /** Multi-line report using a text block. */
    public static String report(Collection<Reading> readings, Map<String, Double> limits) {
        String perSensor = bySensor(readings).entrySet().stream()
                .map(e -> String.format("  %-12s %s", e.getKey(), e.getValue()))
                .collect(Collectors.joining("\n"));
        String perType = byType(readings).entrySet().stream()
                .map(e -> String.format("  %-12s %s %s", e.getKey(), e.getValue(), e.getKey().unit()))
                .collect(Collectors.joining("\n"));
        String top = topN(readings, 5).stream()
                .map(r -> "  " + r)
                .collect(Collectors.joining("\n"));
        return """
                STATISTICS (%d readings, %d over limit)

                Per sensor
                %s

                Per type
                %s

                Top 5 values
                %s
                """.formatted(readings.size(), countAbove(readings, limits),
                perSensor.isEmpty() ? "  -" : perSensor,
                perType.isEmpty() ? "  -" : perType,
                top.isEmpty() ? "  -" : top);
    }

    /**
     * MODULE 7 - Weather Reports: one combined report for the whole station.
     * Sections: registered sensors, latest value per sensor, alert summary, statistics.
     */
    public static String fullReport(WeatherStation station) {
        Collection<Sensor> sensors = station.sensors();
        Map<String, Reading> latest = station.latest();
        List<Alert> alerts = station.alerts();
        List<Reading> readings = station.allReadings();

        Map<String, Double> limits = new HashMap<>();

        for (Sensor s : sensors) {
            limits.put(s.id(), s.limit());
        }

        String sensorLines = sensors.stream()
                .map(s -> String.format("  %-8s %-12s %-12s limit %.1f %s",
                        s.id(), s.type(), s.location(), s.limit(), s.type().unit()))
                .collect(Collectors.joining("\n"));

        String latestLines = latest.values().stream()
                .map(r -> "  " + r)
                .collect(Collectors.joining("\n"));

        Map<AlertLevel, Long> perLevel = new EnumMap<>(AlertLevel.class);

        for (Alert a : alerts) {
            perLevel.merge(a.level(), 1L, Long::sum);
        }

        String alertLines = perLevel.isEmpty()
                ? "  none"
                : perLevel.entrySet().stream()
                        .map(e -> "  " + e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("\n"));

        return """
                ===== WEATHER STATION REPORT =====
                Sensors registered : %d
                Readings stored    : %d
                Alerts raised      : %d

                Registered sensors
                %s

                Latest readings
                %s

                Alerts by level
                %s

                """.formatted(sensors.size(), readings.size(), alerts.size(),
                sensorLines.isEmpty() ? "  -" : sensorLines,
                latestLines.isEmpty() ? "  -" : latestLines,
                alertLines)
                + report(readings, limits);
    }
}
