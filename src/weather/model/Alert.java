package weather.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** CONCEPT: record + nested static formatter constant. */
public record Alert(AlertLevel level, String sensorId, double value, double limit, Instant time)
        implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String toString() {
        return String.format("[%s] %s ALERT %s = %.1f (limit %.1f)",
                FMT.format(time), level, sensorId, value, limit);
    }
}
