package weather;

import weather.exception.*;
import weather.model.*;
import weather.service.*;
import weather.util.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Dependency-free test runner (no JUnit needed): java -cp out weather.Tests
 * Each test is a lambda; failures are collected and reported with a non-zero exit code.
 */
public class Tests {
    interface Case {

        void run() throws Exception;
    }

    static int passed = 0;

    static final List<String> failures = new ArrayList<>();

    static void test(String name, Case c) {
        try {
            c.run();
            passed++;
            System.out.println("  ok   " + name);
        } catch (Throwable t) {
            failures.add(name + " -> " + t);
            System.out.println("  FAIL " + name + " : " + t);
        }
    }

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    static void eq(Object a, Object b) {
        if (!Objects.equals(a, b)) {
            throw new AssertionError(a + " != " + b);
        }
    }

    static <T extends Throwable> void throwsEx(Class<T> type, Case c) {
        try {
            c.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                return;
            }

            throw new AssertionError("expected " + type.getSimpleName() + " but got " + t);
        }

        throw new AssertionError("expected " + type.getSimpleName() + " but nothing thrown");
    }

    public static void main(String[] args) throws Exception {

        System.out.println("Model");

        test("sensor rejects blank id", () -> throwsEx(InvalidSensorException.class, () -> new TemperatureSensor(" ", 10, "x")));

        test("sensor rejects bad id chars", () -> throwsEx(InvalidSensorException.class, () -> new TemperatureSensor("a,b", 10, "x")));

        test("limit validated against type range", () -> throwsEx(InvalidSensorException.class, () -> new HumiditySensor("H", 101, "x")));

        test("setLimit validated", () -> {
            Sensor s = new HumiditySensor("H", 50, "x");
            throwsEx(InvalidSensorException.class, () -> s.setLimit(-1));
        });

        test("equals/hashCode by id across subtypes", () -> {
            Sensor a = new TemperatureSensor("X", 10, "a");
            Sensor b = new WindSensor("X", 10, "b");
            eq(a, b);
            eq(a.hashCode(), b.hashCode());
        });

        test("polymorphic sample stays in range", () -> {
            Random r = new Random(1);
            for (SensorType t : SensorType.values()) {
                Sensor s = SensorFactory.create(t, "S", t.defaultLimit(), "x");
                for (int i = 0; i < 500; i++) {
                    check(t.inRange(s.sample(r, i % 24)), t + " out of range");
                }
            }
        });

        test("reading validates", () -> throwsEx(IllegalArgumentException.class, () -> Reading.now("A", SensorType.WIND, Double.NaN)));

        test("alert level from overage", () -> {
            eq(AlertLevel.fromOverage(31, 30), AlertLevel.WARNING);
            eq(AlertLevel.fromOverage(50, 30), AlertLevel.CRITICAL);
            eq(AlertLevel.fromOverage(10, 30), AlertLevel.INFO);
        });

        test("enum parse", () -> {
            eq(SensorType.parse(" wind "), SensorType.WIND);
            throwsEx(IllegalArgumentException.class, () -> SensorType.parse("x"));
        });

        System.out.println("Util");

        test("mergeSort sorts and is stable", () -> {
            record P(int k, String tag) {}
            List<P> in = List.of(new P(2, "a"), new P(1, "b"), new P(2, "c"), new P(1, "d"));
            List<P> out = Algorithms.mergeSort(in, Comparator.comparingInt(P::k));
            eq(out.stream().map(P::tag).toList(), List.of("b", "d", "a", "c"));
        });

        test("binarySearch", () -> {
            List<Integer> l = List.of(1, 3, 5, 7);
            eq(Algorithms.binarySearch(l, 5, Comparator.naturalOrder()), 2);
            eq(Algorithms.binarySearch(l, 4, Comparator.naturalOrder()), -3);
        });

        test("varargs max", () -> eq(Algorithms.max(3, 9, 4), 9));

        test("ring buffer overwrites oldest", () -> {
            BoundedHistory<Integer> h = new BoundedHistory<>(3);
            for (int i = 1; i <= 5; i++) {
                h.add(i);
            }
            eq(h.toList(), List.of(3, 4, 5));
        });

        test("validator composition", () -> {
            Validator<String> v = Validator.<String>of(s -> !s.isEmpty(), "empty").and(Validator.of(s -> s.length() < 4, "long"));
            eq(v.check(""), "empty");
            eq(v.check("abcde"), "long");
            check(v.isValid("abc"), "valid");
        });

        test("statistics", () -> {
            Statistics s = Statistics.of(List.of(2.0, 4.0, 6.0));
            eq(s.mean(), 4.0);
            eq(s.min(), 2.0);
            eq(s.max(), 6.0);
            eq(s.range(), 4.0);
            eq(Statistics.of(List.of()), Statistics.EMPTY);
        });

        test("repository", () -> {
            InMemoryRepository<Sensor, String> repo = new InMemoryRepository<>(Sensor::id);
            repo.save(new WindSensor("B", 10, "x"));
            repo.save(new WindSensor("A", 10, "x"));
            eq(repo.findAll().iterator().next().id(), "A");
            check(!repo.saveIfAbsent(new WindSensor("A", 5, "y")), "duplicate accepted");
            check(repo.delete("A") && !repo.exists("A"), "delete");
        });

        System.out.println("Station");

        test("duplicate and missing sensors", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("W", 50, "x"));
            throwsEx(DuplicateSensorException.class, () -> st.addSensor(new WindSensor("W", 50, "x")));
            throwsEx(SensorNotFoundException.class, () -> st.removeSensor("nope"));
        });

        test("record -> latest, search, alerts, hourly", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new TemperatureSensor("T", 30, "x"));
            st.record(Reading.now("T", SensorType.TEMPERATURE, 25));
            st.record(Reading.now("T", SensorType.TEMPERATURE, 45));
            eq(st.latest().get("T").value(), 45.0);
            eq(st.search("T").size(), 2);
            eq(st.alerts().size(), 1);
            eq(st.alerts().get(0).level(), AlertLevel.CRITICAL);
            double[] avg = st.hourlyAverage(SensorType.TEMPERATURE);
            check(Arrays.stream(avg).anyMatch(v -> Math.abs(v - 35) < 1e-9), "hourly average 35");
        });

        test("same-instant readings do not overwrite each other", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 99, "x"));
            st.addSensor(new WindSensor("B", 99, "x"));
            Instant t = Instant.now();
            st.record(new Reading("A", SensorType.WIND, 1, t));
            st.record(new Reading("B", SensorType.WIND, 2, t));
            eq(st.allReadings().size(), 2);
        });

        test("range query via TreeMap", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 99, "x"));
            Instant base = Instant.parse("2025-01-01T00:00:00Z");
            for (int i = 0; i < 10; i++) {
                st.record(new Reading("A", SensorType.WIND, i, base.plusSeconds(i)));
            }
            eq(st.between(base.plusSeconds(3), base.plusSeconds(6)).size(), 4);
        });

        test("sortedByValue", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 99, "x"));
            for (double v : new double[]{5, 1, 3}) {
                st.record(Reading.now("A", SensorType.WIND, v));
            }
            eq(st.sortedByValue().stream().map(Reading::value).toList(), List.of(1.0, 3.0, 5.0));
        });

        test("removed sensor's readings are ignored", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 99, "x"));
            st.removeSensor("A");
            st.record(Reading.now("A", SensorType.WIND, 1));
            eq(st.totalReadings(), 0);
        });

        test("listener receives events", () -> {
            WeatherStation st = new WeatherStation();
            AtomicInteger reads = new AtomicInteger(), alerts = new AtomicInteger();
            st.addListener(new WeatherListener() {
                @Override
                public void onReading(Reading r) {
                    reads.incrementAndGet();
                }
                @Override
                public void onAlert(Alert a) {
                    alerts.incrementAndGet();
                }
            });
            st.addSensor(new WindSensor("A", 10, "x"));
            st.record(Reading.now("A", SensorType.WIND, 5));
            st.record(Reading.now("A", SensorType.WIND, 50));
            eq(reads.get(), 2);
            eq(alerts.get(), 1);
        });

        test("policy decorator", () -> {
            WeatherStation st = new WeatherStation();
            st.setPolicy(AlertPolicy.THRESHOLD.atLeast(AlertLevel.CRITICAL));
            st.addSensor(new WindSensor("A", 100, "x"));
            st.record(Reading.now("A", SensorType.WIND, 101));   // warning -> filtered
            st.record(Reading.now("A", SensorType.WIND, 190));   // critical
            eq(st.alerts().size(), 1);
        });

        test("memory is bounded", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 200, "x"));
            for (int i = 0; i < WeatherStation.HISTORY_PER_SENSOR + 100; i++) {
                st.record(Reading.now("A", SensorType.WIND, 1));
            }
            eq(st.search("A").size(), WeatherStation.HISTORY_PER_SENSOR);
        });

        System.out.println("Case-study additions");

        test("rainfall sensor exists and stays in range", () -> {
            Sensor s = SensorFactory.create(SensorType.RAINFALL, "R", 20, "Field");
            eq(s.getClass(), RainfallSensor.class);
            Random r = new Random(3);

            for (int i = 0; i < 300; i++) {
                check(SensorType.RAINFALL.inRange(s.sample(r, 12)), "out of range");
            }
        });

        test("reading outside the sensor range is rejected", () -> {
            throwsEx(IllegalArgumentException.class, () -> Reading.now("H", SensorType.HUMIDITY, 140));
            throwsEx(IllegalArgumentException.class, () -> Reading.now("R", SensorType.RAINFALL, -2));
            throwsEx(IllegalArgumentException.class, () -> Reading.now("T", SensorType.TEMPERATURE, 99));
        });

        test("timestamp in the future is rejected", () ->
                throwsEx(IllegalArgumentException.class,
                        () -> new Reading("A", SensorType.WIND, 1, Instant.now().plusSeconds(3600))));

        test("search by sensor and by date", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 200, "x"));
            st.addSensor(new WindSensor("B", 200, "x"));
            Instant day1 = Instant.parse("2025-03-01T10:00:00Z");
            Instant day2 = Instant.parse("2025-03-05T10:00:00Z");
            st.record(new Reading("A", SensorType.WIND, 1, day1));
            st.record(new Reading("A", SensorType.WIND, 2, day2));
            st.record(new Reading("B", SensorType.WIND, 3, day2));
            java.time.LocalDate d2 = day2.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            eq(st.search("A", null).size(), 2);
            eq(st.search("A", d2).size(), 1);
            eq(st.search(null, d2).size(), 2);
            eq(st.search("", null).size(), 3);
        });

        test("sort by timestamp", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 200, "x"));
            Instant base = Instant.parse("2025-01-01T00:00:00Z");
            st.record(new Reading("A", SensorType.WIND, 3, base.plusSeconds(30)));
            st.record(new Reading("A", SensorType.WIND, 1, base.plusSeconds(10)));
            st.record(new Reading("A", SensorType.WIND, 2, base.plusSeconds(20)));
            eq(st.sortedByTime().stream().map(Reading::value).toList(), List.of(1.0, 2.0, 3.0));
        });

        test("wrapper-class average is a nullable Double", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 200, "x"));
            eq(st.average("A"), null);
            st.record(Reading.now("A", SensorType.WIND, 10));
            st.record(Reading.now("A", SensorType.WIND, 20));
            eq(st.average("A"), Double.valueOf(15.0));
        });

        test("delete readings keeps the sensor", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 200, "x"));
            st.addSensor(new WindSensor("B", 200, "x"));
            st.record(Reading.now("A", SensorType.WIND, 1));
            st.record(Reading.now("A", SensorType.WIND, 2));
            st.record(Reading.now("B", SensorType.WIND, 3));
            eq(st.deleteReadings("A"), 2);
            eq(st.totalReadings(), 1);
            check(st.findSensor("A").isPresent(), "sensor must remain");
            check(st.search("A").isEmpty(), "readings gone");
        });

        test("full weather report", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new TemperatureSensor("T", 30, "Roof"));
            st.record(Reading.now("T", SensorType.TEMPERATURE, 45));
            String rep = StatisticsService.fullReport(st);
            check(rep.contains("WEATHER STATION REPORT"), "title");
            check(rep.contains("Roof"), "sensor list");
            check(rep.contains("CRITICAL: 1"), "alert summary");
        });

        System.out.println("Concurrency");

        test("simulator pipeline end to end", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new TemperatureSensor("T", 30, "x"));
            st.addSensor(new WindSensor("W", 40, "x"));
            try (Simulator sim = new Simulator(st, new Random(7))) {
                sim.setIntervalMillis(50);
                sim.start();
                Thread.sleep(500);
                sim.stop();
                sim.drain(1000);
                check(sim.generateRound().size() == 2, "round size");
            }
            check(st.totalReadings() >= 10, "got " + st.totalReadings());
        });

        test("start/stop is idempotent", () -> {
            try (Simulator sim = new Simulator(new WeatherStation())) {
                sim.start();
                sim.start();
                check(sim.isRunning(), "running");
                sim.stop();
                sim.stop();
                check(!sim.isRunning(), "stopped");
            }
        });

        test("many threads recording concurrently", () -> {
            WeatherStation st = new WeatherStation();
            st.addSensor(new WindSensor("A", 200, "x"));
            Thread[] ts = new Thread[8];
            for (int i = 0; i < ts.length; i++) {
                ts[i] = new Thread(() -> {
                    for (int j = 0; j < 500; j++) st.record(Reading.now("A", SensorType.WIND, j % 100));
                });
                ts[i].start();
            }
            for (Thread t : ts) {
                t.join();
            }
            eq(st.totalReadings(), 4000);
        });

        System.out.println("Persistence & statistics");

        test("sensor serialization round trip", () -> {
            Path f = Files.createTempFile("sensors", ".bin");
            try {
                PersistenceService.saveSensors(f, List.of(new TemperatureSensor("T", 30, "Roof"), new WindSensor("W", 40, "Tower")));
                List<Sensor> back = PersistenceService.loadSensors(f);
                eq(back.size(), 2);
                eq(back.get(0).location(), "Roof");
                eq(back.get(0).getClass(), TemperatureSensor.class);
            } finally {
                Files.deleteIfExists(f);
            }
        });

        test("csv round trip + bad lines skipped", () -> {
            Path f = Files.createTempFile("readings", ".csv");
            try {
                PersistenceService.exportCsv(f, List.of(Reading.now("A", SensorType.WIND, 1.5), Reading.now("B", SensorType.HUMIDITY, 50)));
                Files.writeString(f, Files.readString(f) + "garbage line\nA,WIND,notanumber,2025-01-01T00:00:00Z\n");
                var res = PersistenceService.importCsv(f);
                eq(res.readings().size(), 2);
                eq(res.skipped(), 2);
            } finally {
                Files.deleteIfExists(f);
            }
        });

        test("missing file -> StorageException", () -> throwsEx(StorageException.class, () -> PersistenceService.loadSensors(Path.of("/no/such/file"))));

        test("config defaults", () -> eq(PersistenceService.loadConfig(Path.of("/no/such")).getProperty("intervalMillis"), "1000"));

        test("statistics service", () -> {
            List<Reading> rs = List.of(Reading.now("A", SensorType.WIND, 10), Reading.now("A", SensorType.WIND, 30), Reading.now("T", SensorType.TEMPERATURE, 20));
            eq(StatisticsService.bySensor(rs).get("A").mean(), 20.0);
            eq(StatisticsService.byType(rs).size(), 2);
            eq(StatisticsService.topN(rs, 1).get(0).value(), 30.0);
            eq(StatisticsService.hottest(rs).get().sensorId(), "T");
            check(StatisticsService.report(rs, Map.of("A", 15.0)).contains("1 over limit"), "report text");
        });

        test("factory from text", () -> {
            eq(SensorFactory.fromText("humidity", "H", "", "x").limit(), SensorType.HUMIDITY.defaultLimit());
            throwsEx(InvalidSensorException.class, () -> SensorFactory.fromText("WIND", "W", "abc", "x"));
            throwsEx(InvalidSensorException.class, () -> SensorFactory.fromText("rain", "W", "1", "x"));
        });

        System.out.println("\n" + passed + " passed, " + failures.size() + " failed");
        failures.forEach(f -> System.out.println("  " + f));
        System.exit(failures.isEmpty() ? 0 : 1);
    }
}
