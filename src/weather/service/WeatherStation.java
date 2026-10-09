package weather.service;

import weather.exception.DuplicateSensorException;
import weather.exception.SensorNotFoundException;
import weather.model.*;
import weather.util.Algorithms;
import weather.util.BoundedHistory;
import weather.util.InMemoryRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The thread-safe core (the "model" in MVC). Keeps every collection from the original program,
 * each for the job it is actually good at:
 *
 *  - sensors   : repository over ConcurrentHashMap   -> O(1) lookup by id        (was HashMap)
 *  - latest    : ConcurrentHashMap<String,Reading>   -> newest value per sensor  (was HashMap)
 *  - history   : per-sensor BoundedHistory ring      -> bounded memory           (was ArrayList)
 *  - byTime    : TreeMap<Instant,List<Reading>>      -> sorted + range queries   (was TreeMap)
 *  - alerts    : LinkedList used as a Deque, capped  -> newest first, O(1) add   (was LinkedList)
 *  - hourly    : double[24] sums / int[24] counts    -> fixed-size array         (was Double[24])
 */
public class WeatherStation {

    public static final int HISTORY_PER_SENSOR = 500;
    public static final int MAX_TOTAL_READINGS = 20_000;
    public static final int MAX_ALERTS = 200;

    private final InMemoryRepository<Sensor, String> sensors = new InMemoryRepository<>(Sensor::id);
    private final Map<String, Reading> latest = new ConcurrentHashMap<>();
    private final Map<String, BoundedHistory<Reading>> history = new ConcurrentHashMap<>();

    // The next three structures are NOT thread-safe on their own -> guarded by one RW lock.
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final TreeMap<Instant, List<Reading>> byTime = new TreeMap<>();
    private final LinkedList<Alert> alerts = new LinkedList<>();
    private final EnumMap<SensorType, double[]> hourlySum = new EnumMap<>(SensorType.class);
    private final EnumMap<SensorType, int[]> hourlyCount = new EnumMap<>(SensorType.class);
    private int totalReadings = 0;

    private final List<WeatherListener> listeners = new CopyOnWriteArrayList<>();
    private volatile AlertPolicy policy = AlertPolicy.THRESHOLD;

    public WeatherStation() {
        for (SensorType t : SensorType.values()) {
            hourlySum.put(t, new double[24]);
            hourlyCount.put(t, new int[24]);
        }
    }

    // ---------------------------------------------------------------- listeners / policy
    public void addListener(WeatherListener l) {
        listeners.add(Objects.requireNonNull(l));
    }

    public void removeListener(WeatherListener l) {
        listeners.remove(l);
    }

    public void setPolicy(AlertPolicy p) {
        this.policy = Objects.requireNonNull(p);
    }

    // ---------------------------------------------------------------- sensor management
    public void addSensor(Sensor s) throws DuplicateSensorException {
        if (!sensors.saveIfAbsent(s)) {
            throw new DuplicateSensorException(s.id());
        }

        fireSensorsChanged();
    }

    public void removeSensor(String id) throws SensorNotFoundException {
        if (!sensors.delete(id)) {
            throw new SensorNotFoundException(id);
        }

        latest.remove(id);
        history.remove(id);
        fireSensorsChanged();
    }

    public Optional<Sensor> findSensor(String id) {
        return sensors.findById(id);
    }

    public Collection<Sensor> sensors() {
        return sensors.findAll();
    }

    public void replaceSensors(Collection<Sensor> loaded) {
        for (Sensor s : sensors.findAll()) {
            sensors.delete(s.id());
        }

        latest.clear();
        history.clear();
        loaded.forEach(sensors::save);
        fireSensorsChanged();
    }

    // ---------------------------------------------------------------- recording
    /** Called by the consumer thread. Silently ignores readings from sensors removed meanwhile. */
    public void record(Reading r) {
        Optional<Sensor> found = sensors.findById(r.sensorId());

        if (found.isEmpty()) {
            return;
        }

        Sensor sensor = found.get();

        latest.put(r.sensorId(), r);
        history.computeIfAbsent(r.sensorId(), k -> new BoundedHistory<>(HISTORY_PER_SENSOR)).add(r);

        Optional<Alert> alert = policy.evaluate(sensor, r);

        lock.writeLock().lock();

        try {
            byTime.computeIfAbsent(r.time(), k -> new ArrayList<>(1)).add(r);
            totalReadings++;

            while (totalReadings > MAX_TOTAL_READINGS) {         // evict oldest
                Map.Entry<Instant, List<Reading>> oldest = byTime.firstEntry();
                totalReadings -= oldest.getValue().size();
                byTime.pollFirstEntry();
            }

            int hour = r.time().atZone(ZoneId.systemDefault()).getHour();
            hourlySum.get(r.type())[hour] += r.value();
            hourlyCount.get(r.type())[hour]++;
            alert.ifPresent(a -> {
                alerts.addFirst(a);

                while (alerts.size() > MAX_ALERTS) {
                    alerts.removeLast();
                }
            });
        } finally {
            lock.writeLock().unlock();
        }

        // notify OUTSIDE the lock: listeners may call back into the station -> no deadlock
        for (WeatherListener l : listeners) {
            l.onReading(r);
            alert.ifPresent(l::onAlert);
        }
    }

