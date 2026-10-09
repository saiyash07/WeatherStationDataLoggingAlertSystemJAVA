package weather.service;

import weather.exception.StorageException;
import weather.model.Reading;
import weather.model.Sensor;
import weather.model.SensorType;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * CONCEPT: file I/O — try-with-resources, java.nio.file, object serialization with a
 * deserialization FILTER (security), CSV text parsing, Properties, CompletableFuture for async work.
 */
public final class PersistenceService {
    private PersistenceService() {}

    private static final String CSV_HEADER = "sensorId,type,value,timestamp";

    // ---------------------------------------------------------------- binary: sensors
    public static void saveSensors(Path file, Collection<Sensor> sensors) {
        try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
            out.writeObject(new ArrayList<>(sensors));
        } catch (IOException e) {
            throw new StorageException("Cannot save sensors to " + file, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Sensor> loadSensors(Path file) {
        try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
            // Never deserialize untrusted data without a whitelist.
            in.setObjectInputFilter(ObjectInputFilter.Config.createFilter(
                    "weather.model.*;java.util.*;java.lang.*;java.time.*;!*"));
            return (List<Sensor>) in.readObject();
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            throw new StorageException("Cannot load sensors from " + file, e);
        }
    }

    // ---------------------------------------------------------------- text: readings as CSV
    public static int exportCsv(Path file, Collection<Reading> readings) {
        try (BufferedWriter w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write(CSV_HEADER);
            w.newLine();
            int n = 0;

            for (Reading r : readings) {
                w.write(String.format(Locale.ROOT, "%s,%s,%.3f,%s", r.sensorId(), r.type(), r.value(), r.time()));
                w.newLine();
                n++;
            }

            return n;
        } catch (IOException e) {
            throw new StorageException("Cannot export to " + file, e);
        }
    }

    /** Lenient import: bad lines are skipped and counted, not fatal. */
    public static ImportResult importCsv(Path file) {
        List<Reading> good = new ArrayList<>();
        int bad = 0;

        try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line = r.readLine();                       // header

            while ((line = r.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                String[] p = line.split(",");

                try {
                    if (p.length != 4) {
                        throw new IllegalArgumentException("columns");
                    }

                    good.add(new Reading(p[0], SensorType.parse(p[1]), Double.parseDouble(p[2]), Instant.parse(p[3])));
                } catch (RuntimeException ex) {               // NumberFormat, DateTimeParse, IllegalArgument
                    bad++;
                }
            }
        } catch (IOException e) {
            throw new StorageException("Cannot import " + file, e);
        }

        return new ImportResult(good, bad);
    }

    /** nested record = result type that only this class needs */
    public record ImportResult(List<Reading> readings, int skipped) {}

    /** Async wrapper: UI stays responsive while the file is written. */
    public static CompletableFuture<Integer> exportCsvAsync(Path file, Collection<Reading> snapshot, ExecutorService pool) {
        return CompletableFuture.supplyAsync(() -> exportCsv(file, snapshot), pool);
    }

    // ---------------------------------------------------------------- config: Properties
    public static Properties loadConfig(Path file) {
        Properties defaults = new Properties();
        defaults.setProperty("intervalMillis", "1000");
        defaults.setProperty("autostart", "false");
        Properties p = new Properties(defaults);

        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file)) {
                p.load(r);
            } catch (IOException e) {
                throw new StorageException("Cannot read config " + file, e);
            }
        }

        return p;
    }

    public static void saveConfig(Path file, Properties p) {
        try (Writer w = Files.newBufferedWriter(file)) {
            p.store(w, "Weather Station settings");
        } catch (IOException e) {
            throw new StorageException("Cannot write config " + file, e);
        }
    }
}
