package weather.service;

import weather.exception.InvalidSensorException;
import weather.model.*;

/** CONCEPT: Factory pattern + switch expression over an enum (compiler checks exhaustiveness). */
public final class SensorFactory {
    private SensorFactory() {}

    public static Sensor create(SensorType type, String id, double limit, String location)
            throws InvalidSensorException {
        return switch (type) {
            case TEMPERATURE -> new TemperatureSensor(id, limit, location);
            case HUMIDITY    -> new HumiditySensor(id, limit, location);
            case RAINFALL    -> new RainfallSensor(id, limit, location);
            case PRESSURE    -> new PressureSensor(id, limit, location);
            case WIND        -> new WindSensor(id, limit, location);
        };
    }

    /** Parse user text fields (as typed in the GUI) into a validated sensor. */
    public static Sensor fromText(String type, String id, String limitText, String location)
            throws InvalidSensorException {
        SensorType t;

        try {
            t = SensorType.parse(type);
        } catch (IllegalArgumentException e) {
            throw new InvalidSensorException(e.getMessage());
        }

        double limit;

        if (limitText == null || limitText.isBlank()) {
            limit = t.defaultLimit();
        } else {
            try {
                limit = Double.parseDouble(limitText.trim());
            } catch (NumberFormatException e) {
                throw new InvalidSensorException("Limit is not a number: " + limitText);
            }
        }

        return create(t, id, limit, location);
    }
}