    // ---------------------------------------------------------------- queries
    public Map<String, Reading> latest() {
        return new TreeMap<>(latest);                    // sorted copy
    }

    public List<Alert> alerts() {
        lock.readLock().lock();

        try {
            return new ArrayList<>(alerts);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Reading> allReadings() {
        lock.readLock().lock();

        try {
            List<Reading> out = new ArrayList<>(totalReadings);
            byTime.values().forEach(out::addAll);

            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** Original search(): all readings of one sensor (now O(k) from the ring buffer, not O(N)). */
    public List<Reading> search(String sensorId) {
        BoundedHistory<Reading> h = history.get(sensorId);

        return h == null ? List.of() : h.toList();
    }

    /**
     * Search by sensor id and/or calendar date (either may be null/blank).
     * Examples: ("T1", null) -> all T1 readings, (null, 2026-10-09) -> everything from that day.
     */
    public List<Reading> search(String sensorId, LocalDate date) {
        boolean noId = sensorId == null || sensorId.isBlank();
        List<Reading> source = noId ? allReadings() : search(sensorId.trim());

        if (date == null) {
            return source;
        }

        List<Reading> out = new ArrayList<>();

        for (Reading r : source) {
            LocalDate day = r.time().atZone(ZoneId.systemDefault()).toLocalDate();

            if (day.equals(date)) {
                out.add(r);
            }
        }

        return out;
    }

    /** Sort by timestamp, oldest first (second sort order required by the case study). */
    public List<Reading> sortedByTime() {
        return Algorithms.mergeSort(allReadings(), Comparator.naturalOrder());
    }

    /**
     * WRAPPER CLASSES: average of one sensor's readings.
     * Values are collected as List&lt;Double&gt; (autoboxing), summed by unboxing, and the result is a
     * nullable Double - null means "no data yet", which a primitive double could not express.
     */
    public Double average(String sensorId) {
        List<Double> values = new ArrayList<>();

        for (Reading r : search(sensorId)) {
            values.add(r.value());                 // autoboxing double -> Double
        }

        if (values.isEmpty()) {
            return null;
        }

        double sum = 0;

        for (Double v : values) {
            sum += v;                              // auto-unboxing Double -> double
        }

        return Double.valueOf(sum / values.size());
    }

    /** CRUD "delete" for readings: removes every stored reading of one sensor (the sensor stays). */
    public int deleteReadings(String sensorId) {
        int removed = 0;

        lock.writeLock().lock();

        try {
            Iterator<Map.Entry<Instant, List<Reading>>> it = byTime.entrySet().iterator();

            while (it.hasNext()) {
                List<Reading> bucket = it.next().getValue();
                int before = bucket.size();
                bucket.removeIf(r -> r.sensorId().equals(sensorId));
                removed += before - bucket.size();

                if (bucket.isEmpty()) {
                    it.remove();
                }
            }

            totalReadings -= removed;
            rebuildHourly();
        } finally {
            lock.writeLock().unlock();
        }

        latest.remove(sensorId);
        history.remove(sensorId);
        fireSensorsChanged();

        return removed;
    }

    /** Recompute the 24-hour snapshot arrays from the stored readings. Caller holds the write lock. */
    private void rebuildHourly() {
        hourlySum.values().forEach(a -> Arrays.fill(a, 0));
        hourlyCount.values().forEach(a -> Arrays.fill(a, 0));

        for (List<Reading> bucket : byTime.values()) {
            for (Reading r : bucket) {
                int hour = r.time().atZone(ZoneId.systemDefault()).getHour();
                hourlySum.get(r.type())[hour] += r.value();
                hourlyCount.get(r.type())[hour]++;
            }
        }
    }

    /** Original sort(): by value, via our own stable merge sort. */
    public List<Reading> sortedByValue() {
        return Algorithms.mergeSort(allReadings(), Reading.BY_VALUE);
    }

    /** Range query: this is why byTime is a TreeMap (subMap is O(log n + k)). */
    public List<Reading> between(Instant from, Instant toInclusive) {
        lock.readLock().lock();

        try {
            List<Reading> out = new ArrayList<>();
            byTime.subMap(from, true, toInclusive, true).values().forEach(out::addAll);

            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    /** 24 averages for one type; NaN where no data. */
    public double[] hourlyAverage(SensorType type) {
        lock.readLock().lock();

        try {
            double[] out = new double[24];

            for (int h = 0; h < 24; h++) {
                int c = hourlyCount.get(type)[h];
                out[h] = c == 0 ? Double.NaN : hourlySum.get(type)[h] / c;
            }

            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    public int totalReadings() {
        lock.readLock().lock();

        try {
            return totalReadings;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clearData() {
        lock.writeLock().lock();

        try {
            byTime.clear();
            alerts.clear();
            totalReadings = 0;
            hourlySum.values().forEach(a -> Arrays.fill(a, 0));
            hourlyCount.values().forEach(a -> Arrays.fill(a, 0));
        } finally {
            lock.writeLock().unlock();
        }

        latest.clear();
        history.clear();
    }

    private void fireSensorsChanged() {
        for (WeatherListener l : listeners) {
            l.onSensorsChanged();
        }
    }
}
